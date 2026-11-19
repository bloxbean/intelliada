package com.bloxbean.intelliada.idea.joust.tasks;

/**
 * A task's output, bounded as it arrives: the first {@code cap} characters, and after that only JOUST diagnostic lines
 * ({@code error[JSC…]}, up to {@link #MAX_EXTRA_LINES} lines of {@link #MAX_LINE} characters), so a refusal printed
 * after a long build is never lost to the cap (M2-CDX-6, M2-CDX-15).
 */
public final class JoustOutputCapture {

    static final int MAX_EXTRA_LINES = 256;
    static final int MAX_LINE = 2_000;

    private final int cap;
    private final StringBuilder head = new StringBuilder();
    private final StringBuilder extra = new StringBuilder();
    /** Acceptance outcomes ({@code not accepted}, {@code nothing accepted}, {@code accepted the}…), kept apart (M2-CDX-6). */
    private final StringBuilder outcomes = new StringBuilder();
    private int outcomeLines;
    private final StringBuilder partial = new StringBuilder();
    private boolean discarding;
    private int extraLines;

    /** Lines the caller watches for (the selected contract's records), kept apart from everything else (M2-CDX-6). */
    private final java.util.function.Predicate<String> watch;
    private final StringBuilder watched = new StringBuilder();
    private int watchedLines;
    static final int MAX_WATCHED_LINES = 64;

    public JoustOutputCapture(int cap) {
        this(cap, null);
    }

    public JoustOutputCapture(int cap, java.util.function.Predicate<String> watch) {
        this.cap = cap;
        this.watch = watch;
    }

    public synchronized void append(String text) {
        if (text == null) return;
        int take = Math.min(text.length(), Math.max(0, cap - head.length()));
        head.append(text, 0, take);
        // Every character also passes the line scanner, so a diagnostic that starts before the cap and ends after it is kept.
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                if (!discarding) {
                    String line = partial.toString();
                    if (watch != null && watchedLines < MAX_WATCHED_LINES && watch.test(line)) {
                        watched.append(line).append('\n');
                        watchedLines++;
                    }
                    if (head.length() >= cap) keep(line, i >= take);
                }
                partial.setLength(0);
                discarding = false;
            } else if (!discarding) {
                if (partial.length() >= MAX_LINE) {
                    partial.setLength(0);
                    discarding = true;
                } else {
                    partial.append(c);
                }
            }
        }
    }

    static final int MAX_OUTCOME_LINES = 4_096;

    /**
     * JOUST's own acceptance records only (M2-CDX-6): {@code … ABI manifest …} (accepted the changed, created the) and
     * {@code error[JSC0002]} (not accepted, nothing accepted). Other lines that merely say "accepted" never take a slot.
     */
    static boolean outcome(String line) {
        return line.contains("ABI manifest") || line.contains("error[JSC0002]");
    }

    private void keep(String line, boolean beyondHead) {
        if (!beyondHead) return;
        if (outcome(line) && outcomeLines < MAX_OUTCOME_LINES) {
            outcomes.append(line).append('\n');
            outcomeLines++;
            return;
        }
        if (extraLines >= MAX_EXTRA_LINES || !line.contains("error[JSC")) return;
        extra.append(line).append('\n');
        extraLines++;
    }

    @Override
    public synchronized String toString() {
        String tail = !discarding && head.length() >= cap && (partial.indexOf("error[JSC") >= 0 || outcome(partial.toString()))
                ? partial + "\n" : "";
        String last = !discarding && watch != null && !partial.isEmpty() && watchedLines < MAX_WATCHED_LINES && watch.test(partial.toString())
                ? partial + "\n" : "";
        return extra.isEmpty() && outcomes.isEmpty() && tail.isEmpty() && watched.isEmpty() && last.isEmpty() ? head.toString()
                : head + "\n" + extra + outcomes + tail + watched + last;
    }
}
