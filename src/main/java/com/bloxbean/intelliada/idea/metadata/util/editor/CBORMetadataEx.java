package com.bloxbean.intelliada.idea.metadata.util.editor;

import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import static com.bloxbean.intelliada.idea.metadata.util.editor.MetadataHelper.extractActualValue;

public class CBORMetadataEx extends CBORMetadata {

    public void putValue(BigInteger key, Object value) {
        if (value instanceof String) {
            put(key, String.valueOf(value));
        } else if (value instanceof BigInteger) {
            if (((BigInteger) value).compareTo(BigInteger.ZERO) == -1) {
                putNegative(key, (BigInteger) value);
            } else {
                put(key, (BigInteger) value);
            }
        } else if (value instanceof byte[]) {
            put(key, (byte[]) value);
        } else if(value instanceof CBORMetadataMap) {
            put(key, (CBORMetadataMap) value);
        } else if(value instanceof CBORMetadataList) {
            put(key, (CBORMetadataList) value);
        }
    }

    public void removeItem(BigInteger key) {
        if(((BigInteger) key).compareTo(BigInteger.ZERO) == -1) {
            this.getData().remove(new NegativeInteger(key));
        } else {
            this.getData().remove(new UnsignedInteger(key));
        }
    }

    public Object getValue(BigInteger key) {
        if(key.compareTo(BigInteger.ZERO) == -1) {
            return extractActualValue(getData().get(new NegativeInteger(key)));
        } else {
            return extractActualValue(getData().get(new UnsignedInteger(key)));
        }
    }

    public List getKeys() {
        return getData().getKeys().stream().map(di -> extractActualValue(di)).collect(Collectors.toList());
    }

    public static CBORMetadataEx create() {
        return new CBORMetadataEx();
    }
}
