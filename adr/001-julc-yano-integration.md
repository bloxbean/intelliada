# ADR-001: julc + Yano Integration into IntelliAda

**Status:** In Progress  
**Date:** 2026-04-10  
**Authors:** Satya, Claude Code

## Context

IntelliAda is an IntelliJ plugin for Cardano blockchain development. It currently supports:
- **Aiken** smart contract language (grammar, LSP, completion, navigation)
- **Yaci DevKit** / Blockfrost / Koios backends for chain interaction
- Account management, transactions, token minting, UTXO explorer

Two new bloxbean projects need IDE integration:

1. **julc** (github.com/bloxbean/julc) — A Java-to-UPLC (Untyped Plutus Lambda Calculus) compiler. Developers write Cardano smart contracts in Java using annotations (`@SpendingValidator`, `@MintingValidator`, `@Entrypoint`, `@Param`, `@MultiValidator`) and julc compiles them to Plutus V3 bytecode. Features: CLI (`julc new/build/check/eval/repl`), Gradle plugin, Maven support, annotation processor, testkit, CIP-57 blueprint generation.

2. **Yano** (github.com/bloxbean/yano) — A lightweight Cardano devnet node written in Java. Single JAR or native binary (no Docker). REST API on port 7070 (Blockfrost-compatible). Devnet features: faucet, named snapshots, rollback, time advance, epoch shifting (past time travel), Plutus script evaluation.

**Goal:** Enable end-to-end Cardano dApp development entirely within IntelliJ:
Create julc project -> write Java validators -> build to UPLC -> test locally -> start Yano devnet -> fund accounts -> deploy & execute scripts -> iterate with snapshots/rollback/time travel.

## Decision

### Architecture Decisions

1. **Yano coexists with Yaci DevKit** as a separate `NodeType.Yano` enum value. Existing DevKit configurations are preserved. Yano is preferred for lightweight embedded testing; DevKit for full-stack (node + store + viewer).

2. **julc follows Aiken's integration patterns** — SDK config (`JulcSDKState`/`JulcProjectState`), module builder, compile service, action group. Since julc compiles Java, no grammar/lexer/parser is needed — the IDE already provides full Java support.

3. **julc external annotator uses library dependency** — `julc-compiler` + `javaparser-core` added as `compileOnly` deps. `SubsetValidator` runs in-process for instant inline error highlighting of unsupported Java features.

4. **Three julc project modes** — basic (`julc.toml`), Gradle (`build.gradle` with julc deps), Maven (`pom.xml` with julc deps). `JulcCompileService` auto-detects and routes to appropriate build command.

5. **Yano process management** — Health-based startup detection (polling `/q/health/ready`) instead of stdout parsing. Prefers native binary over JVM JAR for faster startup (~500ms vs 3-5s).

### Key Integration Points

- `NodeServiceFactory` routes `NodeType.Yano` to `BFBackendService` (Blockfrost-compatible API)
- `CardanoServiceFactory` routes `NodeType.Yano` to `YaciAccountServiceImpl`
- `YaciBaseService.ensureYanoRunning()` auto-starts Yano when operations are triggered
- julc build actions reuse Aiken's `CompilationResultListener` and `CardanoConsole` for output streaming
- Yano Devnet Panel provides 5-tab UI: Status, Fund, Snapshots, Rollback, Time Machine

## Implementation Plan & Status

### Phase 0 (P0) — Core Foundation [COMPLETED]

| Component | Files | Status |
|-----------|-------|--------|
| julc SDK config (JulcSDK, JulcSDKState, JulcProjectState, helper, UI) | 13 files in `julc/configuration/`, `julc/messaging/`, `julc/util/`, `julc/common/` | Done |
| julc project detection (JulcTomlService, JulcProjectOpenProcessor) | 2 files in `julc/module/` | Done |
| julc build/check actions (JulcCompileService, JulcBuildAction, JulcCheckAction, JulcActionGroup) | 4 files in `julc/compile/`, `julc/action/` | Done |
| Yano NodeType + process lifecycle (YanoProcessManager, YanoStatusMonitor, YanoLifecycleService) | 3 files in `nodeint/yano/` | Done |
| Yano backend routing (NodeServiceFactory, CardanoServiceFactory, YaciBaseService) | 3 modified files | Done |
| plugin.xml registrations | All services, actions, tool windows registered | Done |

### Phase 1 (P1) — Project Creation & Devnet UI [COMPLETED]

| Component | Files | Status |
|-----------|-------|--------|
| julc project wizard (JulcModuleBuilder with template/group/artifact/package) | 1 file in `julc/module/` | Done |
| Yano download (YanoDownloader — GitHub releases, platform-aware, native preferred) | 1 file in `nodeint/yano/` | Done |
| Yano devnet service (YanoDevnetService — full REST client) | 1 file in `nodeint/yano/` | Done |
| Yano DTOs (FundResponse, SnapshotResponse, RollbackResponse, TimeAdvanceResponse, EpochShiftResponse) | 5 files in `nodeint/yano/model/` | Done |
| Yano devnet tool window (YanoDevnetPanel — 5 tabs: Status, Fund, Snapshots, Rollback, Time Machine) | 2 files in `nodeint/yano/ui/` | Done |

### Phase 2 (P2) — Smart Features [COMPLETED]

| Component | Files | Status |
|-----------|-------|--------|
| julc external annotator (SubsetValidator inline errors) | Deferred — requires julc-compiler dependency | Deferred |
| julc blueprint viewer (UPLC, script hash, size from CIP-57) | `julc/blueprint/` (2 files) + `julc/service/` (2 files) | Done |
| Deploy validator action (with @Param support) | `julc/action/DeployValidatorAction.java` | Done |
| Blueprint loading service | `julc/service/BlueprintLoadService.java`, `PlutusBlueprint.java` | Done |

