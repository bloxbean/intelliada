package com.bloxbean.intelliada.idea.toolwindow;

import com.bloxbean.intelliada.idea.common.CardanoIcons;
import com.bloxbean.intelliada.idea.configuration.model.CLIProvider;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;

public class CLIProviderDescriptor extends NodeDescriptor {

    private CLIProvider provider;
    private boolean isDefaultProvider;

    public CLIProviderDescriptor(final Project project, final NodeDescriptor parentDescriptor, CLIProvider provider, String defaultId) {
        super(project, parentDescriptor);
        this.provider = provider;

        myName = provider.getName() + " [" + StringUtil.trimLog(provider.getVersion(), 40) + "]";
        myColor = JBColor.GREEN;
        myClosedIcon = CardanoIcons.CARDANO_ICON_16x16;

        if(provider.getId() != null) {
            if(provider.getId().equals(defaultId)) {
                myName += " (default)";
                this.isDefaultProvider = true;
            } else {
                this.isDefaultProvider = false;
            }
//            if (sdk.getId().equals(compilerNodeId)){
//                myClosedIcon = AlgoIcons.LOCALSDK_COMPILE;
//                isCompilerTarget = true;
//            } else {
//                myClosedIcon = AlgoIcons.LOCALSDK;
//            }
        } else {
            this.isDefaultProvider = false;
//            myClosedIcon = AlgoIcons.LOCALSDK;
        }
    }

    @Override
    public boolean update() {
        return false;
    }

    @Override
    public Object getElement() {
        return provider;
    }

    public CLIProvider getProvider() {
        return provider;
    }

    public boolean isDefaultProvider() {
        return isDefaultProvider;
    }

}
