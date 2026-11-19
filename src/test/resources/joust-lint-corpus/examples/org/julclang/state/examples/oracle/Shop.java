package org.julclang.state.examples.oracle;

import org.julclang.state.examples.oracle.Oracle;
import org.julclang.core.types.JulcList;
import org.julclang.state.api.Action;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.ForeignInstance;
import org.julclang.state.api.Init;
import org.julclang.state.api.Params;
import org.julclang.state.api.Payable;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Reads;
import org.julclang.state.api.Require;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.StateContract;

import java.math.BigInteger;

/**
 * A shop that sells credits at the oracle's price (ADR-0011 D2). The oracle instance is fixed in the params, so its
 * script and instance are pinned at deploy; the price must be fresh (posted at most {@code maxAgeMs} before the call).
 */
@StateContract
public class Shop {

    @Params
    public record Config(ForeignInstance oracle, BigInteger maxAgeMs) {}

    public record State(byte[] owner, BigInteger sold) {}

    @Init
    public static State open(Config cfg, Ctx ctx) {
        return new State(ctx.caller(), BigInteger.ZERO);
    }

    /** Buys {@code qty} credits, paying {@code qty * price} at a price no older than {@code maxAgeMs}. */
    @Action @Payable
    public static State buy(State s, Config cfg, @Reads("params.oracle") Oracle.State price, BigInteger qty, Ctx ctx) {
        Require.that(qty.signum() > 0, "buy at least one credit");
        Require.that(ctx.validFrom().subtract(price.updatedAt()).compareTo(cfg.maxAgeMs()) <= 0, "stale price");
        Require.that(ctx.received().compareTo(qty.multiply(price.price())) == 0, "pay exactly qty * price");
        return new State(s.owner(), s.sold().add(qty));
    }

    @Destroy @SignedBy("owner")
    public static JulcList<Payout> close(State s, Ctx ctx) {
        return Payouts.of(s.owner(), ctx.balance());
    }
}
