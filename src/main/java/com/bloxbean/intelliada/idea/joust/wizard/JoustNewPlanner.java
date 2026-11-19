package com.bloxbean.intelliada.idea.joust.wizard;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustPrivateTemp;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.core.NodeIdentity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * ADR-004 D9: everything the wizard decides before it runs {@code joust new}, without the IDE and without executing
 * anything.
 *
 * <ol>
 *   <li>The native {@code joust} (application settings only, I7) passes D4's rule and still has the identity recorded
 *   when the user saved it (real path, size, modification time, SHA-256); otherwise the user validates it again.</li>
 *   <li>For the Node and web templates, the configured Node passes D4's rule and has its recorded identity.</li>
 *   <li>The target is an absolute directory that does not exist or is empty (the CLI's own rule, JOUST
 *   {@code Scaffold.check}).</li>
 *   <li>The environment is built by {@link JoustNewEnvironment}, with the target added to the forbidden roots, and the
 *   command line by {@link JoustNewCommand} on the validated real path of {@code joust}.</li>
 *   <li>The working directory, which is also the child's {@code TMPDIR} (JOUST's {@code Scaffold} extracts the npm
 *   package there), is a private directory created by {@link JoustPrivateTemp} under the given base only after the base
 *   is checked against every forbidden root (§12 E12, M2-CDX-11). The caller deletes it after the run.</li>
 * </ol>
 *
 * <p>{@link #plan} reads the tools' contents to check their recorded SHA-256 and so must not run on the EDT;
 * {@link #preflight}, for the wizard page, compares only the recorded real path, size and modification time
 * (M2-CDX-17).
 */
public final class JoustNewPlanner {

    private JoustNewPlanner() {
    }

    /** What the user chose in the wizard. {@code pkg} is ignored unless the template is Gradle. */
    public record Request(JoustTemplate template, JoustLanguage lang, String pkg, String targetDir, String javaHome) {
    }

    /** The application settings the plan reads (D4, D9). */
    public record ToolSettings(String joustCliPath, NodeIdentity joustCli, String nodePath, NodeIdentity node) {
    }

    /** The command, its environment and working directory; or why there is none. */
    public record Plan(List<String> command, Map<String, String> env, Path workDir, JoustNewEnvironment.Result tools,
                       String error) {
        public boolean ok() {
            return error == null;
        }

        static Plan fail(String error) {
            return new Plan(null, null, null, null, error);
        }
    }

    /**
     * The checks that do not need the target directory, for the wizard page: an error, or {@code null}. Cheap enough for
     * the EDT: the tools' identities are compared by metadata only; {@link #plan} checks their contents at Create.
     */
    public static String preflight(Request r, ToolSettings s, Map<String, String> ideEnv, Collection<Path> forbiddenRoots,
                                   JoustPaths.Flavor flavor) {
        String problem = toolProblem(r, s, forbiddenRoots, flavor, false);
        if (problem != null) return problem;
        if (r.template().takesPackage() && r.pkg() != null && !r.pkg().isEmpty()) {
            String p = JoustNewCommand.packageProblem(r.pkg());
            if (p != null) return p;
        }
        JoustNewEnvironment.Result env = JoustNewEnvironment.build(inputs(r, s, ideEnv, roots(forbiddenRoots, null), null),
                JoustNewEnvironment.localFiles(), flavor);
        return env.ok() ? null : env.error();
    }

    /**
     * The full plan. On success {@link Plan#workDir()} is a new private directory under {@code tempBase}, which the
     * caller removes ({@link JoustPrivateTemp#deleteTree}) after the run. Not on the EDT: it hashes the tools.
     */
    public static Plan plan(Request r, ToolSettings s, Map<String, String> ideEnv, Collection<Path> forbiddenRoots,
                            Path tempBase, JoustPaths.Flavor flavor) {
        if (r == null || r.template() == null || r.lang() == null) return Plan.fail("Choose a template and a language.");
        String problem = toolProblem(r, s, forbiddenRoots, flavor, true);
        if (problem != null) return Plan.fail(problem);

        String target = r.targetDir();
        if (target == null || !JoustPaths.isAbsolute(target, flavor)) {
            return Plan.fail("The project directory must be an absolute path: " + target);
        }
        Path t = Path.of(target);
        if (Files.exists(t, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(t, LinkOption.NOFOLLOW_LINKS)) return Plan.fail(target + " exists and is not a directory.");
            try (Stream<Path> entries = Files.list(t)) {
                if (entries.findAny().isPresent()) {
                    return Plan.fail("joust new creates a project only in a new or empty directory, and " + target
                            + " is not empty.");
                }
            } catch (IOException e) {
                return Plan.fail("The project directory could not be read: " + e.getMessage());
            }
        }

        Collection<String> roots = roots(forbiddenRoots, t);
        JoustNewEnvironment.Result env = JoustNewEnvironment.build(inputs(r, s, ideEnv, roots, null),
                JoustNewEnvironment.localFiles(), flavor);
        if (!env.ok()) return Plan.fail(env.error());
        List<String> command;
        try {
            command = JoustNewCommand.build(env.joust(), target, r.template(), r.lang(),
                    r.template().takesPackage() ? r.pkg() : null, flavor);
        } catch (IllegalArgumentException e) {
            return Plan.fail(e.getMessage());
        }
        if (tempBase == null) return Plan.fail("No temporary directory for joust new.");
        List<Path> rootPaths = new ArrayList<>();
        for (String root : roots) rootPaths.add(Path.of(root));
        Path workDir;
        try {
            workDir = JoustPrivateTemp.create(tempBase, "intelliada-joust-new-", rootPaths, flavor);
        } catch (IOException | RuntimeException e) {
            return Plan.fail("No private working directory for joust new: " + e.getMessage());
        }
        env = JoustNewEnvironment.build(inputs(r, s, ideEnv, roots, workDir.toString()), JoustNewEnvironment.localFiles(), flavor);
        if (!env.ok()) {
            JoustPrivateTemp.deleteTree(workDir);
            return Plan.fail(env.error());
        }
        return new Plan(command, env.env(), workDir, env, null);
    }

    private static String toolProblem(Request r, ToolSettings s, Collection<Path> forbiddenRoots, JoustPaths.Flavor flavor,
                                      boolean hash) {
        if (r.template() == null || r.lang() == null) return "Choose a template and a language.";
        if (s == null || s.joustCliPath() == null || s.joustCliPath().isBlank()) {
            return "Configure the native joust in Settings | Tools | JOUST.";
        }
        JoustToolValidator.Result cli = JoustToolValidator.checkExecutable(s.joustCliPath(), "joust", forbiddenRoots, flavor);
        if (!cli.ok()) return cli.error();
        String changed = identityProblem("joust", cli.realPath(), s.joustCli(), hash);
        if (changed != null) return changed;
        if (r.template().usesNpm()) {
            JoustToolValidator.Result node = JoustToolValidator.checkNode(s.nodePath(), forbiddenRoots, flavor);
            if (!node.ok()) {
                return "The " + r.template().cliName() + " template needs Node 22 or later with npm, configured in "
                        + "Settings | Tools | JOUST: " + node.error();
            }
            changed = identityProblem("Node", node.realPath(), s.node(), hash);
            if (changed != null) return changed;
        }
        if (r.javaHome() == null || r.javaHome().isBlank()) return "Choose a JDK.";
        return null;
    }

    /**
     * D4: the file must be the one the user validated on Save; checked by reading it, never by running it. With
     * {@code hash} false only the real path, size and modification time are compared (no read of the contents).
     */
    static String identityProblem(String what, Path real, NodeIdentity expected, boolean hash) {
        if (expected == null) {
            return what + " has not been validated. Open Settings | Tools | JOUST and save it.";
        }
        boolean same;
        try {
            if (hash) {
                same = JoustToolValidator.identityOf(real).equals(expected);
            } else {
                same = real.toString().equals(expected.realPath()) && Files.size(real) == expected.size()
                        && Files.getLastModifiedTime(real).toMillis() == expected.modifiedMillis();
            }
        } catch (IOException e) {
            return what + " could not be read: " + e.getMessage();
        }
        if (!same) {
            return "The " + what + " binary changed since it was validated (" + real + "). Validate it again in "
                    + "Settings | Tools | JOUST.";
        }
        return null;
    }

    private static JoustNewEnvironment.Inputs inputs(Request r, ToolSettings s, Map<String, String> ideEnv,
                                                     Collection<String> roots, String tempDir) {
        return new JoustNewEnvironment.Inputs(s.joustCliPath(), r.template().usesNpm() ? s.nodePath() : null, r.javaHome(),
                r.template(), ideEnv, roots, tempDir);
    }

    /** The forbidden roots as strings, with the new project's directory (and its real path, when it exists) added. */
    private static Collection<String> roots(Collection<Path> forbiddenRoots, Path target) {
        Set<String> out = new LinkedHashSet<>();
        for (Path p : forbiddenRoots) out.add(p.toString());
        if (target != null) {
            out.add(target.toAbsolutePath().normalize().toString());
            try {
                out.add(target.toRealPath().toString());
            } catch (IOException ignored) {
                // not created yet
            }
        }
        return new ArrayList<>(out);
    }
}
