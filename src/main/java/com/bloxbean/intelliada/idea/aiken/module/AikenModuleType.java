package com.bloxbean.intelliada.idea.aiken.module;

import com.bloxbean.intelliada.idea.aiken.common.AikenIcons;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class AikenModuleType extends ModuleType<AikenModuleBuilder> {
    private static final String ID = "Aiken_Module";

    public AikenModuleType() {
        super(ID);
    }

    public static AikenModuleType getInstance() {
        return (AikenModuleType) ModuleTypeManager.getInstance().findByID(ID);
    }

    @Override
    public @NotNull AikenModuleBuilder createModuleBuilder() {
        return new AikenModuleBuilder();
    }

    @Override
    public @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getName() {
        return "Aiken Smart Contract";
    }

    @Override
    public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription() {
        return "Aiken - A Modern Smart Contract Platform For Cardano";
    }

    @Override
    public @NotNull Icon getNodeIcon(boolean isOpened) {
        return AikenIcons.MODULE;
    }

}
