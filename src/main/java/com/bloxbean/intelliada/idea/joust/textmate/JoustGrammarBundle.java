package com.bloxbean.intelliada.idea.joust.textmate;

import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.intellij.openapi.application.PathManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bundled .jsc TextMate files (ADR-004 D2, I6). The TextMate plugin reads a bundle from a directory, so the files are
 * copied from the plugin's resources into IntelliAda's directory under the IDE's system path, and each copy is checked
 * against the SHA-256 recorded in {@code GRAMMAR_SOURCE.txt}. No network is used.
 */
public final class JoustGrammarBundle {

    public static final String RESOURCE_DIR = "/joust-textmate/";
    public static final List<String> FILES = List.of("package.json", "syntaxes/jsc.tmLanguage.json", "language-configuration.json", "LICENSE");

    private JoustGrammarBundle() {
    }

    /** The recorded tag, commit and per-file SHA-256 values from {@code GRAMMAR_SOURCE.txt}. */
    public static Map<String, String> record() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : new String(resource("GRAMMAR_SOURCE.txt"), StandardCharsets.UTF_8).split("\n")) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            int eq = l.indexOf('=');
            if (eq > 0) out.put(l.substring(0, eq), l.substring(eq + 1));
        }
        return out;
    }

    public static byte[] resource(String name) throws IOException {
        try (InputStream in = JoustGrammarBundle.class.getResourceAsStream(RESOURCE_DIR + name)) {
            if (in == null) throw new IOException("missing bundled resource " + name);
            return in.readAllBytes();
        }
    }

    /** The bundle directory, created or refreshed as needed, with every file verified. */
    public static synchronized Path extracted() throws IOException {
        Map<String, String> rec = record();
        String tag = rec.getOrDefault("tag", "unknown").replaceAll("[^A-Za-z0-9._-]", "_");
        Path dir = Path.of(PathManager.getSystemPath(), "intelliada", "joust-textmate", tag);
        for (String f : FILES) {
            byte[] bytes = resource(f);
            String expected = rec.get(f);
            String actual = JoustToolValidator.sha256(bytes);
            if (!actual.equals(expected)) throw new IOException("bundled " + f + " does not match GRAMMAR_SOURCE.txt");
            Path target = dir.resolve(f);
            if (Files.isRegularFile(target) && JoustToolValidator.sha256(target).equals(expected)) continue;
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), "jsc", ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        return dir;
    }
}
