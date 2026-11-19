package org.julclang.state.examples.guild;

import org.julclang.core.types.JulcList;
import org.julclang.state.api.Action;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Init;
import org.julclang.state.api.MerkleMap;
import org.julclang.state.api.MerkleMaps;
import org.julclang.state.api.Owned;
import org.julclang.state.api.OwnedAction;
import org.julclang.state.api.OwnedDestroy;
import org.julclang.state.api.OwnedInit;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Require;
import org.julclang.state.api.Shared;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.StateContract;

import java.math.BigInteger;

/**
 * Player vaults (ADR-0012 D2): each vault is an owned object whose inventory, of any size, is a MerkleMap. Its datum
 * holds only the root; each call's reads and writes are proven to the shared map verifier, keyed by the vault's own
 * input, as an instance's maps are.
 */
@StateContract(maxDatumBytes = 160)
public class Armory {

    public record State(byte[] keeper, boolean open) {}

    /** A player's vault: an inventory (item name -> count) of any size, in a MerkleMap (ADR-0012 D2). */
    @Owned
    public record Vault(byte[] owner, BigInteger kinds, MerkleMap inventory) {}

    @Init
    public static State init(Ctx ctx) {
        return new State(ctx.caller(), true);
    }

    @Action
    @SignedBy("keeper")
    public static State setOpen(State s, boolean open) {
        return new State(s.keeper(), open);
    }

    @OwnedInit
    public static Vault openVault(@Shared State armory, Ctx ctx) {
        Require.that(armory.open(), "the armory is closed");
        return new Vault(ctx.caller(), BigInteger.ZERO, MerkleMaps.empty());
    }

    @OwnedAction
    @SignedBy("owner")
    public static Vault stash(Vault v, byte[] item, BigInteger count) {
        Require.that(count.signum() > 0, "stash a positive count");
        BigInteger have = MerkleMaps.getInt(v.inventory(), item, BigInteger.ZERO);
        BigInteger kinds = have.signum() == 0 ? v.kinds().add(BigInteger.ONE) : v.kinds();
        return new Vault(v.owner(), kinds, MerkleMaps.putInt(v.inventory(), item, have.add(count)));
    }

    @OwnedAction
    @SignedBy("owner")
    public static Vault take(Vault v, byte[] item, BigInteger count) {
        BigInteger have = MerkleMaps.getInt(v.inventory(), item, BigInteger.ZERO);
        Require.that(count.signum() > 0 && count.compareTo(have) <= 0, "not that many in the vault");
        BigInteger left = have.subtract(count);
        if (left.signum() == 0) {
            return new Vault(v.owner(), v.kinds().subtract(BigInteger.ONE), MerkleMaps.remove(v.inventory(), item));
        }
        return new Vault(v.owner(), v.kinds(), MerkleMaps.putInt(v.inventory(), item, left));
    }

    /** Closes an empty vault (no @Shared: it works whatever the armory's state). */
    @OwnedDestroy
    @SignedBy("owner")
    public static JulcList<Payout> closeVault(Vault v, Ctx ctx) {
        Require.that(v.kinds().signum() == 0, "empty the vault first");
        return Payouts.of(v.owner(), ctx.balance());
    }
}
