package com.bloxbean.intelliada.idea.julc.compile;

import com.bloxbean.intelliada.idea.aiken.compile.CompilationResultListener;
import com.bloxbean.intelliada.idea.aiken.compile.CompileException;
import com.bloxbean.intelliada.idea.julc.configuration.JulcConfigurationHelperService;
import com.bloxbean.intelliada.idea.julc.configuration.JulcSDK;
import com.bloxbean.intelliada.idea.julc.configuration.service.JulcProjectState;
import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Compile service for julc projects.
 * Detects project type (basic/gradle/maven) and invokes the appropriate build command.
 */
public class JulcCompileService {
    private static final Logger LOG = Logger.getInstance(JulcCompileService.class);

    private final Project project;
    private final String cwd;
    private final JulcProjectState.ProjectType projectType;

    public JulcCompileService(@NotNull Project project) {
        this.project = project;
        this.cwd = project.getBasePath();

        JulcTomlService tomlService = JulcTomlService.getInstance(project);
        this.projectType = tomlService.detectProjectType();
    }

    /**
     * Builds the julc project.
     */
    public void compile(CompilationResultListener listener) {
        List<String> cmd = buildCompileCommand();
        if (cmd == null) {
            listener.error("Could not determine julc project type. Ensure julc.toml, build.gradle, or pom.xml exists.");
            return;
        }

        executeCommand(cmd, "Build", listener);
    }

    /**
     * Runs tests for the julc project.
     */
    public void check(CompilationResultListener listener) {
        List<String> cmd = buildCheckCommand();
        if (cmd == null) {
            listener.error("Could not determine julc project type for testing.");
            return;
        }

        executeCommand(cmd, "Test", listener);
    }

    private List<String> buildCompileCommand() {
        if (projectType == null) return null;

        List<String> cmd = new ArrayList<>();
        switch (projectType) {
            case basic -> {
                JulcSDK sdk = JulcConfigurationHelperService.getCompilerLocalSDK(project);
                if (sdk == null) {
                    return null;
                }
                cmd.addAll(sdk.getJulcCommand());
                cmd.add("build");
            }
            case gradle -> {
                cmd.add(getGradleWrapper());
                cmd.add("clean");
                cmd.add("build");
                cmd.add("-x");
                cmd.add("test");
            }
            case maven -> {
                cmd.add(getMavenWrapper());
                cmd.add("compile");
            }
        }
        return cmd;
    }

    private List<String> buildCheckCommand() {
        if (projectType == null) return null;

        List<String> cmd = new ArrayList<>();
        switch (projectType) {
            case basic -> {
                JulcSDK sdk = JulcConfigurationHelperService.getCompilerLocalSDK(project);
                if (sdk == null) {
                    return null;
                }
                cmd.addAll(sdk.getJulcCommand());
                cmd.add("check");
            }
            case gradle -> {
                cmd.add(getGradleWrapper());
                cmd.add("test");
            }
            case maven -> {
                cmd.add(getMavenWrapper());
                cmd.add("test");
            }
        }
        return cmd;
    }

    private void executeCommand(List<String> cmd, String taskName, CompilationResultListener listener) {
        OSProcessHandler handler;
        try {
            handler = new OSProcessHandler(
                    new GeneralCommandLine(cmd).withWorkDirectory(cwd)
            );
        } catch (ExecutionException ex) {
            listener.error(taskName + " failed: " + ex.getMessage());
            listener.onFailure(cwd, ex);
            return;
        }

        listener.info("Running julc " + taskName.toLowerCase() + "...");
        listener.attachProcess(handler);

        handler.addProcessListener(new ProcessListener() {
            @Override
            public void startNotified(@NotNull ProcessEvent event) {}

            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
                if (event.getExitCode() == 0 || (SystemInfo.isWindows && event.getExitCode() <= 0)) {
                    listener.info(taskName + " successful.");
                    // Refresh VFS so blueprint files are visible to Deploy Validator
                    refreshBuildOutput();
                    listener.onSuccessful(cwd);
                } else {
                    listener.error(taskName + " failed.");
                    listener.onFailure(cwd, new CompileException("julc " + taskName.toLowerCase() + " process failed."));
                }
            }

            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {}
        });
    }

    private void refreshBuildOutput() {
        VirtualFile buildDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(cwd + "/build");
        if (buildDir != null) {
            VfsUtil.markDirtyAndRefresh(false, true, true, buildDir);
        }
        // Also refresh target/ for Maven projects
        VirtualFile targetDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(cwd + "/target");
        if (targetDir != null) {
            VfsUtil.markDirtyAndRefresh(false, true, true, targetDir);
        }
    }

    private String getGradleWrapper() {
        String wrapper = SystemInfo.isWindows ? "gradlew.bat" : "./gradlew";
        if (new File(cwd, SystemInfo.isWindows ? "gradlew.bat" : "gradlew").exists()) {
            return wrapper;
        }
        return "gradle";
    }

    private String getMavenWrapper() {
        String wrapper = SystemInfo.isWindows ? "mvnw.cmd" : "./mvnw";
        if (new File(cwd, SystemInfo.isWindows ? "mvnw.cmd" : "mvnw").exists()) {
            return wrapper;
        }
        return "mvn";
    }
}
