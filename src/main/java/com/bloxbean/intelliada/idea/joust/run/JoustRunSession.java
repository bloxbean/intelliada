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

    /** A bound node: the configuration, its positive identity, and its Blockfrost-shaped API. */
    public record Binding(RemoteNode node, NetworkIdentity.Identity identity, BlockfrostApi api) {
        /**
         * The binding's identity for host reuse (M31B-CDX-1): the node configuration, its endpoint, and the chain it
         * reached. Another configuration, endpoint or chain never shares a host.
         */
        public String key() {
            return node.getId() + "|" + api.base() + "|" + identity.chainKey();
        }
    }

    private final Binding binding;
    private final JoustHostService.Running running;
    /** The {@code now} floor per host session (I28), shared by every panel session that uses one host (M31B-DES-6). */
    private static final java.util.Map<com.bloxbean.intelliada.idea.joust.host.HostSession, AtomicLong> FLOORS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private final AtomicLong lastNow;

    JoustRunSession(Binding binding, JoustHostService.Running running) {
        this.binding = binding;
        this.running = running;
        this.lastNow = FLOORS.computeIfAbsent(running.session(), k -> new AtomicLong(-1));
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
        NodeType t = node.getNodeType();
        if (t != NodeType.YaciDevKit && t != NodeType.LocalYaciDevKit) {
            throw new NetworkIdentity.Refused(t == NodeType.Yano
                    ? "Yano devnets are not served by the run panel yet (ADR-005 G3.4)"
                    : "the run panel serves Yaci DevKit devnets in this version; public testnets come later");
        }
        String endpoint = node.getApiEndpoint();
        if (endpoint == null || endpoint.isBlank()) throw new NetworkIdentity.Refused("the node configuration has no API endpoint");
        URI u = URI.create(endpoint.trim());
        String host = u.getHost();
        if (host == null || !(host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1") || host.equals("[::1]"))) {
            throw new NetworkIdentity.Refused("a devnet endpoint must be on this machine (loopback)");
        }
        BlockfrostApi api = new BlockfrostApi(endpoint.trim(), null, Duration.ofSeconds(10));
        JsonNode admin = adminDevnet(u.getScheme() + "://" + host + ":10000");
        long magic = configuredMagic(node.getProtocolMagic());
        return new Binding(node, NetworkIdentity.devKit(api, admin, magic), api);
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
        return new ChainReads(b.api(), b.identity().networkId(), BYTES_PER_CALL);
    }

    /** {@code max(previous now, the bound node's tip slot)}: never decreasing within the session (I28). */
    long now() throws BlockfrostApi.Failed, InterruptedException {
        JsonNode t = binding.api().get("/blocks/latest");
        if (t == null || !t.path("slot").canConvertToLong()) throw new BlockfrostApi.Failed("the node has no readable tip");
        return monotone(lastNow, t.path("slot").asLong());
    }

    /** {@code max(floor, tip)}, raising the floor: an older tip yields the previous {@code now} (I28). */
    static long monotone(AtomicLong floor, long tipSlot) {
        return floor.accumulateAndGet(tipSlot, Math::max);
    }

    private final Object dispatch = new Object();

    /**
     * One call: the binding is re-checked (the node configuration and the chain it reached; a change stops the host,
     * M31B-CDX-1), then {@code now} is taken and the call dispatched under one lock, so calls reach the host in the order
     * their {@code now} was taken (M31B-CDX-6).
     */
    private JsonNode call(String method, ObjectNode params, boolean withNow) throws Exception {
        checkBinding();
        synchronized (dispatch) {
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
        RemoteNode now = com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState.getInstance().getRemoteNodes().stream()
                .filter(n -> java.util.Objects.equals(n.getId(), binding.node().getId())).findFirst().orElse(null);
        boolean configChanged = now != null && (!java.util.Objects.equals(now.getApiEndpoint(), binding.node().getApiEndpoint())
                || !java.util.Objects.equals(now.getProtocolMagic(), binding.node().getProtocolMagic())
                || now.getNodeType() != binding.node().getNodeType());
        String why = configChanged ? "the node configuration changed" : null;
        if (why == null && binding.identity().networkClass() == NetworkIdentity.NetworkClass.DEVNET) {
            JsonNode b1 = binding.api().get("/blocks/1");
            String h = b1 == null ? null : b1.path("hash").textValue();
            if (h == null || !binding.identity().chainKey().endsWith("-" + h)) why = "the devnet behind the node was recreated";
        }
        if (why != null) {
            running.session().close();
            running.started().stop();
            throw new NetworkIdentity.Refused(why + "; the host was stopped. Start it again to bind the new network");
        }
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
    public JsonNode nextPage(String method, String listing, int after, int limit) throws Exception {
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
