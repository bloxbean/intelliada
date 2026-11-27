package com.bloxbean.intelliada.idea.joust.journal;

import com.bloxbean.intelliada.idea.joust.journal.HostTable.Expected;
import com.bloxbean.intelliada.idea.joust.journal.HostTable.St;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * ADR-005 I32 (M3.3b1): the model of the host's reservation table, by protocol 1's table ("Reservation journal (D8)")
 * with margin 60. Each expectation here is the table's row, written down from the protocol, not from the model.
 */
public class HostTableTest {

    static final String H = "aa".repeat(32);

    private static HostTable table(long from, long to) {
        HostTable t = new HostTable(60, false);
        t.committed("1", H, from, to);
        return t;
    }

    private static HostTable.Outcome lo(HostTable t, String status, Long slot, long tip) {
        return t.expect("1", H, status, slot, tip).lower();
    }

    @Test
    public void theProtocolsTable() {
        // prepared + confirmed inside the interval and at most the tip -> shallow
        assertEquals(St.shallow, lo(table(0, 2025), "confirmed", 2005L, 2005).state());
        // ... and the thresholds of the event's tip apply after it: final at once
        assertEquals(St.confirmed, lo(table(0, 2025), "confirmed", 2005L, 2066).state());
        // a slot outside [validFrom, validTo), or after the tip: conflict
        assertEquals("bad/conflict", lo(table(1990, 2025), "confirmed", 1989L, 2000).error());
        assertEquals("bad/conflict", lo(table(0, 2025), "confirmed", 2025L, 2030).error());
        assertEquals("bad/conflict", lo(table(0, 2025), "confirmed", 2010L, 2009).error());
        // prepared + rolledBack -> prepared; expiry only past the margin
        assertEquals(St.prepared, lo(table(0, 2025), "rolledBack", null, 2030).state());
        assertEquals("bad/notExpired", lo(table(0, 2025), "expired", null, 2085).error());
        assertEquals(St.expired, lo(table(0, 2025), "expired", null, 2086).state());
        // discarded only from prepared
        assertEquals(St.discarded, lo(table(0, 2025), "discarded", null, 2000).state());
        // an unknown reservation, another body
        assertEquals("bad/unknownReservation", table(0, 2025).expect("9", H, "expired", null, 3000).lower().error());
        assertEquals("bad/conflict", table(0, 2025).expect("1", "bb".repeat(32), "expired", null, 3000).lower().error());
    }

    @Test
    public void shallowAndFinalRows() {
        HostTable t = table(0, 2025);
        Expected x = t.expect("1", H, "confirmed", 2005L, 2005);
        assertTrue(t.reply(x, St.shallow, null));
        assertEquals("confirmed again at the same slot: shallow", St.shallow, lo(t, "confirmed", 2005L, 2006).state());
        assertEquals("competing evidence: conflict", "bad/conflict", lo(t, "confirmed", 2006L, 2006).error());
        assertEquals("a shallow body is not discarded", "bad/conflict", lo(t, "discarded", null, 2006).error());
        // the depth threshold: strictly greater than slot + margin
        t.accepted(2065);
        assertEquals(St.shallow, t.lower("1").st());
        t.accepted(2066);
        assertEquals(St.confirmed, t.lower("1").st());
        assertEquals("a repeat final status is accepted", St.confirmed, lo(t, "confirmed", 2005L, 2100).state());
        assertEquals("a final inclusion rolled back: deepRollback", "refusal/deepRollback", lo(t, "rolledBack", null, 2100).error());
        assertTrue(t.reply(t.expect("1", H, "rolledBack", null, 2100), null, "refusal/deepRollback"));
        assertTrue(t.deepRollback());
    }

    @Test
    public void aTipAppliesToEveryReservationAfterTheEvent() {
        HostTable t = new HostTable(60, false);
        t.committed("1", H, 0L, 1886);
        t.committed("2", "bb".repeat(32), 0L, 1890);
        // Reporting the first inclusion at the poll tip 1951 would expire the other body, which landed too.
        assertTrue(t.reply(t.expect("1", H, "confirmed", 1871L, 1951), St.confirmed, null));
        assertEquals(St.expired, t.lower("2").st());
    }

    @Test
    public void nothingAdvancesWhileRestoreIsOpen() {
        HostTable t = new HostTable(60, true);
        assertTrue(t.restored(List.of(new HostTable.Restored(H, null, 2025, St.prepared, null)), List.of("1"), false));
        t.accepted(5000);
        assertEquals(St.prepared, t.lower("1").st());
        assertEquals("expiry is not established while reconciling", "bad/notExpired", lo(t, "expired", null, 5000).error());
        assertTrue(t.restored(List.of(), List.of(), true));
        t.accepted(5000);
        assertEquals(St.expired, t.lower("1").st());
    }

    @Test
    public void restoreIdsAreChecked() {
        HostTable t = new HostTable(60, true);
        var a = new HostTable.Restored(H, null, 2025, St.prepared, null);
        var b = new HostTable.Restored("bb".repeat(32), null, 2025, St.shallow, 2005L);
        assertFalse("one id per transaction", t.restored(List.of(a, b), List.of("1"), false));
        assertFalse("distinct bodies need distinct ids", t.restored(List.of(a, b), List.of("1", "1"), false));
        assertTrue("identical bodies share one id", t.restored(List.of(a, a, b), List.of("1", "1", "2"), false));
        assertFalse("a known body keeps its id", t.restored(List.of(a), List.of("3"), false));
        assertTrue("restore is closed after complete", t.restored(List.of(), List.of(), true));
        assertFalse(t.restored(List.of(), List.of(), true));
    }

    @Test
    public void anUncertainTipIsHeldInTheUpperCopyUntilAReplySettlesIt() {
        HostTable t = table(0, 2025);
        assertTrue(t.reply(t.expect("1", H, "confirmed", 2005L, 2005), St.shallow, null));
        t.uncertain(2100);   // a partial listing, or a call that ended in bad/readLimit
        assertEquals("the journal keeps the less advanced state", St.shallow, t.lower("1").st());
        assertEquals(St.confirmed, t.upper("1").st());
        Expected x = t.expect("1", H, "confirmed", 2005L, 2006);
        assertFalse(x.certain());
        // The real host answers shallow (it never accepted 2100): matches the lower copy; no fence.
        assertTrue(t.reply(x, St.shallow, null));
        assertEquals(St.shallow, t.upper("1").st());
    }

    @Test
    public void aHostThatAcceptedTheUncertainTipMatchesTheUpperCopy() {
        HostTable t = table(0, 2025);
        assertTrue(t.reply(t.expect("1", H, "confirmed", 2005L, 2005), St.shallow, null));
        t.uncertain(2100);   // a utxos value accepted before the call ended internal/chainMoved
        assertTrue(t.reply(t.expect("1", H, "confirmed", 2005L, 2006), St.confirmed, null));
        assertEquals(St.confirmed, t.lower("1").st());
    }

    @Test
    public void aReplyMatchingNeitherCopyIsRefused() {
        HostTable t = table(0, 2025);
        // One slot after the inclusion, a host answering confirmed is wrong: the caller fences it.
        assertFalse(t.reply(t.expect("1", H, "confirmed", 2005L, 2006), St.confirmed, null));
        assertFalse(t.reply(t.expect("1", H, "confirmed", 2005L, 2006), null, "bad/conflict"));
        assertEquals("the reservation is kept", St.prepared, t.lower("1").st());
    }
}