### Phase 3 (P3) — Polish & Advanced Workflows [COMPLETED]

| Component | Files | Status |
|-----------|-------|--------|
| julc REPL tool window | `julc/repl/JulcReplToolWindowFactory.java`, `JulcReplPanel.java` | Done |
| Live templates (@SpendingValidator, @MintingValidator, @MultiValidator, etc.) | `resources/liveTemplates/julc.xml` | Done |
| Yano status bar widget | `yano/ui/YanoStatusBarWidgetFactory.java` | Done |

### Deferred Items

| Component | Reason |
|-----------|--------|
| julc external annotator | Requires adding `julc-compiler` + `javaparser` as compileOnly deps. Should be done when julc artifacts are published to Maven Central. |
| Run configurations + gutter icons | Requires `ConfigurationType` + `RunConfigurationProducer` — complex IntelliJ API work, best done iteratively with manual testing via `runIde`. |
| Execute script action | Requires full transaction building with collateral selection — depends on existing transaction UI patterns. |
| One-click "Build, Deploy & Test" | Depends on Deploy + Execute actions being complete. |
| UTXO explorer enhancements | Enhancement to existing UI — should be done as a separate PR. |
| Scenario comparison | Enhancement to snapshot tab — can be added incrementally. |

## File Summary

### New Files Created (39 total including tests)

```
src/main/java/com/bloxbean/intelliada/idea/julc/
├── action/JulcActionGroup.java
├── common/JulcIcons.java
├── compile/
│   ├── JulcCompileService.java
│   └── action/
│       ├── JulcBuildAction.java
│       └── JulcCheckAction.java
├── configuration/
│   ├── JulcConfigurationAction.java
│   ├── JulcConfigurationHelperService.java
│   ├── JulcSDK.java
│   ├── service/
│   │   ├── JulcProjectState.java
│   │   └── JulcSDKState.java
│   └── ui/
│       ├── JulcProjectConfig.java
│       ├── JulcProjectConfigurationDialog.java
│       ├── JulcSDKDialog.java
│       └── JulcSDKPanel.java
├── messaging/
│   ├── JulcProjectConfigChangeNotifier.java
│   └── JulcSDKChangeNotifier.java
├── module/
│   ├── JulcModuleBuilder.java
│   ├── pkg/JulcTomlService.java
│   └── project/JulcProjectOpenProcessor.java
└── util/JulcSdkUtil.java

src/main/java/com/bloxbean/intelliada/idea/nodeint/yano/
├── YanoDevnetService.java
├── YanoDownloader.java
├── YanoLifecycleService.java
├── YanoProcessManager.java
├── YanoStatusMonitor.java
├── model/
│   ├── EpochShiftResponse.java
│   ├── FundResponse.java
│   ├── RollbackResponse.java
│   ├── SnapshotResponse.java
│   └── TimeAdvanceResponse.java
└── ui/
    ├── YanoDevnetPanel.java
    ├── YanoDevnetToolWindowFactory.java
    └── YanoStatusBarWidgetFactory.java

src/main/resources/liveTemplates/julc.xml

src/test/java/com/bloxbean/intelliada/idea/julc/
├── configuration/JulcSDKTest.java
└── service/BlueprintLoadServiceTest.java

src/test/java/com/bloxbean/intelliada/idea/nodeint/yano/
├── YanoDevnetServiceTest.java
└── YanoProcessManagerTest.java
```

### Existing Files Modified

| File | Change |
|------|--------|
| `core/util/NodeType.java` | Added `Yano("Yano Devnet")` |
| `nodeint/service/NodeServiceFactory.java` | Route Yano -> BFBackendService |
| `nodeint/service/CardanoServiceFactory.java` | Route Yano -> YaciAccountServiceImpl |
| `nodeint/service/impl/yaciprovider/YaciBaseService.java` | Added `ensureYanoRunning()` |
| `resources/META-INF/plugin.xml` | All julc + Yano registrations |

## Verification

- **Build:** `export JAVA_HOME=~/.sdkman/candidates/java/21.0.3-tem && ./gradlew compileJava` — SUCCESSFUL
- **Tests:** `export JAVA_HOME=~/.sdkman/candidates/java/21.0.3-tem && ./gradlew test` — ALL PASSING
- **Note:** JDK 21 required for compilation (Lombok 1.18.34 incompatible with JDK 25)

## Wow Features (Yano)

1. **Time Machine** — Advance by slots/seconds/epochs + past time travel (shift genesis back N epochs, inject transactions at historical points, catch up to wall clock)
2. **Named Snapshots** — Create/restore/delete chain state snapshots for scenario testing
3. **Rollback** — Undo last N blocks instantly
4. **Script Cost Explorer** — Pre-evaluate Plutus scripts via `/api/v1/utils/txs/evaluate` before submitting
5. **One-Click "Build, Deploy & Test"** — Chains entire workflow from build to devnet deployment
6. **Parameterized Validator Support** — Deploy dialog handles `@Param` fields with per-parameter inputs
7. **Redeemer Variant Selector** — For sealed interface redeemers, shows variant picker with per-variant fields

## References

- julc repo: /Users/satya/work/bloxbean/julc
- julc examples: /Users/satya/work/bloxbean/julc-examples
- Yano repo: /Users/satya/work/bloxbean/yano
- Implementation plan: /Users/satya/.claude/plans/buzzing-spinning-bird.md
