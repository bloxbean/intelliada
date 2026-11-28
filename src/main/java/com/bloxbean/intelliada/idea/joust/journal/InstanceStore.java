package com.bloxbean.intelliada.idea.joust.journal;

import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * ADR-005 D15 "Persistence" (M3.4a): the instances a module created on a chain, recorded so the run panel lists them
 * when the host starts again. Kept per user beside the journal ({@code ~/.intelliada/joust-instances/<chain>/<module>.json}),
 * not in {@code .idea/} or an IDE's system directory, so an IDE update or a second IDE keeps them. No keys. Untrusted on
 * load: a file that is not exactly the expected shape, or is too large, is ignored (and replaced by the next record).
 */
public final class InstanceStore {

    /** One recorded instance. */
    public record Recorded(String contract, String instance, String txHash, String label) {
    }

    static final int VERSION = 1;
    static final int MAX_INSTANCES = 1_000;
    static final long MAX_BYTES = 1L << 20;
    private static final Pattern HASH32 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CONTRACT = Pattern.compile("[A-Za-z0-9_.$]{1,300}");

    private final Path root;

    public InstanceStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** The store for this user: {@code ~/.intelliada/joust-instances}. */
    public static InstanceStore forUser() {
        return new InstanceStore(Path.of(System.getProperty("user.home"), ".intelliada", "joust-instances"));
    }

    private Path file(String chain, Path module) {
        return root.resolve(JournalStore.key(chain)).resolve(JournalStore.key(module.toAbsolutePath().normalize().toString()) + ".json");
    }

    /** The recorded instances of {@code module} on {@code chain}, oldest first; empty when none or unreadable. */
    public synchronized List<Recorded> list(String chain, Path module) {
        Path f = file(chain, module);
        List<Recorded> out = new ArrayList<>();
        try {
            if (!Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS) || Files.size(f) > MAX_BYTES) return out;
            JsonNode n = HostJson.parse(Files.readAllBytes(f));
            if (!n.isObject() || n.size() != 2 || n.path("version").asInt(-1) != VERSION || !n.path("instances").isArray()) return out;
            for (JsonNode e : n.path("instances")) {
                if (out.size() >= MAX_INSTANCES) break;
                Recorded r = recorded(e);
                if (r == null) return new ArrayList<>();   // one bad entry: the file is not ours
                out.add(r);
            }
        } catch (Exception unreadable) {
            return new ArrayList<>();
        }
        return out;
    }

    private static Recorded recorded(JsonNode e) {
        if (!e.isObject() || e.size() != 4) return null;
        String c = e.path("contract").textValue(), i = e.path("instance").textValue(), t = e.path("txHash").textValue(),
                l = e.path("label").textValue();
        if (c == null || i == null || t == null || l == null) return null;
        if (!CONTRACT.matcher(c).matches() || !HASH32.matcher(i).matches() || !HASH32.matcher(t).matches() || l.length() > 200) return null;
        return new Recorded(c, i, t, l);
    }

    /** Records an instance (once per contract and instance), keeping the newest {@link #MAX_INSTANCES}. */
    public synchronized void record(String chain, Path module, Recorded r) throws IOException {
        List<Recorded> all = list(chain, module);
        all.removeIf(x -> x.contract().equals(r.contract()) && x.instance().equals(r.instance()));
        all.add(r);
        while (all.size() > MAX_INSTANCES) all.remove(0);
        write(chain, module, all);
    }

    /** Forgets an instance (closed, or no longer on the chain). */
    public synchronized void forget(String chain, Path module, String contract, String instance) throws IOException {
        List<Recorded> all = list(chain, module);
        if (all.removeIf(x -> x.contract().equals(contract) && x.instance().equals(instance))) write(chain, module, all);
    }

    private void write(String chain, Path module, List<Recorded> all) throws IOException {
        Path f = file(chain, module);
        Files.createDirectories(f.getParent());
        ObjectNode n = HostJson.object().put("version", VERSION);
        ArrayNode a = n.putArray("instances");
        for (Recorded r : all) {
            a.addObject().put("contract", r.contract()).put("instance", r.instance()).put("txHash", r.txHash()).put("label", r.label());
        }
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        Files.write(tmp, HostJson.MAPPER.writeValueAsString(n).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, f, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
