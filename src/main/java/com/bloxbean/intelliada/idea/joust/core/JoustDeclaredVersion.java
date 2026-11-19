package com.bloxbean.intelliada.idea.joust.core;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADR-004 D5: the JOUST version a project declares for a {@code .jsc} file, read <b>as text</b> (never by running a
 * build), so the status bar can show version skew: "advisory: bundled compiler X; this project pins Y" (M1-CDX-15).
 *
 * <p>From the file's directory up to the project's base, the nearest {@code build.gradle(.kts)} or
 * {@code settings.gradle(.kts)} naming {@code org.julclang.joust} gives the plugin's version literal; the nearest
 * {@code package.json} depending on {@code @julclang/joust} gives that dependency's version string.
 */
public final class JoustDeclaredVersion {

    static final long CAP = 1024 * 1024;
    static final int MAX_LEVELS = 12;
    private static final Pattern GRADLE = Pattern.compile(
            "org\\.julclang\\.joust['\"]\\s*\\)?\\s*version\\s*\\(?\\s*['\"]([A-Za-z0-9._+-]{1,40})['\"]");

    private JoustDeclaredVersion() {
    }

    /** What a project declares: an exact version (a Gradle plugin literal, or an npm exact version) or an npm range. */
    public record Declared(String spec, boolean exact) {
        public String describe() {
            return (exact ? "project pins " : "project declares ") + spec;
        }
    }

    private static final Pattern EXACT = Pattern.compile("[0-9][A-Za-z0-9._+-]{0,39}");

    /** The declared version for {@code realFile}, with its kind, or {@code null}. */
    public static Declared declared(Path realFile, Path baseReal) {
        String[] found = find(realFile, baseReal);
        if (found == null) return null;
        String spec = spec(found[0]);
        if (spec.isEmpty()) return null;
        boolean exact = found[1].equals("gradle") || EXACT.matcher(spec).matches();
        return new Declared(spec, exact);
    }

    /** A version spec as a short literal label: ranges keep {@code ^ ~ * x | = < >}, shown as text (M1-CDX-15). */
    static String spec(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < 40; i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) && c < 128 || "._+-^~*|= <>".indexOf(c) >= 0) sb.append(c);
        }
        return PlainText.literal(sb.toString().trim(), 40);
    }

    /** The declared version spec for {@code realFile}, or {@code null}. Both paths are real paths. */
    public static String forFile(Path realFile, Path baseReal) {
        Declared d = declared(realFile, baseReal);
        return d == null ? null : d.spec();
    }

    /** {spec, "gradle"|"npm"} for the nearest declaration, or null. */
    private static String[] find(Path realFile, Path baseReal) {
        if (realFile == null || baseReal == null || !JoustRealPaths.isInside(realFile, baseReal)) return null;
        Path dir = realFile.getParent();
        for (int i = 0; dir != null && i < MAX_LEVELS && JoustRealPaths.isInside(dir, baseReal); i++, dir = dir.getParent()) {
            for (String f : new String[]{"build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"}) {
                String text = read(dir.resolve(f));
                if (text != null && text.contains("org.julclang.joust")) {
                    Matcher m = GRADLE.matcher(text);
                    if (m.find()) return new String[]{m.group(1), "gradle"};
                }
            }
            String pkg = read(dir.resolve("package.json"));
            if (pkg != null) {
                try {
                    JSONObject o = new JSONObject(pkg);
                    for (String section : new String[]{"dependencies", "devDependencies"}) {
                        JSONObject deps = o.optJSONObject(section);
                        Object v = deps == null ? null : deps.opt("@julclang/joust");
                        if (v instanceof String s) return new String[]{s, "npm"};
                    }
                } catch (RuntimeException ignored) {
                    // not JSON
                }
            }
        }
        return null;
    }

    private static String read(Path p) {
        try {
            if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || Files.size(p) > CAP) return null;
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
