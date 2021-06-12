package com.bloxbean.intelliada.idea.scripts.service;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.crypto.*;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptType;
import com.bloxbean.intelliada.idea.scripts.cache.ScriptCacheService;
import com.bloxbean.intelliada.idea.scripts.exception.ScriptGenerationException;

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
