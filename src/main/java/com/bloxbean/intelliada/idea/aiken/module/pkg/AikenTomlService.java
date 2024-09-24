package com.bloxbean.intelliada.idea.aiken.module.pkg;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.bloxbean.intelliada.idea.aiken.module.AikenModuleConstant.AIKEN_TOML;

public class AikenTomlService {
    private final static Logger LOG = Logger.getInstance(AikenTomlService.class);

    private Project project;
    private AikenToml aikenToml;
    private boolean aikenProject;
    private boolean isDirty;

    public static AikenTomlService getInstance(@NotNull Project project) {
        if (project == null)
            return null;

        return project.getService(AikenTomlService.class);
    }

    public AikenTomlService(@NotNull Project project) {
        this.project = project;

        ApplicationManager.getApplication().invokeLater(() -> {
            final String basePath = project.getBasePath();
            VirtualFile pkgJson = VfsUtil.findFileByIoFile(new File(basePath, AIKEN_TOML), true);
            if (pkgJson != null && pkgJson.exists()) {
                aikenProject = true;
                attachListener();
            }
        });
    }

    private void attachListener() {
    }

    public boolean isAikenProject() {
        return aikenProject;
    }

    public AikenToml createAikenToml(String owner, String name) throws AikenTomlException {
        this.aikenToml = new AikenToml();
        aikenToml.setName(owner + "/" + name);
        aikenToml.setVersion("0.0.0");
        aikenToml.setLicense("Apache-2.0");
        aikenToml.setDescription("Aiken contracts");

        aikenToml.setRepository(Repository.builder()
                .user(owner)
                .project(name)
                .platform("github")
                .build());

        aikenToml.setDependencies(List.of(
                Dependency.builder()
                        .name("aiken-lang/stdlib")
                        .version("main")
                        .source("github")
                        .build()
        ));

        save();

        return aikenToml;
    }

    public void load() throws AikenTomlException {
        isDirty = false;
        if(project == null) return;

        String projectBasePath = project.getBasePath();
        String aikenTomlPath = projectBasePath + File.separator + AIKEN_TOML;

        if(!new File(aikenTomlPath).exists()) {
            throw new AikenTomlException(AIKEN_TOML + " file doesn't exist.");
        }

        try {
            aikenToml = readAikenToml(aikenTomlPath);
        } catch (IOException e) {
            throw new AikenTomlException(String.format("Unable to read %s file at locatioin %s , Reason : %s", AIKEN_TOML, aikenTomlPath, e.getMessage()), e);
        }
    }

    public void save() throws AikenTomlException {
        String path = getAikenTomlPath();
        if(path == null)
            throw new AikenTomlException("aiken.toml path cannot be null");

        File file = new File(path);
        try {
            TomlWriter tomlWriter = new TomlWriter();
            tomlWriter.write(aikenToml, file);
        } catch (IOException e) {
            throw new AikenTomlException(String.format("Unable to write aiken.toml file at locatioin %s", path));
        }

        try {
            //Refresh
            VfsUtil.findFileByIoFile(file, false).refresh(true, false);
        } catch (Exception e) {
            if(LOG.isDebugEnabled())
                LOG.warn(e);
        }
    }

    public void markDirty() {
        this.isDirty = true;
    }

    public boolean isDirty() {
        return this.isDirty;
    }

    private AikenToml readAikenToml(String path) throws IOException {
        Toml toml = new Toml().read(path);
        return toml.to(AikenToml.class);
    }

    private String getAikenTomlPath() {
        String projectBasePath = project.getBasePath();
        return projectBasePath + File.separator + AIKEN_TOML;
    }
}
