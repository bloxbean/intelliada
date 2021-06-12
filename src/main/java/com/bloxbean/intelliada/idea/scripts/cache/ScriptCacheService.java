package com.bloxbean.intelliada.idea.scripts.cache;

import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;
import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class ScriptCacheService {
    private final static Logger log = Logger.getInstance(ScriptCacheService.class);

    public ScriptCacheService() {

    }

    public List<ScriptInfo> getScriptInfos() {
//        if(!isContinue())
//            return Collections.EMPTY_LIST;

        try {
            GlobalScriptCache globalCache = new GlobalScriptCache(getScriptInfoCacheFolder());
            ScriptCache scriptCache = globalCache.getScriptCache();

            if(scriptCache != null) {
                List<ScriptInfo> scriptInfos = scriptCache.getScriptInfos();
                return scriptInfos;
            } else {
                return Collections.EMPTY_LIST;
            }
        } catch (Exception e) {
            return Collections.EMPTY_LIST;
        }
    }

    public void storeScript(ScriptInfo scriptInfo) {

        GlobalScriptCache globalCache = new GlobalScriptCache(getScriptInfoCacheFolder());
        ScriptCache scriptCache = globalCache.getScriptCache();

        scriptCache.addScriptInfo(scriptInfo);
        globalCache.setScriptCache(scriptCache);
    }

    public boolean deleteScript(ScriptInfo scriptInfo) {
        GlobalScriptCache globalCache = new GlobalScriptCache(getScriptInfoCacheFolder());
        ScriptCache scriptCache = globalCache.getScriptCache();

        if(scriptCache.deleteScriptInfo(scriptInfo)) {
            globalCache.setScriptCache(scriptCache);
            return true;
        }

        return false;
    }


    public void clearCache() {
        GlobalScriptCache globalCache = new GlobalScriptCache(getScriptInfoCacheFolder());
        globalCache.clearScriptCache();
    }

    protected String getScriptInfoCacheFolder() {
        String home = System.getProperty("user.home");

        File cacheFolder = new File(home);

        if(!cacheFolder.canWrite()) {
            //Let's find temp folder
            String temp = System.getProperty("java.io.tmpdir");
            File tempFolder = new File(temp);

            if(!tempFolder.canWrite()) {
                log.warn("Unable to find a writable folder to keep script cache");
                return null;
            } else {
                cacheFolder = tempFolder;
            }
        }

        File intelliAdaFolder = new File(cacheFolder, ".intelliada");
        if(!intelliAdaFolder.exists()) {
            intelliAdaFolder.mkdirs();
        }

        return intelliAdaFolder.getAbsolutePath();
    }

    public boolean updateScriptName(String uuid, String scriptName) {
        GlobalScriptCache globalCache = new GlobalScriptCache(getScriptInfoCacheFolder());
        ScriptCache scriptCache = globalCache.getScriptCache();

        boolean successful = scriptCache.updateScriptName(uuid, scriptName);
        if(successful) {
            globalCache.setScriptCache(scriptCache);
        }

        return successful;
    }
}
