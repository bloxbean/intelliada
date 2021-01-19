package com.bloxbean.intelliada.idea.configuration.service;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

@State(
        name = "com.bloxbean.cardano.intelliada.CardanoProjectState",
        reloadable = true,
        storages = {@Storage("cardano-project.xml")}
)
public class CardanoProjectState implements PersistentStateComponent<CardanoProjectState.State> {
    private static final Logger LOG = Logger.getInstance(CardanoProjectState.class);

    public static CardanoProjectState getInstance(Project project) {
        return ServiceManager.getService(project, CardanoProjectState.class);
    }

    public static class State {
        private String serviceId;

        public String getServiceId() {
            return serviceId;
        }
        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
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
