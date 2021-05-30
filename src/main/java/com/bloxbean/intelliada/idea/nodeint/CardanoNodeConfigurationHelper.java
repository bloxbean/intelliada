package com.bloxbean.intelliada.idea.nodeint;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState;
import com.intellij.openapi.project.Project;

public class CardanoNodeConfigurationHelper {

    public static RemoteNode getTargetRemoteNode(Project project) {
        return RemoteNodeState.getInstance().getDefaultRemoteNode();
    }
}
