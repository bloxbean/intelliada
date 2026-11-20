package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.ReadOnlyFeatureSet;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.*;

/** ADR-004 D3a items 1 and 2: the guard test over LSP4IJ's features, and the registration filter. */
public class JoustReadOnlySetTest {

    @Test
    public void everyLsp4ijFeatureIsClassified() {
        Set<String> setters = new TreeSet<>();
        for (Method m : LSPClientFeatures.class.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && m.getName().startsWith("set") && m.getName().endsWith("Feature")) {
                setters.add(m.getName());
            }
        }
        Set<String> unknown = new TreeSet<>(setters);
        unknown.removeAll(ReadOnlyFeatureSet.FEATURES.keySet());
        assertTrue("LSP4IJ has features IntelliAda has not classified (review them before enabling): " + unknown, unknown.isEmpty());
        Set<String> stale = new TreeSet<>(ReadOnlyFeatureSet.FEATURES.keySet());
        stale.removeAll(setters);
        assertTrue("classified features LSP4IJ no longer has: " + stale, stale.isEmpty());
    }

    /**
     * I25's third path. LSP4IJ 0.30.1 sends {@code workspace/didChangeWorkspaceFolders} from one place: the wrapper asks
     * {@code WorkspaceFolderNotificationManager}, which asks the client's {@code LSPWorkspaceFolderFeature} for a folder and
     * then whether to mark it notified; IntelliAda's feature answers no to both (JoustClientPlatformTest). This fails when
     * another LSP4IJ class starts building the notification, so that path is reviewed before an upgrade (SEC-16b).
     */
    @Test
    public void onlyTheFolderManagerBuildsWorkspaceFolderChanges() throws Exception {
        String self = LSPClientFeatures.class.getResource("LSPClientFeatures.class").toString();
        assertTrue(self, self.startsWith("jar:"));
        java.net.URL jar = new java.net.URL(self.substring(4, self.indexOf("!/")));
        int scanned = 0;
        Set<String> builders = new TreeSet<>();
        try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(jar.openStream())) {
            for (java.util.zip.ZipEntry e; (e = z.getNextEntry()) != null; ) {
                if (!e.getName().startsWith("com/redhat/devtools/lsp4ij/") || !e.getName().endsWith(".class")) continue;
                scanned++;
                String text = new String(z.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                if (text.contains("DidChangeWorkspaceFoldersParams") || text.contains("WorkspaceFoldersChangeEvent")) {
                    builders.add(e.getName());
                }
            }
        }
        assertTrue("scanned LSP4IJ's classes", scanned > 100);
        assertEquals("classes that build didChangeWorkspaceFolders: review each before upgrading (I25)",
                Set.of("com/redhat/devtools/lsp4ij/client/WorkspaceFolderNotificationManager.class"), builders);
    }

    /**
     * ADR-004 §12.2 E23 (UP-DES-4): every LSP4IJ class that applies a server's edits to a document, with the gate that keeps
     * it from running for the JOUST server. LSP4IJ 0.30.1 added one without a new feature setter (DocumentContentSynchronizer,
     * edits on save); this fails when an upgrade adds or moves one, so it is reviewed first.
     */
    @Test
    public void everyLsp4ijEditApplierIsGated() throws Exception {
        java.util.Map<String, String> gated = java.util.Map.of(
                "client/LanguageClientImpl", "workspace/applyEdit: JoustLanguageClient answers applied=false",
                "DocumentContentSynchronizer", "willSaveWaitUntil: dropped from the sync options (E23)",
                "client/features/LSPCompletionProposal", "completion: feature disabled, capability dropped",
                "commands/CommandExecutor", "commands: executeCommandProvider dropped; code actions and lenses disabled",
                "features/codeAction/LSPLazyCodeActionIntentionAction", "code actions: feature disabled, capability dropped",
                "features/color/LSPColorProvider", "colour presentations: feature disabled, capability dropped",
                "features/files/AbstractLSPFileListener$1", "file operations: capability dropped, client capability cleared",
                "features/formatting/LSPFormattingSupport", "formatting: feature disabled, capability dropped",
                "features/formatting/LSPServerSideOnTypeFormattingHelper", "on-type formatting: feature disabled, capability dropped",
                "features/rename/LSPRenameRefactoringDialog", "rename: feature disabled, capability dropped");
        Set<String> appliers = new TreeSet<>();
        String self = LSPClientFeatures.class.getResource("LSPClientFeatures.class").toString();
        java.net.URL jar = new java.net.URL(self.substring(4, self.indexOf("!/")));
        try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(jar.openStream())) {
            for (java.util.zip.ZipEntry e; (e = z.getNextEntry()) != null; ) {
                String n = e.getName();
                if (!n.startsWith("com/redhat/devtools/lsp4ij/") || !n.endsWith(".class") || n.endsWith("/LSPIJUtils.class")) continue;
                String text = new String(z.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                if (text.contains("com/redhat/devtools/lsp4ij/LSPIJUtils") && (text.contains("applyEdits") || text.contains("applyWorkspaceEdit"))) {
                    appliers.add(n.substring("com/redhat/devtools/lsp4ij/".length(), n.length() - ".class".length()));
                }
            }
        }
        assertEquals("LSP4IJ classes that apply edits: gate each one before upgrading (I5)", new TreeSet<>(gated.keySet()), appliers);
    }

    @Test
    public void editingFeaturesAreNotInR() {
        for (String f : new String[]{"setCompletionFeature", "setCodeActionFeature", "setFormattingFeature", "setOnTypeFormattingFeature",
                "setRenameFeature", "setCodeLensFeature", "setDocumentLinkFeature", "setInlayHintFeature"}) {
            assertEquals(f, ReadOnlyFeatureSet.Treatment.DISABLED, ReadOnlyFeatureSet.FEATURES.get(f));
        }
    }

    @Test
    public void registrationFilter() {
        for (String refused : new String[]{"textDocument/formatting", "textDocument/rangeFormatting", "textDocument/onTypeFormatting",
                "textDocument/rename", "textDocument/prepareRename", "textDocument/completion", "textDocument/codeAction",
                "workspace/executeCommand", "workspace/didChangeWatchedFiles", "workspace/didChangeConfiguration",
                "workspace/willRenameFiles", "workspace/didCreateFiles", "workspace/didChangeWorkspaceFolders", "textDocument/codeLens", "textDocument/documentLink",
                "textDocument/inlayHint", "textDocument/diagnostic", "workspace/diagnostic", "workspace/textDocumentContent",
                "textDocument/inlineCompletion", "textDocument/inlineValue", "textDocument/linkedEditingRange",
                "textDocument/willSaveWaitUntil", "", "textDocument/hover2", null}) {
            assertFalse(String.valueOf(refused), ReadOnlyFeatureSet.allowsRegistration(refused));
        }
        for (String ok : new String[]{"textDocument/hover", "textDocument/definition", "textDocument/references",
                "textDocument/documentSymbol", "textDocument/foldingRange", "textDocument/semanticTokens"}) {
            assertTrue(ok, ReadOnlyFeatureSet.allowsRegistration(ok));
        }
    }
}
