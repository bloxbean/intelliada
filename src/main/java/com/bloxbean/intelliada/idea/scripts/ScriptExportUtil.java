package com.bloxbean.intelliada.idea.scripts;

import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;
import com.bloxbean.intelliada.idea.util.JsonUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.twelvemonkeys.lang.StringUtil;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;

public class ScriptExportUtil {

    public static void exportScriptInfo(ScriptInfo scriptInfo, Project project, JComponent parent) throws Exception {

        String baseDir = null;
        if(project != null)
            baseDir = project.getBasePath();

        JFileChooser fc = new JFileChooser();
        if(baseDir != null)
            fc.setCurrentDirectory(new File(baseDir));

        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.showDialog(parent, "Select");
        File destination = fc.getSelectedFile();
        if (destination == null || !destination.exists()) {
            Messages.showErrorDialog("Invalid folder: " + destination, "Script Export Error");
            return;
        }

        StringBuffer createdFileNames = new StringBuffer();
        if(!StringUtil.isEmpty(scriptInfo.getAddress())) {
            //Write only script
            String scriptFileName = scriptInfo.getName() + ".script";
            File finalFile = new File(destination, scriptFileName);
            if(finalFile.exists()) {
                int ret = 0;
                try {
                    ret = Messages.showYesNoDialog(String.format("Already a file exists with file name %s. Do you want to overwrite?",
                            scriptFileName), "Export script", "Overwrite", "Create New", AllIcons.General.QuestionDialog);
                } catch (Error e) {
                    //TODO BigSur 2020.3.3 error
                    ret = Messages.NO;
                }

                if(ret == Messages.YES) {
                    FileWriter fileWriter = new FileWriter(finalFile);
                    fileWriter.write(JsonUtil.getPrettyJson(scriptInfo.getScript()));
                    createdFileNames.append(scriptFileName);
                }
            }

        } else {
            //Write sk, vkey, script
            String scriptFileName = scriptInfo.getName() + ".script";
            String skeyFileName = scriptInfo.getName() + ".skey";
            String vkeyFileName = scriptInfo.getName() + ".vkey";

            File scriptFile = new File(destination, scriptFileName);
            File skeyFile = new File(destination, skeyFileName);
            File vkeyFile = new File(destination, vkeyFileName);

            String fileExistsName = null;
            if(scriptFile.exists())
                fileExistsName = scriptFileName;
            else if(skeyFile.exists())
                fileExistsName = skeyFileName;
            else if(vkeyFile.exists())
                fileExistsName = vkeyFileName;

            if(fileExistsName != null) {
                int ret = 0;
                try {
                    ret = Messages.showYesNoDialog(String.format("Already a file exists with file name %s. Do you want to overwrite?",
                            fileExistsName), "Export script", "Overwrite", "No", AllIcons.General.QuestionDialog);
                } catch (Error e) {
                    //TODO BigSur 2020.3.3 error
                    ret = Messages.NO;
                }

                if(ret == Messages.NO) {
                    return; //skip
                }
            }

            //Write script file
            FileWriter fileWriter = new FileWriter(scriptFile);
            fileWriter.write(JsonUtil.getPrettyJson(scriptInfo.getScript()));
            fileWriter.flush();
            createdFileNames.append(scriptFileName);

            //Write skey file
            if(scriptInfo.getSkey() != null) {
                fileWriter = new FileWriter(skeyFile);
                fileWriter.write(JsonUtil.getPrettyJson(scriptInfo.getSkey()));
                fileWriter.flush();
                createdFileNames.append("\n" + skeyFileName);
            }

            //Write vkey file
            if(scriptInfo.getVKey() != null) {
                fileWriter = new FileWriter(vkeyFile);
                fileWriter.write(JsonUtil.getPrettyJson(scriptInfo.getVKey()));
                fileWriter.flush();
                createdFileNames.append("\n" + vkeyFileName);
            }
        }

        Messages.showInfoMessage(String.format("Script Export was successful. The following files have been created. \n %s",
                createdFileNames.toString()), "Script Export");
    }
}
