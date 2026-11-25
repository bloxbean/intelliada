package com.bloxbean.intelliada.idea.joust.host.chain;

import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.HostSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Answers the host's reverse reads from the bound node (ADR-005 D18; protocol 1 "Reverse reads"), through the
 * Blockfrost REST shape.
 * <ul>
 *   <li><b>One chain point per answer:</b> the tip is read before and after, and the answer read again when the tip moved
 *   (at most 8 times), all under <b>one deadline</b> covering every request and body of the read (M31B-CDX-2).</li>
 *   <li><b>Budgets:</b> provider pages accumulate under the call's remaining byte budget, and every answer is charged by
 *   its serialized JSON-RPC response; an item that cannot fit a 1 MiB chunk fails the read (M31B-CDX-3).</li>
 *   <li><b>Strict outcomes (T8):</b> a 404 is {@code absent} only where the protocol defines absence; a success body of
 *   the wrong shape, a closing tip that cannot be read, or an exhausted budget is {@code failed}, never an empty answer
 *   (M31B-CDX-5); an empty redeemer list is checked against the transaction's existence (M31B-CDX-13).</li>
 *   <li><b>Continuations</b> are installed only after the bracket is stable and serve only the query that opened them
 *   (M31B-CDX-11).</li>
 * </ul>
 */
public final class ChainReads implements HostSession.Reads {

    static final int MAX_ATTEMPTS = 8;
    static final int CHUNK_BYTES = 1024 * 1024;
    static final int PAGE = 100;
    static final int MAX_PAGES = 2000;
    static final long READ_DEADLINE_NANOS = 10_000_000_000L;
    /** Room for the JSON-RPC envelope when sizing chunks; charges use the exact encoded response. */
    static final int ENVELOPE_BYTES = 96;
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private static final Pattern REF = Pattern.compile("[0-9a-f]{64}#\\d{1,5}");
    private static final Pattern HASH28 = Pattern.compile("[0-9a-f]{56}");
    private static final Pattern HASH32 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern UNIT = Pattern.compile("[0-9a-f]{56}(?:[0-9a-f]{2}){0,32}");
    private static final Pattern HEX = Pattern.compile("(?:[0-9a-fA-F]{2})+");

    private final BlockfrostApi api;
    private final int networkId;
    private final long bytesPerCall;

    /** The current forward call (the reverse id's generation), its budget use, and its pending chunks by cursor. */
    private String call = "";
    /** The last chain point handed to the host in this call: later points must not go back from it (M31B-CDX-22). */
    private ObjectNode lastSent;
    /** Every block hash handed out in this call: a hash names one point (the host's rule). */
    private final java.util.Set<String> sentHashes = new java.util.HashSet<>();
    /** Room kept in the budget for a terminal {@code failed} answer, so one can always be sent (M31B-CDX-32). */
    static final int FAILURE_RESERVE = 1024;
    private long callProviderBytes0;
    private long callAnswerBytes;
    private final Map<String, Pending> pending = new HashMap<>();

    /** A list's remaining chunks, and the budget reserved for them when the cursor was installed (M31B-SEC-9). */
    private record Pending(String kind, String selector, ObjectNode tip, List<JsonNode> rest, long reserve) {
    }

    /** Budget held for pending chunks, so other reads cannot spend it before the continuations come. */
    private long reserved;

    public ChainReads(BlockfrostApi api, int networkId, long bytesPerCall) {
        this.api = api;
        this.networkId = networkId;
        this.bytesPerCall = bytesPerCall;
    }

    /** A failure of this read, carried to the host as {@code failed} with the chain point it was read at. */
    private static final class ReadFailed extends Exception {
        ReadFailed(String why) {
            super(why, null, false, false);
        }
    }

    @Override
    public synchronized ObjectNode read(String id, JsonNode params) throws Exception {
        String generation = id.substring(0, Math.max(0, id.indexOf('.')));
        if (!generation.equals(call)) {
            call = generation;
            lastSent = null;
            sentHashes.clear();
            reserved = 0;
            callProviderBytes0 = api.bytesRead();
            callAnswerBytes = 0;
            pending.clear();
        }
        String kind = params.path("kind").textValue();
        if (kind == null) throw new IllegalArgumentException("a read with no kind");
        ObjectNode answer = params.has("cursor") ? continuation(kind, params) : fresh(kind, params);
        // Every outgoing chain point obeys the host's rule, continuations included (M31B-CDX-29).
        if (answer.get("tip") instanceof ObjectNode t && !usable(t)) {
            if (lastSent == null) throw new IllegalStateException("no usable chain point");
            answer = failed(lastSent, "the chain point of this answer is behind this call's last one");
        }
        return charge(id, answer);
    }

