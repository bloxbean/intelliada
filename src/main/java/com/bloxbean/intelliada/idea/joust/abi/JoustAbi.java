package com.bloxbean.intelliada.idea.joust.abi;

import com.bloxbean.intelliada.idea.joust.core.JoustRealPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * ADR-004 D8 and I8: the ABI-change review, as pure logic.
 *
 * <ul>
 *   <li><b>Bind before running:</b> the contract name matches JOUST's qualified-name grammar, a hash is 64 lowercase hex
 *   characters, the committed hash is that or {@code none}; task arguments are built as lists.</li>
 *   <li><b>Review:</b> the {@code --out} JSON of {@code jscAbiDiff}/{@code joust abi diff} (shape pinned at JOUST
 *   v0.1.0-pre2: {@code contract, file?, path, committed{sha256,text}, now{sha256,text}}) is project data. IntelliAda
 *   recomputes the SHA-256 of the texts it shows, reads the committed manifest itself, and allows Accept only when all
 *   agree.</li>
 *   <li><b>After accepting:</b> that manifest has the new hash and no other manifest changed.</li>
 * </ul>
 */
public final class JoustAbi {

    public static final Pattern CONTRACT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");
    public static final Pattern SIMPLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    public static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    static final long OUT_CAP = 8L * 1024 * 1024;
    private static final Pattern FAILURE_GRADLE = Pattern.compile(
            "^\\s*([^\\s:][^:]{0,500}?\\.jsc):\\d+:\\d+: error\\[(JSC0002|JSC0003)\\]: .*?([A-Za-z_][A-Za-z0-9_]{0,200})\\.abi\\.json");
    private static final Pattern FAILURE_NPM = Pattern.compile(
            "error\\[(JSC0002|JSC0003)\\]: ([^\\s:][^:]{0,500}?\\.jsc):\\d+:\\d+: .*?([A-Za-z_][A-Za-z0-9_]{0,200})\\.abi\\.json");

    private JoustAbi() {
    }

    /** A refusal, with a message for the user. */
    public static final class AbiException extends Exception {
        public AbiException(String message) {
            super(message);
        }
    }

    /** A validated diff. {@code committedText} is null exactly when {@code committedSha} is {@code none}. */
    public record Diff(String contract, String file, String path, String committedSha, String committedText, String nowSha, String nowText) {
        public boolean unchanged() {
            return committedSha.equals(nowSha);
        }
    }

    /** An ABI failure found in build output: the {@code .jsc} path as printed and the contract's simple name. */
    public record Failure(String code, String jscRel, String contract) {
    }

    // ------------------------------------------------------------------ names and arguments

    public static String requireContract(String name) throws AbiException {
        if (name == null || name.length() > 500 || !CONTRACT.matcher(name).matches()) {
            throw new AbiException("not a contract name: " + (name == null ? "none" : name.length() > 80 ? name.substring(0, 80) + "…" : name));
        }
        return name;
    }

    static String requireHash(String h, boolean noneAllowed) throws AbiException {
        if (h != null && (HASH.matcher(h).matches() || (noneAllowed && h.equals("none")))) return h;
        throw new AbiException("not a SHA-256: " + h);
    }

    /** {@code :m:jscAbiDiff --contract C --out <file>} for a Gradle project path ({@code ":"} for the root). */
    public static List<String> gradleDiffArgs(String gradlePath, String contract, Path out) throws AbiException {
        return List.of(task(gradlePath, "jscAbiDiff"), "--contract", requireContract(contract), "--out", out.toString());
    }

    /** {@code :m:acceptJscAbi --contract C --committed-sha256 h1 --sha256 h2}: never the bare task (I8). */
    public static List<String> gradleAcceptArgs(String gradlePath, Diff d) throws AbiException {
        return List.of(task(gradlePath, "acceptJscAbi"), "--contract", requireContract(d.contract()),
                "--committed-sha256", requireHash(d.committedSha(), true), "--sha256", requireHash(d.nowSha(), false));
    }

    public static List<String> npmDiffArgs(String contract, Path out) throws AbiException {
        return List.of("abi", "diff", requireContract(contract), "--out", out.toString());
    }

    /**
     * {@code abi diff C --src <contracts> --out <file>}: bound to the verified contracts directory, so a
     * {@code joust.config.json} changed meanwhile cannot redirect it (M2-CDX-23).
     */
    public static List<String> npmDiffArgs(String contract, String src, Path out) throws AbiException {
        return List.of("abi", "diff", requireContract(contract), "--src", requireSrc(src), "--out", out.toString());
    }

    /** {@code build --accept-abi=C@h1..h2}: never the bare {@code --accept-abi} (I8). */
    public static List<String> npmAcceptArgs(Diff d) throws AbiException {
        return List.of("build", "--accept-abi=" + requireContract(d.contract()) + "@" + requireHash(d.committedSha(), true) + ".."
                + requireHash(d.nowSha(), false));
    }

