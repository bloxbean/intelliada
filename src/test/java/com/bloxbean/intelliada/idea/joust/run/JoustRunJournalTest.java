package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.joust.journal.JournalStore;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** ADR-005 D17 (M3.3b1): which forward calls wait for a settlement pass (M33B1-DES-10). */
public class JoustRunJournalTest {

    private static JournalStore.Entry entry(JournalStore.State st) {
        String cbor = "84a20081825820" + "11".repeat(32) + "00031864a0f5f6";
        return new JournalStore.Entry("~x", "aa".repeat(32), cbor, null, 100, JournalStore.Handout.submitted, "n", st,
                st == JournalStore.State.confirmed ? 50L : null, null, List.of(), "x");
    }

    @Test
    public void onlyAReservationThatCanStillMoveHoldsACallBack() {
        assertFalse("final entries kept for their retention never hold a view back",
                JoustRunJournal.mustSettleFirst(List.of(entry(JournalStore.State.confirmed), entry(JournalStore.State.expired)), false));
        assertTrue(JoustRunJournal.mustSettleFirst(List.of(entry(JournalStore.State.prepared)), false));
        assertTrue(JoustRunJournal.mustSettleFirst(List.of(entry(JournalStore.State.shallow)), false));
        assertTrue("an unreported inclusion", JoustRunJournal.mustSettleFirst(List.of(), true));
        assertFalse(JoustRunJournal.mustSettleFirst(List.of(), false));
    }
}
