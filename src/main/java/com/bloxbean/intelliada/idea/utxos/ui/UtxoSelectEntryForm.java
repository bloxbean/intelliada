package com.bloxbean.intelliada.idea.utxos.ui;

import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.intelliada.idea.utxos.service.UtxoChooser;
import com.bloxbean.intelliada.idea.utxos.ui.model.UtxoWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class UtxoSelectEntryForm {
    private JPanel mainPanel;
    private JList utxosJList;
    private JButton addButton;
    private JButton deleteButton;
    private DefaultListModel<UtxoWrapper> utxoListModel;
    private String address;

    public UtxoSelectEntryForm() {

    }

    public void initialize(Project project) {
        utxosJList.setModel(utxoListModel);

        addButton.addActionListener(e -> {
            if(StringUtil.isEmpty(address)) {
                Messages.showWarningDialog("Utxo Chooser", "Please select a sender address first");
                return;
            }

            List<Utxo> utxoList = UtxoChooser.selectUtxos(project, address, Collections.emptyList());
            if(utxoList != null && utxoList.size() > 0) {
                utxoList.forEach(utxo -> utxoListModel.addElement(new UtxoWrapper(utxo)));
            }
        });
    }

    public void setAddress(String address) {
        this.address = address;
        utxoListModel.clear();
    }

    public List<Utxo> getUtxos() {
        Enumeration<UtxoWrapper> enumeration = utxoListModel.elements();
        if(enumeration == null || !enumeration.hasMoreElements())
            return Collections.emptyList();

        List<Utxo> utxos = new ArrayList<>();
        while(enumeration.hasMoreElements()) {
            utxos.add(enumeration.nextElement().getUtxo());
        }

        return utxos;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        utxoListModel = new DefaultListModel<>();
        utxosJList = new JBList(utxoListModel);
    }
}
