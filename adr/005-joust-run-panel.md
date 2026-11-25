# ADR-005: Running JOUST contracts from IntelliAda (devnets, testnets, mainnet)

**Status:** Proposed (r7). The draft (d1, d2) gave requirements to JOUST's dev-host ADR. r1 amended it to the protocol
JOUST built, as gate G3.1 requires; r2–r7 answer review rounds 1–6 (§10). Reviewed before any milestone that signs starts.

**Date:** 2026-11-25 (draft d1: 2026-11-19).

**Authors:** Claude Code, for the IntelliAda maintainers.

**Scope:** design only. This PR adds no code, build change or plugin.xml entry.

**Origin.** This is Part C of ADR-004 r2 (PR #9), moved here on 2026-11-19 (decision L16 in ADR-004 §11) so that Parts
A and B could be accepted on their own.
- **Kept from ADR-004:** decision IDs (D11–D16, D11a, D13a, D14a), invariant IDs (I9–I14, I16, I17, I19, I22–I24) and
  milestone IDs (M3, M3b, M3c, M5). New IDs continue from them (D14b, D17, D18; I25–I30; L23–L33).
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
- **F41.** A verifier vector's client-side inputs are `method`, `request`, `networkId`, `maxFee`, `anchors`
  (`description`, `language`, `policy`, `address`, and `outputVerifier`/`mapVerifier` each with `language`, `hash`,
  `address`; the contract has no `hash` field), `resolved` (node UTxOs by `txHash#index`), `node` (holder scripts and
  registrations), `protocolParams`, on keyed prepares `clock` (102 vectors), and on some offline and ordering cases
  `orderedReads`; only `result` comes from the host. `ReferenceChecker.main` reads its file argument (`/dev/stdin`
  works) and prints the verdict with `println` (`[]` and a line feed).
- **F42.** The anchors are in the build output: `META-INF/joust/<pkg>.<Contract>.json` (the description, whose
  `x-joust` names `package` and `validator`), and the validator's script. The Counter build holds **two** copies,
  `META-INF/plutus/CounterValidator.plutus.json` and `META-INF/plutus/org/example/counter/CounterValidator.plutus.json`,
  plus an aggregate `plutus.json`; each `.plutus.json` carries `cborHex` and a `hash`. JOUST's runtime reads the
  simple-name resource **from the validator class's own code source** and refuses two classes of one simple name there
  (`ValidatorScripts.resolve`). The shared verifiers are `META-INF/plutus/OutputVerifier.plutus.json` and
  `MapVerifier.plutus.json` in the `julc-state-verifier` jar.
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
- **What D13 defends against** (r2, ADR5-SEC-4, ADR5-DES-5): a **buggy or protocol-misbehaving host**, a **stale
  build**, and **accidental corruption**. It does not contain **hostile project code**: trusted project code runs as the
  user and can rewrite any file the user can, IntelliAda's plugin included (ADR-004 §2; JOUST ADR-0022 §2 "Not
  addressed"). The measures below that keep state in memory or off disk (I26, the checker's input on a pipe) are cheap
  hardening against corruption and races, not a claim of containment.
- **The provider** (r2, ADR5-SEC-7): for the run panel, the bound node's answers are trusted; their integrity rests on
  D14 (HTTPS to Blockfrost; a devnet with positive identity). This narrows ADR-004 §2's "defence in depth against a lying
  endpoint" for the run panel: the checker defends against the host, not the provider (L32).

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
   regular, executable `bin/java` (or `bin/java.exe`) of a JDK home whose `release` file declares `JAVA_VERSION` 25 or
   later; ordinals contiguous from 0; absolute entry paths; `kind` `dir` or `jar`; SHA-256s as 64 lowercase hex;
   relative paths POSIX, without `.`/`..` segments, a leading `/` or a control character.
3. **Snapshot:** IntelliAda creates the snapshot in a temporary private directory under the IDE system directory
   (`<system>/intelliada/joust/snapshots/`), then renames it into place. It opens each source **without following
   links**, copies only regular files under a deadline, hashes **the bytes it writes**, compares each hash to the spec,
   refuses a jar whose manifest has a nonempty `Class-Path` (as the host does), recomputes the digest by the
   protocol's fingerprint format, refuses a mismatch, and makes the snapshot read-only. A snapshot is never reused: each
   launch makes its own, deleted when no host uses it any more (a Counter snapshot is about 95 MB); leftovers are swept
   at IDE start.
4. **Anchors (I26):** before launch, IntelliAda reads from the copies, into memory, every description
   (`META-INF/joust/*.json`) and every Plutus blueprint (`META-INF/plutus/**.json`) with its class-path ordinal, and
   derives the anchors as D13's "Anchors" says. Signing decisions never re-read the snapshot. When the anchors cannot be
   derived under the reviewed set (D12), the host still starts **read-only**: views, listings and decoding work, signing
   and export are off, and the panel says why (ADR5-CDX-14).
5. **Confirmation:** the first start in each IDE session shows the project, the `java`, the main class and the
   project's JOUST version: the string constant in `org/julclang/state/runtime/RuntimeVersion.class`'s static
   initializer (`VERSION = String.valueOf("…")`), read from the snapshot copy with a class-file reader, never by loading
   the class (D1; ADR5-DES-11, ADR5-DES-14, ADR5-CDX-14).
6. **Process:** `GeneralCommandLine(java, -XX:+DisplayVMOutputToStderr, -Xlog:disable, -Xlog:all=warning:stderr,
   --enable-native-access=ALL-UNNAMED, -cp <snapshot entries in ordinal order>, org.julclang.state.host.DevHost,
   --stdio)` from the project root, with `ParentEnvironmentType.NONE` and the protocol's environment allow-list (PATH,
   HOME, LANG, LC_*, and on Windows USERPROFILE, SystemRoot, TEMP/TMP) and nothing else: no project JVM options, no
   `JAVA_TOOL_OPTIONS`. The host console offers no thread dump (the protocol forbids SIGQUIT).
7. **`initialize`:** `protocol [1]`; the bound network (D14) with `mainnet:true` only after M5's gate; the clock and
   time limits of D14b; `limits {callDeadlineMs 60000, readsPerCall 256, readBytesPerCall 32 MiB}`;
   `build {snapshot, digest}`; `restoring` per D17. IntelliAda refuses a host whose `protocol` is not 1, whose
   `fingerprint` differs from the digest it computed, or whose contract list or script hashes disagree with the anchors
   (I24).

**Calls are serial** (ADR5-DES-9): each host has one queue; views, listings, prepares and settlements wait their turn,
so IntelliAda never sends a second forward call (`bad/busy`). Several hosts may be live (one per contract module and
bound node); every host on one **chain** (D14, whichever node configuration bound it) shares that chain's lock and mark
(D17). **One live owner** (ADR5-CDX-19): a contract module has at most one live host per chain; binding the same module to
another node configuration of that chain while its host runs is refused ("already running on this chain through …").
The module's journal records the owning host session; restore and settlement route by it.

**Admission is atomic** (ADR5-CDX-2, ADR5-SEC-17, ADR5-CDX-21): under the chain lock IntelliAda checks the body's
spending, collateral and **reference** inputs against **every live journal entry on the chain** (whether or not its host
is running), and a publication's components (a script at its holder address, a stake registration of its credential;
a combined verifier publication counts as both of its components) against every pending publication there; then it
records the entry and raises the mark, and only then signs or hands the body out. A body refused for overlap was never handed out: it is settled
`discarded` with its own host, and the panel says the wallet's UTxO is pending; the other host keeps choosing that
UTxO until the first body settles.

