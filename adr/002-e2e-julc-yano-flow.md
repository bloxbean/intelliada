# ADR-002: End-to-End julc + Yano Developer Flow

**Status:** Proposed  
**Date:** 2026-04-10

## The Developer Journey

A developer writing Cardano smart contracts with julc in IntelliJ should have a seamless flow from writing code to testing on a live devnet. Here's the ideal experience:

### Step 1: Create Project
- **File > New > Project > julc** (Gradle template)
- Generates: `build.gradle` with julc deps, `AlwaysSucceeds.java` starter, test file
- IntelliJ recognizes as Gradle project, resolves all julc imports
- **Issue today:** Gradle auto-import sometimes doesn't trigger. Fix: call `ExternalSystemUtil.refreshProject()` after scaffolding.

### Step 2: Write Validator
- Full Java IDE support: completion, refactoring, type checking
- Live templates: `julcspend`, `julcmint`, `julcmulti` for quick scaffolding
- julc action group in context menu (Build, Run Tests, View Blueprint, Configuration)

### Step 3: Build
- Right-click > **julc > Build** (or `./gradlew build` in terminal)
- Annotation processor compiles validators to UPLC
- Output: `build/classes/java/main/META-INF/plutus/*.plutus.json`
- Console shows build result + script sizes

### Step 4: Test Locally (No Node Needed)
- Right-click > **julc > Run Tests**
- JUnit tests using `ContractTest` base class evaluate validators on local VM
- Budget tracking shows CPU/memory per evaluation
- Fast feedback loop — no devnet required

### Step 5: Start Devnet
- **Yano Devnet panel > Start Yano** (one click after initial download)
- Status shows: Running, slot, block number (live)
- ~500ms startup for native, ~3-5s for JVM

### Step 6: Create & Fund Test Account
- **Cardano panel > Account > Create Account** (testnet)
- **Yano Devnet > Fund tab > Choose Account > Fund 10000 ADA**
- Inline status: "Funded 10000 ADA | tx: abc123..."

### Step 7: Deploy Validator
- Right-click > **julc > Deploy Validator**
- Select compiled validator from blueprint dropdown
- Enter @Param values (if parameterized)
- Set datum, ADA to lock
- "Evaluate Cost" shows CPU/memory before submitting
- Submit -> logs script address, tx hash
- **TODAY:** Deploy dialog exists but needs Yano integration for auto-selecting the node

### Step 8: Interact with Deployed Validator
- **Cardano panel > UTXO Explorer** > query the script address
- See locked UTXOs with datum
- Submit spending transaction with redeemer
- **TODAY:** Execute Script dialog is a future item

### Step 9: Iterate with Snapshots
- Before testing: **Yano > Snapshots > Create "before-test"**
- Run test scenario
- If failed: **Restore "before-test"** — instant rollback to clean state
- Modify validator, rebuild, re-deploy, re-test

### Step 10: Test Epoch Boundaries
- **Yano > Time Machine > Advance 1 Epoch**
- Verify vesting unlock, governance proposals, delegation rewards
- **Shift Genesis Back N Epochs** for past-time-travel testing

## What's Missing for End-to-End

### Critical (blocks the flow)

1. **Gradle project auto-import** — After `julc new` scaffolds the project, IntelliJ must auto-detect and import the Gradle project. Currently sometimes fails.
   - Fix: After copying files, explicitly call Gradle project link/refresh

2. **Yano as default node for transactions** — When developer uses existing transaction features (Payment, Token Minting, UTXO Explorer), they should automatically route through the running Yano instance. 
   - Fix: Auto-create a RemoteNode entry for the running Yano in RemoteNodeState, set as default

3. **Deploy Validator → Yano integration** — The Deploy Validator dialog should auto-detect the running Yano node and submit through it.
   - Fix: Wire DeployValidatorAction to use Yano's `/api/v1/tx/submit`

### Important (improves experience)

4. **Script address in UTXO Explorer** — After deploying, user should be able to one-click query UTXOs at the script address
   - Add "Query Script Address" in the UTXO dialog

5. **Inline budget display** — After build, show script sizes and estimated costs in the blueprint viewer

6. **Auto-fund on deploy** — If sender account doesn't have enough funds, offer to auto-fund via Yano faucet

### Nice-to-have (polish)

7. **One-click "Build & Deploy"** — Single action: build → deploy to Yano
8. **Watch mode** — Auto-rebuild on file save
9. **Test result panel** — Show test pass/fail with budget in a dedicated view (not just console)
