package org.julclang.state.examples.fees;

import org.julclang.core.types.JulcList;
import org.julclang.state.api.Action;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Deposits;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.Funds;
import org.julclang.state.api.Init;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Receives;
import org.julclang.state.api.Require;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.StateContract;

import java.math.BigInteger;

/**
 * A fee pot (ADR-0011 D3): other contracts deposit into it in the same transaction, and {@link #collect} takes their
 * deposits in. Its validator adds exactly the claimed deposits to its value, and the method credits them.
 */
@StateContract
public class Pot {

    public record State(byte[] owner, BigInteger collected, BigInteger deposits) {}

    @Init
    public static State init(Ctx ctx) {
        return new State(ctx.caller(), BigInteger.ZERO, BigInteger.ZERO);
    }

    /** Takes in every deposit made to this pot in the transaction; anyone may call it, but only with deposits. */
    @Action
    @Receives
    public static State collect(State s, Ctx ctx, Funds funds) {
        BigInteger got = Deposits.lovelace(funds.deposits());
        Require.that(got.signum() > 0, "nothing deposited");
        return new State(s.owner(), s.collected().add(got),
                s.deposits().add(BigInteger.valueOf(funds.deposits().size())));
    }

    /** Hands the pot to a new owner. Not @Receives: a deposit claimed on its output makes it fail (I40). */
    @Action
    @SignedBy("owner")
    public static State handOver(State s, byte[] newOwner) {
        return new State(newOwner, s.collected(), s.deposits());
    }

    @Destroy
    @SignedBy("owner")
    public static JulcList<Payout> close(State s, Ctx ctx) {
        return Payouts.of(s.owner(), ctx.balance());
    }
}
