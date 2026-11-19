package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.core.NodeVersionProbe;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/** ADR-004 M1 "D4 validation unit tests" (I3, I7). Nothing here runs a process. */
public class JoustToolValidatorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final JoustPaths.Flavor flavor = JoustPaths.Flavor.current();
    private Path tools;
    private Path project;
    private Path node;
    private Path lspJs;
    private List<Path> roots;

    @Before
    public void setUp() throws Exception {
        Path base = tmp.getRoot().toPath().toRealPath();
        tools = Files.createDirectories(base.resolve("tools"));
        project = Files.createDirectories(base.resolve("repo"));
        node = JoustTestFiles.executable(tools.resolve("node/bin/node"), "#!/bin/sh\necho v22.1.0\n");
        lspJs = JoustTestFiles.joustPackage(tools.resolve("npm"), "@julclang/joust", "0.1.0-pre2");
        roots = List.of(project);
    }

    @Test
    public void acceptsInstalledTools() throws Exception {
        int probes = NodeVersionProbe.invocations();
        var n = JoustToolValidator.checkNode(node.toString(), roots, flavor);
        assertTrue(n.error(), n.ok());
        assertEquals(node.toRealPath(), n.realPath());
        var l = JoustToolValidator.resolveJoustLsp(tools.resolve("npm/bin/joust-lsp").toString(), roots, flavor);
        assertTrue(l.error(), l.ok());
        assertEquals(lspJs.toRealPath(), l.realPath());
        assertEquals("0.1.0-pre2", JoustToolValidator.readJoustVersion(l.realPath()));
        assertEquals("validation runs no process", probes, NodeVersionProbe.invocations());
    }

    @Test
    public void refusesRelativeBareAndNpx() {
        for (String bad : new String[]{"node", "./node", "../tools/node", "npx", "npx joust-lsp", "", "  "}) {
            assertFalse(bad, JoustToolValidator.checkNode(bad, roots, flavor).ok());
            assertFalse(bad, JoustToolValidator.resolveJoustLsp(bad, roots, flavor).ok());
        }
    }

    @Test
    public void refusesToolsInsideAProject() throws Exception {
        Path inNode = JoustTestFiles.executable(project.resolve("node"), "#!/bin/sh\n");
        assertFalse(JoustToolValidator.checkNode(inNode.toString(), roots, flavor).ok());
        Path inLsp = JoustTestFiles.joustPackage(project.resolve("npm"), "@julclang/joust", "9");
        assertFalse(JoustToolValidator.resolveJoustLsp(inLsp.toString(), roots, flavor).ok());
    }

    @Test
    public void refusesSymlinkFromOutsideIntoProject() throws Exception {
        Path inNode = JoustTestFiles.executable(project.resolve("evil/node"), "#!/bin/sh\n");
        Path link = Files.createSymbolicLink(tools.resolve("node-link"), inNode);
        var r = JoustToolValidator.checkNode(link.toString(), roots, flavor);
        assertFalse(r.ok());
        assertTrue(r.error(), r.error().contains("inside an open project"));
    }

    @Test
    public void refusesSymlinkFromProjectOut() throws Exception {
        Path link = Files.createSymbolicLink(project.resolve("node"), node);
        assertFalse(JoustToolValidator.checkNode(link.toString(), roots, flavor).ok());
    }

    @Test
    public void refusesNodeModulesBinSelectedOrReal() throws Exception {
        Path bin = Files.createDirectories(tools.resolve("other/node_modules/.bin"));
        Path selected = Files.createSymbolicLink(bin.resolve("joust-lsp"), lspJs);
        assertFalse(JoustToolValidator.resolveJoustLsp(selected.toString(), roots, flavor).ok());
        Path realInBin = JoustTestFiles.executable(bin.resolve("node"), "#!/bin/sh\n");
        Path outside = Files.createSymbolicLink(tools.resolve("node-via-bin"), realInBin);
        assertFalse(JoustToolValidator.checkNode(outside.toString(), roots, flavor).ok());
    }

    @Test
    public void refusesPrefixLookAlike() throws Exception {
        Path evil = Files.createDirectories(project.resolveSibling("repo-evil"));
        Path n = JoustTestFiles.executable(evil.resolve("node"), "#!/bin/sh\n");
        assertTrue("repo-evil is not inside repo", JoustToolValidator.checkNode(n.toString(), roots, flavor).ok());
        assertFalse(JoustToolValidator.checkNode(n.toString(), List.of(evil), flavor).ok());
    }

    @Test
    public void refusesScriptShims() throws Exception {
        Path cmd = JoustTestFiles.executable(tools.resolve("npm/joust-lsp.cmd"), "@echo off\n");
        assertFalse(JoustToolValidator.resolveJoustLsp(cmd.toString(), roots, flavor).ok());
        assertFalse(JoustToolValidator.checkNode(tools.resolve("node.bat").toString(), roots, flavor).ok());
    }

    @Test
    public void refusesAFileThatIsNotJoustsPackage() throws Exception {
        Path other = JoustTestFiles.joustPackage(tools.resolve("npm2"), "evil-joust", "1.0.0");
        assertFalse(JoustToolValidator.resolveJoustLsp(other.toString(), roots, flavor).ok());
        Path random = JoustTestFiles.executable(tools.resolve("random/joust-lsp.js"), "x");
        assertFalse(JoustToolValidator.resolveJoustLsp(random.toString(), roots, flavor).ok());
    }

    @Test
    public void windowsPackageLayoutLookup() throws Exception {
        // %APPDATA%\npm\joust-lsp (the extensionless shim) maps to npm\node_modules\@julclang\joust\bin\joust-lsp.js
        Path prefix = Files.createDirectories(tools.resolve("appdata/npm"));
        Path pkg = Files.createDirectories(prefix.resolve("node_modules/@julclang/joust/bin"));
        Files.writeString(pkg.getParent().resolve("package.json"), "{\"name\":\"@julclang/joust\",\"version\":\"0.1.0-pre2\"}");
        Path js = JoustTestFiles.executable(pkg.resolve("joust-lsp.js"), "x");
        Path shim = JoustTestFiles.executable(prefix.resolve("joust-lsp"), "#!/bin/sh\n");
        var r = JoustToolValidator.resolveJoustLsp(shim.toString(), roots, flavor, true);
        assertTrue(r.error(), r.ok());
        assertEquals(js.toRealPath(), r.realPath());
        assertFalse(JoustToolValidator.resolveJoustLsp(prefix.resolve("joust-lsp.cmd").toString(), roots, flavor).ok());
    }

    @Test
    public void nodeMajor() {
        assertEquals(22, JoustToolValidator.nodeMajor("v22.12.0"));
        assertEquals(24, JoustToolValidator.nodeMajor("v24.0.0\n"));
        assertEquals(-1, JoustToolValidator.nodeMajor("<html>"));
        assertEquals(-1, JoustToolValidator.nodeMajor(null));
    }
}
