package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.joust.host.HostError;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.HostSession;
import com.bloxbean.intelliada.idea.joust.host.JoustHostService;
import com.bloxbean.intelliada.idea.joust.host.chain.BlockfrostApi;
import com.bloxbean.intelliada.idea.joust.host.chain.ChainReads;
import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ADR-005 M3.1b: one read-only run-panel session: a running, initialized dev host bound to one explicit node (D14), its
 * reverse reads answered by {@link ChainReads} from that node (D18), and a monotone {@code now} (D14b, I28). Nothing here
 * signs or submits.
 */
public final class JoustRunSession {

    static final Duration CALL_TIMEOUT = Duration.ofSeconds(90);
    static final long BYTES_PER_CALL = 32L * 1024 * 1024;

    /**
     * A bound node: the configuration's id and the values bound from it, copied at Start (the stored configuration is
     * edited in place, so it is never compared with itself; M31B-CDX-15), its positive identity, and its API.
     */
    public record Binding(RemoteNode node, Bound bound, NetworkIdentity.Identity identity, BlockfrostApi api) {
        public Binding(RemoteNode node, NetworkIdentity.Identity identity, BlockfrostApi api) {
            this(node, Bound.of(node), identity, api);
        }

        /**
         * The binding's identity for host reuse (M31B-CDX-1): the node configuration, its endpoint, and the chain it
         * reached. Another configuration, endpoint or chain never shares a host.
         */
        public String key() {
            return bound.id() + "|" + api.base() + "|" + identity.chainKey();
        }
    }

    /** The configuration values a binding depends on, as they were when it was made. */
    public record Bound(String id, @Nullable String endpoint, @Nullable String magic, @Nullable NodeType type) {
        static Bound of(RemoteNode n) {
            return new Bound(n.getId(), n.getApiEndpoint(), n.getProtocolMagic(), n.getNodeType());
        }
    }

    /** Why a stored configuration no longer serves a binding, or null while it does. */
    static @Nullable String configChange(Bound bound, java.util.List<RemoteNode> stored) {
        RemoteNode now = stored.stream().filter(n -> java.util.Objects.equals(n.getId(), bound.id())).findFirst().orElse(null);
        if (now == null) return "the node configuration was removed";   // M31B-SEC-7
        return Bound.of(now).equals(bound) ? null : "the node configuration changed";
    }

    private final Binding binding;
    private final JoustHostService.Running running;
    /**
     * The clock of one host session: its {@code now} floor (I28) and the lock under which {@code now} is taken and the
     * call sent, shared by every panel session on that host (M31B-DES-6, M31B-CDX-18).
     */
    static final class Clock {
        final AtomicLong floor = new AtomicLong(-1);
    }

    private static final java.util.Map<HostSession, Clock> CLOCKS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private final Clock clock;
    private final AtomicLong lastNow;

    JoustRunSession(Binding binding, JoustHostService.Running running) {
        this.binding = binding;
        this.running = running;
        this.clock = CLOCKS.computeIfAbsent(running.session(), k -> new Clock());
        this.lastNow = clock.floor;
    }

    public Binding binding() {
        return binding;
    }

    public JoustHostService.Running running() {
        return running;
    }

    /**
     * Binds a node configuration (D14). M3.1b serves DevKit devnets: Yaci Store's Blockfrost-shaped API from the
     * configuration's endpoint, and its admin API on the same host at port 10000 (DevKit's default, until the node
     * configuration has its own admin field). Public networks come in M3.5; Yano once its read coverage is settled (G3.4).
     */
    public static Binding bind(RemoteNode node) throws NetworkIdentity.Refused, InterruptedException {
        return bind(node, null, () -> com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState.getInstance().getRemoteNodes());
    }

