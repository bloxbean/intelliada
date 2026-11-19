package com.bloxbean.intelliada.idea.joust;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/** Copies the recorded JOUST outputs (src/test/resources/joust-m2) into temporary directories. */
final class JoustM2Fixtures {
    private JoustM2Fixtures() {
    }

    static Path resourceDir() throws URISyntaxException {
        return Path.of(JoustM2Fixtures.class.getClassLoader().getResource("joust-m2/SOURCE.txt").toURI()).getParent();
    }

    /** The Gradle project, copied to {@code target}. */
    static Path gradleProject(Path target) throws Exception {
        Path src = resourceDir().resolve("gradle-jsc");
        try (Stream<Path> s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path t = target.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target;
    }

    static String text(String name) throws Exception {
        try (InputStream in = JoustM2Fixtures.class.getClassLoader().getResourceAsStream("joust-m2/" + name)) {
            if (in == null) throw new IOException("missing " + name);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
