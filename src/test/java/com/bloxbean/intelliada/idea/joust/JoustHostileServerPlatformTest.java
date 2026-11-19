package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.JoustConstants;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.editor.JoustDocumentationTargetProvider;
import com.bloxbean.intelliada.idea.joust.editor.JoustFindReferencesAction;
import com.bloxbean.intelliada.idea.joust.editor.JoustGotoDeclarationHandler;
import com.bloxbean.intelliada.idea.joust.highlight.JoustHighlighterColors;
import com.bloxbean.intelliada.idea.joust.lsp.JoustServerControl;
import com.bloxbean.intelliada.idea.joust.lsp.JoustStreamConnectionProvider;
import com.bloxbean.intelliada.idea.joust.settings.JoustToolsState;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.LanguageServerManager;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.sun.net.httpserver.HttpServer;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ADR-004 M1 through the real entry points: a <b>hostile</b> fake joust-lsp (test resource {@code fake-joust-lsp}),
 * installed outside the project like a real one and started by LSP4IJ from IntelliAda's settings, tries every edit,
 * link and markup trick. The test observes what IntelliAda sends and answers (the fake server logs it), what the editor
 * shows, what navigation opens, how many processes the JVM starts, and whether anything fetched a loopback URL.
 * Needs {@code JOUST_NODE}; skipped otherwise. (M1-CDX-10/11/12/13/20, M1-DES-1/2/3/4, I1, I5, I15, I18, I20, I25.)
 */
public class JoustHostileServerPlatformTest extends BasePlatformTestCase {

    private Path base;
    private Path prefix;
    private Path log;
    private Path inside;
    private Path outside;
    private HttpServer loopback;
    private final AtomicInteger fetched = new AtomicInteger();
    private final List<Notification> notifications = new CopyOnWriteArrayList<>();
    private JoustToolsState.Data saved;
    private final List<VirtualFile> addedRoots = new ArrayList<>();
    private JoustProcessStarts processStarts;
    private Path tmp;
    private Path unopened;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        base = Files.createDirectories(Path.of(getProject().getBasePath())).toRealPath();
        processStarts = new JoustProcessStarts();
        tmp = Files.createTempDirectory("joust-hostile").toRealPath();
        prefix = tmp.resolve("prefix");
        Path pkg = Files.createDirectories(prefix.resolve("lib/node_modules/@julclang/joust/bin"));
        Files.write(pkg.getParent().resolve("package.json"), resource("fake-joust-lsp/package.json"));
        JoustTestFiles.executable(pkg.resolve("joust-lsp.js"), new String(resource("fake-joust-lsp/bin/joust-lsp.js"), StandardCharsets.UTF_8));
        Files.createDirectories(prefix.resolve("bin"));
        Files.createSymbolicLink(prefix.resolve("bin/joust-lsp"), pkg.resolve("joust-lsp.js"));
        log = pkg.getParent().resolve("log.jsonl");

