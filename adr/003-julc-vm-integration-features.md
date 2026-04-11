# ADR-003: julc VM Integration — Real-time Feedback & Execution Trace

**Status:** Approved  
**Date:** 2026-04-10  
**Goal:** Make IntelliAda the best Cardano smart contract development experience — instant compile, evaluate, trace, all from the editor.

## Context

The julc-compiler and julc-vm shadow JARs are loaded at runtime via isolated URLClassLoaders on JBR 25. This gives us:
- `JulcCompiler.compile(source)` → `CompileResult` (program, diagnostics, script size, UPLC, source map)
- `JulcVm.evaluate(program)` → `EvalResult` (success/failure, budget, traces, execution trace, builtin trace)
- `ScriptContextTestBuilder` → mock ScriptContext for testing

These enable 9 features that no other Cardano IDE provides.

## Features

### Feature 1: Live Script Size in Gutter

**Priority:** P0 — Quick win, high visibility

Show compiled script size next to `@Validator` class in the gutter. Updates on file save.

```
  🔷 342 B   | @SpendingValidator
              | public class VestingValidator {
```

- Compile in background on file save (debounced, 500ms delay)
- Show size as gutter annotation text or tooltip
- Color coding: green (< 8KB), yellow (8-14KB), red (> 14KB approaching 16KB limit)
- Uses `CompileResult.scriptSizeBytes()`

**File:** `julc/annotator/JulcScriptSizeAnnotator.java`

---

### Feature 2: Inline Budget Estimation

**Priority:** P1 — Enhances #1

After compiling, show estimated CPU/memory cost next to `@Entrypoint` method. Requires a test evaluation with mock inputs to get budget.

```
  ▶ CPU: 1.2M | Mem: 45K   | @Entrypoint
                             | public static boolean validate(...) {
```

- Auto-evaluate with default/empty inputs after compile
- Show budget in gutter tooltip
- Compare with protocol max (from Yano if running, or hardcoded defaults)

**File:** Enhancement to `JulcScriptSizeAnnotator.java`

---

### Feature 3: "Run Validator" from Gutter

**Priority:** P0 — The killer feature

Click the play icon on `@Entrypoint` → opens a dialog where user provides datum, redeemer, and context inputs → compiles → evaluates → shows result.

**Input Dialog:**
- Extract parameter types from `@Entrypoint` method signature via PSI
- For `record` types (datum): generate input fields per record field
  - `byte[]` → hex input
  - `BigInteger` → number input
  - `String` → text input
  - Nested records → expandable tree
- For `PlutusData` → raw JSON/CBOR editor
- For `ScriptContext` → auto-generate mock with configurable:
  - Signatories (list of pubkey hashes)
  - Validity range (from/to slots)
  - Inputs/outputs (simplified)
  - Or "custom JSON" for advanced users

**Result Panel:**
- ✅ PASS / ❌ FAIL
- Budget: CPU steps / Memory units
- Script size
- User trace messages (from `Builtins.trace()`)
- Link to full execution trace (Feature 7)

**Implementation:**
- `JulcRunValidatorAction.java` — triggered from gutter or context menu
- `JulcRunValidatorDialog.java` — input collection with type-aware fields
- `JulcRunResultPanel.java` — result display
- Uses `JulcVmBridge.compile()` then `JulcVmBridge.evaluate()`
- For `ScriptContext`: load `ScriptContextTestBuilder` from VM shadow JAR via reflection

**File:** `julc/run/JulcRunValidatorAction.java`, `julc/run/JulcRunValidatorDialog.java`, `julc/run/JulcRunResultPanel.java`

---

### Feature 4: Script Size Warning Banner

**Priority:** P1 — Safety net

When compiled script exceeds warning threshold, show an editor notification banner:

```
⚠️ Script size: 14.2 KB / 16 KB — consider optimizing    [Dismiss] [Show UPLC]
```

- Yellow at > 12KB, red at > 15KB
- Triggers after compile (from gutter or build action)
- "Show UPLC" opens the UPLC preview (Feature 5)
- Uses IntelliJ's `EditorNotificationProvider` API

**File:** `julc/editor/JulcScriptSizeNotificationProvider.java`

---

### Feature 5: UPLC Preview Panel

**Priority:** P2 — Educational, helps optimization

Split editor or tool window showing generated UPLC alongside Java source. Updates on save.

```
┌─ VestingValidator.java ────────┬─ UPLC Preview ──────────────┐
│ @SpendingValidator             │ (program 1.1.0              │
│ public class VestingValidator  │   (lam i_0                  │
│   record VestingDatum(...)     │     (lam i_1                │
│                                │       (force                │
│   @Entrypoint                  │         (force              │
│   static boolean validate(...) │           (builtin ifThenE  │
│     return signed && past;     │             (lam ...         │
└────────────────────────────────┴──────────────────────────────┘
```

- Tool window tab "UPLC" — auto-activates for julc validator files
- Recompiles on file save, shows formatted UPLC
- Syntax highlighting for UPLC (keywords, builtins, constants)
- Click on UPLC line → highlights corresponding Java source (via SourceMap)

**File:** `julc/editor/JulcUplcPreviewToolWindowFactory.java`, `julc/editor/JulcUplcPreviewPanel.java`

---

### Feature 6: Budget Comparison (Delta Tracking)

**Priority:** P2 — Helps optimization workflow

Track budget changes between saves. Show delta:

```
Budget: CPU 1,234,567 (+50,234 from last save) | Memory 45,678 (-1,200)
```

