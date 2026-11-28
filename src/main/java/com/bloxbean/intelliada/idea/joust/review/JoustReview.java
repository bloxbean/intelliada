package com.bloxbean.intelliada.idea.joust.review;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ADR-005 D13 "The review modal" (M3.3b3): what IntelliAda derives from the <b>body</b> it is about to sign, with CCL's
 * transaction reader, for display only (I16: CCL decoding lives in this package only). Never the host's
 * {@code predicted}. It shows the outputs to addresses that are neither the deployment's nor the caller's, the caller's
 * net outflow, the fee, the required signers and the validity window. A decline discards the body; the modal can never
 * override a refusal (L25).
 */
public final class JoustReview {

    /** Where an output goes. */
    public enum To { DEPLOYMENT, CALLER, OTHER }

    /** One output: its address, its lovelace, its assets as {@code policy.name quantity}, and where it goes. */
    public record Output(String address, BigInteger lovelace, List<String> assets, To to) {
    }

    /**
     * The summary of one body. {@code callerNetOutflow} is the caller's inputs (read from the node) minus the outputs
     * back to the caller, in lovelace; null when an input's value could not be read.
     */
    public record Summary(String label, String txHash, BigInteger fee, @Nullable Long validFrom, long validTo, List<Output> outputs,
                          @Nullable BigInteger callerNetOutflow, List<String> signers, List<String> minted, List<String> callerTokensOut,
                          @Nullable String note) {

        /** The outputs to addresses that are neither the deployment's nor the caller's. */
        public List<Output> others() {
            return outputs.stream().filter(o -> o.to() == To.OTHER).toList();
        }
    }

    /** An input's output as the node reads it ({@code NodeChain.output}): its {@code address} and {@code amount}. */
    @FunctionalInterface
    public interface Inputs {
        @Nullable JsonNode output(String ref) throws Exception;
    }

    /** Refused: the body cannot be read for review. */
    public static final class Unreadable extends Exception {
        Unreadable(String why) {
            super(why, null, false, false);
        }
    }

    private JoustReview() {
    }

    /**
     * @param deploymentScripts the deployment's script hashes (the contract's and its verifiers'): an output at a script
     *                          address with one of them is the deployment's
     * @param signerNames       payment key hash to an account id, for the signers IntelliAda holds
     */
    public static Summary of(String label, String cborHex, String callerAddress, Set<String> deploymentScripts,
                             Map<String, String> signerNames, Set<String> requiredKeys, Inputs inputs) throws Unreadable {
        Transaction tx;
        try {
            tx = Transaction.deserialize(HexFormat.of().parseHex(cborHex));
        } catch (Exception e) {
            throw new Unreadable("the transaction cannot be read for review (" + e.getClass().getSimpleName() + ")");
        }
        var body = tx.getBody();
        String callerKey = paymentKey(callerAddress);
        List<Output> outs = new ArrayList<>();
        BigInteger back = BigInteger.ZERO;
        Map<String, BigInteger> tokens = new java.util.TreeMap<>();   // unit (policy + name hex) to the caller's net outflow
        for (TransactionOutput o : body.getOutputs()) {
            String address = o.getAddress();
            BigInteger lovelace = o.getValue() == null || o.getValue().getCoin() == null ? BigInteger.ZERO : o.getValue().getCoin();
            To to = to(address, callerAddress, callerKey, deploymentScripts);
            if (to == To.CALLER) {
                back = back.add(lovelace);
                if (o.getValue() != null && o.getValue().getMultiAssets() != null) {
                    for (MultiAsset m : o.getValue().getMultiAssets()) {
                        for (var a : m.getAssets()) tokens.merge(m.getPolicyId() + hexName(a.getName()), a.getValue().negate(), BigInteger::add);
                    }
                }
            }
            outs.add(new Output(address, lovelace, assets(o.getValue() == null ? null : o.getValue().getMultiAssets()), to));
        }
        BigInteger spent = BigInteger.ZERO;
        boolean known = true;
        for (TransactionInput in : body.getInputs()) {
            try {
                JsonNode u = inputs.output(in.getTransactionId() + "#" + in.getIndex());
                if (u == null) {
                    known = false;
                    continue;
                }
                String a = u.path("address").textValue();
                if (a == null || to(a, callerAddress, callerKey, deploymentScripts) != To.CALLER) continue;
                for (JsonNode amt : u.path("amount")) {
                    String unit = amt.path("unit").textValue();
                    BigInteger q = new BigInteger(amt.path("quantity").asText("0"));
                    if ("lovelace".equals(unit)) spent = spent.add(q);
                    else if (unit != null) tokens.merge(unit, q, BigInteger::add);
                }
            } catch (Exception e) {
                known = false;
            }
        }
        List<String> signers = new ArrayList<>();
        for (String k : requiredKeys) signers.add(signerNames.getOrDefault(k, "key " + k));
        Long from = body.getValidityStartInterval() == 0 ? null : body.getValidityStartInterval();
        BigInteger fee = body.getFee() == null ? BigInteger.ZERO : body.getFee();
        List<String> tokensOut = new ArrayList<>();
        if (known) tokens.forEach((unit, q) -> {
            if (q.signum() != 0) tokensOut.add(unit + " " + q);
        });
        return new Summary(label, txHashOf(cborHex), fee, from, body.getTtl(), List.copyOf(outs), known ? spent.subtract(back) : null,
                List.copyOf(signers), assets(body.getMint()), List.copyOf(tokensOut), known ? null : "an input could not be read: token movement unknown");
    }

