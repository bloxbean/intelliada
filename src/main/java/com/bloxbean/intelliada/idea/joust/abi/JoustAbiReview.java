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
    }

    /** A run's outcome. */
    public record Result(boolean ok, String output, boolean cancelled) {
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

    /**
     * @param scan       a fresh scan of the project, taken right before and right after accepting
     * @param live       false once trust is lost, the project closes, or the action's generation ends
     * @param privateTmp IntelliAda's own verified temporary directory (§12 E12), outside every project
     */
    public JoustAbiReview(Target target, Supplier<JoustProjectLayout> scan, Runner runner, Ui ui, BooleanSupplier live, Path privateTmp,
                          Runnable reviewAgain) {
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
            String contract = JoustAbi.requireContract(target.contract());
            tmp = Files.createTempDirectory(privateTmp, "abi-");
            Path out = tmp.resolve("diff.json");   // written by the task into a directory only IntelliAda uses (M2-SEC-3)
            List<String> diffArgs = target.gradle() ? JoustAbi.gradleDiffArgs(target.gradlePath(), contract, out)
                    : JoustAbi.npmDiffArgs(contract, out);
            Result r = runner.run(diffArgs);
            if (r.cancelled() || !live.getAsBoolean()) return false;
            if (!r.ok()) {
                ui.report("ABI review failed", r.output(), true);
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
            if (!file.equals(target.jsc())) throw new JoustAbi.AbiException("the diff is for " + d.file() + ", not the selected source");
            Path manifest = JoustAbi.manifestPath(d, target.dir(), target.contractsDir());
            String simple = contract.substring(contract.lastIndexOf('.') + 1);
            if (!manifest.equals(target.jsc().resolveSibling(simple + ".abi.json"))) {
                throw new JoustAbi.AbiException("the manifest " + d.path() + " is not beside " + target.jsc().getFileName());
            }
            String refusal = JoustAbi.verify(d, manifest);
            if (!live.getAsBoolean()) return false;
            if (!ui.review(target.module(), d, refusal) || refusal != null) return false;
            if (!live.getAsBoolean()) return false;
            if (!ui.confirm(target.module(), d)) return false;
            // After the dialogs: trust, the project and the generation must still hold (M2-DES-2, M2-CDX-2).
            if (!live.getAsBoolean()) return false;
            JoustAbi.Snapshot before = JoustAbi.manifests(scan.get());
            if (before.incomplete() != null) {
                // Stop before anything is written: the result could not be verified (M2-CDX-7).
                ui.report("ABI change not accepted", "The project's ABI manifests could not all be checked: " + before.incomplete()
                        + ".\nNothing was run.", true);
                return false;
            }
            String stale = JoustAbi.verify(d, manifest);
            if (stale != null) {
                ui.refused("ABI change not accepted", stale + "\nReview the change again.", reviewAgain);
                return false;
            }
            List<String> acceptArgs = target.gradle() ? JoustAbi.gradleAcceptArgs(target.gradlePath(), d) : JoustAbi.npmAcceptArgs(d);
            Result a = runner.run(acceptArgs);
            if (a.cancelled()) return false;
            JoustAbi.Snapshot after = JoustAbi.manifests(scan.get());
            // An explicit refusal of this contract by the bound task wins over what the files look like afterwards: another
            // acceptance may have written the same new hash meanwhile (M2-CDX-6).
            String refusedByTask = !a.ok() ? JoustAbi.refusal(a.output(), contract) : null;
            if (refusedByTask != null) {
                ui.refused("ABI change not accepted", refusedByTask + "\nReview the change again.", reviewAgain);
                return false;
            }
            String wrong = JoustAbi.verifyAccepted(before, after, manifest, d);
            if (wrong != null) {
                // The task refuses when the baseline or the source changed since the review (JSC0002 "not accepted").
                ui.refused("ABI change not accepted", (a.ok() ? "" : tail(a.output()) + "\n\n") + wrong + "\nReview the change again.", reviewAgain);
                return false;
            }
            ui.report("ABI change accepted", "Accepted " + d.contract() + " in " + target.module() + " (" + d.committedSha() + " -> "
                    + d.nowSha() + ").\nReview and commit " + manifest.getFileName() + " beside the .jsc file."
                    + (a.ok() ? "" : "\nThe build then failed after accepting: see its output."), !a.ok());
            return true;
        } catch (JoustAbi.AbiException e) {
            ui.report("ABI review refused", e.getMessage(), true);
            return false;
        } catch (IOException e) {
            ui.report("ABI review failed", e.getMessage(), true);
            return false;
        } finally {
            if (tmp != null) deleteTree(tmp);
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
