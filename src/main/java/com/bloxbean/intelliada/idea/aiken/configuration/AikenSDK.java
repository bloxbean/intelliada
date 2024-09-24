package com.bloxbean.intelliada.idea.aiken.configuration;

import com.intellij.openapi.util.SystemInfo;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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

    public List<String> getAikenCommand() {
        List<String> cmd = new ArrayList<>();
        cmd.add(getPath() + File.separator + getAikenExecutable());

        return cmd;
    }

    private String getAikenExecutable() {
        String aikenCmd = "aiken";
        if(SystemInfo.isWindows)
            aikenCmd = "aiken.exe";

        return aikenCmd;
    }
}
