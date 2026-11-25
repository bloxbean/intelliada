package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.joust.host.HostError;
import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/** ADR-005 M3.1b: initialize's parameters (D14b), the pre-launch contract list (D15) and error text (D16). */
public class JoustRunSessionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void devnetParametersFollowD14b() {
        var id = new NetworkIdentity.Identity(NetworkIdentity.NetworkClass.DEVNET, 0, 42, 0, 1_795_508_595_000L, 1000, 300, "devnet-42-x");
        var n = JoustRunSession.initParams(id);
        assertEquals(300, n.path("time").path("maxTtlSlots").intValue());
        assertEquals(60, n.path("time").path("settlementSlots").intValue());
        assertEquals(900, n.path("time").path("maxNowAheadSlots").intValue());
        assertFalse(n.path("network").path("mainnet").booleanValue());
        assertEquals(42, n.path("network").path("protocolMagic").intValue());
        assertEquals(1_795_508_595_000L, n.path("clock").path("zeroTimeMs").longValue());
        assertFalse("build is the service's to fill from the snapshot", n.has("build"));
        var wide = new NetworkIdentity.Identity(NetworkIdentity.NetworkClass.DEVNET, 0, 42, 0, 0, 1000, 5000, "k");
        assertEquals("never above 900 on a devnet", 900, JoustRunSession.initParams(wide).path("time").path("maxTtlSlots").intValue());
    }

    @Test
    public void builtContractsAreListedFromDescriptionsWithoutFollowingLinks() throws Exception {
        Path module = tmp.newFolder("m").toPath();
        Path dir = module.resolve("build/classes/java/main/META-INF/joust");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("org.example.Counter.json"), "{}");
        Files.writeString(dir.resolve("<html>x.json"), "{}");
        Path outside = tmp.newFile("outside.json").toPath();
        Files.createSymbolicLink(dir.resolve("org.example.Linked.json"), outside);
        assertEquals(List.of("org.example.Counter"), JoustRunPanel.builtContracts(module));
    }

    @Test
    public void hostErrorsAreShownByKindAndCodeWithTheirSites() {
        var e = new HostError("refusal", "require", "increment must be positive",
                List.of(new HostError.Site("Counter.jsc", 19, 5, "require", "increment", "by > 0")), 2005L);
        String t = JoustRunPanel.describe(e);
        assertTrue(t, t.startsWith("refusal/require: increment must be positive"));
        assertTrue(t, t.contains("Counter.jsc:19:5 in increment"));
        assertTrue(t, t.contains("retry at slot 2005"));
    }
}
