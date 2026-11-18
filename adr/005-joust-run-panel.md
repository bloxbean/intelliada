# ADR-005: Running JOUST contracts from IntelliAda (devnets, testnets, mainnet)

**Status:** Draft. These are requirements, given as input to JOUST's dev-host ADR. This ADR becomes Proposed, and is
reviewed, once that ADR exists (G3.1).

**Date:** 2026-11-19.

**Authors:** Claude Code, drafted for the IntelliAda maintainers.

**Scope:** design only. It adds no code, build change or plugin.xml entry.

**Origin.** This is Part C of ADR-004 r2 (PR #9), moved here unchanged in substance on 2026-11-19 (decision L16 in
ADR-004 §11). The move lets Parts A and B be accepted on their own.
- **Kept from ADR-004:** decision IDs (D11–D16, D11a, D13a, D14a), invariant IDs (I9–I14, I16, I17, I19, I22–I24) and
  milestone IDs (M3, M3b, M3c, M5), so the round-1 review findings still point at the right text.
- **Cited from ADR-004, not copied:** facts **F1–F37** (§1.1), the threat model **§2** (assets, T1–T8, trust
  boundaries), the rule **D1**, and the shared invariants **I15, I18, I20 and I21**.

**Builds on:**
- IntelliAda ADR-004 (JOUST support: `.jsc` and projects) and ADR-001 to ADR-003.
- JOUST ADR-0009 D5, ADR-0013, ADR-0015, ADR-0017 and ADR-0018 D12, at JOUST `main` `d529bfd`.
- **Depends on** a JOUST dev-host ADR that does not exist yet (ADR-004 Q4, decided; illustratively JOUST ADR-0022).

**Risk:** high. IntelliAda would start project processes, sign with keys it holds, and, behind opt-ins, spend real funds
on mainnet.

## 1. Context

The maintainer asked for a way to deploy and call a JOUST contract **directly from its code in the editor**:
- first on a local DevKit or Yano devnet;
- then on public testnets and mainnet, each behind an explicit opt-in (ADR-004 §11, L4).

ADR-004 decides the editor and project support this builds on: D1's trust rule, the project detection in D7, and the
task actions.

Review round 1 of ADR-004 (design DES-1, DES-4, DES-5, DES-7, DES-9 and DES-10; security SEC-3 to SEC-7; Codex CDX-2
to CDX-8 and CDX-16) shaped every section below. Its findings are answered in the text, and the table in §8 maps each one
to where.

**Two questions this ADR adds to ADR-004 §2:**
- **Chain reads.** The host reads the chain only through IntelliAda (H10), so a node endpoint and its credential are
  never *transmitted* to project code. Trusted project code runs as the user and can read IntelliAda's own
  configuration, including a Blockfrost `authKey`, which IntelliAda stores in plain text today (ADR-004 R8). So H10's
  reason is D14's single node binding, not keeping a credential secret.
- **Funds.** On public networks the assets now include real funds. D14a's opt-ins, the mandatory mainnet review, and
  D13's allow-list as hard refusals are the controls for them.

## 2. Decision

### D11. Where off-chain execution runs: a dev host from the project's own JOUST, in its own process

The four candidates:

| | Project's JVM: generated client and `StateRuntime`, separate process | `julc-state-server` prepare service | JS engine (`@julclang/joust-runtime`, `joust dev/serve`) with Node | IntelliAda's in-IDE CCL |
|---|---|---|---|---|
| Runs the project's JOUST, JuLC and CCL | yes | yes (if run from the project) | yes (npm projects) | **no**: IntelliAda's CCL 0.6.2 and julc (F1, F4) |
| Same off-chain code the project ships (JVM `Require`, ADR-0018 D12 sites) | yes | yes | yes (described contracts) | no: a second implementation of JOUST's transaction building |
| Gradle projects | yes | yes | **no**: needs `build/joust/contracts.json`, which only `joust build` writes (F20) | n/a |
| npm projects | no | no | yes | n/a |
| Keys stay in IntelliAda | yes, with `WalletAccount` (F21) | yes (holds no keys) | yes | yes |
| Isolated from the IDE JVM | yes | yes | yes | **no** |
| Exists today as a runnable entry | **no** generic entry point | **no** main; a browser-facing HTTP library; refuses keyed creation and co-signed calls; no listing, history or events (F21) | `joust dev` exists, but takes its network from `joust.config.json` and may spawn its `start` command (F23) | yes |

**Decision: a "dev host".** It is the project's own JOUST runtime, run in a separate process, speaking a small JSON-RPC
protocol on stdio whose call vocabulary is the prepare service's. IntelliAda is the wallet, the chain client and the
submitter; it never builds the transaction. Q4 is decided: a new JOUST ADR (illustratively ADR-0022) defines the host.
It must meet D11a's requirements, or this ADR drops the feature that depends on the missing one.

