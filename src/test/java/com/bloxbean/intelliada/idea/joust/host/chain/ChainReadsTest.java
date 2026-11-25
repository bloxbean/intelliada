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
                body = "{\"slot\":" + slot + ",\"height\":50,\"hash\":\"" + "ab".repeat(32) + "\",\"epoch\":1}";
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
    public void aMovingTipIsRetriedThenFails() throws Exception {
        routes.put("/api/v1/epochs/latest", "{\"epoch\":7}");
        routes.put("/api/v1/epochs/7/parameters", "{\"min_fee_a\":44}");
        assertEquals(44, reads.read("6.0", q("{\"kind\":\"protocolParams\"}")).path("value").path("min_fee_a").asInt());
        movingTip = true;
        tipCalls.set(0);
        JsonNode r = reads.read("6.1", q("{\"kind\":\"protocolParams\"}"));
        assertTrue(r.toString(), r.has("failed"));
        assertTrue(tipCalls.get() >= 16);
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
    public void theRewardAddressFollowsCip19() {
        String r = StakeAddresses.reward("dd15fe5da434a91d00aa7f3a6891b77a59cfd4e442506d47189b703d", true, 0);
        assertTrue(r, r.startsWith("stake_test1"));
    }
}
