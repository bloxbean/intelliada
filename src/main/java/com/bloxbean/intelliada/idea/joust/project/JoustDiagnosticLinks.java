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
 * ADR-004 D7 and I18: JOUST's diagnostic lines in build output become links, resolved only to real paths inside a
 * verified contracts directory. Both build paths print {@code app/File.jsc:12:5: error[JSC0502]: …}, the file relative
 * to the contracts directory (Gradle's {@code remapJscDiagnostics}; npm's {@code diagnosticLine} in
 * {@code js/joust/src/build.js} at v0.1.0-pre2); the form {@code error[JSC0502]: app/File.jsc:12:5:} is accepted too.
 * Absolute paths, {@code ..}, a root redirected out of its module, and anything that resolves outside (a symlink
 * included) are not links; a path that resolves under more than one root is not a link either.
 */
public final class JoustDiagnosticLinks {

    private static final Pattern PATH_FIRST = Pattern.compile("^\\s*([^\\s:][^:]{0,500}?\\.jsc):(\\d{1,7}):(\\d{1,7}): (?:error|warning)\\[(JSC\\d{4})\\]");
    private static final Pattern CODE_FIRST = Pattern.compile("(?:error|warning)\\[(JSC\\d{4})\\]: ([^\\s:][^:]{0,500}?\\.jsc):(\\d{1,7}):(\\d{1,7}):");

    private JoustDiagnosticLinks() {
    }

    /** A link: the span of the path text, the relative path as printed, the real target, its verified root, line and column. */
    public record Link(int start, int end, String rel, Path target, Path root, int line, int column) {
    }

    /** The printed relative path and its span, before any resolution, or null. */
    public record Printed(int start, int end, String rel, int line, int column) {
    }

    public static Printed printed(String text) {
        if (text == null || text.length() > 10_000) return null;
        Matcher g = PATH_FIRST.matcher(text);
        if (g.find()) return new Printed(g.start(1), g.end(1), g.group(1), Integer.parseInt(g.group(2)), Integer.parseInt(g.group(3)));
        Matcher n = CODE_FIRST.matcher(text);
        if (n.find()) return new Printed(n.start(2), n.end(2), n.group(2), Integer.parseInt(n.group(3)), Integer.parseInt(n.group(4)));
        return null;
    }

    /** The link for a line, resolved under every source root of the layout, or null. */
    public static Link find(String text, JoustProjectLayout layout) {
        if (layout == null) return null;
        return find(text, layout.sourceRoots(), layout.base());
    }

    /** The link for a line, resolved under {@code roots} only, or null. */
    public static Link find(String text, List<JoustProjectLayout.SourceRoot> roots, Path base) {
        Printed p = printed(text);
        if (p == null) return null;
        Path[] hit = resolve(p.rel(), roots, base);
        return hit == null ? null : new Link(p.start(), p.end(), p.rel(), hit[0], hit[1], p.line(), p.column());
    }

    /** The roots under which {@code rel} names a regular file, by real path, each verified. */
    public static List<JoustProjectLayout.SourceRoot> rootsContaining(String rel, List<JoustProjectLayout.SourceRoot> roots, Path base) {
        List<JoustProjectLayout.SourceRoot> out = new ArrayList<>();
        for (JoustProjectLayout.SourceRoot r : roots) {
            if (resolveIn(rel, r, base) != null) out.add(r);
        }
        return out;
    }

    /** The one regular file {@code rel} names under exactly one of {@code roots} and that root's verified real path, or null. */
    static Path[] resolve(String rel, List<JoustProjectLayout.SourceRoot> roots, Path base) {
        Path[] hit = null;
        for (JoustProjectLayout.SourceRoot root : roots) {
            Path real = resolveIn(rel, root, base);
            if (real == null) continue;
            if (hit != null && !hit[0].toString().equals(real.toString())) return null;
            hit = new Path[]{real, root.verifiedContracts(base)};
        }
        return hit;
    }

    /** {@code rel} under one verified root: a relative path without {@code ..}, a regular file whose real path is inside. */
    public static Path resolveIn(String rel, JoustProjectLayout.SourceRoot root, Path base) {
        if (rel == null || rel.startsWith("/") || rel.startsWith("\\") || rel.matches("^[A-Za-z]:.*") || rel.indexOf('\0') >= 0) return null;
        for (String seg : rel.split("[/\\\\]")) {
            if (seg.equals("..") || seg.isEmpty()) return null;
        }
        Path rootReal = root.verifiedContracts(base);
        if (rootReal == null) return null;
        try {
            Path real = rootReal.resolve(rel).toRealPath();
            return Files.isRegularFile(real) && JoustRealPaths.isInside(real, rootReal) ? real : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
