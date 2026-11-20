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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
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
    private Path outsideDir;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        base = Files.createDirectories(Path.of(getProject().getBasePath())).toRealPath();
        inside = Files.writeString(Files.createDirectories(base.resolve("contracts/app")).resolve("Counter.jsc"), "jsc 1\n");
        // A private directory: never the JVM's whole temp directory as a content root (M1-CDX-26).
        outsideDir = Files.createTempDirectory("joust-outside");
        outside = Files.writeString(outsideDir.resolve("outside.jsc"), "secret");
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(inside);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            listening.forEach(f -> f.cancel(true));
            for (java.io.Closeable c : pipes) {
                try {
                    c.close();
                } catch (java.io.IOException ignored) {
                    // already closed
                }
            }
            com.intellij.openapi.util.io.FileUtil.delete(outsideDir);
        } finally {
            super.tearDown();
        }
    }

    private final List<java.io.Closeable> pipes = new ArrayList<>();
    private final List<java.util.concurrent.Future<Void>> listening = new ArrayList<>();

    /** A client whose filtered registrations are recorded instead of handed to an LSP4IJ wrapper. */
    static final class RecordingClient extends JoustLanguageClient {
        final List<String> accepted = new CopyOnWriteArrayList<>();

        RecordingClient(com.intellij.openapi.project.Project p) {
            super(p);
        }

        @Override
        protected CompletableFuture<Void> acceptRegistration(RegistrationParams params) {
            params.getRegistrations().forEach(r -> accepted.add(r.getMethod()));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** The fake server's view of IntelliAda's client, over real JSON-RPC pipes. */
    private LanguageClient connect(JoustLanguageClient client) throws Exception {
        PipedInputStream clientIn = new PipedInputStream(1 << 16);
        PipedOutputStream serverOut = new PipedOutputStream(clientIn);
        PipedInputStream serverIn = new PipedInputStream(1 << 16);
        PipedOutputStream clientOut = new PipedOutputStream(serverIn);
        pipes.addAll(List.of(clientIn, serverOut, serverIn, clientOut));
        Launcher<LanguageServer> clientSide = LSPLauncher.createClientLauncher(client, clientIn, clientOut);
        listening.add(clientSide.startListening());
        Launcher<LanguageClient> serverSide = LSPLauncher.createServerLauncher(new FakeServer(), serverIn, serverOut);
        listening.add(serverSide.startListening());
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
        RecordingClient client = new RecordingClient(getProject());
        LanguageClient c = connect(client);
        for (String method : new String[]{"textDocument/formatting", "textDocument/rangeFormatting", "textDocument/rename",
                "textDocument/completion", "textDocument/codeAction", "workspace/executeCommand", "workspace/didChangeWatchedFiles",
                "workspace/willRenameFiles"}) {
            CompletableFuture<Void> f = c.registerCapability(new RegistrationParams(List.of(new Registration("id-" + method, method))));
            assertInvalidRequest(method, f);
        }
        assertTrue("nothing refused reached LSP4IJ: " + client.accepted, client.accepted.isEmpty());
        // an allowed registration reaches LSP4IJ
        c.registerCapability(new RegistrationParams(List.of(new Registration("h", "textDocument/hover")))).get(10, TimeUnit.SECONDS);
        assertEquals(List.of("textDocument/hover"), client.accepted);
        client.accepted.clear();
        // a mixed request is refused whole
        CompletableFuture<Void> mixed = c.registerCapability(new RegistrationParams(List.of(
                new Registration("a", "textDocument/hover"), new Registration("b", "textDocument/formatting"))));
        assertInvalidRequest("mixed", mixed);
        assertTrue("a mixed request changes no capability", client.accepted.isEmpty());
    }

    private static void assertInvalidRequest(String what, CompletableFuture<Void> f) throws Exception {
        try {
            f.get(10, TimeUnit.SECONDS);
            fail("accepted " + what);
        } catch (java.util.concurrent.ExecutionException e) {
            assertTrue(what + ": " + e.getCause(), e.getCause() instanceof org.eclipse.lsp4j.jsonrpc.ResponseErrorException);
            assertEquals(what, org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidRequest.getValue(),
                    ((org.eclipse.lsp4j.jsonrpc.ResponseErrorException) e.getCause()).getResponseError().getCode());
        }
    }

    /** M1-CDX-17: clicking an action answers with that item; dismissing answers null. */
    public void testMessageRequestAnswersTheClickedItem() throws Exception {
        List<Notification> seen = new CopyOnWriteArrayList<>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(Notification n) {
                seen.add(n);
            }
        });
        LanguageClient c = connect(new JoustLanguageClient(getProject()));
        ShowMessageRequestParams req = new ShowMessageRequestParams(List.of(new MessageActionItem("Yes"), new MessageActionItem("No")));
        req.setType(MessageType.Info);
        req.setMessage("choose");
        CompletableFuture<MessageActionItem> answer = c.showMessageRequest(req);
        PlatformTestUtil.waitWithEventsDispatching("the request notification", () -> !seen.isEmpty(), 10);
        Notification n = seen.get(0);
        var yes = n.getActions().get(0);
        ApplicationManager.getApplication().invokeAndWait(() -> Notification.fire(n, yes, null));
        assertEquals("Yes", answer.get(10, TimeUnit.SECONDS).getTitle());
        seen.clear();
        CompletableFuture<MessageActionItem> dismissed = c.showMessageRequest(req);
        PlatformTestUtil.waitWithEventsDispatching("the second notification", () -> !seen.isEmpty(), 10);
        seen.get(0).expire();
        assertNull(dismissed.get(10, TimeUnit.SECONDS));
    }

    /** M1-CDX-1, M1-CDX-2: native and generated targets are refused; a target moved out after listing is refused. */
    public void testSafeOpenRefusesNativeGeneratedAndMovedTargets() throws Exception {
        Path png = Files.write(base.resolve("contracts/app/pic.png"), new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        VirtualFile pngVf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(png);
        VirtualFile in = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(inside);
        com.intellij.testFramework.ExtensionTestUtil.addExtensions(com.intellij.openapi.roots.GeneratedSourcesFilter.EP_NAME,
                List.of(new com.intellij.openapi.roots.GeneratedSourcesFilter() {
                    @Override
                    public boolean isGeneratedSource(VirtualFile file, com.intellij.openapi.project.Project project) {
                        return file.getName().equals("Gen.jsc");
                    }
                }), getTestRootDisposable());
        Path gen = Files.writeString(base.resolve("contracts/app/Gen.jsc"), "x");
        VirtualFile genVf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(gen);
        Path movable = Files.writeString(base.resolve("contracts/app/Movable.jsc"), "x");
        VirtualFile movableVf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(movable);
        Files.move(movable, outside.resolveSibling("moved-" + System.nanoTime() + ".jsc"));
        Files.createSymbolicLink(movable, outside);
        ApplicationManager.getApplication().invokeAndWait(() -> {
            assertFalse(com.bloxbean.intelliada.idea.joust.editor.JoustSafeOpen.open(getProject(), pngVf, 0, false));
            assertFalse(com.bloxbean.intelliada.idea.joust.editor.JoustSafeOpen.open(getProject(), genVf, 0, false));
            assertFalse(com.bloxbean.intelliada.idea.joust.editor.JoustSafeOpen.open(getProject(), movableVf, 0, false));
            assertTrue(com.bloxbean.intelliada.idea.joust.editor.JoustSafeOpen.open(getProject(), in, 0, false));
        });
    }

    /** G1.1 (M1-CDX-20): TextMate's flat PSI accepts a LineMarkerProvider (ADR-005 D15's gutter icons rely on it). */
    public void testTextMatePsiAcceptsALineMarkerProvider() {
        List<PsiElement> seen = new CopyOnWriteArrayList<>();
        com.intellij.codeInsight.daemon.LineMarkerProviders.getInstance().addExplicitExtension(
                org.jetbrains.plugins.textmate.TextMateLanguage.LANGUAGE, new com.intellij.codeInsight.daemon.LineMarkerProvider() {
                    @Override
                    public com.intellij.codeInsight.daemon.LineMarkerInfo<?> getLineMarkerInfo(PsiElement element) {
                        seen.add(element);
                        return null;
                    }
                }, getTestRootDisposable());
        myFixture.configureByText("Probe.jsc", "jsc 1\npackage app\n");
        myFixture.doHighlighting();
        assertFalse("the provider ran on the .jsc file's PSI", seen.isEmpty());
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
        VirtualFile inBase = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Files.createDirectories(base.resolve("contracts")));
        VirtualFile external = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outside.getParent());
        com.intellij.testFramework.PsiTestUtil.addContentRoot(getModule(), inBase);
        com.intellij.testFramework.PsiTestUtil.addContentRoot(getModule(), external);
        List<WorkspaceFolder> folders;
        try {
            LanguageClient c = connect(new JoustLanguageClient(getProject()));
            folders = c.workspaceFolders().get(10, TimeUnit.SECONDS);
        } finally {
            com.intellij.testFramework.PsiTestUtil.removeContentEntry(getModule(), inBase);
            com.intellij.testFramework.PsiTestUtil.removeContentEntry(getModule(), external);
        }
        assertEquals(1, folders.size());
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

    /** I25 on LSP4IJ 0.30.1: the initial folders are the confined ones, and no file adds another (E21). */
    public void testTheWorkspaceFolderFeatureAddsNoFolder() throws Exception {
        LSPClientFeatures features = new JoustLanguageServerFactory().createClientFeatures();
        var w = features.getWorkspaceFolderFeature();
        assertEquals(com.bloxbean.intelliada.idea.joust.lsp.JoustProjectFolders.confined(getProject()), w.getInitialWorkspaceFolders(getProject()));
        Path elsewhere = Files.createTempDirectory("joust-elsewhere");
        Files.writeString(elsewhere.resolve("Other.jsc"), "jsc 1\n");
        var vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(elsewhere.resolve("Other.jsc"));
        assertNotNull(vf);
        assertNull(w.computeWorkspaceFolderToNotify(vf));
        assertFalse(w.markFolderAsNotified(new WorkspaceFolder(elsewhere.toUri().toString(), "elsewhere")));
        ServerCapabilities caps = new ServerCapabilities();
        WorkspaceServerCapabilities wsCaps = new WorkspaceServerCapabilities();
        WorkspaceFoldersOptions folders = new WorkspaceFoldersOptions();
        folders.setSupported(true);
        folders.setChangeNotifications(true);
        wsCaps.setWorkspaceFolders(folders);
        caps.setWorkspace(wsCaps);
        assertNull(JoustClientFeatures.readOnly(caps).getWorkspace().getWorkspaceFolders());
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
        // The three LSP4IJ 0.30.1 features without isEnabled (ADR-004 §12.2 E21): "enabled" is whether they act at all.
        if (feature instanceof com.redhat.devtools.lsp4ij.client.features.LSPConfigurationFeature c) {
            return c.getOnConfigurationChanged() != com.redhat.devtools.lsp4ij.client.features.LSPConfigurationFeature.OnConfigurationChanged.IGNORE;
        }
        if (feature instanceof com.redhat.devtools.lsp4ij.client.features.EditorBehaviorFeature b) {
            return b.isEnableStringLiteralImprovements(file) || b.isEnableStatementTerminatorImprovements(file)
                    || b.isEnableEnterBetweenBracesFix(file) || b.isEnableTextMateNestedBracesImprovements(file)
                    || b.isEnableSemanticTokensFileViewProvider(file);
        }
        if (feature instanceof com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature w) {
            return w.computeWorkspaceFolderToNotify(file.getVirtualFile()) != null
                    || w.markFolderAsNotified(new WorkspaceFolder("file:///elsewhere", "elsewhere"));
        }
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
