package com.bloxbean.intelliada.idea.joust.abi;

import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * ADR-004 D8's flow for one contract of one module, bound to the selected {@code .jsc}: review the diff, accept the
 * bound change, verify the result. The task runner, the UI, liveness (trust, generation, project open) and the
 * project scan are injected, so the flow is the same in the IDE and in tests.
 */
public final class JoustAbiReview {

    /** Runs the project's own build (Gradle, or the npm CLI) with an argument list; blocking, off the EDT. */
    public interface Runner {
        Result run(List<String> args) throws InterruptedException;

        /**
         * As {@link #run(List)}; lines for which {@code watch} holds are kept as they arrive, whatever else the output
         * holds, so the selected contract's records cannot be displaced (M2-CDX-6).
         */
        default Result run(List<String> args, java.util.function.Predicate<String> watch) throws InterruptedException {
            return run(args);
        }
    }

    /**
     * A run's outcome. {@code records} are the selected contract's outcome records from complete per-stream lines, when
     * the runner watched for them; null when it did not (the display {@code output} is then all there is).
     */
    public record Result(boolean ok, String output, boolean cancelled, java.util.List<String> records) {
        public Result(boolean ok, String output, boolean cancelled) {
            this(ok, output, cancelled, null);
        }
    }

    /** What the user sees. Each method may block on the user (it runs the dialog on the EDT itself). */
    public interface Ui {
        /** Shows both manifests and their SHA-256; {@code refusal} is null when Accept may be offered. True: proceed. */
        boolean review(String module, JoustAbi.Diff diff, String refusal);

        /** The modal naming the one contract, its module and its change. True: accept. */
        boolean confirm(String module, JoustAbi.Diff diff);

        /** An outcome, as plain text. */
        void report(String title, String text, boolean error);

        /** A refusal by the build or by the checks after it, offering to review again. */
        void refused(String title, String text, Runnable reviewAgain);
    }

    /**
     * The contract and where it lives: the module (or npm root) directory, its verified contracts directory, the
     * selected {@code .jsc} (real path) and the contract's qualified name derived from it.
     */
    public record Target(String module, boolean gradle, String gradlePath, Path dir, Path contractsDir, Path jsc, String contract) {

        /** The qualified name of {@code jsc} under {@code contractsDir}: {@code org/acme/Counter.jsc} is {@code org.acme.Counter}. */
        public static String contractOf(Path contractsDirReal, Path jscReal) throws JoustAbi.AbiException {
            if (!jscReal.startsWith(contractsDirReal) || !jscReal.getFileName().toString().endsWith(".jsc")) {
                throw new JoustAbi.AbiException("the .jsc is not in the contracts directory");
            }
            String rel = contractsDirReal.relativize(jscReal).toString().replace('\\', '/');
            return JoustAbi.requireContract(rel.substring(0, rel.length() - ".jsc".length()).replace('/', '.'));
        }
    }

    private final Target target;
    private final Supplier<JoustProjectLayout> scan;
    private final Runner runner;
    private final Ui ui;
    private final BooleanSupplier live;
    private final Path privateTmp;
    private final Runnable reviewAgain;
    private final Supplier<String> linkedNow;
    private String linkedAtStart;

    /**
     * @param scan       a fresh scan of the project, taken right before and right after accepting
     * @param live       false once trust is lost, the project closes, or the action's generation ends
     * @param privateTmp IntelliAda's own verified temporary directory (§12 E12), outside every project
     */
    public JoustAbiReview(Target target, Supplier<JoustProjectLayout> scan, Runner runner, Ui ui, BooleanSupplier live, Path privateTmp,
                          Runnable reviewAgain) {
        this(target, scan, runner, ui, live, privateTmp, reviewAgain, () -> target.gradle() ? "gradle:" + target.gradlePath() : "npm");
    }

    /**
     * @param linkedNow the module's linked build and Gradle path as of now ({@code root|path}), or null when not linked;
     *                  compared before accepting with its value when the review started (M2-CDX-23)
     */
    public JoustAbiReview(Target target, Supplier<JoustProjectLayout> scan, Runner runner, Ui ui, BooleanSupplier live, Path privateTmp,
                          Runnable reviewAgain, Supplier<String> linkedNow) {
        this(target, scan, runner, ui, live, privateTmp, reviewAgain, linkedNow, null);
    }

    /**
     * @param linkedExpected the {@code root|path} the runner and the arguments were built from; the final check compares
     *                       against it, so one linked pair drives the whole flow (M2-CDX-23). Null: sampled at start.
     */
    public JoustAbiReview(Target target, Supplier<JoustProjectLayout> scan, Runner runner, Ui ui, BooleanSupplier live, Path privateTmp,
                          Runnable reviewAgain, Supplier<String> linkedNow, String linkedExpected) {
        this.linkedNow = linkedNow;
        this.linkedAtStart = linkedExpected;
        this.target = target;
        this.scan = scan;
        this.runner = runner;
        this.ui = ui;
        this.live = live;
        this.privateTmp = privateTmp;
        this.reviewAgain = reviewAgain;
    }

