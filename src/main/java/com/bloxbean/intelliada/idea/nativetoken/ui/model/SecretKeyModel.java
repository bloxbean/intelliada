package com.bloxbean.intelliada.idea.nativetoken.ui.model;

import com.bloxbean.cardano.client.crypto.SecretKey;

public class SecretKeyModel {
    private SecretKey secretKey;

    public SecretKeyModel(SecretKey key) {
        this.secretKey = key;
    }

    public SecretKey getSecretKey() {
        return this.secretKey;
    }

    @Override
    public String toString() {
        return "sk (" + this.secretKey.getCborHex() + ")";
    }
}
