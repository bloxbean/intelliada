package com.bloxbean.intelliada.idea.aiken.action.util;

import com.bloxbean.intelliada.idea.aiken.lang.AikenFileType;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.util.Condition;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

public class AikenFileTemplateUtil {

    private final static String AIKEN_TEMPLATE_PREFIX = "Aiken.";

    public static List<FileTemplate> getApplicableTemplates() {
        return getApplicableTemplates(new Condition<FileTemplate>() {
            @Override
            public boolean value(FileTemplate fileTemplate) {
                return AikenFileType.INSTANCE.getDefaultExtension().equals(fileTemplate.getExtension())
                        && fileTemplate.getName().startsWith(AIKEN_TEMPLATE_PREFIX);
            }
        });
    }

    public static List<FileTemplate> getApplicableTemplates(Condition<FileTemplate> filter) {
        final List<FileTemplate> applicableTemplates = new SmartList<FileTemplate>();
        applicableTemplates.addAll(ContainerUtil.findAll(FileTemplateManager.getDefaultInstance().getInternalTemplates(), filter));
        //applicableTemplates.addAll(ContainerUtil.findAll(FileTemplateManager.getInstance().getAllTemplates(), filter));
        return applicableTemplates;
    }

    public static String getTemplateShortName(String templateName) {
        if (templateName.startsWith(AIKEN_TEMPLATE_PREFIX)) {
            return templateName.substring(AIKEN_TEMPLATE_PREFIX.length());
        }
        return templateName;
    }
}
