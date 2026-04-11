package com.bloxbean.intelliada.idea.aiken.action;

import com.bloxbean.intelliada.idea.aiken.service.AikenStdlibService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Test action to verify stdlib navigation is working
 */
public class TestStdlibNavigationAction extends AnAction {

    public TestStdlibNavigationAction() {
        super("Test Stdlib Navigation");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            Messages.showErrorDialog("No project found", "Error");
            return;
        }

        // Get current editor and cursor position
        com.intellij.openapi.editor.Editor editor = com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR.getData(e.getDataContext());
        if (editor != null) {
            // Get element at cursor
            com.intellij.psi.PsiFile psiFile = com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE.getData(e.getDataContext());
            if (psiFile != null) {
                int offset = editor.getCaretModel().getOffset();
                com.intellij.psi.PsiElement element = psiFile.findElementAt(offset);
                
                System.out.println("DEBUG: Element at cursor: " + (element != null ? element.getText() : "null"));
                
                // Try to extract module from cursor position
                String modulePath = extractModuleFromElement(element);
                System.out.println("DEBUG: Extracted module path: " + modulePath);
                
                if (modulePath != null) {
                    testNavigation(project, modulePath);
                    return;
                }
            }
        }

        // Fallback: Test navigation to aiken/math
        testNavigation(project, "aiken/math");
    }
    
    private void testNavigation(Project project, String modulePath) {
        AikenStdlibService stdlibService = AikenStdlibService.getInstance(project);
        File moduleFile = stdlibService.findModuleFile(modulePath);
        
        if (moduleFile != null && moduleFile.exists()) {
            System.out.println("DEBUG: File found: " + moduleFile.getAbsolutePath());
            
            // Open the file
            ApplicationManager.getApplication().invokeLater(() -> {
                VirtualFile virtualFile = VfsUtil.findFileByIoFile(moduleFile, true);
                System.out.println("DEBUG: Virtual file created: " + virtualFile);
                
                if (virtualFile != null) {
                    System.out.println("DEBUG: Opening file in editor...");
                    FileEditorManager.getInstance(project).openFile(virtualFile, true);
                    Messages.showInfoMessage("Successfully opened: " + moduleFile.getAbsolutePath(), "Success");
                } else {
                    System.out.println("DEBUG: Failed to create virtual file");
                    Messages.showErrorDialog("Could not create virtual file for: " + moduleFile.getAbsolutePath(), "Error");
                }
            });
        } else {
            System.out.println("DEBUG: File not found or doesn't exist");
            Messages.showErrorDialog("Could not find " + modulePath + " module. Expected at: " + 
                (moduleFile != null ? moduleFile.getAbsolutePath() : "unknown path"), "Error");
        }
    }
    
    private String extractModuleFromElement(com.intellij.psi.PsiElement element) {
        if (element == null) return null;
        
        System.out.println("DEBUG: Looking for module from element: '" + element.getText() + "'");
        
        // First, try to find the specific use statement that contains this element
        String containingLine = findContainingUseLine(element);
        if (containingLine != null) {
            System.out.println("DEBUG: Found containing use line: " + containingLine);
            return extractStdlibPath(containingLine);
        }
        
        // Fallback: Walk up the tree to find any import context
        com.intellij.psi.PsiElement current = element;
        while (current != null) {
            String text = current.getText();
            if (text.contains("use ")) {
                System.out.println("DEBUG: Found use statement: " + text);
                return extractStdlibPath(text);
            }
            current = current.getParent();
        }
        
        return null;
    }
    
    private String findContainingUseLine(com.intellij.psi.PsiElement element) {
        // Get the file text and find which line contains the cursor
        com.intellij.psi.PsiFile file = element.getContainingFile();
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
    
    private String extractStdlibPath(String text) {
        System.out.println("DEBUG: Extracting path from: '" + text + "'");
        
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
            
            String result = afterUse.trim();
            System.out.println("DEBUG: Extracted module path: '" + result + "'");
            return result;
        }
        
        return null;
    }
}