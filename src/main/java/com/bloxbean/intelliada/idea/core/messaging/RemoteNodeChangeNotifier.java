package com.bloxbean.intelliada.idea.core.messaging;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.intellij.util.messages.Topic;

public interface RemoteNodeChangeNotifier {
    Topic<RemoteNodeChangeNotifier> CHANGE_CARDANO_REMOTE_NODE_TOPIC = Topic.create("CardanoRemoteNodeTopic", RemoteNodeChangeNotifier.class);

    void nodeAdded(RemoteNode sdk);
    void nodeUpdated(RemoteNode sdk);
    void nodeDeleted(RemoteNode sdk);
}