**Lifecycle (I20, I24):** started on the first run action, one host per contract module and bound node; stopped on
project close, trust revocation, a change of the bound node or its identity, **Stop** in the panel, a protocol
violation, or process exit (the session state is then lost, and D17's restore applies at the next start). A rebuild
does not change what runs (the snapshot is pinned); the panel marks the host **stale** when the module's build output
changes, and Deploy or a call from a stale host first offers a restart. The host's stderr is drained continuously into a
capped "JOUST host" console; stdout carries only frames. An **orderly** stop sends `shutdown` and `exit`, closes the host's
input and reads its output to the end for at most 5 s (a frame after `exit` is a violation), then ends the process tree.
After a **protocol violation** IntelliAda sends nothing more: it discards the in-flight result, closes the host's input
and ends the process at once (ADR5-CDX-15).

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
| H9 | time from the client | `now` per call, never decreasing; `reset` re-baselines | met; D14b |
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
- **IntelliAda runs no JOUST code that builds transactions** (I9). It bundles JOUST's **reference checker** (L24) with
  the smallest class-path closure that passes all 333 vectors; it is only ever invoked as the checker, out of process
  (D13), never as a host or builder.
- **Version policy (L31; ADR5-SEC-1, ADR5-DES-7, ADR5-DES-14, ADR5-CDX-14):** the plugin carries a **reviewed set**:
  for each JOUST version its kit was reviewed against, the SHA-256s of that version's `julc-state-api`,
  `julc-state-runtime`, `julc-state-serve`, `julc-state-host` and `julc-state-verifier` jars, and the `x-joust.version` values of its
  descriptions; the version string is a label. IntelliAda signs or exports only when
  - the snapshot's launch entries for those five artifacts are jars whose hashes (from the verified launch spec) are a
    reviewed set's;
  - **no other launch entry holds a class** under `org/julclang/state/api/`, `runtime/`, `serve/`, `host/` or
    `verifier/` (ADR5-DES-21), so the
    classes the JVM selects are the reviewed jars' (an earlier directory or shaded jar would otherwise win; ADR5-CDX-14,
    ADR5-SEC-16), and `RuntimeVersion` is read from the reviewed runtime jar's own entry;
  - `RuntimeVersion` (D11 step 5) and `initialize`'s `versions.engine.joust` both equal that set's label;
  - every anchored description declares one of its `x-joust.version` values.

  Otherwise the panel stays read-only (views, listings) and names the versions. A `dir` runtime (a composite build) or
  an unknown SNAPSHOT build is never in the set. **npm (M3b):** the fingerprinted
  `node_modules/@julclang/joust-runtime/package.json` version, every contract's `built.joust` and `versions.engine.joust`
  must agree and be reviewed. An IntelliAda update widens the set after the new kit's vectors pass.
- IntelliAda shows the host's versions and refuses a protocol other than 1.
- **Before Deploy,** IntelliAda offers to run the build when a contract source is newer than its description.
- **The language server's compiler** (ADR-004 D5) may differ from the project's. The run panel never uses it.

### D13. Accounts, keys and signing (rewritten in r1, revised r2)

**Accounts.** The caller list shows only accounts of the session's network class (D13a, I12): on devnets also the
devnet's genesis accounts. Co-signers (quorum calls) come from the same class.

**Keys stay in the IDE process (I10).** The host receives addresses only. No mnemonic or private key appears in any
child's arguments, environment, stdin or files (the checker's included), nor in IntelliAda's consoles, notifications or
`idea.log`.

**What IntelliAda checks before signing or exporting (I11, I13, I23).** The protocol's **client signing rules**
(dev-host-1 "Client signing rules (D13, normative)": rules 1–8, the MerkleMap rules and the three publication shapes)
are IntelliAda's allow-list. They supersede d2's checks 1–10 and are a superset of them. **They are run by JOUST's
reference checker, bundled in the plugin (L24):**
- **The checker files:** the `conformance` jar of a reviewed JOUST version and the smallest closure that passes all 333
  vectors, in their own plugin directory (`joust-verifier/`), **off the plugin class path**, listed in a SHA-256
  manifest built into the plugin and verified before each run. The build resolves them under Gradle dependency
  verification (`verification-metadata.xml`; PGP for releases) and **refuses to build a Marketplace distribution from a
  SNAPSHOT kit** (ADR5-SEC-6).
- **The process:** the IDE's own runtime (`java.home`, JBR 25 on the 2026.1 floor, F43; checked to be Java 25 or later),
  never the project's `java`. One process per check, on every OS: a small runner class bundled beside the kit (listed in
  the manifest) that reads stdin, calls `ReferenceChecker.check(JsonNode)` and prints the verdict. The input is written to the process's **stdin from memory** (no file), so the verdict is about the exact bytes
  IntelliAda assembled (ADR5-CDX-1). The working directory is a fresh private empty directory; the environment is the
  protocol's environment allow-list (D11 step 6; Windows `SystemRoot` included); deadline 30 s. **Accepted:** exit status 0 and stdout exactly `[]` with
  at most one line terminator. **Refused:** exit status 1 with one JSON array of code strings (shown), and anything else
  (a timeout, a crash, other output).
- **The input IntelliAda assembles** (F41), with only `result` from the host:
  - `method` and `request`: the exact forward method and parameters IntelliAda sent;
  - `anchors`: derived as below, from the in-memory snapshot resources (I26);
  - `resolved`: every spending, reference and collateral input, read by IntelliAda through the bound node (D18)
    **at signing time**, never taken from the answers it gave the host;
  - `node`: script presence at the holder addresses and registration status, read the same way;
  - `protocolParams` from the bound node; `networkId` from D14; `maxFee` per D14b;
  - `clock`: the session's clock as initialized or last reset (keyed calls, ADR5-CDX-8). (`orderedReads` in the vectors is
    replay evidence; the checker does not read it, so IntelliAda does not send it.)
- **Anchors (ADR5-SEC-2, ADR5-CDX-5, ADR5-DES-3).** IntelliAda derives them as JOUST's runtime resolves scripts:
  - the description is `META-INF/joust/<pkg>.<Contract>.json`; two different copies on the class path refuse;
  - the validator class is `x-joust.package` + `x-joust.validator`; its script is
    `META-INF/plutus/<validator simple name>.plutus.json` **from the class-path entry that holds the validator's class
    file**; that entry holding two classes of that simple name, or no such resource, refuses;
  - the **script hash is recomputed** as CCL computes a Plutus script's hash: Blake2b-224 of the language tag (`0x03`
    for V3) followed by the `cborHex` bytes **with their outer CBOR byte-string header removed once**; never read from
    the blueprint's `hash`, though the two must agree (a difference refuses). On the F38 Counter this gives
    `5de2e73f…`, the host's reported hash (ADR5-SEC-13, ADR5-CDX-5). The policy is that hash; the address (and a
    staking contract's reward address) is derived with CCL for the bound network;
  - the **output and map verifiers** are resolved like contracts (ADR5-DES-13, ADR5-SEC-12, ADR5-CDX-5): from the
    class-path entry that holds `org/julclang/state/verifier/OutputVerifier.class` / `MapVerifier.class`, which must be
    the `julc-state-verifier` jar of the reviewed set (D12), reading `META-INF/plutus/OutputVerifier.plutus.json` /
    `MapVerifier.plutus.json` from that entry; another entry holding a resource of that name, or a second class of that
    simple name in it, refuses;
  - npm (M3b): `build/joust/contracts.json` and the installed `@julclang/joust-runtime` engine bundle, as ADR-0022 D13
    names them.
- **Deployment check:** before any `prerequisites`, `publish` or `prepare`, the host's `deployment` answer (addresses,
  policies, script hashes, verifier hashes and holder addresses, reward addresses) must equal the derived anchors; any
  difference stops the session (I24).
- **IntelliAda's own checks on top:** the bound node's identity is positive for its class and the class's opt-in is on
  (D14, D14a); the caller and co-signers are accounts of that class (D13a); the session, host and binding that prepared
  the body are still current; the version policy (D12, L31); no overlap with another host's live reservations (D11);
  the fee within D14b's user maximum (also passed as `maxFee`).
- **The review modal** shows what IntelliAda derives from the **body** with CCL's transaction reader (display only):
  outputs to addresses that are not the deployment's or the caller's, the caller's net outflow, the fee, the required
  signers, the validity window; never the host's `predicted`. A refusal is final: the modal cannot override it (L25).
  Devnet: off by default (setting **Confirm every devnet transaction**); testnet: on by default, can be turned off per
  testnet; mainnet: always.

