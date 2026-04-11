package com.bloxbean.intelliada.idea.julc.messaging;

import com.bloxbean.intelliada.idea.julc.configuration.JulcSDK;
import com.intellij.util.messages.Topic;

public interface JulcSDKChangeNotifier {
    Topic<JulcSDKChangeNotifier> CHANGE_JULC_SDK_TOPIC = Topic.create("JulcSDKTopic", JulcSDKChangeNotifier.class);

    void sdkAdded(JulcSDK sdk);
    void sdkUpdated(JulcSDK sdk);
    void sdkDeleted(JulcSDK sdk);
}
