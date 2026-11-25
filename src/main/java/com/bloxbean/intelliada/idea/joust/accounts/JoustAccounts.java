package com.bloxbean.intelliada.idea.joust.accounts;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressType;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.account.service.AccountService;
import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity;
import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity.NetworkClass;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ADR-005 D13a (M3.2): the run panel's account store, separate from IntelliAda's legacy accounts.
 * <ul>
 *   <li>Each account has <b>one network class</b>; a session offers only accounts of its bound network's class, through
 *   {@link #forSession} (I12).</li>
 *   <li><b>Devnet accounts are only created fresh</b> (or are the genesis constants), never imported.</li>
 *   <li>Testnet and mainnet accounts may be created or imported, for that class only. A key already held, under any
 *   class here or as a legacy IntelliAda account, is refused by its payment-key fingerprint, never by comparing
 *   phrases. A legacy account comes in only by {@link #migrateLegacy}, as testnet, and leaves the legacy store.</li>
 *   <li>Phrases live in IntelliJ's {@code PasswordSafe}, which must keep them across restarts: a memory-only safe is
 *   refused, and every write is read back. The persisted state holds no secret and is validated against this store's
 *   invariants on load. A phrase is returned only by {@link #revealMnemonic}, after it re-derives the entry's key (I10).</li>
 * </ul>
 * Every method that touches the safe or derives keys runs off the EDT (callers use a background task).
 */
@State(name = "IntelliAdaJoustAccounts", storages = @Storage(value = "intelliada-joust-accounts.xml", roamingType = RoamingType.DISABLED))
public final class JoustAccounts implements PersistentStateComponent<JoustAccounts.StateBean> {

    /** DevKit's and Yano's genesis mnemonic (JOUST examples/devkit DevKit.DEFAULT_MNEMONIC; Yano's devnet fixtures). */
    static final String DEVNET_GENESIS_MNEMONIC = "test test test test test test test test test test test test test test test test test"
            + " test test test test test test sauce";
    /** The funded genesis accounts: DevKit funds accounts m/1852'/1815'/i'/0/0 for i = 0..19 (M32-CDX-4, M32-DES-1). */
    static final int GENESIS_ACCOUNTS = 20;
    static final String SERVICE = "IntelliAda JOUST account";
    static final int MAX_NAME = 100;
    private static final Pattern UUID_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Set<Integer> PHRASE_WORDS = Set.of(12, 15, 18, 21, 24);

    /** Where an account came from. */
    public enum Origin { FRESH, IMPORTED, MIGRATED, GENESIS }

    /** One account's metadata (persisted; no secret). */
    public static final class Entry {
        public String id;
        public String name;
        public String networkClass;
        public String address;
        /** Hex Blake2b-224 of the payment verification key: the payment key hash, the same on every network. */
        public String fingerprint;
        public String origin;
        /** False until a later IDE session read its phrase back from the safe (a read in the writing session may be cached). */
        public boolean confirmed;
        /** The IDE session that stored it (to tell a later session's confirming read from a cached one). */
        public String createdIn;

        public NetworkClass cls() {
            return NetworkClass.valueOf(networkClass);
        }

        Entry copy() {
            Entry e = new Entry();
            e.id = id;
            e.name = name;
            e.networkClass = networkClass;
            e.address = address;
            e.fingerprint = fingerprint;
            e.origin = origin;
            e.confirmed = confirmed;
            e.createdIn = createdIn;
            return e;
        }

        /** Whether its phrase is not yet known to survive a restart (never for the public genesis or worthless devnet keys). */
        public boolean unconfirmed() {
            return !confirmed && !"GENESIS".equals(origin) && !"DEVNET".equals(networkClass);
        }
    }

    /** The persisted state: entries only. */
    public static final class StateBean {
        public List<Entry> accounts = new ArrayList<>();
    }

    /** A refusal, shown to the user as plain text. */
    public static final class Refused extends Exception {
        public Refused(String why) {
            super(why, null, false, false);
        }
    }

    /** The credential store (IntelliJ's PasswordSafe; replaceable in tests). */
    interface Safe {
        boolean memoryOnly();

        void set(CredentialAttributes a, @Nullable Credentials c);

        @Nullable Credentials get(CredentialAttributes a);
    }

    static final Safe PASSWORD_SAFE = new Safe() {
        @Override
        public boolean memoryOnly() {
            return PasswordSafe.getInstance().isMemoryOnly();
        }

        @Override
        public void set(CredentialAttributes a, @Nullable Credentials c) {
            PasswordSafe.getInstance().set(a, c);
        }

        @Override
        public @Nullable Credentials get(CredentialAttributes a) {
            return PasswordSafe.getInstance().get(a);
        }
    };

    /** This IDE session (a write and a read in one session prove nothing about durability; M32-CDX-12). */
    static final String PROCESS = UUID.randomUUID().toString();
    /** The held payment keys, published after every change: the legacy store's filter reads it without the lock. */
    private volatile Set<String> held = Set.of();

    private StateBean state = new StateBean();
    private Safe safe = PASSWORD_SAFE;
    /** Mainnet accounts are created or imported only once M5's mainnet opt-in exists (D14a; L38). */
    static volatile boolean mainnetEnabled = false;

    static void mainnetForTests(boolean on) {
        mainnetEnabled = on;
    }

    private static void mainnetGate(NetworkClass cls) throws Refused {
        if (cls == NetworkClass.MAINNET && !mainnetEnabled) {
            throw new Refused("mainnet accounts come with IntelliAda's mainnet support (its own opt-in); not in this version");
        }
    }

    public static JoustAccounts getInstance() {
        return ApplicationManager.getApplication().getService(JoustAccounts.class);
    }

    void safeForTests(Safe s) {
        safe = s;
    }

    @Override
    public synchronized @NotNull StateBean getState() {
        // A copy taken under the lock: a settings save never serializes a list being changed (M32-SEC-8).
        StateBean copy = new StateBean();
        for (Entry e : state.accounts) copy.accounts.add(e.copy());
        return copy;
    }

    /**
     * Validated as untrusted against this store's invariants (M32-CDX-7, -SEC-3, -DES-5): a UUID id and a short name;
     * a known class and origin, never GENESIS; a devnet account only FRESH, a migrated one only TESTNET; a base address
     * of the class's network whose payment key hash is the fingerprint; no genesis key; and no id or fingerprint held
     * twice (every entry of such a group is dropped).
     */
    @Override
    public synchronized void loadState(@NotNull StateBean s) {
        List<Entry> valid = new ArrayList<>();
        Map<String, Integer> ids = new HashMap<>(), fps = new HashMap<>();
        for (Entry e : s.accounts == null ? List.<Entry>of() : s.accounts) {
            if (e == null) continue;
            // Counted over every entry: a malformed copy sharing an id or key is evidence of tampering with both.
            if (e.id != null) ids.merge(e.id, 1, Integer::sum);
            if (e.fingerprint != null) fps.merge(e.fingerprint, 1, Integer::sum);
            if (wellFormed(e)) valid.add(e.copy());
        }
        StateBean clean = new StateBean();
        for (Entry e : valid) if (ids.get(e.id) == 1 && fps.get(e.fingerprint) == 1) clean.accounts.add(e);
        state = clean;
        publish();
    }

    private void publish() {
        Set<String> h = new java.util.HashSet<>();
        for (Entry e : state.accounts) h.add(e.fingerprint);
        held = Set.copyOf(h);
    }

    private static boolean wellFormed(Entry e) {
        try {
            if (e.id == null || !UUID_ID.matcher(e.id).matches() || e.name == null || e.name.length() > MAX_NAME
                    || e.name.chars().anyMatch(Character::isISOControl) || e.address == null || e.address.length() > 200
                    || e.fingerprint == null || !e.fingerprint.matches("[0-9a-f]{56}")
                    || (e.createdIn != null && e.createdIn.length() > 64)) return false;
            NetworkClass cls = NetworkClass.valueOf(e.networkClass);
            Origin origin = Origin.valueOf(e.origin);
            if (origin == Origin.GENESIS) return false;
            if (cls == NetworkClass.DEVNET && origin != Origin.FRESH) return false;
            if (origin == Origin.MIGRATED && cls != NetworkClass.TESTNET) return false;
            if (genesisFingerprints().contains(e.fingerprint)) return false;
            return e.fingerprint.equals(paymentKeyHash(e.address, cls));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** The payment key hash of a base address of {@code cls}'s network, or null. */
    static @Nullable String paymentKeyHash(String address, NetworkClass cls) {
        String prefix = cls == NetworkClass.MAINNET ? "addr1" : "addr_test1";
        if (!address.startsWith(prefix)) return null;
        try {
            Address a = new Address(address);
            byte[] b = a.getBytes();
            // The decoded header, not only the prefix: its network id, and a base address's exact length (M32-CDX-19).
            int network = b[0] & 0x0f;
            if (network != (cls == NetworkClass.MAINNET ? 1 : 0) || b.length != 57) return null;
            if (a.getAddressType() != AddressType.Base || !a.isPubKeyHashInPaymentPart()) return null;
            return a.getPaymentCredentialHash().map(HexFormat.of()::formatHex).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The accounts a session bound to {@code id} may offer: its class only (I12). The only path for a caller chooser. */
    public List<Entry> forSession(NetworkIdentity.Identity id) {
        return list(id.networkClass());
    }

    /** The accounts of one class (for managing them), the devnet genesis accounts first on a devnet. */
    public synchronized List<Entry> list(NetworkClass cls) {
        List<Entry> out = new ArrayList<>();
        if (cls == NetworkClass.DEVNET) for (Entry g : genesisEntries()) out.add(g.copy());
        for (Entry e : state.accounts) if (e.cls() == cls) out.add(e.copy());
        return out;
    }

    private static volatile List<Entry> genesis;

    /** The funded devnet genesis accounts (IntelliAda constants, never read from the project or the node; D13a). */
    static List<Entry> genesisEntries() {
        List<Entry> g = genesis;
        if (g == null) {
            List<Entry> out = new ArrayList<>();
            for (int i = 0; i < GENESIS_ACCOUNTS; i++) {
                Account a = new Account(Networks.testnet(), DEVNET_GENESIS_MNEMONIC, DerivationPath.createExternalAddressDerivationPathForAccount(i));
                Entry e = new Entry();
                e.id = "genesis-" + i;
                e.name = "Devnet genesis #" + i;
                e.networkClass = NetworkClass.DEVNET.name();
                e.address = a.baseAddress();
                e.fingerprint = fingerprint(a);
                e.origin = Origin.GENESIS.name();
                out.add(e);
            }
            genesis = g = List.copyOf(out);
        }
        return g;
    }

    static Set<String> genesisFingerprints() {
        Set<String> s = new java.util.HashSet<>();
        for (Entry e : genesisEntries()) s.add(e.fingerprint);
        return s;
    }

    public static Entry genesis(int index) {
        return genesisEntries().get(index).copy();
    }

    /** A fresh account of {@code cls}: a new 24-word phrase, kept in the safe. */
    public synchronized Entry createFresh(String name, NetworkClass cls) throws Refused {
        mainnetGate(cls);
        return store(name, cls, new Account(network(cls)), Origin.FRESH, null, null);
    }

    /** Imports {@code mnemonic} as a testnet or mainnet account; refused for devnets, across classes and for legacy keys. */
    public synchronized Entry importMnemonic(String name, NetworkClass cls, String mnemonic) throws Refused {
        if (cls == NetworkClass.DEVNET) {
            throw new Refused("devnet accounts are created fresh, never imported (a fresh key is the only kind known to be worthless elsewhere)");
        }
        mainnetGate(cls);
        Account a = account(cls, mnemonic);
        if (!legacyHolding(fingerprint(a)).isEmpty()) {
            throw new Refused("this key is a legacy IntelliAda account; use \"Migrate a legacy account\" (testnet keys only)");
        }
        return store(name, cls, a, Origin.IMPORTED, null, null);
    }

    /**
     * Copies a legacy IntelliAda testnet account into the store (ADR-005 L39). The legacy store is never written: the
     * copied key is hidden from every legacy chooser by its payment key ({@code AccountService.getAccounts}), so it is
     * selectable under one class only, and the legacy copy is left for the user and #10. Only a testnet base address that
     * is exactly its phrase's first address copies; a key any other legacy entry also holds (a mainnet address of the
     * same phrase, an enterprise variant) is refused as ambiguous. The entry's id is derived from the key, so a retry
     * after an interruption reuses it, and its phrase is verified, or written again from the legacy copy, on every try.
     */
    public synchronized Entry migrateLegacy(CardanoAccount legacy) throws Refused {
        String addr = legacy.getAddress(), phrase = legacy.getMnemonic();
        if (phrase == null || phrase.isBlank()) throw new Refused("this legacy account has no recovery phrase");
        if (addr == null || !addr.startsWith("addr_test1")) {
            throw new Refused("legacy mainnet accounts do not enter the JOUST accounts (they are never offered on mainnet here) "
                    + "until IntelliAda's mainnet support and the legacy-store fix (#10) decide how");
        }
        Account a = account(NetworkClass.TESTNET, phrase);
        String fp = fingerprint(a);
        if (!a.baseAddress().equals(addr) || !fp.equals(paymentKeyHash(addr, NetworkClass.TESTNET))) {
            throw new Refused("this legacy account's address is not the first address of its recovery phrase; it is not migrated");
        }
        List<CardanoAccount> sharing = legacyHolding(fp);
        if (sharing.size() != 1 || !addr.equals(sharing.get(0).getAddress())) {
            throw new Refused("another legacy account holds the same key (for example its mainnet address); it is not migrated");
        }
        if (safe.memoryOnly()) {
            throw new Refused("IntelliJ is set to forget passwords on restart; a copy kept that way would be lost");
        }
        String id = UUID.nameUUIDFromBytes(("intelliada-joust-migrated:" + fp).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        Entry done = null;
        for (Entry e : state.accounts) if (e.fingerprint.equals(fp)) done = e;
        if (done != null) {
            if (!(done.id.equals(id) && done.cls() == NetworkClass.TESTNET && Origin.valueOf(done.origin) == Origin.MIGRATED)) {
                throw new Refused("this key is already held as a " + done.cls().name().toLowerCase() + " account");
            }
            writeVerified(id, NetworkClass.TESTNET, a.mnemonic());   // a retry repairs a missing or different phrase from the legacy copy
            return done.copy();
        }
        return store(legacy.getName(), NetworkClass.TESTNET, a, Origin.MIGRATED, fp, id);
    }

    /**
     * Confirms entries stored in an earlier IDE session whose phrase now reads back as their key: the phrase survived a
     * restart (M32-CDX-12). Run when the Accounts tab opens. Returns the entries still unconfirmed.
     */
    public synchronized List<Entry> confirmPending() {
        List<Entry> still = new ArrayList<>();
        for (Entry e : state.accounts) {
            if (!e.unconfirmed()) continue;
            if (!PROCESS.equals(e.createdIn)) {
                try {
                    revealMnemonic(e.id);
                    e.confirmed = true;
                    continue;
                } catch (Refused ignored) {
                    // not readable: stays unconfirmed
                }
            }
            still.add(e.copy());
        }
        return still;
    }

    /** The mainnet entries kept while mainnet is not offered (named, read-only; M32-CDX-24). */
    public synchronized List<Entry> mainnetEntries() {
        return state.accounts.stream().filter(e -> e.cls() == NetworkClass.MAINNET).map(Entry::copy).toList();
    }

    private static Account account(NetworkClass cls, String mnemonic) throws Refused {
        String m = mnemonic == null ? "" : String.join(" ", mnemonic.trim().split("\\s+"));
        if (!PHRASE_WORDS.contains(m.isEmpty() ? 0 : m.split(" ").length)) {
            throw new Refused("a recovery phrase has 12, 15, 18, 21 or 24 words");
        }
        try {
            return new Account(network(cls), m);
        } catch (RuntimeException e) {
            throw new Refused("not a valid recovery phrase");
        }
    }

    /**
     * Stores an account, refusing a key held anywhere (except {@code migrating}, the legacy key being moved), and only
     * when the safe keeps it: never a memory-only safe, and the phrase read back must be the one written
     * (M32-CDX-1, -SEC-4, -DES-6).
     */
    private Entry store(String name, NetworkClass cls, Account a, Origin origin, @Nullable String migrating, @Nullable String fixedId)
            throws Refused {
        String clean = name == null || name.isBlank() ? "Account" : name.strip();
        if (clean.split("\\s+").length >= 12) throw new Refused("this name looks like a recovery phrase; names are stored in plain text");
        if (clean.length() > MAX_NAME || clean.chars().anyMatch(Character::isISOControl)) throw new Refused("the name is too long or has control characters");
        String fp = fingerprint(a);
        for (Entry e : state.accounts) {
            if (!e.fingerprint.equals(fp)) continue;
            throw new Refused(e.cls() == cls ? "this account is already in the store" : "this key is already held as a "
                    + e.cls().name().toLowerCase() + " account; a key is held under one network class only");
        }
        if (genesisFingerprints().contains(fp)) throw new Refused("this is a public devnet genesis key; it is offered on devnets only");
        if (!fp.equals(migrating) && !legacyHolding(fp).isEmpty()) {
            throw new Refused("this key is a legacy IntelliAda account; use \"Migrate a legacy account\" (testnet keys only)");
        }
        if (safe.memoryOnly()) {
            throw new Refused("IntelliJ is set to forget passwords on restart (Settings | Appearance & Behavior | System Settings | "
                    + "Passwords); a recovery phrase kept that way would be lost");
        }
        Entry e = new Entry();
        e.id = fixedId != null ? fixedId : UUID.randomUUID().toString();
        e.name = clean;
        e.networkClass = cls.name();
        e.address = a.baseAddress();
        e.fingerprint = fp;
        e.origin = origin.name();
        e.createdIn = PROCESS;
        e.confirmed = false;
        writeVerified(e.id, cls, a.mnemonic());
        state.accounts.add(e);
        publish();
        return e.copy();
    }

    /** The credential's user name: the id and the class, so the class is bound to the secret, not only to metadata. */
    static String holder(String id, NetworkClass cls) {
        return id + "|" + cls.name();
    }

    /**
     * Writes a phrase, with its class, and reads it back; a mismatch removes it and refuses. (Durability is confirmed only
     * in a later session; M32-CDX-12.)
     */
    private void writeVerified(String id, NetworkClass cls, String phrase) throws Refused {
        CredentialAttributes attr = attributes(id);
        safe.set(attr, new Credentials(holder(id, cls), phrase));
        Credentials back = safe.get(attr);
        if (back == null || !phrase.equals(back.getPasswordAsString()) || !holder(id, cls).equals(back.getUserName())) {
            safe.set(attr, null);
            throw new Refused("the password safe did not keep the recovery phrase; nothing was stored");
        }
    }

    /** The recovery phrase of {@code id}, for an explicit user request only: re-derived and checked against its entry. */
    public synchronized String revealMnemonic(String id) throws Refused {
        if (id.startsWith("genesis-")) {
            for (Entry g : genesisEntries()) if (g.id.equals(id)) return DEVNET_GENESIS_MNEMONIC;
            throw new Refused("no such account");
        }
        Entry e = state.accounts.stream().filter(x -> x.id.equals(id)).findFirst().orElse(null);
        if (e == null) throw new Refused("no such account");
        Credentials c = safe.get(attributes(id));
        String phrase = c == null ? null : c.getPasswordAsString();
        if (phrase == null) throw new Refused("the recovery phrase is not in the password safe");
        // The class stored with the secret must be the entry's: a re-tagged entry is never served (M32-SEC-11).
        if (!holder(id, e.cls()).equals(c.getUserName())) {
            throw new Refused("the password safe holds this account under another network class; it is not shown");
        }
        if (!fingerprint(account(e.cls(), phrase)).equals(e.fingerprint)) {
            throw new Refused("the password safe holds another key for this account; it is not shown");
        }
        return phrase;
    }

    /** Removes an account and its secret. */
    public synchronized boolean remove(String id) {
        boolean removed = state.accounts.removeIf(e -> e.id.equals(id));
        if (removed) {
            safe.set(attributes(id), null);
            publish();
        }
        return removed;
    }

    /**
     * Whether this store holds the key with payment key hash {@code fingerprint} (the legacy store asks, M32-CDX-2). The
     * public genesis keys are not "held": legacy DevKit workflows may import them.
     */
    public boolean holdsKey(String fingerprint) {
        return held.contains(fingerprint);   // lock-free: the legacy choosers call it on the EDT (M32-CDX-21)
    }

    /** The payment key hash of any key-payment address (base or enterprise, either network), or null. */
    public static @Nullable String anyPaymentKeyHash(String address) {
        try {
            Address a = new Address(address);
            if (!a.isPubKeyHashInPaymentPart()) return null;
            return a.getPaymentCredentialHash().map(HexFormat.of()::formatHex).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The legacy accounts holding the key with this payment key hash (hidden ones included): only entries with a recovery
     * phrase hold a key; a watch-only entry does not (M32-DES-13). An unreadable legacy store refuses rather than counting
     * as one without the key (M32-CDX-18).
     */
    private static List<CardanoAccount> legacyHolding(String fingerprint) throws Refused {
        List<CardanoAccount> all;
        try {
            all = AccountService.getAccountService().getAllAccountsStrict();
        } catch (java.io.IOException e) {
            throw new Refused("the legacy IntelliAda account store could not be read, so this key's ownership is unknown; nothing was stored");
        }
        List<CardanoAccount> out = new ArrayList<>();
        for (CardanoAccount c : all) {
            String a = c.getAddress();
            if (a != null && c.getMnemonic() != null && !c.getMnemonic().isBlank() && fingerprint.equals(anyPaymentKeyHash(a))) out.add(c);
        }
        return out;
    }

    static CredentialAttributes attributes(String id) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName(SERVICE, id), id);
    }

    static Network network(NetworkClass cls) {
        return cls == NetworkClass.MAINNET ? Networks.mainnet() : Networks.testnet();
    }

    /** Blake2b-224 of the account's payment verification key: its payment key hash, the same on every network. */
    static String fingerprint(Account a) {
        return HexFormat.of().formatHex(Blake2bUtil.blake2bHash224(a.publicKeyBytes()));
    }
}
