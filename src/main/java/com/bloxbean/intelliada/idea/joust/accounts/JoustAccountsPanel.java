package com.bloxbean.intelliada.idea.joust.accounts;

import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.account.service.AccountService;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity.NetworkClass;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * ADR-005 D13a (M3.2): the run panel's Accounts tab. One list per network class; devnet accounts are created fresh only
 * (the genesis accounts are listed as constants); testnet and mainnet accounts are created or imported for that class;
 * a recovery phrase is shown only on an explicit, confirmed request and never logged (I10); legacy IntelliAda accounts
 * migrate by an explicit action, as testnet accounts only.
 */
public final class JoustAccountsPanel {

    private final Project project;
    private final JPanel root = new JPanel(new BorderLayout());
    private final JComboBox<NetworkClass> cls = new JComboBox<>(NetworkClass.values());
    private final DefaultListModel<JoustAccounts.Entry> model = new DefaultListModel<>();
    private final JBList<JoustAccounts.Entry> list = new JBList<>(model);
    private final JButton create = new JButton("Create fresh");
    private final JButton importBtn = new JButton("Import…");
    private final JButton reveal = new JButton("Show recovery phrase…");
    private final JButton remove = new JButton("Remove");
    private final JButton migrate = new JButton("Migrate a legacy account…");

    public JoustAccountsPanel(Project project) {
        this.project = project;
        list.setCellRenderer((l, e, i, sel, focus) -> {
            JBLabel c = new JBLabel(PlainText.label(e.name, 60) + "  " + PlainText.label(e.address, 120)
                    + (e.origin.equals("GENESIS") ? "  (devnet genesis)" : ""));
            c.setOpaque(sel);
            if (sel) {
                c.setBackground(l.getSelectionBackground());
                c.setForeground(l.getSelectionForeground());
            }
            return c;
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JBLabel("Network class:"));
        top.add(cls);
        top.add(create);
        top.add(importBtn);
        top.add(reveal);
        top.add(remove);
        top.add(migrate);
        root.add(top, BorderLayout.NORTH);
        root.add(new JBScrollPane(list), BorderLayout.CENTER);
        cls.addActionListener(e -> refresh());
        create.addActionListener(e -> createFresh());
        importBtn.addActionListener(e -> importMnemonic());
        reveal.addActionListener(e -> revealSelected());
        remove.addActionListener(e -> removeSelected());
        migrate.addActionListener(e -> migrateLegacy());
        refresh();
    }

    public JComponent component() {
        return root;
    }

    private NetworkClass selectedClass() {
        return (NetworkClass) cls.getSelectedItem();
    }

    void refresh() {
        model.clear();
        for (JoustAccounts.Entry e : JoustAccounts.getInstance().list(selectedClass())) model.addElement(e);
        importBtn.setEnabled(selectedClass() != NetworkClass.DEVNET);   // devnet accounts are fresh only
    }

    private void createFresh() {
        String name = Messages.showInputDialog(project, "Account name", "New " + label(selectedClass()) + " Account", null);
        if (name == null) return;
        try {
            JoustAccounts.getInstance().createFresh(name, selectedClass());
            refresh();
        } catch (JoustAccounts.Refused e) {
            Messages.showErrorDialog(project, PlainText.literal(e.getMessage(), 500), "JOUST Accounts");
        }
    }

    private void importMnemonic() {
        NetworkClass c = selectedClass();
        String name = Messages.showInputDialog(project, "Account name", "Import " + label(c) + " Account", null);
        if (name == null) return;
        String phrase = Messages.showPasswordDialog(project, "Recovery phrase (it is kept in the IDE's password safe)",
                "Import " + label(c) + " Account", null);
        if (phrase == null || phrase.isBlank()) return;
        try {
            JoustAccounts.getInstance().importMnemonic(name, c, phrase);
            refresh();
        } catch (JoustAccounts.Refused e) {
            Messages.showErrorDialog(project, PlainText.literal(e.getMessage(), 500), "JOUST Accounts");
        }
    }

    private void revealSelected() {
        JoustAccounts.Entry e = list.getSelectedValue();
        if (e == null) return;
        int ok = Messages.showYesNoDialog(project, "Show the recovery phrase of \"" + PlainText.label(e.name, 60)
                + "\"? Anyone who sees it controls the account.", "Show Recovery Phrase", Messages.getWarningIcon());
        if (ok != Messages.YES) return;
        String m = JoustAccounts.getInstance().revealMnemonic(e.id);
        if (m == null) {
            Messages.showErrorDialog(project, "The recovery phrase is not in the password safe.", "JOUST Accounts");
            return;
        }
        // Shown in a modal only: never a console, a notification or the log (I10).
        JTextArea area = new JTextArea(m, 3, 60);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(root, new JBScrollPane(area), "Recovery Phrase", JOptionPane.WARNING_MESSAGE);
    }

    private void removeSelected() {
        JoustAccounts.Entry e = list.getSelectedValue();
        if (e == null || e.origin.equals("GENESIS")) return;
        if (Messages.showYesNoDialog(project, "Remove \"" + PlainText.label(e.name, 60) + "\" and its recovery phrase from the IDE?",
                "Remove Account", Messages.getWarningIcon()) != Messages.YES) return;
        JoustAccounts.getInstance().remove(e.id);
        refresh();
    }

    private void migrateLegacy() {
        List<CardanoAccount> legacy = AccountService.getAccountService().getAccounts();
        List<CardanoAccount> eligible = legacy.stream().filter(a -> a.getMnemonic() != null && !a.getMnemonic().isBlank()).toList();
        if (eligible.isEmpty()) {
            Messages.showInfoMessage(project, "No legacy account with a recovery phrase to migrate.", "JOUST Accounts");
            return;
        }
        String[] names = eligible.stream().map(a -> PlainText.label(a.getName() + " " + a.getAddress(), 120)).toArray(String[]::new);
        int i = Messages.showChooseDialog(project, "Migrate a legacy IntelliAda account as a testnet account (never mainnet).",
                "Migrate Legacy Account", null, names, names[0]);
        if (i < 0) return;
        try {
            JoustAccounts.getInstance().migrateLegacy(eligible.get(i).getName(), eligible.get(i).getMnemonic());
            cls.setSelectedItem(NetworkClass.TESTNET);
            refresh();
        } catch (JoustAccounts.Refused e) {
            Messages.showErrorDialog(project, PlainText.literal(e.getMessage(), 500), "JOUST Accounts");
        }
    }

    private static String label(NetworkClass c) {
        return switch (c) {
            case DEVNET -> "Devnet";
            case TESTNET -> "Testnet";
            case MAINNET -> "Mainnet";
        };
    }
}
