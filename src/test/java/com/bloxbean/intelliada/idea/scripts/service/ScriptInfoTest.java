package com.bloxbean.intelliada.idea.scripts.service;

import com.bloxbean.cardano.client.transaction.spec.script.*;
import com.bloxbean.intelliada.idea.scripts.exception.ScriptGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import org.junit.Test;

import static org.junit.Assert.*;

public class ScriptInfoTest {

    @Test
    public void testSerialization() throws ScriptGenerationException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerSubtypes(new NamedType(ScriptPubkey.class, "ScriptPubkey"));
        objectMapper.registerSubtypes(new NamedType(ScriptAll.class, "ScriptAll"));
        objectMapper.registerSubtypes(new NamedType(ScriptAny.class, "ScriptAny"));
        objectMapper.registerSubtypes(new NamedType(ScriptAtLeast.class, "ScriptAtLeast"));
        objectMapper.registerSubtypes(new NamedType(RequireTimeAfter.class, "RequireTimeAfter"));
        objectMapper.registerSubtypes(new NamedType(RequireTimeBefore.class, "RequireTimeBefore"));

        ScriptService scriptService = new ScriptService();
        ScriptInfo scriptInfo = scriptService.generateNewScriptPubkey("test");

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(scriptInfo);
        System.out.println(json);

        ScriptInfo script = objectMapper.readValue(json, ScriptInfo.class);
        System.out.println(script);
    }

}
