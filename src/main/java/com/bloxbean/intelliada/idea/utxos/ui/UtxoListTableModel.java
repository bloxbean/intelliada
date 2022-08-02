/*
 * Copyright (c) 2021 BloxBean Project
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.bloxbean.intelliada.idea.utxos.ui;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.intelliada.idea.util.AdaConversionUtil;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

public class UtxoListTableModel extends AbstractTableModel {
    private List<Utxo> utxos;
    protected String[] columnNames = new String[]{"#", "Txn Index", "Txn Hash", "Amount (Ada)"};
    protected Class[] columnClasses = new Class[]{String.class, String.class, String.class, String.class};

    public UtxoListTableModel() {
        this.utxos = new ArrayList<>();
    }

    @Override
    public int getRowCount() {
        return utxos.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return columnNames[columnIndex];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnClasses[columnIndex];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (utxos == null || utxos.size() == 0 || utxos.size() - 1 < rowIndex)
            return null;

        Utxo utxo = utxos.get(rowIndex);
        if (columnIndex == 0)
            return rowIndex + 1;
        else if (columnIndex == 1)
            return utxo.getOutputIndex();
        else if (columnIndex == 2)
            return utxo.getTxHash();
        else if (columnIndex == 3) {
            List<Amount> amts = utxo.getAmount();
            if (amts != null && amts.size() > 0) {
                Optional<Amount> loveLaceAmt = amts.stream().filter(amount -> LOVELACE.equals(amount.getUnit())).findFirst();
                if (loveLaceAmt.isPresent()) {
                    String adaValue = AdaConversionUtil.lovelaceToAdaFormatted(loveLaceAmt.get().getQuantity());
                    return adaValue + " Ada";
                } else {
                    return "";
                }
            } else {
                return "";
            }

//            if(balance == null)
//                return "..";
//            else {
//                if(balance == 0)
//                    return balance;
//                else {
//                   String adaValue = AdaConversionUtil.lovelaceToAdaFormatted(BigInteger.valueOf(balance));
//                   return adaValue + " ADA (" + balance + ")";
//                }
//            }
        } else
            return null;
    }

    public boolean isCellEditable(int row, int col) {
//        if (col== 0) {
//            return true;
//        } else {
//            return false;
//        }
        return false;
    }

    public void addElement(Utxo utxo) {
        utxos.add(utxo);
        fireTableRowsUpdated(utxos.size() - 1, utxos.size() - 1);
    }

    public void setElements(List<Utxo> utxoList) {
        int size = this.utxos.size();
        this.utxos.clear();

        if (size == 0)
            size = 1;
        if (size > 0) { //Refresh based on old size
            fireTableRowsDeleted(0, size - 1);
        }

        this.utxos.addAll(utxoList);
        fireTableRowsUpdated(0, this.utxos.size() - 1);
    }

    public List<Utxo> getUtxos() {
        if (this.utxos == null)
            return Collections.emptyList();

        return this.utxos;
    }
}
