package org.julclang.state.examples.pool;

import org.julclang.core.types.JulcList;
import org.julclang.state.api.Action;
import org.julclang.state.api.Batched;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.Init;
import org.julclang.state.api.Payable;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Require;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.StateContract;
import org.julclang.state.api.View;

import java.math.BigInteger;

/**
 * A community pool (ADR-0004): anyone donates, and anyone buys a ticket whose price rises with every ticket sold.
 * Both are hot shared writes. As {@code @Batched} actions they run as intents: each buyer locks a request in their own
 * UTxO, and a batcher applies many of them in one transaction, so buyers never contend for the pool UTxO.
 * <p>
 * {@code buy} depends on order (each ticket costs more than the last). The buyer bounds what they accept with
 * {@code maxPrice} and gets their change back as a payout.
 */
@StateContract(maxDatumBytes = 192)
public class Pool {

    public record State(byte[] admin, BigInteger donated, BigInteger sold, BigInteger price, BigInteger step) {}

    public record Sale(State state, JulcList<Payout> payouts) {}

    @Init
    public static State init(BigInteger price, BigInteger step, Ctx ctx) {
        Require.that(price.compareTo(BigInteger.ZERO) > 0 && step.compareTo(BigInteger.ZERO) >= 0, "bad pricing");
        return new State(ctx.caller(), BigInteger.ZERO, BigInteger.ZERO, price, step);
    }

    @Action
    @Batched
    @Payable
    public static State donate(State s, Ctx ctx) {
        Require.that(ctx.received().compareTo(BigInteger.valueOf(1_000_000)) >= 0, "donate at least 1 ADA");
        return new State(s.admin(), s.donated().add(ctx.received()), s.sold(), s.price(), s.step());
    }

    /**
     * Buys one ticket at the current price, if it is at most {@code maxPrice}; the change goes back to the buyer. Only as
     * an intent ({@code only = true}): nobody can spend the pool with a direct buy and so invalidate pending batches.
     */
    @Action
    @Batched(only = true)
    @Payable
    public static Sale buy(State s, BigInteger maxPrice, Ctx ctx) {
        Require.that(s.price().compareTo(maxPrice) <= 0, "price above the buyer's bound");
        Require.that(ctx.received().compareTo(s.price()) >= 0, "pays less than the price");
        BigInteger change = ctx.received().subtract(s.price());
        State next = new State(s.admin(), s.donated(), s.sold().add(BigInteger.ONE), s.price().add(s.step()), s.step());
        if (change.compareTo(BigInteger.ZERO) > 0) {
            return new Sale(next, Payouts.of(ctx.caller(), change));
        }
        return new Sale(next, Payouts.none());
    }

    @Action
    @SignedBy("admin")
    public static Sale withdraw(State s, BigInteger amount, Ctx ctx) {
        Require.that(amount.compareTo(BigInteger.ZERO) > 0 && amount.compareTo(ctx.balance()) <= 0, "bad amount");
        return new Sale(s, Payouts.of(s.admin(), amount));
    }

    @Destroy
    @SignedBy("admin")
    public static JulcList<Payout> close(State s, Ctx ctx) {
        return Payouts.of(s.admin(), ctx.balance());
    }

    @View
    public static BigInteger nextPrice(State s) {
        return s.price();
    }
}