**Signing (I11).** IntelliAda slices the received transaction's body bytes, hashes them (Blake2b-256), and signs the hash
with each required **payment** key it holds: the caller's, and each co-signer the user chose from IntelliAda's accounts
of the class (ADR5-CDX-9). It builds the signed transaction by **splicing**: the original array header, the body bytes as
received, a witness set made of the received witness set's entries **as raw bytes** (the checker admits only keys 4, 5
and 7 there) plus a new vkey-witness entry, and the remaining elements as received. It never re-encodes the body, the
redeemers or the datums. **Before submission, every key the body requires** (its `required_signers` and the payment key
of every wallet spending and collateral input) has a witness; otherwise nothing is submitted. It submits through the bound node and checks that
the returned id equals `txHash`.

**External signing (D14a, M3.5):** export the unsigned transaction (after the same checks); import witness sets (one per
co-signer if needed), accepting **only vkey witnesses** (key 0), each verified against `txHash` and belonging to a key the
body requires; merged by the same splice.

**Calls the panel offers:** every call the checker can verify and the host serves: init, action, destroy, owned calls,
quorum calls (co-signers chosen from the class, or by import), calls with sends, map calls, and publications. **Keyed
calls** wait for M3c (D17's designation journal). `@Params` and `@AuthorizedBy` contracts show the host's
`unsupported` reason.

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
- **Chain identity** (for the journal and the mark, D17; r3: ADR5-SEC-10, ADR5-SEC-11, ADR5-DES-15, ADR5-CDX-2,
  ADR5-CDX-6): **the chain alone, never the node configuration** (which is kept only as metadata on each entry):
  - **public networks:** the network magic (1, 2, 764824073). A public chain is never "recreated";
  - **devnets:** the network magic and the hash of the block at height 1 (`/blocks/1`), which survives time controls
    and epoch shifts (they do not rewrite blocks) and differs on a recreated devnet (G3.3).
- **A lower tip is never a new chain** (ADR5-DES-17, ADR5-CDX-6). On any class, a tip below one IntelliAda observed
  is first a transient read (re-read; after D18's retries, `failed`). It is **persistent** after three consecutive reads
  over at least 10 s. A persistent lower tip, or a different block hash at a height IntelliAda recorded (a fork, even
  at the same slot or the same tip), is reconciled under the **same** identity by **restarting the hosts**
  (ADR5-CDX-6, r6): IntelliAda persists a recovery fence and the reconciliation, stops **every live host on that chain**,
  starts fresh ones with `restoring:true` (re-sending `registered` evidence for confirmed publications, ADR5-DES-23), and restores every journal entry from its re-read status. (A live host's `reset` with `rewound`
  cannot restore an inclusion at a different slot: JOUST's `Reservations` keeps the old slot and `restore` answers
  `bad/conflict`, which Codex reproduced against `01bad1f7`.) The re-read status is: an inclusion still on chain is restored `shallow` with its slot, **even one that was final** (its depth is
  recomputed against the restored tip, so its inputs and collateral stay reserved through the margin again); an
  inclusion at another slot is restored `shallow` at the new slot; an absent one is restored `prepared` while its
  `validTo` is above the tip. It is triggered only when the fork or lower tip touches a journal entry: an inclusion
  whose height or block changed, or whose depth margin the new tip re-opens; a handed-out body whose `validTo` is above
  the new tip (an expired body a rewind makes valid again); or a new tip more than `maxNowAheadSlots` below the last
  `now` sent (ADR5-DES-22, ADR5-SEC-20). A fork elsewhere triggers nothing. Building is fenced until that restore
  completes. A rewind IntelliAda did not make (an external Yano restore) takes the same path, keeping the journal and
  the mark.
- **A recreated devnet** is recognised only by a different block hash **at height 1**. Until the devnet has a block 1,
  or while it cannot be read, the panel shows "waiting for the devnet's first block" and building is blocked.
- **The clock** (`zeroSlot`, `zeroTimeMs`, `slotLengthMs`), era-correct (ADR5-CDX-6):
  - **public networks:** the Shelley-era anchors CCL's `SlotConfigs` uses: mainnet slot 4,492,800 at
    1,596,059,091,000 ms, preprod 86,400 at 1,655,769,600,000, preview 0 at 1,666,656,000,000; 1,000 ms slots;
  - **devnets:** slot 0 at the genesis start, the genesis slot length (DevKit admin API, Yano genesis);
  - **after a Yano epoch shift or catch-up** that moves genesis time: IntelliAda re-reads the genesis and sends
    `reset({now, clock})`; after a time advance (no genesis change) only `now` moves.

### D14b. Signing and time policy (new in r2; was L30, ADR5-DES-8)

- **Fee:** within the protocol's derived cap (rule 3) **and** the user maximum: an application setting, default 3 ADA,
  hard ceiling 10 ADA, sent as `maxFee`.
- **`now`:** `max(previous now, bound node's tip slot)` per call (I28); re-baselined only by `reset`. A rewind (Yano
  snapshot restore, rollback) goes through D17's rewind path (a restored fresh host), never a lower `now`.
- **TTL per call:** devnets `min(900, horizon)` slots, where the horizon is ⌊3k/f⌋ from the devnet's genesis or admin API
  (300 when not stated), as JOUST's playground computes it (ADR5-CDX-7); public networks 900 slots (15 min at 1 s
  slots). **Export** is chosen **before** `prepare`, and an exported call asks for 7,200 slots (2 h).
- **Session limits:** `maxTtlSlots` = the largest TTL a call of that session may ask (devnets the per-call TTL;
  public networks 7,200); `maxNowAheadSlots` 900.
- **`settlementSlots`:** devnets 60; public networks 2,160 slots. At an active-slot coefficient of 0.05 that is about 108
  blocks on average, well short of the security parameter (2,160 blocks on preprod and mainnet, 432 on preview); the
  accepted exposure is that a rollback deeper than that, after the host released a body's inputs, surfaces as
  `refusal/deepRollback` and a restart (ADR5-CDX-12).

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
  - **A JOUST Run tool window** lists the contracts from the **built descriptions** (before any host runs; the host is
    never started just to list them, ADR5-DES-11); once a network is bound and the host is up, each contract shows its
    `describe` calls with their `served` flag or `unsupported` reason.
  - Icons only open the panel. Nothing runs without D1.
- **Flow:** choose the network (D14) and, on a devnet, **Start** it; choose the caller (D13a); fund it (devnets: Yano
  `fundAddress`, DevKit's top-up); **Deploy** = `prerequisites`, then each `publish` in order (each signed, submitted and
  settled before the next), then the `init` call; call actions and views.
- **Registration evidence (ADR5-CDX-10):** when a node rejects a registration as already registered, or IntelliAda sees
  the registration confirmed at an inclusion slot, it sends `registered` with that evidence and lists `prerequisites`
  again; a script's presence is checked separately (a duplicate registration says nothing about it).
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
  so the next call's `now` follows it (D14b); an epoch shift also resets the clock (D14). A **snapshot restore or rollback** rewinds: IntelliAda persists the
  fence and the reconciliation, restarts the host with `restoring:true`, restores (D17), then re-checks each recorded
  instance (I17).
- **Persistence:** recorded instances per project and per chain identity (D14) in IntelliAda's state under the IDE
  system directory, not in `.idea/`, with no keys; validated as untrusted on load.

### D16. Results and errors

- **Success:** the transaction id, the fee, execution units per redeemer, the predicted effects (labelled), and after
  confirmation the new state and its diff.
- **Host errors** by `kind`/`code` only (never by message text): `refusal/require` shows the message with its `.jsc`
  `sites` as links, opened only inside the project's contracts directory (I18); `refusal/pending`,
  `refusal/prerequisite` (offers Deploy's next step), `refusal/cooldown` (shows `retryAtSlot`), `refusal/reconcile`,
  `refusal/deepRollback` (restart required); `unsupported/*` names the feature; `bad/*` and `internal/*` are shown as
  host or IntelliAda faults. **Any other code** (`expiredOnArrival`, `window`, `busy`, `nowDecreased`, codes added later)
  is shown with its kind and code and the host's message as plain text, and never retried automatically.
- **The checker refused:** "IntelliAda did not sign: <codes>", visually distinct from contract errors, each code with
  its protocol meaning.
- **The node refused:** the node's text, with any `#…` trace key matched against the message map.
- **Where:** the panel's result pane; the raw exchange (no key material, I10) in the Cardano Log console; the host's
  stderr in its own capped console. All text plain: HTML disabled, ANSI sequences stripped (I15).

### D17. The journal: reservations, settlement, the high-water mark and restore (new in r1, rewritten r2)

The protocol makes these the client's duties (dev-host-1 "Reservation journal (D8)"); JOUST's playground journal
(`journal.js`) is the reference.

- **Store and lock (ADR5-DES-2, ADR5-CDX-2):** under the IDE system directory, one journal per **chain identity (D14)
  and contract module** (the host that reserved), and one **chain record** per chain holding its high-water mark and
  the list of module journals that ever held a handed-out body there; a **registry entry per chain**, a file of its own
  (independently replaceable, written under that chain's lock; ADR5-CDX-24), records that the chain has a record and
  holds the **highest tip and the highest `now`** IntelliAda ever observed or sent on it, raised (never lowered) in the
  same admission that raises the mark (ADR5-DES-12, ADR5-SEC-14, ADR5-CDX-3,
  ADR5-SEC-18). Every update is made under the
  per-chain lock (in-process and a file lock), written to a temporary file, forced to disk, renamed, and the directory
  forced where the platform supports it; schema-versioned; no keys. Loads are strict: an unreadable journal or mark is
  **lost history** (below), never an empty one.
- **Entry (ADR5-CDX-16):** reservation id; `txHash`; the transaction CBOR as handed out (signed, or unsigned for an
  export or a pending approval); `validFrom`/`validTo` read from the body; the handout (`none`, `approvalPending`,
  `submitted`, `exported`); the node configuration that bound it (metadata); the reservation **state** as the host
  returned it (`prepared`, `shallow` with its inclusion slot, `confirmed`, `expired`, `discarded`); and, separately,
  the **events** IntelliAda observed (`confirmed@slot`, `rolledBack`, a deep rollback) and any **fence** (a deep
  rollback after final confirmation fences building and requires a restart; a rewind fence until restore completes).
- **Before signing for submission or export (I25, ADR5-SEC-5):** the entry and the per-chain mark
  `max(validTo + settlementSlots)` are both persisted, as one admission under the chain lock (D11). A failed write
  refuses to sign and settles the body `discarded` (it was never handed out).
- **Settlement (ADR5-DES-1, ADR5-CDX-4, ADR5-SEC-3):** IntelliAda records a submission **before** sending it. It
  observes the bound node (the transaction's inclusion slot; the tip), at least every `settlementSlots/2` slots while
  any entry is live and before forwarding the next call, and sends `settled` with the reservation, `txHash`, the status
  and the observed tip (the first `confirmed` carries the inclusion slot as its tip, as the playground does; ADR5-DES-20): `confirmed` with the inclusion slot, `rolledBack`, `expired` once the
  observed tip passes `validTo + settlementSlots`. **The reply is checked** (ADR5-CDX-20): IntelliAda keeps, per
  reservation, the state machine of the protocol's table and applies, **in order, every tip the host accepted**: each
  `settled` event's tip, and each reverse-read answer's tip once the host has used it (an answer with a value or
  `absent` in a call that completed). A tip whose acceptance is uncertain (a `failed` or `unsupported` answer, or a read
  of a call that ended in an error or was cancelled) is applied only to an **upper** copy of the machine: the host's
  reply may match either copy, and the journal keeps the less advanced state until a later accepted tip settles it
  (ADR5-CDX-23). A tip applies to every reservation of that host as it exists when the tip is accepted, so **inclusions
  are reported first** (ADR5-CDX-25): before any forward call and on each poll, IntelliAda sends every newly observed
  inclusion in ascending slot order, each `confirmed` with its inclusion slot as tip, and only then the current tip; no
  forward call (whose reads would carry newer tips) is sent while an observed inclusion is unreported. The expected state
  is the machine's and compares the host's reply;
  the journal keeps IntelliAda's derived state, and a disagreement keeps the reservation and fences that host (stopped;
  a new host is restored from the journal), as the playground's `inform` does. **`discarded` only for a body declined before handout** (and, in M3c,
  a keyed attempt the node definitely rejected). A handed-out ordinary body, even one the node refused or whose
  submission timed out, is released only by chain evidence or expiry: a refusal from one node or a 5xx is not evidence
  that it cannot land.
- **Restart (ADR5-CDX-3):** a new host is initialized with `restoring:true` when the journal holds bodies that can still
  land or retained inclusions, or a persisted rewind fence. IntelliAda then sends `restore` in batches: every handed-out
  body whose `validTo` is above the restored tip, every retained inclusion (`shallow` with its slot) within its margin
  **even when its `validTo` has passed**; bodies awaiting approval are **invalidated** (their modal closes, they are
  removed from the journal) rather than restored. The last batch has `complete:true`. Settlement then
  uses **the ids `restore` returned**, recorded in the journal.
  - **Complete history** (journal and mark readable, **and no persisted recovery fence**): restore and resume at once.
    A recovery fence is persisted in the chain record next to any replacement or derived mark and is cleared only once
    an observed tip passes it, so a crash right after writing a replacement never looks like complete history
    (ADR5-CDX-3).
  - **Lost history, mark intact** (ADR5-CDX-3): a module journal the chain record lists is missing or unreadable: start
    fenced, restore what survives, and complete restore only once an observed tip is **strictly** above the mark.
  - **Lost mark** (the chain record unreadable or missing while the registry lists the chain, or any journal for it
    survives; ADR5-CDX-3, ADR5-SEC-15, ADR5-DES-18): no surviving journal is trusted to bound what was handed out.
    IntelliAda derives a conservative bound from the policy's hard ceilings, which no handed-out body can exceed:
    `base + maxNowAheadSlots (900) + 7,200 (the largest maxTtlSlots any class allows, D14b) + settlementSlots`, where
    **base is the highest of the registry's highest tip and highest `now` for the chain, every tip and `validTo` in any
    surviving journal, and the tip at detection** (a rewind before detection cannot lower it; ADR5-CDX-3, ADR5-SEC-18).
    It persists the bound as the chain's new mark **before** lifting anything (so an IDE restart during recovery keeps
    it), restores what survives, and completes restore only once an observed tip is strictly above it (about 2.9 h past
    the base on a public network). No user confirmation ends this fence.
  - **Registry entry and chain record both unreadable** while a journal survives (ADR5-CDX-3, ADR5-SEC-19): no bound
    on the missing history can be derived (k counts blocks, not slots, so no slot margin covers a rewind before
    detection), and there is no user override for the same chain. Building on it stays fenced, and the panel explains
    why: it lifts only when the lost files are restored (from a backup), or, for a devnet, when the devnet is positively
    recreated (D14). Views and listings still work.
  - **Everything lost** (no registry entry, no record, no journal for a chain): IntelliAda cannot tell it from a new
    chain. This residual, losing all of IntelliAda's state for a chain, is accepted and documented.
  - **Forget this chain** is offered only for a devnet positively identified as recreated (D14); forgetting local
    state cannot invalidate a body already exported, so it is never offered for a public chain.
- **Rewind (Yano snapshot restore, rollback, an external rewind or fork; r6):** persist the recovery fence and the
  outstanding reconciliation first, then start a fresh host with `restoring:true`, `restore` from the re-read chain
  state as above (D14), and clear the persisted reconciliation and fence only after the final response.
- **A recreated devnet** (positively identified, D14) is a new chain: its journal starts empty; the old chain's journal
  is kept, read-only, until the user clears it.
- **Keyed designation (M3c):** the persisted designated input and `operation` record with its attempts, per the
  protocol's "Keyed calls" duties.

### D18. Chain reads (new in r1; L26)

- **One read adapter over the Blockfrost REST shape** (`java.net.http`), serving DevKit's Yaci Store, Yano and
  Blockfrost (F44). It answers the host's `chain/read` requests (all nine kinds) and IntelliAda's own reads for D13.
- **Strict outcomes (T8):** HTTP 404 is `absent` only for the kinds where the protocol defines absence; any other failure
  is `failed {message}`, never an empty answer. A kind the provider cannot answer is `unsupported`.
- **One chain point per answer:** the tip is read before and after; the answer is read again when the tip moved (at most
  8 times, then `failed`). For `utxos` and `assetHistory`, **all provider pages** are fetched inside one bracket, so the
  whole set has one chain point and the provider's order; IntelliAda then hands it to the host in chunks of at most
  1 MiB of JSON, each nonfinal chunk with an opaque `more` cursor that IntelliAda keeps in memory for that call only
  (ADR5-CDX-11). Each chunk's response counts toward the call's byte budget. The pages are accumulated under the
  call's **remaining** byte budget and one deadline covering the whole bracket and its retries; exceeding either
  answers `failed` and returns no partial set (ADR5-CDX-17).
- **Registration:** Blockfrost's `/accounts/{stake}` 404 is `registered:false`; a provider that cannot say (Yaci Store
  for an unknown credential form) is `unsupported`, as the playground answers.
