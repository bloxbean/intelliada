package com.bloxbean.intelliada.idea.scripts.util;

import com.bloxbean.cardano.client.transaction.spec.script.*;
import com.bloxbean.intelliada.idea.scripts.exception.JsonParsingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class ScriptParser {
    private static ObjectMapper objectMapper = new ObjectMapper();

    public static NativeScript parse(String json) throws JsonProcessingException {
        JsonNode jsonNode = objectMapper.readTree(json);
        return parse(jsonNode);
    }

    public static NativeScript parse(JsonNode node) {
        String type = node.get("type").asText();
        if (ScriptType.sig.toString().equals(type)) {
            return deserializeScriptSig(node);
        } else if (ScriptType.all.toString().equals(type)) {
            return deserializeScriptAll(node);
        } else if (ScriptType.any.toString().equals(type)) {
            return deserializeScriptAny(node);
        } else if (ScriptType.atLeast.toString().equals(type)) {
            return deserializeScriptAtLeast(node);
        } else if (ScriptType.before.toString().equals(type)) {
            return deserializeRequireTimeBefore(node);
        } else if (ScriptType.after.toString().equals(type)) {
            return deserializeRequireTimeAfter(node);
        } else {
            throw new JsonParsingException("Invalid type : " + type);
        }
    }

    public static ScriptPubkey deserializeScriptSig(JsonNode node) {
        String type = node.get("type").asText();
        String keyHash = node.get("keyHash").asText();
        ScriptPubkey scriptPubkey = new ScriptPubkey();
        scriptPubkey.setKeyHash(keyHash);

        return scriptPubkey;
    }

    public static ScriptAll deserializeScriptAll(JsonNode node) {
        JsonNode scriptsNode = node.get("scripts");
        if (scriptsNode == null || !scriptsNode.isArray())
            throw new JsonParsingException("Json processing error");

        ScriptAll scriptAll = new ScriptAll();
        ArrayNode array = (ArrayNode) scriptsNode;
        for (JsonNode nd : array) {
            NativeScript script = parse(nd);
            if (script != null) {
                scriptAll.getScripts().add(script);
            }
        }

        return scriptAll;
    }

    public static ScriptAny deserializeScriptAny(JsonNode node) {
        JsonNode scriptsNode = node.get("scripts");
        if (scriptsNode == null || !scriptsNode.isArray())
            throw new JsonParsingException("Json processing error");

        ScriptAny scriptAny = new ScriptAny();
        ArrayNode array = (ArrayNode) scriptsNode;
        for (JsonNode nd : array) {
            NativeScript script = parse(nd);
            if (script != null) {
                scriptAny.getScripts().add(script);
            }
        }

        return scriptAny;
    }

    public static ScriptAtLeast deserializeScriptAtLeast(JsonNode node) {
        JsonNode scriptsNode = node.get("scripts");
        if (scriptsNode == null || !scriptsNode.isArray())
            throw new JsonParsingException("Json processing error");

        int required = node.get("required").asInt();
        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(required);
        ArrayNode array = (ArrayNode) scriptsNode;
        for (JsonNode nd : array) {
            NativeScript script = parse(nd);
            if (script != null) {
                scriptAtLeast.getScripts().add(script);
            }
        }

        return scriptAtLeast;
    }

    public static RequireTimeBefore deserializeRequireTimeBefore(JsonNode node) {
        String type = node.get("type").asText();
        long slot = node.get("slot").asLong();

        RequireTimeBefore requireTimeBefore = new RequireTimeBefore(slot);
        return requireTimeBefore;
    }

    public static RequireTimeAfter deserializeRequireTimeAfter(JsonNode node) {
        String type = node.get("type").asText();
        long slot = node.get("slot").asLong();

        RequireTimeAfter requireTimeAfter = new RequireTimeAfter(slot);
        return requireTimeAfter;
    }
}
