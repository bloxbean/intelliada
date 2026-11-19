package org.julclang.state.examples.fees;

import org.julclang.core.types.JulcList;
import org.julclang.stdlib.Builtins;
import org.julclang.state.api.Action;
import org.julclang.state.api.Assets;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Deposit;
import org.julclang.state.api.Deposits;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.ForeignInstance;
import org.julclang.state.api.Init;
import org.julclang.state.api.Params;
import org.julclang.state.api.Payable;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Require;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.StateContract;

import java.math.BigInteger;

/**
 * A market stall (ADR-0011 D3): each sale pays the stall, and a fee goes straight into a {@link Pot} instance, fixed in
 * the params, as a deposit. The stall's validator subtracts the fee from its own value and requires the pot to take it
 * in the same transaction; the memo names the buyer.
 */
@StateContract
public class Stall {

    @Params
    public record Config(ForeignInstance pot, BigInteger feeBps) {}

    public record State(byte[] owner, BigInteger sales) {}

    public record Sale(State state, JulcList<Deposit> deposits) {}

    @Init
    public static State init(Config cfg, Ctx ctx) {
        Require.that(cfg.feeBps().signum() > 0 && cfg.feeBps().compareTo(BigInteger.valueOf(10_000)) < 0, "fee in (0, 100%)");
        return new State(ctx.caller(), BigInteger.ZERO);
    }

    /** Sells for {@code price} lovelace: the stall keeps the price less the fee, the pot gets the fee. */
    @Action
    @Payable
    public static Sale sell(State s, Config cfg, BigInteger price, Ctx ctx) {
        Require.that(ctx.received().compareTo(price) == 0 && price.compareTo(BigInteger.valueOf(2_000_000)) >= 0,
                "pay exactly the price, at least 2 ADA");
        BigInteger fee = price.multiply(cfg.feeBps()).divide(BigInteger.valueOf(10_000));
        Require.that(fee.compareTo(BigInteger.ONE) >= 0, "the fee rounds to zero");
        return new Sale(new State(s.owner(), s.sales().add(price.subtract(fee))),
                Deposits.of(cfg.pot(), Assets.lovelace(fee), Builtins.bData(ctx.caller())));
    }

    @Destroy
    @SignedBy("owner")
    public static JulcList<Payout> close(State s, Ctx ctx) {
        return Payouts.of(s.owner(), ctx.balance());
    }
}
