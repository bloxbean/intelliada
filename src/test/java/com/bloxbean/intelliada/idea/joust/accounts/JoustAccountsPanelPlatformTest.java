package com.bloxbean.intelliada.idea.joust.accounts;

import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity.NetworkClass;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.*;
import java.awt.*;

/** ADR-005 D13a (M3.2): the Accounts tab offers each class only its own accounts; devnet accounts cannot be imported. */
public class JoustAccountsPanelPlatformTest extends BasePlatformTestCase {

    @SuppressWarnings("unchecked")
    public void testImportIsOffForDevnetsAndGenesisAccountsAreDevnetOnly() throws Exception {
        JoustAccountsPanel p = new JoustAccountsPanel(getProject());
        JComboBox<NetworkClass> cls = (JComboBox<NetworkClass>) find(p.component(), JComboBox.class);
        JButton importBtn = button(p.component(), "Import…");
        JList<JoustAccounts.Entry> list = (JList<JoustAccounts.Entry>) find(p.component(), JList.class);
        cls.setSelectedItem(NetworkClass.DEVNET);
        assertFalse("devnet accounts are fresh only", importBtn.isEnabled());
        assertTrue(list.getModel().getSize() >= JoustAccounts.GENESIS_ACCOUNTS);
        cls.setSelectedItem(NetworkClass.MAINNET);
        assertTrue(importBtn.isEnabled());
        for (int i = 0; i < list.getModel().getSize(); i++) {
            assertFalse("no genesis account outside devnets", list.getModel().getElementAt(i).origin.equals("GENESIS"));
        }
    }

    private static Component find(Container c, Class<?> type) {
        for (Component x : c.getComponents()) {
            if (type.isInstance(x)) return x;
            if (x instanceof Container k) {
                Component f = find(k, type);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static JButton button(Container c, String text) {
        for (Component x : c.getComponents()) {
            if (x instanceof JButton b && text.equals(b.getText())) return b;
            if (x instanceof Container k) {
                JButton f = button(k, text);
                if (f != null) return f;
            }
        }
        return null;
    }
}
