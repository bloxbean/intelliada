package com.bloxbean.intelliada.idea.configuration.model;

import java.util.Objects;

public class CLIProvider {
    private String id;
    private String name;
    private String home;
    private String version;

    public CLIProvider() {
        id = "";
        name = "";
    }

    public CLIProvider(String id, String name, String home, String version) {
        this.id = id;
        this.name = name;
        this.home = home;
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHome() {
        return home;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void updateValues(CLIProvider provider) { //Update everything except id
        if (provider == null) return;

        this.setName(provider.getName());
        this.setHome(provider.getHome());
        this.setVersion(provider.getVersion());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CLIProvider localSDK = (CLIProvider) o;
        return id.equals(localSDK.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public String toString() {
        return name;
    }

    public String print() {
        return "CLIProvider{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", home='" + home + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}
