package com.bloxbean.intelliada.idea.joust.verifier;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.HostSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * ADR-005 D13 "Anchors" (ADR5-SEC-2, -CDX-5, -DES-3, -DES-13, -SEC-12): what IntelliAda derives itself, from the
 * verified snapshot's resources, as JOUST's runtime resolves scripts; the checker's {@code anchors} and the deployment
 * check's reference. Never taken from the host.
 * <ul>
 *   <li>The description is {@code META-INF/joust/<pkg>.<Contract>.json}; two different copies on the class path refuse.</li>
 *   <li>The validator class is {@code x-joust.package} + {@code x-joust.validator}; its script is
 *   {@code META-INF/plutus/<validator simple name>.plutus.json} from the entry that holds the validator's class file. That
 *   entry holding two classes of that simple name, or no such resource, refuses.</li>
 *   <li>The script hash is recomputed: Blake2b-224 of the language tag ({@code 0x03}, Plutus V3) and the {@code cborHex}
 *   bytes with their outer CBOR byte-string header removed once; the blueprint's {@code hash} must agree.</li>
 *   <li>The output and map verifiers are resolved like contracts, from the entry holding
 *   {@code org/julclang/state/verifier/OutputVerifier.class} / {@code MapVerifier.class}; another entry holding a resource
 *   of that name, or a second class of that simple name in that entry, refuses.</li>
 *   <li>Addresses are derived with CCL for the bound network: a {@code stakeSelf} contract's state sits at a base address
 *   staked to its own script, any other at an enterprise address (as JOUST's runtime derives them).</li>
 * </ul>
 */
public final class JoustAnchors {

    /** The anchors cannot be derived; plain text for the panel. The session must not sign. */
    public static final class Refused extends Exception {
        public Refused(String why) {
            super(why, null, false, false);
        }
    }

    /** A script: its language, recomputed hash, address (a verifier's holder) and reward address. */
    public record Script(String language, String hash, String address, String reward) {
        ObjectNode toJson() {
            return HostJson.object().put("language", language).put("hash", hash).put("address", address);
        }
    }

    /**
     * One contract's anchors for one network (0 or 1): what the checker receives, the reward address for the
     * deployment check, and the launch entries the scripts came from (M33A-DES-3, -DES-10).
     */
    public record Anchors(JsonNode description, int networkId, Script contract, Script outputVerifier, @Nullable Script mapVerifier,
                          int validatorEntry, int outputVerifierEntry, int mapVerifierEntry) {

        public String rewardAddress() {
            return contract.reward();
        }

        /** The checker's {@code anchors} object (D13; the vectors' shape). */
        public ObjectNode toJson() {
            ObjectNode n = HostJson.object();
            n.set("description", description);
            n.put("language", contract.language()).put("policy", contract.hash()).put("address", contract.address());
            n.set("outputVerifier", outputVerifier.toJson());
            if (mapVerifier != null) n.set("mapVerifier", mapVerifier.toJson());
            return n;
        }
    }

    static final String OUTPUT_VERIFIER = "org/julclang/state/verifier/OutputVerifier.class";
    static final String MAP_VERIFIER = "org/julclang/state/verifier/MapVerifier.class";
    /** A validator's simple name: no {@code $}, whose blueprint name the runtime would derive differently (M33A-SEC-2). */
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,200}");
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{0,100}(?:\\.[A-Za-z_$][A-Za-z0-9_$]{0,100}){0,30}");
    private static final Pattern HASH28 = Pattern.compile("[0-9a-f]{56}");

    private JoustAnchors() {
    }

    /**
     * Derives {@code contract}'s ({@code <pkg>.<Contract>}) anchors from the snapshot for network id 0 or 1, resolving
     * classes as a JVM of feature version {@code javaFeature} (the launch spec's java) loads them.
     */
    public static Anchors derive(HostSnapshot snapshot, String contract, int networkId, int javaFeature) throws Refused {
        if (networkId != 0 && networkId != 1) throw new Refused("network id " + networkId + " is neither 0 nor 1");
        Network network = networkId == 1 ? Networks.mainnet() : Networks.testnet();
        ContractScript cs = contractScript(snapshot, contract, javaFeature);
        JsonNode description = cs.description();
        JsonNode x = description.path("x-joust");
        Resolved v = new Resolved(cs.entry(), cs.hash());
        String address = x.path("stakeSelf").asBoolean(false)
                ? AddressProvider.getBaseAddress(Credential.fromScript(HexFormat.of().parseHex(v.hash)),
                        Credential.fromScript(HexFormat.of().parseHex(v.hash)), network).toBech32()
                : AddressProvider.getEntAddress(Credential.fromScript(HexFormat.of().parseHex(v.hash)), network).toBech32();
        String reward = AddressProvider.getRewardAddress(Credential.fromScript(HexFormat.of().parseHex(v.hash)), network).toBech32();
        Resolved ov = resolve(snapshot, OUTPUT_VERIFIER, "OutputVerifier", true, javaFeature);
        Script map = null;
        int mapEntry = -1;
        if (description.path("definitions").has("org.julclang.state.api.MerkleMap")) {
            Resolved mv = resolve(snapshot, MAP_VERIFIER, "MapVerifier", true, javaFeature);
            map = verifierScript(mv.hash, network);
            mapEntry = mv.entry;
        }
        return new Anchors(description, networkId, new Script("PlutusScriptV3", v.hash, address, reward),
                verifierScript(ov.hash, network), map, v.entry, ov.entry, mapEntry);
    }

    /**
     * The contracts a snapshot describes, by the host's discovery rule: the first copy of each
     * {@code META-INF/joust/<name>.json} on the class path, skipped when it has no {@code x-joust}. The one discovery
     * function for the catalog check and the signing gate (M33A-DES-13).
     */
    public static java.util.List<String> describedContracts(HostSnapshot snapshot) throws Refused {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (var e : snapshot.firstByPath().entrySet()) {
            String path = e.getKey();
            if (!path.startsWith("META-INF/joust/") || !path.endsWith(".json")) continue;
            try {
                if (parseResource(e.getValue().bytes()).path("x-joust").isMissingNode()) continue;
            } catch (HostJson.Malformed m) {
                throw new Refused("the build's " + path + " is not valid JSON");
            }
            out.add(path.substring("META-INF/joust/".length(), path.length() - 5));
        }
        return out;
    }

    /** A contract's own description and recomputed script hash, without its verifiers (M33A-CDX-28). */
    public record ContractScript(JsonNode description, String hash, int entry) {
    }

    /**
     * The contract's description and its validator's script, as {@link #derive} establishes them: what the catalog
     * check compares even when a shared verifier cannot be anchored (M33A-CDX-28).
     */
    public static ContractScript contractScript(HostSnapshot snapshot, String contract, int javaFeature) throws Refused {
        if (!PACKAGE.matcher(contract).matches()) throw new Refused("not a contract name: " + contract);
        // The description: every copy on the class path must be the same bytes.
        String descPath = "META-INF/joust/" + contract + ".json";
        byte[] desc = null;
        for (HostSnapshot.Anchor a : snapshot.anchors()) {
            if (!a.path().equals(descPath)) continue;
            if (desc == null) desc = a.bytes();
            else if (!Arrays.equals(desc, a.bytes())) throw new Refused("two different descriptions of " + contract + " are on the class path");
        }
        if (desc == null) throw new Refused("no description of " + contract + " in the build");
        JsonNode description;
        try {
            description = parseResource(desc);
        } catch (HostJson.Malformed e) {
            throw new Refused("the description of " + contract + " is not valid JSON");
        }
        JsonNode x = description.path("x-joust");
        String pkg = x.path("package").textValue(), validator = x.path("validator").textValue(), name = x.path("contract").textValue();
        if (pkg == null || validator == null || name == null || !PACKAGE.matcher(pkg).matches() || !NAME.matcher(validator).matches()
                || !(pkg + "." + name).equals(contract)) {
            throw new Refused("the description of " + contract + " does not name its package, contract and validator");
        }
        // The validator's script, from the entry holding its class.
        Resolved v = resolve(snapshot, pkg.replace('.', '/') + "/" + validator + ".class", validator, false, javaFeature);
        return new ContractScript(description, v.hash, v.entry);
    }

    /**
     * A description or blueprint read as the host reads it (Jackson over the bytes): a leading UTF-8 byte-order mark is
     * accepted, otherwise strict (M33A-CDX-29). Protocol frames keep their own strict parser.
     */
    public static JsonNode parseResource(byte[] b) throws HostJson.Malformed {
        if (b.length >= 3 && (b[0] & 0xff) == 0xef && (b[1] & 0xff) == 0xbb && (b[2] & 0xff) == 0xbf) {
            b = Arrays.copyOfRange(b, 3, b.length);
        }
        return HostJson.parse(b);
    }

    private record Resolved(int entry, String hash) {
    }

    /**
     * The script of the class at {@code classPath}: from the first entry holding it, which must hold exactly one class
     * of that simple name and the blueprint {@code META-INF/plutus/<simple>.plutus.json}; for a verifier, no other entry
     * may hold a resource of that name.
     */
    private static Resolved resolve(HostSnapshot snapshot, String classPath, String simple, boolean verifier, int javaFeature)
            throws Refused {
        int entry = snapshot.firstWithClass(classPath, javaFeature);
        if (entry < 0) throw new Refused("the class " + classPath.replace('/', '.').replace(".class", "") + " is not in the build");
        // The class in exactly one launch entry: a dependency shipping the validator's class with a blueprint of its
        // own would otherwise decide the contract (M33A-SEC-1).
        List<Integer> holders = snapshot.entriesHolding(classPath);
        if (holders.size() != 1) {
            throw new Refused("the class " + classPath.replace('/', '.').replace(".class", "") + " is in " + holders.size()
                    + " class-path entries; it must be in exactly one");
        }
        boolean folded = snapshot.isDirectory(entry);   // M33A-CDX-26
        String tail = (simple + ".class").toLowerCase(java.util.Locale.ROOT);
        long sameName = snapshot.classesAt(entry).stream()
                .filter(c -> folded ? c.toLowerCase(java.util.Locale.ROOT).equals(tail) || c.toLowerCase(java.util.Locale.ROOT).endsWith("/" + tail)
                        : c.equals(simple + ".class") || c.endsWith("/" + simple + ".class")).count();
        if (sameName != 1) throw new Refused("the class-path entry holding " + simple + " holds " + sameName + " classes of that name");
        String blueprint = "META-INF/plutus/" + simple + ".plutus.json";
        List<HostSnapshot.Anchor> copies = new ArrayList<>();
        for (HostSnapshot.Anchor a : snapshot.anchors()) if (a.path().equals(blueprint)) copies.add(a);
        List<HostSnapshot.Anchor> owned = copies.stream().filter(a -> a.ordinal() == entry).toList();
        if (owned.isEmpty()) throw new Refused("no blueprint " + blueprint + " beside " + simple);
        // Exactly one in the owning entry: a merged jar with two entries of that name is ambiguous (M33A-CDX-7).
        if (owned.size() > 1) throw new Refused("the class-path entry holding " + simple + " has " + owned.size() + " blueprints of that name");
        HostSnapshot.Anchor own = owned.get(0);
        if (verifier && copies.size() > 1) throw new Refused("another class-path entry holds a blueprint named " + simple);
        JsonNode b;
        try {
            b = parseResource(own.bytes());
        } catch (HostJson.Malformed e) {
            throw new Refused("the blueprint of " + simple + " is not valid JSON");
        }
        if (!"PlutusScriptV3".equals(b.path("type").textValue())) throw new Refused("the script of " + simple + " is not Plutus V3");
        String cbor = b.path("cborHex").textValue();
        if (cbor == null || !cbor.matches("(?:[0-9a-fA-F]{2})+")) throw new Refused("the blueprint of " + simple + " has no script");
        String hash = scriptHash(HexFormat.of().parseHex(cbor));
        String claimed = b.path("hash").textValue();
        if (claimed == null || !HASH28.matcher(claimed).matches() || !claimed.equals(hash)) {
            throw new Refused("the blueprint of " + simple + " claims hash " + claimed + ", but its script hashes to " + hash);
        }
        return new Resolved(entry, hash);
    }

    /** CCL's Plutus V3 script hash: Blake2b-224 of {@code 0x03} and the script with its outer byte-string header removed once. */
    static String scriptHash(byte[] cborHex) throws Refused {
        byte[] inner = unwrapByteString(cborHex);
        byte[] tagged = new byte[inner.length + 1];
        tagged[0] = 0x03;
        System.arraycopy(inner, 0, tagged, 1, inner.length);
        return HexFormat.of().formatHex(Blake2bUtil.blake2bHash224(tagged));
    }

    /** The content of one definite-length CBOR byte string that spans {@code b} exactly. */
    static byte[] unwrapByteString(byte[] b) throws Refused {
        if (b.length == 0 || (b[0] & 0xe0) != 0x40) throw new Refused("the script is not a CBOR byte string");
        int info = b[0] & 0x1f, head;
        long len;
        if (info < 24) {
            len = info;
            head = 1;
        } else if (info <= 27) {
            int n = 1 << (info - 24);
            if (b.length < 1 + n) throw new Refused("the script's CBOR header is truncated");
            len = 0;
            for (int i = 0; i < n; i++) len = (len << 8) | (b[1 + i] & 0xff);
            head = 1 + n;
        } else {
            throw new Refused("the script is not a definite-length CBOR byte string");
        }
        if (len != b.length - head) throw new Refused("the script's CBOR length does not match its bytes");
        return Arrays.copyOfRange(b, head, b.length);
    }

    /** A verifier's holder (its enterprise address) and reward address, as the runtime's VerifierHandle derives them. */
    private static Script verifierScript(String hash, Network network) {
        return new Script("PlutusScriptV3", hash, enterprise(hash, network),
                AddressProvider.getRewardAddress(Credential.fromScript(HexFormat.of().parseHex(hash)), network).toBech32());
    }

    private static String enterprise(String hash, Network network) {
        return AddressProvider.getEntAddress(Credential.fromScript(HexFormat.of().parseHex(hash)), network).toBech32();
    }
}
