package com.bloxbean.intelliada.idea.julc.configuration.ui;

import com.bloxbean.intelliada.idea.julc.configuration.JulcDownloader;
import com.bloxbean.intelliada.idea.julc.configuration.JulcSDK;
import com.bloxbean.intelliada.idea.julc.util.JulcSdkUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.nio.file.Path;

public class JulcSDKPanel {
    private static final Logger LOG = Logger.getInstance(JulcSDKPanel.class);

    private JTextField versionTf;
    private JPanel mainPanel;
    private JTextField nameTf;
    private TextFieldWithBrowseButton pathTfWithBrowserBtn;
    private JLabel errorMsgLabel;
    private JTextField pathTf;
    private JButton downloadBtn;

    public JulcSDKPanel() {
        this(null);
    }

    public JulcSDKPanel(JulcSDK sdk) {
        super();
        initComponents();

        if (sdk != null) {
            nameTf.setText(sdk.getName());
            pathTf.setText(sdk.getPath());
            versionTf.setText(sdk.getVersion());
        }

        pathTf.setToolTipText("<html>Folder where the 'julc' binary is available (e.g., the 'bin' directory).</html>");

        pathTf.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                checkExecutable();
            }
        });
    }

    private void initComponents() {
        mainPanel = new JPanel(new BorderLayout());
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("julc SDK"));

        GridBagConstraints labelC = new GridBagConstraints();
        labelC.anchor = GridBagConstraints.WEST;
        labelC.insets = new Insets(4, 4, 4, 4);

        GridBagConstraints fieldC = new GridBagConstraints();
        fieldC.fill = GridBagConstraints.HORIZONTAL;
        fieldC.weightx = 1.0;
        fieldC.insets = new Insets(4, 4, 4, 4);

        // Name
        nameTf = new JTextField("julc");
        labelC.gridy = 0; labelC.gridx = 0;
        formPanel.add(new JLabel("Name"), labelC);
        fieldC.gridy = 0; fieldC.gridx = 1; fieldC.gridwidth = 2;
        formPanel.add(nameTf, fieldC);

        // Path with browser button
        pathTf = new JTextField();
        pathTfWithBrowserBtn = new TextFieldWithBrowseButton(pathTf, e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.showDialog(mainPanel, "Select");
            File file = fc.getSelectedFile();
            if (file == null) return;
            pathTf.setText(file.getAbsolutePath());
            checkExecutable();
        });
        labelC.gridy = 1; fieldC.gridy = 1; fieldC.gridwidth = 1;
        formPanel.add(new JLabel("julc Exec Path"), labelC);
        formPanel.add(pathTfWithBrowserBtn, fieldC);

        // Download button
        downloadBtn = new JButton("Download julc");
        downloadBtn.addActionListener(e -> downloadJulc());
        GridBagConstraints btnC = new GridBagConstraints();
        btnC.gridx = 2; btnC.gridy = 1;
        btnC.insets = new Insets(4, 4, 4, 4);
        formPanel.add(downloadBtn, btnC);

        // Version
        versionTf = new JTextField();
        versionTf.setEditable(false);
        labelC.gridy = 2; fieldC.gridy = 2; fieldC.gridwidth = 2;
        formPanel.add(new JLabel("julc Version"), labelC);
        formPanel.add(versionTf, fieldC);

        // Error label
        errorMsgLabel = new JLabel("");
        fieldC.gridy = 3;
        formPanel.add(errorMsgLabel, fieldC);

        mainPanel.add(formPanel, BorderLayout.NORTH);
        mainPanel.setPreferredSize(new Dimension(750, 300));
        mainPanel.setMinimumSize(new Dimension(750, 300));
    }

    private void downloadJulc() {
        // Choose install directory
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select julc installation directory");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String defaultDir = System.getProperty("user.home") + File.separator + ".julc";
        fc.setSelectedFile(new File(defaultDir));

        if (fc.showSaveDialog(mainPanel) != JFileChooser.APPROVE_OPTION) return;

        Path installDir = fc.getSelectedFile().toPath();
        JulcDownloader downloader = new JulcDownloader(installDir);

        // After download, auto-detect the binary path
        downloader.setOnComplete(() -> {
            String binDir = JulcDownloader.findJulcBinDir(installDir);
            if (binDir != null) {
                SwingUtilities.invokeLater(() -> {
                    pathTf.setText(binDir);
                    checkExecutable();
                });
            }
        });

        downloadBtn.setEnabled(false);
        downloadBtn.setText("Downloading...");
        downloader.install();

        // Re-enable after a delay (download runs in background)
        new Timer(2000, e -> {
            downloadBtn.setEnabled(true);
            downloadBtn.setText("Download julc");
            ((Timer)e.getSource()).stop();
        }).start();
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

    public JTextField getVersionTf() {
        return versionTf;
    }

    public JTextField getNameTf() {
        return nameTf;
    }

    public JTextField getPathTf() {
        return pathTf;
    }

    private void checkExecutable() {
        errorMsgLabel.setText("");
        versionTf.setText("");

        if (!new File(pathTf.getText() + File.separator + JulcSdkUtil.getJulcExecutable()).exists()) {
            versionTf.setText("");
            printError("<html>'julc' was not found. Please make sure 'julc' is available under the selected folder.</html>");
            return;
        }

        String version;
        try {
            version = JulcSdkUtil.getVersionString(pathTf.getText());
        } catch (Exception exception) {
            versionTf.setText("");
            printError(exception.getMessage());
            return;
        }

        if (StringUtil.isEmpty(version)) {
            versionTf.setText("");
            printError("<html>Invalid julc binary folder. Version could not be determined.</html>");
        } else {
            versionTf.setText(version);
            errorMsgLabel.setText("");
        }
    }

    private void printError(String msg) {
        errorMsgLabel.setText(msg);
        errorMsgLabel.setForeground(Color.red);
    }
}
