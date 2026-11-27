package com.bloxbean.intelliada.idea.joust.journal;

import com.bloxbean.intelliada.idea.joust.host.chain.BlockfrostApi;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Inclusion;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * D17's chain observations through the bound node (D18's Blockfrost-shaped API): the tip from {@code /blocks/latest}
 * and a transaction's inclusion from {@code /txs/{hash}} (404: not on chain). Every value is validated before it is
 * used; anything malformed fails the observation (the pass is retried), never reads as "absent".
 */
public final class ProviderChain implements Settlement.ChainView {

    private static final Pattern HASH32 = Pattern.compile("[0-9a-f]{64}");
    private static final long READ_BYTES = 256 * 1024;
    private final BlockfrostApi api;
    private final Duration deadline;

    public ProviderChain(BlockfrostApi api, Duration deadline) {
        this.api = api;
        this.deadline = deadline;
    }

    @Override
    public Tip tip() throws BlockfrostApi.Failed, InterruptedException {
        JsonNode b = api.get("/blocks/latest", System.nanoTime() + deadline.toNanos(), READ_BYTES);
        if (b == null) throw new BlockfrostApi.Failed("the node has no tip");
        return new Tip(nat(b.get("slot")), nat(b.get("height")), hash(b.get("hash")));
    }

    @Override
    public @Nullable Inclusion inclusion(String txHash) throws BlockfrostApi.Failed, InterruptedException {
        if (!HASH32.matcher(txHash).matches()) throw new IllegalArgumentException("not a transaction hash");
        JsonNode t = api.get("/txs/" + txHash, System.nanoTime() + deadline.toNanos(), READ_BYTES);
        if (t == null) return null;
        if (!txHash.equals(t.path("hash").textValue())) throw new BlockfrostApi.Failed("the node answered another transaction");
        return new Inclusion(nat(t.get("slot")), nat(t.get("block_height")), hash(t.get("block")));
    }

    private static long nat(@Nullable JsonNode n) throws BlockfrostApi.Failed {
        if (n == null || !n.isIntegralNumber() || !n.canConvertToLong() || n.longValue() < 0) {
            throw new BlockfrostApi.Failed("the node's chain point is malformed");
        }
        return n.longValue();
    }

    private static String hash(@Nullable JsonNode n) throws BlockfrostApi.Failed {
        if (n == null || !n.isTextual() || !HASH32.matcher(n.textValue()).matches()) throw new BlockfrostApi.Failed("the node's block hash is malformed");
        return n.textValue();
    }
}
