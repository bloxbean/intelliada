package com.bloxbean.intelliada.idea.julc.configuration;

import com.intellij.openapi.util.SystemInfo;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@AllArgsConstructor
public class JulcSDK {
    private String id;
    private String name;
    private String path;
    private String version;

    public JulcSDK() {
        id = "";
        name = "";
        path = "";
        version = "";
    }

    public void updateValues(JulcSDK sdk) {
        if (sdk == null) return;
        this.setName(sdk.getName());
        this.setPath(sdk.getPath());
        this.setVersion(sdk.getVersion());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JulcSDK sdk = (JulcSDK) o;
        return id.equals(sdk.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public String toString() {
        return name;
    }

    public List<String> getJulcCommand() {
        List<String> cmd = new ArrayList<>();
        cmd.add(getPath() + File.separator + getJulcExecutable());
        return cmd;
    }

    private String getJulcExecutable() {
        String julcCmd = "julc";
        if (SystemInfo.isWindows)
            julcCmd = "julc.exe";
        return julcCmd;
    }
}
