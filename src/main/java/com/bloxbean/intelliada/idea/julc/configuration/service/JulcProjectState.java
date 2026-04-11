package com.bloxbean.intelliada.idea.julc.configuration.service;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

@State(
        name = "com.bloxbean.julc.JulcProjectState",
        reloadable = true,
        storages = {@Storage("julc-project.xml")}
)
public class JulcProjectState implements PersistentStateComponent<JulcProjectState.State> {
    private static final Logger LOG = Logger.getInstance(JulcProjectState.class);

    public enum ConfigType {local_sdk}

    public enum ProjectType {basic, gradle, maven}

    public static JulcProjectState getInstance(Project project) {
        return project.getService(JulcProjectState.class);
    }

    public static class State {
        private ConfigType sdkType;
        private String sdkId;
        private ProjectType projectType;

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

        public ProjectType getProjectType() {
            return projectType;
        }

        public void setProjectType(ProjectType projectType) {
            this.projectType = projectType;
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
