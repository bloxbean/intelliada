# ADR-004: JOUST support in IntelliAda

**Status:** Proposed (r1)
**Date:** 2026-11-18
**Authors:** Claude Code (drafted for the IntelliAda maintainers' review)
**Scope:** design only. This ADR adds no code, no build change and no plugin.xml entry. Every snippet and name below is
illustrative unless it cites a file.
**Base:** stacked on `feat/julc-yano-integration` (PR #8) at `d7a022e`, because ADR-001 to ADR-003 and the Yano code
exist only there (not on `master` at `b3d3fce`).
**Builds on:** IntelliAda ADR-001 (julc and Yano integration), ADR-002 (the julc and Yano developer flow), ADR-003
(julc VM features). JOUST ADR-0013 (descriptions, the prepare service, wallets), ADR-0015 (the npm toolchain and
runtime), ADR-0017 (the `joust` CLI and the Gradle plugin), ADR-0018 D12 (stable codes, ABI manifests, message maps),
ADR-0021 (the `.jsc` language server and the VS Code extension), at JOUST `main` `d529bfd` (2026-11-17).
**Risk:** high. IntelliAda would start a language server and project-supplied processes, sign transactions with keys
it holds, and talk to devnet nodes. A wrong trust decision runs a hostile repository's code; a wrong signing decision
spends funds the keys control elsewhere. Nothing here changes a validator, a wire format or what JOUST's build accepts.

## 1. Context

JOUST (JuLC State Contracts) lets a developer write a Cardano contract as a state record plus pure `init`, action and
view functions, in Java (`@StateContract`) or in JOUST's own language, `.jsc`. JOUST generates the validator and a
typed off-chain client, and the same method runs on the JVM (to build the transaction) and on chain (to check it).
JOUST already ships a language server (`joust-lsp`), a VS Code extension, a Gradle plugin (`org.julclang.joust`), an
npm toolchain and runtime, and a prepare service. IntelliAda already integrates Aiken through LSP4IJ, julc through
in-IDE compiler bridges, and Yaci DevKit and Yano devnets.

The maintainer asked for three things: `.jsc` language support, JOUST project support, and a way to deploy and call a
JOUST contract on a local DevKit or Yano devnet **directly from its code in the editor**. This ADR decides how, with
read-only editing first.

### 1.1 Facts this design relies on

IntelliAda facts are at `d7a022e` (`feat/julc-yano-integration`). JOUST facts are at `d529bfd` (`origin/main`). LSP4IJ
facts come from the 0.10.0 jar the branch resolves (`instrumented-lsp4ij-0.10.0.jar`, disassembled with `javap`) and
from its docs at tag `0.10.0` (`bc13e84`). IntelliJ facts were checked in an installed IntelliJ IDEA 2025.2.4
(build 252.27397.103), **not** in 2026.1, which the branch targets; M1's entry gate re-checks them.

| # | Fact | Source |
|---|---|---|
| F1 | The branch builds with IntelliJ Platform Gradle plugin 2.14.0 against IntelliJ IDEA 2026.1, LSP4IJ 0.10.0 and CCL 0.6.2, with `sinceBuild = "261"`. `master` builds against 2024.2 (`sinceBuild 242`) and LSP4IJ 0.5.0. `CLAUDE.md` and `README.md` still say "Min IDE 2024.2" and LSP4IJ 0.7.0. The branch's plugin.xml adds a mandatory `com.intellij.modules.java` dependency that `master` does not have | `build.gradle:20`, `:39`, `:41`, `:45`, `:118`; `git show origin/master:build.gradle` (`:20`, `:46`, `:48`, `:111`); `plugin.xml:15-18` |
| F2 | Aiken's language server is an LSP4IJ `server` with a `languageMapping` to the Aiken language. `AikenLanguageServer` runs `<sdk path>/aiken lsp --stdio`, with the project base path as working directory and the system environment. The SDK path is application-level state (`aiken-sdks.xml`); the project stores only the SDK's id (`aiken-project.xml`). IntelliAda has no other LSP client | `plugin.xml:189-199`; `aiken/AikenLanguageServer.java:13-27`; `aiken/configuration/AikenSDK.java:52-57`; `aiken/configuration/AikenConfigurationHelperService.java:20-39`; `AikenSDKState` and `AikenProjectState` `@Storage` |
| F3 | IntelliAda never consults IntelliJ's project trust (no use of `TrustedProjects`), and `JulcCompileService` runs a project's `./gradlew` or `./mvnw` when the user builds | `grep -rn "TrustedProjects\|isTrusted" src` → nothing; `julc/compile/JulcCompileService.java:126-133`, `:174-189` |
| F4 | julc support loads `julc-compiler-all.jar` and `julc-vm-java-all.jar` into isolated `URLClassLoader`s **inside the IDE JVM** (ADR-003), and `JulcExternalAnnotator` runs julc's `SubsetValidator` there. It triggers only on files that contain a julc validator annotation or `@OnchainLibrary`, never on `@StateContract`. julc project detection (`JulcTomlService`) looks for `julc.toml` or julc artifact names in build files, so a JOUST Gradle project (which names `org.julclang.joust`) is not a julc project | `julc/annotator/validate/JulcVmBridge.java:29-41`; `julc/annotator/JulcExternalAnnotator.java:128-132`; `julc/module/pkg/JulcTomlService.java:32-90` |
| F5 | Node types: `YaciDevKit`, `LocalYaciDevKit`, `Yano` and the Blockfrost and Koios networks. The target node is one application-level default (`RemoteNodeState`), not a project setting. Yano routes to CCL's `BFBackendService` (Blockfrost-style API); Yano starts on port 7070 by default | `core/util/NodeType.java:7-18`; `nodeint/CardanoNodeConfigurationHelper.java:9-11`; `nodeint/service/NodeServiceFactory.java:39-41`; `nodeint/yano/YanoLifecycleService.java:41-42` |
| F6 | `YanoDevnetService` calls Yano's devnet API: fund (`/api/v1/devnet/fund`), snapshots (create, restore, list, delete), rollback (by slot, block or count), time advance (slots, seconds, epochs), epoch shift and catch-up, transaction evaluation, and genesis download | `nodeint/yano/YanoDevnetService.java:35-151` |
| F7 | DevKit lifecycle (`DevKitLifecycleService`, a light `@Service`) runs the downloaded DevKit's `devkit.sh up`; its health check is `GET /api/v1/health` on `http://localhost:8080`. IntelliAda has **no** client for DevKit's admin API (`grep` finds no `topup` or `/local-cluster` call) | `nodeint/devkit/DevKitLifecycleService.java:17-25`; `nodeint/devkit/DevKitProcessManager.java:45-46`, `:192`, `:206-230` |
| F8 | Constructing a `YaciBaseService` auto-starts a `LocalYaciDevKit` or `Yano` target node | `nodeint/service/impl/yaciprovider/YaciBaseService.java:74-81` |
| F9 | Accounts (`CardanoAccount`: name, address, **mnemonic**, balance) live in `~/.intelliada/.cardano.account.conf`, encrypted with a key kept beside it in `.cardano.account.conf.key`, and written in plain text when no key can be made. An account carries no network tag; the chooser filters by network from the address | `account/model/CardanoAccount.java:9-14`; `account/service/AccountCacheService.java:129-152`; `account/cache/GlobalCache.java:37`, `:54-75`, `:218-224`; `account/service/AccountChooser.java:54-55` |
| F10 | LSP4IJ 0.10.0 maps files to a server by `languageMapping`, `fileTypeMapping` or `fileNamePatternMapping`. Its guide: defining a file type for a pattern "will override and disable the syntax coloration with TextMate"; `fileNamePatternMapping` keeps it. Semantic tokens are supported (marked experimental) through IntelliJ's semantic highlighting, with a `semanticTokensColorsProvider` extension point | LSP4IJ `docs/DeveloperGuide.md` at 0.10.0, lines 268-284, 386-404, 728-761; `docs/LSPSupport.md` line 63, 584-596; the jar's `plugin.xml` extension points |
| F11 | LSP4IJ 0.10.0's client capabilities declare `workspace.applyEdit`, `workspaceEdit.documentChanges` and resource operations, and **no `failureHandling`** (`ClientCapabilitiesFactory` never calls `setFailureHandling`). Its default `workspace/applyEdit` handler applies the edit through `LSPIJUtils.applyWorkspaceEdit` and **always answers `applied: true`**, without reading any document version. `LanguageClientImpl.applyEdit` is public (overridable); `LSPClientFeatures.initializeParams(InitializeParams)` runs after LSP4IJ fills the parameters (so a plugin *could* add capabilities); `StreamConnectionProvider.getInitializationOptions(VirtualFile)` supplies `initializationOptions`; LSP4IJ sends `workspaceFolders` from `LSPIJUtils.toWorkspaceFolders(project)`; `LanguageServerManager` can start, stop and query a project's servers | `javap -c` on `internal/ClientCapabilitiesFactory`, `client/LanguageClientImpl` (`lambda$applyEdit$1`: `iconst_1`), `LSPIJUtils`, `LanguageServerWrapper`, `LanguageServerManager`, `client/features/LSPClientFeatures` |
| F12 | IntelliJ's bundled TextMate plugin (`org.jetbrains.plugins.textmate`) declares `com.intellij.textmate.bundleProvider` (`TextMateBundleProvider.getBundles()` → `PluginBundle(name, path)`), and reads VS Code extension layouts (`VSCBundleReader`); its own bundled grammars are in that layout (`package.json`, `language-configuration.json`, `syntaxes/`). LSP4IJ registers its folding, structure view and other features for the `textmate` language too | IntelliJ 2025.2.4 `plugins/textmate/lib/textmate.jar` (`META-INF/plugin.xml`, `org/jetbrains/plugins/textmate/api/`, `bundles/VSCBundleReader`); `plugins/textmate/lib/bundles/go/`; LSP4IJ jar `plugin.xml:177`, `:232`, `:251` |
| F13 | IntelliJ's trust API: `com.intellij.ide.trustedProjects.TrustedProjects.isProjectTrusted(Project)`, `isTrustedCheckDisabled()`, and `TrustedProjectsListener.TOPIC` (`onProjectTrusted`, `onProjectUntrusted`). `GeneratedSourcesFilter` and `ExternalSystemUtil` exist; IntelliJ IDEA bundles the `gradle` plugin | IntelliJ 2025.2.4 `lib/app-client.jar` (`javap`); `plugins/gradle` |
| F14 | `joust-lsp` (`@julclang/joust`, `bin/joust-lsp.js`) takes `--stdio`, `--project-engine`, `--enable-edits`, `--engine-dir` and `--storage <dir>` (default `~/.cache/joust-lsp`). Without `--project-engine` it is **advisory**: it analyses with its own bundled compiler and runs no project code. The published package is `0.1.0-pre2` and needs Node ≥ 22 | JOUST `js/joust/bin/joust-lsp.js:1-38`; ADR-0021 D3, D3b; `npm view @julclang/joust` (2026-11-18) |
| F15 | The server decides edits from the client: `isVscode = io.client === 'vscode-joust'` (an `initializationOptions` claim); `trusted = isVscode ? io.trusted : flags.projectEngine`; `edits = (isVscode || flags.enableEdits) && applyEdit && documentChanges && failureHandling ∈ {transactional, textOnlyTransactional}`. Completion, code actions, `executeCommand` and formatting are advertised only with `edits`, and `textDocument/rename` only to non-VS Code clients with edits. Without them the server offers sync, diagnostics, symbols, folding, selection ranges, hover, definition, references and semantic tokens. The `joust/trust` notification is ignored for non-VS Code clients; `joust/status`, `joust/generatedJava`, `joust/checkBuild` and `joust/restartAnalysis` answer any client, and `joust/rename` refuses without `edits` | JOUST `js/joust/src/lsp/server.js:115-138`, `:242-248`, `:286`, `:1608-1611`, `:1690`, `:2492-2512`, `:2526-2527` |
| F16 | Semantic token types: `namespace, type, enum, enumMember, property, parameter, variable, function, method, class, keyword`; modifiers: `declaration, readonly, defaultLibrary, timeValue`. No `string`, `comment` or `number` type | JOUST `js/joust/src/lsp/features.js:9-11` |
| F17 | The VS Code extension sends `client: 'vscode-joust'` and `trusted`, and installs its own `workspace/applyEdit` handler: only versioned `TextDocumentEdit`s of open documents, each carrying the SHA-256 of the text it was checked on (`joustExpected`), all checked synchronously before `workspace.applyEdit`, else the whole edit is refused. Its TextMate grammar is `syntaxes/jsc.tmLanguage.json` (scope `source.jsc`) with `language-configuration.json`, MIT-licensed like IntelliAda | JOUST `js/vscode-joust/src/extension.js:73-102`; `src/apply-edit.js:1-60`; `package.json`; `LICENSE` |
| F18 | Other editors start a `joust-lsp` the user installed, by absolute path, on an absolute `node`, and never one inside the project, `node_modules/.bin` or `npx`; the reason is that they have no workspace trust, so a repository must never choose the program that runs. Their editing features are off because the server offers an edit only to a client known to reject one when the text changed after it was computed | JOUST `www/src/content/docs/languages/editors.mdx` ("Other editors: a server you installed", the Neovim and Helix sections); ADR-0021 D3b, D4a, §16 E4 |
| F19 | `org.julclang.joust` registers, when `src/main/jsc` exists: `generateJsc` (lowers `.jsc` into `build/generated/sources/jsc/java/main`, added to the main source set, with `Name.jscmap.json` source and message maps beside each generated class, also copied as resources), `acceptJscAbi` (`--contract C --committed-sha256 h1 --sha256 h2`, or bare to accept everything), `jscAbiDiff --contract C [--out f]`, `acceptJoustLock`, `checkJscAbi` (against `build/classes/java/main/META-INF/joust`, part of `check`), `checkJoustLibraries`, `recordJoustLibraries` and `remapJscDiagnostics`. The plugin adds `julc-state-runtime`, the Blockfrost backend, the processors and the testkit, but **not `julc-state-server`**, and sets a Java 25 toolchain | JOUST `julc-state-gradle-plugin/src/main/java/org/julclang/state/gradle/JoustPlugin.java:54-103`, `:105-292`; `AcceptJscAbi.java:21-40`; `JscAbiDiff.java:17-22`; `julc-state-dsl/.../JscMain.java:883-886` |
| F20 | The processor writes each contract's **description** to `META-INF/joust/<package>.<Contract>.json`: a CIP-57-style blueprint plus `x-joust` (version 2) with `state`, `creations`, `actions`, `views`, `owned`, `keyed` (key field, tags, cooldowns, `inserts/actions/removes/retires`), per-call `args` with schemas, `payable`, `takesAssets`, `policies` (`signedBy`, `validAfter`, `validBefore`, `quorum`, `authorizedBy`, …), `needsService`, and `encoding.json`, the JSON vocabulary for values (integer as decimal string, bytes as hex, `$variant` for sum types, Bool, Optional as null, maps as key-ordered pairs, Data as CBOR hex). Only the npm toolchain writes `build/joust/contracts.json` | JOUST `julc-state-processor/.../JoustProcessor.java:107-109`; `js/state-client/test/fixtures/registry.description.json`; `js/joust/src/build.js:163` |
| F21 | `julc-state-server`'s `JoustServer` is a **library** (a builder; examples wire it, e.g. `examples/dapp/.../DappServer.java:81`), an HTTP service for browsers (CORS allow-list, rate limits, size limit). `prepare(JsonNode)` is public: `{contract, kind, method, instance, object, args, pay, address}` → `{cbor, txHash, validTo, signers, plan, instance?, object?}`, for a `WalletAccount` (address only, fails closed). It holds no keys, refuses co-signed calls and a keyed contract's creation, and serves views only over HTTP (`view` is private). It has no instance listing, history or events | JOUST `julc-state-server/.../JoustServer.java:32-46`, `:76-152`, `:173-235`, `:256-295`, `:297-305`; `julc-state-runtime/.../WalletAccount.java:15-22` |
| F22 | `PreparedTx.withWitnesses(...)` merges CIP-30-style vkey witness sets into the prepared transaction, each checked to be a valid signature of `txHash` | JOUST `julc-state-runtime/.../PreparedTx.java:76-100` |
| F23 | `@julclang/joust-runtime`'s `engineService({ api, contracts, clock, … }).prepare(request)` takes the same request shape and returns the same `Prepared`, from `build/joust/contracts.json`; it can also `publish` reference scripts. `joust dev` attaches to the network named in `joust.config.json` (`networks`), may **spawn that network's `start` command through a shell**, and serves a prepare service on `127.0.0.1` | JOUST `js/joust-runtime/src/index.d.ts:11-54`; `js/joust/src/dev.js:1-17`, `:51-60`, `:198` |
| F24 | A `.jsc` refusal names its sites through the message map: a generated check traces a key, and the JVM runtime appends the `.jsc` sites, e.g. `#pay:spend2.1 [Vault.jsc:9:17 (a payout in split is negative)]` | JOUST `julc-state-runtime/.../JscMessages.java:14-22` |
| F25 | JOUST's DevKit flows use Yaci Store on `:8080` and the admin API on `:10000`: the clock from `/local-cluster/api/admin/devnet`, top-up at `/local-cluster/api/addresses/topup`. DevKit's default mnemonic funds its first 20 addresses in genesis. The `joust new` template's DevKit runtime uses a 20 s TTL ("DevKit's time horizon is minutes"). The runtime's MerkleMap mirror defaults to RocksDB at `./.joust/mpf` unless `-Djoust.mapStore=memory` | JOUST `examples/devkit/.../DevKit.java:30-31`, `:146-183`; `julc-state-cli/.../templates/gradle/Devnet.java`; `julc-state-runtime/.../DefaultMapStore.java:13-23` |
| F26 | `fixtures/m0` holds JOUST's oracle: each DevKit transaction **unsigned** (`unsignedTx`) with its `txHash` and the chain reads behind it, 1,556 files across the examples | JOUST `fixtures/m0/bank/0004-Bank.deposit.json`; `git ls-tree -r origin/main fixtures/m0 \| wc -l` |
| F27 | The native `joust` decides `new`, `version` and `help` without running project code; `joust new <dir> [-t gradle\|node\|web] [-l java\|jsc] [-p <package>]` writes a Gradle project from its own templates, and an npm one through `npm` and `node` from `PATH` | JOUST `julc-state-cli/.../JoustMain.java:16-36`, `:145-193` |
| F28 | JOUST's processor lint (`SubsetLint`) rejects fixed-width numbers and `MerkleMap.reads()/writes()/root()`; it does **not** lint `byte[]` compared with `==` or `.equals`, which JOUST's `CLAUDE.md` asks for ("The processor should lint for this") | JOUST `julc-state-processor/.../SubsetLint.java:35-37`, `:183-256`; JOUST `CLAUDE.md`, Gotchas |
| F29 | JOUST's events are carried in the redeemer and are authentic only under ADR-0009 D5's provenance rule (phase-2 valid, the spending redeemer of the followed input, the expected policy, lineage); "an indexer integration that applies the rule to chain data is a follow-up". Yaci Store on DevKit serves no transaction CBOR | JOUST `docs/adr/0009-*.md:123-139`; JOUST `CLAUDE.md` (ADR-0016 M3 note) |

## 2. Threat model and trust assumptions

**Assets.** (1) The developer's machine and IDE process. (2) Every mnemonic in IntelliAda's account store, including
ones that control funds on mainnet or public testnets (F9: the store has no network tag, and a mnemonic derives the
same keys on every network). (3) Project files (sources, ABI manifests, `joust.lock`): a wrong write can strand a
deployed instance. (4) Credentials in IntelliAda's node configuration (Blockfrost project ids). (5) The developer's
attention: a spoofed dialog or link.

**Untrusted.** The contents of every project IntelliAda opens: `.jsc` and `.java` sources, build scripts, the Gradle
wrapper and its `distributionUrl`, `package.json`, `node_modules`, `joust.config.json` (including `networks` and their
`start` commands, F23), `.idea/` files committed to the repository, build outputs (descriptions, message maps, ABI
diffs), and anything a project process prints. A repository may be a hostile clone. **Also untrusted:** a devnet
endpoint's answers (they decide what a transaction spends).

**Trusted.** IntelliAda and the IDE; the user's application-level settings; a Node binary and a `joust-lsp` the user
installed and named by absolute path (as in editors.mdx, F18); the IDE's own environment; and, **once IntelliJ marks the
project trusted**, the project's own build and runtime, run on the user's explicit request (the same code
`./gradlew build` or `npm run` would run).

**Trust boundaries.** IDE JVM ↔ `joust-lsp` (a Node child, stdio) ↔ the project's dev host (a JVM or Node child,
stdio; D11) ↔ the devnet node (HTTP) ↔ the project's files.

**What IntelliAda must never do:**
- **T1.** Run project-supplied code when a project is opened, indexed, synced or changed on disk: no Gradle task, no
  wrapper, no `npm`, no `node_modules` code, no dev host. The only process that may start without a click is the
  user-installed `joust-lsp`, when a `.jsc` file is opened, and it runs no project code in advisory mode (F14).
- **T2.** Run project-supplied code in a project IntelliJ does not trust, even on request.
- **T3.** Let a project choose an executable, an argument, an endpoint or a key: those come from application-level
  settings only.
- **T4.** Let a mnemonic or private key leave the IDE process, or sign a transaction it has not checked itself.
- **T5.** Talk to mainnet from the run panel at all (D14).
- **T6.** Render project-supplied text as HTML, or follow a link from it to anything but a file inside the project.
- **T7.** Apply an edit from the language server in M1–M3 (D6).
- **T8.** Write to project files except through a command the user invoked (a Gradle task, the wizard, an accepted ABI).

**Out of scope.** A compromised IDE, Node or JDK; a malicious user-installed `joust-lsp`; a malicious devnet the user
started themselves (it can lie about state, but it holds only devnet value and D13's checks keep it from obtaining a
signature for anything else); side channels.

## 3. Decision

### Part A: `.jsc` language support

### D1. One rule for running project code

IntelliAda runs project-supplied code only when **all** of these hold: the project is trusted
(`TrustedProjects.isProjectTrusted(project)`, F13), the user invoked an action that says what it runs, and the code is
the project's own toolchain (its Gradle build through IntelliJ's Gradle integration, a process started from a launch
spec that build produced (D11), or its own `@julclang/joust` package). Never on open, sync, indexing or a file event (T1).

