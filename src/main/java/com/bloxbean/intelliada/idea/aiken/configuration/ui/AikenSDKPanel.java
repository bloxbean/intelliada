package com.bloxbean.intelliada.idea.aiken.configuration.ui;

import com.bloxbean.intelliada.idea.aiken.configuration.AikenSDK;
import com.bloxbean.intelliada.idea.aiken.util.AikenSdkUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;

public class AikenSDKPanel {
    private final static Logger LOG = Logger.getInstance(AikenSDKPanel.class);

    private JTextField versionTf;
    private JPanel mainPanel;
    private JTextField nameTf;
    private TextFieldWithBrowseButton pathTfWithBrowserBtn;
    private JLabel errorMsgLabel;
    private JTextField pathTf;

    public AikenSDKPanel() {
        this(null);
    }

    public AikenSDKPanel(AikenSDK sdk) {
        super();

        if(sdk != null) {
            nameTf.setText(sdk.getName());
            pathTf.setText(sdk.getPath());
            versionTf.setText(sdk.getVersion());
        }

        pathTf.setToolTipText("<html>Folder where the \'aiken\' binary is available.</html>");

        pathTf.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                checkGoalExecutable();
            }
        });
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public String getPath() {
        return pathTf.getText();
    }

    public String getName() {
        return nameTf.getText();
    }

    public String getVersion() {
        return versionTf.getText();
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        pathTf = new JTextField();
        pathTfWithBrowserBtn = new TextFieldWithBrowseButton(pathTf, e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.showDialog(mainPanel, "Select");
            File file = fc.getSelectedFile();
            if (file == null) {
                return;
            }

            pathTf.setText(file.getAbsolutePath());

            checkGoalExecutable();
        });
    }

    private void checkGoalExecutable() {
        errorMsgLabel.setText(""); //reset error msg
        versionTf.setText("");

        if(!new File(pathTf.getText() + File.separator + getAikenCommand()).exists()) {
            versionTf.setText("");
            printError("<html>\'aiken\' was not found. Please make sure \'aiken\' file is available under the selected folder.</html>");
            return;
        }

        String version = null;
        try {
            version = AikenSdkUtil.getVersionString(pathTf.getText());
        } catch (Exception exception) {
            versionTf.setText("");
            printError(exception.getMessage());
            return;
        }
        if(StringUtil.isEmpty(version)) {
            versionTf.setText("");
            //Invalid version
            printError("<html>Invalid Aiken Binary folder. Version could not be determined. " +
                    "<br/> Make sure \'aiken\' is available under the selected folder</html>");
            return;
        } else {
            versionTf.setText(version);
        }
    }

    private void printError(String msg) {
        errorMsgLabel.setText(msg);
        errorMsgLabel.setForeground(Color.red);
    }

    private String getAikenCommand() {
        String goalCmd = "aiken";
        if(SystemInfo.isWindows)
            goalCmd = "aiken.exe";

        return goalCmd;
    }

    public JTextField getVersionTf() {
        return versionTf;
    }

    public JTextField getNameTf() {
        return nameTf;
    }

    public JTextField getPathTf() {
        return pathTf;
    }

}
