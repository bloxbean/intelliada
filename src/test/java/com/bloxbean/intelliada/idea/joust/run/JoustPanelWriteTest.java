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
        assertTrue(text, text.contains("Your net outflow: unknown"));
    }
}
