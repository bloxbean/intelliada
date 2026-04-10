package com.bloxbean.intelliada.idea.julc.annotator.validate;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime bridge to julc-compiler (full compilation) and julc-vm (local evaluation).
 *
 * Uses isolated URLClassLoaders to load Java 24+ bytecode shadow JARs at runtime on JBR 25.
 * Provides:
 * 1. Full compilation: source → UPLC Program (script hash, size, diagnostics)
 * 2. Local evaluation: Program → EvalResult (success/failure, budget, traces)
 *
 * Shadow JARs are loaded from:
 * - ~/.intelliada/julc-libs/ (user override, checked first)
 * - Plugin lib/julc/ (bundled default)
 */
public class JulcVmBridge {
    private static final Logger LOG = Logger.getInstance(JulcVmBridge.class);
    private static final String PLUGIN_ID = "com.bloxbean.intelliada";
    private static final String COMPILER_JAR = "julc-compiler-all.jar";
    private static final String VM_JAR = "julc-vm-java-all.jar";
    private static final String USER_LIB_DIR = System.getProperty("user.home")
            + File.separator + ".intelliada" + File.separator + "julc-libs";

    private static volatile boolean initialized = false;
    private static volatile boolean compilerAvailable = false;
    private static volatile boolean vmAvailable = false;
    private static URLClassLoader compilerClassLoader;
    private static URLClassLoader vmClassLoader;

    // Compiler reflection
    private static Class<?> julcCompilerClass;
    private static Method compileMethod; // compile(String source) -> CompileResult
    private static Method hasErrorsMethod;
    private static Method diagnosticsMethod;
    private static Method scriptSizeBytesMethod;
    private static Method programMethod;
    private static Method isParameterizedMethod;
    private static Method uplcFormattedMethod;

    // VM reflection
    private static Class<?> julcVmClass;
    private static Method evaluateMethod;
    private static Method vmCreateMethod;

    /**
     * Compile result accessible from Java 21 code.
     */
    public static class CompileInfo {
        public final boolean hasErrors;
        public final List<JulcDiagnostic> diagnostics;
        public final int scriptSizeBytes;
        public final boolean parameterized;
        public final String uplcText;
        public final Object program; // opaque handle for VM evaluation

        public CompileInfo(boolean hasErrors, List<JulcDiagnostic> diagnostics,
                           int scriptSizeBytes, boolean parameterized, String uplcText, Object program) {
            this.hasErrors = hasErrors;
            this.diagnostics = diagnostics;
            this.scriptSizeBytes = scriptSizeBytes;
            this.parameterized = parameterized;
            this.uplcText = uplcText;
            this.program = program;
        }
    }

    /**
     * Evaluation result accessible from Java 21 code.
     */
    public static class EvalInfo {
        public final boolean success;
        public final long cpuSteps;
        public final long memoryUnits;
        public final List<String> traces;
        public final String errorMessage;

        public EvalInfo(boolean success, long cpuSteps, long memoryUnits, List<String> traces, String errorMessage) {
            this.success = success;
            this.cpuSteps = cpuSteps;
            this.memoryUnits = memoryUnits;
            this.traces = traces;
            this.errorMessage = errorMessage;
        }
    }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        // Ensure JulcCompilerBridge is initialized first (we reuse its classloader)
        JulcCompilerBridge.initialize();

