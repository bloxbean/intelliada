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
        // Restore the real source, so the next assertion fails only for the reason it names (M2-CDX-9).
        Files.delete(jscDir.resolve("Counter.jsc"));
        Files.writeString(jscDir.resolve("Counter.jsc"), "jsc 1\n");
        assertNotNull(JoustGeneratedSources.jscFor(layout.gradleModules().get(0), java, 30));
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

    /** M2-CDX-9: a contracts directory redirected out of its module (here, to another directory in the project) is not used. */
    @Test
    public void aRedirectedContractsRootIsNotUsed() throws Exception {
        Path g = JoustM2Fixtures.gradleProject(Files.createDirectories(base().resolve("g")));
        Path elsewhere = Files.createDirectories(base().resolve("elsewhere"));
        Path jscRoot = g.resolve("src/main/jsc");
        Files.move(jscRoot, elsewhere.resolve("jsc"));
        Files.createSymbolicLink(jscRoot, elsewhere.resolve("jsc"));
        var layout = JoustProjectLayout.scan(base(), flavor);
        Path java = g.resolve("build/generated/sources/jsc/java/main/org/acme/Counter.java").toRealPath();
        assertNull(JoustGeneratedSources.jscFor(layout.gradleModules().get(0), java, 30));
        assertNull(JoustDiagnosticLinks.find("org/acme/Counter.jsc:1:1: error[JSC0502]: x", layout));
        assertNotNull("the inventory is incomplete, not silently empty", JoustAbi.manifests(layout).incomplete());
    }

    /** M2-CDX-12: settings-only detection, the nearest owner across kinds, and npm first in one directory (project.js). */
    @Test
    public void ownersFollowJoustsOwnRule() throws Exception {
        Path base = base();
        // Settings-only: the build's root is a JOUST module, with the settings file's version.
        Path s = Files.createDirectories(base.resolve("settingsonly"));
        Files.writeString(s.resolve("settings.gradle"), "plugins { id 'org.julclang.joust' version '0.1.0-pre2' }\n");
        Files.writeString(s.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.createDirectories(s.resolve("src/main/jsc"));
        // A Gradle module with an npm root nested inside it.
        Path g = JoustM2Fixtures.gradleProject(Files.createDirectories(base.resolve("g")));
        Path nested = Files.createDirectories(g.resolve("web"));
        Files.writeString(nested.resolve("joust.config.json"), "{}");
        Files.createDirectories(nested.resolve("contracts/app"));
        Path nestedJsc = Files.writeString(nested.resolve("contracts/app/A.jsc"), "x");
        // Both kinds in one directory: npm wins.
        Path both = JoustM2Fixtures.gradleProject(Files.createDirectories(base.resolve("both")));
        Files.writeString(both.resolve("joust.config.json"), "{\"contracts\":\"src/main/jsc\"}");
        // A Gradle subproject without the plugin owns its files: no JOUST owner.
        Path plain = Files.createDirectories(g.resolve("plain"));
        Files.writeString(plain.resolve("build.gradle"), "plugins { id 'java' }\n");
        Path plainFile = Files.writeString(plain.resolve("A.jsc"), "x");

        var layout = JoustProjectLayout.scan(base, flavor);
        var settingsModule = layout.gradleModules().stream().filter(m -> m.dir().equals(s)).findFirst().orElseThrow();
        assertEquals("0.1.0-pre2", settingsModule.pluginVersion());
        assertEquals(s, layout.owner(s.resolve("src/main/jsc")).gradle().dir());
        assertEquals(nested, layout.owner(nestedJsc.toRealPath()).npm().dir());
        assertEquals(g, layout.owner(g.resolve("src/main/jsc/org/acme/Counter.jsc").toRealPath()).gradle().dir());
        var bothOwner = layout.owner(both.resolve("src/main/jsc/org/acme/Counter.jsc").toRealPath());
        assertNotNull(bothOwner.npm());
        assertNull(bothOwner.gradle());
        assertNull(layout.owner(plainFile.toRealPath()));
    }

    /** M2-CDX-12: an npm app under a Gradle repository's src/ is its own owner, as in project.js. */
    @Test
    public void anNpmAppUnderSrcOwnsItsFiles() throws Exception {
        Path g = JoustM2Fixtures.gradleProject(Files.createDirectories(base().resolve("repo")));
        Path client = Files.createDirectories(g.resolve("src/client"));
        Files.writeString(client.resolve("joust.config.json"), "{}");
        Path jsc = Files.writeString(Files.createDirectories(client.resolve("contracts/app")).resolve("Counter.jsc"), "x");
        var layout = JoustProjectLayout.scan(base(), flavor);
        assertTrue("discovered", layout.npmRoots().stream().anyMatch(r -> r.dir().equals(client)));
        assertEquals(client, layout.owner(jsc.toRealPath()).npm().dir());
        assertEquals(g, layout.owner(g.resolve("src/main/jsc/org/acme/Counter.jsc").toRealPath()).gradle().dir());
    }

    /** M2-CDX-14: two npm roots with the same relative source: no project-wide link, but a link within each root. */
    @Test
    public void scopedLinksResolveWithinTheirRoot() throws Exception {
        Path a = Files.createDirectories(base().resolve("a"));
        Path b = Files.createDirectories(base().resolve("b"));
        for (Path r : new Path[]{a, b}) {
            Files.writeString(r.resolve("joust.config.json"), "{}");
            Files.writeString(Files.createDirectories(r.resolve("contracts/app")).resolve("Counter.jsc"), "x");
        }
        var layout = JoustProjectLayout.scan(base(), flavor);
        String line = "app/Counter.jsc:1:1: error[JSC0002]: x";
        assertNull(JoustDiagnosticLinks.find(line, layout));
        var onlyB = layout.sourceRoots().stream().filter(r -> r.ownerDir().equals(b)).toList();
        assertEquals(b.resolve("contracts/app/Counter.jsc"), JoustDiagnosticLinks.find(line, onlyB, layout.base()).target());
    }
}
