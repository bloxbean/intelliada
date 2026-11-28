package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.joust.journal.InstanceStore;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/** ADR-005 D15 (M3.4a): the .jsc entry-point scan, and the recorded-instance store. */
public class JoustEntryMarkersTest {

    static final String COUNTER = """
            jsc 1
            package org.example.counter

            // action fake(x: Int) in a comment is no entry point
            /* view hidden(): Int
               destroy gone() */
            contract Counter(maxDatumBytes = 128) {
              state { count: Int, owner: Key }
              init() {
                return { count: 0, owner: caller }
              }
              action increment(by: Int) {
                require(by > 0, "action inString(")
                return { ...state, count: count + by }
              }
              action reset() only owner {
                return { ...state, count: 0 }
              }
              destroy close() only owner {
                pay(owner, balance)
              }
              view count(): Int = count
            }
            """;

    @Test
    public void aJscFilesEntryPointsAreItsCallsAndNothingInCommentsOrStrings() {
        var f = JoustEntryMarkers.scan(COUNTER);
        assertNotNull(f);
        assertEquals("org.example.counter.Counter", f.contract());
        assertEquals(List.of("init", "increment", "reset", "close", "count"), f.entries().stream().map(JoustEntryMarkers.JscEntry::name).toList());
        assertEquals(List.of("init", "action", "action", "destroy", "view"), f.entries().stream().map(JoustEntryMarkers.JscEntry::kind).toList());
        var inc = f.entries().get(1);
        assertTrue(COUNTER.substring(inc.offset(), inc.offset() + inc.length()).startsWith("action increment"));
        assertNull("no contract: no markers", JoustEntryMarkers.scan("jsc 1\npackage a.b\n// contract X\n"));
        assertEquals("a.b".length(), JoustEntryMarkers.blankComments("a.b").length());
    }

    @Test
    public void recordedInstancesRoundTripAndAnythingElseIsIgnored() throws Exception {
        Path root = Files.createTempDirectory("joust-instances");
        InstanceStore store = new InstanceStore(root);
        Path module = Path.of("/tmp/some/module");
        String i1 = "11".repeat(32), i2 = "22".repeat(32), t = "aa".repeat(32);
        store.record("chain-a", module, new InstanceStore.Recorded("p.C", i1, t, "init init"));
        store.record("chain-a", module, new InstanceStore.Recorded("p.C", i2, t, "init init"));
        store.record("chain-a", module, new InstanceStore.Recorded("p.C", i1, t, "init init"));   // once per instance
        assertEquals(List.of(i2, i1), store.list("chain-a", module).stream().map(InstanceStore.Recorded::instance).toList());
        assertTrue("another chain has its own", store.list("chain-b", module).isEmpty());
        store.forget("chain-a", module, "p.C", i2);
        assertEquals(List.of(i1), store.list("chain-a", module).stream().map(InstanceStore.Recorded::instance).toList());
        // Untrusted on load: a file of another shape is ignored, and the next record replaces it.
        Path file;
        try (var w = Files.walk(root)) {
            file = w.filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow();
        }
        Files.writeString(file, "{\"version\":1,\"instances\":[{\"contract\":\"p.C\",\"instance\":\"<b>x</b>\",\"txHash\":\"" + t + "\",\"label\":\"l\"}]}");
        assertTrue(store.list("chain-a", module).isEmpty());
        Files.writeString(file, "{\"version\":2,\"instances\":[]}");
        assertTrue(store.list("chain-a", module).isEmpty());
        store.record("chain-a", module, new InstanceStore.Recorded("p.C", i2, t, "init init"));
        assertEquals(List.of(i2), store.list("chain-a", module).stream().map(InstanceStore.Recorded::instance).toList());
    }
}
