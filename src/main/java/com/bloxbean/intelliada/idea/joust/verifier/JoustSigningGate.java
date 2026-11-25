package com.bloxbean.intelliada.idea.joust.verifier;

import com.bloxbean.intelliada.idea.joust.host.HostSnapshot;
import com.bloxbean.intelliada.idea.joust.host.HostSpec;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ADR-005 D12 + D13 composed (M33A-DES-3): whether a launch set may sign at all, and the anchors it signs against.
 * Every contract must anchor; the version policy must pass for exactly those descriptions; and the shared verifiers
 * must come from the reviewed {@code julc-state-verifier} jar's own entry (dev-host-1: "Anchor shared verifiers in the
 * snapshot's julc-state-verifier jar"). The per-contract deployment check and the checker itself follow, per call.
 */
public final class JoustSigningGate {

    public record Gate(boolean maySign, String why, Map<String, JoustAnchors.Anchors> anchors) {
    }

    private JoustSigningGate() {
    }

    /**
     * The gate over every contract the snapshot describes (the host's discovery rule), never a caller's subset
     * (M33A-CDX-17).
     */
    public static Gate evaluate(List<JoustVersionPolicy.ReviewedSet> sets, HostSpec spec, HostSnapshot snapshot, JsonNode initialize,
                                int networkId, int javaFeature) {
        List<String> contracts = new ArrayList<>();
        for (var e : snapshot.firstByPath().entrySet()) {
            String path = e.getKey();
            if (!path.startsWith("META-INF/joust/") || !path.endsWith(".json")) continue;
            try {
                if (com.bloxbean.intelliada.idea.joust.host.HostJson.parse(e.getValue().bytes()).path("x-joust").isMissingNode()) continue;
            } catch (com.bloxbean.intelliada.idea.joust.host.HostJson.Malformed m) {
                return new Gate(false, "read-only: " + path + " is not valid JSON", Map.of());
            }
            contracts.add(path.substring("META-INF/joust/".length(), path.length() - 5));
        }
        if (contracts.isEmpty()) return new Gate(false, "read-only: the build describes no contract", Map.of());
        Map<String, JoustAnchors.Anchors> anchors = new LinkedHashMap<>();
        List<JsonNode> descriptions = new ArrayList<>();
        for (String c : contracts) {
            try {
                JoustAnchors.Anchors a = JoustAnchors.derive(snapshot, c, networkId, javaFeature);
                anchors.put(c, a);
                descriptions.add(a.description());
            } catch (JoustAnchors.Refused r) {
                return new Gate(false, "read-only: " + c + " cannot be anchored: " + r.getMessage(), Map.of());
            }
        }
        JoustVersionPolicy.Verdict v = JoustVersionPolicy.check(sets, spec, snapshot, initialize, javaFeature, descriptions);
        if (!v.maySign()) return new Gate(false, v.why(), Map.copyOf(anchors));
        Integer verifierJar = v.ordinals().get("julc-state-verifier");
        for (var e : anchors.entrySet()) {
            JoustAnchors.Anchors a = e.getValue();
            boolean ok = verifierJar != null && a.outputVerifierEntry() == verifierJar
                    && (a.mapVerifier() == null || a.mapVerifierEntry() == verifierJar);
            if (!ok) {
                return new Gate(false, "read-only: " + e.getKey() + "'s verifiers do not come from the reviewed julc-state-verifier jar",
                        Map.copyOf(anchors));
            }
        }
        return new Gate(true, v.why(), Map.copyOf(anchors));
    }
}
