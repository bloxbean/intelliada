package com.bloxbean.intelliada.idea.joust;

import org.junit.Test;

import static org.junit.Assert.*;

/** M1-DES-18, M1-CDX-25: the milestone gate. With joust.requireTools set, a missing tool fails; without it, it skips visibly. */
public class JoustTestToolsTest {

    @Test
    public void theGate() {
        String before = System.getProperty("joust.requireTools");
        try {
            for (String on : new String[]{"1", "true", "TRUE"}) {
                System.setProperty("joust.requireTools", on);
                try {
                    JoustTestTools.env("JOUST_SURELY_UNSET_VARIABLE", "gate");
                    fail("required tools were not required with " + on);
                } catch (AssertionError expected) {
                    assertTrue(expected.getMessage().contains("JOUST_SURELY_UNSET_VARIABLE"));
                }
            }
            System.setProperty("joust.requireTools", "false");
            assertNull(JoustTestTools.env("JOUST_SURELY_UNSET_VARIABLE", "gate"));
        } finally {
            if (before == null) System.clearProperty("joust.requireTools");
            else System.setProperty("joust.requireTools", before);
        }
    }
}
