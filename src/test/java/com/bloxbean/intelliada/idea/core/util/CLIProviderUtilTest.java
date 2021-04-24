package com.bloxbean.intelliada.idea.core.util;

import junit.framework.TestCase;
import static org.junit.Assert.*;

public class CLIProviderUtilTest extends TestCase {

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
