package org.julclang.state.examples.guild;

import org.julclang.core.types.JulcList;
import org.julclang.state.api.Action;
import org.julclang.state.api.Admits;
import org.julclang.state.api.Ctx;
import org.julclang.state.api.Init;
import org.julclang.state.api.Into;
import org.julclang.state.api.Invariant;
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
import org.julclang.state.api.View;

import java.math.BigInteger;

/**
 * A game guild (ADR-0012) with two kinds of owned objects: heroes and items. Each object is its own UTxO, typed by its
 * datum's constructor, so a hero's methods never run on an item. A hero can move to another guild: the destination
 * admits it in the same transaction ({@link #admit}), and the source must be open for it to leave. Vaults, whose
 * inventories are MerkleMaps, are a separate contract ({@link Armory}) so that each script stays well under 16 KB.
 */
@StateContract(maxDatumBytes = 160)
public class Guild {

    /** The guild: its master, whether it is open, how many heroes it admitted, and the level a hero needs to join. */
    public record State(byte[] master, boolean open, BigInteger arrivals, BigInteger minLevel) {}

    @Owned
    public record Hero(byte[] owner, BigInteger level) {}

    @Owned
    public record Item(byte[] owner, BigInteger power) {}

    @Init
    public static State init(BigInteger minLevel, Ctx ctx) {
        Require.that(minLevel.signum() > 0, "the minimum level must be positive");
        return new State(ctx.caller(), true, BigInteger.ZERO, minLevel);
    }

    @Action
    @SignedBy("master")
    public static State setOpen(State s, boolean open) {
        return new State(s.master(), open, s.arrivals(), s.minLevel());
    }

    /**
     * Admits a hero moving in from another guild (the other half of {@link #transfer}). It sees the arriving hero, read
     * on-chain from the hero's own input, and admits only heroes of at least the guild's level.
     */
    @Action
    @Admits("Hero")
    public static State admit(State s, byte[] heroId, Hero hero, Ctx ctx) {
        Require.that(s.open(), "the guild is closed");
        Require.that(hero.level().compareTo(s.minLevel()) >= 0, "the hero's level is too low for this guild");
        return new State(s.master(), s.open(), s.arrivals().add(BigInteger.ONE), s.minLevel());
    }

    /** Admits an item carried in from another guild (the other half of {@link #carry}); items do not count. */
    @Action
    @Admits("Item")
    public static State admitItem(State s, byte[] item, Ctx ctx) {
        Require.that(s.open(), "the guild is closed");
        return s;
    }

    // ---- heroes

    @OwnedInit
    public static Hero recruit(@Shared State guild, Ctx ctx) {
        Require.that(guild.open(), "the guild is closed");
        return new Hero(ctx.caller(), BigInteger.ONE);
    }

    @OwnedAction
    @SignedBy("owner")
    public static Hero levelUp(Hero h) {
        return new Hero(h.owner(), h.level().add(BigInteger.ONE));
    }

    /** Moves the hero to {@code destination}, which must admit it; only from an open guild, and only from level 2. */
    @OwnedAction
    @SignedBy("owner")
    public static Hero transfer(Hero h, @Shared State source, @Into State destination, Ctx ctx) {
        Require.that(source.open(), "the source guild is closed");
        Require.that(h.level().compareTo(BigInteger.TWO) >= 0, "a hero moves from level 2");
        return new Hero(h.owner(), h.level());
    }

    @OwnedDestroy
    @SignedBy("owner")
    public static JulcList<Payout> retire(Hero h, Ctx ctx) {
        return Payouts.of(h.owner(), ctx.balance());
    }

    // ---- items

    @OwnedInit
    public static Item forge(@Shared State guild, BigInteger power, Ctx ctx) {
        Require.that(guild.open(), "the guild is closed");
        Require.that(power.signum() > 0 && power.compareTo(BigInteger.TEN) <= 0, "a new item has power 1 to 10");
        return new Item(ctx.caller(), power);
    }

    @OwnedAction
    @SignedBy("owner")
    public static Item enchant(Item i, BigInteger by) {
        Require.that(by.signum() > 0, "enchant by a positive amount");
        return new Item(i.owner(), i.power().add(by));
    }

    /** Carries the item to {@code destination}, which must admit it with {@link #admitItem}. */
    @OwnedAction
    @SignedBy("owner")
    public static Item carry(Item i, @Into State destination, Ctx ctx) {
        return i;
    }

    @OwnedDestroy
    @SignedBy("owner")
    public static JulcList<Payout> scrap(Item i, Ctx ctx) {
        return Payouts.of(i.owner(), ctx.balance());
    }

    // ---- invariants, by type

    @Invariant
    public static boolean heroLevel(Hero h) {
        return h.level().signum() > 0;
    }

    @Invariant
    public static boolean itemPower(Item i) {
        return i.power().compareTo(BigInteger.valueOf(100)) <= 0;
    }

    @View
    public static boolean open(State s) {
        return s.open();
    }
}
