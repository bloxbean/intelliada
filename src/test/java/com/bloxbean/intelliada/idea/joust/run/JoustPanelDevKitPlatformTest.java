package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.accounts.JoustAccounts;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.JoustHostService;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore;
import com.bloxbean.intelliada.idea.joust.review.JoustReview;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * ADR-005 M3.3b3's exit on DevKit (tool-gated), through the panel's own paths: a fresh account (a canary mnemonic) is
 * imported and funded; the Counter is deployed with it, incremented, a reset is declined in the review, and the
 * instance closed. The review summary is checked against the real bodies; I10: the canary mnemonic and its private key
 * appear in no child's arguments or environment, the host's stderr, idea.log, the journal or new temp files; I21: the
 * project's file tree is unchanged apart from the build's outputs.
 */
public class JoustPanelDevKitPlatformTest extends BasePlatformTestCase {

    private static <T> T off(Callable<T> c) throws Exception {
        return com.intellij.testFramework.PlatformTestUtil.waitForFuture(
                com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(c), 600_000);
    }

    public void testDeployCallDeclineAndCloseThroughThePanelPathsLeakNoKey() throws Exception {
        String name = "testDeployCallDeclineAndCloseThroughThePanelPathsLeakNoKey";
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", name);
        String store = JoustTestTools.env("JOUST_DEVKIT_STORE", name);
        String admin = JoustTestTools.env("JOUST_DEVKIT_ADMIN", name);
        if (fixture == null || store == null || admin == null) return;
        String bundle = System.getProperty("joust.verifier.dir", ""), java = System.getProperty("joust.lint.java", "");
        if (bundle.isEmpty() || java.isEmpty() || !Files.isDirectory(Path.of(bundle, "lib"))) {
            if (JoustTestTools.required()) throw new AssertionError("this test needs the bundled checker and a JDK 25");
            System.out.println("JOUST-SKIPPED " + name + ": no bundled checker or JDK 25");
            return;
        }
        long started = System.currentTimeMillis();
        Path module = Path.of(fixture);
        Map<String, String> treeBefore = tree(module);
        JoustRunSession.checkerForTests = com.bloxbean.intelliada.idea.joust.verifier.JoustChecker.forTests(Path.of(bundle), Path.of(java));
        TrustedProjects.setProjectTrusted(getProject(), true);
        JoustHostService.confirmForTests = (project, dir, spec, version) -> true;
        RemoteNode node = new RemoteNode();
        node.setId("devkit-panel-test");
        node.setName("DevKit");
        node.setNodeType(NodeType.LocalYaciDevKit);
        node.setApiEndpoint(store);
        node.setProtocolMagic("42");
        var nodes = com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState.getInstance();
        Path journalDir = Files.createTempDirectory("joust-panel-journal");
        JournalStore journalStore = new JournalStore(journalDir);
        JoustHostService service = JoustHostService.getInstance(getProject());
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        var accounts = JoustAccounts.getInstance();
        // The canary: a fresh devnet account (devnet accounts are created, never imported), funded by DevKit.
        var created = accounts.createFreshForBackup("canary", com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity.NetworkClass.DEVNET);
        String canary = created.phrase();
        JoustAccounts.Entry caller = created.entry();
        String privateHex = HexFormat.of().formatHex(accounts.signingKey(caller.id,
                com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity.NetworkClass.DEVNET).privateKey());
        JoustRunSession s = null;
        try {
            topUp(admin, store, caller.address);
            nodes.addRemoteNode(node);
            JoustRunSession.Launcher launcher = (init, key, reads) -> service.launch(guard, module, init, key, reads, e -> { });
            JoustRunSession s1 = off(() -> JoustRunSession.start(service, module, JoustRunSession.bind(node), journalStore, launcher, r -> { }));
            s = s1;
            List<JoustReview.Summary> reviewed = new CopyOnWriteArrayList<>();
            boolean[] decline = {false};
            s1.reviewer(summary -> {
                reviewed.add(summary);
                return !decline[0];
            });
            String contract = s1.running().initialize().path("contracts").get(0).path("name").textValue();
            assertTrue("the canary signs on this network", s1.signers().stream().anyMatch(a -> a.id.equals(caller.id)));

            // The forms come from the snapshot's description (I26).
            JsonNode x = s1.callsOf(contract);
            List<JoustCallDialog.Call> inits = JoustCallDialog.calls(x, true), all = JoustCallDialog.calls(x, false);
            assertEquals(List.of("init"), inits.stream().map(JoustCallDialog.Call::name).toList());
            JoustCallDialog.Call increment = all.stream().filter(c -> c.name().equals("increment")).findFirst().orElseThrow();
            assertEquals(List.of("by"), increment.args());
            assertTrue(increment.needsInstance());
            assertEquals("destroy", all.stream().filter(c -> c.name().equals("close")).findFirst().orElseThrow().kind());

            // Deploy.
            List<String> steps = new CopyOnWriteArrayList<>();
            var deployed = off(() -> s1.deploy(contract, caller.id, HostJson.object().put("method", "init").set("args", HostJson.object()),
                    JoustRunPanel.ttl(s1), Duration.ofMinutes(3), steps::add));
            assertTrue(steps.toString(), steps.contains("Creating the instance (init)"));
            JoustRunSession.Submitted init = deployed.get(deployed.size() - 1);
            assertNotNull(init.instance());
            assertTrue(off(() -> s1.awaitInclusion(init.txHash(), Duration.ofMinutes(3))));
            JoustReview.Summary initReview = reviewed.get(reviewed.size() - 1);
            assertEquals(init.txHash(), initReview.txHash());
            assertTrue("the caller signs", initReview.signers().contains(caller.id));
            assertTrue("a fee", initReview.fee().signum() > 0);
            assertTrue("the caller pays (fee and the instance's deposit)", initReview.callerNetOutflow() != null
                    && initReview.callerNetOutflow().compareTo(initReview.fee()) > 0);
            assertTrue("an output to the contract", initReview.outputs().stream().anyMatch(o -> o.to() == JoustReview.To.DEPLOYMENT));
            assertTrue("nothing to other addresses: " + JoustReview.lines(initReview), initReview.others().isEmpty());

            // Increment.
            ObjectNode inc = call(contract, caller.address, "action", "increment", init.instance());
            inc.with("args").put("by", "2");
            var incremented = off(() -> s1.submit("prepare", HostJson.object().put("ttlSlots", JoustRunPanel.ttl(s1)).set("call", inc), caller.id, List.of()));
            assertTrue(off(() -> s1.awaitInclusion(incremented.txHash(), Duration.ofMinutes(3))));
            JsonNode state = off(() -> s1.view(contract, init.instance(), null, null));
            assertTrue("the state shows the increment: " + state, state.toString().contains("2"));

            // A reset declined in the review: nothing submitted, nothing journaled, its inputs released.
            decline[0] = true;
            int journaled = s1.history().size();
            try {
                off(() -> s1.submit("prepare", HostJson.object().put("ttlSlots", JoustRunPanel.ttl(s1))
                        .set("call", call(contract, caller.address, "action", "reset", init.instance())), caller.id, List.of()));
                fail("a declined transaction was submitted");
            } catch (Throwable expected) {
                String m = String.valueOf(expected.getMessage());
                assertTrue(m, m.contains("declined in the review") && m.contains("inputs are released"));
            }
            assertEquals("a declined body is not journaled", journaled, s1.history().size());
            decline[0] = false;

            // Close.
            var closed = off(() -> s1.submit("prepare", HostJson.object().put("ttlSlots", JoustRunPanel.ttl(s1))
                    .set("call", call(contract, caller.address, "destroy", "close", init.instance())), caller.id, List.of()));
            assertTrue(off(() -> s1.awaitInclusion(closed.txHash(), Duration.ofMinutes(3))));
            String history = JoustRunPanel.historyText(s1.history());
            assertTrue(history, history.contains(closed.txHash()) && history.contains(incremented.txHash()));

            // I10: the canary and its key reach no child, console, log, journal or temp file.
            List<String> places = new ArrayList<>();
            ProcessHandle host = s1.running().process().toHandle();
            places.add("host command line: " + host.info().commandLine().orElse(""));
            places.add("host environment: " + environment(host.pid()));
            places.add("host stderr: " + s1.running().stderr().text());
            Path log = Path.of(PathManager.getLogPath(), "idea.log");
            if (Files.isRegularFile(log)) places.add("idea.log: " + Files.readString(log, StandardCharsets.ISO_8859_1));
            for (Path p : recentFiles(journalDir, 0)) places.add(p + ": " + Files.readString(p, StandardCharsets.ISO_8859_1));
            for (Path p : recentFiles(Path.of(System.getProperty("java.io.tmpdir")), started)) {
                places.add(p + ": " + Files.readString(p, StandardCharsets.ISO_8859_1));
            }
            String firstWords = String.join(" ", List.of(canary.split(" ")).subList(0, 6));
            for (String place : places) {
                assertFalse("the canary mnemonic in " + place.substring(0, Math.min(120, place.length())), place.contains(firstWords));
                assertFalse("the private key in " + place.substring(0, Math.min(120, place.length())), place.contains(privateHex));
            }
        } finally {
            JoustRunSession.checkerForTests = null;
            nodes.removeRemoteNode(node);
            service.stop(module);
            accounts.remove(caller.id);
        }
        // I21: the project is unchanged apart from the build's own outputs.
        assertEquals("the project's files changed", treeBefore, tree(module));
    }

