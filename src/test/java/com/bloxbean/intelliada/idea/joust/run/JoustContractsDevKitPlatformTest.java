package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.accounts.JoustAccounts;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.JoustHostService;
import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore;
import com.bloxbean.intelliada.idea.joust.sign.JoustSigner;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * ADR-005 M3.3b4 (L46): the rest of the M3.3b row's exit on DevKit (tool-gated), each contract through the session's
 * signing path (the panel's): the Counter in Java; JOUST's {@code bank} (owned calls); {@code token} (map calls); a
 * quorum call signed by two IntelliAda keys on the repackaged {@code HostCouncil}; and {@code dex} (calls with sends).
 * Each runs with fresh, funded devnet accounts. Fixtures: {@code JOUST_FIXTURES} holds {@code counter-java},
 * {@code bank}, {@code token}, {@code council} and {@code dex}, built with {@code joustHostSpec}.
 */
public class JoustContractsDevKitPlatformTest extends BasePlatformTestCase {

    private static <T> T off(Callable<T> c) throws Exception {
        return com.intellij.testFramework.PlatformTestUtil.waitForFuture(
                com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(c), 900_000);
    }

    /** One session on one fixture, with fresh funded accounts; closed by {@link #close}. */
    private final class Run implements AutoCloseable {
        final Path module;
        final JoustRunSession s;
        final String contract;
        final List<JoustAccounts.Created> accounts = new ArrayList<>();
        final RemoteNode node = new RemoteNode();
        final String store;

        Run(String fixture, int accountsNeeded) throws Exception {
            String dir = JoustTestTools.env("JOUST_FIXTURES", getName());
            store = JoustTestTools.env("JOUST_DEVKIT_STORE", getName());
            String admin = JoustTestTools.env("JOUST_DEVKIT_ADMIN", getName());
            String bundle = System.getProperty("joust.verifier.dir", ""), java = System.getProperty("joust.lint.java", "");
            if (dir == null || store == null || admin == null) throw new Skipped();
            if (bundle.isEmpty() || java.isEmpty() || !Files.isDirectory(Path.of(bundle, "lib"))) {
                if (JoustTestTools.required()) throw new AssertionError("this test needs the bundled checker and a JDK 25");
                throw new Skipped();
            }
            module = Path.of(dir, fixture);
            assertTrue("fixture " + module + " is built (joustHostSpec)", Files.isRegularFile(module.resolve("build/joust/host-spec.json")));
            JoustRunSession.checkerForTests = com.bloxbean.intelliada.idea.joust.verifier.JoustChecker.forTests(Path.of(bundle), Path.of(java));
            TrustedProjects.setProjectTrusted(getProject(), true);
            JoustHostService.confirmForTests = (project, d, spec, version) -> true;
            node.setId("devkit-contracts-" + fixture);
            node.setName("DevKit");
            node.setNodeType(NodeType.LocalYaciDevKit);
            node.setApiEndpoint(store);
            node.setProtocolMagic("42");
            com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState.getInstance().addRemoteNode(node);
            for (int i = 0; i < accountsNeeded; i++) {
                var c = JoustAccounts.getInstance().createFreshForBackup(fixture + "-" + i, NetworkIdentity.NetworkClass.DEVNET);
                accounts.add(c);
                topUp(admin, store, c.entry().address);
            }
            JoustHostService service = JoustHostService.getInstance(getProject());
            JoustRunGuard guard = JoustRunGuard.capture(getProject());
            JournalStore journal = new JournalStore(Files.createTempDirectory("joust-contracts-journal"));
            JoustRunSession.Launcher launcher = (init, key, reads) -> service.launch(guard, module, init, key, reads, e -> { });
            s = off(() -> JoustRunSession.start(service, module, JoustRunSession.bind(node), journal, launcher, r -> { }));
            contract = s.running().initialize().path("contracts").get(0).path("name").textValue();
        }

        String id(int i) {
            return accounts.get(i).entry().id;
        }

        String address(int i) {
            return accounts.get(i).entry().address;
        }

        String key(int i) throws Exception {
            return JoustSigner.paymentKey(address(i));
        }

        JoustRunSession.Submitted deploy(int caller, String method, ObjectNode args, ObjectNode extra) throws Exception {
            ObjectNode init = HostJson.object().put("method", method);
            init.set("args", args);
            init.setAll(extra);
            List<JoustRunSession.Submitted> done = off(() -> s.deploy(contract, id(caller), init, JoustRunPanel.ttl(s), Duration.ofMinutes(4)));
            JoustRunSession.Submitted last = done.get(done.size() - 1);
            assertTrue("init lands", off(() -> s.awaitInclusion(last.txHash(), Duration.ofMinutes(3))));
            return last;
        }

        /** One call through the session's signing path, waited for until it lands. */
        JoustRunSession.Submitted call(int caller, List<Integer> coSigners, ObjectNode call) throws Exception {
            call.put("contract", contract).put("address", address(caller));
            List<String> co = new ArrayList<>();
            for (int c : coSigners) co.add(id(c));
            JoustRunSession.Submitted done = off(() -> s.submit("prepare", HostJson.object().put("ttlSlots", JoustRunPanel.ttl(s))
                    .set("call", call), id(caller), co));
            assertNotNull(done);
            assertTrue(call.path("method").asText() + " lands", off(() -> s.awaitInclusion(done.txHash(), Duration.ofMinutes(3))));
            return done;
        }

        @Override
        public void close() {
            JoustRunSession.checkerForTests = null;
            com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState.getInstance().removeRemoteNode(node);
            if (module != null) JoustHostService.getInstance(getProject()).stop(module);
            for (var c : accounts) JoustAccounts.getInstance().remove(c.entry().id);
        }
    }

    private static final class Skipped extends RuntimeException {
        Skipped() {
            super(null, null, false, false);
        }
    }

    private static ObjectNode call(String kind, String method, ObjectNode args) {
        ObjectNode c = HostJson.object().put("kind", kind).put("method", method);
        c.set("args", args);
        return c;
    }

    private static ObjectNode args() {
        return HostJson.object();
    }

    /** Runs {@code body}; a missing tool is a skip (or a failure when tools are required). */
    private void run(String fixture, int accounts, RunBody body) throws Exception {
        Run r;
        try {
            r = new Run(fixture, accounts);
        } catch (Skipped skipped) {
            System.out.println("JOUST-SKIPPED " + getName() + ": tools or fixtures not set");
            return;
        }
        try (r) {
            body.run(r);
        }
    }

    @FunctionalInterface
    private interface RunBody {
        void run(Run r) throws Exception;
    }

    public void testTheJavaCounter() throws Exception {
        run("counter-java", 1, r -> {
            var init = r.deploy(0, "init", args(), HostJson.object());
            r.call(0, List.of(), call("action", "increment", args().put("by", "3")).put("instance", init.instance()));
            JsonNode state = off(() -> r.s.view(r.contract, init.instance(), null, null));
            assertTrue("the state shows the increment: " + state, state.toString().contains("3"));
            r.call(0, List.of(), call("destroy", "close", args()).put("instance", init.instance()));
        });
    }

    public void testBankOwnedAccounts() throws Exception {
        run("bank", 2, r -> {
            var bank = r.deploy(0, "init", args().put("withdrawLimit", "30000000"), HostJson.object());
            ObjectNode open = call("ownedInit", "open", args()).put("instance", bank.instance());
            open.putObject("pay").put("lovelace", "20000000").putArray("assets");
            var opened = r.call(1, List.of(), open);
            assertNotNull("an owned object is created", opened.object());
            ObjectNode deposit = call("ownedAction", "deposit", args()).put("instance", bank.instance()).put("object", opened.object());
            deposit.putObject("pay").put("lovelace", "3000000").putArray("assets");
            r.call(1, List.of(), deposit);
            r.call(1, List.of(), call("ownedAction", "withdraw", args().put("amount", "1000000")).put("instance", bank.instance())
                    .put("object", opened.object()));
            r.call(1, List.of(), call("ownedDestroy", "close", args()).put("instance", bank.instance()).put("object", opened.object()));
        });
    }

    public void testTokenMapCalls() throws Exception {
        run("token", 2, r -> {
            var token = r.deploy(0, "create", args(), HostJson.object());
            r.call(0, List.of(), call("action", "mint", args().put("to", r.key(1)).put("amount", "100")).put("instance", token.instance()));
            r.call(1, List.of(), call("action", "transfer", args().put("to", r.key(0)).put("amount", "10")).put("instance", token.instance()));
            JsonNode b = off(() -> r.s.view(r.contract, token.instance(), "balanceOf", HostJson.object().put("holder", r.key(1))));
            assertTrue("B holds 90: " + b, b.toString().contains("90"));
        });
    }

    public void testAQuorumCallSignedByTwoIntelliAdaKeys() throws Exception {
        run("council", 2, r -> {
            ObjectNode members = args();
            // The quorum's keys in ascending order, as JOUST's quorum check expects (the kit's HostCouncil-init vector).
            List<String> keys = new ArrayList<>(List.of(r.key(0), r.key(1)));
            java.util.Collections.sort(keys);
            var list = members.putArray("members");
            keys.forEach(list::add);
            members.put("threshold", "2");
            ObjectNode pay = HostJson.object();
            pay.putObject("pay").put("lovelace", "5000000").putArray("assets");
            var council = r.deploy(0, "open", members, pay);
            ObjectNode vote = call("action", "vote", args().put("n", "42").put("to", r.key(0))).put("instance", council.instance());
            vote.putArray("cosigners").add(r.key(1));
            var voted = r.call(0, List.of(1), vote);
            assertNotNull(voted);
            // Without the second key the body still needs it: IntelliAda refuses before any review or signing.
            ObjectNode alone = call("action", "vote", args().put("n", "43").put("to", r.key(0))).put("instance", council.instance());
            alone.putArray("cosigners").add(r.key(1));
            alone.put("contract", r.contract).put("address", r.address(0));
            try {
                off(() -> r.s.submit("prepare", HostJson.object().put("ttlSlots", JoustRunPanel.ttl(r.s)).set("call", alone), r.id(0), List.of()));
                fail("signed without the co-signer's key");
            } catch (Throwable expected) {
                String m = String.valueOf(expected.getMessage());
                assertTrue(m, m.contains("does not hold") && m.contains("inputs are released"));
            }
        });
    }

    public void testDexCallsWithSends() throws Exception {
        run("dex", 2, r -> {
            // The pool opens first (it needs no token); the token is minted after Deploy's publications, which may
            // otherwise spend the token's UTxO into a reference-script output the caller cannot spend again.
            Account a = new Account(Networks.testnet(), r.accounts.get(0).phrase());
            ScriptPubkey policy = ScriptPubkey.create(com.bloxbean.cardano.client.crypto.VerificationKey.create(a.publicKeyBytes()));
            String policyId = policy.getPolicyId();
            String name = "4d31";
            var pool = r.deploy(0, "open", args().put("tokenPolicy", policyId).put("tokenName", name), HostJson.object());
            var backend = new BFBackendService(r.store.endsWith("/") ? r.store : r.store + "/", "devkit");
            Tx mint = new Tx().mintAssets(policy, new Asset("M1", BigInteger.valueOf(1_000_000)), a.baseAddress()).from(a.baseAddress());
            var result = new QuickTxBuilder(backend).compose(mint).withSigner(SignerProviders.signerFrom(a)).completeAndWait();
            assertTrue("the token is minted: " + result, result.isSuccessful());
            ObjectNode add = call("action", "addLiquidity", args().put("tokenIn", "100000")).put("instance", pool.instance());
            ObjectNode pay = add.putObject("pay").put("lovelace", "100000000");
            pay.putArray("assets").addArray().add(policyId).addArray().addArray().add(name).add("100000");
            r.call(0, List.of(), add);
            ObjectNode swap = call("action", "swapAdaForToken", args().put("minOut", "1")).put("instance", pool.instance());
            swap.putObject("pay").put("lovelace", "10000000").putArray("assets");
            r.call(1, List.of(), swap);
            // B received tokens from the pool: a send, to the enterprise address of B's key (ADR-0007 D6).
            String bWallet = new Account(Networks.testnet(), r.accounts.get(1).phrase()).enterpriseAddress();
            var u = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(r.store + "/addresses/" + bWallet + "/utxos")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue("B's wallet holds the pool's token: " + u.body(), u.body().contains(policyId + name));
        });
    }

    /** DevKit's faucet: 1000 ADA to {@code address}, then waits until the store shows it. */
    private static void topUp(String admin, String store, String address) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        // Two UTxOs: an init needs a seed input and, separately, collateral the composition does not spend.
        String body = "{\"address\":\"" + address + "\",\"adaAmount\":1000}";
        for (int t = 0; t < 2; t++) {
            var r = http.send(HttpRequest.newBuilder(URI.create(admin + "/local-cluster/api/addresses/topup"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue("top-up: " + r.statusCode() + " " + r.body(), r.statusCode() / 100 == 2);
        }
        for (int i = 0; i < 60; i++) {
            var u = http.send(HttpRequest.newBuilder(URI.create(store + "/addresses/" + address + "/utxos")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (u.statusCode() == 200 && u.body().split("\"tx_hash\"", -1).length - 1 >= 2) return;
            Thread.sleep(1_000);
        }
        fail("the top-up did not appear");
    }
}
