package com.bloxbean.intelliada.idea.aiken.comment;

import com.bloxbean.intelliada.idea.aiken.lang.psi.AikenTypes;
import com.intellij.codeInsight.generation.IndentedCommenter;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

public class AikenCommenter implements CodeDocumentationAwareCommenter, IndentedCommenter {
    public static final String COMMENT = "// ";
    public static final String DOC_COMMENT = "/// ";

    @Override
    public @Nullable Boolean forceIndentedLineComment() {
        return true;
    }

    @Override
    public @Nullable IElementType getLineCommentTokenType() {
        return AikenTypes.COMMENT;
    }

    @Override
    public @Nullable IElementType getBlockCommentTokenType() {
        return null;
    }

    @Override
    public @Nullable IElementType getDocumentationCommentTokenType() {
        return AikenTypes.DOC_COMMENT;
    }

    @Override
    public @Nullable String getDocumentationCommentPrefix() {
        return null;
    }

    @Override
    public @Nullable String getDocumentationCommentLinePrefix() {
        return DOC_COMMENT;
    }

    @Override
    public @Nullable String getDocumentationCommentSuffix() {
        return null;
    }

    @Override
    public boolean isDocumentationComment(PsiComment element) {
        return false;
    }

    @Override
    public @Nullable String getLineCommentPrefix() {
        return COMMENT;
    }

    @Override
    public @Nullable String getBlockCommentPrefix() {
        return null;
    }

    @Override
    public @Nullable String getBlockCommentSuffix() {
        return null;
    }

    @Override
    public @Nullable String getCommentedBlockCommentPrefix() {
        return null;
    }

    @Override
    public @Nullable String getCommentedBlockCommentSuffix() {
        return null;
    }
}
