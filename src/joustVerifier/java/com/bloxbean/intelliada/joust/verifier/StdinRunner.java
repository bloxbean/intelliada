package com.bloxbean.intelliada.joust.verifier;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ADR-005 D13: IntelliAda's runner around JOUST's reference checker, bundled in {@code joust-verifier/} beside the kit
 * and listed in its SHA-256 manifest. It reads one checker input from stdin (never a file), calls
 * {@code ReferenceChecker.check(JsonNode)}, and prints the verdict on stdout as one JSON array followed by one newline:
 * exit 0 with {@code []} when accepted, exit 1 with the refusal codes otherwise. Any other failure exits 2 with nothing
 * on stdout, which IntelliAda treats as a refusal.
 */
public final class StdinRunner {

    static final int MAX_INPUT = 64 * 1024 * 1024;

    private StdinRunner() {
    }

    public static void main(String[] args) {
        PrintStream out = System.out;
        // Nothing but the verdict may reach stdout: the kit's own output (if any) goes to stderr.
        System.setOut(System.err);
        try {
            byte[] in = readAll(System.in);
            ObjectMapper m = JsonMapper.builder()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .disable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
                    .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                    .build();
            m.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(1000).build());
            JsonNode input;
            try {
                input = m.readTree(new String(in, StandardCharsets.UTF_8));
            } catch (Exception e) {
                verdict(out, m, List.of("malformed"));
                return;
            }
            if (input == null || !input.isObject()) {
                verdict(out, m, List.of("malformed"));
                return;
            }
            Method check = Class.forName("org.julclang.state.host.conformance.ReferenceChecker").getMethod("check", JsonNode.class);
            @SuppressWarnings("unchecked") List<String> problems = (List<String>) check.invoke(null, input);
            verdict(out, m, problems);
        } catch (Throwable t) {
            System.err.println("joust-verifier: " + t);
            System.exit(2);
        }
    }

    private static void verdict(PrintStream out, ObjectMapper m, List<String> problems) throws Exception {
        out.write(m.writeValueAsBytes(problems));
        out.write('\n');
        out.flush();
        System.exit(problems.isEmpty() ? 0 : 1);
    }

    private static byte[] readAll(InputStream in) throws Exception {
        byte[] b = in.readNBytes(MAX_INPUT + 1);
        if (b.length > MAX_INPUT) throw new IllegalArgumentException("input over 64 MiB");
        return b;
    }
}
