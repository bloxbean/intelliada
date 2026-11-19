package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.JoustDeclaredVersion;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustRealPaths;
import com.bloxbean.intelliada.idea.joust.trust.JoustProjectOpenCheck;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/** Round-3 unit tests: Windows containment, the project-open conflict check, and declared versions. */
public class JoustRound3UnitTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** M1-CDX-3: under the Windows rule an ancestor must spell the base exactly; case-distinct siblings never match. */
    @Test
    public void windowsRuleNeedsTheExactSpelling() {
        assertTrue(JoustRealPaths.isInsideWindowsRule(Path.of("/t/Repo/a.jsc"), Path.of("/t/Repo")));
        assertFalse(JoustRealPaths.isInsideWindowsRule(Path.of("/t/repo/a.jsc"), Path.of("/t/Repo")));
        assertFalse(JoustRealPaths.isInsideWindowsRule(Path.of("/t/Repo-x/a.jsc"), Path.of("/t/Repo")));
    }

    /** M1-DES-17, M1-CDX-23: the open check finds a tool, or joust-lsp's execution target, inside the new project. */
    @Test
    public void openCheckFindsConflictsByExecutionTarget() throws Exception {
        Path base = tmp.getRoot().toPath().toRealPath();
        Path node = JoustTestTools.nodeBinary(base.resolve("tools/node"));
        Path lsp = JoustTestFiles.joustPackage(base.resolve("tools/npm"), "@julclang/joust", "1");
        Path project = Files.createDirectories(base.resolve("repo"));
        JoustPaths.Flavor f = JoustPaths.Flavor.current();
        assertNull(JoustProjectOpenCheck.conflict(node.toString(), lsp.toString(), List.of(project), f));
        assertNotNull(JoustProjectOpenCheck.conflict(node.toString(), lsp.toString(), List.of(base.resolve("tools")), f));
        // Windows layout: the extensionless launcher outside, the package (what runs) inside the opened project.
        Path prefix = Files.createDirectories(base.resolve("appdata/npm"));
        Path pkgDir = Files.createDirectories(prefix.resolve("node_modules/@julclang/joust/bin"));
        Files.writeString(pkgDir.getParent().resolve("package.json"), "{\"name\":\"@julclang/joust\",\"version\":\"1\"}");
        JoustTestFiles.executable(pkgDir.resolve("joust-lsp.js"), "x");
        Path shim = JoustTestFiles.executable(prefix.resolve("joust-lsp"), "x");
        Path opened = prefix.resolve("node_modules");
        assertNotNull("the package that runs is inside the opened project",
                JoustProjectOpenCheck.conflict(node.toString(), shim.toString(), List.of(opened), f, true));
        assertNull(JoustProjectOpenCheck.conflict(node.toString(), shim.toString(), List.of(project), f, true));
    }

    /** M1-CDX-12: case-distinct roots are distinct folders (exact spelling), and duplicates collapse. */
    @Test
    public void foldersDedupeByExactSpelling() throws Exception {
        Path base = tmp.getRoot().toPath().toRealPath();
        Path a = Files.createDirectories(base.resolve("a"));
        assertEquals(List.of(a), com.bloxbean.intelliada.idea.joust.core.JoustWorkspaceFolders.confine(List.of(a, a, Path.of(a.toString())), base));
    }

    /** M1-CDX-23: the target is found without a valid package.json. */
    @Test
    public void openCheckTargetDoesNotNeedAValidPackage() throws Exception {
        Path base = tmp.getRoot().toPath().toRealPath();
        Path node = JoustTestTools.nodeBinary(base.resolve("tools/node"));
        Path prefix = Files.createDirectories(base.resolve("appdata/npm"));
        Path pkgDir = Files.createDirectories(prefix.resolve("node_modules/@julclang/joust/bin"));
        JoustTestFiles.executable(pkgDir.resolve("joust-lsp.js"), "x");
        Path shim = JoustTestFiles.executable(prefix.resolve("joust-lsp"), "x");
        assertNotNull("no package.json, still a conflict",
                JoustProjectOpenCheck.conflict(node.toString(), shim.toString(), List.of(prefix.resolve("node_modules")), JoustPaths.Flavor.current(), true));
    }

    /** M1-CDX-28: the TextMate bundle is never written through an ancestor redirected into a project. */
    @Test
    public void bundleExtractionRefusesARedirectedAncestor() throws Exception {
        Path base = tmp.getRoot().toPath().toRealPath();
        Path project = Files.createDirectories(base.resolve("repo"));
        Path system = Files.createDirectories(base.resolve("system"));
        Files.createSymbolicLink(system.resolve("intelliada"), project);
        try {
            com.bloxbean.intelliada.idea.joust.textmate.JoustGrammarBundle.extracted(system, List.of(project));
            fail("extracted into a project");
        } catch (java.io.IOException expected) {
            // refused
        }
        try (var s = Files.walk(project)) {
            assertEquals(List.of(project), s.toList());
        }
        // A symlinked syntaxes/ under a valid tag directory is refused too (M1-CDX-28).
        Path sys3 = Files.createDirectories(base.resolve("sys3"));
        Path tagDir = Files.createDirectories(sys3.resolve("intelliada/joust-textmate/v0.1.0-pre2"));
        Files.createSymbolicLink(tagDir.resolve("syntaxes"), project);
        try {
            com.bloxbean.intelliada.idea.joust.textmate.JoustGrammarBundle.extracted(sys3, List.of(project));
            fail("wrote the grammar through a symlinked syntaxes/");
        } catch (java.io.IOException expected) {
            // refused
        }
        try (var s = Files.walk(project)) {
            assertEquals(List.of(project), s.toList());
        }
        Path ok = com.bloxbean.intelliada.idea.joust.textmate.JoustGrammarBundle.extracted(Files.createDirectories(base.resolve("sys2")), List.of(project));
        assertTrue(Files.isRegularFile(ok.resolve("syntaxes/jsc.tmLanguage.json")));
    }

    /** M1-DES-16, M1-CDX-15: exact pins and ranges are told apart; nothing outside the base is read. */
    @Test
    public void declaredVersions() throws Exception {
        Path base = tmp.getRoot().toPath().toRealPath();
        Path g = Files.createDirectories(base.resolve("g/src/main/jsc"));
        Files.writeString(base.resolve("g/build.gradle"), "plugins { id 'org.julclang.joust' version '0.1.0-pre2' }");
        var d = JoustDeclaredVersion.declared(Files.writeString(g.resolve("A.jsc"), "x"), base);
        assertEquals("0.1.0-pre2", d.spec());
        assertTrue(d.exact());
        Path n = Files.createDirectories(base.resolve("n/contracts"));
        Files.writeString(base.resolve("n/package.json"), "{\"devDependencies\":{\"@julclang/joust\":\"^0.1.0-pre2\"}}");
        var r = JoustDeclaredVersion.declared(Files.writeString(n.resolve("B.jsc"), "x"), base);
        assertEquals("^0.1.0-pre2", r.spec());
        assertFalse(r.exact());
        assertEquals("project declares ^0.1.0-pre2", r.describe());
        Files.writeString(base.resolve("n/package.json"), "{\"devDependencies\":{\"@julclang/joust\":\"<img src=x>\"}}");
        var evil = JoustDeclaredVersion.declared(n.resolve("B.jsc"), base);
        // Range operators are kept literally (M1-CDX-15); the widget text always starts with "JOUST", so a Swing label
        // never takes it for HTML.
        assertFalse(new com.bloxbean.intelliada.idea.joust.lsp.JoustStatusCache.Status("1", "bundled", "advisory", "", "", "ok")
                .summary(evil).startsWith("<"));
        Path outside = Files.createDirectories(base.resolve("outside"));
        assertNull(JoustDeclaredVersion.declared(Files.writeString(outside.resolve("C.jsc"), "x"), base.resolve("g")));
        var s = new com.bloxbean.intelliada.idea.joust.lsp.JoustStatusCache.Status("0.1.0-pre2", "bundled", "advisory", "", "", "ok");
        assertEquals(s.summary(), s.summary(d));
        for (String range : new String[]{"1", "1.2", "1.x", "*", "~1.2.3", ">=1.0.0 <2", "1.2.3 - 2"}) {
            Files.writeString(base.resolve("n/package.json"), "{\"devDependencies\":{\"@julclang/joust\":\"" + range + "\"}}");
            assertFalse(range, JoustDeclaredVersion.declared(n.resolve("B.jsc"), base).exact());
        }
        for (String exact : new String[]{"1.2.3", "0.1.0-pre2", "=1.2.3", "v1.2.3"}) {
            Files.writeString(base.resolve("n/package.json"), "{\"devDependencies\":{\"@julclang/joust\":\"" + exact + "\"}}");
            assertTrue(exact, JoustDeclaredVersion.declared(n.resolve("B.jsc"), base).exact());
        }
        assertTrue(s.summary(r).endsWith("project declares ^0.1.0-pre2"));
    }
}
