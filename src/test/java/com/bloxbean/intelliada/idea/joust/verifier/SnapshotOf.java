package com.bloxbean.intelliada.idea.joust.verifier;

import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.HostSnapshot;
import com.bloxbean.intelliada.idea.joust.host.HostSpec;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;

/** Test helper: a real verified snapshot of any directories and jars, through a launch spec built here. */
public final class SnapshotOf {

    public record Made(HostSpec spec, HostSnapshot snapshot) {
    }

    private SnapshotOf() {
    }

    public static Made create(List<Path> entries, Path base) throws Exception {
        ObjectNode spec = HostJson.object().put("protocol", 1).put("mainClass", HostSpec.MAIN_CLASS).put("java", Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().startsWith("windows") ? "java.exe" : "java").toString());
        ArrayNode launch = spec.putArray("launch");
        StringBuilder lines = new StringBuilder();
        for (int o = 0; o < entries.size(); o++) {
            Path e = entries.get(o);
            if (Files.isDirectory(e)) {
                ObjectNode dir = launch.addObject().put("ordinal", o).put("path", e.toString()).put("kind", "dir");
                ObjectNode files = dir.putObject("files");
                TreeMap<String, String> sorted = new TreeMap<>((a, b) -> java.util.Arrays.compareUnsigned(
                        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)));
                try (var w = Files.walk(e)) {
                    for (Path p : (Iterable<Path>) w.filter(Files::isRegularFile)::iterator) {
                        sorted.put(e.relativize(p).toString().replace(File.separatorChar, '/'), sha(Files.readAllBytes(p)));
                    }
                }
                for (var f : sorted.entrySet()) {
                    files.put(f.getKey(), f.getValue());
                    lines.append(f.getValue()).append(' ').append(o).append(" dir ").append(f.getKey()).append('\n');
                }
            } else {
                String h = sha(Files.readAllBytes(e));
                launch.addObject().put("ordinal", o).put("path", e.toString()).put("kind", "jar").put("sha256", h);
                lines.append(h).append(' ').append(o).append(" jar \n");
            }
        }
        spec.put("digest", sha(lines.toString().getBytes(StandardCharsets.UTF_8)));
        HostSpec s = HostSpec.parse(spec);
        return new Made(s, HostSnapshot.create(s, base));
    }

    static String sha(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }
}
