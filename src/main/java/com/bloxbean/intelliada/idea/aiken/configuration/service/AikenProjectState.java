package com.bloxbean.intelliada.idea.aiken.configuration.service;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

@State(
        name = "com.bloxbean.aiken.AikenProjectState",
        reloadable = true,
        storages = {@Storage("aiken-project.xml")}
)
public class AikenProjectState implements PersistentStateComponent<AikenProjectState.State> {
    private static final Logger LOG = Logger.getInstance(AikenProjectState.class);

    public enum ConfigType { local_sdk, embedded_sdk}

    public static AikenProjectState getInstance(Project project) {
        return ServiceManager.getService(project, AikenProjectState.class);
    }

    public static class State {
        private ConfigType sdkType;
        private String sdkId;


        public ConfigType getSdkType() {
            return sdkType;
        }

        public void setSdkType(ConfigType sdkType) {
            this.sdkType = sdkType;
        }

        public String getSdkId() {
            return sdkId;
        }

        public void setSdkId(String sdkId) {
            this.sdkId = sdkId;
        }

    }

    private State state = new State();

    public State getState() {
        return state;
    }

    public void loadState(State state) {
        this.state = state;
    }

    public void setState(State state) {
        this.state = state;
    }
}