- **Limits:** reads are accepted only while a forward call is in flight; they run off the EDT; the host's per-call
  counts and byte budgets apply, and IntelliAda's own per-read deadline (10 s).
- **Hygiene:** no redirects at all (`Redirect.NEVER`); the Blockfrost `project_id` is sent only to its configured HTTPS host and
  never logged; parameters are validated against the protocol's formats before they reach a URL.
- **Koios is not offered** in the run panel until an adapter exists (L26).

## 3. Invariants

These continue ADR-004's. This ADR also relies on ADR-004's I15, I18, I20 and I21.

| # | Invariant | Decisions |
|---|---|---|
| I9 (r2) | No JOUST code runs in the IDE process; the only JOUST code IntelliAda ships is the bundled checker, invoked only as the checker, out of process; the dev host is the project's | D11, D12, D13 |
| I10 | No mnemonic or private key reaches a child process's arguments, environment, stdin or files (the checker's included), nor IntelliAda's consoles, notifications or `idea.log`. The host receives addresses only | D13, D13a |
| I11 | IntelliAda signs only a body whose hash it computed from the received bytes and which equals `txHash`. It never re-encodes the body, redeemers or datums, signs only with payment keys, submits only when every required key has a witness, and the submitted id equals `txHash` | D13 |
| I12 | An account can be selected only in sessions of its own network class, in every IntelliAda flow. A mnemonic is held under one class only | D13, D13a |
| I13 | IntelliAda signs nothing unless the bound node's identity is positive for its class (D14) and the class's opt-in is on (D14a) | D13, D14, D14a |
| I14 | Endpoints and credentials come from IntelliAda's application-level node configuration only. The host reads the chain only through IntelliAda (D18) | D11, D14, D18 |
| I16 (amended r1, r2) | IntelliAda's own code never encodes Plutus data for the host, the chain or a form; it decodes only to display the review modal. Encoding and decoding to compare happen only inside the bundled checker | D13, D15 |
| I17 | After a snapshot restore or rollback, the next prepare runs on a fresh host that was restored from the re-read chain state; recorded instances are re-checked | D15, D17 |
| I19 | The dev host and the checker listen on no socket: stdio only | D11, D13 |
| I22 | The host channel enforces protocol 1's framing: header ≤ 1 KiB, body ≤ 4 MiB, a frame complete within 10 s of its first byte, matched ids, no reverse request outside a call, no stray bytes; a violation discards the in-flight result and stops the host. A hard client timeout stops a host that never answers | D11, D11a |
| I23 | IntelliAda signs or exports only a transaction the bundled checker accepted (exit 0, `[]`) for the input IntelliAda assembled and piped to it (only `result` from the host), plus D13's own checks. A refusal is final on every class | D13, D14a |
| I24 (r2) | Every prepare comes from a host whose `fingerprint` equals the digest IntelliAda computed from its snapshot copies, and whose `initialize` contracts and script hashes and whose `deployment` answers equal the anchors IntelliAda derived | D11, D12, D13 |
| I25 (r3) | Before a body is signed for submission or export, its journal entry and the per-chain high-water mark covering it are persisted in one admission under the chain lock; a failed write refuses; after lost history, restore completes only once an observed tip is above the mark | D11, D17 |
| I26 (r2) | Signing decisions use anchors read into memory from the snapshot copies before launch, never the host's answers or a later read of the snapshot (hardening against staleness and corruption, not containment) | D11, D13 |
| I27 (r2) | The checker runs only from files whose SHA-256s equal the manifest built into the plugin, on the IDE's Java 25+ runtime, with its input on stdin; any failure to obtain a clean verdict refuses | D13 |
| I28 | `now` never decreases within a host session except through `reset` | D14b |
| I29 (new) | IntelliAda signs or exports only for a host whose JOUST runtime version and description versions are in the bundled checker's reviewed set | D12, L31 |
| I30 (new) | A handed-out ordinary body is released (settled) only on chain evidence or expiry, never `discarded` | D17 |
| I31 (r3–r5) | The journal and the mark are keyed by the chain, never by the node configuration; a lower tip or a fork never starts an empty journal and is reconciled as a rewind, restoring each entry from its re-read status with its depth recomputed; a devnet counts as recreated only on a different block at height 1 | D14, D17 |
| I32 (r4) | A host's settlement reply is accepted only when it equals the state IntelliAda derives from its own chain evidence; otherwise the reservation is kept and the host fenced | D17 |
| I33 (r4) | Each chain and module has at most one live host; admission checks every live journal entry and pending publication on the chain | D11, D17 |

