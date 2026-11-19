package org.julclang.state.examples.staking;

import org.julclang.core.types.JulcList;
import org.julclang.ledger.Address;
import org.julclang.ledger.Credential;
import org.julclang.ledger.GovernanceAction;
import org.julclang.ledger.GovernanceActionId;
import org.julclang.ledger.OutputDatum;
import org.julclang.ledger.ProposalProcedure;
import org.julclang.ledger.PubKeyHash;
import org.julclang.ledger.TxCert;
import org.julclang.ledger.Vote;
import org.julclang.ledger.Voter;
import org.julclang.state.api.Action;
import org.julclang.state.api.Assets;
import org.julclang.state.api.Certify;
import org.julclang.state.api.Certs;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Destroy;
import org.julclang.state.api.Init;
import org.julclang.state.api.Params;
import org.julclang.state.api.Payable;
import org.julclang.state.api.Payout;
import org.julclang.state.api.Payouts;
import org.julclang.state.api.Propose;
import org.julclang.state.api.Quorum;
import org.julclang.state.api.Require;
import org.julclang.state.api.Send;
import org.julclang.state.api.Sends;
import org.julclang.state.api.StateContract;
import org.julclang.state.api.Votes;
import org.julclang.state.api.Withdraw;

import java.math.BigInteger;
import java.util.Optional;

/**
 * A council-governed vault whose funds are staked (ADR-0010). Its state UTxOs sit at base addresses staked to its own
 * script, so the funds earn rewards. The council (a quorum fixed at deploy) delegates only to the pool in the params,
 * withdraws the rewards only to the payee, and votes as a script DRep. A guardrail refuses treasury withdrawals over a
 * cap.
 */
@StateContract(stakeSelf = true)
public class StakedVault {

    @Params
    public record Config(JulcList<byte[]> council, BigInteger quorum, byte[] pool, byte[] payee, BigInteger cap) {}

    public record State(BigInteger deposits) {}

    @Init @Payable
    public static State open(Config cfg, Ctx ctx) {
        return new State(ctx.received());
    }

    @Action @Payable
    public static State deposit(State s, Ctx ctx) {
        Require.that(ctx.received().signum() > 0, "deposit some ADA");
        return new State(s.deposits().add(ctx.received()));
    }

    @Destroy @Quorum(keys = "params.council", threshold = "params.quorum")
    public static JulcList<Payout> close(State s, Config cfg, Ctx ctx) {
        return Payouts.of(cfg.payee(), ctx.balance());
    }

    /** Rewards go only to the payee. */
    @Withdraw @Quorum(keys = "params.council", threshold = "params.quorum")
    public static JulcList<Send> collect(Config cfg, BigInteger amount) {
        return amount.signum() == 0 ? Sends.none()
                : Sends.of(wallet(cfg.payee()), Assets.lovelace(amount), new OutputDatum.NoOutputDatum());
    }

    /** Registration, and delegation of stake only (no vote delegation) to the pool in the params; nothing else. */
    @Certify @Quorum(keys = "params.council", threshold = "params.quorum")
    public static boolean certificate(Config cfg, TxCert cert) {
        return Certs.isRegistration(cert) || Certs.delegatesOnlyTo(cert, cfg.pool()) || Certs.isDRepLifecycle(cert);
    }

    /** The council votes as the script DRep: a quorum signing is the decision. */
    @Votes @Quorum(keys = "params.council", threshold = "params.quorum")
    public static boolean vote(Config cfg, Voter voter, GovernanceActionId action, Vote vote) {
        return true;
    }

    /** Guardrail: no treasury withdrawal above the cap in total. */
    @Propose
    public static boolean proposal(Config cfg, ProposalProcedure p) {
        return switch (p.governanceAction()) {
            case GovernanceAction.TreasuryWithdrawals t -> totalOf(t).compareTo(cfg.cap()) <= 0;
            default -> true;
        };
    }

    static BigInteger totalOf(GovernanceAction.TreasuryWithdrawals t) {
        BigInteger sum = BigInteger.ZERO;
        for (var k : t.withdrawals().keys()) {
            sum = sum.add(t.withdrawals().get(k));
        }
        return sum;
    }

    static Address wallet(byte[] key) {
        return new Address(new Credential.PubKeyCredential(new PubKeyHash(key)), Optional.empty());
    }
}
