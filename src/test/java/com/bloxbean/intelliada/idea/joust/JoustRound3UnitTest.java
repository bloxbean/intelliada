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
        assertFalse(evil.spec().contains("<"));
        Path outside = Files.createDirectories(base.resolve("outside"));
        assertNull(JoustDeclaredVersion.declared(Files.writeString(outside.resolve("C.jsc"), "x"), base.resolve("g")));
        var s = new com.bloxbean.intelliada.idea.joust.lsp.JoustStatusCache.Status("0.1.0-pre2", "bundled", "advisory", "", "", "ok");
        assertEquals(s.summary(), s.summary(d));
        assertTrue(s.summary(r).endsWith("project declares ^0.1.0-pre2"));
    }
}
