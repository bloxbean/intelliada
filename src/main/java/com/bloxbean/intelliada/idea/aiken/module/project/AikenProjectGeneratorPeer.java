package com.bloxbean.intelliada.idea.aiken.module.project;

import com.bloxbean.intelliada.idea.aiken.module.ui.AikenModulePanel;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.platform.GeneratorPeerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AikenProjectGeneratorPeer extends GeneratorPeerImpl<ProjectCreateSettings> {

    AikenModulePanel aikenModulePanel;
    SettingsListener settingsListener;

    public AikenProjectGeneratorPeer() {
        aikenModulePanel = new AikenModulePanel();
        aikenModulePanel.addSettingsListener(new AikenModulePanel.SettingChangeListener() {

            @Override
            public void settingsChanged() {
                settingsListener.stateChanged(true);
            }
        });
    }

    @Override
    public @NotNull JComponent getComponent(@NotNull TextFieldWithBrowseButton myLocationField, @NotNull Runnable checkValid) {
        return super.getComponent(myLocationField, checkValid);
    }

    @Override
    public @NotNull JComponent getComponent() {
        return aikenModulePanel.getMainPanel();
    }

    @NotNull
    @Override
    public ProjectCreateSettings getSettings() {
        return new ProjectCreateSettings(aikenModulePanel.getOwner());
    }

    @Nullable
    @Override
    public ValidationInfo validate() {
        return aikenModulePanel.doValidate();
    }

    @Override
    public void addSettingsListener(@NotNull SettingsListener listener) {
        super.addSettingsListener(listener);
        this.settingsListener = listener;
    }
}
