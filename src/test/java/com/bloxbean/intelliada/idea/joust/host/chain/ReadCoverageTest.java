package com.bloxbean.intelliada.idea.joust.host.chain;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * ADR-005 G3.4 (tool-gated on {@code JOUST_DEVKIT_STORE}): every read kind against a live Blockfrost-shaped provider,
 * each outcome recorded (value, absent, unsupported or failed). Printed as the coverage table the gate asks for.
 */
public class ReadCoverageTest {

    @Test
    public void everyReadKindHasAnOutcomeOnTheProvider() throws Exception {
        String store = JoustTestTools.env("JOUST_DEVKIT_STORE", "everyReadKindHasAnOutcomeOnTheProvider");
        if (store == null) return;
        BlockfrostApi api = new BlockfrostApi(store, null, Duration.ofSeconds(10));
        ChainReads reads = new ChainReads(api, 0, 32L * 1024 * 1024);
        JsonNode b1 = api.get("/blocks/1");
        String someTx = null;
        JsonNode txs = api.get("/blocks/1/txs");
        if (txs != null && txs.isArray() && txs.size() > 0) someTx = txs.get(0).isTextual() ? txs.get(0).textValue() : txs.get(0).path("tx_hash").textValue();
        String tx = someTx != null ? someTx : "0".repeat(64);
        String addr = "addr_test1qryvgass5dsrf2kxl3vgfz76uhp83kv5lagzcp29tcana68ca5aqa6swlq6llfamln09tal7n5kvt4275ckwedpt4v7q48uhex";
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("tip", "{\"kind\":\"tip\"}");
        queries.put("protocolParams", "{\"kind\":\"protocolParams\"}");
        queries.put("utxos", "{\"kind\":\"utxos\",\"address\":\"" + addr + "\"}");
        queries.put("output", "{\"kind\":\"output\",\"ref\":\"" + tx + "#0\"}");
        queries.put("script", "{\"kind\":\"script\",\"hash\":\"" + "0".repeat(56) + "\"}");
        queries.put("stakeRegistration", "{\"kind\":\"stakeRegistration\",\"credential\":\"" + "1".repeat(56) + "\"}");
        queries.put("assetHistory", "{\"kind\":\"assetHistory\",\"unit\":\"" + "2".repeat(56) + "\"}");
        queries.put("redeemers", "{\"kind\":\"redeemers\",\"txHash\":\"" + tx + "\"}");
        queries.put("datum", "{\"kind\":\"datum\",\"hash\":\"" + "3".repeat(64) + "\"}");
        StringBuilder table = new StringBuilder("G3.4 read coverage at " + store + " (block 1 " + (b1 == null ? "?" : b1.path("hash").textValue()) + ")\n");
        int gen = 1;
        for (var e : queries.entrySet()) {
            JsonNode r = reads.read((gen++) + ".1", HostJson.MAPPER.readTree(e.getValue()));
            String outcome = r.has("value") ? "value" : r.has("absent") ? "absent" : r.has("unsupported") ? "unsupported" : "failed";
            table.append(String.format("  %-18s %s%n", e.getKey(), outcome + (r.has("failed") ? " (" + r.path("failed").path("message").textValue() + ")" : "")));
            assertTrue(e.getKey() + " has a chain point", r.path("tip").path("slot").canConvertToLong());
            assertFalse(e.getKey() + " is answered without a provider failure: " + r, r.has("failed"));
        }
        System.out.println(table);
    }
}
