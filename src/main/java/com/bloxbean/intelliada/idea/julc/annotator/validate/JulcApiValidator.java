package com.bloxbean.intelliada.idea.julc.annotator.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates that Java source only uses APIs available on-chain.
 * Checks import statements and constructor calls against a blocklist of java.* packages
 * that are not available in the Plutus VM.
 *
 * User's own classes, julc stdlib/ledger types, and @OnchainLibrary dependencies are allowed.
 * Only core java.* packages known to be unavailable on-chain are blocked.
 */
public class JulcApiValidator extends VoidVisitorAdapter<Void> {

    private final List<JulcDiagnostic> diagnostics = new ArrayList<>();
    private String fileName = "<unknown>";

    /**
     * Java packages that are NOT available on-chain.
     * Only block packages where usage is clearly a mistake.
     */
    private static final Set<String> BLOCKED_PACKAGES = Set.of(
            "java.io",
            "java.net",
            "java.nio",
            "java.sql",
            "java.awt",
            "java.swing",
            "javax.swing",
            "javax.net",
            "javax.crypto",
            "java.util.concurrent",
            "java.util.stream",
            "java.util.regex",
            "java.lang.reflect",
            "java.lang.invoke",
            "java.text",
            "java.time"
    );

    /**
     * Specific java.* classes that are blocked (classes in otherwise allowed packages).
     */
    private static final Set<String> BLOCKED_CLASSES = Set.of(
            "java.lang.Thread",
            "java.lang.Runtime",
            "java.lang.System",
            "java.lang.ProcessBuilder",
            "java.lang.ClassLoader",
            "java.util.HashMap",
            "java.util.LinkedHashMap",
            "java.util.TreeMap",
            "java.util.HashSet",
            "java.util.LinkedHashSet",
            "java.util.TreeSet",
            "java.util.ArrayList",
            "java.util.LinkedList",
            "java.util.ArrayDeque",
            "java.util.Collections",
            "java.util.Arrays",
            "java.util.Scanner",
            "java.util.Random"
    );

    /**
     * Java packages/classes that ARE allowed on-chain.
     * These take precedence over blocked packages.
     */
    private static final Set<String> ALLOWED_CLASSES = Set.of(
            "java.math.BigInteger",
            "java.lang.String",
            "java.lang.Boolean",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Object",
            "java.lang.Comparable",
            "java.lang.Override",
            "java.lang.SuppressWarnings",
            "java.util.Optional",
            "java.util.List",
            "java.util.Map"
    );

    private static final Set<String> ALLOWED_PACKAGES = Set.of(
            "com.bloxbean.cardano.julc",    // all julc packages
            "org.junit"                      // test annotations
    );

    public List<JulcDiagnostic> validate(CompilationUnit cu) {
        diagnostics.clear();
        cu.getStorage().ifPresent(s -> fileName = s.getFileName());

        // Check import statements
        for (ImportDeclaration imp : cu.getImports()) {
            checkImport(imp);
        }

        // Check constructor calls for blocked types
        cu.accept(this, null);

        return List.copyOf(diagnostics);
    }

    private void checkImport(ImportDeclaration imp) {
        String importName = imp.getNameAsString();

        // Allow all non-java imports (user classes, julc types, etc.)
        if (!importName.startsWith("java.") && !importName.startsWith("javax.")) {
            return;
        }

        // Check explicit allowlist
        if (ALLOWED_CLASSES.contains(importName)) {
            return;
        }

        // Check allowed packages
        for (String allowed : ALLOWED_PACKAGES) {
            if (importName.startsWith(allowed)) return;
        }

        // Check blocked specific classes
        if (BLOCKED_CLASSES.contains(importName)) {
            int line = imp.getBegin().map(p -> p.line).orElse(0);
            int col = imp.getBegin().map(p -> p.column).orElse(0);
            diagnostics.add(new JulcDiagnostic(
                    JulcDiagnostic.Level.ERROR,
                    "'" + importName + "' is not available on-chain",
                    fileName, line, col,
                    getSuggestion(importName)));
            return;
        }

        // Check blocked packages
        for (String blocked : BLOCKED_PACKAGES) {
            if (importName.startsWith(blocked)) {
                int line = imp.getBegin().map(p -> p.line).orElse(0);
                int col = imp.getBegin().map(p -> p.column).orElse(0);
                diagnostics.add(new JulcDiagnostic(
                        JulcDiagnostic.Level.ERROR,
                        "'" + blocked + ".*' packages are not available on-chain",
                        fileName, line, col,
                        getSuggestion(importName)));
                return;
            }
        }

        // Wildcard imports from blocked packages
        if (imp.isAsterisk()) {
            String pkg = importName;
            for (String blocked : BLOCKED_PACKAGES) {
                if (pkg.equals(blocked)) {
                    int line = imp.getBegin().map(p -> p.line).orElse(0);
                    int col = imp.getBegin().map(p -> p.column).orElse(0);
                    diagnostics.add(new JulcDiagnostic(
                            JulcDiagnostic.Level.ERROR,
                            "'" + blocked + ".*' packages are not available on-chain",
                            fileName, line, col, null));
                    return;
                }
            }
        }
    }

    @Override
    public void visit(ObjectCreationExpr n, Void arg) {
        String typeName = n.getTypeAsString();
        // Check for direct usage of blocked simple class names
        if (SIMPLE_BLOCKED_NAMES.contains(typeName)) {
            int line = n.getBegin().map(p -> p.line).orElse(0);
            int col = n.getBegin().map(p -> p.column).orElse(0);
            diagnostics.add(new JulcDiagnostic(
                    JulcDiagnostic.Level.WARNING,
                    "'" + typeName + "' may not be available on-chain. Verify it is a record or @OnchainLibrary type.",
                    fileName, line, col, null));
        }
        super.visit(n, arg);
    }

    /**
     * Simple names of commonly misused classes (without package prefix).
     * These generate warnings, not errors, since they might be user-defined records.
     */
    private static final Set<String> SIMPLE_BLOCKED_NAMES = Set.of(
            "HashMap", "ArrayList", "LinkedList", "HashSet", "TreeMap",
            "Scanner", "Random", "Thread", "File", "FileInputStream",
            "FileOutputStream", "BufferedReader", "PrintWriter", "Socket"
    );

    private String getSuggestion(String className) {
        if (className.contains("HashMap") || className.contains("TreeMap"))
            return "Use julc Map type from julc-ledger-api instead";
        if (className.contains("ArrayList") || className.contains("LinkedList"))
            return "Use julc List type from julc-ledger-api instead";
        if (className.contains("HashSet") || className.contains("TreeSet"))
            return "Sets are not available on-chain; use List with contains()";
        if (className.contains("File") || className.contains("io"))
            return "File I/O is not available on-chain";
        if (className.contains("Thread") || className.contains("concurrent"))
            return "Threading is not available on-chain; validators are single-threaded";
        if (className.contains("System"))
            return "System.* is not available on-chain; use Builtins.trace() for debugging";
        if (className.contains("stream"))
            return "Streams are not available on-chain; use julc ListsLib (map, filter, foldl) instead";
        if (className.contains("Random"))
            return "Random is not available on-chain; use on-chain randomness from block hash";
        return "This API is not available in the Plutus VM";
    }
}
