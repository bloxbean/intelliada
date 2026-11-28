package com.bloxbean.intelliada.idea.joust.sign;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.config.CryptoConfiguration;
import com.bloxbean.intelliada.idea.joust.accounts.JoustAccounts;
import com.bloxbean.intelliada.idea.joust.tx.TxCbor;
import com.bloxbean.intelliada.idea.joust.tx.TxSplice;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ADR-005 D13 "Signing" (I11; M3.3b2): signs a body the host built with the payment keys IntelliAda holds, by splicing.
 * The body hash is Blake2b-256 of the body bytes as received; each held key that the body requires signs it; the signed
 * transaction is spliced ({@link TxSplice}), never re-encoded, and its id must be the body's. Before submission every
 * key the body requires has a witness: its {@code required_signers}, and the payment key of every spending and
 * collateral input held at a key address. Keys never leave this process (I10).
 */
public final class JoustSigner {

    /** Nothing is signed; plain text for the panel. */
    public static final class Refused extends Exception {
        public Refused(String why) {
            super(why, null, false, false);
        }
    }

    /** Where an input's output is held: its address (bech32), or null when the node does not know it. */
    @FunctionalInterface
    public interface Addresses {
        @Nullable String of(String ref) throws Exception;
    }

    private JoustSigner() {
    }

    /**
     * Every payment key hash the body requires: its {@code required_signers}, and the payment credential of each spending
     * and collateral input held at a key address (a script address needs no vkey witness). An input whose address the
     * node does not know refuses: its key would otherwise be missed.
     */
    public static Set<String> requiredKeys(TxCbor.Body body, Addresses addresses) throws Refused {
        Set<String> out = new LinkedHashSet<>(body.requiredSigners());
        Set<String> refs = new LinkedHashSet<>(body.inputs());
        refs.addAll(body.collateral());
        for (String ref : refs) {
            String address;
            try {
                address = addresses.of(ref);
            } catch (Exception e) {
                throw new Refused("an input's address cannot be read from the node (" + e.getClass().getSimpleName() + ")");
            }
            if (address == null) throw new Refused("the node does not know input " + ref);
            String key = paymentKey(address);
            if (key != null) out.add(key);
        }
        return out;
    }

    /** The payment key hash of a key address, or null for a script address. */
    public static @Nullable String paymentKey(String bech32) throws Refused {
        try {
            var cred = new Address(bech32).getPaymentCredential();
            if (cred.isEmpty() || cred.get().getType() != CredentialType.Key) return null;
            return HexFormat.of().formatHex(cred.get().getBytes());
        } catch (RuntimeException e) {
            throw new Refused("an input's address cannot be read");
        }
    }

    /**
     * The transaction {@code cborHex}, signed with each of {@code keys} that {@code required} names, spliced. Refused when
     * a required key is not among them (nothing is submitted), or the splice would change the body.
     */
    public static String sign(String cborHex, List<JoustAccounts.SigningKey> keys, Set<String> required) throws Refused {
        TxCbor.Tx tx;
        try {
            tx = TxCbor.transaction(cborHex);
        } catch (TxCbor.Malformed m) {
            throw new Refused("the prepared transaction cannot be read: " + m.getMessage());
        }
        byte[] hash = Blake2bUtil.blake2bHash256(tx.body().of(tx.bytes()));
        var signing = CryptoConfiguration.INSTANCE.getSigningProvider();
        List<TxSplice.VkeyWitness> witnesses = new ArrayList<>();
        Set<String> signed = new LinkedHashSet<>();
        for (JoustAccounts.SigningKey k : keys) {
            if (!required.contains(k.fingerprint()) || !signed.add(k.fingerprint())) continue;
            byte[] sig = signing.signExtended(hash, k.privateKey());
            if (!signing.verify(sig, hash, k.publicKey())) throw new Refused("a signature did not verify");
            witnesses.add(new TxSplice.VkeyWitness(k.publicKey(), sig));
        }
        Set<String> missing = new LinkedHashSet<>(required);
        missing.removeAll(signed);
        if (!missing.isEmpty()) {
            throw new Refused("the transaction needs a signature from a key IntelliAda does not hold (" + String.join(", ", missing)
                    + "); nothing was submitted");
        }
        if (witnesses.isEmpty()) throw new Refused("the transaction requires no key IntelliAda holds; nothing was signed");
        byte[] out;
        try {
            out = TxSplice.splice(tx, witnesses);
            if (!TxCbor.transaction(out).txHash().equals(tx.txHash())) throw new Refused("signing would change the transaction's id");
        } catch (TxCbor.Malformed m) {
            throw new Refused("the transaction cannot be signed: " + m.getMessage());
        }
        return HexFormat.of().formatHex(out);
    }
}
