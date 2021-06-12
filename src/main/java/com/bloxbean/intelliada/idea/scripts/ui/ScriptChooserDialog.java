package com.bloxbean.intelliada.idea.scripts.ui;

import com.bloxbean.intelliada.idea.scripts.ScriptExportUtil;
import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;
import com.bloxbean.intelliada.idea.scripts.service.ScriptService;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.util.JsonUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import static java.awt.event.MouseEvent.BUTTON1;

public class ScriptChooserDialog extends DialogWrapper {
    private JTable scriptTable;
    private JPanel mainPanel;
    private JEditorPane sourceEditor;
    private ScriptListTableModel tableModel;
    private Project project;
    private CardanoConsole console;
    private ScriptService scriptService;

    public ScriptChooserDialog(@Nullable Project project) {
        super(project, true);
        setTitle("Scripts");
        this.project = project;
        this.console = CardanoConsole.getConsole(project);
        this.scriptService = new ScriptService();
        initialize();
        init();
    }

    private void initialize() {
        initializeTable();
        loadScriptInfos(project);
    }

    private void initializeTable() {
        tableModel = new ScriptListTableModel();
        scriptTable.setModel(tableModel);

        scriptTable.getColumnModel().getColumn(0).setMaxWidth(150);
        scriptTable.getColumnModel().getColumn(0).setMinWidth(50);
        scriptTable.getColumnModel().getColumn(0).setPreferredWidth(150);

        scriptTable.getColumnModel().getColumn(1).setMaxWidth(150);
        scriptTable.getColumnModel().getColumn(1).setMinWidth(50);
        scriptTable.getColumnModel().getColumn(1).setPreferredWidth(50);

        scriptTable.setSelectionMode(DefaultListSelectionModel.SINGLE_SELECTION);
        ListSelectionModel listSelectionModel = scriptTable.getSelectionModel();
        listSelectionModel.addListSelectionListener(e -> {
            int index = scriptTable.getSelectedRow();
            if(index != -1 && index <= tableModel.getScriptInfos().size() - 1) {
                int selectedRowsCount = scriptTable.getSelectedRowCount();
                if(selectedRowsCount > 1) {
                    sourceEditor.setText("");
                } else if(selectedRowsCount == 1) {
                    ScriptInfo scriptInfo = tableModel.getScriptInfos().get(index);
                    if(scriptInfo.getScript() != null)
                        sourceEditor.setText(JsonUtil.getPrettyJson(scriptInfo.getScript()));
                }
            } else {
                sourceEditor.setText("");
            }
        });

        attachTableListener();
    }

    public void loadScriptInfos(Project project) {
        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
                @Override
                public void run() {
                    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
                    progressIndicator.setIndeterminate(false);
                    progressIndicator.setText("Fetching scripts ....");
                    try {
                        List<ScriptInfo> scriptInfos = scriptService.getScripts();
                        tableModel.setElements(scriptInfos);

                        progressIndicator.setFraction(1.0);
                    } catch (Exception e) {
                        console.showErrorMessage("Error getting scripts");
                        console.showErrorMessage(e.getMessage(), e);
                    } finally {
                        if(progressIndicator != null) {
                            try {
                                progressIndicator.setFraction(1.0);
                            } catch (Exception e) {

                            }
                        }
                    }
                }
            }, "Fetching scripts ...", true, project);

        } finally {

        }
    }

    private void attachTableListener() {
        scriptTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if(e.getClickCount() == 1)
                    tableRowPopupMenuHandler(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if(e.getClickCount() == 1)
                    tableRowPopupMenuHandler(e);
            }

            public void mouseClicked(MouseEvent me) {
                if (me.getClickCount() == 2 && me.getButton() == BUTTON1) {     // to detect doble click events
                    handleDoubleClickEvents();
                }
            }
        });

    }

    private void handleDoubleClickEvents() {
        //No action for now
    }

    private void tableRowPopupMenuHandler(MouseEvent e) {
        int r = scriptTable.rowAtPoint(e.getPoint());
        if (r >= 0 && r < scriptTable.getRowCount()) {
            scriptTable.setRowSelectionInterval(r, r);
        } else {
            scriptTable.clearSelection();
        }

        int rowindex = scriptTable.getSelectedRow();
        if (rowindex < 0)
            return;
        if (e.isPopupTrigger() && e.getComponent() instanceof JTable ) {
            ScriptInfo scriptInfo = tableModel.getScriptInfos().get(rowindex);
            ListPopup popup = createPopup(scriptInfo);
            RelativePoint relativePoint = new RelativePoint(e.getComponent(), new Point(e.getX(), e.getY()));
            popup.show(relativePoint);
        }
    }

    private ListPopup createPopup(ScriptInfo scriptInfo) {
        final DefaultActionGroup group = new DefaultActionGroup();

        group.add(createExportAction(scriptInfo));
        group.add(createRemoveAction(scriptInfo));

        DataContext dataContext = DataManager.getInstance().getDataContext(scriptTable);
        return JBPopupFactory.getInstance().createActionGroupPopup("",
                group, dataContext, JBPopupFactory.ActionSelectionAid.MNEMONICS, true);
    }

    private AnAction createExportAction(ScriptInfo scriptInfo) {
        return new AnAction("Export", "Export Script", AllIcons.General.ArrowRight) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                try {
                    ScriptExportUtil.exportScriptInfo(scriptInfo, project, mainPanel);
                } catch (Exception exception) {
                    console.showErrorMessage("Script Export error", exception);
                }
            }

            @Override
            public boolean isDumbAware() {
                return true;
            }
        };
    }

    private AnAction createRemoveAction(ScriptInfo scriptInfo) {
        return new AnAction("Delete", "Delete", AllIcons.General.Remove) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if(scriptInfo == null) return;
                int response = Messages.showYesNoDialog("Do you really want to delete this script: " + scriptInfo.getName() +"?",
                        "Delete Script", AllIcons.General.Warning);
                if (response == Messages.YES) {
                    if (scriptService.removeScript(scriptInfo)) {
                        int index = tableModel.getScriptInfos().indexOf(scriptInfo);
                        if (index != -1 && index < tableModel.getScriptInfos().size()) {
                            tableModel.getScriptInfos().remove(index);
                            tableModel.fireTableDataChanged();
                        }
                    }
                }
            }

            @Override
            public boolean isDumbAware() {
                return true;
            }
        };
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return mainPanel;
    }
}
