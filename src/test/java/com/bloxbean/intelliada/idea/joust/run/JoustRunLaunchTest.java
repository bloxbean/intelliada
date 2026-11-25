package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/** M3.1b round 2: Stop through the whole launch, listing continuations, and re-binding. */
public class JoustRunLaunchTest {

    @Test
    public void stopWhileBindingNeverStartsTheHost() throws Exception {
        JoustRunLaunch l = new JoustRunLaunch();
        CountDownLatch binding = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean started = new AtomicBoolean();
        CompletableFuture<Object> f = CompletableFuture.supplyAsync(() -> {
            try {
                return l.run(x -> {
                    binding.countDown();
                    release.await();
                    return "bound";
                }, b -> {
                    started.set(true);
                    return "host";
                }, r -> fail("nothing to stop"), () -> false);
            } catch (Exception e) {
                return e;
            }
        });
        assertTrue(binding.await(5, TimeUnit.SECONDS));
        l.cancel();   // Stop pressed while the identity reads are pending (M31B-CDX-17)
        release.countDown();
        assertTrue(f.get(5, TimeUnit.SECONDS) instanceof JoustRunLaunch.Cancelled);
        assertFalse("the host was never started", started.get());
    }

    @Test
    public void stopWhileStartingStopsTheHostItGot() throws Exception {
        JoustRunLaunch l = new JoustRunLaunch();
        AtomicBoolean stopped = new AtomicBoolean();
        try {
            l.run(x -> "bound", b -> {
                l.cancel();
                return "host";
            }, r -> stopped.set(r.equals("host")), () -> false);
            fail("a cancelled launch returned a host");
        } catch (JoustRunLaunch.Cancelled expected) {
            assertTrue(stopped.get());
        }
        try {
            new JoustRunLaunch().run(x -> "bound", b -> "host", r -> { }, () -> true);
            fail("the indicator's cancel was ignored");
        } catch (JoustRunLaunch.Cancelled expected) {
            // refused before binding
        }
    }

    @Test
    public void aContinuationBelongsToItsSelection() throws Exception {
        Object s = new Object();
        var r = HostJson.MAPPER.readTree("{\"items\":[],\"listing\":\"L1\",\"next\":4294967296}");
        JoustRunPager p = JoustRunPager.next(r, s, "objects", "C", "i1");
        assertNotNull(p);
        assertEquals("a long offset is kept (M31B-DES-13)", 4294967296L, p.after());
        assertTrue(p.belongsTo(s, "C", "i1"));
        assertFalse("another instance (M31B-CDX-20)", p.belongsTo(s, "C", "i2"));
        assertFalse("another contract (M31B-CDX-19)", p.belongsTo(s, "D", "i1"));
        assertFalse("another session", p.belongsTo(new Object(), "C", "i1"));
        assertNull(JoustRunPager.next(HostJson.MAPPER.readTree("{\"items\":[]}"), s, "instances", "C", null));
        JoustRunPager inst = JoustRunPager.next(HostJson.MAPPER.readTree("{\"listing\":\"L2\",\"next\":100}"), s, "instances", "C", null);
        assertTrue("an instances listing ignores the selected instance", inst.belongsTo(s, "C", "anything"));
    }

    @Test
    public void anEditedOrRemovedConfigurationIsAChange() {
        RemoteNode n = new RemoteNode();
        n.setId("n1");
        n.setNodeType(NodeType.LocalYaciDevKit);
        n.setApiEndpoint("http://localhost:8080/api/v1");
        n.setProtocolMagic("42");
        JoustRunSession.Bound bound = JoustRunSession.Bound.of(n);
        assertNull(JoustRunSession.configChange(bound, List.of(n)));
        n.setApiEndpoint("http://localhost:9090/api/v1");   // edited in place, as RemoteNodeState does (M31B-CDX-15)
        assertEquals("the node configuration changed", JoustRunSession.configChange(bound, List.of(n)));
        assertEquals("the node configuration was removed", JoustRunSession.configChange(bound, List.of()));
    }

