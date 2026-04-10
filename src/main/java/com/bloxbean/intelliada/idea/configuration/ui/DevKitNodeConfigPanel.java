package com.bloxbean.intelliada.idea.configuration.ui;

import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.Network;
import com.bloxbean.intelliada.idea.core.util.NetworkUrls;
import com.bloxbean.intelliada.idea.core.util.Networks;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.nodeint.devkit.DevKitDownloader;
import com.bloxbean.intelliada.idea.nodeint.devkit.DevKitLifecycleService;
import com.bloxbean.intelliada.idea.nodeint.devkit.DevKitProcessManager;
import com.bloxbean.intelliada.idea.nodeint.devkit.DevKitStatusMonitor;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.service.api.NetworkInfoService;
import com.bloxbean.intelliada.idea.nodeint.service.impl.NetworkServiceImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

import static com.bloxbean.intelliada.idea.core.util.CLIProviderUtil.getDevKitScript;
import static com.bloxbean.intelliada.idea.core.util.CLIProviderUtil.getSuggestedCLIFolder;

//Config panel for YaciDevKit api
public class DevKitNodeConfigPanel implements NodeConfigurator {
    private JPanel mainPanel;
    private JTextField nameTf;
    private JButton testConnectionBtn;
    private JComboBox nodeTypesCB;
    private JTextField protocolMagicTf;
    private JTextField apiEndpointTf;
    private JLabel connectionTestLabel;
    private boolean newConfig = true;
    
    // Local DevKit management components
    private JPanel localDevKitPanel;
    private JTextField devKitHomeTf;
    private TextFieldWithBrowseButton devKitHomeTfWithBrowserBtn;
    private JButton installDevKitBtn;
    private JButton startDevKitBtn;
    private JButton stopDevKitBtn;
    private JLabel devKitStatusLabel;
    private JLabel devKitVersionLabel;
    private DevKitStatusMonitor statusMonitor;

    public DevKitNodeConfigPanel() {
        this(null);
    }

    public DevKitNodeConfigPanel(RemoteNode node) {
        super();

        initialize(node);
    }

    private void initialize(RemoteNode node) {
        handleNodeTypeSelection();
        initializeLocalDevKitComponents();

        testConnectionBtn.addActionListener(e -> {
            testNetworkConnection();
        });
    }

    @Override
    public void setNodeData(RemoteNode node) {
        if (node != null) {
            newConfig = false;
            nameTf.setText(node.getName());
            nodeTypesCB.setSelectedItem(node.getNodeType());
            apiEndpointTf.setText(node.getApiEndpoint());
            protocolMagicTf.setText(node.getProtocolMagic());
            
            // Set DevKit home if available
            if (!StringUtil.isEmpty(node.getHome())) {
                devKitHomeTf.setText(node.getHome());
            }
        }
        
        // Update local DevKit panel visibility
        updateLocalDevKitPanelVisibility();
    }

    private void handleNodeTypeSelection() {
        nodeTypesCB.addActionListener(e -> {
            apiEndpointTf.setText(NetworkUrls.YACI_DEVKIT_BASEURL);
            apiEndpointTf.setEnabled(true);
            updateLocalDevKitPanelVisibility();
        });
    }

    private void setNetwork(Network network) {
        if (network != null) {
            protocolMagicTf.setText(network.getProtocolMagic());
        }
    }

