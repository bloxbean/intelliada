/*
 * Copyright (c) 2021 BloxBean Project
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.bloxbean.intelliada.idea.scripts.cache;

import com.bloxbean.cardano.client.transaction.spec.script.*;
import com.bloxbean.intelliada.idea.util.FileEncrypterDecrypter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;

import javax.crypto.SecretKey;
import java.io.*;
import java.util.Properties;

public class GlobalScriptCache {
    private final static Logger log = Logger.getInstance(GlobalScriptCache.class);

    public static String SCRIPTS_CACHE = ".cardano.scripts.conf";

    //props in key file
    private final static String SECRET_KEY = "secret-key";
    private final static String PROTECTION_MODE = "protection-mode";

    private final String targetFolder;

    private ObjectMapper objectMapper;

    public GlobalScriptCache(String targetFolder) {
        this.targetFolder = targetFolder;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerSubtypes(new NamedType(ScriptPubkey.class, "ScriptPubkey"));
        this.objectMapper.registerSubtypes(new NamedType(ScriptAll.class, "ScriptAll"));
        this.objectMapper.registerSubtypes(new NamedType(ScriptAny.class, "ScriptAny"));
        this.objectMapper.registerSubtypes(new NamedType(ScriptAtLeast.class, "ScriptAtLeast"));
        this.objectMapper.registerSubtypes(new NamedType(RequireTimeAfter.class, "RequireTimeAfter"));
        this.objectMapper.registerSubtypes(new NamedType(RequireTimeBefore.class, "RequireTimeBefore"));

    }

    public void setScriptCache(ScriptCache scriptCache) {
        File file = getScriptsCacheFile();

        SecretKey secretKey = getSecretKeyFromFile();
        if(secretKey == null) { //Create a secret key
            secretKey = generateNewSecretAndStore();
        }

        if(secretKey == null) { //If secret is still null. write cache in plain text
            getScriptCacheKeyFile().delete();
            writeScriptCacheToFile(scriptCache, file);
        } else {
            try {
                String jsonContent = writeScriptCacheToJson(scriptCache);

                FileEncrypterDecrypter fileEncrypterDecrypter = new FileEncrypterDecrypter(secretKey);
                fileEncrypterDecrypter.encrypt(jsonContent, getScriptsCacheFile().getAbsolutePath());
            } catch (Exception e) {
                log.debug("Error writing encrypted script cache content.", e);
                //Let's try to write plain content and delete secret file as fallback
                getScriptCacheKeyFile().delete();
                writeScriptCacheToFile(scriptCache, file);
            }
        }
    }

    public ScriptCache getScriptCache() {
        File file = getScriptsCacheFile();
        File keyFile = getScriptCacheKeyFile();

        if(!file.exists())
            return new ScriptCache();

        if(keyFile.exists()) {
            SecretKey secretKey = getSecretKeyFromFile();
            if(secretKey == null) {
                return new ScriptCache();
            } else {
                try {
                    FileEncrypterDecrypter fileEncrypterDecrypter = new FileEncrypterDecrypter(secretKey);
                    String encContent = fileEncrypterDecrypter.decrypt(file);
                    return readScriptCacheFromJson(encContent);
                } catch (Exception e) {
                    log.warn("Script cache could not be read", e);
                    return new ScriptCache();
                }
            }
        } else { //If not encrypted. Just to support older version and migration
            ScriptCache scriptCache = readScriptCacheFromFile(file);

            return scriptCache;
        }
    }

    private ScriptCache readScriptCacheFromJson(String content) {
        ScriptCache scriptCache = null;

        try {
            scriptCache = objectMapper.readValue(content, ScriptCache.class);
        } catch (Exception e) {
            scriptCache = new ScriptCache();
            log.warn("Could not read from script cache: " + e.getMessage());
            if(log.isDebugEnabled()) {
                log.debug("Could not read from script cache", e);
            }
        }
        return scriptCache;
    }

    private ScriptCache readScriptCacheFromFile(File file) {
        ScriptCache scriptCache = null;

        try {
            scriptCache = objectMapper.readValue(file, ScriptCache.class);
        } catch (Exception e) {
            scriptCache = new ScriptCache();
            //e.printStackTrace();
            log.warn("Could not read from script cache: " + e.getMessage());
            if(log.isDebugEnabled()) {
                log.debug("Could not read from script cache", e);
            }
        }
        return scriptCache;
    }

    private String writeScriptCacheToJson(ScriptCache scriptCache) {
        try {
            StringWriter writer = new StringWriter();
            objectMapper.writeValue(writer, scriptCache);
            return writer.toString();
        } catch (Exception e) {
            log.warn("Could not convert script cache to json", e);
            if (log.isDebugEnabled()) {
                log.debug("Could not convert script cache to json", e);
            }
        }

        return null;
    }

    private void writeScriptCacheToFile(ScriptCache scriptCache, File file) {
        try {
            objectMapper.writeValue(file, scriptCache);
        } catch (Exception e) {
            log.warn("Could not write to script cache", e);
            if (log.isDebugEnabled()) {
                log.debug("Could not write to script cache", e);
            }
        }
    }

    public void clearScriptCache() {
        File file = getScriptsCacheFile();
        file.delete();

        File keyFile = getScriptCacheKeyFile();
        keyFile.delete();
    }

    private SecretKey getSecretKeyFromFile() {
        try {
            File keyFile = getScriptCacheKeyFile();
            if (!keyFile.exists()) {
                return null;
            }

            Properties props = readKeyProperties(keyFile);
            String secretKey = (String) props.get(SECRET_KEY);

            if (!StringUtil.isEmpty(secretKey)) {
                return FileEncrypterDecrypter.getSecretKeyFromEncodedKey(secretKey);
            } else {
                return null;
            }
        } catch (Exception e) {
            log.warn("Invalid secret key.");
            log.debug("Invalid key content", e);
            return null;
        }
    }

    private SecretKey generateNewSecretAndStore() {
        String key = null;
        try {
            key = FileEncrypterDecrypter.generateKey();
        } catch (Exception e) {
            log.debug("Error generating secret key. Something is really wrong.", e);
            log.warn("Secret generation failed. Keys will be stored in plain text");
            return null;
        }

        if(key == null) {
            return null;
        }

        Properties props = new Properties();
        props.put(SECRET_KEY, key);
        props.put(PROTECTION_MODE, "none");

        File keyFile = getScriptCacheKeyFile();
        writeKeyProperties(keyFile, props);

        return FileEncrypterDecrypter.getSecretKeyFromEncodedKey(key);
    }

    private File getScriptsCacheFile() {
        return new File(targetFolder, SCRIPTS_CACHE);
    }

    private File getScriptCacheKeyFile() {
        return new File(targetFolder, SCRIPTS_CACHE + ".key");
    }

    private void writeKeyProperties(File file, Properties props) {
        Properties prop = new Properties();
        OutputStream output = null;

        try {
            output = new FileOutputStream(file);
            props.store(output, null);

        } catch (Exception io) {
            if(log.isDebugEnabled()) {
                log.warn(io);
            }
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    //e.printStackTrace();
                }
            }
        }
    }

    private Properties readKeyProperties(File file) {
        InputStream input = null;

        try {

            input = new FileInputStream(file);

            Properties properties = new Properties();
            properties.load(input);

            return properties;

        } catch (Exception io) {
            if(log.isDebugEnabled()) {
                log.warn(io);
            }
            return new Properties();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
//                    e.printStackTrace();
                }
            }

        }
    }
}

