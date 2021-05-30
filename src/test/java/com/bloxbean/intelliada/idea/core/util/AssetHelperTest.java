package com.bloxbean.intelliada.idea.core.util;

import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class AssetHelperTest {

    @Test
    public void testCalculateFingerPrint1() {
        String policyId = "7eae28af2208be856f7a119668ae52a49b73725e326dc16579dcc373";
        String assetName = "";

        String fingerPrint =
                AssetHelper.calculateFingerPrint(policyId, HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8)));

        assertEquals("asset1rjklcrnsdzqp65wjgrg55sy9723kw09mlgvlc3", fingerPrint);
    }

    @Test
    public void testCalculateFingerPrint2() {
        String policyId = "7eae28af2208be856f7a119668ae52a49b73725e326dc16579dcc37e";
        String assetName = "";

        String fingerPrint =
                AssetHelper.calculateFingerPrint(policyId, HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8)));

        assertEquals("asset1nl0puwxmhas8fawxp8nx4e2q3wekg969n2auw3", fingerPrint);
    }

    @Test
    public void testCalculateFingerPrint3() {
        String policyId = "1e349c9bdea19fd6c147626a5260bc44b71635f398b67c59881df209";
        String assetName = "";

        String fingerPrint =
                AssetHelper.calculateFingerPrint(policyId, HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8)));

        assertEquals("asset1uyuxku60yqe57nusqzjx38aan3f2wq6s93f6ea", fingerPrint);
    }

    @Test
    public void testCalculateFingerPrint4() {
        String policyId = "7eae28af2208be856f7a119668ae52a49b73725e326dc16579dcc373";
        String assetName = "504154415445";

        String fingerPrint =
                AssetHelper.calculateFingerPrint(policyId, assetName);

        assertEquals("asset13n25uv0yaf5kus35fm2k86cqy60z58d9xmde92", fingerPrint);
    }

    @Test
    public void testCalculateFingerPrint5() {
        String policyId = "1e349c9bdea19fd6c147626a5260bc44b71635f398b67c59881df209";
        String assetName = "504154415445";

        String fingerPrint =
                AssetHelper.calculateFingerPrint(policyId, assetName);

        assertEquals("asset1hv4p5tv2a837mzqrst04d0dcptdjmluqvdx9k3", fingerPrint);
    }

    @Test
    public void testCalculateFingerPrint6() {
        String policyId = "1e349c9bdea19fd6c147626a5260bc44b71635f398b67c59881df209";
        String assetName = "7eae28af2208be856f7a119668ae52a49b73725e326dc16579dcc373";

        String fingerPrint =
                AssetHelper.calculateFingerPrint(policyId, assetName);

        assertEquals("asset1aqrdypg669jgazruv5ah07nuyqe0wxjhe2el6f", fingerPrint);
    }

    @Test
    public void testCalculateFingerPrint7() {
        String policyId = "7eae28af2208be856f7a119668ae52a49b73725e326dc16579dcc373";
        String assetName = "1e349c9bdea19fd6c147626a5260bc44b71635f398b67c59881df209";

        String fingerPrint =
                AssetHelper.calculateFingerPrint(policyId, assetName);

        assertEquals("asset17jd78wukhtrnmjh3fngzasxm8rck0l2r4hhyyt", fingerPrint);
    }

    @Test
    public void testCalculateFingerPrint8() {
        String policyId = "7eae28af2208be856f7a119668ae52a49b73725e326dc16579dcc373";
        String assetName = "0000000000000000000000000000000000000000000000000000000000000000";

        String fingerPrint =
                AssetHelper.calculateFingerPrint(policyId, assetName);

        assertEquals("asset1pkpwyknlvul7az0xx8czhl60pyel45rpje4z8w", fingerPrint);
    }
}
