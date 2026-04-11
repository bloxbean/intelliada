package com.bloxbean.intelliada.idea.julc.messaging;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;

public interface JulcProjectConfigChangeNotifier {
    Topic<JulcProjectConfigChangeNotifier> CHANGE_JULC_PROJECT_CONFIG_TOPIC
            = Topic.create("JulcProjectConfigurationTopic", JulcProjectConfigChangeNotifier.class);

    void configUpdated(Project project);
}
