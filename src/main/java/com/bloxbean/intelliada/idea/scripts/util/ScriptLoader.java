package com.bloxbean.intelliada.idea.scripts.util;

import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.util.List;

public class ScriptLoader {

    private ObjectMapper mapper;
    public ScriptLoader() {
        mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(NativeScript.class, new NativeScriptDeserializer());
        module.addDeserializer(NativeScript[].class, new NativeScriptListDeserializer());
        mapper.registerModule(module);
    }

    public NativeScript loadScriptFromJson(String json) throws JsonProcessingException {
        NativeScript nativeScript = mapper.readValue(json, NativeScript.class);

        return nativeScript;
    }
}
