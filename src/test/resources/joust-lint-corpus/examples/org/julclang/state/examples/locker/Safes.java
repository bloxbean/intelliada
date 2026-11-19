package org.julclang.state.examples.locker;

import org.julclang.core.types.JulcList;
import org.julclang.ledger.Address;
import org.julclang.ledger.Credential;
import org.julclang.ledger.OutputDatum;
import org.julclang.ledger.PubKeyHash;
import org.julclang.ledger.Value;
import org.julclang.state.api.Assets;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Funds;
import org.julclang.state.api.Init;
import org.julclang.state.api.MerkleMap;
import org.julclang.state.api.MerkleMaps;
import org.julclang.state.api.Owned;
import org.julclang.state.api.OwnedAction;
import org.julclang.state.api.OwnedDestroy;
import org.julclang.state.api.OwnedInit;
import org.julclang.state.api.Payable;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Require;
import org.julclang.state.api.Send;
import org.julclang.state.api.Sends;
import org.julclang.state.api.Shared;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.StateContract;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Safes (owned MerkleMaps with native assets): each safe is an owned object holding a native token and a MerkleMap of
 * notes about it. Opening takes the tokens in and writes the first note at creation; emptying sends the tokens back.
 */
@StateContract(assetSlots = 2)
public class Safes {

    public record State(byte[] keeper, boolean open) {}

    @Owned
    public record Safe(byte[] owner, byte[] tokenPolicy, byte[] tokenName, BigInteger quantity, MerkleMap notes) {}

    public record Opened(Safe state, Value accepted) {}

    public record Emptied(JulcList<Payout> payouts, JulcList<Send> sends) {}

    @Init
    public static State init(Ctx ctx) {
        return new State(ctx.caller(), true);
    }

    @OwnedInit
    @Payable
    public static Opened openSafe(@Shared State s, byte[] tokenPolicy, byte[] tokenName, BigInteger qty, Ctx ctx, Funds funds) {
        Require.that(s.open(), "closed");
        Require.that(qty.signum() > 0, "put something in");
        MerkleMap notes = MerkleMaps.putInt(MerkleMaps.empty(), tokenName, qty);   // a write at creation
        return new Opened(new Safe(ctx.caller(), tokenPolicy, tokenName, qty, notes), Assets.of(tokenPolicy, tokenName, qty));
    }

    @OwnedAction
    @SignedBy("owner")
    public static Safe note(Safe s, byte[] key, BigInteger value) {
        return new Safe(s.owner(), s.tokenPolicy(), s.tokenName(), s.quantity(), MerkleMaps.putInt(s.notes(), key, value));
    }

    @OwnedDestroy
    @SignedBy("owner")
    public static Emptied empty(Safe s, Ctx ctx, Funds funds) {
        return new Emptied(Payouts.of(s.owner(), ctx.balance()),
                Sends.of(new Address(new Credential.PubKeyCredential(new PubKeyHash(s.owner())), Optional.empty()), funds.held(),
                        new OutputDatum.NoOutputDatum()));
    }
}
