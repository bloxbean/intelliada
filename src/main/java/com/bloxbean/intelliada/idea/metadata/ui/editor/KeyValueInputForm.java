package com.bloxbean.intelliada.idea.metadata.ui.editor;

import com.bloxbean.cardano.client.util.HexUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.math.BigInteger;

public class KeyValueInputForm {
    private JPanel panel1;
    private JTextField keyTf;
    private JComboBox typeCB;
    private JTextField valueTf;
    private boolean metadataLabel;
    private boolean listMode;
    private DataType[] types = {DataType.String, DataType.Int, DataType.Bytes, DataType.List, DataType.Map};

    public KeyValueInputForm() {

    }

    public void initialize(boolean metadataLabel) {
        this.metadataLabel = metadataLabel;
        typeCB.addActionListener(e -> {
            DataType type = (DataType) typeCB.getSelectedItem();
            if(type == DataType.List || type == DataType.Map)
                valueTf.setEnabled(false);
            else
                valueTf.setEnabled(true);
        });
    }

    public void enableListMode() {
        listMode = true;
        keyTf.setEnabled(false);
    }

    public void enableEditMode() {
        keyTf.setEditable(false);
        typeCB.setEnabled(false);
    }

    public BigInteger getKeyAsBI() {
        return new BigInteger(keyTf.getText());
    }

    public String getKey() {
        return keyTf.getText();
    }

    public Object getValue() {
        if(getType() == DataType.String)
            return valueTf.getText();
        else if(getType() == DataType.Int)
            return new BigInteger(valueTf.getText());
        else if(getType() == DataType.Bytes) {
            String val = valueTf.getText();
            if(val != null && val.length() >= 2 && val.startsWith("0x")) {
                val = val.substring(2);
            }
            return HexUtil.decodeHexString(val);
        } else
            return null;
    }

    public DataType getType() {
        return (DataType) typeCB.getSelectedItem();
    }

    public void setKey(String key) {
        keyTf.setText(key);
    }

    public void setValue(String value) {
        valueTf.setText(value);
    }

    public void setType(DataType type) {
        typeCB.setSelectedItem(type);
    }

    public ValidationInfo doValidate() {
        if(metadataLabel) {
            try {
                getKeyAsBI();
            } catch (Exception e) {
                return new ValidationInfo("Invalid number. Only Integer value is allowed for metadata label key", keyTf);
            }
        }

        if(!listMode && StringUtil.isEmpty(getKey())) {
            return new ValidationInfo("Invalid key", keyTf);
        }

        if(getType() == DataType.Int) {
            try {
                getValue();
            } catch (Exception e) {
                return new ValidationInfo("Invalid integer value", valueTf);
            }
        }

        if(getType() == DataType.Bytes) {
            try {
                getValue();
            } catch (Exception e) {
                return new ValidationInfo("Invalid hex value", valueTf);
            }
        }

        return null;
    }
    private void createUIComponents() {
        // TODO: place custom component creation code here
        typeCB = new ComboBox(types);
    }

}
