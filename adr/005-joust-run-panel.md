# ADR-005: Running JOUST contracts from IntelliAda (devnets, testnets, mainnet)

**Status:** Proposed (r1). The draft (d1, d2) gave requirements to JOUST's dev-host ADR. r1 amends it to the protocol
JOUST built, as gate G3.1 requires, and is reviewed before any milestone that signs starts.

**Date:** 2026-11-25 (draft d1: 2026-11-19).

**Authors:** Claude Code, for the IntelliAda maintainers.

**Scope:** design only. This PR adds no code, build change or plugin.xml entry.

**Origin.** This is Part C of ADR-004 r2 (PR #9), moved here on 2026-11-19 (decision L16 in ADR-004 §11) so that Parts
A and B could be accepted on their own.
- **Kept from ADR-004:** decision IDs (D11–D16, D11a, D13a, D14a), invariant IDs (I9–I14, I16, I17, I19, I22–I24) and
  milestone IDs (M3, M3b, M3c, M5). New IDs continue from them (D17, D18; I25–I28; L23–L30).
- **Cited from ADR-004, not copied:** facts **F1–F37** (§1.1), the threat model **§2**, the rule **D1**, and the shared
  invariants **I15, I18, I20 and I21**.

**Builds on:**
- IntelliAda ADR-004 (accepted; M1 #19, M2 #22 and the LSP4IJ upgrade #25 are approved).
- **JOUST dev-host protocol 1** at JOUST `main` `01bad1f7` (2026-11-25): `docs/protocol/dev-host-1.md` and its JSON
  Schemas (normative), the client conformance kit (`docs/protocol/dev-host-1-conformance.md`), and JOUST ADR-0022
  (r8, the dev host; its §12 maps this ADR's H1–H17 to the protocol). JOUST ADR-0023 (`@Params` and `@AuthorizedBy`,
  protocol 2) is not implemented; this ADR uses protocol 1 only.
- JOUST ADR-0024 (the browser playground, a second client of the same host): a reference for the chain-read adapter
  and the journal (`js/joust/src/playground/devkit.js`, `journal.js`).

**Risk:** high. IntelliAda starts project processes, signs with keys it holds, and, behind opt-ins, spends real funds on
public networks.

## 0. What r1 changes, and the facts it rests on

JOUST merged its dev host (PRs #145–#176, landed in #189 on 2026-11-24) and its playground (#174, #178–#180). r1:
- replaces D11a's requirements table with the protocol's answer to each (§D11a);
- changes D11's launch to the protocol's: a `joustHostSpec` launch spec, a private snapshot, a digest (§D11);
- replaces D13's ten IntelliAda checks with **the protocol's normative client signing rules**, run by **JOUST's own
  reference checker, bundled in the plugin** (§D13, L24);
- adds the reservation journal, the recovery high-water mark and restore (D17), and the chain-read adapter (D18);
- amends I16 (L25) and L17's testnet escalation (L25);
- splits M3 into reviewable parts (§4, L27).

**Facts (r1), each checked at JOUST `01bad1f7` with `0.1.0-pre3-01bad1f-SNAPSHOT` published to the local Maven
repository** (`./gradlew publishToMavenLocal -Pversion=0.1.0-pre3-SNAPSHOT`; JOUST appends the commit):
- **F38.** A Gradle Counter made by that build's `joust new -t gradle -l jsc --joust-repository file://~/.m2/repository`
  runs `./gradlew build joustHostSpec` and writes `build/joust/host-spec.json`: `protocol` 1, `java` (the toolchain's
  JDK 25), `mainClass` `org.julclang.state.host.DevHost`, `digest`, and 74 ordered `launch` entries (`dir` entries with a
  `files` map of SHA-256s, `jar` entries with one `sha256`).
- **F39.** A scratch client (Python) copied the 74 entries into a snapshot, recomputed the digest from the copies
  (equal to the spec's), launched the host with protocol 1's arguments and an allow-listed environment, and sent
  `initialize` (devnet magic 42): the host answered protocol 1, `fingerprint` equal to the digest, capabilities
  `views, objects, quorum`, read kinds `utxos, output, tip, protocolParams, script, stakeRegistration`, engine version
  `0.1.0-pre3-01bad1f-SNAPSHOT`, and the contract with its spend and mint hashes. `describe` listed `init`,
  `increment`, `reset`, `close` and the view `count`, all `served`, with the `.jsc` message map. `shutdown` and `exit`
  ended it with exit status 0.
- **F40.** The conformance kit is `org.julclang:julc-state-host:<v>:conformance` (4.9 MB jar, 365 entries): the
  reference checker `org.julclang.state.host.conformance.ReferenceChecker` (`check(JsonNode)` → a list of violation
  codes; `main(file)` prints them as JSON and exits 0 or 1), a scripted `FakeHost`, recorded sessions (`sessions/`,
  including seven `violation-*` transport faults) and **333 verifier vectors** (`verifier/`). Its classes are Java 25
  and need `julc-state-runtime`, `julc-state-serve`, `julc-vm`, JuLC and CCL 0.8.0-pre8 on the class path.
- **F41.** A verifier vector's client-side inputs are `request`, `networkId`, `maxFee`, `anchors` (`description`,
  `language`, `policy`, `address`, and `outputVerifier`/`mapVerifier` each with `language`, `hash`, `address`),
  `resolved` (node UTxOs by `txHash#index`), `node` (holder scripts and registrations), `protocolParams`; only `result`
  comes from the host.
- **F42.** The anchors are in the build output: `META-INF/joust/<pkg>.<Contract>.json` (the description) and
  `META-INF/plutus/<Validator>.plutus.json` in the contract's class directory, and the shared verifiers'
  `META-INF/plutus/OutputVerifier.plutus.json` and `MapVerifier.plutus.json` in the `julc-state-verifier` jar.
- **F43.** IntelliJ IDEA 2026.1 (build 261.22158) ships JBR **25.0.2**; 2025.2.x ships JBR 21. IntelliAda's floor is
  2026.1 (`sinceBuild = "261"`, commit `57ddbc0`), so a released IntelliAda always runs on a Java 25 runtime.
- **F44.** The playground's DevKit adapter answers every read kind from Yaci Store's Blockfrost-style API (`/addresses/
  {a}/utxos`, `/txs/{h}/utxos`, `/blocks/latest`, `/epochs/{e}/parameters` (after `/epochs/latest`), `/scripts/{h}/cbor`, `/accounts/{s}`,
  `/assets/{u}/transactions`, `/txs/{h}/redeemers`, `/scripts/datum/{h}/cbor`), reading the tip before and after each
  answer and retrying when the chain moved (`devkit.js` `chainReads`).

**Two questions this ADR adds to ADR-004 §2:**
- **Chain reads.** The host reads the chain only through IntelliAda (D18), so a node endpoint and its credential are
  never transmitted to project code. Trusted project code runs as the user and can read IntelliAda's configuration,
  including a Blockfrost `authKey` held in plain text today (ADR-004 R8); D18's reason is D14's single node binding.
- **Funds.** On public networks the assets include real funds. D14a's opt-ins, the mandatory mainnet review and the
  protocol's refusals are the controls.

**Trust (as JOUST ADR-0023 states it, 2026-11-23):** the chain provider, the wallet (IntelliAda) and the client's journal
are trusted, as in any CCL or MeshJS application. The **host is not**: every transaction it returns is checked (D13)
against anchors IntelliAda reads itself.

## 1. Context

The maintainer asked for a way to deploy and call a JOUST contract **directly from its code in the editor**: first on a
local DevKit or Yano devnet, then on public testnets and mainnet behind explicit opt-ins (ADR-004 §11, L4). On
2026-11-25 the maintainer asked to build it now, against a local build of JOUST main (L23).

## 2. Decision

### D11. Where off-chain execution runs: the project's JOUST dev host, in its own process

The candidates (unchanged from d1): the project's JVM through a dev host; JOUST's prepare service; the JS engine; and
IntelliAda's in-IDE CCL. **Decision: the dev host** (JOUST ADR-0022), the project's own JOUST runtime in a separate
process, speaking protocol 1 over stdio. IntelliAda is the wallet, the chain client and the submitter; it never builds a
transaction. In-IDE CCL, `JoustServer` over HTTP, `joust dev`, loading JOUST into the IDE JVM and an IntelliAda-written
launcher stay rejected for d1's reasons.

**Launch (Gradle, M3; protocol 1 "Launch and framing"):**
1. On the first run action in a trusted project (D1), IntelliAda runs `joustHostSpec` on the contract module through
   IntelliJ's Gradle integration (ADR-004 M2's runner), and reads `build/joust/host-spec.json` after success, with
   the strict JSON reader and a size cap (1 MiB).
2. **Validation:** `protocol` 1; `mainClass` exactly `org.julclang.state.host.DevHost`; `java` an absolute path to a
   regular, executable file; ordinals contiguous from 0; absolute entry paths; `kind` `dir` or `jar`; SHA-256s as 64
   lowercase hex; relative paths POSIX, without `.`/`..` segments, a leading `/` or a control character.
3. **Snapshot:** IntelliAda copies every entry into a **private snapshot** under the IDE system directory
   (`<system>/intelliada/joust/snapshots/<project id>/<digest>/`), opening each source **without following links**
   and copying only regular files under a deadline; it hashes **the copies**, compares each to the spec, recomputes the
   digest by the protocol's fingerprint format, refuses a mismatch, makes the snapshot read-only, and keeps it until
   the host exits.
4. **Anchors in memory (I26):** before launch, IntelliAda reads the D13 anchors from the snapshot copies into memory:
   every description and `.plutus.json` (F42), and the shared verifiers from the snapshot's `julc-state-verifier` jar.
   The verifier never re-reads the snapshot from disk afterwards: the host runs as the same user and could rewrite it.
5. **Confirmation:** the first start in each IDE session shows the project, the `java`, the main class and the
   JOUST version (D1).
6. **Process:** `GeneralCommandLine(java, -XX:+DisplayVMOutputToStderr, -Xlog:disable, -Xlog:all=warning:stderr,
   --enable-native-access=ALL-UNNAMED, -cp <snapshot entries in ordinal order>, org.julclang.state.host.DevHost,
   --stdio)` from the project root, with the protocol's environment allow-list (PATH, HOME, LANG, LC_*, and on Windows
   USERPROFILE, SystemRoot, TEMP/TMP) and nothing else: no project JVM options, no `JAVA_TOOL_OPTIONS`.
7. **`initialize`:** `protocol [1]`; the bound network (D14) with `mainnet:true` only after M5's gate; the node's clock
   (genesis start, slot length, zero slot); `time {maxTtlSlots, settlementSlots, maxNowAheadSlots}` (L30);
   `limits {callDeadlineMs 60000, readsPerCall 256, readBytesPerCall 32 MiB}`; `build {snapshot, digest}`;
   `restoring` per D17. IntelliAda refuses a host whose `protocol` is not 1, whose `fingerprint` differs from the
   digest it computed, or whose contract list or script hashes disagree with the anchors (I24).

**Lifecycle (I20, I24):** started on the first run action, one host per contract module and bound node; stopped on
project close, trust revocation, a change of the bound node or its identity, **Stop** in the panel, a protocol
violation, or process exit (the session state is then lost, and D17's restore applies at the next start). A rebuild
does not change what runs (the snapshot is pinned); the panel marks the host **stale** when the module's build output
changes, and Deploy or a call from a stale host first offers a restart. The host's stderr is drained continuously into a
capped "JOUST host" console; stdout carries only frames.

**npm (M3b):** `node node_modules/@julclang/joust/bin/joust-host.js --stdio` from the project root, after checking the
real paths of the package, the entry and the runtime; `build.snapshot` is the project root and the digest covers the
closure the protocol lists. Node must be the validated Node from ADR-004 D4; `NODE_OPTIONS` is never passed.

### D11a. The JOUST protocol against this ADR's requirements (r1)

| H | Requirement (d2) | Protocol 1 | Status |
|---|---|---|---|
| H1 | versions, protocol, contracts, script hashes, fingerprint | `initialize` result (F39) | met; build version is null on Gradle |
| H2 | `describe` | `describe`: description, messages (null for Java), `calls` with `served` or an `unsupported` code | met |
| H3 | `deployment`; `@Params` unsupported | `deployment(now, contract)`: addresses, policies, script hashes, reference scripts, governors (empty); `@Params` → `unsupported/params`, `@AuthorizedBy` → `unsupported/authorizedBy` (ADR-0023 is protocol 2) | met; governors later |
| H4 | `prepare` with predicted effects, co-signers, designation | `prepare(now, ttlSlots, call)` → `prepared` (reservation, cbor, txHash, validFrom, validTo, signers, plan) and `predicted` (state, objects, events, payouts, sends); `cosigners` up to 64 on quorum calls; `designated` and `operation` on keyed calls | met |
| H5 | `view` | `view(now, contract, instance, view, args)`; named views on the JVM host only | met (Gradle); npm state only |
| H6 | `instances`, `objects` | paged over a listing snapshot (limit 1–500; `bad/listingGone` restarts) | met |
| H7 | `decode` | may answer `ambiguous` | met |
| H8 | bounded `publish` | `prerequisites`, then `publish(now, ttlSlots, contract, prerequisite, address)` per component; `registered` asserts a credential | met |
| H9 | time from the client | `now` per call, never decreasing; `reset` re-baselines | met; L30 |
| H10 | chain reads through the client | `chain/read` reverse requests, nine kinds, only during a call, under per-call caps | met; D18 |
| H11 | `settled` | by reservation id with the observed tip; `discarded`; thresholds on observed tips only | met; D17 |
| H12 | cancellation, deadlines | `$/cancelRequest`; host deadline; client hard timeout | met |
| H13 | framing, stdout redirected | Content-Length frames ≤ 4 MiB, header ≤ 1 KiB, 10 s per frame; fd 1 diverted on Linux and macOS | met; Windows relies on the client's strict parser |
| H14 | structured errors | `data {kind, code, message, sites, retryAtSlot}`; branch on kind/code only | met |
| H15 | `reset(now)` | `reset(now, clock?, rewound?)`, keeps reservations | met |
| H16 | keyed designation | `call.designated` + `call.operation` with `attempts`; `needsDesignated`, `pending`, `mayHaveLanded`, `cooldown` | met; M3c |
| H17 | history | none | not required (L7) |

### D12. Which JOUST runs: the project's, never IntelliAda's

- **The host and every task are the project's** (Gradle: the plugin version pins the runtime, processor, JuLC and CCL;
  npm: `package.json` and the lockfile). A script's hash depends on the JuLC version that compiled it.
- **IntelliAda bundles no JOUST runtime that builds transactions** (I9). It does bundle JOUST's **reference checker**
  (L24), which builds nothing and only judges a transaction; it runs out of process (D13).
- IntelliAda shows the host's versions and refuses a protocol other than 1.
- **Before Deploy,** IntelliAda offers to run the build when a contract source is newer than its description.
- **The language server's compiler** (ADR-004 D5) may differ from the project's. The run panel never uses it.

### D13. Accounts, keys and signing (rewritten in r1)

**Accounts.** The caller list shows only accounts of the session's network class (D13a, I12): on devnets also the
devnet's genesis accounts. Co-signers (quorum calls) come from the same class.

**Keys stay in the IDE process (I10).** The host receives addresses only. No mnemonic or private key appears in any
child's arguments, environment, stdin or files (the checker's included), nor in IntelliAda's consoles, notifications or
`idea.log`.

