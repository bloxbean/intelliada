package com.bloxbean.intelliada.idea.configuration.ui;

import com.bloxbean.intelliada.idea.configuration.model.CLIProvider;
import com.bloxbean.intelliada.idea.core.util.CLIProviderUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;

import static com.bloxbean.intelliada.idea.core.util.CLIProviderUtil.getCardanoCLICommand;
import static com.bloxbean.intelliada.idea.core.util.CLIProviderUtil.getSuggestedCLIFolder;

public class CLIProviderPanel {
    private final static Logger LOG = Logger.getInstance(CLIProviderPanel.class);

    private JTextField versionTf;
    private JPanel mainPanel;
    private JTextField nameTf;
    private TextFieldWithBrowseButton homeTfWithBrowserBtn;
    private JLabel errorMsgLabel;
    private JTextField homeTf;

    public CLIProviderPanel() {
        this(null);
    }

    public CLIProviderPanel(CLIProvider cliProvider) {
        super();

        if(cliProvider != null) {
            nameTf.setText(cliProvider.getName());
            homeTf.setText(cliProvider.getHome());
            versionTf.setText(cliProvider.getVersion());
        }

        homeTf.setToolTipText("<html>Folder where the \'cardano-cli\' binary is available. \n</html>");

        homeTf.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                checkCardanoCLIExecutable();
            }
        });
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public String getHome() {
        return homeTf.getText();
    }

    public String getName() {
        return nameTf.getText();
    }

    public String getVersion() {
        return versionTf.getText();
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        homeTf = new JTextField();
        homeTfWithBrowserBtn = new TextFieldWithBrowseButton(homeTf, e -> {

            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            fc.showDialog(mainPanel, "Select");
            File file = fc.getSelectedFile();
            if (file == null) {
                return;
            }

            String suggestedFolder = getSuggestedCLIFolder(file.getAbsolutePath());

            homeTf.setText(suggestedFolder);

            checkCardanoCLIExecutable();
        });
    }

    private void checkCardanoCLIExecutable() {
        errorMsgLabel.setText(""); //reset error msg
        versionTf.setText("");

        if(!new File(homeTf.getText() + File.separator + getCardanoCLICommand()).exists()) {
            versionTf.setText("");
            printError("<html>\'cardano-cli\' was not found. Please make sure \'cardano-cli\' file is available under the selected folder.</html>");
            return;
        }

        String version = null;
        try {
            version = CLIProviderUtil.getVersionString(homeTf.getText());
        } catch (Exception exception) {
            versionTf.setText("");
            printError(exception.getMessage());
            return;
        }
        if(StringUtil.isEmpty(version)) {
            versionTf.setText("");
            //Invalid version
            printError("<html>Invalid Cardano bin folder. Version could not be determined. " +
                    "<br/> Make sure \'cardano-cli\' is available under the selected folder</html>");
            return;
        } else {
            versionTf.setText(version);
        }
    }

    private void printError(String msg) {
        errorMsgLabel.setText(msg);
        errorMsgLabel.setForeground(Color.red);
    }

    public JTextField getVersionTf() {
        return versionTf;
    }

    public JTextField getNameTf() {
        return nameTf;
    }

    public JTextField getHomeTf() {
        return homeTf;
    }

}