    /**
     * Binds from the configuration values copied <b>before any request</b> (M31B-CDX-28): every client and identity
     * parameter comes from that copy, and the stored configuration is checked against it again once the identity reads
     * are done, so an edit made meanwhile refuses rather than binding one endpoint under another's values.
     * {@code adminBase} overrides DevKit's admin endpoint (tests).
     */
    static Binding bind(RemoteNode node, @Nullable String adminBase, java.util.function.Supplier<java.util.List<RemoteNode>> stored)
            throws NetworkIdentity.Refused, InterruptedException {
        Bound bound = Bound.of(node);
        NodeType t = bound.type();
        if (t != NodeType.YaciDevKit && t != NodeType.LocalYaciDevKit) {
            throw new NetworkIdentity.Refused(t == NodeType.Yano
                    ? "Yano devnets are not served by the run panel yet (ADR-005 G3.4)"
                    : "the run panel serves Yaci DevKit devnets in this version; public testnets come later");
        }
        String endpoint = bound.endpoint();
        if (endpoint == null || endpoint.isBlank()) throw new NetworkIdentity.Refused("the node configuration has no API endpoint");
        URI u = URI.create(endpoint.trim());
        String host = u.getHost();
        if (host == null || !(host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1") || host.equals("[::1]"))) {
            throw new NetworkIdentity.Refused("a devnet endpoint must be on this machine (loopback)");
        }
        long magic = configuredMagic(bound.magic());
        BlockfrostApi api = new BlockfrostApi(endpoint.trim(), null, Duration.ofSeconds(10));
        JsonNode admin = adminDevnet(adminBase != null ? adminBase : u.getScheme() + "://" + host + ":10000");
        NetworkIdentity.Identity id = NetworkIdentity.devKit(api, admin, magic);
        String changed = configChange(bound, stored.get());
        if (changed != null) throw new NetworkIdentity.Refused(changed + " while it was being bound; start again");
        return new Binding(node, bound, id, api);
    }

    /** The devnet magic recorded in the node configuration; required, never defaulted (M31B-CDX-9). */
    static long configuredMagic(@Nullable String s) throws NetworkIdentity.Refused {
        if (s == null || s.isBlank()) throw new NetworkIdentity.Refused("the node configuration records no protocol magic");
        try {
            long m = Long.parseLong(s.trim());
            if (m < 0 || m > 0xFFFFFFFFL) throw new NumberFormatException();
            return m;
        } catch (NumberFormatException e) {
            throw new NetworkIdentity.Refused("the node configuration's protocol magic is not a 32-bit number");
        }
    }

    private static @Nullable JsonNode adminDevnet(String base) throws InterruptedException {
        // The same capped, deadline-bounded client as every provider read (M31B-SEC-1, -SEC-2).
        try {
            JsonNode n = new BlockfrostApi(base, null, Duration.ofSeconds(10)).get("/local-cluster/api/admin/devnet",
                    System.nanoTime() + Duration.ofSeconds(10).toNanos(), 64 * 1024);
            return n != null && n.isObject() ? n : null;
        } catch (BlockfrostApi.Failed e) {
            return null;
        }
    }

    /** {@code initialize}'s parameters for a binding, without {@code build} (D11 step 7, D14b). */
    public static ObjectNode initParams(NetworkIdentity.Identity id) {
        ObjectNode n = HostJson.object();
        n.putArray("protocol").add(1);
        n.putObject("network").put("networkId", id.networkId()).put("protocolMagic", id.magic()).put("mainnet", false);
        n.putObject("clock").put("zeroSlot", id.zeroSlot()).put("zeroTimeMs", id.zeroTimeMs()).put("slotLengthMs", id.slotLengthMs());
        boolean devnet = id.networkClass() == NetworkIdentity.NetworkClass.DEVNET;
        int ttl = devnet ? Math.min(900, id.horizonSlots()) : 7200;
        n.putObject("time").put("maxTtlSlots", ttl).put("settlementSlots", devnet ? 60 : 2160).put("maxNowAheadSlots", 900);
        n.putObject("limits").put("callDeadlineMs", 60000).put("readsPerCall", 256).put("readBytesPerCall", BYTES_PER_CALL);
        n.put("restoring", false);
        return n;
    }

    /** The reverse-read answerer for a binding. */
    public static HostSession.Reads reads(Binding b) {
        return new ChainReads(b.api().copy(), b.identity().networkId(), BYTES_PER_CALL);
    }

    /** {@code max(previous now, the bound node's tip slot)}: never decreasing within the session (I28). */
    long now() throws BlockfrostApi.Failed, InterruptedException {
        return monotone(lastNow, tipSlot(binding.api().get("/blocks/latest")));
    }

    /**
     * The tip's slot, validated as reverse reads validate it (a non-negative JSON integer) before it can raise the
     * floor; anything else withholds the call (M31B-CDX-33).
     */
    static long tipSlot(@Nullable JsonNode tip) throws BlockfrostApi.Failed {
        JsonNode slot = tip == null ? null : tip.get("slot");
        if (slot == null || !slot.isIntegralNumber() || !slot.canConvertToLong() || slot.longValue() < 0) {
            throw new BlockfrostApi.Failed("the node has no readable tip");
        }
        return slot.longValue();
    }

    /** {@code max(floor, tip)}, raising the floor: an older tip yields the previous {@code now} (I28). */
    static long monotone(AtomicLong floor, long tipSlot) {
        return floor.accumulateAndGet(tipSlot, Math::max);
    }

    Clock clockForTests() {
        return clock;
    }

    /**
     * One call: the binding is re-checked (the node configuration and the chain it reached; a change stops the host,
     * M31B-CDX-1), then {@code now} is taken and the call dispatched under one lock, so calls reach the host in the order
     * their {@code now} was taken (M31B-CDX-6).
     */
    private JsonNode call(String method, ObjectNode params, boolean withNow) throws Exception {
        synchronized (clock) {
            checkBinding();   // under the lock, so no call is sent after a change was seen (M31B-CDX-15)
            if (withNow) {
                ObjectNode p = HostJson.object().put("now", now());
                p.setAll(params);
                params = p;
            }
            return running.session().call(method, params, CALL_TIMEOUT, () -> false);
        }
    }

    /** Refuses (and stops the host) when the node configuration or the chain behind it changed since binding. */
    void checkBinding() throws Exception {
        String why = configChange(binding.bound(),
                com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState.getInstance().getRemoteNodes());
        if (why == null && binding.identity().networkClass() == NetworkIdentity.NetworkClass.DEVNET) {
            JsonNode b1;
            try {
                b1 = binding.api().get("/blocks/1");
            } catch (BlockfrostApi.Failed e) {
                throw new NetworkIdentity.Refused("block 1 of the node could not be read (" + e.getMessage() + "); the call was not sent");
            }
            why = blockOne(binding.identity(), b1);
        }
        if (why != null) {
            running.session().close();
            running.started().stop();
            throw new NetworkIdentity.Refused(why + "; the host was stopped. Start it again to bind the new network");
        }
    }

    /**
     * Block 1's verdict for a devnet binding: null while it is the bound chain's, "recreated" for a valid different hash
     * (D14). Anything else (absent, no or a malformed hash) withholds the call without stopping the host (M31B-CDX-26).
     */
    static @Nullable String blockOne(NetworkIdentity.Identity id, @Nullable JsonNode b1) throws NetworkIdentity.Refused {
        String h = b1 == null ? null : b1.path("hash").textValue();
        if (h == null || !h.matches("[0-9a-f]{64}")) {
            throw new NetworkIdentity.Refused("block 1 of the node is unavailable; the call was not sent");
        }
        return id.chainKey().endsWith("-" + h) ? null : "the devnet behind the node was recreated";
    }

    public JsonNode describe(String contract) throws Exception {
        return call("describe", HostJson.object().put("contract", contract), false);
    }

    public JsonNode deployment(String contract) throws Exception {
        return call("deployment", HostJson.object().put("contract", contract), true);
    }

    public JsonNode prerequisites(String contract) throws Exception {
        return call("prerequisites", HostJson.object().put("contract", contract), true);
    }

    public JsonNode instances(String contract, int limit) throws Exception {
        return call("instances", HostJson.object().put("contract", contract).put("limit", limit), true);
    }

    /** The owned objects of an instance (M31B-CDX-4). */
    public JsonNode objects(String contract, String instance, int limit) throws Exception {
        return call("objects", HostJson.object().put("contract", contract).put("instance", instance).put("limit", limit), true);
    }

    /** A later page of an {@code instances} or {@code objects} listing (protocol: listing, after, limit; M31B-CDX-10). */
    public JsonNode nextPage(String method, String listing, long after, int limit) throws Exception {
        return call(method, HostJson.object().put("listing", listing).put("after", after).put("limit", limit), false);
    }

    /** Decodes a datum (hex CBOR) by the contract's declared schemas (M31B-CDX-4). */
    public JsonNode decode(String contract, String datumHex) throws Exception {
        return call("decode", HostJson.object().put("contract", contract).put("datum", datumHex), false);
    }

    /** The instance's decoded state ({@code view} null), or a named view with its arguments in the description's JSON encoding. */
    public JsonNode view(String contract, String instance, @Nullable String view, @Nullable ObjectNode args) throws Exception {
        ObjectNode p = HostJson.object().put("contract", contract).put("instance", instance);
        if (view == null) p.putNull("view");
        else p.put("view", view);
        p.set("args", args == null ? HostJson.object() : args);
        return call("view", p, true);
    }
}
