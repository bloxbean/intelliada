package com.bloxbean.intelliada.idea.aiken.reference;

import com.bloxbean.intelliada.idea.aiken.service.AikenStdlibService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Reference implementation for Aiken standard library module imports
 */
public class AikenStdlibModuleReference extends PsiReferenceBase<PsiElement> {
    
    private final String modulePath;
    
    public AikenStdlibModuleReference(@NotNull PsiElement element) {
        super(element);
        this.modulePath = extractModulePath(element);
        System.out.println("DEBUG: AikenStdlibModuleReference created for element '" + element.getText() + "' with modulePath: " + modulePath);
    }
    
    @Override
    public @Nullable PsiElement resolve() {
        System.out.println("DEBUG: AikenStdlibModuleReference.resolve() called for element: '" + getElement().getText() + "'");
        if (modulePath == null) {
            System.out.println("DEBUG: modulePath is null for element: " + getElement().getText());
            return null;
        }
        
        System.out.println("DEBUG: Resolving module path: " + modulePath);
        
        // Use the stdlib service to find the module file
        AikenStdlibService stdlibService = AikenStdlibService.getInstance(getElement().getProject());
        File moduleFile = stdlibService.findModuleFile(modulePath);
        
        System.out.println("DEBUG: Found module file: " + (moduleFile != null ? moduleFile.getAbsolutePath() : "null"));
        
        if (moduleFile != null && moduleFile.exists()) {
            // Convert file to PSI element
            PsiManager psiManager = PsiManager.getInstance(getElement().getProject());
            com.intellij.openapi.vfs.VirtualFile virtualFile = com.intellij.openapi.vfs.VfsUtil.findFileByIoFile(moduleFile, true);
            System.out.println("DEBUG: Virtual file: " + virtualFile);
            if (virtualFile != null) {
                PsiFile psiFile = psiManager.findFile(virtualFile);
                System.out.println("DEBUG: PSI file: " + psiFile);
                return psiFile;
            }
        }
        
        return null;
    }
    
    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        // For now, we don't support renaming stdlib modules
        throw new IncorrectOperationException("Cannot rename standard library modules");
    }
    
    @Override
    public @NotNull String getCanonicalText() {
        return modulePath != null ? modulePath : "";
    }
    
    @Override
    public @NotNull TextRange getRangeInElement() {
        return TextRange.from(0, getElement().getTextLength());
    }
    
    private String extractModulePath(PsiElement element) {
        String text = element.getText();
        System.out.println("DEBUG: Extracting module path from text: '" + text + "'");
        
        // Remove quotes if present
        if (text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        
        // First, try to find the specific use statement that contains this element
        String containingLine = findContainingUseLine(element);
        if (containingLine != null) {
            System.out.println("DEBUG: Found containing use line: " + containingLine);
            return extractStdlibPath(containingLine);
        }
        
        // Fallback: Direct module path
        if (looksLikeModulePath(text)) {
            String extracted = extractStdlibPath(text);
            System.out.println("DEBUG: Extracted direct path: " + extracted);
            return extracted;
        }
        
        // Look in surrounding context for use statements
        PsiElement parent = element.getParent();
        while (parent != null) {
            String parentText = parent.getText();
            if (parentText.contains("use ")) {
                String extracted = extractStdlibPath(parentText);
                System.out.println("DEBUG: Extracted from parent: " + extracted);
                return extracted;
            }
            parent = parent.getParent();
        }
        
        System.out.println("DEBUG: No module path found");
        return null;
    }
    
    private String findContainingUseLine(PsiElement element) {
        // Get the file text and find which line contains the cursor
        PsiFile file = element.getContainingFile();
        if (file == null) return null;
        
        String fileText = file.getText();
        int elementOffset = element.getTextOffset();
        
        // Find the line containing the element
        String[] lines = fileText.split("\n");
        int currentOffset = 0;
        
        for (String line : lines) {
            int lineEnd = currentOffset + line.length();
            if (elementOffset >= currentOffset && elementOffset <= lineEnd) {
                // This line contains our element
                if (line.trim().startsWith("use ")) {
                    return line.trim();
                }
                break;
            }
            currentOffset = lineEnd + 1; // +1 for newline
        }
        
        return null;
    }
    
    private boolean looksLikeModulePath(String text) {
        // Check if text looks like a module path (could be from any package)
        return text.contains("/") || // paths like "aiken/math", "cocktail/vodka_inputs" 
               text.matches("^[a-zA-Z][a-zA-Z0-9_]*$"); // simple names like "mocktail"
    }
    
    private String extractStdlibPath(String text) {
        // Extract module path from use statements - now generic for any package
        if (text.contains("use ")) {
            int useIndex = text.indexOf("use ");
            String afterUse = text.substring(useIndex + 4).trim();
            
            // Remove trailing syntax
            if (afterUse.contains(".{")) {
                afterUse = afterUse.substring(0, afterUse.indexOf(".{"));
            }
            if (afterUse.contains(" ")) {
                afterUse = afterUse.substring(0, afterUse.indexOf(" "));
            }
            if (afterUse.contains("\"")) {
                afterUse = afterUse.substring(0, afterUse.indexOf("\""));
            }
            return afterUse.trim();
        }
        
        // Fallback: try to find any module-like path
        String cleaned = text.trim();
        if (cleaned.contains(".{")) {
            cleaned = cleaned.substring(0, cleaned.indexOf(".{"));
        }
        if (cleaned.contains(" ")) {
            cleaned = cleaned.substring(0, cleaned.indexOf(" "));
        }
        if (cleaned.contains("\"")) {
            cleaned = cleaned.substring(0, cleaned.indexOf("\""));
        }
        
        // Check if it looks like a module path (contains letters/numbers/slashes)
        if (cleaned.matches("[a-zA-Z0-9_/\\-]+")) {
            return cleaned;
        }
        
        return null;
    }
}