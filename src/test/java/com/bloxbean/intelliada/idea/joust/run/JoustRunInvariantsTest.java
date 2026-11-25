package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.host.HostError;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/** ADR-005 §4.0 rows for M3.1b: I14, I15, I28. */
public class JoustRunInvariantsTest {

    /** I28: a node answering an older tip yields the previous {@code now}. */
    @Test
    public void nowNeverDecreases() {
        AtomicLong floor = new AtomicLong(-1);
        assertEquals(100, JoustRunSession.monotone(floor, 100));
        assertEquals("an older tip yields the previous now", 100, JoustRunSession.monotone(floor, 90));
        assertEquals(120, JoustRunSession.monotone(floor, 120));
    }

    /** I15: host strings render as plain text; HTML and ANSI are shown literally, never interpreted. */
    @Test
    public void hostTextIsPlain() {
        HostError e = new HostError("refusal", "require", "<html><b>bold</b> \u001B[31mred\u001B[0m", List.of(), null);
        String shown = PlainText.literal(JoustRunPanel.describe(e), 1000);
        assertFalse("ANSI removed", shown.contains("\u001B"));
        assertTrue(shown.contains("<b>bold</b>"));   // shown as characters, in a plain text area
        String label = PlainText.literal("<html><img src=x>", 300);
        assertFalse("a list cell never starts with <html>", label.startsWith("<html"));
    }

    /** I14: nothing in the run panel or the host channel reads a project's joust.config.json (its networks or start). */
    @Test
    public void noJoustConfigIsRead() throws Exception {
        Path main = Path.of("src/main/java/com/bloxbean/intelliada/idea/joust");
        for (String pkg : new String[]{"host", "run", "accounts"}) {
            Path dir = main.resolve(pkg);
            if (!Files.isDirectory(dir)) continue;
            try (var walk = Files.walk(dir)) {
                for (Path f : (Iterable<Path>) walk.filter(p -> p.toString().endsWith(".java"))::iterator) {
                    String src = Files.readString(f);
                    assertFalse(f + " reads joust.config.json", src.contains("joust.config.json"));
                }
            }
        }
    }
}
