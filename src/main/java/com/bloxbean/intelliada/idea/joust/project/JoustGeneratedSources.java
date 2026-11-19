package com.bloxbean.intelliada.idea.joust.project;

import com.bloxbean.intelliada.idea.joust.core.JoustRealPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * ADR-004 D7: generated Java and its {@code .jsc} source. {@code generateJsc} writes {@code Name.java} with
 * {@code Name.jscmap.json} beside it (F19): {@code {version: 2, source: "Name.jsc", java: "pkg/Name.java",
 * lines: [[javaLine, jscLine, jscCol], …]}}. The {@code .jsc} a map names is opened only when its <b>real path</b> lies
 * inside the module's contracts directory, {@code src/main/jsc} (I18).
 */
public final class JoustGeneratedSources {

    static final long MAP_CAP = 4L * 1024 * 1024;
    static final Pattern SOURCE = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$.-]{0,200}\\.jsc");

    private JoustGeneratedSources() {
    }

    /** A {@code .jsc} position: a real path inside the contracts directory, one-based line and column. */
    public record JscTarget(Path jsc, int line, int column) {
    }

    /** The {@code .jsc} position for a one-based line of a generated Java file, or {@code null}. */
    public static JscTarget jscFor(JoustProjectLayout.GradleModule module, Path realJava, int javaLine) {
        if (module == null || realJava == null || !realJava.getFileName().toString().endsWith(".java")) return null;
        Path jscRoot;
        try {
            jscRoot = module.jscRoot().toRealPath();
        } catch (IOException e) {
            return null;
        }
        String name = realJava.getFileName().toString();
        Path map = realJava.resolveSibling(name.substring(0, name.length() - ".java".length()) + ".jscmap.json");
        JSONObject o;
        try {
            if (!Files.isRegularFile(map, LinkOption.NOFOLLOW_LINKS) || Files.size(map) > MAP_CAP) return null;
            o = new JSONObject(Files.readString(map, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (o.optInt("version", -1) != 2 || !(o.opt("source") instanceof String source) || !SOURCE.matcher(source).matches()) return null;
        int line = 1;
        int col = 1;
        JSONArray lines = o.optJSONArray("lines");
        if (lines != null) {
            int best = -1;
            for (int i = 0; i < lines.length() && i < 1_000_000; i++) {
                JSONArray e = lines.optJSONArray(i);
                if (e == null || e.length() < 3) continue;
                int jl = e.optInt(0, -1);
                if (jl <= javaLine && jl > best) {
                    best = jl;
                    line = Math.max(1, e.optInt(1, 1));
                    col = Math.max(1, e.optInt(2, 1));
                }
            }
        }
        Path jsc = locate(jscRoot, o.optString("java", ""), source);
        return jsc == null ? null : new JscTarget(jsc, line, col);
    }

    /**
     * The {@code .jsc} named {@code source}: first beside the package directory of {@code javaRel}, else the only file
     * of that name under the contracts directory. Its real path must be a regular file inside {@code jscRoot}.
     */
    static Path locate(Path jscRoot, String javaRel, String source) {
        List<Path> candidates = new ArrayList<>();
        if (javaRel.matches("[A-Za-z0-9_$/.-]{1,500}") && !javaRel.contains("..") && !javaRel.startsWith("/")) {
            int slash = javaRel.lastIndexOf('/');
            candidates.add(slash < 0 ? jscRoot.resolve(source) : jscRoot.resolve(javaRel.substring(0, slash)).resolve(source));
        }
        for (Path c : candidates) {
            Path ok = confined(c, jscRoot);
            if (ok != null) return ok;
        }
        List<Path> found = new ArrayList<>();
        try (Stream<Path> s = Files.walk(jscRoot, 20)) {
            s.filter(p -> p.getFileName().toString().equals(source)).limit(3).forEach(found::add);
        } catch (IOException e) {
            return null;
        }
        return found.size() == 1 ? confined(found.get(0), jscRoot) : null;
    }

    static Path confined(Path p, Path jscRootReal) {
        try {
            Path real = p.toRealPath();
            return Files.isRegularFile(real) && JoustRealPaths.isInside(real, jscRootReal) ? real : null;
        } catch (IOException e) {
            return null;
        }
    }
}