- **Gradle projects (M3):** the host runs on the project's runtime class path and Java toolchain (25, F19), with the generated clients and descriptions on that class path.
- **npm projects (M3b):** the same protocol over the project's `@julclang/joust-runtime` `engineService` (F23).
- **Rejected alternatives:**
  - **In-IDE CCL:** a second implementation in another CCL and JuLC version, so wrong scripts and wrong addresses, and the project's code would not run.
  - **`JoustServer` over HTTP as it is:** there is no entry point; it is a browser service with CORS and rate limits; and it would be an unauthenticated local port where stdio needs none.
  - **`joust dev`:** it chooses the network from project files, against T3.
  - **Loading JOUST into the IDE JVM,** as ADR-003 does for julc: the project's version, its generated classes, RocksDB natives and Java 25 bytecode do not belong in the IDE.
  - **An IntelliAda-written launcher shipped into the project's JVM:** it would be compiled against one JOUST API and run inside every project's version.
- **Launch (Gradle, illustrative):**
  1. IntelliAda asks the project's Gradle, through IntelliJ's Gradle integration, for a **launch spec**: a JOUST plugin task that prints the toolchain's `java`, the runtime class path including the host, the host's main class and the protocol version. IntelliAda keeps the spec **in memory only**, never in `.idea/`.
  2. The **first start** in each IDE session shows a confirmation naming the project, the `java` and the main class (D1).
  3. IntelliAda then starts `java -Djoust.mapStore=memory -cp … <main> --stdio` with `GeneralCommandLine`. `joust.mapStore=memory` keeps the host from writing `./.joust/mpf` into the project, and lets a restart rebuild mirrors after a rollback (F25).
- **What the host gets from IntelliAda:**
  - the network id and protocol parameters;
  - per call, the caller's **address** and "now" (H9);
  - **chain reads through IntelliAda** (H10).

  Nothing else: no key, no endpoint URL or credential (a Blockfrost project id never leaves the IDE), and nothing from `joust.config.json`.
- **Lifecycle (I20, I24):**
  - **Started** on the first run action in a trusted project (D1), one per project.
  - **Stopped** on any of these:
    - project close;
    - trust revocation;
    - a build-file change;
    - **a build-output change:** descriptions under `META-INF/joust` or class files on the host's runtime class path;
    - **Stop** in the run panel.
  - **Prepares from a stopped host** are discarded.
  - Its stderr goes to a "JOUST host" console, capped. Stdout carries only the protocol.

### D11a. Requirements on the JOUST dev host (new in r2; G3.1 checks them)

