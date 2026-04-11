package com.bloxbean.intelliada.idea.julc.annotator;

import com.bloxbean.intelliada.idea.julc.common.JulcIcons;
import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

/**
 * Gutter icon provider for julc constructs.
 * Shows icons next to @Validator classes, @Entrypoint methods, @Param fields, and @OnchainLibrary classes.
 *
 * Only activates in julc projects.
 */
public class JulcLineMarkerProvider implements LineMarkerProvider {

    private static final Set<String> VALIDATOR_ANNOTATIONS = Set.of(
            "Validator", "SpendingValidator", "MintingValidator", "MultiValidator",
            "WithdrawValidator", "CertifyingValidator", "VotingValidator", "ProposingValidator"
    );

    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // IntelliJ convention: only process leaf elements (PsiIdentifier)
        if (!(element instanceof PsiIdentifier)) return null;

        // Only in julc projects
        JulcTomlService tomlService = JulcTomlService.getInstance(element.getProject());
        if (tomlService == null || !tomlService.isJulcProject()) return null;

        PsiElement parent = element.getParent();

        // @Validator / @SpendingValidator / etc. on a class
        if (parent instanceof PsiClass psiClass && element.equals(psiClass.getNameIdentifier())) {
            if (hasAnyAnnotation(psiClass, VALIDATOR_ANNOTATIONS)) {
                return createMarker(element, AllIcons.Nodes.Deploy,
                        "julc Validator — Click to build",
                        (e, elt) -> triggerBuild(elt));
            }
            if (hasAnnotation(psiClass, "OnchainLibrary")) {
                return createMarker(element, AllIcons.Nodes.Library,
                        "julc On-chain Library — Click to build",
                        (e, elt) -> triggerBuild(elt));
            }
        }

        // @Entrypoint on a method
        if (parent instanceof PsiMethod psiMethod && element.equals(psiMethod.getNameIdentifier())) {
            if (hasAnnotation(psiMethod, "Entrypoint")) {
                return createMarker(element, AllIcons.RunConfigurations.TestState.Run,
                        "julc Entrypoint — Click to build",
                        (e, elt) -> triggerBuild(elt));
            }
        }

        // @Param on a field
        if (parent instanceof PsiField psiField && element.equals(psiField.getNameIdentifier())) {
            if (hasAnnotation(psiField, "Param")) {
                return createMarker(element, AllIcons.Nodes.Parameter,
                        "julc @Param — compile-time parameter baked into script hash",
                        (e, elt) -> triggerBuild(elt));
            }
        }

        return null;
    }

    private void triggerBuild(PsiElement element) {
        AnAction buildAction = ActionManager.getInstance()
                .getAction("com.bloxbean.intelliada.idea.julc.compile.action.JulcBuildAction");
        if (buildAction != null) {
            DataContext dataContext = SimpleDataContext.getProjectContext(element.getProject());
            AnActionEvent event = AnActionEvent.createFromAnAction(buildAction, null, "JulcGutter", dataContext);
            buildAction.actionPerformed(event);
        }
    }

    private LineMarkerInfo<PsiElement> createMarker(PsiElement element, Icon icon, String tooltip,
                                                     GutterIconNavigationHandler<PsiElement> handler) {
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                icon,
                e -> tooltip,
                handler,
                GutterIconRenderer.Alignment.LEFT,
                () -> tooltip
        );
    }

    private boolean hasAnyAnnotation(PsiModifierListOwner owner, Set<String> annotationNames) {
        PsiModifierList modifierList = owner.getModifierList();
        if (modifierList == null) return false;

        for (PsiAnnotation ann : modifierList.getAnnotations()) {
            String name = ann.getQualifiedName();
            if (name == null) continue;
            for (String target : annotationNames) {
                if (name.endsWith(target)) return true;
            }
        }
        return false;
    }

    private boolean hasAnnotation(PsiModifierListOwner owner, String annotationSimpleName) {
        PsiModifierList modifierList = owner.getModifierList();
        if (modifierList == null) return false;

        for (PsiAnnotation ann : modifierList.getAnnotations()) {
            String name = ann.getQualifiedName();
            if (name != null && name.endsWith(annotationSimpleName)) return true;
        }
        return false;
    }
}
