package com.bloxbean.intelliada.idea.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CLIProviderUtilTest {

    @Test
    public void testDaedalusMacMainnetPath() {
        String path = "/Applications/Daedalus Mainnet";

        boolean result = CLIProviderUtil.isMacDaedalusHome(path);
        assertTrue(result);
    }

    public void testDaedalusMacMainnetPathWithSlash() {
        String path = "/Applications/Daedalus Mainnet/";

        boolean result = CLIProviderUtil.isMacDaedalusHome(path);
        assertTrue(result);
    }

    public void testDaedalusMacTestnetPath() {
        String path = "/Applications/Daedalus Testnet";

        boolean result = CLIProviderUtil.isMacDaedalusHome(path);
        assertTrue(result);
    }

    public void testDaedalusMacTestnetPathWithSlash() {
        String path = "/Applications/Daedalus Testnet/";

        boolean result = CLIProviderUtil.isMacDaedalusHome(path);
        assertTrue(result);
    }

    public void testDaedalusMacInvalidPath() {
        String path = "/Applications/xyz";

        boolean result = CLIProviderUtil.isMacDaedalusHome(path);
        assertFalse(result);
    }

    public void testDaedalusMacInvalidPathWithSlash() {
        String path = "/Applications/xyz/";

        boolean result = CLIProviderUtil.isMacDaedalusHome(path);
        assertFalse(result);
    }

}