**What IntelliAda checks before signing or exporting (I11, I13, I23).** The protocol's **client signing rules**
(dev-host-1 "Client signing rules (D13, normative)": rules 1–8, the MerkleMap rules and the three publication shapes)
are IntelliAda's allow-list. They supersede d2's checks 1–10 and are a superset of them. **They are run by JOUST's
reference checker, bundled in the plugin (L24):**
- **The checker:** the `conformance` jar of the JOUST version IntelliAda was built against, with its runtime closure,
  in its own plugin directory (`joust-verifier/`), **off the plugin class path**. A manifest built into the plugin lists
  every file's SHA-256; IntelliAda verifies the files before each run and refuses on any difference.
- **The process:** the IDE's own runtime (`java.home`, JBR 25 on the 2026.1 floor, F43), never the project's `java`
  from the launch spec, which D13 distrusts. One process per check, with `-cp <the verified files>`,
  `ReferenceChecker <input file>`, an empty environment except `PATH`, a private temp input file (owner-only), and a
  deadline (30 s). The verdict is accepted **only** as exit status 0 with stdout exactly `[]`; exit status 1 with a JSON
  array of codes is a refusal naming them; anything else (a timeout, a crash, other output) is a refusal. The input
  holds no key material.
- **The input IntelliAda assembles** (F41), with only `result` from the host:
  - `method` and `request`: the exact forward method and parameters IntelliAda sent;
  - `anchors`: from the **in-memory** snapshot anchors (D11 step 4, I26): the contract's description and
    `.plutus.json` (language, hash; address and policy derived by IntelliAda for the bound network), the output and map
    verifiers from the `julc-state-verifier` jar;
  - `resolved`: every spending, reference and collateral input, read by IntelliAda through the bound node (D18) **at
    signing time**, never taken from the answers it gave the host;
  - `node`: script presence at the holder addresses and registration status, read the same way;
  - `protocolParams` from the bound node; `networkId` from D14; `maxFee` per L30.
