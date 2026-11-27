package com.bloxbean.intelliada.idea.joust.journal;

import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Entry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.bloxbean.intelliada.idea.joust.journal.Bodies.*;
import static org.junit.Assert.*;

/**
 * ADR-005 D17, I25, I31 and I33 (M3.3b1): the journal's store, admission and lost-history classification, against the
 * rows of §4.0 that need no host.
 */
public class ChainJournalTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    static final String CHAIN = "devnet-42-" + "ab".repeat(32);
    static final String MOD_A = JournalStore.key("/work/a"), MOD_B = JournalStore.key("/work/b");

    private JournalStore store() {
        return new JournalStore(tmp.getRoot().toPath().resolve("journal"));
    }

    private ChainJournal j(JournalStore s, String module) {
        return new ChainJournal(s, CHAIN, module, 60);
    }

    private static String ref(int tx, int ix) {
        return txid(tx) + "#" + ix;
    }

    @Test
    public void anAdmissionWritesTheEntryTheMarkAndTheRegistry() throws Exception {
        JournalStore s = store();
        ChainJournal a = j(s, MOD_A);
        assertEquals("new chain", a.open(100).history());
        a.admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 120, 130);
        String k = JournalStore.key(CHAIN);
        assertEquals(1060L, (long) s.record(k).value().mark());
        assertEquals(Set.of(MOD_A), s.record(k).value().modules());
        assertEquals(120, s.registry(k).value().highestTip());
        assertEquals(130, s.registry(k).value().highestNow());
        assertEquals(1, a.entries().size());
    }

    @Test
    public void aFailedJournalWriteRefuses() throws Exception {
        JournalStore s = store();
        ChainJournal a = j(s, MOD_A);
        a.open(0);
        // The entry's write fails (a full disk): admission refuses (I25); the mark already over-covers.
        Path f = s.journalFile(JournalStore.key(CHAIN), MOD_A);
        s.failWritesForTests = f::equals;
        try {
            a.admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
            fail("refused");
        } catch (JournalStore.StoreFailed expected) {
            // the body is not handed out
        }
        s.failWritesForTests = p -> false;
        assertTrue("nothing journaled", a.entries().isEmpty());
        assertEquals(1060L, (long) s.record(JournalStore.key(CHAIN)).value().mark());
    }

    @Test
    public void aCrashAfterAdmissionRestoresTheBodyOnTheNextHost() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        // A new IDE process: the entry survives and the host starts restoring, with complete history.
        ChainJournal.Opening o = j(new JournalStore(s.root()), MOD_A).open(10);
        assertEquals("complete", o.history());
        assertTrue(o.restoring());
        assertNull(o.completeAbove());
        assertEquals(1, o.survivors().size());
    }

    @Test
    public void twoModulesSharingAPayerAdmitOne() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        try {
            j(s, MOD_B).admit(submitted("1", tx(List.of(ref(1, 0), ref(2, 0)), 1000)), 0, 0);
            fail("the payer's input is reserved by module A");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("reserved"));
        }
        // Collateral overlaps too, and a reference to an input another live body spends.
        String collateral = tx(List.of(ref(3, 0)), List.of(ref(1, 0)), List.of(), null, 1000, List.of());
        String reference = tx(List.of(ref(4, 0)), List.of(), List.of(ref(1, 0)), null, 1000, List.of());
        for (String c : List.of(collateral, reference)) {
            try {
                j(s, MOD_B).admit(submitted("2", c), 0, 0);
                fail("refused");
            } catch (ChainJournal.Fenced expected) {
                // refused
            }
        }
    }

    @Test
    public void simultaneousAdmissionsThroughTwoConfigurationsOfOneChainAdmitOne() throws Exception {
        JournalStore s = store();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger admitted = new AtomicInteger();
            java.util.List<Future<?>> fs = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                int n = i;
                // Two node configurations of one chain share its identity, so its lock and its journal.
                fs.add(pool.submit(() -> {
                    go.await();
                    try {
                        new ChainJournal(new JournalStore(s.root()), CHAIN, n % 2 == 0 ? MOD_A : MOD_B, 60)
                                .admit(submitted("r" + n, tx(List.of(ref(7, 0), ref(100 + n, 0)), 1000)), 0, 0);
                        admitted.incrementAndGet();
                    } catch (ChainJournal.Fenced refused) {
                        // the other one won
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);
            assertEquals(1, admitted.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void concurrentPublicationsOfOneSharedVerifierAdmitOne() throws Exception {
        JournalStore s = store();
        String verifier = "cd".repeat(28);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), List.of(), List.of(), null, 1000, List.of(verifier))), 0, 0);
        try {
            j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), List.of(), List.of(), null, 1000, List.of(verifier))), 0, 0);
            fail("one publication of the verifier is live");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("publishes"));
        }
    }

    @Test
    public void aRestartWithALowerLimitNeverLowersTheMark() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        new ChainJournal(s, CHAIN, MOD_A, 10).admit(submitted("2", tx(List.of(ref(2, 0)), 900)), 0, 0);
        assertEquals(1060L, (long) s.record(JournalStore.key(CHAIN)).value().mark());
    }

    @Test
    public void aLostJournalCompletesRestoreOnlyAboveTheMark() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 2000)), 0, 0);
        Files.delete(s.journalFile(JournalStore.key(CHAIN), MOD_B));
        ChainJournal a = j(new JournalStore(s.root()), MOD_A);
        ChainJournal.Opening o = a.open(500);
        assertEquals("lost journal", o.history());
        assertEquals(2060L, (long) o.completeAbove());
        assertEquals(1, o.survivors().size());
        // Persisted: building waits for a tip strictly above the mark.
        assertFalse(a.observed(2060));
        try {
            a.admit(submitted("9", tx(List.of(ref(9, 0)), 3000)), 0, 0);
            fail("fenced");
        } catch (ChainJournal.Fenced expected) {
            // fenced
        }
        assertTrue(a.observed(2061));
        assertEquals("the lost module leaves the record once its history settled", Set.of(MOD_A),
                s.record(JournalStore.key(CHAIN)).value().modules());
        a.admit(submitted("9", tx(List.of(ref(9, 0)), 3000)), 2061, 2061);
    }

    @Test
    public void anUnreadableJournalIsLostHistoryAndIsSetAsideOnceItsFenceClears() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        Path f = s.journalFile(JournalStore.key(CHAIN), MOD_A);
        Files.writeString(f, "{not json");
        ChainJournal a = j(s, MOD_A);
        ChainJournal.Opening o = a.open(10);
        assertEquals("lost journal", o.history());
        assertTrue(o.survivors().isEmpty());
        assertTrue(a.observed(1061));
        assertFalse("moved aside", Files.exists(f));
        try (var l = Files.list(f.getParent())) {
            assertTrue("kept, never deleted", l.anyMatch(p -> p.getFileName().toString().startsWith(MOD_A + ".journal.json.lost-")));
        }
    }

    @Test
    public void aMissingChainRecordBesideASurvivingJournalWaitsForTheDerivedBound() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 300, 310);
        Files.delete(s.recordFile(JournalStore.key(CHAIN)));
        ChainJournal.Opening o = j(s, MOD_A).open(200);
        assertEquals("lost mark", o.history());
        // base: the highest of the registry's tip and now (310), the journal's validTo (1000) and the tip now (200).
        assertEquals(1000 + 900 + 7200 + 60, (long) o.completeAbove());
        assertEquals(o.completeAbove(), s.record(JournalStore.key(CHAIN)).value().mark());
        assertEquals("persisted with its fence", o.completeAbove().longValue(), s.record(JournalStore.key(CHAIN)).value().fence().until());
    }

    @Test
    public void aPublicChainInTheRegistryWithNeitherRecordNorJournalWaitsForTheDerivedBound() throws Exception {
        JournalStore s = store();
        String pub = "public-1";
        new ChainJournal(s, pub, MOD_A, 2160).admit(submitted("1", tx(List.of(ref(1, 0)), 90_000)), 80_000, 80_100);
        String k = JournalStore.key(pub);
        Files.delete(s.recordFile(k));
        Files.delete(s.journalFile(k, MOD_A));
        ChainJournal.Opening o = new ChainJournal(s, pub, MOD_A, 2160).open(70_000);
        assertEquals("lost mark", o.history());
        assertEquals(80_100 + 900 + 7200 + 2160, (long) o.completeAbove());
    }

    @Test
    public void aRecordLostAfterARewindUsesTheRegistrysHighestTip() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 5000, 900);
        Files.delete(s.recordFile(JournalStore.key(CHAIN)));
        // The chain was rewound to 3000 before detection: the base is still the registry's 5000.
        assertEquals(5000 + 900 + 7200 + 60, (long) j(s, MOD_A).open(3000).completeAbove());
    }

    @Test
    public void anIdeRestartDuringRecoveryKeepsTheDerivedMarkAndItsFence() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        Files.delete(s.recordFile(JournalStore.key(CHAIN)));
        long bound = j(s, MOD_A).open(0).completeAbove();
        // Right after writing the replacement: the next open is not complete history.
        ChainJournal.Opening again = j(new JournalStore(s.root()), MOD_A).open(0);
        assertEquals("fenced", again.history());
        assertEquals(bound, (long) again.completeAbove());
    }

    @Test
    public void bothLostStaysFencedAcrossRestartsAndAStaleBackupDoesNotLiftIt() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        byte[] backup = Files.readAllBytes(s.recordFile(k));   // lists module A only
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 1000)), 0, 0);
        Files.delete(s.recordFile(k));
        Files.delete(s.registryFile(k));
        Files.delete(s.journalFile(k, MOD_B));
        ChainJournal.Opening o = j(s, MOD_A).open(0);
        assertNotNull(o.permanent());
        assertNotNull("across restarts", j(new JournalStore(s.root()), MOD_A).open(0).permanent());
        Files.write(s.recordFile(k), backup);
        assertNotNull("a restored stale backup does not lift it", j(s, MOD_A).open(0).permanent());
        try {
            j(s, MOD_A).admit(submitted("2", tx(List.of(ref(3, 0)), 1000)), 0, 0);
            fail("fenced");
        } catch (ChainJournal.Fenced expected) {
            // no override
        }
    }

    @Test
    public void aStaleRecordBehindTheRegistryIsALostMark() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        byte[] old = Files.readAllBytes(s.recordFile(k));
        j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 5000)), 0, 0);
        Files.write(s.recordFile(k), old);   // a backup of the record from before the second admission
        ChainJournal.Opening o = j(s, MOD_A).open(0);
        assertEquals("lost mark", o.history());
        assertEquals(5000 + 900 + 7200 + 60, (long) o.completeAbove());
    }

    @Test
    public void simultaneousAdmissionsOnTwoChainsKeepBothRegistryMaxima() throws Exception {
        JournalStore s = store();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> {
                new ChainJournal(s, "devnet-1-" + "01".repeat(32), MOD_A, 60).admit(submitted("1", tx(List.of(ref(1, 0)), 100)), 50, 55);
                return null;
            });
            Future<?> b = pool.submit(() -> {
                new ChainJournal(s, "devnet-2-" + "02".repeat(32), MOD_A, 60).admit(submitted("1", tx(List.of(ref(1, 0)), 100)), 70, 75);
                return null;
            });
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(50, s.registry(JournalStore.key("devnet-1-" + "01".repeat(32))).value().highestTip());
        assertEquals(75, s.registry(JournalStore.key("devnet-2-" + "02".repeat(32))).value().highestNow());
    }

    @Test
    public void loadsAreStrict() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        String k = JournalStore.key(CHAIN);
        Path f = s.journalFile(k, MOD_A);
        String good = Files.readString(f);
        for (String bad : List.of(good.replace("\"version\" : 1", "\"version\" : 2"), good.replace("\"label\"", "\"extra\" : 1, \"label\""),
                good.replace("\"validTo\" : 1000", "\"validTo\" : 1001"), "", "[]")) {
            Files.writeString(f, bad);
            assertTrue("unreadable, never empty: " + bad.length(), s.journal(k, MOD_A).lost());
        }
    }

    @Test
    public void aFenceClearsOnlyStrictlyAboveIt() throws Exception {
        JournalStore s = store();
        ChainJournal a = j(s, MOD_A);
        a.admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        a.beginRewind(500, "test");
        assertFalse(a.observed(10_000));
        a.endRewind();
        assertTrue(a.observed(10_000));
    }

    @Test
    public void noKeyMaterialIsStored() throws Exception {
        JournalStore s = store();
        Entry e = submitted("1", tx(List.of(ref(1, 0)), 1000));
        j(s, MOD_A).admit(e, 0, 0);
        try (var w = Files.walk(s.root())) {
            for (Path p : (Iterable<Path>) w::iterator) {
                if (!Files.isRegularFile(p)) continue;
                String text = Files.readString(p);
                assertFalse(p.toString(), text.contains("mnemonic") || text.contains("signingKey") || text.contains("xprv"));
            }
        }
    }
}
