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
                Files.writeString(m, text2());
                return new JoustAbiReview.Result(true, "accepted", false);
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
                return args.get(0).endsWith(":acceptJscAbi") ? new JoustAbiReview.Result(false, "error: compileJava failed", false) : r;
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
}
