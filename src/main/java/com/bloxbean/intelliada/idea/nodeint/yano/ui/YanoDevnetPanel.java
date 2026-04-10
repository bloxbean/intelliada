package com.bloxbean.intelliada.idea.nodeint.yano.ui;

import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.account.service.AccountChooser;
import com.bloxbean.intelliada.idea.nodeint.yano.*;
import com.bloxbean.intelliada.idea.nodeint.yano.model.*;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Yano Devnet control panel with tabs for:
 * - Setup & Control (download, start, stop with inline status)
 * - Fund Account (with account chooser integration)
 * - Snapshots
 * - Rollback
 * - Time Machine (advance + epoch shifting)
 *
 * Each tab has an inline status label for immediate feedback.
 */
public class YanoDevnetPanel {
    private static final Logger LOG = Logger.getInstance(YanoDevnetPanel.class);
    private static final int DEFAULT_HTTP_PORT = 8888;

    private final Project project;
    private JPanel mainPanel;

    // Setup tab
    private JComboBox<YanoDownloader.ReleaseInfo> versionCombo;
    private JLabel installStatusLabel;
    private JSpinner portSpinner;
    private JButton startBtn;
    private JButton stopBtn;
    private JLabel statusLabel;
    private JLabel slotLabel;
    private JLabel blockLabel;

    // Fund tab
    private JTextField fundAddressTf;
    private JSpinner fundAmountSpinner;
    private JLabel fundStatusLabel;

    // Snapshot tab
    private DefaultTableModel snapshotTableModel;
    private JTable snapshotTable;
    private JTextField snapshotNameTf;
    private JLabel snapshotStatusLabel;

    // Rollback tab
    private JSpinner rollbackCountSpinner;
    private JLabel rollbackStatusLabel;

    // Time Machine tab
    private JSpinner advanceSlotsSpinner;
    private JSpinner advanceEpochsSpinner;
    private JSpinner shiftEpochsSpinner;
    private JLabel timeStatusLabel;