| # | Requirement | Needed by |
|---|---|---|
| H1 | `hello` → JOUST, JuLC and CCL versions; the protocol version; contracts; each contract's **script hashes**; and a **build fingerprint** (a digest of the descriptions and generated classes it loaded) | D12, I24 |
| H2 | `describe(contract)` → the description and message map | D15, D16 |
| H3 | `deployment(contract)` → addresses, policy ids, script hashes, reference-script outputs and governors (`@AuthorizedBy`). `@Params` contracts give a stable `unsupported` error until JOUST serves them | D13, D15 Deploy |
| H4 | `prepare(request)` → `Prepared` (F21's request, plus `cosigners` per Q5 and `designate` per H16). It also returns **predicted effects**: new state, events and payouts in the description's JSON encoding, labelled predicted | D13, D16 |
| H5 | `view(contract, instance, view \| null, args)` without HTTP | D15 |
| H6 | `instances(contract)` and `objects(contract, instance)` → UTxO refs and decoded datums | D15 |
| H7 | `decode(contract, datumCbor)` → JSON | D15 |
| H8 | `publish(contract, address)` → an unsigned reference-script transaction **with a validity upper bound** (today's has none, F33) | D13 check 7 |
| H9 | **Time from the client:** the host takes "now" (slot) from IntelliAda per call, never from the machine clock, so validity windows stay right after Yano's time advance and epoch shift (F32) | D13, D15 time controls |
| H10 | **Chain reads through IntelliAda.** The property: every chain read the host makes is answered by IntelliAda from the session's bound node (D14), and the host is never sent an endpoint or a credential.<br>**The mechanism is JOUST's ADR to choose.** Either reverse requests over the same stdio channel, or ADR-0015's snapshot-and-`Need` loop. JOUST's `StateRuntime.builder(BackendService)` is the seam, and `SnapshotBackend` is an existing I/O-free `BackendService`.<br>**Rules for reverse requests:**<br>• the vocabulary is closed and versioned in `hello` (`chain/utxos`, `chain/utxo`, `chain/tip`, `chain/params`, optionally `chain/evaluate`), with no submit;<br>• requests are accepted only while a forward call is in flight, under per-call count and size caps (I22);<br>• they are answered off the EDT, and H12's deadlines cover them.<br>`chain/evaluate` is optional, because the host can evaluate locally at the project's JuLC version. The reason for H10 is D14's single binding, not credential secrecy (ADR-004 R8) | D14, D14a, I14 |
| H11 | `settled(txHash, status)` (confirmed, rolled back or expired) → the host updates its mirrors. IntelliAda does not prepare again on the same instance or object until the previous call settles | D15, D16 |
| H12 | Cancellation (`$/cancelRequest`) and a host-side deadline per call. IntelliAda stops the host after a hard timeout | D15 |
| H13 | **Framing:** Content-Length frames, at most 4 MiB each, with ids matched and unknown ids dropped. The host sends the project's `System.out` to stderr **before** loading project classes, so contract or library output can never corrupt or forge a frame | I22 |
| H14 | Errors as `{ kind: refusal \| bad \| internal \| unsupported, code, message, sites[], slot? }`, carrying `JoustException`'s fields and ADR-0018 D12's message-map sites (F24) | D16 |
| H15 | `reset(now)` → re-read the clock from IntelliAda and drop map mirrors | D15, I17 |
| H16 | **Keyed designation (M3c):** the host accepts a designated input and the protocol defines retry and settlement. IntelliAda persists each pending keyed operation (designated input, `txHash`) in application state until it settles, as JOUST's JS client does (F32) | M3c |
| H17 | `history(instance)`: *not* required for M3 (Q6, decided: session history only) | later |

### D12. Which JOUST runs: the project's, never IntelliAda's

- **The host and every task are the project's.**
  - **Gradle projects:** the plugin version pins the runtime, processor, JuLC and CCL (F19).
  - **npm projects:** `package.json` and the lockfile pin `@julclang/joust` and `@julclang/joust-runtime`.

  A script's hash depends on the JuLC version that compiled it, so a bundled JOUST would deploy a different script at a different address.
- **IntelliAda bundles no JOUST or JuLC runtime** for this feature (I9).
  - It shows the host's versions (`hello`) and refuses a host whose protocol version it does not support.
  - **Before Deploy and before each call,** it checks the host's script hashes and build fingerprint (H1) against the descriptions on disk; on a mismatch it restarts the host (I24).
  - **Before Deploy,** it offers to run the build when any contract source is newer than its description.
- **The language server's compiler** (D5) may differ from the project's. The run panel never uses the language server.

### D13. Accounts, keys and signing

**Accounts.** The caller list shows only accounts whose network class matches the session's network (D13a, I12):
- on devnets, the devnet's public genesis accounts as well (D13a);
- co-signers (Q5, decided: through the host ADR, H4) come from the same class.

**Keys stay in the IDE process (I10).**
- The host receives the caller's address and builds for a `WalletAccount` (address only, fails closed, F21).
- No mnemonic or private key appears in any child's arguments, environment, stdin, files or logs, nor in IntelliAda's consoles, notifications or `idea.log`.

**Before it signs, IntelliAda checks the transaction against an allow-list** (I11, I13, I23). Every check uses
IntelliAda's own parse, and IntelliAda's own resolution of every input through the session's bound node, never the host's
reads:
1. **Body hash:** the body hash, computed from the **body bytes as received**, equals `Prepared.txHash`.
2. **Inputs:** every input, reference input and collateral input resolves on the bound node.
3. **Network:** the body's network id, if present, and every output address match the session's network class (D14).
4. **Signers:**
   - the body's `required_signers` are a subset of the caller's payment key hash and the co-signers the user picked;
   - IntelliAda adds witnesses only from **payment** keys, never a stake key.
5. **Certificates, withdrawals and minting:**
   - certificates and withdrawals are allowed only for script credentials of the selected deployment (H3), and withdrawals only of 0 (JOUST's withdraw-zero pattern);
   - voting procedures, proposal procedures and treasury donations are refused;
   - minting is allowed only under the deployment's policies.
6. **Collateral:** collateral inputs belong to the caller, and the collateral return goes to the caller. The value at risk is `total_collateral` when it is present, and otherwise the **full value of the collateral inputs**; it is at most 5 ADA, which matches JOUST.
7. **Validity and fee:**
   - the validity upper bound is **present** and at most 900 slots ahead of the **bound node's tip**, converted by that node's slot length and never by the wall clock. That is 15 min, and matches JOUST's `maxValiditySlots`;
   - **Export unsigned transaction** (D14a) asks H4 for a longer TTL and allows up to 2 h;
   - the fee is at most 3 ADA on every class (JOUST's `maxFee`). This is an application setting with a hard ceiling of 10 ADA, never read from the project.
8. **Caller outflow:** the caller's net outflow is at most what the call declares, computed as the value of the caller's inputs minus the value of outputs to the caller's address. What the call declares is:
   - the call's `payable` lovelace and `takesAssets`;
   - for a creation, the lovelace locked in the new UTxO, up to the payment plus `minAda`;
   - the fee;
   - deposits for the deployment's script registrations.

   JOUST's wallet verifier (`js/state-client/src/verify.js`, ADR-0013 D2, ADR-004 F33) is the reference for checks 5–8, and M3 runs its fixtures through IntelliAda's checks.
   - **Devnet:** anything more is refused.
   - **Testnet:** anything more requires the review modal.
   - **Mainnet:** anything more is refused.
9. **`is_valid`:** the flag is `true`.
10. **The transaction is the requested call:**
    - its script inputs and redeemers are those of the requested deployment, instance or object, and method, as JOUST's wallet verifier checks them (`verify.js:260-301`);
    - its outputs to the contract go to that deployment's address.

    Because of I16, the redeemer comparison uses the host's `describe`/H4 metadata for the call (the redeemer index and constructor tag), never IntelliAda's own Plutus encoding (CDX-2, round 2).

**Signing** uses CCL 0.6.2's `TransactionSigner.sign(byte[], HdKeyPair)` (F35).
- It adds a vkey witness over the body bytes as received, and IntelliAda never re-encodes the body.
- IntelliAda submits through its own node service and checks that the submitted id equals `txHash`.
- **External signing** (D14a) instead exports the unsigned transaction and accepts a witness set back. Each witness must verify against `txHash`, and its key must be a required signer.

**Review.** The review modal shows:
- the network class and name;
- the caller's net outflow (ADA and assets);
- the fee;
- every output not to a script of the deployment;
- the required signers;
- the validity window;
- any check that escalated.

When it appears depends on the network class:
- **Devnet:** off by default; the application setting **Confirm every devnet transaction** turns it on.
- **Testnet:** on by default, and it can be turned off per testnet. **An escalation (check 8) always shows the modal,** with the exact excess and where it goes, even when the modal is turned off.
- **Mainnet:** always shown, and it cannot be turned off.

**Calls the panel does not offer yet:**
- **Co-signed calls** (`@Quorum`, `@AuthorizedBy`, or a `@SignedBy` field other than the caller) are "not available from IntelliAda yet" until H4's `cosigners` ships.
- **Keyed calls**, including keyed creation, wait for H16 and M3c (CDX-7). They are not tied to Q5.

**Publication** (`publish`) is signed only if it has a validity bound (H8, check 7).

### D13a. The account model (new in r2; Q12 decided)

- **A new account store in IntelliJ's `PasswordSafe`.** Each account is tagged with **one network class**: `devnet`, `testnet` or `mainnet`. The run panel uses only this store.
  - **`devnet` accounts are only ever created fresh** (or are the genesis constants), never imported. ADR-004 §2 states the assumption behind them: a class controls which keys IntelliAda offers and uses, and cannot make a key worthless elsewhere. Devnet keys are therefore safe only if they are used only on devnets, and a fresh key is the only kind IntelliAda can vouch for (CDX-20).
  - **`testnet` and `mainnet` accounts** may be created fresh or imported, **for that class only**.
- **Legacy accounts.** They are migrated by an explicit action, which also starts fixing #10, and are never offered on mainnet.
- **Class tags hold everywhere** (I12):
  - every account chooser in IntelliAda hides accounts of another class, including today's `addr_test` filter (F9);
  - importing a mnemonic that is already held under another class is refused, in either direction (the check compares a key fingerprint, never the stored mnemonic in plain text).
- **Devnet genesis mnemonics** (DevKit's default, F25, and Yano's) are **IntelliAda constants**, never read from the project, the node or `joust.config.json`.
- **A mnemonic is shown only on explicit request** ("Show recovery phrase…"), never written to a console, notification or log (I10). Today's `CreateAccountAction` prints it (R7).

### D14. Networks and network classes

- **Eligible nodes:** IntelliAda's own node configurations only, never an endpoint from the project (I14). That rules out `joust.config.json`'s `networks` and their `start` commands (F23), and Gradle properties.
- **The run panel session is bound to one explicit `RemoteNode`,** chosen in the panel (not the application default).
  - **Its node service** uses the `(RemoteNode, …)` constructor, which starts nothing (F8), so a devnet starts only on the panel's **Start** click.
  - **The same binding** serves every read, H10 chain read, faucet call, admin control, signature check and submission.
  - **Pending work is invalidated** (prepares discarded, the host reset) when the bound node, its endpoint or genesis, or the project's trust changes.
- **Network identity is positive,** never only a deny-list:
  - **Devnet:** the node type is `YaciDevKit`, `LocalYaciDevKit` or `Yano`; the endpoint host is loopback or on the application-level allow-list; the node's magic equals the **devnet magic recorded in IntelliAda's node configuration** (default 42 for Yano, F37; DevKit's as configured); **and** the node's devnet admin API answers. Yano's is `/api/v1/devnet/…`, and DevKit's is `/local-cluster/api/admin/devnet`. DevKit's admin endpoint is a new application-level field on `RemoteNode`, with a documented default of the same host on port 10000 (F5, F25).
  - **Testnet:** a node configuration explicitly marked as preprod (magic 1) or preview (2), with the node's reported magic equal to it (D14a).
  - **Mainnet:** a node configuration explicitly marked mainnet (764824073), with the node's reported magic equal to it (D14a).
  - **Any other magic is refused,** SanchoNet's 4 included (F37).
  - **Where the magic comes from on each node type** (a Blockfrost-style `GET /genesis`, Yano's genesis download, DevKit's admin API) is an M3 entry gate.
- **Allow-listed non-loopback devnet hosts require HTTPS.** Otherwise D13's network checks would rest on an unauthenticated channel.

### D14a. Public testnets (M3) and mainnet (M5), each by explicit opt-in (new in r2; Q3 decided)

Q3 is decided, superseding r1's hard refusal: support testnets and mainnet, each behind its own opt-in.

- **Opt-ins:** two application-level settings, both off by default: **Allow the JOUST run panel on public testnets** and **Allow the JOUST run panel on mainnet**. Turning either on shows what it enables. For mainnet, the user must also confirm per panel session.
- **Endpoints:** IntelliAda's Blockfrost or Koios node configurations, over HTTPS. Their credentials stay in IntelliAda (H10).
- **Accounts:**
  - testnet-class accounts on testnets;
  - mainnet-class accounts on mainnet, held only in `PasswordSafe` (D13a);
  - **Export unsigned transaction** and **Import witness set,** for a hardware wallet or `cardano-cli`, on testnets and mainnet.
- **Checks:** all of D13's. On mainnet every escalation is a refusal, and the review modal is mandatory.
- **Panel features:** no faucet, time, snapshot or rollback controls on public networks. Session history only (Q6).
- **Phasing:** testnets ship in M3, alongside devnets; mainnet ships in M5, with its own gates.

### D15. The run panel: from the code to a network

- **Entry points:**
  - **`.jsc`:** gutter icons on `init`, each action, destroy and view. They come from a `LineMarkerProvider` registered for `language="textmate"`, filtered to `*.jsc`, so the platform handles invalidation and disposal.
    - **Positions** come from JOUST's requested `joust/entryPoints` (description kind, name and selection range: D6 amendment item (e), or Q4's ADR). Until that exists they come from document symbols matched by name **and** `detail` (F34).
    - **When the text and the last build disagree,** the icon shows a stale marker and opens the panel with "build first".
  - **Java:** a `LineMarkerProvider` on methods annotated `@Init`, `@Action`, `@View`, `@Destroy`, `@OwnedInit`, `@OwnedAction` or `@OwnedDestroy` (`org.julclang.state.api`), resolved by qualified name.
  - **A JOUST Run tool window** lists the contracts whose descriptions are in the build output.
  - Icons are always visible, but only open the panel. Nothing runs without D1.
- **Flow:**
  1. Choose the network (D14, D14a). On a devnet, start Yano or DevKit with the panel's **Start** button.
  2. Choose the caller (D13, D13a).
  3. Fund it. Yano: `fundAddress` (F6). DevKit: a new admin client for `/local-cluster/api/addresses/topup` (F25). Devnets only.
  4. **Deploy:** H3, then the `init` creation, and reference scripts if wanted.
  5. Call actions and views.
- **Forms from the description** (F20) are produced in the description's own JSON encoding (`x-joust.encoding.json`):
  - bytes as hex, with pickers for an account's key hash and the caller's;
  - integers as decimal strings;
  - Bool recognised by its shape (`anyOf` of `False`/`True` constructors);
  - Optional as "None" or a value;
  - lists as rows, maps as key-value rows;
  - sum types as a `$variant` selector with that variant's fields;
  - `Data` as CBOR hex.

  `payable` adds lovelace, and `takesAssets` adds asset rows. `policies` are shown: who must sign, and the time window. Owned calls pick an object (H6). **IntelliAda never encodes or decodes Plutus data for a contract** (I16): the host does that, in the project's version.
- **Untrusted descriptions** have caps: size (illustrative: 1 MiB), schema depth (32), and field count (500). `$ref` cycles are refused, and every label is plain text (I15).
- **State.** The panel shows:
  - the instance's state, decoded by the host (`view` with no view);
  - its UTxO (reference, value, datum CBOR);
  - keyed and owned objects;
  - the **session history:** each call IntelliAda submitted, with method, arguments, transaction id, status (pending, confirmed, rolled back or expired, H11), execution units, and the **predicted** effects (H4) until the call is confirmed. After confirmation the panel reads the state again with `view` and shows the diff.

  A call whose transaction is rolled back is never shown as a confirmed state transition.
  **Chain history** (other parties' calls) and **chain events** are not in M3 (Q6, decided). They need ADR-0009 D5's provenance rule and transaction data that DevKit's Yaci Store does not serve (F29).
- **Time, faucet, snapshots and rollback (devnets).**
  - **On Yano** (F6): advance by slots, seconds or epochs; epoch shift and catch-up; named snapshots and restore; rollback.
  - **After any of them,** IntelliAda resets the host (`reset(now)`, H15) and re-checks each recorded instance's UTxO, marking vanished ones (I17).
  - **A keyed call refused with "prepare again at slot N"** offers **Advance to slot N** on Yano. N is checked to be ahead of the tip by at most one epoch, and is confirmed by the user.
  - **DevKit** gets the faucet only. Its time, snapshot and rollback controls stay hidden until an M3 task verifies DevKit's admin API, after which they are enabled without a new ADR (Q7, decided).
- **Persistence.** Recorded instances are stored per project and per network (keyed by the network's genesis hash) in **IntelliAda's own state under the IDE system directory**, not in `.idea/`, with no keys. They are validated as untrusted on load (caps, schema).

### D16. Results and errors

- **Success:** the transaction id, fee, execution units per redeemer, the predicted effects (H4), and, after confirmation, the new state and its diff and the payouts.
- **The contract refused off-chain** (the same method on the JVM, a `RequireException`): the message with its `.jsc` sites from the message map (F24). Each site is a link to the `.jsc` position, opened only when its real path is inside the project's contracts directory (I18). Java contracts have no map, so their message is shown as is.
- **The runtime refused** (`JoustException`: contention, a co-signer needed, a keyed cooldown with its slot): its fields, structured (H14).
- **The node refused** (phase 1 or 2, at evaluation or submission): the node's text, with any trace key of the form `#…` matched against the message map.
- **IntelliAda refused** (D13, D14, D14a): shown as "IntelliAda did not sign: <check>", visually distinct from contract errors.
- **Where:**
  - the run panel's result pane;
  - the raw exchange (with no key material, I10) in the Cardano Log console (`CardanoConsole`);
  - the host's stderr in its own capped console.

  All text is plain: HTML is disabled and ANSI sequences are stripped (I15).


## 3. Invariants

These invariant numbers continue ADR-004's. This ADR also relies on ADR-004's I15, I18, I20 and I21.

| # | Invariant | Decisions |
|---|---|---|
| I9 | IntelliAda's plugin contains no JOUST or JuLC runtime classes for the run feature; the dev host is the project's | D11, D12 |
| I10 | No mnemonic or private key reaches a child process's arguments, environment, stdin or files, nor IntelliAda's consoles, notifications or `idea.log`. The host receives addresses only | D13, D13a |
| I11 | IntelliAda signs only a body whose hash it computed from the received bytes and which equals `txHash`. It never re-encodes the body, signs only with payment keys, and the submitted id equals `txHash` | D13 |
| I12 | An account can be selected only in sessions of its own network class, in every IntelliAda flow. A mnemonic is held under one class only | D13, D13a |
| I13 | IntelliAda signs nothing unless the bound node's identity is positive for its class (D14), the class's opt-in is on (D14a), every output address and the body's network id match the class, and every input resolves on the bound node through IntelliAda's own client | D13, D14, D14a |
| I14 | Endpoints and credentials come from IntelliAda's application-level node configuration only. IntelliAda never reads `networks` or `start` from `joust.config.json`. The host reads the chain only through IntelliAda (H10) | D7, D11, D14 |
| I16 | IntelliAda never encodes or decodes a contract's Plutus data. Forms produce the description's JSON encoding, and the host converts | D15 |
| I17 | After a time control, snapshot restore or rollback, the next prepare runs on a reset host, and recorded instances are re-checked | D15 |
| I19 | The dev host listens on no socket: stdio only | D11 |
| I22 | The host channel enforces H13: frames of at most 4 MiB, matched ids, per-call deadlines, a capped stderr console. A violation stops the host | D11a |
| I23 | IntelliAda signs only a transaction that passes every D13 allow-list check for its network class. On mainnet an escalation is a refusal, and the review modal is mandatory | D13, D14a |
| I24 | Every prepare comes from a host whose build fingerprint and script hashes match the descriptions on disk | D11, D12 |

## 4. Milestones

### 4.0 Invariant to test traceability

| Invariant | Test (milestone) |
|---|---|
| I9 | M3: scan the built plugin zip for `org/julclang/**` |
| I10 | **M3:** a canary mnemonic, searched for in every child's arguments, environment, stdin capture and temp files, in the Cardano Log and JOUST host consoles, in notifications and in `idea.log`.<br>**M3:** devnet account creation logs no mnemonic |
| I11, I23 | **G3.2 (signer vectors)** and **M3 negative cases:** each runs with a **correctly recomputed hash**:<br>• change to a foreign address;<br>• excess caller outflow;<br>• an unknown withdrawal;<br>• a vote or proposal procedure;<br>• a treasury donation;<br>• a foreign mint;<br>• collateral return elsewhere;<br>• `is_valid = false`;<br>• a stake-key signer requested;<br>• a missing validity bound;<br>• a fee above the cap.<br>Plus a `txHash` mismatch, and mainnet escalations refused |
| I12 | M3: an imported account and an account of another class are absent from the run panel and from the existing transfer and mint choosers. A cross-class re-import is refused |
| I13 | M3: a fake node reporting magic 4, 1 on a devnet configuration, or a devnet magic without an admin API; a non-loopback host that is not allow-listed; HTTP to an allow-listed host; a default-node change between prepare and submit. Each is refused |
| I14 | M3: a fixture `joust.config.json` whose `networks.x.start` writes a sentinel and whose URL is non-loopback is never used. A host that asks for an endpoint gets none |
| I16 | M3: an architecture test showing that the run-panel package references no CCL `PlutusData` serialisation |
| I17 | M3: a snapshot restore, after which the instance is re-checked |
| I19 | M3: no listening socket belongs to the host |
| I22 | M3: a fake host that sends an oversized frame, an unknown id or `System.out` noise, or that hangs, is stopped |
| I24 | M3: edit a contract's behaviour (same name and ABI), rebuild, call: the new script hash is used, and a prepare from the old host is discarded |

### M3: the run panel on devnets and public testnets, Gradle projects (D11–D16, D13a, D14a testnets); M3b: npm; M3c: keyed calls

- **Entry gates:**
  - **G3.0** M1 and M2 exited. D11's launch-spec task runs through M2's Gradle integration, and D12's "build first" reuses M2's actions.
  - **G3.1** JOUST's dev-host ADR is accepted and released, with a protocol version. **Its protocol satisfies H1–H15, or this ADR drops the dependent feature,** and this ADR is amended to the accepted protocol and re-reviewed before M3 starts. Q5's `cosigners` is in that ADR (decided), or co-signed calls stay unavailable.
  - **G3.2 Signer vectors.** For every one of the 211 transactions in JOUST's `fixtures/m0` (F26):
    - the signer reproduces `txHash` from `unsignedTx`;
    - after signing, the body bytes are **byte-identical** and the transaction id equals `txHash`;
    - a mutated body is refused.

    Separately, the allow-list cases (I23) are their own vectors: the 57 unbounded bodies must be refused by check 7, not counted as hash failures.
  - **G3.3** The network-identity source for each node type (devnet, Blockfrost and Koios testnets) is chosen and verified (D14).
  - **G3.4 Yano compatibility.** Every chain **read** operation that JOUST's runtime makes is enumerated from `fixtures/m0` (excluding `submitTransaction`, F26) and answers on Yano as on Yaci Store, over equivalent devnet state. Otherwise Yano support waits and the gap is reported to Yano. Submission is covered by the end-to-end tests.
  - **G3.5** The class-tagged account store (D13a) is built and migrates legacy accounts on request.
  - **G3.6** JOUST time semantics on Yano: after time advance, epoch shift and catch-up, a `@ValidAfter` call prepared with H9's "now" is valid on the node.
- **Exit criteria and verification:**
  - **End to end on DevKit and on Yano,** from a `joust new` Gradle Counter in Java and in `.jsc`:
    1. start the devnet;
    2. create and fund a devnet account;
    3. deploy;
    4. increment;
    5. view the state;
    6. reset as the owner;
    7. close.

    Then JOUST's `bank` example (owned objects), a `@ValidAfter` call made valid by advancing Yano's time, and a snapshot restore after which the instance is re-checked (I17).
  - **On preprod,** behind the opt-in: the Counter flow with a testnet account, with the review modal shown, and once through **Export unsigned transaction** and **Import witness set**.
  - **§6.0's M3 tests.**
  - **Error display:** a `.jsc` `require` failure shows its message-map site and navigates to it.
  - **Measurements:** record, as measurements with machine and versions, the first-call latency (launch spec plus host start) and the warm call latency.
- **M3b (npm):** after JOUST's npm host command exists. The same protocol tests and end-to-end flow on a `joust new -t node` project.
- **M3c (keyed calls):** after H16 is in JOUST's host. JOUST's `registry` example end to end, including a refused keyed call with its cooldown slot and **Advance to slot N** on Yano, and an IDE restart between submit and settlement.


### M5: mainnet (D14a)

- **Entry gates:**
  - **Decide the default signing path for mainnet** (SEC-13). On Linux and Windows, `PasswordSafe` does not keep keys from same-user project code, and the run panel always runs project code. Lean: on those platforms mainnet defaults to **Export unsigned transaction** and external signing, with `PasswordSafe` signing as an explicit per-account opt-in. On macOS the Keychain's per-application access control applies, and it should be verified.
  - M3 has exited on preprod and preview.
  - D13a's `PasswordSafe` store is shipped and legacy migration is done (#10).
  - Every I23 negative case passes on testnets.
  - A security review of the signing path and of the mainnet review modal, on the implementation.
- **Exit criteria and verification:**
  - **Mainnet refusals:** with the opt-in off, and without the per-session confirmation, the panel refuses mainnet.
  - **Mandatory modal:** every mainnet transaction shows the modal, which cannot be turned off.
  - **Escalations refused:** every check that escalates on testnets refuses on mainnet.
  - **Legacy accounts:** they are not selectable.
  - **External signing:** export and import of a witness set work with `cardano-cli`.
  - **A minimal real deployment** (a Counter on mainnet with a small amount), done manually by the maintainer, is recorded in the PR.


## 5. Compatibility

- **IDE:** this ADR needs the Java plugin (D15's Java line markers) and `com.intellij.gradle` (D11's launch spec). The IDE floor follows ADR-004 §7.
- **JOUST:** a project can use the run panel only with a JOUST release that has the dev-host protocol version.
- **Data:**
  - Recorded instances live in IntelliAda's state under the IDE system directory, with a schema version and no key material.
  - Accounts move to `PasswordSafe` by an explicit migration (D13a). The legacy store stays readable until migrated.

## 6. Risks

| Risk | Mitigation |
|---|---|
| A project process obtains a signature it should not | D13's allow-list against IntelliAda's own node reads (I11, I13, I23); class-tagged accounts (I12); opt-ins and the mandatory mainnet review (D14a) |
| A user mnemonic with real funds is used on a devnet | class-tagged accounts (I12, D13a) |
| Real funds are lost on mainnet through an IntelliAda bug | M5's gates; the mandatory review; refusals instead of escalation; fee and outflow caps; the external-signing path |
| Trusted project code reads IntelliAda's key stores or node credentials | stated in ADR-004 §2; D13a moves keys to `PasswordSafe`; ADR-004 R8 for `authKey` |
| JOUST's dev-host API never materialises, or lacks H1–H15 | Part C waits, or drops the dependent features (G3.1) |
| Yano is not fully Blockfrost-compatible for JOUST's reads | G3.4 |
| The host's reverse chain reads exhaust a Blockfrost quota or probe unrelated addresses | H10's closed vocabulary, accepted only during a forward call, under per-call caps (I22) |

## 7. Decisions taken (from ADR-004 §11)

These were decided by the maintainer, except where marked:
- **L4:** testnets in M3 and mainnet in M5, each by opt-in. Mainnet signs with `PasswordSafe` accounts plus external signing.
- **L5:** a new JOUST dev-host ADR.
- **L6:** co-signers through that ADR.
- **L7:** session history only.
- **L8:** Yano gets the full controls, DevKit the faucet.
- **L13:** a class-tagged `PasswordSafe` account store.
- **L14 (delegated):** chain reads through IntelliAda.
- **L17 (delegated):** the signing defaults in D13.

Each delegated decision is tracked for the maintainer's ratification in ADR-004 §11.

## 8. Round-1 findings carried here

| Finding | Where it is answered |
|---|---|
| DES-1, CDX-16 | D11a (H1–H17), G3.1, G3.6 |
| DES-4 | D15 entry points |
| DES-5, CDX-6 | H1, I24, D11 lifecycle |
| DES-7 | G3.0 |
| DES-9 | D13 signing (ADR-004 F35), G3.2 |
| DES-10 | D14 (admin endpoint, explicit-node service) |
| SEC-3, CDX-3 | D14 positive identity, HTTPS, session binding |
| SEC-4, CDX-2 | D13 checks 1–9, I23 |
| SEC-5 | D13a, I12 |
| SEC-6 | I10, ADR-004 R7 |
| SEC-7 | H13, I22, D11 launch spec in memory, D15 persistence, Advance to slot N |
| CDX-5 | ADR-004 §2 ("Trusted project code runs as the user"), I10 |
| CDX-7 | H16, M3c |
| CDX-8 | H8, D13 check 7 |
| CDX-15 | G3.2, G3.4 |

## Revision history

- **d1** (2026-11-19): moved from ADR-004 r2's Part C.
  - **Advisor consult changes** (decisions L14 and L17):
    - H10 now states the property and leaves the mechanism to JOUST (reverse RPC, or ADR-0015's snapshot-and-`Need` loop), with a closed vocabulary and caps; `chain/evaluate` is optional.
    - D13: the validity horizon is measured in slots from the bound node's tip (15 min, or 2 h for exports); the fee cap is 3 ADA on every class, as a setting with a hard ceiling; check 8 allows a creation's locked lovelace (per JOUST `verify.js`); the full value of the collateral inputs counts when `total_collateral` is absent; an escalation always shows the modal.
- **d2** (2026-11-19): carries round 2 of PR #9.
  - D13a: `devnet` accounts are fresh only (CDX-20).
  - D13 check 10: bind the transaction to the requested call (the CDX-2 note).
  - M5 gate: decide the default signing path for mainnet (SEC-13).
