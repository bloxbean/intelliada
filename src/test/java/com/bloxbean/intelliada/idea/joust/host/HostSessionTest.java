package com.bloxbean.intelliada.idea.joust.host;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * ADR-005 I22: the session's own obligations that the conformance kit cannot test (its conformance doc, "Host faults"):
 * a frame that starts and never finishes, a host that never answers, plus the call discipline.
 */
public class HostSessionTest {

    /** An in-process scripted host: the test reads what the client sent and writes raw host output. */
    private static final class Peer {
        final java.io.InputStream clientReads;
        final java.io.OutputStream hostWrites;
        final java.io.InputStream hostReads;
        final java.io.OutputStream clientWrites;
        final HostFrames.Reader fromClient;
        final AtomicInteger stops = new AtomicInteger();
        final List<String> ends = new CopyOnWriteArrayList<>();
        final HostSession session;

        Peer(HostSession.Reads reads, long stallNanos) throws IOException {
            java.nio.channels.Pipe toClient = java.nio.channels.Pipe.open(), toHost = java.nio.channels.Pipe.open();
            clientReads = java.nio.channels.Channels.newInputStream(toClient.source());
            hostWrites = java.nio.channels.Channels.newOutputStream(toClient.sink());
            hostReads = java.nio.channels.Channels.newInputStream(toHost.source());
            clientWrites = java.nio.channels.Channels.newOutputStream(toHost.sink());
            fromClient = new HostFrames.Reader(hostReads);
            session = new HostSession(clientReads, clientWrites, stops::incrementAndGet, reads, ends::add, stallNanos);
        }

        JsonNode next() throws Exception {
            return HostJson.parse(fromClient.next());
        }

        void raw(String s) throws IOException {
            hostWrites.write(s.getBytes(StandardCharsets.UTF_8));
            hostWrites.flush();
        }

        void frame(String json) throws IOException {
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            raw("Content-Length: " + b.length + "\r\n\r\n" + json);
        }
    }

    private Peer peer;

    @After
    public void close() {
        if (peer != null) peer.session.close();
    }

    private static final HostSession.Reads NO_READS = (id, p) -> {
        throw new IllegalStateException("no reads expected");
    };

