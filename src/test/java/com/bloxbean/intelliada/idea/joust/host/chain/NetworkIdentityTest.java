package com.bloxbean.intelliada.idea.joust.host.chain;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.Assert.*;

/** ADR-005 D14: positive devnet identity, refusals, and the public clock anchors. */
public class NetworkIdentityTest {

    @Test
    public void aRunningDevKitIsPositivelyIdentified() throws Exception {
        String store = JoustTestTools.env("JOUST_DEVKIT_STORE", "aRunningDevKitIsPositivelyIdentified");
        String admin = JoustTestTools.env("JOUST_DEVKIT_ADMIN", "aRunningDevKitIsPositivelyIdentified");
        if (store == null || admin == null) return;
        var body = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(admin + "/local-cluster/api/admin/devnet")).build(),
                HttpResponse.BodyHandlers.ofByteArray()).body();
        var api = new BlockfrostApi(store, null, Duration.ofSeconds(10));
        var id = NetworkIdentity.devKit(api, NetworkIdentity.parseAdmin(body), 42);
        assertEquals(NetworkIdentity.NetworkClass.DEVNET, id.networkClass());
        assertEquals(42, id.magic());
        assertTrue(id.chainKey(), id.chainKey().matches("devnet-42-[0-9a-f]{64}"));
        assertTrue(id.horizonSlots() > 0);
        try {
            NetworkIdentity.devKit(api, NetworkIdentity.parseAdmin(body), 43);
            fail("a different configured magic was accepted");
        } catch (NetworkIdentity.Refused expected) {
            // refused
        }
    }

    @Test
    public void publicMagicsAndAMissingAdminApiAreRefusedForDevnets() throws Exception {
        var api = new BlockfrostApi("http://127.0.0.1:9/api", null, Duration.ofSeconds(1));
        for (long m : new long[]{1, 2, 4, 764824073}) {
            try {
                NetworkIdentity.devKit(api, HostJson.MAPPER.readTree("{\"protocolMagic\":" + m + ",\"startTime\":1}"), m);
                fail("public magic " + m + " accepted as a devnet");
            } catch (NetworkIdentity.Refused expected) {
                assertTrue(expected.getMessage().contains("public"));
            }
        }
        try {
            NetworkIdentity.devKit(api, null, 42);
            fail();
        } catch (NetworkIdentity.Refused expected) {
            assertTrue(expected.getMessage().contains("admin API"));
        }
    }
}
