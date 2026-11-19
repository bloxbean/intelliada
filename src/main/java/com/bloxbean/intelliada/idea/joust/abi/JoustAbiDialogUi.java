package com.bloxbean.intelliada.idea.joust.abi;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.lsp.JoustNotifications;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ADR-004 D8's dialogs: the review (both manifests side by side, each with its SHA-256; Accept enabled only when
 * {@link JoustAbi#verify} found nothing wrong) and the confirmation naming the contract, its module and its change.
 * Project text is shown as plain text.
 */
public final class JoustAbiDialogUi implements JoustAbiReview.Ui {

    private final Project project;

    public JoustAbiDialogUi(Project project) {
        this.project = project;
    }

    @Override
    public boolean review(String module, JoustAbi.Diff diff, String refusal) {
        AtomicBoolean ok = new AtomicBoolean();
        ApplicationManager.getApplication().invokeAndWait(() -> ok.set(new ReviewDialog(module, diff, refusal).showAndGet()));
        return ok.get();
    }

    @Override
    public boolean confirm(String module, JoustAbi.Diff diff) {
        AtomicBoolean ok = new AtomicBoolean();
        ApplicationManager.getApplication().invokeAndWait(() -> ok.set(Messages.showYesNoDialog(project,
                PlainText.html("Accept the ABI change of " + diff.contract() + " in " + module + "?\n\n" + diff.committedSha() + "\n-> "
                        + diff.nowSha() + "\n\nDeployed instances of the old encoding will not read the new one."),
                "Accept ABI Change", "Accept", "Cancel", Messages.getWarningIcon()) == Messages.YES));
        return ok.get();
    }

    @Override
    public void report(String title, String text, boolean error) {
        JoustNotifications.show(project, title, text, error ? NotificationType.ERROR : NotificationType.INFORMATION);
    }

    @Override
    public void refused(String title, String text, Runnable reviewAgain) {
        var n = JoustNotifications.create(title, text, NotificationType.ERROR);
        n.addAction(com.intellij.notification.NotificationAction.createSimpleExpiring("Review ABI change again…", reviewAgain));
        n.notify(project);
    }

    final class ReviewDialog extends DialogWrapper {
        private final String module;
        private final JoustAbi.Diff diff;
        private final String refusal;

        ReviewDialog(String module, JoustAbi.Diff diff, String refusal) {
            super(project, true);
            this.module = module;
            this.diff = diff;
            this.refusal = refusal;
            setTitle("Review ABI Change: " + PlainText.title(diff.contract(), 120));
            setOKButtonText("Accept This Change…");
            setOKActionEnabled(refusal == null);
            init();
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            var factory = DiffContentFactory.getInstance();
            var request = new SimpleDiffRequest(PlainText.label(diff.contract(), 120),
                    factory.create(project, diff.committedText() == null ? "" : diff.committedText(), PlainTextFileType.INSTANCE),
                    factory.create(project, diff.nowText(), PlainTextFileType.INSTANCE),
                    "Committed: " + diff.committedSha(), "Now: " + diff.nowSha());
            var panel = DiffManager.getInstance().createRequestPanel(project, getDisposable(), null);
            panel.setRequest(request);
            JPanel root = new JPanel(new BorderLayout(0, 8));
            root.add(new JBLabel(PlainText.html(module + ": " + (refusal == null ? "the manifests and their hashes agree." : "Accept is disabled: " + refusal))),
                    BorderLayout.NORTH);
            root.add(panel.getComponent(), BorderLayout.CENTER);
            root.setPreferredSize(new Dimension(900, 600));
            return root;
        }
    }
}
