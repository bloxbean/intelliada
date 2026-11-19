package org.julclang.state.examples.upgrade;

import org.julclang.core.types.JulcList;
import org.julclang.state.api.Action;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.Init;
import org.julclang.state.api.MigrateTo;
import org.julclang.state.api.Payable;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.StateContract;

import java.math.BigInteger;

/**
 * Version 1 of an upgradeable counter (ADR-0011 D4). Its admin names the successor contract ({@code setSuccessor}),
 * then {@code upgrade} ends the instance by moving it, count and funds, into that successor, in one transaction with
 * the successor's adoption.
 */
@StateContract
public class CounterV1 {

    public record State(byte[] admin, byte[] successor, BigInteger count) {}

    @Init @Payable
    public static State open(Ctx ctx) {
        return new State(ctx.caller(), new byte[]{}, BigInteger.ZERO);
    }

    @Action
    public static State increment(State s) {
        return new State(s.admin(), s.successor(), s.count().add(BigInteger.ONE));
    }

    @Action @SignedBy("admin")
    public static State setSuccessor(State s, byte[] successor) {
        return new State(s.admin(), successor, s.count());
    }

    /** Ends v1 by migrating: nothing is paid out, everything moves to the successor's new instance. */
    @Destroy @SignedBy("admin") @MigrateTo("successor")
    public static JulcList<Payout> upgrade(State s, Ctx ctx) {
        return Payouts.none();
    }
}
