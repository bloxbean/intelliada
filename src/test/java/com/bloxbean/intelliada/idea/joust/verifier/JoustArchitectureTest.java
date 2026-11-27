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

    static final String REVIEW = "com/bloxbean/intelliada/idea/joust/review/";

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

    /**
     * The internal class names a class file refers to, parsed with ASM: every type the remapper sees (class
     * references, field and method descriptors, signatures, annotations), never a raw byte scan (M33A-CDX-24).
     */
    static java.util.Set<String> referencedClasses(byte[] b) {
        java.util.Set<String> out = new java.util.HashSet<>();
        org.jetbrains.org.objectweb.asm.commons.Remapper collect = new org.jetbrains.org.objectweb.asm.commons.Remapper() {
            @Override
            public String map(String internalName) {
                out.add(internalName);
                return internalName;
            }
        };
        new org.jetbrains.org.objectweb.asm.ClassReader(b).accept(new org.jetbrains.org.objectweb.asm.commons.ClassRemapper(
                new org.jetbrains.org.objectweb.asm.ClassWriter(0), collect), 0);
        return out;
    }

    /** A compiled canary the parser and the rule must flag: a field of type BytesPlutusData is an encoder reference. */
    @Test
    public void aCompiledCanaryIsFlagged() throws Exception {
        assertTrue(encoder("com/bloxbean/cardano/client/plutus/spec/BytesPlutusData"));
        assertTrue(encoder("org/julclang/core/cbor/PlutusDataCborEncoder"));
        assertTrue(decoder("com/bloxbean/cardano/client/transaction/spec/TransactionWitnessSet"));
        assertFalse(encoder("org/julclang/state/verifier/OutputVerifier"));
        assertFalse(encoder("com/bloxbean/cardano/client/address/Address"));
        // The canary is a test source, compiled with the tests: no compiler launched here (M33A-CDX-33).
        byte[] canary;
        try (var in = getClass().getResourceAsStream("/com/bloxbean/intelliada/idea/joust/verifier/canary/ArchitectureCanary.class")) {
            assertNotNull("the compiled canary", in);
            canary = in.readAllBytes();
        }
        java.util.Set<String> refs = referencedClasses(canary);
        assertTrue("the field's descriptor is seen: " + refs, refs.stream().anyMatch(JoustArchitectureTest::encoder));
        assertTrue("the decoder call is seen: " + refs, refs.stream().anyMatch(JoustArchitectureTest::decoder));
    }

    /** The methods each compiled class invokes, as {@code owner.name}. */
    static java.util.Set<String> invoked(byte[] b) {
        java.util.Set<String> out = new java.util.HashSet<>();
        new org.jetbrains.org.objectweb.asm.ClassReader(b).accept(new org.jetbrains.org.objectweb.asm.ClassVisitor(org.jetbrains.org.objectweb.asm.Opcodes.ASM9) {
            @Override
            public org.jetbrains.org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                return new org.jetbrains.org.objectweb.asm.MethodVisitor(org.jetbrains.org.objectweb.asm.Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                        out.add(owner + "." + n);
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String n, String d, org.jetbrains.org.objectweb.asm.Handle bsm, Object... args) {
                        for (Object a : args) {
                            if (a instanceof org.jetbrains.org.objectweb.asm.Handle h) out.add(h.getOwner() + "." + h.getName());
                        }
                    }
                };
            }
        }, 0);
        return out;
    }

    /**
     * ADR-005 §12 E10, L43 (M3.3b1): until M3.3b3 wires the panel's write path, no production class admits a body to the
     * journal, builds one for handout, hands one out or discards one (#38). Method references count (invokedynamic
     * handles). Only the commit that wires these paths may relax this test, referencing #38.
     */
    @Test
    public void nothingInProductionReachesAdmissionOrABuildForHandout() throws Exception {
        Path root = Path.of("build/classes/java/main/com/bloxbean/intelliada/idea");
        assertTrue(Files.isDirectory(root));
        List<String> bad = new ArrayList<>();
        try (var w = Files.walk(root)) {
            for (Path c : (Iterable<Path>) w.filter(p -> p.toString().endsWith(".class"))::iterator) {
                String rel = root.relativize(c).toString().replace('\\', '/');
                for (String m : invoked(Files.readAllBytes(c))) {
                    // ChainJournal.admit only through JoustRunJournal; that, its handout record and the build path from nowhere.
                    boolean admitOutsideTheSession = m.equals("com/bloxbean/intelliada/idea/joust/journal/ChainJournal.admit")
                            && !rel.equals("joust/journal/Settlement.class") && !rel.equals("joust/journal/ChainJournal.class");
                    boolean settlementAdmit = (m.equals("com/bloxbean/intelliada/idea/joust/journal/Settlement.admit")
                            || m.equals("com/bloxbean/intelliada/idea/joust/journal/Settlement.handedOut"))
                            && !rel.equals("joust/run/JoustRunJournal.class");
                    if (settlementAdmit) bad.add(rel + " calls " + m);
                    // The whole deferred surface of ADR-005 L43 (#38; M33B1-DES-29): discard and the journal's handout too.
                    boolean deferred = m.equals("com/bloxbean/intelliada/idea/joust/run/JoustRunSession.discard")
                            || (m.equals("com/bloxbean/intelliada/idea/joust/run/JoustRunJournal.discard") && !rel.equals("joust/run/JoustRunSession.class"))
                            || (m.equals("com/bloxbean/intelliada/idea/joust/journal/Settlement.discard")
                                && !rel.equals("joust/run/JoustRunJournal.class") && !own(rel, "joust/journal/Settlement"))
                            || (m.equals("com/bloxbean/intelliada/idea/joust/journal/ChainJournal.handout")
                                && !own(rel, "joust/journal/Settlement") && !own(rel, "joust/journal/ChainJournal"));
                    if (deferred) bad.add(rel + " calls " + m);
                    if (admitOutsideTheSession || m.equals("com/bloxbean/intelliada/idea/joust/run/JoustRunJournal.admit")
                            || m.equals("com/bloxbean/intelliada/idea/joust/run/JoustRunJournal.handedOut")
                            || m.equals("com/bloxbean/intelliada/idea/joust/run/JoustRunSession.build")) bad.add(rel + " calls " + m);
                }
            }
        }
        assertTrue("E10: " + bad, bad.isEmpty());
        // The scan sees calls: a test class that admits is found by the same reader.
        try (var in = getClass().getResourceAsStream("/com/bloxbean/intelliada/idea/joust/journal/ChainJournalTest.class")) {
            assertNotNull(in);
            assertTrue(invoked(in.readAllBytes()).contains("com/bloxbean/intelliada/idea/joust/journal/ChainJournal.admit"));
        }
    }

    /**
     * SEC-15: a page request carries listings only, never a build around {@code build()}'s checks. (A session needs a
     * live host; the guard is asserted where it stands.)
     */
    @Test
    public void aPageRequestIsAListingOnly() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/bloxbean/intelliada/idea/joust/run/JoustRunSession.java"));
        int page = src.indexOf("public JsonNode nextPage(");
        assertTrue(page > 0);
        String body = src.substring(page, src.indexOf("\n    }\n", page));
        assertTrue(body, body.contains("!method.equals(\"instances\") && !method.equals(\"objects\")"));
        assertTrue(body, body.indexOf("throw new IllegalArgumentException") < body.indexOf("return call("));
    }

    /** {@code rel} is the class {@code base} itself or one of its nested classes, never a similarly named one (M33B1-CDX-159). */
    static boolean own(String rel, String base) {
        return rel.equals(base + ".class") || rel.startsWith(base + "$");
    }

    @Test
    public void aSimilarlyNamedClassIsNotExempt() {
        assertTrue(own("joust/journal/Settlement.class", "joust/journal/Settlement"));
        assertTrue(own("joust/journal/Settlement$Observation.class", "joust/journal/Settlement"));
        assertFalse(own("joust/journal/SettlementWorker.class", "joust/journal/Settlement"));
        assertFalse(own("joust/journal/ChainJournalHelper.class", "joust/journal/ChainJournal"));
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
