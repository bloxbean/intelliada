package com.bloxbean.intelliada.idea.core.messaging;

import com.bloxbean.intelliada.idea.configuration.model.CLIProvider;
import com.intellij.util.messages.Topic;

public interface CLIProvidersChangeNotifier {
    Topic<CLIProvidersChangeNotifier> CHANGE_CLI_PROVIDERS_TOPIC = Topic.create("CardanoCLIProvidersTopic", CLIProvidersChangeNotifier.class);

    void providerAdded(CLIProvider provider);
    void providerUpdated(CLIProvider provider);
    void providerDeleted(CLIProvider provider);
    void defaultProviderChanged(String newProviderId);
}