- **IntelliAda's own checks on top** (they need IntelliAda state the checker does not have): the bound node's identity
  is positive for its class and the class's opt-in is on (D14, D14a); the caller and co-signers are accounts of that
  class (D13a); the session, host and binding that prepared the body are still current; the fee is within L30's user
  maximum.
- **The review modal** shows what IntelliAda derives from the **body** (outputs to addresses that are not the
  deployment's or the caller's, the caller's net outflow, the fee, the required signers, the validity window), never
  the host's `predicted`. A refusal is final: the modal cannot override it (L25). Devnet: off by default (setting
  **Confirm every devnet transaction**); testnet: on by default, can be turned off per testnet; mainnet: always.

**Signing (I11).** IntelliAda slices the received transaction's body bytes, hashes them (Blake2b-256), signs the hash
with the caller's **payment** key (CCL's Ed25519), and builds the signed transaction by **splicing**: the original
array header, the body bytes as received, a witness set made of the received witness set's entries **as raw bytes** plus
the new vkey witnesses, and the remaining elements as received. It never re-encodes the body, the redeemers or the
datums. It submits through the bound node and checks that the returned id equals `txHash`.

**External signing (D14a, M3.5):** export the unsigned transaction (after the same checks) and import a witness set; each
witness must verify against `txHash`, and its key must be a required signer or the payer's payment key.

