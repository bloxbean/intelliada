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
            return e;
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
    }

    private static boolean wellFormed(Entry e) {
        try {
            if (e.id == null || !UUID_ID.matcher(e.id).matches() || e.name == null || e.name.length() > MAX_NAME
                    || e.name.chars().anyMatch(Character::isISOControl) || e.address == null || e.address.length() > 200
                    || e.fingerprint == null || !e.fingerprint.matches("[0-9a-f]{56}")) return false;
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
        return store(name, cls, new Account(network(cls)), Origin.FRESH, null);
    }

    /** Imports {@code mnemonic} as a testnet or mainnet account; refused for devnets, across classes and for legacy keys. */
    public synchronized Entry importMnemonic(String name, NetworkClass cls, String mnemonic) throws Refused {
        if (cls == NetworkClass.DEVNET) {
            throw new Refused("devnet accounts are created fresh, never imported (a fresh key is the only kind known to be worthless elsewhere)");
        }
        mainnetGate(cls);
        Account a = account(cls, mnemonic);
        if (legacyHolds(fingerprint(a)) != null) {
            throw new Refused("this key is a legacy IntelliAda account; use \"Migrate a legacy account\" (testnet keys only)");
        }
        return store(name, cls, a, Origin.IMPORTED, null);
    }

    /**
     * Moves a legacy IntelliAda account into the store as a testnet account, then removes it from the legacy store
     * (M32-CDX-3, -SEC-1, -SEC-2, -DES-2, -DES-8). Only a testnet ({@code addr_test}) base address whose payment key is
     * the phrase's first key moves; a mainnet account is refused (import it as mainnet). A move interrupted after its
     * entry was stored completes when repeated.
     */
    public synchronized Entry migrateLegacy(CardanoAccount legacy) throws Refused {
        String addr = legacy.getAddress(), phrase = legacy.getMnemonic();
        if (phrase == null || phrase.isBlank()) throw new Refused("this legacy account has no recovery phrase");
        if (addr == null || !addr.startsWith("addr_test1")) {
            throw new Refused("a mainnet account is not migrated as testnet; import it as a mainnet account instead");
        }
        Account a = account(NetworkClass.TESTNET, phrase);
        String fp = fingerprint(a);
        if (!fp.equals(paymentKeyHash(addr, NetworkClass.TESTNET))) {
            throw new Refused("this legacy account's address is not the first address of its recovery phrase; it is not migrated");
        }
        Entry done = null;
        for (Entry e : state.accounts) if (e.fingerprint.equals(fp)) done = e;
        if (done != null && !(done.cls() == NetworkClass.TESTNET && Origin.valueOf(done.origin) == Origin.MIGRATED)) {
            throw new Refused("this key is already held as a " + done.cls().name().toLowerCase() + " account");
        }
        if (safe.memoryOnly()) {
            throw new Refused("IntelliJ is set to forget passwords on restart; the legacy copy is kept (a move would lose the phrase)");
        }
        Entry e = done != null ? done.copy() : store(legacy.getName(), NetworkClass.TESTNET, a, Origin.MIGRATED, fp);
        // The phrase is durably in the safe (read back); now it leaves the legacy store.
        AccountService.getAccountService().removeAccount(legacy);
        return e;
    }

    /**
     * Completes moves an interruption left half done: a migrated entry whose phrase is in the safe, while the legacy
     * store still holds the same key, loses the legacy copy. Run when the Accounts tab opens; never with a memory-only
     * safe. Returns how many moves were completed.
     */
    public synchronized int completePendingMoves() {
        if (safe.memoryOnly()) return 0;
        int n = 0;
        for (Entry e : state.accounts) {
            if (Origin.valueOf(e.origin) != Origin.MIGRATED) continue;
            CardanoAccount legacy = legacyHolds(e.fingerprint);
            if (legacy == null) continue;
            try {
                if (revealMnemonic(e.id).equals(String.join(" ", String.valueOf(legacy.getMnemonic()).trim().split("\\s+")))) {
                    AccountService.getAccountService().removeAccount(legacy);
                    n++;
                }
            } catch (Refused ignored) {
                // the phrase is not verifiably here: the legacy copy stays
            }
        }
        return n;
    }

    /** How many mainnet entries the store keeps (shown as a note while mainnet is not offered). */
    public synchronized int mainnetCount() {
        return (int) state.accounts.stream().filter(e -> e.cls() == NetworkClass.MAINNET).count();
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
    private Entry store(String name, NetworkClass cls, Account a, Origin origin, @Nullable String migrating) throws Refused {
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
        if (!fp.equals(migrating) && legacyHolds(fp) != null) {
            throw new Refused("this key is a legacy IntelliAda account; use \"Migrate a legacy account\" (testnet keys only)");
        }
        if (safe.memoryOnly()) {
            throw new Refused("IntelliJ is set to forget passwords on restart (Settings | Appearance & Behavior | System Settings | "
                    + "Passwords); a recovery phrase kept that way would be lost");
        }
        Entry e = new Entry();
        e.id = UUID.randomUUID().toString();
        e.name = clean;
        e.networkClass = cls.name();
        e.address = a.baseAddress();
        e.fingerprint = fp;
        e.origin = origin.name();
        CredentialAttributes attr = attributes(e.id);
        safe.set(attr, new Credentials(e.id, a.mnemonic()));
        Credentials back = safe.get(attr);
        if (back == null || !a.mnemonic().equals(back.getPasswordAsString())) {
            safe.set(attr, null);
            throw new Refused("the password safe did not keep the recovery phrase; nothing was stored");
        }
        state.accounts.add(e);
        return e.copy();
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
        if (!fingerprint(account(e.cls(), phrase)).equals(e.fingerprint)) {
            throw new Refused("the password safe holds another key for this account; it is not shown");
        }
        return phrase;
    }

    /** Removes an account and its secret. */
    public synchronized boolean remove(String id) {
        boolean removed = state.accounts.removeIf(e -> e.id.equals(id));
        if (removed) safe.set(attributes(id), null);
        return removed;
    }

    /**
     * Whether this store holds the key with payment key hash {@code fingerprint} (the legacy store asks, M32-CDX-2). The
     * public genesis keys are not "held": legacy DevKit workflows may import them.
     */
    public synchronized boolean holdsKey(String fingerprint) {
        for (Entry e : state.accounts) if (e.fingerprint.equals(fingerprint)) return true;
        return false;
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

    /** The legacy account holding the key with this payment key hash, or null. */
    private static @Nullable CardanoAccount legacyHolds(String fingerprint) {
        for (CardanoAccount c : AccountService.getAccountService().getAccounts()) {
            String a = c.getAddress();
            if (a != null && fingerprint.equals(anyPaymentKeyHash(a))) return c;
        }
        return null;
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
