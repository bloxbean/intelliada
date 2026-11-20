package com.bloxbean.intelliada.idea.joust.editor;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.intellij.model.Pointer;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ADR-004 I15 (M1-SEC-3): the Ctrl+hover and quick-navigate hint of a .jsc navigation target, built by IntelliAda from
 * escaped text. Without it the platform's fallback puts the target's file name, which a repository chooses, into the
 * hint's HTML unescaped.
 */
public final class JoustNavigationHintProvider implements PsiDocumentationTargetProvider {

    @Override
    public @Nullable DocumentationTarget documentationTarget(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        return element instanceof JoustNavigationElement e ? new Hint(e) : null;
    }

    static final class Hint implements DocumentationTarget {
        private final JoustNavigationElement element;

        Hint(JoustNavigationElement element) {
            this.element = element;
        }

        @Override
        public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
            return Pointer.hardPointer(this);
        }

        @Override
        public @NotNull TargetPresentation computePresentation() {
            return TargetPresentation.builder(PlainText.label(element.getContainingFile().getName(), 120)).presentation();
        }

        @Override
        public @Nullable String computeDocumentationHint() {
            return PlainText.fragment(element.getContainingFile().getName() + ": " + element.getPresentableText(), 300);
        }

        @Override
        public @Nullable DocumentationResult computeDocumentation() {
            return DocumentationResult.documentation("<pre>" + PlainText.escape(PlainText.clean(element.getPresentableText(), 300)) + "</pre>");
        }
    }
}
