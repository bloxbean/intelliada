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
     * ADR-004 §12.2 E23 (UP-DES-4, UP-SEC-9): every LSP4IJ class whose bytecode applies edits ({@code LSPIJUtils.apply…})
     * or writes a document directly ({@code Document.insertString/replaceString/deleteString/setText}), with the gate that
     * keeps it from acting for the JOUST server. LSP4IJ 0.30.1 added edits on save without a new feature setter
     * (DocumentContentSynchronizer); this fails when an upgrade adds or moves a writer, so it is reviewed first.
     */
    @Test
    public void everyLsp4ijEditApplierIsGated() throws Exception {
        java.util.Map<String, String> gated = new java.util.TreeMap<>(java.util.Map.ofEntries(
                java.util.Map.entry("client/LanguageClientImpl", "workspace/applyEdit: JoustLanguageClient answers applied=false"),
                java.util.Map.entry("DocumentContentSynchronizer", "willSaveWaitUntil: dropped from the sync options (E23)"),
                java.util.Map.entry("client/features/LSPCompletionProposal", "completion: feature disabled, capability dropped"),
                java.util.Map.entry("commands/CommandExecutor", "commands: executeCommandProvider dropped; code actions and lenses disabled"),
                java.util.Map.entry("features/codeAction/LSPLazyCodeActionIntentionAction", "code actions: feature disabled, capability dropped"),
                java.util.Map.entry("features/color/LSPColorProvider", "colour presentations: feature disabled, capability dropped"),
                java.util.Map.entry("features/files/AbstractLSPFileListener$1", "file operations: capability dropped, client capability cleared"),
                java.util.Map.entry("features/formatting/LSPFormattingSupport", "formatting: feature disabled, capability dropped"),
                java.util.Map.entry("features/formatting/LSPServerSideOnTypeFormattingHelper", "on-type formatting: feature disabled, capability dropped"),
                java.util.Map.entry("features/rename/LSPRenameRefactoringDialog", "rename: feature disabled, capability dropped"),
                java.util.Map.entry("features/linkedEditingRange/LSPLinkedEditingRangeSynchronizer", "linked editing: feature disabled, capability dropped"),
                java.util.Map.entry("features/formatting/LSPEditorImprovementsBackspaceHandler", "typing aid on the user's keystroke: EDITOR_BEHAVIOR all off"),
                java.util.Map.entry("features/formatting/LSPEditorImprovementsEnterBetweenBracesHandler", "typing aid on the user's keystroke: EDITOR_BEHAVIOR all off"),
                java.util.Map.entry("features/formatting/LSPEditorImprovementsTypedHandler", "typing aid on the user's keystroke: EDITOR_BEHAVIOR all off"),
                java.util.Map.entry("features/formatting/LSPImprovedTextMateNestedBracesTypedHandler", "typing aid on the user's keystroke: EDITOR_BEHAVIOR all off"),
                java.util.Map.entry("dap/completion/DAPLookupElementDecorator", "debug-adapter completion: IntelliAda registers no DAP server"),
                java.util.Map.entry("dap/client/files/DAPSourceReferencePosition", "debug-adapter source view: IntelliAda registers no DAP server"),
                java.util.Map.entry("dap/disassembly/DisassemblyFile", "debug-adapter disassembly view: IntelliAda registers no DAP server"),
                java.util.Map.entry("features/semanticTokens/inspector/SemanticTokensInspectorManager", "LSP4IJ's own inspector view, not a project document"),
                java.util.Map.entry("installation/ConsoleProvider", "LSP4IJ's own installation console, not a project document")));
        Set<String> writers = new TreeSet<>();
        String self = LSPClientFeatures.class.getResource("LSPClientFeatures.class").toString();
        java.net.URL jar = new java.net.URL(self.substring(4, self.indexOf("!/")));
        try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(jar.openStream())) {
            for (java.util.zip.ZipEntry e; (e = z.getNextEntry()) != null; ) {
                String n = e.getName();
                if (!n.startsWith("com/redhat/devtools/lsp4ij/") || !n.endsWith(".class") || n.endsWith("/LSPIJUtils.class")) continue;
                boolean[] writes = {false};
                new org.jetbrains.org.objectweb.asm.ClassReader(z.readAllBytes()).accept(new org.jetbrains.org.objectweb.asm.ClassVisitor(
                        org.jetbrains.org.objectweb.asm.Opcodes.ASM9) {
                    @Override
                    public org.jetbrains.org.objectweb.asm.MethodVisitor visitMethod(int a, String name, String d, String sig, String[] ex) {
                        return new org.jetbrains.org.objectweb.asm.MethodVisitor(org.jetbrains.org.objectweb.asm.Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int op, String owner, String m, String desc, boolean itf) {
                                if (owner.equals("com/redhat/devtools/lsp4ij/LSPIJUtils") && m.startsWith("apply")) writes[0] = true;
                                if (owner.equals("com/intellij/openapi/editor/Document") && Set.of("insertString", "replaceString",
                                        "deleteString", "setText").contains(m)) writes[0] = true;
                            }
                        };
                    }
                }, 0);
                if (writes[0]) writers.add(n.substring("com/redhat/devtools/lsp4ij/".length(), n.length() - ".class".length()));
            }
        }
        assertEquals("LSP4IJ classes that apply edits or write documents: gate each one before upgrading (I5)",
                new TreeSet<>(gated.keySet()), writers);
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
