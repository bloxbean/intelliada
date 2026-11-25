package com.bloxbean.intelliada.idea.joust.host.chain;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.HostLauncher;
import com.bloxbean.intelliada.idea.joust.host.HostSession;
import com.bloxbean.intelliada.idea.joust.host.HostSnapshot;
import com.bloxbean.intelliada.idea.joust.host.HostSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.*;

/**
 * ADR-005 M3.1b (tool-gated): the real dev host of a built JOUST Counter, its chain reads answered by {@link ChainReads}
 * from a running Yaci DevKit (Yaci Store's Blockfrost-shaped API), initialized with the DevKit's own clock. Set
 * {@code JOUST_HOST_FIXTURE} (the built Counter) and {@code JOUST_DEVKIT_STORE} (e.g. {@code http://localhost:8080/api/v1}).
 */
public class DevKitReadsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void deploymentPrerequisitesAndListingsReadTheDevKit() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "deploymentPrerequisitesAndListingsReadTheDevKit");
        String store = JoustTestTools.env("JOUST_DEVKIT_STORE", "deploymentPrerequisitesAndListingsReadTheDevKit");
        if (fixture == null || store == null) return;
        BlockfrostApi api = new BlockfrostApi(store, null, Duration.ofSeconds(10));
        JsonNode genesis = api.get("/genesis");
        assertNotNull("Yaci Store serves /genesis", genesis);
        assertEquals("a devnet magic", 42, genesis.path("network_magic").asInt());
        long startMs = genesis.path("system_start").asLong() * 1000;
        long slotMs = genesis.path("slot_length").asLong(1) * 1000;
        int k = genesis.path("security_param").asInt();
        double f = genesis.path("active_slots_coefficient").asDouble();
        int horizon = (int) Math.floor(3 * k / f);
        int ttl = Math.min(900, horizon > 0 ? horizon : 300);

        HostSpec spec = HostSpec.read(Path.of(fixture));
        HostSnapshot snap = HostSnapshot.create(spec, tmp.newFolder("s").toPath());
        HostLauncher.Started started = HostLauncher.start(spec, snap, Path.of(fixture), 256 * 1024);
        Process p = started.process();
        List<String> kinds = new CopyOnWriteArrayList<>();
        ChainReads chain = new ChainReads(api, 0, 32L * 1024 * 1024);
        HostSession s = new HostSession(p.getInputStream(), p.getOutputStream(), () -> HostLauncher.stop(p),
                (id, params) -> {
                    kinds.add(params.path("kind").textValue());
                    return chain.read(id, params);
                }, e -> { });
        try {
            ObjectNode init = HostJson.object();
            init.putArray("protocol").add(1);
            init.putObject("network").put("networkId", 0).put("protocolMagic", 42).put("mainnet", false);
            init.putObject("clock").put("zeroSlot", 0).put("zeroTimeMs", startMs).put("slotLengthMs", slotMs);
            init.putObject("time").put("maxTtlSlots", ttl).put("settlementSlots", 60).put("maxNowAheadSlots", 900);
            init.putObject("limits").put("callDeadlineMs", 60000).put("readsPerCall", 256).put("readBytesPerCall", 33554432);
            init.putObject("build").put("snapshot", snap.root().toString()).put("digest", snap.digest());
            init.put("restoring", false);
            JsonNode r = s.call("initialize", init, Duration.ofSeconds(120), () -> false);
            String contract = r.path("contracts").get(0).path("name").textValue();
            long now = api.get("/blocks/latest").path("slot").asLong();

            JsonNode dep = s.call("deployment", HostJson.object().put("now", now).put("contract", contract), Duration.ofSeconds(60), () -> false);
            String address = dep.path("addresses").path("contract").textValue();
            assertNotNull(dep.toString(), address);
            assertTrue("a testnet script address", address.startsWith("addr_test1w"));
            assertEquals(r.path("contracts").get(0).path("scriptHashes").path("spend").textValue(),
                    dep.path("scriptHashes").path("spend").textValue());

            JsonNode pre = s.call("prerequisites", HostJson.object().put("now", now).put("contract", contract), Duration.ofSeconds(60), () -> false);
            assertTrue(pre.toString(), pre.isArray());

            JsonNode list = s.call("instances", HostJson.object().put("now", now).put("contract", contract).put("limit", 10),
                    Duration.ofSeconds(60), () -> false);
            assertTrue(list.toString(), list.has("items") && list.has("listing"));
            assertTrue("the host read the chain through IntelliAda: " + kinds, kinds.contains("utxos"));
            s.shutdown(Duration.ofSeconds(10));
            assertFalse(s.violated());
        } finally {
            s.close();
            HostLauncher.stop(p);
            snap.delete();
        }
    }
}
