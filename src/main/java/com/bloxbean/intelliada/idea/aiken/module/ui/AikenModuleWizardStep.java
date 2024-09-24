package com.bloxbean.intelliada.idea.aiken.module.ui;

import com.bloxbean.intelliada.idea.aiken.module.AikenModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;

import javax.swing.*;

public class AikenModuleWizardStep extends ModuleWizardStep {
    private final AikenModulePanel aikenPanel = new AikenModulePanel();
    private AikenModuleBuilder moduleBuilder;

    public AikenModuleWizardStep(AikenModuleBuilder builder) {
        this.moduleBuilder = builder;
    }

    @Override
    public JComponent getComponent() {
        return aikenPanel.getMainPanel();
    }

    @Override
    public void updateDataModel() {

    }
}
