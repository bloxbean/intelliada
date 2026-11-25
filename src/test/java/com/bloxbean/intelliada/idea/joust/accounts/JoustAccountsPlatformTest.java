package com.bloxbean.intelliada.idea.joust.accounts;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity.NetworkClass;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.xmlb.XmlSerializer;

/** ADR-005 D13a, I10, I12 (M3.2): the class-tagged account store. */
public class JoustAccountsPlatformTest extends BasePlatformTestCase {

    private JoustAccounts store;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        store = new JoustAccounts();
    }

    public void testAccountsAreOfferedOnlyInTheirOwnClass() throws Exception {
        var t = store.createFresh("t", NetworkClass.TESTNET);
        var d = store.createFresh("d", NetworkClass.DEVNET);
        assertTrue(store.list(NetworkClass.TESTNET).stream().anyMatch(e -> e.id.equals(t.id)));
        assertFalse(store.list(NetworkClass.DEVNET).stream().anyMatch(e -> e.id.equals(t.id)));
        assertFalse(store.list(NetworkClass.MAINNET).stream().anyMatch(e -> e.id.equals(t.id) || e.id.equals(d.id)));
        assertTrue(store.list(NetworkClass.DEVNET).stream().anyMatch(e -> e.id.equals(d.id)));
        assertEquals("genesis accounts are devnet-only constants", 0,
                store.list(NetworkClass.TESTNET).stream().filter(e -> e.origin.equals("GENESIS")).count());
        assertEquals(JoustAccounts.GENESIS_ACCOUNTS,
                store.list(NetworkClass.DEVNET).stream().filter(e -> e.origin.equals("GENESIS")).count());
    }

    public void testDevnetAccountsAreNeverImported() {
        String m = new Account(Networks.testnet()).mnemonic();
        try {
            store.importMnemonic("x", NetworkClass.DEVNET, m);
            fail("a devnet import was accepted");
        } catch (JoustAccounts.Refused expected) {
            assertTrue(expected.getMessage().contains("fresh"));
        }
    }

    public void testAMnemonicIsHeldUnderOneClassOnly() throws Exception {
        String m = new Account(Networks.testnet()).mnemonic();
        store.importMnemonic("a", NetworkClass.TESTNET, m);
        try {
            store.importMnemonic("b", NetworkClass.MAINNET, m);
            fail("a cross-class re-import was accepted");
        } catch (JoustAccounts.Refused expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("one network class"));
        }
        try {
            store.importMnemonic("c", NetworkClass.TESTNET, m);
            fail("a duplicate was accepted");
        } catch (JoustAccounts.Refused expected) {
            assertTrue(expected.getMessage().contains("already"));
        }
        try {
            store.importMnemonic("g", NetworkClass.TESTNET, JoustAccounts.DEVNET_GENESIS_MNEMONIC);
            fail("the public genesis key was imported as a testnet account");
        } catch (JoustAccounts.Refused expected) {
            assertTrue(expected.getMessage().contains("genesis"));
        }
    }

    public void testThePersistedStateHoldsNoSecretAndTheSafeDoes() throws Exception {
        var e = store.createFresh("secret holder", NetworkClass.TESTNET);
        String mnemonic = store.revealMnemonic(e.id);
        assertNotNull(mnemonic);
        assertEquals(24, mnemonic.split(" ").length);
        String xml = com.intellij.openapi.util.JDOMUtil.write(XmlSerializer.serialize(store.getState()));
        for (String word : mnemonic.split(" ")) {
            if (word.length() > 5) assertFalse("I10: no mnemonic word in the persisted state: " + word, xml.contains(" " + word + " "));
        }
        assertFalse(xml.contains(mnemonic));
        assertTrue(store.remove(e.id));
        assertNull("the secret leaves PasswordSafe with the account", store.revealMnemonic(e.id));
    }

    public void testLoadedStateIsValidatedAsUntrusted() {
        var bad = new JoustAccounts.StateBean();
        var e = new JoustAccounts.Entry();
        e.id = "x";
        e.name = "x";
        e.address = "addr_test1x";
        e.fingerprint = "not hex";
        e.networkClass = "TESTNET";
        e.origin = "FRESH";
        var e2 = new JoustAccounts.Entry();
        e2.id = "y";
        e2.name = "y";
        e2.address = "addr_test1y";
        e2.fingerprint = "a".repeat(56);
        e2.networkClass = "SANCHONET";
        e2.origin = "FRESH";
        bad.accounts.add(e);
        bad.accounts.add(e2);
        store.loadState(bad);
        assertTrue(store.getState().accounts.isEmpty());
    }

    public void testLegacyAccountsMigrateAsTestnetOnly() throws Exception {
        String m = new Account(Networks.testnet()).mnemonic();
        var e = store.migrateLegacy("legacy", m);
        assertEquals("TESTNET", e.networkClass);
        assertEquals("MIGRATED", e.origin);
        assertTrue(store.list(NetworkClass.MAINNET).stream().noneMatch(x -> x.id.equals(e.id)));
    }
}
