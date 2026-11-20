package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.JoustLaunch;
import com.bloxbean.intelliada.idea.joust.core.JoustLaunchPlanner;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.core.NodeVersionProbe;
import com.bloxbean.intelliada.idea.joust.lsp.JoustLanguageServerApi;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * ADR-004 M1 integration test with the real, published {@code joust-lsp}, started with exactly I3's command line as
 * {@link JoustLaunchPlanner} builds it.
 *
 * <ul>
 *   <li><b>I5 (real server):</b> a client that <i>does</i> declare {@code applyEdit}, {@code documentChanges} and
 *   {@code failureHandling: transactional}, with IntelliAda's {@code client: "intelliada"}, gets no completion, code
 *   action, {@code executeCommand}, rename or formatting provider, and no {@code workspace/applyEdit}.</li>
 *   <li><b>Features:</b> diagnostics carry {@code JSC} codes; hover, definition, references and document symbols answer;
 *   {@code joust/status} reports the bundled compiler in advisory mode.</li>
 *   <li><b>I1 sentinels:</b> a project with a planted engine (both at {@code engine/} and in
 *   {@code node_modules/@julclang/joust/engine/}), {@code node_modules/.bin/joust-lsp}, a {@code node}, a
 *   {@code gradlew} and npm scripts, each writing a sentinel when run or loaded. None is written.</li>
 * </ul>
 * Runs when {@code JOUST_NODE} and {@code JOUST_LSP} name an installed Node 22+ and {@code joust-lsp}; skipped otherwise.
 */
public class JoustLspIntegrationTest {

    private static final String COUNTER = """
            jsc 1
            package app

            contract Counter(maxDatumBytes = 128) {
              state { count: Int, owner: Key }

              init() {
                return { count: 0, owner: caller }
              }

              action increment(by: Int) {
                require(by > 0, "increment must be positive")
                return { ...state, count: count + by }
              }

              action reset() only owner {
                return { ...state, count: 0 }
              }

              view current(): Int = count
            }
            """;

    private static final String BROKEN = COUNTER.replace("Counter", "Broken").replace("count + by", "count + missingName");

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static String nodePath;
    private static String lspPath;

    private Path project;
    private List<String> beforeLaunch;
    private Process process;
    private RecordingClient client;
    private JoustLanguageServerApi server;

    @BeforeClass
    public static void tools() {
        nodePath = JoustTestTools.env("JOUST_NODE", "JoustLspIntegrationTest");
        lspPath = JoustTestTools.env("JOUST_LSP", "JoustLspIntegrationTest");
        Assume.assumeTrue("set JOUST_NODE and JOUST_LSP to run against a real joust-lsp", nodePath != null && lspPath != null);
    }

    @Before
    public void start() throws Exception {
        Path base = tmp.getRoot().toPath().toRealPath();
        project = Files.createDirectories(base.resolve("repo"));
        plant(project);
        Files.createDirectories(project.resolve("contracts/app"));
        Files.writeString(project.resolve("contracts/app/Counter.jsc"), COUNTER);
        Files.writeString(project.resolve("contracts/app/Broken.jsc"), BROKEN);
        beforeLaunch = snapshot();

        JoustPaths.Flavor flavor = JoustPaths.Flavor.current();
        Path realNode = JoustToolValidator.checkNode(nodePath, List.of(project), flavor).realPath();
        int probes = NodeVersionProbe.invocations();
        var plan = JoustLaunchPlanner.plan(new JoustLaunchPlanner.Settings(nodePath, lspPath, JoustToolValidator.identityOf(realNode)),
                List.of(project), base.resolve("system/intelliada/joust-lsp"), flavor);
        assertTrue(plan.error(), plan.ok());
        assertEquals(probes, NodeVersionProbe.invocations());
        JoustLaunch launch = plan.launch();
        assertEquals(5, launch.command().size());
        assertEquals(List.of("--stdio", "--storage"), launch.command().subList(2, 4));

        ProcessBuilder pb = new ProcessBuilder(launch.command()).directory(launch.workingDirectory().toFile());
        for (String k : JoustLaunch.REMOVED_ENVIRONMENT) pb.environment().remove(k);
        String trace = System.getenv("JOUST_LSP_TRACE_DIR");
        pb.redirectError(trace == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.to(new java.io.File(trace, "stderr.txt")));
        process = pb.start();
        client = new RecordingClient();
        Launcher<JoustLanguageServerApi> launcher = new LSPLauncher.Builder<JoustLanguageServerApi>()
                .setLocalService(client).setRemoteInterface(JoustLanguageServerApi.class)
                .setInput(trace == null ? process.getInputStream() : new TeeInput(process.getInputStream(),
                        new java.io.FileOutputStream(new java.io.File(trace, "stdout.raw"))))
                .setOutput(process.getOutputStream())
                .traceMessages(trace == null ? null : new java.io.PrintWriter(new java.io.FileWriter(new java.io.File(trace, "trace.txt")), true))
                .create();
        launcher.startListening();
        server = launcher.getRemoteProxy();

        InitializeParams init = new InitializeParams();
        init.setProcessId((int) ProcessHandle.current().pid());
        init.setWorkspaceFolders(List.of(new WorkspaceFolder(project.toUri().toString(), "repo")));
        init.setInitializationOptions(Map.of("client", "intelliada", "buildOnSave", false));
        ClientCapabilities caps = new ClientCapabilities();
        WorkspaceClientCapabilities ws = new WorkspaceClientCapabilities();
        ws.setApplyEdit(true);
        WorkspaceEditCapabilities we = new WorkspaceEditCapabilities();
        we.setDocumentChanges(true);
        we.setFailureHandling(FailureHandlingKind.Transactional);
        ws.setWorkspaceEdit(we);
        caps.setWorkspace(ws);
        caps.setTextDocument(new TextDocumentClientCapabilities());
        init.setCapabilities(caps);
        InitializeResult result = server.initialize(init).get(60, TimeUnit.SECONDS);
        server.initialized(new InitializedParams());
        client.capabilities = result.getCapabilities();
    }

