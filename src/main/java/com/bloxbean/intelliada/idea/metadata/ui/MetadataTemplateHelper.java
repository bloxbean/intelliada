package com.bloxbean.intelliada.idea.metadata.ui;

import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.metadata.helper.MetadataToJsonNoSchemaConverter;
import com.bloxbean.cardano.client.util.HexUtil;
import com.intellij.openapi.util.text.StringUtil;

import java.math.BigInteger;

public class MetadataTemplateHelper {

    public static String SIMPLE_KEY_VALUE_TEMPLATE = "Simple Key Value";
    public static String COMPLEX_KEY_VALUE_TEMPLATE = "Complex Key Value";
    public static String NFT_TEMPLATE = "NFT";

    public String getTemplateJson(String template) {
        if(StringUtil.isEmpty(template))
            return "";

        if(SIMPLE_KEY_VALUE_TEMPLATE.equals(template)) {
            return createSimpleKeyValueTemplate();
        } else {
            return "";
        }
    }

    private String createSimpleKeyValueTemplate() {
        CBORMetadata metadata = new CBORMetadata();
        CBORMetadataMap data = new CBORMetadataMap();
        data.put("key1", "value1");
        data.put("key2", "value2");
        metadata.put(BigInteger.valueOf(10000000001L), data);

        return MetadataToJsonNoSchemaConverter.cborHexToJson(HexUtil.encodeHexString(metadata.serialize()));
    }
}
