package com.bloxbean.intelliada.idea.core.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class NodeTypeTest {

    @Test
    public void valueOf() {
        NodeType nodeType = NodeType.lookupByName(NodeType.CARDANO_WALLET.name());
        assertNotNull(nodeType);
    }
}