    public YanoDevnetPanel(Project project) {
        this.project = project;
        initComponents();
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void initComponents() {
        mainPanel = new JPanel(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Setup", createSetupTab());
        tabbedPane.addTab("Fund", createFundTab());
        tabbedPane.addTab("Snapshots", createSnapshotTab());
        tabbedPane.addTab("Rollback", createRollbackTab());
        tabbedPane.addTab("Time Machine", createTimeMachineTab());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
    }

    // ========== Setup Tab ==========

    private JPanel createSetupTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints c = gbc();

        // Download Section
        JLabel downloadTitle = new JLabel("Download & Install");
        downloadTitle.setFont(downloadTitle.getFont().deriveFont(Font.BOLD, 13f));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 3;
        panel.add(downloadTitle, c);

        c.gridy = 1; c.gridwidth = 1;
        panel.add(new JLabel("Version:"), c);
        versionCombo = new JComboBox<>();
        versionCombo.setPreferredSize(new Dimension(300, 27));
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        panel.add(versionCombo, c);
        JButton fetchBtn = new JButton("Fetch Versions");
        fetchBtn.addActionListener(e -> fetchVersions());
        c.gridx = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        panel.add(fetchBtn, c);

        JButton downloadBtn = new JButton("Download & Install");
        downloadBtn.addActionListener(e -> downloadSelected());
        c.gridx = 1; c.gridy = 2;
        panel.add(downloadBtn, c);

        installStatusLabel = new JLabel("");
        c.gridx = 0; c.gridy = 3; c.gridwidth = 3;
        panel.add(installStatusLabel, c);

        // Separator
        c.gridy = 4; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JSeparator(), c);

        // Run Section
        JLabel runTitle = new JLabel("Run Yano Devnet");
        runTitle.setFont(runTitle.getFont().deriveFont(Font.BOLD, 13f));
        c.gridy = 5; c.fill = GridBagConstraints.NONE;
        panel.add(runTitle, c);

        c.gridy = 6; c.gridwidth = 1;
        panel.add(new JLabel("HTTP Port:"), c);
        portSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_HTTP_PORT, 1024, 65535, 1));
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(portSpinner, c);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        startBtn = new JButton("Start Yano");
        startBtn.addActionListener(e -> doStartYano());
        btnPanel.add(startBtn);
        stopBtn = new JButton("Stop Yano");
        stopBtn.setEnabled(false);
        stopBtn.addActionListener(e -> doStopYano());
        btnPanel.add(stopBtn);
        c.gridx = 0; c.gridy = 7; c.gridwidth = 3;
        panel.add(btnPanel, c);

        // Status
        c.gridy = 8; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JSeparator(), c);

        JPanel statusPanel = new JPanel(new GridBagLayout());
        GridBagConstraints sc = gbc();
        statusPanel.add(new JLabel("Status:"), sc);
        statusLabel = new JLabel("Stopped");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 14f));
        statusLabel.setForeground(Color.GRAY);
        sc.gridx = 1; sc.gridwidth = 2;
        statusPanel.add(statusLabel, sc);

        sc.gridy = 1; sc.gridx = 0; sc.gridwidth = 1;
        statusPanel.add(new JLabel("Slot:"), sc);
        slotLabel = new JLabel("-");
        slotLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        sc.gridx = 1;
        statusPanel.add(slotLabel, sc);

        sc.gridy = 2; sc.gridx = 0;
        statusPanel.add(new JLabel("Block:"), sc);
        blockLabel = new JLabel("-");
        blockLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        sc.gridx = 1;
        statusPanel.add(blockLabel, sc);

        c.gridy = 9; c.gridwidth = 3;
        panel.add(statusPanel, c);

        checkInstalledVersions();
        return wrapNorth(panel);
    }

    // ========== Fund Tab ==========

    private JPanel createFundTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Fund Account"));
        GridBagConstraints c = gbc();

        c.gridx = 0; c.gridy = 0;
        panel.add(new JLabel("Address:"), c);
        fundAddressTf = new JTextField(35);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        panel.add(fundAddressTf, c);

        // Account chooser button - integrates with Cardano Account management
        JButton chooseAccountBtn = new JButton("Choose Account");
        chooseAccountBtn.addActionListener(e -> {
            CardanoAccount account = AccountChooser.getSelectedAccount(project, false);
            if (account != null) {
                fundAddressTf.setText(account.getAddress());
            }
        });
        c.gridx = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        panel.add(chooseAccountBtn, c);

        c.gridy = 1; c.gridx = 0;
        panel.add(new JLabel("ADA Amount:"), c);
        fundAmountSpinner = new JSpinner(new SpinnerNumberModel(1000, 1, 1000000, 100));
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(fundAmountSpinner, c);

        JButton fundBtn = new JButton("Fund");
        fundBtn.addActionListener(e -> doFund());
        c.gridx = 1; c.gridy = 2; c.fill = GridBagConstraints.NONE;
        panel.add(fundBtn, c);

        // Inline status
        fundStatusLabel = createStatusLabel();
        c.gridx = 0; c.gridy = 3; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(fundStatusLabel, c);

        return wrapNorth(panel);
    }

    // ========== Snapshot Tab ==========

    private JPanel createSnapshotTab() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Snapshots"));

        snapshotTableModel = new DefaultTableModel(new String[]{"Name", "Slot", "Block", "Created"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        snapshotTable = new JTable(snapshotTableModel);
        panel.add(new JScrollPane(snapshotTable), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        snapshotNameTf = new JTextField(15);
        buttonPanel.add(new JLabel("Name:"));
        buttonPanel.add(snapshotNameTf);

        JButton createBtn = new JButton("Create");
        createBtn.addActionListener(e -> doCreateSnapshot());
        buttonPanel.add(createBtn);
        JButton restoreBtn = new JButton("Restore");
        restoreBtn.addActionListener(e -> doRestoreSnapshot());
        buttonPanel.add(restoreBtn);
        JButton deleteBtn = new JButton("Delete");
        deleteBtn.addActionListener(e -> doDeleteSnapshot());
        buttonPanel.add(deleteBtn);
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> doRefreshSnapshots());
        buttonPanel.add(refreshBtn);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(buttonPanel, BorderLayout.NORTH);
        snapshotStatusLabel = createStatusLabel();
        southPanel.add(snapshotStatusLabel, BorderLayout.SOUTH);
        panel.add(southPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ========== Rollback Tab ==========

    private JPanel createRollbackTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Rollback"));
        GridBagConstraints c = gbc();

        panel.add(new JLabel("Undo Last N Blocks:"), c);
        rollbackCountSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
        c.gridx = 1;
        panel.add(rollbackCountSpinner, c);

        JButton rollbackBtn = new JButton("Rollback");
        rollbackBtn.addActionListener(e -> doRollback());
        c.gridy = 1; c.gridx = 1;
        panel.add(rollbackBtn, c);

        rollbackStatusLabel = createStatusLabel();
        c.gridx = 0; c.gridy = 2; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(rollbackStatusLabel, c);

        return wrapNorth(panel);
    }

    // ========== Time Machine Tab ==========

    private JPanel createTimeMachineTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Time Machine"));
        GridBagConstraints c = gbc();

        panel.add(new JLabel("Advance by Slots:"), c);
        advanceSlotsSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 100000, 10));
        c.gridx = 1;
        panel.add(advanceSlotsSpinner, c);
        JButton advanceSlotsBtn = new JButton("Advance");
        advanceSlotsBtn.addActionListener(e -> doAdvanceSlots());
        c.gridx = 2;
        panel.add(advanceSlotsBtn, c);

        c.gridy = 1; c.gridx = 0;
        panel.add(new JLabel("Advance by Epochs:"), c);
        advanceEpochsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        c.gridx = 1;
        panel.add(advanceEpochsSpinner, c);
        JButton advanceEpochsBtn = new JButton("Advance");
        advanceEpochsBtn.addActionListener(e -> doAdvanceEpochs());
        c.gridx = 2;
        panel.add(advanceEpochsBtn, c);

        c.gridy = 2; c.gridx = 0; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JSeparator(), c);

        JLabel ttLabel = new JLabel("Past Time Travel (Epoch Shifting)");
        ttLabel.setFont(ttLabel.getFont().deriveFont(Font.BOLD));
        c.gridy = 3; c.fill = GridBagConstraints.NONE;
        panel.add(ttLabel, c);

        c.gridy = 4; c.gridwidth = 1;
        panel.add(new JLabel("Shift Back Epochs:"), c);
        shiftEpochsSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 1000, 1));
        c.gridx = 1;
        panel.add(shiftEpochsSpinner, c);
        JButton shiftBtn = new JButton("Shift Genesis");
        shiftBtn.addActionListener(e -> doShiftEpochs());
        c.gridx = 2;
        panel.add(shiftBtn, c);

        JButton catchUpBtn = new JButton("Catch Up to Wall Clock");
        catchUpBtn.addActionListener(e -> doCatchUp());
        c.gridy = 5; c.gridx = 0; c.gridwidth = 3;
        panel.add(catchUpBtn, c);

        timeStatusLabel = createStatusLabel();
        c.gridy = 6; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(timeStatusLabel, c);

        return wrapNorth(panel);
    }

    // ========== Setup Actions ==========

    private void fetchVersions() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Fetching Yano versions...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    List<YanoDownloader.ReleaseInfo> versions = YanoDownloader.fetchAvailableVersions();
                    SwingUtilities.invokeLater(() -> {
                        versionCombo.removeAllItems();
                        for (YanoDownloader.ReleaseInfo v : versions) {
                            versionCombo.addItem(v);
                        }
                        if (!versions.isEmpty()) versionCombo.setSelectedIndex(0);
                        setStatus(installStatusLabel, "Found " + versions.size() + " version(s)", false);
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> setStatus(installStatusLabel, "Failed to fetch: " + e.getMessage(), true));
                }
            }
        });
    }

    private void downloadSelected() {
        YanoDownloader.ReleaseInfo selected = (YanoDownloader.ReleaseInfo) versionCombo.getSelectedItem();
        if (selected == null) {
            setStatus(installStatusLabel, "Select a version first", true);
            return;
        }

        Path installDir = YanoDownloader.getVersionInstallDir(selected.tag);
        setStatus(installStatusLabel, "Downloading " + selected.tag + "...", false);
        log("Downloading Yano " + selected.tag + " to " + installDir);

        YanoDownloader downloader = new YanoDownloader(installDir);
        downloader.setOnComplete(() -> SwingUtilities.invokeLater(() -> {
            setStatus(installStatusLabel, "Installed: " + selected.tag, false);
            installStatusLabel.setForeground(new Color(0, 128, 0));
            log("Yano " + selected.tag + " installed successfully");
        }));
        downloader.install(selected.downloadUrl);
    }

    private void checkInstalledVersions() {
        Path baseDir = Path.of(YanoDownloader.DEFAULT_INSTALL_BASE);
        if (Files.exists(baseDir)) {
            try {
                Files.list(baseDir).filter(Files::isDirectory).findFirst().ifPresent(dir -> {
                    String home = YanoDownloader.findYanoHome(dir);
                    if (home != null) {
                        setStatus(installStatusLabel, "Installed: " + dir.getFileName(), false);
                        installStatusLabel.setForeground(new Color(0, 128, 0));
                    }
                });
            } catch (Exception e) { /* ignore */ }
        }
    }

    private String resolveYanoHome() {
        YanoDownloader.ReleaseInfo selected = (YanoDownloader.ReleaseInfo) versionCombo.getSelectedItem();
        if (selected != null) {
            Path installDir = YanoDownloader.getVersionInstallDir(selected.tag);
            if (Files.exists(installDir)) return YanoDownloader.findYanoHome(installDir);
        }
        Path baseDir = Path.of(YanoDownloader.DEFAULT_INSTALL_BASE);
        if (Files.exists(baseDir)) {
            try {
                return Files.list(baseDir).filter(Files::isDirectory)
                        .map(YanoDownloader::findYanoHome)
                        .filter(h -> h != null).findFirst().orElse(null);
            } catch (Exception e) { /* ignore */ }
        }
        return null;
    }

    private void doStartYano() {
        String home = resolveYanoHome();
        if (home == null) {
            setStatus(installStatusLabel, "No Yano installation found. Download first.", true);
            return;
        }
        int port = (int) portSpinner.getValue();
        startBtn.setEnabled(false);
        statusLabel.setText("Starting...");
        statusLabel.setForeground(new Color(200, 150, 0));
        log("Starting Yano from " + home + " on port " + port);

        YanoLifecycleService.getInstance().startYano(project, home, port)
                .thenAccept(success -> SwingUtilities.invokeLater(() -> {
                    if (success) {
                        statusLabel.setText("Running");
                        statusLabel.setForeground(new Color(0, 128, 0));
                        stopBtn.setEnabled(true);
                        log("Yano started on http://localhost:" + port);
                        initStatusMonitor();
                    } else {
                        statusLabel.setText("Failed");
                        statusLabel.setForeground(Color.RED);
                        startBtn.setEnabled(true);
                        log("Failed to start Yano");
                    }
                }))
                .exceptionally(t -> {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Error: " + t.getMessage());
                        statusLabel.setForeground(Color.RED);
                        startBtn.setEnabled(true);
                    });
                    return null;
                });
    }

    private void doStopYano() {
        stopBtn.setEnabled(false);
        statusLabel.setText("Stopping...");
        log("Stopping Yano...");

        YanoLifecycleService.getInstance().stopYano(project)
                .thenAccept(success -> SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Stopped");
                    statusLabel.setForeground(Color.GRAY);
                    startBtn.setEnabled(true);
                    slotLabel.setText("-");
                    blockLabel.setText("-");
                    log("Yano stopped");
                }));
    }

    // ========== Fund Actions ==========

    private void doFund() {
        String address = fundAddressTf.getText().trim();
        int amount = (int) fundAmountSpinner.getValue();
        if (address.isEmpty()) {
            setStatus(fundStatusLabel, "Please enter or choose an address", true);
            return;
        }
        setStatus(fundStatusLabel, "Funding...", false);

        runAsync("Funding account", () -> {
            YanoDevnetService svc = getDevnetService();
            FundResponse resp = svc.fundAddress(address, BigDecimal.valueOf(amount));
            String msg = "Funded " + amount + " ADA | tx: " + resp.getTxHash();
            log(msg);
            SwingUtilities.invokeLater(() -> setStatus(fundStatusLabel, msg, false));
        });
    }

    // ========== Snapshot Actions ==========

    private void doCreateSnapshot() {
        String name = snapshotNameTf.getText().trim();
        if (name.isEmpty()) {
            setStatus(snapshotStatusLabel, "Enter a snapshot name", true);
            return;
        }
        setStatus(snapshotStatusLabel, "Creating snapshot...", false);

        runAsync("Creating snapshot", () -> {
            YanoDevnetService svc = getDevnetService();
            SnapshotResponse resp = svc.createSnapshot(name);
            String msg = "Snapshot '" + resp.getName() + "' created at slot " + resp.getSlot();
            log(msg);
            SwingUtilities.invokeLater(() -> {
                setStatus(snapshotStatusLabel, msg, false);
                doRefreshSnapshots();
            });
        });
    }

    private void doRestoreSnapshot() {
        int row = snapshotTable.getSelectedRow();
        if (row < 0) {
            setStatus(snapshotStatusLabel, "Select a snapshot to restore", true);
            return;
        }
        String name = (String) snapshotTableModel.getValueAt(row, 0);
        setStatus(snapshotStatusLabel, "Restoring '" + name + "'...", false);

        runAsync("Restoring snapshot", () -> {
            getDevnetService().restoreSnapshot(name);
            String msg = "Restored snapshot '" + name + "'";
            log(msg);
            SwingUtilities.invokeLater(() -> setStatus(snapshotStatusLabel, msg, false));
        });
    }

    private void doDeleteSnapshot() {
        int row = snapshotTable.getSelectedRow();
        if (row < 0) return;
        String name = (String) snapshotTableModel.getValueAt(row, 0);

        runAsync("Deleting snapshot", () -> {
            getDevnetService().deleteSnapshot(name);
            log("Deleted snapshot '" + name + "'");
            SwingUtilities.invokeLater(() -> {
                setStatus(snapshotStatusLabel, "Deleted '" + name + "'", false);
                doRefreshSnapshots();
            });
        });
    }

    private void doRefreshSnapshots() {
        runAsync("Refreshing snapshots", () -> {
            List<SnapshotResponse> snapshots = getDevnetService().listSnapshots();
            SwingUtilities.invokeLater(() -> {
                snapshotTableModel.setRowCount(0);
                for (SnapshotResponse s : snapshots) {
                    snapshotTableModel.addRow(new Object[]{s.getName(), s.getSlot(), s.getBlockNumber(), s.getCreatedAt()});
                }
                setStatus(snapshotStatusLabel, snapshots.size() + " snapshot(s)", false);
            });
        });
    }

    // ========== Rollback Actions ==========

    private void doRollback() {
        int count = (int) rollbackCountSpinner.getValue();
        setStatus(rollbackStatusLabel, "Rolling back " + count + " blocks...", false);

        runAsync("Rolling back", () -> {
            RollbackResponse resp = getDevnetService().rollbackByCount(count);
            String msg = "Rolled back " + count + " blocks -> slot " + resp.getSlot() + ", block " + resp.getBlockNumber();
            log(msg);
            SwingUtilities.invokeLater(() -> setStatus(rollbackStatusLabel, msg, false));
        });
    }

    // ========== Time Machine Actions ==========

    private void doAdvanceSlots() {
        int slots = (int) advanceSlotsSpinner.getValue();
        setStatus(timeStatusLabel, "Advancing " + slots + " slots...", false);

        runAsync("Advancing time", () -> {
            TimeAdvanceResponse resp = getDevnetService().advanceBySlots(slots);
            String msg = "Advanced " + resp.getBlocksProduced() + " blocks -> slot " + resp.getNewSlot();
            log(msg);
            SwingUtilities.invokeLater(() -> setStatus(timeStatusLabel, msg, false));
        });
    }

    private void doAdvanceEpochs() {
        int epochs = (int) advanceEpochsSpinner.getValue();
        setStatus(timeStatusLabel, "Advancing " + epochs + " epoch(s)...", false);

        runAsync("Advancing epochs", () -> {
            TimeAdvanceResponse resp = getDevnetService().advanceByEpochs(epochs);
            String msg = "Advanced " + epochs + " epoch(s), " + resp.getBlocksProduced() + " blocks -> slot " + resp.getNewSlot();
            log(msg);
            SwingUtilities.invokeLater(() -> setStatus(timeStatusLabel, msg, false));
        });
    }

    private void doShiftEpochs() {
        int epochs = (int) shiftEpochsSpinner.getValue();
        setStatus(timeStatusLabel, "Shifting genesis back " + epochs + " epochs...", false);

        runAsync("Shifting epochs", () -> {
            EpochShiftResponse resp = getDevnetService().shiftEpochs(epochs);
            String msg = "Shifted back " + epochs + " epochs. New start: " + resp.getNewSystemStart();
            log(msg);
            SwingUtilities.invokeLater(() -> setStatus(timeStatusLabel, msg, false));
        });
    }

    private void doCatchUp() {
        setStatus(timeStatusLabel, "Catching up to wall clock...", false);

        runAsync("Catching up", () -> {
            TimeAdvanceResponse resp = getDevnetService().catchUpToWallClock();
            String msg = "Caught up: " + resp.getBlocksProduced() + " blocks -> slot " + resp.getNewSlot();
            log(msg);
            SwingUtilities.invokeLater(() -> setStatus(timeStatusLabel, msg, false));
        });
    }

    // ========== Status Monitor ==========

    private void initStatusMonitor() {
        YanoStatusMonitor monitor = YanoLifecycleService.getInstance().getStatusMonitor(project);
        if (monitor != null) {
            monitor.addStatusChangeListener(new YanoStatusMonitor.StatusChangeListener() {
                @Override
                public void onStatusChanged(YanoProcessManager.YanoStatus oldStatus, YanoProcessManager.YanoStatus newStatus) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText(newStatus.name());
                        statusLabel.setForeground(newStatus == YanoProcessManager.YanoStatus.RUNNING
                                ? new Color(0, 128, 0) : Color.GRAY);
                        startBtn.setEnabled(newStatus != YanoProcessManager.YanoStatus.RUNNING);
                        stopBtn.setEnabled(newStatus == YanoProcessManager.YanoStatus.RUNNING);
                    });
                }

                @Override public void onHealthChanged(boolean healthy) {}

                @Override
                public void onChainTipChanged(long slot, long blockNumber) {
                    SwingUtilities.invokeLater(() -> {
                        slotLabel.setText(String.valueOf(slot));
                        blockLabel.setText(String.valueOf(blockNumber));
                    });
                }
            });
        }
    }

    // ========== Helpers ==========

    private YanoDevnetService getDevnetService() {
        YanoProcessManager pm = YanoLifecycleService.getInstance().getProcessManager(project);
        String baseUrl = pm != null ? pm.getBaseUrl() : "http://localhost:" + portSpinner.getValue();
        return new YanoDevnetService(baseUrl);
    }

    private void log(String message) {
        CardanoConsole console = CardanoConsole.getConsole(project);
        console.showInfoMessage("[Yano] " + message);
    }

    private void runAsync(String title, DevnetAction action) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    action.execute();
                } catch (Exception e) {
                    LOG.warn("Yano action failed: " + title, e);
                    String errMsg = "Error: " + e.getMessage();
                    log(errMsg);
                    // Update the relevant status label on error
                    SwingUtilities.invokeLater(() -> {
                        if (title.contains("Fund")) setStatus(fundStatusLabel, errMsg, true);
                        else if (title.contains("snapshot") || title.contains("Snapshot")) setStatus(snapshotStatusLabel, errMsg, true);
                        else if (title.contains("Rolling") || title.contains("Rollback")) setStatus(rollbackStatusLabel, errMsg, true);
                        else setStatus(timeStatusLabel, errMsg, true);
                    });
                }
            }
        });
    }

    private static void setStatus(JLabel label, String text, boolean isError) {
        label.setText(text);
        label.setForeground(isError ? Color.RED : new Color(0, 100, 0));
    }

    private static JLabel createStatusLabel() {
        JLabel label = new JLabel(" ");
        label.setFont(label.getFont().deriveFont(Font.ITALIC, 11f));
        label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return label;
    }

    @FunctionalInterface
    private interface DevnetAction {
        void execute() throws Exception;
    }

    private static GridBagConstraints gbc() {
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 4);
        return c;
    }

    private static JPanel wrapNorth(JPanel inner) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(inner, BorderLayout.NORTH);
        return wrapper;
    }
}
