package com.bloxbean.intelliada.idea.joust.editor;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.NotNull;

/** A navigation target at an exact offset of an in-project .jsc file (TextMate files have only a flat PSI). */
final class JoustNavigationElement extends FakePsiElement {
    private final PsiFile file;
    private final int offset;
    private final String text;

    JoustNavigationElement(PsiFile file, int offset, String text) {
        this.file = file;
        this.offset = offset;
        this.text = text;
    }

    @Override
    public PsiElement getParent() {
        return file;
    }

    @Override
    public PsiFile getContainingFile() {
        return file;
    }

    @Override
    public int getTextOffset() {
        return offset;
    }

    @Override
    public TextRange getTextRange() {
        return TextRange.from(offset, 0);
    }

    @Override
    public String getPresentableText() {
        return text;
    }

    @Override
    public String getLocationString() {
        return file.getName();
    }

    @Override
    public boolean canNavigate() {
        return true;
    }

    @Override
    public void navigate(boolean requestFocus) {
        VirtualFile vf = file.getVirtualFile();
        if (vf != null) JoustSafeOpen.open(getProject(), vf, offset, requestFocus);
    }

    @Override
    public @NotNull com.intellij.openapi.project.Project getProject() {
        return file.getProject();
    }
}
