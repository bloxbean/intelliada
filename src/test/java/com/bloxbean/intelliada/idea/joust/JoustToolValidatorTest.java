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
        node = JoustTestTools.nodeBinary(tools.resolve("node/bin/node"));
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
        Path inNode = JoustTestTools.nodeBinary(project.resolve("node"));
        assertFalse(JoustToolValidator.checkNode(inNode.toString(), roots, flavor).ok());
        Path inLsp = JoustTestFiles.joustPackage(project.resolve("npm"), "@julclang/joust", "9");
        assertFalse(JoustToolValidator.resolveJoustLsp(inLsp.toString(), roots, flavor).ok());
    }

    @Test
    public void refusesSymlinkFromOutsideIntoProject() throws Exception {
        Path inNode = JoustTestTools.nodeBinary(project.resolve("evil/node"));
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
        Path realInBin = JoustTestTools.nodeBinary(bin.resolve("node"));
        Path outside = Files.createSymbolicLink(tools.resolve("node-via-bin"), realInBin);
        assertFalse(JoustToolValidator.checkNode(outside.toString(), roots, flavor).ok());
    }

    @Test
    public void refusesPrefixLookAlike() throws Exception {
        Path evil = Files.createDirectories(project.resolveSibling("repo-evil"));
        Path n = JoustTestTools.nodeBinary(evil.resolve("node"));
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
    public void nodeMustBeTheNodeBinary() throws Exception {
        Path cmd = JoustTestFiles.executable(tools.resolve("shims/node.cmd"), "@echo off\n");
        Path viaLink = Files.createSymbolicLink(tools.resolve("shims/node"), cmd);
        assertFalse("an extensionless node resolving to a .cmd", JoustToolValidator.checkNode(viaLink.toString(), roots, flavor).ok());
        Path npx = JoustTestFiles.executable(tools.resolve("bin2/npx"), "#!/bin/sh\n");
        assertFalse("an absolute npx", JoustToolValidator.checkNode(npx.toString(), roots, flavor).ok());
        Path npm = JoustTestFiles.executable(tools.resolve("bin3/npm"), "#!/bin/sh\n");
        Path nodeToNpm = Files.createSymbolicLink(tools.resolve("bin3/node"), npm);
        assertFalse("node resolving to npm", JoustToolValidator.checkNode(nodeToNpm.toString(), roots, flavor).ok());
        assertTrue(JoustToolValidator.checkNode(node.toString(), roots, flavor).ok());
    }

    /** M1-CDX-5: a script named node (an npm or version-manager shim) is refused before anything could run it. */
    @Test
    public void aScriptNamedNodeIsRefused() throws Exception {
        int probes = com.bloxbean.intelliada.idea.joust.core.NodeVersionProbe.invocations();
        Path script = JoustTestFiles.executable(tools.resolve("asdf/shims/node"), "#!/usr/bin/env bash\nexec asdf exec node \"$@\"\n");
        var r = JoustToolValidator.checkNode(script.toString(), roots, flavor);
        assertFalse(r.ok());
        assertTrue(r.error(), r.error().contains("script"));
        Path viaLink = Files.createSymbolicLink(tools.resolve("node-shim-link"), script);
        assertFalse(JoustToolValidator.checkNode(viaLink.toString(), roots, flavor).ok());
        assertEquals("nothing was run", probes, com.bloxbean.intelliada.idea.joust.core.NodeVersionProbe.invocations());
    }

    /** M1-CDX-5: an executable text file without a shebang (the JDK would run it through /bin/sh) is refused. */
    @Test
    public void aTextFileNamedNodeIsRefused() throws Exception {
        int probes = com.bloxbean.intelliada.idea.joust.core.NodeVersionProbe.invocations();
        Path text = JoustTestFiles.executable(tools.resolve("plain/node"), "touch /tmp/should-not-run\n");
        assertFalse(JoustToolValidator.checkNode(text.toString(), roots, flavor).ok());
        assertEquals(probes, com.bloxbean.intelliada.idea.joust.core.NodeVersionProbe.invocations());
    }

    /** M1-CDX-5: only this host's native format, structurally; truncated, headerless and foreign files are refused. */
    @Test
    public void onlyTheHostsNativeFormat() throws Exception {
        assertTrue("the host's own executable", JoustToolValidator.isNativeExecutable(node.toRealPath(), flavor));
        if (flavor == JoustPaths.Flavor.MAC) {
            assertTrue("/bin/ls (universal)", JoustToolValidator.isNativeExecutable(Path.of("/bin/ls"), JoustPaths.Flavor.MAC));
            assertFalse("ELF rules refuse Mach-O", JoustToolValidator.isNativeExecutable(Path.of("/bin/ls"), JoustPaths.Flavor.UNIX));
            assertFalse("PE rules refuse Mach-O", JoustToolValidator.isNativeExecutable(Path.of("/bin/ls"), JoustPaths.Flavor.WINDOWS));
        }
        Path f = Files.createDirectories(tools.resolve("t")).resolve("x");
        // Magic alone, zero-filled: no file type, no load commands, no program headers, no PE header.
        byte[] macho = new byte[4096];
        macho[0] = (byte) 0xCF; macho[1] = (byte) 0xFA; macho[2] = (byte) 0xED; macho[3] = (byte) 0xFE;
        Files.write(f, macho);
        assertFalse("Mach-O magic without MH_EXECUTE or load commands", JoustToolValidator.isNativeExecutable(f, JoustPaths.Flavor.MAC));
        byte[] elf = new byte[4096];
        elf[0] = 0x7F; elf[1] = 'E'; elf[2] = 'L'; elf[3] = 'F'; elf[4] = 2; elf[5] = 1; elf[6] = 1; elf[16] = 2;
        Files.write(f, elf);
        assertFalse("ELF without a machine or program headers", JoustToolValidator.isNativeExecutable(f, JoustPaths.Flavor.UNIX));
        // A structurally complete ELF64 header with one program header is accepted on Linux, refused elsewhere.
        elf[18] = (byte) 0xB7;            // e_machine: aarch64
        elf[32] = 64;                     // e_phoff
        elf[54] = 56;                     // e_phentsize
        elf[56] = 1;                      // e_phnum
        Files.write(f, elf);
        assertTrue(JoustToolValidator.isNativeExecutable(f, JoustPaths.Flavor.UNIX));
        assertFalse(JoustToolValidator.isNativeExecutable(f, JoustPaths.Flavor.MAC));
        Files.write(f, java.util.Arrays.copyOf(elf, 80));
        assertFalse("program header table past the end", JoustToolValidator.isNativeExecutable(f, JoustPaths.Flavor.UNIX));
        byte[] mz = new byte[4096];
        mz[0] = 'M'; mz[1] = 'Z';
        Files.write(f, mz);
        assertFalse("MZ without a PE header", JoustToolValidator.isNativeExecutable(f, JoustPaths.Flavor.WINDOWS));
        mz[0x3C] = (byte) 0xFC; mz[0x3D] = (byte) 0xFF; mz[0x3E] = (byte) 0xFF; mz[0x3F] = (byte) 0x7F;
        Files.write(f, mz);
        assertFalse("an e_lfanew near Integer.MAX_VALUE neither overflows nor throws",
                JoustToolValidator.isNativeExecutable(f, JoustPaths.Flavor.WINDOWS));
        mz[0x3F] = (byte) 0xFF;
        Files.write(f, mz);
        assertFalse(JoustToolValidator.isNativeExecutable(f, JoustPaths.Flavor.WINDOWS));
        byte[] cafe = new byte[4096];
        cafe[0] = (byte) 0xCA; cafe[1] = (byte) 0xFE; cafe[2] = (byte) 0xBA; cafe[3] = (byte) 0xBE; cafe[7] = 65;
        Files.write(f, cafe);
        assertFalse("a Java class file is not universal Mach-O (M1-DES-19)", JoustToolValidator.isNativeExecutable(f, JoustPaths.Flavor.MAC));
        cafe[7] = 1;
        Files.write(f, cafe);
        assertFalse("a universal header whose slice is not a Mach-O", JoustToolValidator.isNativeExecutable(f, JoustPaths.Flavor.MAC));
        Path truncated = tools.resolve("t/node");
        Files.write(truncated, java.util.Arrays.copyOf(Files.readAllBytes(node.toRealPath()), 40));
        truncated.toFile().setExecutable(true);
        assertFalse("a truncated copy of the host's node", JoustToolValidator.checkNode(truncated.toString(), roots, flavor).ok());
    }

    /** M1-CDX-30 (P2): a shallow path ending in node_modules/@julclang/joust/bin returns a refusal, not an exception. */
    @Test
    public void aShallowInstallationPathIsRefusedNotThrown() {
        JoustToolValidator.Result r = JoustToolValidator.resolveJoustLsp("/node_modules/@julclang/joust/bin/joust-lsp.js", roots,
                JoustPaths.Flavor.UNIX);
        assertFalse(r.ok());
        assertFalse(JoustToolValidator.named(Path.of("/"), "lib"));
        assertFalse(JoustToolValidator.named(null, "lib"));
    }

    /** M1-CDX-30: the right package name and entry point outside a global npm layout is refused. */
    @Test
    public void joustLspMustBeAGlobalNpmInstallation() throws Exception {
        Path checkout = tools.resolve("checkout/@julclang/joust");
        Files.createDirectories(checkout.resolve("bin"));
        Files.writeString(checkout.resolve("package.json"), "{\"name\":\"@julclang/joust\",\"version\":\"1\"}");
        Path js = JoustTestFiles.executable(checkout.resolve("bin/joust-lsp.js"), "x");
        assertFalse(JoustToolValidator.resolveJoustLsp(js.toString(), roots, flavor).ok());
        Path other = tools.resolve("elsewhere/joust/bin");
        Files.createDirectories(other);
        Files.writeString(other.getParent().resolve("package.json"), "{\"name\":\"@julclang/joust\",\"version\":\"1\"}");
        assertFalse(JoustToolValidator.resolveJoustLsp(JoustTestFiles.executable(other.resolve("joust-lsp.js"), "x").toString(), roots, flavor).ok());
    }

    @Test
    public void nodeMajor() {
        assertEquals(22, JoustToolValidator.nodeMajor("v22.12.0"));
        assertEquals(24, JoustToolValidator.nodeMajor("v24.0.0\n"));
        assertEquals(-1, JoustToolValidator.nodeMajor("<html>"));
        assertEquals(-1, JoustToolValidator.nodeMajor(null));
    }
}
