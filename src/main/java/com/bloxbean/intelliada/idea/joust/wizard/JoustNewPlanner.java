package com.bloxbean.intelliada.idea.joust.wizard;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
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
 * </ol>
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

    /** The checks that do not need the target directory, for the wizard page: an error, or {@code null}. */
    public static String preflight(Request r, ToolSettings s, Map<String, String> ideEnv, Collection<Path> forbiddenRoots,
                                   JoustPaths.Flavor flavor) {
        String problem = toolProblem(r, s, forbiddenRoots, flavor);
        if (problem != null) return problem;
        if (r.template().takesPackage() && r.pkg() != null && !r.pkg().isEmpty()) {
            String p = JoustNewCommand.packageProblem(r.pkg());
            if (p != null) return p;
        }
        JoustNewEnvironment.Result env = JoustNewEnvironment.build(inputs(r, s, ideEnv, roots(forbiddenRoots, null)),
                JoustNewEnvironment.localFiles(), flavor);
        return env.ok() ? null : env.error();
    }

    public static Plan plan(Request r, ToolSettings s, Map<String, String> ideEnv, Collection<Path> forbiddenRoots,
                            Path workDir, JoustPaths.Flavor flavor) {
        if (r == null || r.template() == null || r.lang() == null) return Plan.fail("Choose a template and a language.");
        String problem = toolProblem(r, s, forbiddenRoots, flavor);
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

        JoustNewEnvironment.Result env = JoustNewEnvironment.build(inputs(r, s, ideEnv, roots(forbiddenRoots, t)),
                JoustNewEnvironment.localFiles(), flavor);
        if (!env.ok()) return Plan.fail(env.error());
        List<String> command;
        try {
            command = JoustNewCommand.build(env.joust(), target, r.template(), r.lang(),
                    r.template().takesPackage() ? r.pkg() : null, flavor);
        } catch (IllegalArgumentException e) {
            return Plan.fail(e.getMessage());
        }
        if (workDir == null || !Files.isDirectory(workDir)) return Plan.fail("No working directory for joust new.");
        return new Plan(command, env.env(), workDir, env, null);
    }

    private static String toolProblem(Request r, ToolSettings s, Collection<Path> forbiddenRoots, JoustPaths.Flavor flavor) {
        if (r.template() == null || r.lang() == null) return "Choose a template and a language.";
        if (s == null || s.joustCliPath() == null || s.joustCliPath().isBlank()) {
            return "Configure the native joust in Settings | Tools | JOUST.";
        }
        JoustToolValidator.Result cli = JoustToolValidator.checkExecutable(s.joustCliPath(), "joust", forbiddenRoots, flavor);
        if (!cli.ok()) return cli.error();
        String changed = identityProblem("joust", cli.realPath(), s.joustCli());
        if (changed != null) return changed;
        if (r.template().usesNpm()) {
            JoustToolValidator.Result node = JoustToolValidator.checkNode(s.nodePath(), forbiddenRoots, flavor);
            if (!node.ok()) {
                return "The " + r.template().cliName() + " template needs Node 22 or later with npm, configured in "
                        + "Settings | Tools | JOUST: " + node.error();
            }
            changed = identityProblem("Node", node.realPath(), s.node());
            if (changed != null) return changed;
        }
        if (r.javaHome() == null || r.javaHome().isBlank()) return "Choose a JDK.";
        return null;
    }

    /** D4: the file must be the one the user validated on Save; checked by reading it, never by running it. */
    static String identityProblem(String what, Path real, NodeIdentity expected) {
        if (expected == null) {
            return what + " has not been validated. Open Settings | Tools | JOUST and save it.";
        }
        NodeIdentity actual;
        try {
            actual = JoustToolValidator.identityOf(real);
        } catch (IOException e) {
            return what + " could not be read: " + e.getMessage();
        }
        if (!actual.equals(expected)) {
            return "The " + what + " binary changed since it was validated (" + real + "). Validate it again in "
                    + "Settings | Tools | JOUST.";
        }
        return null;
    }

    private static JoustNewEnvironment.Inputs inputs(Request r, ToolSettings s, Map<String, String> ideEnv, Collection<String> roots) {
        return new JoustNewEnvironment.Inputs(s.joustCliPath(), r.template().usesNpm() ? s.nodePath() : null, r.javaHome(),
                r.template(), ideEnv, roots);
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
