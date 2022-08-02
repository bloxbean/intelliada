package com.bloxbean.intelliada.idea.metadata.util.editor;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import static com.bloxbean.intelliada.idea.metadata.util.editor.MetadataHelper.extractActualValue;

public class CBORMetadataMapEx extends CBORMetadataMap {
    protected Map map;

    public CBORMetadataMapEx(Map map) {
        super(map);
        this.map = map;
    }

    public void putValue(String key, Object value) {
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
        } else if (value instanceof CBORMetadataMap) {
            put(key, (CBORMetadataMap) value);
        } else if (value instanceof CBORMetadataList) {
            put(key, (CBORMetadataList) value);
        }
    }

    public void removeItem(String key) {
        this.map.remove(new UnicodeString(key));
    }

    public void removeItem(BigInteger key) {
        if (key.compareTo(BigInteger.ZERO) == -1) {
            this.map.remove(new NegativeInteger(key));
        } else {
            this.map.remove(new UnsignedInteger(key));
        }
    }

    public void removeItem(byte[] key) {
        this.map.remove(new ByteString(key));
    }

    public Object getValue(String key) {
        return extractActualValue(this.map.get(new UnicodeString(key)));
    }

    public Object getValue(BigInteger key) {
        if (key.compareTo(BigInteger.ZERO) == -1) {
            return extractActualValue(this.map.get(new NegativeInteger(key)));
        } else {
            return extractActualValue(this.map.get(new UnsignedInteger(key)));
        }
    }

    public Object getValue(byte[] key) {
        return extractActualValue(this.map.get(new ByteString(key)));
    }

    public List getKeys() {
        return this.map.getKeys().stream().map(di -> extractActualValue(di)).collect(Collectors.toList());
    }

    public static CBORMetadataMapEx create() {
        Map map = new Map();
        return new CBORMetadataMapEx(map);
    }
}
