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
        assertEquals(inside, JoustLocationPolicy.allowedTarget(inside.toUri().toString(), project, flavor));
        assertNull(JoustLocationPolicy.allowedTarget(outside.toUri().toString(), project, flavor));
        assertNull(JoustLocationPolicy.allowedTarget(lspJs.toUri().toString(), project, flavor));
        assertNull(JoustLocationPolicy.allowedTarget(storageFile.toUri().toString(), project, flavor));
        Path link = Files.createSymbolicLink(project.resolve("contracts/app/Link.jsc"), outside);
        assertNull("an in-project symlink to an outside file", JoustLocationPolicy.allowedTarget(link.toUri().toString(), project, flavor));
        Path linkIn = Files.createSymbolicLink(project.resolve("contracts/Alias.jsc"), inside);
        assertEquals(inside, JoustLocationPolicy.allowedTarget(linkIn.toUri().toString(), project, flavor));
    }

    @Test
    public void otherSchemesAndFormsAreDropped() {
        for (String uri : new String[]{"https://example.com/Counter.jsc", "joust-lib:/std/List.jsc", "jar:file:/x.jar!/a",
                "file://evil-host/share/Counter.jsc", inside.toUri() + "?q=1", "Counter.jsc", "../../etc/passwd",
                "file:" + project.resolve("missing.jsc"), project.toUri().toString(), "file:///%2e%2e/etc/passwd", "", null}) {
            assertNull(String.valueOf(uri), JoustLocationPolicy.allowedTarget(uri, project, flavor));
        }
        assertNotNull(JoustLocationPolicy.allowedTarget(inside.toUri() + "#L3", project, flavor));
        assertNull(JoustLocationPolicy.allowedTarget(inside.toUri().toString(), null, flavor));
    }

    @Test
    public void workspaceFoldersAreConfined() throws Exception {
        Path module = Files.createDirectories(project.resolve("module"));
        Path external = Files.createDirectories(project.resolveSibling("included-build"));
        Path lookAlike = Files.createDirectories(project.resolveSibling("repo-evil"));
        List<Path> got = JoustWorkspaceFolders.confine(List.of(project, module, external, lookAlike), project, flavor);
        assertEquals(List.of(project), got);
        assertEquals(List.of(module), JoustWorkspaceFolders.confine(List.of(module, external), project, flavor));
        assertEquals(List.of(project), JoustWorkspaceFolders.confine(List.of(external), project, flavor));
    }
}
