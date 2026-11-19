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

    public JoustOutputCapture(int cap) {
        this.cap = cap;
    }

    public synchronized void append(String text) {
        if (text == null) return;
        int take = Math.min(text.length(), Math.max(0, cap - head.length()));
        head.append(text, 0, take);
        // Every character also passes the line scanner, so a diagnostic that starts before the cap and ends after it is kept.
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                if (!discarding && head.length() >= cap) keep(partial.toString(), i >= take);
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

    private void keep(String line, boolean beyondHead) {
        if (!beyondHead) return;
        if (line.contains("accepted") && outcomeLines < MAX_EXTRA_LINES) {
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
        String tail = !discarding && head.length() >= cap && (partial.indexOf("error[JSC") >= 0 || partial.indexOf("accepted") >= 0)
                ? partial + "\n" : "";
        return extra.isEmpty() && outcomes.isEmpty() && tail.isEmpty() ? head.toString() : head + "\n" + extra + outcomes + tail;
    }
}
