package com.bloxbean.intelliada.idea.configuration.service;

import com.bloxbean.intelliada.idea.configuration.model.CLIProvider;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(
        name = "com.bloxbean.intelliada.CLIProvidersState",
        storages = {@Storage("cardano-cli-providers.xml")}
)
public class CLIProvidersState implements PersistentStateComponent<Element> {
    private static final Logger LOG = Logger.getInstance(CLIProvidersState.class);

    public static CLIProvidersState getInstance() {
        return ServiceManager.getService(CLIProvidersState.class);
    }

    private List<CLIProvider> cliProviders;
    private String defaultProvider;

    public CLIProvidersState() {
        this.cliProviders = new ArrayList<>();
    }

    @Nullable
    @Override
    public Element getState() {
        Element elm = new Element("installations");
        for(CLIProvider provider: cliProviders) {
            Element entry = new Element("providers");
            entry.setAttribute("id", provider.getId());
            entry.setAttribute("name", provider.getName());
            entry.setAttribute("home", StringUtil.notNullize(provider.getHome()));
            entry.setAttribute("version", StringUtil.notNullize(provider.getVersion()));

            elm.addContent(entry);
        }

        if(defaultProvider != null)
            elm.setAttribute("default-provider", defaultProvider);
        return elm;
    }

    @Override
    public void loadState(@NotNull Element elm) {
        List<CLIProvider> list = new ArrayList<>();

        defaultProvider = elm.getAttributeValue("default-provider");
        for (Element child : elm.getChildren("providers")) {
            String id = child.getAttributeValue("id");
            String name = child.getAttributeValue("name");
            String home = child.getAttributeValue("home");
            String version = child.getAttributeValue("version");

            CLIProvider pro = new CLIProvider(id, name, home, version);

            list.add(pro);
        }

        setCLIProviders(list);
    }

    public List<CLIProvider> getCLIProviders() {
        return cliProviders;
    }

    public void addCLIProvider(CLIProvider provider) {
        cliProviders.add(provider);
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public void updateCLIProvider(CLIProvider provider) {
        for(CLIProvider rnode: cliProviders) {
            if(rnode.getId() != null && rnode.getId().equals(provider.getId())) {
                rnode.updateValues(provider);
                break;
            }
        }
    }

    private void setCLIProviders(List<CLIProvider> list) {
        cliProviders = list;
    }

    public void removeCLIProvider(CLIProvider node) {
        if(cliProviders == null || node == null) return;
        cliProviders.remove(node);
    }
}