    /** {@code build --src <contracts> --accept-abi=C@h1..h2}, bound to the verified contracts directory (M2-CDX-23). */
    public static List<String> npmAcceptArgs(Diff d, String src) throws AbiException {
        return List.of("build", "--src", requireSrc(src), "--accept-abi=" + requireContract(d.contract()) + "@"
                + requireHash(d.committedSha(), true) + ".." + requireHash(d.nowSha(), false));
    }

    /**
     * A contracts directory relative to the npm root, passed as one argument (M2-CDX-26): any name the scanner confined
     * (spaces included), {@code .} for the root itself; never absolute, never {@code ..}, never an option, no control
     * characters.
     */
    static String requireSrc(String src) throws AbiException {
        if (src == null || src.isEmpty() || src.length() > 1000 || src.startsWith("-") || src.startsWith("/") || src.startsWith("\\")
                || src.matches("^[A-Za-z]:.*")
                || src.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT)) {   // M2-SEC-7
            throw new AbiException("not a contracts directory: " + src);
        }
        for (String seg : src.split("[/\\\\]", -1)) {
            if (seg.equals("..") || (seg.isEmpty() && !src.equals("."))) throw new AbiException("not a contracts directory: " + src);
        }
        return src;
    }

    /** A qualified Gradle task: {@code :generateJsc} for the root project, {@code :a:b:generateJsc} for a module. */
    public static String task(String gradlePath, String name) throws AbiException {
        if (gradlePath == null || !gradlePath.matches(":|(:[A-Za-z0-9_.-]{1,200}){1,20}")) throw new AbiException("not a Gradle project path: " + gradlePath);
        return (gradlePath.equals(":") ? "" : gradlePath) + ":" + name;
    }

    // ------------------------------------------------------------------ failures in build output

    /** The JSC0002 and JSC0003 failures in build output, in order. */
    public static List<Failure> failures(String output) {
        List<Failure> out = new ArrayList<>();
        if (output == null) return out;
        // Every line, however long the output; the matches themselves are bounded (M2-CDX-24).
        for (String line : (Iterable<String>) output.lines()::iterator) {
            if (out.size() >= 256) break;
            Matcher g = FAILURE_GRADLE.matcher(line);
            if (g.find()) {
                out.add(new Failure(g.group(2), g.group(1), g.group(3)));
                continue;
            }
            Matcher n = FAILURE_NPM.matcher(line);
            if (n.find()) out.add(new Failure(n.group(1), n.group(2), n.group(3)));
        }
        return out;
    }

    /**
     * The bound acceptance's explicit refusal in task output, or null (M2-CDX-6). Both pinned producers (Gradle's
     * JscMain, npm's {@code src/abi.js} at v0.1.0-pre2) print a refusal either as
     * {@code <source>:1:1: error[JSC0002]: not accepted: …}, the source relative to the contracts directory (whether
     * the baseline or the source changed), or, when the contract cannot be found, as
     * {@code error[JSC0002]: --accept-abi=<Contract>@…: …; nothing accepted}. Paths are compared with {@code /}.
     */
    public static String refusal(String output, String jscRel, String contract) {
        if (output == null) return null;
        String prefix = jscRel == null ? null : jscRel.replace('\\', '/') + ":";
        String bound = contract == null ? null : "--accept-abi=" + contract + "@";
        for (String line : (Iterable<String>) output.lines()::iterator) {
            String l = line.strip();
            String slashed = l.replace('\\', '/');
            boolean hit = (prefix != null && slashed.startsWith(prefix) && l.contains(": error[JSC0002]: not accepted"))
                    || (bound != null && l.contains("error[JSC0002]: " + bound) && l.endsWith("nothing accepted"));
            if (hit) return l.length() > 1000 ? l.substring(0, 1000) : l;
        }
        return null;
    }

    public static String refusal(String output, String jscRel) {
        return refusal(output, jscRel, null);
    }

    /**
     * Whether task output affirmatively reports accepting this manifest (M2-CDX-6): Gradle prints
     * {@code <source>: note: accepted the changed ABI manifest <Name>.abi.json} (or {@code created the}), npm
     * {@code <manifest, relative to the root>: accepted the changed ABI manifest …} (or {@code created the}).
     */
    public static boolean reportsAccepted(String output, String jscRel, String manifestName, String manifestRelToRoot) {
        if (output == null) return false;
        String src = jscRel == null ? null : jscRel.replace('\\', '/') + ": note: ";
        String rel = manifestRelToRoot == null ? null : manifestRelToRoot.replace('\\', '/') + ": ";
        for (String line : (Iterable<String>) output.lines()::iterator) {
            String l = line.strip().replace('\\', '/');
            boolean says = l.contains("accepted the changed ABI manifest") || l.contains("created the ABI manifest");
            if (!says) continue;
            if (src != null && l.startsWith(src) && l.contains("ABI manifest " + manifestName)) return true;
            if (rel != null && l.startsWith(rel)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ the diff

    /** Parses and validates the {@code --out} JSON (size-capped). */
    public static Diff parse(Path outFile) throws AbiException {
        try {
            if (!Files.isRegularFile(outFile, LinkOption.NOFOLLOW_LINKS) || Files.size(outFile) > OUT_CAP) {
                throw new AbiException("the ABI diff was not written, or is too large");
            }
            return parse(Files.readString(outFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AbiException("the ABI diff could not be read: " + e.getMessage());
        }
    }

    public static Diff parse(String json) throws AbiException {
        JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (RuntimeException e) {
            throw new AbiException("the ABI diff is not JSON");
        }
        String contract = requireContract(o.optString("contract", null));
        String file = o.opt("file") instanceof String f ? f : null;
        if (!(o.opt("path") instanceof String path) || path.isEmpty()) throw new AbiException("the ABI diff names no manifest path");
        JSONObject committed = o.optJSONObject("committed");
        JSONObject now = o.optJSONObject("now");
        if (committed == null || now == null) throw new AbiException("the ABI diff has no committed or new manifest");
        String h1 = requireHash(committed.optString("sha256", null), true);
        String h2 = requireHash(now.optString("sha256", null), false);
        Object t1 = committed.opt("text");
        String text1 = t1 instanceof String s ? s : null;
        if (!(now.opt("text") instanceof String text2)) throw new AbiException("the ABI diff has no new manifest text");
        if ((text1 == null) != h1.equals("none")) throw new AbiException("the committed manifest and its hash disagree");
        return new Diff(contract, file, path, h1, text1, h2, text2);
    }

    /**
     * Why Accept must stay disabled, or null (D8 step 3). The SHA-256 of each displayed text must equal its reported hash,
     * and the committed manifest IntelliAda reads itself must be the reported baseline.
     */
    public static String verify(Diff d, Path committedManifestOrNull) {
        if (d.committedText() != null && !JoustToolValidator.sha256(d.committedText().getBytes(StandardCharsets.UTF_8)).equals(d.committedSha())) {
            return "the committed manifest shown does not have the reported SHA-256";
        }
        if (!JoustToolValidator.sha256(d.nowText().getBytes(StandardCharsets.UTF_8)).equals(d.nowSha())) {
            return "the new manifest shown does not have the reported SHA-256";
        }
        String actual;
        try {
            actual = committedManifestOrNull != null && Files.isRegularFile(committedManifestOrNull)
                    ? JoustToolValidator.sha256(committedManifestOrNull) : "none";
        } catch (IOException e) {
            return "the committed manifest could not be read";
        }
        if (!actual.equals(d.committedSha())) return "the committed manifest on disk is " + actual + ", not the reviewed " + d.committedSha();
        if (d.unchanged()) return "the manifest is unchanged";
        return null;
    }

    /**
     * The committed manifest a diff names, confined: {@code dir/path} (the module's directory, or the npm root) must
     * resolve inside {@code contractsDir} and be named {@code <Contract>.abi.json}. A manifest not yet committed is
     * allowed to be absent. Returns the path (possibly not existing), or throws.
     */
    public static Path manifestPath(Diff d, Path dir, Path contractsDir) throws AbiException {
        String rel = d.path();
        if (rel.startsWith("/") || rel.startsWith("\\") || rel.matches("^[A-Za-z]:.*") || rel.contains("..") || rel.indexOf('\0') >= 0) {
            throw new AbiException("the manifest path is not inside the project: " + rel);
        }
        String simple = d.contract().substring(d.contract().lastIndexOf('.') + 1);
        Path p = dir.resolve(rel).normalize();
        if (!p.getFileName().toString().equals(simple + ".abi.json")) throw new AbiException("the manifest is not " + simple + ".abi.json");
        Path parentReal;
        try {
            parentReal = p.getParent().toRealPath();
            Path rootReal = contractsDir.toRealPath();
            if (!JoustRealPaths.isInside(parentReal, rootReal)) throw new AbiException("the manifest is outside the contracts directory");
            Path real = parentReal.resolve(p.getFileName());
            if (Files.exists(real, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(real) || !Files.isRegularFile(real))) {
                throw new AbiException("the manifest is not a regular file");
            }
            return real;
        } catch (IOException e) {
            throw new AbiException("the manifest's directory does not exist: " + rel);
        }
    }

    // ------------------------------------------------------------------ after accepting

    /**
     * The manifests of a project: SHA-256 of every {@code *.abi.json} under every contracts directory, and why the
     * inventory is incomplete (null when complete). Incomplete is a verification failure, never a silent pass (M2-CDX-7).
     */
    public record Snapshot(Map<String, String> hashes, String incomplete) {
    }

    static final int MAX_MANIFESTS = 10_000;
    static final int MAX_MANIFEST_DEPTH = 32;

    /** Every manifest under every contracts directory of {@code layout} (I8's "only that manifest"). */
    public static Snapshot manifests(JoustProjectLayout layout) {
        // Keyed by the exact real-path string, never Path equality (which folds case on Windows), M2-CDX-5.
        Map<String, String> out = new TreeMap<>();
        if (!layout.complete()) return new Snapshot(out, "the project scan stopped at its limit");
        String[] incomplete = new String[1];
        for (JoustProjectLayout.SourceRoot r : layout.sourceRoots()) {
            if (!Files.exists(r.contracts(), LinkOption.NOFOLLOW_LINKS)) continue;
            Path root = r.verifiedContracts(layout.base());
            if (root == null) {
                incomplete[0] = "a contracts directory is redirected out of its module: " + r.contracts();
                break;
            }
            try {
                Files.walkFileTree(root, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), MAX_MANIFEST_DEPTH + 1,
                        new java.nio.file.SimpleFileVisitor<>() {
                            @Override
                            public java.nio.file.FileVisitResult visitFile(Path p, java.nio.file.attribute.BasicFileAttributes attrs) {
                                if (root.relativize(p).getNameCount() > MAX_MANIFEST_DEPTH) {
                                    incomplete[0] = "a contracts directory is deeper than " + MAX_MANIFEST_DEPTH + " levels";
                                    return java.nio.file.FileVisitResult.TERMINATE;
                                }
                                if (!p.getFileName().toString().endsWith(".abi.json")) return java.nio.file.FileVisitResult.CONTINUE;
                                if (!attrs.isRegularFile()) {
                                    incomplete[0] = "a manifest is not a regular file: " + p;
                                    return java.nio.file.FileVisitResult.TERMINATE;
                                }
                                if (out.size() >= MAX_MANIFESTS) {
                                    incomplete[0] = "more than " + MAX_MANIFESTS + " manifests";
                                    return java.nio.file.FileVisitResult.TERMINATE;
                                }
                                try {
                                    out.put(p.toAbsolutePath().normalize().toString(), JoustToolValidator.sha256(p));
                                } catch (IOException e) {
                                    incomplete[0] = "a manifest could not be read: " + p;
                                    return java.nio.file.FileVisitResult.TERMINATE;
                                }
                                return java.nio.file.FileVisitResult.CONTINUE;
                            }

                            @Override
                            public java.nio.file.FileVisitResult visitFileFailed(Path p, IOException e) {
                                incomplete[0] = "a directory could not be read: " + p;
                                return java.nio.file.FileVisitResult.TERMINATE;
                            }
                        });
            } catch (IOException e) {
                incomplete[0] = "a contracts directory could not be read: " + root;
            }
            if (incomplete[0] != null) break;
        }
        return new Snapshot(out, incomplete[0]);
    }

    /**
     * Why the acceptance did not do exactly what was reviewed, or null (D8 step 4, M2-CDX-6): before it, the manifest
     * was the reviewed baseline (absent for {@code none}); after it, it has the new hash; every other manifest is
     * unchanged, none appeared, and both inventories are complete.
     */
    public static String verifyAccepted(Snapshot before, Snapshot after, Path manifest, Diff d) {
        if (before.incomplete() != null) return "the manifests could not all be checked before accepting: " + before.incomplete();
        if (after.incomplete() != null) return "the manifests could not all be checked after accepting: " + after.incomplete();
        String m = manifest.toAbsolutePath().normalize().toString();
        String name = manifest.getFileName().toString();
        String was = before.hashes().get(m);
        String expected = d.committedSha().equals("none") ? null : d.committedSha();
        if (!java.util.Objects.equals(was, expected)) {
            return "before accepting, " + name + " was " + (was == null ? "absent" : was) + ", not the reviewed " + d.committedSha();
        }
        if (!d.nowSha().equals(after.hashes().get(m))) return "the manifest " + name + " does not have the accepted SHA-256";
        for (var e : before.hashes().entrySet()) {
            if (!e.getKey().equals(m) && !e.getValue().equals(after.hashes().get(e.getKey()))) return "another manifest changed or disappeared: " + e.getKey();
        }
        for (String p : after.hashes().keySet()) {
            if (!p.equals(m) && !before.hashes().containsKey(p)) return "another manifest appeared: " + p;
        }
        return null;
    }
}