        loopback = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        loopback.createContext("/", ex -> {
            fetched.incrementAndGet();
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        loopback.start();
        Files.writeString(pkg.getParent().resolve("port.txt"), String.valueOf(loopback.getAddress().getPort()));

        inside = base.resolve("mod1/contracts/Counter.jsc");
        Files.createDirectories(inside.getParent());
        Files.writeString(inside, "jsc 1\npackage app\ncontract Counter { state { count: Int } }\n");
        Files.createDirectories(base.resolve("libs/mod2"));
        outside = Files.writeString(tmp.resolve("outside.jsc"), "secret\n");
        Files.deleteIfExists(base.resolve("mod1/contracts/Link.jsc"));
        Path symlink = Files.createSymbolicLink(base.resolve("mod1/contracts/Link.jsc"), outside);
        Path external = Files.createDirectories(tmp.resolve("external-module"));
        // A file whose name is markup (not a valid Windows name: plain there, M1-CDX-20).
        Path markupName = base.resolve(JoustTestTools.windows() ? "mod1/contracts/Bmarkup.jsc" : "mod1/contracts/<b>B<img src=http:127.0.0.1:9>.jsc");
        Files.writeString(markupName, "jsc 1\n");
        unopened = Files.writeString(base.resolve("mod1/contracts/Unopened.jsc"), "jsc 1\n");
        Files.writeString(pkg.getParent().resolve("targets.json"), new JSONObject(Map.of("inside", inside.toUri().toString(),
                "outside", outside.toUri().toString(), "symlink", symlink.toUri().toString(),
                "markupName", markupName.toUri().toString(), "unopened", unopened.toUri().toString())).toString());

        // I25 topology: two in-base module roots, one external root; the base itself is not a content root.
        Module module = getModule();
        for (Path root : new Path[]{base.resolve("mod1"), base.resolve("libs/mod2"), external}) {
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root);
            PsiTestUtil.addContentRoot(module, vf);
            addedRoots.add(vf);
        }

        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(Notification n) {
                notifications.add(n);
            }
        });
        ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(Notification n) {
                notifications.add(n);
            }
        });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            JoustStreamConnectionProvider.setLaunchRecorderForTests(null);
            LanguageServerManager.getInstance(getProject()).stop(JoustConstants.SERVER_ID,
                    new LanguageServerManager.StopOptions().setWillDisable(false));
            if (saved != null) JoustToolsState.getInstance().loadState(saved);
            TrustedProjects.setProjectTrusted(getProject(), true);
            if (loopback != null) loopback.stop(0);
            for (VirtualFile r : addedRoots) PsiTestUtil.removeContentEntry(getModule(), r);
            if (processStarts != null) processStarts.close();
        } finally {
            super.tearDown();
        }
    }

    public void testEverythingTheHostileServerTriesIsRefused() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        if (JoustTestTools.windows()) {
            // The fake's npm layout is Unix (a bin symlink); Windows is covered by the manual runIde smoke test (M1-CDX-20).
            System.out.println("JOUST-SKIPPED " + getName() + ": Unix layout");
            return;
        }
        configure(node, prefix.resolve("bin/joust-lsp").toString());
        Set<Long> before = descendants();
        List<String> before1 = listing(base);

        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(inside);
        myFixture.openFileInEditor(vf);
        waitStatus(ServerStatus.started);
        Map<String, JSONObject> answers = waitForAnswers("register-formatting", "register-willRename", "applyEdit",
                "showDocument-external", "showDocument-outside", "showDocument-symlink", "workspaceFolders");

        // --- one process, at the JVM boundary (I1, M1-DES-4)
        Set<Long> started = new HashSet<>(descendants());
        started.removeAll(before);
        assertEquals("exactly one process started: " + describe(started), 1, started.size());
        long pid = started.iterator().next();
        JSONObject startedEvent = events("started").get(0);
        assertEquals(pid, startedEvent.getLong("pid"));
        assertEquals(List.of("--stdio", "--storage", JoustStreamConnectionProvider.storageDir().toRealPath().toString()),
                startedEvent.getJSONArray("argv").toList());

        // --- what IntelliAda sent at initialize (M1-DES-2, I5, I25)
        JSONObject init = received("initialize").get(0).getJSONObject("params");
        assertEquals(Map.of("client", "intelliada", "buildOnSave", false), init.getJSONObject("initializationOptions").toMap());
        JSONObject ws = init.getJSONObject("capabilities").getJSONObject("workspace");
        assertFalse(ws.optBoolean("applyEdit", false));
        assertFalse(ws.has("fileOperations"));
        assertFalse(ws.optJSONObject("workspaceEdit") != null && ws.getJSONObject("workspaceEdit").has("failureHandling"));
        Set<Path> expected = Set.of(base.resolve("mod1"), base.resolve("libs/mod2"));
        assertEquals("only the in-base top-level roots (I25)", expected, folders(init.getJSONArray("workspaceFolders")));
        assertEquals("workspace/workspaceFolders answers the same", expected,
                folders(answers.get("workspaceFolders").getJSONArray("result")));

        // --- server-to-client requests (I5, D3a items 2-4)
        assertEquals(-32600, answers.get("register-formatting").getJSONObject("error").getInt("code"));
        assertEquals(-32600, answers.get("register-willRename").getJSONObject("error").getInt("code"));
        assertFalse(answers.get("applyEdit").getJSONObject("result").getBoolean("applied"));
        for (String t : new String[]{"showDocument-external", "showDocument-outside", "showDocument-symlink"}) {
            assertFalse(t, answers.get(t).getJSONObject("result").getBoolean("success"));
        }
        assertEquals("jsc 1\npackage app\ncontract Counter { state { count: Int } }\n", Files.readString(inside));
        assertFalse(java.util.Arrays.stream(FileEditorManager.getInstance(getProject()).getOpenFiles())
                .anyMatch(f -> f.getName().equals("outside.jsc") || f.getName().equals("Link.jsc")));

        // --- the server's editing capabilities never reach LSP4IJ (M1-DES-1)
        var item = LanguageServerManager.getInstance(getProject()).getLanguageServer(JoustConstants.SERVER_ID).get(30, TimeUnit.SECONDS);
        var caps = item.getServerCapabilities();
        assertNull(caps.getCompletionProvider());
        assertNull(caps.getRenameProvider());
        assertNull(caps.getDocumentFormattingProvider());
        assertNull(caps.getCodeActionProvider());
        assertNull(caps.getExecuteCommandProvider());
        assertTrue(caps.getWorkspace() == null || caps.getWorkspace().getFileOperations() == null);

        // --- diagnostics and semantic tokens in the editor (D3a item 8, D2, M1-CDX-11/20)
        List<HighlightInfo> infos = waitForHighlights();
        HighlightInfo diag = infos.stream().filter(i -> i.getDescription() != null && i.getDescription().startsWith("List<T>")).findFirst()
                .orElseThrow(() -> new AssertionError("no diagnostic in " + infos));
        assertEquals("the Problems text is plain", "List<T> is bad <img src=\"http://127.0.0.1:" + loopback.getAddress().getPort()
                + "/diag\">", diag.getDescription());
        String tooltip = diag.getToolTip();
        assertNotNull(tooltip);
        String body = tooltip.replace("<html>", "").replace("</html>", "").replace("<br>", "");
        assertFalse(tooltip, body.contains("<"));
        assertTrue(infos.stream().anyMatch(i -> JoustHighlighterColors.STATE_FIELD.equals(i.forcedTextAttributesKey)
                || JoustHighlighterColors.STATE_FIELD.equals(i.type.getAttributesKey())));

        // --- hover through the registered provider (D3a item 6)
        PsiFile psi = ReadAction.compute(() -> PsiManager.getInstance(getProject()).findFile(vf));
        var provider = DocumentationTargetProvider.EP_NAME.getExtensionList().stream()
                .filter(p -> p instanceof JoustDocumentationTargetProvider).findFirst().orElseThrow();
        var target = (JoustDocumentationTargetProvider.HoverTarget) ReadAction.compute(() -> provider.documentationTargets(psi, 10)).get(0);
        String html = ApplicationManager.getApplication().executeOnPooledThread(target::computeHtml).get(30, TimeUnit.SECONDS);
        assertTrue(html, html.startsWith("<pre>") && !html.substring(5).contains("<img") && !html.contains("<a "));

        // --- navigation through the registered handler and the action's search (D3a item 7, I18)
        var gtd = GotoDeclarationHandler.EP_NAME.getExtensionList().stream()
                .filter(h -> h instanceof JoustGotoDeclarationHandler).findFirst().orElseThrow();
        PsiElement[] targets = ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(
                () -> gtd.getGotoDeclarationTargets(psi.findElementAt(10), 10, myFixture.getEditor()))).get(30, TimeUnit.SECONDS);
        assertNotNull(targets);
        assertEquals("only the two in-project targets", 2, targets.length);
        assertEquals("Counter.jsc", targets[0].getContainingFile().getName());
        // The Ctrl+hover hint of a target whose name is markup is escaped (M1-SEC-3).
        for (PsiElement t : targets) {
            var docTarget = com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider.EP_NAME.getExtensionList().stream()
                    .map(p -> ReadAction.compute(() -> p.documentationTarget(t, null))).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
            String hint = ReadAction.compute(docTarget::computeDocumentationHint);
            assertFalse(hint, hint.contains("<b>") || hint.contains("<img"));
        }
        var hits = ApplicationManager.getApplication().executeOnPooledThread(
                () -> JoustFindReferencesAction.findHits(getProject(), vf, new Position(0, 1))).get(30, TimeUnit.SECONDS);
        assertEquals(2, hits.size());
        assertEquals("Counter.jsc", hits.get(0).file().getName());

        // --- symbols and progress through the installed features (I15, M1-CDX-13)
        var features = item.getClientFeatures();
        DocumentSymbol sym = new DocumentSymbol("\u001b[31m<html>Counter" + "x".repeat(5000), org.eclipse.lsp4j.SymbolKind.Class,
                new Range(new Position(0, 0), new Position(0, 3)), new Range(new Position(0, 0), new Position(0, 3)), "<b>d</b>");
        String shown = features.getDocumentSymbolFeature().getPresentableText(sym, psi);
        assertTrue(shown.length() <= 210 && !shown.contains("\u001b"));
        assertFalse(features.getProgressFeature().getProgressTaskTitle("\u001b[31m<html><b>T</b>").contains("<"));

        // --- messages, and the error LSP4IJ shows for a request it issued itself (I15, M1-SEC-1)
        PlatformTestUtil.waitWithEventsDispatching("the showMessage notification", () -> notifications.stream()
                .anyMatch(n -> n.getContent().contains("boom")), 10);
        for (int i = 0; i < 40 && notifications.stream().noneMatch(n -> n.getContent().contains("fold")); i++) {
            com.intellij.codeInsight.folding.CodeFoldingManager.getInstance(getProject()).buildInitialFoldings(myFixture.getEditor());
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(250);
        }
        assertTrue("LSP4IJ showed the folding error (escaped): " + received("textDocument/foldingRange").size(),
                notifications.stream().anyMatch(n -> n.getContent().contains("fold")));
        for (Notification n : notifications) {
            assertFalse(n.getContent(), n.getContent().contains("<img") || n.getContent().contains("<a "));
            assertTrue(n.getContent().length() < 4000);
        }

        // --- a progress message that starts with <html> is literal (M1-DES-7)
        var ind = new com.intellij.openapi.progress.util.ProgressIndicatorBase();
        org.eclipse.lsp4j.WorkDoneProgressBegin begin = new org.eclipse.lsp4j.WorkDoneProgressBegin();
        begin.setTitle("t");
        begin.setMessage("<html><img src=\"http://127.0.0.1:" + loopback.getAddress().getPort() + "/p\">");
        features.getProgressFeature().onProgressBegin(begin, ind);
        assertFalse(String.valueOf(ind.getText2()), String.valueOf(ind.getText2()).contains("<"));

        // --- joust/status arrives through LSP4IJ, as labels (M1-DES-12)
        var status = com.bloxbean.intelliada.idea.joust.lsp.JoustStatusCache.getInstance(getProject()).forUri(inside.toUri().toString())
                != null ? com.bloxbean.intelliada.idea.joust.lsp.JoustStatusCache.getInstance(getProject()).forUri(inside.toUri().toString())
                : com.bloxbean.intelliada.idea.joust.lsp.JoustStatusCache.getInstance(getProject()).forUri(
                        com.redhat.devtools.lsp4ij.LSPIJUtils.toUriAsString(vf));
        assertNotNull("joust/status reached the cache", status);
        assertEquals("0.0.0-hostile", status.compiler());
        assertFalse(status.server().contains("<"));

        // --- the user-visible actions (M1-CDX-11): navigate, choose a row, open documentation, render hover
        for (PsiElement t : targets) {
            ApplicationManager.getApplication().invokeAndWait(() -> ((com.intellij.pom.Navigatable) t).navigate(true));
            VirtualFile shownFile = FileEditorManager.getInstance(getProject()).getSelectedFiles()[0];
            assertTrue(shownFile.getPath(), com.bloxbean.intelliada.idea.joust.core.JoustRealPaths.isInside(shownFile.toNioPath().toRealPath(), base));
        }
        for (var h : hits) {
            ApplicationManager.getApplication().invokeAndWait(() -> assertTrue(JoustFindReferencesAction.choose(getProject(), h)));
        }
        List<String> browsed = new CopyOnWriteArrayList<>();
        com.intellij.testFramework.ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), com.intellij.ide.browsers.BrowserLauncher.class,
                new com.intellij.ide.browsers.BrowserLauncher() {
                    @Override public void open(String url) { browsed.add(url); }
                    @Override public void browse(String url, com.intellij.ide.browsers.WebBrowser b, Project p) { browsed.add(url); }
                    @Override public void browse(java.io.File file) { browsed.add(file.toString()); }
                    @Override public void browse(java.nio.file.Path file) { browsed.add(file.toString()); }
                }, getTestRootDisposable());
        var docFix = diag.findRegisteredQuickFix((descriptor, range) -> descriptor.getAction().getText().startsWith("Open JOUST documentation")
                ? descriptor.getAction() : null);
        assertNotNull("Open JOUST documentation is offered for JSC0502", docFix);
        ApplicationManager.getApplication().invokeAndWait(() -> docFix.invoke(getProject(), myFixture.getEditor(), psi));
        assertEquals(List.of(JoustConstants.DIAGNOSTICS_CATALOGUE + "#:~:text=JSC0502,-error"), browsed);
        ApplicationManager.getApplication().invokeAndWait(() -> {
            javax.swing.JEditorPane pane = new javax.swing.JEditorPane("text/html", html);
            pane.setSize(400, 400);
            pane.getPreferredSize();
        });

        // --- a module added during the session is seen; an external one is not; no folder-change notification (I25)
        Path mod3 = Files.createDirectories(base.resolve("mod3"));
        Path external2 = Files.createDirectories(tmp.resolve("external-2"));
        for (Path root : new Path[]{mod3, external2}) {
            VirtualFile rv = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root);
            PsiTestUtil.addContentRoot(getModule(), rv);
            addedRoots.add(rv);
        }
        Path second = Files.writeString(mod3.resolve("Second.jsc"), "jsc 1\npackage app\ncontract Second { state { n: Int } }\nfn x() {}\n");
        String tag = "workspaceFolders-" + (received("textDocument/didOpen").size() + 1);
        myFixture.openFileInEditor(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(second));
        Map<String, JSONObject> later = waitForAnswers(tag);
        assertEquals(Set.of(base.resolve("mod1"), base.resolve("libs/mod2"), mod3), folders(later.get(tag).getJSONArray("result")));
        assertTrue("no didChangeWorkspaceFolders (pinned LSP4IJ)", received("workspace/didChangeWorkspaceFolders").isEmpty());

        // --- the second folding answer (for Second.jsc) succeeds; its markup placeholder is literal text (M1-CDX-13)
        for (int i = 0; i < 40; i++) {
            ApplicationManager.getApplication().invokeAndWait(() -> com.intellij.codeInsight.folding.CodeFoldingManager.getInstance(getProject())
                    .updateFoldRegions(myFixture.getEditor()));
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            if (myFixture.getEditor().getFoldingModel().getAllFoldRegions().length > 0) break;
            Thread.sleep(250);
        }
        var regions = myFixture.getEditor().getFoldingModel().getAllFoldRegions();
        assertTrue("a folding region from the server; requests: " + received("textDocument/foldingRange").size(), regions.length > 0);
        for (var r : regions) {
            assertFalse(r.getPlaceholderText(), r.getPlaceholderText().contains("<"));
            assertTrue(r.getPlaceholderText().length() <= 210);
        }


        // --- diagnostics for a document the server never had opened are dropped (M1-CDX-24)
        myFixture.openFileInEditor(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(unopened));
        Thread.sleep(1000);
        assertTrue(myFixture.doHighlighting().stream().noneMatch(i -> i.getDescription() != null && i.getDescription().contains("UNSOLICITED")));

        // --- every process the JVM started during the test (JFR, M1-CDX-10)
        List<String> commands = processStarts.commands();
        assertEquals("only the fake joust-lsp started: " + commands, 1, commands.size());
        assertTrue(commands.get(0), commands.get(0).contains("joust-lsp.js"));

        // --- nothing anywhere fetched a URL (I15)
        Thread.sleep(500);
        assertEquals("loopback requests", 0, fetched.get());
        assertEquals("the project's files are unchanged, apart from the module this test added (I21)", before1,
                listing(base).stream().filter(e -> !e.startsWith("mod3")).toList());
    }

    public void testRestartStopAndUserChoice() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        configure(node, prefix.resolve("bin/joust-lsp").toString());
        TrustedProjects.setProjectTrusted(getProject(), true);
        Set<Long> before = descendants();
        myFixture.openFileInEditor(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(inside));
        waitStatus(ServerStatus.started);
        long first = newDescendant(before);

        // A real trust change restarts it: the old process exits, a new one starts (I4, I20).
        TrustedProjects.setProjectTrusted(getProject(), false);
        PlatformTestUtil.waitWithEventsDispatching("the old process exits", () -> !ProcessHandle.of(first).map(ProcessHandle::isAlive).orElse(false), 30);
        waitStatus(ServerStatus.started);
        long second = newDescendant(before);
        assertTrue(second != first);
        TrustedProjects.setProjectTrusted(getProject(), true);
        PlatformTestUtil.waitWithEventsDispatching("restarted again", () -> !ProcessHandle.of(second).map(ProcessHandle::isAlive).orElse(false), 30);
        waitStatus(ServerStatus.started);

        // stopAll (a conflict) stops without disabling (M1-CDX-6, M1-DES-5).
        JoustServerControl.stopAll("test");
        waitStatus(ServerStatus.stopped);
        assertTrue(LanguageServersRegistry.getInstance().getServerDefinition(JoustConstants.SERVER_ID).isEnabled(getProject()));

        // A server the user disabled stays down through a trust change (M1-CDX-6).
        LanguageServerManager.getInstance(getProject()).start(JoustConstants.SERVER_ID);
        waitStatus(ServerStatus.started);
        LanguageServerManager.getInstance(getProject()).stop(JoustConstants.SERVER_ID, new LanguageServerManager.StopOptions().setWillDisable(true));
        waitStatus(ServerStatus.stopped);
        Set<Long> beforeTrust = descendants();
        TrustedProjects.setProjectTrusted(getProject(), false);
        TrustedProjects.setProjectTrusted(getProject(), true);
        Thread.sleep(3000);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        Set<Long> now = descendants();
        now.removeAll(beforeTrust);
        assertTrue("no process for a disabled server: " + describe(now), now.isEmpty());
        assertNotSame(ServerStatus.started, JoustServerControl.status(getProject()));
        LanguageServerManager.getInstance(getProject()).start(JoustConstants.SERVER_ID, new LanguageServerManager.StartOptions().setWillEnable(true));
    }

    /** I20 (M1-DES-3): closing a project stops the process IntelliAda started for it. */
    public void testClosingTheProjectStopsItsServer() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        configure(node, prefix.resolve("bin/joust-lsp").toString());
        Path dir = Files.createTempDirectory("joust-close").toRealPath();
        Path jsc = Files.createDirectories(dir.resolve("contracts")).resolve("Counter.jsc");
        Files.writeString(jsc, "jsc 1\n");
        Set<Long> before = descendants();
        Project other = PlatformTestUtil.loadAndOpenProject(dir, getTestRootDisposable());
        try {
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(jsc);
            ApplicationManager.getApplication().invokeAndWait(() -> FileEditorManager.getInstance(other).openFile(vf, true));
            PlatformTestUtil.waitWithEventsDispatching("started in the other project",
                    () -> JoustServerControl.status(other) == ServerStatus.started, 60);
            long pid = newDescendant(before);
            PlatformTestUtil.forceCloseProjectWithoutSaving(other);
            PlatformTestUtil.waitWithEventsDispatching("its process exits on close",
                    () -> !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false), 30);
        } finally {
            if (!other.isDisposed()) PlatformTestUtil.forceCloseProjectWithoutSaving(other);
        }
    }

    public void testARefusedStartShowsOnlyEscapedText() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        configure(node, prefix.resolve("bin/joust-lsp").toString());
        JoustToolsState.getInstance().getState().joustLspPath = "/nonexistent/<b>evil</b>/joust-lsp";
        Set<Long> before = descendants();
        myFixture.openFileInEditor(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(inside));
        PlatformTestUtil.waitWithEventsDispatching("a notification", () -> !notifications.isEmpty(), 30);
        Thread.sleep(1000);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertFalse(notifications.isEmpty());
        for (Notification n : notifications) {
            assertFalse(n.getTitle() + ": " + n.getContent(), (n.getTitle() + n.getContent()).contains("<b>"));
        }
        Set<Long> now = descendants();
        now.removeAll(before);
        assertTrue("nothing started", now.isEmpty());
    }

    // ------------------------------------------------------------------ helpers

    /** M1-CDX-7: a joust-lsp that passes validation but exits at once, under a path with markup, shows only escaped text. */
    public void testAnEarlyExitShowsOnlyEscapedText() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null || JoustTestTools.windows()) return;
        Path pkg = Files.createDirectories(tmp.resolve("<b>pre</b>/lib/node_modules/@julclang/joust/bin"));
        Files.write(pkg.getParent().resolve("package.json"), resource("fake-joust-lsp/package.json"));
        JoustTestFiles.executable(pkg.resolve("joust-lsp.js"), "#!/usr/bin/env node\nprocess.exit(3)\n");
        configure(node, pkg.resolve("joust-lsp.js").toString());
        myFixture.openFileInEditor(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(inside));
        PlatformTestUtil.waitWithEventsDispatching("a notification", () -> notifications.stream()
                .anyMatch(n -> n.getContent().contains("exited early") || n.getContent().contains("did not start")), 60);
        Thread.sleep(1000);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        for (Notification n : notifications) {
            assertFalse(n.getTitle() + ": " + n.getContent(), (n.getTitle() + n.getContent()).contains("<b>"));
        }
    }

    /** M1-CDX-14: after Node is upgraded in place, Settings offers Apply, and Apply records it again. */
    public void testSettingsRecoverAfterAnInPlaceNodeUpgrade() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        Path copy = tmp.resolve("node-install/bin/node");
        Files.createDirectories(copy.getParent());
        Files.copy(Path.of(node).toRealPath(), copy);
        copy.toFile().setExecutable(true);
        configure(copy.toString(), prefix.resolve("bin/joust-lsp").toString());
        var configurable = new com.bloxbean.intelliada.idea.joust.settings.JoustToolsConfigurable();
        ApplicationManager.getApplication().invokeAndWait(() -> {
            configurable.createComponent();
            configurable.reset();
            assertFalse(configurable.isModified());
        });
        Files.setLastModifiedTime(copy, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        var planBefore = com.bloxbean.intelliada.idea.joust.core.JoustLaunchPlanner.plan(JoustToolsState.getInstance().settings(),
                List.of(base), JoustStreamConnectionProvider.storageDir(), com.bloxbean.intelliada.idea.joust.core.JoustPaths.Flavor.current());
        assertFalse("the upgraded Node is refused until validated", planBefore.ok());
        ApplicationManager.getApplication().invokeAndWait(() -> {
            assertTrue("Settings offers Apply", configurable.isModified());
            try {
                configurable.apply();
            } catch (com.intellij.openapi.options.ConfigurationException e) {
                throw new AssertionError(e);
            }
            assertFalse(configurable.isModified());
            configurable.disposeUIResources();
        });
        var planAfter = com.bloxbean.intelliada.idea.joust.core.JoustLaunchPlanner.plan(JoustToolsState.getInstance().settings(),
                List.of(base), JoustStreamConnectionProvider.storageDir(), com.bloxbean.intelliada.idea.joust.core.JoustPaths.Flavor.current());
        assertTrue(planAfter.error(), planAfter.ok());
    }

    private void configure(String node, String lsp) throws Exception {
        JoustToolsState state = JoustToolsState.getInstance();
        saved = state.getState();
        JoustToolsState.Data d = new JoustToolsState.Data();
        d.nodePath = node;
        d.joustLspPath = lsp;
        state.loadState(d);
        state.setValidatedNode(JoustToolValidator.identityOf(Path.of(node).toRealPath()), "test");
    }

    private void waitStatus(ServerStatus s) {
        PlatformTestUtil.waitWithEventsDispatching("server " + s, () -> JoustServerControl.status(getProject()) == s, 60);
    }

    private static Set<Long> descendants() {
        return ProcessHandle.current().descendants().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).collect(Collectors.toSet());
    }

    private static long newDescendant(Set<Long> before) {
        Set<Long> s = descendants();
        s.removeAll(before);
        assertEquals("one JOUST process: " + describe(s), 1, s.size());
        return s.iterator().next();
    }

    private static String describe(Set<Long> pids) {
        return pids.stream().map(p -> p + "=" + ProcessHandle.of(p).flatMap(h -> h.info().commandLine()).orElse("?")).toList().toString();
    }

    private List<JSONObject> events(String event) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        if (!Files.exists(log)) return out;
        for (String line : Files.readAllLines(log)) {
            JSONObject o = new JSONObject(line);
            if (event.equals(o.optString("event"))) out.add(o);
        }
        return out;
    }

    private List<JSONObject> received(String method) throws Exception {
        return events("received").stream().filter(o -> method.equals(o.optString("method"))).toList();
    }

    private Map<String, JSONObject> waitForAnswers(String... tags) throws Exception {
        Map<String, JSONObject> got = new HashMap<>();
        long end = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < end) {
            got.clear();
            for (JSONObject a : events("answer")) got.put(a.getString("to"), a);
            if (got.keySet().containsAll(List.of(tags))) return got;
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(100);
        }
        fail("the client did not answer " + List.of(tags) + "; got " + got.keySet());
        return got;
    }

    private List<HighlightInfo> waitForHighlights() throws Exception {
        long end = System.currentTimeMillis() + 60_000;
        List<HighlightInfo> infos = List.of();
        while (System.currentTimeMillis() < end) {
            infos = myFixture.doHighlighting();
            boolean diag = infos.stream().anyMatch(i -> i.getDescription() != null && i.getDescription().startsWith("List<T>"));
            boolean token = infos.stream().anyMatch(i -> JoustHighlighterColors.STATE_FIELD.equals(i.forcedTextAttributesKey)
                    || JoustHighlighterColors.STATE_FIELD.equals(i.type.getAttributesKey()));
            if (diag && token) return infos;
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(200);
        }
        return infos;
    }

    private static Set<Path> folders(JSONArray a) throws Exception {
        Set<Path> out = new HashSet<>();
        for (int i = 0; i < a.length(); i++) out.add(Path.of(java.net.URI.create(a.getJSONObject(i).getString("uri"))).toRealPath());
        return out;
    }

    private static List<String> listing(Path dir) throws Exception {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).map(p -> {
                try {
                    return dir.relativize(p) + "=" + JoustToolValidator.sha256(p);
                } catch (Exception e) {
                    return dir.relativize(p) + "=?";
                }
            }).sorted().toList();
        }
    }

    private static byte[] resource(String name) throws Exception {
        try (var in = JoustHostileServerPlatformTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(name, in);
            return in.readAllBytes();
        }
    }
}