- **Trust granted or revoked mid-session** (`TrustedProjectsListener.TOPIC`): IntelliAda restarts the language server
  with the matching flags (D5) and stops any dev host (D11) on revocation.
- **Trust check disabled** (`TrustedProjects.isTrustedCheckDisabled()`): IntelliAda asks once per project before the
  first action that runs project code, and records the answer in application-level state, never in the project.
- **Existing gap:** `JulcCompileService` runs `./gradlew` with no trust check (F3). This ADR does not change julc, but
  every JOUST action follows D1, and R2 asks for julc to follow it too.

### D2. File type and highlighting: a TextMate bundle plus the server's semantic tokens

- **No IntelliAda `LanguageFileType`, lexer or parser for `.jsc`.** `.jsc` is highlighted by JOUST's own TextMate
  grammar, shipped in IntelliAda as a bundle in VS Code extension layout: `syntaxes/jsc.tmLanguage.json` and
  `language-configuration.json` copied verbatim from `js/vscode-joust/` at a pinned JOUST release tag, with a minimal
  `package.json` holding only that extension's `contributes.languages` and `contributes.grammars` entries, and registered through `com.intellij.textmate.bundleProvider`
  (F12) under an **optional** dependency on `org.jetbrains.plugins.textmate`. The grammar is data, not code; both
  projects are MIT (F17).
