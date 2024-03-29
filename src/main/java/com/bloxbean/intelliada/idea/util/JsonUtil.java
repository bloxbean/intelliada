package com.bloxbean.intelliada.idea.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.intellij.openapi.util.text.StringUtil;

import java.util.Collection;

public class JsonUtil {
    protected final static ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static String getPrettyJson(Object obj) {
        if (obj == null) return null;
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    public static String getPrettyJson(String jsonStr) {
        if (jsonStr == null)
            return null;

        try {
            Object json = mapper.readValue(jsonStr, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            return jsonStr;
        }
    }

    public static JsonNode parseToJsonNode(String jsonStr) throws JsonProcessingException {
        if (StringUtil.isEmpty(jsonStr))
            return null;

        return mapper.readTree(jsonStr);
    }

    public static <T> T parseToObject(String jsonStr, Class<T> clazz) throws JsonProcessingException {
        return mapper.readValue(jsonStr, clazz);
    }
}
