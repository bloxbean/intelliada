package com.bloxbean.intelliada.idea.joust.tx;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

/**
 * ADR-005 D13 rule 1, D17 (M3.3b1): IntelliAda's structural transaction reader against the host's own account of each
 * body in the kit's vectors (independent evidence: the JVM host's {@code txHash}, validity bounds and plan, and the
 * publication's prerequisite id), and against malformed encodings.
 */
public class TxCborTest {

    public static List<JsonNode> vectors(String test) throws Exception {
        Path jar = null;
        for (String p : System.getProperty("joust.conformance.classpath", "").split(File.pathSeparator)) {
            if (p.endsWith("-conformance.jar")) jar = Path.of(p);
        }
        if (jar == null) {
            if (JoustTestTools.required()) throw new AssertionError(test + " needs the JOUST conformance kit");
            System.out.println("JOUST-SKIPPED " + test + ": no conformance kit");
            return null;
        }
        List<JsonNode> out = new ArrayList<>();
        try (ZipFile z = new ZipFile(jar.toFile())) {
            for (ZipEntry e : Collections.list(z.entries())) {
                if (e.getName().startsWith("verifier/") && e.getName().endsWith(".json")) {
                    JsonNode v = HostJson.MAPPER.readTree(z.getInputStream(e));
                    ((com.fasterxml.jackson.databind.node.ObjectNode) v).put("$name", e.getName());
                    out.add(v);
                }
            }
        }
        return out;
    }

    private static Set<String> strings(JsonNode a) {
        Set<String> s = new HashSet<>();
        for (JsonNode n : a) s.add(n.textValue());
        return s;
    }

    @Test
    public void everyAcceptedBodyReadsAsTheHostBuiltIt() throws Exception {
        List<JsonNode> vs = vectors("everyAcceptedBodyReadsAsTheHostBuiltIt");
        if (vs == null) return;
        assertEquals(333, vs.size());
        int accepted = 0, publications = 0;
        for (JsonNode v : vs) {
            String name = v.path("$name").textValue();
            JsonNode p = v.path("result").path("prepared");
            if (!p.isObject()) continue;
            TxCbor.Tx tx;
            try {
                tx = TxCbor.transaction(p.path("cbor").textValue());
            } catch (TxCbor.Malformed m) {
                assertFalse(name + " is accepted by the checker yet unreadable: " + m.getMessage(), v.path("violations").isEmpty());
                continue;
            }
            TxCbor.Body b;
            try {
                b = TxCbor.body(tx);
            } catch (TxCbor.Malformed m) {
                assertFalse(name + " is accepted by the checker yet its body is unreadable: " + m.getMessage(), v.path("violations").isEmpty());
                continue;
            }
            if (!v.path("violations").isEmpty()) continue;
            accepted++;
            JsonNode plan = p.path("plan");
            assertEquals(name, p.path("txHash").textValue(), b.txHash());
            assertEquals(name, p.path("validTo").asLong(), (long) b.validTo());
            assertEquals(name, p.path("validFrom").isNull() ? null : p.path("validFrom").asLong(), b.validFrom());
            assertEquals(name, strings(plan.path("inputs")), b.inputs());
            assertEquals(name, strings(plan.path("collateral")), b.collateral());
            assertEquals(name, strings(plan.path("referenceInputs")), b.referenceInputs());
            assertEquals(name, strings(plan.path("requiredSigners")), new HashSet<>(b.requiredSigners()));
            String prerequisite = v.path("request").path("prerequisite").textValue();
            if (v.path("method").textValue().equals("publish") && prerequisite != null) {
                publications++;
                String[] id = prerequisite.split(":", 2);
                Set<String> want = id[0].equals("verifier")
                        ? Set.of("referenceScript:" + id[1], "stakeRegistration:" + id[1]) : Set.of(prerequisite);
                assertEquals(name + " publishes exactly its prerequisite", want, b.publishes());
            } else {
                assertTrue(name + " publishes nothing: " + b.publishes(), b.publishes().isEmpty());
            }
        }
        // Every vector with a prepared body and no violation (the checker's 42 accepted ones among them).
        assertEquals("the kit's vectors without a violation", 62, accepted);
        assertTrue("publications were checked: " + publications, publications >= 3);
    }

    @Test
    public void theAcceptedEncodingVariantsKeepTheirSpans() throws Exception {
        List<JsonNode> vs = vectors("theAcceptedEncodingVariantsKeepTheirSpans");
        if (vs == null) return;
        int seen = 0;
        for (JsonNode v : vs) {
            String name = v.path("$name").textValue();
            if (!name.matches("verifier/accept-(indefinite-body|nonminimal-integers|untagged-sets)\\.json")) continue;
            seen++;
            byte[] bytes = HexFormat.of().parseHex(v.path("result").path("prepared").path("cbor").textValue());
            TxCbor.Tx tx = TxCbor.transaction(bytes);
            // The four spans tile the transaction after the header (and before a break): nothing is lost or re-encoded.
            assertEquals(name, tx.header().end(), tx.body().start());
            assertEquals(name, tx.body().end(), tx.witnesses().start());
            assertEquals(name, tx.witnesses().end(), tx.isValid().start());
            assertEquals(name, tx.isValid().end(), tx.auxiliary().start());
            assertEquals(name, tx.indefinite() ? bytes.length - 1 : bytes.length, tx.auxiliary().end());
            assertEquals(name, v.path("result").path("prepared").path("txHash").textValue(), tx.txHash());
        }
        assertEquals(3, seen);
    }

