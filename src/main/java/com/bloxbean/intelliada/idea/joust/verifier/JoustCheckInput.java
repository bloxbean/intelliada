package com.bloxbean.intelliada.idea.joust.verifier;

import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ADR-005 D13 "The input IntelliAda assembles" (F41): the checker's input, with only {@code result} from the host.
 * {@code method} and {@code request} are what IntelliAda sent; {@code anchors} are derived from the snapshot;
 * {@code resolved} holds every spending, reference and collateral input, read by IntelliAda through the bound node at
 * signing time (the refs to read come from the host's plan, and the checker refuses a body input that is not resolved);
 * {@code node} holds the script presence at the deployment's holder addresses and the payer's, and the verifiers'
 * stake registration ({@code true}, {@code false}, or {@code "unknown"} when the node cannot say); {@code protocolParams}
 * come from the bound node; {@code networkId} from D14; {@code maxFee} per D14b.
 */
public final class JoustCheckInput {

    /** What IntelliAda reads itself, through the bound node (D18), at signing time. */
    public interface Chain {
        /** The output at {@code ref} in the snapshot encoding, or null when it does not exist. */
        @Nullable JsonNode output(String ref) throws Exception;

        /** The reference-script hashes present in the outputs at {@code address}. */
        List<String> scriptsAt(String address) throws Exception;

        /** Whether a script's stake credential is registered; null when the node cannot say. */
        @Nullable Boolean registered(String scriptHash) throws Exception;

        JsonNode protocolParams() throws Exception;
    }

    /** The input cannot be assembled; signing stops. */
    public static final class Refused extends Exception {
        public Refused(String why) {
            super(why, null, false, false);
        }
    }

    private static final Pattern REF = Pattern.compile("[0-9a-f]{64}#\\d{1,5}");
    static final int MAX_INPUTS = 1000;

    private JoustCheckInput() {
    }

    public static ObjectNode assemble(String method, ObjectNode request, JoustAnchors.Anchors anchors, int networkId, long maxFee,
                                      JsonNode result, Chain chain) throws Refused {
        return assemble(method, request, anchors, networkId, maxFee, result, chain, null);
    }

    /**
     * As above, with the session's {@code clock} (as initialized or last reset) for keyed calls (D13, ADR5-CDX-8; keyed
     * calls come with M3c).
     */
    public static ObjectNode assemble(String method, ObjectNode request, JoustAnchors.Anchors anchors, int networkId, long maxFee,
                                      JsonNode result, Chain chain, @Nullable ObjectNode clock) throws Refused {
        if (!method.equals("prepare") && !method.equals("publish")) throw new Refused("only prepare and publish are signed");
        if (anchors.networkId() != networkId) throw new Refused("the anchors are for another network");
        JsonNode plan = result.path("prepared").path("plan");
        Set<String> refs = new LinkedHashSet<>();
        for (String group : List.of("inputs", "referenceInputs", "collateral")) {
            for (JsonNode r : plan.path(group)) {
                if (!r.isTextual() || !REF.matcher(r.textValue()).matches()) throw new Refused("the host's plan names a malformed input");
                refs.add(r.textValue());
            }
        }
        if (refs.size() > MAX_INPUTS) throw new Refused("the host's plan names too many inputs");
        ObjectNode in = HostJson.object();
        in.put("method", method);
        in.set("request", request.deepCopy());
        in.put("networkId", networkId);
        in.put("maxFee", String.valueOf(maxFee));
        in.set("anchors", anchors.toJson());
        ObjectNode resolved = in.putObject("resolved");
        ObjectNode node = in.putObject("node");
        try {
            for (String ref : refs) {
                JsonNode o = chain.output(ref);
                if (o == null) throw new Refused("an input of the prepared transaction is not on the node: " + ref);
                resolved.set(ref, o);
            }
            ObjectNode scriptsAt = node.putObject("scriptsAt");
            ObjectNode expected = JoustDeployment.expected(anchors);
            List<String> holders = new java.util.ArrayList<>();
            holders.add(anchors.contract().address());
            for (String v : List.of("outputVerifier", "mapVerifier")) {
                if (expected.path("addresses").has(v)) holders.add(expected.path("addresses").path(v).textValue());
            }
            String payer = request.path("call").path("address").textValue();
            if (payer == null) payer = request.path("address").textValue();
            if (payer == null) throw new Refused("the request names no payer");
            holders.add(payer);
            for (String a : holders) {
                ArrayNode arr = scriptsAt.putArray(a);
                for (String h : chain.scriptsAt(a)) arr.add(h);
            }
            ObjectNode regs = HostJson.object();
            // The contract's own stake credential too, when its deployment has one: a stakeRegistration publication of
            // the contract needs its status (M33A-CDX-3).
            List<String> hashes = new java.util.ArrayList<>();
            for (String v : List.of("stake", "outputVerifier", "mapVerifier")) {
                String h = expected.path("scriptHashes").path(v).textValue();
                if (h != null && !hashes.contains(h)) hashes.add(h);
            }
            // A publication that registers its target (kind stakeRegistration or verifier, as the checker reads it): that
            // target's registration, whichever script it is (M33A-DES-17).
            String prerequisite = request.path("prerequisite").textValue();
            if (method.equals("publish") && prerequisite != null) {
                String[] p = prerequisite.split(":", 2);
                if (p.length == 2 && !p[0].equals("referenceScript") && p[1].matches("[0-9a-f]{56}") && !hashes.contains(p[1])) {
                    hashes.add(0, p[1]);
                }
            }
            for (String h : hashes) {
                Boolean r = chain.registered(h);
                if (r == null) regs.put(h, "unknown");
                else regs.put(h, r);
            }
            if (!regs.isEmpty()) node.set("registrations", regs);
            in.set("protocolParams", chain.protocolParams());
        } catch (Refused e) {
            throw e;
        } catch (Exception e) {
            throw new Refused("the bound node could not be read for the check (" + e.getClass().getSimpleName() + ")");
        }
        if (clock != null) in.set("clock", clock.deepCopy());
        in.set("result", result.deepCopy());
        return in;
    }
}
