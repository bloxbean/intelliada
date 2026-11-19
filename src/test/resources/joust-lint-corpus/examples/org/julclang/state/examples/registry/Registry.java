package org.julclang.state.examples.registry;

import org.julclang.core.types.JulcList;
import org.julclang.state.api.Action;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Init;
import org.julclang.state.api.Invariant;
import org.julclang.state.api.Owned;
import org.julclang.state.api.OwnedAction;
import org.julclang.state.api.OwnedDestroy;
import org.julclang.state.api.OwnedInit;
import org.julclang.state.api.Payable;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Quorum;
import org.julclang.state.api.Require;
import org.julclang.state.api.Shared;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.StateContract;
import org.julclang.stdlib.lib.ByteStringLib;

import java.math.BigInteger;

/**
 * A registry of keyed objects (ADR-0016): each key is its own UTxO, unique per instance, kept in a sorted on-chain list
 * whose head is created with the instance.
 * <ul>
 *   <li>{@link Ballot}: one ballot per voter, <em>ever</em>. The key is the voter's key hash, which must sign the insert
 *   ({@code signedKey}); the only exit retires the ballot, so its key stays taken.</li>
 *   <li>{@link Entry}: a name registry. A name can be released (removed, freeing the name) or retired (kept for good);
 *   guardians can recover an entry by quorum.</li>
 * </ul>
 * A keyed registry is permanent: it can be neither destroyed nor migrated (ADR-0016 §5, Q5).
 */
@StateContract(maxDatumBytes = 384)
public class Registry {

    public record State(byte[] admin, boolean open) {}

    /** One ballot per voter, ever (only a retiring exit). */
    @Owned(key = "voter", maxKeyBytes = 28, signedKey = true)
    public record Ballot(byte[] voter, BigInteger choice) {}

    /**
     * A registered name: its owner, the lovelace it holds, a tier (0..3), and guardians who can recover it. The
     * guardians' quorum must be reachable (strictly ascending 28-byte keys, 1 <= needed <= their number; ADR-0009).
     */
    @Owned(key = "name", maxKeyBytes = 64)
    public record Entry(byte[] name, byte[] owner, BigInteger stake, BigInteger tier, JulcList<byte[]> guardians,
                        BigInteger needed) {}

    public record Withdrawal(Entry state, JulcList<Payout> payouts) {}

    @Init
    public static State init(Ctx ctx) {
        return new State(ctx.caller(), true);
    }

    @Action
    @SignedBy("admin")
    public static State setOpen(State s, boolean open) {
        return new State(s.admin(), open);
    }

    // ---- ballots

    @OwnedInit
    public static Ballot cast(@Shared State s, byte[] voter, BigInteger choice, Ctx ctx) {
        Require.that(s.open(), "voting is closed");
        Require.that(choice.signum() >= 0 && choice.compareTo(BigInteger.TEN) < 0, "choose 0 to 9");
        return new Ballot(voter, choice);
    }

    @OwnedAction
    @SignedBy("voter")
    public static Ballot change(Ballot b, BigInteger choice) {
        Require.that(choice.signum() >= 0 && choice.compareTo(BigInteger.TEN) < 0, "choose 0 to 9");
        return new Ballot(b.voter(), choice);
    }

    /** The voter leaves with everything but the deposit; the ballot stays retired, so the voter can never vote again. */
    @OwnedDestroy(retire = true)
    @SignedBy("voter")
    public static JulcList<Payout> leave(Ballot b, Ctx ctx) {
        return ctx.balance().signum() > 0 ? Payouts.of(b.voter(), ctx.balance()) : Payouts.none();
    }

    // ---- names

    /** Claims a free name for the caller, staking what the call pays. */
    @OwnedInit
    @Payable
    public static Entry claim(byte[] name, BigInteger tier, JulcList<byte[]> guardians, BigInteger needed, Ctx ctx) {
        return new Entry(name, ctx.caller(), ctx.received(), tier, guardians, needed);
    }

    /** The admin grants a name to someone, while the registry is open; the owner is its only guardian. */
    @OwnedInit
    public static Entry grant(@Shared State s, byte[] name, byte[] owner, Ctx ctx) {
        Require.that(s.open(), "the registry is closed");
        Require.that(ByteStringLib.equals(ctx.caller(), s.admin()), "only the admin grants names");
        JulcList<byte[]> none = JulcList.empty();
        return new Entry(name, owner, BigInteger.ZERO, BigInteger.ZERO, none.prepend(owner), BigInteger.ONE);
    }

    /** Adds to the stake and sets the tier (the tier invariant is checked on-chain on the result). */
    @OwnedAction
    @Payable
    @SignedBy("owner")
    public static Entry upgrade(Entry e, BigInteger tier, Ctx ctx) {
        return new Entry(e.name(), e.owner(), e.stake().add(ctx.received()), tier, e.guardians(), e.needed());
    }

    @OwnedAction
    @SignedBy("owner")
    public static Withdrawal withdraw(Entry e, BigInteger amount, Ctx ctx) {
        Require.that(amount.signum() > 0 && amount.compareTo(e.stake()) <= 0, "withdraw within the stake");
        return new Withdrawal(new Entry(e.name(), e.owner(), e.stake().subtract(amount), e.tier(), e.guardians(),
                e.needed()), Payouts.of(e.owner(), amount));
    }

    /** Guardians hand the entry to a new owner (the old owner can still retire or release first). */
    @OwnedAction
    @Quorum(keys = "guardians", threshold = "needed")
    public static Entry recover(Entry e, byte[] newOwner) {
        return new Entry(e.name(), newOwner, e.stake(), e.tier(), e.guardians(), e.needed());
    }

    /** Releases the name: the entry is removed from the list and the name can be claimed again. */
    @OwnedDestroy
    @SignedBy("owner")
    public static JulcList<Payout> release(Entry e, Ctx ctx) {
        return Payouts.of(e.owner(), ctx.balance());
    }

    /** Retires the name: the owner leaves with the stake, the name stays taken for good. */
    @OwnedDestroy(retire = true)
    @SignedBy("owner")
    public static JulcList<Payout> retire(Entry e, Ctx ctx) {
        return ctx.balance().signum() > 0 ? Payouts.of(e.owner(), ctx.balance()) : Payouts.none();
    }

    @Invariant
    public static boolean tierInRange(Entry e) {
        return e.tier().signum() >= 0 && e.tier().compareTo(BigInteger.valueOf(3)) <= 0;
    }
}
