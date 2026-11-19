import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.julclang.state.processor.SubsetLint;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * IntelliAda ADR-004 M2 (D10): runs JOUST's own SubsetLint (julc-state-processor 0.1.0-pre2) over a corpus, the way
 * JOUST's embedders do: javac parses and attributes every file (JavacTask parse + analyze), then
 * {@code SubsetLint.lint(trees, type, reporter)} runs on each {@code @StateContract} class. Writes one line per
 * finding: path relative to the corpus, line, start offset, end offset, message (tab-separated). Exits 2 if the corpus
 * does not compile, since the typed pass needs attribution.
 * <p>
 * Launched by JoustSubsetLintDifferentialTest in a JDK 25 (the processor's class files are Java 25) as a source-file
 * program: {@code java -cp <lint classpath> SubsetLintHarness.java <corpus> <out> <classpath>}.
 */
public class SubsetLintHarness {

    static final String STATE_CONTRACT = "org.julclang.state.api.StateContract";

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toRealPath();
        Path out = Path.of(args[1]);
        String classpath = args[2];
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = javac.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) javac.getTask(null, fm, diagnostics,
                    List.of("-proc:none", "-classpath", classpath, "-encoding", "UTF-8"), null,
                    fm.getJavaFileObjectsFromPaths(files));
            Iterable<? extends CompilationUnitTree> units = task.parse();
            task.analyze();
            boolean failed = false;
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) {
                    System.err.println("javac: " + d);
                    failed = true;
                }
            }
            if (failed) System.exit(2);

            Trees trees = Trees.instance(task);
            List<String> findings = new ArrayList<>();
            int linted = 0;
            for (CompilationUnitTree unit : units) {
                String rel = root.relativize(Path.of(unit.getSourceFile().toUri()).toRealPath()).toString().replace('\\', '/');
                for (Tree decl : unit.getTypeDecls()) {
                    if (!(decl instanceof ClassTree)) continue;
                    Element element = trees.getElement(new TreePath(new TreePath(unit), decl));
                    if (!(element instanceof TypeElement type) || !isStateContract(type)) continue;
                    linted++;
                    SubsetLint.lint(trees, type, (t, node, cu, message) -> {
                        SourcePositions positions = t.getSourcePositions();
                        long start = positions.getStartPosition(cu, node);
                        long end = positions.getEndPosition(cu, node);
                        long line = cu.getLineMap().getLineNumber(start);
                        findings.add(rel + "\t" + line + "\t" + start + "\t" + end + "\t"
                                + message.replace('\t', ' ').replace('\n', ' '));
                    });
                }
            }
            Files.write(out, findings, StandardCharsets.UTF_8);
            System.out.println("linted " + linted + " @StateContract classes in " + files.size() + " files: "
                    + findings.size() + " findings");
        }
    }

    private static boolean isStateContract(TypeElement type) {
        return type.getAnnotationMirrors().stream().anyMatch(a -> ((TypeElement) a.getAnnotationType().asElement())
                .getQualifiedName().contentEquals(STATE_CONTRACT));
    }
}
