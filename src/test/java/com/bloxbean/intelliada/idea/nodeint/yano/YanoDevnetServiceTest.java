package com.bloxbean.intelliada.idea.nodeint.yano;

import com.bloxbean.intelliada.idea.nodeint.yano.model.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Yano devnet service DTO parsing and request/response structure.
 * These tests verify the JSON contract without requiring a running Yano instance.
 */
class YanoDevnetServiceTest {

    @Test
    void testFundResponseParsing() {
        JSONObject json = new JSONObject();
        json.put("tx_hash", "abc123");
        json.put("index", 0);
        json.put("lovelace", 1000000000L);

        FundResponse response = new FundResponse(
                json.optString("tx_hash"),
                json.optInt("index"),
                json.optLong("lovelace")
        );

        assertEquals("abc123", response.getTxHash());
        assertEquals(0, response.getIndex());
        assertEquals(1000000000L, response.getLovelace());
    }

    @Test
    void testSnapshotResponseParsing() {
        JSONObject json = new JSONObject();
        json.put("name", "before-deploy");
        json.put("slot", 12345L);
        json.put("block_number", 100L);
        json.put("created_at", "2026-04-10T10:00:00Z");

        SnapshotResponse response = new SnapshotResponse(
                json.optString("name"),
                json.optLong("slot"),
                json.optLong("block_number"),
                json.optString("created_at")
        );

        assertEquals("before-deploy", response.getName());
        assertEquals(12345L, response.getSlot());
        assertEquals(100L, response.getBlockNumber());
        assertEquals("2026-04-10T10:00:00Z", response.getCreatedAt());
    }

    @Test
    void testRollbackResponseParsing() {
        JSONObject json = new JSONObject();
        json.put("message", "Rolled back successfully");
        json.put("slot", 500L);
        json.put("block_number", 50L);

        RollbackResponse response = new RollbackResponse(
                json.optString("message"),
                json.optLong("slot"),
                json.optLong("block_number")
        );

        assertEquals("Rolled back successfully", response.getMessage());
        assertEquals(500L, response.getSlot());
        assertEquals(50L, response.getBlockNumber());
    }

    @Test
    void testTimeAdvanceResponseParsing() {
        JSONObject json = new JSONObject();
        json.put("message", "Advanced");
        json.put("new_slot", 2000L);
        json.put("new_block_number", 200L);
        json.put("blocks_produced", 100);

        TimeAdvanceResponse response = new TimeAdvanceResponse(
                json.optString("message"),
                json.optLong("new_slot"),
                json.optLong("new_block_number"),
                json.optInt("blocks_produced")
        );

        assertEquals("Advanced", response.getMessage());
        assertEquals(2000L, response.getNewSlot());
        assertEquals(200L, response.getNewBlockNumber());
        assertEquals(100, response.getBlocksProduced());
    }

    @Test
    void testEpochShiftResponseParsing() {
        JSONObject json = new JSONObject();
        json.put("message", "Shifted");
        json.put("shift_millis", 432000000L);
        json.put("new_system_start", "2026-04-05T00:00:00Z");
        json.put("genesis_slot", 0L);

        EpochShiftResponse response = new EpochShiftResponse(
                json.optString("message"),
                json.optLong("shift_millis"),
                json.optString("new_system_start"),
                json.optLong("genesis_slot")
        );

        assertEquals("Shifted", response.getMessage());
        assertEquals(432000000L, response.getShiftMillis());
        assertEquals("2026-04-05T00:00:00Z", response.getNewSystemStart());
        assertEquals(0L, response.getGenesisSlot());
    }

    @Test
    void testFundRequestBody() {
        // Verify the request body structure for funding
        JSONObject body = new JSONObject();
        body.put("address", "addr_test1qz...");
        body.put("ada", 1000);

        assertTrue(body.has("address"));
        assertTrue(body.has("ada"));
        assertEquals("addr_test1qz...", body.getString("address"));
        assertEquals(1000, body.getInt("ada"));
    }

    @Test
    void testRollbackRequestVariants() {
        // By slot
        JSONObject bySlot = new JSONObject();
        bySlot.put("slot", 100L);
        assertTrue(bySlot.has("slot"));
        assertFalse(bySlot.has("block_number"));
        assertFalse(bySlot.has("count"));

        // By block number
        JSONObject byBlock = new JSONObject();
        byBlock.put("block_number", 50L);
        assertFalse(byBlock.has("slot"));
        assertTrue(byBlock.has("block_number"));

        // By count
        JSONObject byCount = new JSONObject();
        byCount.put("count", 10);
        assertFalse(byCount.has("slot"));
        assertTrue(byCount.has("count"));
    }

    @Test
    void testTimeAdvanceRequestVariants() {
        // By slots
        JSONObject bySlots = new JSONObject();
        bySlots.put("slots", 100);
        assertTrue(bySlots.has("slots"));
        assertFalse(bySlots.has("seconds"));
        assertFalse(bySlots.has("epochs"));

        // By epochs
        JSONObject byEpochs = new JSONObject();
        byEpochs.put("epochs", 5);
        assertTrue(byEpochs.has("epochs"));
    }

    @Test
    void testSnapshotListParsing() {
        JSONArray array = new JSONArray();
        array.put(new JSONObject().put("name", "snap1").put("slot", 100L).put("block_number", 10L).put("created_at", "2026-01-01"));
        array.put(new JSONObject().put("name", "snap2").put("slot", 200L).put("block_number", 20L).put("created_at", "2026-01-02"));

        assertEquals(2, array.length());
        assertEquals("snap1", array.getJSONObject(0).getString("name"));
        assertEquals("snap2", array.getJSONObject(1).getString("name"));
    }

    @Test
    void testNodeTypeYanoExists() {
        com.bloxbean.intelliada.idea.core.util.NodeType yano =
                com.bloxbean.intelliada.idea.core.util.NodeType.Yano;
        assertNotNull(yano);
        assertEquals("Yano Devnet", yano.getDisplayName());
    }

    @Test
    void testNodeTypeLookup() {
        com.bloxbean.intelliada.idea.core.util.NodeType result =
                com.bloxbean.intelliada.idea.core.util.NodeType.lookupByName("Yano");
        assertNotNull(result);
        assertEquals(com.bloxbean.intelliada.idea.core.util.NodeType.Yano, result);
    }
}
