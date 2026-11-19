package app;

import java.math.BigInteger;
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
import org.julclang.state.api.View;

/**
 * GENERATED from Counter.jsc by julc-state-dsl (ADR-0018, .jsc). Do not edit: change the .jsc file.
 */
// Counter.jsc:11:1
@StateContract(maxDatumBytes = 128)
public class Counter {

    // Counter.jsc:11:1
    public record State(BigInteger count, byte[] owner) {}

    // Counter.jsc:16:3
    @Init
    public static State init(Ctx ctx) {
        return new State(BigInteger.valueOf(0L), ctx.caller());
    }

    // Counter.jsc:21:3
    @Action
    public static State increment(State s__, BigInteger by) {
        // Counter.jsc:22:5
        Require.that((by.compareTo(BigInteger.valueOf(0L)) > 0), "increment must be positive");
        return new State(s__.count().add(by), s__.owner());
    }

    // Counter.jsc:27:3
    @Action
    @SignedBy("owner")
    public static State reset(State s__) {
        return new State(BigInteger.valueOf(0L), s__.owner());
    }

    // Counter.jsc:32:3
    @Destroy
    @SignedBy("owner")
    public static JulcList<Payout> close(State s__, Ctx ctx) {
        // Counter.jsc:33:5
        BigInteger amount__1 = ctx.balance();
        Require.that(amount__1.compareTo(BigInteger.ZERO) >= 0, "#pay:spend2.1");
        JulcList<Payout> payouts__2 = amount__1.compareTo(BigInteger.ZERO) == 0 ? Payouts.none() : Payouts.add(Payouts.none(), s__.owner(), amount__1);
        return payouts__2;
    }

    // Counter.jsc:37:3
    @View
    public static BigInteger count(State s__) {
        return s__.count();
    }

}
