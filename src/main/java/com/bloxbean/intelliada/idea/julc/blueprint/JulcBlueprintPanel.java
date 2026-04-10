package com.bloxbean.intelliada.idea.julc.blueprint;

import com.bloxbean.intelliada.idea.julc.service.BlueprintLoadService;
import com.bloxbean.intelliada.idea.julc.service.PlutusBlueprint;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * Panel showing compiled validators from the CIP-57 blueprint.
 * Displays: title, script hash, size, compiled code.
 */
public class JulcBlueprintPanel {
    private final Project project;
    private JPanel mainPanel;
    private DefaultTableModel tableModel;
    private JTable validatorTable;
    private JTextArea uplcTextArea;
    private PlutusBlueprint blueprint;

    public JulcBlueprintPanel(Project project) {
        this.project = project;
        initComponents();
        loadBlueprint();
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void initComponents() {
        mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Validator table
        tableModel = new DefaultTableModel(new String[]{"Validator", "Script Hash", "Size (bytes)", "Parameterized"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        validatorTable = new JTable(tableModel);
        validatorTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        validatorTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedValidator();
            }
        });

        JScrollPane tableScroll = new JScrollPane(validatorTable);
        tableScroll.setPreferredSize(new Dimension(600, 200));

        // UPLC text area
        uplcTextArea = new JTextArea();
        uplcTextArea.setEditable(false);
        uplcTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane textScroll = new JScrollPane(uplcTextArea);
        textScroll.setPreferredSize(new Dimension(600, 200));

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, textScroll);
        splitPane.setResizeWeight(0.4);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton copyHashBtn = new JButton("Copy Script Hash");
        copyHashBtn.addActionListener(e -> copySelectedColumn(1));
        buttonPanel.add(copyHashBtn);

        JButton copyCodeBtn = new JButton("Copy Compiled Code");
        copyCodeBtn.addActionListener(e -> copyCompiledCode());
        buttonPanel.add(copyCodeBtn);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadBlueprint());
        buttonPanel.add(refreshBtn);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
    }

    private void loadBlueprint() {
        tableModel.setRowCount(0);
        uplcTextArea.setText("");

        blueprint = BlueprintLoadService.getInstance(project).loadBlueprint();
        if (blueprint == null) {
            uplcTextArea.setText("No blueprint found. Build the project first (julc build / ./gradlew build).");
            return;
        }

        for (PlutusBlueprint.ValidatorInfo v : blueprint.getValidators()) {
            tableModel.addRow(new Object[]{
                    v.getTitle(),
                    v.getHash(),
                    v.getSizeBytes(),
                    v.isParameterized() ? "Yes" : "No"
            });
        }

        if (blueprint.getPreamble() != null) {
            String title = blueprint.getPreamble().getTitle();
            String version = blueprint.getPreamble().getVersion();
            if (!title.isEmpty()) {
                uplcTextArea.setText("Project: " + title + " v" + version +
                        "\nValidators: " + blueprint.getValidators().size() +
                        "\n\nSelect a validator to view compiled code.");
            }
        }
    }

    private void showSelectedValidator() {
        int row = validatorTable.getSelectedRow();
        if (row < 0 || blueprint == null) return;

        PlutusBlueprint.ValidatorInfo v = blueprint.getValidators().get(row);
        StringBuilder sb = new StringBuilder();
        sb.append("Validator: ").append(v.getTitle()).append("\n");
        sb.append("Script Hash: ").append(v.getHash()).append("\n");
        sb.append("Size: ").append(v.getSizeBytes()).append(" bytes\n");
        sb.append("Parameterized: ").append(v.isParameterized()).append("\n\n");
        sb.append("--- Compiled Code (CBOR hex) ---\n");
        sb.append(v.getCompiledCode());
        uplcTextArea.setText(sb.toString());
        uplcTextArea.setCaretPosition(0);
    }

    private void copySelectedColumn(int col) {
        int row = validatorTable.getSelectedRow();
        if (row < 0) return;
        String value = String.valueOf(tableModel.getValueAt(row, col));
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
    }

    private void copyCompiledCode() {
        int row = validatorTable.getSelectedRow();
        if (row < 0 || blueprint == null) return;
        String code = blueprint.getValidators().get(row).getCompiledCode();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
    }
}
