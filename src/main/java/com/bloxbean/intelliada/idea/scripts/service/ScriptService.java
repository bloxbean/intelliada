package com.bloxbean.intelliada.idea.scripts.service;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.crypto.*;
import com.bloxbean.cardano.client.transaction.spec.script.*;
import com.bloxbean.intelliada.idea.scripts.cache.ScriptCacheService;
import com.bloxbean.intelliada.idea.scripts.exception.ScriptGenerationException;
import com.intellij.openapi.util.text.StringUtil;

import java.util.List;
import java.util.UUID;

public class ScriptService {

    private ScriptCacheService scriptCacheService;

    public ScriptService() {
        this.scriptCacheService = new ScriptCacheService();
    }

    public ScriptInfo generateScriptPubkeyFromAddress(String name, Account account) throws ScriptGenerationException {
        try {
            if (account == null)
                return null;
            byte[] prvKeyBytes = account.privateKeyBytes();
            byte[] pubKeyBytes = account.publicKeyBytes();

            SecretKey sk = SecretKey.create(prvKeyBytes);
            VerificationKey vkey = VerificationKey.create(pubKeyBytes);

            ScriptPubkey scriptPubkey = ScriptPubkey.create(vkey);
            return getScriptInfo(name, ScriptType.sig, scriptPubkey, sk, vkey, account.baseAddress());
        } catch (Exception e) {
            throw new ScriptGenerationException("Error generating script", e);
        }
    }

    public ScriptInfo generateScriptPubkeyFromVkey(String name, String vkey) throws ScriptGenerationException {
        try {
            VerificationKey verificationKey = VerificationKey.create(KeyGenCborUtil.cborToBytes(vkey));
            ScriptPubkey scriptPubkey = ScriptPubkey.create(verificationKey);
            return getScriptInfo(name, ScriptType.sig, scriptPubkey, null, verificationKey, null);
        } catch (Exception e) {
            throw new ScriptGenerationException("Error generating script", e);
        }
    }

    public ScriptInfo generateScriptPubkeyFromSecretKey(String name, String skey) throws ScriptGenerationException {
        try {
            SecretKey secretKey = SecretKey.create(KeyGenCborUtil.cborToBytes(skey));
            VerificationKey verificationKey = KeyGenUtil.getPublicKeyFromPrivateKey(secretKey);
            ScriptPubkey scriptPubkey = ScriptPubkey.create(verificationKey);
            return getScriptInfo(name, ScriptType.sig, scriptPubkey, secretKey, verificationKey, null);
        } catch (Exception e) {
            throw new ScriptGenerationException("Error generating script", e);
        }
    }

    public ScriptInfo generateNewScriptPubkey(String name) throws ScriptGenerationException {
        try {
            Keys keys = KeyGenUtil.generateKey();
            VerificationKey vkey = keys.getVkey();
            SecretKey skey = keys.getSkey();

            ScriptPubkey scriptPubkey = ScriptPubkey.create(vkey);
            return getScriptInfo(name, ScriptType.sig, scriptPubkey, skey, vkey, null);
        } catch (Exception e) {
            throw new ScriptGenerationException("Error generating script", e);
        }
    }