        initCompiler();
        initVm();
    }

    private static void initCompiler() {
        try {
            // Reuse JulcCompilerBridge's classloader to share types (especially Program)
            compilerClassLoader = JulcCompilerBridge.julcClassLoader;
            if (compilerClassLoader == null) {
                // Fallback: create our own classloader
                Path jar = resolveJar(COMPILER_JAR);
                if (jar == null) {
                    LOG.info("julc-compiler-all.jar not found for VmBridge");
                    return;
                }
                compilerClassLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null);
            }

            julcCompilerClass = compilerClassLoader.loadClass("com.bloxbean.cardano.julc.compiler.JulcCompiler");
            Class<?> compileResultClass = compilerClassLoader.loadClass("com.bloxbean.cardano.julc.compiler.CompileResult");

            compileMethod = julcCompilerClass.getMethod("compile", String.class);
            hasErrorsMethod = compileResultClass.getMethod("hasErrors");
            diagnosticsMethod = compileResultClass.getMethod("diagnostics");
            scriptSizeBytesMethod = compileResultClass.getMethod("scriptSizeBytes");
            programMethod = compileResultClass.getMethod("program");
            isParameterizedMethod = compileResultClass.getMethod("isParameterized");
            uplcFormattedMethod = compileResultClass.getMethod("uplcFormatted");

            compilerAvailable = true;
            LOG.info("julc VmBridge compiler initialized (shared classloader: " + (JulcCompilerBridge.julcClassLoader != null) + ")");
        } catch (Exception e) {
            LOG.info("julc VmBridge compiler not available: " + e.getMessage());
        }
    }

    private static void initVm() {
        try {
            Path jar = resolveJar(VM_JAR);
            if (jar == null) {
                LOG.info("julc-vm-java-all.jar not found");
                return;
            }

            // VM classloader needs compiler classloader as parent for shared types (Program, Term, etc.)
            if (compilerClassLoader == null) {
                LOG.info("VM requires compiler classloader");
                return;
            }

            vmClassLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, compilerClassLoader);

            julcVmClass = vmClassLoader.loadClass("com.bloxbean.cardano.julc.vm.JulcVm");
            vmCreateMethod = julcVmClass.getMethod("create");

            Class<?> programClass = compilerClassLoader.loadClass("com.bloxbean.cardano.julc.core.Program");
            evaluateMethod = julcVmClass.getMethod("evaluate", programClass);

            vmAvailable = true;
            LOG.info("julc-vm bridge initialized from: " + jar);
        } catch (Exception e) {
            LOG.info("julc-vm bridge not available: " + e.getMessage());
        }
    }

    /**
     * Compile Java source to UPLC using the real julc compiler.
     */
    public static CompileInfo compile(String source) {
        if (!compilerAvailable) return null;

        try {
            // Create compiler with default stdlib lookup
            Object compiler = julcCompilerClass.getDeclaredConstructor().newInstance();
            Object result = compileMethod.invoke(compiler, source);

            boolean hasErrors = (boolean) hasErrorsMethod.invoke(result);

            // Extract diagnostics
            @SuppressWarnings("unchecked")
            List<?> rawDiags = (List<?>) diagnosticsMethod.invoke(result);
            List<JulcDiagnostic> diagnostics = mapDiagnostics(rawDiags);

            int sizeBytes = 0;
            boolean parameterized = false;
            String uplcText = null;
            Object program = null;

            if (!hasErrors) {
                sizeBytes = (int) scriptSizeBytesMethod.invoke(result);
                parameterized = (boolean) isParameterizedMethod.invoke(result);
                uplcText = (String) uplcFormattedMethod.invoke(result);
                program = programMethod.invoke(result);
            }

            return new CompileInfo(hasErrors, diagnostics, sizeBytes, parameterized, uplcText, program);
        } catch (Exception e) {
            LOG.debug("julc compile failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Evaluate a compiled program using the julc VM.
     */
    public static EvalInfo evaluate(Object program) {
        if (!vmAvailable || program == null) return null;

        try {
            Object vm = vmCreateMethod.invoke(null);
            Object evalResult = evaluateMethod.invoke(vm, program);

            String className = evalResult.getClass().getSimpleName();
            boolean success = "Success".equals(className);

            long cpu = 0;
            long mem = 0;
            List<String> traces = new ArrayList<>();
            String errorMessage = null;

            // Extract consumed budget
            try {
                Method consumedMethod = evalResult.getClass().getMethod("consumed");
                Object budget = consumedMethod.invoke(evalResult);
                if (budget != null) {
                    Method cpuMethod = budget.getClass().getMethod("cpu");
                    Method memMethod = budget.getClass().getMethod("mem");
                    cpu = (long) cpuMethod.invoke(budget);
                    mem = (long) memMethod.invoke(budget);
                }
            } catch (Exception ex) { /* budget not available */ }

            // Extract traces
            try {
                Method tracesMethod = evalResult.getClass().getMethod("traces");
                @SuppressWarnings("unchecked")
                List<String> traceList = (List<String>) tracesMethod.invoke(evalResult);
                if (traceList != null) traces.addAll(traceList);
            } catch (Exception ex) { /* traces not available */ }

            // Extract error message for failures
            if (!success) {
                try {
                    Method msgMethod = evalResult.getClass().getMethod("message");
                    errorMessage = (String) msgMethod.invoke(evalResult);
                } catch (Exception ex) {
                    errorMessage = className;
                }
            }

            return new EvalInfo(success, cpu, mem, traces, errorMessage);
        } catch (Exception e) {
            LOG.debug("julc VM evaluation failed: " + e.getMessage());
            return null;
        }
    }

    public static boolean isCompilerAvailable() {
        if (!initialized) initialize();
        return compilerAvailable;
    }

    public static boolean isVmAvailable() {
        if (!initialized) initialize();
        return vmAvailable;
    }

    private static List<JulcDiagnostic> mapDiagnostics(List<?> rawDiags) {
        List<JulcDiagnostic> results = new ArrayList<>();
        for (Object diag : rawDiags) {
            try {
                Method levelM = diag.getClass().getMethod("level");
                Method msgM = diag.getClass().getMethod("message");
                Method lineM = diag.getClass().getMethod("line");
                Method colM = diag.getClass().getMethod("column");
                Method suggM = diag.getClass().getMethod("suggestion");
                Method hasSuggM = diag.getClass().getMethod("hasSuggestion");

                String level = levelM.invoke(diag).toString();
                String message = (String) msgM.invoke(diag);
                int line = (int) lineM.invoke(diag);
                int column = (int) colM.invoke(diag);
                boolean hasSugg = (boolean) hasSuggM.invoke(diag);
                String suggestion = hasSugg ? (String) suggM.invoke(diag) : null;

                results.add(new JulcDiagnostic(
                        "ERROR".equals(level) ? JulcDiagnostic.Level.ERROR :
                                "WARNING".equals(level) ? JulcDiagnostic.Level.WARNING : JulcDiagnostic.Level.INFO,
                        message, "<source>", line, column, suggestion));
            } catch (Exception e) {
                LOG.debug("Failed to map diagnostic: " + e.getMessage());
            }
        }
        return results;
    }

    private static Path resolveJar(String jarName) {
        // Use the same resolution logic as JulcCompilerBridge
        return JulcCompilerBridge.resolveJarPath(jarName);
    }
}
