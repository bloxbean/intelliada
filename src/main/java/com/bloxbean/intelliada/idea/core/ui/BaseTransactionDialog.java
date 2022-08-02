package com.bloxbean.intelliada.idea.core.ui;

import com.bloxbean.intelliada.idea.core.RequestMode;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

public abstract class BaseTransactionDialog extends DialogWrapper {
    private Action exportAction;
    protected CardanoConsole console;
    protected Project project;
    protected RequestMode requestMode;

    protected BaseTransactionDialog(@Nullable Project project, boolean canbeParent) {
        super(project, canbeParent);
        this.console = CardanoConsole.getConsole(project);
        this.project = project;
        this.exportAction = new ExportSignedTransaction();
        setOKButtonText("Send");
    }

    @Override
    protected Action @NotNull [] createLeftSideActions() {
        return new Action[]{
                exportAction
        };
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        ValidationInfo validationInfo = doInputValidation();
        if (validationInfo == null) {
            exportAction.setEnabled(true);
            return null;
        } else {
            exportAction.setEnabled(false);

            return validationInfo;
        }
    }

    public RequestMode getRequestMode() {
        return requestMode;
    }

    protected abstract ValidationInfo doInputValidation();

    class ExportSignedTransaction extends DialogWrapperAction {

        public ExportSignedTransaction() {
            super("Export Signed Tx");
        }

        @Override
        protected void doAction(ActionEvent e) {
            List<ValidationInfo> infoList = doValidateAll();
            if (!infoList.isEmpty()) {
                ValidationInfo info = infoList.get(0);

                startTrackingValidation();
                if (infoList.stream().anyMatch(info1 -> !info1.okEnabled)) return;
            }
            requestMode = RequestMode.EXPORT_SIGNED;
            doOKAction();
        }
    }
}
