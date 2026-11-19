package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.ReadOnlyFeatureSet;
import com.bloxbean.intelliada.idea.joust.lsp.JoustClientFeatures;
import com.bloxbean.intelliada.idea.joust.lsp.JoustLanguageClient;
import com.bloxbean.intelliada.idea.joust.lsp.JoustLanguageServerFactory;
import com.bloxbean.intelliada.idea.joust.textmate.JoustTextMateBundleProvider;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * ADR-004 M1 platform tests: I5's fake-server tests (an LSP4J server over pipes sending edits, registrations, documents
 * and messages to IntelliAda's client), D3a item 1's feature allow-list on the real feature objects, I25's
 * workspace-folder confinement, the LSP4IJ mapping and the TextMate bundle.
 */
public class JoustClientPlatformTest extends BasePlatformTestCase {

    private Path base;
    private Path inside;
    private Path outside;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        base = Files.createDirectories(Path.of(getProject().getBasePath())).toRealPath();
        inside = Files.writeString(Files.createDirectories(base.resolve("contracts/app")).resolve("Counter.jsc"), "jsc 1\n");
        outside = Files.writeString(Files.createTempFile("joust-outside", ".jsc"), "secret");
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(inside);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Files.deleteIfExists(outside);
        } finally {
            super.tearDown();
        }
    }

    /** The fake server's view of IntelliAda's client, over real JSON-RPC pipes. */
    private LanguageClient connect(JoustLanguageClient client) throws Exception {
        PipedInputStream clientIn = new PipedInputStream(1 << 16);
        PipedOutputStream serverOut = new PipedOutputStream(clientIn);
        PipedInputStream serverIn = new PipedInputStream(1 << 16);
        PipedOutputStream clientOut = new PipedOutputStream(serverIn);
        Launcher<LanguageServer> clientSide = LSPLauncher.createClientLauncher(client, clientIn, clientOut);
        clientSide.startListening();
        Launcher<LanguageClient> serverSide = LSPLauncher.createServerLauncher(new FakeServer(), serverIn, serverOut);
        serverSide.startListening();
        return serverSide.getRemoteProxy();
    }

    public void testApplyEditIsRefused() throws Exception {
        LanguageClient c = connect(new JoustLanguageClient(getProject()));
        TextEdit edit = new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "EVIL");
        WorkspaceEdit we = new WorkspaceEdit(Map.of(inside.toUri().toString(), List.of(edit)));
        ApplyWorkspaceEditResponse r = c.applyEdit(new ApplyWorkspaceEditParams(we)).get(10, TimeUnit.SECONDS);
        assertFalse(r.isApplied());
        assertNotNull(r.getFailureReason());
        assertEquals("jsc 1\n", Files.readString(inside));
    }

    public void testEditingRegistrationsAreRefused() throws Exception {
        LanguageClient c = connect(new JoustLanguageClient(getProject()));
        for (String method : new String[]{"textDocument/formatting", "textDocument/rangeFormatting", "textDocument/rename",
                "textDocument/completion", "textDocument/codeAction", "workspace/executeCommand", "workspace/didChangeWatchedFiles",
                "workspace/willRenameFiles"}) {
            CompletableFuture<Void> f = c.registerCapability(new RegistrationParams(List.of(new Registration("id-" + method, method))));
            try {
                f.get(10, TimeUnit.SECONDS);
                fail("accepted " + method);
            } catch (java.util.concurrent.ExecutionException expected) {
                // refused with an error
            }
        }
        // a mixed request is refused whole
        CompletableFuture<Void> mixed = c.registerCapability(new RegistrationParams(List.of(
                new Registration("a", "textDocument/hover"), new Registration("b", "textDocument/formatting"))));
        try {
            mixed.get(10, TimeUnit.SECONDS);
            fail("accepted a request containing formatting");
        } catch (java.util.concurrent.ExecutionException expected) {
            // refused
        }
    }

    public void testShowDocumentIsConfined() throws Exception {
        LanguageClient c = connect(new JoustLanguageClient(getProject()));
        Path link = Files.createSymbolicLink(base.resolve("contracts/app/Link.jsc"), outside);
        for (ShowDocumentParams p : new ShowDocumentParams[]{
                external("https://evil.example/x"), external(inside.toUri().toString()),
                new ShowDocumentParams("https://evil.example/x"), new ShowDocumentParams(outside.toUri().toString()),
                new ShowDocumentParams(link.toUri().toString()), new ShowDocumentParams("joust-lib:/std/List.jsc")}) {
            assertFalse(p.getUri(), c.showDocument(p).get(10, TimeUnit.SECONDS).isSuccess());
        }
        CompletableFuture<ShowDocumentResult> ok = c.showDocument(new ShowDocumentParams(inside.toUri().toString()));
        PlatformTestUtil.waitWithEventsDispatching("showDocument", ok::isDone, 10);
        assertTrue(ok.get().isSuccess());
        VirtualFile[] open = FileEditorManager.getInstance(getProject()).getOpenFiles();
        assertTrue(java.util.Arrays.stream(open).anyMatch(f -> f.getName().equals("Counter.jsc")));
        assertFalse(java.util.Arrays.stream(open).anyMatch(f -> f.getPath().contains("joust-outside")));
    }

    public void testMessagesArePlainText() throws Exception {
        List<Notification> seen = new CopyOnWriteArrayList<>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(Notification n) {
                seen.add(n);
            }
        });
        LanguageClient c = connect(new JoustLanguageClient(getProject()));
        c.showMessage(new MessageParams(MessageType.Error, "<img src=http://127.0.0.1:9/x> <a href=\"file:///etc/passwd\">open</a>"));
        ShowMessageRequestParams req = new ShowMessageRequestParams(List.of(new MessageActionItem("<html><img src=x>OK")));
        req.setType(MessageType.Info);
        req.setMessage("**bold** <b>b</b>");
        c.showMessageRequest(req);
        PlatformTestUtil.waitWithEventsDispatching("notifications", () -> seen.size() >= 2, 10);
        for (Notification n : seen) {
            assertFalse(n.getContent(), n.getContent().contains("<img"));
            assertFalse(n.getContent(), n.getContent().contains("<a "));
            assertFalse(n.getContent(), n.getContent().contains("<b>"));
            assertNull(n.getListener());
        }
        assertTrue(seen.get(0).getContent().contains("&lt;img src=http://127.0.0.1:9/x&gt;"));
        for (var a : seen.get(1).getActions()) {
            String text = a.getTemplatePresentation().getText();
            assertFalse(text, text.contains("<"));
        }
    }

    public void testWorkspaceFoldersAreConfined() throws Exception {
        LanguageClient c = connect(new JoustLanguageClient(getProject()));
        List<WorkspaceFolder> folders = c.workspaceFolders().get(10, TimeUnit.SECONDS);
        assertFalse(folders.isEmpty());
        for (WorkspaceFolder f : folders) {
            Path p = Path.of(java.net.URI.create(f.getUri())).toRealPath();
            assertTrue(p + " is inside " + base, p.startsWith(base));
        }
        InitializeParams init = new InitializeParams();
        init.setWorkspaceFolders(List.of(new WorkspaceFolder(outside.getParent().toUri().toString(), "outside")));
        ClientCapabilities caps = new ClientCapabilities();
        WorkspaceClientCapabilities ws = new WorkspaceClientCapabilities();
        ws.setApplyEdit(true);
        WorkspaceEditCapabilities we = new WorkspaceEditCapabilities();
        we.setFailureHandling(FailureHandlingKind.Transactional);
        ws.setWorkspaceEdit(we);
        caps.setWorkspace(ws);
        init.setCapabilities(caps);
        JoustClientFeatures.confine(init, folders);
        assertEquals(folders, init.getWorkspaceFolders());
        assertFalse(init.getCapabilities().getWorkspace().getApplyEdit());
        assertNull(init.getCapabilities().getWorkspace().getWorkspaceEdit().getFailureHandling());
    }

    public void testFeatureObjectsFollowTheAllowList() throws Exception {
        LSPClientFeatures features = new JoustLanguageServerFactory().createClientFeatures();
        PsiFile file = myFixture.configureByText("Counter.jsc", "jsc 1\n");
        for (Map.Entry<String, ReadOnlyFeatureSet.Treatment> e : ReadOnlyFeatureSet.FEATURES.entrySet()) {
            String getter = "get" + e.getKey().substring(3);
            Method m = LSPClientFeatures.class.getMethod(getter);
            Object feature = m.invoke(features);
            boolean enabled = isEnabled(feature, file);
            if (e.getValue() == ReadOnlyFeatureSet.Treatment.LSP4IJ) {
                assertTrue(getter + " should be LSP4IJ's and enabled", enabled);
            } else {
                assertFalse(getter + " must be disabled in LSP4IJ", enabled);
            }
        }
    }

    /** D3a item 7: the helper Go to Definition and Find References use keeps only in-project targets. */
    public void testNavigationTargetsAreConfined() throws Exception {
        Path link = Files.createSymbolicLink(base.resolve("contracts/app/Out.jsc"), outside);
        Range r = new Range(new Position(0, 0), new Position(0, 1));
        List<Location> locs = List.of(new Location(inside.toUri().toString(), r), new Location(outside.toUri().toString(), r),
                new Location(link.toUri().toString(), r), new Location("joust-lib:/std/List.jsc", r),
                new Location("https://evil.example/Counter.jsc", r), new Location("file:///etc/passwd", r));
        var targets = com.bloxbean.intelliada.idea.joust.editor.JoustServerAccess.confine(getProject(), locs);
        assertEquals(1, targets.size());
        assertEquals("Counter.jsc", targets.get(0).file().getName());
    }

    public void testServerIsMappedByFileNamePattern() {
        var def = LanguageServersRegistry.getInstance().getServerDefinition("joustLanguageServer");
        assertNotNull("the JOUST server is registered", def);
        assertTrue(def.getDisplayName().contains("JOUST"));
    }

    public void testTextMateBundleIsProvided() throws Exception {
        var bundles = new JoustTextMateBundleProvider().getBundles();
        assertEquals(1, bundles.size());
        Path dir = bundles.get(0).getPath();
        assertTrue(Files.isRegularFile(dir.resolve("package.json")));
        assertTrue(Files.isRegularFile(dir.resolve("syntaxes/jsc.tmLanguage.json")));
        assertTrue(Files.isRegularFile(dir.resolve("language-configuration.json")));
        assertFalse(dir.startsWith(base));
    }

    private static boolean isEnabled(Object feature, PsiFile file) throws Exception {
        for (Method m : feature.getClass().getMethods()) {
            if (!m.getName().equals("isEnabled") || Modifier.isStatic(m.getModifiers())) continue;
            m.setAccessible(true);
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(file.getClass())) {
                return (boolean) m.invoke(feature, file);
            }
            if (m.getParameterCount() == 0) return (boolean) m.invoke(feature);
        }
        return true;
    }

    private static ShowDocumentParams external(String uri) {
        ShowDocumentParams p = new ShowDocumentParams(uri);
        p.setExternal(true);
        return p;
    }

    /** A server that does nothing on its own; the test drives the client through its remote proxy. */
    static final class FakeServer implements LanguageServer {
        @Override
        public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
            return CompletableFuture.completedFuture(new InitializeResult(new ServerCapabilities()));
        }

        @Override
        public CompletableFuture<Object> shutdown() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void exit() {
        }

        @Override
        public TextDocumentService getTextDocumentService() {
            return null;
        }

        @Override
        public WorkspaceService getWorkspaceService() {
            return null;
        }
    }
}
