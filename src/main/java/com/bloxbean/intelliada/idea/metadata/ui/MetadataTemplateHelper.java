package com.bloxbean.intelliada.idea.metadata.ui;

import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.metadata.helper.MetadataToJsonNoSchemaConverter;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.intellij.openapi.util.text.StringUtil;

import java.math.BigInteger;
import java.util.Map;

public class MetadataTemplateHelper {

    public static String SIMPLE_KEY_VALUE_TEMPLATE = "Simple Key Value";
//    public static String COMPLEX_KEY_VALUE_TEMPLATE = "Complex Key Value";
    public static String NFT_TEMPLATE = "NFT";

    public String getTemplateJson(String template, Map<String, String> properties) {
        if (StringUtil.isEmpty(template))
            return "";

        if (SIMPLE_KEY_VALUE_TEMPLATE.equals(template)) {
            return createSimpleKeyValueTemplate();
        } else if(NFT_TEMPLATE.equals(template)) {
            return createNFTTemplate(properties);
        } else {
            return "";
        }
    }

    private String createSimpleKeyValueTemplate() {
        CBORMetadata metadata = new CBORMetadata();
        CBORMetadataMap data = new CBORMetadataMap();
        data.put("key1", "value1");
        data.put("key2", "value2");

        metadata.put(BigInteger.valueOf(randomNumber()), data);
        metadata.put(BigInteger.valueOf(randomNumber()), data);
        String json = MetadataToJsonNoSchemaConverter.cborHexToJson(HexUtil.encodeHexString(metadata.serialize()));
        return JsonUtil.getPrettyJson(json);
    }

    private String createNFTTemplate(Map<String, String> properties) {
        CBORMetadata metadata = new CBORMetadata();
        CBORMetadataMap policyMap = new CBORMetadataMap();

        String policyId = properties.getOrDefault("policy_id", "<policy id>");
        CBORMetadataMap policyNftMap = new CBORMetadataMap();
        policyMap.put(policyId, policyNftMap);

        CBORMetadataMap nft = new CBORMetadataMap();
        nft.put("id", "<id>");
        nft.put("name", "<name>");
        nft.put("image", "<image url>");
        policyNftMap.put("<nft_name>", nft);

        metadata.put(new BigInteger("721"), policyMap);

        String json = MetadataToJsonNoSchemaConverter.cborHexToJson(HexUtil.encodeHexString(metadata.serialize()));
        return JsonUtil.getPrettyJson(json);
    }

    private long randomNumber() {
        long min = 200000L;
        long max = 900000000000L;
        long random_int = (long)Math.floor(Math.random()*(max-min+1)+min);
        return random_int;
    }
}
