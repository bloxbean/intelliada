package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbi;
import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;
import org.json.JSONObject;
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

    /** A runner that acts like JOUST: jscAbiDiff writes the recorded JSON; acceptJscAbi writes the new manifest if bound right. */
    private final class FakeJoust implements JoustAbiReview.Runner {
        final List<List<String>> runs = new ArrayList<>();
        boolean baselineChangedBeforeAccept;

        @Override
        public JoustAbiReview.Result run(List<String> args) throws InterruptedException {
            runs.add(args);
            try {
                if (args.get(0).endsWith(":jscAbiDiff")) {
                    Files.writeString(Path.of(args.get(args.indexOf("--out") + 1)), diffJson);
                    return new JoustAbiReview.Result(true, "", false);
                }
                if (baselineChangedBeforeAccept) {
                    Files.writeString(manifest, "{\"changed\":true}");
                    return new JoustAbiReview.Result(false, "error[JSC0002]: not accepted: the committed manifest changed", false);
                }
                String h1 = args.get(args.indexOf("--committed-sha256") + 1);
                String h2 = args.get(args.indexOf("--sha256") + 1);
                if (h1.equals(diff.getJSONObject("committed").getString("sha256")) && h2.equals(diff.getJSONObject("now").getString("sha256"))) {
                    Files.writeString(manifest, diff.getJSONObject("now").getString("text"));
                    return new JoustAbiReview.Result(true, "accepted", false);
                }
                return new JoustAbiReview.Result(false, "refused", false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class FakeUi implements JoustAbiReview.Ui {
        final List<String> reports = new ArrayList<>();
        String refusalSeen = "unset";
        boolean accept = true;

        @Override
        public boolean review(String module, JoustAbi.Diff diff, String refusal) {
            refusalSeen = refusal;
            return accept;
        }

        @Override
        public boolean confirm(String module, JoustAbi.Diff diff) {
            return accept;
        }

        @Override
        public void report(String title, String text, boolean error) {
            reports.add((error ? "ERROR " : "") + title + ": " + text);
        }
    }

    @Test
    public void theFlowAcceptsOnlyThatManifest() throws Exception {
        Path other = JoustM2Fixtures.gradleProject(Files.createDirectories(base.resolve("other")));
        Path otherManifest = other.resolve("src/main/jsc/org/acme/Counter.abi.json");
        String otherBefore = Files.readString(otherManifest);
        var layout = JoustProjectLayout.scan(base, JoustPaths.Flavor.current());
        FakeJoust joust = new FakeJoust();
        FakeUi ui = new FakeUi();
        var target = new JoustAbiReview.Target("app", true, ":app", module, module.resolve("src/main/jsc"));
        assertTrue(ui.reports.toString(), new JoustAbiReview(target, layout, joust, ui).run("Counter"));
        assertNull(ui.refusalSeen);
        assertEquals(":app:jscAbiDiff", joust.runs.get(0).get(0));
        assertEquals(List.of(":app:acceptJscAbi", "--contract", "org.acme.Counter", "--committed-sha256",
                diff.getJSONObject("committed").getString("sha256"), "--sha256", diff.getJSONObject("now").getString("sha256")), joust.runs.get(1));
        assertEquals(diff.getJSONObject("now").getString("text"), Files.readString(manifest));
        assertEquals("the identical contract in another module is untouched", otherBefore, Files.readString(otherManifest));
    }

    @Test
    public void aBaselineChangedBetweenReviewAndAcceptIsReported() throws Exception {
        var layout = JoustProjectLayout.scan(base, JoustPaths.Flavor.current());
        FakeJoust joust = new FakeJoust();
        joust.baselineChangedBeforeAccept = true;
        FakeUi ui = new FakeUi();
        var target = new JoustAbiReview.Target("app", true, ":app", module, module.resolve("src/main/jsc"));
        assertFalse(new JoustAbiReview(target, layout, joust, ui).run("Counter"));
        assertTrue(ui.reports.toString(), ui.reports.stream().anyMatch(r -> r.startsWith("ERROR ABI change not accepted")));
    }

    @Test
    public void aTamperedDiffNeverReachesAccept() throws Exception {
        diffJson = diffJson.replace(diff.getJSONObject("now").getString("sha256"), "0".repeat(64));
        var layout = JoustProjectLayout.scan(base, JoustPaths.Flavor.current());
        FakeJoust joust = new FakeJoust();
        FakeUi ui = new FakeUi();
        var target = new JoustAbiReview.Target("app", true, ":app", module, module.resolve("src/main/jsc"));
        assertFalse(new JoustAbiReview(target, layout, joust, ui).run("Counter"));
        assertNotNull("the dialog was told Accept is disabled", ui.refusalSeen);
        assertEquals("only the diff ran", 1, joust.runs.size());
    }

    @Test
    public void aDiffForAnotherContractIsRefused() throws Exception {
        var layout = JoustProjectLayout.scan(base, JoustPaths.Flavor.current());
        FakeJoust joust = new FakeJoust();
        FakeUi ui = new FakeUi();
        var target = new JoustAbiReview.Target("app", true, ":app", module, module.resolve("src/main/jsc"));
        assertFalse(new JoustAbiReview(target, layout, joust, ui).run("Vault"));
        assertEquals(1, joust.runs.size());
    }

    /** D8 for npm: joust abi diff --out, then joust build --accept-abi=C@h1..h2, with the same checks. */
    @Test
    public void theNpmFlowUsesTheBoundBuildFlag() throws Exception {
        Path npm = Files.createDirectories(base.resolve("web"));
        Files.writeString(npm.resolve("joust.config.json"), "{\"contracts\":\"contracts\"}");
        Path dir = Files.createDirectories(npm.resolve("contracts/org/acme"));
        Path m = dir.resolve("Counter.abi.json");
        Files.writeString(m, diff.getJSONObject("committed").getString("text"));
        JSONObject o = new JSONObject(diffJson);
        o.put("path", "contracts/org/acme/Counter.abi.json");
        String npmDiff = o.toString();
        var layout = JoustProjectLayout.scan(base, JoustPaths.Flavor.current());
        List<List<String>> runs = new ArrayList<>();
        JoustAbiReview.Runner runner = args -> {
            runs.add(args);
            try {
                if (args.get(0).equals("abi")) {
                    Files.writeString(Path.of(args.get(args.indexOf("--out") + 1)), npmDiff);
                } else if (args.get(1).equals("--accept-abi=org.acme.Counter@" + diff.getJSONObject("committed").getString("sha256") + ".."
                        + diff.getJSONObject("now").getString("sha256"))) {
                    Files.writeString(m, diff.getJSONObject("now").getString("text"));
                }
                return new JoustAbiReview.Result(true, "", false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        FakeUi ui = new FakeUi();
        var target = new JoustAbiReview.Target("web", false, null, npm, npm.resolve("contracts"));
        assertTrue(ui.reports.toString(), new JoustAbiReview(target, layout, runner, ui).run("org.acme.Counter"));
        assertEquals(List.of("abi", "diff", "org.acme.Counter", "--out"), runs.get(0).subList(0, 4));
        assertEquals("build", runs.get(1).get(0));
        assertEquals(2, runs.get(1).size());
        assertEquals(diff.getJSONObject("now").getString("text"), Files.readString(m));
        assertEquals("the Gradle module's manifest is untouched", diff.getJSONObject("committed").getString("text"), Files.readString(manifest));
    }
}
