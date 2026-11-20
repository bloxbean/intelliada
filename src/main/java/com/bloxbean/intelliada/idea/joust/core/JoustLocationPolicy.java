package com.bloxbean.intelliada.idea.joust.core;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * ADR-004 D3a items 4 and 7, I18: a server-supplied location may open only when it is a {@code file:} URI whose
 * <b>real path</b> lies inside the project's base directory and is a regular file. Everything else is dropped: other
 * schemes ({@code https:}, {@code joust-lib:}), files in the installed {@code joust-lsp} package or its storage, and an
 * in-project symlink to a file outside.
 *
 * <p>Remote, UNC and device forms are refused from the URI text alone, before any file-system access (M1-CDX-3), and
 * containment is decided by file identity ({@link JoustRealPaths}).
 */
public final class JoustLocationPolicy {

    private JoustLocationPolicy() {
    }

    /** The path a URI names, from its text alone, or {@code null} for anything but a plain local {@code file:} path. */
    public static Path localPathOf(String uri) {
        if (uri == null || uri.length() > 4096 || uri.indexOf('\0') >= 0) return null;
        URI u;
        try {
            u = new URI(uri);
        } catch (URISyntaxException e) {
            return null;
        }
        if (u.getScheme() == null || !u.getScheme().toLowerCase(Locale.ROOT).equals("file") || u.isOpaque()) return null;
        if (u.getRawQuery() != null) return null;
        String host = u.getRawAuthority();
        if (host != null && !host.isEmpty() && !host.equalsIgnoreCase("localhost")) return null;
        String raw = u.getRawPath();
        String path = u.getPath();
        if (raw == null || path == null || !raw.startsWith("/") || raw.startsWith("//") || path.startsWith("//")) return null;
        if (path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0) return null;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/?/") || lower.startsWith("/./") || lower.startsWith("/unc/")) return null;
        for (String seg : path.split("/")) {
            if (seg.equals("..")) return null;
        }
        try {
            return Path.of(new URI("file", null, path, null));
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    /** The real path the URI may open, or {@code null}. {@code projectBaseReal} must itself be a real path. */
    public static Path allowedTarget(String uri, Path projectBaseReal) {
        return allowedTarget(uri, projectBaseReal, projectBaseReal);
    }

    /**
     * As {@link #allowedTarget(String, Path)}; the URI's path must first lie, <b>by its text</b>, inside the base as
     * spelled or as its real path, so nothing outside the project is touched on disk before it is refused (M1-SEC-2).
     */
    public static Path allowedTarget(String uri, Path projectBaseReal, Path projectBaseSpelled) {
        if (projectBaseReal == null) return null;
        Path p = localPathOf(uri);
        if (p == null) return null;
        JoustPaths.Flavor f = JoustPaths.Flavor.current();
        boolean lexical = JoustPaths.isInside(p.toString(), projectBaseReal.toString(), f)
                || (projectBaseSpelled != null && JoustPaths.isInside(p.toString(), projectBaseSpelled.toString(), f));
        if (!lexical) return null;
        return allowedFile(p, projectBaseReal);
    }

    /** {@code p}'s real path if it is a regular file inside {@code projectBaseReal} by identity, else {@code null}. */
    public static Path allowedFile(Path p, Path projectBaseReal) {
        Path real;
        try {
            real = p.toRealPath();
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (!Files.isRegularFile(real)) return null;
        return JoustRealPaths.isInside(real, projectBaseReal) ? real : null;
    }
}
