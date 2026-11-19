# ADR-004: JOUST support in IntelliAda

**Status:** Accepted (r6), by the maintainer on 2026-11-19; r7 records implementation refinements from M1 (§12). Reviewed and approved at `fb953fd` by the design, security
and Codex reviewers (PR #9).

**Date:** 2026-11-18. Revised 2026-11-19.

**Authors:** Claude Code, drafted for the IntelliAda maintainers' review.

**Scope:** design only. This ADR adds no code, no build change and no plugin.xml entry. Every snippet and name below is
illustrative unless it cites a file.

**Base:** stacked on `feat/julc-yano-integration` (PR #8) at `d7a022e`. ADR-001 to ADR-003 and the Yano code exist only
there, not on `master` (`b3d3fce`).

**Builds on:**
- IntelliAda ADR-001 (julc and Yano integration), ADR-002 (the julc and Yano developer flow) and ADR-003 (julc VM
  features).
- JOUST ADRs at JOUST `main` `d529bfd` (2026-11-17):
  - ADR-0009 D5 (event provenance);
  - ADR-0013 (descriptions, the prepare service, wallets);
  - ADR-0015 (the npm toolchain and runtime);
  - ADR-0017 (the `joust` CLI and the Gradle plugin);
  - ADR-0018 D12 (stable codes, ABI manifests, message maps);
  - ADR-0021 (the `.jsc` language server and the VS Code extension).

**Risk:** high.
- **What IntelliAda would do:** start a language server and run project-supplied build tasks.
- **What a wrong decision costs:** a wrong trust decision runs a hostile repository's code.
- **What does not change:** no validator, no wire format, and nothing about what JOUST's build accepts.

**Scope since r2: Parts A (`.jsc`) and B (projects) only.** Part C, the run panel on devnets, testnets and mainnet, moved
to **ADR-005** (`adr/005-joust-run-panel.md`, a draft) with its IDs unchanged (decision L16, §11). This ADR keeps the
facts (§1.1) and the threat model (§2), which ADR-005 cites.

## 1. Context

**JOUST (JuLC State Contracts)** lets a developer write a Cardano contract as a state record plus pure `init`, action and
view functions, written either in Java (`@StateContract`) or in JOUST's own language, `.jsc`.
- JOUST generates the validator and a typed off-chain client.
- The same method runs on the JVM, to build the transaction, and on chain, to check it.
- JOUST already ships a language server (`joust-lsp`), a VS Code extension, a Gradle plugin (`org.julclang.joust`), an
  npm toolchain and runtime, and a prepare service.

**IntelliAda** already integrates Aiken through LSP4IJ, julc through in-IDE compiler bridges, and Yaci DevKit and Yano
devnets.

**The request.** The maintainer asked for three things:
- `.jsc` language support;
- JOUST project support;
- a way to deploy and call a JOUST contract **directly from its code in the editor**: first on a local DevKit or Yano
  devnet, then, after review round 1, on public testnets and mainnet behind explicit opt-ins (ADR-005 D14a).

This ADR decides how, with read-only editing first.

### 1.1 Facts this design relies on

Where each kind of fact was checked:
- **IntelliAda:** at `d7a022e` (`feat/julc-yano-integration`).
- **JOUST:** at `d529bfd` (`origin/main`).
- **LSP4IJ:** the 0.10.0 jar the branch resolves (`instrumented-lsp4ij-0.10.0.jar`, disassembled with `javap`), and its
  docs at tag `0.10.0` (`bc13e84`).
- **IntelliJ:** an installed IntelliJ IDEA 2025.2.4 (build 252.27397.103), **not** 2026.1, which the branch targets.
  M1's entry gate re-checks these facts.
- **Yano:** at `f7bd7de61`.
- **CCL:** 0.6.2.

| # | Fact | Source |
|---|---|---|
| F1 | The branch builds with IntelliJ Platform Gradle plugin 2.14.0 against IntelliJ IDEA 2026.1, LSP4IJ 0.10.0 and CCL 0.6.2, with `sinceBuild = "261"`. `master` builds against 2024.2 (`sinceBuild 242`) and LSP4IJ 0.5.0. `CLAUDE.md` and `README.md` still say "Min IDE 2024.2" and LSP4IJ 0.7.0 (#13). The branch's plugin.xml adds a mandatory `com.intellij.modules.java` dependency that `master` does not have | `build.gradle:20`, `:39`, `:41`, `:45`, `:118`; `git show origin/master:build.gradle` (`:20`, `:46`, `:48`, `:111`); `plugin.xml:15-18` |
| F2 | Aiken's language server is an LSP4IJ `server` with a `languageMapping` to the Aiken language. `AikenLanguageServer` runs `<sdk path>/aiken lsp --stdio`, with the project base path as working directory and the system environment. The SDK path is application-level state (`aiken-sdks.xml`); the project stores only the SDK's id (`aiken-project.xml`). IntelliAda has no other LSP client | `plugin.xml:189-199`; `aiken/AikenLanguageServer.java:13-27`; `aiken/configuration/AikenSDK.java:52-57`; `aiken/configuration/AikenConfigurationHelperService.java:20-39`; `AikenSDKState` and `AikenProjectState` `@Storage` |
| F3 | IntelliAda never consults IntelliJ's project trust (no use of `TrustedProjects`), and `JulcCompileService` runs a project's `./gradlew` or `./mvnw` when the user builds (#11) | `grep -rn "TrustedProjects\|isTrusted" src` → nothing; `julc/compile/JulcCompileService.java:126-133`, `:174-189` |
| F4 | julc support loads `julc-compiler-all.jar` and `julc-vm-java-all.jar` into isolated `URLClassLoader`s **inside the IDE JVM** (ADR-003). `JulcExternalAnnotator` runs julc's `SubsetValidator` there, but only in a julc project (`JulcTomlService.isJulcProject()`) and on files whose text contains a julc validator annotation or `@OnchainLibrary` (a substring check). julc project detection looks for `julc.toml` or julc artifact names in build files, so a JOUST Gradle project (which names `org.julclang.joust`) is not a julc project. A mixed project with both, and a file holding `@StateContract` next to a julc annotation, would still reach the annotator | `julc/annotator/validate/JulcVmBridge.java:29-41`; `julc/annotator/JulcExternalAnnotator.java:53-62`, `:128-132`; `julc/module/pkg/JulcTomlService.java:32-90` |
| F5 | Node types: `YaciDevKit`, `LocalYaciDevKit`, `Yano`, and the Blockfrost and Koios networks. The target node is one application-level default (`RemoteNodeState`). Yano routes to CCL's `BFBackendService` (a Blockfrost-style API). `YanoLifecycleService` starts Yano on port 7070 by default, while the Yano panel's own default is 8888. `RemoteNode` holds `apiEndpoint`, `authKey`, `network`, `networkId`, `protocolMagic`, `headers`, `timeout`, `home` and `version`, and **no admin endpoint** | `core/util/NodeType.java:7-18`; `nodeint/CardanoNodeConfigurationHelper.java:9-11`; `nodeint/service/NodeServiceFactory.java:39-41`; `nodeint/yano/YanoLifecycleService.java:42`; `nodeint/yano/ui/YanoDevnetPanel.java:35`; `configuration/model/RemoteNode.java:9-22` |
| F6 | `YanoDevnetService` calls Yano's devnet API: fund (`/api/v1/devnet/fund`), snapshots (create, restore, list, delete), rollback (by slot, block or count), time advance (slots, seconds, epochs), epoch shift and catch-up, transaction evaluation, and genesis download | `nodeint/yano/YanoDevnetService.java:35-151` |
| F7 | DevKit lifecycle (`DevKitLifecycleService`, a light `@Service`) runs the downloaded DevKit's `devkit.sh up`; its health check is `GET /api/v1/health` on `http://localhost:8080`. IntelliAda has **no** client for DevKit's admin API (`grep` finds no `topup` or `/local-cluster` call) | `nodeint/devkit/DevKitLifecycleService.java:17-25`; `nodeint/devkit/DevKitProcessManager.java:45-46`, `:192`, `:206-230` |
| F8 | Constructing a `YaciBaseService` through its `(Project, LogListener)` constructor binds the application default node and **auto-starts** a `LocalYaciDevKit` or `Yano` target (#14). The `(RemoteNode, LogListener)` constructor binds the given node and starts nothing | `nodeint/service/impl/yaciprovider/YaciBaseService.java:62-83`, `:86-95`, `:160-210` |
| F9 | Accounts (`CardanoAccount`: name, address, **mnemonic**, balance) live in `~/.intelliada/.cardano.account.conf`. They are encrypted with a key kept beside the store in `.cardano.account.conf.key`, and written in plain text when no key can be made (#10). An account carries no network tag. The account choosers keep every `addr_test…` account for any non-mainnet network. `CreateAccountAction` prints the new mnemonic to the Cardano Log console (R7) | `account/model/CardanoAccount.java:9-14`; `account/service/AccountCacheService.java:129-152`; `account/cache/GlobalCache.java:37`, `:54-75`, `:194-224`; `account/service/AccountChooser.java:54-55`; `account/ui/ListAccountDialog.java:414-417`, `:486-489`; `account/action/CreateAccountAction.java:75` |
| F10 | LSP4IJ 0.10.0 maps files to a server by `languageMapping`, `fileTypeMapping` or `fileNamePatternMapping`. Its guide says that defining a file type for a pattern "will override and disable the syntax coloration with TextMate", while `fileNamePatternMapping` keeps that colouring. Semantic tokens are supported (marked experimental) through IntelliJ's semantic highlighting, with a `semanticTokensColorsProvider` extension point | LSP4IJ `docs/DeveloperGuide.md` at 0.10.0, lines 268-284, 386-404, 728-761; `docs/LSPSupport.md` line 63, 584-596; the jar's `plugin.xml` extension points |
| F11 | **LSP4IJ 0.10.0, client capabilities and `applyEdit`.** It declares `workspace.applyEdit`, `workspaceEdit.documentChanges` and resource operations, and **no `failureHandling`** (`ClientCapabilitiesFactory` never calls `setFailureHandling`). Its default `workspace/applyEdit` handler applies the edit through `LSPIJUtils.applyWorkspaceEdit` and **always answers `applied: true`**, without reading any document version.<br>**Extension points.** `LanguageClientImpl`'s `applyEdit`, `registerCapability`, `unregisterCapability`, `showDocument`, `showMessage` and `showMessageRequest` are public and not final. `LSPClientFeatures.initializeParams(InitializeParams)` runs after LSP4IJ fills the parameters, and `LSPClientFeatures` has a `set…Feature` for every feature (completion, code action, code lens, document link, formatting, on-type formatting, rename, inlay hint, …). `StreamConnectionProvider.getInitializationOptions(VirtualFile)` supplies `initializationOptions`. `LanguageServerManager` can start, stop and query a project's servers.<br>**Edit paths that bypass `applyEdit`.** Formatting, completion text edits and LSP4IJ's rename dialog (`LSPRenameRefactoringDialog` → `LSPIJUtils.applyWorkspaceEdit`) never pass through `LanguageClientImpl.applyEdit`. `DocumentContentSynchronizer.getVersion()` is package-private | `javap -c -p` on `internal/ClientCapabilitiesFactory`, `client/LanguageClientImpl` (`lambda$applyEdit$1`: `iconst_1`), `LSPIJUtils`, `LanguageServerWrapper`, `LanguageServerManager`, `client/features/LSPClientFeatures` |
| F12 | IntelliJ's bundled TextMate plugin (`org.jetbrains.plugins.textmate`) declares `com.intellij.textmate.bundleProvider` (`TextMateBundleProvider.getBundles()` → `PluginBundle(name, path)`) and reads VS Code extension layouts through `VSCBundleReader`. That reader deserialises `package.json` as `VSCodeExtension`, which **requires `name` and `contributes`**. TextMate files have a flat PSI (`TextMateFile`, language `textmate`, `TextMateParserDefinition`). LSP4IJ registers its folding, structure view and other features for the `textmate` language too | IntelliJ 2025.2.4 `plugins/textmate/lib/textmate.jar` (`META-INF/plugin.xml:17`, `:28`, `org/jetbrains/plugins/textmate/api/`, `VSCBundleReaderKt`, `VSCodeExtension` serialiser); LSP4IJ jar `plugin.xml:177`, `:232`, `:251` |
| F13 | IntelliJ's trust API: `TrustedProjects.isProjectTrusted(Project)` and `TrustedProjectsListener.TOPIC` (`onProjectTrusted`, `onProjectUntrusted`) are public. `isTrustedCheckDisabled()` is **`@ApiStatus.Internal`**. `getProjectTrustedState` already answers `YES` when the product disables trust checks (`idea.trust.disabled`, `idea.trust.all.projects`, headless), for LightEdit, and for any project under a trusted location. `GeneratedSourcesFilter` and `ExternalSystemUtil` exist, and IntelliJ IDEA bundles the `gradle` plugin | IntelliJ 2025.2.4 `lib/app-client.jar` (`javap -v -p TrustedProjects`); `plugins/gradle` |
| F14 | `joust-lsp` (`@julclang/joust`, `bin/joust-lsp.js`) takes `--stdio`, `--project-engine`, `--enable-edits`, `--engine-dir` and `--storage <dir>` (default `~/.cache/joust-lsp`). Without `--project-engine` it is **advisory**: it analyses with its own bundled compiler and runs no project code. The published package is `0.1.0-pre2` and needs Node ≥ 22 | JOUST `js/joust/bin/joust-lsp.js:1-38`; ADR-0021 D3, D3b; `npm view @julclang/joust` (2026-11-18) |
| F15 | **How the server decides on edits.** It decides from the client: `isVscode = io.client === 'vscode-joust'`, which is an `initializationOptions` **claim** (joust#141). Then `trusted = isVscode ? io.trusted : flags.projectEngine`, and `edits = (isVscode \|\| flags.enableEdits) && applyEdit && documentChanges && failureHandling ∈ {transactional, textOnlyTransactional}`. A client that declares `failureHandling` alone therefore gets no edits.<br>**What depends on `edits`.** Completion, code actions, `executeCommand` and formatting are advertised only with `edits`, and `textDocument/rename` only to non-VS Code clients with edits. Without them the server offers sync, diagnostics, symbols, folding, selection ranges, hover, definition, references and semantic tokens.<br>**Custom messages.** The `joust/trust` notification is ignored for non-VS Code clients. `joust/status`, `joust/generatedJava`, `joust/checkBuild` and `joust/restartAnalysis` answer any client, and `joust/rename` refuses without `edits` | JOUST `js/joust/src/lsp/server.js:115-138`, `:242-248`, `:286`, `:1608-1611`, `:1690`, `:2492-2512`, `:2526-2527` |
| F16 | Semantic token types: `namespace, type, enum, enumMember, property, parameter, variable, function, method, class, keyword`; modifiers: `declaration, readonly, defaultLibrary, timeValue`. No `string`, `comment` or `number` type | JOUST `js/joust/src/lsp/features.js:9-11` |
| F17 | **The VS Code extension** sends `client: 'vscode-joust'` and `trusted`, and installs its own `workspace/applyEdit` handler. That handler accepts only versioned `TextDocumentEdit`s of open documents, each carrying the SHA-256 of the text it was checked on (`joustExpected`, sent beside `edit`). It checks all of them synchronously before `workspace.applyEdit`, and otherwise refuses the whole edit.<br>**Its grammar.** `syntaxes/jsc.tmLanguage.json` (scope `source.jsc`) with `language-configuration.json`, MIT-licensed like IntelliAda | JOUST `js/vscode-joust/src/extension.js:73-102`; `src/apply-edit.js:1-60`; `server.js:1145-1158`; `package.json`; `LICENSE` |
| F18 | **Other editors** start a `joust-lsp` the user installed, by absolute path, on an absolute `node`. They never start one inside the project, in `node_modules/.bin` or through `npx`, because they have no workspace trust and a repository must never choose the program that runs. Their editing features are off, because the server offers an edit only to a client known to reject it when the text changed after it was computed | JOUST `www/src/content/docs/languages/editors.mdx` ("Other editors: a server you installed", the Neovim and Helix sections); ADR-0021 D3b, D4a, §16 E4 |
| F19 | **`org.julclang.joust` tasks,** registered when `src/main/jsc` exists:<br>• `generateJsc`: lowers `.jsc` into `build/generated/sources/jsc/java/main`, added to the main source set, with `Name.jscmap.json` source and message maps beside each generated class, also copied as resources;<br>• `acceptJscAbi`: `--contract C --committed-sha256 h1 --sha256 h2`, or bare to accept everything;<br>• `jscAbiDiff --contract C [--out f]`;<br>• `acceptJoustLock`;<br>• `checkJscAbi`: against `build/classes/java/main/META-INF/joust`, part of `check`;<br>• `checkJoustLibraries`, `recordJoustLibraries` and `remapJscDiagnostics`.<br>**Dependencies.** The plugin adds `julc-state-runtime`, the Blockfrost backend, the processors and the testkit, but **not `julc-state-server`**, and it sets a Java 25 toolchain. JOUST's VS Code tasks refuse unqualified ABI tasks in multi-project builds | JOUST `julc-state-gradle-plugin/src/main/java/org/julclang/state/gradle/JoustPlugin.java:54-103`, `:105-292`; `AcceptJscAbi.java:21-40`; `JscAbiDiff.java:17-22`; `julc-state-dsl/.../JscMain.java:883-886`; `js/vscode-joust/src/tasks.js:91-97` |
| F20 | **The description file.** The processor writes each contract's description to `META-INF/joust/<package>.<Contract>.json`: a CIP-57-style blueprint plus `x-joust` (version 2). `x-joust` holds:<br>• `state`, `creations`, `actions`, `views`, `owned`;<br>• `keyed` (key field, tags, cooldowns, `inserts/actions/removes/retires`);<br>• per-call `args` with schemas, `payable` and `takesAssets`;<br>• `policies` (`signedBy`, `validAfter`, `validBefore`, `quorum`, `authorizedBy`, …) and `needsService`;<br>• `encoding.json`, the JSON vocabulary for values: integer as decimal string, bytes as hex, `$variant` for sum types, Bool, Optional as null, maps as key-ordered pairs, Data as CBOR hex.<br>Only the npm toolchain writes `build/joust/contracts.json` | JOUST `julc-state-processor/.../JoustProcessor.java:107-109`; `js/state-client/test/fixtures/registry.description.json`; `js/joust/src/build.js:163` |
| F21 | **`julc-state-server`'s `JoustServer`** is a **library**: a builder, wired by examples such as `examples/dapp/.../DappServer.java:81`. It is an HTTP service for browsers, with a CORS allow-list, rate limits and a size limit.<br>**`prepare(JsonNode)`** is public. It maps `{contract, kind, method, instance, object, args, pay, address}` to `{cbor, txHash, validTo, signers, plan, instance?, object?}`, for a `WalletAccount` (address only, fails closed).<br>**What it lacks.** It holds no keys, refuses co-signed calls and a keyed contract's creation (no designated input), and serves views only over HTTP (`view` is private). It has no instance listing, history or events. `ServedContract` refuses `@Params` contracts, offers `deployment()`, and keeps the loaded class and description for its lifetime | JOUST `julc-state-server/.../JoustServer.java:32-46`, `:76-152`, `:173-235`, `:256-295`, `:297-305`; `ServedContract.java:25-57`, `:189-200`; `julc-state-runtime/.../WalletAccount.java:15-22` |
| F22 | `PreparedTx.withWitnesses(...)` merges CIP-30-style vkey witness sets into the prepared transaction, checking that each is a valid signature of `txHash`. `Prepared.signers` includes the payer and every required signature | JOUST `julc-state-runtime/.../PreparedTx.java:58-61`, `:76-100` |
| F23 | `@julclang/joust-runtime`'s `engineService({ api, contracts, clock, … }).prepare(request)` takes the same request shape and returns the same `Prepared`, from `build/joust/contracts.json`. It can also `publish` reference scripts. `joust dev` attaches to the network named in `joust.config.json` (`networks`), may **spawn that network's `start` command through a shell**, and serves a prepare service on `127.0.0.1` | JOUST `js/joust-runtime/src/index.d.ts:11-54`; `js/joust/src/dev.js:1-17`, `:51-60`, `:198` |
| F24 | A `.jsc` refusal names its sites through the message map. A generated check traces a key, and the JVM runtime appends the `.jsc` sites, e.g. `#pay:spend2.1 [Vault.jsc:9:17 (a payout in split is negative)]` | JOUST `julc-state-runtime/.../JscMessages.java:14-22` |
| F25 | **JOUST's DevKit flows** use Yaci Store on `:8080` and the admin API on `:10000`: the clock from `/local-cluster/api/admin/devnet`, top-up at `/local-cluster/api/addresses/topup`. DevKit's default mnemonic funds its first 20 addresses in genesis. The `joust new` template's DevKit runtime uses a 20 s TTL ("DevKit's time horizon is minutes"). The runtime's MerkleMap mirror defaults to RocksDB at `./.joust/mpf` unless `-Djoust.mapStore=memory` | JOUST `examples/devkit/.../DevKit.java:30-31`, `:146-183`; `julc-state-cli/.../templates/gradle/Devnet.java`; `julc-state-runtime/.../DefaultMapStore.java:13-23` |
| F26 | **`fixtures/m0`** holds JOUST's oracle: 1,556 files across the examples. Among them are **211 transactions**, each **unsigned** (`unsignedTx`) with its `txHash`. Hashing each body's original byte slice with BLAKE2b-256 reproduces all 211 `txHash` values (Codex, round 1). **57** of those bodies have no validity upper bound. The other files are 1,344 recorded chain responses (`reads`, which also record `submitTransaction`) and one clock fixture | JOUST `fixtures/m0/bank/0004-Bank.deposit.json`, `bank/0002-Bank.open.json:9`; `FixtureRecorder.java:80-90`; `git ls-tree -r origin/main fixtures/m0 \| wc -l` |
| F27 | The native `joust` decides `new`, `version` and `help` without running project code. `joust new <dir> [-t gradle\|node\|web] [-l java\|jsc] [-p <package>]` writes a Gradle project from its own templates, and an npm one through `npm` and `node` found on `PATH`; it runs Java from `JAVA_HOME` or `PATH`. **`-p` is refused for `node` and `web`** | JOUST `julc-state-cli/.../JoustMain.java:16-36`, `:145-202`; `Scaffold.java:74-79`; `Sys.java:47-62` |
| F28 | **JOUST's processor lint** (`SubsetLint`) rejects fixed-width numbers and `MerkleMap.reads()/writes()/root()`.<br>**The JuLC compiler JOUST pins** (`0.1.0-pre21-rc1`, `gradle/libs.versions.toml:4`) rejects:<br>• reference equality `==`/`!=` on arrays: **JULC0086**, `EqualityRules.requireValueEquality`;<br>• `byte[].equals`: **JULC0087**;<br>• `Objects.equals` on byte arrays.<br>So the gotcha in JOUST's `CLAUDE.md` ("the processor should lint for this") is already enforced by the compiler, not by the processor. `Arrays.equals(byte[], byte[])` compares contents on the JVM | JOUST `julc-state-processor/.../SubsetLint.java:33-42`, `:183-256`; `CLAUDE.md:266-267`; julc `v0.1.0-pre21-rc1` `EqualityRules.java:79-89`, `BigIntegerCompatTest.java:356-361`, `ArrayIdentityEqualityTest.java:154`, `:416`; `java.util.Arrays` |
| F29 | JOUST's events are carried in the redeemer and are authentic only under ADR-0009 D5's provenance rule (phase-2 valid, the spending redeemer of the followed input, the expected policy, lineage). Per that ADR, "an indexer integration that applies the rule to chain data is a follow-up". Yaci Store on DevKit serves no transaction CBOR | JOUST `docs/adr/0009-*.md:123-139`; JOUST `CLAUDE.md` (ADR-0016 M3 note) |
| F30 | **LSP4IJ 0.10.0, other server-to-client requests** (client side, by bytecode):<br>• **Dynamic registration.** `client/registerCapability` is accepted for every method `LSPClientFeatures.getCapabilityRegistry` knows, including formatting, rename, completion and code action, and for `workspace/executeCommand` commands.<br>• **`window/showDocument`.** With `external: true` it calls `BrowserUtil.browse(uri)` with no scheme check; otherwise it opens any `file:` URI.<br>• **`window/showMessage` and `showMessageRequest`.** The text is rendered as markdown to HTML (flexmark, raw HTML passed through) in a notification with `URL_OPENING_LISTENER`.<br>• **Escaping.** In `LSPDiagnosticFeature.getTooltip` **only the diagnostic message** is escaped (`StringUtil.escapeXmlEntities`). The source, the code, `codeDescription.href` (inserted into `<a href="…">` without attribute escaping) and related-information messages go into the HTML raw. Related-information locations become `#lsp-navigation/` links, handled by a globally registered handler that calls `openInEditor`. `getTooltip` and `getMessage` are public and overridable. JOUST's own diagnostics set `codeDescription.href` to an `https:` documentation URL (`model.js:10`, `:38`)<br>• **`workspaceFolders`** = every top-level content root (`BaseProjectDirectories.getBaseDirectories`) | `javap -c -p` on `LanguageServerWrapper`, `ServerMessageHandler`, `MarkdownConverter`, `LSPDiagnosticFeature`, `LSPIJUtils` |
| F31 | **`--project-engine`.** With it, `joust-lsp` selects the project's engine **at initialize**: it reads the project's `engine/joust-toolchain.js` and `.wasm` and calls the engine (`select.js:481-490`).<br>**Build on save.** It then builds on every save of a project-matched file in memory (the build "writes nothing"), on that engine, unless the client sends `initializationOptions.buildOnSave: false`.<br>**npm root.** It recognises an npm root by `joust.config.json` **or** a `package.json` dependency or devDependency on `@julclang/joust` | JOUST `js/joust/src/lsp/select.js:392-403`, `:481-490`; `server.js:243`, `:2310-2320`, `:2483-2488` |
| F32 | **JVM time and keyed calls.** JOUST's JVM `ChainTime.currentSlot()` defaults to the machine clock. JOUST's DevKit clock comes from DevKit's admin API (`devkitClock(adminUrl)`). Yano has no such endpoint: its time advance produces blocks up to a new slot, and its epoch shift moves the genesis start. JOUST's JS client persists a keyed operation (the designated input) **before** preparing, and checks that the returned transaction spends it | JOUST `julc-state-runtime/.../ChainTime.java`; `js/joust-runtime/src/index.d.ts:59`; `js/state-client/src/client.js:342-346`, `:410-431`, `:445-458`; Yano `DevnetResource.java:256-371` |
| F33 | **JOUST's JS client and publishing.** JOUST's JS client verifies a prepared transaction against the request before signing (ADR-0013 D2: its spends, outputs and signers). JOUST's reference-script publication sets no validity upper bound | JOUST `js/state-client/src/verify.js:116-137`, `:260-301`, `:434-447`; `StateContractHandle.java:269-280`; `TxRunner.java:331-345` |
| F34 | JOUST's document symbols give `action`, `destroy` and `view` the same `SymbolKind` (6); `init` and owned `init` are both 9. Owned methods are named `T.m`. Only `detail` (a reader-facing string) tells them apart. Descriptions also have creation kinds such as `keyedInit` | JOUST `js/joust/src/lsp/model.js:58-116`; `registry.description.json` |
| F35 | CCL 0.6.2's `TransactionSigner.sign(byte[], HdKeyPair)` signs the body bytes of an encoded transaction and adds the witness without re-encoding the body | `cardano-client-transaction-spec-0.6.2.jar` (`javap`); DES-9 |
| F36 | LSP4J's `ApplyWorkspaceEditParams` has only `edit` and `label`, so a typed `applyEdit` handler never sees the `joustExpected` digest that `joust-lsp` sends beside `edit` | `javap -p org.eclipse.lsp4j.ApplyWorkspaceEditParams`; JOUST `server.js:1145-1158` |
| F37 | Network magics: mainnet 764824073, preprod 1, preview 2. Yano's devnet profile uses **42**, and Yano also ships a **SanchoNet** profile with magic **4** | Yano `app/config/application-devnet.yml:14`, `application-sanchonet.yml:8` |

## 2. Threat model and trust assumptions

**Assets.**
1. The developer's machine and IDE process.
2. Every mnemonic in IntelliAda's account stores, including ones that control funds on mainnet and public testnets. F9: the legacy store has no network tag, and a mnemonic derives the same keys on every network.
3. Funds on public networks, once ADR-005's opt-ins are on.
4. Project files (sources, ABI manifests, `joust.lock`). A wrong write can strand a deployed instance.
5. Credentials in IntelliAda's node configuration (Blockfrost project ids).
6. The developer's attention: a spoofed dialog or link.

**Untrusted.**
- **The contents of every project IntelliAda opens:**
  - `.jsc` and `.java` sources and build scripts;
  - the Gradle wrapper and its `distributionUrl`, and `gradle.properties`;
  - `package.json`, `node_modules`, and `joust.config.json`, including `networks` and their `start` commands (F23);
  - **every file under `.idea/`**, including `workspace.xml` and run configurations, because a repository can commit them;
  - build outputs (descriptions, message maps, ABI diffs);
  - anything a project process prints.

  A repository may be a hostile clone.
- **A node endpoint's answers,** which decide what a transaction spends.
- **The language server's messages,** which, under the project engine, carry the output of project JavaScript.

**Trusted.**
- IntelliAda and the IDE.
- The user's application-level settings.
- A Node binary and a `joust-lsp` the user installed and named by absolute path (as in editors.mdx, F18).
- The IDE's own environment.
- **Once IntelliJ marks the project trusted and the user has invoked an action** (D1), the project's own build and runtime: the same code that `./gradlew build` or `npm run` would run.

**Trusted project code runs as the user** and can read every file the user can, including IntelliAda's account stores. This ADR does not claim to protect existing mnemonics from code the user has chosen to run. I10 limits what IntelliAda *transmits or logs*. Protecting keys at rest is D13a's account store (in PasswordSafe), plus the rule that a public-network key never sits in the legacy file store (#10).

**Trust boundaries.** The IDE JVM ↔ `joust-lsp` (a Node child on stdio) ↔ the project's dev host (a JVM or Node child on stdio, D11) ↔ the node (HTTP or HTTPS) ↔ the project's files.

**What IntelliAda must never do:**
- **T1.** Run or load project-supplied code when a project is opened, indexed, synced or changed on disk. That rules out any Gradle task, wrapper, `npm`, `node_modules` code, dev host or project engine. **Exception (not in M1):** once a `joust-lsp` with engine pins exists (D5, ADR-0021 amendment item f), and with the user's per-project consent, the server may load that project's **consented and pinned** engine pairs. In M1 there is no exception.
  - The only process that may start without a click is the user-installed `joust-lsp`, when a `.jsc` file is opened.
  - IntelliJ's own Gradle import in a trusted project is IntelliJ's behaviour, not IntelliAda's; I1 is scoped to what IntelliAda starts or loads.
- **T2.** Run project-supplied code in a project IntelliJ does not trust, even on request.
- **T3.** Let a project choose an executable, an argument, an endpoint or a key **outside D1**. Under D1, the project's own toolchain chooses its own launch, through D11's launch spec. Endpoints and keys never come from the project.
- **T4.** Let a mnemonic or private key leave the IDE process, or sign a transaction it has not checked itself (ADR-005 D13).
- **T5.** Use a public network from the run panel without that network class's explicit opt-in (ADR-005 D14a), or sign on mainnet without the mandatory per-transaction review.
- **T6.** Render project-supplied or server-supplied text as HTML, or follow a link from it to anything but a file inside the project. This covers every surface IntelliAda renders or configures, LSP4IJ's included (D3a).
  - **Accepted, and out of scope** (SEC-19): IntelliJ's own console filters in the LSP4IJ log and trace console and in the Gradle and run consoles that show D7's task output. They turn URLs and file paths in text into links that the user may click. That is standard IDE behaviour, not IntelliAda rendering, and IntelliAda adds no filter to those consoles except D7's confined `JSC` diagnostic links.
- **T7.** Apply an edit from the language server in M1–M3, by any path (D6, D3a).
- **T8.** Write to project files except through a command the user invoked: a Gradle task, the wizard, or an accepted ABI (I21).

**Out of scope:** a compromised IDE, Node or JDK; a malicious user-installed `joust-lsp` running *without* the project engine; side channels.
- **A lying endpoint.** It can lie about state. ADR-005's I12 controls **which keys IntelliAda offers and uses** on each network class; it cannot make a key worthless elsewhere. A fresh devnet key can still receive funds on another network, and a mnemonic imported as `devnet` may control funds held elsewhere. So the devnet argument rests on an **explicit assumption**: devnet-class keys are used only on devnets. ADR-005 carries that assumption into its account policy, where devnet accounts are created fresh, never imported (CDX-20). ADR-005 D13's checks are defence in depth against bugs and a lying endpoint.
- **Public networks.** The load-bearing controls there are ADR-005 D14a's opt-ins, the mandatory review on mainnet, and D13's allow-list as hard refusals.

## 3. Decision

### Part A: `.jsc` language support

### D1. One rule for running project code

IntelliAda runs or loads project-supplied code only when **all** of these hold:
- the project is trusted. IntelliAda checks with the **public** `TrustedProjects.isProjectTrusted(project)` only (F13), never the internal `isTrustedCheckDisabled()`;
- the user invoked an action that says what it runs;
- the code is the project's own toolchain: its Gradle build through IntelliJ's Gradle integration, a process started from a launch spec that build produced (D11), its own `@julclang/joust` package, or its engine files under D5's consent.

Never on open, sync, indexing or a file event (T1).

- **Implicit trust.** `isProjectTrusted` already answers yes in products that disable the check, under trusted locations, and in LightEdit (F13). So D1's protection in those cases is the explicit action, plus D5's separate consent for the project engine and D11's first-start confirmation for the dev host. IntelliAda adds no "ask once" branch on an internal API.
- **Trust granted or revoked mid-session** (`TrustedProjectsListener.TOPIC`). On revocation IntelliAda restarts the language server in advisory mode (D5) and stops any dev host (D11) (I4, I20).
- **Existing gap.** `JulcCompileService` runs `./gradlew` with no trust check (F3, #11). This ADR does not change julc, but every JOUST action follows D1.

### D2. File type and highlighting: a TextMate bundle plus the server's semantic tokens

- **No IntelliAda `LanguageFileType`, lexer or parser for `.jsc`.** `.jsc` is highlighted by JOUST's own TextMate grammar, which IntelliAda ships as a bundle in VS Code extension layout:
  - `syntaxes/jsc.tmLanguage.json` and `language-configuration.json`, copied verbatim from `js/vscode-joust/` at a pinned JOUST release tag;
  - JOUST's `LICENSE` notice;
  - a minimal `package.json` holding a top-level **`name`** and only that extension's `contributes.languages` and `contributes.grammars` entries. `VSCBundleReader` requires `name` and `contributes` (F12).

  The bundle is registered through `com.intellij.textmate.bundleProvider` (F12), under an **optional** dependency on `org.jetbrains.plugins.textmate`. The grammar is data, not code, and both projects are MIT (F17).
- **The server is mapped with `fileNamePatternMapping patterns="*.jsc"`,** because LSP4IJ documents that a file type for the pattern disables TextMate colouring (F10).
- **Semantic tokens** from the server colour what the grammar cannot know: state field vs local vs parameter, `std` functions, and ADR-0018 D11's time values. A `semanticTokensColorsProvider` maps JOUST's token types and modifiers (F16), including `timeValue`, to IntelliAda text attribute keys, so themes can style them.
- **Editing behaviour:** the bundle's commenter, brackets and auto-closing pairs come from `language-configuration.json`.
- **A grammar sync test** records the SHA-256 of each bundled file and the JOUST tag it came from, and fails when they differ. Updating the bundle is part of moving IntelliAda to a new JOUST release (I6). Q9 is decided: copy the files, with no network at runtime.

**Alternatives considered:**
- *(b) An IntelliAda language with a JFlex lexer, as for Aiken.* Rejected:
  - a second lexer of ADR-0018's grammar drifts from JOUST's (the Aiken grammar is already "intentionally incomplete" and needs `ParseErrorHighlightFilter`);
  - a file type would turn TextMate off.
- *(c) A file type with semantic tokens only.* Rejected:
  - JOUST's token types have no string, comment or number (F16), so those stay uncoloured;
  - nothing is coloured until a server is configured.
- *(d) tree-sitter.* JOUST ships `tree-sitter-jsc`, but IntelliJ has no tree-sitter host.

**Consequence:** `.jsc` files have no IntelliAda PSI, only TextMate's flat PSI (`TextMateFile`, F12).
- D15's gutter icons are registered for the `textmate` language and filtered to `*.jsc`, and take positions from the server.
- If the TextMate plugin is absent, `.jsc` files are plain text and still get the server's diagnostics, hover and navigation.

### D3. The language server definition

- **The server:** an LSP4IJ `server` (illustrative id `joustLanguageServer`) with a `fileNamePatternMapping` for `*.jsc` and `languageId="jsc"`.
- **Its connection provider** extends `ProcessStreamConnectionProvider` (as Aiken's does, F2), with the command `[<node real path>, <joust-lsp.js real path>, "--stdio", "--storage", <dir>]`, plus D5's pinned-engine flag only after the pin release and under consent. It is exactly I3's command line, and **never** contains the bare `--project-engine` (CDX-26). It executes exactly the real paths D4 validated.
  - **Never `--enable-edits`** (D6), and never an argument taken from project files.
  - **`--storage`** names a directory under the IDE's system directory (`PathManager.getSystemPath()`), outside every project. The server refuses a storage directory inside a workspace folder anyway (`server.js:264-266`).
  - **Working directory:** the directory of the real `joust-lsp.js`, outside the project, not the project base path as for Aiken.
  - **Environment:** the IDE's own. IntelliAda adds nothing from the project.
- **`initializationOptions`** (through `getInitializationOptions`, F11): `{ "client": "intelliada", "buildOnSave": <setting, default false> }`, and nothing that claims another client.
  - **IntelliAda never sends `client: "vscode-joust"`** (F15). That value tells the server the client applies edits through the VS Code extension's verified handler (F17), which IntelliAda does not have (I5).
  - Build on save runs the project engine on every save (F31). It stays off unless the user turns on the application setting **Build on save with the project's compiler**.
- **`workspaceFolders` are confined on every path** (I25). LSP4IJ's list is every top-level content root (F30). IntelliAda replaces it with the roots whose real paths lie inside the project's base directory, applying the same filter in three places:
  - `LSPClientFeatures.initializeParams`, for the folders sent at initialize;
  - the client's `workspaceFolders()` override, for the `workspace/workspaceFolders` request;
  - every `workspace/didChangeWorkspaceFolders` notification. IntelliAda sends only in-base changes, or none.

  A module or `includeBuild` outside the project is then never discovered as a workspace root and never becomes an engine folder. A file outside the project that the user opens explicitly is still analysed, but only in advisory mode (`select.js:449-452`), which runs no project code (CDX-24).

  LSP4IJ 0.10.0 sends no `didChangeWorkspaceFolders` at all: the jar does not reference the change-event classes. The third path is therefore guarded by design rather than by an existing hook, and every LSP4IJ upgrade (Q11) must re-check whether it sends them and that IntelliAda filters them (SEC-16b).
- **Enablement** (`LanguageServerEnablementSupport`, as for Aiken): the server is enabled only when D4's paths are set and valid. Otherwise opening a `.jsc` shows one notification with **Configure JOUST…** and starts nothing.
- **Commands (M1),** all of which answer any client (F15) and write nothing:
  - **Restart analysis** (`joust/restartAnalysis`).
  - **Show compiler and mode** (`joust/status`): the compiler version, `bundled` or `project`, and `advisory` or `project-matched`. A status bar widget also shows it.
  - **Show generated Java** (`joust/generatedJava`), opened as a read-only light virtual file.
  - **Check build (in memory)** (`joust/checkBuild`): the server's capped in-memory build.
- **Lifecycle:** LSP4IJ starts the server when a mapped file opens and stops it with the project.

### D3a. Client-side read-only and plain-text enforcement (new in r2)

The server's own decision not to offer edits (F15) is one mechanism, with three inputs. IntelliAda does not rely on it
alone. **In M1–M3 the client accepts no edit and renders no server text as HTML, whatever the server sends** (I5, T6, T7):

1. **Features by allow-list.**
   - **Enabled:** only I5's read-only set (diagnostics, IntelliAda's own hover from item 6, definition, declaration, type definition, implementation, references and usages, document highlight, document and workspace symbols, folding, selection range, semantic tokens, progress).
   - **Disabled** (`isEnabled` → `false`): every other `LSPClientFeatures` feature, including completion, code action, code lens, document link, document colour, formatting, on-type formatting, rename, inlay hint, signature help and call or type hierarchy (F11).
   - **A guard test** lists `LSPClientFeatures`' `set…Feature` methods and fails on any feature it does not know. So a new feature in a future LSP4IJ is reviewed and classified before it can be enabled.
2. **`registerCapability` filtered by the same set.** One constant, the **read-only set R** (the features item 1 enables), drives both item 1 and this filter. A registration is accepted only for a method of a feature in R; everything else is refused with an error, including `workspace/executeCommand`, `workspace/didChangeWatchedFiles` registrations outside R, and file operations (SEC-16a).
3. **`applyEdit` refused.** Every `workspace/applyEdit` is answered `applied: false`, with a reason.
4. **`showDocument` confined.** `external` is never honoured. A `file:` URI opens only when its real path is inside the project, and is then read-only for generated files. Everything else is refused.
5. **`showMessage` and `showMessageRequest` as plain text.** Text is shown escaped, with no markdown-to-HTML and no URL listener. Action items are labels only.
6. **Hover is rendered by IntelliAda, as plain text.**
   - **Why not LSP4IJ's renderer:** it converts markdown to HTML with raw HTML passed through. Its `LSPDocumentationLinkHandler` opens `file://` links directly through `LSPIJUtils.openInEditor`, bypassing item 4. Its legacy `MarkedString` path need not go through `LSPHoverFeature.getContent` (`javap` on `LSPDocumentationTarget` and `LSPDocumentationHelper`).
   - **What IntelliAda does instead:** it disables LSP4IJ's hover feature for this server and registers its own `DocumentationTargetProvider` for `*.jsc`. That provider requests `textDocument/hover` through `LanguageServerManager` and renders the result as escaped literal text: no markdown-to-HTML, no anchors and no images. The only link it may offer is navigation to a `.jsc` position whose real path is inside the project (I18).
   - **The rest:** JOUST escapes its own hover markdown (`features.js:101`), but that is not relied on.
   - **Tests (M1):** a fake server sends raw HTML, an `<img>`, and `file://` and `https:` links in hover. They must render literally, nothing is fetched, and no out-of-project link opens.
7. **Navigation confined** (CDX-23, SEC-16c). Several features in R consume server-supplied locations: definition, declaration, type definition, implementation, references and usages, workspace symbols. In LSP4IJ 0.10.0, `WorkspaceSymbolData.navigate` calls `LSPIJUtils.openInEditor` directly, and definitions go through `LSPPsiElementFactory.toPsiElement`, so neither passes item 4.
   - **What may open:** in M1, a location is kept only when it is a `file:` URI whose **real path is inside the project**, as T6 says. Everything else is dropped before it reaches the editor or a results list, including:
     - files in the installed `joust-lsp` package (e.g. `bin/joust-lsp.js`) and in its `--storage` directory;
     - JOUST's `joust-lib:` documents. JOUST returns these for package-library imports, backed by the server's in-memory content (`server.js:1248-1252`, `:2143-2148`).

     Navigation into JOUST libraries is a possible later change. It would open only the server-supplied text, in a read-only light virtual file, never a path; T6 would be amended and tests added (CDX-23).
   - **How:** through a per-feature result hook where LSP4IJ offers one. Where it does not, IntelliAda disables that LSP4IJ feature and provides the feature itself, as it does for hover. G1.1 lists which applies to each feature.
   - **Tests (M1):** a fake server returns, for each feature, an in-project target, an outside target, and an in-project symlink to an outside target. Only the first opens.
8. **Diagnostics rendered as plain text** (SEC-18). IntelliAda's `LSPDiagnosticFeature` overrides `getTooltip` (F30).
   - **The tooltip** is built by IntelliAda: the message, source, code and related-information messages are all HTML-escaped, with no markup from the server.
   - **`getMessage`** returns the plain message unescaped. It feeds plain-text surfaces such as the Problems view, where escaping would show `&lt;T&gt;` literally (SEC-20).
   - **`codeDescription.href`** is shown **as literal text** only, and **never opened** (CDX-27, DES-15).
   - **The Open documentation action,** where offered, is built by IntelliAda from **its own** fixed catalogue URL. That URL is a constant equal to JOUST's `CATALOGUE` (`model.js:10`), pinned with the grammar's JOUST tag. IntelliAda adds a fragment derived from the diagnostic's code, only when that code matches `^JSC\d{4}$`, and from its severity, as JOUST itself does (`model.js:35-38`). The server's href never chooses the destination.
   - Related-information locations are rendered as text. When they navigate, they do so only through item 7's filter, never through `#lsp-navigation/` links.
   - **Tests (M1):** a fake server's diagnostic with HTML in its source, code, href (including a quote that breaks out of the attribute) and related information renders literally. No link opens, and a related location outside the project does not navigate.

Each item has a fake-server test in M1, and the tests run again on every LSP4IJ upgrade (Q11, decided: a separate upgrade gated by these tests).

### D4. Locating Node and `joust-lsp`: application settings, absolute paths, never the project

**Settings.** There are two **application-level** settings (a `PersistentStateComponent` with application storage, like `AikenSDKState`): the Node binary and `joust-lsp`. No project-level or `.idea/` setting can name an executable, a path or an argument (I7). The reason is editors.mdx's: the repository must not choose what runs (F18). Q1 is decided: detect and confirm, and never bundle.

**Validation** runs when the setting is saved, at every server start, and when a project opens whose roots contain a configured path. It works on real paths. **Only the explicit configuration actions (Save, Detect…) execute anything;** validation at server start executes nothing (CDX-25):
- **Absolute paths only:** no bare name, no relative path, no `npx`, and never a `.cmd`, `.bat` or `.ps1` (cmd.exe argument parsing).
- **Resolve `joust-lsp` to its real file.**
  - **Unix:** resolve through symlinks to `<prefix>/lib/node_modules/@julclang/joust/bin/joust-lsp.js` (where `<prefix>/bin/joust-lsp` is a link).
  - **Windows:** npm's global commands are `.cmd` shims, not links, so locate the file by package layout instead: `<prefix>\node_modules\@julclang\joust\bin\joust-lsp.js`.

  Either way, the server runs on the configured `node`, never through a shebang's `PATH` lookup.
- **Location checks.** Neither the selected path nor its real target may be inside any open project's base directory or content roots, nor under a `node_modules/.bin` directory. Paths are compared by segment, never by string prefix, with Windows drive and UNC forms normalised.
- **Versions.**
  - Node's version comes from `<node> --version`, run on the user's binary by absolute path, with the IDE's environment. It must be at least 22 (F14).
    - It runs **only when the user saves or detects the setting.** IntelliAda then records the binary's identity: its real path, size, modification time and SHA-256.
    - **At server start,** IntelliAda re-checks the paths and that identity, without executing anything. If the identity changed, the server does not start, and a notification asks the user to validate Node again. So I1's "no process but `joust-lsp`" holds.
  - `joust-lsp`'s version is read **as text** from the `package.json` beside its package, never by running it.
- **Executing the validated path.** The command executes exactly the validated real paths, not names re-resolved at launch.

**Detect…** offers candidates **without running anything except the candidate `node --version`**, which runs only after that candidate passes every location check above:
- absolute `PATH` entries outside open projects;
- the usual global layouts, read as directories: nvm, Volta, asdf, fnm, Homebrew, `/usr/local`, and `%APPDATA%\npm` on Windows.

IntelliAda does not run `npm prefix -g`. The user confirms one candidate; detection never sets a path silently.

### D5. Analysis mode: advisory by default; the project's compiler only by per-project consent

Q8 is decided: per-project opt-in. **r3 amends it (L19):** the opt-in is deferred until `joust-lsp` itself enforces it.
- **Default, in every project:** the server starts **without** `--project-engine`. It analyses with the compiler bundled in the user's `joust-lsp`, says "advisory" (`joust/status`), and runs no project code (F14, F15).
- **Project engine: gated on a server-side pin (SEC-11, DES-12, CDX-1).** Today `--project-engine` is one global flag.
  - `joust-lsp` selects an engine **per file**, by walking up to the nearest npm root (`select.js:392-421`).
  - It **re-reads** the engine bytes on `joust/restartAnalysis` and on watched-file changes (`select.js:478-541`; `server.js:2512-2573`).

  So a digest IntelliAda checked before launch cannot bind what the server loads. The feature therefore waits for **ADR-0021 amendment item (f)** (D6). Under it, `joust-lsp` accepts, at launch, a SHA-256 pin `<js>:<wasm>` for each engine root, **in a new flag that replaces `--project-engine`** (illustrative: `--engine-pin <root>=<js>:<wasm>`, repeatable; deliberately not prefixed `--project-engine`), and **denies by default**: it loads an engine only when the bytes it reads, within its read caps, match that root's pin. Any unpinned or mismatched pair falls back to advisory and is reported.

  The check belongs where the bytes are used. `selectUncached` already reads both files once, hashes them into a content-addressed copy and loads that copy (`select.js:325-327`, `:484-487`). Once a `joust-lsp` release has the pin (an entry gate for this feature only, not for M1):
  - In a trusted npm project, the action **Use this project's JOUST compiler…** lists **every** engine pair under the in-base workspace folders, by root real path and SHA-256, and records consent per pair in application-level state.
  - IntelliAda then restarts the server with the pinned-engine flag, one pin per consented pair. **It never sends the bare `--project-engine`.**
  - **Fail closed (SEC-15):** today's `joust-lsp` matches flags exactly and ignores unknown ones (`bin/joust-lsp.js:37`), and it honours `--project-engine` at initialize (`server.js:283-284`). So a server without pin support ignores the new flag and stays advisory. Safety does not depend on IntelliAda reading the server's version correctly; the version is used only to explain the result in the status bar.
  - When the server reports a refused, changed or new pair, IntelliAda shows it as advisory with the reason, and offers to consent again.
  - **Effect on T1:** this is T1's single exception, and the reason I1 is scoped to what IntelliAda starts or loads.
- **Until then, M1 ships advisory only:** IntelliAda passes no engine flag at all. The status widget shows any version skew, e.g. "advisory: bundled compiler X; this project pins Y".
- **Rejected:** watching engine files and restarting on change. It is time-of-check/time-of-use; `node_modules` is often excluded from the VFS; a new pair can load before any event. Its I26 would admit unconsented loads, and the machinery would be thrown away once the pin exists (both advisors, L19).
- **Build on save** stays off unless the user enables it (D3).
- **Trust changes** restart the project's server through `LanguageServerManager` (F11), because the server ignores `joust/trust` from clients other than VS Code (F15). Revocation drops back to advisory (I4).
- **Gradle projects** are always advisory in the server, because ADR-0021 D3 reads the plugin version as text and does not run Gradle. IntelliAda shows the project's `org.julclang.joust` version next to the server's compiler version when they differ.

### D6. Editing: read-only first; parity is an option (M4) that needs an ADR-0021 amendment

**Decision for M1–M3: read-only, as in Neovim and Helix.** Q2 is decided: off until M3 ships, then revisited.
- **What IntelliAda offers:** diagnostics, hover, go to definition, find references, document symbols (Structure view), folding, selection ranges and semantic tokens, plus D3's read-only commands.
- **What it does not offer from the server:** completion, quick fixes, rename and formatting.
- **What the user keeps:** typing and editing `.jsc` text normally.

**How read-only holds.** Two layers that do not depend on each other:
- **The server offers no edits.** IntelliAda never passes `--enable-edits` and never claims `vscode-joust`. LSP4IJ 0.10.0 also declares no `failureHandling` (F11, F15). These are three inputs to *one* server-side decision.
- **The client accepts none** (D3a), whatever the server or a future LSP4IJ declares. The M1 test sends a client that *does* declare `failureHandling: transactional` and checks that the server still offers no editing provider (F15). Separately, a fake server that registers edit features dynamically or sends `applyEdit` is refused.

**Formatting** is available as the project's own `joust fmt`, in a trusted project (D7). It is a user command that writes the file on disk, not a server edit.

**Why read-only:** the server's edits are safe only through a client that rejects an edit whose target text changed after the server checked it (ADR-0021 D4a). LSP4IJ's default handler applies a `workspace/applyEdit` without reading versions and reports `applied: true` (F11). That is the failure mode ADR-0021 D4a was written to exclude.

**Option for parity (M4).** It is recorded for later (Q2 says revisit after M3). IntelliAda would have to build:
1. **A verified `workspace/applyEdit` at the wire level.** LSP4J's typed parameters drop `joustExpected` (F36), so IntelliAda must read the raw message, or JOUST must add a typed request that carries the digest.
   - Accept only `documentChanges` of versioned `TextDocumentEdit`s: no `changes` map and no file operations.
   - Require `joustExpected` (F17).
   - In one write action on the EDT, check that every target is an open document at exactly that LSP version and digest, and refuse the **whole** edit otherwise.
   - Apply all of it as one undoable command.
   - LSP4IJ's per-document version is package-private in 0.10.0, so either a public API or an upstream change is an M4 entry gate.
2. **JOUST: Rename Symbol** through `joust/rename` and `joust/prepareRename`, not LSP4IJ's rename dialog, which applies the result through the unverified path (F11). It keeps the server's confirmations (`needsConfirmation`, `scriptChange`), its `needsOpen` list, and confinement to the project's contracts directory.
3. **Quick fixes** only on explicit invocation, carrying the server's opaque, single-use fix id through `joust.applyFix`.
4. **Completion** without `additionalTextEdits`, and **formatting** only if IntelliJ's LSP formatting path rejects a result computed for older text.
5. **Remove invisible characters…** as a modal that lists every occurrence before changing anything.
6. **Trust** as in D5.

**The ADR-0021 amendment JOUST would need** (joust#141):
- **(a)** a capability a client declares to say it implements D4a's verified application (illustrative: `experimental.joust.verifiedEdits: 1`), instead of trusting a client name;
- **(b)** IntelliAda listed as a tested client once its scenarios pass;
- **(c)** `joust/rename` for such clients;
- **(d)** `trusted` passed by such a client, with `joust/trust` honoured for it;
- **(e)** a stable `joust/entryPoints` request (D15);
- **(f)** engine pins (D5): a **new** flag that carries a SHA-256 `<js>:<wasm>` pin per engine root and **replaces** `--project-engine`, so an older server, which ignores unknown flags, stays advisory. The server denies by default, and checks the pin against the exact bytes it copies and loads, on **every** selection.

### Part B: JOUST projects

### D7. Detection, generated sources and task actions

- **Detection by reading files as text, never by running a build.** IntelliAda follows JOUST's own root rule (F31):
  - **Gradle:** the project names the plugin `org.julclang.joust` in `build.gradle(.kts)` or `settings.gradle(.kts)`. IntelliAda reads the version literal for display.
  - **npm:** the root has `joust.config.json` **or** a `package.json` that depends on `@julclang/joust` (in dependencies or devDependencies).

  A new project service (not `JulcTomlService`, F4) holds the result. Whether the project's CLI is installed (`node_modules/@julclang/joust`) is tracked separately from detection, and decides only whether npm actions are available. From `joust.config.json` IntelliAda reads only `contracts` (ADR-0021 D4b's rule), never `networks`, URLs or `out`.
- **Generated sources.** `build/generated/sources/jsc/java/main` is already in the main source set (F19), so IntelliJ's Gradle import treats it as a source root. IntelliAda:
  - marks files there as generated (`GeneratedSourcesFilter`, F13);
  - shows a banner: "Generated from `Counter.jsc` by `generateJsc`: edit the `.jsc`";
  - navigates from a generated line to the `.jsc` position through the `Name.jscmap.json` beside it (F19). The target's real path must be inside the contracts directory (I18).
- **Task actions,** in a "JOUST" group in the editor, project view and Gradle tool window context menus:
  - Build;
  - **Generate from .jsc** (`generateJsc`);
  - **Check ABI** (`checkJscAbi`);
  - **Review ABI change…** (D8);
  - **Accept `joust.lock`** (`acceptJoustLock`), after a confirmation naming the file;
  - Run tests.

  **How they run:**
  - **Gradle:** through IntelliJ's Gradle integration (`ExternalSystemUtil`, an optional dependency on `com.intellij.gradle`), so the project's Gradle JVM and wrapper apply, only under D1. Each run uses **freshly built execution settings**, never a run configuration stored in `.idea/`. In a multi-project build, every task is addressed by its **qualified path** (`:module:generateJsc`) for the module that owns the file.
  - **IDEs without the Gradle plugin:** the Gradle actions are hidden.
  - **npm projects:** the same actions run the project's own CLI, only under D1, with the package's real path inside the project: `<node> node_modules/@julclang/joust/bin/joust.js build`, and `joust fmt <file>`.
- **Diagnostics in consoles.** Build output lines in JOUST's formats become hyperlinks, resolved only to real paths inside the project's contracts directory (I18). Both build paths print `app/File.jsc:12:5: error[JSC0502]: …`, the file relative to the contracts directory (Gradle's `remapJscDiagnostics`; npm's `diagnosticLine` in `js/joust/src/build.js` at v0.1.0-pre2, corrected in r12: the earlier npm example had the code first). IntelliAda also accepts the code-first form.

### D8. The ABI-change experience

A `.jsc` change that changes the datum or a redeemer fails `generateJsc` with JSC0002 or JSC0003, by design (ADR-0018
D12). IntelliAda follows ADR-0021 D7a exactly:

1. **Notify.** On that failure IntelliAda shows a notification naming the contract and its **owning module** (the Gradle project path whose `generateJsc` failed), with **Review ABI change…**.
2. **Bind before running.**
   - **Ambiguity:** if the contract's owner is ambiguous (the same contract name in two modules, or no single qualified task), the action is disabled with the reason, as JOUST's VS Code tasks do (F19).
   - **Validation:** the contract name must match JOUST's qualified-name grammar (`[A-Za-z_][A-Za-z0-9_]*` segments joined by `.`). The new hash must be 64 lowercase hex characters. The committed hash must be either that, or `none`, and `none` only when no manifest is committed. This is exactly the rule in `AcceptJscAbi.java` and `abi.js` (I8).
   - **Construction:** the task arguments are built as a list, never as a parsed string.
3. **Review** runs `:<module>:jscAbiDiff --contract <C> --out <file>`, with `<file>` in the IDE's temp directory. It shows the committed and the new manifest of **that module** in IntelliJ's diff viewer, with the SHA-256 of each (`none` when nothing is committed).
   - The diff output is project data, and it reports texts and hashes as separate fields. So IntelliAda **recomputes the SHA-256 of the exact manifest texts it displays**, and enables **Accept** only when they equal the reported hashes.
   - The baseline it shows is the committed file as read by IntelliAda, or none.
4. **Accept this change** opens a modal naming the one contract, its module and its change, then runs `:<module>:acceptJscAbi --contract <C> --committed-sha256 <h1> --sha256 <h2>`.
   - The UI never runs the bare `acceptJscAbi`, which accepts every manifest (I8).
   - Afterwards, IntelliAda checks that that module's manifest file now has SHA-256 `h2`, even if the wider build failed, and that no other module's manifest changed.
   - If the baseline or the source changed since the review, the task refuses. IntelliAda shows that refusal and offers to review again.
5. **Remind.** Afterwards IntelliAda reminds the user to review and commit `<Contract>.abi.json`.

npm projects use `joust abi diff <C> --out <file>` and `joust build --accept-abi=<C>@<h1>..<h2>` (ADR-0021 D7a's names).
The exact `--out` JSON is pinned with the JOUST maintainers before M2 (an entry gate).

### D9. New-project wizard

A **JOUST** project generator: a `moduleBuilder` in IntelliJ IDEA, like `JulcModuleBuilder`.
- **What it asks:** the language (`.jsc` or Java), the template (Gradle, Node or web), and, **for Gradle only**, the package. `-p` is refused for Node and web (F27).
- **What it runs:** the user's native `joust` (an absolute path in application settings, validated as in D4), as `joust new <empty dir> -t <template> -l <lang> [-p <package>]`.
- **The child environment is built, not inherited** (I7):
  - `PATH` contains only the directories of the configured Node (for `npm` and `node`) and of `joust`, plus system directories, never a directory inside any open project;
  - `JAVA_HOME` is a JDK the user picks in the wizard from IntelliJ's application SDK table.

  This holds for every template, because `joust new` resolves `npm`, `node` and `java` from `PATH` and `JAVA_HOME` (F27).
  - D4's rule (selected path **and** real target, outside every open project and every `node_modules/.bin`) applies to the **executables the CLI will actually pick**, not only to the directories: the `node`, the `npm` and the `java` (`JAVA_HOME/bin/java`). An `npm` symlink whose real target lies in a project is refused.
  - Each permitted directory is checked to contain no other entry named `node`, `npm`, `npx` or `java` with a disallowed real target.
- **Why this is safe:** `joust new` runs no project code (F27), and the directory is new.
- **Network:** the Node and web templates need `npm` and network access, and the wizard says so.
- **Afterwards:** IntelliAda opens the project and links the Gradle build (ADR-002 notes that auto-import sometimes fails). `npm create joust` is not offered (Q9, decided).

### D10. Optional inspections for Java `@StateContract` contracts

Local inspections in a "JOUST" group, active only in classes annotated `org.julclang.state.api.StateContract` (resolved by
qualified name), all **advice** (the build stays the authority):

| Inspection | From | Default |
|---|---|---|
| `byte[]` compared with `==`/`!=` (JULC0086), `.equals` (JULC0087) or `Objects.equals`: "compares references on the JVM and bytes on chain; use `ByteStringLib.equals`", with a quick fix. This is **earlier feedback** for the pinned JuLC's own errors, which use those codes | F28 (the compiler enforces it, not the processor) | warning |
| `Arrays.equals(byte[], byte[])`: not a semantic hazard on the JVM (F28). It is flagged only if M2 shows that JuLC does not compile it, and then with JuLC's message | F28 | off until verified |
| Fixed-width numbers (`int`, `long`, `short`, `byte`, `char`, their boxes, and arithmetic on them) | mirrors `SubsetLint` (F28), as earlier feedback only | warning |
| `throw` in contract code: "use `Require.that(cond, "msg")`" | JOUST `CLAUDE.md` ("no `throw`", "Use `Require.that`") | warning |
| `MerkleMap.reads()`, `writes()`, `root()`, or constructing a `MerkleMap`/`MapEntry` | `SubsetLint`; JOUST `CLAUDE.md` | error |
| `JulcMap.getOrDefault`; `IntervalLib.finite*Bound` compared without a `Finite` check | JOUST `CLAUDE.md` | weak warning |

**IntelliAda does not run its bundled julc `SubsetValidator` (F4) on `@StateContract` files.** The bundled julc is not the
project's (D12). The annotator's existing filters are a project predicate and substring checks (F4). M2 therefore adds an
**explicit guard**: a file that references `@StateContract` is skipped. A mixed julc-and-JOUST fixture tests it.

### Part C: running contracts from their code (moved to ADR-005 in r2)

Part C (D11, D11a, D12, D13, D13a, D14, D14a, D15 and D16) is now **ADR-005, "Running JOUST contracts from IntelliAda"**, a
draft that keeps these IDs. It is the requirements input to JOUST's dev-host ADR (Q4) and becomes Proposed once that ADR
exists (decision L16, §11).

**This ADR authorizes none of Part C:** no dev host, no signing, no network access from a run panel, and no account-store
change. What it keeps for ADR-005 to cite: §1.1's facts, §2's threat model, D1, and the shared invariants I15, I18, I20
and I21.
## 4. Invariants

Each is testable; §6.0 maps each invariant to its tests.

| # | Invariant | Decisions |
|---|---|---|
| I1 | Opening, indexing, syncing or changing a JOUST project makes **IntelliAda** start or load no project code, and start no process except the user-installed `joust-lsp` (when a `.jsc` opens). That includes no `node --version` probe (D4). The only exception, after the pin release and never in M1, is consented and pinned engines under D5 | D1, D3, D4, D5 |
| I2 | A Gradle task, the project's CLI, a dev host or the project engine runs only when the project is trusted and the user invoked an action (for the engine, D5's consent) | D1, D5, D7, D11 |
| I3 | The server command is exactly `<node> <joust-lsp.js> --stdio --storage <IDE system dir>`, plus the pinned-engine flag only under D5, never the bare `--project-engine`. Both paths are the validated absolute real paths, outside every open project and every `node_modules/.bin`. Never `npx`, `.cmd` or `--enable-edits` | D3, D4, D5 |
| I4 | A project that is untrusted or without D5's consent, and every project in M1, gets **no engine flag of either kind**. A trust or consent change restarts the server with the matching flags | D3, D5 |
| I5 | In M1–M3, both of these hold (with D3a's navigation confinement for location results).<br>**What IntelliAda sends:** `initializationOptions.client` is never `vscode-joust`, and IntelliAda adds no client capability.<br>**What IntelliAda accepts:** the server advertises no completion, code action, `executeCommand`, rename or formatting, even to a client declaring `failureHandling: transactional`. IntelliAda answers every `workspace/applyEdit` with `applied: false`, refuses every dynamic registration outside the read-only set, and runs no edit feature | D3, D3a, D6 |
| I6 | The bundled `.jsc` grammar files equal JOUST's at the recorded tag (SHA-256) | D2 |
| I7 | **Outside D1,** every setting that names an executable, a path, an argument, an environment or an endpoint is application-level, and nothing under the project or `.idea/` sets one. **Under D1,** only the project's own launch spec chooses the host's command line | D4, D9, D11, D14 |
| I8 | The UI accepts an ABI change only in the bound form: one validated contract name, one module's qualified task, and both validated SHA-256 values. It never runs bare `acceptJscAbi` or `--accept-abi`. After acceptance only that module's manifest changed | D8 |
| I9 | *(moved to ADR-005 in r2)* | D11, D12 |
| I10 | *(moved to ADR-005 in r2)* | D13, D13a |
| I11 | *(moved to ADR-005 in r2)* | D13 |
| I12 | *(moved to ADR-005 in r2)* | D13, D13a |
| I13 | *(moved to ADR-005 in r2)* | D13, D14, D14a |
| I14 | *(moved to ADR-005 in r2)* | D7, D11, D14 |
| I15 | Project- and server-supplied text (descriptions, messages, diagnostics including their tooltip fields, LSP messages, host output) is rendered as plain text (Swing HTML disabled, ANSI stripped), within size and depth caps, with `$ref` cycles refused | D3a, D15, D16 |
| I16 | *(moved to ADR-005 in r2)* | D15 |
| I17 | *(moved to ADR-005 in r2)* | D15 |
| I18 | Links that IntelliAda renders or offers, from messages, diagnostics, source maps and the consoles it adds filters to, open only files whose **real path** is inside the project (`.jsc` sites inside its contracts directory), or IntelliAda's own fixed documentation URL. IntelliJ's standard console filters are outside I18 (T6) | D3a, D7, D16 |
| I19 | *(moved to ADR-005 in r2)* | D11 |
| I20 | **On project close,** every process IntelliAda started for the project stops. **On loss of trust:**<br>• the language server restarts in advisory mode;<br>• active and queued JOUST actions are cancelled (Gradle tasks through `ExternalSystemTask.cancel`, the project's npm processes destroyed), and their late results are discarded;<br>• the dev host stops (ADR-005).<br>IntelliJ's shared Gradle daemon is not IntelliAda's and is left alone | D1, D7, D11 |
| I21 | **IntelliAda's own writes:** it writes, or asks a tool to write, project files only through a command the user invoked (a Gradle task, the CLI, the wizard, an accepted ABI).<br>**What IntelliAda never asks for:** a write by the language server or the dev host, and recorded state in the project.<br>**What is not claimed:** code the user has consented to run (a project engine, the project's own build) runs as the user and is not sandboxed (§2) | D3a, D7, D8, D9, D15 |
| I22 | *(moved to ADR-005 in r2)* | D11a |
| I23 | *(moved to ADR-005 in r2)* | D13, D14a |
| I24 | *(moved to ADR-005 in r2)* | D11, D12 |
| I25 | Every workspace-folder list or change IntelliAda sends to the server, at initialize, in answer to `workspace/workspaceFolders`, or in `didChangeWorkspaceFolders`, contains only roots whose real paths are inside the project's base directory | D3 |
| I26 | IntelliAda never passes the bare `--project-engine`. It passes the pinned-engine flag only with one SHA-256 pin per consented engine pair. Enforcement is the server's: it loads an engine only when its bytes match the pin for that root, and a server without pin support ignores the flag and stays advisory (ADR-0021 amendment item f). In M1 IntelliAda passes no engine flag | D5 |

## 5. Consequences

- **Editing:** `.jsc` gets the same read-only experience as Neovim and Helix, with the TextMate grammar VS Code uses. It adds project trust, which those editors lack, and an opt-in to the project's own compiler.
- **Setup:** developers must install Node 22+ and `@julclang/joust` themselves, and point IntelliAda at them once.
- **JOUST dependency:** the run panel (ADR-005) depends on a JOUST ADR and release (Q4). Nothing in this ADR depends on it.
- **Trust API:** IntelliAda gains its first use of IntelliJ's trust API. Applying D1 to julc (#11) would change julc's behaviour.
- **No PSI:** `.jsc` has no IntelliAda PSI. That rules out PSI-based refactoring, which read-only does not need and M4 would get from the server anyway.
- **Accounts and funds:** this ADR changes no account, key or network behaviour; ADR-005 does.

## 6. Milestones

### 6.0 Invariant to test traceability

| Invariant | Test (milestone) |
|---|---|
| I1 | **Sentinel test (M1), untrusted:** a project with a planted `gradlew`, `node_modules/.bin/joust-lsp`, `node`, npm scripts and `engine/joust-toolchain.js`, each writing a sentinel. Open it, open its `.jsc`, sync: no sentinel appears.<br>**Trusted:** the same result, and in M1 **no engine marker in any case** (CDX-1, DES-14).<br>**The `node --version` probe:** no process except `joust-lsp` starts on open, as captured by a process-launch recorder, not only by the planted executables (CDX-25).<br>**After the pin release:** in a trusted project with consent, only the marker of the consented, pinned engine appears, only for its root and bytes. An engine in an out-of-base content root, an ancestor directory or an unconsented nested root never loads |
| I2 | M2: every action in an untrusted project is disabled and starts no process. ADR-005 M3: the panel is disabled in an untrusted project |
| I3, I7 | M1: D4 validation unit tests (below), and a captured command line. M2: the wizard's child environment |
| I4, I26 | **M1:**<br>• the server is never started with an engine flag of either kind, whether trusted or untrusted;<br>• a trust change restarts it advisory.<br>**When the pin release exists:**<br>• consent gives one pin per pair;<br>• a nested npm root with an unconsented engine stays advisory;<br>• replacing an engine between consent and load, and after `joust/restartAnalysis`, is refused by the server and reported;<br>• an older server given the pinned-engine flag stays advisory, and the bare `--project-engine` is never sent;<br>• **a gate test runs every published `joust-lsp` version that IntelliAda would accept** with the pinned flag and confirms each one without pin support stays advisory (SEC-17) |
| I5 | **Real server (M1):** initialised with `failureHandling: transactional`, it advertises no editing provider.<br>**Fake server (M1)** sends dynamic registrations of formatting, rename, completion and `executeCommand`; `applyEdit`; and `showDocument` with `external:true`, `https:` and `file:` URIs outside the project. All are refused, and documents are unchanged.<br>Rerun on every LSP4IJ bump |
| I6 | M1 grammar sync test |
| I8 | M2: ABI flow tests, including:<br>• two modules with identical contracts;<br>• a crafted contract name containing spaces and quotes;<br>• a **first acceptance** (committed `none`);<br>• a diff whose valid-looking hash disagrees with its displayed text, for which Accept stays disabled |
| I9 | *(moved to ADR-005 in r2)* |
| I10 | *(moved to ADR-005 in r2)* |
| I11, I23 | *(moved to ADR-005 in r2)* |
| I12 | *(moved to ADR-005 in r2)* |
| I13 | *(moved to ADR-005 in r2)* |
| I14 | *(moved to ADR-005 in r2)* |
| I15 | M1: a `showMessage` and a hover with `<img src=http://127.0.0.1:<port>/>`, raw HTML and `file://`/`https:` links render literally and produce no request, and no link outside the project opens. ADR-005 M3: the same in a description title and in host output |
| I16 | *(moved to ADR-005 in r2)* |
| I17 | *(moved to ADR-005 in r2)* |
| I18 | M2: a crafted `../../etc/passwd:1:1:` line and a symlink inside the contracts directory pointing outside are not links |
| I19 | *(moved to ADR-005 in r2)* |
| I20 | **M1:** closing the project stops the server, and revoking trust restarts it advisory.<br>**M2:** revoking trust while a Gradle task and an npm build are running cancels both, and their results are discarded; closing the project does the same.<br>**ADR-005 M3:** the host |
| I21 | M1, M2 and ADR-005 M3, with the official JOUST tools: file-system snapshots of the project before and after server sessions, host sessions and panel use, with only user-invoked commands changing files |
| I22 | *(moved to ADR-005 in r2)* |
| I24 | *(moved to ADR-005 in r2)* |
| D3a item 7 | M1: navigation for every location-consuming feature in R. A fake server's in-project, outside, symlink-to-outside, installed-`joust-lsp.js`, storage-file and `joust-lib:` targets: only the in-project one opens |
| D3a item 8 | **M1:**<br>• a diagnostic tooltip with HTML and quote break-outs in source, code, href and related information renders literally;<br>• no link opens, and outside related locations do not navigate;<br>• a valid `https:` href to a different GitHub repository is shown as text, and **Open documentation** opens only IntelliAda's catalogue URL for a valid `JSC` code, never that href. A non-`JSC` code offers no action;<br>• the Problems view shows `<T>` unescaped |
| I25 | M1: with an out-of-base content root present, the folders at initialize, a later `workspace/workspaceFolders` request from a fake server, and module add and remove events all carry only in-base folders |

### M1: file type, highlighting, read-only language server, trust (D1–D6, D3a)

- **Entry gates:**
  - **G1.1** Re-verify on IntelliJ 2026.1 (build 261, the branch's `sinceBuild`) the APIs checked only on 2025.2.4: `TextMateBundleProvider` and the `VSCodeExtension` fields it requires, `TrustedProjects` and its listener, `GeneratedSourcesFilter` (F12, F13), and that TextMate's flat PSI accepts a `LineMarkerProvider`. Re-verify on the LSP4IJ version then pinned:
    - `fileNamePatternMapping` keeps TextMate colouring;
    - semantic tokens apply to TextMate files;
    - D3a's override points still exist (F11, F30);
    - for each location-consuming feature, whether LSP4IJ has a result hook or IntelliAda must provide the feature itself (D3a item 7).
  - **G1.2** A published `@julclang/joust` with `joust-lsp` (0.1.0-pre2 has it, F14).
  - **G1.3** The IntelliJ Platform test framework is added to the build (none is used today; `CLAUDE.md`, Testing).
  - **G1.4** PR #8 is merged, or this branch is rebased on what it becomes.
- **Exit criteria and verification:**
  - **D4 validation unit tests** (I3, I7): relative paths, bare names, `npx`, a path inside the project, a symlink from outside into the project and from the project out, `node_modules/.bin` (both the selected path and its real target), prefix look-alikes (`/u/repo` vs `/u/repo-evil`), Windows drive and UNC forms, a `.cmd` shim refused, and the Windows package-layout lookup.
  - **Platform tests** (`BasePlatformTestCase`, or heavier where a process is needed):
    - a `.jsc` file gets TextMate token attributes;
    - the LSP4IJ mapping selects the JOUST server;
    - the server command matches I3 in every mode;
    - the I4, I25 and I26 tests.
  - **Integration test** with the real pinned `joust-lsp`, over JOUST's template `Counter.jsc` and an erroneous copy:
    - diagnostics carry `JSC` codes;
    - hover, definition, references and document symbols answer;
    - I5's real-server test passes.
  - **I5's fake-server tests,** and I15's LSP tests.
  - **I1's sentinel tests,** untrusted and trusted.
  - **The grammar sync test** (I6).
  - **A manual `runIde` smoke test** on macOS, Linux and Windows, recorded in the PR.

### M2: projects, task actions, ABI review, wizard, inspections (D7–D10)

- **Entry gates:**
  - M1 exited.
  - The optional `com.intellij.gradle` dependency agreed.
  - The `jscAbiDiff --out` and `joust abi diff --out` JSON shape pinned with JOUST.
  - A native `joust` release with `new`.
- **Exit criteria and verification:**
  - **Fixtures:** a Gradle JOUST project (from `joust new`), an npm project with `joust.config.json`, and an npm project with **only** the dependency. Each covers detection by text, generated-source marking and the banner, and navigation through `Name.jscmap.json`.
  - **D1:** in an untrusted project every action is disabled, and no process starts (I2).
  - **ABI flow:**
    - change a state field, and `generateJsc` fails with JSC0002/JSC0003;
    - the review shows both manifests and hashes;
    - acceptance runs the bound, qualified form (I8);
    - a baseline changed between review and accept is refused and shown;
    - **two modules with identical contracts:** accepting one changes only its manifest;
    - the same flow for an npm fixture.
  - **Console links and source maps:** I18's tests.
  - **Wizard:** Gradle with `.jsc`, Gradle with Java, and Node and web with `.jsc` projects (no `-p`) are created into empty directories and open with their builds linked. A run with an absolute project directory first on the user's `PATH` uses only the validated directories (I7).
  - **Inspections:**
    - positive and negative fixture tests per row of D10;
    - a mixed julc-and-JOUST fixture shows that the julc annotator skips `@StateContract` files;
    - **a differential check against JOUST's processor:** on JOUST's examples and a corpus of mutated contracts, the fixed-width and `MerkleMap` inspections flag every finding `SubsetLint` reports.

### M3, M3b, M3c: moved to ADR-005 in r2

The run panel's milestones (devnets and testnets in M3, npm in M3b, keyed calls in M3c) are defined in ADR-005.
### M4 (optional): editing parity (D6's option)

- **Entry gates:**
  - Q2 is revisited after M3 ships, and the maintainers choose parity.
  - The ADR-0021 amendment of D6 is accepted and released in `joust-lsp`.
  - IntelliAda can read LSP4IJ's document versions (a public API or an upstream change).
  - **Wire-level digest preservation:** a raw handler, or a typed JOUST request that carries `joustExpected` (F36).
  - LSP4IJ's formatting and completion paths are verified to drop stale results, or are wrapped.
- **Exit criteria and verification:** IntelliJ ports of the VS Code extension's scenarios, over a **real transport**:
  - a valid digest applies, while a missing or wrong digest refuses the whole edit;
  - an edit computed before the user typed is refused;
  - an edit to a closed target is refused;
  - a multi-file rename applies atomically or not at all;
  - a rename of a name the ABI manifest records asks first;
  - stale formatting and completion are dropped;
  - quick fixes never apply on save.

  Then JOUST lists IntelliAda as a tested client (D6's amendment, item b).

### M5: moved to ADR-005 in r2

Mainnet is defined in ADR-005.
## 7. Compatibility

- **IDE floor:** the plugin's floor is plugin-wide (`sinceBuild`, F1). The JOUST features follow whatever floor PR #8 ships (2026.1 today). Q10 is decided: the question moves to #13.
- **PyCharm and WebStorm:** the branch's mandatory `com.intellij.modules.java` dependency (F1) already excludes them (#13).
  - Part A needs no Java plugin.
  - Part B's Gradle actions and D10 need the Java and Gradle plugins.
  - ADR-005 needs both.
- **JOUST versions:**
  - **The language server:** the user's installed `joust-lsp` (any version with `--storage`, F14).
  - **Project tasks:** whatever the project's plugin provides. IntelliAda hides an action whose task is missing.
- **Data:** the only new state is application-level settings (D4, D5's consent and D3's build-on-save setting). There is no key material and nothing under `.idea/`.

## 8. Risks

| Risk | Mitigation |
|---|---|
| A hostile repository runs code through IntelliAda | D1, D4, D5's consent, I1–I3, I25, I26, the sentinel tests |
| A hostile or buggy server edits files or opens links | D3a, I5, I15, the fake-server tests |
| The bundled grammar drifts from JOUST's | I6 |
| JOUST's dev-host API never materialises | this ADR does not depend on it; ADR-005 waits |
| An LSP4IJ upgrade changes capabilities or client behaviour | D3a's client-side enforcement; I5's tests run on every LSP4IJ bump (Q11) |

## 9. Open questions

All twelve r1 questions are decided, and §11 records the decisions. Q3–Q7, Q12 and Q13 govern ADR-005 and are kept here
for traceability. Questions r2 raised are listed below with the decisions taken on them.

- **Q1. Bundle or detect Node and `joust-lsp`?** **Decided: (a), detect and confirm; never bundle.**
- **Q2. Editing parity (M4)?** **Decided: (a), read-only until M3 ships, then revisit.**
- **Q3. Mainnet and public testnets.** **Decided:** support them, each behind an explicit opt-in. Testnets ship in M3 and mainnet in M5. Mainnet signs with `PasswordSafe` accounts behind a mandatory review, plus an export path for external signing (D14a). This supersedes r1's hard refusal.
- **Q4. The integration surface on the JOUST side.** **Decided: (b), a new JOUST dev-host ADR,** whose protocol must meet D11a's H1–H15. M3 waits for its release.
- **Q5. Co-signers.** **Decided: yes, through Q4's ADR** (`cosigners`, H4). They are unavailable until it ships.
- **Q6. Chain history and events beyond the session.** **Decided: (a), session history only in M3.** H17 (`history`) is for later.
- **Q7. DevKit time, snapshot and rollback controls.** **Decided:** Yano gets the full controls, and DevKit the faucet only. DevKit's other controls are enabled without a new ADR once M3 verifies its admin API.
- **Q8. The project engine in the language server.** **Decided: (a), per-project consent bound to the engine's digests.** Build on save is off by default. **r3 (L19, delegated):** the consent waits for `joust-lsp` to enforce digest pins (ADR-0021 amendment item f), so M1 ships advisory only (D5).
- **Q9. Grammar distribution and `npm create joust`.** **Decided:** copy the grammar with a sync test, and offer the native `joust new` only.
- **Q10. IDE floor.** **Decided:** follow PR #8's floor; the question moves to #13.
- **Q11. LSP4IJ upgrade.** **Decided:** a separate change, gated by I5's and D3a's tests.
- **Q12. The account model.** **Decided:** a class-tagged store in `PasswordSafe`, used by the run panel; classes are enforced across IntelliAda; legacy accounts are migrated explicitly (D13a).
- **Q13 (r2). Chain reads for the host.** Through IntelliAda (H10), or an endpoint URL handed to the host? **Delegated decision: through IntelliAda** (§11).
- **Q14 (r2). Split Part C into its own ADR (DES-11)?** **Delegated decision: split** into ADR-005, a draft that becomes Proposed once JOUST's dev-host ADR exists (§11, L16). Both advisors chose this independently, against the author's first lean.

## 10. Related findings (out of scope)

- **R1 (#10).** The account store's encryption key sits beside the ciphertext (`.cardano.account.conf.key`), and the store falls back to plain text when no key can be made, so mnemonics are effectively unprotected at rest (`account/cache/GlobalCache.java:54-75`, `:194-224`). D13a starts the fix.
- **R2 (#11).** `JulcCompileService` runs a project's `./gradlew` or `./mvnw` without a trust check (F3). D1 would apply.
- **R3 (#12).** `AikenSDKState.getState()` writes `<sdk>` elements but `loadState()` reads `localSdk` children, so configured Aiken SDKs may not survive a restart (`aiken/configuration/service/AikenSDKState.java:40`, `:56`; also on `master`).
- **R4 (#13).** `CLAUDE.md` and `README.md` say "Min IDE 2024.2", LSP4IJ 0.7.0 and support for PyCharm and WebStorm. The branch builds for 2026.1 with LSP4IJ 0.10.0 and a mandatory Java module dependency (F1).
- **R5 (#14).** Constructing `YaciBaseService` through its project constructor starts a local DevKit or Yano as a side effect (F8). D14 binds the panel to the explicit-node constructor.
- **R6 (joust#141).** JOUST's server recognises VS Code by an `initializationOptions` claim (`server.js:242`), which any client can make. D6's amendment item (a) replaces it with a declared capability. This is a JOUST finding, for JOUST's maintainers.
- **R8 (new in r2).** `RemoteNodeState` stores each node's `authKey` (a Blockfrost project id) in plain text in `cardano-remote-nodes.xml` (`configuration/service/RemoteNodeState.java:22`, `:52`). Trusted project code can read it, so IntelliAda can promise only never to *transmit* it (ADR-005 H10). `PasswordSafe` would protect it.
- **R7 (new in r2).** `CreateAccountAction` prints a new account's mnemonic to the Cardano Log console (`account/action/CreateAccountAction.java:75`), and `ListAccountDialog` offers every `addr_test` account on every non-mainnet network (`:414-417`, `:486-489`).

## 11. Decision log

These are decisions taken on this ADR, with who took them.
- **Maintainer:** answered by the maintainer.
- **Delegated:** taken by the authoring agent, after consulting two independent advisor agents, while the maintainer asked
  not to be consulted until the morning of 2026-11-20. The maintainer ratified them by accepting this ADR on 2026-11-19.

| # | Decision | By | Date | Basis |
|---|---|---|---|---|
| L1 | Q1: detect and confirm Node and `joust-lsp`; never bundle | maintainer | 2026-11-18 | walkthrough |
| L2 | Q2: read-only until M3 ships | maintainer | 2026-11-18 | walkthrough |
| L3 | Q3 (r1): devnets only, mainnet a hard refusal | maintainer | 2026-11-18 | walkthrough; superseded by L4 |
| L4 | Add testnet and mainnet support: testnets in M3, mainnet in M5, each by opt-in; mainnet signs with `PasswordSafe` accounts plus external signing | maintainer | 2026-11-19 | "also add testnet and mainnet support"; follow-up answers |
| L5 | Q4: a new JOUST dev-host ADR; M3 waits | maintainer | 2026-11-19 | walkthrough |
| L6 | Q5: co-signers through the host ADR | maintainer | 2026-11-19 | walkthrough |
| L7 | Q6: session history only in M3 | maintainer | 2026-11-19 | walkthrough |
| L8 | Q7: Yano gets full controls; DevKit the faucet until verified | maintainer | 2026-11-19 | walkthrough |
| L9 | Q8: the project engine by per-project consent bound to the engine's digests; build on save off | maintainer | 2026-11-19 | walkthrough (reframed by SEC-1, DES-2, CDX-1) |
| L10 | Q9: copy the grammar; native `joust new` only | maintainer | 2026-11-19 | walkthrough |
| L11 | Q10: follow PR #8's floor; moved to #13 | maintainer | 2026-11-19 | walkthrough |
| L12 | Q11: the LSP4IJ upgrade is separate and test-gated | maintainer | 2026-11-19 | walkthrough |
| L13 | Q12: class-tagged `PasswordSafe` account store | maintainer | 2026-11-19 | walkthrough |
| L14 | Q13: the host reads the chain through IntelliAda (ADR-005 H10). H10 states the property, and JOUST's ADR chooses the mechanism; the rationale is the single node binding, not credential secrecy (R8) | delegated | 2026-11-19 | two advisors (maintainer and security lenses), both chose this; see the PR comment |
| L15 | Q14 (author's first lean): keep Part C here as provisional | delegated | 2026-11-19 | **superseded by L16** |
| L16 | Q14: split Part C into ADR-005 (a draft) with IDs unchanged; ADR-004 covers Parts A and B | delegated | 2026-11-19 | both advisors independently chose the split |
| L17 | ADR-005 signing defaults: validity at most 900 slots from the bound node's tip (2 h for exports); fee cap 3 ADA on every class (a setting with a 10 ADA ceiling); collateral at risk at most 5 ADA, counting the full input value when `total_collateral` is absent; a creation's locked lovelace allowed in the outflow; an escalation always shows the modal | delegated | 2026-11-19 | both advisors; JOUST `verify.js` and `StateRuntime` defaults |
| L19 | Q8 amended: the project engine is gated on a JOUST-side digest pin (ADR-0021 amendment item f). M1 is advisory only. IntelliAda-side watching was rejected | delegated | 2026-11-19 | both advisors chose (a) independently; SEC-11, DES-12, CDX-1 |
| L18 | Sequencing: no M1 implementation until the maintainer accepts ADR-004, even after three reviewer approvals. Only read-only preparation in the scratchpad (G1.1 checks, an M1 task list) | delegated | 2026-11-19 | both advisors; the maintainer's rule "design only until I accept the ADR" |
| L20 | Accept ADR-004 (r6), ratifying L14, L16, L17, L18 and L19; implement the milestones, each as its own PR reviewed by the same three reviewers | maintainer | 2026-11-19 | "ADR004 is accepted. You can start implementation and complete all milestones and review with reviewers" |

## 12. Implementation refinements (M1, r7)

M1's three-reviewer process (PR #19) found surfaces this ADR did not name. Each refinement below tightens a decision
without changing it; none relaxes an invariant.

| # | Refines | Refinement | Found by |
|---|---|---|---|
| E1 | D3a item 4, I18 | IntelliAda opens a server-named file only in a **text editor** (`FileEditorManager.openTextEditor`), never `OpenFileDescriptor.navigate()`, which hands native file types to their associated application. Binary, native and generated files are refused, and the real-path check runs again at the moment of opening | M1-CDX-1, M1-CDX-2 |
| E2 | D3a item 7, I18, I25 | "Inside the project" is decided by **file identity** walking up from the real path, not by spelling. Remote, UNC and device URI forms are refused from the URI text before any file-system access | M1-CDX-3, M1-SEC-2 |
| E3 | D3a, I15 | LSP4IJ shows the error message of a request it issued itself (folding, semantic tokens, …) as an HTML notification. IntelliAda therefore re-frames the server's stdout: headers are normalised (one `Content-Length`, a UTF-8 `Content-Type` at most), every body is read by IntelliAda's own strict RFC 8259 reader (no trailing commas, single quotes, unquoted names, NUL, duplicate members or trailing input) within budgets enforced while reading (nesting 64, 100,000 containers, 2,000,000 values), then re-serialised and re-serialised as canonical UTF-8 JSON, error messages are escaped and capped, log and show messages and folding placeholders become literal text; anything that does not parse ends the stream | M1-SEC-1, M1-CDX-22 |
| E4 | D3a item 1, I5 | The server's **editing capabilities** (completion, code actions, code lens, execute command, formatting, rename, document links, inlay hints, signature help) and **workspace file operations** are stripped from its initialize answer before LSP4IJ sees them, and IntelliAda declares no file operations. LSP4IJ would otherwise apply a `willRenameFiles` edit directly | M1-DES-1 |
| E5 | D3 commands, I15 | **Show generated Java** opens the server's text as read-only **plain text**, not as a Java file, because Quick Documentation renders Javadoc as HTML | M1-SEC-4 |
| E6 | D3a, I15 | Progress titles and messages, document-symbol names, log messages and folding placeholders are cleaned and capped, with every character the server wrote kept, and a zero-width space put in front of text that would start with `<html` so no Swing component takes it for markup. The Ctrl+hover hint of a navigation target is escaped by IntelliAda | M1-CDX-13, M1-SEC-3 Exception: progress titles reach an HTML tooltip, so their `<` and `>` become `‹` and `›`. |
| E7 | D3 commands | `joust/status` is taken from the server's **pushed** notification, not requested: joust-lsp uses the same method name for both, and LSP4J keeps one method table for both directions | implementation |
| E8 | D3, D4 | Signatures crossing into LSP4J use plain `java.util.Map`, never Gson types (IntelliAda bundles its own Gson). A refused start gives LSP4IJ a fixed message with no paths; the details go in IntelliAda's escaped notification | implementation, M1-CDX-7 |
| E9 | D1, D5, I4 | Restarts and stops keep the user's enablement: only a running server is restarted, and a stop for a temporary conflict never disables the server | M1-CDX-6, M1-DES-5 |
| E10 | D4 | Node's real target must be a node binary (`node`, `node.exe`, `nodejs`) in this host's native executable format (Mach-O `MH_EXECUTE` with load commands, ELF executable or shared object with program headers, or PE with an executable image, each table bounded inside the file); joust-lsp must be in the global npm layout (`lib/node_modules/@julclang/joust/bin/joust-lsp.js`); `npx`, `npm`, `corepack` and script shims are refused. The storage directory is resolved through its nearest existing ancestor's real path before anything is created. The project-open check tests joust-lsp's execution target, not only the selected launcher | M1-CDX-4, M1-CDX-5, M1-CDX-23 |
| E12 | I21 | IntelliAda's own files outside projects (the TextMate bundle, joust-lsp's storage) are written only to a destination resolved through its real ancestors and checked against every open project before anything is created | M1-CDX-28, M1-CDX-4 |
| E11 | D3a item 7, I18 | Diagnostics are accepted only for a local `file:` document the server has open; an unsolicited or remote URI is dropped before any lookup. On Windows, where path equality folds case, an ancestor must spell the project base exactly | M1-CDX-24, M1-CDX-3 |

### 12.1 Implementation refinements (M2, r12)

M2's review (PR #22) refined D7–D9 the same way.

| # | Refines | Refinement | Found by |
|---|---|---|---|
| E13 | D1, I2, I20 | An action is bound to a **run guard** taken when the user invokes it: the project, trusted, in the current task generation. Every launch and every continuation after a dialog (review, confirm) checks it; losing trust or closing the project ends it, and trusting again does not revive it. A run is registered before its process or Gradle task exists, and a cancellation that arrives first is applied as soon as the real handle does | M2-DES-2, M2-DES-3, M2-CDX-2, M2-CDX-3 |
| E14 | D7 | Gradle tasks run in a freshly built execution environment that keeps nothing IntelliJ copies from the project's Gradle run-configuration template (**before-launch tasks, run target, debug and run-as-test flags**), through public APIs only (the execution events, the run's process handler and its external-system task); a wait that times out or is interrupted cancels the task before IntelliAda lets go of it | M2-CDX-3, M2-CDX-4, M2-SEC-6, M2-DES-7 |
| E15 | D8, I8 | Review is bound to the **selected `.jsc`**: the contract is its qualified name under the contracts directory, and the diff must name exactly that contract, that source file (by real path) and the manifest beside it. Before accepting, the baseline is checked again; an explicit refusal of the contract by the bound task is a refusal whatever the files show; an incomplete baseline inventory stops before accept; afterwards, the manifest went from the reviewed baseline to the new hash, and an inventory of every manifest, taken from a fresh scan before and after, must be complete and otherwise unchanged. The diff is exchanged in IntelliAda's own verified temporary directory | M2-CDX-5, M2-CDX-6, M2-CDX-7, M2-SEC-3, M2-SEC-5 |
| E16 | D7, I18 | A file's owner follows JOUST's own rule (`js/joust/src/project.js`): the nearest npm root or Gradle build walking up, npm first in one directory; a settings file naming the plugin makes its build's root a JOUST module. Contracts and generated directories (Gradle and npm, `build/joust/generated`) are used only when their real path is exactly where their owner says; console links are resolved again when clicked and checked once more on the EDT right before opening; a console started for one npm root resolves within that root | M2-CDX-9, M2-CDX-12, M2-CDX-14 |
| E17 | D8 step 1 | A JSC0002/JSC0003 failure from any Gradle run is attributed to the task whose output it was in (else to a failed JOUST task), names that module by its Gradle path, and is published only within the trust generation its run started in; a module where Review cannot run gets the reason instead of the action; when the output does not say and the source is in several modules, the notification names them and offers no action | M2-DES-5, M2-CDX-13 |
| E18 | D9, I7 | The wizard refuses a `PATH` directory containing the path separator and checks that the child's `PATH` splits into exactly the validated directories; its temporary directory (the child's `TMPDIR`) is IntelliAda's own, verified outside every project | M2-CDX-10, M2-CDX-11 |
| E19 | D3a, I15 | Every task and progress title built from project text (module and file names) uses `‹` and `›` for angle brackets, like E6's progress titles | M2-SEC-1 |

LSP4IJ 0.10.0's `EditorFeatureManager` clears the inlay-hint cache on a pooled thread when a server starts or stops,
which IntelliJ 2026.1 reports as a soft threading assertion. It is a defect of the pinned dependency, tracked for the
LSP4IJ upgrade (Q11).

## Revision history

- **r1** (2026-11-18): initial proposal.
- **r2** (2026-11-19; responds to round 1's design, security and Codex reviews of `d8eaff6`).

  **Trust and the project engine:**
  - **SEC-1, DES-2, CDX-1:** D5 now needs per-project consent bound to the engine's digests (I26). D1, T1, I1 and I2 name it as the single exception, I1 is scoped to IntelliAda, and build on save is off. `workspaceFolders` are confined (I25). The sentinel tests now cover untrusted, trusted-without-consent and trusted-with-consent projects.
  - **DES-3:** D1 now uses only the public `isProjectTrusted`, and F13 records the internal annotation and the implicit-trust cases.

  **Read-only and rendering:**
  - **SEC-2:** new D3a (client-side read-only and plain-text enforcement); I5 and I15 widened; F30.
  - **CDX-11:** I5's real-server test now declares `failureHandling`.
  - **CDX-12:** the M4 gate requires wire-level `joustExpected` preservation (F36).

  **Networks and signing:**
  - **SEC-3, CDX-3:** positive network identity (D14), SanchoNet refused (F37), HTTPS for allow-listed hosts, and the session bound to an explicit `RemoteNode`.
  - **SEC-4, CDX-2:** D13's checks are now an allow-list with an outflow bound (I23), backed by negative tests with recomputed hashes.
  - **SEC-5:** new D13a; I12 enforced across IntelliAda.
  - **SEC-6:** I10 extended to IntelliAda's own surfaces; R7.
  - **CDX-5:** §2 now states that trusted project code runs as the user, and I10 is scoped to what IntelliAda transmits.
  - **CDX-8:** H8 (bounded `publish`) and D13 check 7.
  - **DES-9:** F35 names the signer, and G3.2 checks byte identity.

  **The dev host:**
  - **DES-1, CDX-16:** new D11a (H1–H17). G3.1 now binds this ADR to it, and G3.6 checks Yano time.
  - **SEC-7:** H13 and I22; T3 and I7 reworded; the launch spec is kept in memory; recorded instances moved out of `.idea/`; Advance to slot N bounded.
  - **DES-5, CDX-6:** the build fingerprint (H1, I24) and restart on build-output change.
  - **CDX-7:** keyed calls are separated from Q5 and moved to M3c (H16).
  - **DES-10:** the admin endpoint gets a home, and the explicit-node service starts nothing.

  **Language and project support:**
  - **DES-4:** gutter icons through `LineMarkerProvider` on `textmate`, with `joust/entryPoints` requested (F34).
  - **CDX-10:** the bundle's `package.json` gains `name`, and the licence is included.
  - **CDX-4:** D4 no longer runs `npm prefix -g`; the wizard gets a built environment; the `.bin` check covers real targets.
  - **SEC-8:** Windows package layout; never `.cmd`.
  - **CDX-9, SEC-9:** ABI review and acceptance bound to a module's qualified task and validated arguments.
  - **SEC-10:** real-path confinement (I18).
  - **CDX-13:** npm detection follows JOUST's rule, and `-p` is passed for Gradle only.
  - **CDX-14:** F28 and D10 corrected (JULC0087; `Arrays.equals`), and an explicit julc-annotator guard.

  **Tests, gates and scope:**
  - **CDX-15:** F26 corrected (211 transactions, 57 unbounded); G3.2 and G3.4 reworked.
  - **DES-6:** §6.0 traceability table, and I21 for T8.
  - **DES-7:** G3.0.
  - **DES-8:** §7 corrected, and Q10 moved to #13.
  - **DES-11:** Part C marked provisional (Q14).
  - **F5:** corrected (Yano panel port).

  **Maintainer decisions and new scope:** maintainer decisions on Q1–Q12, and the testnet and mainnet scope (new D14a and M5). §11 is the decision log.

  **Split:** after a consult with two advisors (L14–L18), Part C (D11–D16, D11a, D13a, D14a, the moved invariants, M3 and M5) moved to ADR-005 with its IDs unchanged, and ADR-004 now covers Parts A and B. The round-1 findings on Part C are answered in ADR-005 (its §8 maps them). New R8.
- **r3** (2026-11-19; responds to round 2's reviews of `78f6328`).
  - **SEC-11, DES-12, CDX-1:** the project engine is now gated on a server-side digest pin (L19, ADR-0021 amendment item f); M1 is advisory only; I26 restated.
  - **SEC-2 remainder, CDX-17:** feature allow-list with a guard test; IntelliAda renders hover itself as plain text.
  - **SEC-12, CDX-19:** I25 now applies to all three folder paths.
  - **CDX-18:** `none` is allowed only for the baseline, and displayed manifests are re-hashed before Accept.
  - **CDX-20:** §2's devnet-key claim restated as an explicit assumption.
  - **DES-13, CDX-21:** I20 covers trust loss and the cancellation of running actions.
  - **CDX-22:** I21 is scoped to IntelliAda's own writes.
  - **CDX-4 remainder:** D4's rule applies to the executables the wizard's CLI picks.
  - **CDX-14 remainder:** F28 and D10 use the pinned JuLC's codes (JULC0086, JULC0087).
  - **SEC-14:** location checks run before `node --version`.
  - **SEC-13** and the CDX-2 note are recorded for ADR-005 (P3, draft).
- **r4** (2026-11-19; responds to round 3's reviews of `8ca8872`; design APPROVED at round 3).
  - **SEC-15:** pins travel in a new flag that replaces `--project-engine`, so an older server stays advisory (fail closed); I3 and I26 updated.
  - **CDX-23, SEC-16c:** new D3a item 7 confines navigation, with tests.
  - **SEC-16a:** one read-only set R for features and registrations.
  - **SEC-16b:** recorded that LSP4IJ 0.10.0 sends no `didChangeWorkspaceFolders`; the upgrade gate re-checks it.
  - **CDX-24:** D3's sentence narrowed.
  - **CDX-25:** `node --version` runs only on explicit configuration, and server start checks the binary's identity without executing anything; I1 updated.
  - **CDX-1 remainder, DES-14:** T1's exception is post-pin only, and the M1 sentinel test expects no engine marker in any case.
- **r5** (2026-11-19; responds to round 4's reviews of `2477d3e`; design APPROVED at round 4).
  - **SEC-18:** new D3a item 8 renders diagnostics as plain text, and F30 is corrected (only the message was escaped).
  - **CDX-23 remainder:** M1 navigation is project-only; the package, its storage and `joust-lib:` targets are dropped.
  - **CDX-26:** D3's command line is I3's, with no bare `--project-engine`; I4 and its test say "no engine flag of either kind" in M1.
  - **SEC-17:** the illustrative flag is renamed `--engine-pin`, with a gate test across the accepted `joust-lsp` versions.
- **r6** (2026-11-19; responds to round 5's reviews of `82890ab`; design and security APPROVED at round 5).
  - **CDX-27, DES-15:** the server's href is never opened; Open documentation uses IntelliAda's own catalogue URL and a validated `JSC` code.
  - **SEC-19:** T6 and I18 are scoped to surfaces IntelliAda renders; IntelliJ's console filters are accepted.
  - **SEC-20:** only the tooltip is escaped, and `getMessage` stays plain.
- **r7** (2026-11-19, after M1 review round 1 on PR #19): §12 E1–E10 record implementation refinements; no decision changed.
- **r8** (2026-11-19, after M1 review round 2): §12 E3, E10 refined; E11 added.
- **r9** (2026-11-19, after M1 review round 3): §12 E6 refined (text kept, HTML detection defused); E12 added.
- **r10** (2026-11-19, after M1 review round 4): §12 E3 and E10 refined.
- **r11** (2026-11-19, after M1 review round 5): §12 E3, E6 and E10 refined.
- **r12** (2026-11-19, after M2 review round 1): §12.1 (E13–E19) added; D7's npm diagnostic example corrected (M2-CDX-14).
- **r13** (2026-11-19, after M2 review round 2): §12.1 E14–E17 refined.
