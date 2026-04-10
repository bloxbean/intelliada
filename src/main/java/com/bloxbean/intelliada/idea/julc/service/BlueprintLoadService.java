package com.bloxbean.intelliada.idea.julc.service;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads compiled validator blueprints from julc build output.
 * Supports both CLI output (build/plutus/plutus.json) and
 * annotation processor output (META-INF/plutus/*.plutus.json).
 */
public class BlueprintLoadService {
    private static final Logger LOG = Logger.getInstance(BlueprintLoadService.class);

    private final Project project;

    public BlueprintLoadService(Project project) {
        this.project = project;
    }

    public static BlueprintLoadService getInstance(Project project) {
        return new BlueprintLoadService(project);
    }

    /**
     * Load all validators from the project's build output.
     */
    public PlutusBlueprint loadBlueprint() {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) return null;

        // Try CLI output: build/plutus/plutus.json
        VirtualFile cliBlueprint = baseDir.findFileByRelativePath("build/plutus/plutus.json");
        if (cliBlueprint != null) {
            return parseBlueprintFile(cliBlueprint);
        }

        // Try Gradle annotation processor output: build/classes/java/main/META-INF/plutus/
        VirtualFile apDir = baseDir.findFileByRelativePath("build/classes/java/main/META-INF/plutus");
        if (apDir != null && apDir.isDirectory()) {
            return loadFromAnnotationProcessorOutput(apDir);
        }

        // Try Maven annotation processor output: target/classes/META-INF/plutus/
        VirtualFile mavenDir = baseDir.findFileByRelativePath("target/classes/META-INF/plutus");
        if (mavenDir != null && mavenDir.isDirectory()) {
            return loadFromAnnotationProcessorOutput(mavenDir);
        }

        return null;
    }

    /**
     * Load validators from a CIP-57 plutus.json blueprint file.
     */
    private PlutusBlueprint parseBlueprintFile(VirtualFile file) {
        try {
            String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);

            PlutusBlueprint blueprint = new PlutusBlueprint();

            // Parse preamble
            JSONObject preamble = json.optJSONObject("preamble");
            if (preamble != null) {
                blueprint.setPreamble(new PlutusBlueprint.Preamble(
                        preamble.optString("title", ""),
                        preamble.optString("version", "")
                ));
            }

            // Parse validators
            JSONArray validators = json.optJSONArray("validators");
            List<PlutusBlueprint.ValidatorInfo> validatorList = new ArrayList<>();
            if (validators != null) {
                for (int i = 0; i < validators.length(); i++) {
                    JSONObject v = validators.getJSONObject(i);
                    String compiledCode = v.optString("compiledCode", "");
                    validatorList.add(new PlutusBlueprint.ValidatorInfo(
                            v.optString("title", "unknown"),
                            v.optString("hash", ""),
                            compiledCode,
                            compiledCode.length() / 2, // hex bytes
                            false
                    ));
                }
            }
            blueprint.setValidators(validatorList);
            return blueprint;

        } catch (Exception e) {
            LOG.warn("Failed to parse blueprint: " + file.getPath(), e);
            return null;
        }
    }

    /**
     * Load individual .plutus.json files from annotation processor output.
     */
    private PlutusBlueprint loadFromAnnotationProcessorOutput(VirtualFile dir) {
        PlutusBlueprint blueprint = new PlutusBlueprint();
        blueprint.setPreamble(new PlutusBlueprint.Preamble("", ""));
        List<PlutusBlueprint.ValidatorInfo> validators = new ArrayList<>();

        for (VirtualFile child : dir.getChildren()) {
            if (child.getName().endsWith(".plutus.json")) {
                try {
                    String content = new String(child.contentsToByteArray(), StandardCharsets.UTF_8);
                    JSONObject json = new JSONObject(content);

                    String compiledCode = json.optString("compiledCode",
                            json.optString("cborHex", ""));
                    String title = child.getNameWithoutExtension().replace(".plutus", "");

                    validators.add(new PlutusBlueprint.ValidatorInfo(
                            title,
                            json.optString("hash", json.optString("scriptHash", "")),
                            compiledCode,
                            compiledCode.length() / 2,
                            json.optBoolean("parameterized", false)
                    ));
                } catch (IOException e) {
                    LOG.warn("Failed to parse " + child.getPath(), e);
                }
            }
        }

        if (validators.isEmpty()) return null;

        blueprint.setValidators(validators);
        return blueprint;
    }
}
