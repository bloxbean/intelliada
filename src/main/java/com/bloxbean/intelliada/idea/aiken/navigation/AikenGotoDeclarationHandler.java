package com.bloxbean.intelliada.idea.aiken.navigation;

import com.bloxbean.intelliada.idea.aiken.service.AikenStdlibService;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Direct navigation handler for Aiken imports - bypasses reference resolution
 */
public class AikenGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement,
                                                             int offset,
                                                             Editor editor) {
        System.out.println("DEBUG: AikenGotoDeclarationHandler called for element: " + 
                          (sourceElement != null ? sourceElement.getText() : "null"));
        
        if (sourceElement == null) return null;
        
        // Only handle .ak files
        PsiFile file = sourceElement.getContainingFile();
        if (file == null || !file.getName().endsWith(".ak")) {
            return null;
        }
        
        System.out.println("DEBUG: Processing Aiken file: " + file.getName());
        
        // Extract module path and specific function name from the import context
        ImportInfo importInfo = extractImportInfoFromContext(sourceElement);
        if (importInfo.modulePath == null) {
            System.out.println("DEBUG: No module path found");
            return null;
        }
        
        System.out.println("DEBUG: Extracted module path: " + importInfo.modulePath);
        System.out.println("DEBUG: Target function/type: " + importInfo.targetName);
        
        // Find the module file
        AikenStdlibService stdlibService = AikenStdlibService.getInstance(sourceElement.getProject());
        File moduleFile = stdlibService.findModuleFile(importInfo.modulePath);
        
        if (moduleFile != null && moduleFile.exists()) {
            System.out.println("DEBUG: Found module file: " + moduleFile.getAbsolutePath());
            
            // Convert to PSI element
            VirtualFile virtualFile = VfsUtil.findFileByIoFile(moduleFile, true);
            if (virtualFile != null) {
                PsiManager psiManager = PsiManager.getInstance(sourceElement.getProject());
                PsiFile psiFile = psiManager.findFile(virtualFile);
                if (psiFile != null) {
                    System.out.println("DEBUG: Successfully created PSI file");
                    
                    // If we have a specific target name, try to find it within the file
                    if (importInfo.targetName != null) {
                        PsiElement specificTarget = findSpecificTargetElement(psiFile, importInfo.targetName);
                        if (specificTarget != null) {
                            System.out.println("DEBUG: Found specific target element: " + importInfo.targetName);
                            return new PsiElement[] { specificTarget };
                        }
                    }
                    
                    // Fallback to file-level navigation
                    System.out.println("DEBUG: Using file-level navigation");
                    return new PsiElement[] { psiFile };
                }
            }
        }
        
        System.out.println("DEBUG: No navigation target found");
        return null;
    }
    
    private static class ImportInfo {
        String modulePath;
        String targetName; // specific function/type name, null for module-level imports
        
        ImportInfo(String modulePath, String targetName) {
            this.modulePath = modulePath;
            this.targetName = targetName;
        }
    }
    
    private ImportInfo extractImportInfoFromContext(PsiElement element) {
        // Get the file text and check if this element is part of a use statement
        PsiFile file = element.getContainingFile();
        if (file == null) return new ImportInfo(null, null);
        
        String fileText = file.getText();
        int elementOffset = element.getTextOffset();
        String elementText = element.getText();
        
        // Find the line containing the element
        String[] lines = fileText.split("\n");
        int currentOffset = 0;
        
        for (String line : lines) {
            int lineEnd = currentOffset + line.length();
            if (elementOffset >= currentOffset && elementOffset <= lineEnd) {
                // This line contains our element - check if it's a use statement
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("use ")) {
                    return extractImportInfoFromLine(trimmedLine, elementText);
                }
                break;
            }
            currentOffset = lineEnd + 1; // +1 for newline
        }
        
        return new ImportInfo(null, null);
    }
    
    private ImportInfo extractImportInfoFromLine(String line, String elementText) {
        System.out.println("DEBUG: Extracting import info from line: '" + line + "', element: '" + elementText + "'");
        
        // Extract module path first
        String modulePath = extractStdlibPath(line);
        if (modulePath == null) {
            return new ImportInfo(null, null);
        }
        
        // Check if the element text corresponds to a specific function/type import
        String targetName = null;
        
        // Check if this is a specific import (has .{...})
        if (line.contains(".{") && line.contains("}")) {
            int startBrace = line.indexOf(".{");
            int endBrace = line.indexOf("}", startBrace);
            if (startBrace != -1 && endBrace != -1) {
                String importList = line.substring(startBrace + 2, endBrace);
                // Check if our element text is one of the imported names
                String[] imports = importList.split(",");
                for (String importName : imports) {
                    String cleanImport = importName.trim();
                    if (cleanImport.equals(elementText)) {
                        targetName = elementText;
                        break;
                    }
                }
            }
        }
        
        System.out.println("DEBUG: Extracted - modulePath: '" + modulePath + "', targetName: '" + targetName + "'");
        return new ImportInfo(modulePath, targetName);
    }
    
    private PsiElement findSpecificTargetElement(PsiFile psiFile, String targetName) {
        System.out.println("DEBUG: Searching for target '" + targetName + "' in file: " + psiFile.getName());
        
        String fileContent = psiFile.getText();
        
        // Search for function definitions: "pub fn targetName(" or "fn targetName("
        String[] patterns = {
            "pub fn " + targetName + "\\s*\\(",
            "fn " + targetName + "\\s*\\(",
            "pub type " + targetName + "\\s*[={]",
            "type " + targetName + "\\s*[={]",
            "pub const " + targetName + "\\s*[=:]",
            "const " + targetName + "\\s*[=:]"
        };
        
        for (String pattern : patterns) {
            int index = findPatternIndex(fileContent, pattern);
            if (index != -1) {
                System.out.println("DEBUG: Found target using pattern: " + pattern + " at index: " + index);
                
                // Find the exact position of the target name
                int targetNameIndex = findTargetNameInPattern(fileContent, targetName, index);
                if (targetNameIndex != -1) {
                    // Get PSI element at the target name position
                    PsiElement elementAtOffset = psiFile.findElementAt(targetNameIndex);
                    if (elementAtOffset != null) {
                        System.out.println("DEBUG: Found PSI element at target name: " + elementAtOffset.getText());
                        
                        // Walk up to find a meaningful parent element (usually the function/type declaration)
                        PsiElement parent = elementAtOffset;
                        int maxWalkUp = 5; // Limit to prevent infinite loops
                        while (parent != null && maxWalkUp > 0) {
                            String parentText = parent.getText();
                            // Look for a parent that contains the full declaration
                            if (parentText.contains(targetName) && 
                                (parentText.contains("fn ") || parentText.contains("type ") || parentText.contains("const "))) {
                                System.out.println("DEBUG: Using parent element: " + parent.getClass().getSimpleName());
                                return parent;
                            }
                            parent = parent.getParent();
                            maxWalkUp--;
                            if (parent == psiFile) break; // Don't go beyond the file
                        }
                        
                        // Fallback to the original element
                        return elementAtOffset;
                    }
                }
            }
        }
        
        System.out.println("DEBUG: Target '" + targetName + "' not found in file");
        return null;
    }
    
    private int findTargetNameInPattern(String content, String targetName, int patternStart) {
        // Find the actual position of the target name within the matched pattern
        try {
            // Look for the target name starting from the pattern match
            String substring = content.substring(patternStart, Math.min(content.length(), patternStart + 100));
            int nameIndex = substring.indexOf(targetName);
            if (nameIndex != -1) {
                return patternStart + nameIndex;
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Error finding target name position: " + e.getMessage());
        }
        return -1;
    }
    
    private int findPatternIndex(String content, String regex) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            java.util.regex.Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return matcher.start();
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Regex error for pattern '" + regex + "': " + e.getMessage());
        }
        return -1;
    }
    
    private String extractModulePathFromContext(PsiElement element) {
        // Get the file text and check if this element is part of a use statement
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
                // This line contains our element - check if it's a use statement
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("use ")) {
                    return extractStdlibPath(trimmedLine);
                }
                break;
            }
            currentOffset = lineEnd + 1; // +1 for newline
        }
        
        return null;
    }
    
    private String extractStdlibPath(String text) {
        // Extract module path from use statements - generic for any package
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
            
            String result = afterUse.trim();
            System.out.println("DEBUG: Extracted module path: '" + result + "'");
            return result;
        }
        
        return null;
    }
}