package com.bloxbean.intelliada.idea.aiken.action;

import com.bloxbean.intelliada.idea.aiken.action.util.AikenFileTemplateUtil;
import com.bloxbean.intelliada.idea.aiken.common.AikenIcons;
import com.bloxbean.intelliada.idea.aiken.module.AikenModuleType;
import com.bloxbean.intelliada.idea.aiken.module.pkg.AikenTomlService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.actions.CreateFromTemplateAction;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Properties;

public class NewAikenFileAction extends CreateFromTemplateAction<PsiFile> {

    public NewAikenFileAction() {
        super("Aiken File", null, AikenIcons.AIKEN_ICON);
    }

    @Override
    protected boolean isAvailable(DataContext dataContext) {

        final Module module = LangDataKeys.MODULE.getData(dataContext);
        final ModuleType moduleType = module == null ? null : ModuleType.get(module);
        boolean isAikenModule = moduleType instanceof AikenModuleType;

        if(!isAikenModule && module != null) { //For non aiken modules. Check if aiken.toml available.
            AikenTomlService aikenTomlService = AikenTomlService.getInstance(module.getProject());
            if (aikenTomlService != null)
                isAikenModule = aikenTomlService.isAikenProject();
        }

        return super.isAvailable(dataContext) && isAikenModule;
    }

    @Override
    protected String getActionName(PsiDirectory directory, String newName, String templateName) {
        return "Creating File " + newName;
    }

    @Override
    protected void buildDialog(Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
//        builder.setTitle(IdeBundle.message("action.create.new.class"));
        builder.setTitle("Create Aiken file");
        for (FileTemplate fileTemplate : AikenFileTemplateUtil.getApplicableTemplates()) {
            final String templateName = fileTemplate.getName();
            final String shortName = AikenFileTemplateUtil.getTemplateShortName(templateName);
            final Icon icon = AllIcons.FileTypes.Any_type;
            builder.addKind(shortName, icon, templateName);
        }
        builder.setValidator(new InputValidatorEx() {
            @Override
            public String getErrorText(String inputString) {
                if (inputString.length() > 0 && !StringUtil.isJavaIdentifier(inputString)) {
                    return "This is not a valid Aiken file name";
                }
                return null;
            }

            @Override
            public boolean checkInput(String inputString) {
                return true;
            }

            @Override
            public boolean canClose(String inputString) {
                return !StringUtil.isEmptyOrSpaces(inputString) && getErrorText(inputString) == null;
            }
        });
    }

    @Nullable
    @Override
    protected PsiFile createFile(String akName, String templateName, PsiDirectory dir) {
        try {
            return createFile(akName, dir, templateName).getContainingFile();
        }
        catch (Exception e) {
            if(LOG.isDebugEnabled()) {
                LOG.error("Unable to create aiken file");
            }
            throw new RuntimeException(e);
        }
    }

    private static PsiElement createFile(String className, @NotNull PsiDirectory directory, final String templateName)
            throws Exception {
        final Properties props = new Properties(FileTemplateManager.getDefaultInstance().getDefaultProperties());
        props.setProperty(FileTemplate.ATTRIBUTE_NAME, className);

        final FileTemplate template = FileTemplateManager.getDefaultInstance().getInternalTemplate(templateName);

        return FileTemplateUtil.createFromTemplate(template, className, props, directory, NewAikenFileAction.class.getClassLoader());
    }
}
