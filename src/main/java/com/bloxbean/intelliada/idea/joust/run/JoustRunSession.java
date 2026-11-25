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
    }

    private final Binding binding;
    private final JoustHostService.Running running;
    private final AtomicLong lastNow = new AtomicLong(-1);

    JoustRunSession(Binding binding, JoustHostService.Running running) {
        this.binding = binding;
        this.running = running;
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
        long magic = parseMagic(node.getProtocolMagic(), 42);
        return new Binding(node, NetworkIdentity.devKit(api, admin, magic), api);
    }

    private static long parseMagic(@Nullable String s, long dflt) {
        try {
            return s == null || s.isBlank() ? dflt : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static @Nullable JsonNode adminDevnet(String base) throws InterruptedException {
        try {
            HttpClient c = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
            HttpResponse<byte[]> r = c.send(HttpRequest.newBuilder(URI.create(base + "/local-cluster/api/admin/devnet"))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200 || r.body().length > 64 * 1024) return null;
            return NetworkIdentity.parseAdmin(r.body());
        } catch (java.io.IOException | NetworkIdentity.Refused e) {
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
        long slot = t.path("slot").asLong();
        return lastNow.accumulateAndGet(slot, Math::max);
    }

    private JsonNode call(String method, ObjectNode params) throws HostError, HostSession.Ended, InterruptedException {
        return running.session().call(method, params, CALL_TIMEOUT, () -> false);
    }

    public JsonNode describe(String contract) throws Exception {
        return call("describe", HostJson.object().put("contract", contract));
    }

    public JsonNode deployment(String contract) throws Exception {
        return call("deployment", HostJson.object().put("now", now()).put("contract", contract));
    }

    public JsonNode prerequisites(String contract) throws Exception {
        return call("prerequisites", HostJson.object().put("now", now()).put("contract", contract));
    }

    public JsonNode instances(String contract, int limit) throws Exception {
        return call("instances", HostJson.object().put("now", now()).put("contract", contract).put("limit", limit));
    }

    /** The instance's decoded state ({@code view} with a null view) or a named view with no arguments. */
    public JsonNode view(String contract, String instance, @Nullable String view) throws Exception {
        ObjectNode p = HostJson.object().put("now", now()).put("contract", contract).put("instance", instance);
        if (view == null) p.putNull("view");
        else p.put("view", view);
        p.putObject("args");
        return call("view", p);
    }
}
