package org.julclang.state.examples.token;

import org.julclang.core.types.JulcList;
import org.julclang.state.api.Action;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.Init;
import org.julclang.state.api.MerkleMap;
import org.julclang.state.api.MerkleMaps;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Require;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.StateContract;
import org.julclang.state.api.View;

import java.math.BigInteger;

/**
 * An account-model fungible token: balances in a {@link MerkleMap}, so any number of holders fit in a datum of about
 * 100 bytes. Each call proves only the balances it reads and writes (ADR-0002).
 */
@StateContract(maxDatumBytes = 160)
public class Token {

    public record State(byte[] admin, BigInteger supply, MerkleMap balances) {}

    @Init
    public static State create(Ctx ctx) {
        return new State(ctx.caller(), BigInteger.ZERO, MerkleMaps.empty());
    }

    /** The admin creates new tokens for a holder. */
    @Action
    @SignedBy("admin")
    public static State mint(State s, byte[] to, BigInteger amount) {
        Require.that(amount.compareTo(BigInteger.ZERO) > 0, "amount must be positive");
        BigInteger balance = MerkleMaps.getInt(s.balances(), to, BigInteger.ZERO);
        return new State(s.admin(), s.supply().add(amount), MerkleMaps.putInt(s.balances(), to, balance.add(amount)));
    }

    /** A holder sends tokens; an emptied balance is removed from the map. */
    @Action
    public static State transfer(State s, byte[] to, BigInteger amount, Ctx ctx) {
        Require.that(amount.compareTo(BigInteger.ZERO) > 0, "amount must be positive");
        BigInteger fromBalance = MerkleMaps.getInt(s.balances(), ctx.caller(), BigInteger.ZERO);
        Require.that(fromBalance.compareTo(amount) >= 0, "insufficient balance");
        BigInteger left = fromBalance.subtract(amount);
        MerkleMap debited = left.compareTo(BigInteger.ZERO) == 0
                ? MerkleMaps.remove(s.balances(), ctx.caller())
                : MerkleMaps.putInt(s.balances(), ctx.caller(), left);
        BigInteger toBalance = MerkleMaps.getInt(debited, to, BigInteger.ZERO);   // reads the debit on a self-transfer
        return new State(s.admin(), s.supply(), MerkleMaps.putInt(debited, to, toBalance.add(amount)));
    }

    /** A holder destroys some of their tokens. */
    @Action
    public static State burn(State s, BigInteger amount, Ctx ctx) {
        BigInteger balance = MerkleMaps.getInt(s.balances(), ctx.caller(), BigInteger.ZERO);
        Require.that(amount.compareTo(BigInteger.ZERO) > 0 && balance.compareTo(amount) >= 0, "cannot burn that much");
        BigInteger left = balance.subtract(amount);
        MerkleMap updated = left.compareTo(BigInteger.ZERO) == 0
                ? MerkleMaps.remove(s.balances(), ctx.caller())
                : MerkleMaps.putInt(s.balances(), ctx.caller(), left);
        return new State(s.admin(), s.supply().subtract(amount), updated);
    }

    /** The admin closes the token once everything has been burned. */
    @Destroy
    @SignedBy("admin")
    public static JulcList<Payout> close(State s, Ctx ctx) {
        Require.that(s.supply().compareTo(BigInteger.ZERO) == 0 && MerkleMaps.isEmpty(s.balances()),
                "tokens are still in circulation");
        return Payouts.of(s.admin(), ctx.balance());
    }

    @View
    public static BigInteger balanceOf(State s, byte[] holder) {
        return MerkleMaps.getInt(s.balances(), holder, BigInteger.ZERO);
    }

    @View
    public static BigInteger supply(State s) {
        return s.supply();
    }
}
