package com.bloxbean.intelliada.idea.julc.annotator.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates that Java source uses only the supported subset for on-chain compilation.
 * Rejects unsupported constructs with descriptive error messages and suggestions.
 *
 * Adapted from julc-compiler's SubsetValidator for use in IntelliAda.
 * This class can be removed once julc-compiler is available as a JVM 21 compatible dependency.
 *
 * @see <a href="https://github.com/bloxbean/julc">julc project</a>
 */
public class JulcSubsetValidator extends VoidVisitorAdapter<Void> {

    private final List<JulcDiagnostic> diagnostics = new ArrayList<>();
    private String fileName = "<unknown>";
    private int forEachDepth = 0;
    private int whileDepth = 0;

    public List<JulcDiagnostic> validate(CompilationUnit cu) {
        diagnostics.clear();
        cu.getStorage().ifPresent(s -> fileName = s.getFileName());
        cu.accept(this, null);
        return List.copyOf(diagnostics);
    }

    private void error(com.github.javaparser.ast.Node node, String message) {
        error(node, message, null);
    }

    private void error(com.github.javaparser.ast.Node node, String message, String suggestion) {
        int line = node.getBegin().map(p -> p.line).orElse(0);
        int col = node.getBegin().map(p -> p.column).orElse(0);
        diagnostics.add(new JulcDiagnostic(
                JulcDiagnostic.Level.ERROR, message, fileName, line, col, suggestion));
    }

    private void warning(com.github.javaparser.ast.Node node, String message, String suggestion) {
        int line = node.getBegin().map(p -> p.line).orElse(0);
        int col = node.getBegin().map(p -> p.column).orElse(0);
        diagnostics.add(new JulcDiagnostic(
                JulcDiagnostic.Level.WARNING, message, fileName, line, col, suggestion));
    }

    // --- Rejected statements ---

    @Override
    public void visit(TryStmt n, Void arg) {
        error(n, "try/catch is not supported on-chain",
                "Use if/else checks instead of exception handling");
        super.visit(n, arg);
    }

    @Override
    public void visit(ThrowStmt n, Void arg) {
        error(n, "throw is not supported on-chain",
                "Return false from the validator to reject a transaction");
        super.visit(n, arg);
    }

    @Override
    public void visit(SynchronizedStmt n, Void arg) {
        error(n, "synchronized is not supported on-chain",
                "On-chain code is single-threaded; remove synchronized blocks");
        super.visit(n, arg);
    }

    @Override
    public void visit(ForStmt n, Void arg) {
        error(n, "C-style for loops are not supported on-chain",
                "Use for-each over a list or while loops instead");
        super.visit(n, arg);
    }

    @Override
    public void visit(ForEachStmt n, Void arg) {
        forEachDepth++;
        super.visit(n, arg);
        forEachDepth--;
    }