    @After
    public void stop() throws Exception {
        if (server != null) {
            try {
                server.shutdown().get(10, TimeUnit.SECONDS);
                server.exit();
            } catch (Exception ignored) {
                // the process is destroyed below
            }
        }
        if (process != null) {
            process.waitFor(5, TimeUnit.SECONDS);
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            // I21 (M1-CDX-10): from before the launch to after the shutdown, no project file changed.
            assertEquals(beforeLaunch, snapshot());
        }
    }

    @Test
    public void readOnlyEvenForAClientDeclaringTransactionalEdits() throws Exception {
        ServerCapabilities c = client.capabilities;
        assertNull("completion", c.getCompletionProvider());
        assertNull("executeCommand", c.getExecuteCommandProvider());
        assertTrue("code actions", c.getCodeActionProvider() == null || (c.getCodeActionProvider().isLeft() && !c.getCodeActionProvider().getLeft()));
        assertTrue("rename", c.getRenameProvider() == null || (c.getRenameProvider().isLeft() && !c.getRenameProvider().getLeft()));
        assertTrue("formatting", c.getDocumentFormattingProvider() == null || (c.getDocumentFormattingProvider().isLeft() && !c.getDocumentFormattingProvider().getLeft()));
        assertTrue("range formatting", c.getDocumentRangeFormattingProvider() == null
                || (c.getDocumentRangeFormattingProvider().isLeft() && !c.getDocumentRangeFormattingProvider().getLeft()));
        assertNull("on-type formatting", c.getDocumentOnTypeFormattingProvider());
        assertNotNull(c.getHoverProvider());
        assertNotNull(c.getDefinitionProvider());
        assertNotNull(c.getReferencesProvider());
        assertNotNull(c.getDocumentSymbolProvider());

        open("Counter.jsc", COUNTER);
        open("Broken.jsc", BROKEN);
        client.diagnosticsFor(uri("Broken.jsc"), 90);
        assertTrue("the server asked for no edit: " + client.applyEdits, client.applyEdits.isEmpty());
        assertNoSentinels();
    }