    @Test
    public void aCallGetsItsResultAndReadsAreAnsweredDuringIt() throws Exception {
        peer = new Peer((id, p) -> HostJson.object().put("echo", p.path("kind").textValue()), HostFrames.STALL_NANOS);
        var t = new Thread(() -> {
            try {
                JsonNode req = peer.next();
                assertEquals("describe", req.get("method").textValue());
                long id = req.get("id").longValue();
                peer.frame("{\"jsonrpc\":\"2.0\",\"id\":\"" + id + ".1\",\"method\":\"chain/read\",\"params\":{\"kind\":\"tip\"}}");
                JsonNode answer = peer.next();
                assertEquals("tip", answer.path("result").path("echo").textValue());
                peer.frame("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"ok\":true}}");
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        JsonNode r = peer.session.call("describe", HostJson.object().put("contract", "C"), Duration.ofSeconds(10), () -> false);
        assertTrue(r.path("ok").booleanValue());
        t.join(5000);
        assertNull(peer.session.ended());
    }

    @Test
    public void aHostErrorIsTypedByKindAndCode() throws Exception {
        peer = new Peer(NO_READS, HostFrames.STALL_NANOS);
        new Thread(() -> {
            try {
                long id = peer.next().get("id").longValue();
                peer.frame("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":-32001,\"message\":\"x\",\"data\":{\"kind\":\"refusal\","
                        + "\"code\":\"require\",\"message\":\"increment must be positive\",\"sites\":[{\"file\":\"Counter.jsc\",\"line\":19,"
                        + "\"col\":5,\"kind\":\"require\",\"in\":\"increment\",\"what\":\"by > 0\"}],\"retryAtSlot\":null}}}");
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }).start();
        try {
            peer.session.call("prepare", HostJson.object(), Duration.ofSeconds(10), () -> false);
            fail("no error");
        } catch (HostError e) {
            assertTrue(e.is("refusal", "require"));
            assertEquals("increment must be positive", e.getMessage());
            assertEquals(19, e.sites().get(0).line());
            assertNull(e.retryAtSlot());
        }
    }

    @Test
    public void aSecondCallWhileOneRunsIsRefusedLocally() throws Exception {
        peer = new Peer(NO_READS, HostFrames.STALL_NANOS);
        var first = new Thread(() -> {
            try {
                peer.session.call("prepare", HostJson.object(), Duration.ofSeconds(10), () -> false);
            } catch (Exception ignored) {
            }
        });
        first.start();
        peer.next();
        try {
            peer.session.call("view", HostJson.object(), Duration.ofSeconds(1), () -> false);
            fail("a second call reached the host");
        } catch (IllegalStateException expected) {
            // refused before sending
        }
        peer.session.close();
        first.join(5000);
    }

    @Test
    public void aFrameThatStallsEndsTheSession() throws Exception {
        peer = new Peer(NO_READS, 300_000_000L);
        var t = new Thread(() -> {
            try {
                peer.next();
                peer.raw("Content-Length: 50\r\n\r\n{\"jsonrpc\"");   // never finishes
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        try {
            peer.session.call("describe", HostJson.object(), Duration.ofSeconds(20), () -> false);
            fail("a stalled frame was waited for");
        } catch (HostSession.Ended expected) {
            assertTrue(peer.session.violated());
            assertTrue(peer.session.ended(), peer.session.ended().contains("stalled"));
            assertEquals("the host is stopped", 1, peer.stops.get());
        }
    }

    @Test
    public void aHostThatNeverAnswersIsCancelledThenStopped() throws Exception {
        peer = new Peer(NO_READS, HostFrames.STALL_NANOS);
        List<String> seen = new CopyOnWriteArrayList<>();
        var t = new Thread(() -> {
            try {
                seen.add(peer.next().get("method").textValue());
                seen.add(peer.next().get("method").textValue());
            } catch (Throwable ignored) {
            }
        });
        t.start();
        long start = System.nanoTime();
        try {
            peer.session.call("prepare", HostJson.object(), Duration.ofMillis(500), () -> false);
            fail("an unanswered call returned");
        } catch (HostSession.Ended expected) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            assertTrue("hard timeout plus the cancel grace: " + ms, ms >= 500 && ms < 8000);
            t.join(2000);
            assertEquals(List.of("prepare", "$/cancelRequest"), seen);
            assertEquals(1, peer.stops.get());
        }
    }

    @Test
    public void aCancelledCallSendsCancelAndWaitsForItsTerminalAnswer() throws Exception {
        peer = new Peer(NO_READS, HostFrames.STALL_NANOS);
        new Thread(() -> {
            try {
                long id = peer.next().get("id").longValue();
                assertEquals("$/cancelRequest", peer.next().get("method").textValue());
                peer.frame("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":-32800,\"message\":\"c\",\"data\":{\"kind\":\"internal\","
                        + "\"code\":\"cancelled\",\"message\":\"cancelled\",\"sites\":[],\"retryAtSlot\":null}}}");
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }).start();
        try {
            peer.session.call("prepare", HostJson.object(), Duration.ofSeconds(30), () -> true);
            fail("no error");
        } catch (HostError e) {
            assertTrue(e.is("internal", "cancelled"));
        }
        assertNull("a cancelled call leaves the session live", peer.session.ended());
    }

    @Test
    public void aReadThatCannotBeAnsweredCancelsTheCall() throws Exception {
        peer = new Peer((id, p) -> {
            throw new IOException("provider down");
        }, HostFrames.STALL_NANOS);
        List<String> seen = new CopyOnWriteArrayList<>();
        new Thread(() -> {
            try {
                long id = peer.next().get("id").longValue();
                peer.frame("{\"jsonrpc\":\"2.0\",\"id\":\"" + id + ".1\",\"method\":\"chain/read\",\"params\":{\"kind\":\"tip\"}}");
                seen.add(peer.next().get("method").textValue());
                peer.frame("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":-32800,\"message\":\"c\",\"data\":{\"kind\":\"internal\","
                        + "\"code\":\"cancelled\",\"message\":\"cancelled\",\"sites\":[],\"retryAtSlot\":null}}}");
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }).start();
        try {
            peer.session.call("prepare", HostJson.object(), Duration.ofSeconds(30), () -> false);
            fail("no error");
        } catch (HostError e) {
            assertTrue(e.is("internal", "cancelled"));
        }
        assertEquals(List.of("$/cancelRequest"), seen);
    }

    @Test
    public void aReusedReverseIdIsAViolation() throws Exception {
        peer = new Peer((id, p) -> HostJson.object(), HostFrames.STALL_NANOS);
        new Thread(() -> {
            try {
                long id = peer.next().get("id").longValue();
                String read = "{\"jsonrpc\":\"2.0\",\"id\":\"" + id + ".1\",\"method\":\"chain/read\",\"params\":{\"kind\":\"tip\"}}";
                peer.frame(read);
                peer.next();
                peer.frame(read);
            } catch (Throwable ignored) {
            }
        }).start();
        try {
            peer.session.call("prepare", HostJson.object(), Duration.ofSeconds(10), () -> false);
            fail("a reused id was accepted");
        } catch (HostSession.Ended expected) {
            assertTrue(peer.session.violated());
        }
    }
}
