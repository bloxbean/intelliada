package com.bloxbean.intelliada.idea.joust.host.chain;

import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.HostSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Answers the host's reverse reads from the bound node (ADR-005 D18; protocol 1 "Reverse reads"), through the
 * Blockfrost REST shape. Every answer carries one chain point: the tip is read before and after, and the answer is read
 * again when the tip moved (at most 8 times). {@code utxos} and {@code assetHistory} fetch every provider page inside
 * that bracket, under the call's remaining byte budget, and are handed to the host in chunks of at most 1 MiB of JSON with
 * call-local {@code more} cursors. A provider failure is {@code failed}; only kinds whose absence the protocol defines
 * answer {@code absent} on a 404.
 */
public final class ChainReads implements HostSession.Reads {

    static final int MAX_ATTEMPTS = 8;
    static final int CHUNK_BYTES = 1024 * 1024;
    static final int PAGE = 100;
    static final int MAX_PAGES = 2000;
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private static final Pattern REF = Pattern.compile("[0-9a-f]{64}#\\d{1,5}");
    private static final Pattern HASH28 = Pattern.compile("[0-9a-f]{56}");
    private static final Pattern HASH32 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern UNIT = Pattern.compile("[0-9a-f]{56}(?:[0-9a-f]{2}){0,32}");

    private final BlockfrostApi api;
    private final int networkId;
    private final long bytesPerCall;

    /** The current forward call (the reverse id's prefix), its provider bytes, and its pending chunks by cursor. */
    private String call = "";
    private long callStartBytes;
    private final Map<String, List<JsonNode>> pending = new HashMap<>();

    public ChainReads(BlockfrostApi api, int networkId, long bytesPerCall) {
        this.api = api;
        this.networkId = networkId;
        this.bytesPerCall = bytesPerCall;
    }

    /** A provider error, carried to the host as {@code failed} with the chain point it was read at. */
    private static final class ReadFailed extends Exception {
        ReadFailed(String why) {
            super(why, null, false, false);
        }
    }

