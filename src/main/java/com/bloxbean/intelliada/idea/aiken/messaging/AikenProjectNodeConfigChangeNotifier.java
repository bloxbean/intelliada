package com.bloxbean.intelliada.idea.aiken.messaging;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;

public interface AikenProjectNodeConfigChangeNotifier {
    Topic<AikenProjectNodeConfigChangeNotifier> CHANGE_AIKEN_PROJECT_NODES_CONFIG_TOPIC
            = Topic.create("AikenProjectNodeConfigurationTopic", AikenProjectNodeConfigChangeNotifier.class);

    void configUpdated(Project project);
}
