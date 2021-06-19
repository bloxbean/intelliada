package com.bloxbean.intelliada.idea.metadata.util.editor;

import co.nstant.in.cbor.model.*;

public class MetadataHelper {

    public static Object extractActualValue(DataItem dataItem) {
        if (dataItem instanceof UnicodeString) {
            return ((UnicodeString) dataItem).getString();
        } else if (dataItem instanceof UnsignedInteger) {
            return ((UnsignedInteger) dataItem).getValue();
        } else if (dataItem instanceof NegativeInteger) {
            return ((NegativeInteger) dataItem).getValue();
        } else if (dataItem instanceof ByteString) {
            return ((ByteString) dataItem).getBytes();
        } else if (dataItem instanceof Map) {
            return new CBORMetadataMapEx((Map) dataItem);
        } else if (dataItem instanceof Array) {
            return new CBORMetadataListEx((Array) dataItem);
        } else {
            return dataItem;
        }
    }
}
