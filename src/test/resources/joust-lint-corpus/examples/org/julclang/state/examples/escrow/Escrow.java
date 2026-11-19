package org.julclang.state.examples.escrow;

import org.julclang.core.types.JulcList;
import org.julclang.state.api.Clock;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.Init;
import org.julclang.state.api.Payable;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Require;
import org.julclang.state.api.StateContract;
import org.julclang.state.api.View;
import org.julclang.stdlib.lib.ByteStringLib;

import java.math.BigInteger;

/**
 * Two-party escrow with an arbiter. The buyer funds it when deploying. The buyer (or the arbiter) releases the funds
 * to the seller; the seller (or the arbiter) can refund the buyer at any time, and anyone can once the deadline has
 * passed. Either way the instance ends and everything it holds goes to the winner.
 */
@StateContract(maxDatumBytes = 192)
public class Escrow {

    public record State(byte[] buyer, byte[] seller, byte[] arbiter, BigInteger amount, BigInteger deadline) {}

    /** The buyer deposits the price. */
    @Init
    @Payable
    public static State open(byte[] seller, byte[] arbiter, BigInteger deadline, Ctx ctx) {
        Require.that(ctx.received().compareTo(BigInteger.ZERO) > 0, "the escrow must be funded");
        Require.that(Clock.before(ctx, deadline), "deadline must be in the future");
        return new State(ctx.caller(), seller, arbiter, ctx.received(), deadline);
    }

    /** Buyer or arbiter: the seller delivered, pay them. */
    @Destroy
    public static JulcList<Payout> release(State s, Ctx ctx) {
        boolean allowed = ByteStringLib.equals(ctx.caller(), s.buyer()) || ByteStringLib.equals(ctx.caller(), s.arbiter());
        Require.that(allowed, "only the buyer or the arbiter can release");
        return Payouts.of(s.seller(), ctx.balance());
    }

    /** Seller or arbiter at any time, anyone after the deadline: give the buyer their money back. */
    @Destroy
    public static JulcList<Payout> refund(State s, Ctx ctx) {
        boolean party = ByteStringLib.equals(ctx.caller(), s.seller()) || ByteStringLib.equals(ctx.caller(), s.arbiter());
        Require.that(party || Clock.after(ctx, s.deadline()), "only the seller or the arbiter can refund before the deadline");
        return Payouts.of(s.buyer(), ctx.balance());
    }

    @View
    public static BigInteger amount(State s) {
        return s.amount();
    }
}
