package com.bloxbean.intelliada.idea.aiken.messaging;

import com.bloxbean.intelliada.idea.aiken.configuration.AikenSDK;
import com.intellij.util.messages.Topic;

public interface AikenSDKChangeNotifier {
    Topic<AikenSDKChangeNotifier> CHANGE_AIKEN_SDK_TOPIC = Topic.create("AikenSDKTopic", AikenSDKChangeNotifier.class);

    void sdkAdded(AikenSDK sdk);
    void sdkUpdated(AikenSDK sdk);
    void sdkDeleted(AikenSDK sdk);
}
