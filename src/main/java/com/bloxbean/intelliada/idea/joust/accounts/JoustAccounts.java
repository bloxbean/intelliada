package com.bloxbean.intelliada.idea.joust.accounts;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
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
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * ADR-005 D13a (M3.2): the run panel's account store, separate from IntelliAda's legacy accounts.
 * <ul>
 *   <li>Each account has <b>one network class</b> ({@code devnet}, {@code testnet}, {@code mainnet}); the run panel offers
 *   only accounts of the bound network's class (I12).</li>
 *   <li><b>Devnet accounts are only created fresh</b> (or are the genesis constants), never imported (CDX-20): a fresh key
 *   is the only kind IntelliAda can vouch is worthless elsewhere.</li>
 *   <li>Testnet and mainnet accounts may be created or imported, for that class only. A mnemonic already held under
 *   another class is refused, compared by its payment-key fingerprint, never by the mnemonic itself.</li>
 *   <li>Mnemonics live in IntelliJ's {@code PasswordSafe}; this component's persisted state holds no secret. A mnemonic is
 *   returned only by {@link #revealMnemonic}, for an explicit "Show recovery phrase" (I10).</li>
 * </ul>
 */
@State(name = "IntelliAdaJoustAccounts", storages = @Storage(value = "intelliada-joust-accounts.xml", roamingType = RoamingType.DISABLED))
public final class JoustAccounts implements PersistentStateComponent<JoustAccounts.StateBean> {

    /** DevKit's and Yano's genesis mnemonic (JOUST examples/devkit DevKit.DEFAULT_MNEMONIC; Yano's devnet fixtures). */
    static final String DEVNET_GENESIS_MNEMONIC = "test test test test test test test test test test test test test test test test test"
            + " test test test test test test sauce";
    static final int GENESIS_ACCOUNTS = 10;
    static final String SERVICE = "IntelliAda JOUST account";

    /** Where an account came from. */
    public enum Origin { FRESH, IMPORTED, MIGRATED, GENESIS }

    /** One account's metadata (persisted; no secret). */
    public static final class Entry {
        public String id;
        public String name;
        public String networkClass;
        public String address;
        /** Hex Blake2b-224 of the payment verification key (index 0): the cross-class fingerprint. */
        public String fingerprint;
        public String origin;

        public NetworkClass cls() {
            return NetworkClass.valueOf(networkClass);
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

    private StateBean state = new StateBean();

    public static JoustAccounts getInstance() {
        return ApplicationManager.getApplication().getService(JoustAccounts.class);
    }

    @Override
    public @NotNull StateBean getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull StateBean s) {
        // Validated as untrusted on load: unknown classes or malformed fields are dropped.
        StateBean clean = new StateBean();
        for (Entry e : s.accounts == null ? List.<Entry>of() : s.accounts) {
            try {
                if (e.id == null || e.name == null || e.address == null || e.fingerprint == null) continue;
                NetworkClass.valueOf(e.networkClass);
                Origin.valueOf(e.origin);
                if (!e.fingerprint.matches("[0-9a-f]{56}")) continue;
                clean.accounts.add(e);
            } catch (RuntimeException ignored) {
                // dropped
            }
        }
        state = clean;
    }

    /** The accounts of one class, the devnet genesis accounts first on a devnet. */
    public synchronized List<Entry> list(NetworkClass cls) {
        List<Entry> out = new ArrayList<>();
        if (cls == NetworkClass.DEVNET) {
            for (int i = 0; i < GENESIS_ACCOUNTS; i++) out.add(genesis(i));
        }
        for (Entry e : state.accounts) if (e.cls() == cls) out.add(e);
        return out;
    }

    /** A devnet genesis account (an IntelliAda constant, never read from the project or the node; D13a). */
    public static Entry genesis(int index) {
        Account a = new Account(Networks.testnet(), DEVNET_GENESIS_MNEMONIC, index);
        Entry e = new Entry();
        e.id = "genesis-" + index;
        e.name = "Devnet genesis #" + index;
        e.networkClass = NetworkClass.DEVNET.name();
        e.address = a.baseAddress();
        e.fingerprint = fingerprint(a);
        e.origin = Origin.GENESIS.name();
        return e;
    }

    /** A fresh account of {@code cls}: a new mnemonic, stored in {@code PasswordSafe}. */
    public synchronized Entry createFresh(String name, NetworkClass cls) throws Refused {
        Account a = new Account(network(cls));
        return store(name, cls, a, Origin.FRESH);
    }

    /** Imports {@code mnemonic} as a testnet or mainnet account; refused for devnets and across classes. */
    public synchronized Entry importMnemonic(String name, NetworkClass cls, String mnemonic) throws Refused {
        if (cls == NetworkClass.DEVNET) {
            throw new Refused("devnet accounts are created fresh, never imported (a fresh key is the only kind known to be worthless elsewhere)");
        }
        return store(name, cls, account(cls, mnemonic), Origin.IMPORTED);
    }

    /** Moves a legacy IntelliAda account into the store as a testnet account (never mainnet; never devnet). */
    public synchronized Entry migrateLegacy(String name, String mnemonic) throws Refused {
        return store(name, NetworkClass.TESTNET, account(NetworkClass.TESTNET, mnemonic), Origin.MIGRATED);
    }

    private static Account account(NetworkClass cls, String mnemonic) throws Refused {
        try {
            return new Account(network(cls), mnemonic.trim());
        } catch (RuntimeException e) {
            throw new Refused("not a valid recovery phrase");
        }
    }

    private Entry store(String name, NetworkClass cls, Account a, Origin origin) throws Refused {
        String fp = fingerprint(a);
        for (Entry e : state.accounts) {
            if (!e.fingerprint.equals(fp)) continue;
            throw new Refused(e.cls() == cls ? "this account is already in the store" : "this key is already held as a "
                    + e.cls().name().toLowerCase() + " account; a key is held under one network class only");
        }
        for (int i = 0; i < GENESIS_ACCOUNTS; i++) {
            if (genesis(i).fingerprint.equals(fp)) throw new Refused("this is a public devnet genesis key; it is offered on devnets only");
        }
        Entry e = new Entry();
        e.id = UUID.randomUUID().toString();
        e.name = name == null || name.isBlank() ? "Account" : name.strip();
        e.networkClass = cls.name();
        e.address = a.baseAddress();
        e.fingerprint = fp;
        e.origin = origin.name();
        PasswordSafe.getInstance().set(attributes(e.id), new Credentials(e.id, a.mnemonic()));
        state.accounts.add(e);
        return e;
    }

    /** The recovery phrase of {@code id}, for an explicit user request only; null for unknown ids. */
    public @Nullable String revealMnemonic(String id) {
        if (id.startsWith("genesis-")) return DEVNET_GENESIS_MNEMONIC;
        Credentials c = PasswordSafe.getInstance().get(attributes(id));
        return c == null ? null : c.getPasswordAsString();
    }

    /** Removes an account and its secret. */
    public synchronized boolean remove(String id) {
        boolean removed = state.accounts.removeIf(e -> e.id.equals(id));
        if (removed) PasswordSafe.getInstance().set(attributes(id), null);
        return removed;
    }

    static CredentialAttributes attributes(String id) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName(SERVICE, id), id);
    }

    static Network network(NetworkClass cls) {
        return cls == NetworkClass.MAINNET ? Networks.mainnet() : Networks.testnet();
    }

    /** Blake2b-224 of the account's payment verification key (index 0): the same for a mnemonic on every network. */
    static String fingerprint(Account a) {
        return HexFormat.of().formatHex(Blake2bUtil.blake2bHash224(a.publicKeyBytes()));
    }
}