    public ScriptInfo generateCompositeScript(String name, ScriptType type, ScriptType timeLockType, List<NativeScript> nativeScripts,
                                              ScriptGenInputs params) throws ScriptGenerationException {
        if(StringUtil.isEmpty(name)) {
            throw new ScriptGenerationException("Name cannot be empty");
        }

        if(type == null && timeLockType == null) {
            throw new ScriptGenerationException("Script type and Time Lock type, both cannot be null");
        }

        NativeScript newScript = null;

        if(type != null) {
            switch (type) {
                case sig:
                    break;
                case all:
                    ScriptAll scriptAll = new ScriptAll();
                    scriptAll.setScripts(nativeScripts);
                    newScript = scriptAll;
                    break;
                case any:
                    ScriptAny scriptAny = new ScriptAny();
                    scriptAny.setScripts(nativeScripts);
                    newScript = scriptAny;
                    break;
                case atLeast:
                    ScriptAtLeast scriptAtLeast = new ScriptAtLeast(params.getRequired());
                    scriptAtLeast.setScripts(nativeScripts);
                    newScript = scriptAtLeast;
                    break;
//                case after:
//                    RequireTimeAfter requireTimeAfter = new RequireTimeAfter(params.getSlot());
//                    if (nativeScripts != null && nativeScripts.size() > 0) {
//                        ScriptAll sall = new ScriptAll();
//                        nativeScripts.add(0, requireTimeAfter);
//                        sall.addScript(requireTimeAfter);
//                        sall.setScripts(nativeScripts);
//                        newScript = sall;
//                    } else {
//                        newScript = requireTimeAfter;
//                    }
//                    break;
//                case before:
//                    RequireTimeBefore requireTimeBefore = new RequireTimeBefore(params.getSlot());
//                    if (nativeScripts != null && nativeScripts.size() > 0) {
//                        ScriptAll sall = new ScriptAll();
//                        nativeScripts.add(0, requireTimeBefore);
//                        sall.setScripts(nativeScripts);
//                        newScript = sall;
//                    } else {
//                        newScript = requireTimeBefore;
//                    }
//                    break;
                default:
                    break;
            }
        }

        if(timeLockType != null) {
            if(params.getSlot() == null)
                throw new ScriptGenerationException("Slot cannot be null when TimeLock is used.");
            switch (timeLockType) {
                case before:
                    if(newScript != null) {
                        addTimeLockTypeToScript(newScript, new RequireTimeBefore(params.getSlot()));
                    } else {
                        RequireTimeBefore requireTimeBefore = new RequireTimeBefore(params.getSlot());
                        newScript = requireTimeBefore;
                    }
                    break;
                case after:
                    if(newScript != null) {
                        addTimeLockTypeToScript(newScript, new RequireTimeAfter(params.getSlot()));
                    } else {
                        RequireTimeAfter requireTimeAfter = new RequireTimeAfter(params.getSlot());
                        newScript = requireTimeAfter;
                    }
                    break;
                default:
                    break;
            }
        }

        if(newScript == null)
            return null;

        ScriptInfo scriptInfo = ScriptInfo.builder()
                .uuid(UUID.randomUUID().toString())
                .name(name)
                .type(type)
                .script(newScript)
                .build();

        return scriptInfo;
    }

    private void addTimeLockTypeToScript(NativeScript script, NativeScript timeLockScript) {
       if(timeLockScript == null || script == null)
           return;

       if(script instanceof ScriptAll) {
           if(((ScriptAll)script).getScripts() != null)
               ((ScriptAll)script).getScripts().add(0, timeLockScript);
       }
       else if(script instanceof ScriptAny) {
           if(((ScriptAny)script).getScripts() != null)
               ((ScriptAny)script).getScripts().add(0, timeLockScript);
       }
       else if(script instanceof ScriptAtLeast) {
           if(((ScriptAtLeast)script).getScripts() != null)
               ((ScriptAtLeast)script).getScripts().add(0, timeLockScript);
       }
    }

    public List<ScriptInfo> getScripts() {
        return scriptCacheService.getScriptInfos();
    }

    public void addScript(ScriptInfo scriptInfo) {
        if(scriptInfo == null)
            return;

        scriptCacheService.storeScript(scriptInfo);
    }

    public boolean removeScript(ScriptInfo scriptInfo) {
        if(scriptInfo == null)
            return false;

        return scriptCacheService.deleteScript(scriptInfo);
    }

    private ScriptInfo getScriptInfo(String scriptName, ScriptType scriptType, NativeScript script, SecretKey skey, VerificationKey vkey,
                                     String address) {
        ScriptInfo scriptInfo = ScriptInfo.builder()
                .uuid(UUID.randomUUID().toString())
                .name(scriptName)
                .type(scriptType)
                .script(script)
                .skey(skey)
                .vKey(vkey)
                .address(address)
                .build();
        return scriptInfo;
    }
}
