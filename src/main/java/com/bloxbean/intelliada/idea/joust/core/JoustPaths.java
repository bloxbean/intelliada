package com.bloxbean.intelliada.idea.joust.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Path checks for ADR-004 D4 and D3a item 7, as pure functions over path text.
 *
 * <p>Paths are compared <b>by segment</b>, never by string prefix: {@code /u/repo-evil} is not inside {@code /u/repo}.
 * Windows drive and UNC forms are normalised: {@code \\?\C:\x} equals {@code C:\x}, {@code \\?\UNC\srv\share\x} equals
 * {@code \\srv\share\x}, separators may be either slash, and case and trailing dots or spaces of a segment are ignored
 * (Windows ignores them when it opens a file). Callers pass <b>real paths</b> (symlinks resolved) wherever the ADR
 * says "real path"; this class never touches the file system.
 */
public final class JoustPaths {

    /** How a platform spells and compares paths. */
    public enum Flavor {
        /** Linux and other Unix: case-sensitive. */
        UNIX(true),
        /** macOS: case-insensitive by default (APFS). Treated as case-insensitive, which errs towards "inside". */
        MAC(false),
        /** Windows: case-insensitive, drive and UNC roots. */
        WINDOWS(false);

        private final boolean caseSensitive;

        Flavor(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
        }

        public static Flavor current() {
            if (File.separatorChar == '\\') return WINDOWS;
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            return os.contains("mac") || os.contains("darwin") ? MAC : UNIX;
        }

        /**
         * The flavor for decisions that <b>allow</b> something (a navigation target, a workspace folder): both sides are
         * real paths, so they are compared exactly on macOS. Case-insensitive comparison stays the default for decisions
         * that <b>refuse</b> (a tool inside a project), where erring towards "inside" is the safe side.
         */
        public static Flavor forAllowDecisions() {
            Flavor f = current();
            return f == MAC ? UNIX : f;
        }
    }

    private JoustPaths() {
    }

    /**
     * The normalised segments of an absolute path, root first: {@code "/"} on Unix, {@code "c:"} for a drive, or
     * {@code "\\server\share"} for UNC. Returns {@code null} when the path is not absolute or not well formed (a
     * {@code ..} above the root, a drive-relative {@code C:x}, a rooted {@code \x} without a drive, a NUL).
     */
    public static List<String> segments(String path, Flavor flavor) {
        if (path == null || path.isEmpty() || path.indexOf('\0') >= 0) return null;
        return flavor == Flavor.WINDOWS ? windowsSegments(path) : unixSegments(path, flavor);
    }

    public static boolean isAbsolute(String path, Flavor flavor) {
        return segments(path, flavor) != null;
    }

    /** True when {@code child} is {@code root} or lies under it, segment by segment. False if either is not absolute. */
    public static boolean isInside(String child, String root, Flavor flavor) {
        List<String> c = segments(child, flavor);
        List<String> r = segments(root, flavor);
        if (c == null || r == null || c.size() < r.size()) return false;
        for (int i = 0; i < r.size(); i++) {
            if (!c.get(i).equals(r.get(i))) return false;
        }
        return true;
    }

    /** True when any two consecutive segments are {@code node_modules} then {@code .bin}. */
    public static boolean isUnderNodeModulesBin(String path, Flavor flavor) {
        List<String> s = segments(path, flavor);
        if (s == null) return false;
        for (int i = 1; i + 1 < s.size(); i++) {
            if (fold(s.get(i)).equals("node_modules") && fold(s.get(i + 1)).equals(".bin")) return true;
        }
        return false;
    }

    /**
     * True for a Windows script shim ({@code .cmd}, {@code .bat}, {@code .ps1}), in any case and with Windows' trailing
     * dots and spaces ignored. These are refused on every platform: cmd.exe parses their arguments (D4).
     */
    public static boolean hasRefusedExtension(String path) {
        if (path == null) return false;
        String name = path;
        int cut = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (cut >= 0) name = name.substring(cut + 1);
        name = stripTrailingDotsAndSpaces(name).toLowerCase(Locale.ROOT);
        return name.endsWith(".cmd") || name.endsWith(".bat") || name.endsWith(".ps1");
    }

    /** The last segment, as spelled (no normalisation). */
    public static String fileName(String path) {
        if (path == null) return "";
        int cut = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return cut >= 0 ? path.substring(cut + 1) : path;
    }

    private static String fold(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private static List<String> unixSegments(String path, Flavor flavor) {
        if (!path.startsWith("/")) return null;
        List<String> out = new ArrayList<>();
        out.add("/");
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (out.size() == 1) return null;
                out.remove(out.size() - 1);
                continue;
            }
            out.add(flavor.caseSensitive ? part : fold(part));
        }
        return out;
    }

    private static List<String> windowsSegments(String raw) {
        String p = raw.replace('/', '\\');
        String root;
        String rest;
        if (p.regionMatches(true, 0, "\\\\?\\UNC\\", 0, 8) || p.regionMatches(true, 0, "\\\\.\\UNC\\", 0, 8)) {
            p = "\\\\" + p.substring(8);
        } else if (p.startsWith("\\\\?\\") || p.startsWith("\\\\.\\")) {
            p = p.substring(4);
        }
        if (p.length() >= 3 && Character.isLetter(p.charAt(0)) && p.charAt(1) == ':' && p.charAt(2) == '\\') {
            root = fold(p.substring(0, 2));
            rest = p.substring(3);
        } else if (p.startsWith("\\\\")) {
            String[] parts = p.substring(2).split("\\\\", -1);
            if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null;
            String server = stripTrailingDotsAndSpaces(parts[0]);
            String share = stripTrailingDotsAndSpaces(parts[1]);
            if (server.isEmpty() || share.isEmpty() || server.equals("?") || server.equals(".")) return null;
            root = "\\\\" + fold(server) + "\\" + fold(share);
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < parts.length; i++) {
                if (sb.length() > 0) sb.append('\\');
                sb.append(parts[i]);
            }
            rest = sb.toString();
        } else {
            return null;
        }
        List<String> out = new ArrayList<>();
        out.add(root);
        for (String part : rest.split("\\\\")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (out.size() == 1) return null;
                out.remove(out.size() - 1);
                continue;
            }
            String seg = stripTrailingDotsAndSpaces(part);
            if (seg.isEmpty()) continue;
            if (seg.indexOf(':') >= 0) return null; // an alternate data stream or a second drive: not a plain path
            out.add(fold(seg));
        }
        return out;
    }

    private static String stripTrailingDotsAndSpaces(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '.' || s.charAt(end - 1) == ' ')) end--;
        // "." and ".." are handled by the callers before this; a segment of only dots and spaces becomes empty.
        return s.substring(0, end);
    }
}