    @Override
    public synchronized ObjectNode read(String id, JsonNode params) throws Exception {
        String prefix = id.substring(0, Math.max(0, id.indexOf('.')));
        if (!prefix.equals(call)) {
            call = prefix;
            callStartBytes = api.bytesRead();
            pending.clear();
        }
        String kind = params.path("kind").textValue();
        if (kind == null) throw new IllegalArgumentException("a read with no kind");
        // A continuation of a chunked answer: served from memory, at the chain point of its first chunk.
        if (params.has("cursor")) {
            List<JsonNode> rest = pending.remove(params.path("cursor").textValue());
            if (rest == null) throw new IllegalArgumentException("an unknown cursor");
            return chunk(rest, (ObjectNode) rest.get(0));
        }
        validate(kind, params);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            ObjectNode before = tip();
            ObjectNode answer;
            try {
                answer = answer(kind, params, before);
            } catch (ReadFailed | BlockfrostApi.Failed e) {
                ObjectNode r = F.objectNode();
                r.set("tip", before);
                r.putObject("failed").put("message", e.getMessage());
                return r;
            }
            ObjectNode after = tip();
            if (after.equals(before)) return answer;
        }
        ObjectNode r = F.objectNode();
        r.set("tip", tip());
        r.putObject("failed").put("message", "the chain kept moving: no stable chain point for this read");
        return r;
    }

    private ObjectNode answer(String kind, JsonNode q, ObjectNode tip) throws Exception {
        switch (kind) {
            case "tip":
                return with(tip, "value", tip.deepCopy());
            case "protocolParams": {
                JsonNode e = api.get("/epochs/latest");
                if (e == null || !e.path("epoch").canConvertToLong()) throw new ReadFailed("the provider has no current epoch");
                JsonNode pp = api.get("/epochs/" + e.path("epoch").asLong() + "/parameters");
                if (pp == null) pp = api.get("/epochs/latest/parameters");
                if (pp == null || !pp.isObject()) throw new ReadFailed("the provider has no protocol parameters");
                return with(tip, "value", pp);
            }
            case "utxos": {
                List<JsonNode> all = pages("/addresses/" + q.path("address").textValue() + "/utxos");
                List<JsonNode> out = new ArrayList<>();
                for (JsonNode u : all) out.add(snapshot(u, null));
                if (out.isEmpty()) return absent(tip);
                return chunk(out, tip);
            }
            case "output": {
                String[] ref = q.path("ref").textValue().split("#");
                JsonNode t = api.get("/txs/" + ref[0] + "/utxos");
                if (t == null) return absent(tip);
                int index = Integer.parseInt(ref[1]);
                for (JsonNode o : t.path("outputs")) {
                    if (o.path("output_index").asInt(-1) == index) return with(tip, "value", snapshot(o, ref[0]));
                }
                return absent(tip);
            }
            case "script": {
                String hash = q.path("hash").textValue();
                JsonNode t = api.get("/scripts/" + hash);
                JsonNode c = api.get("/scripts/" + hash + "/cbor");
                if (t == null || c == null || !c.path("cbor").isTextual()) return absent(tip);
                String type = switch (t.path("type").asText("")) {
                    case "plutusV1" -> "PlutusScriptV1";
                    case "plutusV2" -> "PlutusScriptV2";
                    case "plutusV3" -> "PlutusScriptV3";
                    default -> null;
                };
                if (type == null) return absent(tip);
                ObjectNode v = F.objectNode().put("type", type).put("cborHex", PlutusScripts.doubleWrapped(c.path("cbor").textValue()));
                return with(tip, "value", v);
            }
            case "stakeRegistration": {
                String cred = q.path("credential").textValue();
                Boolean any = null;
                boolean unknown = false;
                for (boolean script : new boolean[]{true, false}) {
                    JsonNode a = api.get("/accounts/" + StakeAddresses.reward(cred, script, networkId));
                    if (a == null) {
                        any = any == null ? Boolean.FALSE : any;
                        continue;
                    }
                    JsonNode active = a.get("active");
                    JsonNode reg = a.get("registered");
                    if (active != null && active.isBoolean() && active.booleanValue() || reg != null && reg.isBoolean() && reg.booleanValue()) {
                        return with(tip, "value", F.objectNode().put("registered", true));
                    }
                    if (active == null && reg == null) unknown = true;
                    else any = Boolean.FALSE;
                }
                if (unknown) return unsupported(tip);
                return with(tip, "value", F.objectNode().put("registered", false));
            }
            case "assetHistory": {
                List<JsonNode> all = pages("/assets/" + q.path("unit").textValue() + "/transactions?order=asc");
                List<JsonNode> out = new ArrayList<>();
                for (JsonNode t : all) {
                    String h = t.path("tx_hash").textValue();
                    if (h == null || !HASH32.matcher(h).matches()) throw new ReadFailed("the provider's asset history is malformed");
                    out.add(F.objectNode().put("txHash", h));
                }
                if (out.isEmpty()) return absent(tip);
                return chunk(out, tip);
            }
            case "redeemers": {
                JsonNode list = api.get("/txs/" + q.path("txHash").textValue() + "/redeemers");
                if (list == null) return absent(tip);
                ArrayNode out = F.arrayNode();
                for (JsonNode r : list) {
                    String purpose = r.path("purpose").asText("");
                    if (purpose.equals("withdrawal")) purpose = "reward";
                    ObjectNode o = F.objectNode().put("purpose", purpose).put("index", r.path("tx_index").asInt())
                            .put("scriptHash", r.path("script_hash").asText()).put("dataHash", r.path("redeemer_data_hash").asText());
                    out.add(o);
                }
                return with(tip, "value", out);
            }
            case "datum": {
                JsonNode d = api.get("/scripts/datum/" + q.path("hash").textValue() + "/cbor");
                if (d == null || !d.path("cbor").isTextual()) return absent(tip);
                return with(tip, "value", F.textNode(d.path("cbor").textValue().toLowerCase(java.util.Locale.ROOT)));
            }
            default:
                return unsupported(tip);
        }
    }

    /** Every page of a paged Blockfrost list, under the call's remaining byte budget. */
    private List<JsonNode> pages(String path) throws Exception {
        List<JsonNode> all = new ArrayList<>();
        String sep = path.contains("?") ? "&" : "?";
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode p = api.get(path + sep + "count=" + PAGE + "&page=" + page);
            if (api.bytesRead() - callStartBytes > bytesPerCall) throw new ReadFailed("the answer exceeds this call's read budget");
            if (p == null) return all;
            if (!p.isArray()) throw new ReadFailed("the provider's list is malformed");
            p.forEach(all::add);
            if (p.size() < PAGE) return all;
        }
        throw new ReadFailed("the provider's list has too many pages");
    }

    /** The first chunk of {@code items} (at most 1 MiB of JSON), with a cursor for the rest. */
    private ObjectNode chunk(List<JsonNode> items, ObjectNode tip) {
        ArrayNode value = F.arrayNode();
        int bytes = 0, i = 0;
        // A continuation list carries its chain point as its first element.
        int start = items.get(0) == tip ? 1 : 0;
        for (i = start; i < items.size(); i++) {
            int n = HostJson.write(items.get(i)).length + 1;
            if (value.size() > 0 && bytes + n > CHUNK_BYTES) break;
            value.add(items.get(i));
            bytes += n;
        }
        ObjectNode r = with(tip, "value", value);
        if (i < items.size()) {
            List<JsonNode> rest = new ArrayList<>();
            rest.add(tip);
            rest.addAll(items.subList(i, items.size()));
            String cursor = UUID.randomUUID().toString();
            pending.put(cursor, rest);
            r.put("more", cursor);
        }
        return r;
    }

    /** A Blockfrost UTxO in the protocol's snapshot encoding (ADR-0015 D6), exactly the seven fields. */
    static ObjectNode snapshot(JsonNode u, @Nullable String txHash) throws ReadFailed {
        String h = txHash != null ? txHash : u.path("tx_hash").textValue();
        if (h == null || !HASH32.matcher(h).matches() || !u.path("output_index").canConvertToInt() || !u.path("address").isTextual()) {
            throw new ReadFailed("the provider's UTxO is malformed");
        }
        ObjectNode o = F.objectNode().put("tx_hash", h).put("output_index", u.path("output_index").asInt())
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

    private ObjectNode tip() throws Exception {
        JsonNode b = api.get("/blocks/latest");
        if (b == null || !b.path("slot").canConvertToLong() || !b.path("height").canConvertToLong() || !b.path("hash").isTextual()
                || !HASH32.matcher(b.path("hash").textValue()).matches()) {
            throw new BlockfrostApi.Failed("the provider has no readable tip");
        }
        return F.objectNode().put("slot", b.path("slot").asLong()).put("hash", b.path("hash").textValue())
                .put("height", b.path("height").asLong());
    }

    private static ObjectNode with(ObjectNode tip, String field, JsonNode value) {
        ObjectNode r = F.objectNode();
        r.set("tip", tip);
        r.set(field, value);
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
}
