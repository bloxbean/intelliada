package org.julclang.state.examples.dex;

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
import org.julclang.state.api.Payable;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Require;
import org.julclang.state.api.Send;
import org.julclang.state.api.Sends;
import org.julclang.state.api.SignedBy;
import org.julclang.state.api.StateContract;
import org.julclang.state.api.View;
import org.julclang.stdlib.lib.CryptoLib;

import java.math.BigInteger;
import java.util.Optional;

/**
 * A constant-product AMM for one ADA/token pair (ADR-0007). The pool's reserves are what its UTxO holds: lovelace
 * above the deposit ({@code ctx.balance()}) and the token ({@code funds.held()}), so the state cannot drift from the
 * funds. Liquidity providers get LP tokens minted by this contract's own policy, named {@code blake2b_224(pool id)}:
 * one name per pool, so one pool's LP tokens can never redeem another's reserves. Swaps pay a 0.3 % fee to the pool.
 */
@StateContract(maxDatumBytes = 256, assetSlots = 2)
public class Dex {

    public record Pool(byte[] admin, byte[] tokenPolicy, byte[] tokenName, byte[] lpName, BigInteger lpSupply) {}

    public record Opened(Pool state) {}
    public record Moved(Pool state, JulcList<Payout> payouts, JulcList<Send> sends, Value mint, Value accepted) {}
    public record Closed(JulcList<Payout> payouts, JulcList<Send> sends) {}

    /** Opens an empty pool for {@code (tokenPolicy, tokenName)}; its LP token is named after the pool id. */
    @Init
    public static Pool open(byte[] tokenPolicy, byte[] tokenName, Ctx ctx, Funds funds) {
        Require.that(tokenPolicy.length == 28, "token policy must be 28 bytes");
        return new Pool(ctx.caller(), tokenPolicy, tokenName, CryptoLib.blake2b_224(funds.self()), BigInteger.ZERO);
    }

    /** Adds {@code ctx.received()} lovelace and {@code tokenIn} tokens; mints LP tokens to the caller. */
    @Action @Payable
    public static Moved addLiquidity(Pool p, BigInteger tokenIn, Ctx ctx, Funds funds) {
        BigInteger adaIn = ctx.received();
        Require.that(adaIn.signum() > 0 && tokenIn.signum() > 0, "add both ADA and tokens");
        BigInteger reserveAda = ctx.balance();
        BigInteger reserveToken = Assets.quantity(funds.held(), p.tokenPolicy(), p.tokenName());
        BigInteger minted = p.lpSupply().signum() == 0 ? adaIn
                : adaIn.multiply(p.lpSupply()).divide(reserveAda).min(tokenIn.multiply(p.lpSupply()).divide(reserveToken));
        Require.that(minted.signum() > 0, "deposit too small");
        Value lp = Assets.of(funds.policy(), p.lpName(), minted);
        Pool next = new Pool(p.admin(), p.tokenPolicy(), p.tokenName(), p.lpName(), p.lpSupply().add(minted));
        return new Moved(next, Payouts.none(), Sends.of(wallet(ctx.caller()), lp, new OutputDatum.NoOutputDatum()), lp,
                Assets.of(p.tokenPolicy(), p.tokenName(), tokenIn));
    }

    /** Burns {@code lp} LP tokens; pays the provider its share of both reserves. */
    @Action @Payable
    public static Moved removeLiquidity(Pool p, BigInteger lp, Ctx ctx, Funds funds) {
        Require.that(lp.signum() > 0 && lp.compareTo(p.lpSupply()) <= 0, "bad LP amount");
        BigInteger adaOut = ctx.balance().multiply(lp).divide(p.lpSupply());
        BigInteger tokenOut = Assets.quantity(funds.held(), p.tokenPolicy(), p.tokenName()).multiply(lp).divide(p.lpSupply());
        Value lpTokens = Assets.of(funds.policy(), p.lpName(), lp);
        Pool next = new Pool(p.admin(), p.tokenPolicy(), p.tokenName(), p.lpName(), p.lpSupply().subtract(lp));
        return new Moved(next, Payouts.of(ctx.caller(), adaOut),
                Sends.of(wallet(ctx.caller()), Assets.of(p.tokenPolicy(), p.tokenName(), tokenOut), new OutputDatum.NoOutputDatum()),
                Assets.subtract(Assets.none(), lpTokens), lpTokens);
    }

    /** Swaps {@code ctx.received()} lovelace for at least {@code minOut} tokens. */
    @Action @Payable
    public static Moved swapAdaForToken(Pool p, BigInteger minOut, Ctx ctx, Funds funds) {
        BigInteger out = amountOut(ctx.received(), ctx.balance(), Assets.quantity(funds.held(), p.tokenPolicy(), p.tokenName()));
        Require.that(out.signum() > 0 && out.compareTo(minOut) >= 0, "slippage: output below minOut");
        return new Moved(p, Payouts.none(),
                Sends.of(wallet(ctx.caller()), Assets.of(p.tokenPolicy(), p.tokenName(), out), new OutputDatum.NoOutputDatum()),
                Assets.none(), Assets.none());
    }

    /** Swaps {@code tokenIn} tokens for at least {@code minOut} lovelace. */
    @Action @Payable
    public static Moved swapTokenForAda(Pool p, BigInteger tokenIn, BigInteger minOut, Ctx ctx, Funds funds) {
        Require.that(ctx.received().signum() == 0, "pay tokens, not ADA");
        BigInteger out = amountOut(tokenIn, Assets.quantity(funds.held(), p.tokenPolicy(), p.tokenName()), ctx.balance());
        Require.that(out.signum() > 0 && out.compareTo(minOut) >= 0, "slippage: output below minOut");
        return new Moved(p, Payouts.of(ctx.caller(), out), Sends.none(), Assets.none(),
                Assets.of(p.tokenPolicy(), p.tokenName(), tokenIn));
    }

    /** Closes an empty pool: the admin gets the deposit and any dust. */
    @Destroy @SignedBy("admin")
    public static Closed close(Pool p, Ctx ctx, Funds funds) {
        Require.that(p.lpSupply().signum() == 0, "liquidity is still in the pool");
        JulcList<Send> dust = Assets.isEmpty(funds.held()) ? Sends.none()
                : Sends.of(wallet(p.admin()), funds.held(), new OutputDatum.NoOutputDatum());
        return new Closed(Payouts.of(p.admin(), ctx.balance()), dust);
    }

    /** Output of a swap of {@code in} into reserves {@code (reserveIn, reserveOut)}, after the 0.3 % fee. */
    public static BigInteger amountOut(BigInteger in, BigInteger reserveIn, BigInteger reserveOut) {
        BigInteger inWithFee = in.multiply(BigInteger.valueOf(997));
        return reserveOut.multiply(inWithFee).divide(reserveIn.multiply(BigInteger.valueOf(1000)).add(inWithFee));
    }

    @View
    public static BigInteger lpSupply(Pool p) {
        return p.lpSupply();
    }

    /** The enterprise address of a key: never the caller's fee-paying base address (ADR-0007 D6). */
    static Address wallet(byte[] key) {
        return new Address(new Credential.PubKeyCredential(new PubKeyHash(key)), Optional.empty());
    }
}
