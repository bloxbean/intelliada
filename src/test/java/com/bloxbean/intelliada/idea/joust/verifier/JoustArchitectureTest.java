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

    static final List<String> ENCODERS = List.of("com/bloxbean/cardano/client/plutus/spec/PlutusData",
            "com/bloxbean/cardano/client/plutus/spec/ConstrPlutusData", "org/julclang/core/cbor/PlutusDataCborEncoder",
            "org/julclang/core/PlutusData", "com/bloxbean/cardano/client/transaction/spec/Redeemer");
    static final List<String> DECODERS = List.of("com/bloxbean/cardano/client/transaction/spec/Transaction",
            "com/bloxbean/cardano/client/transaction/spec/TransactionBody", "com/bloxbean/cardano/client/common/cbor/CborSerializationUtil");
    static final String REVIEW = "com/bloxbean/intelliada/idea/joust/review/";

    /**
     * Whether a class file refers to {@code internalName} exactly: as a constant-pool UTF-8 entry of that name (its
     * two-byte length, then the name), or inside a descriptor ({@code L<name>;}), never as a prefix of another name.
     */
    static boolean refers(String classFile, String internalName) {
        int n = internalName.length();
        String utf8 = "" + (char) (n >> 8 & 0xff) + (char) (n & 0xff) + internalName;
        return classFile.contains(utf8) || classFile.contains("L" + internalName + ";");
    }

    @Test
    public void theMatchIsExact() {
        String t = "\u0000\u0042com/bloxbean/cardano/client/transaction/spec/TransactionOutput";
        assertFalse(refers(t, "com/bloxbean/cardano/client/transaction/spec/Transaction"));
        String name = "com/bloxbean/cardano/client/transaction/spec/Transaction";
        assertTrue(refers("\u0000" + (char) name.length() + name, name));
        assertTrue(refers("(L" + name + ";)V", name));
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
                String text = new String(Files.readAllBytes(c), StandardCharsets.ISO_8859_1);
                String rel = root.getParent().getParent().getParent().getParent().getParent().relativize(c).toString().replace('\\', '/');
                for (String e : ENCODERS) if (refers(text, e)) bad.add(rel + " refers to " + e);
                if (!rel.contains(REVIEW)) for (String d : DECODERS) if (refers(text, d)) bad.add(rel + " refers to " + d);
            }
        }
        assertTrue("scanned the JOUST packages", scanned > 50);
        assertTrue("I16: " + bad, bad.isEmpty());
    }
}
