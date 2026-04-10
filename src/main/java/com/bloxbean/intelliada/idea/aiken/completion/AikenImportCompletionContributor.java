package com.bloxbean.intelliada.idea.aiken.completion;

import com.bloxbean.intelliada.idea.aiken.lang.psi.AikenTypes;
import com.bloxbean.intelliada.idea.aiken.lang.AikenLanguage;
import com.bloxbean.intelliada.idea.aiken.lang.psi.impl.AikenPsiImplUtil;
import com.bloxbean.intelliada.idea.aiken.module.pkg.AikenTomlService;
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

import java.util.Map;
import java.util.HashMap;

public class AikenImportCompletionContributor extends CompletionContributor {

    // Complete Aiken standard library modules
    private static final String[] STDLIB_MODULES = {
            // Core Aiken modules
            "aiken/builtin", "aiken/cbor", "aiken/fuzz", "aiken/primitive/bytearray", 
            "aiken/primitive/int", "aiken/primitive/string",
            
            // Collections
            "aiken/collection/dict", "aiken/collection/list", "aiken/collection/pairs",
            
            // Data structures
            "aiken/option", "aiken/result", "aiken/ordering",
            
            // Utilities
            "aiken/string", "aiken/bytearray", "aiken/math", "aiken/interval",
            "aiken/time", "aiken/rational",
            
            // Cryptography
            "aiken/crypto", "aiken/hash", "aiken/crypto/bls12_381", "aiken/crypto/ed25519",
            
            // Cardano-specific modules
            "cardano/address", "cardano/assets", "cardano/certificate", "cardano/credential",
            "cardano/governance", "cardano/script_context", "cardano/transaction",
            "cardano/wallet", "cardano/compatibility"
    };

    // Module-specific exports for completion
    private static final Map<String, String[]> MODULE_EXPORTS = createModuleExports();
    
