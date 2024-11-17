package com.bloxbean.intelliada.idea.aiken.module.ui;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class AikenModulePanel {
    private JTextField ownerTf;
    private JPanel mainPanel;
    private JLabel errorMessageLabel;
    private SettingChangeListener settingChangeListener;

    public AikenModulePanel() {

    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public String getOwner() {
        return ownerTf.getText();
    }

    public @Nullable ValidationInfo doValidate() {
        String owner = getOwner();
        if(StringUtil.isEmpty(owner)) {
            return new ValidationInfo("Owner cannot be empty", ownerTf);
        }

        return null;
    }

    public void addSettingsListener(SettingChangeListener settingChangeListener) {
        this.settingChangeListener = settingChangeListener;
    }

    public interface SettingChangeListener {
        void settingsChanged();
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        String owner = System.getProperty("user.name").toLowerCase();
        ownerTf = new JBTextField(owner);

        ownerTf.setInputVerifier(new NotEmptyTextVerifier());

        errorMessageLabel = new JLabel();
        errorMessageLabel.setText(" ");
    }

    public class NotEmptyTextVerifier extends InputVerifier {
        @Override
        public boolean verify(JComponent input) {
            String text = ((JTextField) input).getText();
            if(!StringUtil.isEmpty(text)) {
                errorMessageLabel.setText("");
                if(settingChangeListener != null)
                    settingChangeListener.settingsChanged();
                return true;
            } else {
                errorMessageLabel.setText("Please provide owner name");
                errorMessageLabel.setForeground(Color.RED);
                return false;
            }
        }
    }
}