- Store last-known budget per file in memory
- After each compile+evaluate, compare and show delta
- Green arrow (↓ decreased), red arrow (↑ increased)
- Shown in the Run Result panel and gutter tooltip
- Helps developers see the cost impact of each code change

**File:** Enhancement to `JulcRunResultPanel.java` + `JulcBudgetTracker.java`

---

### Feature 7: Visual Execution Trace

**Priority:** P1 — Unique differentiator

After "Run with Trace", show a step-by-step execution trace panel:

```
┌─ Execution Trace ─────────────────────────────────────────────┐
│ Step │ Operation          │ CPU    │ Source                    │
│──────│────────────────────│────────│───────────────────────────│
│  1   │ Lam (validate)     │     12 │ VestingValidator.java:15  │
│  2   │ App                │      8 │ VestingValidator.java:16  │
│  3   │ Force              │      4 │                           │
│  4   │ Builtin signedBy   │ 12,456 │ VestingValidator.java:17  │
│  5   │ Builtin afterSlot  │  8,234 │ VestingValidator.java:18  │
│  6   │ IfThenElse (true)  │     12 │ VestingValidator.java:19  │
│  7   │ Const True         │      4 │ VestingValidator.java:19  │
├───────────────────────────────────────────────────────────────┤
│ Total: CPU 21,234 | Memory 3,456 | Result: ✅ True            │
└───────────────────────────────────────────────────────────────┘
```

- Uses `EvalResult.executionTrace()` (list of `ExecutionTraceEntry`)
- Map UPLC positions to Java source lines via `CompileResult.sourceMap()`
- Click on a row → navigate to the Java source line
- Sortable by CPU cost to find hotspots
- User trace messages (`Builtins.trace()`) shown inline

**File:** `julc/trace/JulcExecutionTracePanel.java`, `julc/trace/JulcTraceToolWindowFactory.java`

---

### Feature 8: Trace-to-Source Mapping

**Priority:** P2 — Enhances #7

Overlay execution trace on the source editor:

- After "Run with Trace", show colored line markers in the gutter
- Green: executed, passed
- Red: executed, failed here
- Gray: not reached (dead code in this execution path)
- Hover on marker → shows CPU/memory cost for that line
- Like a code coverage overlay but for on-chain execution

Uses `SourceMap` from `CompileResult` + `executionTrace` from `EvalResult`.

**File:** Enhancement to `JulcLineMarkerProvider.java` or new `JulcTraceOverlayAnnotator.java`

---

### Feature 9: Budget Hotspot Visualization

**Priority:** P2 — Helps optimization

Use the `builtinTrace` (list of `BuiltinExecution`) to show per-line costs:

```
   142 │  12,456 CPU │ boolean signed = ContextsLib.signedBy(txInfo, beneficiary);
   143 │   8,234 CPU │ boolean past = IntervalLib.after(deadline, txInfo.validRange());
   144 │      16 CPU │ return signed && past;
```

- Color intensity proportional to cost (heatmap effect)
- Most expensive lines highlighted in red/orange
- Gutter shows CPU cost per line
- Bottom summary: "Top 3 costly operations: signedBy (60%), after (39%), return (1%)"

**File:** `julc/trace/JulcBudgetHeatmapAnnotator.java`

---

## Implementation Order

| Phase | Features | Effort | Impact |
|-------|----------|--------|--------|
| **Phase 1** | #3 Run Validator (basic, auto inputs) | Medium | Killer feature — compile + evaluate from gutter |
| **Phase 1** | #1 Live Script Size | Low | Quick win — size in gutter |
| **Phase 2** | #3 Run Validator (user inputs dialog) | High | Full input customization |
| **Phase 2** | #7 Visual Execution Trace | Medium | Unique differentiator |
| **Phase 2** | #4 Script Size Warning | Low | Safety net |
| **Phase 3** | #2 Inline Budget Estimation | Medium | Enhances gutter info |
| **Phase 3** | #5 UPLC Preview Panel | Medium | Educational |
| **Phase 3** | #6 Budget Delta Tracking | Low | Optimization workflow |
| **Phase 3** | #8 Trace-to-Source Mapping | Medium | Enhances trace |
| **Phase 3** | #9 Budget Hotspot Visualization | Medium | Optimization visualization |

## Technical Notes

### Shadow JAR Classes Used

From `julc-compiler-all.jar`:
- `JulcCompiler.compile(source)` → `CompileResult`
- `CompileResult.program()`, `.scriptSizeBytes()`, `.diagnostics()`, `.sourceMap()`, `.uplcFormatted()`
- `SubsetValidator.validate(cu)` → diagnostics

From `julc-vm-java-all.jar`:
- `JulcVm.create()` → VM instance
- `JulcVm.evaluate(program)` → `EvalResult`
- `EvalResult.Success/Failure/BudgetExhausted`
- `EvalResult.consumed()` → `ExBudget` (cpu, mem)
- `EvalResult.traces()` → user trace messages
- `EvalResult.executionTrace()` → per-step trace
- `EvalResult.builtinTrace()` → per-builtin cost
- `ScriptContextTestBuilder` → mock context for testing

### Reflection Pattern

All calls go through `JulcVmBridge` which handles:
- Classloader isolation (parent = null for compiler, compiler as parent for VM)
- Reflection-based method invocation
- Result mapping to Java 21 compatible POJOs (`CompileInfo`, `EvalInfo`)
- Graceful error handling

### Performance

- `JulcCompiler.compile()` takes ~50-200ms for typical validators
- `JulcVm.evaluate()` takes ~10-50ms
- Total round-trip for "Run Validator": < 500ms
- Suitable for real-time feedback on file save (debounced)
