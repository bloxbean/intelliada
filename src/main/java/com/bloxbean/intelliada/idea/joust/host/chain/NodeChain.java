package com.bloxbean.intelliada.idea.joust.host.chain;

import com.bloxbean.intelliada.idea.joust.verifier.JoustCheckInput;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The bound node, read by IntelliAda itself at signing time (D13: never the answers it gave the host): outputs in the
 * protocol's snapshot encoding (as {@link ChainReads} gives them to the host), reference scripts held at an address, a
 * script credential's registration, and the protocol parameters. Also the address of an input, for the signer.
 */
public final class NodeChain implements JoustCheckInput.Chain {

    private static final Pattern REF = Pattern.compile("[0-9a-f]{64}#\\d{1,5}");
    private static final int PAGES = 50;

    private final BlockfrostApi api;
    private final int networkId;

    public NodeChain(BlockfrostApi api, int networkId) {
        this.api = api;
        this.networkId = networkId;
    }

    @Override
    public @Nullable JsonNode output(String ref) throws Exception {
        if (!REF.matcher(ref).matches()) throw new IllegalArgumentException("not an output reference: " + ref);
        String[] r = ref.split("#");
        JsonNode t = api.get("/txs/" + r[0] + "/utxos");
        if (t == null || !t.path("outputs").isArray()) return null;
        int index = Integer.parseInt(r[1]);
        for (JsonNode o : t.path("outputs")) {
            JsonNode col = o.get("collateral");
            if (col != null && col.isBoolean() && col.booleanValue()) continue;   // a collateral return is not an output
            if (o.path("output_index").asLong(-1) == index) return ChainReads.snapshot(o, r[0]);
        }
        return null;
    }

    /** The address holding {@code ref}, or null when the node does not know the output. */
    public @Nullable String address(String ref) throws Exception {
        JsonNode o = output(ref);
        return o == null ? null : o.path("address").textValue();
    }

    @Override
    public List<String> scriptsAt(String address) throws Exception {
        List<String> out = new ArrayList<>();
        for (int page = 1; page <= PAGES; page++) {
            JsonNode us = api.get("/addresses/" + address + "/utxos?count=100&page=" + page);
            if (us == null || !us.isArray() || us.isEmpty()) break;
            for (JsonNode u : us) {
                String h = u.path("reference_script_hash").textValue();
                if (h != null && !out.contains(h)) out.add(h);
            }
            if (us.size() < 100) break;
        }
        return out;
    }

    @Override
    public @Nullable Boolean registered(String scriptHash) throws Exception {
        JsonNode a = api.get("/accounts/" + StakeAddresses.reward(scriptHash, true, networkId));
        if (a == null) return Boolean.FALSE;
        JsonNode active = a.get("active"), reg = a.get("registered");
        if (active != null && active.isBoolean() && active.booleanValue() || reg != null && reg.isBoolean() && reg.booleanValue()) return Boolean.TRUE;
        if ((active == null || !active.isBoolean()) && (reg == null || !reg.isBoolean())) return null;
        return Boolean.FALSE;
    }

    @Override
    public JsonNode protocolParams() throws Exception {
        JsonNode pp = api.get("/epochs/latest/parameters");
        if (pp == null || !pp.isObject()) throw new IllegalStateException("the node has no protocol parameters");
        return pp;
    }
}
