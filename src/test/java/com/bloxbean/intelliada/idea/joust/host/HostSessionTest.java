package com.bloxbean.intelliada.idea.joust.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
            session.markInitializedForTests();
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
    public void aSecondCallWaitsForTheFirstsTerminalAnswer() throws Exception {
        peer = new Peer(NO_READS, HostFrames.STALL_NANOS);
        var first = new Thread(() -> {
            try {
                peer.session.call("prepare", HostJson.object(), Duration.ofSeconds(10), () -> false);
            } catch (Exception ignored) {
            }
        });
        first.start();
        long id1 = peer.next().get("id").longValue();
        var second = new java.util.concurrent.CompletableFuture<JsonNode>();
        new Thread(() -> {
            try {
                second.complete(peer.session.call("view", HostJson.object(), Duration.ofSeconds(10), () -> false));
            } catch (Exception e) {
                second.completeExceptionally(e);
            }
        }).start();
        Thread.sleep(300);
        assertFalse("the second call waits (D11 serial calls)", second.isDone());
        peer.frame("{\"jsonrpc\":\"2.0\",\"id\":" + id1 + ",\"result\":{}}");
        JsonNode req2 = peer.next();
        assertEquals("view", req2.get("method").textValue());
        peer.frame("{\"jsonrpc\":\"2.0\",\"id\":" + req2.get("id").longValue() + ",\"result\":{\"v\":1}}");
        assertEquals(1, second.get(5, java.util.concurrent.TimeUnit.SECONDS).path("v").intValue());
        first.join(5000);
    }

    @Test
    public void anInterruptedCallerKeepsTheSlotUntilTheHostAnswers() throws Exception {
        peer = new Peer(NO_READS, HostFrames.STALL_NANOS);
        Thread caller = new Thread(() -> {
            try {
                peer.session.call("prepare", HostJson.object(), Duration.ofSeconds(30), () -> false);
            } catch (Exception ignored) {
            }
        });
        caller.start();
        long id1 = peer.next().get("id").longValue();
        caller.interrupt();
        assertEquals("an interrupted caller cancels its call", "$/cancelRequest", peer.next().get("method").textValue());
        var second = new java.util.concurrent.CompletableFuture<JsonNode>();
        new Thread(() -> {
            try {
                second.complete(peer.session.call("view", HostJson.object(), Duration.ofSeconds(10), () -> false));
            } catch (Exception e) {
                second.completeExceptionally(e);
            }
        }).start();
        Thread.sleep(300);
        assertFalse("no request reaches the host before the first call's terminal answer", second.isDone());
        peer.frame("{\"jsonrpc\":\"2.0\",\"id\":" + id1 + ",\"error\":{\"code\":-32800,\"message\":\"c\",\"data\":{\"kind\":\"internal\","
                + "\"code\":\"cancelled\",\"message\":\"cancelled\",\"sites\":[],\"retryAtSlot\":null}}}");
        JsonNode req2 = peer.next();
        assertEquals("view", req2.get("method").textValue());
        peer.frame("{\"jsonrpc\":\"2.0\",\"id\":" + req2.get("id").longValue() + ",\"result\":{}}");
        second.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    public void aReadInTheSameWriteAsTheResponseIsAViolationAndNeverAnswered() throws Exception {
        java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();
        peer = new Peer((id, p) -> {
            asked.incrementAndGet();
            return HostJson.object();
        }, HostFrames.STALL_NANOS);
        new Thread(() -> {
            try {
                long id = peer.next().get("id").longValue();
                String resp = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{}}";
                String read = "{\"jsonrpc\":\"2.0\",\"id\":\"" + id + ".1\",\"method\":\"chain/read\",\"params\":{\"kind\":\"tip\"}}";
                byte[] a = resp.getBytes(StandardCharsets.UTF_8), b = read.getBytes(StandardCharsets.UTF_8);
                peer.raw("Content-Length: " + a.length + "\r\n\r\n" + resp + "Content-Length: " + b.length + "\r\n\r\n" + read);
            } catch (Throwable ignored) {
            }
        }).start();
        peer.session.call("describe", HostJson.object(), Duration.ofSeconds(10), () -> false);
        long end = System.nanoTime() + 5_000_000_000L;
        while (peer.session.ended() == null && System.nanoTime() < end) Thread.sleep(10);
        assertTrue("a chain/read after the call's terminal answer is a violation (M31A-DES-1)", peer.session.violated());
        assertEquals("never answered", 0, asked.get());
    }

    @Test
    public void aReadOfAnEarlierCallsGenerationIsAViolation() throws Exception {
        java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();
        peer = new Peer((id, p) -> {
            asked.incrementAndGet();
            return HostJson.object();
        }, HostFrames.STALL_NANOS);
        new Thread(() -> {
            try {
                long a = peer.next().get("id").longValue();
                peer.frame("{\"jsonrpc\":\"2.0\",\"id\":\"" + a + ".1\",\"method\":\"chain/read\",\"params\":{\"kind\":\"tip\"}}");
                peer.next();
                peer.frame("{\"jsonrpc\":\"2.0\",\"id\":" + a + ",\"result\":{}}");
                peer.next();   // B's request
                // A read carrying A's generation while B is in flight (M31A-DES-12).
                peer.frame("{\"jsonrpc\":\"2.0\",\"id\":\"" + a + ".2\",\"method\":\"chain/read\",\"params\":{\"kind\":\"tip\"}}");
            } catch (Throwable ignored) {
            }
        }).start();
        peer.session.call("describe", HostJson.object(), Duration.ofSeconds(10), () -> false);
        try {
            peer.session.call("view", HostJson.object(), Duration.ofSeconds(10), () -> false);
            fail("a read of an earlier call was accepted");
        } catch (HostSession.Ended expected) {
            assertTrue(peer.session.violated());
        }
        assertEquals("only A's own read was answered", 1, asked.get());
    }

    @Test
    public void aSilentHostIsStoppedEvenAfterItsCallerWasInterrupted() throws Exception {
        peer = new Peer(NO_READS, HostFrames.STALL_NANOS);
        Thread caller = new Thread(() -> {
            try {
                peer.session.call("prepare", HostJson.object(), Duration.ofMillis(300), () -> false);
            } catch (Exception ignored) {
            }
        });
        caller.start();
        peer.next();
        caller.interrupt();
        long end = System.nanoTime() + 8_000_000_000L;
        while (peer.session.ended() == null && System.nanoTime() < end) Thread.sleep(20);
        assertNotNull("the watchdog owns the deadline (M31A-CDX-16)", peer.session.ended());
        assertTrue(peer.session.violated());
        assertEquals(1, peer.stops.get());
    }

    @Test
    public void aQueuedCallCancelledWhileWaitingIsNeverSent() throws Exception {
        peer = new Peer(NO_READS, HostFrames.STALL_NANOS);
        new Thread(() -> {
            try {
                peer.session.call("describe", HostJson.object(), Duration.ofSeconds(10), () -> false);
            } catch (Exception ignored) {
            }
        }).start();
        long a = peer.next().get("id").longValue();
        java.util.concurrent.atomic.AtomicBoolean cancel = new java.util.concurrent.atomic.AtomicBoolean();
        var queued = new java.util.concurrent.CompletableFuture<String>();
        new Thread(() -> {
            try {
                peer.session.call("view", HostJson.object(), Duration.ofSeconds(10), cancel::get);
                queued.complete("sent");
            } catch (HostSession.Ended e) {
                queued.complete("not sent");
            } catch (Exception e) {
                queued.complete(e.toString());
            }
        }).start();
        Thread.sleep(200);
        cancel.set(true);
        assertEquals("not sent", queued.get(5, java.util.concurrent.TimeUnit.SECONDS));
        peer.frame("{\"jsonrpc\":\"2.0\",\"id\":" + a + ",\"result\":{}}");
        // The next frame the host sees is a new call, never the cancelled view.
        new Thread(() -> {
            try {
                peer.session.call("instances", HostJson.object(), Duration.ofSeconds(10), () -> false);
            } catch (Exception ignored) {
            }
        }).start();
        assertEquals("instances", peer.next().get("method").textValue());
    }

    @Test
    public void aMalformedErrorEnvelopeIsAViolation() throws Exception {
        peer = new Peer(NO_READS, HostFrames.STALL_NANOS);
        new Thread(() -> {
            try {
                long id = peer.next().get("id").longValue();
                peer.frame("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{}}");
            } catch (Throwable ignored) {
            }
        }).start();
        try {
            peer.session.call("prepare", HostJson.object(), Duration.ofSeconds(10), () -> false);
            fail("a malformed error was accepted");
        } catch (HostSession.Ended expected) {
            assertTrue(peer.session.violated());
        }
    }

    @Test
    public void aFrameAfterExitIsAViolation() throws Exception {
        peer = new Peer(NO_READS, HostFrames.STALL_NANOS);
        new Thread(() -> {
            try {
                JsonNode sd = peer.next();
                assertEquals("shutdown", sd.get("method").textValue());
                peer.frame("{\"jsonrpc\":\"2.0\",\"id\":" + sd.get("id").longValue() + ",\"result\":{}}");
                assertEquals("exit", peer.next().get("method").textValue());
                peer.frame("{\"jsonrpc\":\"2.0\",\"method\":\"late\"}");
                peer.hostWrites.close();
            } catch (Throwable ignored) {
            }
        }).start();
        peer.session.shutdown(Duration.ofSeconds(5));
        assertTrue("the reader classified the frame after exit (M31A-CDX-5)", peer.session.violated());
    }

    @Test
    public void aHostThatStopsReadingItsInputEndsTheSession() throws Exception {
        // The peer never reads: once the pipe fills, the client's frame write stalls.
        java.nio.channels.Pipe toClient = java.nio.channels.Pipe.open(), toHost = java.nio.channels.Pipe.open();
        java.util.concurrent.atomic.AtomicInteger stops = new java.util.concurrent.atomic.AtomicInteger();
        HostSession s = new HostSession(java.nio.channels.Channels.newInputStream(toClient.source()),
                java.nio.channels.Channels.newOutputStream(toHost.sink()), stops::incrementAndGet, NO_READS, e -> { }, 300_000_000L);
        s.markInitializedForTests();
        ObjectNode big = HostJson.object();
        big.put("x", "y".repeat(3_000_000));
        try {
            s.call("prepare", big, Duration.ofSeconds(20), () -> false);
            fail("a write the host never read completed");
        } catch (HostSession.Ended expected) {
            assertTrue(s.violated());
            assertTrue(s.ended(), s.ended().contains("stopped reading"));
        } finally {
            s.close();
        }
    }

    @Test
    public void stopDuringACallCancelsItAndDoesNotThrow() throws Exception {
        peer = new Peer(NO_READS, HostFrames.STALL_NANOS);
        new Thread(() -> {
            try {
                peer.session.call("prepare", HostJson.object(), Duration.ofSeconds(30), () -> false);
            } catch (Exception ignored) {
            }
        }).start();
        peer.next();
        peer.session.shutdown(Duration.ofSeconds(2));
        assertEquals("$/cancelRequest", peer.next().get("method").textValue());
        assertNotNull(peer.session.ended());
    }

    @Test
    public void theHandshakeRefusesAnotherFingerprintOrProtocol() throws Exception {
        for (String result : new String[]{
                "{\"protocol\":1,\"fingerprint\":\"" + "1".repeat(64) + "\",\"network\":{\"networkId\":0},\"contracts\":[]}",
                "{\"protocol\":2,\"fingerprint\":\"" + "0".repeat(64) + "\",\"network\":{\"networkId\":0},\"contracts\":[]}",
                "{\"protocol\":1,\"fingerprint\":\"" + "0".repeat(64) + "\",\"network\":{\"networkId\":1},\"contracts\":[]}"}) {
            java.nio.channels.Pipe toClient = java.nio.channels.Pipe.open(), toHost = java.nio.channels.Pipe.open();
            java.util.concurrent.atomic.AtomicInteger stops = new java.util.concurrent.atomic.AtomicInteger();
            var hostWrites = java.nio.channels.Channels.newOutputStream(toClient.sink());
            var fromClient = new HostFrames.Reader(java.nio.channels.Channels.newInputStream(toHost.source()));
            HostSession s = new HostSession(java.nio.channels.Channels.newInputStream(toClient.source()),
                    java.nio.channels.Channels.newOutputStream(toHost.sink()), stops::incrementAndGet, NO_READS, e -> { });
            new Thread(() -> {
                try {
                    long id = HostJson.parse(fromClient.next()).get("id").longValue();
                    byte[] b = ("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + result + "}").getBytes(StandardCharsets.UTF_8);
                    hostWrites.write(("Content-Length: " + b.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    hostWrites.write(b);
                    hostWrites.flush();
                } catch (Throwable ignored) {
                }
            }).start();
            ObjectNode params = HostJson.object();
            params.putObject("network").put("networkId", 0);
            try {
                s.initialize(params, "0".repeat(64), Duration.ofSeconds(10));
                fail("accepted " + result);
            } catch (HostSession.Ended expected) {
                assertTrue(s.violated());
                assertEquals("the host is stopped", 1, stops.get());
            }
            try {
                s.call("describe", HostJson.object(), Duration.ofSeconds(1), () -> false);
                fail("a call after a failed handshake");
            } catch (IllegalStateException | HostSession.Ended expected) {
                // refused
            }
        }
    }

    @Test
    public void noCallButInitializeBeforeTheHandshake() throws Exception {
        java.nio.channels.Pipe a = java.nio.channels.Pipe.open(), b = java.nio.channels.Pipe.open();
        HostSession s = new HostSession(java.nio.channels.Channels.newInputStream(a.source()),
                java.nio.channels.Channels.newOutputStream(b.sink()), () -> { }, NO_READS, e -> { });
        try {
            s.call("describe", HostJson.object(), Duration.ofSeconds(1), () -> false);
            fail("describe before initialize");
        } catch (IllegalStateException expected) {
            // refused locally (M31A-CDX-1)
        } finally {
            s.close();
        }
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
        java.util.concurrent.atomic.AtomicBoolean cancel = new java.util.concurrent.atomic.AtomicBoolean();
        new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
            cancel.set(true);
        }).start();
        try {
            peer.session.call("prepare", HostJson.object(), Duration.ofSeconds(30), cancel::get);
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
