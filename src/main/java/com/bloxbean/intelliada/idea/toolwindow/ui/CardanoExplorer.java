package com.bloxbean.intelliada.idea.toolwindow.ui;

import com.bloxbean.intelliada.idea.account.action.AccountListAction;
import com.bloxbean.intelliada.idea.configuration.action.*;
import com.bloxbean.intelliada.idea.configuration.model.CLIProvider;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.messaging.CLIProvidersChangeNotifier;
import com.bloxbean.intelliada.idea.core.messaging.RemoteNodeChangeNotifier;
import com.bloxbean.intelliada.idea.nativetoken.action.TokenMintingTransactionAction;
import com.bloxbean.intelliada.idea.scripts.action.CreateCompositeScriptAction;
import com.bloxbean.intelliada.idea.scripts.action.CreateScriptPubkeyAction;
import com.bloxbean.intelliada.idea.scripts.action.ListScriptsAction;
import com.bloxbean.intelliada.idea.toolwindow.CLIProviderDescriptor;
import com.bloxbean.intelliada.idea.toolwindow.CardanoExplorerTreeStructure;
import com.bloxbean.intelliada.idea.toolwindow.action.NetworkInfoAction;
import com.bloxbean.intelliada.idea.toolwindow.action.SetDefaultProviderAction;
import com.bloxbean.intelliada.idea.toolwindow.action.SetDefaultRemoteNodeAction;
import com.bloxbean.intelliada.idea.transaction.action.PaymentTransactionAction;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;

public class CardanoExplorer extends SimpleToolWindowPanel implements DataProvider, Disposable {
    private final static String CARDANO_EXPLORER_POPUP = "CardanoExplorerPopup";
    private Project myProject;
    private Tree myTree;
    private StructureTreeModel myTreeModel;
    private CardanoExplorerTreeStructure myTreeStructure;

    public CardanoExplorer(Project project) {
        super(true, true);

        myProject = project;

        myTreeStructure = new CardanoExplorerTreeStructure(project);
//       // myTreeStructure.setFilteredTargets(AntConfigurationBase.getInstance(project).isFilterTargets());
        final StructureTreeModel treeModel = new StructureTreeModel<>(myTreeStructure, this);
        myTreeModel = treeModel;
        myTree = new Tree(new AsyncTreeModel(treeModel, this));
        myTree.setRootVisible(true);
        myTree.setShowsRootHandles(true);
        myTree.setCellRenderer(new NodeRenderer());

        setToolbar(createToolbarPanel());
        setContent(ScrollPaneFactory.createScrollPane(myTree));
        ToolTipManager.sharedInstance().registerComponent(myTree);

        attachListeners();
        attachHandlers();
    }

    private void attachHandlers() {
        myTree.addMouseListener(new PopupHandler() {
            @Override
            public void invokePopup(Component comp, int x, int y) {
                popupInvoked(comp, x, y);
            }
        });
    }

    private void attachListeners() {
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(RemoteNodeChangeNotifier.CHANGE_CARDANO_REMOTE_NODE_TOPIC, new RemoteNodeChangeNotifier() {

                    @Override
                    public void nodeAdded(RemoteNode node) {
                        myTreeModel.invalidate();
                    }

                    @Override
                    public void nodeUpdated(RemoteNode node) {
                        myTreeModel.invalidate();
                    }

                    @Override
                    public void nodeDeleted(RemoteNode node) {
                        myTreeModel.invalidate();
                    }

                    @Override
                    public void defaultNodeChanged(String nodeId) {
                        myTreeModel.invalidate();
                    }
                });

        //TODO limited change scope
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(CLIProvidersChangeNotifier.CHANGE_CLI_PROVIDERS_TOPIC, new CLIProvidersChangeNotifier() {
                    @Override
                    public void providerAdded(CLIProvider provider) {
                        myTreeModel.invalidate();
                    }

                    @Override
                    public void providerUpdated(CLIProvider provider) {
                        myTreeModel.invalidate();
                    }

                    @Override
                    public void providerDeleted(CLIProvider provider) {
                        myTreeModel.invalidate();
                    }

                    @Override
                    public void defaultProviderChanged(String newProviderId) {
                        myTreeModel.invalidate();
                    }
                });
    }

    private void popupInvoked(Component comp, int x, int y) {
        Object userObject = null;
        final TreePath path = myTree.getSelectionPath();
        if (path != null) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node != null) {
                userObject = node.getUserObject();
            }
        }

        final DefaultActionGroup group = new DefaultActionGroup();
        if (userObject instanceof com.bloxbean.intelliada.idea.toolwindow.RemoteNodeDescriptor) {
            com.bloxbean.intelliada.idea.toolwindow.RemoteNodeDescriptor remoteNodeDescriptor = ((com.bloxbean.intelliada.idea.toolwindow.RemoteNodeDescriptor) userObject);
            RemoteNode nodeInfo = remoteNodeDescriptor.getNode();

            group.add(new UpdateRemoteNodeAction(nodeInfo));
            group.add(new DeleteRemoteNodeAction(nodeInfo));

            if (!remoteNodeDescriptor.isDefaultNode()) {
                group.add(new SetDefaultRemoteNodeAction(nodeInfo.getId()));
            }

            group.add(new NetworkInfoAction(nodeInfo));

        } else if (userObject instanceof CLIProviderDescriptor) {
            CLIProviderDescriptor providerDescriptor = (CLIProviderDescriptor) userObject;
            CLIProvider cliProvider = providerDescriptor.getProvider();

            group.add(new UpdateCLIProviderAction(cliProvider));
            group.add(new DeleteCLIProviderAction(cliProvider));

            if (!providerDescriptor.isDefaultProvider()) {
                group.add(new SetDefaultProviderAction(cliProvider.getId()));
            }

        } else if (userObject instanceof CardanoExplorerTreeStructure.CLIProvidersDescriptor) {
            group.add(new CreateCLIProviderAction());
        } else if (userObject instanceof CardanoExplorerTreeStructure.RemoteNodesDescriptor) {
            group.add(new CreateRemoteNodeAction());
        }


        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(CardanoExplorer.CARDANO_EXPLORER_POPUP, group);
        popupMenu.getComponent().show(comp, x, y);
    }

    @Override
    public @Nullable JComponent getToolbar() {
        return createToolbarPanel();
    }

    private JPanel createToolbarPanel() {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(new CreateRemoteNodeAction());
        group.add(new AccountListAction());
        group.add(new Separator());
        group.add(new PaymentTransactionAction());
        group.add(new TokenMintingTransactionAction());
        group.add(new Separator());

        final DefaultActionGroup scriptGroup = new DefaultActionGroup();
        scriptGroup.add(new CreateScriptPubkeyAction());
        scriptGroup.add(new CreateCompositeScriptAction());
        scriptGroup.add(new ListScriptsAction());

        group.add(scriptGroup);

        final ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.ANT_EXPLORER_TOOLBAR, group, true);
        return JBUI.Panels.simplePanel(actionToolBar.getComponent());
    }

    @Override
    public void dispose() {
        myTree = null;
        myProject = null;
    }
}