    // ---- synthetic encodings ---------------------------------------------------------------------------------------

    private static final String TXID = "11".repeat(32);
    /** A minimal body {0: [[txid, 0]], 3: 100}, an empty witness set, true, null. */
    private static String tx(String body) {
        return "84" + body + "a0" + "f5" + "f6";
    }

    private static String body() {
        return "a2" + "00" + "81" + "82" + "5820" + TXID + "00" + "03" + "1864";
    }

    @Test
    public void aMinimalBodyIsRead() throws Exception {
        TxCbor.Body b = TxCbor.body(TxCbor.transaction(tx(body())));
        assertEquals(Set.of(TXID + "#0"), b.inputs());
        assertEquals(100L, (long) b.validTo());
        assertNull(b.validFrom());
    }

    @Test
    public void malformedTransactionsRefuse() {
        String[] bad = {
                tx(body()) + "00",                                                // trailing bytes
                "83" + body() + "a0" + "f5",                                      // three elements
                "85" + body() + "a0" + "f5" + "f6" + "f6",                        // five elements
                tx("a2" + "00" + "81" + "82" + "5820" + TXID + "00" + "00" + "80"), // a repeated body key
                tx("a1" + "03" + "1864"),                                          // no inputs
                tx("a2" + "00" + "81" + "82" + "581f" + "11".repeat(31) + "00" + "03" + "1864"), // a 31-byte txid
                tx("a2" + "00" + "82" + "82" + "5820" + TXID + "00" + "82" + "5820" + TXID + "00" + "03" + "1864"), // a repeated input
                "84" + body() + "a0" + "f4",                                      // ends early
                "84" + body() + "a0" + "00" + "f6",                               // is_valid not a boolean
                "84" + "80" + "a0" + "f5" + "f6",                                 // the body is not a map
                "",
                "zz",
                tx("a2" + "00" + "81" + "82" + "5820" + TXID + "00" + "03" + "1c"), // reserved additional information
        };
        for (String b : bad) {
            try {
                TxCbor.body(TxCbor.transaction(b));
                fail("refused: " + b);
            } catch (TxCbor.Malformed expected) {
                // refused
            }
        }
    }

    @Test
    public void nestingIsBounded() {
        // An unknown body key holding 700 nested arrays.
        String deep = "81".repeat(700) + "00";
        try {
            TxCbor.body(TxCbor.transaction(tx("a3" + "00" + "81" + "82" + "5820" + TXID + "00" + "03" + "1864" + "1863" + deep)));
            fail("too deep");
        } catch (TxCbor.Malformed expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("deep"));
        }
    }

    @Test
    public void indefiniteForms() throws Exception {
        // An indefinite top array, an indefinite body map, a tagged set, and an indefinite input pair.
        String body = "bf" + "00" + "d90102" + "81" + "9f" + "5820" + TXID + "01" + "ff" + "03" + "1864" + "08" + "0a" + "ff";
        TxCbor.Tx t = TxCbor.transaction("9f" + body + "a0" + "f5" + "f6" + "ff");
        TxCbor.Body b = TxCbor.body(t);
        assertTrue(t.indefinite());
        assertEquals(Set.of(TXID + "#1"), b.inputs());
        assertEquals(10L, (long) b.validFrom());
        // A non-minimal ttl head (25 0064) reads as 100.
        assertEquals(100L, (long) TxCbor.body(TxCbor.transaction(tx("a2" + "00" + "81" + "82" + "5820" + TXID + "00" + "03" + "190064"))).validTo());
    }

    @Test
    public void registrationsAndReferenceScriptsArePublications() throws Exception {
        String cred = "22".repeat(28);
        // An output map {0: addr bytes, 1: 2000000, 3: #6.24(bytes [3, bytes "abcd"])}.
        String inner = "82" + "03" + "42" + "abcd";
        String scriptRef = "d818" + "45" + inner;
        String output = "a3" + "00" + "41" + "70" + "01" + "1a001e8480" + "03" + scriptRef;
        String certs = "82" + "82" + "00" + "82" + "01" + "581c" + cred + "83" + "07" + "82" + "00" + "581c" + cred + "1a001e8480";
        String body = "a4" + "00" + "81" + "82" + "5820" + TXID + "00" + "01" + "81" + output + "03" + "1864" + "04" + certs;
        TxCbor.Body b = TxCbor.body(TxCbor.transaction(tx(body)));
        String hash = HexFormat.of().formatHex(com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash224(HexFormat.of().parseHex("03abcd")));
        assertEquals(Set.of("referenceScript:" + hash, "stakeRegistration:" + cred), b.publishes());
    }
}
