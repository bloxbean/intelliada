package com.bloxbean.intelliada.idea.aiken.completion;

import com.bloxbean.intelliada.idea.aiken.lang.psi.AikenTypes;
import com.bloxbean.intelliada.idea.aiken.lang.AikenLanguage;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class AikenCompletionContributor extends CompletionContributor {

    // Aiken keywords
    private static final String[] KEYWORDS = {
            "if", "else", "when", "is", "fn", "use", "let", "pub", "type", "opaque", 
            "const", "todo", "expect", "check", "test", "trace", "fail", "validator", 
            "and", "or", "as", "via", "benchmark"
    };

    // Validator purpose keywords
    private static final String[] VALIDATOR_PURPOSES = {
            "mint", "spend", "withdraw", "publish", "vote", "propose"
    };

    // Built-in types
    private static final String[] BUILTIN_TYPES = {
            "Int", "Bool", "ByteArray", "String", "Data", "List", "Option"
    };

    // Built-in functions
    private static final String[] BUILTIN_FUNCTIONS = {
            "trace", "fail", "todo", "expect"
    };

    // Boolean literals
    private static final String[] BOOLEAN_LITERALS = {
            "True", "False"
    };

    public AikenCompletionContributor() {
        // Complete keywords and built-ins for Aiken language
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(AikenLanguage.INSTANCE),
                new CompletionProvider<CompletionParameters>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        // Skip basic completions if we're in an import export context
                        if (isInImportExportContext(parameters.getPosition())) {
                            return;
                        }
                        
                        // Use default prefix matching for now - it should work correctly
                        addKeywordCompletions(result);
                        addBuiltinFunctionCompletions(result);
                        addBuiltinTypeCompletions(result);
                        addBooleanLiteralCompletions(result);
                        addValidatorPurposeCompletions(result);
                    }
                });
    }

    private void addKeywordCompletions(@NotNull CompletionResultSet result) {
        for (String keyword : KEYWORDS) {
            result.addElement(LookupElementBuilder.create(keyword)
                    .withBoldness(true)
                    .withTypeText("keyword")
                    .withInsertHandler(new KeywordInsertHandler()));
        }
    }

    private void addBuiltinTypeCompletions(@NotNull CompletionResultSet result) {
        for (String type : BUILTIN_TYPES) {
            result.addElement(LookupElementBuilder.create(type)
                    .withTypeText("type")
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Class)
                    .withInsertHandler(new KeywordInsertHandler()));
        }
    }

    private void addBuiltinFunctionCompletions(@NotNull CompletionResultSet result) {
        for (String function : BUILTIN_FUNCTIONS) {
            result.addElement(LookupElementBuilder.create(function)
                    .withTypeText("function")
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Function)
                    .withInsertHandler(new KeywordInsertHandler()));
        }
    }

    private void addBooleanLiteralCompletions(@NotNull CompletionResultSet result) {
        for (String literal : BOOLEAN_LITERALS) {
            result.addElement(LookupElementBuilder.create(literal)
                    .withTypeText("boolean")
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Variable)
                    .withInsertHandler(new KeywordInsertHandler()));
        }
    }

    private void addValidatorPurposeCompletions(@NotNull CompletionResultSet result) {
        for (String purpose : VALIDATOR_PURPOSES) {
            result.addElement(LookupElementBuilder.create(purpose)
                    .withTypeText("validator purpose")
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Method)
                    .withInsertHandler(new KeywordInsertHandler()));
        }
    }

    private boolean isInImportExportContext(@NotNull PsiElement element) {
        // Check if we're in an import statement with export braces
        String lineText = getCurrentLineText(element);
        if (lineText != null) {
            String trimmed = lineText.trim();
            // Check if we're inside export braces: "use module.{..."
            if (trimmed.startsWith("use ") && trimmed.contains(".{")) {
                return true;
            }
        }
        return false;
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
     * Custom insert handler for keyword completions to handle proper text replacement
     */
    private static class KeywordInsertHandler implements InsertHandler<LookupElement> {
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
            Editor editor = context.getEditor();
            Document document = editor.getDocument();
            
            int startOffset = context.getStartOffset();
            int tailOffset = context.getTailOffset();
            
            // Get the typed prefix
            String prefix = findTypedPrefix(context);
            String insertText = item.getLookupString();
            
            if (prefix != null && insertText.startsWith(prefix)) {
                // Calculate the correct replacement range
                int prefixStart = startOffset - prefix.length();
                
                // Replace the entire prefix with the selected completion
                document.replaceString(prefixStart, tailOffset, insertText);
                
                // Position cursor at the end of the inserted text
                editor.getCaretModel().moveToOffset(prefixStart + insertText.length());
            } else {
                // Fallback to default behavior
                document.replaceString(startOffset, tailOffset, insertText);
            }
        }
        
        private String findTypedPrefix(@NotNull InsertionContext context) {
            try {
                PsiFile file = context.getFile();
                if (file != null) {
                    String fileText = file.getText();
                    int offset = context.getStartOffset();
                    
                    // Look backwards to find the start of the current word
                    int wordStart = offset;
                    while (wordStart > 0) {
                        char c = fileText.charAt(wordStart - 1);
                        if (!Character.isLetterOrDigit(c) && c != '_') {
                            break;
                        }
                        wordStart--;
                    }
                    
                    if (wordStart < offset) {
                        return fileText.substring(wordStart, offset);
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
            return null;
        }
    }
}