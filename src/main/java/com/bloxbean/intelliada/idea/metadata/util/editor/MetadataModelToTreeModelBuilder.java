package com.bloxbean.intelliada.idea.metadata.util.editor;

import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.intelliada.idea.metadata.exception.InvalidMetadataException;
import com.bloxbean.intelliada.idea.metadata.ui.editor.DataType;
import com.bloxbean.intelliada.idea.metadata.ui.editor.TreeMetadataEditor;

import javax.swing.tree.DefaultMutableTreeNode;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

public class MetadataModelToTreeModelBuilder {

    public static DefaultMutableTreeNode buildTree(Metadata metadata) throws InvalidMetadataException {
        if (metadata == null)
            return null;

        Map map = metadata.getData();

        TreeMetadataEditor.RootNode rootNode = new TreeMetadataEditor.RootNode(metadata);

        List keys = getKeys(map);

        for (Object key : keys) {
            if (!(key instanceof BigInteger))
                throw new InvalidMetadataException("Metadata label - only unsigned integer allowed");

            BigInteger keyBI = (BigInteger) key;
            Object value = getValue(map, keyBI);

            processValue(key, value, (node -> {
                rootNode.add(node);
            }));
        }

        return rootNode;
    }

    private static TreeMetadataEditor.MapNode processMetadataMap(Object keyValue, CBORMetadataMapEx map) {
        if (map == null)
            return null;

        TreeMetadataEditor.MapNode mapNode = new TreeMetadataEditor.MapNode(keyValue, map);
        List keys = map.getKeys();
        for (Object key : keys) {
            Object value = null;
            if (key instanceof BigInteger) {
                value = map.getValue((BigInteger) key);
            } else if (key instanceof String) {
                value = map.getValue((String) key);
            } else if (key instanceof byte[]) {
                value = map.getValue((byte[]) key);
            }

            processValue(key, value, (node -> {
                mapNode.add(node);
            }));
        }
        return mapNode;
    }

    private static TreeMetadataEditor.ListNode processMetadataList(Object keyVal, CBORMetadataListEx list) {
        if (list == null)
            return null;

        TreeMetadataEditor.ListNode listNode = new TreeMetadataEditor.ListNode(keyVal, list);
        for (int i = 0; i < list.size(); i++) {
            Object value = list.getValueAt(i);

            processValue(keyVal, value, true, (node -> {
                listNode.add(node);
            }));
        }

        return listNode;
    }

    private static void processValue(Object key, Object value, ValueProcessor processor) {
        processValue(key, value, false, processor);
    }

    private static void processValue(Object key, Object value, boolean isListParent, ValueProcessor processor) {
        if (value instanceof CBORMetadataMapEx) {
            processor.attachToParent(processMetadataMap(null, (CBORMetadataMapEx) value));
        } else if (value instanceof CBORMetadataListEx) {
            processor.attachToParent(processMetadataList(null, (CBORMetadataListEx) value));
        } else {
            if (value instanceof BigInteger) {
                if (isListParent) {
                    processor.attachToParent(new TreeMetadataEditor.SingleValueLeafNode(DataType.Int, value));
                } else {
                    processor.attachToParent(new TreeMetadataEditor.KeyValueLeafNode(DataType.Int, key, value));
                }
            } else if (value instanceof String) {
                if (isListParent) {
                    processor.attachToParent(new TreeMetadataEditor.SingleValueLeafNode(DataType.String, value));
                } else {
                    processor.attachToParent(new TreeMetadataEditor.KeyValueLeafNode(DataType.String, key, value));
                }
            } else if (value instanceof byte[]) {
                if (isListParent) {
                    processor.attachToParent(new TreeMetadataEditor.SingleValueLeafNode(DataType.Bytes, value));
                } else {
                    processor.attachToParent(new TreeMetadataEditor.KeyValueLeafNode(DataType.Bytes, key, value));
                }
            }
        }
    }

    private static Object getValue(Map map, BigInteger key) {
        if (((BigInteger) key).compareTo(BigInteger.ZERO) == -1) {
            return MetadataHelper.extractActualValue(map.get(new NegativeInteger(key)));
        } else {
            return MetadataHelper.extractActualValue(map.get(new UnsignedInteger(key)));
        }
    }

    private static List getKeys(Map map) {
        return map.getKeys().stream().map(di -> MetadataHelper.extractActualValue(di)).collect(Collectors.toList());
    }

    interface ValueProcessor {
        public void attachToParent(DefaultMutableTreeNode node);
    }
}
