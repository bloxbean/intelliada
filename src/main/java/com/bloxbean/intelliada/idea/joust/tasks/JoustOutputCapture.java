package com.bloxbean.intelliada.idea.joust.tasks;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A task's output, bounded as it arrives (M2-CDX-6, M2-CDX-15):
 * <ul>
 *   <li>the first {@code cap} characters, as printed;</li>
 *   <li>after that, JOUST diagnostic lines ({@code error[JSC…]}, up to {@link #MAX_EXTRA_LINES}) and JOUST's own
 *   acceptance records ({@code … ABI manifest …}, {@code error[JSC0002]}, up to {@link #MAX_OUTCOME_LINES});</li>
 *   <li>the lines the caller {@code watch}es for (the selected contract's outcome records), the first watched refusal in
 *   a slot of its own that nothing can displace, the others up to {@link #MAX_WATCHED_LINES}.</li>
 * </ul>
 * Lines are assembled <b>per output stream</b> (stdout, stderr…), so an unfinished line on one stream cannot absorb a
 * line on another. A line longer than {@link #MAX_LINE} is judged by its first and last {@code MAX_LINE} characters. A
 * last line with no newline is classified like any other when the output is read.
 */
public final class JoustOutputCapture {

    static final int MAX_EXTRA_LINES = 256;
    static final int MAX_OUTCOME_LINES = 4_096;
    static final int MAX_WATCHED_LINES = 64;
    static final int MAX_LINE = 2_000;

    private final int cap;
    private final Predicate<String> watch;
    private final StringBuilder head = new StringBuilder();
    private final StringBuilder extra = new StringBuilder();
    private int extraLines;
    private final StringBuilder outcomes = new StringBuilder();
    private int outcomeLines;
    private final StringBuilder watched = new StringBuilder();
    private int watchedLines;
    private String firstRefusal;
    /** JSC0002/JSC0003 failures from complete per-stream lines, from the start of the run (M2-CDX-24). */
    private final java.util.List<String> failures = new java.util.ArrayList<>();
    static final int MAX_FAILURES = 256;
    private final Map<Object, Line> streams = new HashMap<>();

    /** One stream's line being assembled. */
    private static final class Line {
        final StringBuilder start = new StringBuilder();
        final StringBuilder tail = new StringBuilder();
        boolean longLine;

        boolean isEmpty() {
            return start.isEmpty() && tail.isEmpty();
        }

        String text() {
            return longLine ? start + " … " + tail : start.toString();
        }

        void reset() {
            start.setLength(0);
            tail.setLength(0);
            longLine = false;
        }
    }

    /** For tests: how many line buffers are kept. */
    public synchronized int streamCount() {
        return streams.size();
    }

    public JoustOutputCapture(int cap) {
        this(cap, null);
    }

    public JoustOutputCapture(int cap, Predicate<String> watch) {
        this.cap = cap;
        this.watch = watch;
    }

    /** Output with no stream distinction. */
    public void append(String text) {
        append(text, "");
    }

    /** At most this many streams are assembled apart; any further key shares one buffer (memory stays bounded). */
    static final int MAX_STREAMS = 4;

    /**
     * The base stream of an IntelliJ output type (M2-CDX-15, M2-SEC-8): stdout, stderr or system. IntelliJ makes a new
     * type per ANSI style, and a style change inside a line must not split it, nor every colour make a buffer.
     */
    public static String baseStream(com.intellij.openapi.util.Key<?> outputType) {
        if (com.intellij.execution.process.ProcessOutputType.isStderr(outputType)) return "stderr";
        if (com.intellij.execution.process.ProcessOutputType.isStdout(outputType)) return "stdout";
        return "system";
    }

    /** Output from one stream ({@code stream} names it: {@link #baseStream} of IntelliJ's output type, say). */
    public synchronized void append(String text, Object stream) {
        if (text == null) return;
        int take = Math.min(text.length(), Math.max(0, cap - head.length()));
        head.append(text, 0, take);
        boolean pastCap = head.length() >= cap;
        Object key = stream == null ? "" : stream;
        if (!streams.containsKey(key) && streams.size() >= MAX_STREAMS) key = "*";
        Line line = streams.computeIfAbsent(key, k -> new Line());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                classify(line.text(), pastCap && !line.longLine, true);
                line.reset();
            } else if (!line.longLine && line.start.length() < MAX_LINE) {
                line.start.append(c);
            } else {
                line.longLine = true;
                line.tail.append(c);
                if (line.tail.length() > 2 * MAX_LINE) line.tail.delete(0, line.tail.length() - MAX_LINE);
            }
        }
    }

    /** Files one complete line where it belongs; {@code commit} false classifies into a copy (for a pending last line). */
    private void classify(String line, boolean beyondHead, boolean commit) {
        if (!commit) return;
        // Only a line the pinned parser reads as a failure takes a slot (M2-CDX-24).
        if (failure(line) && failures.size() < MAX_FAILURES && !com.bloxbean.intelliada.idea.joust.abi.JoustAbi.failures(line).isEmpty()) {
            failures.add(line);
        }
        if (watch != null && watch.test(line)) {
            if (firstRefusal == null && line.contains("error[JSC0002]")) firstRefusal = line;
            else if (watchedLines < MAX_WATCHED_LINES) {
                watched.append(line).append('\n');
                watchedLines++;
            }
        }
        if (!beyondHead) return;
        // JOUST diagnostics (JSC0002 failures included) have their own slots, then fall back to the outcome slots; the
        // outcome slots take only JOUST's acceptance phrases, so other lines about manifests cannot fill them (M2-CDX-24).
        if (line.contains("error[JSC")) {
            if (extraLines < MAX_EXTRA_LINES) {
                extra.append(line).append('\n');
                extraLines++;
            } else if (outcomeLines < MAX_OUTCOME_LINES) {
                outcomes.append(line).append('\n');
                outcomeLines++;
            }
        } else if (outcome(line) && outcomeLines < MAX_OUTCOME_LINES) {
            outcomes.append(line).append('\n');
            outcomeLines++;
        }
    }

    static boolean failure(String line) {
        return line.contains("error[JSC0002]") || line.contains("error[JSC0003]");
    }

    /** The ABI failures (JSC0002, JSC0003) of complete per-stream lines and each stream's pending line, as one text. */
    public synchronized String failures() {
        StringBuilder out = new StringBuilder();
        for (String f : failures) out.append(f).append('\n');
        for (Line line : streams.values()) {
            if (!line.isEmpty() && failure(line.text()) && failures.size() < MAX_FAILURES
                    && !com.bloxbean.intelliada.idea.joust.abi.JoustAbi.failures(line.text()).isEmpty()) {
                out.append(line.text()).append('\n');
            }
        }
        return out.toString();
    }

    /** JOUST's acceptance phrases (the producers' "accepted the changed" / "created the" ABI manifest records). */
    static boolean outcome(String line) {
        return line.contains("accepted the changed ABI manifest") || line.contains("created the ABI manifest");
    }

    /**
     * The watched records, from complete lines of single streams (M2-CDX-6): the first watched refusal, then the others,
     * then each stream's pending last line if it is watched. Never the merged display text.
     */
    public synchronized java.util.List<String> records() {
        java.util.List<String> out = new java.util.ArrayList<>();
        String refusal = firstRefusal;
        java.util.List<String> pending = new java.util.ArrayList<>();
        if (watch != null) {
            for (Line line : streams.values()) {
                if (line.isEmpty()) continue;
                String text = line.text();
                if (!watch.test(text)) continue;
                if (refusal == null && text.contains("error[JSC0002]")) refusal = text;
                else pending.add(text);
            }
        }
        if (refusal != null) out.add(refusal);
        if (!watched.isEmpty()) out.addAll(java.util.List.of(watched.toString().split("\n")));
        out.addAll(pending);
        return out;
    }

    @Override
    public synchronized String toString() {
        // A last line with no newline, on each stream, is classified exactly as a finished one would be, without
        // consuming it: the first-refusal slot is honoured here too (M2-CDX-6).
        String refusal = firstRefusal;
        StringBuilder pendingWatched = new StringBuilder();
        StringBuilder pendingOther = new StringBuilder();
        int w = watchedLines;
        boolean pastCap = head.length() >= cap;
        for (Line line : streams.values()) {
            if (line.isEmpty()) continue;
            String text = line.text();
            if (watch != null && watch.test(text)) {
                if (refusal == null && text.contains("error[JSC0002]")) refusal = text;
                else if (w < MAX_WATCHED_LINES) {
                    pendingWatched.append(text).append('\n');
                    w++;
                }
            } else if (pastCap && !line.longLine && (outcome(text) || text.contains("error[JSC"))) {   // display only
                pendingOther.append(text).append('\n');
            }
        }
        String r = refusal == null ? "" : refusal + "\n";
        if (extra.isEmpty() && outcomes.isEmpty() && watched.isEmpty() && pendingWatched.isEmpty() && pendingOther.isEmpty() && r.isEmpty()) {
            return head.toString();
        }
        return head + "\n" + extra + outcomes + pendingOther + r + watched + pendingWatched;
    }
}
