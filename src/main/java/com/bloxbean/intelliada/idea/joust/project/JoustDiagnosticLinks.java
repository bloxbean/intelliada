package com.bloxbean.intelliada.idea.joust.project;

import com.bloxbean.intelliada.idea.joust.core.JoustRealPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADR-004 D7 and I18: JOUST's diagnostic lines in build output become links, resolved only to real paths inside the
 * project's contracts directories. The formats are {@code org/acme/File.jsc:12:5: error[JSC0502]: …} (Gradle's
 * {@code remapJscDiagnostics}, relative to {@code src/main/jsc}) and {@code error[JSC0502]: app/File.jsc:12:5: …}
 * (npm, relative to the {@code contracts} directory). Absolute paths, {@code ..}, and anything that resolves outside
 * (a symlink included) are not links; a path that resolves in more than one module is not a link either.
 */
public final class JoustDiagnosticLinks {

    private static final Pattern GRADLE = Pattern.compile("^\\s*([^\\s:][^:]{0,500}?\\.jsc):(\\d{1,7}):(\\d{1,7}): (?:error|warning)\\[(JSC\\d{4})\\]");
    private static final Pattern NPM = Pattern.compile("(?:error|warning)\\[(JSC\\d{4})\\]: ([^\\s:][^:]{0,500}?\\.jsc):(\\d{1,7}):(\\d{1,7}):");

    private JoustDiagnosticLinks() {
    }

    /** A link: the span of the path text in the line, the real target, and one-based line and column. */
    public record Link(int start, int end, Path target, int line, int column) {
    }

    public static Link find(String text, JoustProjectLayout layout) {
        if (text == null || layout == null || text.length() > 10_000) return null;
        Matcher g = GRADLE.matcher(text);
        if (g.find()) {
            List<Path> roots = new ArrayList<>();
            for (var m : layout.gradleModules()) roots.add(m.jscRoot());
            Path t = resolve(g.group(1), roots);
            if (t != null) return new Link(g.start(1), g.end(1), t, Integer.parseInt(g.group(2)), Integer.parseInt(g.group(3)));
        }
        Matcher n = NPM.matcher(text);
        if (n.find()) {
            List<Path> roots = new ArrayList<>();
            for (var r : layout.npmRoots()) roots.add(r.contractsDir());
            Path t = resolve(n.group(2), roots);
            if (t != null) return new Link(n.start(2), n.end(2), t, Integer.parseInt(n.group(3)), Integer.parseInt(n.group(4)));
        }
        return null;
    }

    /** The one regular file {@code rel} names under exactly one of {@code roots}, by real path, or null. */
    static Path resolve(String rel, List<Path> roots) {
        if (rel.startsWith("/") || rel.startsWith("\\") || rel.matches("^[A-Za-z]:.*") || rel.indexOf('\0') >= 0) return null;
        for (String seg : rel.split("[/\\\\]")) {
            if (seg.equals("..") || seg.isEmpty()) return null;
        }
        Path hit = null;
        for (Path root : roots) {
            try {
                Path rootReal = root.toRealPath();
                Path real = rootReal.resolve(rel).toRealPath();
                if (Files.isRegularFile(real) && JoustRealPaths.isInside(real, rootReal)) {
                    if (hit != null && !hit.equals(real)) return null;
                    hit = real;
                }
            } catch (IOException | RuntimeException ignored) {
                // not in this root
            }
        }
        return hit;
    }
}
