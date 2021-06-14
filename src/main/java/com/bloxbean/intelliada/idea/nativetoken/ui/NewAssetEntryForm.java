package com.bloxbean.intelliada.idea.nativetoken.ui;

import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.intelliada.idea.scripts.service.ScriptChooser;
import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.io.File;

public class NewAssetEntryForm {
    private JPanel panel1;
    private JTextField policyScriptTf;
    private JButton internalScriptBrowseBtn;
    private JButton externalScriptBrowseBtn;
    private JTextField policyIdTf;
    private JTextField assetNameTf;
    private JTextField quantityTf;
    private JList policyKeysList;
    private JButton addPolicyKeyBtn;
    private JButton removePolicyKeyBtn;
    private JPanel mainPanel;
    private CardanoConsole console;
    private Project project;

    private NativeScript policyScript;

    public NewAssetEntryForm() {

    }

    public void initialize(Project project, CardanoConsole console) {
        this.project = project;
        this.console = console;

        internalScriptBrowseBtn.addActionListener(e -> {
            ScriptInfo scriptInfo = ScriptChooser.selectScript(project);
            if(scriptInfo == null)
                return;
            setPolicyScript(scriptInfo.getScript());
        });

        externalScriptBrowseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            String baseDir = project.getBasePath();
            if(baseDir != null)
                fc.setCurrentDirectory(new File(baseDir));

            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fc.showDialog(mainPanel, "Select");
            File destination = fc.getSelectedFile();
            if (destination == null || !destination.exists()) {
                Messages.showErrorDialog("Invalid script file: " + destination, "External script");
                return;
            }


        });

    }

    private void setPolicyScript(NativeScript script) {
        this.policyScript = script;
    }
}
