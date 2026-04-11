package com.bloxbean.intelliada.idea.julc.trace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks budget changes between evaluations to show deltas.
 * "Budget: CPU 1,234,567 (+50,234 from last run)"
 */
public class JulcBudgetTracker {

    private static final Map<String, BudgetSnapshot> snapshots = new ConcurrentHashMap<>();

    public record BudgetSnapshot(long cpu, long memory, int scriptSizeBytes, long timestamp) {}

    public record BudgetDelta(long cpuDelta, long memDelta, int sizeDelta, BudgetSnapshot current, BudgetSnapshot previous) {
        public boolean hasChanged() {
            return cpuDelta != 0 || memDelta != 0 || sizeDelta != 0;
        }

        public String formatCpuDelta() {
            return formatDelta(cpuDelta);
        }

        public String formatMemDelta() {
            return formatDelta(memDelta);
        }

        public String formatSizeDelta() {
            if (sizeDelta == 0) return "";
            return (sizeDelta > 0 ? "+" : "") + sizeDelta + " B";
        }

        private String formatDelta(long delta) {
            if (delta == 0) return "";
            String sign = delta > 0 ? "+" : "";
            if (Math.abs(delta) < 1000) return sign + delta;
            if (Math.abs(delta) < 1_000_000) return String.format("%s%.1fK", sign, delta / 1000.0);
            return String.format("%s%.2fM", sign, delta / 1_000_000.0);
        }
    }

    public static BudgetDelta record(String filePath, long cpu, long memory, int scriptSizeBytes) {
        BudgetSnapshot current = new BudgetSnapshot(cpu, memory, scriptSizeBytes, System.currentTimeMillis());
        BudgetSnapshot previous = snapshots.put(filePath, current);

        if (previous == null) {
            return new BudgetDelta(0, 0, 0, current, null);
        }

        return new BudgetDelta(
                cpu - previous.cpu,
                memory - previous.memory,
                scriptSizeBytes - previous.scriptSizeBytes,
                current, previous
        );
    }

    public static BudgetSnapshot getLastSnapshot(String filePath) {
        return snapshots.get(filePath);
    }
}
