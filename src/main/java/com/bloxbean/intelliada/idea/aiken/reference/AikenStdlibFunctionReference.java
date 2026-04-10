package com.bloxbean.intelliada.idea.aiken.reference;

import com.bloxbean.intelliada.idea.aiken.service.AikenStdlibService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Reference implementation for Aiken standard library function calls
 */
public class AikenStdlibFunctionReference extends PsiReferenceBase<PsiElement> {
    
    private final String functionName;
    private final String modulePath;
    
    public AikenStdlibFunctionReference(@NotNull PsiElement element) {
        super(element);
        String[] moduleAndFunction = extractModuleAndFunction(element);
        this.modulePath = moduleAndFunction[0];
        this.functionName = moduleAndFunction[1];
    }
    
    @Override
    public @Nullable PsiElement resolve() {
        if (modulePath == null || functionName == null) {
            return null;
        }
        
        // Use the stdlib service to find the function in the module file
        AikenStdlibService stdlibService = AikenStdlibService.getInstance(getElement().getProject());
        PsiElement functionElement = stdlibService.findFunctionInModule(modulePath, functionName);
        
        return functionElement;
    }
    
    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        // For now, we don't support renaming stdlib functions
        throw new IncorrectOperationException("Cannot rename standard library functions");
    }
    
    @Override
    public @NotNull String getCanonicalText() {
        return functionName != null ? functionName : "";
    }
    
    @Override
    public @NotNull TextRange getRangeInElement() {
        return TextRange.from(0, getElement().getTextLength());
    }
    
    private String[] extractModuleAndFunction(PsiElement element) {
        String text = element.getText();
        PsiElement parent = element.getParent();
        
        // Look for qualified function calls like "list.map", "math.abs", etc.
        if (parent != null) {
            String parentText = parent.getText();
            
            // Check for patterns like "module.function"
            if (parentText.contains(".")) {
                String[] parts = parentText.split("\\.");
                if (parts.length >= 2) {
                    String moduleAlias = parts[0].trim();
                    String functionName = parts[1].trim();
                    
                    // Map common aliases to full module paths
                    String fullModulePath = mapAliasToModulePath(moduleAlias);
                    
                    return new String[]{fullModulePath, functionName};
                }
            }
        }
        
        // Look for import context to determine module
        String contextModule = findModuleFromImportContext(element);
        if (contextModule != null) {
            return new String[]{contextModule, text};
        }
        
        return new String[]{null, null};
    }
    
    private String mapAliasToModulePath(String alias) {
        // Map common aliases to full module paths
        return switch (alias) {
            case "list" -> "aiken/collection/list";
            case "dict" -> "aiken/collection/dict";
            case "math" -> "aiken/math";
            case "string" -> "aiken/string";
            case "option" -> "aiken/option";
            case "result" -> "aiken/result";
            case "crypto" -> "aiken/crypto";
            case "address" -> "cardano/address";
            case "transaction" -> "cardano/transaction";
            case "assets" -> "cardano/assets";
            default -> alias; // Return as-is for unknown aliases
        };
    }
    
    private String findModuleFromImportContext(PsiElement element) {
        // Walk up the PSI tree to find import statements
        PsiElement current = element;
        while (current != null) {
            if (current instanceof PsiFile) {
                // Search for import statements in the file
                String fileText = current.getText();
                // Look for patterns like "use aiken/math.{abs, max}"
                if (fileText.contains("use ")) {
                    String[] lines = fileText.split("\n");
                    for (String line : lines) {
                        if (line.trim().startsWith("use ") && line.contains(".{")) {
                            String modulePath = extractModulePathFromImport(line);
                            if (modulePath != null && line.contains(element.getText())) {
                                return modulePath;
                            }
                        }
                    }
                }
                break;
            }
            current = current.getParent();
        }
        return null;
    }
    
    private String extractModulePathFromImport(String importLine) {
        // Extract module path from "use aiken/math.{abs, max}" -> "aiken/math"
        String trimmed = importLine.trim();
        if (trimmed.startsWith("use ")) {
            String afterUse = trimmed.substring(4).trim();
            int braceIndex = afterUse.indexOf(".{");
            if (braceIndex > 0) {
                return afterUse.substring(0, braceIndex).trim();
            }
        }
        return null;
    }
}