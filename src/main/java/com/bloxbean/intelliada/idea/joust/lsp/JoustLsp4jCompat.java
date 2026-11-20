package com.bloxbean.intelliada.idea.joust.lsp;

import com.bloxbean.intelliada.idea.joust.core.ReadOnlyFeatureSet;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;

/**
 * ADR-004 §12.2 E22 (L22): the run-time half of D3a's guard. IntelliAda classifies every LSP4IJ client feature of the
 * LSP4IJ it was built against ({@link ReadOnlyFeatureSet}). The Marketplace installs whatever LSP4IJ is current, so before
 * the JOUST server may start, the installed LSP4IJ's {@code LSPClientFeatures.set…Feature} methods must be exactly the
 * classified ones:
 * <ul>
 *   <li>a <b>newer</b> LSP4IJ with a feature IntelliAda has not reviewed would run it with LSP4IJ's default, which may be
 *   an edit (inline completion, linked editing) or may widen what the server sees (workspace folders);</li>
 *   <li>an <b>older</b> LSP4IJ lacks classes {@link JoustClientFeatures} installs, and would fail with a linkage error
 *   each time a .jsc is opened.</li>
 * </ul>
 * Either way the server stays off, .jsc keeps its TextMate highlighting, and the status bar and the JOUST actions say why.
 */
public final class JoustLsp4jCompat {

    static final String LSP4IJ_ID = "com.redhat.devtools.lsp4ij";
    /** The LSP4IJ this build classifies; kept in step with build.gradle. */
    public static final String BUILT_FOR = "0.30.1";

    private static volatile Result cached;

    private JoustLsp4jCompat() {
    }

    /** The outcome: {@code problem} is null when the JOUST server may run on the installed LSP4IJ. */
    public record Result(@Nullable String problem, String version) {
    }

    /** Null when the installed LSP4IJ is supported; otherwise a sentence saying why the JOUST server stays off. */
    public static @Nullable String problem() {
        return result().problem();
    }

    public static Result result() {
        Result r = cached;
        if (r == null) cached = r = compute();
        return r;
    }

    private static Result compute() {
        String version = installedVersion();
        Set<String> setters;
        try {
            setters = setters(Class.forName("com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures",
                    false, JoustLsp4jCompat.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError e) {
            return new Result(message(version, "its client features could not be read"), version);
        }
        return new Result(check(setters, version), version);
    }

    /** The public {@code set…Feature} methods a class declares, by name. */
    static Set<String> setters(Class<?> features) {
        Set<String> out = new TreeSet<>();
        for (Method m : features.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && m.getName().startsWith("set") && m.getName().endsWith("Feature")) {
                out.add(m.getName());
            }
        }
        return out;
    }

    /** Null when {@code setters} are exactly the classified features; otherwise the reason. */
    static @Nullable String check(Set<String> setters, String version) {
        Set<String> unknown = new TreeSet<>(setters);
        unknown.removeAll(ReadOnlyFeatureSet.FEATURES.keySet());
        Set<String> missing = new TreeSet<>(ReadOnlyFeatureSet.FEATURES.keySet());
        missing.removeAll(setters);
        if (unknown.isEmpty() && missing.isEmpty()) return null;
        StringBuilder why = new StringBuilder();
        if (!missing.isEmpty()) why.append("it lacks ").append(names(missing));
        if (!unknown.isEmpty()) {
            if (why.length() > 0) why.append(", and ");
            why.append("it has features IntelliAda has not reviewed: ").append(names(unknown));
        }
        return message(version, why.toString());
    }

    private static String message(String version, String why) {
        return "LSP4IJ " + version + " is not supported by this IntelliAda, which was built for LSP4IJ " + BUILT_FOR + " (" + why
                + "). The JOUST language server stays off; .jsc files keep their highlighting. Install the LSP4IJ version "
                + "this IntelliAda supports, or an IntelliAda built for yours.";
    }

    private static String names(Set<String> setters) {
        StringBuilder sb = new StringBuilder();
        for (String s : setters) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s, 3, s.length() - "Feature".length());
        }
        return sb.toString();
    }

    private static String installedVersion() {
        try {
            var d = PluginManagerCore.getPlugin(PluginId.getId(LSP4IJ_ID));
            return d == null || d.getVersion() == null ? "(version unknown)" : d.getVersion();
        } catch (RuntimeException | LinkageError e) {
            return "(version unknown)";
        }
    }
}