    /** What one provider request may read: the remaining budget less the failure reserve, so a failure always fits. */
    private long requestCap() {
        return Math.max(0, remaining() - FAILURE_RESERVE);
    }

    /** What this call may still read from the provider and answer (M31B-SEC-6). */
    private long remaining() {
        return Math.max(0, bytesPerCall - (api.bytesRead() - callProviderBytes0) - callAnswerBytes - reserved);
    }

    /**
     * Charges the serialized response against what the call has left (provider bytes and answers together); an answer
     * over it becomes {@code failed}, which is charged in turn (M31B-CDX-16). Records the chain point handed out.
     */
    private ObjectNode charge(String id, ObjectNode answer) {
        long n = encoded(id, answer);
        boolean terminal = answer.has("failed");
        // An answer may use the budget only up to the reserve; a failure may use the reserve (M31B-CDX-32).
        if (n > remaining() - (terminal ? 0 : FAILURE_RESERVE)) {
            answer = failed(answer.has("tip") ? (ObjectNode) answer.get("tip") : lastSent, "the answer exceeds this call's read budget");
            n = encoded(id, answer);
            if (n > remaining()) {
                // No legal response fits: the call is cancelled instead (HostSession cancels on an exception).
                throw new IllegalStateException("this call's read budget is exhausted");
            }
        }
        callAnswerBytes += n;
        if (answer.get("tip") instanceof ObjectNode t) {
            lastSent = t;
            sentHashes.add(t.path("hash").textValue());
        }
        return answer;
    }

    /** The exact bytes of the JSON-RPC response HostSession sends for this answer. */
    private static long encoded(String id, ObjectNode answer) {
        ObjectNode resp = F.objectNode().put("jsonrpc", "2.0").put("id", id);
        resp.set("result", answer);
        return HostJson.write(resp).length;
    }

    /**
     * Whether a chain point may follow the last one handed out in this call: higher, or the same block. A lower or
     * conflicting point would read as a moved chain to the host (M31B-CDX-22).
     */
    private boolean usable(ObjectNode tip) {
        // The host's rule (Reads.answer): the same point again, or a later one in both slot and height whose hash was
        // not seen in this call (M31B-CDX-29).
        if (lastSent == null || tip.equals(lastSent)) return true;
        return tip.path("slot").longValue() > lastSent.path("slot").longValue()
                && tip.path("height").longValue() > lastSent.path("height").longValue()
                && !sentHashes.contains(tip.path("hash").textValue());
    }

    private ObjectNode continuation(String kind, JsonNode q) {
        String cursor = q.path("cursor").textValue();
        Pending p = cursor == null ? null : pending.get(cursor);
        // The continuation must repeat the query that opened it (M31B-CDX-11).
        if (p == null || !p.kind().equals(kind) || !p.selector().equals(selector(kind, q))) {
            throw new IllegalArgumentException("an unknown cursor, or one of another query");
        }
        pending.remove(cursor);
        reserved -= p.reserve();   // released to pay for this chunk (and to reserve what still follows)
        return chunk(p.kind(), p.selector(), p.tip(), p.rest(), true);
    }

