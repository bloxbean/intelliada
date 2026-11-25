package com.bloxbean.intelliada.idea.joust.host;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/** ADR-005 I22: protocol 1's frame rules, on the client side. */
public class HostFramesTest {

    private static HostFrames.Reader reader(String s) {
        return new HostFrames.Reader(new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1)));
    }

    private static String refusal(String s) {
        try {
            reader(s).next();
            return null;
        } catch (HostFrames.Violation v) {
            return v.getMessage();
        } catch (Exception e) {
            return e.toString();
        }
    }

    @Test
    public void aWellFormedFrameIsRead() throws Exception {
        HostFrames.Reader r = reader("Content-Length: 2\r\n\r\n{}content-length:  4 \r\nContent-Type: x\r\n\r\nnull");
        assertEquals("{}", new String(r.next(), StandardCharsets.UTF_8));
        assertEquals("null", new String(r.next(), StandardCharsets.UTF_8));
        assertNull("end of input between frames", r.next());
    }

    @Test
    public void malformedHeadersAreViolations() {
        assertNotNull(refusal("Content-Length: 2\r\nContent-Length: 2\r\n\r\n{}"));
        assertNotNull(refusal("Content-Length: -2\r\n\r\n{}"));
        assertNotNull(refusal("Content-Length: 2\r\nX-Other: 1\r\n\r\n{}"));
        assertNotNull(refusal("Content-Type: a\r\n\r\n{}"));
        assertNotNull(refusal("stray output\r\n\r\n"));
        assertNotNull(refusal("Content-Length: 2é\r\n\r\n{}"));
        assertNotNull(refusal("Content-Length: " + "1".repeat(11) + "\r\n\r\n"));
        assertNotNull("header over 1 KiB", refusal("Content-Length: 2\r\nContent-Type: " + "a".repeat(1100) + "\r\n\r\n{}"));
    }

    @Test
    public void oversizedAndTruncatedFramesAreViolations() {
        assertTrue(refusal("Content-Length: 4194305\r\n\r\n").contains("cap"));
        assertNotNull(refusal("Content-Length: 10\r\n\r\n{}"));
        assertNotNull(refusal("Content-Len"));
    }

    @Test
    public void theWriterFramesBodies() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new HostFrames.Writer(out).write("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        assertEquals("Content-Length: 7\r\n\r\n{\"a\":1}", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void bodiesMustBeStrictUtf8Json() {
        for (String bad : new String[]{"{\"a\":1}x", "{\"a\":1,\"a\":2}", "{'a':1}", "[1,]", "", "{\"a\":NaN}"}) {
            try {
                HostJson.parse(bad.getBytes(StandardCharsets.UTF_8));
                fail("accepted " + bad);
            } catch (HostJson.Malformed expected) {
                // refused
            }
        }
        try {
            HostJson.parse(new byte[]{'"', (byte) 0xc3, '"'});
            fail("accepted invalid UTF-8");
        } catch (HostJson.Malformed expected) {
            // refused
        }
        try {
            HostJson.parse("{}".getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
            fail("accepted BOM-less UTF-16 (M31A-CDX-9)");
        } catch (HostJson.Malformed expected) {
            // refused
        }
        String deep = "[".repeat(1001) + "]".repeat(1001);
        try {
            HostJson.parse(deep.getBytes(StandardCharsets.UTF_8));
            fail("accepted nesting deeper than 1,000");
        } catch (HostJson.Malformed expected) {
            // refused
        }
    }
}
