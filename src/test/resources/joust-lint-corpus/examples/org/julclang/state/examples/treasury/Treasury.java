package org.julclang.state.examples.treasury;

import org.julclang.core.types.JulcList;
import org.julclang.ledger.Credential;
import org.julclang.state.api.Action;
import org.julclang.state.api.AuthorizedBy;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.Init;
import org.julclang.state.api.Invariant;
import org.julclang.state.api.Params;
import org.julclang.state.api.Payable;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Quorum;
import org.julclang.state.api.Require;
import org.julclang.state.api.StateContract;

import java.math.BigInteger;

/**
 * A multisig treasury (ADR-0009). A council of keys decides by quorum ({@code @Quorum}); a guardian credential fixed
 * at deploy ({@code @Params}) can freeze it ({@code @AuthorizedBy}), here a script that approves through a zero
 * withdrawal; every state keeps a reachable quorum ({@code @Invariant}); payouts and council changes emit authenticated
 * events.
 */
@StateContract(maxDatumBytes = 640)
public class Treasury {

    @Params
    public record Config(Credential guardian, BigInteger maxPayout) {}

    public record State(JulcList<byte[]> council, BigInteger quorum, boolean frozen) {}

    public sealed interface Event permits Paid, CouncilChanged, Frozen {}
    public record Paid(byte[] to, BigInteger amount) implements Event {}
    public record CouncilChanged(BigInteger members, BigInteger quorum) implements Event {}
    public record Frozen(boolean frozen) implements Event {}

    public record Spent(State state, JulcList<Payout> payouts, JulcList<Event> events) {}
    public record Changed(State state, JulcList<Event> events) {}

    @Init @Payable
    public static State open(Config cfg, JulcList<byte[]> council, BigInteger quorum, Ctx ctx) {
        return new State(council, quorum, false);
    }

    @Action @Payable
    public static State fund(State s, Ctx ctx) {
        Require.that(ctx.received().signum() > 0, "fund with some ADA");
        return s;
    }

    /** Pays {@code amount} to {@code to}; needs a quorum of the council. */
    @Action @Quorum(keys = "council", threshold = "quorum")
    public static Spent pay(State s, Config cfg, byte[] to, BigInteger amount, Ctx ctx) {
        Require.that(!s.frozen(), "the treasury is frozen");
        Require.that(amount.signum() > 0 && amount.compareTo(cfg.maxPayout()) <= 0, "amount out of range");
        Require.that(amount.compareTo(ctx.balance()) <= 0, "not enough funds");
        JulcList<Event> none = JulcList.empty();
        return new Spent(s, Payouts.of(to, amount), none.prepend(new Paid(to, amount)));
    }

    /** Replaces the council; needs a quorum of the current one. The new council must keep a reachable quorum. */
    @Action @Quorum(keys = "council", threshold = "quorum")
    public static Changed rotate(State s, JulcList<byte[]> council, BigInteger quorum) {
        JulcList<Event> none = JulcList.empty();
        return new Changed(new State(council, quorum, s.frozen()),
                none.prepend(new CouncilChanged(BigInteger.valueOf(council.size()), quorum)));
    }

    /** The guardian freezes or unfreezes the treasury. */
    @Action @AuthorizedBy("params.guardian")
    public static Changed freeze(State s, boolean frozen) {
        JulcList<Event> none = JulcList.empty();
        return new Changed(new State(s.council(), s.quorum(), frozen), none.prepend(new Frozen(frozen)));
    }

    /** Ends the treasury, paying everything to {@code to}; needs a quorum. */
    @Destroy @Quorum(keys = "council", threshold = "quorum")
    public static JulcList<Payout> close(State s, byte[] to, Ctx ctx) {
        return Payouts.of(to, ctx.balance());
    }

    /** A quorum the council can reach (the generated check also requires distinct, sorted 28-byte keys). */
    @Invariant
    public static boolean reachable(State s) {
        return s.quorum().signum() > 0 && s.quorum().compareTo(BigInteger.valueOf(s.council().size())) <= 0;
    }
}