    /** Runs the whole flow. Returns true when the change was accepted and verified. */
    public boolean run() throws InterruptedException {
        Path tmp = null;
        try {
            if (!live.getAsBoolean()) return false;
            if (linkedAtStart == null) linkedAtStart = linkedNow.get();
            String contract = JoustAbi.requireContract(target.contract());
            tmp = Files.createTempDirectory(privateTmp, "abi-");
            Path out = tmp.resolve("diff.json");   // written by the task into a directory only IntelliAda uses (M2-SEC-3)
            List<String> diffArgs = target.gradle() ? JoustAbi.gradleDiffArgs(target.gradlePath(), contract, out)
                    : JoustAbi.npmDiffArgs(contract, npmSrc(), out);
            Result r = runner.run(diffArgs);
            if (r.cancelled() || !live.getAsBoolean()) return false;
            if (!r.ok()) {
                report("ABI review failed", r.output(), true);
                return false;
            }
            JoustAbi.Diff d = JoustAbi.parse(out);
            // Bound to the selected source (M2-CDX-5): the exact contract, its .jsc, and the manifest beside it.
            if (!d.contract().equals(contract)) throw new JoustAbi.AbiException("the diff is for " + d.contract() + ", not " + contract);
            if (d.file() == null) throw new JoustAbi.AbiException("the diff names no source file");
            Path file;
            try {
                file = target.contractsDir().resolve(d.file()).toRealPath();
            } catch (IOException | RuntimeException e) {
                throw new JoustAbi.AbiException("the diff's source " + d.file() + " is not in the contracts directory");
            }
            if (!file.toString().equals(target.jsc().toString())) throw new JoustAbi.AbiException("the diff is for " + d.file() + ", not the selected source");
            Path manifest = JoustAbi.manifestPath(d, target.dir(), target.contractsDir());
            String simple = contract.substring(contract.lastIndexOf('.') + 1);
            if (!manifest.toString().equals(target.jsc().resolveSibling(simple + ".abi.json").toString())) {
                throw new JoustAbi.AbiException("the manifest " + d.path() + " is not beside " + target.jsc().getFileName());
            }
            String refusal = JoustAbi.verify(d, manifest);
            if (!live.getAsBoolean()) return false;
            if (!ui.review(target.module(), d, refusal) || refusal != null) return false;
            if (!live.getAsBoolean()) return false;
            if (!ui.confirm(target.module(), d)) return false;
            // After the dialogs: trust, the project and the generation must still hold (M2-DES-2, M2-CDX-2).
            if (!live.getAsBoolean()) return false;
            JoustProjectLayout fresh = scan.get();
            JoustAbi.Snapshot before = JoustAbi.manifests(fresh);
            String moved = rebind(fresh, manifest);
            if (moved != null) {
                // The project changed under the review (a contracts directory moved, the source or its owner changed):
                // nothing is run (M2-CDX-23).
                refused("ABI change not accepted", moved + "\nNothing was run. Review the change again.");
                return false;
            }
            if (before.incomplete() != null) {
                // Stop before anything is written: the result could not be verified (M2-CDX-7).
                report("ABI change not accepted", "The project's ABI manifests could not all be checked: " + before.incomplete()
                        + ".\nNothing was run.", true);
                return false;
            }
            String stale = JoustAbi.verify(d, manifest);
            if (stale != null) {
                refused("ABI change not accepted", stale + "\nReview the change again.");
                return false;
            }
            List<String> acceptArgs = target.gradle() ? JoustAbi.gradleAcceptArgs(target.gradlePath(), d) : JoustAbi.npmAcceptArgs(d, npmSrc());
            String jscRelEarly = slashed(target.contractsDir().relativize(target.jsc()));
            Result a = runner.run(acceptArgs, watch(jscRelEarly, slashed(target.dir().relativize(manifest)), contract));
            if (a.cancelled()) return false;
            JoustAbi.Snapshot after = JoustAbi.manifests(scan.get());
            // Nothing is published outside the action's trust generation, even after the run (M2-CDX-2).
            if (!live.getAsBoolean()) return false;
            // An explicit refusal of this contract by the bound task wins over what the files look like afterwards: another
            // acceptance may have written the same new hash meanwhile (M2-CDX-6).
            String jscRel = jscRelEarly;
            // A run is an acceptance only when it says so, whether it succeeded or failed (M2-CDX-6): an explicit refusal
            // wins over the files, and a run that does not report writing this manifest did not accept it (npm skips a
            // target another acceptance already wrote, and succeeds).
            // From the runner's per-stream records when it kept them, never the merged display text (M2-CDX-6).
            String evidence = a.records() != null ? String.join("\n", a.records()) : a.output();
            String refusedByTask = JoustAbi.refusal(evidence, jscRel, contract);
            if (refusedByTask == null && !JoustAbi.reportsAccepted(evidence, jscRel, manifest.getFileName().toString(),
                    slashed(target.dir().relativize(manifest)))) {
                refusedByTask = "the accept task " + (a.ok() ? "succeeded" : "failed") + " without reporting that it accepted "
                        + manifest.getFileName();
            }
            if (refusedByTask != null) {
                refused("ABI change not accepted", refusedByTask + "\nReview the change again.");
                return false;
            }
            String wrong = JoustAbi.verifyAccepted(before, after, manifest, d);
            if (wrong != null) {
                // The task refuses when the baseline or the source changed since the review (JSC0002 "not accepted").
                refused("ABI change not accepted", (a.ok() ? "" : tail(a.output()) + "\n\n") + wrong + "\nReview the change again.");
                return false;
            }
            if (!live.getAsBoolean()) return false;
            report("ABI change accepted", "Accepted " + d.contract() + " in " + target.module() + " (" + d.committedSha() + " -> "
                    + d.nowSha() + ").\nReview and commit " + manifest.getFileName() + " beside the .jsc file."
                    + (a.ok() ? "" : "\nThe build then failed after accepting: see its output."), !a.ok());
            return true;
        } catch (JoustAbi.AbiException e) {
            report("ABI review refused", e.getMessage(), true);
            return false;
        } catch (IOException e) {
            report("ABI review failed", e.getMessage(), true);
            return false;
        } finally {
            if (tmp != null) deleteTree(tmp);
        }
    }

