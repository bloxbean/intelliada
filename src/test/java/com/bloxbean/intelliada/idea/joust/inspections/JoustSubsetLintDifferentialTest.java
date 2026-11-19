package com.bloxbean.intelliada.idea.joust.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ADR-004 M2 exit criterion, the differential check against JOUST's processor: "on JOUST's examples and a corpus of
 * mutated contracts, the fixed-width and {@code MerkleMap} inspections flag every finding {@code SubsetLint} reports".
 * <ol>
 *   <li>JOUST's real {@code SubsetLint} ({@code org.julclang:julc-state-processor:0.1.0-pre2} from Maven Central) runs
 *       over {@code joust-lint-corpus/} in a forked JDK 25 ({@code joust-lint/SubsetLintHarness.java}): javac parses
 *       and attributes the corpus, then {@code SubsetLint.lint(trees, type, reporter)} runs per
 *       {@code @StateContract} class. The processor's class files are Java 25, which this JDK 21 test JVM cannot
 *       load; that is the only reason for the fork.</li>
 *   <li>The same files are highlighted here with {@link JoustFixedWidthNumberInspection} and
 *       {@link JoustMerkleMapInternalsInspection}, against the same API jars.</li>
 *   <li>Every SubsetLint finding must be covered by one of those highlights: the highlight lies inside the finding's
 *       node, or contains it (a node javac synthesised, without an end position, counts as its first character).</li>
 * </ol>
 */
public class JoustSubsetLintDifferentialTest extends BasePlatformTestCase {

    private static final Set<String> TOOLS = Set.of("JoustFixedWidthNumber", "JoustMerkleMapInternals");

    /** Mutants into which no SubsetLint violation was injected (they test the inspections' superset only). */
    private static final Set<String> NO_SUBSETLINT_FINDING_EXPECTED = Set.of("BoxesOnly.java");