    private static Map<String, String[]> createModuleExports() {
        Map<String, String[]> exports = new HashMap<>();
        
        exports.put("aiken/collection/list", new String[]{
                "length", "map", "filter", "fold", "any", "all", "head", "tail", "take", "drop",
                "reverse", "concat", "flatten", "unique", "sort", "at", "push", "indexed_map"
        });
        
        exports.put("aiken/collection/dict", new String[]{
                "new", "insert", "delete", "get", "has_key", "keys", "values", "to_list",
                "from_list", "size", "is_empty", "filter", "map", "fold"
        });
        
        exports.put("aiken/option", new String[]{
                "Some", "None", "map", "or_else", "and_then", "is_some", "is_none", "choice"
        });
        
        exports.put("aiken/result", new String[]{
                "Ok", "Error", "map", "map_error", "or_else", "and_then", "is_ok", "is_error"
        });
        
        exports.put("aiken/crypto", new String[]{
                "VerificationKeyHash", "ScriptHash", "blake2b_256", "blake2b_224", "sha2_256", "sha3_256"
        });
        
        exports.put("cardano/transaction", new String[]{
                "Transaction", "Input", "Output", "OutputReference", "TxId", "find_input", "find_output",
                "find_script_outputs", "find_datum", "find_script", "value_sent_to", "value_sent_to_address"
        });
        
        exports.put("cardano/address", new String[]{
                "Address", "VerificationKeyCredential", "ScriptCredential", "StakeCredential",
                "from_verification_key", "from_script", "payment_credential", "stake_credential"
        });
        
        exports.put("cardano/assets", new String[]{
                "PolicyId", "AssetName", "Value", "from_lovelace", "from_asset", "quantity_of",
                "policies", "tokens", "flatten", "negate", "zero"
        });
        
        exports.put("cardano/script_context", new String[]{
                "ScriptContext", "ScriptPurpose", "Spend", "Mint", "Withdraw", "Publish", "Vote", "Propose"
        });
        
        exports.put("aiken/string", new String[]{
                "length", "slice", "drop_prefix", "drop_suffix", "starts_with", "ends_with", 
                "concat", "join", "split", "to_bytearray", "from_bytearray"
        });
        
        exports.put("aiken/bytearray", new String[]{
                "length", "slice", "drop", "take", "concat", "push", "compare", "from_string", "to_string"
        });
        
        exports.put("aiken/math", new String[]{
                "abs", "max", "min", "clamp", "pow", "sqrt", "log", "gcd", "lcm"
        });
        
        exports.put("aiken/interval", new String[]{
                "Interval", "before", "after", "contains", "is_empty", "intersection", "hull"
        });
        
        exports.put("aiken/builtin", new String[]{
                "head_list", "tail_list", "null_list", "choose_list", "mk_cons", "mk_nil_data",
                "mk_nil_pair_data", "serialise_data", "un_i_data", "un_b_data", "un_map_data",
                "un_list_data", "un_constr_data", "equals_data", "less_than_equals_integer",
                "less_than_integer", "add_integer", "subtract_integer", "multiply_integer",
                "divide_integer", "quotient_integer", "remainder_integer", "mod_integer"
        });
        
        exports.put("aiken/cbor", new String[]{
                "diagnostic", "bytearray", "int", "simple", "tag", "array", "map", "indefinite_array",
                "indefinite_map", "indefinite_bytearray", "indefinite_string"
        });
        
        exports.put("aiken/fuzz", new String[]{
                "int", "int_between", "bool", "bytearray", "bytearray_between", "list",
                "list_between", "constant", "and_then", "map", "one_of", "frequency"
        });
        
        exports.put("aiken/time", new String[]{
                "now", "after", "before", "between", "PosixTime"
        });
        
        exports.put("aiken/rational", new String[]{
                "Rational", "from_int", "numerator", "denominator", "add", "subtract",
                "multiply", "divide", "negate", "abs", "compare", "zero", "one"
        });
        
        exports.put("aiken/ordering", new String[]{
                "Less", "Equal", "Greater", "compare"
        });
        
        exports.put("aiken/collection/pairs", new String[]{
                "new", "get_1st", "get_2nd", "map_1st", "map_2nd", "swap"
        });
        
        exports.put("aiken/primitive/bytearray", new String[]{
                "length", "take", "drop", "slice", "push", "from_string", "to_string",
                "compare", "concat", "is_empty"
        });
        
        exports.put("aiken/primitive/int", new String[]{
                "max", "min", "abs", "compare", "to_string", "from_string"
        });
        
        exports.put("aiken/primitive/string", new String[]{
                "length", "slice", "take", "drop", "concat", "starts_with", "ends_with",
                "contains", "to_bytearray", "from_bytearray", "to_lower", "to_upper"
        });
        
        exports.put("aiken/crypto/ed25519", new String[]{
                "VerificationKey", "Signature", "verify"
        });
        
        exports.put("cardano/credential", new String[]{
                "Credential", "VerificationKey", "Script", "from_verification_key", "from_script"
        });
        
        exports.put("cardano/wallet", new String[]{
                "from_verification_key", "from_script", "with_stake_credential"
        });
        
        exports.put("cardano/compatibility", new String[]{
                "output_reference", "resolved_input"
        });
        
        return exports;
    }