    /**
     * FAB-30: a body CCL cannot read is still reviewed, from IntelliAda's own reader's facts (its id, validity and signers),
     * with the details marked unavailable; the decision stays the user's.
     */
    public static Summary reduced(String label, String txHash, @Nullable Long validFrom, long validTo, List<String> signers, String why) {
        return new Summary(label, txHash, BigInteger.ZERO, validFrom, validTo, List.of(), null, List.copyOf(signers), List.of(), List.of(),
                "details unavailable: " + why + ". JOUST's checker accepted it; the outputs, fee and outflow cannot be shown");
    }

    /** An asset name as hex (CCL gives a {@code 0x}-prefixed hex or the name's text). */
    private static String hexName(@Nullable String name) {
        if (name == null) return "";
        if (name.startsWith("0x")) return name.substring(2);
        return HexFormat.of().formatHex(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String txHashOf(String cborHex) {
        try {
            return com.bloxbean.intelliada.idea.joust.tx.TxCbor.transaction(cborHex).txHash();
        } catch (Exception e) {
            return "?";
        }
    }

    private static To to(String address, String callerAddress, @Nullable String callerKey, Set<String> deploymentScripts) {
        if (address.equals(callerAddress)) return To.CALLER;
        try {
            var cred = new Address(address).getPaymentCredential();
            if (cred.isPresent()) {
                String h = HexFormat.of().formatHex(cred.get().getBytes());
                if (cred.get().getType() == CredentialType.Script && deploymentScripts.contains(h)) return To.DEPLOYMENT;
                if (cred.get().getType() == CredentialType.Key && h.equals(callerKey)) return To.CALLER;
            }
        } catch (RuntimeException notAnAddress) {
            // shown as another address
        }
        return To.OTHER;
    }

    private static @Nullable String paymentKey(String address) {
        try {
            var cred = new Address(address).getPaymentCredential();
            return cred.isPresent() && cred.get().getType() == CredentialType.Key ? HexFormat.of().formatHex(cred.get().getBytes()) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<String> assets(@Nullable List<MultiAsset> multi) {
        List<String> out = new ArrayList<>();
        if (multi == null) return out;
        for (MultiAsset m : multi) {
            for (var a : m.getAssets()) {
                String name = a.getName() == null ? "" : a.getName();
                if (name.startsWith("0x")) name = name.substring(2);
                out.add(m.getPolicyId() + "." + name + " " + a.getValue());
            }
        }
        return out;
    }

    /** The summary as plain lines (I15: the caller renders them literally). */
    public static List<String> lines(Summary s) {
        List<String> l = new ArrayList<>();
        l.add(s.label());
        if (s.note() != null) l.add("Note: " + s.note());
        l.add("Transaction " + s.txHash());
        l.add("Fee: " + ada(s.fee()) + " ADA");
        l.add("Your net outflow: " + (s.callerNetOutflow() == null ? "unknown (an input could not be read)" : ada(s.callerNetOutflow()) + " ADA")
                + " (fee and deposits included)");
        l.add("Valid " + (s.validFrom() == null ? "" : "from slot " + s.validFrom() + " ") + "until slot " + s.validTo());
        l.add("Signed by: " + (s.signers().isEmpty() ? "no key" : String.join(", ", s.signers())));
        if (!s.minted().isEmpty()) l.add("Mints or burns: " + String.join("; ", s.minted()));
        if (!s.callerTokensOut().isEmpty()) l.add("Your tokens out (negative: in): " + String.join("; ", s.callerTokensOut()));
        List<Output> others = s.others();
        if (others.isEmpty()) {
            l.add("Sends nothing to other addresses.");
        } else {
            l.add("Sends to other addresses:");
            for (Output o : others) {
                l.add("  " + o.address() + ": " + ada(o.lovelace()) + " ADA" + (o.assets().isEmpty() ? "" : " + " + String.join(", ", o.assets())));
            }
        }
        long toDeployment = s.outputs().stream().filter(o -> o.to() == To.DEPLOYMENT).count();
        if (toDeployment > 0) l.add(toDeployment + " output(s) to the contract's own addresses.");
        return l;
    }

    static String ada(BigInteger lovelace) {
        return new java.math.BigDecimal(lovelace, 6).stripTrailingZeros().toPlainString();
    }
}
