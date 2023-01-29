package com.bloxbean.intelliada.idea.aiken.module;

import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

public class ProjectGeneratorUtil {
    private final static Logger LOG = Logger.getInstance(ProjectGeneratorUtil.class);

    public static void createNewContract(Project project, VirtualFile srcRoot, String templateName, String contractName) {
        if (srcRoot == null)
            return;

            try {
                createFile(contractName, srcRoot, templateName);
            } catch (Exception e) {
                if(LOG.isDebugEnabled()) {
                    LOG.error(e);
                }
                IdeaUtil.showNotification(project, "Project create",
                        "Aiken smart contract file generation failed : " + e.getMessage(), NotificationType.ERROR, null);
                return;
            }
    }

    private static boolean createFile(String file, VirtualFile folder, final String templateName)
            throws Exception {
        final FileTemplate template = FileTemplateManager.getDefaultInstance().getInternalTemplate(templateName);

        VirtualFile srcFile = folder.createChildData(folder, file);
        VfsUtil.saveText(srcFile, template.getText());

        return true;
    }
}
