package com.bloxbean.intelliada.idea.aiken.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to handle Aiken standard library file resolution and function lookup
 */
public class AikenStdlibService {
    
    private static final String STDLIB_PATH = "build/packages/aiken-lang-stdlib/lib";
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("^pub fn\\s+(\\w+)\\s*\\(", Pattern.MULTILINE);
    
    private final Project project;
    
    public AikenStdlibService(Project project) {
        this.project = project;
    }
    
    public static AikenStdlibService getInstance(Project project) {
        return project.getService(AikenStdlibService.class);
    }
    
    /**
     * Find any module file for the given module path (delegates to package service)
     */
    @Nullable
    public File findModuleFile(@NotNull String modulePath) {
        System.out.println("DEBUG: AikenStdlibService delegating to AikenPackageService for: " + modulePath);
        
        // Use the new package service for dynamic discovery
        AikenPackageService packageService = AikenPackageService.getInstance(project);
        return packageService.findModuleFile(modulePath);
    }
    
    /**
     * Find a specific function in a stdlib module
     */
    @Nullable
    public PsiElement findFunctionInModule(@NotNull String modulePath, @NotNull String functionName) {
        File moduleFile = findModuleFile(modulePath);
        if (moduleFile == null || !moduleFile.exists()) {
            return null;
        }
        
        try {
            // Read the file content
            String content = Files.readString(moduleFile.toPath());
            
            // Find the function definition
            int functionLine = findFunctionLine(content, functionName);
            if (functionLine != -1) {
                // Convert file to PSI and find the element at the specific line
                VirtualFile virtualFile = VfsUtil.findFileByIoFile(moduleFile, true);
                if (virtualFile != null) {
                    PsiManager psiManager = PsiManager.getInstance(project);
                    PsiFile psiFile = psiManager.findFile(virtualFile);
                    if (psiFile != null) {
                        return findElementAtLine(psiFile, functionLine);
                    }
                }
            }
        } catch (IOException e) {
            // Handle error silently
        }
        
        return null;
    }
    
    /**
     * Find the line number where a function is defined
     */
    private int findFunctionLine(@NotNull String content, @NotNull String functionName) {
        String[] lines = content.split("\n");
        Pattern functionPattern = Pattern.compile("^pub fn\\s+" + Pattern.quote(functionName) + "\\s*\\(");
        
        for (int i = 0; i < lines.length; i++) {
            if (functionPattern.matcher(lines[i]).find()) {
                return i + 1; // Line numbers are 1-based
            }
        }
        
        return -1;
    }
    
    /**
     * Find PSI element at a specific line number
     */
    @Nullable
    private PsiElement findElementAtLine(@NotNull PsiFile psiFile, int lineNumber) {
        String[] lines = psiFile.getText().split("\n");
        if (lineNumber <= 0 || lineNumber > lines.length) {
            return null;
        }
        
        // Calculate the offset of the line
        int offset = 0;
        for (int i = 0; i < lineNumber - 1; i++) {
            offset += lines[i].length() + 1; // +1 for newline
        }
        
        // Find the element at that offset
        PsiElement element = psiFile.findElementAt(offset);
        if (element != null) {
            // Try to find the function declaration element
            PsiElement parent = element.getParent();
            while (parent != null) {
                String parentText = parent.getText();
                if (parentText.contains("pub fn")) {
                    return parent;
                }
                parent = parent.getParent();
            }
            return element;
        }
        
        return null;
    }
    
    /**
     * Get all available stdlib modules
     */
    @NotNull
    public List<String> getAllStdlibModules() {
        return List.of(
            "aiken/builtin", "aiken/cbor", "aiken/fuzz", "aiken/primitive/bytearray", 
            "aiken/primitive/int", "aiken/primitive/string",
            "aiken/collection/dict", "aiken/collection/list", "aiken/collection/pairs",
            "aiken/option", "aiken/result", "aiken/ordering",
            "aiken/string", "aiken/bytearray", "aiken/math", "aiken/interval",
            "aiken/time", "aiken/rational",
            "aiken/crypto", "aiken/hash", "aiken/crypto/bls12_381", "aiken/crypto/ed25519",
            "cardano/address", "cardano/assets", "cardano/certificate", "cardano/credential",
            "cardano/governance", "cardano/script_context", "cardano/transaction",
            "cardano/wallet", "cardano/compatibility"
        );
    }
    
    /**
     * Check if a module path is a stdlib module
     */
    public boolean isStdlibModule(@NotNull String modulePath) {
        return modulePath.startsWith("aiken/") || modulePath.startsWith("cardano/");
    }
}