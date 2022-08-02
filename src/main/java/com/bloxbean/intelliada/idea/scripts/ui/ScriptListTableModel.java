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

package com.bloxbean.intelliada.idea.scripts.ui;

import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScriptListTableModel extends AbstractTableModel {
    private List<ScriptInfo> scriptInfos;
    protected String[] columnNames = new String[]{"Name", "Type", "KeyHash", "Address"};
    protected Class[] columnClasses = new Class[]{String.class, String.class, String.class, String.class};

    public ScriptListTableModel() {
        this.scriptInfos = new ArrayList<>();

    }

    @Override
    public int getRowCount() {
        return scriptInfos.size();
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
        if (scriptInfos == null || scriptInfos.size() == 0 || scriptInfos.size() - 1 < rowIndex)
            return null;

        ScriptInfo scriptInfo = scriptInfos.get(rowIndex);
        if (columnIndex == 0)
            return scriptInfo.getName();
        else if (columnIndex == 1) {
            return scriptInfo.getType();
        } else if (columnIndex == 2) {
            if ((scriptInfo.getScript() instanceof ScriptPubkey) &&
                    ((ScriptPubkey) scriptInfo.getScript()).getKeyHash() != null) {
                return ((ScriptPubkey) scriptInfo.getScript()).getKeyHash();
            } else
                return "";
        } else if (columnIndex == 3) {
            if (!StringUtil.isEmpty(scriptInfo.getAddress())) {
                return scriptInfo.getAddress();
            } else {
                return "";
            }
        } else {
            return null;
        }
    }

    public boolean isCellEditable(int row, int col) {
        return false;
    }

    public void addElement(ScriptInfo scriptInfo) {
        scriptInfos.add(scriptInfo);
        fireTableRowsUpdated(scriptInfos.size() - 1, scriptInfos.size() - 1);
    }

    public void setElements(List<ScriptInfo> scriptInfos) {
        int size = this.scriptInfos.size();
        this.scriptInfos.clear();

        if (size == 0)
            size = 1;
        if (size > 0) { //Refresh based on old size
            fireTableRowsDeleted(0, size - 1);
        }

        this.scriptInfos.addAll(scriptInfos);
        fireTableRowsUpdated(0, this.scriptInfos.size() - 1);
    }

    public List<ScriptInfo> getScriptInfos() {
        if (this.scriptInfos == null)
            return Collections.emptyList();

        return this.scriptInfos;
    }
}
