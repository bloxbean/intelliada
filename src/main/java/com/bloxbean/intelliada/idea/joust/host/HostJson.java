package com.bloxbean.intelliada.idea.joust.host;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Strict JSON for the host channel: well-formed UTF-8, one RFC 8259 value with nothing after it, duplicate member names
 * refused, and nesting at most 1,000 levels (protocol 1: deeper is malformed). Numbers are read as exact decimals.
 */
public final class HostJson {

    private HostJson() {
    }

    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .disable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS, JsonReadFeature.ALLOW_JAVA_COMMENTS,
                    JsonReadFeature.ALLOW_SINGLE_QUOTES, JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES,
                    JsonReadFeature.ALLOW_TRAILING_COMMA, JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .build();

    static {
        MAPPER.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(1000)
                .maxStringLength(HostFrames.MAX_BODY)
                .maxNumberLength(1000)
                .build());
    }

    /** A malformed body: a protocol violation on the host channel. */
    public static final class Malformed extends Exception {
        Malformed(String why) {
            super(why, null, false, false);
        }
    }

    /** Parses one frame body: valid UTF-8 and exactly one JSON value. */
    public static JsonNode parse(byte[] body) throws Malformed {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body));
        } catch (CharacterCodingException e) {
            throw new Malformed("a frame body that is not UTF-8");
        }
        try {
            JsonNode n = MAPPER.readTree(body);
            if (n == null || n.isMissingNode()) throw new Malformed("an empty frame body");
            return n;
        } catch (IOException e) {
            throw new Malformed("a frame body that is not one well-formed JSON value");
        }
    }

    /** Reads a whole JSON file's bytes (a launch spec) under the same rules. */
    public static JsonNode parseFile(byte[] bytes) throws Malformed {
        return parse(bytes);
    }

    public static byte[] write(JsonNode n) {
        try {
            return MAPPER.writeValueAsBytes(n);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }
}
