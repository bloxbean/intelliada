package org.julclang.state.examples.oracle;

import org.julclang.core.types.JulcList;
import org.julclang.state.api.Action;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.Init;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Require;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.StateContract;

import java.math.BigInteger;

/** A price feed (ADR-0011): the publisher posts a price with the time it was set (the call's validity start). */
@StateContract
public class Oracle {

    public record State(byte[] publisher, BigInteger price, BigInteger updatedAt) {}

    @Init
    public static State open(BigInteger price, Ctx ctx) {
        Require.that(price.signum() > 0, "price must be positive");
        return new State(ctx.caller(), price, ctx.validFrom());
    }

    @Action @SignedBy("publisher")
    public static State post(State s, BigInteger price, Ctx ctx) {
        Require.that(price.signum() > 0, "price must be positive");
        return new State(s.publisher(), price, ctx.validFrom());
    }

    @Destroy @SignedBy("publisher")
    public static JulcList<Payout> close(State s, Ctx ctx) {
        return Payouts.of(s.publisher(), ctx.balance());
    }
}
