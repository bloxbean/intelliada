package com.bloxbean.intelliada.idea.transaction.util;

import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.transaction.model.SerializedTransaction;
import com.bloxbean.intelliada.idea.util.JsonUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class ExportUtil {

    public static void exportSignedTransaction(SerializedTransaction transaction, String exportFileLocation, LogListener logListener) throws Exception {
        try {
            FileUtil.writeToFile(new File(exportFileLocation), JsonUtil.getPrettyJson(transaction));
            logListener.info("Transaction exported to " + exportFileLocation);

            try {
                final VirtualFile vfile =
                        LocalFileSystem.getInstance().refreshAndFindFileByPath(exportFileLocation.replace('\\', '/'));
            } catch (Exception e) {}
        } catch (Exception e) {
            logListener.error("Export transaction failed", e);
        }
    }
}
