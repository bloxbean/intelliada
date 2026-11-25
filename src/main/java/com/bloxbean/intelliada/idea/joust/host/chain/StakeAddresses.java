package com.bloxbean.intelliada.idea.joust.host.chain;

import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.util.HexUtil;

/** Reward (stake) addresses for a 28-byte credential (CIP-19 header 0xE0 key / 0xF0 script, OR the network id). */
public final class StakeAddresses {

    private StakeAddresses() {
    }

    public static String reward(String credentialHex, boolean script, int networkId) {
        byte[] cred = HexUtil.decodeHexString(credentialHex);
        if (cred.length != 28) throw new IllegalArgumentException("a stake credential is 28 bytes");
        byte[] b = new byte[29];
        b[0] = (byte) ((script ? 0xF0 : 0xE0) | (networkId & 0x0F));
        System.arraycopy(cred, 0, b, 1, 28);
        return Bech32.encode(b, networkId == 1 ? "stake" : "stake_test");
    }
}
