package com.bloxbean.intelliada.idea.core.util;

import com.bloxbean.cardano.client.common.Bech32;
import com.bloxbean.cardano.client.util.HexUtil;
import org.bouncycastle.crypto.digests.Blake2bDigest;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

//TODO remove this class, once the functionality is available in cardano-client-lib
public class AssetHelper {

    /**
     * Calculate fingerprint from policy id and asset name (CIP-0014)
     * @param policyIdHex  Policy id
     * @param assetNameHex Asset name in hex
     * @return
     */
    public static String calculateFingerPrint(String policyIdHex, String assetNameHex) {
        String assetId = policyIdHex + assetNameHex;
        byte[] hashBytes = blake2bHash160(HexUtil.decodeHexString(assetId));

        List<Integer> words = convertBits(hashBytes, 8, 5, false);
        byte[] bytes = new byte[words.size()];

        for(int i=0; i < words.size(); i++) {
            bytes[i] = words.get(i).byteValue();
        }

        String hrp = "asset";

        return Bech32.encode(hrp, bytes);
    }

    private static List<Integer> convertBits(byte[] data, int fromWidth, int toWidth, boolean pad) {
        int acc = 0;
        int bits = 0;
        int maxv = (1 << toWidth) - 1;
        List<Integer> ret = new ArrayList<>();

        for (int i = 0; i < data.length; i++) {
            int value = data[i] & 0xff;
            if (value < 0 || value >> fromWidth != 0) {
                return null;
            }
            acc = (acc << fromWidth) | value;
            bits += fromWidth;
            while (bits >= toWidth) {
                bits -= toWidth;
                ret.add((acc >> bits) & maxv);
            }
        }

        if (pad) {
            if (bits > 0) {
                ret.add((acc << (toWidth - bits)) & maxv);
            } else if (bits >= fromWidth || ((acc << (toWidth - bits)) & maxv) != 0) {
                return null;
            }
        }

        return ret;
    }

    private static byte[] blake2bHash160(byte[] in) {
        final Blake2bDigest hash = new Blake2bDigest(null, 20, null, null);
        hash.update(in, 0, in.length);
        final byte[] out = new byte[hash.getDigestSize()];
        hash.doFinal(out, 0);
        return out;
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        String fp = AssetHelper.calculateFingerPrint("7eae28af2208be856f7a119668ae52a49b73725e326dc16579dcc373","");
        System.out.println(fp);
    }
}
