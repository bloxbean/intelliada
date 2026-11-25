package com.bloxbean.intelliada.idea.joust.verifier;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * ADR-005 I16 (M3.3a): an architecture test over IntelliAda's compiled JOUST packages. No class refers to a Plutus data
 * encoder (IntelliAda never builds datums or redeemers), and CCL's transaction decoding appears only in the review
 * modal's package (display only; none exists before M3.3b). Read from the class files' constant pools, so an import,
 * a fully qualified name or a reflective string all count.
 */
public class JoustArchitectureTest {

    /**
     * By rule, not by a list of names (M33A-DES-5): any Plutus data type or serializer of CCL, any JuLC class, and CCL's
     * and cbor's encoders and transaction readers.
     */
    static boolean encoder(String name) {
        return name.startsWith("com/bloxbean/cardano/client/plutus/spec/") && (name.contains("PlutusData") || name.contains("/serializers/"))
                || name.startsWith("org/julclang/core/") || name.startsWith("org/julclang/clientlib/")
                || name.equals("com/bloxbean/cardano/client/transaction/spec/Redeemer")
                || name.equals("co/nstant/in/cbor/CborEncoder") || name.equals("co/nstant/in/cbor/CborBuilder");
    }

    static boolean decoder(String name) {
        return name.startsWith("com/bloxbean/cardano/client/transaction/spec/Transaction")
                || name.equals("com/bloxbean/cardano/client/common/cbor/CborSerializationUtil")
                || name.equals("co/nstant/in/cbor/CborDecoder");
    }

    /** The internal class names a class file refers to: CONSTANT_Class entries, and names inside descriptors. */
    static java.util.Set<String> referencedClasses(byte[] b) {
        java.util.Set<String> out = new java.util.HashSet<>();
        String text = new String(b, StandardCharsets.ISO_8859_1);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(?:/[A-Za-z_$][A-Za-z0-9_$]*)+").matcher(text);
        while (m.find()) out.add(m.group());
        return out;
    }

    static final String REVIEW = "com/bloxbean/intelliada/idea/joust/review/";

    /** A canary the rule must flag: a reference to BytesPlutusData is an encoder. */
    @Test
    public void theRuleFlagsEveryPlutusDataTypeAndJuLC() {
        assertTrue(encoder("com/bloxbean/cardano/client/plutus/spec/BytesPlutusData"));
        assertTrue(encoder("com/bloxbean/cardano/client/plutus/spec/serializers/PlutusDataJsonConverter"));
        assertTrue(encoder("org/julclang/clientlib/PlutusDataAdapter"));
        assertTrue(encoder("org/julclang/core/cbor/PlutusDataCborEncoder"));
        assertFalse("naming a JOUST class (a resource path) is not an encoder", encoder("org/julclang/state/verifier/OutputVerifier"));
        assertTrue(decoder("com/bloxbean/cardano/client/transaction/spec/TransactionWitnessSet"));
        assertFalse(encoder("com/bloxbean/cardano/client/address/Address"));
        byte[] canary = "\u0001\u0000\u0037com/bloxbean/cardano/client/plutus/spec/BytesPlutusData".getBytes(StandardCharsets.ISO_8859_1);
        assertTrue(referencedClasses(canary).stream().anyMatch(JoustArchitectureTest::encoder));
    }

    @Test
    public void noEncoderAndDecodingOnlyInTheReviewModal() throws Exception {
        Path root = Path.of("build/classes/java/main/com/bloxbean/intelliada/idea/joust");
        assertTrue("compiled classes at " + root.toAbsolutePath(), Files.isDirectory(root));
        List<String> bad = new ArrayList<>();
        int scanned = 0;
        try (var w = Files.walk(root)) {
            for (Path c : (Iterable<Path>) w.filter(p -> p.toString().endsWith(".class"))::iterator) {
                scanned++;
                String rel = root.getParent().getParent().getParent().getParent().getParent().relativize(c).toString().replace('\\', '/');
                for (String n : referencedClasses(Files.readAllBytes(c))) {
                    if (encoder(n)) bad.add(rel + " refers to " + n);
                    if (!rel.contains(REVIEW) && decoder(n)) bad.add(rel + " refers to " + n);
                }
            }
        }
        assertTrue("scanned the JOUST packages", scanned > 50);
        assertTrue("I16: " + bad, bad.isEmpty());
    }
}
