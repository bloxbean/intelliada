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
 * ADR-004 §12.2 E22 (L22): the run-time half of D3a's guard. The Marketplace installs whatever LSP4IJ is current, so
 * before the JOUST server may start, the installed LSP4IJ must be
 * <ol>
 *   <li>a release IntelliAda has <b>reviewed</b> ({@link #REVIEWED}). Behaviour that matters to D3a can change without a
 *   new feature: LSP4IJ 0.30.1 added edits applied on save ({@code willSaveWaitUntil}) and server-named virtual documents
 *   with no new {@code set…Feature} method (UP-CDX-8); and</li>
 *   <li>one whose {@code LSPClientFeatures.set…Feature} methods are exactly the classified ones
 *   ({@link ReadOnlyFeatureSet}), as a second alarm.</li>
 * </ol>
 * Otherwise:
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
    /** The LSP4IJ releases reviewed for D3a and I5 (each one an upgrade under Q11/L12). */
    public static final Set<String> REVIEWED = Set.of(BUILT_FOR);

    private static volatile Result forTests;

    /** For tests: the result to report instead of the installed LSP4IJ's; {@code null} restores it. */
    public static void setResultForTests(@Nullable Result r) {
        forTests = r;
    }

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
        Result t = forTests;
        if (t != null) return t;
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

    /** Null when {@code version} is reviewed and {@code setters} are exactly the classified features; otherwise the reason. */
    static @Nullable String check(Set<String> setters, String version) {
        Set<String> unknown = new TreeSet<>(setters);
        unknown.removeAll(ReadOnlyFeatureSet.FEATURES.keySet());
        Set<String> missing = new TreeSet<>(ReadOnlyFeatureSet.FEATURES.keySet());
        missing.removeAll(setters);
        boolean reviewed = REVIEWED.contains(version);
        if (reviewed && unknown.isEmpty() && missing.isEmpty()) return null;
        java.util.List<String> why = new java.util.ArrayList<>();
        if (!reviewed) why.add("that release has not been reviewed for JOUST's read-only client");
        if (!missing.isEmpty()) why.add("it lacks " + names(missing));
        if (!unknown.isEmpty()) why.add("it has features IntelliAda has not classified: " + names(unknown));
        return message(version, String.join("; ", why));
    }

    private static String message(String version, String why) {
        // UP-DES-7: the advice depends on which side of the reviewed release the installed one is.
        String advice = older(version, BUILT_FOR)
                ? "Update LSP4IJ to " + BUILT_FOR + " (Settings | Plugins)."
                : "This IntelliAda supports LSP4IJ " + BUILT_FOR + " only: install that version, or an IntelliAda update that supports " + version + ".";
        return "The JOUST language server is off: LSP4IJ " + version + " is not supported (" + why + "). "
                + ".jsc files keep their highlighting. " + advice;
    }

    /** True when {@code a} is a plain dotted version below {@code b}; false when either is not. */
    static boolean older(String a, String b) {
        if (a == null || !a.matches("[0-9]{1,6}(\\.[0-9]{1,6}){0,3}")) return false;
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0, q = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (p != q) return p < q;
        }
        return false;
    }

    private static String names(Set<String> setters) {
        StringBuilder sb = new StringBuilder();
        for (String s : setters) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s, 3, s.length() - "Feature".length());
        }
        return sb.toString();
    }

    /** The installed LSP4IJ's version: the plugin manager's, else the {@code <version>} in its jar's plugin.xml. */
    private static String installedVersion() {
        try {
            var d = PluginManagerCore.getPlugin(PluginId.getId(LSP4IJ_ID));
            if (d != null && d.getVersion() != null) return d.getVersion();
        } catch (RuntimeException | LinkageError e) {
            // not running in an IDE (a plain unit test): read the jar
        }
        try {
            Class<?> c = Class.forName("com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures", false,
                    JoustLsp4jCompat.class.getClassLoader());
            String self = String.valueOf(c.getResource("LSPClientFeatures.class"));
            if (!self.startsWith("jar:") || !self.contains("!/")) return UNKNOWN;
            String xml;
            try (var in = new java.net.URL(self.substring(0, self.indexOf("!/") + 2) + "META-INF/plugin.xml").openStream()) {
                xml = new String(in.readNBytes(256 * 1024), java.nio.charset.StandardCharsets.UTF_8);
            }
            var m = java.util.regex.Pattern.compile("<version>\\s*([0-9A-Za-z.+-]{1,40})\\s*</version>").matcher(xml);
            return m.find() ? m.group(1) : UNKNOWN;
        } catch (Exception | LinkageError e) {
            return UNKNOWN;
        }
    }

    static final String UNKNOWN = "(version unknown)";
}