    private static ObjectNode call(String contract, String address, String kind, String method, String instance) {
        ObjectNode c = HostJson.object().put("contract", contract).put("kind", kind).put("method", method).put("address", address)
                .put("instance", instance);
        c.putObject("args");
        return c;
    }

    /** DevKit's faucet: 200 ADA to {@code address}, then waits until the store shows it. */
    private static void topUp(String admin, String store, String address) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String body = "{\"address\":\"" + address + "\",\"adaAmount\":200}";
        var r = http.send(HttpRequest.newBuilder(URI.create(admin + "/local-cluster/api/addresses/topup"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue("top-up: " + r.statusCode() + " " + r.body(), r.statusCode() / 100 == 2);
        for (int i = 0; i < 60; i++) {
            var u = http.send(HttpRequest.newBuilder(URI.create(store + "/addresses/" + address + "/utxos")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (u.statusCode() == 200 && u.body().contains("tx_hash")) return;
            Thread.sleep(1_000);
        }
        fail("the top-up did not appear");
    }

    /** The environment of a process of this user (macOS and Linux {@code ps}). */
    private static String environment(long pid) throws Exception {
        Process p = new ProcessBuilder("ps", "-E", "-ww", "-p", Long.toString(pid), "-o", "command=").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(10, TimeUnit.SECONDS);
        return out;
    }

    /** Regular files under {@code dir} modified at or after {@code since} (ms), at most 4 MB each, 2000 in all. */
    private static List<Path> recentFiles(Path dir, long since) throws Exception {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> w = Files.walk(dir, 6)) {
            w.filter(p -> {
                try {
                    return Files.isRegularFile(p) && Files.size(p) <= 4L << 20 && Files.getLastModifiedTime(p).toMillis() >= since;
                } catch (Exception e) {
                    return false;
                }
            }).limit(2000).forEach(out::add);
        } catch (java.io.UncheckedIOException unreadable) {
            // a directory we may not read
        }
        return out;
    }

    /** Every file of the module but the build's outputs, by path to SHA-256. */
    private static Map<String, String> tree(Path module) throws Exception {
        Map<String, String> out = new TreeMap<>();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (Stream<Path> w = Files.walk(module)) {
            for (Path p : (Iterable<Path>) w::iterator) {
                String rel = module.relativize(p).toString().replace('\\', '/');
                if (rel.startsWith("build/") || rel.startsWith(".gradle/") || rel.equals("build") || rel.equals(".gradle")) continue;
                if (Files.isRegularFile(p)) out.put(rel, HexFormat.of().formatHex(md.digest(Files.readAllBytes(p))));
            }
        }
        return out;
    }
}
