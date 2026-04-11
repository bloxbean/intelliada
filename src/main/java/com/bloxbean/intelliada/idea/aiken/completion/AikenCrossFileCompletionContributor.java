package com.bloxbean.intelliada.idea.aiken.completion;

import com.bloxbean.intelliada.idea.aiken.index.AikenSymbolIndex;
import com.bloxbean.intelliada.idea.aiken.index.AikenSymbolInfo;
import com.bloxbean.intelliada.idea.aiken.index.AikenSymbolType;
import com.bloxbean.intelliada.idea.aiken.lang.AikenLanguage;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

public class AikenCrossFileCompletionContributor extends CompletionContributor {

    public AikenCrossFileCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(AikenLanguage.INSTANCE),
                new CompletionProvider<CompletionParameters>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        
                        // Skip completions if we're in an import export context
                        if (isInImportExportContext(parameters.getPosition())) {
                            return;
                        }
                        
                        // Add public symbols from other files
                        addPublicSymbolCompletions(parameters, result);
                        
                        // Add type constructors from project
                        addConstructorCompletions(parameters, result);
                        
                        // Add validators from project
                        addValidatorCompletions(parameters, result);
                    }
                });
    }

    private void addPublicSymbolCompletions(@NotNull CompletionParameters parameters, 
                                          @NotNull CompletionResultSet result) {
        Collection<AikenSymbolInfo> publicSymbols = AikenSymbolIndex.getPublicSymbols(parameters.getPosition().getProject());
        
        for (AikenSymbolInfo symbol : publicSymbols) {
            // Skip symbols from the same file
            if (isSameFile(symbol, parameters)) {
                continue;
            }
            
            LookupElementBuilder element = LookupElementBuilder.create(symbol.getName())
                    .withTypeText(symbol.getType().getDisplayName())
                    .withIcon(getIconForSymbolType(symbol.getType()));
            
            if (symbol.getSignature() != null && !symbol.getSignature().isEmpty()) {
                element = element.withTailText(symbol.getSignature());
            }
            
            // Add file information
            String fileName = extractFileName(symbol.getFilePath());
            element = element.withTailText(" (" + fileName + ")", true);
            
            result.addElement(element);
        }
    }

    private void addConstructorCompletions(@NotNull CompletionParameters parameters, 
                                         @NotNull CompletionResultSet result) {
        Collection<AikenSymbolInfo> constructors = AikenSymbolIndex.getSymbolsByType(
                AikenSymbolType.CONSTRUCTOR, parameters.getPosition().getProject());
        
        for (AikenSymbolInfo constructor : constructors) {
            if (constructor.isPublic() && !isSameFile(constructor, parameters)) {
                String parentType = constructor.getSignature(); // Parent type name is stored in signature for constructors
                LookupElementBuilder element = LookupElementBuilder.create(constructor.getName())
                        .withTypeText("constructor")
                        .withTailText(" :: " + parentType)
                        .withIcon(AllIcons.Nodes.Method);
                
                result.addElement(element);
            }
        }
    }

    private void addValidatorCompletions(@NotNull CompletionParameters parameters, 
                                       @NotNull CompletionResultSet result) {
        Collection<AikenSymbolInfo> validators = AikenSymbolIndex.getSymbolsByType(
                AikenSymbolType.VALIDATOR, parameters.getPosition().getProject());
        
        for (AikenSymbolInfo validator : validators) {
            if (!isSameFile(validator, parameters)) {
                String validatorType = validator.getSignature(); // Validator type is stored in signature
                LookupElementBuilder element = LookupElementBuilder.create(validator.getName())
                        .withTypeText("validator")
                        .withTailText(" (" + validatorType + ")")
                        .withIcon(AllIcons.Nodes.Lambda);
                
                result.addElement(element);
            }
        }
    }

    private boolean isSameFile(@NotNull AikenSymbolInfo symbol, @NotNull CompletionParameters parameters) {
        String currentFilePath = parameters.getOriginalFile().getVirtualFile().getPath();
        return symbol.getFilePath().equals(currentFilePath);
    }

    private String extractFileName(@NotNull String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash != -1) {
            return filePath.substring(lastSlash + 1);
        }
        return filePath;
    }

    private Icon getIconForSymbolType(@NotNull AikenSymbolType type) {
        return switch (type) {
            case FUNCTION -> AllIcons.Nodes.Function;
            case TYPE -> AllIcons.Nodes.Class;
            case CONSTRUCTOR -> AllIcons.Nodes.Method;
            case VALIDATOR -> AllIcons.Nodes.Lambda;
            case CONSTANT -> AllIcons.Nodes.Constant;
            case VARIABLE -> AllIcons.Nodes.Variable;
            case TEST -> AllIcons.Nodes.Test;
            case BENCHMARK -> AllIcons.Nodes.Function; // Use Function icon as fallback
        };
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
}