package com.bloxbean.intelliada.idea.joust.verifier;

import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.HostSnapshot;
import com.bloxbean.intelliada.idea.joust.host.HostSpec;
import com.bloxbean.intelliada.idea.joust.host.HostVersions;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ADR-005 D12 version policy (L31; ADR5-SEC-1, -DES-7, -DES-14, -DES-21, -CDX-14). IntelliAda signs or exports only when
 * <ul>
 *   <li>the launch set holds the five runtime artifacts as jars whose hashes are one reviewed set's;</li>
 *   <li>no other launch entry holds a class under {@code org/julclang/state/{api,runtime,serve,host,verifier}/} (an
 *   earlier directory or a shaded jar would otherwise be what the JVM loads);</li>
 *   <li>{@code RuntimeVersion}, read from the reviewed runtime jar's own entry, and {@code initialize}'s
 *   {@code versions.engine.joust} both equal the set's label;</li>
 *   <li>every anchored description declares one of the set's {@code x-joust.version} values.</li>
 * </ul>
 * Otherwise the panel stays read-only and names the versions. A {@code dir} runtime or an unreviewed build is never in a set.
 */
public final class JoustVersionPolicy {

    static final List<String> ARTIFACTS = List.of("julc-state-api", "julc-state-runtime", "julc-state-serve", "julc-state-host",
            "julc-state-verifier");
    static final List<String> PACKAGES = List.of("org/julclang/state/api/", "org/julclang/state/runtime/", "org/julclang/state/serve/",
            "org/julclang/state/host/", "org/julclang/state/verifier/");

    public record ReviewedSet(String label, Map<String, String> jars, Set<Integer> xJoustVersions) {
    }

    /** May the session sign or export; if not, why (plain text, naming the versions). */
    public record Verdict(boolean maySign, String why, @Nullable String label) {
        static Verdict no(String why) {
            return new Verdict(false, why, null);
        }
    }

    private JoustVersionPolicy() {
    }

    /** The reviewed sets built into this plugin. */
    public static List<ReviewedSet> reviewed() {
        try (InputStream in = JoustVersionPolicy.class.getResourceAsStream("/joust/reviewed-sets.json")) {
            if (in == null) return List.of();
            return parse(HostJson.parse(in.readNBytes(256 * 1024)));
        } catch (IOException | HostJson.Malformed | RuntimeException e) {
            return List.of();
        }
    }

    static List<ReviewedSet> parse(JsonNode n) {
        List<ReviewedSet> out = new ArrayList<>();
        for (JsonNode s : n.path("sets")) {
            Map<String, String> jars = new LinkedHashMap<>();
            for (String a : ARTIFACTS) {
                String h = s.path("jars").path(a).textValue();
                if (h == null || !h.matches("[0-9a-f]{64}")) throw new IllegalStateException("a reviewed set without " + a);
                jars.put(a, h);
            }
            Set<Integer> xv = new java.util.HashSet<>();
            s.path("xJoustVersions").forEach(v -> xv.add(v.intValue()));
            String label = s.path("label").textValue();
            if (label == null || label.isBlank() || xv.isEmpty()) throw new IllegalStateException("a malformed reviewed set");
            out.add(new ReviewedSet(label, Map.copyOf(jars), Set.copyOf(xv)));
        }
        return List.copyOf(out);
    }

    /**
     * The verdict for a launch set: its spec (the jars' verified hashes), the snapshot (class index and copies),
     * {@code initialize}'s result, and the descriptions IntelliAda anchored.
     */
    public static Verdict check(List<ReviewedSet> sets, HostSpec spec, HostSnapshot snapshot, JsonNode initialize, int javaFeature,
                                Collection<JsonNode> anchoredDescriptions) {
        Map<String, Integer> byHash = new HashMap<>();
        List<HostSpec.Entry> launch = spec.launch();
        for (HostSpec.Entry e : launch) if (e.jar()) byHash.putIfAbsent(e.sha256(), e.ordinal());
        String engine = initialize.path("versions").path("engine").path("joust").textValue();
        String declared = engine == null ? "an unnamed JOUST" : "JOUST " + engine;
        for (ReviewedSet set : sets) {
            Map<String, Integer> ordinals = new HashMap<>();
            for (var a : set.jars().entrySet()) {
                Integer o = byHash.get(a.getValue());
                if (o != null) ordinals.put(a.getKey(), o);
            }
            if (ordinals.size() != ARTIFACTS.size()) continue;
            // No other entry holds a class of those packages: the JVM loads the reviewed jars' classes.
            Set<Integer> reviewedEntries = Set.copyOf(ordinals.values());
            for (HostSpec.Entry e : launch) {
                if (reviewedEntries.contains(e.ordinal())) continue;
                for (String c : snapshot.classesAt(e.ordinal())) {
                    for (String pkg : PACKAGES) {
                        if (c.startsWith(pkg)) {
                            return Verdict.no("read-only: another class-path entry also holds JOUST's " + pkg.replace('/', '.')
                                    + " classes (" + declared + ")");
                        }
                    }
                }
            }
            Path runtimeJar = snapshot.classPath().get(indexOf(launch, ordinals.get("julc-state-runtime")));
            String runtime = HostVersions.runtimeVersionAt(runtimeJar, javaFeature);
            if (!set.label().equals(runtime) || !set.label().equals(engine)) {
                return Verdict.no("read-only: the runtime reports " + runtime + " and the host " + engine + ", not the reviewed "
                        + set.label());
            }
            for (JsonNode d : anchoredDescriptions) {
                JsonNode v = d.path("x-joust").path("version");
                if (!v.isIntegralNumber() || !set.xJoustVersions().contains(v.intValue())) {
                    return Verdict.no("read-only: a description declares x-joust.version " + v + ", which " + set.label() + " was not reviewed with");
                }
            }
            return new Verdict(true, "JOUST " + set.label() + " (reviewed)", set.label());
        }
        return Verdict.no("read-only: " + declared + " is not a version this IntelliAda was reviewed against (its runtime jars "
                + "differ from every reviewed set); views and listings still work");
    }

    private static int indexOf(List<HostSpec.Entry> launch, int ordinal) {
        for (int i = 0; i < launch.size(); i++) if (launch.get(i).ordinal() == ordinal) return i;
        throw new IllegalStateException("no launch entry " + ordinal);
    }
}
