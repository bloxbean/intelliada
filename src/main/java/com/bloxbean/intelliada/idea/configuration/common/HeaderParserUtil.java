package com.bloxbean.intelliada.idea.configuration.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HeaderParserUtil {
    public static Map<String, String> parseHeaders(String payload) {
        Map<String, String> holder = new HashMap();
        String[] keyVals = payload.split(",");
        for (String keyVal : keyVals) {
            String[] parts = keyVal.split("=", 2);
            holder.put(parts[0].trim(), parts[1].trim());
        }

        return holder;
    }

    public static String encodeHeaders(Map<String, String> headers) {
        if (headers == null)
            return "";

        Set<String> keys = headers.keySet();
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            String value = headers.get(key);
            if (value != null) {
                sb.append(key + "=" + value);
                sb.append(",");
            }
        }

        return sb.toString();
    }
}
