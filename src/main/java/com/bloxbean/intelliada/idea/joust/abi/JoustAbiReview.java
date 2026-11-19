package com.bloxbean.intelliada.idea.joust.abi;

import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * ADR-004 D8's flow for one contract of one module: review the diff, accept the bound change, verify the result.
 * The task runner and the UI are injected, so the flow is the same in the IDE and in tests.
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
    }

    /** The module the contract belongs to, and how to address it. */
    public record Target(String module, boolean gradle, String gradlePath, Path dir, Path contractsDir) {
    }

    private final Target target;
    private final JoustProjectLayout layout;
    private final Runner runner;
    private final Ui ui;

    public JoustAbiReview(Target target, JoustProjectLayout layout, Runner runner, Ui ui) {
        this.target = target;
        this.layout = layout;
        this.runner = runner;
        this.ui = ui;
    }

    /** Runs the whole flow for {@code contract}. Returns true when a change was accepted and verified. */
    public boolean run(String contract) throws InterruptedException {
        Path out = null;
        try {
            JoustAbi.requireContract(contract);
            out = Files.createTempFile("intelliada-abi-diff", ".json");
            Files.delete(out);   // the task writes it; a stale file must not be read
            List<String> diffArgs = target.gradle() ? JoustAbi.gradleDiffArgs(target.gradlePath(), contract, out)
                    : JoustAbi.npmDiffArgs(contract, out);
            Result r = runner.run(diffArgs);
            if (r.cancelled()) return false;
            if (!r.ok()) {
                ui.report("ABI review failed", r.output(), true);
                return false;
            }
            JoustAbi.Diff d = JoustAbi.parse(out);
            String simple = contract.substring(contract.lastIndexOf('.') + 1);
            if (!d.contract().equals(contract) && !d.contract().endsWith("." + simple)) {
                throw new JoustAbi.AbiException("the diff is for " + d.contract() + ", not " + contract);
            }
            Path manifest = JoustAbi.manifestPath(d, target.dir(), target.contractsDir());
            String refusal = JoustAbi.verify(d, manifest);
            if (!ui.review(target.module(), d, refusal) || refusal != null) return false;
            if (!ui.confirm(target.module(), d)) return false;
            Map<Path, String> before = JoustAbi.manifests(layout);
            List<String> acceptArgs = target.gradle() ? JoustAbi.gradleAcceptArgs(target.gradlePath(), d) : JoustAbi.npmAcceptArgs(d);
            Result a = runner.run(acceptArgs);
            if (a.cancelled()) return false;
            Map<Path, String> after = JoustAbi.manifests(layout);
            String wrong = JoustAbi.verifyAccepted(before, after, manifest, d.nowSha());
            if (wrong != null) {
                // The task refuses when the baseline or the source changed since the review (JSC0002 "not accepted").
                ui.report("ABI change not accepted", (a.ok() ? "" : a.output() + "\n\n") + wrong + "\nReview the change again.", true);
                return false;
            }
            ui.report("ABI change accepted", "Accepted " + d.contract() + " in " + target.module() + " (" + d.committedSha() + " -> "
                    + d.nowSha() + ").\nReview and commit " + manifest.getFileName() + " beside the .jsc file.", false);
            return true;
        } catch (JoustAbi.AbiException e) {
            ui.report("ABI review refused", e.getMessage(), true);
            return false;
        } catch (IOException e) {
            ui.report("ABI review failed", e.getMessage(), true);
            return false;
        } finally {
            if (out != null) {
                try {
                    Files.deleteIfExists(out);
                } catch (IOException ignored) {
                    // a temp file
                }
            }
        }
    }
}
