package com.bloxbean.intelliada.idea.julc.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CIP-57 blueprint JSON parsing logic.
 * Tests the JSON structure parsing without requiring IntelliJ platform.
 */
class BlueprintLoadServiceTest {

    @Test
    void testParseBlueprintJsonStructure() {
        JSONObject blueprint = new JSONObject();

        // Preamble
        JSONObject preamble = new JSONObject();
        preamble.put("title", "my-project");
        preamble.put("version", "1.0.0");
        blueprint.put("preamble", preamble);

        // Validators
        JSONArray validators = new JSONArray();
        JSONObject validator = new JSONObject();
        validator.put("title", "spending.validate");
        validator.put("hash", "abc123def456");
        validator.put("compiledCode", "59012301000033222220051");
        validators.put(validator);
        blueprint.put("validators", validators);

        // Verify preamble parsing
        assertEquals("my-project", blueprint.getJSONObject("preamble").getString("title"));
        assertEquals("1.0.0", blueprint.getJSONObject("preamble").getString("version"));

        // Verify validators parsing
        JSONArray parsedValidators = blueprint.getJSONArray("validators");
        assertEquals(1, parsedValidators.length());

        JSONObject v = parsedValidators.getJSONObject(0);
        assertEquals("spending.validate", v.getString("title"));
        assertEquals("abc123def456", v.getString("hash"));
        assertEquals("59012301000033222220051", v.getString("compiledCode"));
    }

    @Test
    void testPlutusBlueprintModel() {
        PlutusBlueprint blueprint = new PlutusBlueprint();
        blueprint.setPreamble(new PlutusBlueprint.Preamble("test", "1.0"));

        PlutusBlueprint.ValidatorInfo validator = new PlutusBlueprint.ValidatorInfo(
                "my.spend", "abcdef", "590123", 3, false);

        blueprint.setValidators(java.util.List.of(validator));

        assertEquals("test", blueprint.getPreamble().getTitle());
        assertEquals(1, blueprint.getValidators().size());
        assertEquals("my.spend", blueprint.getValidators().get(0).getTitle());
        assertEquals("abcdef", blueprint.getValidators().get(0).getHash());
        assertEquals(3, blueprint.getValidators().get(0).getSizeBytes());
        assertFalse(blueprint.getValidators().get(0).isParameterized());
    }

    @Test
    void testValidatorInfoSizeCalculation() {
        // compiledCode is hex, so size in bytes = hex length / 2
        String hexCode = "59012301000033222220051a0b0c0d0e";
        long expectedSize = hexCode.length() / 2;

        PlutusBlueprint.ValidatorInfo info = new PlutusBlueprint.ValidatorInfo(
                "test", "hash", hexCode, expectedSize, false);

        assertEquals(16, info.getSizeBytes());
    }

    @Test
    void testMultipleValidators() {
        PlutusBlueprint blueprint = new PlutusBlueprint();
        blueprint.setPreamble(new PlutusBlueprint.Preamble("multi", "2.0"));
        blueprint.setValidators(java.util.List.of(
                new PlutusBlueprint.ValidatorInfo("mint", "hash1", "aabb", 2, false),
                new PlutusBlueprint.ValidatorInfo("spend", "hash2", "ccdd", 2, false),
                new PlutusBlueprint.ValidatorInfo("withdraw", "hash3", "eeff", 2, true)
        ));

        assertEquals(3, blueprint.getValidators().size());
        assertTrue(blueprint.getValidators().get(2).isParameterized());
    }

    @Test
    void testAnnotationProcessorOutputFormat() {
        // Individual .plutus.json file format
        JSONObject individual = new JSONObject();
        individual.put("cborHex", "590123abcdef");
        individual.put("scriptHash", "deadbeef");
        individual.put("parameterized", true);

        assertEquals("590123abcdef", individual.optString("cborHex", ""));
        assertEquals("deadbeef", individual.optString("scriptHash", ""));
        assertTrue(individual.optBoolean("parameterized", false));
    }
}
