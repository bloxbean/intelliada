package com.bloxbean.intelliada.idea.scripts.cache;

import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;

import java.util.ArrayList;
import java.util.List;

public class ScriptCache {
    private List<ScriptInfo> scriptInfos;

    public ScriptCache() {
        scriptInfos = new ArrayList<>();
    }

    public List<ScriptInfo> getScriptInfos() {
        return scriptInfos;
    }

    public void addScriptInfo(ScriptInfo scriptInfo) {
        this.scriptInfos.add(scriptInfo);
    }

    public boolean deleteScriptInfo(ScriptInfo scriptInfo) {
        if (scriptInfos.contains(scriptInfo)) {
            scriptInfos.remove(scriptInfo);
            return true;
        } else {
            return false;
        }
    }

    public ScriptInfo getScriptInfoByUuid(String uuid) {
        for (ScriptInfo sc : scriptInfos) {
            if (sc.getUuid().equals(uuid)) {
                return sc;
            }
        }
        return null;
    }

    public boolean updateScriptName(String uuid, String name) {
        ScriptInfo scriptInfo = getScriptInfoByUuid(uuid);
        if (scriptInfo != null) {
            scriptInfo.setName(name);
            return true;
        } else {
            return false;
        }
    }

}
