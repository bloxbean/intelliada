package com.bloxbean.intelliada.idea.joust.host.chain;

import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** ADR-005 D18: the reverse-read adapter over a scripted Blockfrost-shaped server. */
public class ChainReadsTest {

    private HttpServer server;
    private final Map<String, String> routes = new ConcurrentHashMap<>();
    private final Map<String, Integer> status = new ConcurrentHashMap<>();
    private final AtomicInteger tipCalls = new AtomicInteger();
    private volatile boolean movingTip;
    /** When set, the tip body for the n-th tip read. */
    private volatile java.util.function.IntFunction<String> tipBody;
    private ChainReads reads;

    private static final String A = "addr_test1wrp6e300jwk22nphnklqx5j0wsj57qjcrfv4djf05xkuvug25n5yy";
    private static final String TX = "659ecb42a26b6a91c4e84854d96a279c8bd23c92cdd7a6889ea29be2bfb484c0";

    @Before
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().toString();
            String body;
            int code;
            if (path.equals("/api/v1/blocks/latest")) {
                int n = tipCalls.incrementAndGet();
                long slot = movingTip ? 1000 + n : 1000;
                body = tipBody != null ? tipBody.apply(n)
                        : "{\"slot\":" + slot + ",\"height\":50,\"hash\":\"" + "ab".repeat(32) + "\",\"epoch\":1}";
                code = 200;
            } else {
                body = routes.get(path);
                code = status.getOrDefault(path, body == null ? 404 : 200);
                if (body == null) body = "{\"status_code\":404}";
            }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        reads = new ChainReads(new BlockfrostApi("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1", null,
                Duration.ofSeconds(5)), 0, 32L * 1024 * 1024);
    }

    @After
    public void stop() {
        server.stop(0);
    }

    private static ObjectNode q(String json) throws Exception {
        return (ObjectNode) HostJson.MAPPER.readTree(json);
    }

    private static String utxo(int i) {
        return "{\"tx_hash\":\"" + TX + "\",\"output_index\":" + i + ",\"address\":\"" + A + "\",\"tx_index\":3,\"block\":\"x\","
                + "\"amount\":[{\"unit\":\"lovelace\",\"quantity\":\"" + (1000000 + i) + "\"}],\"data_hash\":null,"
                + "\"inline_datum\":null,\"reference_script_hash\":null}";
    }

    @Test
    public void theTipIsAChainPoint() throws Exception {
        JsonNode r = reads.read("1.1", q("{\"kind\":\"tip\"}"));
        assertEquals(1000, r.path("tip").path("slot").asLong());
        assertEquals(r.get("tip"), r.get("value"));
        assertEquals(2, r.size());
    }

    @Test
    public void utxosAreSnapshotEncodedAcrossPages() throws Exception {
        StringBuilder p1 = new StringBuilder("[");
        for (int i = 0; i < 100; i++) p1.append(i == 0 ? "" : ",").append(utxo(i));
        routes.put("/api/v1/addresses/" + A + "/utxos?count=100&page=1", p1.append("]").toString());
        routes.put("/api/v1/addresses/" + A + "/utxos?count=100&page=2", "[" + utxo(100) + "]");
        JsonNode r = reads.read("2.1", q("{\"kind\":\"utxos\",\"address\":\"" + A + "\"}"));
        assertEquals(101, r.path("value").size());
        JsonNode u = r.path("value").get(0);
        assertEquals("exactly the seven snapshot fields", 7, u.size());
        assertEquals("1000000", u.path("amount").get(0).path("quantity").textValue());
        assertFalse(r.has("more"));
    }

    @Test
    public void aLargeAnswerIsChunkedWithACallLocalCursor() throws Exception {
        StringBuilder p1 = new StringBuilder("[");
        String datum = "d8799f" + "41".repeat(20000) + "ff";
        for (int i = 0; i < 99; i++) {
            p1.append(i == 0 ? "" : ",").append(utxo(i).replace("\"inline_datum\":null", "\"inline_datum\":\"" + datum + "\""));
        }
        routes.put("/api/v1/addresses/" + A + "/utxos?count=100&page=1", p1.append("]").toString());
        JsonNode first = reads.read("3.1", q("{\"kind\":\"utxos\",\"address\":\"" + A + "\"}"));
        assertTrue(first.has("more"));
        int n = first.path("value").size();
        String cursor = first.path("more").textValue();
        JsonNode next = reads.read("3.2", q("{\"kind\":\"utxos\",\"address\":\"" + A + "\",\"cursor\":\"" + cursor + "\"}"));
        assertEquals("one chain point for the whole set", first.get("tip"), next.get("tip"));
        int total = n + next.path("value").size();
        while (next.has("more")) {
            next = reads.read("3.9", q("{\"kind\":\"utxos\",\"address\":\"" + A + "\",\"cursor\":\"" + next.path("more").textValue() + "\"}"));
            total += next.path("value").size();
        }
        assertEquals(99, total);
        try {
            reads.read("4.1", q("{\"kind\":\"utxos\",\"address\":\"" + A + "\",\"cursor\":\"" + cursor + "\"}"));
            fail("a cursor outlived its call");
        } catch (IllegalArgumentException expected) {
            // cursors are call-local
        }
    }

    @Test
    public void a404IsAbsentOnlyWhereAbsenceIsDefinedAndOtherErrorsFail() throws Exception {
        assertTrue(reads.read("5.1", q("{\"kind\":\"output\",\"ref\":\"" + TX + "#0\"}")).path("absent").booleanValue());
        status.put("/api/v1/txs/" + TX + "/utxos", 500);
        routes.put("/api/v1/txs/" + TX + "/utxos", "{}");
        JsonNode r = reads.read("5.2", q("{\"kind\":\"output\",\"ref\":\"" + TX + "#0\"}"));
        assertTrue(r.has("failed"));
        assertTrue(r.has("tip"));
        assertFalse(r.has("absent"));
    }

    @Test
    public void aStableAnswerUnderAMovingTipIsReportedAtTheLaterTip() throws Exception {
        routes.put("/api/v1/epochs/latest", "{\"epoch\":7}");
        routes.put("/api/v1/epochs/7/parameters", "{\"min_fee_a\":44}");
        movingTip = true;
        JsonNode r = reads.read("6.1", q("{\"kind\":\"protocolParams\"}"));
        assertEquals("the same answer before and after the later tip holds there (M31B-SEC-3)", 44, r.path("value").path("min_fee_a").asInt());
        assertTrue(r.path("tip").path("slot").asLong() > 1001);
    }

    @Test
    public void aChangingAnswerUnderAMovingTipFails() throws Exception {
        AtomicInteger n = new AtomicInteger();
        server.removeContext("/");
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().toString();
            String body = path.equals("/api/v1/blocks/latest")
                    ? "{\"slot\":" + (2000 + n.incrementAndGet()) + ",\"height\":50,\"hash\":\"" + "ab".repeat(32) + "\"}"
                    : path.startsWith("/api/v1/epochs/latest") && !path.contains("parameters") ? "{\"epoch\":7}"
                    : "{\"min_fee_a\":" + n.get() + "}";
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        JsonNode r = reads.read("6.2", q("{\"kind\":\"protocolParams\"}"));
        assertTrue("never a mixed chain point: " + r, r.has("failed"));
    }

    @Test
    public void aDrippingBodyIsBoundedByTheReadDeadline() throws Exception {
        server.removeContext("/");
        server.createContext("/", ex -> {
            ex.sendResponseHeaders(200, 0);
            try {
                for (int i = 0; i < 60; i++) {
                    ex.getResponseBody().write(' ');
                    ex.getResponseBody().flush();
                    Thread.sleep(1000);
                }
            } catch (Exception ignored) {
            }
            ex.close();
        });
        long t0 = System.nanoTime();
        try {
            reads.read("6.3", q("{\"kind\":\"protocolParams\"}"));
            fail("no chain point at all: the read is unanswerable");
        } catch (BlockfrostApi.Failed expected) {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertTrue("the body is bounded by the deadline (M31B-SEC-1): " + ms + " ms", ms < 15_000);
        }
    }

    @Test
    public void parametersAreValidatedBeforeAnyRequest() throws Exception {
        for (String bad : new String[]{
                "{\"kind\":\"utxos\",\"address\":\"addr1qx\"}",
                "{\"kind\":\"utxos\",\"address\":\"" + A + "/../x\"}",
                "{\"kind\":\"output\",\"ref\":\"" + TX + "\"}",
                "{\"kind\":\"script\",\"hash\":\"zz\"}",
                "{\"kind\":\"tip\",\"extra\":1}",
                "{\"kind\":\"datum\",\"hash\":\"" + TX + "\",\"x\":1}"}) {
            int before = tipCalls.get();
            try {
                reads.read("7.1", q(bad));
                fail("accepted " + bad);
            } catch (IllegalArgumentException expected) {
                assertEquals("no request was made for " + bad, before, tipCalls.get());
            }
        }
    }

    @Test
    public void anAnswerOverTheCallsBudgetFails() throws Exception {
        reads = new ChainReads(new BlockfrostApi("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1", null,
                Duration.ofSeconds(5)), 0, 10_000);
        StringBuilder p = new StringBuilder("[");
        for (int i = 0; i < 100; i++) p.append(i == 0 ? "" : ",").append(utxo(i));
        routes.put("/api/v1/addresses/" + A + "/utxos?count=100&page=1", p.append("]").toString());
        JsonNode r = reads.read("8.1", q("{\"kind\":\"utxos\",\"address\":\"" + A + "\"}"));
        assertTrue(r.toString(), r.has("failed"));
        assertFalse("no partial set", r.has("value"));
    }

    @Test
    public void scriptsAreDoubleWrappedAndTyped() throws Exception {
        String h = "5de2e73fb4674a846230cb548335f0e095c3997043fcedfc7fc9c4ed";
        routes.put("/api/v1/scripts/" + h, "{\"type\":\"plutusV3\"}");
        routes.put("/api/v1/scripts/" + h + "/cbor", "{\"cbor\":\"4401020304\"}");
        JsonNode r = reads.read("9.1", q("{\"kind\":\"script\",\"hash\":\"" + h + "\"}"));
        assertEquals("PlutusScriptV3", r.path("value").path("type").textValue());
        assertEquals("454401020304", r.path("value").path("cborHex").textValue());
        assertEquals("454401020304", PlutusScripts.doubleWrapped("454401020304"));
    }

    @Test
    public void aCollateralReturnIsNeverAnOutput() throws Exception {
        routes.put("/api/v1/txs/" + TX + "/utxos", "{\"outputs\":[" + utxo(0).replace("\"tx_index\":3,", "\"collateral\":true,") + "," + utxo(1) + "]}");
        assertTrue("a flagged collateral return is skipped (M31B-CDX-12)",
                reads.read("10.1", q("{\"kind\":\"output\",\"ref\":\"" + TX + "#0\"}")).path("absent").booleanValue());
        assertEquals(1, reads.read("10.2", q("{\"kind\":\"output\",\"ref\":\"" + TX + "#1\"}")).path("value").path("output_index").intValue());
    }

    @Test
    public void malformedSuccessBodiesAreFailuresNotAbsence() throws Exception {
        routes.put("/api/v1/txs/" + TX + "/utxos", "{}");
        assertTrue(reads.read("11.1", q("{\"kind\":\"output\",\"ref\":\"" + TX + "#0\"}")).has("failed"));
        String dh = "ab".repeat(32);
        routes.put("/api/v1/scripts/datum/" + dh + "/cbor", "{}");
        assertTrue(reads.read("11.2", q("{\"kind\":\"datum\",\"hash\":\"" + dh + "\"}")).has("failed"));
        routes.put("/api/v1/txs/" + TX + "/redeemers", "[{\"purpose\":\"spend\"}]");
        assertTrue("a redeemer missing fields fails (M31B-CDX-5)", reads.read("11.3", q("{\"kind\":\"redeemers\",\"txHash\":\"" + TX + "\"}")).has("failed"));
    }

    @Test
    public void anEmptyRedeemerListOfAnUnknownTransactionIsAbsent() throws Exception {
        routes.put("/api/v1/txs/" + TX + "/redeemers", "[]");
        assertTrue("[] for an unknown transaction is absent (M31B-CDX-13)",
                reads.read("12.1", q("{\"kind\":\"redeemers\",\"txHash\":\"" + TX + "\"}")).path("absent").booleanValue());
        routes.put("/api/v1/txs/" + TX, "{\"hash\":\"" + TX + "\"}");
        assertEquals(0, reads.read("12.2", q("{\"kind\":\"redeemers\",\"txHash\":\"" + TX + "\"}")).path("value").size());
    }

    @Test
    public void aContinuationServesOnlyTheQueryThatOpenedIt() throws Exception {
        StringBuilder p1 = new StringBuilder("[");
        String datum = "d8799f" + "41".repeat(20000) + "ff";
        for (int i = 0; i < 99; i++) {
            p1.append(i == 0 ? "" : ",").append(utxo(i).replace("\"inline_datum\":null", "\"inline_datum\":\"" + datum + "\""));
        }
        routes.put("/api/v1/addresses/" + A + "/utxos?count=100&page=1", p1.append("]").toString());
        JsonNode first = reads.read("13.1", q("{\"kind\":\"utxos\",\"address\":\"" + A + "\"}"));
        String cursor = first.path("more").textValue();
        String other = "addr_test1qrp6e300jwk22nphnklqx5j0wsj57qjcrfv4djf05xkuvug25n5yy";
        try {
            reads.read("13.2", q("{\"kind\":\"utxos\",\"address\":\"" + other + "\",\"cursor\":\"" + cursor + "\"}"));
            fail("another address consumed the cursor (M31B-CDX-11)");
        } catch (IllegalArgumentException expected) {
            // refused, and the cursor is still the original query's
        }
        assertTrue(reads.read("13.3", q("{\"kind\":\"utxos\",\"address\":\"" + A + "\",\"cursor\":\"" + cursor + "\"}")).has("value"));
    }

    @Test
    public void anItemLargerThanAChunkFailsTheRead() throws Exception {
        String datum = "d8799f" + "41".repeat(700_000) + "ff";
        routes.put("/api/v1/addresses/" + A + "/utxos?count=100&page=1", "[" + utxo(0).replace("\"inline_datum\":null", "\"inline_datum\":\"" + datum + "\"") + "]");
        JsonNode r = reads.read("14.1", q("{\"kind\":\"utxos\",\"address\":\"" + A + "\"}"));
        assertTrue("never above the 1 MiB chunk (M31B-CDX-3): " + r.toString().length(), r.has("failed"));
    }

    @Test
    public void aProviderFailureAfterAChainPointIsFailedNotACancellation() throws Exception {
        assertEquals(1000, reads.read("15.1", q("{\"kind\":\"tip\"}")).path("tip").path("slot").intValue());
        status.put("/api/v1/blocks/latest", 503);
        server.removeContext("/");
        server.createContext("/", ex -> {
            byte[] b = "{}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(503, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        JsonNode r = reads.read("15.2", q("{\"kind\":\"tip\"}"));
        assertTrue("failed with the call's last chain point (M31B-DES-8)", r.has("failed") && r.path("tip").path("slot").intValue() == 1000);
    }

    private static String tipAt(long slot, long height, String hashByte) {
        return "{\"slot\":" + slot + ",\"height\":" + height + ",\"hash\":\"" + hashByte.repeat(32) + "\"}";
    }

    @Test
    public void chainPointsNeverGoBackWithinACall() throws Exception {
        // The first read sees height 101; every later tip read answers height 100 (a lagging backend).
        tipBody = n -> n == 1 ? tipAt(2001, 101, "cd") : tipAt(2000, 100, "ab");
        JsonNode first = reads.read("20.1", q("{\"kind\":\"tip\"}"));
        assertEquals(101, first.path("tip").path("height").intValue());
        JsonNode second = reads.read("20.2", q("{\"kind\":\"tip\"}"));
        assertTrue("failed, not a lower point (M31B-CDX-22): " + second, second.has("failed"));
        assertEquals("reported at the last point handed out", 101, second.path("tip").path("height").intValue());
        routes.put("/api/v1/epochs/latest", "{\"epoch\":7}");
        routes.put("/api/v1/epochs/7/parameters", "{\"min_fee_a\":44}");
        JsonNode third = reads.read("20.3", q("{\"kind\":\"protocolParams\"}"));
        assertTrue(third.has("failed") && third.path("tip").path("height").intValue() == 101);
        // A new call starts afresh.
        assertEquals(100, reads.read("21.1", q("{\"kind\":\"tip\"}")).path("tip").path("height").intValue());
    }

    @Test
    public void aLaggingTipIsReadAgainWithinTheDeadline() throws Exception {
        tipBody = n -> n == 1 ? tipAt(2001, 101, "cd") : n <= 3 ? tipAt(2000, 100, "ab") : tipAt(2002, 102, "ef");
        reads.read("22.1", q("{\"kind\":\"tip\"}"));
        JsonNode r = reads.read("22.2", q("{\"kind\":\"tip\"}"));
        assertEquals("the backend caught up", 102, r.path("tip").path("height").intValue());
    }

    @Test
    public void fractionalOrMissingIndicesAreMalformed() throws Exception {
        routes.put("/api/v1/txs/" + TX + "/utxos", "{\"outputs\":[" + utxo(0).replace("\"output_index\":0", "\"output_index\":1.5") + "]}");
        assertTrue("1.5 is not an index (M31B-CDX-21)", reads.read("23.1", q("{\"kind\":\"output\",\"ref\":\"" + TX + "#1\"}")).has("failed"));
        routes.put("/api/v1/txs/" + TX + "/utxos", "{\"outputs\":[" + utxo(0).replace("\"output_index\":0,", "") + "]}");
        assertTrue("a row without an index is malformed, not absent",
                reads.read("23.2", q("{\"kind\":\"output\",\"ref\":\"" + TX + "#0\"}")).has("failed"));
        tipBody = n -> "{\"slot\":-5,\"height\":1,\"hash\":\"" + "ab".repeat(32) + "\"}";
        try {
            reads.read("24.1", q("{\"kind\":\"tip\"}"));
            fail("a negative slot was a chain point");
        } catch (BlockfrostApi.Failed expected) {
            // no chain point at all
        }
    }

    @Test
    public void theWholeListIsCheckedBeforeTheFirstChunk() throws Exception {
        String big = "d8799f" + "41".repeat(700_000) + "ff";
        routes.put("/api/v1/addresses/" + A + "/utxos?count=100&page=1",
                "[" + utxo(0) + "," + utxo(1).replace("\"inline_datum\":null", "\"inline_datum\":\"" + big + "\"") + "]");
        JsonNode r = reads.read("25.1", q("{\"kind\":\"utxos\",\"address\":\"" + A + "\"}"));
        assertTrue("no partial first chunk (M31B-CDX-23): " + r.toString().length(), r.has("failed"));
        assertFalse(r.has("more"));
    }

    @Test
    public void anExhaustedBudgetFailsEveryRequestIncludingTheTip() throws Exception {
        ChainReads small = new ChainReads(new BlockfrostApi("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1", null,
                Duration.ofSeconds(5)), 0, 480);
        JsonNode first = small.read("26.1", q("{\"kind\":\"tip\"}"));
        assertTrue(first.toString(), first.has("value"));
        JsonNode r = small.read("26.2", q("{\"kind\":\"tip\"}"));
        assertTrue("the tip request is under the budget too (M31B-CDX-16): " + r, r.has("failed"));
    }

    @Test
    public void theRewardAddressFollowsCip19() {
        String r = StakeAddresses.reward("dd15fe5da434a91d00aa7f3a6891b77a59cfd4e442506d47189b703d", true, 0);
        assertTrue(r, r.startsWith("stake_test1"));
    }
}