    public AikenImportCompletionContributor() {
        // Complete import-related items for Aiken language
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(AikenLanguage.INSTANCE),
                new CompletionProvider<CompletionParameters>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        PsiElement element = parameters.getPosition();
                        
                        // Get the current line text to check for import context
                        String lineText = getCurrentLineText(element);
                        
                        if (lineText != null && lineText.trim().startsWith("use ")) {
                            if (lineText.contains(".{")) {
                                // Inside export braces - provide specific exports
                                String moduleName = extractModuleName(lineText);
                                addSpecificExportCompletions(moduleName, result);
                            } else {
                                // Module path completion
                                addModuleCompletions(parameters, result);
                            }
                        } else if (shouldProvideImportCompletions(element)) {
                            // Fallback for PSI-based detection
                            addModuleCompletions(parameters, result);
                        }
                    }
                });
    }

    private void addModuleCompletions(@NotNull CompletionParameters parameters, 
                                     @NotNull CompletionResultSet result) {
        String importText = getImportText(parameters.getPosition());
        
        // Always add basic completions first
        result.addElement(LookupElementBuilder.create("aiken/collection/list")
                .withTypeText("stdlib module")
                .withIcon(com.intellij.icons.AllIcons.Nodes.Module)
                .withInsertHandler(new ModuleInsertHandler()));
        
        result.addElement(LookupElementBuilder.create("aiken/collection/dict")
                .withTypeText("stdlib module")
                .withIcon(com.intellij.icons.AllIcons.Nodes.Module)
                .withInsertHandler(new ModuleInsertHandler()));
                
        result.addElement(LookupElementBuilder.create("cardano/transaction")
                .withTypeText("stdlib module")
                .withIcon(com.intellij.icons.AllIcons.Nodes.Module)
                .withInsertHandler(new ModuleInsertHandler()));
        
        // Add standard library modules
        for (String module : STDLIB_MODULES) {
            // Filter based on what's already typed
            if (importText == null || importText.isEmpty() || module.startsWith(importText)) {
                result.addElement(LookupElementBuilder.create(module)
                        .withTypeText("stdlib module")
                        .withIcon(com.intellij.icons.AllIcons.Nodes.Module)
                        .withInsertHandler(new ModuleInsertHandler()));
            }
        }

        // Add namespace completions
        result.addElement(LookupElementBuilder.create("aiken/")
                .withTypeText("stdlib namespace")
                .withIcon(com.intellij.icons.AllIcons.Nodes.ModuleGroup));
        result.addElement(LookupElementBuilder.create("cardano/")
                .withTypeText("cardano namespace")
                .withIcon(com.intellij.icons.AllIcons.Nodes.ModuleGroup));
        result.addElement(LookupElementBuilder.create("aiken/collection/")
                .withTypeText("collections namespace")
                .withIcon(com.intellij.icons.AllIcons.Nodes.ModuleGroup));
    }

    private void addSpecificExportCompletions(@NotNull String moduleName, @NotNull CompletionResultSet result) {
        if (moduleName != null && MODULE_EXPORTS.containsKey(moduleName)) {
            // Only show exports specific to this module
            String[] exports = MODULE_EXPORTS.get(moduleName);
            for (String export : exports) {
                if (Character.isUpperCase(export.charAt(0))) {
                    // Types and constructors
                    result.addElement(LookupElementBuilder.create(export)
                            .withTypeText("type")
                            .withTailText(" from " + moduleName)
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Class));
                } else {
                    // Functions and values
                    result.addElement(LookupElementBuilder.create(export)
                            .withTypeText("function")
                            .withTailText(" from " + moduleName)
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Function));
                }
            }
        }
        // Remove fallback - if we don't know the module, don't guess
    }


    private String extractModuleName(@NotNull String lineText) {
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

    private void addExportCompletions(@NotNull CompletionParameters parameters,
                                     @NotNull CompletionResultSet result) {
        // This method is now handled by addSpecificExportCompletions
        // No fallback completions - only show what we know
    }

    private void addTypeExportCompletions(@NotNull CompletionParameters parameters,
                                         @NotNull CompletionResultSet result) {
        // This method is now handled by addSpecificExportCompletions
        // No additional implementation needed
    }

    /**
     * Simplified import context detection
     */
    private boolean shouldProvideImportCompletions(@NotNull PsiElement element) {
        // Check if we're inside a complete import statement
        if (AikenPsiImplUtil.isInImportContext(element)) {
            return true;
        }
        
        // Get some context around the current position
        String beforeText = getTextBefore(element, 30);
        if (beforeText != null) {
            // Look for "use " followed by potential module path
            if (beforeText.matches(".*\\buse\\s+[a-zA-Z0-9_/]*$")) {
                return true;
            }
        }
        
        // Check current line
        String lineText = getLineText(element);
        if (lineText != null) {
            String trimmed = lineText.trim();
            if (trimmed.startsWith("use ")) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Get the import text being typed
     */
    private String getImportText(@NotNull PsiElement element) {
        String lineText = getLineText(element);
        if (lineText != null) {
            String trimmed = lineText.trim();
            if (trimmed.startsWith("use ")) {
                return trimmed.substring(4); // Remove "use " prefix
            }
        }
        return null;
    }

    /**
     * Get the text of the current line
     */
    private String getLineText(@NotNull PsiElement element) {
        try {
            PsiElement file = element.getContainingFile();
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
            // Ignore errors
        }
        return null;
    }

    /**
     * Get text before the current element position
     */
    private String getTextBefore(@NotNull PsiElement element, int length) {
        try {
            PsiElement file = element.getContainingFile();
            if (file != null) {
                String fileText = file.getText();
                int offset = element.getTextOffset();
                int start = Math.max(0, offset - length);
                return fileText.substring(start, offset);
            }
        } catch (Exception e) {
            // Ignore errors
        }
        return null;
    }

    private String getCurrentLineText(@NotNull PsiElement element) {
        try {
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
                    String beforeCursor = lineText.substring(0, lineText.length() - (tailOffset - startOffset));
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