- **The server is mapped with `fileNamePatternMapping patterns="*.jsc"`**, because LSP4IJ documents that a file type for
  the pattern disables TextMate coloring (F10).
- **Semantic tokens** from the server colour what the grammar cannot know (state field vs local vs parameter, `std`
  functions, ADR-0018 D11's time values), through a `semanticTokensColorsProvider` that maps JOUST's token types and modifiers
  (F16), including `timeValue`, to IntelliAda text attribute keys (so themes can style them).
- The bundle's commenter, brackets and auto-closing pairs come from `language-configuration.json`.
- A **grammar sync test** records the SHA-256 of each bundled file and the JOUST tag it came from, and fails when they
  differ; updating the bundle is part of moving IntelliAda to a new JOUST release (I6).

**Alternatives considered:**
- *(b) An IntelliAda language with a JFlex lexer, as for Aiken.* Rejected: a second lexer of ADR-0018's grammar drifts
  from JOUST's (the Aiken grammar is already "intentionally incomplete" and needs `ParseErrorHighlightFilter`), and a
  file type would turn TextMate off.
- *(c) A file type with semantic tokens only.* Rejected: JOUST's token types have no string, comment or number (F16), so
  those stay uncoloured, and nothing is coloured until a server is configured.
- *(d) tree-sitter.* JOUST ships `tree-sitter-jsc`, but IntelliJ has no tree-sitter host.

**Consequence:** `.jsc` files have no IntelliAda PSI. Features that need positions (gutter icons, D15) take them from
the server's document symbols, not from a PSI tree. If the TextMate plugin is absent, `.jsc` files are plain text and
still get the server's diagnostics, hover and navigation.

### D3. The language server definition

- An LSP4IJ `server` (illustrative id `joustLanguageServer`) with a `fileNamePatternMapping` for `*.jsc` and
  `languageId="jsc"`.
- Its connection provider extends `ProcessStreamConnectionProvider` (as Aiken's, F2) with the command
  `[<node>, <joust-lsp.js>, "--stdio", "--storage", <dir>]`, plus `--project-engine` only under D5. Both paths are
  absolute and come from D4.
  - **Never `--enable-edits`** (D6), and never an argument from project files.
  - `--storage` names a directory under the IDE's system directory (`PathManager.getSystemPath()`), outside every
    project. The server refuses a storage directory inside a workspace folder anyway (`server.js:264-266`).
  - Working directory: the directory of the real `joust-lsp.js` (outside the project), not the project base path as for
    Aiken.
  - Environment: the IDE's own; IntelliAda adds nothing from the project.
- `initializationOptions` (through `getInitializationOptions`, F11): `{ "client": "intelliada" }` and nothing that
  claims another client. **IntelliAda never sends `client: "vscode-joust"`** (F15): that value tells the server the
  client applies edits through the VS Code extension's verified handler (F17), which IntelliAda does not have (I5).
- Its `LanguageClientImpl` subclass **refuses every `workspace/applyEdit`** (`applied: false`, with a reason) in
  M1–M3, overriding LSP4IJ's default, which applies without versions and reports success (F11). The JOUST server sends
  none in read-only mode (F15); the override is defence in depth.
- Its `LSPClientFeatures` adds **no capability** to LSP4IJ's (in particular no `failureHandling`, F11).
- **Enablement** (`LanguageServerEnablementSupport`, as Aiken): the server is enabled only when D4's paths are set and
  valid. Otherwise opening a `.jsc` shows one notification with **Configure JOUST…** and starts nothing.
- **Commands** (M1): **Restart analysis** (`joust/restartAnalysis`), **Show compiler and mode** (`joust/status`: the
  compiler version, `bundled` or `project`, `advisory` or `project-matched`, which a status bar widget also shows),
  **Show generated Java** (`joust/generatedJava`, opened as a read-only light virtual file), and **Check build (in
  memory)** (`joust/checkBuild`, the server's capped in-memory build). All of them answer any client (F15) and write
  nothing.
- Lifecycle: LSP4IJ starts the server when a mapped file opens and stops it with the project.

### D4. Locating Node and `joust-lsp`: application settings, absolute paths, never the project

- Two **application-level** settings (a `PersistentStateComponent` with application storage, like `AikenSDKState`):
  the Node binary and `joust-lsp`. No project-level or `.idea/` setting can name an executable, a path or an argument
  (I7), for editors.mdx's reason: the repository must not choose what runs (F18).
- **Validation**, when the setting is saved and again at every server start, on real paths:
  - both paths absolute; no bare name, no relative path, no `npx`;
  - `joust-lsp` resolved through symlinks to its real file (`<prefix>/lib/node_modules/@julclang/joust/bin/joust-lsp.js`
    on Unix, where `<prefix>/bin/joust-lsp` is a link), so the server runs on the configured `node` and not on the
    `#!/usr/bin/env node` shebang's `PATH` lookup;
  - neither real path inside any open project's base directory or content roots (comparison by path segment, never by
    string prefix), nor under a `node_modules/.bin` directory;
  - Node's version from `<node> --version` (the user's binary, by absolute path) is at least 22 (F14);
  - `joust-lsp`'s version is read **as text** from the `package.json` beside its package, never by running it.
- **Detect…** offers candidates without running anything from a project: absolute `PATH` entries outside open
  projects, and the usual global prefixes (nvm, Volta, asdf, Homebrew, `npm prefix -g` run from the user's home). The
  user confirms one; detection never sets a path silently.
- IntelliAda **does not bundle** Node or `joust-lsp` (Q1).

### D5. Analysis mode: advisory by default, the project's compiler only in trusted projects

- **Untrusted project:** the server starts **without** `--project-engine`. It analyses with the compiler bundled in the
  user's `joust-lsp`, says "advisory" (`joust/status`), and runs no project code (F14, F15).
- **Trusted project, setting on:** IntelliAda adds `--project-engine`. The server then loads only the project's two
  engine files, confined to a workspace folder, as ADR-0021 D3b defines; the editors.mdx warning ("it runs that engine's
  JavaScript for every repository you open") does not apply, because IntelliAda passes the flag per project and only
  for trusted ones. The application-level setting **Analyse trusted projects with their own JOUST compiler** defaults
  to on (Q8).
- **Trust changes** restart the project's server through `LanguageServerManager` (F11), since the server ignores
  `joust/trust` from clients other than VS Code (F15) (I4).
- **Gradle projects** are always advisory in the server (ADR-0021 D3: it reads the plugin version as text and does not
  run Gradle). IntelliAda shows the project's `org.julclang.joust` version next to the server's compiler version when
  they differ.

### D6. Editing: read-only first; parity is an option (M4) that needs an ADR-0021 amendment

**Decision for M1–M3: read-only, as in Neovim and Helix.** IntelliAda offers diagnostics, hover, go to definition, find
references, document symbols (Structure view), folding, selection ranges and semantic tokens, plus D3's read-only
commands. It offers **no** completion, quick fixes, rename or formatting from the server. This holds by three
independent means: IntelliAda never passes `--enable-edits`; it never claims `vscode-joust`; and LSP4IJ 0.10.0 declares
no `failureHandling`, which the server requires (F11, F15). The client additionally refuses any `workspace/applyEdit`
(D3). Formatting is available as the project's own `joust fmt` in a trusted project (D7), a user command that writes the
file on disk, not a server edit.

**Why:** the server's edits are safe only through a client that rejects an edit whose target text changed after the
server checked it (ADR-0021 D4a). LSP4IJ's default handler applies a `workspace/applyEdit` without reading versions and
reports `applied: true` (F11), the failure mode ADR-0021 D4a was written to exclude.

**Option for parity (M4), recorded for the maintainers' decision (Q2).** IntelliAda would have to build:
1. **A verified `workspace/applyEdit`**, replacing LSP4IJ's: accept only `documentChanges` of versioned
   `TextDocumentEdit`s (no `changes` map, no file operations); require `joustExpected` (the SHA-256 of each target's
   checked text, F17); in one write action on the EDT, check every target is an open document at exactly that LSP
   version and digest, and refuse the **whole** edit otherwise; apply all of it as one undoable command. This needs
   LSP4IJ's per-document version, which 0.10.0 keeps package-private (`DocumentContentSynchronizer.getVersion()`,
   by `javap`), so a public API or an upstream change is an M4 entry gate.
2. **JOUST: Rename Symbol** through `joust/rename` and `joust/prepareRename` (not LSP4IJ's `textDocument/rename`
   handler, whose `LSPRenameRefactoringDialog` applies the result through `LSPIJUtils.applyWorkspaceEdit`, the
   unverified path, by `javap`), with the server's confirmations (`needsConfirmation` for a name
   the ABI manifest records, `scriptChange` for a keyed type), its `needsOpen` list (opened as versioned documents
   first), and confinement to the project's contracts directory.
3. **Quick fixes** only on explicit invocation (an intention the user picks), carrying the server's opaque, single-use
   fix id through `joust.applyFix`; never applied on save or by "optimise imports".
4. **Completion** without `additionalTextEdits` (imports through `joust.addImport`), and **formatting** only if IntelliJ's
   LSP formatting path rejects a result computed for older text (to be verified for LSP4IJ, ADR-0021 D4a's "other
   clients" rule).
5. **Remove invisible characters…** as a modal listing every occurrence before changing anything.
6. **Trust** as D5.

**The ADR-0021 amendment JOUST would need:** today edits depend on a client *name* (`vscode-joust`) or on
`--enable-edits` (F15). The amendment would (a) define a capability a client declares to say it implements ADR-0021 D4a's
verified application (illustrative: `experimental.joust.verifiedEdits: 1`), instead of trusting a name; (b) list
IntelliAda as a tested client once its scenarios pass (ADR-0021 D4a: "a client is listed only after a test shows it
rejects a result when the text changed after the response was sent"); (c) give such clients `joust/rename` rather than
`textDocument/rename`; and (d) let such a client pass `trusted` (from IntelliJ's trust) instead of `--project-engine`,
with `joust/trust` honoured for it.

### Part B: JOUST projects

### D7. Detection, generated sources and task actions

- **Detection by reading files as text, never by running a build:** a Gradle JOUST project names the plugin
  `org.julclang.joust` in `build.gradle(.kts)` or `settings.gradle(.kts)` (IntelliAda reads the version literal for
  display); an npm JOUST project has `joust.config.json` and depends on `@julclang/joust` in `package.json`. A new
  project service (not `JulcTomlService`, F4) holds the result. From `joust.config.json` IntelliAda reads only
  `contracts` (ADR-0021 D4b's rule), never `networks`, URLs or `out`.
- **Generated sources:** `build/generated/sources/jsc/java/main` is already in the main source set (F19), so IntelliJ's
  Gradle import treats it as a source root. IntelliAda marks files there as generated (`GeneratedSourcesFilter`, F13),
  shows a banner "Generated from `Counter.jsc` by `generateJsc`: edit the `.jsc`", and navigates from a generated line
  to the `.jsc` position through the `Name.jscmap.json` beside it (F19).
- **Task actions** (a "JOUST" group in the editor, project view and Gradle tool window context menus): Build,
  **Generate from .jsc** (`generateJsc`), **Check ABI** (`checkJscAbi`), **Review ABI change…** (D8), **Accept
  `joust.lock`** (`acceptJoustLock`, after a confirmation naming the file), and Run tests. They run through IntelliJ's
  Gradle integration (`ExternalSystemUtil`, an optional dependency on `com.intellij.gradle`) so the project's Gradle
  JVM and wrapper apply, only under D1. In IDEs without the Gradle plugin, the Gradle actions are hidden. For npm
  projects the same actions run the project's own CLI (`<node> node_modules/@julclang/joust/bin/joust.js build`, and
  `joust fmt <file>`), only under D1, with the package's real path inside the project.
- **Diagnostics in consoles:** build output lines in JOUST's formats (`org/acme/File.jsc:12:5: error[JSC0502]: …` from
  `remapJscDiagnostics`, and the npm form `error[JSC0502]: app/File.jsc:12:5: …`) become hyperlinks, resolved under the
  project's contracts directory only (I18).

### D8. The ABI-change experience

A `.jsc` change that changes the datum or a redeemer fails `generateJsc` with JSC0002 or JSC0003, by design (ADR-0018
D12). IntelliAda follows ADR-0021 D7a exactly:

1. On that failure IntelliAda shows a notification naming the contract, with **Review ABI change…**.
2. Review runs `jscAbiDiff --contract <C> --out <file>` (the tool that will accept), with `<file>` in the IDE's temp
   directory, and shows the committed and the new manifest in IntelliJ's diff viewer, with the SHA-256 of each (`none`
   when nothing is committed).
3. **Accept this change** opens a modal naming the one contract and its change, then runs
   `acceptJscAbi --contract <C> --committed-sha256 <h1> --sha256 <h2>`. The UI never runs the bare `acceptJscAbi`,
   which accepts every manifest (I8). If the baseline or the source changed since the review, the task refuses, and
   IntelliAda shows that refusal and offers to review again.
4. Afterwards IntelliAda reminds the user to review and commit `<Contract>.abi.json`.

npm projects use `joust abi diff <C> --out <file>` and `joust build --accept-abi=<C>@<h1>..<h2>` (ADR-0021 D7a's names).
The exact `--out` JSON is pinned with the JOUST maintainers before M2 (an entry gate).

### D9. New-project wizard

A **JOUST** project generator (a `moduleBuilder` in IntelliJ IDEA, like `JulcModuleBuilder`) asks for the language
(`.jsc` or Java), the template (Gradle, Node or web) and the package, then runs the user's native `joust` (an absolute
path in application settings, validated as in D4) as `joust new <empty dir> -t <template> -l <lang> -p <package>`.
`joust new` runs no project code (F27), and the directory is new. The Node and web templates need `npm` and network
access (F27), and the wizard says so. After it finishes, IntelliAda opens the project and links the Gradle build
(ADR-002 notes that auto-import sometimes fails). `npm create joust` without the native CLI is Q9.

### D10. Optional inspections for Java `@StateContract` contracts

Local inspections in a "JOUST" group, active only in classes annotated `org.julclang.state.api.StateContract` (resolved
by qualified name), all **advice** (the build stays the authority):

| Inspection | From | Default |
|---|---|---|
| `byte[]` compared with `==`, `!=`, `.equals`, `Arrays.equals` or `Objects.equals`: "compares references on the JVM and bytes on chain; use `ByteStringLib.equals`", with a quick fix | JOUST `CLAUDE.md` Gotchas; not linted by JOUST today (F28) | warning |
| Fixed-width numbers (`int`, `long`, `short`, `byte`, `char`, their boxes, arithmetic on them) | mirrors `SubsetLint` (F28), earlier feedback only | warning |
| `throw` in contract code: "use `Require.that(cond, "msg")`" | JOUST `CLAUDE.md` ("no `throw`", "Use `Require.that`") | warning |
| `MerkleMap.reads()`, `writes()`, `root()`, or constructing a `MerkleMap`/`MapEntry` | `SubsetLint`; JOUST `CLAUDE.md` | error |
| `JulcMap.getOrDefault`; `IntervalLib.finite*Bound` compared without a `Finite` check | JOUST `CLAUDE.md` | weak warning |

IntelliAda does **not** run its bundled julc `SubsetValidator` (F4) on `@StateContract` files: the bundled julc is not
the project's (D12). The existing julc annotators already skip them (F4); M2 keeps it that way with a test.

### Part C: DevKit and Yano, from the contract's code

### D11. Where off-chain execution runs: a dev host from the project's own JOUST, in its own process

The four candidates:

| | Project's JVM: generated client and `StateRuntime`, separate process | `julc-state-server` prepare service | JS engine (`@julclang/joust-runtime`, `joust dev/serve`) with Node | IntelliAda's in-IDE CCL |
|---|---|---|---|---|
| Runs the project's JOUST, JuLC and CCL | yes | yes (if run from the project) | yes (npm projects) | **no**: IntelliAda's CCL 0.6.2 and julc (F1, F4) |
| Same off-chain code the project ships (JVM `Require`, ADR-0018 D12 sites) | yes | yes | yes (described contracts) | no: a second implementation of JOUST's transaction building |
| Gradle projects | yes | yes | **no**: needs `build/joust/contracts.json`, which only `joust build` writes (F20) | n/a |
| npm projects | no | no | yes | n/a |
| Keys stay in IntelliAda | yes, with `WalletAccount` and witness sets (F21, F22) | yes (holds no keys) | yes | yes |
| Isolated from the IDE JVM | yes | yes | yes | **no** |
| Exists today as a runnable entry | **no** generic entry point | **no** main; a browser-facing HTTP library; refuses keyed creation and co-signed calls; no listing, history or events (F21) | `joust dev` exists, but takes its network from `joust.config.json` and may spawn its `start` command (F23) | yes |

**Decision: a "dev host": the project's own JOUST runtime in a separate process, speaking a small JSON-RPC protocol on
stdio, whose call vocabulary is the prepare service's.** IntelliAda is the wallet and the submitter, never the
transaction builder.

- **Gradle projects (M3):** the host runs on the project's runtime class path and Java toolchain (25, F19), with the
  generated clients and descriptions on that class path. Internally it is `JoustServer.prepare` and `ServedContract`
  (F21) without HTTP, plus what the IDE needs that the service lacks (below).
- **npm projects (M3b):** the same protocol over the project's `@julclang/joust-runtime` `engineService` (F23).
- **Rejected:** in-IDE CCL (a second implementation in another CCL and JuLC version: wrong scripts, wrong addresses,
  and the project's code would not run). `JoustServer` over HTTP as is (no entry point; a browser service with CORS and
  rate limits; and an unauthenticated local port where stdio needs none). `joust dev` (it chooses the network from
  project files, against T3). Loading JOUST into the IDE JVM as ADR-003 does for julc (the project's version, its
  generated classes, RocksDB natives and Java 25 bytecode do not belong in the IDE).
- **The protocol (illustrative; JOUST's own ADR decides, Q4):**
  - `hello` → JOUST, JuLC and CCL versions, protocol version, contracts;
  - `describe(contract)` → the description and message map;
  - `prepare(request)` → `Prepared` (F21's request, plus `designate` for keyed calls and, if Q5 is accepted,
    `cosigners`);
  - `view(contract, instance, view | null, args)` → the state or a view's value, in the description's JSON encoding;
  - `instances(contract)` and `objects(contract, instance)` → UTxO refs and decoded datums;
  - `decode(contract, datumCbor)` → JSON (for history IntelliAda reads from the node);
  - `publish(contract, address)` → an unsigned reference-script transaction;
  - `reset()` → re-read the clock, drop map mirrors;
  - errors as `{ kind: refusal | bad | internal, message, sites[], slot? }`, carrying `JoustException`'s fields (a
    keyed cooldown's slot) and ADR-0018 D12's message-map sites (F24).
- **Launch (Gradle, illustrative):** IntelliAda asks the project's Gradle, through IntelliJ's Gradle integration, for a
  launch spec (a JOUST plugin task that prints the toolchain's `java`, the runtime class path and the host's main
  class), then starts `java -Djoust.mapStore=memory -cp … <main> --stdio` with `GeneralCommandLine`. The class path is
  the project's resolution; restarts reuse the spec until build files change. `joust.mapStore=memory` keeps the host
  from writing `./.joust/mpf` into the project and lets a restart rebuild mirrors after a rollback (F25).
- **The host gets from IntelliAda:** the devnet's chain endpoint and admin endpoint (D14), the network id, and, per
  call, the caller's **address**. Nothing else: no key, no Blockfrost id (D14 refuses public networks), nothing from
  `joust.config.json`.
- **Lifecycle:** started on the first run action in a trusted project (D1), one per project, stopped on project close,
  trust revocation, a build-file change, or **Stop** in the run panel (I20). Its stderr goes to a "JOUST host" console;
  stdout carries only the protocol.

**Entry dependency:** the host needs a JOUST-side entry point that does not exist today (F19: the plugin does not even
add `julc-state-server`). M3 cannot start until JOUST accepts and releases it (Q4). IntelliAda does not ship a launcher
class into the project's JVM as a stop-gap: it would be IntelliAda code compiled against one JOUST API, run inside every
project's version.

### D12. Which JOUST runs: the project's, never IntelliAda's

- The host and every task are the project's: the Gradle plugin version pins the runtime, processor, JuLC and CCL
  (F19); in npm projects, `package.json` and the lockfile pin `@julclang/joust` and `@julclang/joust-runtime`. A
  script's hash depends on the JuLC version that compiled it, so a bundled JOUST would deploy a different script at a
  different address.
- IntelliAda bundles no JOUST or JuLC runtime for this feature (I9). It shows the host's versions (`hello`), refuses a
  host whose protocol version it does not support, and before **Deploy** offers to run the build when any contract
  source is newer than its description.
- The language server's compiler (D5) may differ from the project's; the run panel never uses the language server.

### D13. Accounts, keys and signing

- **Devnet-eligible accounts only.** The caller list shows (a) accounts IntelliAda creates for devnets (a new
  **Create devnet account**, fresh mnemonic, flagged `devnetOnly`; never an imported mnemonic), and (b) the devnet's
  public genesis accounts (DevKit's default mnemonic, F25). Imported accounts and accounts created for other networks
  are not offered, because the same mnemonic controls funds on every network (F9) (I12). Where the flag is stored is
  Q12.
- **Keys stay in the IDE process (I10).** The host receives the caller's address; it builds for a `WalletAccount`
  (address only, fails closed, F21). No mnemonic or private key appears in any child's arguments, environment, stdin,
  files or logs.
- **IntelliAda checks before it signs (I11, I13):**
  1. it parses the transaction and computes the body hash from the **body bytes as received**; this must equal
     `Prepared.txHash`;
  2. every input, reference input and collateral input resolves on the selected devnet, through **IntelliAda's own**
     node client (not the host's reads): a transaction for another network cannot pass;
  3. the body's network id, if present, is testnet, and every output address has network id 0;
  4. the body's required signers are a subset of the selected caller's payment key hash (and of co-signers the user
     picked, if Q5);
  5. the fee is below a cap (illustrative: 10 ADA), and the validity upper bound is within the devnet's horizon (JOUST's
     DevKit runtimes use a 20 s TTL, F25).
- **Signing** produces a vkey witness set over the body hash only; IntelliAda never re-encodes the body. IntelliAda
  submits through its own node service and checks that the submitted id equals `txHash`. (Merging the witness set
  without re-encoding the body is IntelliAda's; `PreparedTx.withWitnesses` (F22) is the host-side alternative.)
- **Confirmation:** on a devnet with devnet-only accounts, no modal per transaction by default; the result pane shows
  the caller's net outflow, the fee and the outputs. An application setting **Confirm every transaction** turns on a
  modal.
- **Co-signed calls** (`@Quorum`, `@AuthorizedBy`, a `@SignedBy` field other than the caller) and keyed creations are
  refused by today's prepare path (F21); M3 shows them as "not available from IntelliAda yet" unless Q5 is accepted.

### D14. Networks: local devnets only; mainnet is a hard refusal

- **Eligible nodes:** IntelliAda's own node configurations of type `YaciDevKit`, `LocalYaciDevKit` or `Yano` (F5) whose
  endpoint host is loopback, or a host the user lists in an application-level allow-list (for a DevKit in a VM or
  container). Never an endpoint from the project (I14): not `joust.config.json`'s `networks` or their `start`
  commands (F23), not Gradle properties.
- **Network identity:** IntelliAda reads the network magic from the node when it connects and before each signature,
  and refuses mainnet (764824073) and, in this ADR, preprod (1) and preview (2) (I13). Which endpoint gives the magic
  on each node type (a Blockfrost-style `GET /genesis`, Yano's genesis download, DevKit's
  `/local-cluster/api/admin/devnet`) is an M3 entry gate.
- **Mainnet: a hard refusal, not an opt-in.** The run panel has no mainnet path, no setting to enable one, and the D13
  checks refuse mainnet transactions independently. Reasons: IntelliAda signs with keys it holds in a store whose key
  sits beside it (F9) and without a per-transaction modal by default; mainnet contracts should go through a hardware or
  CIP-30 wallet and JOUST's own deploy tooling; and nothing in JOUST's IDE path is tested on public networks. Changing
  this, or adding public testnets, needs a new ADR (Q3).

### D15. The run panel: from the code to a devnet

- **Entry points:**
  - **`.jsc`:** gutter icons on `init`, each action, destroy and view, placed through the editor's markup model
    (`GutterIconRenderer`) at the positions of the server's document symbols (requested through
    `LanguageServerManager`, F11), matched by name and kind to the description's `creations`, `actions` and `views`.
    No PSI is needed (D2).
  - **Java:** a `LineMarkerProvider` on methods annotated `@Init`, `@Action`, `@View`, `@Destroy`, `@OwnedInit`,
    `@OwnedAction` or `@OwnedDestroy` (`org.julclang.state.api`), resolved by qualified name.
  - A **JOUST Run** tool window listing the contracts whose descriptions are in the build output.
  - Icons are always visible but only open the panel. Nothing runs without D1.
- **Flow:** choose the devnet (start Yano or DevKit through the existing lifecycle services, on click, F6, F7); choose
  the caller (D13); fund it (Yano: `fundAddress`, F6; DevKit: a new admin client for `/local-cluster/api/addresses/topup`,
  F25); **Deploy** creates an instance (the `init` creation) and, if wanted, publishes reference scripts; then call
  actions and views.
- **Forms from the description** (F20), produced in the description's own JSON encoding (`x-joust.encoding.json`):
  bytes as hex (with pickers: an account's key hash, the caller's), integers as decimal strings, Bool recognised by its
  shape (`anyOf` of `False`/`True` constructors), Optional as "None" or a value, lists as rows, maps as key-value rows,
  sum types as a `$variant` selector with that variant's fields, `Data` as CBOR hex. `payable` adds lovelace,
  `takesAssets` asset rows. `policies` are shown (who must sign, the time window). Owned calls pick an object
  (`objects`); keyed calls show the key and the input IntelliAda designates from the caller's UTxOs, one keyed call in
  flight at a time (as pages do). **IntelliAda never encodes or decodes Plutus data for a contract** (I16): the host
  does, in the project's version.
- **Untrusted descriptions:** size cap (illustrative: 1 MiB), schema depth cap (32), `$ref` cycles refused, field count
  cap (500); every label is plain text (I15).
- **State:** the instance's state decoded by the host (`view` with no view), its UTxO (reference, value, datum CBOR),
  the **session history** (each call IntelliAda submitted: method, arguments, transaction id, state before and after as
  a diff, events the call returned, execution units), and keyed and owned objects. **Chain history** (other parties'
  calls) and **chain events** need ADR-0009 D5's provenance rule and transaction data that DevKit's Yaci Store does not
  serve (F29): not in M3 (Q6).
- **Time, faucet, snapshots, rollback** (Yano, F6): advance by slots, seconds or epochs, epoch shift and catch-up,
  named snapshots and restore, rollback, all in the run panel. After any of them IntelliAda resets the host
  (`reset`, or a restart) and re-checks each recorded instance's UTxO, marking vanished ones (I17). A keyed call
  refused with "prepare again at slot N" offers **Advance to slot N** on Yano. DevKit gets the faucet; its time,
  snapshot and rollback controls stay hidden until verified (Q7).
- **Persistence:** recorded instances per project and per devnet (keyed by the devnet's genesis hash), in IntelliAda's
  workspace-level state (not shared through VCS); no keys.

### D16. Results and errors

- **Success:** transaction id, fee, execution units per redeemer, the new state and its diff, the events the call
  returned, payouts.
- **The contract refused off-chain** (the same method on the JVM, a `RequireException`): the message with its `.jsc`
  sites from the message map (F24), each a link to the `.jsc` position (only inside the project's contracts directory,
  I18). Java contracts have no map; their message is shown as is.
- **The runtime refused** (`JoustException`: contention, a co-signer needed, a keyed cooldown with its slot): its
  fields, structured.
- **The node refused** (phase 1 or 2 at evaluation or submission): the node's text, with any trace key of the form
  `#…` matched against the message map.
- **IntelliAda refused** (D13, D14): shown as "IntelliAda did not sign: <check>", visually distinct from contract
  errors.
- Where: the run panel's result pane, with the raw exchange in the Cardano Log console (`CardanoConsole`) and the host's
  stderr in its own console. All text is plain, HTML disabled, and ANSI sequences stripped (I15).

## 4. Invariants

Each is testable; the milestone that tests it is in §6.

| # | Invariant | Decisions |
|---|---|---|
| I1 | Opening, indexing, syncing or changing a JOUST project starts no process except the user-installed `joust-lsp` (when a `.jsc` opens) | D1, D3 |
| I2 | A Gradle task, the project's CLI or a dev host runs only when the project is trusted and the user invoked an action | D1, D7, D11 |
| I3 | The server command is exactly `<node> <joust-lsp.js> --stdio --storage <IDE system dir>`, plus `--project-engine` only under D5; both paths absolute real paths outside every open project and every `node_modules/.bin`; never `npx`, never `--enable-edits` | D3, D4, D5 |
| I4 | An untrusted project's server has no `--project-engine`; a trust change restarts it with the matching flags | D5 |
| I5 | In M1–M3: `initializationOptions.client` is never `vscode-joust`; IntelliAda adds no client capability; the server advertises no completion, code action, `executeCommand`, rename or formatting; IntelliAda answers every `workspace/applyEdit` with `applied: false` | D3, D6 |
| I6 | The bundled `.jsc` grammar files equal JOUST's at the recorded tag (SHA-256) | D2 |
| I7 | Every setting that names an executable, a path, an argument or an endpoint is application-level; nothing under the project or `.idea/` sets one | D4, D9, D14 |
| I8 | The UI accepts an ABI change only in the bound form (one contract, both SHA-256 values); never bare `acceptJscAbi` or `--accept-abi` | D8 |
| I9 | IntelliAda's plugin contains no JOUST or JuLC runtime classes for the run feature; the dev host is the project's | D11, D12 |
| I10 | No mnemonic or private key reaches a child process's arguments, environment, stdin, files or logs; the host receives addresses and witness sets only | D13 |
| I11 | IntelliAda signs only a body whose hash it computed from the received bytes and which equals `txHash`; it never re-encodes the body; the submitted id equals `txHash` | D13 |
| I12 | Only devnet-eligible accounts can be selected as caller or co-signer | D13 |
| I13 | IntelliAda signs nothing unless the node is eligible (type, loopback or allow-listed host, magic not 764824073, 1 or 2), every output is a network-0 address, and every input resolves on that node through IntelliAda's own client | D13, D14 |
| I14 | Endpoints come from IntelliAda's application-level node configuration only; IntelliAda never reads `networks` or `start` from `joust.config.json` | D7, D14 |
| I15 | Project-supplied text (descriptions, messages, diagnostics, host output) is rendered as plain text (Swing HTML disabled, ANSI stripped), within size and depth caps, with `$ref` cycles refused | D15, D16 |
| I16 | IntelliAda never encodes or decodes a contract's Plutus data; forms produce the description's JSON encoding and the host converts | D15 |
| I17 | After a time control, snapshot restore or rollback, the next prepare runs on a reset host, and recorded instances are re-checked | D15 |
| I18 | Links from messages, diagnostics and consoles open only files inside the project (`.jsc` sites inside its contracts directory) | D7, D16 |
| I19 | The dev host listens on no socket: stdio only | D11 |
| I20 | Every process IntelliAda starts for a project stops when the project closes or loses trust | D1, D11 |

## 5. Consequences

- `.jsc` gets the same read-only experience as Neovim and Helix, with the TextMate grammar VS Code uses, and project
  trust that those editors lack: trusted projects get the project's compiler in the editor.
- Developers must install Node 22+ and `@julclang/joust` themselves, and point IntelliAda at them once.
- The run panel depends on a JOUST change (Q4). Until it ships, Part C cannot be built; Parts A and B do not depend on
  it.
- IntelliAda gains its first use of IntelliJ's trust API. Applying D1 to julc (R2) would be a behaviour change there.
- The TextMate route means `.jsc` has no IntelliAda PSI: no PSI-based refactoring, which read-only does not need, and
  which M4 would get from the server anyway.
- Accounts gain a devnet-only kind (D13), which touches IntelliAda's account model and UI.

## 6. Milestones

### M1: file type, highlighting, read-only language server (D1–D6)

- **Entry gates:**
  - **G1.1** Re-verify on IntelliJ 2026.1 (build 261, the branch's `sinceBuild`) the APIs checked only on 2025.2.4:
    `TextMateBundleProvider`, `TrustedProjects` and its listener, `GeneratedSourcesFilter` (F12, F13). Re-verify on the
    LSP4IJ version then pinned: `fileNamePatternMapping` keeps TextMate colouring, semantic tokens apply to TextMate
    files, `workspaceFolders` holds the project's roots, no `failureHandling` (F10, F11).
  - **G1.2** A published `@julclang/joust` with `joust-lsp` (0.1.0-pre2 has it, F14).
  - **G1.3** The IntelliJ Platform test framework added to the build (none is used today; `CLAUDE.md`, Testing).
  - **G1.4** PR #8 merged, or this branch rebased on what it becomes.
- **Exit criteria and verification:**
  - Unit tests for D4's validation (I3, I7): relative paths, bare names, `npx`, a path inside the project, a symlink
    from outside into the project and from the project out, `node_modules/.bin`, prefix look-alikes (`/u/repo` vs
    `/u/repo-evil`), Windows drive and UNC forms.
  - Platform tests (`BasePlatformTestCase` or heavier where a process is needed): a `.jsc` file gets TextMate token
    attributes; the LSP4IJ mapping selects the JOUST server; the server command matches I3 for trusted and untrusted
    projects; a trust change restarts the server (I4).
  - Integration test with the real pinned `joust-lsp` over JOUST's template `Counter.jsc` and an erroneous copy:
    diagnostics carry `JSC` codes; hover, definition, references and document symbols answer; the initialize result
    advertises none of I5's edit capabilities.
  - A fake server that sends `workspace/applyEdit` gets `applied: false` and the document is unchanged (I5).
  - Sentinel test (I1): a project with a planted `gradlew`, `node_modules/.bin/joust-lsp`, `node` and npm scripts that
    each write a sentinel file; open it, open its `.jsc`, sync; no sentinel appears.
  - Grammar sync test (I6).
  - Manual `runIde` smoke on macOS, Linux and Windows, recorded in the PR.

### M2: projects, task actions, ABI review, wizard, inspections (D7–D10)

- **Entry gates:** M1 exited; the optional `com.intellij.gradle` dependency agreed; the `jscAbiDiff --out` and
  `joust abi diff --out` JSON shape pinned with JOUST; a native `joust` release with `new`.
- **Exit criteria and verification:**
  - A fixture Gradle JOUST project (from `joust new`): detection by text; generated-source marking and the banner;
    navigation through `Name.jscmap.json`.
  - D1 tests: in an untrusted project every action is disabled and no process starts (I2).
  - ABI flow: change a state field; `generateJsc` fails with JSC0002/JSC0003; the review shows both manifests and
    hashes; acceptance runs the bound form (I8); a baseline changed between review and accept is refused and shown;
    the same for an npm fixture.
  - Console links resolve only inside the contracts directory; a crafted `../../etc/passwd:1:1:` line is not a link
    (I18).
  - Wizard: Gradle with `.jsc`, Gradle with Java, and Node with `.jsc` projects are created into empty directories and
    open with their builds linked.
  - Inspections: positive and negative fixture tests per row of D10; on JOUST's examples, the fixed-width and
    `MerkleMap` inspections flag every finding `SubsetLint` reports on a corpus of mutated contracts (a differential
    check against JOUST's processor, not values derived from IntelliAda's code).

### M3: the run panel on DevKit and Yano, Gradle projects (D11–D16); M3b: npm projects

- **Entry gates:**
  - **G3.1** JOUST's dev-host entry point accepted in a JOUST ADR and released (Q4), with its protocol version.
  - **G3.2** The witness-only signer reproduces every `txHash` of JOUST's `fixtures/m0` from its `unsignedTx` (F26:
    independent test vectors from JOUST's own DevKit runs), and refuses mutated bodies (I11).
  - **G3.3** The network-identity endpoint per node type chosen and verified on DevKit and Yano (D14).
  - **G3.4** Yano compatibility: every chain read JOUST's runtime makes (the `reads` recorded in `fixtures/m0`) answers
    on Yano as on Yaci Store; otherwise Yano support waits and the gap is reported to Yano.
  - **G3.5** The devnet-only account model (Q12) agreed.
- **Exit criteria and verification:**
  - End-to-end on DevKit and on Yano, from a `joust new` Gradle Counter in Java and in `.jsc`: start the devnet; create
    and fund a devnet account; deploy; increment; view the state; reset as the owner; close. Then JOUST's `bank`
    (owned objects) and `registry` (keyed) examples, a `@ValidAfter` call made valid by advancing Yano's time, and a
    snapshot restore after which the instance is re-checked (I17).
  - Negative tests: a fake node reporting mainnet magic, and a non-loopback host not allow-listed (I13); a host that
    returns a transaction with a mainnet output, an input unknown to the node, a required signer that is not the
    caller, or a `txHash` that does not match its body (I11, I13); a canary mnemonic searched for in every child's
    arguments, environment, stdin capture and temp files (I10); a description with an HTML title such as
    `<html><img src=http://127.0.0.1:<port>/>` produces no request to that port and shows the literal text (I15);
    closing the project ends the host (I20); an untrusted project shows the panel disabled (I2); no listening socket
    belongs to the host (I19).
  - Error display: a `.jsc` `require` failure shows its message-map site and navigates to it; a keyed cooldown shows
    its slot and offers the advance on Yano.
  - Measure and record (as measurements, with machine and versions) the first-call latency (launch spec plus host
    start) and the warm call latency.
- **M3b (npm):** after JOUST's npm host command exists; the same protocol tests and end-to-end flow on a `joust new -t
  node` project.

### M4 (optional): editing parity (D6's option)

- **Entry gates:** the maintainers choose parity (Q2); the ADR-0021 amendment of D6 accepted and released in
  `joust-lsp`; a way to read LSP4IJ's document versions (public API or upstream change); LSP4IJ's formatting and
  completion paths verified to drop stale results, or wrapped.
- **Exit criteria and verification:** IntelliJ ports of the VS Code extension's scenarios: an edit computed before the
  user typed is refused; an edit to a closed target is refused; a multi-file rename applies atomically or not at all;
  a rename of a name the ABI manifest records asks first; stale formatting and completion are dropped; quick fixes
  never apply on save. Then JOUST lists IntelliAda as a tested client (D6's amendment, item b).

## 7. Compatibility

- **IDE floor:** this design uses APIs present in 2025.2.4 and targets the branch's 2026.1 (`sinceBuild 261`, F1). The
  README's 2024.2 floor belongs to `master`; which floor the JOUST features target is Q10.
- **PyCharm and WebStorm:** the branch's mandatory `com.intellij.modules.java` dependency (F1) already excludes them.
  Parts A and C need no Java plugin; Part B's Gradle actions and D10 do.
- **JOUST versions:** the language server is the user's installed `joust-lsp` (any version with `--storage`, F14); the
  run panel needs the dev-host protocol version from Q4; project tasks are whatever the project's plugin provides
  (IntelliAda hides an action whose task is missing).
- **Data:** recorded instances are workspace state with a schema version; no key material is added to any store.

## 8. Risks

| Risk | Mitigation |
|---|---|
| A hostile repository runs code through IntelliAda | D1, D4, I1–I3, the sentinel tests |
| A project process obtains a signature usable elsewhere | D13 checks against IntelliAda's own node reads; devnet-only accounts; mainnet refused (I10–I13) |
| A user mnemonic with real funds is used on a devnet | only devnet-only and public genesis accounts are offered (I12); the account store's weakness is R1 |
| The bundled grammar drifts from JOUST's | I6 |
| JOUST's dev-host API never materialises | Parts A and B ship without it; Part C waits (Q4) |
| LSP4IJ upgrade changes capabilities (e.g. declares `failureHandling`) | edits stay off without `--enable-edits` or `vscode-joust` (F15); I5's test runs on every LSP4IJ bump |
| Yano is not fully Blockfrost-compatible for JOUST's reads | G3.4 |

## 9. Open questions (points needing a maintainer decision)

- **Q1. Bundle or detect Node and `joust-lsp`?** (a) Detect and confirm, never bundle; (b) bundle a pinned `joust-lsp`
  (about 30 MB unpacked for 0.1.0-pre1-rc9, ADR-0021 F8) and still require Node; (c) bundle both. **Lean: (a)**: bundling ties every
  project to IntelliAda's JOUST version, adds a supply-chain path, and still needs Node 22+ or a platform-specific
  Node per OS.
- **Q2. Editing parity (M4)?** (a) Stay read-only; (b) build D6's option and propose the ADR-0021 amendment. **Lean:
  (a) until M3 ships**, then revisit with LSP4IJ's version API settled.
- **Q3. Mainnet policy.** (a) Hard refusal (this ADR); (b) an explicit opt-in per session with a modal per transaction
  and hardware/CIP-30 signing. Public testnets likewise. **Lean: (a)**; a later ADR can add (b) for testnets first.
- **Q4. The integration surface on the JOUST side.** (a) `julc-state-server` as is (needs a project-written main, HTTP,
  no keyed creation or co-signers, no listing); (b) a small new JOUST API: a dev host speaking JSON-RPC on stdio
  (`hello`, `describe`, `prepare`, `view`, `instances`, `decode`, `publish`, `reset`), built on `ServedContract`, started
  by a plugin task that prints a launch spec (Gradle) and by a CLI command (npm), **its own JOUST ADR**. **Lean: (b)**.
- **Q5. Co-signers.** Should the host accept extra signer addresses, so IntelliAda can co-sign `@Quorum` and
  `@AuthorizedBy` calls with several devnet accounts it holds? **Lean: yes, in Q4's ADR.**
- **Q6. Chain history and events beyond the session.** (a) Session history only; (b) a host `history(instance)` reading
  the chain; (c) an indexer applying ADR-0009 D5. **Lean: (a) in M3.**
- **Q7. DevKit time, snapshot and rollback controls.** Not verified that DevKit's admin API offers them. **Lean:**
  Yano-only until verified.
- **Q8. `--project-engine` default in trusted projects.** **Lean: on**, as VS Code does in trusted workspaces.
- **Q9. Grammar distribution and `npm create joust`.** Copy the grammar into IntelliAda (D2) or fetch the release asset
  at runtime (network)? Offer `npm create joust` when no native `joust` is installed? **Lean: copy; native `joust`
  only.**
- **Q10. IDE floor for the JOUST features:** 2026.1 (the branch) or lower (the README's 2024.2)? **Lean: the branch's.**
- **Q11. LSP4IJ upgrade** (0.10.0 today; 0.21.0 is the newest tag): a separate change, with I5's test as a gate.
- **Q12. The devnet-only account model:** a flag on `CardanoAccount` in the existing store, or a separate store in
  IntelliJ's `PasswordSafe`? **Lean: a separate store in `PasswordSafe`**, which also starts R1's fix.

## 10. Related findings (out of scope)

- **R1.** The account store's encryption key sits beside the ciphertext (`.cardano.account.conf.key`), and the store
  falls back to plain text when no key can be made, so mnemonics are effectively unprotected at rest
  (`account/cache/GlobalCache.java:54-75`, `:218-224`). IntelliJ's `PasswordSafe` would protect them.
- **R2.** `JulcCompileService` runs a project's `./gradlew` or `./mvnw` without a trust check (F3); D1 would apply.
- **R3.** `AikenSDKState.getState()` writes `<sdk>` elements but `loadState()` reads `localSdk` children, so configured
  Aiken SDKs may not survive a restart (`aiken/configuration/service/AikenSDKState.java:40`, `:53-56`).
- **R4.** `CLAUDE.md` and `README.md` say "Min IDE 2024.2", LSP4IJ 0.7.0 and support for PyCharm and WebStorm; the
  branch builds for 2026.1 with LSP4IJ 0.10.0 and a mandatory Java module dependency (F1).
- **R5.** Constructing `YaciBaseService` starts a local DevKit or Yano as a side effect (F8). D15 starts devnets only on
  a click.
- **R6.** JOUST's server recognises VS Code by an `initializationOptions` claim (`server.js:242`); any client can make
  it. D6's amendment item (a) replaces it with a declared capability. (A JOUST finding, for JOUST's maintainers.)

## Revision history

- **r1** (2026-11-18): initial proposal.
