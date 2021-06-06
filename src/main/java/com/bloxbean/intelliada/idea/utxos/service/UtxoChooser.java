package com.bloxbean.intelliada.idea.utxos.service;

import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.intelliada.idea.utxos.ui.ListUtxosDialog;
import com.intellij.openapi.project.Project;

import java.util.Collections;
import java.util.List;

public class UtxoChooser {

    public static List<Utxo> selectUtxos(Project project, String address, List<Utxo> ignoreUtxos) {
        ListUtxosDialog dialog = new ListUtxosDialog(project, address, ignoreUtxos);

        boolean ok = dialog.showAndGet();
        if(!ok)
            return Collections.emptyList();

        return dialog.getSelectedUtxos();

    }
}
