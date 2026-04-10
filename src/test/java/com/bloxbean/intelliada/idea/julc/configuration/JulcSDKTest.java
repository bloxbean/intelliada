package com.bloxbean.intelliada.idea.julc.configuration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JulcSDKTest {

    @Test
    void testDefaultConstructor() {
        JulcSDK sdk = new JulcSDK();
        assertEquals("", sdk.getId());
        assertEquals("", sdk.getName());
        assertEquals("", sdk.getPath());
        assertEquals("", sdk.getVersion());
    }

    @Test
    void testAllArgsConstructor() {
        JulcSDK sdk = new JulcSDK("id1", "My julc", "/usr/local/bin", "0.1.0");
        assertEquals("id1", sdk.getId());
        assertEquals("My julc", sdk.getName());
        assertEquals("/usr/local/bin", sdk.getPath());
        assertEquals("0.1.0", sdk.getVersion());
    }

    @Test
    void testUpdateValues() {
        JulcSDK sdk = new JulcSDK("id1", "Old", "/old/path", "0.1.0");
        JulcSDK updated = new JulcSDK("id2", "New", "/new/path", "0.2.0");

        sdk.updateValues(updated);

        // ID should NOT change
        assertEquals("id1", sdk.getId());
        // Other fields should update
        assertEquals("New", sdk.getName());
        assertEquals("/new/path", sdk.getPath());
        assertEquals("0.2.0", sdk.getVersion());
    }

    @Test
    void testUpdateValuesWithNull() {
        JulcSDK sdk = new JulcSDK("id1", "Name", "/path", "1.0");
        sdk.updateValues(null);
        // Should not change
        assertEquals("Name", sdk.getName());
    }

    @Test
    void testEqualsAndHashCode() {
        JulcSDK sdk1 = new JulcSDK("id1", "SDK1", "/path1", "1.0");
        JulcSDK sdk2 = new JulcSDK("id1", "SDK2", "/path2", "2.0");
        JulcSDK sdk3 = new JulcSDK("id2", "SDK1", "/path1", "1.0");

        // Same ID -> equal
        assertEquals(sdk1, sdk2);
        assertEquals(sdk1.hashCode(), sdk2.hashCode());

        // Different ID -> not equal
        assertNotEquals(sdk1, sdk3);
    }

    @Test
    void testToString() {
        JulcSDK sdk = new JulcSDK("id1", "My julc SDK", "/path", "1.0");
        assertEquals("My julc SDK", sdk.toString());
    }

    @Test
    void testGetJulcCommand() {
        JulcSDK sdk = new JulcSDK("id1", "julc", "/usr/local/bin", "0.1.0");
        List<String> cmd = sdk.getJulcCommand();

        assertNotNull(cmd);
        assertEquals(1, cmd.size());
        assertTrue(cmd.get(0).startsWith("/usr/local/bin"));
        assertTrue(cmd.get(0).contains("julc"));
    }
}
