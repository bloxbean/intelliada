package com.bloxbean.intelliada.idea.joust;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/** Helpers that build tool layouts in temporary directories. */
final class JoustTestFiles {
    private JoustTestFiles() {
    }

    static Path executable(Path p, String content) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException ignored) {
            p.toFile().setExecutable(true);
        }
        return p;
    }

    /** {@code <prefix>/lib/node_modules/@julclang/joust/bin/joust-lsp.js} plus {@code <prefix>/bin/joust-lsp} -> it. */
    static Path joustPackage(Path prefix, String name, String version) throws IOException {
        Path pkg = prefix.resolve("lib/node_modules/@julclang/joust");
        Files.createDirectories(pkg.resolve("bin"));
        Files.writeString(pkg.resolve("package.json"), "{\"name\":\"" + name + "\",\"version\":\"" + version + "\"}");
        Path js = executable(pkg.resolve("bin/joust-lsp.js"), "#!/usr/bin/env node\n");
        Files.createDirectories(prefix.resolve("bin"));
        Files.createSymbolicLink(prefix.resolve("bin/joust-lsp"), js);
        return js;
    }
}
