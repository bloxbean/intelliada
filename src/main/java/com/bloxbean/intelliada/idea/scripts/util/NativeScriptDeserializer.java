package com.bloxbean.intelliada.idea.scripts.util;

import com.bloxbean.cardano.client.transaction.spec.script.*;
import com.bloxbean.intelliada.idea.util.JsonUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.intellij.openapi.util.text.StringUtil;

import java.io.IOException;

public class NativeScriptDeserializer extends StdDeserializer<NativeScript> {

    public NativeScriptDeserializer() {
        this(null);
    }

    protected NativeScriptDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public NativeScript deserialize(JsonParser jp, DeserializationContext context) throws IOException, JsonProcessingException {
        JsonNode node = jp.getCodec().readTree(jp);

        JsonNode typeNode = node.get("type");
        if(typeNode == null)
            return null;

        String type = typeNode.asText();
        if(StringUtil.isEmpty(type))
            return null;

        String json = node.toString();

        NativeScript script = null;
        switch (type) {
            case "sig":
                script = JsonUtil.parseToObject(json, ScriptPubkey.class);
                break;
            case "all":
                script = JsonUtil.parseToObject(json, ScriptAll.class);
                break;
            case "any":
                script = JsonUtil.parseToObject(json, ScriptAny.class);
                break;
            case "after":
                script = JsonUtil.parseToObject(json, RequireTimeAfter.class);
                break;
            case "before":
                script = JsonUtil.parseToObject(json, RequireTimeBefore.class);
                break;
        }

        return script;
    }
}
