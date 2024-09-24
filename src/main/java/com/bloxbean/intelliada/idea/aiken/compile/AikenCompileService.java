package com.bloxbean.intelliada.idea.aiken.compile;

import com.bloxbean.intelliada.idea.aiken.configuration.AikenConfigurationHelperService;
import com.bloxbean.intelliada.idea.aiken.configuration.AikenSDK;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AikenCompileService extends BaseCompileService {
    private AikenSDK localSDK;
    private String cwd;

    public AikenCompileService(@NotNull Project project) throws SDKNotConfigured {
        this.cwd = project.getBasePath();

        localSDK = AikenConfigurationHelperService.getCompilerLocalSDK(project);
        if(localSDK == null) {
            throw new SDKNotConfigured("Aiken SDK is not configured.");
        }
    }

    @Override
    public void compile(String sourceFilePath, CompilationResultListener listener) {
        List<String> cmd = new ArrayList<>();
        cmd.add(localSDK.getPath() + File.separator + getAikenCommand());
        cmd.add("build");

        OSProcessHandler handler;
        try {
            handler = new OSProcessHandler(
                    new GeneralCommandLine(cmd).withWorkDirectory(cwd)
            );

        } catch (ExecutionException ex) {
            failed(listener, sourceFilePath, "Compilation failed : " + ex.getMessage(), ex);
            return;
        }

        listener.info("Compiling Aiken file ...");
        listener.attachProcess(handler);

        handler.addProcessListener(new ProcessListener() {
            @Override
            public void startNotified(@NotNull ProcessEvent event) {

            }

            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
                if(event.getExitCode() == 0) {
                    listener.info("Compilation successful.");
                    listener.onSuccessful(sourceFilePath);
                } else {
                    failed(listener, sourceFilePath, "Compilation failed.", new CompileException("Aiken compilation process failed with error."));
                }
            }

            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {

            }
        });
    }

    public void format(String sourceFilePath, CompilationResultListener listener) {
        List<String> cmd = new ArrayList<>();
        cmd.add(localSDK.getPath() + File.separator + getAikenCommand());
        cmd.add("fmt");
        cmd.add(sourceFilePath);

        OSProcessHandler handler;
        try {
            handler = new OSProcessHandler(
                    new GeneralCommandLine(cmd).withWorkDirectory(cwd)
            );

        } catch (ExecutionException ex) {
            failed(listener, sourceFilePath, "Formatting failed : " + ex.getMessage(), ex);
            return;
        }

        listener.info("Formatting Aiken file ...");
        listener.attachProcess(handler);

        handler.addProcessListener(new ProcessListener() {
            @Override
            public void startNotified(@NotNull ProcessEvent event) {

            }

            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
                if(event.getExitCode() == 0) {
                    listener.info("Formatting successful.");
                    listener.onSuccessful(sourceFilePath);
                } else {
                    failed(listener, sourceFilePath, "Formatting failed.", new CompileException("Aiken formatting failed with error."));
                }
            }

            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {

            }
        });
    }

    private String getAikenCommand() {
        String aikenCmd = "aiken";
        if(SystemInfo.isWindows)
            aikenCmd = "aiken.exe";

        return aikenCmd;
    }
}