## 4. Milestones

### 4.0 Invariant to test traceability

| Invariant | Test (milestone) |
|---|---|
| I9 | M3.3a: the plugin zip has `org/julclang/**` classes only under `joust-verifier/`, none on the plugin class path; the checker closure is the minimal one, and its SHA-256s are posted on the PR |
| I10 | M3.3b: a canary mnemonic, searched for in every child's arguments, environment, stdin capture and temp files (host and checker), in the consoles, notifications and `idea.log`; M3.2: account creation logs no mnemonic |
| I11 | M3.3b: the kit's `accept-indefinite-body`, `accept-nonminimal-integers`, `accept-untagged-sets` signed by IntelliAda keep their body bytes and give id `txHash`; a mutated body is refused; a quorum call with two IntelliAda keys has both witnesses, and a missing one blocks submission |
| I12 | M3.2: an account of another class is absent from the run panel and the transfer and mint choosers; a cross-class re-import is refused |
| I13 | M3.1b (binding) and M3.3b (at signing): a fake node reporting magic 4, 1 on a devnet configuration, or a devnet magic without an admin API; a non-allow-listed host; HTTP to an allow-listed host; each refused; M3.5 for testnets |
| I14 | M3.1b: a fixture `joust.config.json` whose `networks.x.start` writes a sentinel is never used |
| I15 (ADR-004) | M3.1b, M3.3b: host strings, descriptions, checker codes and node errors render as plain text (HTML and ANSI in a fixture are shown literally) |
| I16 | M3.3a: an architecture test over IntelliAda's JOUST packages: no reference to a Plutus data encoder; CCL decoding only in the review modal's package |
| I17 | M3.4: a Yano snapshot restore, after which a fresh host is restored and the instance re-checked; a body whose inclusion moved slot is restored at the new slot on the real host |
| I19 | M3.1a (host) and M3.3a (checker): no listening socket belongs to either process |
| I20, I21 (ADR-004) | M3.1a: trust revocation and project close stop the host and delete its snapshot; M3.3a: the checker process ends with its verdict or deadline; M3.3b: the project's file tree is snapshotted before and after a host session and panel use (deploy, call, view) and is unchanged apart from the build's own outputs (ADR-004's I21 test) |
| I22 | M3.1a: the kit's Bank session and seven `violation-*` sessions against `FakeHost`, plus IntelliAda's own tests for a stalled frame, a host that never answers, and a frame after `exit`; M3.3b: `sessions/maps.json`; M3c: `sessions/keyed.json` |
| I23 | M3.3a: the checker adapter agrees with all 333 vectors; a recorded `prepared` (Bank) fed through IntelliAda's **own input assembly** (anchors derived from a snapshot of JOUST's example, reads from a recorded node) is accepted, and one mutation per rule family refused; a checker that crashes, times out, prints extra output or is tampered with refuses |
| I24 | M3.1a: a snapshot copy edited after hashing, a spec digest mismatch, and a host reporting another fingerprint are each refused; M3.3a: anchors derived from JOUST examples' snapshots equal the vectors' `anchors`; the recomputed Counter hash equals the host's; a tampered blueprint `hash`, a duplicate resource, a project class named `OutputVerifier` and a conflicting `deployment` are refused |
| I25 | M3.3b: a crash immediately before signing and between persisting and handout; a failed journal write refuses; two modules sharing a payer on one chain, and simultaneous prepares through two node configurations of one chain; a restart with a lower limit never lowers the mark; with complete history restore resumes at once; a lost journal completes restore only above the mark; a missing chain record beside a surviving journal, and a public chain in the registry with neither, unblock only after the derived bound; with the registry entry and the record both lost the chain stays fenced across restarts; a record lost after a rewind uses the registry's highest tip as the base; an IDE restart during recovery keeps the derived mark and its fence (a restart right after writing the replacement is not complete history); simultaneous admissions on two chains keep both registry maxima |
| I26 | M3.3b: rewriting the snapshot's description after launch does not change the anchors |
| I27 | M3.3a: a changed checker file refuses before any process starts; output `[]` with extra text refuses |
| I28 | M3.1b: a node answering an older tip yields the previous `now` |
| I29 | M3.3a: a description with a bumped `x-joust.version`, a runtime jar whose hash is not reviewed, a `dir` runtime, a `RuntimeVersion` that disagrees with `versions.engine.joust`, and an earlier launch entry holding a same-version copy of `org/julclang/state/runtime/**` each leave the panel read-only |
| I32 | M3.3b: a host answering `confirmed` one slot after inclusion is fenced and the reservation kept; a body restored valid through 2025, a read answered at tip 2070, then `confirmed` at slot and tip 2005, is expected exactly as the real host answers (no fence); a `failed` read carrying tip 2100 does not advance the expected state (`Registry-remove`: `shallow` at 2005, `confirmed` at 2070); two bodies restored together (`Bank-publish-1`, `Bank-init`) with inclusions at 1871 and 1875 are both reported before the poll tip 1951, with no `conflict` |
| I33 | M3.3b: the same module opened through two aliases of one chain is refused while the first runs; restarting one host leaves another host's live reservation untouched; concurrent publications of one shared verifier from distinct payers admit one |
| I30 | M3.3b: a 5xx and a node refusal on submit leave the body reserved until chain evidence or expiry; a shallow rollback returns it to `prepared` and keeps its inputs across a restart; a deep rollback fences building |
| I31 | M3.3b: a lagging provider tip and a shallow rollback keep the journal and the mark; a lower tip below an expired handed-out body's `validTo`, and one more than 900 slots below the last `now`, each trigger reconciliation; a public-network reorg below the depth margin leaves deeper inclusions untouched; a replacement branch with unchanged block 1, one retained and one removed inclusion, restores the retained one `shallow` and the removed one `prepared`; a final inclusion retained after a rewind to within its margin becomes shallow again (inputs held), and a final one that moved slots is restored at its new slot; two node configurations of one chain share one mark; M3.4: an external Yano snapshot restore across an inclusion with an outstanding export fences and reconciles (same identity); a recreated DevKit (new block at height 1) starts a new journal |

