package com.bloxbean.intelliada.idea.joust.host.chain;

import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * ADR-005 D14: a bound node's positive network identity, its clock and its chain identity.
 * <ul>
 *   <li><b>Devnet</b> (DevKit): the admin API answers; Yaci Store's genesis has the admin API's magic and start time; the
 *   magic is the configured devnet magic and never a public network's; the chain identity is the magic and the hash of
 *   block 1 (absent until the devnet has produced it).</li>
 *   <li><b>Public</b> (Blockfrost): the configuration's class (preprod 1, preview 2, mainnet 764824073) and the provider's
 *   genesis magic agree; the clock is the Shelley-era anchor CCL's {@code SlotConfigs} uses.</li>
 * </ul>
 */
public final class NetworkIdentity {

    public static final Set<Long> PUBLIC_MAGICS = Set.of(1L, 2L, 4L, 764824073L);
    private static final Pattern HASH32 = Pattern.compile("[0-9a-f]{64}");

    /** The class of a network (D13a, D14). */
    public enum NetworkClass { DEVNET, TESTNET, MAINNET }

    /**
     * A positively identified network: its class, network id and magic; the {@code initialize} clock; the devnet TTL
     * horizon (⌊3k/f⌋ slots, 300 when not stated); and the chain identity key (D14, D17).
     */
    public record Identity(NetworkClass networkClass, int networkId, long magic, long zeroSlot, long zeroTimeMs, long slotLengthMs,
                           int horizonSlots, String chainKey) {
    }

    /** The node is not acceptable, or not readable now; plain text for the panel. */
    public static final class Refused extends Exception {
        public Refused(String why) {
            super(why, null, false, false);
        }

        public Refused(String why, @Nullable Throwable cause) {
            super(why, cause, false, false);
        }
    }

    private NetworkIdentity() {
    }

    /**
     * A Yaci DevKit: {@code store} is Yaci Store's Blockfrost-shaped API, {@code adminDevnetJson} the admin API's
     * {@code /local-cluster/api/admin/devnet} answer (read by the caller from the configured admin endpoint).
     */
    public static Identity devKit(BlockfrostApi store, @Nullable JsonNode adminDevnetJson, long configuredMagic)
            throws Refused, InterruptedException {
        if (adminDevnetJson == null || !adminDevnetJson.isObject()) throw new Refused("the DevKit admin API did not answer");
        long magic = adminDevnetJson.path("protocolMagic").asLong(-1);
        long start = adminDevnetJson.path("startTime").asLong(-1);
        if (magic < 0 || start < 0) throw new Refused("the DevKit admin API has no protocol magic or start time");
        if (PUBLIC_MAGICS.contains(magic)) throw new Refused("protocol magic " + magic + " is a public network's, not a devnet's");
        if (magic != configuredMagic) {
            throw new Refused("the devnet's magic " + magic + " differs from the configured " + configuredMagic);
        }
        JsonNode g;
        try {
            g = store.get("/genesis");
        } catch (BlockfrostApi.Failed e) {
            throw new Refused("Yaci Store did not answer: " + e.getMessage());
        }
        if (g == null || g.path("network_magic").asLong(-2) != magic || g.path("system_start").asLong(-2) != start) {
            throw new Refused("Yaci Store does not serve the admin API's chain (magic or start time differ)");
        }
        double slotLength = g.path("slot_length").asDouble(adminDevnetJson.path("slotLength").asDouble(1));
        long slotMs = Math.round(slotLength * 1000);
        if (slotMs <= 0) throw new Refused("the devnet's slot length is not positive");
        int k = g.path("security_param").asInt(adminDevnetJson.path("securityParam").asInt(0));
        double f = g.path("active_slots_coefficient").asDouble(adminDevnetJson.path("activeSlotsCoeff").asDouble(0));
        int horizon = k > 0 && f > 0 && f <= 1 ? (int) Math.floor(3 * k / f) : 300;
        String block1 = blockOneHash(store);
        return new Identity(NetworkClass.DEVNET, 0, magic, 0, start * 1000, slotMs, horizon, "devnet-" + magic + "-" + block1);
    }

    /** A public network on Blockfrost: {@code expectedMagic} from the node configuration's class. */
    public static Identity publicNetwork(BlockfrostApi api, long expectedMagic) throws Refused, InterruptedException {
        NetworkClass c;
        long zeroSlot, zeroTime;
        if (expectedMagic == 764824073L) {
            c = NetworkClass.MAINNET;
            zeroSlot = 4_492_800L;
            zeroTime = 1_596_059_091_000L;
        } else if (expectedMagic == 1L) {
            c = NetworkClass.TESTNET;
            zeroSlot = 86_400L;
            zeroTime = 1_655_769_600_000L;
        } else if (expectedMagic == 2L) {
            c = NetworkClass.TESTNET;
            zeroSlot = 0L;
            zeroTime = 1_666_656_000_000L;
        } else {
            throw new Refused("only preprod, preview and mainnet are supported public networks");
        }
        JsonNode g;
        try {
            g = api.get("/genesis");
        } catch (BlockfrostApi.Failed e) {
            throw new Refused("the provider did not answer: " + e.getMessage());
        }
        if (g == null || g.path("network_magic").asLong(-1) != expectedMagic) {
            throw new Refused("the provider's network magic is not the configured network's");
        }
        return new Identity(c, c == NetworkClass.MAINNET ? 1 : 0, expectedMagic, zeroSlot, zeroTime, 1000, 900,
                "public-" + expectedMagic);
    }

    private static String blockOneHash(BlockfrostApi store) throws Refused, InterruptedException {
        JsonNode b;
        try {
            b = store.get("/blocks/1");
        } catch (BlockfrostApi.Failed e) {
            throw new Refused("the devnet's first block could not be read: " + e.getMessage());
        }
        String h = b == null ? null : b.path("hash").textValue();
        if (h == null || !HASH32.matcher(h).matches()) throw new Refused("waiting for the devnet's first block");
        return h;
    }

    /** Parses an admin API answer (for callers that fetched it as text). */
    public static JsonNode parseAdmin(byte[] body) throws Refused {
        try {
            return HostJson.MAPPER.readTree(body);
        } catch (java.io.IOException e) {
            throw new Refused("the DevKit admin API's answer is not JSON");
        }
    }
}
