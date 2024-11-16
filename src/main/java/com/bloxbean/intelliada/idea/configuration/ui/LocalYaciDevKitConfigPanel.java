package com.bloxbean.intelliada.idea.configuration.ui;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.CLIProviderUtil;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.nodeint.devkit.DevKitDownloader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static com.bloxbean.intelliada.idea.core.util.CLIProviderUtil.getDevKitScript;
import static com.bloxbean.intelliada.idea.core.util.CLIProviderUtil.getSuggestedCLIFolder;

public class LocalYaciDevKitConfigPanel implements NodeConfigurator {
    private final static Logger LOG = Logger.getInstance(LocalYaciDevKitConfigPanel.class);

    private JTextField versionTf;
    private JPanel mainPanel;
    private JTextField nameTf;
    private TextFieldWithBrowseButton homeTfWithBrowserBtn;
    private JLabel errorMsgLabel;
    private JButton installDevKitBtn;
    private JTextField restEndpointTf;
    private JTextField protocolMagicTf;
    private JTextField homeTf;

    public LocalYaciDevKitConfigPanel() {
        this(null);
    }

    public LocalYaciDevKitConfigPanel(RemoteNode node) {
        super();

        if (node != null) {
            nameTf.setText(node.getName());
            homeTf.setText(node.getHome());
            versionTf.setText(node.getVersion());
        }

        homeTf.setToolTipText("<html>Folder where the \'Yaci DevKit\' is available. \n</html>");

        homeTf.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                checkCardanoCLIExecutable();
            }
        });

        initializeListeners();
    }

    private void initializeListeners() {
        installDevKitBtn.addActionListener(e -> {
            //Install Yaci DevKit
            //TODO
            //Download the latest Yaci Devkit zip and extract it to the selected folder
            //fomr https://github.com/bloxbean/yaci-devkit/releases

            String path = homeTf.getText();

            Path installDir = Paths.get(path + File.separator + "yaci-devkit");
            DevKitDownloader devKitDownloader = new DevKitDownloader(installDir);
            devKitDownloader.installSDK();
        });
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public String getHome() {
        return homeTf.getText();
    }

    @Override
    public void setNodeData(RemoteNode node) {

    }

    public String getName() {
        return nameTf.getText();
    }

    @Override
    public String getApiEndpoint() {
        return "";
    }

    @Override
    public String getAuthKey() {
        return "";
    }

    @Override
    public NodeType getNodeType() {
        return null;
    }

    @Override
    public String getNetwork() {
        return "";
    }

    @Override
    public String getNetworkId() {
        return "";
    }

    @Override
    public String getProtocolMagic() {
        return "";
    }

    @Override
    public Map<String, String> getHeaders() {
        return Map.of();
    }

    @Override
    public int getTimeout() {
        return 0;
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

        if (!new File(homeTf.getText() + File.separator + getDevKitScript()).exists()) {
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
        if (StringUtil.isEmpty(version)) {
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
