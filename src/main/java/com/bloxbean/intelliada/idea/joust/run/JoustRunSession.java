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
        this.journal = JOURNALS.get(running.session());
    }

    // ---- D17: the journal of a host session (M3.3b1) ------------------------------------------------------------------

    /** The journal of each host session this IDE launched; a reused host keeps its own. */
    private static final java.util.Map<HostSession, JoustRunJournal> JOURNALS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private final @Nullable JoustRunJournal journal;

    /**
     * Starts (or reuses) the module's host for a binding (D11, D17): a new host's chain history is classified first, so
     * {@code initialize} carries {@code restoring}, its reverse reads go through the settlement guard, and once its
     * catalog is verified it is restored from the journal. A reused host keeps the journal it was started with.
     * {@code store} overrides the IDE system directory's journal (tests).
     */
    public static JoustRunSession start(JoustHostService service, java.nio.file.Path moduleDir, Binding b,
                                        @Nullable com.bloxbean.intelliada.idea.joust.journal.JournalStore store, Launcher launcher,
                                        java.util.function.Consumer<JoustHostService.Running> adopted) throws Exception {
        JoustRunJournal fresh = service.running(moduleDir) == null ? JoustRunJournal.open(b, moduleDir, store) : null;
        boolean owned = false;
        try {
            ObjectNode init = fresh != null ? JoustRunJournal.initParams(b.identity(), fresh.restoring()) : initParams(b.identity());
            HostSession.Reads reads = fresh != null ? fresh.reads(reads(b)) : reads(b);
            JoustHostService.Running r = launcher.launch(init, b.key(), reads);
            adopted.accept(r);
            owned = fresh != null && JOURNALS.putIfAbsent(r.session(), fresh) == null;
            JoustRunSession made = new JoustRunSession(b, r);
            made.verifyCatalog();   // the host's catalog is the build's, or the host is stopped (M33A-CDX-2)
            if (owned) {
                try {
                    synchronized (made.clock) {
                        fresh.started(r.session(), made.clock);
                    }
                } catch (Exception e) {
                    made.stopHost();
                    throw e;
                }
            }
            return made;
        } finally {
            if (fresh != null && !owned) fresh.close();
        }
    }

    /** How a host is started or reused: {@link JoustHostService#start} from the panel, {@code launch} in tests. */
    @FunctionalInterface
    public interface Launcher {
        JoustHostService.Running launch(ObjectNode initialize, String binding, HostSession.Reads reads) throws Exception;
    }

    /**
     * A {@code prepare} or {@code publish} through the journal's bookkeeping (the deployment checked first): an unsigned
     * body the host reserved. Nothing is handed out or admitted here; M3.3b2's signing flow builds on it.
     */
    JsonNode build(String method, ObjectNode params) throws Exception {
        if (!method.equals("prepare") && !method.equals("publish")) throw new IllegalArgumentException(method);
        String blocked = journalBlocks();
        if (blocked != null) throw new NetworkIdentity.Refused(blocked);
        checkDeployment(method.equals("publish") ? params.path("contract").textValue() : params.path("call").path("contract").textValue());
        return call(method, params, true);
    }

    @Nullable JoustRunJournal journalForTests() {
        return journal;
    }

    /** Why building is blocked by the journal (D17's fences, a fenced host, an open restore), or null. */
    @Nullable String journalBlocks() {
        if (journal == null) return "this dev host was started without IntelliAda's journal; restart it from the run panel";
        return journal.buildingBlocked();
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
            if (journal == null) return running.session().call(method, params, CALL_TIMEOUT, () -> false);
            // D17: settlement before the call when anything is live; the call's read tips go to the host-table model.
            journal.beforeCall(params.path("now").asLong(0));
            try {
                JsonNode r = running.session().call(method, params, CALL_TIMEOUT, () -> false);
                journal.afterCall(method, r, null);
                return r;
            } catch (HostError e) {
                journal.afterCall(method, null, e.kind() + "/" + e.code());
                throw e;
            } catch (Exception e) {
                journal.afterCall(method, null, "transport");
                throw e;
            }
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

    // ---- D13: what IntelliAda derives itself, checked against the host (M33A-CDX-1, -CDX-2) ------------------------------

    private final java.util.Map<String, com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors.Anchors> anchors =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> deploymentChecked = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Why this session cannot sign (read-only use continues), accumulated; empty when it can (M33A-CDX-12, -DES-13). */
    private final java.util.Set<String> signingDisabled = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    /** The feature version of the JVM the host was launched with, as read at launch (M33A-CDX-30). */
    int javaFeature() throws NetworkIdentity.Refused {
        int f = running.javaFeature();
        if (f < 9) throw new NetworkIdentity.Refused("the launch JDK is unknown");
        return f;
    }

    /** Why the session cannot sign, or null; views, listings and decoding work either way (D11 step 4). */
    public @Nullable String signingDisabled() {
        synchronized (signingDisabled) {
            java.util.List<String> why = new java.util.ArrayList<>(signingDisabled);
            String j = journalBlocks();
            if (j != null) why.add(j);
            return why.isEmpty() ? null : "read-only: " + String.join("; ", why);
        }
    }

    /** Tests: contracts whose anchors are made unavailable, to drive the read-only path live (M33A-DES-20). */
    final java.util.Set<String> unanchorableForTests = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The contract's anchors, derived from the verified snapshot (never from the host). */
    public com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors.Anchors anchors(String contract) throws Exception {
        if (unanchorableForTests.contains(contract)) throw new com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors.Refused("made unavailable by a test");
        var a = anchors.get(contract);
        if (a == null) {
            a = com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors.derive(running.snapshot(), contract,
                    binding.identity().networkId(), javaFeature());
            anchors.put(contract, a);
        }
        return a;
    }

    void anchorsForTests(String contract, com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors.Anchors a) {
        anchors.put(contract, a);
    }

    /**
     * Checks a deployment reply against the derived one whenever anchors are available: every reply, the Deployment
     * button's included (M33A-CDX-14); a difference stops the host and refuses (I24).
     */
    private JsonNode checked(String contract, JsonNode dep) throws Exception {
        com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors.Anchors a;
        try {
            a = anchors(contract);
        } catch (com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors.Refused | NetworkIdentity.Refused unanchored) {
            signingDisabled.add(contract + " cannot be anchored: " + unanchored.getMessage());
            return dep;   // read-only use continues; nothing is signed without anchors
        }
        String why = com.bloxbean.intelliada.idea.joust.verifier.JoustDeployment.check(a, dep);
        if (why != null) {
            stopHost();
            throw new NetworkIdentity.Refused(why + "; the host was stopped");
        }
        deploymentChecked.add(contract);
        return dep;
    }

    /** Before any prerequisites, publish or prepare: the deployment, checked (the cache only spares a request). */
    void checkDeployment(String contract) throws Exception {
        if (deploymentChecked.contains(contract)) return;
        deployment(contract);
        if (!deploymentChecked.contains(contract)) {
            String why = signingDisabled();
            throw new NetworkIdentity.Refused(why != null ? why : contract + " cannot be anchored");
        }
    }

    /**
     * Checks {@code initialize}'s contract catalog against the snapshot (M33A-CDX-2): the host's own discovery rule
     * (the first copy of each {@code META-INF/joust/*.json} with {@code x-joust}), the same names, each entry's status
     * (parameterized: {@code unsupported:"params"}, otherwise {@code served:true}) and script hashes. A contract that
     * cannot be anchored disables signing only; an established conflict stops the host. Any failure to verify releases
     * the host too (M33A-CDX-22).
     */
    public void verifyCatalog() throws Exception {
        Catalog c;
        try {
            c = catalog(running.initialize(), running.snapshot(), binding.identity().networkId(), javaFeature());
        } catch (Exception e) {
            stopHost();
            throw e;
        }
        if (c.conflict() != null) {
            stopHost();
            throw new NetworkIdentity.Refused(c.conflict() + "; the host was stopped");
        }
        signingDisabled.addAll(c.unanchored());
    }

    /** A catalog comparison: a conflict (stop the host), or contracts that cannot be anchored (no signing). */
    record Catalog(@Nullable String conflict, java.util.List<String> unanchored) {
    }

    static @Nullable String catalogDifference(JsonNode initialize, com.bloxbean.intelliada.idea.joust.host.HostSnapshot snapshot,
                                              int networkId, int javaFeature) {
        return catalog(initialize, snapshot, networkId, javaFeature).conflict();
    }

    static Catalog catalog(JsonNode initialize, com.bloxbean.intelliada.idea.joust.host.HostSnapshot snapshot, int networkId,
                           int javaFeature) {
        java.util.Map<String, JsonNode> listed = new java.util.HashMap<>();
        for (JsonNode c : initialize.path("contracts")) {
            String n = c.path("name").textValue();
            if (n == null || listed.put(n, c) != null) return new Catalog("the host lists a contract twice or without a name", java.util.List.of());
        }
        // The host's discovery rule (M33A-CDX-15), shared with the signing gate.
        java.util.Set<String> described;
        try {
            described = new java.util.HashSet<>(com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors.describedContracts(snapshot));
        } catch (com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors.Refused r) {
            return new Catalog(r.getMessage(), java.util.List.of());
        }
        if (!listed.keySet().equals(described)) {
            return new Catalog("the host's contracts " + new java.util.TreeSet<>(listed.keySet()) + " are not the build's "
                    + new java.util.TreeSet<>(described), java.util.List.of());
        }
        java.util.List<String> unanchored = new java.util.ArrayList<>();
        for (var e : listed.entrySet()) {
            JsonNode entry = e.getValue();
            // Each fact as soon as its evidence exists (M33A-CDX-28, -CDX-37). First the status, from the description
            // alone (as the host resolves it), before any script is resolved.
            boolean params;
            try {
                var desc = snapshot.anchorsNamed("META-INF/joust/" + e.getKey() + ".json");
                params = HostJson.parseResource(desc.get(0).bytes()).path("x-joust").has("params");
            } catch (HostJson.Malformed | IndexOutOfBoundsException m) {
                return new Catalog("the build's description of " + e.getKey() + " cannot be read", java.util.List.of());
            }
            JsonNode served = entry.get("served"), unsupported = entry.get("unsupported");
            if (unsupported != null && (!unsupported.isTextual() || unsupported.textValue().isEmpty())) {
                // Protocol 1: a non-empty string (M33A-CDX-38).
                return new Catalog("the host reports " + e.getKey() + " with a malformed unsupported reason", java.util.List.of());
            }
            boolean both = served != null && unsupported != null;
            if (params) {
                if (served != null || unsupported == null || !"params".equals(unsupported.textValue())) {
                    return new Catalog("the host reports parameterized " + e.getKey() + " as served=" + served + ", unsupported=" + unsupported,
                            java.util.List.of());
                }
            } else if (both || (served == null && unsupported == null) || (served != null && !(served.isBoolean() && served.booleanValue()))) {
                return new Catalog("the host reports " + e.getKey() + "'s status as served=" + served + ", unsupported=" + unsupported,
                        java.util.List.of());
            } else if (unsupported != null && "params".equals(unsupported.textValue())) {
                return new Catalog("the host reports " + e.getKey() + " as parameterized; its description is not", java.util.List.of());
            } else if (unsupported != null) {
                unanchored.add(e.getKey() + " is not served by this host (" + unsupported.textValue() + ")");
            }
            // Then the script hash, from the contract's own script, even when a shared verifier cannot be anchored.
            com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors.ContractScript cs;
            try {
                cs = com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors.contractScript(snapshot, e.getKey(), javaFeature);
            } catch (com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors.Refused r) {
                unanchored.add(e.getKey() + " cannot be anchored: " + r.getMessage());
                continue;
            }
            try {
                com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors.derive(snapshot, e.getKey(), networkId, javaFeature);
            } catch (com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors.Refused r) {
                unanchored.add(e.getKey() + " cannot be anchored: " + r.getMessage());
            }
            String h = cs.hash();
            ObjectNode want = HostJson.object();
            if (params) {
                want.put("unapplied", h);
            } else {
                want.put("spend", h).put("mint", h);
                JsonNode x = cs.description().path("x-joust");
                if (x.path("stakeSelf").asBoolean(false) || x.has("purposes")) want.put("stake", h);
            }
            if (!want.equals(entry.get("scriptHashes"))) {
                return new Catalog("the host reports " + e.getKey() + "'s script hashes as " + entry.get("scriptHashes")
                        + ", the build's are " + want, java.util.List.of());
            }
        }
        return new Catalog(null, java.util.List.copyOf(unanchored));
    }

    private void stopHost() {
        running.session().close();
        running.started().stop();
    }

    public JsonNode describe(String contract) throws Exception {
        return call("describe", HostJson.object().put("contract", contract), false);
    }

    public JsonNode deployment(String contract) throws Exception {
        // The reply is validated under the session lock, so no other call is sent between a conflicting reply and the
        // host being stopped (M33A-CDX-31).
        synchronized (clock) {
            return checked(contract, call("deployment", HostJson.object().put("contract", contract), true));
        }
    }

    public JsonNode prerequisites(String contract) throws Exception {
        checkDeployment(contract);   // D13: before any prerequisites, publish or prepare (M33A-CDX-1)
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
