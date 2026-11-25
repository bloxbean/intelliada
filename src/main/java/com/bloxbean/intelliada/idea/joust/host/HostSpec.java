package com.bloxbean.intelliada.idea.joust.host;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@code build/joust/host-spec.json}, the Gradle launch spec {@code joustHostSpec} writes (ADR-005 D11 steps 1–2;
 * protocol 1 "Launch and framing"). Validated before anything is copied or started; everything here is untrusted input.
 */
public record HostSpec(int protocol, Path java, String mainClass, String digest, List<Entry> launch) {

    public static final String MAIN_CLASS = "org.julclang.state.host.DevHost";
    public static final String RELATIVE = "build/joust/host-spec.json";
    static final int MAX_BYTES = 1024 * 1024;
    static final int MAX_ENTRIES = 4096;
    static final int MAX_FILES = 200_000;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /** One ordered class-path entry: a jar with one hash, or a directory with a hash per relative POSIX path. */
    public record Entry(int ordinal, Path path, boolean jar, String sha256, Map<String, String> files) {
    }

    /** The spec cannot be used; the message is plain text for the panel. */
    public static final class Invalid extends Exception {
        public Invalid(String why) {
            super(why, null, false, false);
        }
    }

    /** Reads and validates the spec at {@code moduleDir/build/joust/host-spec.json}, without following a link to it. */
    public static HostSpec read(Path moduleDir) throws Invalid, IOException {
        Path file = moduleDir.resolve(RELATIVE);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new Invalid("no launch spec: run joustHostSpec");
        byte[] bytes;
        // Opened without following a link and read to at most the cap (M31A-CDX-11): a file grown or replaced after the
        // check cannot be read past it.
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = in.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw new Invalid("the launch spec is larger than 1 MiB");
        try {
            return parse(HostJson.parseFile(bytes));
        } catch (HostJson.Malformed e) {
            throw new Invalid("the launch spec is not well-formed JSON");
        }
    }

    public static HostSpec parse(JsonNode n) throws Invalid {
        if (!n.isObject()) throw new Invalid("the launch spec is not an object");
        if (!exactInt(n.path("protocol"), 1)) {
            throw new Invalid("this IntelliAda supports dev-host protocol 1 only");
        }
        String main = n.path("mainClass").textValue();
        if (!MAIN_CLASS.equals(main)) throw new Invalid("unexpected main class in the launch spec");
        String java = n.path("java").textValue();
        if (java == null || java.isEmpty() || hasControl(java)) throw new Invalid("no java executable in the launch spec");
        Path javaPath = Path.of(java);
        if (!javaPath.isAbsolute()) throw new Invalid("the launch spec's java is not an absolute path");
        String digest = n.path("digest").textValue();
        if (digest == null || !SHA256.matcher(digest).matches()) throw new Invalid("the launch spec's digest is malformed");
        JsonNode launch = n.path("launch");
        if (!launch.isArray() || launch.isEmpty() || launch.size() > MAX_ENTRIES) throw new Invalid("the launch spec has no usable launch list");
        List<Entry> entries = new ArrayList<>();
        int files = 0;
        for (int i = 0; i < launch.size(); i++) {
            JsonNode e = launch.get(i);
            if (!e.isObject()) throw new Invalid("a launch entry is not an object");
            if (!exactInt(e.path("ordinal"), i)) {
                throw new Invalid("launch entries are not in ordinal order from 0");
            }
            String p = e.path("path").textValue();
            if (p == null || p.isEmpty() || hasControl(p) || !Path.of(p).isAbsolute()) throw new Invalid("a launch entry's path is not absolute");
            String kind = e.path("kind").textValue();
            if ("jar".equals(kind)) {
                String h = e.path("sha256").textValue();
                if (h == null || !SHA256.matcher(h).matches() || e.has("files")) throw new Invalid("a jar entry's hash is malformed");
                entries.add(new Entry(i, Path.of(p), true, h, Map.of()));
                files++;
            } else if ("dir".equals(kind)) {
                JsonNode f = e.path("files");
                if (!f.isObject() || e.has("sha256")) throw new Invalid("a directory entry has no files map");
                Map<String, String> map = new LinkedHashMap<>();
                for (Iterator<Map.Entry<String, JsonNode>> it = f.fields(); it.hasNext(); ) {
                    var fe = it.next();
                    String rel = fe.getKey();
                    if (!safeRelative(rel)) throw new Invalid("a directory entry names an unsafe relative path");
                    String h = fe.getValue().textValue();
                    if (h == null || !SHA256.matcher(h).matches()) throw new Invalid("a file hash is malformed");
                    map.put(rel, h);
                    if (++files > MAX_FILES) throw new Invalid("the launch spec lists too many files");
                }
                entries.add(new Entry(i, Path.of(p), false, null, map));
            } else {
                throw new Invalid("a launch entry's kind is neither jar nor dir");
            }
        }
        return new HostSpec(1, javaPath, main, digest, List.copyOf(entries));
    }

    /** True when {@code n} is an integer equal to {@code v}, without narrowing (M31A-CDX-14). */
    static boolean exactInt(JsonNode n, int v) {
        return n.isIntegralNumber() && n.canConvertToInt() && n.bigIntegerValue().equals(BigInteger.valueOf(v));
    }

    /** A relative POSIX path: nonempty segments, no {@code .} or {@code ..}, no leading slash, backslash or control character. */
    static boolean safeRelative(String rel) {
        if (rel.isEmpty() || rel.length() > 1024 || rel.startsWith("/") || rel.indexOf('\\') >= 0 || hasControl(rel)) return false;
        if (rel.length() >= 2 && rel.charAt(1) == ':') return false;
        for (String seg : rel.split("/", -1)) {
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")) return false;
        }
        return true;
    }

    static boolean hasControl(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= 0x1f || c == 0x7f) return true;
        }
        return false;
    }
}
