package org.julclang.state.examples.upgrade;

import org.julclang.state.examples.upgrade.CounterV1;
import org.julclang.core.types.JulcList;
import org.julclang.state.api.Action;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.Init;
import org.julclang.state.api.Migrate;
import org.julclang.state.api.Params;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.StateContract;

import java.math.BigInteger;

/** Version 2: adds a step size; adopts v1 instances, keeping their admin, count and funds (ADR-0011 D4). */
@StateContract
public class CounterV2 {

    @Params
    public record Config(byte[] previous) {}

    public record State(byte[] admin, BigInteger count, BigInteger step) {}

    @Init
    public static State open(Config cfg, Ctx ctx) {
        return new State(ctx.caller(), BigInteger.ZERO, BigInteger.ONE);
    }

    @Migrate(from = "params.previous")
    public static State adopt(Config cfg, CounterV1.State old) {
        return new State(old.admin(), old.count(), BigInteger.ONE);
    }

    @Action
    public static State increment(State s) {
        return new State(s.admin(), s.count().add(s.step()), s.step());
    }

    @Action @SignedBy("admin")
    public static State setStep(State s, BigInteger step) {
        return new State(s.admin(), s.count(), step);
    }

    @Destroy @SignedBy("admin")
    public static JulcList<Payout> close(State s, Ctx ctx) {
        return Payouts.of(s.admin(), ctx.balance());
    }
}
