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
 * Runtime bridge to julc-compiler via isolated URLClassLoader.
 *
 * The julc-compiler shadow JAR is bundled in lib/julc/ (Java 24+ bytecode).
 * The plugin compiles at Java 21 but JBR 25 at runtime can execute it.
 *
 * Users can override with newer JARs by placing them in ~/.intelliada/julc-libs/
 *
 * Falls back gracefully to the local JavaParser-based validators if JARs are missing
 * or if the classloader fails (e.g., running on JBR 21).
 */
public class JulcCompilerBridge {
    private static final Logger LOG = Logger.getInstance(JulcCompilerBridge.class);
    private static final String PLUGIN_ID = "com.bloxbean.intelliada";
    private static final String COMPILER_JAR = "julc-compiler-all.jar";
    private static final String USER_LIB_DIR = System.getProperty("user.home")
            + File.separator + ".intelliada" + File.separator + "julc-libs";

    private static volatile boolean initialized = false;
    private static volatile boolean available = false;
    static URLClassLoader julcClassLoader; // Package-visible for JulcVmBridge

    // Reflected classes and methods
    private static Class<?> staticJavaParserClass;
    private static Class<?> subsetValidatorClass;
    private static Class<?> compilerDiagnosticClass;
    private static Method parseMethod;
    private static Method validateMethod;
    private static Method levelMethod;
    private static Method messageMethod;
    private static Method lineMethod;
    private static Method columnMethod;
    private static Method suggestionMethod;
    private static Method hasSuggestionMethod;

    /**
     * Initialize the bridge. Safe to call multiple times.
     */
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            Path compilerJar = resolveJarPath(COMPILER_JAR);
            if (compilerJar == null) {
                LOG.info("julc-compiler-all.jar not found. Using local validators.");
                return;
            }

            LOG.info("Loading julc-compiler from: " + compilerJar);

            // Create isolated classloader (parent = null to avoid conflicts)
            julcClassLoader = new URLClassLoader(
                    new URL[]{compilerJar.toUri().toURL()},
                    null // isolated from plugin classloader
            );

            // Load classes via reflection
            staticJavaParserClass = julcClassLoader.loadClass("com.github.javaparser.StaticJavaParser");
            subsetValidatorClass = julcClassLoader.loadClass("com.bloxbean.cardano.julc.compiler.validate.SubsetValidator");
            compilerDiagnosticClass = julcClassLoader.loadClass("com.bloxbean.cardano.julc.compiler.error.CompilerDiagnostic");

            // Cache methods
            parseMethod = staticJavaParserClass.getMethod("parse", String.class);
            validateMethod = subsetValidatorClass.getMethod("validate", julcClassLoader.loadClass("com.github.javaparser.ast.CompilationUnit"));
            levelMethod = compilerDiagnosticClass.getMethod("level");
            messageMethod = compilerDiagnosticClass.getMethod("message");
            lineMethod = compilerDiagnosticClass.getMethod("line");
            columnMethod = compilerDiagnosticClass.getMethod("column");
            suggestionMethod = compilerDiagnosticClass.getMethod("suggestion");
            hasSuggestionMethod = compilerDiagnosticClass.getMethod("hasSuggestion");

            available = true;
            LOG.info("julc-compiler bridge initialized successfully");

        } catch (Exception e) {
            LOG.info("julc-compiler bridge not available (expected on JBR < 25): " + e.getMessage());
            available = false;
        }
    }

    /**
     * Validate source code using the real julc SubsetValidator.
     * Returns empty list if bridge is not available.
     */
    public static List<JulcDiagnostic> validate(String source) {
        if (!available) return List.of();

        try {
            // Parse source with JavaParser (in julc classloader)
            Object compilationUnit = parseMethod.invoke(null, source);

            // Create SubsetValidator and call validate()
            Object validator = subsetValidatorClass.getDeclaredConstructor().newInstance();
            @SuppressWarnings("unchecked")
            List<?> diagnostics = (List<?>) validateMethod.invoke(validator, compilationUnit);

            // Map CompilerDiagnostic → JulcDiagnostic
            List<JulcDiagnostic> results = new ArrayList<>();
            for (Object diag : diagnostics) {
                Object level = levelMethod.invoke(diag);
                String message = (String) messageMethod.invoke(diag);
                int line = (int) lineMethod.invoke(diag);
                int column = (int) columnMethod.invoke(diag);
                String suggestion = (String) suggestionMethod.invoke(diag);
                boolean hasSugg = (boolean) hasSuggestionMethod.invoke(diag);

                JulcDiagnostic.Level diagLevel = mapLevel(level.toString());
                results.add(new JulcDiagnostic(diagLevel, message, "<source>", line, column,
                        hasSugg ? suggestion : null));
            }
            return results;

        } catch (Exception e) {
            LOG.debug("julc SubsetValidator call failed: " + e.getMessage());
            return List.of();
        }
    }

    public static boolean isAvailable() {
        if (!initialized) initialize();
        return available;
    }

    /**
     * Resolve JAR path: check user override dir first, then plugin lib/julc/.
     * Package-visible so JulcVmBridge can reuse.
     */
    static Path resolveJarPath(String jarName) {
        // 1. Check user override: ~/.intelliada/julc-libs/
        Path userJar = Path.of(USER_LIB_DIR, jarName);
        if (Files.exists(userJar)) {
            LOG.info("Using user-provided julc JAR: " + userJar);
            return userJar;
        }

        // 2. Check plugin bundle via PluginManagerCore
        try {
            IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
            if (plugin != null) {
                Path pluginPath = plugin.getPluginPath();
                LOG.info("julc bridge: plugin path = " + pluginPath);

                Path pluginJar = pluginPath.resolve("lib").resolve("julc").resolve(jarName);
                if (Files.exists(pluginJar)) {
                    return pluginJar;
                }

                pluginJar = pluginPath.resolve("julc").resolve(jarName);
                if (Files.exists(pluginJar)) {
                    return pluginJar;
                }
            }
        } catch (Exception e) {
            LOG.info("julc bridge: plugin path resolution error: " + e.getMessage());
        }

        // 3. Locate via classloader — find our own JAR and look for lib/julc/ next to it
        try {
            var url = JulcCompilerBridge.class.getProtectionDomain().getCodeSource().getLocation();
            if (url != null) {
                Path ourJar = Path.of(url.toURI());
                // Our class is in plugins/intelliada/lib/intelliada-xxx.jar
                // Shadow JARs are in plugins/intelliada/lib/julc/
                Path libDir = ourJar.getParent(); // lib/
                Path pluginJar = libDir.resolve("julc").resolve(jarName);
                LOG.info("julc bridge: classloader-based check: " + pluginJar);
                if (Files.exists(pluginJar)) {
                    return pluginJar;
                }
            }
        } catch (Exception e) {
            LOG.info("julc bridge: classloader resolution error: " + e.getMessage());
        }

        // 4. Check working directory (development mode)
        Path devJar = Path.of("lib", "julc", jarName);
        if (Files.exists(devJar)) {
            return devJar;
        }

        LOG.info("julc bridge: " + jarName + " not found in any location");
        return null;
    }

    private static JulcDiagnostic.Level mapLevel(String level) {
        return switch (level) {
            case "ERROR" -> JulcDiagnostic.Level.ERROR;
            case "WARNING" -> JulcDiagnostic.Level.WARNING;
            default -> JulcDiagnostic.Level.INFO;
        };
    }
}
