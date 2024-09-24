package com.bloxbean.intelliada.idea.folding;

import com.bloxbean.intelliada.idea.aiken.lang.psi.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AikenFolderingBuilder implements FoldingBuilder, DumbAware {

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document) {
        List<FoldingDescriptor> descriptors = new ArrayList<>();
        addDescriptorsForNode(descriptors, node);
        return descriptors.toArray(new FoldingDescriptor[0]);
    }

    private void addDescriptorsForNode(List<FoldingDescriptor> descriptors, ASTNode node) {
        PsiElement psi = node.getPsi();
            IElementType type = psi.getNode().getElementType();
            if (type == AikenTypes.LBRACE || type == AikenTypes.RBRACE) {
                PsiElement parent = psi.getParent();
                if (parent != null) {
                    PsiElement lBrace = parent.getFirstChild();
                    PsiElement rBrace = parent.getLastChild();
                    if (lBrace != null && rBrace != null && lBrace != rBrace) {
                        int start = lBrace.getTextRange().getStartOffset();
                        int end = rBrace.getTextRange().getEndOffset();

                        descriptors.add(new FoldingDescriptor(parent.getNode(),
                                new TextRange(start, end)));
                    }
                }
            }

        for (ASTNode child : node.getChildren(null)) {
            addDescriptorsForNode(descriptors, child);
        }
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        return "{...}";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
