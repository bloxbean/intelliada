package com.bloxbean.intelliada.idea.joust.journal;

import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Entry;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Handout;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.State;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static com.bloxbean.intelliada.idea.joust.journal.Bodies.*;
import static org.junit.Assert.*;

/**
 * ADR-005 D17 as simplified by §12 E12 and L44 (M3.3b1): one module's journal on a chain is a history and restart aid
 * that never blocks a developer. A missing journal is a new start; a damaged one is moved aside; only live bodies hold
 * their inputs; one host per module and chain.
 */
public class ChainJournalTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    static final String CHAIN = "devnet-42-" + "ab".repeat(32);
    static final String TESTNET = "preprod-1-" + "cd".repeat(32);
    static final String MOD_A = JournalStore.key("/work/a");
    static final String MOD_B = JournalStore.key("/work/b");

    private JournalStore store() throws Exception {
        return new JournalStore(tmp.newFolder().toPath().resolve("joust-journal"));
    }

    private static ChainJournal j(JournalStore s, String module) {
        return new ChainJournal(s, CHAIN, module, 60);
    }

    private static String ref(int n, int i) {
        return txid(n) + "#" + i;
    }

    // ---- opening ------------------------------------------------------------------------------------------------------

    @Test
    public void aMissingJournalIsANewStart() throws Exception {
        ChainJournal.Opening o = j(store(), MOD_A).open();
        assertEquals(ChainJournal.History.NEW_CHAIN, o.history());
        assertFalse(o.restoring());
        assertTrue(o.survivors().isEmpty());
        assertNull(o.note());
    }

    @Test
    public void aJournalWithEntriesIsRestored() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        ChainJournal.Opening o = j(s, MOD_A).open();
        assertEquals(ChainJournal.History.COMPLETE, o.history());
        assertTrue(o.restoring());
        assertEquals(1, o.survivors().size());
    }

    /** A damaged journal never blocks a developer: it is moved aside, said once, and a new one begun. */
    @Test
    public void aDamagedJournalIsMovedAsideAndANewOneBegun() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        Path f = s.journalFile(JournalStore.key(CHAIN), MOD_A);
        Files.writeString(f, "{broken");
        ChainJournal.Opening o = j(s, MOD_A).open();
        assertEquals(ChainJournal.History.SET_ASIDE, o.history());
        assertFalse(o.restoring());
        assertNotNull(o.note());
        assertTrue(o.note(), o.note().contains("moved aside"));
        assertFalse("the damaged file is gone from its place", Files.exists(f));
        try (var files = Files.list(f.getParent())) {
            assertTrue("and kept beside it", files.anyMatch(p -> p.getFileName().toString().startsWith(f.getFileName() + ".lost-")));
        }
        // The next body is journaled as on a new start.
        j(s, MOD_A).admit(submitted("2", tx(List.of(ref(2, 0)), 900)), 400, 400);
        assertEquals(1, j(s, MOD_A).entries().size());
    }

    /** A journal of another format version is moved aside too (no version migration, no permanent fence). */
    @Test
    public void anOtherVersionIsMovedAside() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        Path f = s.journalFile(JournalStore.key(CHAIN), MOD_A);
        Files.writeString(f, Files.readString(f).replaceFirst("\"version\"\\s*:\\s*2", "\"version\" : 3"));
        assertEquals(ChainJournal.History.SET_ASIDE, j(s, MOD_A).open().history());
        assertEquals(ChainJournal.History.NEW_CHAIN, j(s, MOD_A).open().history());
    }

    /** Each module opens only its own journal: another module's damage is not this one's concern. */
    @Test
    public void anotherModulesDamageIsNotThisOnes() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 900)), 400, 400);
        Files.writeString(s.journalFile(JournalStore.key(CHAIN), MOD_B), "{broken");
        ChainJournal.Opening o = j(s, MOD_A).open();
        assertEquals(ChainJournal.History.COMPLETE, o.history());
        assertEquals(1, o.survivors().size());
    }

    // ---- admission ----------------------------------------------------------------------------------------------------

    @Test
    public void anAdmittedEntryReadsBack() throws Exception {
        JournalStore s = store();
        Entry e = submitted("1", tx(List.of(ref(1, 0)), 900));
        j(s, MOD_A).admit(e, 400, 400);
        assertEquals(List.of(e), j(s, MOD_A).entries());
    }

    /** I33: another module's live body holding an input refuses a second spend of it. */
    @Test
    public void aLiveBodyHoldsItsInputsAcrossModules() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        try {
            j(s, MOD_B).admit(submitted("1", tx(List.of(ref(1, 0)), 950)), 400, 400);
            fail("a second spend of a reserved input");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("reserved"));
        }
    }

    /** Fable S11: a final or expired body is history; it holds no inputs (FAB-4: nothing is reserved forever). */
    @Test
    public void aFinalBodyHoldsNoInputs() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        j(s, MOD_A).update("1", e -> e.withState(State.expired, null));
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(1, 0)), 950)), 400, 400);
        assertEquals(1, j(s, MOD_B).entries().size());
    }

    @Test
    public void theSameTransactionIsRefused() throws Exception {
        JournalStore s = store();
        String body = tx(List.of(ref(1, 0)), 900);
        j(s, MOD_A).admit(submitted("1", body), 400, 400);
        try {
            j(s, MOD_B).admit(submitted("7", body), 400, 400);
            fail("the same transaction twice");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("same transaction"));
        }
    }

    /** Another module's damaged journal is skipped by the overlap check, never a refusal of every admission. */
    @Test
    public void aDamagedJournalDoesNotBlockAdmission() throws Exception {
        JournalStore s = store();
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 900)), 400, 400);
        Files.writeString(s.journalFile(JournalStore.key(CHAIN), MOD_B), "{broken");
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        assertEquals(1, j(s, MOD_A).entries().size());
    }

    @Test
    public void aBodyValidTooFarAheadIsRefused() throws Exception {
        JournalStore s = store();
        try {
            j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 400 + TimePolicy.LARGEST_TTL + 1)), 400, 400);
            fail("valid beyond what a host builds");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("further ahead"));
        }
    }

    @Test
    public void aReusedReservationIdIsRefused() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        try {
            j(s, MOD_A).admit(submitted("1", tx(List.of(ref(2, 0)), 900)), 400, 400);
            fail("a reused id");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("reused"));
        }
    }

    @Test
    public void aFailedWriteJournalsNothing() throws Exception {
        JournalStore s = store();
        s.failWritesForTests = p -> true;
        try {
            j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
            fail("the write was to fail");
        } catch (JournalStore.StoreFailed expected) {
        }
        s.failWritesForTests = p -> false;
        assertTrue(j(s, MOD_A).entries().isEmpty());
    }

    // ---- handout, retention, updates ----------------------------------------------------------------------------------

    @Test
    public void anApprovalMovesForwardOnce() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(entry("1", tx(List.of(ref(1, 0)), 900), Handout.approvalPending), 400, 400);
        j(s, MOD_A).handout("1", Handout.exported);
        assertEquals(Handout.exported, j(s, MOD_A).entries().get(0).handout());
        try {
            j(s, MOD_A).handout("1", Handout.submitted);
            fail("a handout moved twice");
        } catch (ChainJournal.Fenced expected) {
        }
    }

    @Test
    public void retentionCountsFromTheLaterOfInclusionAndValidity() throws Exception {
        ChainJournal j = j(store(), MOD_A);
        Entry e = submitted("1", tx(List.of(ref(1, 0)), 500));
        assertFalse("live is never over", j.retentionOver(e, 1_000_000));
        Entry confirmed = e.withState(State.confirmed, 450L);
        assertFalse(j.retentionOver(confirmed, 500 + 120));
        assertTrue(j.retentionOver(confirmed, 500 + 121));
        assertTrue("discarded leaves at once", j.retentionOver(e.withState(State.discarded, null), 0));
    }

    @Test
    public void updatesAndRemovalsAreThisModulesOnly() throws Exception {
        JournalStore s = store();
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        j(s, MOD_B).admit(submitted("1", tx(List.of(ref(2, 0)), 900)), 400, 400);
        j(s, MOD_A).update("1", e -> e.withEvent("seen"));
        j(s, MOD_A).remove(Set.of("1"));
        assertTrue(j(s, MOD_A).entries().isEmpty());
        assertEquals(1, j(s, MOD_B).entries().size());
        assertEquals(1, j(s, MOD_B).liveOnChain().size());
    }

    // ---- the store ----------------------------------------------------------------------------------------------------

    /** I33: one host per module and chain, in this IDE and across IDEs. */
    @Test
    public void oneHostPerModuleAndChain() throws Exception {
        JournalStore s = store();
        String k = JournalStore.key(CHAIN);
        try (var held = s.ownModule(k, MOD_A)) {
            try {
                s.ownModule(k, MOD_A);
                fail("a second host of one module");
            } catch (JournalStore.StoreFailed expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("another dev host"));
            }
            try (var other = s.ownModule(k, MOD_B)) {
                assertNotNull("another module runs beside it", other);
            }
        }
        try (var again = s.ownModule(k, MOD_A)) {
            assertNotNull("free again once closed", again);
        }
    }

    /** FAB-1: a store behind a linked parent is simply used (no parent or ACL checks on a developer's own machine). */
    @Test
    public void aLinkedParentIsUsed() throws Exception {
        Path real = tmp.newFolder("real").toPath();
        Path link = Files.createSymbolicLink(tmp.getRoot().toPath().resolve("link"), real);
        JournalStore s = new JournalStore(link.resolve("joust-journal"));
        j(s, MOD_A).admit(submitted("1", tx(List.of(ref(1, 0)), 900)), 400, 400);
        assertEquals(1, j(s, MOD_A).entries().size());
    }

    @Test
    public void theStoreIsPerUserNotPerIde() {
        assertEquals(Path.of(System.getProperty("user.home"), ".intelliada", "joust-journal").toAbsolutePath().normalize(),
                JournalStore.forUser().root());
    }

    @Test
    public void devnetIsKnownByItsIdentity() {
        assertTrue(new ChainJournal(new JournalStore(tmp.getRoot().toPath()), CHAIN, MOD_A, 60).devnet());
        assertFalse(new ChainJournal(new JournalStore(tmp.getRoot().toPath()), TESTNET, MOD_A, 2160).devnet());
    }
}
