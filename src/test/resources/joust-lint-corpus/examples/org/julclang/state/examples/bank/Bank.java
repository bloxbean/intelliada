package org.julclang.state.examples.bank;

import org.julclang.core.types.JulcList;
import org.julclang.state.api.Action;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Init;
import org.julclang.state.api.Owned;
import org.julclang.state.api.OwnedAction;
import org.julclang.state.api.OwnedDestroy;
import org.julclang.state.api.OwnedInit;
import org.julclang.state.api.Payable;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Require;
import org.julclang.state.api.Shared;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.StateContract;
import org.julclang.state.api.View;

import java.math.BigInteger;

/**
 * A bank with one account per customer (ADR-0003). The bank's shared state (admin, paused flag, withdrawal limit) is
 * read by accounts through a reference input; each account is its own UTxO, so customers never queue behind each
 * other. A customer can always close their account, whatever the bank's state (the escape hatch).
 */
@StateContract(maxDatumBytes = 160)
public class Bank {

    public record State(byte[] admin, boolean paused, BigInteger withdrawLimit) {}

    @Owned
    public record Account(byte[] owner, BigInteger balance) {}

    public record Withdrawal(Account state, JulcList<Payout> payouts) {}

    @Init
    public static State init(BigInteger withdrawLimit, Ctx ctx) {
        return new State(ctx.caller(), false, withdrawLimit);
    }

    @Action
    @SignedBy("admin")
    public static State setPaused(State s, boolean paused) {
        return new State(s.admin(), paused, s.withdrawLimit());
    }

    @Action
    @SignedBy("admin")
    public static State setLimit(State s, BigInteger withdrawLimit) {
        Require.that(withdrawLimit.compareTo(BigInteger.ZERO) > 0, "limit must be positive");
        return new State(s.admin(), s.paused(), withdrawLimit);
    }

    /** Opens an account for the caller, with a first deposit. */
    @OwnedInit
    @Payable
    public static Account open(@Shared State bank, Ctx ctx) {
        Require.that(!bank.paused(), "the bank is paused");
        return new Account(ctx.caller(), ctx.received());
    }

    @OwnedAction
    @Payable
    @SignedBy("owner")
    public static Account deposit(Account a, Ctx ctx) {
        Require.that(ctx.received().compareTo(BigInteger.ZERO) > 0, "deposit must be positive");
        return new Account(a.owner(), a.balance().add(ctx.received()));
    }

    /** Takes money out, within the bank's limit, unless the bank is paused. */
    @OwnedAction
    @SignedBy("owner")
    public static Withdrawal withdraw(Account a, @Shared State bank, BigInteger amount, Ctx ctx) {
        Require.that(!bank.paused(), "the bank is paused");
        Require.that(amount.compareTo(BigInteger.ZERO) > 0 && amount.compareTo(bank.withdrawLimit()) <= 0,
                "amount outside the withdrawal limit");
        Require.that(amount.compareTo(a.balance()) <= 0, "insufficient balance");
        return new Withdrawal(new Account(a.owner(), a.balance().subtract(amount)), Payouts.of(a.owner(), amount));
    }

    /** The owner closes the account and takes everything (works even when the bank is paused). */
    @OwnedDestroy
    @SignedBy("owner")
    public static JulcList<Payout> close(Account a, Ctx ctx) {
        return Payouts.of(a.owner(), ctx.balance());
    }

    @View
    public static boolean paused(State s) {
        return s.paused();
    }
}
