package com.bloxbean.intelliada.idea.aiken.configuration;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Objects;

@Data
@AllArgsConstructor
public class AikenSDK {
    private String id;
    private String name;
    private String path;
    private String version;

    public AikenSDK() {
        id = "";
        name = "";
        path = "";
        version = "";
    }

    public void updateValues(AikenSDK aikenSDK) { //Update everything except id
        if(aikenSDK == null) return;

        this.setName(aikenSDK.getName());
        this.setPath(aikenSDK.getPath());
        this.setVersion(aikenSDK.getVersion());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AikenSDK sdk = (AikenSDK) o;
        return id.equals(sdk.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public String toString() {
        return name;
    }
}
