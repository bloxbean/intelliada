package com.bloxbean.intelliada.idea.joust.wizard;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ADR-004 D9: the command line of the wizard, {@code joust new <empty dir> -t <template> -l <lang> [-p <package>]}, as
 * an argument list (never a shell string). Pure: it checks and assembles, and runs nothing.
 *
 * <p>{@code -p} is passed for the Gradle template only and refused for Node and web, as the CLI itself refuses it
 * (F27, JOUST {@code Scaffold.check}). The package is checked by the CLI's own rule ({@code Scaffold.checkPackage} at
 * JOUST {@code v0.1.0-pre2}): dot-separated Java identifiers of letters, digits and {@code _}, no keyword, no {@code _}
 * segment.
 */
public final class JoustNewCommand {

    /** A package segment that Java and {@code .jsc} both accept (JOUST {@code Scaffold.IDENT}). */
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int PACKAGE_CAP = 200;
    /** JOUST {@code Scaffold.KEYWORDS} at {@code v0.1.0-pre2}: Java's reserved and contextual keywords. */
    static final Set<String> KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long",
            "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile",
            "while", "true", "false", "null", "var", "yield", "record", "sealed", "permits", "non-sealed", "when",
            "module", "open", "exports", "requires", "uses", "provides", "to", "with", "opens", "transitive");

    private JoustNewCommand() {
    }

    /** Why {@code pkg} is not a Java package name, or {@code null} when it is one. */
    public static String packageProblem(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "The package is empty.";
        if (pkg.length() > PACKAGE_CAP) return "The package is longer than " + PACKAGE_CAP + " characters.";
        for (String seg : pkg.split("\\.", -1)) {
            if (!IDENT.matcher(seg).matches() || KEYWORDS.contains(seg) || seg.equals("_")) {
                return "'" + pkg + "' is not a Java package name (dot-separated identifiers of letters, digits and _, "
                        + "not keywords).";
            }
        }
        return null;
    }

    /**
     * The argument list. {@code joust} is the validated real path of the user's native {@code joust}; {@code target}
     * the absolute directory to create (new or empty). {@code pkg} must be {@code null} or empty unless the template is
     * Gradle; for Gradle an absent package is left to the CLI's default ({@code app}). A package is used exactly as
     * given: surrounding white space is refused, not trimmed.
     *
     * @throws IllegalArgumentException when any input is refused
     */
    public static List<String> build(String joust, String target, JoustTemplate template, JoustLanguage lang, String pkg,
                                     JoustPaths.Flavor flavor) {
        if (joust == null || !JoustPaths.isAbsolute(joust, flavor)) {
            throw new IllegalArgumentException("joust must be an absolute path: " + joust);
        }
        if (target == null || !JoustPaths.isAbsolute(target, flavor) || target.startsWith("-")) {
            throw new IllegalArgumentException("The project directory must be an absolute path: " + target);
        }
        if (template == null || lang == null) throw new IllegalArgumentException("A template and a language are required.");
        boolean hasPackage = pkg != null && !pkg.isEmpty();
        if (hasPackage && !template.takesPackage()) {
            throw new IllegalArgumentException("-p/--package is for the Gradle template only; the " + template.cliName()
                    + " template uses the package 'app' (F27).");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(joust);
        cmd.add("new");
        cmd.add(target);
        cmd.add("-t");
        cmd.add(template.cliName());
        cmd.add("-l");
        cmd.add(lang.cliName());
        if (hasPackage) {
            String problem = packageProblem(pkg);
            if (problem != null) throw new IllegalArgumentException(problem);
            cmd.add("-p");
            cmd.add(pkg);
        }
        return Collections.unmodifiableList(cmd);
    }

    /** The command line as one line for display only (never executed through a shell). */
    public static String display(List<String> command) {
        StringBuilder sb = new StringBuilder();
        for (String a : command) {
            if (sb.length() > 0) sb.append(' ');
            boolean plain = !a.isEmpty() && a.chars().allMatch(c -> Character.isLetterOrDigit(c) || "/\\:._-+@=,".indexOf(c) >= 0);
            sb.append(plain ? a : "'" + a.replace("'", "'\\''") + "'");
        }
        return sb.toString();
    }
}
