package com.bloxbean.intelliada.idea.aiken.completion;

import com.bloxbean.intelliada.idea.aiken.lang.AikenLanguage;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Simplified import completion contributor for debugging
 */
public class AikenSimpleImportCompletionContributor extends CompletionContributor {

    public AikenSimpleImportCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(AikenLanguage.INSTANCE),
                new CompletionProvider<CompletionParameters>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        
                        // Get context around cursor
                        String lineText = getCurrentLineText(parameters);
                        
                        // If line contains "use " then add import completions
                        if (lineText != null && lineText.trim().startsWith("use ")) {
                            if (lineText.contains(".{")) {
                                // Inside export braces - provide exports
                                String moduleName = extractModuleName(lineText);
                                addExportCompletions(moduleName, result);
                            } else {
                                // Add common stdlib modules
                                String[] commonModules = {
                                    "aiken/collection/list", "aiken/collection/dict", "aiken/option", "aiken/result",
                                    "aiken/string", "aiken/bytearray", "aiken/math", "aiken/crypto", "aiken/hash",
                                    "aiken/interval", "aiken/time", "aiken/builtin", "aiken/cbor", "aiken/fuzz",
                                    "aiken/primitive/bytearray", "aiken/primitive/int", "aiken/primitive/string",
                                    "aiken/collection/pairs", "aiken/ordering", "aiken/rational",
                                    "aiken/crypto/bls12_381", "aiken/crypto/ed25519",
                                    "cardano/address", "cardano/assets", "cardano/certificate", "cardano/credential",
                                    "cardano/governance", "cardano/script_context", "cardano/transaction",
                                    "cardano/wallet", "cardano/compatibility"
                                };
                                
                                for (String module : commonModules) {
                                    result.addElement(LookupElementBuilder.create(module)
                                            .withTypeText("stdlib")
                                            .withIcon(com.intellij.icons.AllIcons.Nodes.Module)
                                            .withInsertHandler(new ModuleInsertHandler()));
                                }
                            }
                        }
                    }
                });
    }

    private String getCurrentLineText(@NotNull CompletionParameters parameters) {
        try {
            var element = parameters.getPosition();
            var file = element.getContainingFile();
            if (file != null) {
                String fileText = file.getText();
                int offset = element.getTextOffset();
                
                // Find start of line
                int lineStart = offset;
                while (lineStart > 0 && fileText.charAt(lineStart - 1) != '\n') {
                    lineStart--;
                }
                
                // Find end of line  
                int lineEnd = offset;
                while (lineEnd < fileText.length() && fileText.charAt(lineEnd) != '\n') {
                    lineEnd++;
                }
                
                if (lineStart <= lineEnd) {
                    return fileText.substring(lineStart, lineEnd);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String extractModuleName(String lineText) {
        // Extract module name from "use cardano/transaction.{" -> "cardano/transaction"
        String trimmed = lineText.trim();
        if (trimmed.startsWith("use ")) {
            String afterUse = trimmed.substring(4).trim();
            int braceIndex = afterUse.indexOf(".{");
            if (braceIndex > 0) {
                return afterUse.substring(0, braceIndex).trim();
            }
        }
        return null;
    }

    private void addExportCompletions(String moduleName, @NotNull CompletionResultSet result) {
        if ("cardano/transaction".equals(moduleName)) {
            // Cardano transaction exports
            result.addElement(LookupElementBuilder.create("Transaction")
                    .withTypeText("type").withIcon(com.intellij.icons.AllIcons.Nodes.Class));
            result.addElement(LookupElementBuilder.create("Input")
                    .withTypeText("type").withIcon(com.intellij.icons.AllIcons.Nodes.Class));
            result.addElement(LookupElementBuilder.create("Output")
                    .withTypeText("type").withIcon(com.intellij.icons.AllIcons.Nodes.Class));
            result.addElement(LookupElementBuilder.create("OutputReference")
                    .withTypeText("type").withIcon(com.intellij.icons.AllIcons.Nodes.Class));
            result.addElement(LookupElementBuilder.create("TxId")
                    .withTypeText("type").withIcon(com.intellij.icons.AllIcons.Nodes.Class));
            result.addElement(LookupElementBuilder.create("find_input")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("find_output")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
        } else if ("aiken/collection/list".equals(moduleName)) {
            // Aiken list exports
            result.addElement(LookupElementBuilder.create("map")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("filter")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("fold")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("length")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("head")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("tail")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
        } else if ("aiken/crypto".equals(moduleName)) {
            // Aiken crypto exports
            result.addElement(LookupElementBuilder.create("VerificationKeyHash")
                    .withTypeText("type").withIcon(com.intellij.icons.AllIcons.Nodes.Class));
            result.addElement(LookupElementBuilder.create("ScriptHash")
                    .withTypeText("type").withIcon(com.intellij.icons.AllIcons.Nodes.Class));
            result.addElement(LookupElementBuilder.create("blake2b_256")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
        } else if ("aiken/math".equals(moduleName)) {
            // Aiken math exports
            result.addElement(LookupElementBuilder.create("abs")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("max")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("min")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("clamp")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("pow")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
        } else if ("aiken/string".equals(moduleName)) {
            // Aiken string exports
            result.addElement(LookupElementBuilder.create("length")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("slice")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("concat")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("starts_with")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
        } else if ("aiken/option".equals(moduleName)) {
            // Aiken option exports
            result.addElement(LookupElementBuilder.create("Some")
                    .withTypeText("constructor").withIcon(com.intellij.icons.AllIcons.Nodes.Method));
            result.addElement(LookupElementBuilder.create("None")
                    .withTypeText("constructor").withIcon(com.intellij.icons.AllIcons.Nodes.Method));
            result.addElement(LookupElementBuilder.create("map")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("or_else")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
        } else if ("aiken/result".equals(moduleName)) {
            // Aiken result exports
            result.addElement(LookupElementBuilder.create("Ok")
                    .withTypeText("constructor").withIcon(com.intellij.icons.AllIcons.Nodes.Method));
            result.addElement(LookupElementBuilder.create("Error")
                    .withTypeText("constructor").withIcon(com.intellij.icons.AllIcons.Nodes.Method));
            result.addElement(LookupElementBuilder.create("map")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            result.addElement(LookupElementBuilder.create("is_ok")
                    .withTypeText("function").withIcon(com.intellij.icons.AllIcons.Nodes.Function));
        }
        // Remove generic fallback - only show exports we know about
    }

    /**
     * Custom insert handler for module completions to handle proper text replacement
     */
    private static class ModuleInsertHandler implements InsertHandler<LookupElement> {
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
            Editor editor = context.getEditor();
            Document document = editor.getDocument();
            PsiFile file = context.getFile();
            
            int startOffset = context.getStartOffset();
            int tailOffset = context.getTailOffset();
            
            // Get the line text to find the "use " prefix
            String lineText = getCurrentLineText(context);
            if (lineText != null && lineText.trim().startsWith("use ")) {
                // Find the position after "use "
                int useIndex = lineText.indexOf("use ");
                if (useIndex != -1) {
                    // Calculate the absolute position after "use "
                    int lineStart = startOffset;
                    while (lineStart > 0 && !document.getText().substring(lineStart - 1, lineStart).equals("\n")) {
                        lineStart--;
                    }
                    
                    int useEndPos = lineStart + useIndex + 4; // 4 = length of "use "
                    
                    // Replace everything from after "use " to the current cursor position
                    String insertText = item.getLookupString();
                    document.replaceString(useEndPos, tailOffset, insertText);
                    
                    // Position cursor at the end of the inserted text
                    editor.getCaretModel().moveToOffset(useEndPos + insertText.length());
                    return;
                }
            }
            
            // Fallback to default behavior
            document.replaceString(startOffset, tailOffset, item.getLookupString());
        }
        
        private String getCurrentLineText(@NotNull InsertionContext context) {
            try {
                PsiFile file = context.getFile();
                if (file != null) {
                    String fileText = file.getText();
                    int offset = context.getStartOffset();
                    
                    // Find start of line
                    int lineStart = offset;
                    while (lineStart > 0 && fileText.charAt(lineStart - 1) != '\n') {
                        lineStart--;
                    }
                    
                    // Find end of line  
                    int lineEnd = offset;
                    while (lineEnd < fileText.length() && fileText.charAt(lineEnd) != '\n') {
                        lineEnd++;
                    }
                    
                    if (lineStart <= lineEnd) {
                        return fileText.substring(lineStart, lineEnd);
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
            return null;
        }
    }
}