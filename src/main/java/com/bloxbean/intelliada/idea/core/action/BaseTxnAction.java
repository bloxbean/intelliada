package com.bloxbean.intelliada.idea.core.action;

import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.transaction.model.SerializedTransaction;
import com.bloxbean.intelliada.idea.transaction.util.ExportUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.io.File;

public abstract class BaseTxnAction extends AnAction {
    public BaseTxnAction(String text, String description, Icon icon) {
        super(text, description, icon);
    }

    protected String getExportFileLocation(Project project, JComponent parent) {
        String baseDir = null;
        if(project != null)
            baseDir = project.getBasePath();

        JFileChooser fc = new JFileChooser();
        if(baseDir != null)
            fc.setCurrentDirectory(new File(baseDir));

        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.showSaveDialog(parent);
        File destination = fc.getSelectedFile();

        if(destination.exists()) {
            int ret = 0;
            try {
                ret = Messages.showYesNoCancelDialog(String.format("Already a file exists with file name %s. Do you want to overwrite?",
                        destination), "Export", "Overwrite", "Create New", "Cancel", AllIcons.General.QuestionDialog);
            } catch (Error e) {
                //TODO BigSur 2020.3.3 error
                ret = Messages.NO;
            }

            String fileName = destination.getName();
            String outputFile = fileName;
            if(ret == Messages.NO) {
                int i = 0;
                File parentFolder = destination.getParentFile();
                while(new File(parentFolder, outputFile).exists())
                    outputFile = fileName + "-" + ++i;
            } else if(ret == Messages.CANCEL) {
//                logListener.warn(command + " was cancelled");
                return null;
            }

            return new File(destination.getParentFile(), outputFile).getAbsolutePath();
        } else {
            return destination.getAbsolutePath();
        }
    }

    protected void exportTransaction(SerializedTransaction transaction, String exportFile, LogListener logListener) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                try {
                    ExportUtil.exportSignedTransaction(transaction, exportFile, logListener);
                } catch (Exception exception) {
                    logListener.error("Transaction export failed", exception);
                }
            }
        });
    }
}
