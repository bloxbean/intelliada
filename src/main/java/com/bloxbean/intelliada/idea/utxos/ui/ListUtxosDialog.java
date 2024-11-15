package com.bloxbean.intelliada.idea.utxos.ui;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.intelliada.idea.nodeint.exception.TargetNodeNotConfigured;
import com.bloxbean.intelliada.idea.nodeint.service.CardanoServiceFactory;
import com.bloxbean.intelliada.idea.nodeint.service.api.CardanoAccountService;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListenerAdapter;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.bloxbean.intelliada.idea.util.JsonUtil;
import com.bloxbean.intelliada.idea.utxos.ui.model.UtxoAsset;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.awt.event.MouseEvent.BUTTON1;

public class ListUtxosDialog extends DialogWrapper {
    private JPanel mainPanel;
    private JTable utxosTable;
    private JTextField addressTf;
    private JList amountsJList;
    private UtxoListTableModel tableModel;
    private DefaultListModel<UtxoAsset> amountsListModel;
    private List<Utxo> ignoreUtxosList;
    private String address;
    private Project project;
    private CardanoConsole console;

    public ListUtxosDialog(@Nullable Project project, String address, List<Utxo> ignoreUtxos) {
        super(project, true);
        init();
        setTitle("Utxos Chooser");
        this.project = project;
        this.address = address;
        this.ignoreUtxosList = ignoreUtxos;
        this.console = CardanoConsole.getConsole(project);
        initialize();
    }

    private void initialize() {
        addressTf.setText(address);

        initializeUTxoTable();

        loadUtxos(project);

    }

    private void initializeUTxoTable() {
        tableModel = new UtxoListTableModel();
        utxosTable.setModel(tableModel);
        //Amount field
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        utxosTable.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);

        utxosTable.getColumnModel().getColumn(0).setMaxWidth(50);
        utxosTable.getColumnModel().getColumn(0).setMinWidth(20);
        utxosTable.getColumnModel().getColumn(0).setPreferredWidth(50);

        utxosTable.getColumnModel().getColumn(1).setMaxWidth(50);
        utxosTable.getColumnModel().getColumn(1).setMinWidth(20);
        utxosTable.getColumnModel().getColumn(1).setPreferredWidth(50);

        utxosTable.getColumnModel().getColumn(3).setMaxWidth(220);
        utxosTable.getColumnModel().getColumn(3).setMinWidth(50);
        utxosTable.getColumnModel().getColumn(3).setPreferredWidth(220);

        amountsJList.setModel(amountsListModel);

        ListSelectionModel listSelectionModel = utxosTable.getSelectionModel();
        listSelectionModel.addListSelectionListener(e -> {
            int index = utxosTable.getSelectedRow();
            if (index != -1 && index <= tableModel.getUtxos().size() - 1) {
                int selectedRowsCount = utxosTable.getSelectedRowCount();
                if (selectedRowsCount > 1) {
                    amountsListModel.clear();
                } else if (selectedRowsCount == 1) {
                    Utxo utxo = tableModel.getUtxos().get(index);
                    List<UtxoAsset> utxoAssets = utxo.getAmount().stream().map(amount -> new UtxoAsset(amount.getUnit(), amount.getQuantity()))
                            .collect(Collectors.toList());

                    amountsListModel.clear();
                    utxoAssets.stream().forEach(utxoAsset -> {
                        amountsListModel.addElement(utxoAsset);
                    });
                }
            } else {
                amountsListModel.clear();
            }
        });
        attachTableListener();
    }

    private void attachTableListener() {
        utxosTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent me) {
                if (me.getClickCount() == 2 && me.getButton() == BUTTON1) {     // to detect doble click events
                    copyTxnHash();
                }
            }
        });
    }

    private void copyTxnHash() {
        int index = utxosTable.getSelectedRow();
        if (index == -1) return;
        int selectedRowsCount = utxosTable.getSelectedRowCount();
        if (selectedRowsCount == 1) {
            Utxo utxo = tableModel.getUtxos().get(index);
            if (utxo == null)
                return;
            StringSelection stringSelection = new StringSelection(utxo.getTxHash());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
            Messages.showInfoMessage("Transaction hash copied to the clipboard", "Copy Transaction Hash");
        }
    }

    public List<Utxo> getSelectedUtxos() {
        int index = utxosTable.getSelectedRow();
        if (index == -1) return Collections.emptyList();

        int selectedRowsCount = utxosTable.getSelectedRowCount();
        if (selectedRowsCount == 1) {
            int selectedRow = utxosTable.getSelectedRow();
            return Arrays.asList(tableModel.getUtxos().get(selectedRow));
        } else if (selectedRowsCount > 1) {
            int[] selectedRows = utxosTable.getSelectedRows();
            List<Utxo> selectedUtxos = new ArrayList<>();
            for (int selectedRow : selectedRows) {
                Utxo utxo = tableModel.getUtxos().get(selectedRow);
                selectedUtxos.add(utxo);
            }

            return selectedUtxos;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        return mainPanel;
    }

    public void loadUtxos(Project project) {
        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
                @Override
                public void run() {
                    int count = 50;
                    int page = 1;
                    OrderEnum order = OrderEnum.asc;

                    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
                    progressIndicator.setIndeterminate(false);
                    progressIndicator.setText("Fetching utxos ....");

                    CardanoAccountService cardanoAccountService = null;
                    try {
                        cardanoAccountService = CardanoServiceFactory.getAccountService(project, new LogListenerAdapter(console));
                    } catch (TargetNodeNotConfigured targetNodeNotConfigured) {
                        console.showErrorMessage(targetNodeNotConfigured.getMessage());
                        IdeaUtil.showNotification(project, "Node Configuration",
                                "Cardano default remote node is not configured", NotificationType.ERROR, null);
                        return;
                    }

                    try {

                        List<Utxo> utxos = new ArrayList<>();
                        boolean canContinue = true;
                        while (canContinue) {
                            List fetchUtxos = cardanoAccountService.getUtxos(address, count, page, order);
                            if (page == 20) {
                                console.showWarningMessage("Too many utxos. Can't show more than 1000.");
                            }
                            if (fetchUtxos == null || fetchUtxos.size() == 0) {
                                canContinue = false;
                            } else {
                                utxos.addAll(fetchUtxos);
                                page++;
                            }

                            progressIndicator.setFraction(page / 20);
                            if (progressIndicator.isCanceled()) {
                                break;
                            }
                        }
                        console.showInfoMessage(JsonUtil.getPrettyJson(utxos));
                        tableModel.setElements(utxos);

                        progressIndicator.setFraction(1.0);
                    } catch (Exception e) {
                        console.showErrorMessage("Error getting utxos for address : " + address);
                        console.showErrorMessage(e.getMessage(), e);
                    } finally {
                        if (progressIndicator != null) {
                            try {
                                progressIndicator.setFraction(1.0);
                            } catch (Exception e) {

                            }
                        }
                    }
                }
            }, "Fetching utxos for the address ...", true, project);

        } finally {

        }
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        amountsListModel = new DefaultListModel<>();
        amountsJList = new JBList(amountsListModel);
    }
}