### M3: the run panel on devnets and public testnets, Gradle projects

**Entry gates:**
- **G3.0** M1 and M2 exited. **Met** (PRs #19, #22, #25 approved).
- **G3.1** JOUST's dev host exists with a protocol version. **Met for development** at JOUST `01bad1f7`, local Maven
  `0.1.0-pre3-01bad1f-SNAPSHOT` (L23, L29). **Shipping** signing to users needs a JOUST release containing protocol 1
  and its conformance kit; IntelliAda then pins that release. The build refuses a Marketplace distribution whose
  checker comes from a SNAPSHOT (D13), so a development build cannot reach users by accident.
- **G3.2** (replaced in r1): the bundled checker agrees with the kit's 333 vectors (M3.3a), and IntelliAda's signer keeps
  body bytes (I11).
- **G3.3** network identity sources verified per node type (D14): DevKit admin API, Yaci Store genesis and
  `/blocks/1`; Yano's devnet API, genesis and `/blocks/1`; Blockfrost's `/genesis`.
- **G3.4** Yano read coverage: every D18 endpoint answered by Yano as by Yaci Store, or the gap reported to Yano and the
  kind answered `unsupported` (map calls then refused by the host).
- **G3.5** the class-tagged account store (M3.2).

**Parts (L27), each its own stacked PR with the three-reviewer process:**

| Part | Scope | Exit |
|---|---|---|
| **M3.1a** | Launch spec through Gradle, validation, private snapshot and digest, in-memory resources (D11 steps 1–6); the protocol client: framing, JSON-RPC, serial calls, cancellation, deadlines, hard timeout, orderly stop, stderr console; `initialize`, `describe`, `shutdown`/`exit` | the kit's FakeHost Bank session and seven violation sessions; IntelliAda's stall, hang and after-`exit` tests; a real host on the F38 Counter (tool-gated); I19 (host), I20, I22, I24 (snapshot and fingerprint) |
| **M3.1b** | D18's read adapter (paging, chunking); D14's devnet identity, clock and chain identity; `deployment`, `prerequisites`, `view`, `instances`, `objects`, `decode`; the JOUST Run tool window, read-only (no signing) | reads answered for a real host on DevKit and Yano (tool-gated), G3.4's coverage table; I13 (binding), I14, I15, I28 |
| **M3.2** | D13a's `PasswordSafe` account store, class tags, legacy migration, filtered choosers, genesis constants, R7's fix | I10 (accounts), I12 |
| **M3.3a** | The bundled checker (L24): minimal closure, `joust-verifier/` directory, manifest, dependency verification, SNAPSHOT guard, the stdin runner, anchor derivation and the deployment check, input assembly; the version policy (L31); no UI | I9, I16, I19 (checker), I23, I24 (anchors), I27, I29; all 333 vectors |
| **M3.3b** | Signing by splicing (caller and co-signers), submission, the review modal, D17's journal, mark, settlement and restore; Deploy (prerequisites, publications, registration evidence) and calls on devnets; the session history | end to end on DevKit and Yano: a Gradle Counter in Java and in `.jsc` (start, create and fund an account, deploy, increment, view, reset as the owner, close); JOUST's `bank` (owned), a quorum call with two IntelliAda keys on IntelliAda's own fixture project, the kit's `HostCouncil` repackaged
outside `org.julclang.state` (for example `com.bloxbean.intelliada.fixture.HostCouncil`, so D12's package rule admits
it; ADR5-DES-19, ADR5-CDX-18) and rebuilt with the reviewed JOUST (`vote`, as in `quorum-two`; protocol 1 serves `@Quorum` without `@Params`, and every JOUST example with `@Quorum` is
parameterized or keyed: `StakedVault`, `Treasury`, `Registry`; ADR5-DES-16, ADR5-CDX-18), a call with sends (`dex`) and a map call (`token`); a restart between submit and settlement restores; I10, I11, I13 (signing), I25, I26, I30 |
| **M3.4** | Yano controls (time, epoch shift with clock reset, snapshots, rollback with a restored fresh host), DevKit faucet, gutter icons (`.jsc`, Java), recorded instances | a `@ValidAfter` call made valid by advancing Yano's time, and across an epoch shift; a snapshot restore with re-check (I17) |
| **M3.5** | Public testnets behind the opt-in (Blockfrost preprod and preview), export unsigned / import witness sets | the Counter on preprod **and** preview with a testnet account and the review modal, and once through export and import; I13 for testnets |

**M3b (npm):** the npm host, D11's npm launch and fingerprint, the same parts' tests on a `joust new -t node` project.
**M3c (keyed calls):** D17's designation journal and the keyed duties; JOUST's `registry` example end to end,
including a refused keyed call with its cooldown slot and **Advance to slot N** on Yano, an IDE restart between
submit and settlement, and an accepted **future-start** body (the kit's `Registry-remove`: `now` 1929, `validFrom`
2005) that IntelliAda **signs only at `validFrom`** (after the D13 checks again) and then submits: a reset or rebinding
before signing settles the unsigned attempt `discarded`; once signed (or exported for external signing) the attempt is
journaled and stays reserved and recoverable until chain evidence settles it, even if the scheduled send is cancelled
(ADR5-CDX-8, ADR5-CDX-22, ADR5-SEC-17).

**Measurements** (recorded with machine and versions): first-call latency (launch spec, snapshot, host start), warm call
latency, the checker's per-call time, Yaci Store's indexing delay after a submit, and the HTTP requests behind each
read kind on Blockfrost (ADR5-DES-12).

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
- **Plugin size:** the checker and its minimal closure add a measured amount (recorded in M3.3a).
- **Disk:** each live host has its own snapshot (about 95 MB for a Counter); deleted when the host ends, swept at start.
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
| The journal is lost or corrupted | atomic writes under a per-chain lock; the high-water mark kept apart (I25); restore refuses to complete below it; no journal and no mark blocks the chain until the user acts |
| The project's JOUST is newer than the bundled checker | the version policy (L31, I29): read-only until IntelliAda reviews that version |
| Hostile project code (same user) | out of scope (§0 Trust); D13 defends against a buggy host, staleness and corruption |

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
| L30 | Signing and time policy, now **D14b** (r2): fee within the derived cap and the user maximum (3 ADA, ceiling 10); devnet TTL `min(900, ⌊3k/f⌋)`, public 900, exports 7,200 chosen before `prepare`; `settlementSlots` 60 devnet, 2,160 public (accepted exposure stated); monotone `now`; journal keyed by chain identity and module | delegated | 2026-11-25 | both advisors; revised in r2 for ADR5-CDX-7, ADR5-CDX-12, ADR5-DES-8 |
| L31 | Version policy: sign or export only for runtime, host and verifier jars whose SHA-256s are a reviewed set's, whose `RuntimeVersion` and `versions.engine.joust` equal its label, and whose descriptions declare a reviewed `x-joust.version`; otherwise read-only (r3) | delegated | 2026-11-25 | ADR5-SEC-1, ADR5-DES-7 (the same pattern as ADR-004 E22 for LSP4IJ) |
| L32 | For the run panel the bound node's answers are trusted (ADR-0023's model); ADR-004 §2's "defence in depth against a lying endpoint" no longer applies to it; provider integrity rests on D14 | delegated | 2026-11-25 | ADR5-SEC-7; JOUST ADR-0023 (maintainer, 2026-11-23) |
| L33 | Threat boundary: D13 defends against a buggy or misbehaving host, staleness and corruption, not hostile same-user project code | delegated | 2026-11-25 | ADR5-SEC-4, ADR5-DES-5; ADR-004 §2, ADR-0022 §2 |
| L34 | Chain identity is the chain (magic; devnets also the block at height 1), never the node configuration; a lower tip is a rollback or a transient read, never a new chain; recreation only on a positive identity change | delegated | 2026-11-25 | ADR5-SEC-10, ADR5-SEC-11, ADR5-DES-15, ADR5-CDX-6 |
| L35 | A lost chain record is replaced by a bound derived from the policy's hard ceilings over the highest tip and `now` ever recorded (r5), persisted with an explicit recovery fence and waited out; with the registry entry also lost, the chain stays fenced until the files are restored or a devnet is recreated (r7); no user confirmation ends a recovery fence; public chains are never forgotten | delegated | 2026-11-25 | ADR5-SEC-15, ADR5-CDX-3, ADR5-DES-18 |