    private ObjectNode fresh(String kind, JsonNode q) throws Exception {
        validate(kind, q);
        long deadline = System.nanoTime() + READ_DEADLINE_NANOS;
        if (kind.equals("tip")) {
            // A tip is its own chain point: one read, no bracket; a point behind the last one sent is read again.
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                try {
                    ObjectNode t = tip(deadline);
                    if (usable(t)) return with(t, "value", t.deepCopy());
                } catch (BlockfrostApi.Failed e) {
                    if (lastSent == null) throw e;
                    return failed(lastSent, e.getMessage());
                }
            }
            if (lastSent == null) throw new BlockfrostApi.Failed("the provider's tip went back");
            return failed(lastSent, "the provider's tip went back from this call's last chain point");
        }
        ObjectNode before = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            ObjectNode opened;
            try {
                opened = tip(deadline);
            } catch (BlockfrostApi.Failed e) {
                // A provider failure is a node failure for the host (D16), with the last usable chain point; only with
                // none at all is the read unanswerable (the call is then cancelled; M31B-DES-8).
                ObjectNode known = lastSent != null ? lastSent : before;
                if (known == null) throw e;
                return failed(known, e.getMessage());
            }
            if (!usable(opened)) continue;   // behind this call's last point: read again (M31B-CDX-22)
            before = opened;
            Answer a;
            try {
                a = answer(kind, q, before, deadline);
            } catch (ReadFailed | BlockfrostApi.Failed e) {
                return failed(before, e.getMessage());
            }
            ObjectNode after;
            try {
                after = tip(deadline);
            } catch (BlockfrostApi.Failed e) {
                return failed(before, e.getMessage());    // the opening chain point is known (M31B-CDX-5)
            }
            if (!usable(after)) continue;
            if (after.equals(before)) {
                // Chunks (and their cursors) only once the bracket is stable (M31B-CDX-11).
                return a.items == null ? a.single : chunk(kind, selector(kind, q), before, a.items, false);
            }
            // The tip moved during the read (a provider whose tip read is slower than a block; M31B-SEC-3). Read the
            // answer once more: identical answers read before and after the later tip was observed held at that tip,
            // so the answer is reported there. Otherwise try again.
            Answer again;
            try {
                again = answer(kind, q, after, deadline);
            } catch (ReadFailed | BlockfrostApi.Failed e) {
                return failed(after, e.getMessage());
            }
            if (sameAnswer(a, again)) {
                return again.items == null ? again.single : chunk(kind, selector(kind, q), after, again.items, false);
            }
        }
        ObjectNode at = lastSent != null ? lastSent : before;
        if (at == null) throw new BlockfrostApi.Failed("the provider's tip went back");
        return failed(at, "the chain kept moving: no stable chain point for this read");
    }

    /** The same content, whatever chain point each was labelled with. */
    private static boolean sameAnswer(Answer a, Answer b) {
        if (a.items != null || b.items != null) return a.items != null && a.items.equals(b.items);
        ObjectNode x = a.single.deepCopy(), y = b.single.deepCopy();
        x.remove("tip");
        y.remove("tip");
        if (x.path("value").isObject() && x.path("value").has("slot")) return false;   // a tip read never repeats
        return x.equals(y);
    }

    /** One read's outcome: a single answer, or a list to hand out in chunks. */
    private record Answer(ObjectNode single, List<JsonNode> items) {
        static Answer of(ObjectNode single) {
            return new Answer(single, null);
        }

        static Answer list(List<JsonNode> items) {
            return new Answer(null, items);
        }
    }

    private Answer answer(String kind, JsonNode q, ObjectNode tip, long deadline) throws Exception {
        switch (kind) {
            case "tip":
                return Answer.of(with(tip, "value", tip.deepCopy()));
            case "protocolParams": {
                JsonNode e = api.get("/epochs/latest", deadline, requestCap());
                if (e == null || !e.path("epoch").canConvertToLong()) throw new ReadFailed("the provider has no current epoch");
                JsonNode pp = api.get("/epochs/" + e.path("epoch").asLong() + "/parameters", deadline, requestCap());
                if (pp == null) pp = api.get("/epochs/latest/parameters", deadline, requestCap());
                if (pp == null || !pp.isObject() || !pp.has("min_fee_a")) throw new ReadFailed("the provider has no protocol parameters");
                return Answer.of(with(tip, "value", pp));
            }
            case "utxos": {
                List<JsonNode> out = new ArrayList<>();
                for (JsonNode u : pages("/addresses/" + q.path("address").textValue() + "/utxos", deadline)) out.add(snapshot(u, null));
                return out.isEmpty() ? Answer.of(absent(tip)) : Answer.list(out);
            }
            case "output": {
                String[] ref = q.path("ref").textValue().split("#");
                JsonNode t = api.get("/txs/" + ref[0] + "/utxos", deadline, requestCap());
                if (t == null) return Answer.of(absent(tip));
                if (!t.path("outputs").isArray()) throw new ReadFailed("the provider's transaction outputs are malformed");
                int index = Integer.parseInt(ref[1]);
                for (JsonNode o : t.path("outputs")) {
                    // Every row must carry a valid index, so a malformed one is never skipped into absence (M31B-CDX-21).
                    long i = nat(o.get("output_index"), 65535, "output index");
                    JsonNode col = o.get("collateral");
                    if (col != null && !col.isNull() && !col.isBoolean()) throw new ReadFailed("the provider's collateral flag is malformed");
                    // A collateral return exists only when a script failed; Blockfrost lists it flagged (M31B-CDX-12).
                    if (col != null && col.booleanValue()) continue;
                    if (i == index) return Answer.of(with(tip, "value", snapshot(o, ref[0])));
                }
                return Answer.of(absent(tip));
            }
            case "script": {
                String hash = q.path("hash").textValue();
                JsonNode t = api.get("/scripts/" + hash, deadline, requestCap());
                if (t == null) return Answer.of(absent(tip));
                JsonNode c = api.get("/scripts/" + hash + "/cbor", deadline, requestCap());
                if (c == null) return Answer.of(absent(tip));
                if (!c.path("cbor").isTextual() || !HEX.matcher(c.path("cbor").textValue()).matches()) {
                    throw new ReadFailed("the provider's script CBOR is malformed");
                }
                String type = switch (t.path("type").asText("")) {
                    case "plutusV1" -> "PlutusScriptV1";
                    case "plutusV2" -> "PlutusScriptV2";
                    case "plutusV3" -> "PlutusScriptV3";
                    default -> null;
                };
                if (type == null) return Answer.of(absent(tip));   // a native script: not a Plutus script the host can use
                ObjectNode v = F.objectNode().put("type", type).put("cborHex", PlutusScripts.doubleWrapped(c.path("cbor").textValue()));
                return Answer.of(with(tip, "value", v));
            }
            case "stakeRegistration": {
                String cred = q.path("credential").textValue();
                boolean unknown = false;
                for (boolean script : new boolean[]{true, false}) {
                    JsonNode a = api.get("/accounts/" + StakeAddresses.reward(cred, script, networkId), deadline, requestCap());
                    if (a == null) continue;
                    if (!a.isObject()) throw new ReadFailed("the provider's account is malformed");
                    JsonNode active = a.get("active");
                    JsonNode reg = a.get("registered");
                    if (active != null && active.isBoolean() && active.booleanValue() || reg != null && reg.isBoolean() && reg.booleanValue()) {
                        return Answer.of(with(tip, "value", F.objectNode().put("registered", true)));
                    }
                    if ((active == null || !active.isBoolean()) && (reg == null || !reg.isBoolean())) unknown = true;
                }
                return Answer.of(unknown ? unsupported(tip) : with(tip, "value", F.objectNode().put("registered", false)));
            }
            case "assetHistory": {
                List<JsonNode> out = new ArrayList<>();
                for (JsonNode t : pages("/assets/" + q.path("unit").textValue() + "/transactions?order=asc", deadline)) {
                    String h = t.path("tx_hash").textValue();
                    if (h == null || !HASH32.matcher(h).matches()) throw new ReadFailed("the provider's asset history is malformed");
                    out.add(F.objectNode().put("txHash", h));
                }
                return out.isEmpty() ? Answer.of(absent(tip)) : Answer.list(out);
            }
            case "redeemers": {
                String tx = q.path("txHash").textValue();
                JsonNode list = api.get("/txs/" + tx + "/redeemers", deadline, requestCap());
                if (list == null) return Answer.of(absent(tip));
                if (!list.isArray()) throw new ReadFailed("the provider's redeemers are malformed");
                if (list.isEmpty()) {
                    // Yaci Store answers [] for an unknown transaction too: [] is for a known one only (M31B-CDX-13).
                    if (api.get("/txs/" + tx, deadline, requestCap()) == null) return Answer.of(absent(tip));
                }
                ArrayNode out = F.arrayNode();
                for (JsonNode r : list) {
                    String purpose = r.path("purpose").asText("");
                    if (purpose.equals("withdrawal")) purpose = "reward";
                    String sh = r.path("script_hash").textValue(), dh = r.path("redeemer_data_hash").textValue();
                    if (!purpose.matches("spend|mint|cert|reward|voting|proposing")
                            || sh == null || !HASH28.matcher(sh).matches() || dh == null || !HASH32.matcher(dh).matches()) {
                        throw new ReadFailed("the provider's redeemer is malformed");
                    }
                    long index = nat(r.get("tx_index"), 65535, "redeemer index");
                    out.add(F.objectNode().put("purpose", purpose).put("index", index)
                            .put("scriptHash", sh).put("dataHash", dh));
                }
                return Answer.of(with(tip, "value", out));
            }
            case "datum": {
                JsonNode d = api.get("/scripts/datum/" + q.path("hash").textValue() + "/cbor", deadline, requestCap());
                if (d == null) return Answer.of(absent(tip));
                if (!d.path("cbor").isTextual() || !HEX.matcher(d.path("cbor").textValue()).matches()) {
                    throw new ReadFailed("the provider's datum CBOR is malformed");
                }
                return Answer.of(with(tip, "value", F.textNode(d.path("cbor").textValue().toLowerCase(java.util.Locale.ROOT))));
            }
            default:
                return Answer.of(unsupported(tip));
        }
    }

    /** Every page of a paged Blockfrost list, under the call's remaining byte budget and the read's deadline. */
    private List<JsonNode> pages(String path, long deadline) throws Exception {
        List<JsonNode> all = new ArrayList<>();
        String sep = path.contains("?") ? "&" : "?";
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode p = api.get(path + sep + "count=" + PAGE + "&page=" + page, deadline, requestCap());
            if (api.bytesRead() - callProviderBytes0 + callAnswerBytes > bytesPerCall) {
                throw new ReadFailed("the answer exceeds this call's read budget");
            }
            if (p == null) return all;
            if (!p.isArray()) throw new ReadFailed("the provider's list is malformed");
            p.forEach(all::add);
            if (p.size() < PAGE) return all;
        }
        throw new ReadFailed("the provider's list has too many pages");
    }

    /**
     * The next chunk of {@code items} (at most 1 MiB of serialized response), with a cursor for the rest. An item too
     * large for any chunk fails the read rather than overrun the cap (M31B-CDX-3).
     */
    private ObjectNode chunk(String kind, String selector, ObjectNode tip, List<JsonNode> items, boolean continuation) {
        int base = HostJson.write(with(tip, "value", F.arrayNode())).length + ENVELOPE_BYTES + 64;   // envelope and a cursor
        if (!continuation) {
            // The whole list must be deliverable before any chunk or cursor is published (M31B-CDX-23): each item fits a
            // chunk, and all chunks fit what the call has left.
            long total = 0;
            int chunkFill = base, chunks = 1;
            for (JsonNode it : items) {
                int n = HostJson.write(it).length + 1;
                if (base + n > CHUNK_BYTES) return failed(tip, "a single answer item is larger than a chunk");
                if (chunkFill + n > CHUNK_BYTES) {
                    chunks++;
                    chunkFill = base;
                }
                chunkFill += n;
                total += n;
            }
            if (total + (long) chunks * base > remaining() - FAILURE_RESERVE) return failed(tip, "the answer exceeds this call's read budget");
        }
        ArrayNode value = F.arrayNode();
        int bytes = base, i;
        for (i = 0; i < items.size(); i++) {
            int n = HostJson.write(items.get(i)).length + 1;
            if (bytes + n > CHUNK_BYTES) {
                if (value.isEmpty()) return failed(tip, "a single answer item is larger than a chunk");
                break;
            }
            value.add(items.get(i));
            bytes += n;
        }
        ObjectNode r = with(tip, "value", value);
        if (i < items.size()) {
            String cursor = UUID.randomUUID().toString();
            List<JsonNode> rest = new ArrayList<>(items.subList(i, items.size()));
            long reserve = deliverable(rest, base);
            reserved += reserve;
            pending.put(cursor, new Pending(kind, selector, tip, rest, reserve));
            r.put("more", cursor);
        }
        return r;
    }

    /** An upper bound on the bytes of the chunks that deliver {@code items}, each with {@code base} overhead. */
    private static long deliverable(List<JsonNode> items, int base) {
        long total = 0;
        int fill = base, chunks = 1;
        for (JsonNode it : items) {
            int n = HostJson.write(it).length + 1;
            if (fill + n > CHUNK_BYTES) {
                chunks++;
                fill = base;
            }
            fill += n;
            total += n;
        }
        return total + (long) chunks * base;
    }

    /** The query a continuation must repeat: the kind's selector field. */
    private static String selector(String kind, JsonNode q) {
        return switch (kind) {
            case "utxos" -> String.valueOf(q.path("address").textValue());
            case "assetHistory" -> String.valueOf(q.path("unit").textValue());
            default -> "";
        };
    }

    /** A Blockfrost UTxO in the protocol's snapshot encoding (ADR-0015 D6), exactly the seven fields. */
    static ObjectNode snapshot(JsonNode u, @Nullable String txHash) throws ReadFailed {
        String h = txHash != null ? txHash : u.path("tx_hash").textValue();
        if (h == null || !HASH32.matcher(h).matches() || !u.path("address").isTextual() || !u.path("amount").isArray()) {
            throw new ReadFailed("the provider's UTxO is malformed");
        }
        long index = nat(u.get("output_index"), 65535, "output index");
        ObjectNode o = F.objectNode().put("tx_hash", h).put("output_index", index)
                .put("address", u.path("address").textValue());
        ArrayNode amount = o.putArray("amount");
        for (JsonNode a : u.path("amount")) {
            String unit = a.path("unit").textValue();
            String q = a.path("quantity").isTextual() ? a.path("quantity").textValue() : a.path("quantity").asText();
            if (unit == null || q == null || !q.matches("[0-9]{1,40}")) throw new ReadFailed("the provider's amount is malformed");
            amount.addObject().put("unit", unit).put("quantity", q);
        }
        o.set("data_hash", nullable(u.get("data_hash")));
        o.set("inline_datum", nullable(u.get("inline_datum")));
        o.set("reference_script_hash", nullable(u.get("reference_script_hash")));
        return o;
    }

    private static JsonNode nullable(@Nullable JsonNode n) {
        return n == null || n.isNull() || (n.isTextual() && n.textValue().isEmpty()) ? F.nullNode() : n;
    }

    private ObjectNode tip(long deadline) throws Exception {
        JsonNode b = api.get("/blocks/latest", deadline, requestCap());
        if (b == null || !b.path("hash").isTextual() || !HASH32.matcher(b.path("hash").textValue()).matches()) {
            throw new BlockfrostApi.Failed("the provider has no readable tip");
        }
        try {
            return F.objectNode().put("slot", nat(b.get("slot"), Long.MAX_VALUE, "slot")).put("hash", b.path("hash").textValue())
                    .put("height", nat(b.get("height"), Long.MAX_VALUE, "height"));
        } catch (ReadFailed e) {
            throw new BlockfrostApi.Failed("the provider's tip is malformed");
        }
    }

    /** A JSON integer in {@code [0, max]}: never a fraction, a string or a negative (M31B-CDX-21). */
    static long nat(@Nullable JsonNode n, long max, String what) throws ReadFailed {
        if (n == null || !n.isIntegralNumber() || !n.canConvertToLong() || n.longValue() < 0 || n.longValue() > max) {
            throw new ReadFailed("the provider's " + what + " is malformed");
        }
        return n.longValue();
    }

    private static ObjectNode with(ObjectNode tip, String field, JsonNode value) {
        ObjectNode r = F.objectNode();
        r.set("tip", tip);
        r.set(field, value);
        return r;
    }

    private static ObjectNode failed(@Nullable ObjectNode tip, String why) {
        ObjectNode r = F.objectNode();
        if (tip != null) r.set("tip", tip);
        r.putObject("failed").put("message", why);
        return r;
    }

    private static ObjectNode absent(ObjectNode tip) {
        ObjectNode r = F.objectNode();
        r.set("tip", tip);
        r.put("absent", true);
        return r;
    }

    private static ObjectNode unsupported(ObjectNode tip) {
        ObjectNode r = F.objectNode();
        r.set("tip", tip);
        r.put("unsupported", true);
        return r;
    }

    /** Checks every parameter against the protocol's formats before it reaches a URL. */
    void validate(String kind, JsonNode q) {
        switch (kind) {
            case "tip", "protocolParams" -> only(q, "kind");
            case "utxos" -> {
                only(q, "kind", "address");
                String a = q.path("address").textValue();
                String prefix = networkId == 1 ? "addr1" : "addr_test1";
                if (a == null || !a.startsWith(prefix) || !a.substring(prefix.length()).matches("[02-9ac-hj-np-z]{20,200}")) {
                    throw new IllegalArgumentException("an address of another network, or malformed");
                }
            }
            case "output" -> require(q, "ref", REF);
            case "script" -> require(q, "hash", HASH28);
            case "stakeRegistration" -> require(q, "credential", HASH28);
            case "assetHistory" -> require(q, "unit", UNIT);
            case "redeemers" -> require(q, "txHash", HASH32);
            case "datum" -> require(q, "hash", HASH32);
            default -> only(q, "kind");
        }
    }

    private static void require(JsonNode q, String field, Pattern p) {
        only(q, "kind", field);
        String v = q.path(field).textValue();
        if (v == null || !p.matcher(v).matches()) throw new IllegalArgumentException("a malformed " + field);
    }

    private static void only(JsonNode q, String... fields) {
        if (q.size() != fields.length) throw new IllegalArgumentException("unexpected read parameters");
        for (String f : fields) if (!q.has(f)) throw new IllegalArgumentException("a read without " + f);
    }

    static int utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }
}
