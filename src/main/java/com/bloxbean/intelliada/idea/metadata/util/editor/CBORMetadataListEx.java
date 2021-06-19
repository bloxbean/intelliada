package com.bloxbean.intelliada.idea.metadata.util.editor;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;

import java.math.BigInteger;

import static com.bloxbean.intelliada.idea.metadata.util.editor.MetadataHelper.extractActualValue;

public class CBORMetadataListEx extends CBORMetadataList {

    protected Array array;
    public CBORMetadataListEx(Array array) {
        super(array);
        this.array = array;
    }

    public void addValue(Object value) {
        if (value instanceof String) {
            add((String) value);
        } else if (value instanceof BigInteger) {
            if (((BigInteger) value).compareTo(BigInteger.ZERO) == -1) {
                addNegative((BigInteger) value);
            } else {
                add((BigInteger) value);
            }
        } else if(value instanceof byte[]) {
            add((byte[]) value);
        } else if(value instanceof CBORMetadataMap) {
            add((CBORMetadataMap) value);
        } else if(value instanceof CBORMetadataList) {
           add((CBORMetadataList) value);
        }
    }

    public void replaceValueAt(int index, Object value) {
        if(index == -1)
            return;
        array.getDataItems().remove(index);
        if(value instanceof String) {
            array.getDataItems().add(index, new UnicodeString((String)value));
        } else if(value instanceof BigInteger) {
            if(((BigInteger) value).compareTo(BigInteger.ZERO) == -1) {
                array.getDataItems().add(index, new NegativeInteger((BigInteger) value));
            } else {
                array.getDataItems().add(index, new UnsignedInteger((BigInteger) value));
            }
        }
    }

    public void removeItem(Object value) {
        array.getDataItems().remove(value);
    }

    public void removeItemAt(int index) {
        if(index != -1 && index < array.getDataItems().size()) {
            array.getDataItems().remove(index);
        }
    }

    public Object getValueAt(int index) {
        if(index != -1 && index < array.getDataItems().size()) {
            DataItem dataItem = array.getDataItems().get(index);
            return extractActualValue(dataItem);
        }

        return null;
    }

    public int size() {
        if(array.getDataItems() != null)
            return array.getDataItems().size();
        else
            return 0;
    }

    public static CBORMetadataListEx create() {
        Array array = new Array();
        return new CBORMetadataListEx(array);
    }
}