**Calls the panel offers:** every call the checker can verify and the host serves: init, action, destroy, owned calls,
quorum calls (co-signers chosen from the class), calls with sends, map calls, and publications. **Keyed calls** wait for
M3c (D17's designation journal). `@Params` and `@AuthorizedBy` contracts show the host's `unsupported` reason.

### D13a. The account model (unchanged from d2; Q12 decided, L13)

- **A new account store in IntelliJ's `PasswordSafe`.** Each account is tagged with **one network class**: `devnet`,
  `testnet` or `mainnet`. The run panel uses only this store.
  - **`devnet` accounts are only ever created fresh** (or are the genesis constants), never imported (CDX-20).
  - **`testnet` and `mainnet` accounts** may be created fresh or imported, for that class only.
- **Legacy accounts** are migrated by an explicit action (which also starts fixing #10) and are never offered on
  mainnet.
- **Class tags hold everywhere** (I12): every account chooser hides accounts of another class; importing a mnemonic
  already held under another class is refused (compared by a key fingerprint).
- **Devnet genesis mnemonics** are IntelliAda constants, never read from the project or the node.
- **A mnemonic is shown only on explicit request,** never written to a console, notification or log (I10, R7).

### D14. Networks and network classes

- **Eligible nodes:** IntelliAda's own node configurations only (I14); never `joust.config.json`'s `networks` or
  `start`, nor Gradle properties.
- **The run panel session is bound to one explicit `RemoteNode`**, chosen in the panel. Its services start nothing
  (F8); a devnet starts only on the panel's **Start**. The binding serves every read (D18), faucet call, admin control,
  signature check and submission. Changing the bound node, its endpoint or its identity stops the host (D11).
- **Network identity is positive:**
  - **Devnet:** node type `YaciDevKit`, `LocalYaciDevKit` or `Yano`; a loopback or allow-listed (HTTPS) endpoint; the
    node's magic equals the devnet magic recorded in IntelliAda's configuration and is never a public network's
    (1, 2, 764824073, 4); the devnet admin API answers (DevKit `/local-cluster/api/admin/devnet`, Yano
    `/api/v1/devnet/…`); and the provider's genesis has the admin API's magic and start time (playground D-1).
  - **Testnet:** a Blockfrost configuration marked preprod (1) or preview (2), the node's reported magic equal to it.
  - **Mainnet:** a Blockfrost configuration marked mainnet (764824073), the node's reported magic equal to it (M5).
  - Any other magic is refused. Koios configurations are not offered (L26).
- **Chain identity** (for the journal, D17): the network magic **and** the genesis system start (devnets reuse magic
  42; a recreated devnet is a new chain).
- **The clock for `initialize`** comes from the bound node's genesis (start time, slot length) or the DevKit admin API.

### D14a. Public testnets (M3.5) and mainnet (M5), each by explicit opt-in

- **Opt-ins:** two application settings, off by default: **Allow the JOUST run panel on public testnets** and **Allow
  the JOUST run panel on mainnet**. Mainnet also needs a confirmation per panel session.
- **Endpoints:** IntelliAda's Blockfrost configurations over HTTPS; credentials stay in IntelliAda (D18).
- **Accounts:** testnet-class accounts on testnets, mainnet-class on mainnet; **Export unsigned transaction** and
  **Import witness set** on both.
- **Checks:** all of D13's; refusals are final on every class (L25); the modal is mandatory on mainnet.
- **No faucet, time, snapshot or rollback** on public networks. Session history only (L7).
- **Phasing:** testnets in M3.5; mainnet in M5 with its own gates.

### D15. The run panel: from the code to a network

- **Entry points:**
  - **`.jsc`:** gutter icons on `init`, actions, destroy and views (a `LineMarkerProvider` for `language="textmate"`
    filtered to `*.jsc`). Positions come from document symbols matched by name and `detail` (F34) until JOUST's
    `joust/entryPoints` exists.
  - **Java:** a `LineMarkerProvider` on methods annotated `@Init`, `@Action`, `@View`, `@Destroy`, `@OwnedInit`,
    `@OwnedAction` or `@OwnedDestroy` (`org.julclang.state.api`).
  - **A JOUST Run tool window** lists the contracts of the host's `initialize` and their `describe` calls, each with its
    `served` flag or `unsupported` reason.
  - Icons only open the panel. Nothing runs without D1.
- **Flow:** choose the network (D14) and, on a devnet, **Start** it; choose the caller (D13a); fund it (devnets: Yano
  `fundAddress`, DevKit's top-up); **Deploy** = `prerequisites`, then each `publish` in order (each signed, submitted and
  settled before the next), then the `init` call; call actions and views.
- **Forms from the description** in its JSON encoding (`x-joust.encoding.json`), as d2 lists: bytes as hex (with key
  hash pickers), integers as decimal strings, Bool by shape, Optional, lists, maps (canonical key order), sum types by
  `$variant`, `Data` as CBOR hex; `payable` lovelace and `takesAssets` rows (policies and names in the protocol's
  canonical order); owned calls pick an object (`objects`). **Forms never encode Plutus data** (I16): the host does.
- **Untrusted descriptions** have caps: 1 MiB, schema depth 32, 500 fields; `$ref` cycles refused; every label plain
  text (I15).
- **State:** the instance's state (`view` with a null view), its UTxO, owned objects (`objects`), and the **session
  history**: each call IntelliAda handed out, with method, arguments, transaction id, the D17 status, and the host's
  **predicted** effects labelled as predicted until the call is confirmed, after which the state is read again and the
  diff shown. A rolled-back call is never shown as confirmed.
- **Time, faucet, snapshots and rollback (devnets):** Yano gets time advance (slots, seconds, epochs), epoch shift,
  catch-up, named snapshots, restore and rollback; DevKit the faucet (L8). A time control only moves the chain forward,
  so the next call's `now` follows it (L30). A **snapshot restore or rollback** rewinds: IntelliAda persists the
  reconciliation, sends `reset` with `rewound:{tip}`, restores (D17), then re-checks each recorded instance (I17).
- **Persistence:** recorded instances per project and per chain identity (D14) in IntelliAda's state under the IDE
  system directory, not in `.idea/`, with no keys; validated as untrusted on load.

### D16. Results and errors

- **Success:** the transaction id, the fee, execution units per redeemer, the predicted effects (labelled), and after
  confirmation the new state and its diff.
- **Host errors** by `kind`/`code` only (never by message text): `refusal/require` shows the message with its `.jsc`
  `sites` as links, opened only inside the project's contracts directory (I18); `refusal/pending`,
  `refusal/prerequisite` (offers Deploy's next step), `refusal/cooldown` (shows `retryAtSlot`), `refusal/reconcile`,
  `refusal/deepRollback` (restart required); `unsupported/*` names the feature; `bad/*` and `internal/*` are shown as
  host or IntelliAda faults.
- **The checker refused:** "IntelliAda did not sign: <codes>", visually distinct from contract errors, each code with
  its protocol meaning.
- **The node refused:** the node's text, with any `#…` trace key matched against the message map.
- **Where:** the panel's result pane; the raw exchange (no key material, I10) in the Cardano Log console; the host's
  stderr in its own capped console. All text plain: HTML disabled, ANSI sequences stripped (I15).

### D17. The journal: reservations, settlement, the high-water mark and restore (new in r1)

The protocol makes these the client's duties (dev-host-1 "Reservation journal (D8)").
- **Journal store:** per chain identity (D14), in IntelliAda's state under the IDE system directory, written atomically
  (write-temp-then-move), schema-versioned, no keys. Each entry: the reservation id, `txHash`, the body's `validTo`
  (read from the body), the signed or unsigned CBOR, its handout kind (submitted, exported), and its status.
- **High-water mark (I25):** before handing a body out (submission or export), IntelliAda persists, per chain,
  `max(validTo + settlementSlots)` over every handed-out body, independently of the journal. Lower limits in a later
  session never lower it.
- **Settlement:** IntelliAda observes the bound node (the transaction's inclusion slot, the tip) and sends `settled`
  with the reservation, `txHash`, the status and the observed tip: `confirmed` with the inclusion slot, `rolledBack`,
  `discarded` for a body declined before handout or rejected by the node; `expired` only once the observed tip
  passes `validTo + settlementSlots`. Prepares on an instance with a live reservation get `refusal/pending`; the panel
  shows them as waiting.
- **Restart:** a new host after a crash or restart is initialized with `restoring:true` when the journal holds live
  bodies or a persisted rewind fence, and IntelliAda restores them (`restore` in batches, `complete:true` last). After
  losing history, it does not complete restore until an observed tip passes the persisted high-water mark.
- **Rewind (Yano snapshot restore, rollback):** persist the outstanding reconciliation first, then `reset` with
  `rewound:{tip}`, then `restore`, then clear the persisted reconciliation after the final response.
- **A recreated devnet** (same magic, a new system start) is a new chain: its journal starts empty; the old chain's
  journal is kept, read-only, until the user clears it.
- **Keyed designation (M3c):** the persisted designated input and `operation` record with its attempts, per the
  protocol's "Keyed calls" duties.

### D18. Chain reads (new in r1; L26)

- **One read adapter over the Blockfrost REST shape** (`java.net.http`), serving DevKit's Yaci Store, Yano and
  Blockfrost (F44). It answers the host's `chain/read` requests (all nine kinds) and IntelliAda's own reads for D13.
- **Strict outcomes (T8):** HTTP 404 is `absent` only for the kinds where the protocol defines absence; any other failure
  is `failed {message}`, never an empty answer. A kind the provider cannot answer is `unsupported`.
- **One chain point per answer:** the tip is read before and after; the answer is read again when the tip moved (at most
  8 times, then `failed`). A `utxos` answer covers the whole address set at one point and keeps the provider's order.
- **Limits:** reads are accepted only while a forward call is in flight; they run off the EDT; the host's per-call
  counts and byte budgets apply, and IntelliAda's own per-read deadline (10 s).
- **Hygiene:** no redirects across hosts; the Blockfrost `project_id` is sent only to its configured HTTPS host and
  never logged; parameters are validated against the protocol's formats before they reach a URL.
- **Koios is not offered** in the run panel until an adapter exists (L26).

## 3. Invariants

These continue ADR-004's. This ADR also relies on ADR-004's I15, I18, I20 and I21.

| # | Invariant | Decisions |
|---|---|---|
| I9 | IntelliAda's plugin contains no JOUST runtime that builds transactions; the dev host is the project's. The bundled checker only judges, out of process, from verified files | D11, D12, D13 |
| I10 | No mnemonic or private key reaches a child process's arguments, environment, stdin or files (the checker's included), nor IntelliAda's consoles, notifications or `idea.log`. The host receives addresses only | D13, D13a |
| I11 | IntelliAda signs only a body whose hash it computed from the received bytes and which equals `txHash`. It never re-encodes the body, redeemers or datums, signs only with payment keys, and the submitted id equals `txHash` | D13 |
| I12 | An account can be selected only in sessions of its own network class, in every IntelliAda flow. A mnemonic is held under one class only | D13, D13a |
| I13 | IntelliAda signs nothing unless the bound node's identity is positive for its class (D14) and the class's opt-in is on (D14a) | D13, D14, D14a |
| I14 | Endpoints and credentials come from IntelliAda's application-level node configuration only. The host reads the chain only through IntelliAda (D18) | D11, D14, D18 |
| I16 (amended r1, L25) | Plutus data is encoded or decoded only inside the bundled checker, to compare. Forms produce the description's JSON encoding; nothing IntelliAda sends to the host or the chain is Plutus data it encoded | D13, D15 |
| I17 | After a snapshot restore or rollback, the next prepare runs on a host that was reset with `rewound` and restored; recorded instances are re-checked | D15, D17 |
| I19 | The dev host and the checker listen on no socket: stdio only | D11, D13 |
| I22 | The host channel enforces protocol 1's framing: header ≤ 1 KiB, body ≤ 4 MiB, a frame complete within 10 s of its first byte, matched ids, no reverse request outside a call, no stray bytes; a violation discards the in-flight result and stops the host. A hard client timeout stops a host that never answers | D11, D11a |
| I23 | IntelliAda signs or exports only a transaction the bundled checker accepted (exit 0, `[]`) for an input IntelliAda assembled (only `result` from the host), plus D13's own checks. A refusal is final on every class | D13, D14a |
| I24 | Every prepare comes from a host whose `fingerprint` equals the digest IntelliAda computed from its snapshot copies, and whose contracts and script hashes equal the in-memory anchors | D11, D12 |
| I25 (new) | Before any body is handed out, the per-chain high-water mark covering it is persisted; restore never completes below it | D17 |
| I26 (new) | The checker's anchors come from IntelliAda's in-memory copy of the snapshot, read before the host launched; never from the host or from disk afterwards | D11, D13 |
| I27 (new) | The checker runs only from files whose SHA-256s equal the manifest built into the plugin, on the IDE's runtime; any failure to obtain a clean verdict refuses | D13 |
| I28 (new) | `now` never decreases within a host session except through `reset` | D14, L30 |

## 4. Milestones

### 4.0 Invariant to test traceability

| Invariant | Test (milestone) |
|---|---|
| I9 | M3.3a: the plugin zip has `org/julclang/**` classes only under `joust-verifier/`, none on the plugin class path |
| I10 | M3.3b: a canary mnemonic, searched for in every child's arguments, environment, stdin capture and temp files (host and checker), in the consoles, notifications and `idea.log`; M3.2: account creation logs no mnemonic |
| I11 | M3.3b: the kit's `accept-indefinite-body`, `accept-nonminimal-integers`, `accept-untagged-sets` signed by IntelliAda keep their body bytes and give id `txHash`; a mutated body is refused |
| I12 | M3.2: an account of another class is absent from the run panel and the transfer and mint choosers; a cross-class re-import is refused |
| I13 | M3.1b/M3.5: a fake node reporting magic 4, 1 on a devnet configuration, or a devnet magic without an admin API; a non-allow-listed host; HTTP to an allow-listed host; each refused |
| I14 | M3.1b: a fixture `joust.config.json` whose `networks.x.start` writes a sentinel is never used |
| I16 | M3.3a: an architecture test: no class outside the checker's directory references a Plutus data codec |
| I17 | M3.4: a Yano snapshot restore, after which the host is reset with `rewound`, restored, and the instance re-checked |
| I19 | M3.1a: no listening socket belongs to the host or the checker |
| I22 | M3.1a: the kit's seven `violation-*` sessions against `FakeHost`, plus IntelliAda's own tests for a stalled frame and a host that never answers |
| I23 | M3.3a: IntelliAda's checker adapter agrees with all 333 vectors; a checker that crashes, times out, prints extra output or is tampered with refuses |
| I24 | M3.1a: a snapshot copy edited after hashing, a spec digest mismatch, and a host reporting another fingerprint are each refused |
| I25 | M3.3b: a crash between persisting and handout, and a restart with a lower limit, never lower the mark |
| I26 | M3.3b: rewriting the snapshot's description after launch does not change the checker's anchors |
| I27 | M3.3a: a changed checker file refuses before any process starts |
| I28 | M3.1b: a node answering an older tip yields the previous `now` |

### M3: the run panel on devnets and public testnets, Gradle projects

**Entry gates:**
- **G3.0** M1 and M2 exited. **Met** (PRs #19, #22, #25 approved).
- **G3.1** JOUST's dev host exists with a protocol version. **Met for development** at JOUST `01bad1f7`, local Maven
  `0.1.0-pre3-01bad1f-SNAPSHOT` (L23, L29). **Shipping** signing to users needs a JOUST release containing protocol 1
  and its conformance kit; IntelliAda then pins that release.
- **G3.2** (replaced in r1): the bundled checker agrees with the kit's 333 vectors (M3.3a), and IntelliAda's signer keeps
  body bytes (I11).
- **G3.3** network identity sources verified per node type (D14): DevKit admin API and Yaci Store genesis; Yano's
  devnet API and genesis; Blockfrost's `/genesis`.
- **G3.4** Yano read coverage: every D18 endpoint answered by Yano as by Yaci Store, or the gap reported to Yano and the
  kind answered `unsupported` (map calls then refused by the host).
- **G3.5** the class-tagged account store (M3.2).

**Parts (L27), each its own stacked PR with the three-reviewer process:**

| Part | Scope | Exit |
|---|---|---|
| **M3.1a** | Launch spec through Gradle, validation, private snapshot and digest, in-memory anchors (D11 steps 1–6); the protocol client: framing, JSON-RPC, cancellation, deadlines, hard timeout, stderr console; `initialize`, `describe`, `shutdown`/`exit` | the kit's FakeHost Bank session and seven violation sessions; a real host on the F38 Counter (tool-gated); I19, I22, I24 |
| **M3.1b** | D18's read adapter; D14's devnet identity and clock; `deployment`, `prerequisites`, `view`, `instances`, `objects`, `decode`; the JOUST Run tool window, read-only (no signing) | reads answered for a real host on DevKit and Yano (tool-gated); I13 (devnet), I14, I28 |
| **M3.2** | D13a's `PasswordSafe` account store, class tags, legacy migration, filtered choosers, genesis constants, R7's fix | I10 (accounts), I12 |
| **M3.3a** | The bundled checker (L24): `joust-verifier/` directory, manifest, verification, the out-of-process runner, input assembly from in-memory anchors and node reads; no UI | I9, I16, I23, I27; all 333 vectors |
| **M3.3b** | Signing by splicing, submission, review modal, D17's journal, high-water mark, settlement and restore; Deploy (prerequisites, publications) and calls on devnets; the session history | end to end on DevKit and Yano: a Gradle Counter in Java and in `.jsc`: start, create and fund an account, deploy, increment, view, reset as the owner, close; JOUST's `bank` (owned) example; a restart between submit and settlement restores; I10, I11, I25, I26 |
| **M3.4** | Yano controls (time, epoch shift, snapshots, rollback with rewound reset), DevKit faucet, gutter icons (`.jsc`, Java), recorded instances | a `@ValidAfter` call made valid by advancing Yano's time; a snapshot restore with re-check (I17) |
| **M3.5** | Public testnets behind the opt-in (Blockfrost preprod and preview), export unsigned / import witness set | the Counter on preprod with a testnet account and the review modal, and once through export and import |

**M3b (npm):** the npm host, D11's npm launch and fingerprint, the same parts' tests on a `joust new -t node` project.
**M3c (keyed calls):** D17's designation journal and the keyed duties; JOUST's `registry` example end to end,
including a refused keyed call with its cooldown slot and **Advance to slot N** on Yano, and an IDE restart between
submit and settlement.

**Measurements** (recorded with machine and versions): first-call latency (launch spec, snapshot, host start), warm call
latency, and the checker's per-call time.

### M5: mainnet (D14a)

- **Entry gates:** decide the default signing path for mainnet (SEC-13; lean: export and external signing by default on
  Linux and Windows); M3.5 exited on preprod and preview; D13a's store shipped and legacy migration done (#10); a JOUST
  release pinned; a security review of the signing path and the mandatory modal on the implementation.
- **Exit:** mainnet refused without the opt-in and the per-session confirmation; the modal on every mainnet
  transaction; legacy accounts not selectable; export and import of a witness set with `cardano-cli`; a minimal real
  deployment done manually by the maintainer and recorded in the PR.

## 5. Compatibility

- **IDE:** 2026.1 or later (IntelliAda's floor, F43), with the Java plugin (Java line markers) and `com.intellij.gradle`
  (the launch spec). The checker needs the IDE's Java 25 runtime.
- **JOUST:** protocol 1 (JOUST `main` `01bad1f7` and later). A project whose JOUST has no `joustHostSpec` task gets
  "this JOUST version has no dev host" in the panel.
- **Plugin size:** the checker and its closure add a measured amount (recorded in M3.3a).
- **Data:** journals, high-water marks and recorded instances under the IDE system directory, schema-versioned, no keys;
  accounts move to `PasswordSafe` by an explicit migration (D13a).

## 6. Risks

| Risk | Mitigation |
|---|---|
| A host obtains a signature it should not | D13: the bundled checker on an input IntelliAda assembled; I23, I26, I27 |
| The bundled checker is a conformance-kit class, not a supported production artifact | pinned files and manifest (I27); a JOUST issue asking to support it and to expose anchor derivation; agreement with the vectors in every update |
| Signing is built against an unreleased protocol | G3.1: shipping waits for a JOUST release; tests pin the local snapshot (L29) |
| Verifier evidence is not produced in CI (local snapshot only, L29) | the tool-gated suite runs in each PR's verification and its output is posted on the PR; switch to the release when it exists |
| A user mnemonic with real funds is used on a devnet | class-tagged accounts (I12, D13a) |
| Real funds lost on mainnet through an IntelliAda bug | M5's gates; the mandatory review; refusals; fee caps; external signing |
| Yano or Yaci Store lacks a read the host needs | G3.4; `unsupported` answers fail closed |
| The journal is lost or corrupted | atomic writes; the high-water mark kept apart (I25); restore refuses to complete below it |

## 7. Decision log

From ADR-004 §11 (maintainer unless marked): L4 testnets in M3 and mainnet in M5 by opt-in; L5 a JOUST dev-host ADR;
L6 co-signers through it; L7 session history only; L8 Yano full controls, DevKit the faucet; L13 class-tagged
`PasswordSafe` accounts; L14 (delegated, ratified by L20) chain reads through IntelliAda; L17 (delegated, ratified)
the d2 signing defaults, amended by L25 and L30.

New in r1:

| # | Decision | By | Date | Basis |
|---|---|---|---|---|
| L23 | Build the dev-host features now, against a local build of JOUST main published to the local Maven repository | maintainer | 2026-11-25 | "all joust PR related to dev-host and playground is done. You can create a local build and publish to local maven repository and build remaining feature in intelliada which are dev-host specific" |
| L24 | The protocol's client signing rules are run by JOUST's reference checker, bundled in the plugin (own directory, off the class path, SHA-256 manifest) and run out of process on the IDE's JBR 25; not ported. IntelliAda's floor is 2026.1 | maintainer | 2026-11-25 | asked with both advisors' views (security: port; maintainer lens: bundle); the maintainer confirmed the 2026.1 floor and the reason, avoiding duplicate code |
| L25 | Amend I16 (the verifier alone encodes and decodes Plutus data, to compare) and L17 (a testnet outflow excess is a refusal, not a modal; the modal never overrides a refusal) | maintainer | 2026-11-25 | "Accept both" |
| L26 | Chain reads through one Blockfrost-style adapter (DevKit, Yano, Blockfrost); Koios is not offered in the M3 run panel | maintainer | 2026-11-25 | "Blockfrost-style only" |
| L27 | M3 in parts M3.1a, M3.1b, M3.2, M3.3a, M3.3b, M3.4, M3.5, each its own PR | delegated | 2026-11-25 | both advisors asked for a finer split, the verifier in its own PR |
| L28 | The verifiable set is everything the checker verifies (plain, owned, quorum, sends, maps, publications); keyed calls after M3c | delegated | 2026-11-25 | maintainer-lens advisor (under bundling the checker covers maps); security advisor's map deferral applied only to a port |
| L29 | IntelliAda's JOUST-dependent tests resolve the kit and plugin from the local Maven repository (`joustTestVersion`, default `0.1.0-pre3-01bad1f-SNAPSHOT`) and run with `JOUST_REQUIRE_TOOLS=1` | maintainer | 2026-11-25 | "Local snapshot only" |
| L30 | Signing policy: the fee is within both the protocol's derived cap and the user maximum (3 ADA, setting, ceiling 10 ADA); `ttlSlots` 900 slots per call on every class, a session `maxTtlSlots` of 900 on devnets and 7200 on public networks (exports up to 7200); `settlementSlots` 60 on devnets and 2160 slots (36 min at 1 s slots, the security parameter k of preprod, preview and mainnet, as a depth proxy) on public networks; `maxNowAheadSlots` 900; `now = max(previous now, bound node's tip slot)`, re-baselined only by `reset`; journal and high-water mark keyed by chain identity (magic and system start) | delegated | 2026-11-25 | both advisors (TTL contradiction, monotone `now`, chain identity) |

Delegated decisions are tracked for the maintainer's ratification on this PR.

## 8. Earlier findings carried here

| Finding | Where it is answered |
|---|---|
| DES-1, CDX-16 | D11a, G3.1 |
| DES-4 | D15 entry points |
| DES-5, CDX-6 | I24, D11 lifecycle |
| DES-7 | G3.0 |
| DES-9 | D13 signing, I11 |
| DES-10 | D14 |
| SEC-3, CDX-3 | D14 |
| SEC-4, CDX-2 | D13, I23 (the protocol's rules supersede checks 1–10; the redeemer is checked by the checker against the description IntelliAda read) |
| SEC-5 | D13a, I12 |
| SEC-6 | I10, ADR-004 R7 |
| SEC-7 | I22, D11, D15 persistence |
| SEC-13 | M5 gate |
| CDX-5 | ADR-004 §2, I10 |
| CDX-7 | M3c |
| CDX-8 | protocol `publish` with bounds; the checker's publication shapes |
| CDX-15 | G3.2, G3.4 |
| CDX-20 | D13a |

## 9. Open questions

- **Q15.** JOUST support for the bundled checker as a production artifact (a stable `julc-state-verify` jar or entry)
  and an exported anchor derivation from a snapshot. Lean: file a JOUST issue in M3.3a.
- **Q16.** Yano's coverage of `/txs/{h}/redeemers`, `/scripts/datum/{h}/cbor` and `/assets/{u}/transactions` (G3.4).
  Lean: answer `unsupported` where missing (map calls then refused) and report the gap to Yano.

## Revision history

- **d1** (2026-11-19): moved from ADR-004 r2's Part C; advisor changes L14 and L17.
- **d2** (2026-11-19): carries round 2 of PR #9 (CDX-20, CDX-2 note, SEC-13).
- **r1** (2026-11-25): amended to JOUST dev-host protocol 1 at `01bad1f7` (G3.1). D11's launch spec, snapshot, digest
  and in-memory anchors; D11a mapped to the protocol; D13 rewritten around the protocol's rules run by the bundled
  reference checker (L24), with splicing for signatures; D14 chain identity and clock; D15 flow through
  `prerequisites`/`publish`; D16 by kind/code; new D17 (journal) and D18 (reads); I16 amended (L25); I25–I28; G3.1 met
  for development, G3.2 replaced; M3 split into seven parts (L27); facts F38–F44; decisions L23–L30. Status Proposed.