    @Test
    public void onlyAValidDifferentBlockOneIsARecreatedDevnet() throws Exception {
        String h = "ab".repeat(32);
        NetworkIdentity.Identity id = new NetworkIdentity.Identity(NetworkIdentity.NetworkClass.DEVNET, 0, 42,
                0, 1_000_000L, 1000, 300, "devnet-42-" + h);
        assertNull(JoustRunSession.blockOne(id, HostJson.MAPPER.readTree("{\"hash\":\"" + h + "\"}")));
        assertEquals("the devnet behind the node was recreated",
                JoustRunSession.blockOne(id, HostJson.MAPPER.readTree("{\"hash\":\"" + "cd".repeat(32) + "\"}")));
        for (String bad : new String[]{null, "{}", "{\"hash\":\"zz\"}"}) {
            try {
                JoustRunSession.blockOne(id, bad == null ? null : HostJson.MAPPER.readTree(bad));
                fail("an unreadable block 1 was a verdict (M31B-CDX-26)");
            } catch (NetworkIdentity.Refused expected) {
                assertTrue(expected.getMessage().contains("unavailable"));
            }
        }
    }

    @Test
    public void aHostThatEndedIsNeverPublishedWhicheverCallbackComesFirst() {
        JoustRunLaunch endedFirst = new JoustRunLaunch();
        endedFirst.markEnded("exited");                     // ended(why) reached the EDT before the completion
        assertFalse(endedFirst.publishable(null));
        JoustRunLaunch completedFirst = new JoustRunLaunch();
        assertFalse("the session itself reports the end (M31B-CDX-31)", completedFirst.publishable("exited"));
        assertTrue(new JoustRunLaunch().publishable(null));
        JoustRunLaunch stopped = new JoustRunLaunch();
        stopped.cancel();
        assertFalse(stopped.publishable(null));
    }

    @Test
    public void aMalformedTipSlotWithholdsTheCallAndLeavesTheFloor() throws Exception {
        java.util.concurrent.atomic.AtomicLong floor = new java.util.concurrent.atomic.AtomicLong(50);
        for (String bad : new String[]{"{\"slot\":1.5}", "{\"slot\":-1}", "{\"slot\":\"7\"}", "{}"}) {
            try {
                JoustRunSession.monotone(floor, JoustRunSession.tipSlot(HostJson.MAPPER.readTree(bad)));
                fail("accepted " + bad + " (M31B-CDX-33)");
            } catch (com.bloxbean.intelliada.idea.joust.host.chain.BlockfrostApi.Failed expected) {
                assertEquals(50, floor.get());
            }
        }
        assertEquals(60, JoustRunSession.tipSlot(HostJson.MAPPER.readTree("{\"slot\":60}")));
    }

    @Test
    public void anEditDuringTheIdentityReadsRefusesTheBinding() throws Exception {
        java.util.concurrent.atomic.AtomicInteger adminCalls = new java.util.concurrent.atomic.AtomicInteger();
        CountDownLatch secondAsked = new CountDownLatch(1), release = new CountDownLatch(1);
        String h = "ab".repeat(32);
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", ex -> {
            String p = ex.getRequestURI().getPath();
            String body = switch (p) {
                case "/local-cluster/api/admin/devnet" -> {
                    if (adminCalls.incrementAndGet() == 2) {   // the second bind's identity read is held open
                        secondAsked.countDown();
                        try {
                            release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException ignored) {
                        }
                    }
                    yield "{\"protocolMagic\":42,\"startTime\":1000}";
                }
                case "/api/v1/genesis" -> "{\"network_magic\":42,\"system_start\":1000,\"slot_length\":1,\"security_param\":100,\"active_slots_coefficient\":1.0}";
                case "/api/v1/blocks/1" -> "{\"hash\":\"" + h + "\"}";
                default -> null;
            };
            byte[] b = (body == null ? "{}" : body).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.sendResponseHeaders(body == null ? 404 : 200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            RemoteNode n = new RemoteNode();
            n.setId("n1");
            n.setNodeType(NodeType.LocalYaciDevKit);
            n.setApiEndpoint(base + "/api/v1");
            n.setProtocolMagic("42");
            assertEquals("unchanged: bound", "devnet-42-" + h, JoustRunSession.bind(n, base, () -> List.of(n)).identity().chainKey());
            CompletableFuture<Object> f = CompletableFuture.supplyAsync(() -> {
                try {
                    return JoustRunSession.bind(n, base, () -> List.of(n));
                } catch (Exception e) {
                    return e;
                }
            });
            assertTrue(secondAsked.await(10, TimeUnit.SECONDS));
            n.setApiEndpoint("http://localhost:9999/api/v1");   // edited in place while the identity read is pending
            release.countDown();
            Object r = f.get(15, TimeUnit.SECONDS);
            assertTrue("bound one endpoint under another's values (M31B-CDX-28): " + r,
                    r instanceof Exception && ((Exception) r).getMessage().contains("changed"));
        } finally {
            release.countDown();
            server.stop(0);
        }
    }
}
