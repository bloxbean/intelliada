package com.bloxbean.intelliada.idea.joust.wizard;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustRealPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ADR-004 D9 and I7: the environment of the wizard's {@code joust new} is <b>built, not inherited</b>.
 *
 * <ul>
 *   <li>{@code PATH} holds only: the directory of the configured Node's real path (Node and web templates only, for
 *   {@code node} and {@code npm}), the directory of the native {@code joust}'s real path, and the system directories
 *   ({@code /usr/bin:/bin:/usr/sbin:/sbin}, or on Windows the {@code %SystemRoot%} directories). Never a directory inside
 *   any open project or under {@code node_modules/.bin}: the user's own {@code PATH} is not consulted at all.</li>
 *   <li>{@code JAVA_HOME} is the real home of the JDK the user picked from IntelliJ's SDK table.</li>
 *   <li>D4's rule (the selected path <b>and</b> its real target outside every open project and every
 *   {@code node_modules/.bin}; no {@code .cmd/.bat/.ps1}) applies to the executables the CLI will actually pick
 *   ({@code joust new} resolves {@code npm} and {@code node} on {@code PATH} and Java from {@code JAVA_HOME}, F27):
 *   the {@code node}, the {@code npm} (beside the configured Node) and {@code JAVA_HOME/bin/java}.</li>
 *   <li>Every permitted directory is scanned: an entry named {@code node}, {@code npm}, {@code npx} or {@code java}
 *   (any case and Windows extension) whose real target is disallowed refuses the whole environment.</li>
 *   <li>Only a fixed allow-list of the IDE's own variables is copied (home, temporary directory, locale, proxies, and
 *   Windows' system variables); {@code NODE_OPTIONS}, {@code JAVA_TOOL_OPTIONS}, {@code npm_config_*} and the like are
 *   never passed. A path-valued variable is resolved through its nearest existing ancestor's real path and dropped
 *   when it names a place inside a project, by spelling or by file identity (§12 E12, M2-CDX-11).</li>
 *   <li>The temporary-directory variables ({@code TMPDIR}, and {@code TEMP} and {@code TMP} on Windows) are never
 *   inherited: JOUST's {@code Scaffold} creates and extracts the npm package under {@code TMPDIR}, so they name the
 *   private directory the planner created and checked ({@link Inputs#tempDir}), or are left out.</li>
 *   <li>No {@code PATH} entry may contain the path separator ({@code :} or {@code ;}): the CLI splits {@code PATH} again
 *   ({@code Sys.safePath}), and a separator inside a directory name would split into unchecked entries (M2-CDX-10).</li>
 * </ul>
 *
 * <p>Pure: the file system is read through {@link FileView}, so Windows forms are tested on any platform, and nothing is
 * executed.
 */
public final class JoustNewEnvironment {

    /** The names the CLI (or a program it starts) resolves on {@code PATH}, scanned in every permitted directory. */
    public static final List<String> PROBED_NAMES = List.of("node", "npm", "npx", "java");
    static final List<String> UNIX_SYSTEM_DIRS = List.of("/usr/bin", "/bin", "/usr/sbin", "/sbin");
    private static final List<String> WINDOWS_EXTENSIONS = List.of("", ".exe", ".com", ".cmd", ".bat", ".ps1");

    /** Copied from the IDE's environment when present; no executable, option or module path among them. */
    static final List<String> PASSED_VARIABLES = List.of(
            "HOME", "USER", "LOGNAME", "LANG", "LC_ALL", "LC_CTYPE", "TZ", "TMPDIR",
            "http_proxy", "https_proxy", "no_proxy", "HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY",
            // Windows
            "SystemRoot", "SystemDrive", "windir", "PATHEXT", "TEMP", "TMP", "USERPROFILE", "USERNAME", "APPDATA",
            "LOCALAPPDATA", "HOMEDRIVE", "HOMEPATH", "PROCESSOR_ARCHITECTURE", "NUMBER_OF_PROCESSORS", "OS");
    /** Of {@link #PASSED_VARIABLES}, those that name a directory: dropped unless absolute and outside every project. */
    static final List<String> PATH_VARIABLES = List.of("HOME", "TMPDIR", "SystemRoot", "windir", "TEMP", "TMP",
            "USERPROFILE", "APPDATA", "LOCALAPPDATA");
    /** Of {@link #PASSED_VARIABLES}, those never inherited: they are set to {@link Inputs#tempDir} or left out. */
    static final List<String> TEMP_VARIABLES = List.of("TMPDIR", "TEMP", "TMP");

    private JoustNewEnvironment() {
    }

    /** What the builder reads from the file system. Paths are strings in the given flavor's syntax. */
    public interface FileView {
        /** The path with every symlink resolved; throws when it does not exist. */
        String realPath(String path) throws IOException;

        /** True when the path exists (following links). */
        boolean exists(String path);

        boolean isDirectory(String realPath);

        boolean isRegularFile(String realPath);

        boolean isExecutable(String realPath);

        /** The names of a directory's entries. */
        List<String> list(String dir) throws IOException;

        /**
         * True when the real path {@code real} is {@code root} or lies under it <b>by file identity</b> (the root's own
         * real path is resolved first). Only a real file system can answer; the default answers false, leaving the
         * decision to the spelling checks.
         */
        default boolean insideByIdentity(String real, String root) {
            return false;
        }
    }

    /** The local file system. */
    public static FileView localFiles() {
        return new FileView() {
            @Override
            public String realPath(String path) throws IOException {
                try {
                    return Path.of(path).toRealPath().toString();
                } catch (RuntimeException e) {
                    throw new IOException(e.getMessage(), e);
                }
            }

            @Override
            public boolean exists(String path) {
                try {
                    return Files.exists(Path.of(path));
                } catch (RuntimeException e) {
                    return false;
                }
            }

            @Override
            public boolean isDirectory(String realPath) {
                return Files.isDirectory(Path.of(realPath), LinkOption.NOFOLLOW_LINKS);
            }

            @Override
            public boolean isRegularFile(String realPath) {
                return Files.isRegularFile(Path.of(realPath), LinkOption.NOFOLLOW_LINKS);
            }

            @Override
            public boolean isExecutable(String realPath) {
                return Files.isExecutable(Path.of(realPath));
            }

            @Override
            public List<String> list(String dir) throws IOException {
                List<String> out = new ArrayList<>();
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(Path.of(dir))) {
                    for (Path p : ds) out.add(p.getFileName().toString());
                }
                return out;
            }

            @Override
            public boolean insideByIdentity(String real, String root) {
                try {
                    return JoustRealPaths.isInside(Path.of(real), Path.of(root).toRealPath());
                } catch (IOException | RuntimeException e) {
                    return false; // a root that does not exist has no identity; its spelling is checked
                }
            }
        };
    }

    /**
     * The inputs: the configured native {@code joust} and Node (as the user configured them), the chosen JDK home, the
     * template, the IDE's own environment (only the allow-listed variables are read from it; its {@code PATH} never),
     * the forbidden roots (every open project's base and content roots, and the new project's directory), and the
     * private temporary directory the planner created for this run ({@code null}: no temporary-directory variable).
     */
    public record Inputs(String joust, String node, String javaHome, JoustTemplate template, Map<String, String> ideEnv,
                         Collection<String> forbiddenRoots, String tempDir) {
        public Inputs(String joust, String node, String javaHome, JoustTemplate template, Map<String, String> ideEnv,
                      Collection<String> forbiddenRoots) {
            this(joust, node, javaHome, template, ideEnv, forbiddenRoots, null);
        }
    }

    /** The built environment and the executables the CLI will pick, or an error. */
    public record Result(Map<String, String> env, List<String> path, String joust, String node, String npm, String java,
                         String error) {
        public boolean ok() {
            return error == null;
        }

        static Result fail(String error) {
            return new Result(null, null, null, null, null, null, error);
        }
    }

    private record Exe(String selected, String real, String error) {
    }

    public static Result build(Inputs in, FileView fs, JoustPaths.Flavor flavor) {
        if (in == null || in.template() == null) return Result.fail("No template was chosen.");
        Collection<String> roots = in.forbiddenRoots() == null ? List.of() : in.forbiddenRoots();
        Map<String, String> ide = in.ideEnv() == null ? Map.of() : in.ideEnv();

        Exe joust = checkExe("joust", in.joust(), roots, fs, flavor);
        if (joust.error() != null) return Result.fail(joust.error());

        Exe node = null;
        if (in.template().usesNpm()) {
            node = checkExe("Node", in.node(), roots, fs, flavor);
            if (node.error() != null) {
                return Result.fail("The " + in.template().cliName() + " template needs the Node you configured in "
                        + "Settings | Tools | JOUST: " + node.error());
            }
        }

        // JAVA_HOME: the chosen JDK's real home, and the java the CLI runs from it.
        String javaHome = in.javaHome();
        if (javaHome == null || javaHome.isBlank() || !JoustPaths.isAbsolute(javaHome, flavor)) {
            return Result.fail("Choose a JDK: its home must be an absolute path, not \"" + javaHome + "\".");
        }
        if (!fs.exists(javaHome)) return Result.fail("The JDK home was not found: " + javaHome);
        String javaHomeReal;
        try {
            javaHomeReal = fs.realPath(javaHome);
        } catch (IOException e) {
            return Result.fail("The JDK home's real path could not be resolved: " + javaHome);
        }
        if (!fs.isDirectory(javaHomeReal)) return Result.fail("The JDK home is not a directory: " + javaHomeReal);
        String where = locationProblem(javaHome, javaHomeReal, roots, flavor);
        if (where != null) return Result.fail("The JDK " + where);
        String javaName = flavor == JoustPaths.Flavor.WINDOWS ? "java.exe" : "java";
        Exe java = checkExe("JAVA_HOME's java", join(join(javaHomeReal, "bin", flavor), javaName, flavor), roots, fs, flavor);
        if (java.error() != null) return Result.fail(java.error());

        // PATH: only the validated directories, in this order.
        List<String> candidates = new ArrayList<>();
        if (node != null) candidates.add(parent(node.real(), flavor));
        candidates.add(parent(joust.real(), flavor));
        int required = candidates.size();
        candidates.addAll(systemDirs(ide, flavor));
        String separator = pathSeparator(flavor);
        List<String> dirs = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            String dir = candidates.get(i);
            String real = checkDir(dir, roots, fs, flavor);
            if (real != null && (dir.contains(separator) || real.contains(separator))) {
                // M2-CDX-10: the CLI splits PATH again; such an entry would become unchecked directories.
                if (i < required) {
                    return Result.fail("The directory " + dir + " cannot be on the PATH of joust new: its name contains the "
                            + "path separator \"" + separator + "\". Install the tool in a directory without it.");
                }
                continue;
            }
            if (real == null) {
                if (i < required) return Result.fail("The directory " + dir + " cannot be on the PATH of joust new.");
                continue; // a missing or disallowed system directory is left out
            }
            if (dirs.stream().noneMatch(d -> same(d, real, flavor))) dirs.add(real);
        }

        // Every permitted directory: no node/npm/npx/java whose real target is disallowed.
        for (String dir : dirs) {
            String problem = scanDir(dir, roots, fs, flavor);
            if (problem != null) return Result.fail(problem);
        }

        // The executables the CLI will actually pick on that PATH.
        String npmReal = null;
        String nodeReal = null;
        if (node != null) {
            String nodeDir = dirs.get(0);
            String pickedNode = which(dirs, "node", fs, flavor);
            if (pickedNode == null || !same(real(pickedNode, fs), node.real(), flavor)) {
                return Result.fail("joust new would not run the configured Node " + node.real() + " (it would run "
                        + pickedNode + ").");
            }
            nodeReal = node.real();
            String pickedNpm = which(dirs, "npm", fs, flavor);
            if (pickedNpm == null || !same(parent(pickedNpm, flavor), nodeDir, flavor)) {
                return Result.fail("No npm was found beside the configured Node in " + nodeDir + "; the "
                        + in.template().cliName() + " template needs the npm that came with that Node.");
            }
            Exe npm = checkExe("npm", pickedNpm, roots, fs, flavor);
            if (npm.error() != null) return Result.fail(npm.error());
            npmReal = npm.real();
        }

        Map<String, String> env = new LinkedHashMap<>();
        for (String name : PASSED_VARIABLES) {
            if (TEMP_VARIABLES.contains(name)) continue;
            String v = get(ide, name, flavor);
            if (v == null || v.isEmpty()) continue;
            if (PATH_VARIABLES.contains(name) && !pathVariableAllowed(v, roots, fs, flavor)) continue;
            env.put(name, v);
        }
        if (in.tempDir() != null) {
            String temp = in.tempDir();
            if (!JoustPaths.isAbsolute(temp, flavor) || !fs.exists(temp) || !pathVariableAllowed(temp, roots, fs, flavor)) {
                return Result.fail("The private temporary directory for joust new cannot be used: " + temp);
            }
            env.put("TMPDIR", temp);
            if (flavor == JoustPaths.Flavor.WINDOWS) {
                env.put("TEMP", temp);
                env.put("TMP", temp);
            }
        }
        String joined = String.join(separator, dirs);
        // What the CLI will split is exactly what was validated, in order (M2-CDX-10).
        if (!Arrays.asList(joined.split(Pattern.quote(separator), -1)).equals(dirs)) {
            return Result.fail("The PATH of joust new could not be built from the validated directories: " + joined);
        }
        env.put("PATH", joined);
        env.put("JAVA_HOME", javaHomeReal);
        return new Result(Collections.unmodifiableMap(env), List.copyOf(dirs), joust.real(), nodeReal, npmReal, java.real(), null);
    }

    /** The system directories for the flavor: Unix's four, or Windows' {@code %SystemRoot%} directories. */
    static List<String> systemDirs(Map<String, String> ide, JoustPaths.Flavor flavor) {
        if (flavor != JoustPaths.Flavor.WINDOWS) return UNIX_SYSTEM_DIRS;
        String root = get(ide, "SystemRoot", flavor);
        if (root == null || !JoustPaths.isAbsolute(root, flavor)) root = get(ide, "windir", flavor);
        if (root == null || !JoustPaths.isAbsolute(root, flavor)) root = "C:\\Windows";
        String sys32 = join(root, "System32", flavor);
        return List.of(sys32, root, join(sys32, "Wbem", flavor), join(join(sys32, "WindowsPowerShell", flavor), "v1.0", flavor));
    }

    /** D4's checks on an executable: absolute, no script shim, an executable regular file, outside every forbidden place. */
    private static Exe checkExe(String what, String configured, Collection<String> roots, FileView fs, JoustPaths.Flavor flavor) {
        if (configured == null || configured.isBlank()) return new Exe(null, null, what + " is not configured.");
        String s = configured.trim();
        if (!JoustPaths.isAbsolute(s, flavor)) return new Exe(null, null, what + " must be an absolute path, not \"" + s + "\".");
        if (JoustPaths.hasRefusedExtension(s)) return new Exe(null, null, what + " must not be a .cmd, .bat or .ps1 script: " + s);
        if (!fs.exists(s)) return new Exe(null, null, what + " was not found at " + s + ".");
        String real;
        try {
            real = fs.realPath(s);
        } catch (IOException e) {
            return new Exe(null, null, what + "'s real path could not be resolved: " + s);
        }
        if (JoustPaths.hasRefusedExtension(real)) {
            return new Exe(null, null, what + " resolves to a script shim, which is never run: " + real);
        }
        if (!fs.isRegularFile(real)) return new Exe(null, null, what + " must be a file: " + real);
        if (!fs.isExecutable(real)) return new Exe(null, null, what + " is not executable: " + real);
        String where = locationProblem(s, real, roots, flavor);
        if (where != null) return new Exe(null, null, what + " " + where);
        return new Exe(s, real, null);
    }

    /** The directory's real path when it may be on PATH, else {@code null}. */
    private static String checkDir(String dir, Collection<String> roots, FileView fs, JoustPaths.Flavor flavor) {
        if (dir == null || !JoustPaths.isAbsolute(dir, flavor) || !fs.exists(dir)) return null;
        String real;
        try {
            real = fs.realPath(dir);
        } catch (IOException e) {
            return null;
        }
        if (!fs.isDirectory(real)) return null;
        return locationProblem(dir, real, roots, flavor) == null ? real : null;
    }

    /** The problem with the first probed entry of {@code dir} whose path or real target is disallowed, or {@code null}. */
    private static String scanDir(String dir, Collection<String> roots, FileView fs, JoustPaths.Flavor flavor) {
        List<String> names;
        try {
            names = fs.list(dir);
        } catch (IOException e) {
            return "The directory " + dir + " could not be read: " + e.getMessage();
        }
        for (String name : names) {
            if (!isProbedName(name, flavor)) continue;
            String entry = join(dir, name, flavor);
            String real;
            try {
                real = fs.realPath(entry);
            } catch (IOException e) {
                continue; // a dangling link cannot be run
            }
            String where = locationProblem(entry, real, roots, flavor);
            if (where != null) {
                return "The PATH directory " + dir + " holds " + name + ", which resolves to " + real
                        + "; it " + where + ". joust new is not run with that directory.";
            }
            if (JoustPaths.hasRefusedExtension(real) && !JoustPaths.hasRefusedExtension(entry)) {
                return "The PATH directory " + dir + " holds " + name + ", which resolves to the script shim " + real + ".";
            }
        }
        return null;
    }

    /** True for {@code node}, {@code npm}, {@code npx} and {@code java}: exact on Linux, any case on macOS and Windows. */
    static boolean isProbedName(String name, JoustPaths.Flavor flavor) {
        if (flavor == JoustPaths.Flavor.UNIX) return PROBED_NAMES.contains(name);
        String n = name.toLowerCase(Locale.ROOT);
        if (flavor == JoustPaths.Flavor.WINDOWS) {
            int end = n.length();
            while (end > 0 && (n.charAt(end - 1) == '.' || n.charAt(end - 1) == ' ')) end--;
            n = n.substring(0, end);
            for (String ext : WINDOWS_EXTENSIONS) {
                if (!ext.isEmpty() && n.endsWith(ext)) {
                    n = n.substring(0, n.length() - ext.length());
                    break;
                }
            }
        }
        return PROBED_NAMES.contains(n);
    }

    /**
     * What the CLI's lookup ({@code Sys.which} at JOUST {@code v0.1.0-pre2}) finds: the first directory holding an
     * executable regular file of that name ({@code name.exe} also on Windows).
     */
    static String which(List<String> dirs, String name, FileView fs, JoustPaths.Flavor flavor) {
        List<String> names = flavor == JoustPaths.Flavor.WINDOWS ? List.of(name, name + ".exe") : List.of(name);
        for (String dir : dirs) {
            for (String n : names) {
                String p = join(dir, n, flavor);
                if (!fs.exists(p)) continue;
                String r = real(p, fs);
                if (r != null && fs.isRegularFile(r) && fs.isExecutable(r)) return p;
            }
        }
        return null;
    }

    /** D4's location rule, by {@link JoustToolValidator#locationProblem} on both the selected path and its real target. */
    static String locationProblem(String selected, String real, Collection<String> roots, JoustPaths.Flavor flavor) {
        List<Path> rootPaths = new ArrayList<>();
        try {
            for (String r : roots) rootPaths.add(Path.of(r));
            return JoustToolValidator.locationProblem(Path.of(selected), Path.of(real), rootPaths, flavor);
        } catch (RuntimeException e) {
            return "is not a valid path: " + selected;
        }
    }

    /** The PATH separator the CLI splits on: {@code ;} on Windows, {@code :} elsewhere. */
    static String pathSeparator(JoustPaths.Flavor flavor) {
        return flavor == JoustPaths.Flavor.WINDOWS ? ";" : ":";
    }

    /**
     * A directory-valued variable is passed only when it is absolute and, resolved through its nearest existing
     * ancestor's real path (§12 E12), neither the value nor that destination lies inside a forbidden root, by spelling
     * or by file identity (M2-CDX-11). A symlink into a project is therefore dropped, not only a project spelling.
     */
    static boolean pathVariableAllowed(String value, Collection<String> roots, FileView fs, JoustPaths.Flavor flavor) {
        if (!JoustPaths.isAbsolute(value, flavor)) return false;
        String existing = value;
        while (!fs.exists(existing)) {
            String up = parent(existing, flavor);
            if (up.equals(existing)) return false;
            existing = up;
        }
        String real;
        try {
            real = fs.realPath(existing);
        } catch (IOException e) {
            return false;
        }
        String rest = value.length() > existing.length() ? value.substring(existing.length()) : "";
        while (rest.startsWith("/") || rest.startsWith("\\")) rest = rest.substring(1);
        String destination = rest.isEmpty() ? real : join(real, rest, flavor);
        if (locationProblem(value, destination, roots, flavor) != null) return false;
        for (String r : roots) {
            if (fs.insideByIdentity(real, r)) return false;
        }
        return true;
    }

    private static String real(String p, FileView fs) {
        try {
            return fs.realPath(p);
        } catch (IOException e) {
            return null;
        }
    }

    static boolean same(String a, String b, JoustPaths.Flavor flavor) {
        return a != null && b != null && JoustPaths.isInside(a, b, flavor) && JoustPaths.isInside(b, a, flavor);
    }

    static String join(String dir, String name, JoustPaths.Flavor flavor) {
        String sep = flavor == JoustPaths.Flavor.WINDOWS ? "\\" : "/";
        return dir.endsWith("/") || dir.endsWith("\\") ? dir + name : dir + sep + name;
    }

    static String parent(String path, JoustPaths.Flavor flavor) {
        int cut = flavor == JoustPaths.Flavor.WINDOWS ? Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) : path.lastIndexOf('/');
        if (cut < 0) return path;
        if (cut == 0) return path.substring(0, 1);
        if (flavor == JoustPaths.Flavor.WINDOWS && cut == 2 && path.charAt(1) == ':') return path.substring(0, 3);
        return path.substring(0, cut);
    }

    private static String get(Map<String, String> env, String name, JoustPaths.Flavor flavor) {
        String v = env.get(name);
        if (v != null || flavor != JoustPaths.Flavor.WINDOWS) return v;
        for (Map.Entry<String, String> e : env.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }
}
