package org.julclang.state.examples.crowdfund;

import org.julclang.core.types.JulcList;
import org.julclang.core.types.JulcMap;
import org.julclang.state.api.Action;
import org.julclang.state.api.Clock;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.Init;
import org.julclang.state.api.Payable;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Require;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.SortedMaps;
import org.julclang.state.api.StateContract;
import org.julclang.state.api.View;

import java.math.BigInteger;

/**
 * A crowdfunding campaign. Backers contribute before the deadline. After it, if the goal was reached the creator
 * claims everything; if not, each backer takes their contribution back, and the creator closes the empty campaign.
 * <p>
 * Capacity: the state holds one entry per backer and is capped by {@code maxDatumBytes} (about 20 backers here). The
 * minimum contribution makes filling it expensive; refunds and claims always shrink the state, so they never fail on
 * size (ADR-0001 §10). The backers map is kept in canonical key order with {@link SortedMaps}, so the datum the
 * client writes (canonical CBOR) is exactly the one the validator recomputes.
 */
@StateContract(maxDatumBytes = 1024)
public class Crowdfund {

    public record State(byte[] creator, BigInteger goal, BigInteger deadline, BigInteger minContribution,
                        BigInteger raised, JulcMap<byte[], BigInteger> backers) {}

    public record Result(State state, JulcList<Payout> payouts) {}

    @Init
    public static State launch(BigInteger goal, BigInteger deadline, BigInteger minContribution, Ctx ctx) {
        Require.that(goal.compareTo(BigInteger.ZERO) > 0, "goal must be positive");
        Require.that(minContribution.compareTo(BigInteger.valueOf(2_000_000)) >= 0, "minimum contribution is at least 2 ADA");
        Require.that(Clock.before(ctx, deadline), "deadline must be in the future");
        JulcMap<byte[], BigInteger> none = JulcMap.empty();
        return new State(ctx.caller(), goal, deadline, minContribution, BigInteger.ZERO, none);
    }

    @Action
    @Payable
    public static State contribute(State s, Ctx ctx) {
        Require.that(Clock.before(ctx, s.deadline()), "the campaign has ended");
        Require.that(ctx.received().compareTo(s.minContribution()) >= 0, "below the minimum contribution");
        BigInteger before = SortedMaps.getOr(s.backers(), ctx.caller(), BigInteger.ZERO);
        JulcMap<byte[], BigInteger> backers = SortedMaps.put(s.backers(), ctx.caller(), before.add(ctx.received()));
        return new State(s.creator(), s.goal(), s.deadline(), s.minContribution(), s.raised().add(ctx.received()), backers);
    }

    /** The goal was missed: a backer takes their contribution back. */
    @Action
    public static Result refund(State s, Ctx ctx) {
        Require.that(Clock.after(ctx, s.deadline()), "the campaign is still running");
        Require.that(s.raised().compareTo(s.goal()) < 0, "the goal was reached: no refunds");
        Require.that(s.backers().containsKey(ctx.caller()), "nothing to refund");
        BigInteger amount = s.backers().get(ctx.caller());
        State next = new State(s.creator(), s.goal(), s.deadline(), s.minContribution(), s.raised().subtract(amount),
                SortedMaps.remove(s.backers(), ctx.caller()));
        return new Result(next, Payouts.of(ctx.caller(), amount));
    }

    /** The goal was reached: the creator takes everything. */
    @Destroy
    @SignedBy("creator")
    public static JulcList<Payout> claim(State s, Ctx ctx) {
        Require.that(Clock.after(ctx, s.deadline()), "the campaign is still running");
        Require.that(s.raised().compareTo(s.goal()) >= 0, "the goal was not reached");
        return Payouts.of(s.creator(), ctx.balance());
    }

    /** The goal was missed and every backer has been refunded: the creator closes the campaign. */
    @Destroy
    @SignedBy("creator")
    public static JulcList<Payout> close(State s, Ctx ctx) {
        Require.that(Clock.after(ctx, s.deadline()), "the campaign is still running");
        Require.that(s.backers().isEmpty(), "backers still have funds in the campaign");
        return Payouts.of(s.creator(), ctx.balance());
    }

    @View
    public static BigInteger raised(State s) {
        return s.raised();
    }

    @View
    public static BigInteger contributionOf(State s, byte[] backer) {
        return SortedMaps.getOr(s.backers(), backer, BigInteger.ZERO);
    }
}
