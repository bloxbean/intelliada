package com.bloxbean.intelliada.idea.joust.verifier.canary;

/** A compiled canary for the I16 architecture test: it refers to a Plutus data encoder and a transaction reader. */
public final class ArchitectureCanary {
    com.bloxbean.cardano.client.plutus.spec.BytesPlutusData data;

    Object read(byte[] b) throws Exception {
        return com.bloxbean.cardano.client.transaction.spec.Transaction.deserialize(b);
    }
}