    public @Nullable ValidationInfo doValidate() {
        if (StringUtil.isEmpty(getName())) {
            return new ValidationInfo("Please enter a valid name", nameTf);
        }

        if (getNodeType() == null || getNodeType().equals(NodeType.EMPTY)) {
            return new ValidationInfo("Please select a valid node type", nodeTypesCB);
        }

        if (StringUtil.isEmpty(getApiEndpoint())) {
            return new ValidationInfo("Please enter a valid api endpoint url", apiEndpointTf);
        }

        return null;
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public String getName() {
        return StringUtil.trim(nameTf.getText());
    }

    public String getApiEndpoint() {
        return StringUtil.trim(apiEndpointTf.getText());
    }

    public String getAuthKey() {
        return "dummy key";
    }

    public NodeType getNodeType() {
        return NodeType.YaciDevKit;
    }

    public String getNetwork() {
        return "devkit_network";
    }

    public String getNetworkId() {
        return "devkit_network_id";
        //return networkIdTf.getText();
    }

    public String getProtocolMagic() {
        return protocolMagicTf.getText();
    }

    @Override
    public Map<String, String> getHeaders() {
        return null;
    }

    @Override
    public int getTimeout() {
        return 120; //Not used for Blockfrost
    }

    private void testNetworkConnection() {

        Task.Backgroundable task = new Task.Backgroundable(null, "Network Info") {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                RemoteNode remoteNode = new RemoteNode();
                remoteNode.setId(UUID.randomUUID().toString()); //Some random id
                remoteNode.setName(getName());
                remoteNode.setApiEndpoint(getApiEndpoint());
//                remoteNode.setAuthKey(getAuthKey());
                remoteNode.setNodeType(getNodeType());
                remoteNode.setProtocolMagic(getProtocolMagic());
               // remoteNode.setNetworkId(getNetworkId());

                LogListener logListener = new LogListener() {
                    @Override
                    public void info(String msg) {

                    }

                    @Override
                    public void error(String msg) {

                    }

                    @Override
                    public void warn(String msg) {

                    }
                };

                try {
                    //First remove
                    NetworkInfoService networkService = new NetworkServiceImpl(remoteNode, logListener);
                    Long currentSlot = networkService.getCurrentSlot();

                    if (currentSlot != null && currentSlot > 0) {
                        connectionTestLabel.setForeground(Color.black);
                        connectionTestLabel.setText("Successfully connected !!!");
                    } else {
                        connectionTestLabel.setForeground(Color.red);
//                        String response = result.getResponse();
//                        if (response != null && response.length() > 30)
//                            response = response.substring(0, 27) + "...";

                        connectionTestLabel.setText("Could not connect to node " );
//                        connectionTestLabel.setToolTipText(result.getResponse());
                    }
                } catch (Exception exception) {
                    connectionTestLabel.setText("Could not connect to node. Reason: " + exception.getMessage());
                }
            }
        };

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        nodeTypesCB = new ComboBox(new NodeType[]{NodeType.YaciDevKit});
        
        // Create local DevKit components (but don't add to mainPanel yet)
        createLocalDevKitComponentsOnly();
    }
    
    private void createLocalDevKitComponentsOnly() {
        devKitHomeTf = new JTextField();
        devKitHomeTfWithBrowserBtn = new TextFieldWithBrowseButton(devKitHomeTf, e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            fc.showDialog(mainPanel, "Select");
            File file = fc.getSelectedFile();
            if (file == null) {
                return;
            }

            String suggestedFolder = getSuggestedCLIFolder(file.getAbsolutePath());
            devKitHomeTf.setText(suggestedFolder);
            checkDevKitInstallation();
        });
        