    record Finding(String file, int line, int start, int end, String message) {
        TextRange range() {
            return end > start ? new TextRange(start, end) : new TextRange(start, start + 1);
        }
    }

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JoustJavaProjectDescriptor.INSTANCE;
    }

    @Override
    protected String getTestDataPath() {
        return corpus().toString();
    }

    public void testInspectionsCoverEverySubsetLintFinding() throws Exception {
        Path corpus = corpus();
        List<Finding> findings = runSubsetLint(corpus);

        // the corpus is meaningful: every mutant made SubsetLint report, including each MerkleMap rule
        Map<String, List<Finding>> byFile = findings.stream()
                .collect(Collectors.groupingBy(Finding::file, LinkedHashMap::new, Collectors.toList()));
        List<String> corpusFiles;
        try (Stream<Path> walk = Files.walk(corpus)) {
            corpusFiles = walk.filter(p -> p.toString().endsWith(".java"))
                    .map(p -> corpus.relativize(p).toString().replace('\\', '/')).sorted().toList();
        }
        for (String file : corpusFiles) {
            String name = file.substring(file.lastIndexOf('/') + 1);
            if (file.startsWith("mutants/") && !NO_SUBSETLINT_FINDING_EXPECTED.contains(name)) {
                assertTrue("SubsetLint reported nothing for the mutant " + file, byFile.containsKey(file));
            }
        }
        for (String rule : List.of("MerkleMap.root()", "MerkleMap.reads()", "MerkleMap.writes()",
                "constructing org.julclang.state.api.MapEntry", "constructing org.julclang.state.api.MerkleMap",
                "fixed-width arithmetic", "intValue()", "'int' in", "' is int in", "'char' in", "'double' in")) {
            assertTrue("no SubsetLint finding of kind " + rule, findings.stream().anyMatch(f -> f.message().startsWith(rule)
                    || f.message().contains(rule)));
        }

        myFixture.copyDirectoryToProject("examples", "");
        myFixture.copyDirectoryToProject("mutants", "");
        myFixture.enableInspections(new JoustFixedWidthNumberInspection(), new JoustMerkleMapInternalsInspection());

        List<String> missed = new ArrayList<>();
        List<String> extra = new ArrayList<>();
        int covered = 0;
        for (String file : corpusFiles) {
            String inProject = file.substring(file.indexOf('/') + 1);
            myFixture.configureFromTempProjectFile(inProject);
            List<HighlightInfo> infos = myFixture.doHighlighting();
            List<HighlightInfo> ours = infos.stream().filter(i -> ours(i)).toList();
            // the corpus must resolve in the fixture too, or the inspections would be silent for the wrong reason
            List<HighlightInfo> javaErrors = infos.stream().filter(i -> i.getSeverity() == HighlightSeverity.ERROR
                    && !ours(i)).toList();
            assertTrue(file + " does not resolve in the fixture: " + describe(javaErrors), javaErrors.isEmpty());

            List<Finding> expected = byFile.getOrDefault(file, List.of());
            for (Finding finding : expected) {
                TextRange node = finding.range();
                boolean hit = ours.stream().anyMatch(i -> {
                    TextRange h = new TextRange(i.getStartOffset(), i.getEndOffset());
                    return node.contains(h) || h.contains(node);
                });
                if (hit) covered++;
                else missed.add(file + ":" + finding.line() + " " + finding.message());
            }
            for (HighlightInfo info : ours) {
                TextRange h = new TextRange(info.getStartOffset(), info.getEndOffset());
                boolean matched = expected.stream().anyMatch(f -> f.range().contains(h) || h.contains(f.range()));
                if (!matched) extra.add(file + ":" + info.getDescription());
            }
        }
        System.out.println("SubsetLint findings: " + findings.size() + " in " + byFile.size() + " of " + corpusFiles.size()
                + " files; covered by the inspections: " + covered);
        System.out.println("inspection highlights SubsetLint does not report (" + extra.size() + "): " + extra);
        assertTrue("the inspections missed SubsetLint findings:\n" + String.join("\n", missed), missed.isEmpty());
        assertEquals(findings.size(), covered);
    }

    private static boolean ours(HighlightInfo info) {
        String tool = info.getInspectionToolId();
        return tool != null && TOOLS.contains(tool);
    }

    private static String describe(List<HighlightInfo> infos) {
        return infos.stream().map(i -> i.getStartOffset() + ":" + i.getDescription()).collect(Collectors.joining("; "));
    }

    private List<Finding> runSubsetLint(Path corpus) throws Exception {
        String java = System.getProperty("joust.lint.java", "");
        String classpath = System.getProperty("joust.lint.classpath", "");
        assertFalse("The differential check runs JOUST's SubsetLint (Java 25 class files) in a JDK 25, and none was"
                + " found. Install one where Gradle's toolchain detection finds it, or pass -PjoustLintJava=<path to a"
                + " JDK 25 java>.", java.isBlank());
        assertTrue("joust.lint.classpath lacks julc-state-processor: " + classpath,
                classpath.contains("julc-state-processor"));
        Path harness = Path.of(resource("/joust-lint/SubsetLintHarness.java").toURI());
        Path out = Files.createTempFile("subsetlint", ".tsv");
        try {
            Process process = new ProcessBuilder(java, "-cp", classpath, harness.toString(), corpus.toString(),
                    out.toString(), classpath).redirectErrorStream(true).start();
            String log;
            boolean done = process.waitFor(5, TimeUnit.MINUTES);
            log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!done) process.destroyForcibly();
            assertTrue("SubsetLint harness timed out: " + log, done);
            assertEquals("SubsetLint harness failed:\n" + log, 0, process.exitValue());
            System.out.println(log.trim());
            List<Finding> findings = new ArrayList<>();
            for (String line : Files.readAllLines(out, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] f = line.split("\t", 5);
                findings.add(new Finding(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), f[4]));
            }
            return findings;
        } finally {
            Files.deleteIfExists(out);
        }
    }

    private static Path corpus() {
        try {
            return Path.of(resource("/joust-lint-corpus/SOURCE.txt").toURI()).getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static URL resource(String name) {
        URL url = JoustSubsetLintDifferentialTest.class.getResource(name);
        if (url == null) throw new IllegalStateException("missing test resource " + name);
        return url;
    }
}
