package com.bloxbean.intelliada.idea.aiken.module.project;

public class ProjectCreateSettings {
    private String owner;

    public ProjectCreateSettings(String owner) {
        this.owner = owner;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