    /** Outcomes are published only while the action's guard is live, on every path (M2-CDX-2). */
    private void report(String title, String text, boolean error) {
        if (live.getAsBoolean()) ui.report(title, text, error);
    }

    private void refused(String title, String text) {
        if (live.getAsBoolean()) ui.refused(title, text, reviewAgain);
    }

    /**
     * A relative path as JOUST prints and reads it: its own components joined with {@code /}, each kept exactly (a
     * backslash inside a POSIX name stays part of that name), M2-CDX-26.
     */
    static String slashed(Path rel) {
        List<String> parts = new java.util.ArrayList<>();
        for (Path p : rel) parts.add(p.toString());
        String s = String.join("/", parts);
        return s.isEmpty() ? "." : s;
    }

    /** The npm contracts directory relative to the npm root, for {@code --src}. */
    private String npmSrc() {
        return slashed(target.dir().relativize(target.contractsDir()));
    }

    /**
     * The selected contract's <b>outcome records</b> in task output, classified as each line arrives (M2-CDX-6): its
     * refusal (by source path or bound contract) or its acceptance of this manifest, in this host's path spelling. Other
     * lines about the same source (notes, diagnostics) are not kept here, so they cannot fill the slots.
     */
    private java.util.function.Predicate<String> watch(String jscRel, String manifestRel, String contract) {
        String manifestName = target.jsc().resolveSibling(contract.substring(contract.lastIndexOf('.') + 1) + ".abi.json").getFileName().toString();
        return line -> JoustAbi.refusal(line, jscRel, contract) != null || JoustAbi.reportsAccepted(line, jscRel, manifestName, manifestRel);
    }

    /**
     * Why the fresh scan no longer binds the selected source to the reviewed manifest, or null (M2-CDX-23): the source
     * must still be owned by the same module or npm root, under the same verified contracts directory, as the same real
     * file, with the same sibling manifest.
     */
    private String rebind(JoustProjectLayout fresh, Path manifest) {
        try {
            Path jsc = target.jsc().toRealPath();
            if (!jsc.toString().equals(target.jsc().toString())) return "the selected source moved";
            JoustProjectLayout.Owner owner = fresh.owner(jsc);
            Path ownerDir = owner == null ? null : owner.gradle() != null ? owner.gradle().dir() : owner.npm().dir();
            if (ownerDir == null || owner.gradle() != null != target.gradle() || !ownerDir.toString().equals(target.dir().toString())) {
                return "the source's module changed";
            }
            // By owner kind as well as directory: a directory can be both a Gradle module and an npm root (M2-CDX-25).
            JoustProjectLayout.SourceRoot root = fresh.sourceRoots().stream()
                    .filter(r -> (r.gradle() != null) == target.gradle() && r.ownerDir().toString().equals(ownerDir.toString()))
                    .findFirst().orElse(null);
            Path contracts = root == null ? null : root.verifiedContracts(fresh.base());
            if (contracts == null || !contracts.toString().equals(target.contractsDir().toString())) return "the contracts directory changed";
            String simple = target.contract().substring(target.contract().lastIndexOf('.') + 1);
            if (!jsc.resolveSibling(simple + ".abi.json").toString().equals(manifest.toString())) return "the manifest's place changed";
            // Gradle: the module must still be the same linked build and qualified project (a reimport may remap them).
            if (target.gradle()) {
                String now = linkedNow.get();
                if (now == null || !now.equals(linkedAtStart)) return "the module's Gradle project changed (" + now + ")";
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return "the selected source is gone";
        }
    }

    private static String tail(String s) {
        return s == null ? "" : s.length() > 4000 ? s.substring(s.length() - 4000) : s;
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // a private temporary directory
        }
    }
}
