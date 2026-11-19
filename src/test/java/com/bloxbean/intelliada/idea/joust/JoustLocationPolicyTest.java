package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.JoustLocationPolicy;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustWorkspaceFolders;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/** ADR-004 D3a items 4 and 7, I18 and I25 over real files and symlinks. */
public class JoustLocationPolicyTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final JoustPaths.Flavor flavor = JoustPaths.Flavor.current();
    private Path project;
    private Path inside;
    private Path outside;
    private Path lspJs;
    private Path storageFile;

    @Before
    public void setUp() throws Exception {
        Path base = tmp.getRoot().toPath().toRealPath();
        project = Files.createDirectories(base.resolve("repo"));
        inside = Files.writeString(Files.createDirectories(project.resolve("contracts/app")).resolve("Counter.jsc"), "jsc 1\n");
        outside = Files.writeString(base.resolve("secret.txt"), "x");
        lspJs = JoustTestFiles.joustPackage(base.resolve("npm"), "@julclang/joust", "1");
        storageFile = Files.writeString(Files.createDirectories(base.resolve("system/joust-lsp")).resolve("engine.js"), "x");
    }

    @Test
    public void onlyInProjectFilesOpen() throws Exception {
        assertEquals(inside, JoustLocationPolicy.allowedTarget(inside.toUri().toString(), project));
        assertNull(JoustLocationPolicy.allowedTarget(outside.toUri().toString(), project));
        assertNull(JoustLocationPolicy.allowedTarget(lspJs.toUri().toString(), project));
        assertNull(JoustLocationPolicy.allowedTarget(storageFile.toUri().toString(), project));
        Path link = Files.createSymbolicLink(project.resolve("contracts/app/Link.jsc"), outside);
        assertNull("an in-project symlink to an outside file", JoustLocationPolicy.allowedTarget(link.toUri().toString(), project));
        Path linkIn = Files.createSymbolicLink(project.resolve("contracts/Alias.jsc"), inside);
        assertEquals(inside, JoustLocationPolicy.allowedTarget(linkIn.toUri().toString(), project));
    }

    @Test
    public void otherSchemesAndFormsAreDropped() {
        for (String uri : new String[]{"https://example.com/Counter.jsc", "joust-lib:/std/List.jsc", "jar:file:/x.jar!/a",
                "file://evil-host/share/Counter.jsc", inside.toUri() + "?q=1", "Counter.jsc", "../../etc/passwd",
                "file:" + project.resolve("missing.jsc"), project.toUri().toString(), "file:///%2e%2e/etc/passwd", "", null}) {
            assertNull(String.valueOf(uri), JoustLocationPolicy.allowedTarget(uri, project));
        }
        assertNotNull(JoustLocationPolicy.allowedTarget(inside.toUri() + "#L3", project));
        assertNull(JoustLocationPolicy.allowedTarget(inside.toUri().toString(), null));
    }

    @Test
    public void workspaceFoldersAreTheInBaseTopLevelRoots() throws Exception {
        Path module = Files.createDirectories(project.resolve("module"));
        Path module2 = Files.createDirectories(project.resolve("libs/module2"));
        Path nested = Files.createDirectories(module.resolve("sub"));
        Path external = Files.createDirectories(project.resolveSibling("included-build"));
        Path lookAlike = Files.createDirectories(project.resolveSibling("repo-evil"));
        assertEquals(List.of(project), JoustWorkspaceFolders.confine(List.of(project, module, external, lookAlike), project));
        assertEquals("the base is not added when it is not a content root (M1-CDX-12)", List.of(module2, module),
                JoustWorkspaceFolders.confine(List.of(module, module2, nested, external), project));
        assertEquals(List.of(), JoustWorkspaceFolders.confine(List.of(external, lookAlike), project));
    }

    @Test
    public void remoteAndDeviceFormsAreRefusedFromTheText() {
        for (String uri : new String[]{"file:////server/share/repo/a.jsc", "file://server/share/a.jsc", "file:///%2F%2Fserver/share/a.jsc",
                "file:///%5C%5Cserver%5Cshare", "file:///?/C:/a.jsc", "file:///./C:/a.jsc", "file:///UNC/server/a.jsc",
                "file:///a/../b.jsc", "file:///a%00b.jsc"}) {
            assertNull(uri, JoustLocationPolicy.localPathOf(uri));
        }
        assertNotNull(JoustLocationPolicy.localPathOf("file:///repo/a.jsc"));
        assertNotNull(JoustLocationPolicy.localPathOf("file://localhost/repo/a.jsc"));
    }

    @Test
    public void containmentIsByIdentityNotSpelling() throws Exception {
        Path sibling = Files.createDirectories(project.resolveSibling(project.getFileName() + "-x"));
        Path f = Files.writeString(sibling.resolve("a.jsc"), "x");
        assertFalse(com.bloxbean.intelliada.idea.joust.core.JoustRealPaths.isInside(f, project));
        assertTrue(com.bloxbean.intelliada.idea.joust.core.JoustRealPaths.isInside(inside, project));
    }
}
