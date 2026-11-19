package org.julclang.state.examples.counter;

import org.julclang.core.types.JulcList;
import org.julclang.state.api.Action;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.Init;
import org.julclang.state.api.OnlyOwner;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Require;
import org.julclang.state.api.StateContract;
import org.julclang.state.api.View;

import java.math.BigInteger;

/**
 * A counter anyone can increment and only its owner can reset or close.
 * <p>
 * This is the only file the developer writes. The JOUST processor generates {@code CounterValidator} (compiled to
 * UPLC by JuLC) and {@code CounterClient}. The same methods run on the JVM (to compute the next state) and on-chain
 * (to check it).
 */
@StateContract(maxDatumBytes = 128)
public class Counter {

    /** The state, stored as the inline datum of the state UTxO. */
    public record State(BigInteger count, byte[] owner) {}

    /** The deployer becomes the owner. */
    @Init
    public static State init(Ctx ctx) {
        return new State(BigInteger.ZERO, ctx.caller());
    }

    /** Anyone can increment by a positive amount (no Ctx: no signature needed, cheaper). */
    @Action
    public static State increment(State s, BigInteger by) {
        Require.that(by.compareTo(BigInteger.ZERO) > 0, "increment must be positive");
        return new State(s.count().add(by), s.owner());
    }

    /** Only the owner can reset. */
    @Action
    @OnlyOwner
    public static State reset(State s) {
        return new State(BigInteger.ZERO, s.owner());
    }

    /** Only the owner can close the counter; the deposit goes back to them. */
    @Destroy
    @OnlyOwner
    public static JulcList<Payout> close(State s, Ctx ctx) {
        return Payouts.of(s.owner(), ctx.balance());
    }

    /** Read-only view (off-chain, no transaction). */
    @View
    public static BigInteger count(State s) {
        return s.count();
    }
}
