package com.bloxbean.intelliada.idea.aiken.navigation;

import com.bloxbean.intelliada.idea.aiken.service.AikenStdlibService;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Provides line markers for stdlib module imports to enable navigation
 */
public class AikenStdlibLineMarkerProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // Check if this element is a stdlib module import
        String text = element.getText();
        
        if (isStdlibModuleImport(element, text)) {
            String modulePath = extractModulePath(text);
            if (modulePath != null) {
                AikenStdlibService stdlibService = AikenStdlibService.getInstance(element.getProject());
                File moduleFile = stdlibService.findModuleFile(modulePath);
                
                if (moduleFile != null && moduleFile.exists()) {
                    return NavigationGutterIconBuilder.create(AllIcons.Gutter.ExtAnnotation)
                            .setTarget(element)
                            .setTooltipText("Go to " + modulePath + " source")
                            .createLineMarkerInfo(element);
                }
            }
        }
        
        return null;
    }
    
    private boolean isStdlibModuleImport(PsiElement element, String text) {
        // Check if this is part of a use statement
        PsiElement parent = element.getParent();
        while (parent != null) {
            String parentText = parent.getText();
            if (parentText.contains("use ") && (text.contains("aiken/") || text.contains("cardano/"))) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }
    
    private String extractModulePath(String text) {
        // Remove quotes if present
        if (text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        
        // Extract module path
        if (text.contains("aiken/") || text.contains("cardano/")) {
            // Handle cases like "aiken/math" or "use aiken/math.{abs}"
            int aikenIndex = text.indexOf("aiken/");
            int cardanoIndex = text.indexOf("cardano/");
            
            int startIndex = -1;
            if (aikenIndex != -1 && cardanoIndex != -1) {
                startIndex = Math.min(aikenIndex, cardanoIndex);
            } else if (aikenIndex != -1) {
                startIndex = aikenIndex;
            } else if (cardanoIndex != -1) {
                startIndex = cardanoIndex;
            }
            
            if (startIndex != -1) {
                String modulePath = text.substring(startIndex);
                // Remove any trailing syntax like ".{" or spaces
                if (modulePath.contains(".{")) {
                    modulePath = modulePath.substring(0, modulePath.indexOf(".{"));
                }
                if (modulePath.contains(" ")) {
                    modulePath = modulePath.substring(0, modulePath.indexOf(" "));
                }
                return modulePath.trim();
            }
        }
        
        return null;
    }
}