package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.ReadOnlyFeatureSet;
import com.bloxbean.intelliada.idea.joust.lsp.JoustLsp4jCompat;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.*;

/** ADR-004 §12.2 E22: the JOUST server runs only on an LSP4IJ whose client features are exactly the classified ones. */
public class JoustLsp4jCompatTest {

    @Test
    public void theLsp4ijThisBuildUsesIsSupported() {
        assertNull(JoustLsp4jCompat.problem());
    }

    @Test
    public void anExactMatchPasses() throws Exception {
        assertNull(check(new TreeSet<>(ReadOnlyFeatureSet.FEATURES.keySet()), "0.30.1"));
    }

    @Test
    public void aNewerLsp4ijWithAnUnreviewedFeatureIsRefused() throws Exception {
        Set<String> s = new TreeSet<>(ReadOnlyFeatureSet.FEATURES.keySet());
        s.add("setInlineEditFeature");
        String why = check(s, "0.31.0");
        assertNotNull(why);
        assertTrue(why, why.contains("LSP4IJ 0.31.0 is not supported"));
        assertTrue(why, why.contains("not been reviewed"));
        assertTrue(why, why.contains("not classified: InlineEdit"));
        assertTrue(why, why.contains("language server is off"));
        assertTrue("a newer LSP4IJ: install the supported one or update IntelliAda (UP-DES-7)", why.contains("install that version"));
        assertTrue(check(new TreeSet<>(ReadOnlyFeatureSet.FEATURES.keySet()), "0.7.0").contains("Update LSP4IJ to 0.30.1"));
    }

    @Test
    public void anOlderLsp4ijMissingAClassifiedFeatureIsRefused() throws Exception {
        Set<String> s = new TreeSet<>(ReadOnlyFeatureSet.FEATURES.keySet());
        s.remove("setSelectionRangeFeature");
        s.remove("setWorkspaceFolderFeature");
        String why = check(s, "0.7.0");
        assertNotNull(why);
        assertTrue(why, why.contains("lacks SelectionRange, WorkspaceFolder"));
        assertFalse(why, why.contains("not classified"));
    }

    /** UP-CDX-8: a release that changes behaviour without a new feature setter is refused until it is reviewed. */
    @Test
    public void anUnreviewedReleaseWithTheSameFeaturesIsRefused() throws Exception {
        for (String v : new String[]{"0.30.2", "0.31.0", "unreviewed", "(version unknown)", ""}) {
            String why = check(new TreeSet<>(ReadOnlyFeatureSet.FEATURES.keySet()), v);
            assertNotNull(v, why);
            assertTrue(why, why.contains("not been reviewed"));
        }
    }

    @Test
    public void theInstalledVersionIsRead() {
        assertEquals("0.30.1", JoustLsp4jCompat.result().version());
    }

    @Test
    public void aTestOverrideIsReportedAndCleared() {
        try {
            JoustLsp4jCompat.setResultForTests(new JoustLsp4jCompat.Result("no", "9.9.9"));
            assertEquals("no", JoustLsp4jCompat.problem());
        } finally {
            JoustLsp4jCompat.setResultForTests(null);
        }
        assertNull(JoustLsp4jCompat.problem());
    }

    @Test
    public void theSettersAreReadFromLsp4ijItself() throws Exception {
        Method m = JoustLsp4jCompat.class.getDeclaredMethod("setters", Class.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> setters = (Set<String>) m.invoke(null, LSPClientFeatures.class);
        assertEquals(new TreeSet<>(ReadOnlyFeatureSet.FEATURES.keySet()), setters);
    }

    private static String check(Set<String> setters, String version) throws Exception {
        Method m = JoustLsp4jCompat.class.getDeclaredMethod("check", Set.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, setters, version);
    }
}
