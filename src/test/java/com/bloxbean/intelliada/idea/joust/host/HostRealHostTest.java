package com.bloxbean.intelliada.idea.joust.host;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * ADR-005 M3.1a, F38/F39 (tool-gated): the real JOUST dev host of a built Gradle Counter, launched from IntelliAda's own
 * snapshot with protocol 1's command line and environment, through {@link HostSession}. Set {@code JOUST_HOST_FIXTURE}
 * to a JOUST Gradle project on which {@code ./gradlew build joustHostSpec} has run (a {@code joust new -t gradle}
 * Counter built with the JOUST under test).
 */
public class HostRealHostTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void theCounterHostInitializesDescribesAndStops() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "theCounterHostInitializesDescribesAndStops");
        if (fixture == null) return;
        Path project = Path.of(fixture);
        HostSpec spec = HostSpec.read(project);
        HostSnapshot snap = HostSnapshot.create(spec, tmp.newFolder("snapshots").toPath());
        assertTrue("descriptions read before launch", snap.firstByPath().keySet().stream().anyMatch(k -> k.startsWith("META-INF/joust/")));
        HostLauncher.Started started = HostLauncher.start(spec, snap, project, 256 * 1024);
        Process p = started.process();
        HostSession s = new HostSession(p.getInputStream(), p.getOutputStream(), () -> HostLauncher.stop(p),
                (id, params) -> {
                    throw new IllegalStateException("no chain reads expected for initialize and describe");
                }, e -> { });
        try {
            ObjectNode init = HostJson.object();
            init.putArray("protocol").add(1);
            init.putObject("network").put("networkId", 0).put("protocolMagic", 42).put("mainnet", false);
            init.putObject("clock").put("zeroSlot", 0).put("zeroTimeMs", 1700000000000L).put("slotLengthMs", 1000);
            init.putObject("time").put("maxTtlSlots", 900).put("settlementSlots", 60).put("maxNowAheadSlots", 900);
            init.putObject("limits").put("callDeadlineMs", 60000).put("readsPerCall", 256).put("readBytesPerCall", 33554432);
            init.putObject("build").put("snapshot", snap.root().toString()).put("digest", snap.digest());
            init.put("restoring", false);
            JsonNode r = s.call("initialize", init, Duration.ofSeconds(120), () -> false);
            assertEquals(1, r.path("protocol").intValue());
            assertEquals("I24: the host's fingerprint is IntelliAda's digest", snap.digest(), r.path("fingerprint").textValue());
            JsonNode contracts = r.path("contracts");
            assertTrue(contracts.isArray() && contracts.size() >= 1);
            String name = contracts.get(0).path("name").textValue();
            JsonNode d = s.call("describe", HostJson.object().put("contract", name), Duration.ofSeconds(60), () -> false);
            List<String> methods = new java.util.ArrayList<>();
            d.path("calls").forEach(c -> methods.add(c.path("method").textValue()));
            assertTrue(methods.toString(), methods.containsAll(List.of("init", "increment")));
            assertTrue("I19: the host listens on no socket", noListeningSocket(p.pid()));
            s.shutdown(Duration.ofSeconds(10));
            assertTrue("the host exits after exit", p.waitFor(15, TimeUnit.SECONDS));
            assertEquals(started.stderr().text(), 0, p.exitValue());
            assertFalse("an orderly stop is not a violation", s.violated());
        } finally {
            s.close();
            HostLauncher.stop(p);
            snap.delete();
        }
    }

    /** lsof's listening TCP sockets of {@code pid} (macOS and Linux); true when there are none, or lsof is absent. */
    static boolean noListeningSocket(long pid) throws Exception {
        if (!Files.isExecutable(Path.of("/usr/sbin/lsof")) && !Files.isExecutable(Path.of("/usr/bin/lsof"))) return true;
        String lsof = Files.isExecutable(Path.of("/usr/sbin/lsof")) ? "/usr/sbin/lsof" : "/usr/bin/lsof";
        Process q = new ProcessBuilder(lsof, "-nP", "-a", "-p", String.valueOf(pid), "-iTCP", "-sTCP:LISTEN").redirectErrorStream(true).start();
        String out = new String(q.getInputStream().readAllBytes());
        q.waitFor(10, TimeUnit.SECONDS);
        return !out.contains("LISTEN");
    }
}
