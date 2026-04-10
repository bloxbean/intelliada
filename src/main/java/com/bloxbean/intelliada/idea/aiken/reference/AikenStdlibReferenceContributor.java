package com.bloxbean.intelliada.idea.aiken.reference;

import com.bloxbean.intelliada.idea.aiken.lang.AikenLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Reference contributor for Aiken standard library modules and functions
 */
public class AikenStdlibReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        // Register reference provider for ALL elements in Aiken files - we'll filter in the provider
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(),
                new AikenStdlibReferenceProvider()
        );
    }

    private static class AikenStdlibReferenceProvider extends PsiReferenceProvider {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, 
                                                               @NotNull ProcessingContext context) {
            // Only process Aiken files
            PsiFile file = element.getContainingFile();
            if (file == null || !file.getName().endsWith(".ak")) {
                return PsiReference.EMPTY_ARRAY;
            }
            
            String text = element.getText();
            System.out.println("DEBUG: AikenStdlibReferenceContributor checking element: '" + text + "', class: " + element.getClass().getSimpleName() + ", file: " + file.getName());
            
            // Be very aggressive about detecting potential references
            // Check if element is in import context first (highest priority)
            if (isInStdlibImportContext(element)) {
                System.out.println("DEBUG: Found element in stdlib import context: " + text);
                AikenStdlibModuleReference ref = new AikenStdlibModuleReference(element);
                System.out.println("DEBUG: Created reference: " + ref);
                return new PsiReference[] { ref };
            }
            
            // Check direct module reference (any text that looks like a module path)
            if (looksLikeModulePath(text)) {
                System.out.println("DEBUG: Found direct module reference in: " + text);
                AikenStdlibModuleReference ref = new AikenStdlibModuleReference(element);
                System.out.println("DEBUG: Created reference: " + ref);
                return new PsiReference[] { ref };
            }
            
            // Check if this could be part of a module path even if not obvious
            if (couldBePartOfModulePath(element)) {
                System.out.println("DEBUG: Element could be part of module path: " + text);
                AikenStdlibModuleReference ref = new AikenStdlibModuleReference(element);
                System.out.println("DEBUG: Created reference: " + ref);
                return new PsiReference[] { ref };
            }
            
            System.out.println("DEBUG: No reference created for: " + text);
            return PsiReference.EMPTY_ARRAY;
        }
        
        private boolean isInStdlibImportContext(PsiElement element) {
            // Get the file text and check if this element is part of a use statement
            PsiFile file = element.getContainingFile();
            if (file == null) return false;
            
            String fileText = file.getText();
            int elementOffset = element.getTextOffset();
            
            // Find the line containing the element
            String[] lines = fileText.split("\n");
            int currentOffset = 0;
            
            for (String line : lines) {
                int lineEnd = currentOffset + line.length();
                if (elementOffset >= currentOffset && elementOffset <= lineEnd) {
                    // This line contains our element - check if it's a use statement
                    String trimmedLine = line.trim();
                    if (trimmedLine.startsWith("use ")) {
                        return true;
                    }
                    break;
                }
                currentOffset = lineEnd + 1; // +1 for newline
            }
            
            // Fallback: Walk up the tree looking for use statements
            PsiElement current = element;
            while (current != null) {
                String text = current.getText();
                if (text.contains("use ")) {
                    return true;
                }
                current = current.getParent();
            }
            return false;
        }
        
        private boolean looksLikeModulePath(String text) {
            // Check if text looks like a module path (could be from any package)
            text = text.trim();
            
            // Paths with slashes like "aiken/math", "cocktail/vodka_inputs"
            if (text.contains("/")) {
                return true;
            }
            
            // Simple module names like "mocktail" - but be more careful
            // Must be alphanumeric with underscores, no spaces, and reasonable length
            if (text.matches("^[a-zA-Z][a-zA-Z0-9_]{1,30}$")) {
                return true;
            }
            
            return false;
        }
        
        private boolean couldBePartOfModulePath(PsiElement element) {
            String text = element.getText().trim();
            
            // Check if element text could be a module name part
            if (text.matches("^[a-zA-Z][a-zA-Z0-9_]*$") && text.length() > 1) {
                // Check if this element is on a line that contains "use"
                PsiFile file = element.getContainingFile();
                if (file == null) return false;
                
                String fileText = file.getText();
                int elementOffset = element.getTextOffset();
                
                // Find the line containing the element
                String[] lines = fileText.split("\n");
                int currentOffset = 0;
                
                for (String line : lines) {
                    int lineEnd = currentOffset + line.length();
                    if (elementOffset >= currentOffset && elementOffset <= lineEnd) {
                        // This line contains our element
                        return line.contains("use ");
                    }
                    currentOffset = lineEnd + 1; // +1 for newline
                }
            }
            
            return false;
        }
        
        private boolean isStdlibModuleReference(String text) {
            // Check if this is a stdlib module path (keeping for compatibility)
            return text.contains("aiken/") || text.contains("cardano/");
        }
        
        private boolean isStdlibFunctionReference(PsiElement element) {
            // Check if this is a qualified function call like "list.map" or "math.abs"
            String text = element.getText();
            PsiElement parent = element.getParent();
            
            // Look for patterns like "module.function" in the context
            if (parent != null) {
                String parentText = parent.getText();
                if (parentText.contains(".")) {
                    // Check for common stdlib module aliases
                    return parentText.matches(".*\\b(list|dict|math|string|option|result|crypto|address|transaction|assets)\\.\\w+.*");
                }
            }
            
            // Also check if this element is in an import context with specific exports
            return isInImportExportContext(element);
        }
        
        private boolean isInImportExportContext(PsiElement element) {
            // Walk up the tree to find import statements
            PsiElement current = element;
            while (current != null) {
                if (current instanceof PsiFile) {
                    String fileText = current.getText();
                    // Look for import statements that include this element
                    if (fileText.contains("use ") && fileText.contains(".{")) {
                        // Check if this element is part of an import export list
                        String elementText = element.getText();
                        String[] lines = fileText.split("\n");
                        for (String line : lines) {
                            if (line.contains("use ") && line.contains(".{") && line.contains(elementText)) {
                                return true;
                            }
                        }
                    }
                    break;
                }
                current = current.getParent();
            }
            return false;
        }
    }
}