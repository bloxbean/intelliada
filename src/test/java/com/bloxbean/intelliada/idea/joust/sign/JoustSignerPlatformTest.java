package com.bloxbean.intelliada.idea.joust.sign;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.crypto.config.CryptoConfiguration;
import com.bloxbean.intelliada.idea.joust.accounts.JoustAccounts;
import com.bloxbean.intelliada.idea.joust.tx.TxCbor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;

/**
 * ADR-005 D13 "Signing" (I11; M3.3b2): the splice keeps the body, the received witness entries and the rest byte for
 * byte, adds one vkey witness per required key that verifies against the body hash, and refuses when a required key is
 * not held. (A platform test: CCL's mnemonic code logs through the IDE's slf4j.)
 */
public class JoustSignerPlatformTest extends BasePlatformTestCase {

    private static final String MNEMONIC = "test test test test test test test test test test test test test test test test test"
            + " test test test test test test sauce";

    private static JoustAccounts.SigningKey key(int index) {
        Account a = new Account(Networks.testnet(), MNEMONIC, DerivationPath.createExternalAddressDerivationPathForAccount(index));
        var kp = a.hdKeyPair();
        return new JoustAccounts.SigningKey(HexFormat.of().formatHex(Blake2bUtil.blake2bHash224(a.publicKeyBytes())),
                kp.getPublicKey().getKeyData(), kp.getPrivateKey().getKeyData());
    }

    private static String address(int index) {
        return new Account(Networks.testnet(), MNEMONIC, DerivationPath.createExternalAddressDerivationPathForAccount(index)).baseAddress();
    }

    /** A body spending one input with validTo 900, and a witness set holding only a redeemers entry (key 5, empty). */
    private static final String BODY = "a2" + "00" + "81" + "825820" + "11".repeat(32) + "00" + "03" + "190384";
    private static final String WITNESSES = "a1" + "05" + "80";
    private static final String TX = "84" + BODY + WITNESSES + "f5" + "f6";

    public void testTheSpliceKeepsTheBodyAndTheReceivedEntriesAndAddsAVerifyingWitness() throws Exception {
        JoustAccounts.SigningKey k = key(0);
        String signed = JoustSigner.sign(TX, List.of(k), Set.of(k.fingerprint()));
        TxCbor.Tx before = TxCbor.transaction(TX), after = TxCbor.transaction(signed);
        assertEquals("the id is unchanged", before.txHash(), after.txHash());
        assertArrayEquals("the body is byte for byte", before.body().of(before.bytes()), after.body().of(after.bytes()));
        List<TxCbor.WitnessEntry> entries = TxCbor.witnessEntries(after);
        assertEquals(List.of(0L, 5L), entries.stream().map(TxCbor.WitnessEntry::key).toList());
        assertEquals("the received entry as received", "0580", HexFormat.of().formatHex(entries.get(1).span().of(after.bytes())));
        // key 0: [[vkey(32), sig(64)]]
        String w = HexFormat.of().formatHex(entries.get(0).span().of(after.bytes()));
        assertTrue(w, w.startsWith("00" + "81" + "82" + "5820" + HexFormat.of().formatHex(k.publicKey()) + "5840"));
        byte[] sig = HexFormat.of().parseHex(w.substring(w.length() - 128));
        byte[] hash = Blake2bUtil.blake2bHash256(before.body().of(before.bytes()));
        assertTrue("the signature verifies", CryptoConfiguration.INSTANCE.getSigningProvider().verify(sig, hash, k.publicKey()));
        assertEquals("is_valid and the auxiliary data as received", "f5f6", signed.substring(signed.length() - 4));
    }

    public void testCoSignersEachAddAWitnessAndKeysNotRequiredAreSkipped() throws Exception {
        JoustAccounts.SigningKey a = key(0), b = key(1), c = key(2);
        String signed = JoustSigner.sign(TX, List.of(a, b, c), Set.of(a.fingerprint(), b.fingerprint()));
        String w = HexFormat.of().formatHex(TxCbor.witnessEntries(TxCbor.transaction(signed)).get(0).span().of(TxCbor.transaction(signed).bytes()));
        assertTrue("two witnesses", w.startsWith("0082"));
        assertFalse("the unrequired key does not sign", w.contains(HexFormat.of().formatHex(c.publicKey())));
    }

    public void testARequiredKeyNotHeldRefusesAndNothingIsSigned() {
        JoustAccounts.SigningKey a = key(0);
        try {
            JoustSigner.sign(TX, List.of(a), Set.of(a.fingerprint(), "ab".repeat(28)));
            fail("signed without every required key");
        } catch (JoustSigner.Refused expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("nothing was submitted"));
        }
    }

    public void testRequiredKeysAreTheSignersAndTheKeyInputsPaymentKeys() throws Exception {
        TxCbor.Body body = TxCbor.body(TxCbor.transaction(TX));
        Set<String> keys = JoustSigner.requiredKeys(body, ref -> address(3));
        assertEquals(Set.of(key(3).fingerprint()), keys);
        try {
            JoustSigner.requiredKeys(body, ref -> null);
            fail("an input the node does not know");
        } catch (JoustSigner.Refused expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("does not know"));
        }
    }

    public void testAWitnessSetWithVkeysAlreadyIsRefused() {
        String withVkeys = "84" + BODY + "a1" + "00" + "80" + "f5" + "f6";
        try {
            JoustSigner.sign(withVkeys, List.of(key(0)), Set.of(key(0).fingerprint()));
            fail("spliced over existing vkey witnesses");
        } catch (JoustSigner.Refused expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("already holds vkey"));
        }
    }

    public void testSigningKeysNeverPrintTheirBytes() {
        JoustAccounts.SigningKey k = key(0);
        assertFalse(k.toString().contains(HexFormat.of().formatHex(k.privateKey()).substring(0, 16)));
    }
}
