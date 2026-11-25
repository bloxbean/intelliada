package com.bloxbean.intelliada.idea.joust.accounts;

import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity.NetworkClass;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;

/** ADR-005 D13a, I12 (M3.2): the Accounts tab follows the bound session's class; devnet accounts cannot be imported. */
public class JoustAccountsPanelPlatformTest extends BasePlatformTestCase {

    private String home;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createTempDirectory("intelliada-home").toString());
        JoustAccounts.getInstance().safeForTests(new JoustAccountsPlatformTest.MapSafe());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            JoustAccounts.getInstance().safeForTests(JoustAccounts.PASSWORD_SAFE);
            System.setProperty("user.home", home);
        } finally {
            super.tearDown();
        }
    }

    @SuppressWarnings("unchecked")
    public void testTheTabFollowsTheSessionAndOffersNoMainnetYet() throws Exception {
        JoustAccounts store = JoustAccounts.getInstance();
        var t = store.createFresh("tab test", NetworkClass.TESTNET);
        try {
            JoustAccountsPanel p = new JoustAccountsPanel(getProject());
            JComboBox<NetworkClass> cls = (JComboBox<NetworkClass>) find(p.component(), JComboBox.class);
            JList<JoustAccounts.Entry> list = (JList<JoustAccounts.Entry>) find(p.component(), JList.class);
            JButton importBtn = button(p.component(), "Import…");
            assertEquals("devnet and testnet only before M5 (M32-DES-9)", 2, cls.getItemCount());
            // A testnet class is selected when the user starts a devnet session (M32-CDX-6).
            cls.setSelectedItem(NetworkClass.TESTNET);
            waitFor(() -> contains(list, t.id));
            p.bindSession(NetworkClass.DEVNET);
            assertFalse("no testnet row stays while the devnet list loads (M32-CDX-20)", contains(list, t.id));
            assertFalse("the class cannot be switched while a session is bound", cls.isEnabled());
            assertFalse("devnet accounts are fresh only", importBtn.isEnabled());
            waitFor(() -> list.getModel().getSize() >= JoustAccounts.GENESIS_ACCOUNTS);
            assertFalse("I12: the testnet account is absent from a devnet session", contains(list, t.id));
            for (int i = 0; i < list.getModel().getSize(); i++) {
                assertEquals("only the session's class", "DEVNET", list.getModel().getElementAt(i).networkClass);
            }
            p.bindSession(null);
            assertTrue(cls.isEnabled());
        } finally {
            store.remove(t.id);
        }
    }

    private static boolean contains(JList<JoustAccounts.Entry> list, String id) {
        for (int i = 0; i < list.getModel().getSize(); i++) if (list.getModel().getElementAt(i).id.equals(id)) return true;
        return false;
    }

    private static void waitFor(BooleanSupplier c) throws Exception {
        long end = System.currentTimeMillis() + 15_000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("timed out waiting for the account list");
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
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
