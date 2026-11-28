package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

/** ADR-005 M3.3b3: the call forms come from the description; History renders host and project strings literally (I15). */
public class JoustPanelWriteTest {

    private static final String X = """
            {"creations": [{"name": "init", "kind": "init", "args": []},
                           {"name": "open", "kind": "ownedInit", "args": [{"name": "owner"}], "payable": true}],
             "actions": [{"name": "deposit", "kind": "ownedAction", "args": [{"name": "amount"}], "payable": true},
                         {"name": "grant", "kind": "keyedAction", "args": []},
                         {"name": "close", "kind": "ownedDestroy", "args": []},
                         {"name": "<b>x</b>", "kind": "action", "args": []}]}""";

    @Test
    public void callsAreTheDescriptionsWithoutKeyedOnesAndDeployOffersInitOnly() throws Exception {
        var x = HostJson.parse(X.getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("init"), JoustCallDialog.calls(x, true).stream().map(JoustCallDialog.Call::name).toList());
        var all = JoustCallDialog.calls(x, false);
        assertEquals(List.of("init", "open", "deposit", "close", "<b>x</b>"), all.stream().map(JoustCallDialog.Call::name).toList());
        var deposit = all.get(2);
        assertTrue(deposit.needsInstance() && deposit.needsObject() && deposit.payable());
        assertEquals(List.of("amount"), deposit.args());
        assertFalse("an owned creation names no object yet", all.get(1).needsObject());
        assertFalse(all.get(0).needsInstance());
        assertFalse("I15: markup in a call name is not rendered", all.get(4).toString().contains("<"));
    }

    @Test
    public void historyIsNewestFirstAndRendersLabelsLiterally() {
        var a = new JournalStore.Entry("1", "aa".repeat(32), "84", null, 900, JournalStore.Handout.submitted, "n",
                JournalStore.State.prepared, null, null, List.of(), "init init");
        var b = new JournalStore.Entry("2", "bb".repeat(32), "84", null, 950, JournalStore.Handout.submitted, "n",
                JournalStore.State.shallow, 920L, new JournalStore.Inclusion(920, 9, "cc".repeat(32)),
                List.of("included@920"), "<html><b>action</b>\u001b[31m");
        String text = JoustRunPanel.historyText(List.of(a, b));
        String[] lines = text.split("\n");
        assertTrue(lines[0], lines[0].startsWith("shallow") && lines[0].contains("bb".repeat(32)) && lines[0].contains("slot 920"));
        assertTrue(lines[1], lines[1].startsWith("prepared") && lines[1].contains("valid until 900"));
        String shown = PlainText.literal(text, 200_000);
        assertFalse("I15: no markup", shown.contains("<html>") || shown.contains("<b>"));
        assertFalse("I15: no escape sequence", shown.contains("\u001b"));
        assertEquals("No transactions in this session's journal yet.", JoustRunPanel.historyText(List.of()));
    }

    /** FAB-30: a body CCL cannot read is still reviewed, with its details marked unavailable. */
    @Test
    public void anUnreadableBodyGetsAReducedReview() {
        var r = com.bloxbean.intelliada.idea.joust.review.JoustReview.reduced("init init", "ab".repeat(32), null, 900, List.of("genesis-1"), "CBOR x");
        String text = String.join("\n", com.bloxbean.intelliada.idea.joust.review.JoustReview.lines(r));
        assertTrue(text, text.contains("details unavailable: CBOR x") && text.contains("until slot 900") && text.contains("genesis-1"));
        assertTrue(text, text.contains("Your net outflow: unavailable") && text.contains("Fee: unavailable"));
        assertFalse("FAB-33: nothing is said about outputs it did not read", text.contains("Sends nothing") || text.contains("output(s)"));
    }

    /** D15 (M3.3b4): the ledger's "registered already" refusal, as DevKit's submit API relays it, names the credential. */
    @Test
    public void aRegisteredAlreadyRefusalNamesItsScriptCredentials() {
        String node = "the node refused the transaction (HTTP 400): {\"status_code\":400,\"message\":\"{\\\"contents\\\":{\\\"error\\\":"
                + "[\\\"ConwayCertsFailure (CertFailure (DelegFailure (StakeKeyRegisteredDELEG (ScriptHashObj (ScriptHash "
                + "\\\\\\\"dd15fe5da434a91d00aa7f3a6891b77a59cfd4e442506d47189b703d\\\\\\\")))))\\\"]}}\"}";
        assertEquals(List.of("dd15fe5da434a91d00aa7f3a6891b77a59cfd4e442506d47189b703d"), JoustRunSession.alreadyRegistered(node));
        assertEquals(List.of(), JoustRunSession.alreadyRegistered("the node refused the transaction (HTTP 400): ValueNotConservedUTxO"));
        assertEquals(List.of(), JoustRunSession.alreadyRegistered(null));
    }

    /** FAB-24, FAB-32: History keeps pruned final entries, marks a body that left while prepared as released, holds no CBOR. */
    @Test
    public void historyKeepsFinalEntriesMarksReleasedOnesAndHoldsNoBody() {
        var seen = new java.util.LinkedHashMap<String, JournalStore.Entry>();
        var done = new JournalStore.Entry("1", "aa".repeat(32), "84aa", null, 900, JournalStore.Handout.submitted, "n",
                JournalStore.State.confirmed, 880L, new JournalStore.Inclusion(880, 8, "cc".repeat(32)), List.of(), "init init");
        var pending = new JournalStore.Entry("2", "bb".repeat(32), "84bb", null, 950, JournalStore.Handout.submitted, "n",
                JournalStore.State.prepared, null, null, List.of(), "action reset");
        var h = JoustRunSession.mergeHistory(seen, List.of(done, pending));
        assertEquals(2, h.size());
        assertTrue("no body kept", h.stream().allMatch(e -> e.cbor().isEmpty()));
        h = JoustRunSession.mergeHistory(seen, List.of());   // both left the journal
        assertEquals("the pruned final entry stays", JournalStore.State.confirmed, h.get(0).state());
        assertEquals("the prepared one was released", JournalStore.State.discarded, h.get(1).state());
        assertTrue(h.get(1).events().contains("released"));
    }

    /** FAB-31: evidence comes only from a refusal naming a credential; the body check is the session's. */
    @Test
    public void aRefusalNamingTwoCredentialsListsBoth() {
        String two = "StakeKeyRegisteredDELEG (ScriptHashObj (ScriptHash \"" + "aa".repeat(28) + "\")) and "
                + "StakeKeyRegisteredDELEG (ScriptHashObj (ScriptHash \"" + "bb".repeat(28) + "\"))";
        assertEquals(List.of("aa".repeat(28), "bb".repeat(28)), JoustRunSession.alreadyRegistered(two));
    }
}
