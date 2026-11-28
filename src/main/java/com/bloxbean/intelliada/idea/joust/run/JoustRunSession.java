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

    private static final com.intellij.openapi.diagnostic.Logger LOG = com.intellij.openapi.diagnostic.Logger.getInstance(JoustRunSession.class);

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

    /** Hosts this IDE started read-only, without the journal, and why (M33B1-FAB-1, -12): adopted read-only again. */
    private static final java.util.Map<HostSession, String> READ_ONLY =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * Starts (or reuses) the module's host for a binding (D11, D17): a new host's chain history is classified first, so
     * {@code initialize} carries {@code restoring}, its reverse reads go through the settlement guard, and once its
     * catalog is verified it is restored from the journal. A reused host keeps the journal it was started with.
     * {@code store} overrides this user's journal (tests).
     */
    public static JoustRunSession start(JoustHostService service, java.nio.file.Path moduleDir, Binding b,
                                        @Nullable com.bloxbean.intelliada.idea.joust.journal.JournalStore store, Launcher launcher,
                                        java.util.function.Consumer<JoustHostService.Running> adopted) throws Exception {
        JoustHostService.Running seen = service.running(moduleDir);
        // The journal is optional while nothing is built (M33B1-FAB-1): a store IntelliAda cannot use (a linked or
        // foreign ~/.intelliada, a file system without directory sync) starts the host read-only, without it, and says
        // why when building is asked for. Views, listings and decoding work as before.
        JoustRunJournal fresh = null;
        String journalless = null;
        if (seen == null) {
            try {
                fresh = JoustRunJournal.open(b, moduleDir, store);
            } catch (com.bloxbean.intelliada.idea.joust.journal.JournalStore.StoreFailed unusable) {
                journalless = unusable.getMessage();
                LOG.warn("JOUST: starting the dev host without IntelliAda's journal: " + journalless);
            }
        }
        JoustHostService.Running r = null;
        boolean owned = false, done = false;
        try {
            ObjectNode init = fresh != null ? JoustRunJournal.initParams(b.identity(), fresh.restoring()) : initParams(b.identity());
            HostSession.Reads reads = fresh != null ? fresh.reads(reads(b)) : reads(b);
            r = launcher.launch(init, b.key(), reads);
            adopted.accept(r);
            // Reuse only of the host that was seen (M33B1-CDX-150): one launched meanwhile never opened its journal.
            if (journalless == null && fresh == null && (seen == null || r.session() != seen.session())) {
                service.stop(moduleDir);
                throw new NetworkIdentity.Refused("the dev host it was about to reuse stopped meanwhile; start it again");
            }
            // A host whose own start has not yet registered its journal is never adopted without it (M33B1-CDX-156); one
            // started read-only, without a journal, is adopted read-only again (M33B1-FAB-12).
            JoustRunJournal existing = JOURNALS.get(r.session());
            if (journalless == null && fresh == null && existing == null) journalless = READ_ONLY.get(r.session());
            if (journalless == null && fresh == null && (existing == null || !existing.ready())) {   // registered and started (M33B1-CDX-163)
                throw new NetworkIdentity.Refused("the dev host is still starting; try again in a moment");
            }
            if (fresh == null && seen == null && journalless != null && READ_ONLY.putIfAbsent(r.session(), journalless) == null) {
                HostSession ro = r.session();
                ro.onEnded(why -> READ_ONLY.remove(ro));
            }
            owned = fresh != null && JOURNALS.putIfAbsent(r.session(), fresh) == null;
            if (owned) {
                HostSession ended = r.session();
                ended.onEnded(why -> JOURNALS.remove(ended));   // no entry outlives its session (M33B1-CDX-35)
            }
            JoustRunSession made = new JoustRunSession(b, r);
            made.journalless = journalless;
            made.verifyCatalog();   // the host's catalog is the build's, or the host is stopped (M33A-CDX-2)
            if (owned) {
                // The model's margin is the one the host was initialized with (M33B1-DES-7).
                if (r.requested().path("time").path("settlementSlots").asLong(-1) != fresh.settlementSlots()) {
                    throw new NetworkIdentity.Refused("the dev host was initialized with another settlement margin than IntelliAda's journal");
                }
                synchronized (made.clock) {
                    fresh.started(r.session(), made.clock, made::checkBinding);
                }
            }
            done = true;
            return made;
        } finally {
            if (!done) {
                // Every startup failure releases the module's lock and its journal (M33B1-CDX-17).
                if (owned) {
                    JOURNALS.remove(r.session());
                    service.stop(moduleDir);
                }
            }
            if (fresh != null && (!owned || !done)) fresh.close();
        }
    }

    /**
     * Discards a body the host prepared that was never handed out (D17): checked against the host-table model, its
     * journal entry removed. A handed-out body is refused: chain evidence or expiry releases it (I30).
     */
    void discard(String reservation) throws Exception {
        if (journal == null) throw new NetworkIdentity.Refused(journalBlocks());
        synchronized (clock) {
            journal.discard(reservation);
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
    @org.jetbrains.annotations.ApiStatus.Internal   // unreachable from production in M3.3b1; reviewed in M3.3b2 (ADR-005 L43, #38)
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
    /** Why this host runs without the journal (the store could not be used; M33B1-FAB-1), or null. */
    private volatile @Nullable String journalless;

    @Nullable String journalBlocks() {
        if (journal == null && journalless != null) return "IntelliAda's journal cannot be used (" + journalless + "); building needs it";
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
        // D14b, from the one policy the journal also uses (M33B1-DES-7).
        n.putObject("time").put("maxTtlSlots", com.bloxbean.intelliada.idea.joust.journal.TimePolicy.maxTtlSlots(id))
                .put("settlementSlots", com.bloxbean.intelliada.idea.joust.journal.TimePolicy.settlementSlots(id.networkClass()))
                .put("maxNowAheadSlots", com.bloxbean.intelliada.idea.joust.journal.TimePolicy.MAX_NOW_AHEAD);
        n.putObject("limits").put("callDeadlineMs", 60000).put("readsPerCall", 256).put("readBytesPerCall", BYTES_PER_CALL);
        n.put("restoring", false);
        return n;
    }

    /** The reverse-read answerer for a binding. */
    public static HostSession.Reads reads(Binding b) {
        return new ChainReads(b.api().copy(), b.identity().networkId(), BYTES_PER_CALL);
    }

    /** {@code max(previous now, the bound node's tip slot)}: never decreasing within the session (I28). */
    /** The tip the last {@link #now()} read, before the monotone clock: what a build is checked against (M33B1-CDX-126). */
    private final java.util.concurrent.atomic.AtomicLong lastTip = new java.util.concurrent.atomic.AtomicLong(-1);

    long now() throws Exception {
        long tip = tipSlot(binding.api().get("/blocks/latest"));
        // A chain far below the session's now (a rewind, even with an empty journal; M33B1-CDX-34): the host would refuse
        // every read as nowImplausible, so it is stopped and must be started again. D14: first a transient read, re-read
        // until three readings span at least 10 s (M33B1-DES-12); a recovered reading is used instead.
        long floor = lastNow.get();
        long ahead = com.bloxbean.intelliada.idea.joust.journal.TimePolicy.MAX_NOW_AHEAD;
        if (floor >= 0 && floor - tip > ahead) {
            long since = System.nanoTime();
            int reads = 1;
            while (reads < 3 || System.nanoTime() - since < 10_000_000_000L) {
                Thread.sleep(2_000);
                tip = tipSlot(binding.api().get("/blocks/latest"));
                reads++;
                if (floor - tip <= ahead || reads > 12) break;
            }
        }
        if (floor >= 0 && floor - tip > ahead) {
            // A chain this host cannot follow: it is stopped; a restart restores its transactions from the chain.
            // Fenced and stopped whatever the write does (M33B1-CDX-79).
            try {
                if (journal != null) journal.clockRewound(tip, "the chain went back to slot " + tip + ", far below a session's now");
            } finally {
                stopHost();
            }
            throw new NetworkIdentity.Refused("the chain went back to slot " + tip + ", far below this session's now (" + floor
                    + "); the dev host was stopped. Start it again");
        }
        lastTip.set(tip);   // the chain as read, apart from the host's monotone clock (M33B1-CDX-126)
        return monotone(lastNow, tip);
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
            lastRequest = params;   // the exact parameters sent: the checker's request (D13, ADR5-CDX-1)
            if (journal == null) return running.session().call(method, params, CALL_TIMEOUT, () -> false);
            // D17: settlement before the call when anything is live; the call's read tips go to the host-table model.
            journal.beforeCall(method, params.path("now").asLong(0), lastTip.get());
            // The host call alone in the try; the journal step once, with the outcome (M33B1-DES-11).
            JsonNode r = null;
            String error = null;
            Exception failure = null;
            try {
                r = running.session().call(method, params, CALL_TIMEOUT, () -> false);
            } catch (HostError e) {
                error = e.kind() + "/" + e.code();
                failure = e;
            } catch (Exception e) {
                error = "transport";
                failure = e;
            }
            journal.afterCall(method, r, error);
            if (failure != null) throw failure;
            return r;
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

    // ---- signing and submission (M3.3b2) -------------------------------------------------------------------------------

    /** The exact parameters of the last forward call (its {@code now} included), read under the call lock. */
    private @Nullable ObjectNode lastRequest;

    /** Tests: the checker to run instead of the plugin's bundled one (a test JVM is not the IDE's JBR 25). */
    static volatile com.bloxbean.intelliada.idea.joust.verifier.@Nullable JoustChecker checkerForTests;

    /** The fee IntelliAda signs at most on a devnet or test network (D14b's user maximum; a setting comes with M3.3b3). */
    public static final long MAX_FEE = 5_000_000;

    /** A submitted transaction: its id, the host's reservation, and the instance it creates or acts on (or null). */
    public record Submitted(String txHash, String reservation, @Nullable String instance) {
    }

    /**
     * Builds, checks, signs and submits one transaction (D13, I11, I25, I30; M3.3b2). In order, under the call lock:
     * <ol>
     *   <li>signing must be possible on this session (the signing gate passed, no read-only reason, the journal open);</li>
     *   <li>the host builds it ({@code prepare} or {@code publish}, after the deployment check);</li>
     *   <li>JOUST's reference checker accepts it, with inputs IntelliAda reads from the node itself;</li>
     *   <li>it is signed by splicing, with every key the body requires (the caller's and the chosen co-signers');</li>
     *   <li>it is journaled (awaiting approval: not yet handed out), submitted through the bound node, and recorded as
     *   submitted. A node's refusal discards it (it was never handed out); an unknown outcome keeps it submitted, so it
     *   is settled or expires on the chain's evidence (I30).</li>
     * </ol>
     * Returns null when a {@code publish} had nothing to publish. Any refusal before submission discards the body.
     */
    @org.jetbrains.annotations.ApiStatus.Internal   // the panel's write path wires it in M3.3b3
    public @Nullable Submitted submit(String method, ObjectNode params, String callerId, java.util.List<String> coSignerIds) throws Exception {
        synchronized (clock) {
            String gate = signingDisabled();
            if (gate != null) throw new NetworkIdentity.Refused("signing is not possible on this session: " + gate);
            if (journal == null) throw new NetworkIdentity.Refused(journalBlocks());
            String contract = method.equals("publish") ? params.path("contract").textValue() : params.path("call").path("contract").textValue();
            JsonNode result = build(method, params);
            ObjectNode request = lastRequest;
            if (method.equals("publish") && (result == null || result.isNull())) return null;   // already published
            JsonNode prepared = result.path("prepared");
            String reservation = prepared.path("reservation").textValue();
            try {
                var anchors = anchors(contract);
                var node = new com.bloxbean.intelliada.idea.joust.host.chain.NodeChain(binding.api().copy(), binding.identity().networkId());
                ObjectNode input = com.bloxbean.intelliada.idea.joust.verifier.JoustCheckInput.assemble(method, request, anchors,
                        binding.identity().networkId(), MAX_FEE, result, node);
                var checker = checkerForTests != null ? checkerForTests : com.bloxbean.intelliada.idea.joust.verifier.JoustChecker.bundled();
                var verdict = checker.check(input);
                if (verdict instanceof com.bloxbean.intelliada.idea.joust.verifier.JoustChecker.Refused r) {
                    throw new NetworkIdentity.Refused("JOUST's checker refused the transaction: " + r.why());
                }
                String cbor = prepared.path("cbor").asText("").toLowerCase(java.util.Locale.ROOT);
                var tx = com.bloxbean.intelliada.idea.joust.tx.TxCbor.transaction(cbor);
                var body = com.bloxbean.intelliada.idea.joust.tx.TxCbor.body(tx);
                var required = com.bloxbean.intelliada.idea.joust.sign.JoustSigner.requiredKeys(body, node::address);
                var accounts = com.bloxbean.intelliada.idea.joust.accounts.JoustAccounts.getInstance();
                java.util.List<com.bloxbean.intelliada.idea.joust.accounts.JoustAccounts.SigningKey> keys = new java.util.ArrayList<>();
                keys.add(accounts.signingKey(callerId, binding.identity().networkClass()));
                for (String c : coSignerIds) keys.add(accounts.signingKey(c, binding.identity().networkClass()));
                String signed = com.bloxbean.intelliada.idea.joust.sign.JoustSigner.sign(cbor, keys, required);
                var entry = new com.bloxbean.intelliada.idea.joust.journal.JournalStore.Entry(reservation, body.txHash(), cbor,
                        body.validFrom(), body.validTo(), com.bloxbean.intelliada.idea.joust.journal.JournalStore.Handout.approvalPending,
                        binding.node().getId(), com.bloxbean.intelliada.idea.joust.journal.JournalStore.State.prepared, null, null,
                        java.util.List.of(), label(method, params));
                long tip = lastTip.get(), sentNow = request.path("now").asLong(tip);
                journal.admit(entry, tip, sentNow);   // I25: journaled before it leaves IntelliAda
                // Recorded as submitted before it is sent: a crash after the send keeps its inputs held (I30).
                journal.handedOut(reservation, com.bloxbean.intelliada.idea.joust.journal.JournalStore.Handout.submitted);
                String id;
                try {
                    id = binding.api().submit(java.util.HexFormat.of().parseHex(signed));
                } catch (com.bloxbean.intelliada.idea.joust.host.chain.BlockfrostApi.NodeRefused refused) {
                    try {
                        journal.nodeRefused(reservation);   // the node never took it: its inputs are released
                    } catch (Exception kept) {
                        // kept until the chain settles it
                    }
                    throw new NetworkIdentity.Refused(refused.getMessage());
                } catch (com.bloxbean.intelliada.idea.joust.host.chain.BlockfrostApi.Failed | InterruptedException unknown) {
                    if (unknown instanceof InterruptedException) Thread.currentThread().interrupt();
                    throw new NetworkIdentity.Refused("the submission's outcome is unknown (" + unknown.getMessage()
                            + "); IntelliAda keeps the transaction and settles it from the chain");
                }
                if (!id.equals(body.txHash())) {
                    throw new NetworkIdentity.Refused("the node answered another transaction id (" + id + ") than the transaction's");
                }
                return new Submitted(body.txHash(), reservation, prepared.path("instance").textValue());
            } catch (Exception refused) {
                // Anything refused before the node saw it: the body was never handed out, and its inputs are released.
                discardQuietly(reservation);
                throw refused;
            }
        }
    }

    private void discardQuietly(@Nullable String reservation) {
        if (reservation == null || journal == null) return;
        try {
            journal.discard(reservation);
        } catch (Exception alreadyHandedOutOrGone) {
            // handed out (submitted): settled from the chain; or never committed: nothing to release
        }
    }

    private static String label(String method, ObjectNode params) {
        if (method.equals("publish")) return "publish " + params.path("prerequisite").asText("");
        JsonNode c = params.path("call");
        return c.path("kind").asText("") + " " + c.path("method").asText("");
    }

    /**
     * Waits until {@code txHash}'s journal entry shows it on chain (included or final), running settlement passes; false
     * when it expired, or did not land within {@code timeout}.
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public boolean awaitInclusion(String txHash, java.time.Duration timeout) throws Exception {
        if (journal == null) return false;
        long end = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < end) {
            synchronized (clock) {
                journal.settlement().poll();
            }
            for (var e : journal.journal().entries()) {
                if (!e.txHash().equals(txHash)) continue;
                switch (e.state()) {
                    case shallow, confirmed -> {
                        return true;
                    }
                    case expired, discarded -> {
                        return false;
                    }
                    default -> {
                    }
                }
            }
            Thread.sleep(1_000);
        }
        return false;
    }

    /**
     * D15's Deploy (M3.3b2): every outstanding prerequisite published and settled on chain before the next, then the
     * contract's {@code init} call. Returns what was submitted, in order (the last is {@code init}, with its instance).
     *
     * @param init the init call ({@code method} and {@code args}); its {@code contract}, {@code kind} and {@code address}
     *             are filled in
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public java.util.List<Submitted> deploy(String contract, String callerId, ObjectNode init, long ttlSlots, java.time.Duration settle)
            throws Exception {
        var accounts = com.bloxbean.intelliada.idea.joust.accounts.JoustAccounts.getInstance();
        String address = accounts.forSession(binding.identity()).stream().filter(e -> e.id.equals(callerId)).map(e -> e.address)
                .findFirst().orElseThrow(() -> new NetworkIdentity.Refused("the caller is not an account of this network"));
        java.util.List<Submitted> ids = new java.util.ArrayList<>();
        for (int round = 0; round < 32; round++) {
            JsonNode pre = prerequisites(contract);
            if (!pre.isArray() || pre.isEmpty()) break;
            String id = pre.get(0).path("id").textValue();
            ObjectNode publish = HostJson.object().put("ttlSlots", ttlSlots).put("contract", contract).put("prerequisite", id).put("address", address);
            Submitted s = submit("publish", publish, callerId, java.util.List.of());
            if (s == null) continue;
            ids.add(s);
            if (!awaitInclusion(s.txHash(), settle)) {
                throw new NetworkIdentity.Refused("the publication of " + id + " did not land on chain in time; Deploy stopped");
            }
        }
        ObjectNode call = init.deepCopy().put("contract", contract).put("kind", "init").put("address", address);
        Submitted s = submit("prepare", HostJson.object().put("ttlSlots", ttlSlots).set("call", call), callerId, java.util.List.of());
        if (s != null) ids.add(s);
        return ids;
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
        // Listings only (M33B1-SEC-15): a build never goes out through a page request, around build()'s checks.
        if (!method.equals("instances") && !method.equals("objects")) throw new IllegalArgumentException("not a listing: " + method);
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