    @Test
    public void diagnosticsHoverNavigationAndStatus() throws Exception {
        open("Counter.jsc", COUNTER);
        open("Broken.jsc", BROKEN);
        List<Diagnostic> broken = client.diagnosticsFor(uri("Broken.jsc"), 90);
        assertFalse("the broken copy has diagnostics", broken.isEmpty());
        assertTrue("JSC codes: " + broken, broken.stream().anyMatch(d -> d.getCode() != null && d.getCode().isLeft()
                && d.getCode().getLeft().matches("JSC[0-9]{4}")));
        List<Diagnostic> ok = client.diagnosticsFor(uri("Counter.jsc"), 90);
        assertTrue("no errors in the template: " + ok, ok.stream().noneMatch(d -> d.getSeverity() == DiagnosticSeverity.Error));

        Position usage = positionOf(COUNTER, "count + by");
        long t0 = System.nanoTime();
        Hover hover = server.getTextDocumentService().hover(new HoverParams(new TextDocumentIdentifier(uri("Counter.jsc")), usage))
                .get(30, TimeUnit.SECONDS);
        System.out.println("JOUST hover took ms: " + (System.nanoTime() - t0) / 1_000_000 + " -> " + hover);
        assertNotNull("hover answers", hover);

        var def = server.getTextDocumentService().definition(new DefinitionParams(new TextDocumentIdentifier(uri("Counter.jsc")), usage))
                .get(30, TimeUnit.SECONDS);
        List<Location> defs = new ArrayList<>();
        if (def != null && def.isLeft()) defs.addAll(def.getLeft());
        if (def != null && def.isRight()) def.getRight().forEach(l -> defs.add(new Location(l.getTargetUri(), l.getTargetSelectionRange())));
        assertFalse("definition answers", defs.isEmpty());
        assertEquals(uri("Counter.jsc"), defs.get(0).getUri());

        var refs = server.getTextDocumentService().references(new ReferenceParams(new TextDocumentIdentifier(uri("Counter.jsc")),
                usage, new ReferenceContext(true))).get(30, TimeUnit.SECONDS);
        assertNotNull(refs);
        assertTrue("references answer: " + refs, refs.size() >= 2);

        var symbols = server.getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(uri("Counter.jsc"))))
                .get(30, TimeUnit.SECONDS);
        assertFalse(symbols.isEmpty());
        assertTrue(symbols.toString(), symbols.toString().contains("Counter"));

        Map<String, Object> status = client.statuses.get(uri("Counter.jsc"));
        assertNotNull("joust/status was pushed", status);
        assertEquals(status.toString(), "advisory", status.get("mode"));
        assertEquals(status.toString(), "bundled", status.get("source"));
        assertNoSentinels();
    }

    @Test
    public void readOnlyCommandsWriteNothing() throws Exception {
        open("Counter.jsc", COUNTER);
        client.diagnosticsFor(uri("Counter.jsc"), 90);
        List<String> before = snapshot();
        Map<String, Object> g = server.joustGeneratedJava(Map.of("uri", uri("Counter.jsc"), "position", new Position(10, 0))).get(60, TimeUnit.SECONDS);
        assertTrue(g.toString(), g.containsKey("id") || g.containsKey("error"));
        if (g.containsKey("id")) {
            Map<String, Object> c = server.joustGeneratedContent(Map.of("id", (String) g.get("id"))).get(30, TimeUnit.SECONDS);
            assertTrue(c.toString(), c.get("text") instanceof String);
            assertTrue(((String) c.get("text")).contains("Counter"));
        }
        Map<String, Object> b = server.joustCheckBuild(Map.of("uri", uri("Counter.jsc"))).get(120, TimeUnit.SECONDS);
        assertTrue(b.toString(), b.containsKey("ok") || b.containsKey("error"));
        Map<String, Object> r = server.joustRestartAnalysis(Map.of()).get(30, TimeUnit.SECONDS);
        assertTrue(r.toString(), Boolean.TRUE.equals(r.get("restarted")));
        client.diagnosticsFor(uri("Counter.jsc"), 90);
        assertEquals("I21: the project's files are unchanged", before, snapshot());
        assertTrue(client.applyEdits.isEmpty());
        assertNoSentinels();
    }

    // ------------------------------------------------------------------ helpers

    private void open(String name, String text) throws IOException {
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri(name), "jsc", 1, text)));
    }

    private String uri(String name) {
        return project.resolve("contracts/app/" + name).toUri().toString();
    }

    private static Position positionOf(String text, String needle) {
        int at = text.indexOf(needle);
        String before = text.substring(0, at);
        int line = (int) before.chars().filter(ch -> ch == '\n').count();
        int col = at - before.lastIndexOf('\n') - 1;
        return new Position(line, col + 1);
    }

    private void assertNoSentinels() throws IOException {
        try (Stream<Path> s = Files.walk(project.getParent())) {
            List<Path> found = s.filter(p -> p.getFileName().toString().startsWith("SENTINEL")).toList();
            assertTrue("I1: project code ran: " + found, found.isEmpty());
        }
    }

    private List<String> snapshot() throws IOException {
        try (Stream<Path> s = Files.walk(project)) {
            return s.filter(Files::isRegularFile).map(p -> {
                try {
                    return project.relativize(p) + "=" + JoustToolValidator.sha256(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).sorted().toList();
        }
    }

    /** A project that writes a sentinel if IntelliAda or the server ever runs or loads anything from it. */
    private static void plant(Path p) throws IOException {
        Path root = p.toRealPath();
        String engineJs = "const P='" + root.resolve("SENTINEL-engine") + "';"
                + "try{require('fs').writeFileSync(P,'x')}catch(e){import('node:fs').then(f=>f.writeFileSync(P,'x'))}\n";
        Files.createDirectories(root.resolve("contracts/app"));
        Files.writeString(root.resolve("joust.config.json"), "{\"contracts\":\"contracts\"}");
        Files.writeString(root.resolve("package.json"), "{\"name\":\"planted\",\"version\":\"1.0.0\",\"type\":\"module\","
                + "\"scripts\":{\"postinstall\":\"touch SENTINEL-npm\",\"build\":\"touch SENTINEL-npm\"},"
                + "\"devDependencies\":{\"@julclang/joust\":\"0.1.0-pre2\"}}");
        for (Path dir : new Path[]{root.resolve("engine"), root.resolve("node_modules/@julclang/joust/engine")}) {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("joust-toolchain.js"), engineJs);
            Files.write(dir.resolve("joust-toolchain.js.wasm"), new byte[]{0, 'a', 's', 'm', 1, 0, 0, 0});
        }
        Files.writeString(root.resolve("node_modules/@julclang/joust/package.json"),
                "{\"name\":\"@julclang/joust\",\"version\":\"0.1.0-pre2\",\"bin\":{\"joust-lsp\":\"bin/joust-lsp.js\"}}");
        JoustTestFiles.executable(root.resolve("node_modules/@julclang/joust/bin/joust-lsp.js"),
                "#!/usr/bin/env node\nrequire('fs').writeFileSync('" + root.resolve("SENTINEL-lsp") + "','x')\n");
        JoustTestFiles.executable(root.resolve("node_modules/.bin/joust-lsp"), "#!/bin/sh\ntouch '" + root.resolve("SENTINEL-bin") + "'\n");
        JoustTestFiles.executable(root.resolve("node"), "#!/bin/sh\ntouch '" + root.resolve("SENTINEL-node") + "'\n");
        JoustTestFiles.executable(root.resolve("gradlew"), "#!/bin/sh\ntouch '" + root.resolve("SENTINEL-gradle") + "'\n");
        Files.writeString(root.resolve("build.gradle"), "plugins { id 'org.julclang.joust' version '0.1.0-pre2' }\n"
                + "new File('" + root.resolve("SENTINEL-gradle-script") + "').text = 'x'\n", StandardCharsets.UTF_8);
    }

    /** Copies what the server writes to a file, for debugging with JOUST_LSP_TRACE_DIR. */
    static final class TeeInput extends java.io.FilterInputStream {
        private final java.io.OutputStream copy;

        TeeInput(java.io.InputStream in, java.io.OutputStream copy) {
            super(in);
            this.copy = copy;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                copy.write(b);
                copy.flush();
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                copy.write(b, off, n);
                copy.flush();
            }
            return n;
        }
    }

    /** Records what the server sends; refuses every edit, as IntelliAda does. */
    static final class RecordingClient implements LanguageClient {
        final Map<String, List<Diagnostic>> diagnostics = new ConcurrentHashMap<>();
        final List<ApplyWorkspaceEditParams> applyEdits = new CopyOnWriteArrayList<>();
        final List<String> registrations = new CopyOnWriteArrayList<>();
        final Map<String, Map<String, Object>> statuses = new ConcurrentHashMap<>();
        volatile ServerCapabilities capabilities;

        List<Diagnostic> diagnosticsFor(String uri, int seconds) throws InterruptedException {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            while (System.nanoTime() < end) {
                List<Diagnostic> d = diagnostics.get(uri);
                if (d != null) return d;
                Thread.sleep(100);
            }
            fail("no diagnostics for " + uri + " within " + seconds + " s");
            return null;
        }

        @Override
        public void telemetryEvent(Object object) {
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams p) {
            diagnostics.put(p.getUri(), p.getDiagnostics());
        }

        @Override
        public void showMessage(MessageParams messageParams) {
        }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams message) {
        }

        @Override
        public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
            applyEdits.add(params);
            return CompletableFuture.completedFuture(new ApplyWorkspaceEditResponse(false));
        }

        @Override
        public CompletableFuture<Void> registerCapability(RegistrationParams params) {
            params.getRegistrations().forEach(r -> registrations.add(r.getMethod()));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
            return CompletableFuture.completedFuture(List.of());
        }

        @org.eclipse.lsp4j.jsonrpc.services.JsonNotification("joust/status")
        public void joustStatus(Map<String, Object> o) {
            if (o.get("uri") instanceof String u) statuses.put(u, o);
        }
    }
}
