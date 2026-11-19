package org.julclang.state.examples.locker;

import org.julclang.core.types.JulcList;
import org.julclang.ledger.Address;
import org.julclang.ledger.Credential;
import org.julclang.ledger.OutputDatum;
import org.julclang.ledger.PubKeyHash;
import org.julclang.ledger.Value;
import org.julclang.state.api.Action;
import org.julclang.state.api.Assets;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.Funds;
import org.julclang.state.api.Init;
import org.julclang.state.api.MerkleMap;
import org.julclang.state.api.MerkleMaps;
import org.julclang.state.api.Payable;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Require;
import org.julclang.state.api.Send;
import org.julclang.state.api.Sends;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.StateContract;

import java.math.BigInteger;
import java.util.Optional;

/**
 * A token locker (MerkleMaps with native assets): one instance holds a native token for any number of users, and each
 * user's balance sits in a MerkleMap, so the datum stays small. Locking takes the tokens in ({@code accepted}),
 * unlocking sends them back.
 */
@StateContract(assetSlots = 2)
public class Locker {

    public record State(byte[] admin, byte[] tokenPolicy, byte[] tokenName, BigInteger total, MerkleMap balances) {}

    public record Locked(State state, Value accepted) {}

    public record Unlocked(State state, JulcList<Send> sends) {}

    @Init
    public static State init(byte[] tokenPolicy, byte[] tokenName, Ctx ctx) {
        return new State(ctx.caller(), tokenPolicy, tokenName, BigInteger.ZERO, MerkleMaps.empty());
    }

    /** Locks {@code qty} of the token for the caller. */
    @Action
    @Payable
    public static Locked lock(State s, BigInteger qty, Ctx ctx, Funds funds) {
        Require.that(qty.signum() > 0, "lock a positive amount");
        Require.that(ctx.received().signum() == 0, "the locker takes the token, not ADA");
        BigInteger had = MerkleMaps.getInt(s.balances(), ctx.caller(), BigInteger.ZERO);
        return new Locked(new State(s.admin(), s.tokenPolicy(), s.tokenName(), s.total().add(qty),
                MerkleMaps.putInt(s.balances(), ctx.caller(), had.add(qty))), Assets.of(s.tokenPolicy(), s.tokenName(), qty));
    }

    /** Sends {@code qty} of the caller's locked token back to the caller. */
    @Action
    public static Unlocked unlock(State s, BigInteger qty, Ctx ctx) {
        BigInteger had = MerkleMaps.getInt(s.balances(), ctx.caller(), BigInteger.ZERO);
        Require.that(qty.signum() > 0 && qty.compareTo(had) <= 0, "not that many locked");
        BigInteger left = had.subtract(qty);
        MerkleMap balances = left.signum() == 0 ? MerkleMaps.remove(s.balances(), ctx.caller())
                : MerkleMaps.putInt(s.balances(), ctx.caller(), left);
        return new Unlocked(new State(s.admin(), s.tokenPolicy(), s.tokenName(), s.total().subtract(qty), balances),
                Sends.of(wallet(ctx.caller()), Assets.of(s.tokenPolicy(), s.tokenName(), qty), new OutputDatum.NoOutputDatum()));
    }

    /** Closes an empty locker. */
    @Destroy
    @SignedBy("admin")
    public static JulcList<Payout> close(State s, Ctx ctx) {
        Require.that(s.total().signum() == 0, "tokens are still locked");
        return Payouts.of(s.admin(), ctx.balance());
    }

    static Address wallet(byte[] key) {
        return new Address(new Credential.PubKeyCredential(new PubKeyHash(key)), Optional.empty());
    }
}
