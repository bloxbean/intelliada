package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbi;
import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** ADR-004 M2 (D8, I8) with JOUST's real jscAbiDiff --out JSON: validation, the bound arguments, and the whole flow. */
public class JoustAbiTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    /** IntelliAda's private exchange directory, outside the project (M2-CDX-8). */
    @Rule
    public TemporaryFolder ide = new TemporaryFolder();

    private Path base;
    private Path module;
    private Path manifest;
    private String diffJson;
    private JSONObject diff;

    @Before
    public void setUp() throws Exception {
        base = tmp.getRoot().toPath().toRealPath();
        module = JoustM2Fixtures.gradleProject(Files.createDirectories(base.resolve("app")));
        diffJson = JoustM2Fixtures.text("jscAbiDiff-out.json");
        diff = new JSONObject(diffJson);
        manifest = module.resolve("src/main/jsc/org/acme/Counter.abi.json");
        Files.writeString(manifest, diff.getJSONObject("committed").getString("text"));   // the state before acceptance
    }

    @Test
    public void realDiffVerifies() throws Exception {
        JoustAbi.Diff d = JoustAbi.parse(diffJson);
        assertEquals("org.acme.Counter", d.contract());
        Path m = JoustAbi.manifestPath(d, module, module.resolve("src/main/jsc"));
        assertEquals(manifest.toRealPath(), m);
        assertNull(JoustAbi.verify(d, m));
    }

    @Test
    public void acceptIsRefusedWhenTextAndHashDisagree() throws Exception {
        JSONObject o = new JSONObject(diffJson);
        o.getJSONObject("now").put("text", o.getJSONObject("now").getString("text") + " ");
        JoustAbi.Diff d = JoustAbi.parse(o.toString());
        assertNotNull("a valid-looking hash that disagrees with its displayed text", JoustAbi.verify(d, manifest));
        Files.writeString(manifest, "{}");
        assertNotNull("the baseline changed on disk", JoustAbi.verify(JoustAbi.parse(diffJson), manifest));
    }

    @Test
    public void firstAcceptanceHasNoCommittedManifest() throws Exception {
        Files.delete(manifest);
        JSONObject o = new JSONObject(diffJson);
        o.getJSONObject("committed").put("sha256", "none").put("text", JSONObject.NULL);
        JoustAbi.Diff d = JoustAbi.parse(o.toString());
        assertNull(JoustAbi.verify(d, manifest));
        assertEquals(List.of(":app:acceptJscAbi", "--contract", "org.acme.Counter", "--committed-sha256", "none", "--sha256", d.nowSha()),
                JoustAbi.gradleAcceptArgs(":app", d));
        JSONObject wrong = new JSONObject(diffJson);
        wrong.getJSONObject("committed").put("sha256", "none");
        try {
            JoustAbi.parse(wrong.toString());
            fail("none with a text");
        } catch (JoustAbi.AbiException expected) {
            // refused
        }
    }

    @Test
    public void namesAndArgumentsAreBound() throws Exception {
        for (String bad : new String[]{"Counter --contract x", "org.acme.Counter\"; rm", "a..b", "1Counter", "", "a b"}) {
            try {
                JoustAbi.requireContract(bad);
                fail(bad);
            } catch (JoustAbi.AbiException expected) {
                // refused
            }
        }
        JoustAbi.Diff d = JoustAbi.parse(diffJson);
        assertEquals(List.of(":acceptJscAbi", "--contract", "org.acme.Counter", "--committed-sha256", d.committedSha(), "--sha256", d.nowSha()),
                JoustAbi.gradleAcceptArgs(":", d));
        assertEquals(List.of("build", "--accept-abi=org.acme.Counter@" + d.committedSha() + ".." + d.nowSha()), JoustAbi.npmAcceptArgs(d));
        assertEquals(":a:b:jscAbiDiff", JoustAbi.task(":a:b", "jscAbiDiff"));
        for (String bad : new String[]{"", "app", ":a b", ":a;b"}) {
            try {
                JoustAbi.task(bad, "x");
                fail(bad);
            } catch (JoustAbi.AbiException expected) {
                // refused
            }
        }
        JSONObject o = new JSONObject(diffJson);
        o.put("path", "../../../etc/passwd");
        try {
            JoustAbi.manifestPath(JoustAbi.parse(o.toString()), module, module.resolve("src/main/jsc"));
            fail("a manifest path outside");
        } catch (JoustAbi.AbiException expected) {
            // refused
        }
    }

    // ------------------------------------------------------------------ the flow

    private String h1() {
        return diff.getJSONObject("committed").getString("sha256");
    }

    private String h2() {
        return diff.getJSONObject("now").getString("sha256");
    }

    private String text1() {
        return diff.getJSONObject("committed").getString("text");
    }

    private String text2() {
        return diff.getJSONObject("now").getString("text");
    }

    /**
     * A runner that acts like JOUST, per module: {@code :<m>:jscAbiDiff} writes the recorded diff (with {@code path}
     * relative to that module); {@code :<m>:acceptJscAbi} writes the new manifest into <b>that module</b> only when the
     * bound hashes match its current manifest, else refuses as the real task does (acceptJscAbi-refused.log).
     */
    private class FakeJoust implements JoustAbiReview.Runner {
        final List<List<String>> runs = new ArrayList<>();
        String diffOverride;

        Path moduleOf(String task) {
            String m = task.substring(0, task.lastIndexOf(':'));
            return m.isEmpty() ? base : base.resolve(m.substring(1).replace(':', '/'));
        }

        @Override
        public JoustAbiReview.Result run(List<String> args) {
            runs.add(args);
            try {
                Path module = moduleOf(args.get(0));
                Path m = module.resolve("src/main/jsc/org/acme/Counter.abi.json");
                if (args.get(0).endsWith(":jscAbiDiff")) {
                    Files.writeString(Path.of(args.get(args.indexOf("--out") + 1)), diffOverride != null ? diffOverride : diffJson);
                    return new JoustAbiReview.Result(true, "", false);
                }
                String c = args.get(args.indexOf("--committed-sha256") + 1);
                String n = args.get(args.indexOf("--sha256") + 1);
                String onDisk = Files.exists(m) ? com.bloxbean.intelliada.idea.joust.core.JoustToolValidator.sha256(m) : "none";
                if (!c.equals(onDisk) || !n.equals(h2())) {
                    return new JoustAbiReview.Result(false, JoustM2Fixtures.text("acceptJscAbi-refused.log"), false);
                }
                boolean existed = Files.exists(m);
                Files.writeString(m, text2());
                // What acceptJscAbi prints (JscMain.java:167 at v0.1.0-pre2).
                return new JoustAbiReview.Result(true, "org/acme/Counter.jsc: note: " + (existed ? "accepted the changed" : "created the")
                        + " ABI manifest Counter.abi.json", false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class FakeUi implements JoustAbiReview.Ui {
        final List<String> reports = new ArrayList<>();
        String refusalSeen = "unset";
        boolean accept = true;
        Runnable onReview = () -> {
        };
        Runnable onConfirm = () -> {
        };

        @Override
        public boolean review(String module, JoustAbi.Diff diff, String refusal) {
            refusalSeen = refusal;
            onReview.run();
            return accept;
        }

        @Override
        public boolean confirm(String module, JoustAbi.Diff diff) {
            onConfirm.run();
            return accept;
        }

        @Override
        public void report(String title, String text, boolean error) {
            reports.add((error ? "ERROR " : "") + title + ": " + text);
        }

        @Override
        public void refused(String title, String text, Runnable reviewAgain) {
            reports.add("REFUSED " + title + ": " + text);
        }
    }

    private JoustAbiReview.Target target(Path module, String name, String contract) throws Exception {
        Path contracts = module.resolve("src/main/jsc").toRealPath();
        Path jsc = module.resolve("src/main/jsc/org/acme/Counter.jsc").toRealPath();
        return new JoustAbiReview.Target(name, true, ":" + name, module, contracts, jsc, contract);
    }

    private JoustAbiReview review(JoustAbiReview.Target t, FakeJoust joust, FakeUi ui, java.util.function.BooleanSupplier live) throws Exception {
        return new JoustAbiReview(t, () -> JoustProjectLayout.scan(base, JoustPaths.Flavor.current()), joust, ui, live,
                ide.getRoot().toPath().toRealPath(), () -> {
        });
    }

    /** Every file of the project with its SHA-256 (I21): what changed between two points. */
    private java.util.Map<Path, String> tree() throws Exception {
        java.util.Map<Path, String> out = new java.util.TreeMap<>();
        try (var s = Files.walk(base)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (Files.isRegularFile(p)) {
                    out.put(base.relativize(p), com.bloxbean.intelliada.idea.joust.core.JoustToolValidator.sha256(p));
                }
            }
        }
        return out;
    }

    private static java.util.Set<Path> changed(java.util.Map<Path, String> a, java.util.Map<Path, String> b) {
        java.util.Set<Path> out = new java.util.TreeSet<>();
        for (Path p : a.keySet()) if (!a.get(p).equals(b.get(p))) out.add(p);
        for (Path p : b.keySet()) if (!a.containsKey(p)) out.add(p);
        return out;
    }

    /** M2-CDX-8, I8, I21: two modules both at h1 with the same pending change; accepting one changes only its manifest. */
    @Test
    public void twoModulesWithTheSameContractAcceptOnlyTheSelectedOne() throws Exception {
        Path other = JoustM2Fixtures.gradleProject(Files.createDirectories(base.resolve("other")));
        Path otherManifest = other.resolve("src/main/jsc/org/acme/Counter.abi.json");
        Files.writeString(otherManifest, text1());
        var before = tree();   // before detection (M2-CDX-8)
        var t = target(module, "app", "org.acme.Counter");
        var owner = JoustProjectLayout.scan(base, JoustPaths.Flavor.current()).owner(t.jsc());
        assertEquals("production owner resolution picks the selected module", module, owner.gradle().dir());
        FakeJoust joust = new FakeJoust();
        FakeUi ui = new FakeUi();
        assertTrue(ui.reports.toString(), review(t, joust, ui, () -> true).run());
        assertNull(ui.refusalSeen);
        assertEquals(":app:jscAbiDiff", joust.runs.get(0).get(0));
        assertEquals(List.of(":app:acceptJscAbi", "--contract", "org.acme.Counter", "--committed-sha256", h1(), "--sha256", h2()), joust.runs.get(1));
        assertEquals(h2(), com.bloxbean.intelliada.idea.joust.core.JoustToolValidator.sha256(manifest));
        assertEquals(h1(), com.bloxbean.intelliada.idea.joust.core.JoustToolValidator.sha256(otherManifest));
        assertEquals("only that manifest changed (I21)", java.util.Set.of(base.relativize(manifest)), changed(before, tree()));
    }

    /** M2-CDX-6: the baseline changed to the new hash between review and accept: refused before running accept. */
    @Test
    public void aBaselineAlreadyAcceptedElsewhereIsRefusedNotReportedAsSuccess() throws Exception {
        FakeJoust joust = new FakeJoust();
        FakeUi ui = new FakeUi();
        ui.onConfirm = () -> {
            try {
                Files.writeString(manifest, text2());   // someone else accepted it meanwhile
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        assertFalse(review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run());
        assertTrue(ui.reports.toString(), ui.reports.stream().anyMatch(r -> r.startsWith("REFUSED ABI change not accepted")));
        assertEquals("accept never ran", 1, joust.runs.size());
    }

    /** M2-CDX-6 with the real task's refusal: the task refuses, the manifest is unchanged, and that is reported. */
    @Test
    public void aRefusingAcceptTaskIsReportedWithReviewAgain() throws Exception {
        FakeJoust joust = new FakeJoust() {
            @Override
            public JoustAbiReview.Result run(List<String> args) {
                if (args.get(0).endsWith(":acceptJscAbi")) {
                    runs.add(args);
                    try {
                        return new JoustAbiReview.Result(false, JoustM2Fixtures.text("acceptJscAbi-refused.log"), false);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                return super.run(args);
            }
        };
        FakeUi ui = new FakeUi();
        assertFalse(review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run());
        String r = ui.reports.stream().filter(x -> x.startsWith("REFUSED")).findFirst().orElseThrow();
        assertTrue(r, r.contains("not accepted: the committed manifest Counter.abi.json is now"));
        assertEquals(h1(), com.bloxbean.intelliada.idea.joust.core.JoustToolValidator.sha256(manifest));
    }

    /** M2-CDX-5: the review is bound to the selected source: same simple name in another package, or another file. */
    @Test
    public void theReviewIsBoundToTheSelectedSource() throws Exception {
        Path beta = Files.createDirectories(module.resolve("src/main/jsc/beta"));
        Files.copy(module.resolve("src/main/jsc/org/acme/Counter.jsc"), beta.resolve("Counter.jsc"));
        JSONObject wrongContract = new JSONObject(diffJson).put("contract", "beta.Counter");
        FakeJoust joust = new FakeJoust();
        joust.diffOverride = wrongContract.toString();
        FakeUi ui = new FakeUi();
        assertFalse(review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run());
        assertTrue(ui.reports.toString(), ui.reports.get(0).contains("not org.acme.Counter"));
        JSONObject wrongFile = new JSONObject(diffJson).put("file", "beta/Counter.jsc");
        joust = new FakeJoust();
        joust.diffOverride = wrongFile.toString();
        ui = new FakeUi();
        assertFalse(review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run());
        assertTrue(ui.reports.toString(), ui.reports.get(0).contains("not the selected source"));
        assertEquals("nothing reached accept", 1, joust.runs.size());
        assertEquals("org.acme.Counter", JoustAbiReview.Target.contractOf(module.resolve("src/main/jsc").toRealPath(),
                module.resolve("src/main/jsc/org/acme/Counter.jsc").toRealPath()));
    }

    /** M2-CDX-7: an incomplete inventory, or another manifest that appears, changes or disappears, fails verification. */
    @Test
    public void anIncompleteOrChangedInventoryFails() throws Exception {
        Path other = JoustM2Fixtures.gradleProject(Files.createDirectories(base.resolve("other")));
        Path otherManifest = other.resolve("src/main/jsc/org/acme/Counter.abi.json");
        for (String what : new String[]{"change", "add", "remove"}) {
            Files.writeString(manifest, text1());
            Files.writeString(otherManifest, text1());
            Files.deleteIfExists(other.resolve("src/main/jsc/org/acme/Extra.abi.json"));
            FakeJoust joust = new FakeJoust() {
                @Override
                public JoustAbiReview.Result run(List<String> args) {
                    JoustAbiReview.Result r = super.run(args);
                    try {
                        if (args.get(0).endsWith(":acceptJscAbi")) {
                            switch (what) {
                                case "change" -> Files.writeString(otherManifest, "{}");
                                case "add" -> Files.writeString(other.resolve("src/main/jsc/org/acme/Extra.abi.json"), "{}");
                                default -> Files.delete(otherManifest);
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return r;
                }
            };
            FakeUi ui = new FakeUi();
            assertFalse(what, review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run());
            assertTrue(what + ": " + ui.reports, ui.reports.stream().anyMatch(r -> r.startsWith("REFUSED") && r.contains("another manifest")));
        }
        Files.writeString(otherManifest, text1());
        Files.writeString(manifest, text1());
        // An unreadable directory in a contracts tree: the inventory is incomplete, so verification fails.
        Path locked = Files.createDirectories(other.resolve("src/main/jsc/locked"));
        Assume.assumeTrue("needs POSIX permissions", locked.toFile().setReadable(false, false));
        try {
            var snap = JoustAbi.manifests(JoustProjectLayout.scan(base, JoustPaths.Flavor.current()));
            assertNotNull(snap.incomplete());
            assertNotNull(JoustAbi.verifyAccepted(snap, snap, manifest, JoustAbi.parse(diffJson)));
        } finally {
            locked.toFile().setReadable(true, false);
        }
    }

    /** M2-DES-2, M2-CDX-2: once the action is no longer live after a dialog, nothing more runs. */
    @Test
    public void losingLivenessDuringTheDialogsStartsNoAccept() throws Exception {
        for (String when : new String[]{"review", "confirm"}) {
            Files.writeString(manifest, text1());
            java.util.concurrent.atomic.AtomicBoolean live = new java.util.concurrent.atomic.AtomicBoolean(true);
            FakeJoust joust = new FakeJoust();
            FakeUi ui = new FakeUi();
            if (when.equals("review")) ui.onReview = () -> live.set(false);
            else ui.onConfirm = () -> live.set(false);
            assertFalse(review(target(module, "app", "org.acme.Counter"), joust, ui, live::get).run());
            assertEquals(when + ": only the diff ran", 1, joust.runs.size());
            assertEquals(h1(), com.bloxbean.intelliada.idea.joust.core.JoustToolValidator.sha256(manifest));
        }
    }

    /** M2-CDX-8, I21: a dismissed review and a cancelled one leave the whole project tree as it was, detection included. */
    @Test
    public void dismissedOrCancelledReviewsChangeNoFile() throws Exception {
        for (String how : new String[]{"dismissed", "declined", "cancelled"}) {
            var before = tree();
            var t = target(module, "app", "org.acme.Counter");
            JoustProjectLayout.scan(base, JoustPaths.Flavor.current()).owner(t.jsc());
            FakeJoust joust = new FakeJoust();
            FakeUi ui = new FakeUi();
            java.util.concurrent.atomic.AtomicBoolean live = new java.util.concurrent.atomic.AtomicBoolean(true);
            switch (how) {
                case "dismissed" -> ui.accept = false;
                case "declined" -> ui.onConfirm = () -> ui.accept = false;
                default -> ui.onReview = () -> live.set(false);
            }
            assertFalse(how, review(t, joust, ui, live::get).run());
            assertEquals(how + ": no file changed (I21)", java.util.Set.of(), changed(before, tree()));
        }
    }

    /** M2-CDX-6: a concurrent acceptance writes h2 while the bound task refuses: the refusal wins. */
    @Test
    public void anExplicitRefusalWinsOverTheFinalFile() throws Exception {
        FakeJoust joust = new FakeJoust() {
            @Override
            public JoustAbiReview.Result run(List<String> args) {
                if (args.get(0).endsWith(":acceptJscAbi")) {
                    runs.add(args);
                    try {
                        Files.writeString(manifest, text2());   // another invocation accepted meanwhile
                        return new JoustAbiReview.Result(false, JoustM2Fixtures.text("acceptJscAbi-refused.log"), false);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                return super.run(args);
            }
        };
        FakeUi ui = new FakeUi();
        assertFalse(review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run());
        assertTrue(ui.reports.toString(), ui.reports.stream().anyMatch(r -> r.startsWith("REFUSED") && r.contains("not accepted")));
        assertTrue(ui.reports.stream().noneMatch(r -> r.contains("ABI change accepted")));
    }

    /** D8: an acceptance that succeeded, then a later build failure: accepted, with the failure noted, no Review again. */
    @Test
    public void anAcceptedChangeFollowedByABuildFailureIsAccepted() throws Exception {
        FakeJoust joust = new FakeJoust() {
            @Override
            public JoustAbiReview.Result run(List<String> args) {
                JoustAbiReview.Result r = super.run(args);
                return args.get(0).endsWith(":acceptJscAbi") ? new JoustAbiReview.Result(false, "org/acme/Counter.jsc: note: accepted the"
                        + " changed ABI manifest Counter.abi.json\nerror: compileJava failed", false) : r;
            }
        };
        FakeUi ui = new FakeUi();
        assertTrue(ui.reports.toString(), review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run());
        assertTrue(ui.reports.toString(), ui.reports.stream().anyMatch(r -> r.contains("ABI change accepted") && r.contains("build then failed")));
        assertTrue(ui.reports.stream().noneMatch(r -> r.startsWith("REFUSED")));
    }

    /** M2-CDX-7: an incomplete baseline (an unreadable build file or contracts tree) stops before any accept call. */
    @Test
    public void anIncompleteBaselineStopsBeforeAccept() throws Exception {
        Path other = JoustM2Fixtures.gradleProject(Files.createDirectories(base.resolve("other")));
        for (Path locked : new Path[]{other.resolve("build.gradle"), Files.createDirectories(other.resolve("src/main/jsc/locked"))}) {
            Assume.assumeTrue("needs POSIX permissions", locked.toFile().setReadable(false, false));
            try {
                FakeJoust joust = new FakeJoust();
                FakeUi ui = new FakeUi();
                assertFalse(locked.toString(), review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run());
                assertTrue(ui.reports.toString(), ui.reports.stream().anyMatch(r -> r.contains("could not all be checked") && r.contains("Nothing was run")));
                assertEquals(locked + ": zero accept calls", 1, joust.runs.size());
            } finally {
                locked.toFile().setReadable(true, false);
            }
        }
    }

    /** M2-SEC-3, M2-CDX-11: the diff is exchanged in the private directory given, which is cleaned up afterwards. */
    @Test
    public void theDiffUsesThePrivateDirectory() throws Exception {
        FakeJoust joust = new FakeJoust();
        FakeUi ui = new FakeUi();
        ui.accept = false;
        review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run();
        Path out = Path.of(joust.runs.get(0).get(joust.runs.get(0).indexOf("--out") + 1));
        assertTrue(out.toString(), out.startsWith(ide.getRoot().toPath().toRealPath()));
        assertFalse("outside the project", out.startsWith(base));
        try (var s = Files.list(ide.getRoot().toPath())) {
            assertEquals("cleaned up", 0, s.count());
        }
    }

    @Test
    public void aTamperedDiffNeverReachesAccept() throws Exception {
        FakeJoust joust = new FakeJoust();
        joust.diffOverride = diffJson.replace(h2(), "0".repeat(64));
        FakeUi ui = new FakeUi();
        assertFalse(review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run());
        assertNotNull("the dialog was told Accept is disabled", ui.refusalSeen);
        assertEquals("only the diff ran", 1, joust.runs.size());
    }

    /**
     * D8 for npm with the pinned CLI itself (M2-CDX-8): JSC0002 from joust build, then joust abi diff --out and
     * joust build --accept-abi=C@h1..h2 through the flow. Needs JOUST_NPM_PROJECT (an installed project from
     * joust new -t node -l jsc) and JOUST_NODE.
     */
    @Test
    public void theNpmFlowWithThePinnedCli() throws Exception {
        String installed = JoustTestTools.env("JOUST_NPM_PROJECT", "theNpmFlowWithThePinnedCli");
        String node = JoustTestTools.env("JOUST_NODE", "theNpmFlowWithThePinnedCli");
        if (installed == null || node == null) return;
        Path web = base.resolve("web");
        copyTree(Path.of(installed), web);
        Path jsc = web.resolve("contracts/app/Counter.jsc");
        Files.writeString(jsc, Files.readString(jsc).replace("state { count: Int, owner: Key }", "state { count: Int, owner: Key, note: Int }")
                .replace("return { count: 0, owner: caller }", "return { count: 0, owner: caller, note: 0 }"));
        Path npmManifest = web.resolve("contracts/app/Counter.abi.json");
        String committed = Files.readString(npmManifest);
        Path cli = web.resolve("node_modules/@julclang/joust/bin/joust.js");
        JoustAbiReview.Runner runner = args -> {
            try {
                List<String> cmd = new ArrayList<>(List.of(node, cli.toString()));
                cmd.addAll(args);
                Process p = new ProcessBuilder(cmd).directory(web.toFile()).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                return new JoustAbiReview.Result(p.waitFor() == 0, out, false);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        };
        JoustAbiReview.Result build = runner.run(List.of("build"));
        assertFalse(build.ok());
        var failures = JoustAbi.failures(build.output());
        assertEquals(build.output(), "JSC0002", failures.get(0).code());
        var layout = JoustProjectLayout.scan(base, JoustPaths.Flavor.current());
        var link = com.bloxbean.intelliada.idea.joust.project.JoustDiagnosticLinks.find(
                build.output().lines().filter(l -> l.contains("JSC0002")).findFirst().orElseThrow(), layout);
        assertEquals("the npm diagnostic links to the .jsc", jsc.toRealPath(), link.target());
        Path contracts = web.resolve("contracts").toRealPath();
        var t = new JoustAbiReview.Target("web", false, null, web.toRealPath(), contracts, jsc.toRealPath(),
                JoustAbiReview.Target.contractOf(contracts, jsc.toRealPath()));
        var before = tree();
        FakeUi ui = new FakeUi();
        Path tmp = ide.getRoot().toPath().toRealPath();
        boolean ok = new JoustAbiReview(t, () -> JoustProjectLayout.scan(base, JoustPaths.Flavor.current()), runner, ui, () -> true, tmp, () -> {
        }).run();
        assertTrue(ui.reports.toString(), ok);
        assertNotEquals(committed, Files.readString(npmManifest));
        var changed = changed(before, tree());
        assertTrue(changed.toString(), changed.contains(base.relativize(npmManifest)));
        for (Path p : changed) {
            assertTrue("only the manifest and build output changed: " + p, p.equals(base.relativize(npmManifest)) || p.startsWith("web/build"));
        }
    }

    static void copyTree(Path src, Path dst) throws Exception {
        try (var s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path t = dst.resolve(src.relativize(p).toString());
                if (Files.isSymbolicLink(p)) Files.createSymbolicLink(t, Files.readSymbolicLink(p));
                else if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    /** M2-CDX-6: both pinned refusal forms are matched by the selected source path, and only that source. */
    @Test
    public void refusalsAreMatchedBySourcePath() {
        String sourceChanged = "org/acme/Counter.jsc:1:1: error[JSC0002]: not accepted: the manifest the build computes now is "
                + "aaaa, not the reviewed bbbb";
        assertNotNull(JoustAbi.refusal(sourceChanged, "org/acme/Counter.jsc"));
        assertNotNull(JoustAbi.refusal("x\n" + sourceChanged + "\nBUILD FAILED", "org/acme/Counter.jsc"));
        assertNull(JoustAbi.refusal(sourceChanged.replace("Counter.jsc", "OtherCounter.jsc"), "org/acme/Counter.jsc"));
        assertNull(JoustAbi.refusal("org/acme/OtherCounter.jsc:1:1: error[JSC0002]: not accepted: the ABI manifest OtherCounter.abi.json",
                "org/acme/Counter.jsc"));
        assertNull("a later build failure is no refusal", JoustAbi.refusal("error: compileJava failed", "org/acme/Counter.jsc"));
        // The lookup-failure form names the contract, not the source; a Windows Gradle path uses backslashes.
        assertNotNull(JoustAbi.refusal("jsc: error[JSC0002]: --accept-abi=org.acme.Counter@…: no .jsc 2 contract org.acme.Counter; nothing accepted",
                "org/acme/Counter.jsc", "org.acme.Counter"));
        assertNull(JoustAbi.refusal("jsc: error[JSC0002]: --accept-abi=org.acme.CounterX@…: no contract; nothing accepted",
                "org/acme/Counter.jsc", "org.acme.Counter"));
        // A Windows Gradle prints native separators; on POSIX a backslash belongs to a name (M2-CDX-26).
        if (JoustTestTools.windows()) assertNotNull(JoustAbi.refusal(sourceChanged.replace("org/acme/", "org\\acme\\"), "org/acme/Counter.jsc"));
        else assertNull(JoustAbi.refusal(sourceChanged.replace("org/acme/", "org\\acme\\"), "org/acme/Counter.jsc"));
        // Evidence of acceptance, in both producers' forms.
        assertTrue(JoustAbi.reportsAccepted("org/acme/Counter.jsc: note: accepted the changed ABI manifest Counter.abi.json", "org/acme/Counter.jsc",
                "Counter.abi.json", "src/main/jsc/org/acme/Counter.abi.json"));
        assertTrue(JoustAbi.reportsAccepted("contracts/app/Counter.abi.json: created the ABI manifest; commit it", "app/Counter.jsc",
                "Counter.abi.json", "contracts/app/Counter.abi.json"));
        assertFalse(JoustAbi.reportsAccepted("error: compileJava failed", "org/acme/Counter.jsc", "Counter.abi.json", "x"));
        // 256 unrelated diagnostics cannot crowd out the refusal past the cap.
        var crowded = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(64);
        crowded.append("y".repeat(200) + "\n" + "other/X.jsc:1:1: error[JSC0502]: unrelated\n".repeat(300) + sourceChanged + "\n");
        assertNotNull(JoustAbi.refusal(crowded.toString(), "org/acme/Counter.jsc"));
        // Kept past the output cap as it arrives.
        var capture = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(64);
        capture.append("y".repeat(10_000) + "\n");
        for (int i = 0; i < 10; i++) capture.append(sourceChanged.substring(i * 10, Math.min(sourceChanged.length(), i * 10 + 10)));
        capture.append(sourceChanged.substring(Math.min(sourceChanged.length(), 100)) + "\nBUILD FAILED\n");
        assertNotNull(JoustAbi.refusal(capture.toString(), "org/acme/Counter.jsc"));
        assertTrue(capture.toString().length() < 64 + 2_100);
    }

    /** M2-CDX-6: a concurrent acceptance writes h2 while the bound task refuses because the source changed. */
    @Test
    public void aSourceChangedRefusalWinsOverTheFinalFile() throws Exception {
        FakeJoust joust = new FakeJoust() {
            @Override
            public JoustAbiReview.Result run(List<String> args) {
                if (args.get(0).endsWith(":acceptJscAbi")) {
                    runs.add(args);
                    try {
                        Files.writeString(manifest, text2());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return new JoustAbiReview.Result(false, "org/acme/Counter.jsc:1:1: error[JSC0002]: not accepted: the manifest the build"
                            + " computes now is " + "c".repeat(64) + ", not the reviewed " + h2(), false);
                }
                return super.run(args);
            }
        };
        FakeUi ui = new FakeUi();
        assertFalse(review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run());
        assertTrue(ui.reports.toString(), ui.reports.stream().anyMatch(r -> r.startsWith("REFUSED") && r.contains("computes now")));
    }

    /** M2-CDX-5: snapshot keys are exact path strings, so two manifests differing only in case are both checked. */
    @Test
    public void manifestsDifferingOnlyInCaseAreDistinct() throws Exception {
        JoustAbi.Diff d = JoustAbi.parse(diffJson);
        String a = base.resolve("app/X.abi.json").toString();
        String b = base.resolve("App/X.abi.json").toString();
        var before = new JoustAbi.Snapshot(new java.util.TreeMap<>(java.util.Map.of(a, h1(), b, "1".repeat(64))), null);
        var after = new JoustAbi.Snapshot(new java.util.TreeMap<>(java.util.Map.of(a, h2(), b, "2".repeat(64))), null);
        assertEquals(2, before.hashes().size());
        String wrong = JoustAbi.verifyAccepted(before, after, Path.of(a), d);
        assertNotNull("the other-case manifest changed", wrong);
        assertTrue(wrong, wrong.contains("App"));
        var unchanged = new JoustAbi.Snapshot(new java.util.TreeMap<>(java.util.Map.of(a, h2(), b, "1".repeat(64))), null);
        assertNull(JoustAbi.verifyAccepted(before, unchanged, Path.of(a), d));
    }

    /** M2-CDX-7, M2-CDX-12: an npm app under src/main/webapp is owned, inventoried, and its manifest's change detected. */
    @Test
    public void aNestedAppUnderSrcMainIsInTheInventory() throws Exception {
        Path webapp = Files.createDirectories(module.resolve("src/main/webapp"));
        Files.writeString(webapp.resolve("joust.config.json"), "{}");
        Path nested = Files.createDirectories(webapp.resolve("contracts/app"));
        Files.writeString(nested.resolve("Counter.jsc"), "jsc 1\n");
        Path nestedManifest = Files.writeString(nested.resolve("Counter.abi.json"), "{}");
        var layout = JoustProjectLayout.scan(base, JoustPaths.Flavor.current());
        assertEquals(webapp, layout.owner(nested.resolve("Counter.jsc").toRealPath()).npm().dir());
        assertTrue(JoustAbi.manifests(layout).hashes().containsKey(nestedManifest.toRealPath().toString()));
        FakeJoust joust = new FakeJoust() {
            @Override
            public JoustAbiReview.Result run(List<String> args) {
                JoustAbiReview.Result r = super.run(args);
                if (args.get(0).endsWith(":acceptJscAbi")) {
                    try {
                        Files.writeString(nestedManifest, "{\"changed\":1}");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                return r;
            }
        };
        FakeUi ui = new FakeUi();
        assertFalse(review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run());
        assertTrue(ui.reports.toString(), ui.reports.stream().anyMatch(r -> r.contains("another manifest") && r.contains("webapp")));
    }

    /** M2-CDX-6: a failed accept run that neither refuses nor reports accepting is not taken as an acceptance. */
    @Test
    public void aFailedRunWithoutEvidenceIsNotAnAcceptance() throws Exception {
        FakeJoust joust = new FakeJoust() {
            @Override
            public JoustAbiReview.Result run(List<String> args) {
                JoustAbiReview.Result r = super.run(args);   // writes h2 as a real acceptance would
                return args.get(0).endsWith(":acceptJscAbi") ? new JoustAbiReview.Result(false, "", false) : r;   // output lost
            }
        };
        FakeUi ui = new FakeUi();
        assertFalse(review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run());
        assertTrue(ui.reports.toString(), ui.reports.stream().anyMatch(r -> r.startsWith("REFUSED") && r.contains("without reporting")));
    }

    /** M2-CDX-23: the npm contracts directory changed during the confirmation: no accept, both manifests unchanged. */
    @Test
    public void aContractsDirectoryChangedDuringReviewRunsNothing() throws Exception {
        Path web = Files.createDirectories(base.resolve("web"));
        Files.writeString(web.resolve("joust.config.json"), "{\"contracts\":\"c1\"}");
        Path[] manifests = new Path[2];
        for (int i = 0; i < 2; i++) {
            Path dir = Files.createDirectories(web.resolve("c" + (i + 1) + "/app"));
            Files.copy(module.resolve("src/main/jsc/org/acme/Counter.jsc"), dir.resolve("Counter.jsc"));
            manifests[i] = Files.writeString(dir.resolve("Counter.abi.json"), text1());
        }
        JSONObject o = new JSONObject(diffJson).put("contract", "app.Counter").put("file", "app/Counter.jsc").put("path", "c1/app/Counter.abi.json");
        List<List<String>> runs = new ArrayList<>();
        JoustAbiReview.Runner runner = args -> {
            runs.add(args);
            try {
                if (args.get(0).equals("abi")) Files.writeString(Path.of(args.get(args.indexOf("--out") + 1)), o.toString());
                else Files.writeString(manifests[1], text2());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return new JoustAbiReview.Result(true, "", false);
        };
        FakeUi ui = new FakeUi();
        ui.onConfirm = () -> {
            try {
                Files.writeString(web.resolve("joust.config.json"), "{\"contracts\":\"c2\"}");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        Path contracts = web.resolve("c1").toRealPath();
        Path jsc = web.resolve("c1/app/Counter.jsc").toRealPath();
        var t = new JoustAbiReview.Target("web", false, null, web.toRealPath(), contracts, jsc, "app.Counter");
        boolean ok = new JoustAbiReview(t, () -> JoustProjectLayout.scan(base, JoustPaths.Flavor.current()), runner, ui, () -> true,
                ide.getRoot().toPath().toRealPath(), () -> {
        }).run();
        assertFalse(ok);
        assertEquals("the diff ran bound to c1", List.of("abi", "diff", "app.Counter", "--src", "c1"), runs.get(0).subList(0, 5));
        assertEquals("zero accept calls", 1, runs.size());
        assertEquals(text1(), Files.readString(manifests[0]));
        assertEquals(text1(), Files.readString(manifests[1]));
        assertTrue(ui.reports.toString(), ui.reports.stream().anyMatch(r -> r.contains("contracts directory changed")));
    }

    /** M2-CDX-7: a detection file that exists but is not valid UTF-8 leaves the inventory incomplete: no accept. */
    @Test
    public void anUndecodableDetectionFileStopsBeforeAccept() throws Exception {
        Path other = JoustM2Fixtures.gradleProject(Files.createDirectories(base.resolve("other")));
        Files.write(other.resolve("build.gradle"), new byte[]{'p', 'l', 'u', (byte) 0xC3, (byte) 0x28, 'g'});
        assertFalse(JoustProjectLayout.scan(base, JoustPaths.Flavor.current()).complete());
        FakeJoust joust = new FakeJoust();
        FakeUi ui = new FakeUi();
        assertFalse(review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run());
        assertEquals("zero accept calls", 1, joust.runs.size());
    }

    /** M2-CDX-6: a successful run that does not report writing this manifest (npm's no-op) is not an acceptance. */
    @Test
    public void aSuccessfulRunWithoutEvidenceIsNotAnAcceptance() throws Exception {
        FakeJoust joust = new FakeJoust() {
            @Override
            public JoustAbiReview.Result run(List<String> args) {
                if (args.get(0).endsWith(":acceptJscAbi")) {
                    runs.add(args);
                    try {
                        Files.writeString(manifest, text2());   // another invocation's write
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return new JoustAbiReview.Result(true, "built 1 contract(s)", false);
                }
                return super.run(args);
            }
        };
        FakeUi ui = new FakeUi();
        assertFalse(review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run());
        assertTrue(ui.reports.toString(), ui.reports.stream().anyMatch(r -> r.startsWith("REFUSED") && r.contains("succeeded without reporting")));
    }

    /** M2-CDX-6: a first creation reported past the output cap, then a build failure: accepted. */
    @Test
    public void aCreationPastTheCapIsStillEvidence() {
        var capture = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(64);
        capture.append("z".repeat(500) + "\n" + "contracts/app/Counter.abi.json: created the ABI manifest; commit it beside the .jsc file\n"
                + "error: something later failed\n");
        assertTrue(JoustAbi.reportsAccepted(capture.toString(), "app/Counter.jsc", "Counter.abi.json", "contracts/app/Counter.abi.json"));
    }

    /** M2-CDX-23: the module's linked build or Gradle path changed during the confirmation (a reimport): no accept. */
    @Test
    public void aGradleRemappingDuringReviewRunsNothing() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> linked = new java.util.concurrent.atomic.AtomicReference<>(base + "|:app");
        FakeJoust joust = new FakeJoust();
        FakeUi ui = new FakeUi();
        ui.onConfirm = () -> linked.set(base + "|:b");
        boolean ok = new JoustAbiReview(target(module, "app", "org.acme.Counter"), () -> JoustProjectLayout.scan(base, JoustPaths.Flavor.current()),
                joust, ui, () -> true, ide.getRoot().toPath().toRealPath(), () -> {
        }, linked::get).run();
        assertFalse(ok);
        assertEquals("zero accept calls", 1, joust.runs.size());
        assertEquals(h1(), com.bloxbean.intelliada.idea.joust.core.JoustToolValidator.sha256(manifest));
        assertTrue(ui.reports.toString(), ui.reports.stream().anyMatch(r -> r.contains("Gradle project changed")));
    }

    /** M2-CDX-25: a directory that is both a Gradle module and an npm root: the npm review is accepted (root chosen by kind). */
    @Test
    public void aMixedDirectoryReviewsItsNpmContracts() throws Exception {
        Files.writeString(module.resolve("joust.config.json"), "{\"contracts\":\"contracts\"}");
        Path dir = Files.createDirectories(module.resolve("contracts/app"));
        Files.copy(module.resolve("src/main/jsc/org/acme/Counter.jsc"), dir.resolve("Counter.jsc"));
        Path m = Files.writeString(dir.resolve("Counter.abi.json"), text1());
        JSONObject o = new JSONObject(diffJson).put("contract", "app.Counter").put("file", "app/Counter.jsc").put("path", "contracts/app/Counter.abi.json");
        List<List<String>> runs = new ArrayList<>();
        JoustAbiReview.Runner runner = args -> {
            runs.add(args);
            try {
                if (args.get(0).equals("abi")) {
                    Files.writeString(Path.of(args.get(args.indexOf("--out") + 1)), o.toString());
                    return new JoustAbiReview.Result(true, "", false);
                }
                Files.writeString(m, text2());
                return new JoustAbiReview.Result(true, "contracts/app/Counter.abi.json: accepted the changed ABI manifest (h1 -> h2)", false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        var layout = JoustProjectLayout.scan(base, JoustPaths.Flavor.current());
        assertNotNull("npm owns the mixed directory", layout.owner(dir.resolve("Counter.jsc").toRealPath()).npm());
        Path contracts = module.resolve("contracts").toRealPath();
        var t = new JoustAbiReview.Target("app", false, null, module.toRealPath(), contracts, dir.resolve("Counter.jsc").toRealPath(), "app.Counter");
        FakeUi ui = new FakeUi();
        assertTrue(ui.reports.toString(), new JoustAbiReview(t, () -> JoustProjectLayout.scan(base, JoustPaths.Flavor.current()), runner, ui,
                () -> true, ide.getRoot().toPath().toRealPath(), () -> {
        }).run());
        assertEquals(List.of("build", "--src", "contracts"), runs.get(1).subList(0, 3));
    }

    /** M2-CDX-26: --src passes any confined name as one argument, and refuses what could escape or act as an option. */
    @Test
    public void srcAcceptsConfinedNames() throws Exception {
        JoustAbi.Diff d = JoustAbi.parse(diffJson);
        assertEquals("My Contracts", JoustAbi.npmDiffArgs("org.acme.Counter", "My Contracts", Path.of("/tmp/x")).get(4));
        assertEquals("My Contracts", JoustAbi.npmAcceptArgs(d, "My Contracts").get(2));
        assertEquals(".", JoustAbi.npmAcceptArgs(d, ".").get(2));
        for (String bad : new String[]{"../x", "a/../b", "-x", "/abs", "C:\\x", "", "a//b", "a\nb", "a\u0085b", "a\u202Eb", "a\u200Bb"}) {
            try {
                JoustAbi.npmAcceptArgs(d, bad);
                fail(bad);
            } catch (JoustAbi.AbiException expected) {
                // refused
            }
        }
    }

    /** M2-DES-11: the real acceptJscAbi success output is evidence; the real refusal is not. */
    @Test
    public void theRecordedGradleOutputsClassifyCorrectly() throws Exception {
        String accepted = JoustM2Fixtures.text("acceptJscAbi-accepted.log");
        String refused = JoustM2Fixtures.text("acceptJscAbi-refused.log");
        assertTrue(JoustAbi.reportsAccepted(accepted, "org/acme/Counter.jsc", "Counter.abi.json", "src/main/jsc/org/acme/Counter.abi.json"));
        assertNull(JoustAbi.refusal(accepted, "org/acme/Counter.jsc", "org.acme.Counter"));
        assertFalse(JoustAbi.reportsAccepted(refused, "org/acme/Counter.jsc", "Counter.abi.json", "src/main/jsc/org/acme/Counter.abi.json"));
        assertNotNull(JoustAbi.refusal(refused, "org/acme/Counter.jsc", "org.acme.Counter"));
    }

    /** M2-CDX-6: unrelated lines that merely say "accepted" cannot displace JOUST's own acceptance record. */
    @Test
    public void unrelatedAcceptedLinesDoNotDisplaceTheRecord() throws Exception {
        var capture = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(64);
        capture.append("x".repeat(200) + "\n" + "request accepted\n".repeat(300) + "warning: offer accepted\n".repeat(300)
                + JoustM2Fixtures.text("acceptJscAbi-accepted.log"));
        assertTrue(JoustAbi.reportsAccepted(capture.toString(), "org/acme/Counter.jsc", "Counter.abi.json", "src/main/jsc/org/acme/Counter.abi.json"));
        var refusal = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(64);
        refusal.append("x".repeat(200) + "\n" + "request accepted\n".repeat(300) + JoustM2Fixtures.text("acceptJscAbi-refused.log"));
        assertNotNull(JoustAbi.refusal(refusal.toString(), "org/acme/Counter.jsc", "org.acme.Counter"));
    }

    /** M2-CDX-2: trust lost during the scan after the run: nothing is published. */
    @Test
    public void nothingIsPublishedAfterLivenessEnds() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean live = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicInteger scans = new java.util.concurrent.atomic.AtomicInteger();
        FakeJoust joust = new FakeJoust();
        FakeUi ui = new FakeUi();
        boolean ok = new JoustAbiReview(target(module, "app", "org.acme.Counter"), () -> {
            if (scans.incrementAndGet() == 2) live.set(false);   // the scan after the accept run
            return JoustProjectLayout.scan(base, JoustPaths.Flavor.current());
        }, joust, ui, live::get, ide.getRoot().toPath().toRealPath(), () -> {
        }).run();
        assertFalse(ok);
        assertEquals("the accept ran", 2, joust.runs.size());
        assertEquals("no notification of either outcome", List.of(), ui.reports);
    }

    /** M2-CDX-26: a directory with two dots inside its name is a name; a ".." segment is refused. */
    @Test
    public void twoDotsInANameAreNotTraversal() throws Exception {
        Path web = Files.createDirectories(base.resolve("web"));
        Path dir = Files.createDirectories(web.resolve("contracts..v2/app"));
        JSONObject o = new JSONObject(diffJson).put("contract", "app.Counter").put("file", "app/Counter.jsc").put("path", "contracts..v2/app/Counter.abi.json");
        Path m = JoustAbi.manifestPath(JoustAbi.parse(o.toString()), web, web.resolve("contracts..v2"));
        assertEquals(dir.toRealPath().resolve("Counter.abi.json"), m);
        assertEquals("contracts..v2", JoustAbi.npmAcceptArgs(JoustAbi.parse(o.toString()), "contracts..v2").get(2));
        o.put("path", "contracts..v2/../x/Counter.abi.json");
        try {
            JoustAbi.manifestPath(JoustAbi.parse(o.toString()), web, web.resolve("contracts..v2"));
            fail("a .. segment");
        } catch (JoustAbi.AbiException expected) {
            // refused
        }
    }

    /**
     * M2-CDX-6: through the flow with the production capture (4 MiB cap) and the selected contract's watch, thousands of
     * decoy lines can displace neither the recorded acceptance nor the recorded refusal.
     */
    @Test
    public void decoysCannotDisplaceTheSelectedRecords() throws Exception {
        for (String outcome : new String[]{"accepted", "refused"}) {
            Files.writeString(manifest, text1());
            FakeJoust joust = new FakeJoust() {
                @Override
                public JoustAbiReview.Result run(List<String> args, java.util.function.Predicate<String> watch) {
                    JoustAbiReview.Result r = super.run(args);
                    if (!args.get(0).endsWith(":acceptJscAbi")) return r;
                    try {
                        if (outcome.equals("refused")) Files.writeString(manifest, text2());   // a concurrent acceptance
                        var capture = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(
                                com.bloxbean.intelliada.idea.joust.tasks.JoustNpmTasks.OUTPUT_CAP, watch);
                        capture.append("y".repeat(com.bloxbean.intelliada.idea.joust.tasks.JoustNpmTasks.OUTPUT_CAP) + "\n");
                        capture.append("copying ABI manifest metadata\n".repeat(4_096));
                        capture.append("other/X.jsc:1:1: error[JSC0002]: not accepted: unrelated\n".repeat(4_352));
                        capture.append("org/acme/Counter.jsc: note: generated the Java for this contract\n".repeat(200));   // same source, no outcome
                        capture.append(JoustM2Fixtures.text(outcome.equals("accepted") ? "acceptJscAbi-accepted.log" : "acceptJscAbi-refused.log"));
                        return new JoustAbiReview.Result(outcome.equals("accepted"), capture.toString(), false);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            FakeUi ui = new FakeUi();
            boolean ok = review(target(module, "app", "org.acme.Counter"), joust, ui, () -> true).run();
            if (outcome.equals("accepted")) {
                assertTrue(outcome + ": " + ui.reports, ok);
            } else {
                assertFalse(ok);
                assertTrue(ui.reports.toString(), ui.reports.stream().anyMatch(x -> x.startsWith("REFUSED") && x.contains("not accepted: the committed manifest")));
            }
        }
    }

    /** M2-CDX-2: trust lost during the scan before the accept: no accept call and no notification of any kind. */
    @Test
    public void nothingIsPublishedWhenLivenessEndsBeforeAccept() throws Exception {
        Path other = JoustM2Fixtures.gradleProject(Files.createDirectories(base.resolve("other")));
        java.util.concurrent.atomic.AtomicBoolean live = new java.util.concurrent.atomic.AtomicBoolean(true);
        FakeJoust joust = new FakeJoust();
        FakeUi ui = new FakeUi();
        // Deterministically incomplete, whatever the process's privileges (M2-CDX-28): a build file that is not UTF-8.
        Files.write(other.resolve("build.gradle"), new byte[]{'p', 'l', (byte) 0xC3, (byte) 0x28});
        assertFalse(JoustProjectLayout.scan(base, JoustPaths.Flavor.current()).complete());
        boolean ok = new JoustAbiReview(target(module, "app", "org.acme.Counter"), () -> {
            live.set(false);
            return JoustProjectLayout.scan(base, JoustPaths.Flavor.current());
        }, joust, ui, live::get, ide.getRoot().toPath().toRealPath(), () -> {
        }).run();
        assertFalse(ok);
        assertEquals("zero accept calls", 1, joust.runs.size());
        assertEquals("zero notifications", List.of(), ui.reports);
    }

    /** M2-CDX-26: a POSIX contracts directory whose name holds a backslash is passed as itself. */
    @Test
    public void aBackslashInAPosixNameIsKept() throws Exception {
        Assume.assumeFalse("POSIX names only", JoustTestTools.windows());
        Path web = Files.createDirectories(base.resolve("web"));
        Files.writeString(web.resolve("joust.config.json"), "{\"contracts\":\"contracts\\\\v2\"}");
        Path dir = Files.createDirectories(web.resolve("contracts\\v2").resolve("app"));
        Files.copy(module.resolve("src/main/jsc/org/acme/Counter.jsc"), dir.resolve("Counter.jsc"));
        Path m = Files.writeString(dir.resolve("Counter.abi.json"), text1());
        JSONObject o = new JSONObject(diffJson).put("contract", "app.Counter").put("file", "app/Counter.jsc").put("path", "contracts\\v2/app/Counter.abi.json");
        List<List<String>> runs = new ArrayList<>();
        JoustAbiReview.Runner runner = args -> {
            runs.add(args);
            try {
                if (args.get(0).equals("abi")) {
                    Files.writeString(Path.of(args.get(args.indexOf("--out") + 1)), o.toString());
                    return new JoustAbiReview.Result(true, "", false);
                }
                Files.writeString(m, text2());
                return new JoustAbiReview.Result(true, "contracts\\v2/app/Counter.abi.json: accepted the changed ABI manifest (a -> b)", false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        Path contracts = web.resolve("contracts\\v2").toRealPath();
        var t = new JoustAbiReview.Target("web", false, null, web.toRealPath(), contracts, dir.resolve("Counter.jsc").toRealPath(), "app.Counter");
        FakeUi ui = new FakeUi();
        assertTrue(ui.reports.toString(), new JoustAbiReview(t, () -> JoustProjectLayout.scan(base, JoustPaths.Flavor.current()), runner, ui,
                () -> true, ide.getRoot().toPath().toRealPath(), () -> {
        }).run());
        assertEquals("contracts\\v2", runs.get(0).get(4));
        assertEquals("contracts\\v2", runs.get(1).get(2));
    }

    /** M2-CDX-26: paths follow this host's separators, in --src and in the records credited to the selected manifest. */
    @Test
    public void pathsFollowTheHostsSeparators() throws Exception {
        JoustAbi.Diff d = JoustAbi.parse(diffJson);
        String accepted = "contracts/v2/app/Counter.abi.json: accepted the changed ABI manifest (a -> b)";
        if (JoustTestTools.windows()) {
            assertTrue(JoustAbi.reportsAccepted(accepted, "app/Counter.jsc", "Counter.abi.json", "contracts\\v2/app/Counter.abi.json"));
        } else {
            // On POSIX "contracts\v2" is one directory, distinct from contracts/v2: a record for the other is not this one's.
            assertFalse(JoustAbi.reportsAccepted(accepted, "app/Counter.jsc", "Counter.abi.json", "contracts\\v2/app/Counter.abi.json"));
            assertEquals("a\\", JoustAbi.npmAcceptArgs(d, "a\\").get(2));
            assertEquals("a\\\\b", JoustAbi.npmAcceptArgs(d, "a\\\\b").get(2));
            assertNotNull(JoustAbi.refusal("org\\acme\\Counter.jsc:1:1: error[JSC0002]: not accepted: x", "org\\acme\\Counter.jsc"));
            assertNull(JoustAbi.refusal("org/acme/Counter.jsc:1:1: error[JSC0002]: not accepted: x", "org\\acme\\Counter.jsc"));
        }
    }

    /** M2-CDX-6: an outcome is the pinned record right after the exact prefix; a note mentioning acceptance is not one. */
    @Test
    public void onlyThePinnedRecordIsAnOutcome() throws Exception {
        String note = "org/acme/Counter.jsc: note: no task accepted the changed ABI manifest Counter.abi.json";
        assertFalse(JoustAbi.reportsAccepted(note, "org/acme/Counter.jsc", "Counter.abi.json", "src/main/jsc/org/acme/Counter.abi.json"));
        assertTrue(JoustAbi.reportsAccepted(JoustM2Fixtures.text("acceptJscAbi-accepted.log"), "org/acme/Counter.jsc", "Counter.abi.json",
                "src/main/jsc/org/acme/Counter.abi.json"));
        // 64 such notes, then the recorded refusal, through the production capture with a watch: the refusal wins.
        var capture = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(64, line ->
                JoustAbi.refusal(line, "org/acme/Counter.jsc", "org.acme.Counter") != null
                        || JoustAbi.reportsAccepted(line, "org/acme/Counter.jsc", "Counter.abi.json", "src/main/jsc/org/acme/Counter.abi.json"));
        capture.append("y".repeat(10_000) + "\n" + (note + "\n").repeat(64) + JoustM2Fixtures.text("acceptJscAbi-accepted.log").repeat(100)
                + JoustM2Fixtures.text("acceptJscAbi-refused.log"));
        assertNotNull(JoustAbi.refusal(capture.toString(), "org/acme/Counter.jsc", "org.acme.Counter"));
    }

    /** M2-CDX-26: a leading space belongs to the directory name: " contracts" and "contracts" are distinct. */
    @Test
    public void aLeadingSpaceIsPartOfTheName() {
        String plain = "contracts/app/Counter.abi.json: accepted the changed ABI manifest (a -> b)";
        String spaced = " contracts/app/Counter.abi.json: accepted the changed ABI manifest (a -> b)";
        assertTrue(JoustAbi.reportsAccepted(plain, "app/Counter.jsc", "Counter.abi.json", "contracts/app/Counter.abi.json"));
        assertFalse(JoustAbi.reportsAccepted(plain, "app/Counter.jsc", "Counter.abi.json", " contracts/app/Counter.abi.json"));
        assertTrue(JoustAbi.reportsAccepted(spaced, "app/Counter.jsc", "Counter.abi.json", " contracts/app/Counter.abi.json"));
        assertFalse(JoustAbi.reportsAccepted(spaced, "app/Counter.jsc", "Counter.abi.json", "contracts/app/Counter.abi.json"));
    }

    /** M2-CDX-6: the pinned "nothing accepted" refusal listing 120 contracts, past the cap, is still recognised. */
    @Test
    public void aLongProducerRefusalIsStillSeen() {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < 120; i++) list.append(i == 0 ? "" : ", ").append("org.acme.generated.Contract").append(i);
        String refusal = "error[JSC0002]: --accept-abi=org.acme.Counter@…: no .jsc 2 contract org.acme.Counter in this build (contracts: "
                + list + "); nothing accepted";
        assertTrue(refusal.length() > 3_000);
        var capture = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(com.bloxbean.intelliada.idea.joust.tasks.JoustNpmTasks.OUTPUT_CAP,
                line -> JoustAbi.refusal(line, "org/acme/Counter.jsc", "org.acme.Counter") != null);
        capture.append("y".repeat(com.bloxbean.intelliada.idea.joust.tasks.JoustNpmTasks.OUTPUT_CAP) + "\n");
        for (int i = 0; i < refusal.length(); i += 100) capture.append(refusal.substring(i, Math.min(refusal.length(), i + 100)));
        capture.append("\n");
        assertNotNull(JoustAbi.refusal(capture.toString(), "org/acme/Counter.jsc", "org.acme.Counter"));
        assertTrue("memory stays bounded", capture.toString().length() < com.bloxbean.intelliada.idea.joust.tasks.JoustNpmTasks.OUTPUT_CAP + 10_000);
    }

    /** M2-CDX-6: a final refusal with no newline, after 64 acceptances, still wins; streams do not absorb each other's lines. */
    @Test
    public void aPendingRefusalAndInterleavedStreams() throws Exception {
        java.util.function.Predicate<String> watch = line -> JoustAbi.refusal(line, "org/acme/Counter.jsc", "org.acme.Counter") != null
                || JoustAbi.reportsAccepted(line, "org/acme/Counter.jsc", "Counter.abi.json", "src/main/jsc/org/acme/Counter.abi.json");
        String refused = JoustM2Fixtures.text("acceptJscAbi-refused.log").lines().filter(l -> l.contains("not accepted")).findFirst().orElseThrow();
        var atEof = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(64, watch);
        atEof.append("y".repeat(200) + "\n" + JoustM2Fixtures.text("acceptJscAbi-accepted.log").repeat(64) + refused);   // no final newline
        assertNotNull(JoustAbi.refusal(atEof.toString(), "org/acme/Counter.jsc", "org.acme.Counter"));
        var streams = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(64, watch);
        streams.append("y".repeat(200) + "\n", "stdout");
        streams.append("Building contracts... 42%", "stdout");   // an unfinished progress line
        streams.append(refused + "\n", "stderr");
        streams.append(" done\n", "stdout");
        assertNotNull(JoustAbi.refusal(streams.toString(), "org/acme/Counter.jsc", "org.acme.Counter"));
    }

    /** M2-CDX-6: records come from complete per-stream lines; pieces split across streams never make one. */
    @Test
    public void recordsAreNotAssembledAcrossStreams() throws Exception {
        java.util.function.Predicate<String> watch = line -> JoustAbi.refusal(line, "org/acme/Counter.jsc", "org.acme.Counter") != null
                || JoustAbi.reportsAccepted(line, "org/acme/Counter.jsc", "Counter.abi.json", "src/main/jsc/org/acme/Counter.abi.json");
        String accepted = JoustM2Fixtures.text("acceptJscAbi-accepted.log").lines().filter(l -> l.contains("note: accepted")).findFirst().orElseThrow();
        assertTrue(accepted.startsWith("org/acme/"));
        var capture = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(1 << 20, watch);
        capture.append("org/", "stdout");
        capture.append(accepted.substring("org/".length()) + "\n", "stderr");
        // The merged display does read as an acceptance (M2-CDX-34): only the per-stream records keep it from counting.
        assertTrue(JoustAbi.reportsAccepted(capture.toString(), "org/acme/Counter.jsc", "Counter.abi.json", "src/main/jsc/org/acme/Counter.abi.json"));
        String records = String.join("\n", capture.records());
        assertFalse(JoustAbi.reportsAccepted(records, "org/acme/Counter.jsc", "Counter.abi.json", "src/main/jsc/org/acme/Counter.abi.json"));
    }

    /** M2-CDX-24: an ordinary npm Build past the cap, with thousands of manifest decoys, still yields its JSC0002 failure. */
    @Test
    public void aVerboseNpmBuildKeepsItsFailure() throws Exception {
        var capture = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(com.bloxbean.intelliada.idea.joust.tasks.JoustNpmTasks.OUTPUT_CAP);
        capture.append("y".repeat(com.bloxbean.intelliada.idea.joust.tasks.JoustNpmTasks.OUTPUT_CAP) + "\n");
        capture.append("copying ABI manifest metadata\n".repeat(4_096));
        capture.append(JoustM2Fixtures.text("npm-build-JSC0002.log"));
        assertEquals(1, JoustAbi.failures(capture.toString()).size());
    }

    /** M2-CDX-15, M2-SEC-8: IntelliJ's styled output types share their base stream; distinct keys cannot grow the buffers. */
    @Test
    public void streamsAreBounded() {
        assertEquals("stdout", com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture.baseStream(com.intellij.execution.process.ProcessOutputType.STDOUT));
        assertEquals("stderr", com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture.baseStream(com.intellij.execution.process.ProcessOutputType.STDERR));
        var capture = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(64);
        for (int i = 0; i < 20_000; i++) {
            var styled = new com.intellij.execution.process.ProcessOutputType("\u001b[38;2;" + (i % 256) + ";" + (i / 256) + ";0m",
                    com.intellij.execution.process.ProcessOutputType.STDOUT);
            capture.append("x".repeat(100), com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture.baseStream(styled));
        }
        assertEquals("every style is stdout: one buffer (M2-CDX-35)", 1, capture.streamCount());
        var raw = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(64);
        for (int i = 0; i < 20_000; i++) raw.append("x", "key-" + i);
        assertTrue("even raw keys are capped", raw.streamCount() <= 5);
    }

    /** M2-CDX-24: an npm Build's failure on stderr, interleaved with an unfinished stdout line below the cap, is found. */
    @Test
    public void anInterleavedBuildFailureIsFound() throws Exception {
        var capture = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(com.bloxbean.intelliada.idea.joust.tasks.JoustNpmTasks.OUTPUT_CAP);
        capture.append("Build progress: 50%", "stdout");
        capture.append(JoustM2Fixtures.text("npm-build-JSC0002.log"), "stderr");
        assertEquals(1, JoustAbi.failures(capture.failures()).size());
    }
}