Delegated decisions (L27, L28, L30–L35) are tracked for the maintainer's ratification on this PR.

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

## 10. Review round 1 (head `73969bc`) and where r2 answers it

| Finding | Change in r2 |
|---|---|
| ADR5-CDX-1, ADR5-SEC-4, ADR5-DES-5 | §0 Trust (boundary, L33); D13 process: input on stdin, empty working directory, D3 environment; I23, I26, I27 reworded |
| ADR5-CDX-2, ADR5-SEC-5 | D17 store and lock, "before signing", failed writes refuse; D11 overlap check across hosts; I25 |
| ADR5-CDX-3, ADR5-DES-2 | D17 entry schema, restore rules (retained inclusions, approvals invalidated, returned ids, lost history, no-mark block) |
| ADR5-CDX-4, ADR5-SEC-3, ADR5-DES-1 | D17 settlement: `discarded` only before handout (keyed in M3c); I30 |
| ADR5-CDX-5, ADR5-SEC-2, ADR5-DES-3 | D13 anchors (resolution as `ValidatorScripts`, recomputed hashes, CCL addresses, npm sources) and the deployment check; F42 corrected; I24 |
| ADR5-CDX-6 | D14 era-correct clock, `reset({now, clock})` after a Yano shift, chain identity vs recreation |
| ADR5-CDX-7 | D14b devnet TTL from ⌊3k/f⌋ |
| ADR5-CDX-8 | D13 input `clock`, `orderedReads`; F41 corrected |
| ADR5-CDX-9, ADR5-DES-6 | D13 co-signer witnesses, complete-signer rule, imported witnesses; M3.3b exit adds quorum, sends and maps; I11 |
| ADR5-CDX-10 | D15 registration evidence |
| ADR5-CDX-11 | D18 paging and chunking; registration 404 |
| ADR5-CDX-12 | D14b settlement depth with stated exposure |
| ADR5-CDX-13, ADR5-DES-10 | §4.0 rows for I15, I20, I21; I19 split; I13 at signing; input assembly test; `maps.json`, `keyed.json`; preview in M3.5 |
| ADR5-SEC-1, ADR5-DES-7 | D12 version policy (L31), I29 |
| ADR5-SEC-6, ADR5-DES-4 | D12/D13 minimal closure, dependency verification, SNAPSHOT guard; I9 and I16 reworded; I16 test scoped |
| ADR5-SEC-7 | §0 Trust, L32 |
| ADR5-SEC-8 | D11 steps 2–3 (Java 25 `release`, `Class-Path` refused, atomic creation, no reuse, deletion) |
| ADR5-SEC-9, ADR5-DES-12 | D13 (`[]` + one terminator; Windows environment; vkey-only imports); D11 (`ParentEnvironmentType.NONE`, no thread dump; JOUST version from the runtime jar); D16 default for other codes; D18 `Redirect.NEVER`; snapshot size and cleanup in §5 |
| ADR5-DES-8 | D14b |
| ADR5-DES-9 | D11 serial calls and the overlap rule |
| ADR5-DES-11 | D15 tool window from built descriptions; D11 step 5 version from the runtime jar |

### Review round 2 (head `c6b1a33`) and where r3 answers it

| Finding | Change in r3 |
|---|---|
| ADR5-CDX-2, ADR5-SEC-11, ADR5-DES-15 | D14 chain identity without the node configuration; D11 atomic admission (spending, collateral and reference inputs; entry and mark under one lock); D17 directory sync; I31 |
| ADR5-CDX-3 | I25 aligned with D17 (the mark waits only after lost history); unreadable mark; Forget this chain only after a different identity, never on public chains |
| ADR5-CDX-5, ADR5-DES-13, ADR5-SEC-12 | D13 verifiers resolved from the `julc-state-verifier` jar that holds their classes; collision refused; I24 test |
| ADR5-SEC-13, ADR5-DES-12 (hash) | D13 exact hash recipe (tag ‖ `cborHex` unwrapped once), checked against F38's `5de2e73f…` |
| ADR5-CDX-6, ADR5-SEC-10 | D14: a lower tip is a transient read or a rollback, never a new chain; unexplained rewinds fence and reconcile; recreation only on a different block at height 1; L34 |
| ADR5-CDX-8 | M3c future-start submission test; `orderedReads` dropped |
| ADR5-CDX-14, ADR5-DES-14, ADR5-SEC-14 (version) | D11 step 5 reads `RuntimeVersion` from bytecode; D12 reviewed set by jar SHA-256s, cross-checked with `versions.engine.joust`; npm sources; I29 tests |
| ADR5-CDX-15 | D11: orderly stop vs violation (no frame after a violation; bounded EOF wait) |
| ADR5-CDX-16 | D17 state separate from events and fences; I30 tests for rollbacks |
| ADR5-CDX-17 | D18 page accumulation under the remaining budget and one deadline |
| ADR5-DES-12, ADR5-SEC-14 (rest) | D11 overlap → `discarded`, reference inputs; D13 one runner on every OS, collateral witnesses; D17 chain registry, approvals removed; I21 test; `StakedVault` named; measurements |

