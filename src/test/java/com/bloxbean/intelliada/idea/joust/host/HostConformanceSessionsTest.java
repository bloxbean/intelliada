package com.bloxbean.intelliada.idea.joust.host;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

/**
 * ADR-005 I22, M3.1a: IntelliAda's session against JOUST's scripted {@code FakeHost} (protocol 1 conformance kit,
 * "Test a protocol peer"). The recorded Bank session must run to the end; each {@code violation-*} session must end in a
 * protocol violation with the client closing its end and sending nothing more (the fake host then exits 0; another frame
 * exits 3; a client still connected after five seconds exits 4).
 */
public class HostConformanceSessionsTest {

    @Test
    public void theRecordedBankSessionRunsToItsEnd() throws Exception {
        Kit kit = kit("theRecordedBankSessionRunsToItsEnd");
        if (kit == null) return;
        Result r = drive(kit, "sessions/bank.json");
        assertNull("no violation: " + r.ended, r.violation);
        assertEquals("the fake host accepted every frame: " + r.stderr, 0, r.exit);
        assertEquals(13, r.calls);
        assertTrue("the cancelled listing ended as internal/cancelled: " + r.errors, r.errors.contains("internal/cancelled"));
    }

    @Test
    public void everyHostFaultEndsTheSession() throws Exception {
        Kit kit = kit("everyHostFaultEndsTheSession");
        if (kit == null) return;
        List<String> sessions = new ArrayList<>();
        try (ZipFile z = new ZipFile(kit.jar.toFile())) {
            z.stream().map(e -> e.getName()).filter(n -> n.matches("sessions/violation-[a-z-]+\\.json")).sorted().forEach(sessions::add);
        }
        assertEquals("the kit's seven transport faults", 7, sessions.size());
        for (String s : sessions) {
            Result r = drive(kit, s);
            assertNotNull(s + " ended in a violation: " + r.ended, r.violation);
            assertEquals(s + ": the client closed its end and sent nothing more: " + r.stderr, 0, r.exit);
        }
    }

    // ---- driver -------------------------------------------------------------------------------------------------

    record Kit(String java, String classpath, Path jar) {
    }

    record Result(String ended, String violation, int exit, int calls, List<String> errors, String stderr) {
    }

    static Kit kit(String test) throws Exception {
        String cp = System.getProperty("joust.conformance.classpath", "");
        String java = System.getProperty("joust.lint.java", "");
        Path jar = null;
        for (String p : cp.split(File.pathSeparator)) {
            if (p.endsWith("-conformance.jar")) jar = Path.of(p);
        }
        if (jar == null || java.isEmpty()) {
            if (JoustTestTools.required()) {
                throw new AssertionError(test + " needs the JOUST conformance kit (-PjoustTestVersion, ~/.m2) and a JDK 25");
            }
            System.out.println("JOUST-SKIPPED " + test + ": no conformance kit or JDK 25");
            return null;
        }
        return new Kit(java, cp, jar);
    }

    static Result drive(Kit kit, String sessionPath) throws Exception {
        JsonNode frames;
        try (ZipFile z = new ZipFile(kit.jar.toFile())) {
            frames = HostJson.MAPPER.readTree(z.getInputStream(z.getEntry(sessionPath))).get("frames");
        }
        Path file = Files.createTempFile("joust-session", ".json");
        Files.write(file, HostJson.write(HostJson.MAPPER.createObjectNode().set("frames", frames)));
        Process p = new ProcessBuilder(kit.java, "-XX:+DisplayVMOutputToStderr", "-Xlog:disable", "-Xlog:all=warning:stderr",
                "--enable-native-access=ALL-UNNAMED", "-cp", kit.classpath, "org.julclang.state.host.conformance.FakeHost", "--stdio",
                file.toString()).redirectError(ProcessBuilder.Redirect.PIPE).start();
        HostStderr err = new HostStderr(64 * 1024);
        Thread drain = new Thread(() -> {
            try (var in = p.getErrorStream()) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) err.append(b, n);
            } catch (Exception ignored) {
            }
        });
        drain.setDaemon(true);
        drain.start();

        // The recorded answers to reverse reads; a "late" one is answered only after the client's cancellation.
        Map<String, ObjectNode> answers = new HashMap<>();
        Set<String> late = new HashSet<>();
        Set<String> open = new HashSet<>();
        boolean cancelSeen = false;
        for (JsonNode f : frames) {
            JsonNode h = f.get("host");
            if (h != null && "chain/read".equals(h.path("method").textValue())) open.add(h.get("id").textValue());
            JsonNode c = f.get("client");
            if (c == null) continue;
            if ("$/cancelRequest".equals(c.path("method").textValue())) cancelSeen = true;
            if (c.has("result") && c.get("id").isTextual()) {
                String id = c.get("id").textValue();
                answers.put(id, (ObjectNode) c.get("result"));
                if (cancelSeen && open.contains(id)) late.add(id);
                open.remove(id);
                cancelSeen = false;
            }
        }

        AtomicBoolean cancel = new AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>> callDone =
                new java.util.concurrent.atomic.AtomicReference<>(CompletableFuture.completedFuture(null));
        List<String> ended = new CopyOnWriteArrayList<>();
        HostSession.Reads reads = (id, params) -> {
            ObjectNode a = answers.get(id);
            if (a == null) throw new IllegalStateException("no recorded answer for " + id);
            if (late.contains(id)) {
                cancel.set(true);
                callDone.get().get(30, TimeUnit.SECONDS);
            }
            return a;
        };
        HostSession session = new HostSession(p.getInputStream(), p.getOutputStream(), () -> { }, reads, ended::add);
        int calls = 0;
        List<String> errors = new ArrayList<>();
        String violation = null;
        try {
            for (JsonNode f : frames) {
                if (f.has("violation")) break;
                JsonNode c = f.get("client");
                if (c == null || !c.has("method") || !c.has("id")) continue;     // reverse answers and notifications
                String method = c.get("method").textValue();
                if (method.equals("shutdown")) {
                    session.shutdown(Duration.ofSeconds(10));
                    calls++;
                    break;
                }
                CompletableFuture<Void> done = new CompletableFuture<>();
                callDone.set(done);
                cancel.set(false);
                try {
                    session.call(method, c.get("params"), Duration.ofSeconds(30), cancel::get);
                } catch (HostError e) {
                    errors.add(e.kind() + "/" + e.code());
                } catch (HostSession.Ended e) {
                    break;
                } finally {
                    done.complete(null);
                }
                session.awaitReadsIdle(Duration.ofSeconds(10));
                calls++;
            }
        } finally {
            if (session.ended() == null) session.close();
        }
        if (session.violated()) violation = session.ended();
        boolean exited = p.waitFor(10, TimeUnit.SECONDS);
        if (!exited) p.destroyForcibly();
        drain.join(2000);
        Files.deleteIfExists(file);
        return new Result(String.join("; ", ended), violation, exited ? p.exitValue() : -1, calls, errors, err.text());
    }
}