        installDevKitBtn = new JButton("Install DevKit");
        startDevKitBtn = new JButton("Start DevKit");
        stopDevKitBtn = new JButton("Stop DevKit");
        devKitStatusLabel = new JLabel("Status: Not configured");
        devKitVersionLabel = new JLabel("Version: N/A");
    }
    
    private void initializeLocalDevKitComponents() {
        // Set up the local DevKit panel that was created by the form
        if (localDevKitPanel != null) {
            localDevKitPanel.setLayout(new BoxLayout(localDevKitPanel, BoxLayout.Y_AXIS));
            localDevKitPanel.setBorder(BorderFactory.createTitledBorder("Local DevKit Management"));
            
            // Add components to local DevKit panel
            JPanel pathPanel = new JPanel(new BorderLayout());
            pathPanel.add(new JLabel("DevKit Home:"), BorderLayout.WEST);
            pathPanel.add(devKitHomeTfWithBrowserBtn, BorderLayout.CENTER);
            localDevKitPanel.add(pathPanel);
            
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            buttonPanel.add(installDevKitBtn);
            buttonPanel.add(startDevKitBtn);
            buttonPanel.add(stopDevKitBtn);
            localDevKitPanel.add(buttonPanel);
            
            JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            statusPanel.add(devKitStatusLabel);
            statusPanel.add(devKitVersionLabel);
            localDevKitPanel.add(statusPanel);
            
            localDevKitPanel.setVisible(false); // Initially hidden
        }
        
        devKitHomeTf.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                checkDevKitInstallation();
            }
        });
        
        installDevKitBtn.addActionListener(e -> {
            String path = devKitHomeTf.getText();
            if (StringUtil.isEmpty(path)) {
                devKitStatusLabel.setText("Please select a home directory first");
                devKitStatusLabel.setForeground(Color.RED);
                return;
            }
            
            Path installDir = Paths.get(path + File.separator + "yaci-devkit");
            DevKitDownloader devKitDownloader = new DevKitDownloader(installDir);
            devKitDownloader.installSDK();
        });
        
        startDevKitBtn.addActionListener(e -> {
            Project project = getCurrentProject();
            if (project != null) {
                String devKitHome = devKitHomeTf.getText();
                if (StringUtil.isEmpty(devKitHome)) {
                    devKitStatusLabel.setText("Please configure DevKit home directory first");
                    devKitStatusLabel.setForeground(Color.RED);
                    return;
                }
                
                DevKitLifecycleService.getInstance().startDevKit(project, devKitHome)
                    .thenAccept(success -> {
                        SwingUtilities.invokeLater(() -> {
                            if (success) {
                                updateDevKitStatus();
                                updateButtonStates();
                            }
                        });
                    });
            }
        });
        
        stopDevKitBtn.addActionListener(e -> {
            Project project = getCurrentProject();
            if (project != null) {
                DevKitLifecycleService.getInstance().stopDevKit(project)
                    .thenAccept(success -> {
                        SwingUtilities.invokeLater(() -> {
                            if (success) {
                                updateDevKitStatus();
                                updateButtonStates();
                            }
                        });
                    });
            }
        });
        
        initializeStatusMonitoring();
    }
    
    private void updateLocalDevKitPanelVisibility() {
        boolean isLocalhost = isLocalhostUrl(apiEndpointTf.getText());
        localDevKitPanel.setVisible(isLocalhost);
        
        if (isLocalhost) {
            updateDevKitStatus();
            updateButtonStates();
        }
    }
    
    private boolean isLocalhostUrl(String url) {
        return !StringUtil.isEmpty(url) && 
               (url.contains("localhost") || url.contains("127.0.0.1"));
    }
    
    private void checkDevKitInstallation() {
        String homePath = devKitHomeTf.getText();
        if (StringUtil.isEmpty(homePath)) {
            devKitVersionLabel.setText("Version: N/A");
            return;
        }
        
        File devKitScript = new File(homePath + File.separator + "yaci-devkit" + File.separator + getDevKitScript());
        if (devKitScript.exists()) {
            try {
                String version = com.bloxbean.intelliada.idea.core.util.CLIProviderUtil.getVersionString(homePath + File.separator + "yaci-devkit");
                devKitVersionLabel.setText("Version: " + version);
                devKitVersionLabel.setForeground(Color.BLACK);
            } catch (Exception e) {
                devKitVersionLabel.setText("Version: Error reading version");
                devKitVersionLabel.setForeground(Color.RED);
            }
        } else {
            devKitVersionLabel.setText("Version: DevKit not found");
            devKitVersionLabel.setForeground(Color.RED);
        }
    }
    
    private void updateDevKitStatus() {
        Project project = getCurrentProject();
        if (project != null) {
            DevKitLifecycleService service = DevKitLifecycleService.getInstance();
            DevKitProcessManager.DevKitStatus status = service.getDevKitStatus(project);
            
            String statusText = getStatusDisplayText(status);
            devKitStatusLabel.setText("Status: " + statusText);
            devKitStatusLabel.setForeground(getStatusColor(status));
        }
    }
    
    private void updateButtonStates() {
        Project project = getCurrentProject();
        if (project != null) {
            DevKitLifecycleService service = DevKitLifecycleService.getInstance();
            DevKitProcessManager.DevKitStatus status = service.getDevKitStatus(project);
            
            startDevKitBtn.setEnabled(status == DevKitProcessManager.DevKitStatus.STOPPED);
            stopDevKitBtn.setEnabled(status == DevKitProcessManager.DevKitStatus.RUNNING);
        }
    }
    
    private void initializeStatusMonitoring() {
        Project project = getCurrentProject();
        if (project != null) {
            DevKitLifecycleService service = DevKitLifecycleService.getInstance();
            statusMonitor = service.getStatusMonitor(project);
            
            if (statusMonitor != null) {
                statusMonitor.addStatusChangeListener(new DevKitStatusMonitor.StatusChangeListener() {
                    @Override
                    public void onStatusChanged(DevKitProcessManager.DevKitStatus oldStatus, DevKitProcessManager.DevKitStatus newStatus) {
                        SwingUtilities.invokeLater(() -> {
                            updateDevKitStatus();
                            updateButtonStates();
                        });
                    }
                    
                    @Override
                    public void onHealthChanged(boolean healthy) {
                        SwingUtilities.invokeLater(() -> updateDevKitStatus());
                    }
                });
            }
        }
    }
    
    private String getStatusDisplayText(DevKitProcessManager.DevKitStatus status) {
        switch (status) {
            case STOPPED:
                return "Stopped";
            case STARTING:
                return "Starting...";
            case RUNNING:
                return "Running";
            case STOPPING:
                return "Stopping...";
            case ERROR:
                return "Error";
            default:
                return "Unknown";
        }
    }
    
    private Color getStatusColor(DevKitProcessManager.DevKitStatus status) {
        switch (status) {
            case RUNNING:
                return Color.GREEN.darker();
            case STARTING:
            case STOPPING:
                return Color.ORANGE.darker();
            case ERROR:
                return Color.RED;
            case STOPPED:
            default:
                return Color.GRAY;
        }
    }
    
    private Project getCurrentProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        return projects.length > 0 ? projects[0] : null;
    }
    
    public String getDevKitHome() {
        return devKitHomeTf.getText();
    }
    
    @Override
    public String getHome() {
        return getDevKitHome();
    }
}