### Review round 3 (head `907cb56`) and where r4 answers it

Security approved round 3; its P2 and P3 items are folded in here.

| Finding | Change in r4 |
|---|---|
| ADR5-CDX-3, ADR5-SEC-15, ADR5-DES-18 | D17 chain record (mark plus the module journals it covers); a lost mark gets a derived bound from the hard ceilings, waited out with no user confirmation; everything-lost residual stated; L35 |
| ADR5-CDX-6, ADR5-DES-17 | D14 fork reconciliation under one identity, per entry by `txHash`; "persistent" defined; recreation only on block 1; I31 tests |
| ADR5-CDX-14, ADR5-SEC-16 | D12: no other launch entry may hold reviewed packages' classes; `julc-state-serve` added; I29 test |
| ADR5-CDX-18, ADR5-DES-16 | M3.3b quorum exit on IntelliAda's `HostCouncil` fixture |
| ADR5-CDX-19 | D11 one live owner per chain and module; I33 |
| ADR5-CDX-20 | D17 settlement replies checked against IntelliAda's derived state; I32 |
| ADR5-CDX-21, ADR5-SEC-17 | D11 admission over every live journal entry and pending publication components; M3c future-start wording |
| ADR5-DES-18 (rest) | §7 order; D14 "waiting for the devnet's first block" |

### Review round 4 (head `1beb938`) and where r5 answers it

Security and design approved round 4; their non-blocking items are folded in where cheap.

| Finding | Change in r5 |
|---|---|
| ADR5-CDX-3, ADR5-SEC-18, ADR5-DES-21 (bound) | D17: the registry keeps the highest tip and `now` per chain; the lost-mark base is the highest of those, surviving journals and the tip at detection; the derived mark is persisted before anything lifts; both files unreadable keeps the chain fenced |
| ADR5-CDX-6, ADR5-DES-21 (final re-inclusion) | D14: every persistent lower tip or fork is reconciled as a rewind (`reset` with `rewound`, restore from re-read status, depth recomputed) |
| ADR5-CDX-22 | M3c: future-start attempts are signed at `validFrom`; once signed or exported they stay journaled until settled |
| ADR5-CDX-14 (non-blocking) | D11 step 4: read-only start when the anchors cannot be derived under the reviewed set |
| ADR5-CDX-18, ADR5-DES-19 (non-blocking) | M3.3b: the `HostCouncil` fixture repackaged outside `org.julclang.state` |
| ADR5-DES-20 (non-blocking) | D17: expected state from the highest tip given to the host; first `confirmed` carries the inclusion slot; polling cadence |
| ADR5-DES-21 (api) | D12: `julc-state-api` pinned and its package covered |

### Review round 5 (head `11a171d`) and where r6 answers it

Security and design approved round 5.

| Finding | Change in r6 |
|---|---|
| ADR5-CDX-3, ADR5-SEC-19 | D17: no override for an unknown bound; persisted recovery fence beside any replacement mark; public chains derive a k-block-margin bound, devnets stay fenced until recreated; L35 |
| ADR5-CDX-6 | D14, D15, D17, I17: reconciliation restarts the host with `restoring:true` (no live `reset` with `rewound`), so a moved inclusion restores at its new slot |
| ADR5-CDX-23 | D17: settlement predicted by replaying every tip given to the host in order; I32 test sequence |
| ADR5-CDX-24 | D17: one registry file per chain, under that chain's lock |
| ADR5-DES-22 (non-blocking) | D14: only forks touching journal inclusions trigger reconciliation |

### Review round 6 (head `f4b7763`) and where r7 answers it

Security and design approved round 6.

| Finding | Change in r7 |
|---|---|
| ADR5-CDX-3 | D17: with the registry entry and record both lost, no slot bound exists; the chain stays fenced until the files are restored or a devnet is recreated (supersedes r6's k/f margin, ADR5-SEC-19); L35 |
| ADR5-CDX-23 | D17: only tips the host accepted advance the expected state; uncertain tips go to an upper copy; I32 failed-read sequence |
| ADR5-CDX-25 | D17: inclusions reported first, ascending, each at its slot, then the current tip; no forward call while one is unreported; I32 two-body sequence |
| ADR5-SEC-20 (non-blocking) | D14: reconciliation also triggers for an expired handed-out body a rewind revalidates, and for a lower tip more than 900 slots below the last `now`; I31 tests |
| ADR5-DES-23 (non-blocking) | noted for M3.3b: every live host on the chain is restarted under the chain's fence, and `registered` evidence is re-sent after a restart |

## Revision history

- **d1** (2026-11-19): moved from ADR-004 r2's Part C; advisor changes L14 and L17.
- **d2** (2026-11-19): carries round 2 of PR #9 (CDX-20, CDX-2 note, SEC-13).
- **r1** (2026-11-25): amended to JOUST dev-host protocol 1 at `01bad1f7` (G3.1). D11's launch spec, snapshot, digest
  and in-memory anchors; D11a mapped to the protocol; D13 rewritten around the protocol's rules run by the bundled
  reference checker (L24), with splicing for signatures; D14 chain identity and clock; D15 flow through
  `prerequisites`/`publish`; D16 by kind/code; new D17 (journal) and D18 (reads); I16 amended (L25); I25–I28; G3.1 met
  for development, G3.2 replaced; M3 split into seven parts (L27); facts F38–F44; decisions L23–L30. Status Proposed.
- **r2** (2026-11-25, responds to review round 1 of `73969bc`): §0 trust boundary and provider trust (L32, L33); D11
  launch hardening, serial calls, orderly stop; D12 version policy (L31); D13 stdin checker, anchor derivation and the
  deployment check, co-signers and complete signers, vkey-only imports; D14 clock and chain identity; new D14b (was
  L30); D15 tool window and registration evidence; D16 default; D17 rewritten (store, lock, before-signing persistence,
  settlement without discarding handed-out bodies, restore, lost history); D18 paging; I9, I16, I23–I27 reworded; I29,
  I30 new; §4.0 and M3 parts revised; §10 maps every round-1 finding.
- **r3** (2026-11-25, responds to review round 2 of `c6b1a33`): chain identity without the node configuration and
  without recreation on a lower tip (D14, L34, I31); atomic admission across hosts (D11); verifier anchors from the
  verifier jar and the exact hash recipe (D13); the version policy by artifact hashes and `RuntimeVersion` (D11, D12,
  L31); orderly stop vs violation (D11); journal state vs events, chain registry, lost-history rules (D17); bounded page
  accumulation (D18); I25 aligned; M3c future-start test; §10 round-2 table.
- **r4** (2026-11-25, responds to review round 3 of `907cb56`; security approved it): the chain record and lost-mark
  bound (D17, L35); fork reconciliation per entry (D14); effective-class rule for the reviewed artifacts (D12); one live
  owner and admission over every journal entry and pending publication (D11); settlement replies checked (D17); the
  `HostCouncil` quorum fixture (M3.3b); I32, I33.
- **r5** (2026-11-25, responds to review round 4 of `1beb938`; security and design approved it): lost-mark base from the
  registry's highest tip and `now` (D17, L35); forks and lower tips reconciled as rewinds (D14, I31); future-start
  attempts signed at `validFrom` (M3c); read-only start without reviewed anchors (D11); `julc-state-api` pinned (D12);
  settlement tips and cadence (D17); the `HostCouncil` fixture package (M3.3b).
- **r6** (2026-11-25, responds to review round 5 of `11a171d`; security and design approved it): reconciliation by
  restarting the host (D14, D17, I17); settlement replayed in tip order (D17, I32); persisted recovery fences and no
  override for an unknown bound, k-block margin on public chains (D17, L35); a registry file per chain (D17).
- **r7** (2026-11-25, responds to review round 6 of `f4b7763`; security and design approved it): settlement advanced only
  by tips the host accepted, inclusions reported first in slot order (D17, I32); no slot bound after losing both the
  registry entry and the record, so the chain stays fenced (D17, L35); wider reconciliation trigger (D14, I31).