    @Override
    public void visit(BreakStmt n, Void arg) {
        if (forEachDepth == 0 && whileDepth == 0) {
            error(n, "break is only supported inside for-each or while loops on-chain",
                    "Use for-each or while with an accumulator and break to exit early");
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(WhileStmt n, Void arg) {
        whileDepth++;
        super.visit(n, arg);
        whileDepth--;
    }

    @Override
    public void visit(DoStmt n, Void arg) {
        error(n, "do-while loops are not supported on-chain",
                "Use while loops or for-each instead");
        super.visit(n, arg);
    }

    // --- Rejected expressions ---

    private static final Set<String> FUNCTIONAL_TYPES = Set.of(
            "Function", "BiFunction", "UnaryOperator", "BinaryOperator",
            "Predicate", "BiPredicate", "Consumer", "BiConsumer",
            "Supplier", "Runnable");

    private static final Set<String> FUNCTIONAL_METHODS = Set.of(
            "apply", "test", "accept", "get", "run");

    @Override
    public void visit(MethodReferenceExpr n, Void arg) {
        error(n, "Method references (::) are not supported on-chain",
                "Use a regular static method call instead");
        super.visit(n, arg);
    }

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        if (FUNCTIONAL_METHODS.contains(n.getNameAsString())
                && n.getScope().isPresent()
                && isLikelyFunctionalVariable(n)) {
            error(n, "Functional interface ." + n.getNameAsString()
                            + "() is not supported on-chain",
                    "Call the method directly instead of through a function reference");
        }
        super.visit(n, arg);
    }

    private boolean isLikelyFunctionalVariable(MethodCallExpr call) {
        if (!(call.getScope().get() instanceof NameExpr nameExpr)) return false;
        String varName = nameExpr.getNameAsString();
        return call.findAncestor(com.github.javaparser.ast.body.MethodDeclaration.class)
                .map(method -> method.findAll(VariableDeclarator.class).stream()
                        .anyMatch(v -> v.getNameAsString().equals(varName)
                                && FUNCTIONAL_TYPES.contains(rawTypeName(v.getTypeAsString()))))
                .orElse(false);
    }

    private static String rawTypeName(String typeStr) {
        int idx = typeStr.indexOf('<');
        return idx >= 0 ? typeStr.substring(0, idx) : typeStr;
    }

    @Override
    public void visit(NullLiteralExpr n, Void arg) {
        error(n, "null is not supported on-chain",
                "Use Optional<T> to represent absence of a value");
        super.visit(n, arg);
    }

    @Override
    public void visit(ThisExpr n, Void arg) {
        error(n, "'this' is not supported on-chain",
                "On-chain validators are stateless; use static methods instead");
        super.visit(n, arg);
    }

    @Override
    public void visit(SuperExpr n, Void arg) {
        error(n, "'super' is not supported on-chain",
                "Use sealed interfaces and pattern matching instead of inheritance");
        super.visit(n, arg);
    }

    @Override
    public void visit(ArrayCreationExpr n, Void arg) {
        if (n.getElementType().isPrimitiveType()
                && n.getElementType().asPrimitiveType().getType() == PrimitiveType.Primitive.BYTE
                && n.getInitializer().isPresent()) {
            boolean allLiterals = n.getInitializer().get().getValues().stream()
                    .allMatch(Expression::isIntegerLiteralExpr);
            if (allLiterals) {
                super.visit(n, arg);
                return;
            }
        }
        error(n, "arrays are not supported on-chain",
                "Use List<T> instead of arrays, or byte[] with literal initializers");
        super.visit(n, arg);
    }

    @Override
    public void visit(ArrayAccessExpr n, Void arg) {
        error(n, "array access is not supported on-chain",
                "Use List<T> with list operations instead");
        super.visit(n, arg);
    }

    // --- Rejected types ---

    @Override
    public void visit(PrimitiveType n, Void arg) {
        var type = n.getType();
        if (type == PrimitiveType.Primitive.FLOAT || type == PrimitiveType.Primitive.DOUBLE) {
            error(n, "floating point types (float/double) are not supported on-chain",
                    "Use BigInteger for integer arithmetic");
        }
        super.visit(n, arg);
    }

    // --- Unreachable code detection ---

    @Override
    public void visit(BlockStmt n, Void arg) {
        checkUnreachableCode(n.getStatements());
        super.visit(n, arg);
    }

    private void checkUnreachableCode(NodeList<Statement> statements) {
        for (int i = 0; i < statements.size() - 1; i++) {
            if (isTerminal(statements.get(i))) {
                warning(statements.get(i + 1),
                        "Unreachable code after " + describeTerminal(statements.get(i))
                                + ". This code will never execute.", null);
                break;
            }
        }
    }

    private boolean isTerminal(Statement stmt) {
        if (stmt instanceof ReturnStmt) return true;
        if (stmt instanceof BreakStmt) return true;
        if (stmt instanceof ExpressionStmt es
                && es.getExpression() instanceof MethodCallExpr mce
                && "error".equals(mce.getNameAsString())
                && mce.getScope().filter(s -> s.toString().equals("Builtins")).isPresent()) {
            return true;
        }
        return false;
    }

    private String describeTerminal(Statement stmt) {
        if (stmt instanceof ReturnStmt) return "return";
        if (stmt instanceof BreakStmt) return "break";
        return "Builtins.error()";
    }

    // --- Rejected class features ---

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Void arg) {
        if (!n.getExtendedTypes().isEmpty()) {
            for (var ext : n.getExtendedTypes()) {
                if (!ext.getNameAsString().equals("Object")) {
                    error(n, "class inheritance is not supported on-chain",
                            "Use sealed interfaces with record variants instead");
                }
            }
        }
        super.visit(n, arg);
    }
}
