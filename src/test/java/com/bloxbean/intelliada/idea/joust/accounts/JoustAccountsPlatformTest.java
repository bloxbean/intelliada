package com.bloxbean.intelliada.idea.joust.accounts;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.account.service.AccountService;
import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity.NetworkClass;
import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** ADR-005 D13a, I10, I12 (M3.2): the class-tagged account store. The legacy store is redirected to a temporary home. */
public class JoustAccountsPlatformTest extends BasePlatformTestCase {

    private JoustAccounts store;
    private String home;

    /** A durable in-memory safe (the test platform's own safe is memory-only, which the store refuses). */
    static final class MapSafe implements JoustAccounts.Safe {
        final Map<String, Credentials> m = new HashMap<>();
        boolean memoryOnly;
        boolean drops;

        @Override
        public boolean memoryOnly() {
            return memoryOnly;
        }

        @Override
        public void set(CredentialAttributes a, @Nullable Credentials c) {
            if (c == null) m.remove(a.getServiceName());
            else if (!drops) m.put(a.getServiceName(), c);
        }

        @Override
        public @Nullable Credentials get(CredentialAttributes a) {
            return m.get(a.getServiceName());
        }
    }

    private MapSafe safe;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createTempDirectory("intelliada-home").toString());   // never the real legacy store
        store = new JoustAccounts();
        safe = new MapSafe();
        store.safeForTests(safe);
        JoustAccounts.mainnetForTests(true);   // the store's mainnet support is tested; the gate has its own test
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            JoustAccounts.mainnetForTests(false);
            System.setProperty("user.home", home);
        } finally {
            super.tearDown();
        }
    }

    private static String phrase() {
        return new Account(Networks.testnet()).mnemonic();
    }

    public void testAccountsAreOfferedOnlyInTheirOwnClass() throws Exception {
        var t = store.createFresh("t", NetworkClass.TESTNET);
        var d = store.createFresh("d", NetworkClass.DEVNET);
        var m = store.createFresh("m", NetworkClass.MAINNET);
        assertTrue(store.list(NetworkClass.TESTNET).stream().anyMatch(e -> e.id.equals(t.id)));
        for (NetworkClass c : new NetworkClass[]{NetworkClass.DEVNET, NetworkClass.MAINNET}) {
            assertFalse("I12: a testnet account is absent from " + c, store.list(c).stream().anyMatch(e -> e.id.equals(t.id)));
        }
        assertFalse(store.list(NetworkClass.TESTNET).stream().anyMatch(e -> e.id.equals(m.id) || e.id.equals(d.id)));
        assertTrue(store.list(NetworkClass.DEVNET).stream().anyMatch(e -> e.id.equals(d.id)));
        assertEquals(0, store.list(NetworkClass.TESTNET).stream().filter(e -> e.origin.equals("GENESIS")).count());
        assertEquals(JoustAccounts.GENESIS_ACCOUNTS, store.list(NetworkClass.DEVNET).stream().filter(e -> e.origin.equals("GENESIS")).count());
    }

    /** M32-CDX-4, -DES-1: the genesis accounts are DevKit's funded account-index keys (pinned, independent of the code). */
    public void testGenesisAccountsAreTheFundedKeys() throws Exception {
        assertEquals(20, JoustAccounts.GENESIS_ACCOUNTS);
        assertEquals("addr_test1qryvgass5dsrf2kxl3vgfz76uhp83kv5lagzcp29tcana68ca5aqa6swlq6llfamln09tal7n5kvt4275ckwedpt4v7q48uhex",
                JoustAccounts.genesis(0).address);
        assertTrue("#1 is account 1's key, not address 1's", JoustAccounts.genesis(1).address.startsWith("addr_test1qpqy3lufef8c3en9nrnzp2sv"));
        Set<String> stake = new HashSet<>();
        for (int i = 0; i < 20; i++) stake.add(JoustAccounts.genesis(i).address.substring(60));
        assertEquals("every genesis account has its own stake key", 20, stake.size());
        // Tool-gated: every one is funded in the running DevKit's Shelley genesis.
        String admin = JoustTestTools.env("JOUST_DEVKIT_ADMIN", "testGenesisAccountsAreTheFundedKeys");
        if (admin == null) return;
        HttpResponse<String> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create(admin + "/local-cluster/api/admin/devnet/genesis/shelley")).GET().build(), HttpResponse.BodyHandlers.ofString());
        JsonNode funds = HostJson.MAPPER.readTree(r.body()).path("initialFunds");
        assertTrue(r.body().length() + "", funds.isObject() && funds.size() > 0);
        String fundsText = funds.toString();
        for (int i = 0; i < 20; i++) {
            String hex = com.bloxbean.cardano.client.util.HexUtil.encodeHexString(
                    new com.bloxbean.cardano.client.address.Address(JoustAccounts.genesis(i).address).getBytes());
            assertTrue("genesis #" + i + " is funded", fundsText.contains(hex) || fundsText.contains(JoustAccounts.genesis(i).address));
        }
    }

    public void testDevnetAccountsAreNeverImported() {
        try {
            store.importMnemonic("x", NetworkClass.DEVNET, phrase());
            fail("a devnet import was accepted");
        } catch (JoustAccounts.Refused expected) {
            assertTrue(expected.getMessage().contains("fresh"));
        }
    }

    public void testAMnemonicIsHeldUnderOneClassOnly() throws Exception {
        String m = phrase();
        store.importMnemonic("a", NetworkClass.TESTNET, m);
        assertRefused(() -> store.importMnemonic("b", NetworkClass.MAINNET, m), "one network class");
        assertRefused(() -> store.importMnemonic("c", NetworkClass.TESTNET, m), "already");
        assertRefused(() -> store.importMnemonic("g", NetworkClass.TESTNET, JoustAccounts.DEVNET_GENESIS_MNEMONIC), "genesis");
    }

    /** M32-CDX-5: only the BIP-39 lengths are phrases; M32-SEC-7: a name that looks like a phrase is refused. */
    public void testOnlyFullLengthPhrasesAndPlainNames() {
        String[] w = phrase().split(" ");
        assertRefused(() -> store.importMnemonic("x", NetworkClass.TESTNET, String.join(" ", List.of(w).subList(0, 3))), "12, 15, 18, 21 or 24");
        assertRefused(() -> store.importMnemonic(phrase(), NetworkClass.TESTNET, phrase()), "looks like a recovery phrase");
    }

    /** M32-CDX-1, -SEC-4, -DES-6: a phrase is stored only where it survives a restart. */
    public void testAMemoryOnlyOrForgetfulSafeIsRefused() {
        safe.memoryOnly = true;
        assertRefused(() -> store.createFresh("x", NetworkClass.TESTNET), "forget passwords");
        safe.memoryOnly = false;
        safe.drops = true;
        assertRefused(() -> store.createFresh("x", NetworkClass.TESTNET), "did not keep");
        assertTrue("nothing was published", store.list(NetworkClass.TESTNET).isEmpty());
    }

    public void testThePersistedStateHoldsNoSecretAndRevealChecksTheKey() throws Exception {
        var e = store.createFresh("secret holder", NetworkClass.TESTNET);
        String mnemonic = store.revealMnemonic(e.id);
        assertEquals(24, mnemonic.split(" ").length);
        String xml = com.intellij.openapi.util.JDOMUtil.write(XmlSerializer.serialize(store.getState()));
        for (String word : mnemonic.split(" ")) {
            if (word.length() > 5) assertFalse("I10: no mnemonic word in the persisted state: " + word, xml.contains(" " + word + " "));
        }
        assertFalse(xml.contains(mnemonic));
        // The safe holding another key for the entry is never shown as its phrase.
        safe.set(JoustAccounts.attributes(e.id), new Credentials(e.id, phrase()));
        assertRefused(() -> store.revealMnemonic(e.id), "another key");
        assertTrue(store.remove(e.id));
        assertRefused(() -> store.revealMnemonic(e.id), "no such account");
        assertRefused(() -> store.revealMnemonic("genesis-99"), "no such account");
    }

    /** M32-CDX-7, -SEC-3, -DES-5: loaded state is held to the store's invariants. */
    public void testLoadedStateIsValidatedAsUntrusted() throws Exception {
        var good = store.createFresh("good", NetworkClass.TESTNET);
        var main = store.createFresh("main", NetworkClass.MAINNET);
        var state = store.getState();
        List<JoustAccounts.Entry> tampered = new java.util.ArrayList<>(state.accounts);
        // (a) the mainnet account re-tagged as a devnet account under the same id (a conflicting duplicate)
        tampered.add(with(main, e -> { e.networkClass = "DEVNET"; e.origin = "FRESH"; }));
        // (b) a genesis id and origin
        tampered.add(with(JoustAccounts.genesis(3), e -> { e.networkClass = "MAINNET"; }));
        // (c) a devnet account that was imported
        var other = new Account(Networks.testnet());
        tampered.add(entry(other, "DEVNET", "IMPORTED"));
        // (d) a migrated mainnet account
        tampered.add(entry(new Account(Networks.mainnet()), "MAINNET", "MIGRATED"));
        // (e) an address whose payment key is not the fingerprint
        tampered.add(with(entry(new Account(Networks.testnet()), "TESTNET", "FRESH"), e -> e.fingerprint = "ab".repeat(28)));
        // (f) a testnet entry with a mainnet address, a non-UUID id, a long name
        tampered.add(with(entry(new Account(Networks.mainnet()), "TESTNET", "FRESH"), e -> { }));
        tampered.add(with(entry(new Account(Networks.testnet()), "TESTNET", "FRESH"), e -> e.id = "<b>x</b>"));
        tampered.add(with(entry(new Account(Networks.testnet()), "TESTNET", "FRESH"), e -> e.name = "n".repeat(500)));
        var bad = new JoustAccounts.StateBean();
        bad.accounts = tampered;
        store.loadState(bad);
        var kept = store.getState().accounts;
        assertEquals("only the untampered testnet account survives: " + kept.size(), 1, kept.size());
        assertEquals(good.id, kept.get(0).id);
    }

    /** M32-CDX-2: a key moves between stores only by migration, in both directions. */
    public void testAKeyIsNeverInBothStores() throws Exception {
        Account legacyKey = new Account(Networks.testnet());
        CardanoAccount legacy = new CardanoAccount(legacyKey.baseAddress(), legacyKey.mnemonic());
        legacy.setName("legacy");
        assertTrue(AccountService.getAccountService().importAccount(legacy));
        assertRefused(() -> store.importMnemonic("x", NetworkClass.MAINNET, legacyKey.mnemonic()), "legacy IntelliAda account");
        // The other way: the legacy import of a key the JOUST store holds is refused.
        JoustAccounts real = JoustAccounts.getInstance();
        real.safeForTests(safe);
        var held = real.createFresh("held", NetworkClass.TESTNET);
        try {
            String m = real.revealMnemonic(held.id);
            Account a = new Account(Networks.testnet(), m);
            assertFalse("I12: the legacy store refuses a JOUST key", AccountService.getAccountService()
                    .importAccount(new CardanoAccount(a.baseAddress(), m)));
            assertFalse("JOUST accounts never appear in the legacy choosers",
                    AccountService.getAccountService().getAccounts().stream().anyMatch(c -> c.getAddress().equals(held.address)));
        } finally {
            real.remove(held.id);
            real.safeForTests(JoustAccounts.PASSWORD_SAFE);
        }
    }

    /** M32-CDX-3, -SEC-1, -SEC-2, -DES-2, -DES-8: migration moves a legacy testnet key, and only that. */
    public void testMigrationMovesALegacyTestnetAccount() throws Exception {
        Account k = new Account(Networks.testnet());
        CardanoAccount legacy = new CardanoAccount(k.baseAddress(), k.mnemonic());
        legacy.setName("legacy");
        AccountService.getAccountService().importAccount(legacy);
        var e = store.migrateLegacy(legacy);
        assertEquals("TESTNET", e.networkClass);
        assertEquals("MIGRATED", e.origin);
        assertEquals(k.baseAddress(), e.address);
        assertTrue("it left the legacy store", AccountService.getAccountService().getAccounts().stream()
                .noneMatch(c -> c.getAddress().equals(k.baseAddress())));
        assertEquals(k.mnemonic(), store.revealMnemonic(e.id));
        // A mainnet legacy account is not migrated as testnet.
        Account mk = new Account(Networks.mainnet());
        assertRefused(() -> store.migrateLegacy(new CardanoAccount(mk.baseAddress(), mk.mnemonic())), "mainnet");
        // An address that is not the phrase's first address is refused.
        Account other = new Account(Networks.testnet());
        assertRefused(() -> store.migrateLegacy(new CardanoAccount(other.baseAddress(), k.mnemonic())), "not the first address");
    }

    /** I10 (§4.0 M3.2): creating, importing, migrating and revealing log no recovery phrase anywhere. */
    public void testNoPhraseReachesALogOrAStream() throws Exception {
        java.util.List<String> logged = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.logging.Handler h = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord r) {
                logged.add(String.valueOf(r.getMessage()) + " " + (r.getThrown() == null ? "" : r.getThrown()));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        java.util.logging.Level level = root.getLevel();
        root.setLevel(java.util.logging.Level.ALL);
        root.addHandler(h);
        java.io.PrintStream out = System.out, err = System.err;
        java.io.ByteArrayOutputStream streams = new java.io.ByteArrayOutputStream();
        java.io.PrintStream capture = new java.io.PrintStream(streams, true);
        System.setOut(capture);
        System.setErr(capture);
        List<String> phrases = new java.util.ArrayList<>();
        try {
            var f = store.createFresh("fresh", NetworkClass.TESTNET);
            phrases.add(store.revealMnemonic(f.id));
            String imported = phrase();
            phrases.add(imported);
            store.importMnemonic("imported", NetworkClass.MAINNET, imported);
            Account k = new Account(Networks.testnet());
            phrases.add(k.mnemonic());
            CardanoAccount legacy = new CardanoAccount(k.baseAddress(), k.mnemonic());
            AccountService.getAccountService().importAccount(legacy);
            store.migrateLegacy(legacy);
            try {
                store.importMnemonic("bad", NetworkClass.TESTNET, imported.replace(imported.split(" ")[0], "zzzz"));
            } catch (JoustAccounts.Refused expected) {
                assertFalse("a refusal never echoes the phrase", expected.getMessage().contains("zzzz"));
            }
            // The legacy creation backend (CreateAccountAction prints only the address since ADR-004 R7).
            CardanoAccount created = AccountService.getAccountService().createNewAccount("legacy-new",
                    com.bloxbean.cardano.client.common.model.Networks.testnet());
            phrases.add(created.getMnemonic());
        } finally {
            System.setOut(out);
            System.setErr(err);
            root.removeHandler(h);
            root.setLevel(level);
        }
        String all = String.join("\n", logged) + "\n" + streams;
        for (String p : phrases) {
            assertNotNull(p);
            String[] words = p.split(" ");
            assertFalse("I10: a phrase reached a log or stream", all.contains(p));
            assertFalse("I10: no run of phrase words", all.contains(words[0] + " " + words[1] + " " + words[2]));
        }
    }

    /** L38: until M5 the store itself refuses mainnet accounts. */
    public void testMainnetIsRefusedUntilItsOptIn() {
        JoustAccounts.mainnetForTests(false);
        assertRefused(() -> store.createFresh("m", NetworkClass.MAINNET), "mainnet");
        assertRefused(() -> store.importMnemonic("m", NetworkClass.MAINNET, phrase()), "mainnet");
    }

    /** A legacy account never appears in the JOUST lists (the other direction of I12). */
    public void testLegacyAccountsAreNeverInTheJoustLists() throws Exception {
        Account k = new Account(Networks.testnet());
        AccountService.getAccountService().importAccount(new CardanoAccount(k.baseAddress(), k.mnemonic()));
        for (NetworkClass c : NetworkClass.values()) {
            assertTrue(store.list(c).stream().noneMatch(e -> e.address.equals(k.baseAddress())));
        }
    }

    /** An interrupted move (stored here, still in the legacy store) is completed; never with a memory-only safe. */
    public void testAnInterruptedMoveIsCompleted() throws Exception {
        Account k = new Account(Networks.testnet());
        CardanoAccount legacy = new CardanoAccount(k.baseAddress(), k.mnemonic());
        AccountService.getAccountService().importAccount(legacy);
        store.migrateLegacy(legacy);
        AccountService.getAccountService().importAccount(legacy);   // as if the delete had not happened
        safe.memoryOnly = true;
        assertEquals(0, store.completePendingMoves());
        safe.memoryOnly = false;
        assertEquals(1, store.completePendingMoves());
        assertTrue(AccountService.getAccountService().getAccounts().stream().noneMatch(c -> c.getAddress().equals(k.baseAddress())));
    }

    public void testSessionsSeeOnlyTheirClass() throws Exception {
        var t = store.createFresh("t", NetworkClass.TESTNET);
        var devnet = new com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity.Identity(NetworkClass.DEVNET, 0, 42, 0, 0, 1000, 300, "devnet-42-x");
        assertTrue(store.forSession(devnet).stream().noneMatch(e -> e.id.equals(t.id)));
        assertTrue(store.forSession(devnet).stream().allMatch(e -> e.cls() == NetworkClass.DEVNET));
    }

    private interface Call {
        Object run() throws Exception;
    }

    private static void assertRefused(Call c, String why) {
        try {
            c.run();
            fail("accepted; expected a refusal containing: " + why);
        } catch (JoustAccounts.Refused e) {
            assertTrue(e.getMessage(), e.getMessage().contains(why));
        } catch (Exception e) {
            fail("expected a refusal, got " + e);
        }
    }

    private static JoustAccounts.Entry entry(Account a, String cls, String origin) {
        JoustAccounts.Entry e = new JoustAccounts.Entry();
        e.id = java.util.UUID.randomUUID().toString();
        e.name = "e";
        e.networkClass = cls;
        e.address = a.baseAddress();
        e.fingerprint = JoustAccounts.fingerprint(a);
        e.origin = origin;
        return e;
    }

    private static JoustAccounts.Entry with(JoustAccounts.Entry base, java.util.function.Consumer<JoustAccounts.Entry> f) {
        JoustAccounts.Entry e = base.copy();
        f.accept(e);
        return e;
    }
}
