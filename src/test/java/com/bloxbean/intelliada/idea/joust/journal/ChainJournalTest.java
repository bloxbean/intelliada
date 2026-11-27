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
        assertEquals(ChainJournal.History.NEW_CHAIN, a.open(100).history());
        a.admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 120, 130);
        String k = JournalStore.key(CHAIN);
        assertEquals(1060L, (long) s.record(k).value().mark());
        assertEquals(Set.of(MOD_A), s.record(k).value().modules().keySet());
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
        assertEquals(ChainJournal.History.COMPLETE, o.history());
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
        assertEquals(ChainJournal.History.LOST_JOURNAL, o.history());
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
                s.record(JournalStore.key(CHAIN)).value().modules().keySet());
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
        assertEquals(ChainJournal.History.LOST_JOURNAL, o.history());
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
        assertEquals(ChainJournal.History.LOST_MARK, o.history());
        // base: the highest of the registry's tip and now (310), the journal's validTo (1000) and the tip now (200).
        assertEquals(1000 + 900 + 7200 + 60, (long) o.completeAbove());
        assertEquals(o.completeAbove(), s.record(JournalStore.key(CHAIN)).value().mark());
        assertEquals("persisted with its fence", o.completeAbove().longValue(), s.record(JournalStore.key(CHAIN)).value().fence().until());
    }

    @Test
    public void aPublicChainInTheRegistryWithNeitherRecordNorJournalWaitsForTheDerivedBound() throws Exception {
        JournalStore s = store();
        String pub = "public-1";
        new ChainJournal(s, pub, MOD_A, 2160).admit(submitted("1", tx(List.of(ref(1, 0)), 85_000)), 80_000, 80_100);
        String k = JournalStore.key(pub);
        Files.delete(s.recordFile(k));
        Files.delete(s.journalFile(k, MOD_A));
        ChainJournal.Opening o = new ChainJournal(s, pub, MOD_A, 2160).open(70_000);
        assertEquals(ChainJournal.History.LOST_MARK, o.history());
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
        assertEquals(ChainJournal.History.FENCED, again.history());
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
        assertEquals(ChainJournal.History.LOST_MARK, o.history());
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
        a.endRewind(a.record().rewind().id());
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

    // ---- round 1 (M33B1-*) ------------------------------------------------------------------------------------------------

    private static void backup(Path dir, Path to) throws Exception {
        try (var w = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) w::iterator) {
                Path t = to.resolve(dir.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else if (!p.getFileName().toString().equals("lock") && !p.getFileName().toString().endsWith(".host")) {
                    Files.copy(p, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** M33B1-SEC-1: a restored stale chain directory beside an unreadable (or missing) registry entry is a lost mark. */
    @Test
    public void aStaleChainDirectoryBesideABrokenRegistryEntryIsALostMark() throws Exception {
        for (boolean delete : List.of(false, true)) {
            JournalStore s = new JournalStore(tmp.newFolder().toPath());
            String k = JournalStore.key(CHAIN);
            j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
            Path dir = s.recordFile(k).getParent(), saved = tmp.newFolder().toPath();
            backup(dir, saved);
            j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 5000)), 0, 0);
            backup(saved, dir);
            if (delete) Files.delete(s.registryFile(k));
            else Files.writeString(s.registryFile(k), "{trunc");
            ChainJournal.Opening o = j(s, MOD_A).open(0);
            assertEquals(String.valueOf(delete), ChainJournal.History.LOST_MARK, o.history());
            try {
                j(s, MOD_A).admit(submitted("3", tx(List.of(ref(2, 0)), 1000)), 0, 0);
                fail("the stale history was taken as complete");
            } catch (ChainJournal.Fenced expected) {
                // fenced until the derived bound
            }
        }
    }

    /** M33B1-SEC-2: admission classifies as open does (record missing, registry unreadable, no journal: refused). */
    @Test
    public void admissionRefusesWhatOpenWouldFence() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).open(0);
        Files.createDirectories(s.registryFile(k).getParent());
        Files.writeString(s.registryFile(k), "not json");
        try {
            j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
            fail("admitted beside an unreadable registry entry");
        } catch (ChainJournal.Fenced expected) {
            // refused
        }
    }

    /** M33B1-CDX-1: a listed module's journal that disappeared blocks admission on the chain, as open would. */
    @Test
    public void aMissingListedJournalBlocksAdmission() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        j(s, MOD_B).open(0);
        Files.delete(s.journalFile(JournalStore.key(CHAIN), MOD_A));
        try {
            j(s, MOD_B).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
            fail("B spent A's reserved input");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("journal"));
        }
    }

    /** M33B1-CDX-2, -DES-8: a module journal restored from before its last admission is lost history. */
    @Test
    public void anOlderModuleJournalIsLostHistory() throws Exception {
        JournalStore s = store();
        Path f = s.journalFile(JournalStore.key(CHAIN), MOD_A);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        byte[] old = Files.readAllBytes(f);
        j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 2000)), 0, 0);
        Files.write(f, old);
        ChainJournal.Opening o = j(s, MOD_A).open(0);
        assertEquals(ChainJournal.History.LOST_JOURNAL, o.history());
        assertEquals(2060L, (long) o.completeAbove());
        try {
            j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 1000)), 0, 0);
            fail("B spent the input of A's lost entry");
        } catch (ChainJournal.Fenced expected) {
            // fenced
        }
    }

    /** M33B1-CDX-3: recovery never lowers a bound: the registry is rebuilt only from the derived base. */
    @Test
    public void aRebuiltRegistryKeepsTheBoundConservative() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 95_000)), 90_000, 90_000);
        Files.delete(s.registryFile(k));
        long first = j(s, MOD_A).open(100).completeAbove();
        assertTrue("covers the handed-out body: " + first, first > 95_060);
        Files.delete(s.recordFile(k));
        Files.delete(s.journalFile(k, MOD_A));
        long second = j(s, MOD_A).open(100).completeAbove();
        assertTrue("a later loss derives no lower bound: " + second + " < " + first, second >= first);
    }

    /** M33B1-CDX-18: a record that lists handouts without a mark is unreadable, never a fence at slot 0. */
    @Test
    public void aRecordWithModulesAndNoMarkIsNotAZeroFence() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 0, 0);
        Path r = s.recordFile(k);
        Files.writeString(r, Files.readString(r).replace("\"mark\" : 560", "\"mark\" : null"));
        assertTrue(s.record(k).lost());
        ChainJournal.Opening o = j(s, MOD_A).open(0);
        assertEquals(ChainJournal.History.LOST_MARK, o.history());
        assertTrue(o.completeAbove() > 500);
    }

    /** M33B1-CDX-11: restoring nothing for a module without a journal writes no file. */
    @Test
    public void anEmptyRestoreMakesNoJournalFile() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        j(s, MOD_B).replace(List.of());
        assertFalse(Files.exists(s.journalFile(JournalStore.key(CHAIN), MOD_B)));
        assertEquals(ChainJournal.History.COMPLETE, j(s, MOD_A).open(0).history());
    }

    /** M33B1-CDX-13: an entry that would not read back is refused before anything is written. */
    @Test
    public void anEntryThatWouldNotReadBackIsRefused() throws Exception {
        JournalStore s = store();
        Entry good = submitted("1", tx(List.of(ref(1, 0)), 1000));
        Entry bad = new Entry(good.reservation(), good.txHash(), good.cbor(), 3L, good.validTo(), good.handout(), good.node(), good.state(),
                null, null, List.of(), good.label());
        try {
            j(s, MOD_A).admit(bad, 0, 0);
            fail("admitted");
        } catch (ChainJournal.Fenced expected) {
            // refused
        }
        assertFalse(Files.exists(s.recordFile(JournalStore.key(CHAIN))));
        String big = tx(List.of(ref(1, 0)), 1000);
        assertTrue(big.length() < 2 * JournalStore.MAX_BODY_BYTES);
    }

    /** M33B1-CDX-5: one module's restore does not clear a rewind another module must still reconcile. */
    @Test
    public void aRewindClearsOnlyOnceEveryModuleReconciled() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 1000)), 0, 0);
        j(s, MOD_A).beginRewind(500, "test");
        j(s, MOD_B).endRewind(j(s, MOD_A).record().rewind().id());
        assertNotNull("A still pending", j(s, MOD_A).record().rewind());
        try {
            j(s, MOD_B).admit(submitted("2", tx(List.of(ref(3, 0)), 1000)), 0, 0);
            fail("admitted while A reconciles");
        } catch (ChainJournal.Fenced expected) {
            // closed
        }
        j(s, MOD_A).endRewind(j(s, MOD_A).record().rewind().id());
        assertNull(j(s, MOD_A).record().rewind());
    }

    /** M33B1-DES-6 (I33): a module's host lock admits one owner, in this IDE and across store spellings. */
    @Test
    public void aModulesHostLockAdmitsOneOwner() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        JournalStore.Held first = s.ownModule(k, MOD_A);
        try {
            new JournalStore(s.root()).ownModule(k, MOD_A);
            fail("a second owner");
        } catch (JournalStore.StoreFailed expected) {
            // refused, without touching the first owner's descriptor
        }
        JournalStore.Held other = s.ownModule(k, MOD_B);   // another module of the chain runs beside it
        other.close();
        first.close();
        s.ownModule(k, MOD_A).close();   // released
    }

    /** M33B1-DES-6 (I33): restarting one module's host leaves another module's reservations untouched. */
    @Test
    public void restartingOneHostLeavesAnothersReservationUntouched() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 1000)), 0, 0);
        byte[] before = Files.readAllBytes(s.journalFile(k, MOD_B));
        ChainJournal a = j(s, MOD_A);
        ChainJournal.Opening o = a.open(10);
        a.replace(List.of(o.survivors().get(0).withReservation("7")));
        assertArrayEquals(before, Files.readAllBytes(s.journalFile(k, MOD_B)));
        try {
            j(s, MOD_A).admit(submitted("8", tx(List.of(ref(2, 0)), 1000)), 0, 0);
            fail("B's reservation was released");
        } catch (ChainJournal.Fenced expected) {
            // still reserved
        }
    }

    // ---- round 2 (M33B1-CDX-*) ------------------------------------------------------------------------------------------

    /** CDX-2: a journal restored from before a handout change is behind its record (every write bumps the revision). */
    @Test
    public void aJournalFromBeforeAHandoutChangeIsLostHistory() throws Exception {
        JournalStore s = store();
        Path f = s.journalFile(JournalStore.key(CHAIN), MOD_A);
        j(s, MOD_A).admit(entry("1", tx(List.of(ref(1, 0)), 1000), JournalStore.Handout.approvalPending), 0, 0);
        byte[] before = Files.readAllBytes(f);
        j(s, MOD_A).update("1", e -> e.withHandout(JournalStore.Handout.exported));
        Files.write(f, before);
        assertEquals(ChainJournal.History.LOST_JOURNAL, j(s, MOD_A).open(0).history());
    }

    /** CDX-22: a journal newer than its record shows the record is stale: a lost mark. */
    @Test
    public void aJournalAheadOfItsRecordIsALostMark() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 100, 100);
        byte[] rec = Files.readAllBytes(s.recordFile(k)), reg = Files.readAllBytes(s.registryFile(k));
        j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 900)), 100, 100);
        Files.write(s.recordFile(k), rec);
        Files.write(s.registryFile(k), reg);
        ChainJournal.Opening o = j(s, MOD_A).open(100);
        assertEquals(ChainJournal.History.LOST_MARK, o.history());
        assertTrue("covers the body valid to 900: " + o.completeAbove(), o.completeAbove() > 960);
    }

    /** CDX-5: a host acknowledges only the rewind it was restored after. */
    @Test
    public void anOldRestoreDoesNotAcknowledgeANewerRewind() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        j(s, MOD_A).beginRewind(1100, "first");
        String first = j(s, MOD_A).record().rewind().id();
        j(s, MOD_A).beginRewind(100, "second");
        j(s, MOD_A).endRewind(first);
        assertTrue("still pending for the newer rewind", j(s, MOD_A).record().rewind().pending().contains(MOD_A));
    }

    /** CDX-20: a refused admission on lost history persists the irreversible marker. */
    @Test
    public void aBothLostRefusalIsPersisted() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        byte[] rec = Files.readAllBytes(s.recordFile(k)), reg = Files.readAllBytes(s.registryFile(k));
        Files.delete(s.recordFile(k));
        Files.delete(s.registryFile(k));
        try {
            j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 1000)), 0, 0);
            fail("admitted");
        } catch (ChainJournal.Fenced expected) {
            // refused
        }
        Files.write(s.recordFile(k), rec);
        Files.write(s.registryFile(k), reg);
        assertEquals("an older metadata pair does not lift it", ChainJournal.History.BOTH_LOST, j(s, MOD_A).open(0).history());
    }

    /** CDX-21: another module's open after an offline rewind finds A's reopened entry; admission closes. */
    @Test
    public void anOfflineRewindIsFoundByAnyModulesOpen() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 400, 400);
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.expired, null));
        ChainJournal.Opening o = j(s, MOD_B).open(100);   // the chain went back below A's expired body's validTo
        assertEquals(ChainJournal.History.FENCED, o.history());
        assertTrue(j(s, MOD_B).record().rewind().pending().contains(MOD_A));
        try {
            j(s, MOD_B).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 100, 100);
            fail("B spent A's input");
        } catch (ChainJournal.Fenced expected) {
            // closed until A reconciles
        }
    }

    /** CDX-23: a restart keeps the lost-mark bound rather than deriving another. */
    @Test
    public void aRestartKeepsTheLostMarkBound() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 100, 100);
        Files.writeString(s.journalFile(k, MOD_B), "{not json");
        long a = j(s, MOD_A).open(100).completeAbove();
        long b = j(s, MOD_A).open(100).completeAbove();
        assertEquals(a, b);
    }

    /** CDX-31: admission refuses a validity beyond what lost-mark recovery can bound. */
    @Test
    public void aValidityBeyondTheRecoveryBoundIsRefused() throws Exception {
        JournalStore s = store();
        try {
            j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 50_000)), 100, 100);
            fail("admitted");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("valid beyond"));
        }
    }

    /** CDX-10: a journal file this user cannot read is lost history, not a storage failure. */
    @Test
    public void aJournalThatCannotBeReadIsLostHistory() throws Exception {
        org.junit.Assume.assumeTrue(java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        Path f = s.journalFile(k, MOD_A);
        Files.setPosixFilePermissions(f, java.nio.file.attribute.PosixFilePermissions.fromString("---------"));
        try {
            assertTrue(s.journal(k, MOD_A).lost());
            assertEquals(ChainJournal.History.LOST_JOURNAL, j(s, MOD_A).open(0).history());
        } finally {
            Files.setPosixFilePermissions(f, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        }
    }

    /** M33B1-SEC-7: a chain directory restored from before an approval became a submission is a lost mark. */
    @Test
    public void aChainDirectoryFromBeforeAHandoutIsALostMark() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(entry("r1", tx(List.of(ref(1, 0)), 1000), JournalStore.Handout.approvalPending), 100, 100);
        Path dir = s.recordFile(k).getParent(), saved = tmp.newFolder().toPath();
        backup(dir, saved);
        j(s, MOD_A).update("r1", e -> e.withHandout(JournalStore.Handout.submitted));
        backup(saved, dir);   // the registry entry stays current
        ChainJournal.Opening o = j(s, MOD_A).open(100);
        assertEquals(ChainJournal.History.LOST_MARK, o.history());
        try {
            j(s, MOD_B).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 100, 100);
            fail("the submitted body's input was released");
        } catch (ChainJournal.Fenced expected) {
            // fenced until the derived bound
        }
    }

    // ---- round 4 (M33B1-CDX-*) ------------------------------------------------------------------------------------------

    /** CDX-21: an inclusion that moved while no host ran is found at any module's open, even at a higher tip. */
    @Test
    public void anOfflineForkAtAHigherTipIsFoundAtOpen() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 90, 90);
        JournalStore.Inclusion was = new JournalStore.Inclusion(100, 100, "11".repeat(32));
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.confirmed, 100L).withInclusion(was));
        ChainJournal.Opening o = j(s, MOD_B).open(181, h -> null);   // gone on the replacement branch
        assertEquals(ChainJournal.History.FENCED, o.history());
        assertTrue(j(s, MOD_B).record().rewind().pending().contains(MOD_A));
    }

    /** CDX-22: evidence of a lost mark is persisted before an older fence lifts. */
    @Test
    public void aLostMarkFoundWhenAFenceWouldLiftIsPersistedFirst() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 100, 100);
        byte[] rec = Files.readAllBytes(s.recordFile(k)), reg = Files.readAllBytes(s.registryFile(k));
        j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 900)), 100, 100);
        Files.write(s.recordFile(k), rec);
        Files.write(s.registryFile(k), reg);
        assertFalse(j(s, MOD_A).observed(562));
        long bound = s.record(k).value().fence().until();
        assertTrue("a bound over the body valid to 900: " + bound, bound > 960);
    }

    /** CDX-36: an ordinary update never certifies a journal restored from before an admission. */
    @Test
    public void anUpdateDoesNotCertifyAStaleJournal() throws Exception {
        JournalStore s = store();
        Path f = s.journalFile(JournalStore.key(CHAIN), MOD_A);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        byte[] old = Files.readAllBytes(f);
        j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 1000)), 0, 0);
        Files.write(f, old);
        try {
            j(s, MOD_A).update("1", e -> e.withEvent("x"));
            fail("updated");
        } catch (ChainJournal.Fenced expected) {
            // refused
        }
        assertFalse(j(s, MOD_A).intact());
    }

    /** CDX-37: a second offline rewind renews the pending set under a new id. */
    @Test
    public void aSecondOfflineRewindRenewsThePendingSet() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 400, 400);
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.expired, null));
        j(s, MOD_B).beginRewind(450, "first");
        String first = j(s, MOD_B).record().rewind().id();
        j(s, MOD_A).endRewind(first);
        j(s, MOD_B).endRewind(first);
        j(s, MOD_B).beginRewind(600, "again");   // A reconciled at 600 meanwhile (still pending under a new id)
        j(s, MOD_B).open(100);   // and the chain went back to 100 while no host ran
        assertTrue(j(s, MOD_B).record().rewind().pending().contains(MOD_A));
        assertFalse("a fresh id", first.equals(j(s, MOD_B).record().rewind().id()));
    }

    /** CDX-43: a chain back below a lifted lost-history fence is fenced again. */
    @Test
    public void aChainBackBelowARecoveredBoundIsFencedAgain() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 0, 0);
        Files.delete(s.journalFile(JournalStore.key(CHAIN), MOD_A));
        assertEquals(560L, (long) j(s, MOD_B).open(0).completeAbove());
        assertTrue(j(s, MOD_B).observed(561));
        ChainJournal.Opening o = j(s, MOD_B).open(100);
        assertEquals(ChainJournal.History.FENCED, o.history());
        assertEquals(560L, (long) o.completeAbove());
    }

    /** CDX-44: a restore's replacement goes only over the journal revision its opening read. */
    @Test
    public void aReplacementOverAChangedJournalIsRefused() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        ChainJournal.Opening o = j(s, MOD_A).open(0);
        j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 1000)), 0, 0);   // a successor's admission meanwhile
        try {
            j(s, MOD_A).replace(o.survivors(), o.revision());
            fail("overwrote the successor's admission");
        } catch (ChainJournal.Fenced expected) {
            // refused
        }
        assertEquals(2, j(s, MOD_A).entries().size());
    }

    /** CDX-45: a handout moves forward only on intact history with no rewind. */
    @Test
    public void aHandoutDuringAReconciliationIsRefused() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(entry("1", tx(List.of(ref(1, 0)), 1000), JournalStore.Handout.approvalPending), 0, 0);
        j(s, MOD_A).beginRewind(0, "test");
        try {
            j(s, MOD_A).handout("1", JournalStore.Handout.exported);
            fail("handed out");
        } catch (ChainJournal.Fenced expected) {
            // refused
        }
    }

    /** DES-14: a failed inclusion read at open fails that open only; nothing is recorded. */
    @Test
    public void aFailedInclusionReadAtOpenRecordsNothing() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 90, 90);
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.shallow, 100L).withInclusion(new JournalStore.Inclusion(100, 100, "11".repeat(32))));
        try {
            j(s, MOD_B).open(120, h -> {
                throw new java.io.IOException("timeout");
            });
            fail("opened");
        } catch (JournalStore.StoreFailed expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("start again"));
        }
        assertNull("no rewind recorded", j(s, MOD_A).record().rewind());
    }

    // ---- round 5 (M33B1-CDX-*) ------------------------------------------------------------------------------------------

    /** CDX-34: a host that started before a rewind admits nothing (the chain's rewind count moved). */
    @Test
    public void aHostThatMissedARewindAdmitsNothing() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        long seen = s.record(JournalStore.key(CHAIN)).value().rewinds();
        j(s, MOD_B).beginRewind(0, "test");
        j(s, MOD_B).endRewind(j(s, MOD_B).record().rewind().id());
        j(s, MOD_A).endRewind(j(s, MOD_A).record().rewind() == null ? "x" : j(s, MOD_A).record().rewind().id());
        try {
            j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 1000)), 0, 0, seen);
            fail("admitted");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("rewound"));
        }
    }

    /** CDX-36: a fenced recovery's replacement needs the fence that authorized it still persisted. */
    @Test
    public void aRecoveryWithoutItsFenceIsRefused() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 0, 0);
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 500)), 0, 0);
        byte[] beforeFence = Files.readAllBytes(s.recordFile(k));
        byte[] reg = Files.readAllBytes(s.registryFile(k));
        Files.delete(s.journalFile(k, MOD_B));
        ChainJournal.Opening o = j(s, MOD_A).open(100);
        assertEquals(ChainJournal.History.LOST_JOURNAL, o.history());
        Files.write(s.recordFile(k), beforeFence);   // the record from before the fence was written
        Files.write(s.registryFile(k), reg);
        try {
            j(s, MOD_A).replace(o.survivors(), o);
            fail("replaced without its fence");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("fence"));
        }
    }

    /** CDX-43: an admission keeps the remembered recovery bound. */
    @Test
    public void anAdmissionKeepsTheRecoveredBound() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 0, 0);
        Files.delete(s.journalFile(JournalStore.key(CHAIN), MOD_A));
        j(s, MOD_B).open(0);
        assertTrue(j(s, MOD_B).observed(561));
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(9, 0)), 700)), 562, 562);
        assertEquals(560L, (long) s.record(JournalStore.key(CHAIN)).value().recovered());
        assertEquals(ChainJournal.History.FENCED, j(s, MOD_B).open(100).history());
    }

    /** CDX-46, DES-16: an admission far below recorded evidence is refused; it records no rewind (one unconfirmed tip). */
    @Test
    public void anAdmissionFarBelowRecordedEvidenceIsRefused() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 400, 400);
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.expired, null));
        try {
            j(s, MOD_B).admit(submitted("1", tx(List.of(ref(1, 0)), 300)), 100, 100);
            fail("admitted");
        } catch (ChainJournal.Fenced expected) {
            // refused
        }
        assertNull("no chain rewind from one admission's tip", j(s, MOD_A).record().rewind());
    }

    /** CDX-50: opening at the same chain point does not renew a rewind still being reconciled. */
    @Test
    public void anOpenAtTheSamePointKeepsTheRewind() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 400, 400);
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.expired, null));
        j(s, MOD_B).open(100);   // records the rewind
        String id = j(s, MOD_B).record().rewind().id();
        j(s, MOD_B).open(100);
        assertEquals(id, j(s, MOD_B).record().rewind().id());
    }

    /** CDX-52: a record without the later optional fields reads with their defaults. */
    @Test
    public void aRecordWithoutTheOptionalFieldsReads() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        Path r = s.recordFile(k);
        String text = Files.readString(r).replaceAll(",\\s*\"recovered\" : null", "").replaceAll(",\\s*\"rewinds\" : 0", "");
        assertFalse(text.contains("recovered"));
        Files.writeString(r, text);
        assertTrue(s.record(k).ok());
        assertEquals(ChainJournal.History.COMPLETE, j(s, MOD_A).open(0).history());
    }

    // ---- round 6 (M33B1-CDX-*) ------------------------------------------------------------------------------------------

    /** CDX-46: an expired body whose validity the admission's chain point reopens still holds its inputs. */
    @Test
    public void anExpiredBodyValidAgainHoldsItsInputs() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 400, 400);
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.expired, null));
        try {
            j(s, MOD_B).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 490, 490);
            fail("B reused A's input inside its reopened validity");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("reserved"));
        }
    }

    /** CDX-54: newly lost journals get a fence covering the current mark; an older, lower one never releases them. */
    @Test
    public void aNewlyLostJournalNeedsAFenceOverTheCurrentMark() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        String modC = JournalStore.key("/work/c");
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 0, 0);
        Files.delete(s.journalFile(k, MOD_A));
        j(s, MOD_B).open(0);
        assertTrue(j(s, MOD_B).observed(561));
        new ChainJournal(s, CHAIN, modC, 60).admit(submitted("1", tx(List.of(ref(3, 0)), 2000)), 562, 562);
        j(s, MOD_B).open(100);   // the recovered fence (560) comes back
        Files.delete(s.journalFile(k, modC));
        assertFalse("560 does not release C's lost history", j(s, MOD_B).observed(561));
        assertTrue(s.record(k).value().fence().until() >= 2060);
    }

    /** CDX-55: an inclusion recorded while an open read the others is read too before the open completes. */
    @Test
    public void anInclusionRecordedDuringOpenIsChecked() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 900)), 400, 400);
        JournalStore.Inclusion i1 = new JournalStore.Inclusion(450, 450, "11".repeat(32)), i2 = new JournalStore.Inclusion(500, 500, "22".repeat(32));
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.shallow, 450L).withInclusion(i1));
        String h2 = Bodies.hash(tx(List.of(ref(2, 0)), 900));
        java.util.concurrent.atomic.AtomicBoolean once = new java.util.concurrent.atomic.AtomicBoolean();
        ChainJournal.Opening o = j(s, MOD_B).open(600, h -> {
            if (once.compareAndSet(false, true)) {
                // while B reads the first inclusion, A records the second; it is gone on a fork by the time B checks it
                j(s, MOD_A).update("2", e -> e.withState(JournalStore.State.confirmed, 500L).withInclusion(i2));
                return i1;
            }
            return h.equals(h2) ? null : i1;
        });
        assertEquals("the fork is found, not certified", ChainJournal.History.FENCED, o.history());
        assertNotNull(s.record(k).value().rewind());
    }

    /** CDX-56: fork evidence renews a rewind still being reconciled, so an older restore cannot acknowledge it. */
    @Test
    public void forkEvidenceRenewsARewind() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        JournalStore.Inclusion i = new JournalStore.Inclusion(500, 500, "11".repeat(32));
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.confirmed, 500L).withInclusion(i));
        j(s, MOD_A).beginRewind(600, "restore in flight");
        String before = j(s, MOD_A).record().rewind().id();
        j(s, MOD_B).open(600, h -> null);   // the inclusion is gone at the same tip
        assertFalse(before.equals(j(s, MOD_A).record().rewind().id()));
    }

    /** CDX-57: a restored older record that lacks a recorded rewind is behind the registry: a lost mark. */
    @Test
    public void anOlderRecordWithoutARewindIsALostMark() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        byte[] before = Files.readAllBytes(s.recordFile(k));
        j(s, MOD_A).beginRewind(650, "fork");
        Files.write(s.recordFile(k), before);
        assertEquals(ChainJournal.History.LOST_MARK, j(s, MOD_B).open(650).history());
    }

    /** CDX-58: irreversible loss is persisted before any provider read, even when the read then fails. */
    @Test
    public void bothLostIsPersistedBeforeAnyRead() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.shallow, 450L).withInclusion(new JournalStore.Inclusion(450, 450, "11".repeat(32))));
        Files.delete(s.recordFile(k));
        Files.delete(s.registryFile(k));
        ChainJournal.Opening o = j(s, MOD_B).open(500, h -> {
            throw new java.io.IOException("timeout");
        });
        assertEquals(ChainJournal.History.BOTH_LOST, o.history());
        assertTrue(s.permanentlyFenced(k));
    }

    /** CDX-59: a registry behind the record is repaired to the record's mark before its generation advances. */
    @Test
    public void aLaggingRegistryIsRepairedBeforeItAdvances() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 100, 100);
        byte[] oldReg = Files.readAllBytes(s.registryFile(k));
        j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 57_000)), 50_000, 50_000);
        Files.write(s.registryFile(k), oldReg);
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(3, 0)), 700)), 100, 100);
        assertTrue("the base kept: " + s.registry(k).value().highestTip(), s.registry(k).value().highestTip() >= 57_060);
    }

    // ---- round 7 (M33B1-CDX-*) ------------------------------------------------------------------------------------------

    /** CDX-46, -61: a final body still journaled holds its inputs, whatever tip the admission saw. */
    @Test
    public void aFinalBodyStillJournaledHoldsItsInputs() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 400, 400);
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.confirmed, 490L).withInclusion(new JournalStore.Inclusion(490, 490, "11".repeat(32))));
        try {
            j(s, MOD_B).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 600, 600);
            fail("admitted");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("reserved"));
        }
    }

    /** CDX-56: the same evidence seen again keeps the rewind's id; new evidence renews it. */
    @Test
    public void aRewindRenewsOnlyOnNewEvidence() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 400, 400);
        j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 900)), 400, 400);
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.expired, null));
        JournalStore.Inclusion i = new JournalStore.Inclusion(450, 450, "11".repeat(32));
        j(s, MOD_A).update("2", e -> e.withState(JournalStore.State.confirmed, 450L).withInclusion(i));
        String h2 = Bodies.hash(tx(List.of(ref(2, 0)), 900));
        j(s, MOD_B).open(() -> 480, h -> i);   // only the expired body's validity (480 < 500)
        String first = j(s, MOD_B).record().rewind().id();
        j(s, MOD_B).open(() -> 480, h -> i);
        assertEquals("the same evidence keeps the id", first, j(s, MOD_B).record().rewind().id());
        j(s, MOD_B).open(() -> 480, h -> h.equals(h2) ? null : i);   // and now the inclusion is gone
        assertFalse("fork evidence renews it", first.equals(j(s, MOD_B).record().rewind().id()));
    }

    /** CDX-62: a rewind is never recorded over a record the registry proves stale. */
    @Test
    public void aRewindIsNotRecordedOverAStaleRecord() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 1000)), 0, 0);
        byte[] old = Files.readAllBytes(s.recordFile(k));
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 1000)), 0, 0);
        Files.write(s.recordFile(k), old);
        try {
            j(s, MOD_A).beginRewind(0, "test");
            fail("advanced a stale record");
        } catch (JournalStore.StoreFailed expected) {
            // refused: recover first
        }
        // The open recovers the lost mark, then records the refused rewind (M33B1-CDX-84), which this opening reconciles.
        ChainJournal.Opening o = j(s, MOD_A).open(0);
        assertTrue(o.restoring());
        assertEquals("test", j(s, MOD_A).record().rewind().reason());
        assertNull(j(s, MOD_A).unsavedRewind());
    }

    /** CDX-63: a module admitted again after removal never reuses a revision. */
    @Test
    public void revisionsAreNeverReused() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 0, 0);
        long first = s.journal(k, MOD_A).value().generation();
        Files.delete(s.journalFile(k, MOD_A));
        j(s, MOD_B).open(0);
        assertTrue(j(s, MOD_B).observed(561));
        j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 2000)), 562, 562);
        assertTrue(s.journal(k, MOD_A).value().generation() > first);
    }

    /** CDX-66: a chain that only advanced while an open read its inclusions is not a rewind. */
    @Test
    public void aChainThatOnlyAdvancedIsNotARewind() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 390, 390);
        JournalStore.Inclusion early = new JournalStore.Inclusion(395, 395, "11".repeat(32)), late = new JournalStore.Inclusion(410, 410, "22".repeat(32));
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.shallow, 395L).withInclusion(early));
        j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 900)), 390, 390);
        String h2 = Bodies.hash(tx(List.of(ref(2, 0)), 900));
        java.util.concurrent.atomic.AtomicLong tip = new java.util.concurrent.atomic.AtomicLong(400);
        java.util.concurrent.atomic.AtomicBoolean once = new java.util.concurrent.atomic.AtomicBoolean();
        ChainJournal.Opening o = j(s, MOD_B).open(tip::get, h -> {
            if (once.compareAndSet(false, true)) {
                tip.set(420);
                j(s, MOD_A).update("2", e -> e.withState(JournalStore.State.shallow, 410L).withInclusion(late));
            }
            return h.equals(h2) ? late : early;
        });
        assertEquals(ChainJournal.History.COMPLETE, o.history());
        assertNull(j(s, MOD_B).record().rewind());
    }

    /** CDX-67: a first rewind on a new chain creates the registry entry, so a restored older record is behind it. */
    @Test
    public void aFirstRewindCreatesTheRegistryEntry() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).beginRewind(100, "first");
        assertTrue(s.registry(k).ok());
    }

    // ---- round 8 (M33B1-CDX-*) ------------------------------------------------------------------------------------------

    /** CDX-56: a fork into another block and one into absence are different evidence. */
    @Test
    public void twoForksAreDifferentEvidence() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        JournalStore.Inclusion x = new JournalStore.Inclusion(450, 450, "11".repeat(32)), y = new JournalStore.Inclusion(450, 450, "22".repeat(32));
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.confirmed, 450L).withInclusion(x));
        j(s, MOD_B).open(() -> 600, h -> y);
        String first = j(s, MOD_B).record().rewind().id();
        j(s, MOD_B).open(() -> 600, h -> null);
        assertFalse(first.equals(j(s, MOD_B).record().rewind().id()));
    }

    /** CDX-66: a lower tip read during the open's rounds decides reopening. */
    @Test
    public void aLowerTipReadDuringOpenDecides() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 0, 0);
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 500)), 0, 0);
        JournalStore.Inclusion i = new JournalStore.Inclusion(300, 300, "11".repeat(32));
        j(s, MOD_B).update("1", e -> e.withState(JournalStore.State.confirmed, 300L).withInclusion(i));
        Files.delete(s.journalFile(k, MOD_A));
        j(s, MOD_B).open(0);
        assertTrue(j(s, MOD_B).observed(561));   // recovered at 560
        java.util.Iterator<Long> tips = List.of(600L, 490L, 490L, 490L).iterator();
        ChainJournal.Opening o = j(s, MOD_B).open(tips::next, h -> i);
        assertEquals("490, read during the open, is below the recovered bound", ChainJournal.History.FENCED, o.history());
    }

    /** CDX-68: a pass never prunes under a newer rewind. */
    @Test
    public void pruningRefusesUnderANewerRewind() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 0, 0);
        long seen = s.record(JournalStore.key(CHAIN)).value().rewinds();
        j(s, MOD_B).beginRewind(100, "a rewind B found");
        assertFalse(j(s, MOD_A).remove(Set.of("1"), seen));
        assertEquals(1, j(s, MOD_A).entries().size());
    }

    /** CDX-70: a rewind with many evidence items is written and read back. */
    @Test
    public void manyEvidenceItemsReadBack() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 0, 0);
        String k = JournalStore.key(CHAIN);
        JournalStore.ChainRecord c = s.record(k).value();
        Set<String> ev = new java.util.LinkedHashSet<>();
        for (int n = 0; n < 5000; n++) ev.add("below:" + String.format("%064x", n));
        s.writeRecord(c.withNewRewind(new JournalStore.Rewind(0, "many", Set.of(MOD_A), JournalStore.Rewind.newId(), ev)));
        assertTrue(s.record(k).ok());
        assertEquals(5000, s.record(k).value().rewind().evidence().size());
    }

    /** CDX-71: both lost is persisted before the first tip read, even when that read fails. */
    @Test
    public void bothLostIsPersistedBeforeTheTipRead() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 0, 0);
        Files.delete(s.recordFile(k));
        Files.delete(s.registryFile(k));
        ChainJournal.Opening o = j(s, MOD_B).open(() -> {
            throw new java.io.IOException("timeout");
        }, h -> null);
        assertEquals(ChainJournal.History.BOTH_LOST, o.history());
        assertTrue(s.permanentlyFenced(k));
    }

    /** CDX-72: a module leaving a rewind keeps the rewind's evidence. */
    @Test
    public void leavingARewindKeepsItsEvidence() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 400, 400);
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 500)), 400, 400);
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.expired, null));
        j(s, MOD_B).open(() -> 480, h -> null);
        var r = j(s, MOD_B).record().rewind();
        j(s, MOD_B).endRewind(r.id());
        assertEquals(r.evidence(), j(s, MOD_B).record().rewind().evidence());
    }

    /** DES-20: the store is this user's, independent of the IDE product and version, so an upgrade keeps it. */
    @Test
    public void theStoreIsPerUserNotPerIde() {
        Path root = JournalStore.forUser().root();
        assertEquals(Path.of(System.getProperty("user.home"), ".intelliada", "joust-journal").toAbsolutePath().normalize(), root);
        assertFalse(root.startsWith(Path.of(com.intellij.openapi.application.PathManager.getSystemPath()).toAbsolutePath().normalize()));
    }

    // ---- round 10 (M33B1-CDX-78, -80, -81) ------------------------------------------------------------------------------

    /** CDX-78: the same fork seen again after a module acknowledged the rewind renews it for that module. */
    @Test
    public void theSameForkAfterAnAcknowledgementRenewsTheRewind() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        JournalStore.Inclusion x = new JournalStore.Inclusion(450, 450, "11".repeat(32));
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.confirmed, 450L).withInclusion(x));
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 900)), 400, 400);
        j(s, MOD_B).open(() -> 600, h -> null);   // A's inclusion absent: rewind R, A and B pending
        var r = j(s, MOD_B).record().rewind();
        assertTrue(r.pending().contains(MOD_A));
        j(s, MOD_A).endRewind(r.id());            // A restored against the block, which had come back
        j(s, MOD_B).open(() -> 600, h -> null);   // gone again: the same evidence R holds
        var again = j(s, MOD_B).record().rewind();
        assertFalse("a new rewind", r.id().equals(again.id()));
        assertTrue("A reconciles again", again.pending().contains(MOD_A));
    }

    /** CDX-80, DES-22: a linked parent is refused; a loose one of this user's is tightened. */
    @Test
    public void aLooseOrLinkedParentIsRefused() throws Exception {
        Path parent = tmp.newFolder("loose").toPath();
        if (!Files.getFileStore(parent).supportsFileAttributeView("posix")) return;
        Files.setPosixFilePermissions(parent, java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxrwx"));
        new JournalStore(parent.resolve("journal")).admitStoreCheckForTests();
        assertEquals("a loose parent of this user's is tightened (DES-22)", "rwxr-xr-x",
                java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(parent)));
        Path real = tmp.newFolder("real").toPath();
        Path link = Files.createSymbolicLink(tmp.getRoot().toPath().resolve("link"), real);
        try {
            new JournalStore(link.resolve("journal")).admitStoreCheckForTests();
            fail("a linked parent");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("symbolic link"));
        }
        Path fresh = tmp.getRoot().toPath().resolve("fresh");
        new JournalStore(fresh.resolve("journal")).admitStoreCheckForTests();
        assertEquals("a parent it creates is private", "rwx------",
                java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(fresh)));
    }

    /** CDX-81: a version that narrows to 1 is not version 1. */
    @Test
    public void aVersionThatNarrowsToOneIsRefused() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 0, 0);
        Path rec = s.recordFile(JournalStore.key(CHAIN));
        String json = Files.readString(rec);
        String changed = json.replaceFirst("\"version\"\\s*:\\s*1\\b", "\"version\":4294967297");
        assertFalse("the version was replaced", changed.equals(json));
        Files.writeString(rec, changed);
        assertFalse(s.record(JournalStore.key(CHAIN)).ok());
    }

    // ---- round 11 (M33B1-CDX-84, -86) ----------------------------------------------------------------------------------

    /** CDX-84: a rewind whose write failed still refuses another module's admission, and is persisted by a retry. */
    @Test
    public void anUnsavedRewindStopsOtherModulesUntilPersisted() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        s.failWritesForTests = p -> p.getFileName().toString().startsWith("record");
        try {
            j(s, MOD_A).beginRewind(100, "the clock went back");
            fail("the write was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        try {
            j(s, MOD_B).admit(submitted("2", tx(List.of(ref(2, 0)), 900)), 400, 400);
            fail("admitted while a rewind was unsaved");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("rewind"));
        }
        assertNotNull(j(s, MOD_B).unsavedRewind());
        s.failWritesForTests = p -> false;
        assertNotNull("the retry reports it", j(s, MOD_B).unsavedRewind());
        assertNotNull("and persisted it", j(s, MOD_B).record().rewind());
        assertNull(j(s, MOD_B).unsavedRewind());
    }

    /** CDX-86: a refused parent's reason reaches the module lock's refusal. */
    @Test
    public void aLooseParentsReasonReachesTheLockRefusal() throws Exception {
        Path real = tmp.newFolder("real2").toPath();
        Path link = Files.createSymbolicLink(tmp.getRoot().toPath().resolve("link2"), real);
        try {
            new JournalStore(link.resolve("journal")).ownModule(JournalStore.key(CHAIN), MOD_A);
            fail("a linked parent");
        } catch (JournalStore.StoreFailed expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("is a symbolic link"));
        }
    }

    /** A principal with an identity apart from its display name (on Windows, its security identifier). */
    private record P(String name, String sid) implements java.nio.file.attribute.UserPrincipal {
        @Override
        public String getName() {
            return name;
        }
    }

    private static java.nio.file.attribute.AclEntry write(java.nio.file.attribute.UserPrincipal p) {
        return java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(p)
                .setPermissions(java.nio.file.attribute.AclEntryPermission.ADD_FILE, java.nio.file.attribute.AclEntryPermission.DELETE_CHILD).build();
    }

    /** CDX-85, -91: an ACL parent may grant writes only to this user and trusted identities, compared by identity. */
    @Test
    public void anAclParentIsJudgedByIdentityNotName() throws Exception {
        Path d = Path.of("C:/Users/me/.intelliada");
        P me = new P("HOST\\me", "S-1-5-21-1-1001"), admins = new P("BUILTIN\\Administrators", "S-1-5-32-544");
        P lookalike = new P("CORP\\Administrators", "S-1-5-21-9-1234"), users = new P("BUILTIN\\Users", "S-1-5-32-545");
        JournalStore.aclCheck(d, me, me, List.of(write(me), write(admins)), Set.of(admins));
        for (P other : List.of(lookalike, users)) {
            try {
                JournalStore.aclCheck(d, me, me, List.of(write(me), write(other)), Set.of(admins));
                fail(other.name() + " was trusted");
            } catch (java.io.IOException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains(other.name()));
            }
        }
        try {
            JournalStore.aclCheck(d, users, me, List.of(), Set.of(admins));
            fail("another owner");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("another user"));
        }
    }

    // ---- round 12 (M33B1-CDX-87..90, -94) ------------------------------------------------------------------------------

    private void failRecordWrites(JournalStore s, boolean fail) {
        s.failWritesForTests = fail ? p -> p.getFileName().toString().startsWith("record") : p -> false;
    }

    /** CDX-87: a rewind found by an open whose record write fails is still held; other modules refuse. */
    @Test
    public void aRewindFoundByAnOpenIsHeldWhenItsWriteFails() throws Exception {
        JournalStore s = store();
        JournalStore.Inclusion x = new JournalStore.Inclusion(450, 450, "11".repeat(32));
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        j(s, MOD_A).update("1", e -> e.withState(JournalStore.State.confirmed, 450L).withInclusion(x));
        failRecordWrites(s, true);
        try {
            j(s, MOD_B).open(() -> 600, h -> null);   // A's final inclusion is gone
            fail("the write was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        assertNotNull(j(s, MOD_A).unsavedReason());
        failRecordWrites(s, false);
        try {
            j(s, MOD_A).admit(submitted("2", tx(List.of(ref(3, 0)), 900)), 600, 600);
            fail("admitted");
        } catch (ChainJournal.Fenced expected) {
        }
        assertTrue("the retry recorded it with A pending", j(s, MOD_A).record().rewind().pending().contains(MOD_A));
    }

    /** CDX-89: an approval is not promoted while a rewind is unsaved. */
    @Test
    public void anApprovalIsNotHandedOutDuringAnUnsavedRewind() throws Exception {
        JournalStore s = store();
        j(s, MOD_B).admit(Bodies.entry("1", tx(List.of(ref(1, 0)), 900), JournalStore.Handout.approvalPending), 400, 400);
        failRecordWrites(s, true);
        try {
            j(s, MOD_A).beginRewind(100, "the clock went back");
            fail("the write was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        try {
            j(s, MOD_B).handout("1", JournalStore.Handout.exported, 400);
            fail("handed out");
        } catch (ChainJournal.Fenced expected) {
        }
        assertEquals(JournalStore.Handout.approvalPending, j(s, MOD_B).entries().get(0).handout());
        failRecordWrites(s, false);
        j(s, MOD_B).unsavedRewind();
    }

    /** CDX-90: pruning refuses while a rewind is unsaved. */
    @Test
    public void pruningRefusesDuringAnUnsavedRewind() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 500)), 0, 0);
        long seen = s.record(JournalStore.key(CHAIN)).value().rewinds();
        failRecordWrites(s, true);
        try {
            j(s, MOD_B).beginRewind(480, "the clock went back");
            fail("the write was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        assertFalse(j(s, MOD_A).remove(Set.of("1"), seen));
        assertEquals(1, j(s, MOD_A).entries().size());
        failRecordWrites(s, false);
        j(s, MOD_A).unsavedRewind();
    }

    /** CDX-94: a module that only asks never becomes pending in the rewind it persists. */
    @Test
    public void aRetryDoesNotMakeTheAskerPending() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        failRecordWrites(s, true);
        try {
            j(s, MOD_A).beginRewind(100, "the clock went back");
            fail("the write was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        failRecordWrites(s, false);
        assertNotNull(j(s, MOD_B).unsavedRewind());
        var r = j(s, MOD_B).record().rewind();
        assertTrue(r.pending().contains(MOD_A));
        assertFalse("B has no journal and did not detect it", r.pending().contains(MOD_B));
        assertNull(j(s, MOD_B).unsavedReason());
    }

    // ---- round 13 (M33B1-CDX-96, -99, -100) ----------------------------------------------------------------------------

    /** CDX-96: an empty access list (how the JDK shows a null DACL too) is never private. */
    @Test
    public void anEmptyAccessListIsRefused() {
        P me = new P("HOST\\me", "S-1-5-21-1-1001");
        try {
            JournalStore.aclCheck(Path.of("C:/x"), me, me, List.of(), Set.of());
            fail("an empty ACL");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("empty or null"));
        }
    }

    /** CDX-99: two retries of one unsaved rewind record it once. */
    @Test
    public void twoRetriesRecordOneRewind() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        long before = s.record(JournalStore.key(CHAIN)).value().rewinds();
        failRecordWrites(s, true);
        try {
            j(s, MOD_A).beginRewind(100, "the clock went back");
            fail("the write was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        failRecordWrites(s, false);
        j(s, MOD_A).unsavedRewind();
        j(s, MOD_B).unsavedRewind();
        assertEquals(before + 1, s.record(JournalStore.key(CHAIN)).value().rewinds());
    }

    /** CDX-100: a rewind whose record was written but not its registry entry is completed by the retry. */
    @Test
    public void aRetryCompletesTheRegistryWrite() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        j(s, MOD_A).remove(Set.of("1"));
        Path reg = s.registryFile(k).getFileName();
        s.failWritesForTests = p -> p.getFileName().equals(reg);
        try {
            j(s, MOD_A).beginRewind(100, "the clock went back");
            fail("the registry write was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        s.failWritesForTests = p -> false;
        j(s, MOD_B).unsavedRewind();
        assertNull(j(s, MOD_B).unsavedReason());
        assertFalse("no loss of the mark", j(s, MOD_B).open(500).history() == ChainJournal.History.LOST_MARK);
    }

    // ---- round 14 (M33B1-CDX-100, -101, -103) --------------------------------------------------------------------------

    /** CDX-100: on a new chain, a first rewind whose registry write failed is completed without a lost mark. */
    @Test
    public void aFirstRewindsRegistryWriteIsCompletedOnANewChain() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        Path reg = s.registryFile(k).getFileName();
        s.failWritesForTests = p -> p.getFileName().equals(reg);
        try {
            j(s, MOD_A).beginRewind(100, "the clock went back");
            fail("the registry write was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        assertTrue("the record was written", Files.exists(s.recordFile(k)));
        s.failWritesForTests = p -> false;
        j(s, MOD_A).unsavedRewind();
        assertNull(j(s, MOD_A).unsavedReason());
        assertTrue(Files.exists(s.registryFile(k)));
        assertFalse("no loss of the mark", j(s, MOD_A).open(500).history() == ChainJournal.History.LOST_MARK);
    }

    /** CDX-101: a record rebuilt after its loss carries a rewind count no host saw. */
    @Test
    public void aRebuiltRecordCountsAsARewind() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        long seen = s.record(k).value().rewinds();
        Files.delete(s.recordFile(k));
        assertEquals(ChainJournal.History.LOST_MARK, j(s, MOD_B).open(500).history());
        assertNotEquals(seen, s.record(k).value().rewinds());
    }

    /** CDX-103: a directory of the store that is a link is refused, on reads too. */
    @Test
    public void aLinkedStoreDirectoryIsRefused() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        Path regDir = s.registryFile(k).getParent();
        Path moved = tmp.getRoot().toPath().resolve("elsewhere");
        Files.move(regDir, moved);
        Files.createSymbolicLink(regDir, moved);
        try {
            j(s, MOD_A).open(500);
            fail("read through a link");
        } catch (JournalStore.StoreFailed expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("link"));
        }
    }

    /** CDX-106: a record rebuilt over a stale one takes a fresh rewind count. */
    @Test
    public void aRebuildOverAStaleRecordTakesAFreshCount() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        byte[] old = Files.readAllBytes(s.recordFile(k));
        long seen = s.record(k).value().rewinds();
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 900)), 400, 400);
        Files.write(s.recordFile(k), old);
        assertEquals(ChainJournal.History.LOST_MARK, j(s, MOD_A).open(500).history());
        assertNotEquals(seen, s.record(k).value().rewinds());
    }

    // ---- round 16 (M33B1-CDX-112, -113) --------------------------------------------------------------------------------

    /** CDX-112: a second rewind after a first one's failed registry write completes that write first. */
    @Test
    public void aSecondRewindCompletesTheFirstRegistryWrite() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        Path reg = s.registryFile(k).getFileName();
        s.failWritesForTests = p -> p.getFileName().equals(reg);
        try {
            j(s, MOD_A).beginRewind(100, "the first");
            fail("the registry write was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        s.failWritesForTests = p -> false;
        j(s, MOD_B).beginRewind(90, "a further rollback");
        assertTrue(Files.exists(s.registryFile(k)));
        assertNull(j(s, MOD_B).unsavedReason());
        assertFalse("no loss of the mark", j(s, MOD_B).open(500).history() == ChainJournal.History.LOST_MARK);
    }

    /** CDX-113: the trust snapshot never parses module journals. */
    @Test
    public void theSnapshotReadsNoJournal() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        Files.writeString(s.journalFile(k, MOD_A), "not json");   // a parse would see a lost journal
        ChainJournal.Snapshot snap = j(s, MOD_B).snapshot();
        assertNotNull(snap.record());
        assertNull(snap.lost());
    }

    // ---- round 17 (M33B1-CDX-115, -118, -119) --------------------------------------------------------------------------

    /** CDX-115: a readable record whose registry entry is lost is not trusted, as classify judges it. */
    @Test
    public void aRecordWithoutItsRegistryIsNotTrusted() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        Files.delete(s.registryFile(k));
        assertNotNull(j(s, MOD_B).snapshot().lost());
    }

    /** CDX-118: retrying the same registration after a failed registry write finishes that write. */
    @Test
    public void aRetriedRegistrationFinishesItsRegistryWrite() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        JournalStore.Registration r = new JournalStore.Registration("cd".repeat(28), "ab".repeat(32), 10L, null);
        Path reg = s.registryFile(k).getFileName();
        s.failWritesForTests = p -> p.getFileName().equals(reg);
        try {
            j(s, MOD_A).learn(r);
            fail("the registry write was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        s.failWritesForTests = p -> false;
        j(s, MOD_A).learn(r);
        assertTrue(Files.exists(s.registryFile(k)));
        assertFalse(j(s, MOD_A).open(500).history() == ChainJournal.History.LOST_MARK);
    }

    /** CDX-119: an empty generation-zero journal with no record is a new chain to the snapshot too. */
    @Test
    public void anEmptyJournalAloneIsANewChain() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        s.writeJournal(new JournalStore.ModuleJournal(CHAIN, MOD_A, 0, List.of()));
        assertEquals(ChainJournal.History.NEW_CHAIN, j(s, MOD_B).open(500).history());
        assertNull(j(s, MOD_B).snapshot().lost());
    }

    // ---- round 18 (M33B1-CDX-122, -123, -124) --------------------------------------------------------------------------

    /** CDX-122: a sync that failed after its rename makes every later locked operation sync it first, or fail. */
    @Test
    public void aFailedSyncBlocksUntilItIsRepeated() throws Exception {
        JournalStore s = store();
        s.failSyncsForTests = true;
        try {
            j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
            fail("the sync was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        try {
            j(s, MOD_B).snapshot();
            fail("an operation over a change that is not durable");
        } catch (JournalStore.StoreFailed expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("durable"));
        }
        s.failSyncsForTests = false;
        assertNotNull(j(s, MOD_B).snapshot());
    }

    /** CDX-123: an open after a registration's failed registry write finishes it rather than recovering. */
    @Test
    public void anOpenFinishesAPendingRegistryWrite() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        Path reg = s.registryFile(k).getFileName();
        s.failWritesForTests = p -> p.getFileName().equals(reg);
        try {
            j(s, MOD_A).learn(new JournalStore.Registration("cd".repeat(28), "ab".repeat(32), 10L, null));
            fail("the registry write was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        s.failWritesForTests = p -> false;
        assertFalse(j(s, MOD_B).open(500).history() == ChainJournal.History.LOST_MARK);
        assertTrue(Files.exists(s.registryFile(k)));
    }

    /** CDX-124: a generation-zero record without a registry is trusted, as classify accepts it. */
    @Test
    public void aGenerationZeroRecordWithoutARegistryIsTrusted() throws Exception {
        JournalStore s = store();
        s.writeRecord(new JournalStore.ChainRecord(CHAIN, null, java.util.Map.of(), 0, null, null, List.of()));
        assertNull(j(s, MOD_A).snapshot().lost());
    }

    // ---- round 19 (M33B1-CDX-127..130) ---------------------------------------------------------------------------------

    /** CDX-127: a generation-zero record that counts rewinds is not trusted. */
    @Test
    public void aGenerationZeroRecordCountingRewindsIsLostHistory() throws Exception {
        JournalStore s = store();
        s.writeRecord(new JournalStore.ChainRecord(CHAIN, null, java.util.Map.of(), 0, null, null, List.of(), null, 3));
        assertEquals(ChainJournal.History.LOST_MARK, j(s, MOD_A).open(500).history());
    }

    /** CDX-128: two spellings of one store share one unsaved rewind. */
    @Test
    public void twoSpellingsOfOneStoreShareTheUnsavedRewind() throws Exception {
        Path real = tmp.newFolder("realhome").toPath();
        Path alias = Files.createSymbolicLink(tmp.getRoot().toPath().resolve("aliashome"), real);
        Files.createDirectories(real.resolve("p"));
        JournalStore a = new JournalStore(real.resolve("p").resolve("j")), b = new JournalStore(alias.resolve("p").resolve("j"));
        assertEquals(a.identity(), b.identity());
        j(a, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        a.failWritesForTests = p -> p.getFileName().toString().startsWith("record");
        try {
            j(a, MOD_A).beginRewind(100, "the clock went back");
            fail("the write was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        assertNotNull("seen through the other spelling", j(b, MOD_B).snapshot().unsaved());
        a.failWritesForTests = p -> false;
        j(a, MOD_A).unsavedRewind();
    }

    /** CDX-129, -130: a re-entered lock still owes the sync; another chain does not. */
    @Test
    public void pendingSyncsBindTheirChainAndReentry() throws Exception {
        JournalStore s = store();
        String other = "devnet-43-" + "cd".repeat(32);
        s.failSyncsForTests = true;
        try {
            j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
            fail("the sync was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        assertNotNull("another chain is not blocked", new ChainJournal(s, other, MOD_A, 60).snapshot());
        try (var held = s.lock(JournalStore.key(CHAIN))) {
            fail("the outer lock owes the sync");
        } catch (JournalStore.StoreFailed expected) {
        }
        s.failSyncsForTests = false;
        try (var held = s.lock(JournalStore.key(CHAIN))) {
            // A sync fails while the outer lock is held: the re-entered lock owes it (CDX-129).
            s.failSyncsForTests = true;
            try {
                j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 900)), 400, 400);
                fail("the sync was to fail");
            } catch (JournalStore.StoreFailed expected) {
            }
            try {
                j(s, MOD_B).snapshot();
                fail("re-entry over a change that is not durable");
            } catch (JournalStore.StoreFailed expected) {
            }
            s.failSyncsForTests = false;
            assertNotNull(j(s, MOD_B).snapshot());
        } finally {
            s.failSyncsForTests = false;
        }
    }
}
