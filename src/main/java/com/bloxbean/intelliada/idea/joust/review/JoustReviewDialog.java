package com.bloxbean.intelliada.idea.joust.review;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * The review modal (D13): the summary as plain text (I15), "Sign and submit" or "Decline". Shown before signing when
 * the setting asks for it (devnets: off by default).
 */
public final class JoustReviewDialog extends DialogWrapper {

    private final JoustReview.Summary summary;

    public JoustReviewDialog(@Nullable Project project, JoustReview.Summary summary) {
        super(project, false);
        this.summary = summary;
        setTitle("JOUST: Review the Transaction");
        setOKButtonText("Sign and Submit");
        setCancelButtonText("Decline");
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JBTextArea text = new JBTextArea(PlainText.literal(String.join("\n", JoustReview.lines(summary)), 20_000));
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(false);
        text.setFont(JBUI.Fonts.create(Font.MONOSPACED, JBUI.Fonts.label().getSize()));
        JBScrollPane scroll = new JBScrollPane(text);
        scroll.setPreferredSize(JBUI.size(640, 320));
        return scroll;
    }
}
