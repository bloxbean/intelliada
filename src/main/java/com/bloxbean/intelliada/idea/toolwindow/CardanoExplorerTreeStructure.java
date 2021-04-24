package com.bloxbean.intelliada.idea.toolwindow;

import com.bloxbean.intelliada.idea.configuration.model.CLIProvider;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.configuration.service.CLIProvidersState;
import com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState;
import com.bloxbean.intelliada.idea.toolwindow.model.CardanoCLIRoot;
import com.bloxbean.intelliada.idea.toolwindow.model.RemoteNodeRoot;
import com.bloxbean.intelliada.idea.toolwindow.model.CardanoRoot;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class CardanoExplorerTreeStructure extends AbstractTreeStructure {

    private final Project project;
    private CardanoRoot root;
    private CardanoCLIRoot cliProviderRoot;
    private RemoteNodeRoot remoteNodeRoot;
    private String deploymentNodeId;

    public CardanoExplorerTreeStructure(Project project) {
        this.project = project;
        updateSelectedNodeInfos();

    }

    public void updateSelectedNodeInfos() {
        if(project == null)
            return;
//        NodeInfo compilerNodeInfo = AlgoServerConfigurationHelper.getCompilerNodeInfo(project);
//        if(compilerNodeInfo != null) {
//            compilerNodeId = compilerNodeInfo.getId();
//        }
//        RemoteNode localSDK = AlgoServerConfigurationHelper.getCompilerLocalSDK(project);
//        if(localSDK != null) {
//            compilerNodeId = localSDK.getId();
//        }

//        NodeInfo deployNodeInfo = AlgoServerConfigurationHelper.getDeploymentNodeInfo(project);
//        if(deployNodeInfo != null) {
//            deploymentNodeId = deployNodeInfo.getId();
//        }

    }

    @Override
    public @NotNull Object getRootElement() {
        if(root == null)
            root = new CardanoRoot();

        if(remoteNodeRoot == null)
            remoteNodeRoot = new RemoteNodeRoot();

        if(cliProviderRoot == null)
            cliProviderRoot = new CardanoCLIRoot();

        return root;
    }

    @Override
    public @NotNull Object[] getChildElements(@NotNull Object element) {
        if(element instanceof CardanoRoot) {
            return new Object[] {cliProviderRoot, remoteNodeRoot};
        } else if(element instanceof RemoteNodeRoot) { //Show Available Cardano Remote Nodes
            List<RemoteNode> sdks = RemoteNodeState.getInstance().getRemoteNodes();
            if(sdks != null && sdks.size() > 0)
                return sdks.toArray();
        } else if(element instanceof CardanoCLIRoot) { //Show available Cardano CLI Providers
            List<CLIProvider> cliProviders = CLIProvidersState.getInstance().getCLIProviders();
            if(cliProviders != null && cliProviders.size() > 0)
                return cliProviders.toArray();

        }
        return new Object[0];
    }

    @Override
    public @Nullable Object getParentElement(@NotNull Object element) {
        if (element instanceof CardanoRoot) {
            return null;
        } else if(element instanceof RemoteNodeRoot ) {
            return root;
        } else if (element instanceof RemoteNode) {
            return remoteNodeRoot;
        } else
            return null;
    }

    @Override
    public @NotNull NodeDescriptor createDescriptor(@NotNull Object element, @Nullable NodeDescriptor parentDescriptor) {
        if(element == root) {
            return new RootNodeDescriptor(project, parentDescriptor);
        }

        if(element == remoteNodeRoot) {
            return new RemoteNodesDescriptor(project, parentDescriptor);
        } else if(element == cliProviderRoot) {
            return new CLIProvidersDescriptor(project, parentDescriptor);
        }


        if(element instanceof RemoteNode) {
            return new RemoteNodeDescriptor(project, parentDescriptor, (RemoteNode) element,  deploymentNodeId);
        } else if(element instanceof CLIProvider) {
            String defaultProvider = CLIProvidersState.getInstance().getDefaultProvider();
            return new CLIProviderDescriptor(project, parentDescriptor, (CLIProvider) element, defaultProvider);
        }

        return null;
    }

    @Override
    public void commit() {

    }

    @Override
    public boolean hasSomethingToCommit() {
        return false;
    }

    public CardanoRoot getRootNode() {
        return root;
    }

    public RemoteNodeRoot getLocalSDKRoot() {
        return remoteNodeRoot;
    }

    public final class RootNodeDescriptor extends NodeDescriptor {

        public RootNodeDescriptor(@Nullable Project project, @Nullable NodeDescriptor parentDescriptor) {
            super(project, parentDescriptor);
            myName = "Cardano";
            myClosedIcon = AllIcons.Nodes.Folder;
        }

        @Override
        public boolean update() {
            return false;
        }

        @Override
        public Object getElement() {
            return root;
        }
    }

    public final class RemoteNodesDescriptor extends NodeDescriptor {

        public RemoteNodesDescriptor(@Nullable Project project, @Nullable NodeDescriptor parentDescriptor) {
            super(project, parentDescriptor);
            myName = "Remote Nodes";
            myClosedIcon = AllIcons.Nodes.Folder;
            myColor = Color.blue;
        }

        @Override
        public boolean update() {
            return false;
        }

        @Override
        public Object getElement() {
            return remoteNodeRoot;
        }
    }

    public final class CLIProvidersDescriptor extends NodeDescriptor {
        public CLIProvidersDescriptor(@Nullable Project project, @Nullable NodeDescriptor parentDescriptor) {
            super(project, parentDescriptor);
            myName = "CLI Providers";
            myClosedIcon = AllIcons.Nodes.Folder;
            myColor = Color.blue;
        }

        @Override
        public boolean update() {
            return false;
        }

        @Override
        public Object getElement() {
            return cliProviderRoot;
        }
    }
}
