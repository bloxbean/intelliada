package com.bloxbean.intelliada.idea.julc.configuration.service;

import com.bloxbean.intelliada.idea.julc.configuration.JulcSDK;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
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
        name = "com.bloxbean.julc.JulcSDKState",
        storages = {@Storage("julc-sdks.xml")}
)
public class JulcSDKState implements PersistentStateComponent<Element> {
    private static final Logger LOG = Logger.getInstance(JulcSDKState.class);

    public static JulcSDKState getInstance() {
        return ApplicationManager.getApplication().getService(JulcSDKState.class);
    }

    private List<JulcSDK> sdks;

    public JulcSDKState() {
        this.sdks = new ArrayList<>();
    }

    @Nullable
    @Override
    public Element getState() {
        Element state = new Element("sdks");
        for (JulcSDK sdk : sdks) {
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
        List<JulcSDK> list = new ArrayList<>();
        for (Element child : elm.getChildren("sdk")) {
            String id = child.getAttributeValue("id");
            String name = child.getAttributeValue("name");
            String path = child.getAttributeValue("path");
            String version = child.getAttributeValue("version");
            list.add(new JulcSDK(id, name, path, version));
        }
        setSdks(list);
    }

    public List<JulcSDK> getSdks() {
        return sdks;
    }

    public void addSdk(JulcSDK sdk) {
        sdks.add(sdk);
    }

    public void updateSdk(JulcSDK sdk) {
        for (JulcSDK existing : sdks) {
            if (existing.getId() != null && existing.getId().equals(sdk.getId())) {
                existing.updateValues(sdk);
                break;
            }
        }
    }

    public void removeSdk(JulcSDK sdk) {
        if (sdks == null || sdk == null) return;
        sdks.remove(sdk);
    }

    private void setSdks(List<JulcSDK> list) {
        sdks = list;
    }
}
