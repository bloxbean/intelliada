package com.bloxbean.intelliada.idea.aiken.configuration.service;

import com.bloxbean.intelliada.idea.aiken.configuration.AikenSDK;
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
        name = "com.bloxbean.aiken.AikenDKState",
        storages = {@Storage("aiken-sdks.xml")}
)
public class AikenSDKState implements PersistentStateComponent<Element> {
    private static final Logger LOG = Logger.getInstance(AikenSDKState.class);

    public static AikenSDKState getInstance() {
        return ServiceManager.getService(AikenSDKState.class);
    }

    private List<AikenSDK> sdks;

    public AikenSDKState() {
        this.sdks = new ArrayList<>();
    }

    @Nullable
    @Override
    public Element getState() {
        Element state = new Element("sdks");

        for(AikenSDK sdk: sdks) {
            Element entry = new Element("sdk");
            entry.setAttribute("id", sdk.getId());
            entry.setAttribute("name", sdk.getName());
            entry.setAttribute("path", StringUtil.notNullize(sdk.getPath()));
            entry.setAttribute("version", StringUtil.notNullize(sdk.getVersion()));

            state.addContent(entry);
        }

        return state;
    }

    @Override
    public void loadState(@NotNull Element elm) {
        List<AikenSDK> list = new ArrayList<>();

        for (Element child : elm.getChildren("localSdk")) {
            String id = child.getAttributeValue("id");
            String name = child.getAttributeValue("name");
            String path = child.getAttributeValue("path");
            String version = child.getAttributeValue("version");

            AikenSDK sdk = new AikenSDK(id, name, path,version);

            list.add(sdk);
        }

        setSdks(list);
    }

    public List<AikenSDK> getSdks() {
        return sdks;
    }

    public void addLocalSdk(AikenSDK sdk) {
        sdks.add(sdk);
    }

    public void updateLocalSdk(AikenSDK sdk) {
        for(AikenSDK lsdk: sdks) {
            if(lsdk.getId() != null && lsdk.getId().equals(sdk.getId())) {
                lsdk.updateValues(sdk);
                break;
            }
        }
    }

    private void setSdks(List<AikenSDK> list) {
        sdks = list;
    }

    public void removeSdk(AikenSDK sdk) {
        if(sdks == null || sdk == null) return;
        sdks.remove(sdk);
    }
}
