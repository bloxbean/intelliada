package com.bloxbean.intelliada.idea.aiken.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Service to discover and manage Aiken packages dynamically
 * Supports both stdlib (aiken-lang-stdlib) and third-party packages
 */
public class AikenPackageService {
    
    private static final String BUILD_PACKAGES_PATH = "build/packages";
    private static final String STDLIB_PACKAGE_NAME = "aiken-lang-stdlib";
    
    private final Project project;
    private Map<String, PackageInfo> packageRegistry = new HashMap<>();
    private boolean registryInitialized = false;
    
    public AikenPackageService(Project project) {
        this.project = project;
    }
    
    public static AikenPackageService getInstance(Project project) {
        return project.getService(AikenPackageService.class);
    }
    
    /**
     * Find a module file by import path (e.g., "aiken/math", "mocktail", "sidan/utils")
     */
    @Nullable
    public File findModuleFile(@NotNull String importPath) {
        ensureRegistryInitialized();
        
        System.out.println("DEBUG: Looking for import path: " + importPath);
        
        // Try different strategies to resolve the import path
        
        // Strategy 1: Direct package match (e.g., "mocktail" -> look in all packages for mocktail.ak)
        if (!importPath.contains("/")) {
            File moduleFile = findDirectModule(importPath);
            if (moduleFile != null) {
                System.out.println("DEBUG: Found direct module: " + moduleFile.getAbsolutePath());
                return moduleFile;
            }
        }
        
        // Strategy 2: Stdlib path (e.g., "aiken/math" -> aiken-lang-stdlib/lib/aiken/math.ak)
        if (importPath.startsWith("aiken/") || importPath.startsWith("cardano/")) {
            File stdlibFile = findStdlibModule(importPath);
            if (stdlibFile != null) {
                System.out.println("DEBUG: Found stdlib module: " + stdlibFile.getAbsolutePath());
                return stdlibFile;
            }
        }
        
        // Strategy 3: Third-party package path (e.g., "sidan/utils" -> look in sidan-lab-* packages)
        File thirdPartyFile = findThirdPartyModule(importPath);
        if (thirdPartyFile != null) {
            System.out.println("DEBUG: Found third-party module: " + thirdPartyFile.getAbsolutePath());
            return thirdPartyFile;
        }
        
        System.out.println("DEBUG: Module not found for import path: " + importPath);
        return null;
    }
    
    private File findDirectModule(String moduleName) {
        String moduleFileName = moduleName + ".ak";
        
        // Search in all package lib directories
        for (PackageInfo packageInfo : packageRegistry.values()) {
            File moduleFile = new File(packageInfo.libPath, moduleFileName);
            if (moduleFile.exists()) {
                return moduleFile;
            }
            
            // Also search in subdirectories (e.g., lib/mocktail/mocktail.ak)
            File subdirFile = new File(packageInfo.libPath, moduleName + "/" + moduleFileName);
            if (subdirFile.exists()) {
                return subdirFile;
            }
        }
        
        return null;
    }
    
    private File findStdlibModule(String importPath) {
        PackageInfo stdlibPackage = packageRegistry.get(STDLIB_PACKAGE_NAME);
        if (stdlibPackage == null) {
            return null;
        }
        
        String filePath = importPath + ".ak";
        File moduleFile = new File(stdlibPackage.libPath, filePath);
        
        return moduleFile.exists() ? moduleFile : null;
    }
    
    private File findThirdPartyModule(String importPath) {
        // For paths like "sidan/utils", try to find in packages starting with "sidan"
        String[] parts = importPath.split("/", 2);
        if (parts.length >= 1) {
            String packagePrefix = parts[0];
            String remainingPath = parts.length > 1 ? parts[1] : "";
            
            // Look for packages that might contain this module
            for (Map.Entry<String, PackageInfo> entry : packageRegistry.entrySet()) {
                String packageName = entry.getKey();
                PackageInfo packageInfo = entry.getValue();
                
                // Skip stdlib
                if (STDLIB_PACKAGE_NAME.equals(packageName)) {
                    continue;
                }
                
                // Check if package name contains the prefix (e.g., sidan-lab-vodka contains "sidan")
                if (packageName.toLowerCase().contains(packagePrefix.toLowerCase())) {
                    String filePath = remainingPath.isEmpty() ? 
                        packagePrefix + ".ak" : 
                        remainingPath + ".ak";
                    
                    File moduleFile = new File(packageInfo.libPath, filePath);
                    if (moduleFile.exists()) {
                        return moduleFile;
                    }
                    
                    // Also try without the prefix
                    if (!remainingPath.isEmpty()) {
                        File altFile = new File(packageInfo.libPath, remainingPath + ".ak");
                        if (altFile.exists()) {
                            return altFile;
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    private void ensureRegistryInitialized() {
        if (!registryInitialized) {
            discoverPackages();
            registryInitialized = true;
        }
    }
    
    private void discoverPackages() {
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            System.out.println("DEBUG: Project path is null, cannot discover packages");
            return;
        }
        
        File packagesDir = new File(projectPath, BUILD_PACKAGES_PATH);
        if (!packagesDir.exists() || !packagesDir.isDirectory()) {
            System.out.println("DEBUG: Packages directory not found: " + packagesDir.getAbsolutePath());
            return;
        }
        
        System.out.println("DEBUG: Discovering packages in: " + packagesDir.getAbsolutePath());
        
        File[] packageDirs = packagesDir.listFiles(File::isDirectory);
        if (packageDirs != null) {
            for (File packageDir : packageDirs) {
                String packageName = packageDir.getName();
                File libDir = new File(packageDir, "lib");
                
                if (libDir.exists() && libDir.isDirectory()) {
                    PackageInfo packageInfo = new PackageInfo(packageName, packageDir.getAbsolutePath(), libDir.getAbsolutePath());
                    packageRegistry.put(packageName, packageInfo);
                    System.out.println("DEBUG: Registered package: " + packageName + " -> " + libDir.getAbsolutePath());
                } else {
                    System.out.println("DEBUG: Package " + packageName + " has no lib directory");
                }
            }
        }
        
        System.out.println("DEBUG: Discovered " + packageRegistry.size() + " packages");
    }
    
    /**
     * Get all available packages
     */
    public Map<String, PackageInfo> getAllPackages() {
        ensureRegistryInitialized();
        return new HashMap<>(packageRegistry);
    }
    
    /**
     * Refresh the package registry (useful after aiken build)
     */
    public void refreshPackages() {
        packageRegistry.clear();
        registryInitialized = false;
        ensureRegistryInitialized();
    }
    
    /**
     * Information about a discovered package
     */
    public static class PackageInfo {
        public final String name;
        public final String packagePath;
        public final String libPath;
        
        public PackageInfo(String name, String packagePath, String libPath) {
            this.name = name;
            this.packagePath = packagePath;
            this.libPath = libPath;
        }
        
        @Override
        public String toString() {
            return "PackageInfo{name='" + name + "', libPath='" + libPath + "'}";
        }
    }
}