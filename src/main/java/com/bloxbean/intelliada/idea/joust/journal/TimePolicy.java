package com.bloxbean.intelliada.idea.joust.journal;

import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity;

/**
 * ADR-005 D14b's time policy in one place (M33B1-DES-7): what {@code initialize} sends and what the journal derives its
 * bounds from must be the same numbers.
 */
public final class TimePolicy {

    /** {@code maxNowAheadSlots} on every class. */
    public static final long MAX_NOW_AHEAD = 900;
    /** The largest {@code maxTtlSlots} any class allows (an export on a public network). */
    public static final long LARGEST_TTL = 7_200;
    static final long DEVNET_SETTLEMENT = 60, PUBLIC_SETTLEMENT = 2_160;
    static final long DEVNET_TTL = 900;

    private TimePolicy() {
    }

    public static long settlementSlots(NetworkIdentity.NetworkClass c) {
        return c == NetworkIdentity.NetworkClass.DEVNET ? DEVNET_SETTLEMENT : PUBLIC_SETTLEMENT;
    }

    /** {@code maxTtlSlots}: devnets {@code min(900, horizon)}; public networks the export TTL. */
    public static long maxTtlSlots(NetworkIdentity.Identity id) {
        return id.networkClass() == NetworkIdentity.NetworkClass.DEVNET ? Math.min(DEVNET_TTL, id.horizonSlots()) : LARGEST_TTL;
    }
}
