package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbi;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.project.JoustDiagnosticLinks;
import com.bloxbean.intelliada.idea.joust.project.JoustGeneratedSources;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/** ADR-004 M2 (D7): detection by text, generated-source navigation through Name.jscmap.json, console links (I18). */
public class JoustM2ProjectTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private final JoustPaths.Flavor flavor = JoustPaths.Flavor.current();

    private Path base() throws Exception {
        return tmp.getRoot().toPath().toRealPath();
    }

    @Test
    public void detectsTheThreeKindsByTextOnly() throws Exception {
        Path base = base();
        JoustM2Fixtures.gradleProject(Files.createDirectories(base.resolve("g")));
        Path npm = Files.createDirectories(base.resolve("n"));
        Files.writeString(npm.resolve("joust.config.json"), "{\"contracts\":\"src/contracts\",\"networks\":{\"x\":{\"start\":\"touch SENTINEL\"}}}");
        Files.createDirectories(npm.resolve("src/contracts"));
        Path dep = Files.createDirectories(base.resolve("d"));
        Files.writeString(dep.resolve("package.json"), "{\"devDependencies\":{\"@julclang/joust\":\"0.1.0-pre2\"}}");
        Path bad = Files.createDirectories(base.resolve("b"));
        Files.writeString(bad.resolve("joust.config.json"), "{\"contracts\":\"../../outside\"}");
        Path plain = Files.createDirectories(base.resolve("p"));
        Files.writeString(plain.resolve("package.json"), "{\"dependencies\":{\"left-pad\":\"1\"}}");

        JoustProjectLayout layout = JoustProjectLayout.scan(base, flavor);
        assertEquals(1, layout.gradleModules().size());
        assertEquals("0.1.0-pre2", layout.gradleModules().get(0).pluginVersion());
        assertEquals(List.of(dep, npm), layout.npmRoots().stream().map(JoustProjectLayout.NpmRoot::dir).toList());
        assertEquals(npm.resolve("src/contracts"), layout.npmRoots().get(1).contractsDir());
        assertEquals(dep.resolve("contracts"), layout.npmRoots().get(0).contractsDir());
        assertFalse(layout.npmRoots().get(0).cliInstalled());
        assertFalse("nothing ran", Files.exists(npm.resolve("SENTINEL")));
    }

    @Test
    public void generatedJavaMapsToItsJscLine() throws Exception {
        Path g = JoustM2Fixtures.gradleProject(Files.createDirectories(base().resolve("g")));
        JoustProjectLayout layout = JoustProjectLayout.scan(base(), flavor);
        var module = layout.gradleModules().get(0);
        Path java = g.resolve("build/generated/sources/jsc/java/main/org/acme/Counter.java").toRealPath();
        assertSame(module, layout.gradleModuleGenerating(java, flavor));
        List<String> lines = Files.readAllLines(java);
        int marker = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().matches("// Counter\\.jsc:\\d+:\\d+")) {
                marker = i;
                break;
            }
        }
        assertTrue("the generated Java has source markers", marker >= 0);
        String[] lc = lines.get(marker).trim().substring("// Counter.jsc:".length()).split(":");
        var t = JoustGeneratedSources.jscFor(module, java, marker + 2);
        assertNotNull(t);
        assertEquals(g.resolve("src/main/jsc/org/acme/Counter.jsc").toRealPath(), t.jsc());
        assertEquals(Integer.parseInt(lc[0]), t.line());
        assertEquals(Integer.parseInt(lc[1]), t.column());
    }

    @Test
    public void aMapCannotLeaveTheContractsDirectory() throws Exception {
        Path g = JoustM2Fixtures.gradleProject(Files.createDirectories(base().resolve("g")));
        Path outside = Files.writeString(Files.createDirectories(base().resolve("elsewhere")).resolve("Counter.jsc"), "x");
        Path jscDir = g.resolve("src/main/jsc/org/acme");
        Files.delete(jscDir.resolve("Counter.jsc"));
        Files.createSymbolicLink(jscDir.resolve("Counter.jsc"), outside);
        var layout = JoustProjectLayout.scan(base(), flavor);
        Path java = g.resolve("build/generated/sources/jsc/java/main/org/acme/Counter.java").toRealPath();
        assertNull("an in-tree symlink to an outside .jsc", JoustGeneratedSources.jscFor(layout.gradleModules().get(0), java, 30));
        Path map = java.resolveSibling("Counter.jscmap.json");
        Files.writeString(map, Files.readString(map).replace("\"source\": \"Counter.jsc\"", "\"source\": \"../../../../etc/passwd\""));
        assertNull(JoustGeneratedSources.jscFor(layout.gradleModules().get(0), java, 30));
    }

    @Test
    public void consoleLinksAreConfined() throws Exception {
        Path g = JoustM2Fixtures.gradleProject(Files.createDirectories(base().resolve("g")));
        var layout = JoustProjectLayout.scan(base(), flavor);
        String real = JoustM2Fixtures.text("generateJsc-JSC0002.log").lines().filter(l -> l.contains("error[JSC0002]")).findFirst().orElseThrow();
        var link = JoustDiagnosticLinks.find(real, layout);
        assertNotNull(real, link);
        assertEquals(g.resolve("src/main/jsc/org/acme/Counter.jsc").toRealPath(), link.target());
        assertEquals("org/acme/Counter.jsc", real.substring(link.start(), link.end()));
        for (String bad : new String[]{"../../etc/passwd:1:1: error[JSC0502]: x", "/etc/passwd.jsc:1:1: error[JSC0502]: x",
                "org/../../../x.jsc:1:1: error[JSC0502]: x", "org/acme/Missing.jsc:1:1: error[JSC0502]: x"}) {
            assertNull(bad, JoustDiagnosticLinks.find(bad, layout));
        }
        Path outside = Files.writeString(base().resolve("secret.jsc"), "x");
        Files.createSymbolicLink(g.resolve("src/main/jsc/org/acme/Evil.jsc"), outside);
        assertNull(JoustDiagnosticLinks.find("org/acme/Evil.jsc:1:1: error[JSC0502]: x", layout));
        Path npm = Files.createDirectories(base().resolve("n/contracts/app"));
        Files.writeString(base().resolve("n/joust.config.json"), "{}");
        Files.writeString(npm.resolve("File.jsc"), "x");
        var layout2 = JoustProjectLayout.scan(base(), flavor);
        var nl = JoustDiagnosticLinks.find("error[JSC0502]: app/File.jsc:12:5: bad", layout2);
        assertNotNull(nl);
        assertEquals(12, nl.line());
    }

    @Test
    public void abiFailuresInRealOutput() throws Exception {
        var f = JoustAbi.failures(JoustM2Fixtures.text("generateJsc-JSC0002.log"));
        assertEquals(1, f.size());
        assertEquals("JSC0002", f.get(0).code());
        assertEquals("Counter", f.get(0).contract());
        assertEquals("org/acme/Counter.jsc", f.get(0).jscRel());
        assertEquals(1, JoustAbi.failures("error[JSC0003]: app/Vault.jsc:1:1: no ABI manifest Vault.abi.json beside this file").size());
    }
}
