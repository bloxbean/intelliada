# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

IntelliAda is an IntelliJ platform plugin for Cardano blockchain development. It integrates with Blockfrost, Koios, and Yaci DevKit backends so developers can manage accounts, submit transactions, mint tokens, and write Aiken smart contracts without leaving the IDE.

**Plugin ID:** `com.bloxbean.intelliada` | **Version:** 0.2.0-beta2 | **Min IDE:** 2024.2+
**Supported IDEs:** IntelliJ IDEA, PyCharm, WebStorm

## Build Commands

```bash
./gradlew buildPlugin          # Full build (CI uses this)
./gradlew build                # Build + tests
./gradlew test                 # Run all tests
./gradlew test --tests "com.bloxbean.intelliada.idea.scripts.util.ScriptParserTest"  # Single test class
./gradlew generateLexer        # Regenerate Aiken lexer from .flex
./gradlew generateParser       # Regenerate Aiken parser from .bnf
./gradlew runIde               # Launch a sandboxed IDE with the plugin loaded
```

Requires **JDK 21**. Grammar generation must run before compilation if .bnf or .flex files change.

## Source Layout

- `src/main/java/` — Hand-written source code
- `src/main/gen/` — **Auto-generated** parser/lexer/PSI from GrammarKit. Never edit directly.
- `src/main/idea/` — Additional source root (configured in `sourceSets`)
- `src/main/resources/META-INF/plugin.xml` — Plugin extension points, actions, services
- `lib/` — Bundled JARs loaded via `fileTree`

## Architecture

### Backend Abstraction (Node Integration)

The plugin supports multiple Cardano backends. `NodeType` enum defines them: Blockfrost (mainnet/preprod/preview/custom), Koios (preprod/mainnet/custom), YaciDevKit, and LocalYaciDevKit.

- `CardanoServiceFactory` / `NodeServiceFactory` — create the appropriate service implementation based on the configured `RemoteNode`
- `nodeint/service/api/` — service interfaces (`CardanoAccountService`, `TransactionService`, `NetworkInfoService`, etc.)
- `nodeint/service/impl/` — Blockfrost/Koios implementations (via `cardano-client-lib`)
- `nodeint/service/impl/yaciprovider/` — Yaci DevKit implementations with direct REST calls
- `nodeint/devkit/` — DevKit lifecycle: download, process management, status monitoring

Configuration is stored via IntelliJ's `PersistentStateComponent` pattern (`RemoteNodeState`, `CLIProvidersState`).

### Actions

All user-facing IDE actions extend `BaseAction` or `BaseTxnAction` (in `core/action/`). Transaction actions handle node configuration validation, logging to `CardanoConsole`, and error display. Each feature module (account, transaction, nativetoken, scripts, metadata) has its own `action/` package.

### Aiken Language Support

This is the largest subsystem. Two layers work together:

1. **Custom grammar-based support** — BNF grammar (`aiken/lang/grammar/Aiken.bnf`) and JFlex lexer (`_AikenLexer.flex`) generate parser and PSI classes into `src/main/gen/`. `AikenPsiImplUtil` provides helper methods injected into generated PSI via `psiImplUtilClass`. When modifying the grammar, run `generateLexer` then `generateParser` before building.

2. **LSP integration** — `AikenLanguageServerFactory` creates an LSP connection to the `aiken` CLI's language server via LSP4IJ. LSP provides folding, parameter info, and completion (as a fallback). Custom completion contributors are ordered explicitly in plugin.xml (`order="first"` for imports).

Key Aiken subsystems:
- `aiken/completion/` — Multiple completion contributors (imports, types, validators, cross-file, chain-aware) with explicit ordering
- `aiken/navigation/` — Goto-declaration handler for stdlib and package navigation
- `aiken/reference/` — Reference contributor for stdlib symbol resolution
- `aiken/index/` — File-based index (`AikenSymbolIndex`) for symbol lookup
- `aiken/service/` — `AikenStdlibService` (stdlib file resolution), `AikenPackageService` (discovers packages in `build/packages/`)
- `aiken/module/` — Project creation wizards (`AikenModuleBuilder` for IDEA, `AikenDirectoryProjectGenerator` for other IDEs)

### UI Components

- **Cardano Tool Window** (right panel) — Tree-based explorer (`CardanoExplorerTreeStructure`) with nested action nodes
- **Cardano Log** (bottom panel) — Console output via `CardanoConsole` project service
- Forms use IntelliJ's `.form` + Java pattern (e.g., `DevKitNodeConfigPanel.form` / `.java`)

## Key Dependencies

- `cardano-client-lib` 0.6.2 — Core Cardano operations (transactions, accounts, keys)
- `cardano-client-backend-blockfrost` / `cardano-client-backend-koios` — Backend providers
- `com.redhat.devtools.lsp4ij` 0.7.0 — LSP4IJ plugin dependency for language server integration
- `toml4j` — Parses `aiken.toml` project files
- Lombok — Used for model classes (`@Data`, `@Getter`, etc.)

## Testing

Tests use JUnit 5 + Mockito + AssertJ. Most tests are unit tests for utilities and service logic. No IntelliJ platform test framework (`LightPlatformCodeInsightFixtureTestCase`) is currently used, so Aiken PSI/completion tests require the sandboxed IDE (`runIde`).

## Important Patterns

- **slf4j is excluded** from all `cardano-client-lib` transitive dependencies to avoid conflicts with the IDE's logging.
- **Plugin XML ordering matters** for completion contributors — import completions must be `order="first"` to take priority over LSP completions.
- The `ParseErrorHighlightFilter` suppresses grammar-level parse errors since the BNF grammar is intentionally incomplete (LSP handles full validation).


## JULC 

Repository: https://github.com/bloxbean/julc.git
Project path: /Users/satya/work/bloxbean/julc

## Yano

Repository: https://github.com/bloxbean/yano.git
Project path: /Users/satya/work/bloxbean/yano
