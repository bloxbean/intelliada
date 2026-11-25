package com.bloxbean.intelliada.idea.joust.verifier;

import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ADR-005 D13 "Deployment check" (I24): before any {@code prerequisites}, {@code publish} or {@code prepare}, the
 * host's {@code deployment} answer must equal what IntelliAda derives from the snapshot. Any difference stops the
 * session. The expected answer follows the protocol (dev-host-1 "deployment") and the host's rules, from the
 * description: the contract's address and policy; its spend and mint hash; a {@code stake} hash and {@code reward}
 * address for a staking contract ({@code stakeSelf}, a withdraw-purpose method, or {@code purposes}); the output
 * verifier's hash, holder and reward address when a method sends, deposits or receives; the map verifier's when the
 * contract has maps. Reference-script out-refs depend on the chain and are checked for shape and key only.
 */
public final class JoustDeployment {

    private static final Pattern OUTREF = Pattern.compile("[0-9a-f]{64}#[0-9]{1,5}");

    private JoustDeployment() {
    }

    /** The deployment the snapshot implies, without its {@code referenceScripts} and {@code governors}. */
    static ObjectNode expected(JoustAnchors.Anchors a) {
        JsonNode x = a.description().path("x-joust");
        boolean withdraw = false, sends = false;
        for (String group : List.of("actions", "creations", "purposes")) {
            for (JsonNode m : x.path(group)) {
                withdraw |= m.path("purpose").asText().equals("withdraw");
                sends |= m.path("returns").path("sends").asBoolean() || m.path("returns").path("deposits").asBoolean()
                        || m.at("/policies/receives").asBoolean();
            }
        }
        boolean staking = x.path("stakeSelf").asBoolean(false) || withdraw || x.has("purposes");
        ObjectNode d = HostJson.object();
        ObjectNode addresses = d.putObject("addresses").put("contract", a.contract().address());
        d.putObject("policies").put("contract", a.contract().hash());
        ObjectNode hashes = d.putObject("scriptHashes").put("mint", a.contract().hash()).put("spend", a.contract().hash());
        if (staking) {
            addresses.put("reward", a.rewardAddress());
            hashes.put("stake", a.contract().hash());
        }
        if (sends) verifier(addresses, hashes, "outputVerifier", a.outputVerifier());
        if (a.mapVerifier() != null) verifier(addresses, hashes, "mapVerifier", a.mapVerifier());
        return d;
    }

    private static void verifier(ObjectNode addresses, ObjectNode hashes, String label, JoustAnchors.Script v) {
        addresses.put(label, v.address()).put(label + "Reward", v.reward());
        hashes.put(label, v.hash());
    }

    /** Null when the host's {@code deployment} answer is the derived one; otherwise why it is not. */
    public static @Nullable String check(JoustAnchors.Anchors anchors, JsonNode deployment) {
        if (deployment == null || !deployment.isObject()) return "the host's deployment is not an object";
        Set<String> top = Set.of("addresses", "policies", "scriptHashes", "referenceScripts", "governors");
        for (Iterator<String> it = deployment.fieldNames(); it.hasNext(); ) {
            String f = it.next();
            if (!top.contains(f)) return "the host's deployment has an unknown field " + f;
        }
        ObjectNode want = expected(anchors);
        for (String f : List.of("addresses", "policies", "scriptHashes")) {
            if (!want.get(f).equals(deployment.get(f))) {
                return "the host's deployment " + f + " differ from the build's: expected " + want.get(f) + ", got " + deployment.get(f);
            }
        }
        JsonNode refs = deployment.get("referenceScripts");
        if (refs == null || !refs.isObject()) return "the host's deployment has no referenceScripts";
        for (Iterator<java.util.Map.Entry<String, JsonNode>> it = refs.fields(); it.hasNext(); ) {
            var e = it.next();
            boolean known = e.getKey().equals("contract") || want.path("scriptHashes").has(e.getKey()) && !e.getKey().equals("spend")
                    && !e.getKey().equals("mint") && !e.getKey().equals("stake");
            if (!known || !e.getValue().isTextual() || !OUTREF.matcher(e.getValue().textValue()).matches()) {
                return "the host's deployment names a reference script for " + e.getKey() + " that the build does not have";
            }
        }
        JsonNode gov = deployment.get("governors");
        if (gov == null || !gov.isArray() || !gov.isEmpty()) return "the host's deployment names governors (none are served in protocol 1)";
        return null;
    }
}
