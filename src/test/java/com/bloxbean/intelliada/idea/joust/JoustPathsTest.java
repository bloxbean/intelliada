package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import org.junit.Test;

import static com.bloxbean.intelliada.idea.joust.core.JoustPaths.Flavor.*;
import static org.junit.Assert.*;

/** ADR-004 D4 and M1: segment-wise containment, prefix look-alikes, Windows drive and UNC forms, shims, node_modules/.bin. */
public class JoustPathsTest {

    @Test
    public void absoluteOnly() {
        assertFalse(JoustPaths.isAbsolute("node", UNIX));
        assertFalse(JoustPaths.isAbsolute("./node", UNIX));
        assertFalse(JoustPaths.isAbsolute("npx", UNIX));
        assertFalse(JoustPaths.isAbsolute("", UNIX));
        assertFalse(JoustPaths.isAbsolute("/a/\0b", UNIX));
        assertTrue(JoustPaths.isAbsolute("/usr/bin/node", UNIX));
        assertFalse(JoustPaths.isAbsolute("C:node.exe", WINDOWS));
        assertFalse(JoustPaths.isAbsolute("\\node.exe", WINDOWS));
        assertFalse(JoustPaths.isAbsolute("node.exe", WINDOWS));
        assertTrue(JoustPaths.isAbsolute("C:\\Program Files\\nodejs\\node.exe", WINDOWS));
        assertTrue(JoustPaths.isAbsolute("\\\\srv\\share\\node.exe", WINDOWS));
        assertFalse(JoustPaths.isAbsolute("\\\\srv", WINDOWS));
    }

    @Test
    public void insideIsBySegmentNotPrefix() {
        assertTrue(JoustPaths.isInside("/u/repo/a/b", "/u/repo", UNIX));
        assertTrue(JoustPaths.isInside("/u/repo", "/u/repo", UNIX));
        assertFalse(JoustPaths.isInside("/u/repo-evil/node", "/u/repo", UNIX));
        assertFalse(JoustPaths.isInside("/u/rep", "/u/repo", UNIX));
        assertTrue(JoustPaths.isInside("/u/repo/./x/../y", "/u/repo", UNIX));
        assertFalse(JoustPaths.isInside("/u/repo/../other", "/u/repo", UNIX));
        assertFalse(JoustPaths.isInside("/../../etc", "/", UNIX));
    }

    @Test
    public void caseHandling() {
        assertFalse(JoustPaths.isInside("/U/Repo/x", "/u/repo", UNIX));
        assertTrue(JoustPaths.isInside("/U/Repo/x", "/u/repo", MAC));
        assertTrue(JoustPaths.isInside("c:\\USERS\\me\\Repo\\node.exe", "C:\\Users\\me\\repo", WINDOWS));
    }

    @Test
    public void windowsForms() {
        String root = "C:\\Users\\me\\repo";
        assertTrue(JoustPaths.isInside("C:/Users/me/repo/node.exe", root, WINDOWS));
        assertTrue(JoustPaths.isInside("\\\\?\\C:\\Users\\me\\repo\\node.exe", root, WINDOWS));
        assertTrue(JoustPaths.isInside("\\\\.\\C:\\Users\\me\\repo\\node.exe", root, WINDOWS));
        assertTrue(JoustPaths.isInside("C:\\Users\\me\\repo.\\node.exe", root, WINDOWS));
        assertTrue(JoustPaths.isInside("C:\\Users\\me\\repo \\node.exe", root, WINDOWS));
        assertFalse(JoustPaths.isInside("C:\\Users\\me\\repo-evil\\node.exe", root, WINDOWS));
        assertFalse(JoustPaths.isInside("D:\\Users\\me\\repo\\node.exe", root, WINDOWS));
        String unc = "\\\\server\\share\\repo";
        assertTrue(JoustPaths.isInside("\\\\SERVER\\Share\\repo\\x", unc, WINDOWS));
        assertTrue(JoustPaths.isInside("\\\\?\\UNC\\server\\share\\repo\\x", unc, WINDOWS));
        assertTrue(JoustPaths.isInside("//server/share/repo/x", unc, WINDOWS));
        assertFalse(JoustPaths.isInside("\\\\server\\share2\\repo\\x", unc, WINDOWS));
        assertNull(JoustPaths.segments("C:\\a\\b:stream", WINDOWS));
    }

    @Test
    public void nodeModulesBin() {
        assertTrue(JoustPaths.isUnderNodeModulesBin("/p/node_modules/.bin/joust-lsp", UNIX));
        assertTrue(JoustPaths.isUnderNodeModulesBin("/p/a/node_modules/.bin/x/y", UNIX));
        assertFalse(JoustPaths.isUnderNodeModulesBin("/usr/lib/node_modules/@julclang/joust/bin/joust-lsp.js", UNIX));
        assertTrue(JoustPaths.isUnderNodeModulesBin("C:\\p\\NODE_MODULES\\.BIN\\joust-lsp.cmd", WINDOWS));
        assertTrue(JoustPaths.isUnderNodeModulesBin("/p/Node_Modules/.Bin/joust-lsp", MAC));
    }

    @Test
    public void scriptShimsRefused() {
        assertTrue(JoustPaths.hasRefusedExtension("C:\\npm\\joust-lsp.cmd"));
        assertTrue(JoustPaths.hasRefusedExtension("C:\\npm\\joust-lsp.CMD"));
        assertTrue(JoustPaths.hasRefusedExtension("C:\\npm\\joust-lsp.cmd."));
        assertTrue(JoustPaths.hasRefusedExtension("C:\\npm\\joust-lsp.bat "));
        assertTrue(JoustPaths.hasRefusedExtension("/x/joust-lsp.ps1"));
        assertFalse(JoustPaths.hasRefusedExtension("/x/joust-lsp.js"));
        assertFalse(JoustPaths.hasRefusedExtension("/x/joust-lsp"));
    }
}
