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
 */
public final class JoustLocationPolicy {

    private JoustLocationPolicy() {
    }

    /** The real path the URI may open, or {@code null}. {@code projectBaseReal} must itself be a real path. */
    public static Path allowedTarget(String uri, Path projectBaseReal, JoustPaths.Flavor flavor) {
        if (uri == null || projectBaseReal == null || uri.length() > 4096) return null;
        URI u;
        try {
            u = new URI(uri);
        } catch (URISyntaxException e) {
            return null;
        }
        if (u.getScheme() == null || !u.getScheme().toLowerCase(Locale.ROOT).equals("file") || u.isOpaque()) return null;
        if (u.getRawQuery() != null) return null;
        String host = u.getHost();
        if (host != null && !host.isEmpty() && !host.equalsIgnoreCase("localhost")) return null; // no UNC targets
        Path p;
        try {
            URI plain = new URI("file", null, u.getPath(), null);
            p = Path.of(plain);
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
        Path real;
        try {
            real = p.toRealPath();
        } catch (IOException e) {
            return null;
        }
        if (!Files.isRegularFile(real)) return null;
        return JoustPaths.isInside(real.toString(), projectBaseReal.toString(), flavor) ? real : null;
    }
}
