package org.julclang.state.examples.vault;

import org.julclang.core.types.JulcList;
import org.julclang.state.api.Action;
import org.julclang.state.api.Clock;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.Init;
import org.julclang.state.api.OnlyOwner;
import org.julclang.state.api.Payable;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Require;
import org.julclang.state.api.StateContract;
import org.julclang.state.api.ValidAfter;
import org.julclang.state.api.View;

import java.math.BigInteger;

/**
 * A time-locked savings vault. Anyone can deposit; only the owner can withdraw, and only after {@code unlockAt}.
 * The contract tracks {@code total}; the generated validator's exact value accounting keeps it equal to the funds the
 * vault actually holds.
 */
@StateContract(maxDatumBytes = 128)
public class Vault {

    public record State(byte[] owner, BigInteger unlockAt, BigInteger total) {}

    /** An action that pays out returns the next state and its payouts. */
    public record Result(State state, JulcList<Payout> payouts) {}

    /** Opens a vault, optionally with a first deposit; the deployer owns it. */
    @Init
    @Payable
    public static State open(BigInteger unlockAt, Ctx ctx) {
        Require.that(Clock.before(ctx, unlockAt), "unlockAt must be in the future");
        return new State(ctx.caller(), unlockAt, ctx.received());
    }

    /** Anyone can add funds. */
    @Action
    @Payable
    public static State deposit(State s, Ctx ctx) {
        Require.that(ctx.received().compareTo(BigInteger.ZERO) > 0, "deposit must be positive");
        return new State(s.owner(), s.unlockAt(), s.total().add(ctx.received()));
    }

    /** The owner takes funds out once the vault is unlocked. */
    @Action
    @OnlyOwner
    @ValidAfter("unlockAt")
    public static Result withdraw(State s, BigInteger amount, Ctx ctx) {
        Require.that(amount.compareTo(BigInteger.ZERO) > 0, "amount must be positive");
        Require.that(amount.compareTo(s.total()) <= 0, "insufficient funds");
        return new Result(new State(s.owner(), s.unlockAt(), s.total().subtract(amount)), Payouts.of(s.owner(), amount));
    }

    /** The owner closes an unlocked vault and takes everything, deposit included. */
    @Destroy
    @OnlyOwner
    @ValidAfter("unlockAt")
    public static JulcList<Payout> close(State s, Ctx ctx) {
        return Payouts.of(s.owner(), ctx.balance());
    }

    @View
    public static BigInteger total(State s) {
        return s.total();
    }

    @View
    public static BigInteger unlockAt(State s) {
        return s.unlockAt();
    }
}
