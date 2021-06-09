package com.bloxbean.intelliada.idea.utxos.ui;

import com.bloxbean.cardano.client.backend.model.Amount;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.intelliada.idea.utxos.service.UtxoChooser;
import com.bloxbean.intelliada.idea.utxos.ui.model.UtxoWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.math.BigInteger;
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
        utxosJList.setSelectionMode(DefaultListSelectionModel.SINGLE_SELECTION);

        addButton.addActionListener(e -> {
            if(StringUtil.isEmpty(address)) {
                Messages.showWarningDialog("Utxo Chooser", "Please select a sender address first");
                return;
            }

            List<Utxo> utxos = getUtxos();
            List<Utxo> utxoList = UtxoChooser.selectUtxos(project, address, utxos);
            if(utxoList != null && utxoList.size() > 0) {
                utxoList.forEach(utxo -> {
                    if(!utxos.contains(utxo)) {
                        utxoListModel.addElement(new UtxoWrapper(utxo));
                    }
                });
            }
        });

        deleteButton.addActionListener(e -> {
            int index = utxosJList.getSelectedIndex();
            if(index != -1) {
                utxoListModel.remove(index);
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

    public ValidationInfo doValidate(String unit, BigInteger quantity) {
        if(unit != null && unit.length() >= 8 && !verifyUtxoAmountsForTokenAmount(unit, quantity)) {
            return new ValidationInfo("Not enough quantity found in utxos for unit : "
                    + unit.substring(0, 7) + "...", utxosJList);
        } else
            return null;
    }

    private boolean verifyUtxoAmountsForTokenAmount(String unit, BigInteger quantity) {
        List<Utxo> utxoList = getUtxos();
        if(utxoList == null || utxoList.size() == 0)
            return true;
        BigInteger inputAmount = BigInteger.ZERO;
        for(Utxo utxo: utxoList){
            for(Amount amt: utxo.getAmount()) {
                if(unit.equals(amt.getUnit())) {
                    inputAmount = inputAmount.add(amt.getQuantity());
                }
            }
        };

        if(inputAmount.compareTo(quantity) != -1)
            return true;
        else
            return false;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        utxoListModel = new DefaultListModel<>();
        utxosJList = new JBList(utxoListModel);
    }
}
