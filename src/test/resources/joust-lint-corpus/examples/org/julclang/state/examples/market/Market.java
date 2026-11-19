package org.julclang.state.examples.market;

import org.julclang.core.PlutusData;
import org.julclang.core.types.JulcList;
import org.julclang.stdlib.Builtins;
import org.julclang.ledger.Address;
import org.julclang.ledger.Credential;
import org.julclang.ledger.OutputDatum;
import org.julclang.ledger.PubKeyHash;
import org.julclang.ledger.Value;
import org.julclang.state.api.Action;
import org.julclang.state.api.Admits;
import org.julclang.state.api.Assets;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Datum;
import org.julclang.state.api.Funds;
import org.julclang.state.api.Init;
import org.julclang.state.api.Into;
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
import org.julclang.stdlib.lib.ByteStringLib;

import java.math.BigInteger;
import java.util.Optional;

/**
 * An NFT marketplace (ADR-0007). Each listing is an owned object (ADR-0003) that holds the NFT it sells, so listings
 * never contend. A purchase ends the listing: the seller is paid ({@code Payout}), the NFT goes to the buyer and the
 * royalty to the collection's royalty script, with an inline datum naming the NFT ({@code Send}s). The seller can
 * always take the NFT back, whatever the market's state ({@code cancel}, the escape hatch).
 */
@StateContract(maxDatumBytes = 384, assetSlots = 1)
public class Market {

    /** The market: its admin and fee, and where royalties go. */
    public record Config(byte[] admin, BigInteger feeBps, Address royaltyVault, BigInteger royaltyBps, boolean paused) {}

    @Owned
    public record Listing(byte[] seller, byte[] nftPolicy, byte[] nftName, BigInteger price) {}

    public record Listed(Listing state, Value accepted) {}
    public record Sold(JulcList<Payout> payouts, JulcList<Send> sends, Value accepted) {}
    public record Withdrawn(JulcList<Payout> payouts, JulcList<Send> sends) {}

    /** The royalty output's datum: which NFT sold, and for how much. */
    public record Royalty(byte[] nftPolicy, byte[] nftName, BigInteger price) {}

    @Init
    public static Config open(BigInteger feeBps, Address royaltyVault, BigInteger royaltyBps, Ctx ctx) {
        Require.that(feeBps.signum() >= 0 && royaltyBps.signum() >= 0
                && feeBps.add(royaltyBps).compareTo(BigInteger.valueOf(10_000)) < 0, "fees must leave the seller something");
        return new Config(ctx.caller(), feeBps, royaltyVault, royaltyBps, false);
    }

    @Action @SignedBy("admin")
    public static Config setPaused(Config c, boolean paused) {
        return new Config(c.admin(), c.feeBps(), c.royaltyVault(), c.royaltyBps(), paused);
    }

    /** Offers one NFT at {@code price}: the listing takes the NFT in. */
    @OwnedInit @Payable
    public static Listed offer(@Shared Config c, byte[] nftPolicy, byte[] nftName, BigInteger price, Ctx ctx, Funds funds) {
        Require.that(!c.paused(), "the market is paused");
        Require.that(price.compareTo(BigInteger.valueOf(2_000_000)) >= 0, "price below 2 ADA");
        Require.that(ctx.received().signum() == 0, "a listing takes the NFT, not ADA");
        return new Listed(new Listing(ctx.caller(), nftPolicy, nftName, price), Assets.of(nftPolicy, nftName, BigInteger.ONE));
    }

    /** Buys a listing: pays exactly its price, receives the NFT. */
    @OwnedDestroy @Payable
    public static Sold buy(Listing l, @Shared Config c, Ctx ctx, Funds funds) {
        Require.that(!c.paused(), "the market is paused");
        Require.that(ctx.received().compareTo(l.price()) == 0, "pay exactly the price");
        Require.that(!ByteStringLib.equals(ctx.caller(), l.seller()), "the seller cannot buy their own listing");
        BigInteger fee = l.price().multiply(c.feeBps()).divide(BigInteger.valueOf(10_000));
        BigInteger royalty = l.price().multiply(c.royaltyBps()).divide(BigInteger.valueOf(10_000));
        // The seller gets the price less fee and royalty, plus the listing's deposit (ctx.balance()).
        JulcList<Payout> payouts = Payouts.of(l.seller(), l.price().subtract(fee).subtract(royalty).add(ctx.balance()));
        JulcList<Payout> withFee = fee.signum() > 0 ? Payouts.add(payouts, c.admin(), fee) : payouts;
        JulcList<Send> nft = Sends.of(wallet(ctx.caller()), funds.held(), new OutputDatum.NoOutputDatum());
        JulcList<Send> sends = royalty.signum() > 0
                ? Sends.add(nft, c.royaltyVault(), Assets.lovelace(royalty),
                        Datum.inline(royalty(l)))
                : nft;
        return new Sold(withFee, sends, Assets.none());
    }

    /**
     * Moves a listing, NFT included, to another market at a new price (ADR-0012 D3): the destination admits it with
     * {@link #enlist}. The listing's value moves with it, exactly.
     */
    @OwnedAction @SignedBy("seller")
    public static Listing relist(Listing l, @Into Config to, BigInteger price, Ctx ctx) {
        Require.that(price.compareTo(BigInteger.valueOf(2_000_000)) >= 0, "price below 2 ADA");
        return new Listing(l.seller(), l.nftPolicy(), l.nftName(), price);
    }

    /** Admits a listing relisted from another market, unless this market is paused. */
    @Action @Admits
    public static Config enlist(Config c, byte[] listingId, Listing listing, Ctx ctx) {
        Require.that(!c.paused(), "the market is paused");
        return c;
    }

    /** The seller takes the NFT back. Reads no market state, so it works even if the market is paused. */
    @OwnedDestroy @SignedBy("seller")
    public static Withdrawn cancel(Listing l, Ctx ctx, Funds funds) {
        return new Withdrawn(Payouts.of(l.seller(), ctx.balance()),
                Sends.of(wallet(l.seller()), funds.held(), new OutputDatum.NoOutputDatum()));
    }

    /** The royalty datum, {@code Royalty(nftPolicy, nftName, price)} as data (built so it also runs on the JVM). */
    public static PlutusData royalty(Listing l) {
        return Builtins.constrData(0, Builtins.mkCons(Builtins.bData(l.nftPolicy()), Builtins.mkCons(Builtins.bData(l.nftName()),
                Builtins.mkCons(Builtins.iData(l.price()), Builtins.mkNilData()))));
    }

    /** A key's enterprise address (never a fee-paying base address; ADR-0007 D6). */
    static Address wallet(byte[] key) {
        return new Address(new Credential.PubKeyCredential(new PubKeyHash(key)), Optional.empty());
    }
}
