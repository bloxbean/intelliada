package com.bloxbean.intelliada.idea.joust.accounts;

import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.account.service.AccountService;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity.NetworkClass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * ADR-005 D13a (M3.2): the run panel's Accounts tab. While a session is bound, the tab shows only that session's
 * network class and cannot be switched (I12; M32-CDX-6); without one it manages the classes this version serves
 * (devnet, testnet; mainnet waits for M5's opt-in, M32-DES-9). Devnet accounts are created fresh only. A recovery phrase
 * is shown only on an explicit, confirmed request, in a modal (I10). Every store call (PasswordSafe, key derivation) runs
 * in a background task; results come back on the EDT (M32-CDX-8).
 */
public final class JoustAccountsPanel {

    /** The classes the tab offers before M5 (D14a: mainnet needs its own opt-in). */
    static final NetworkClass[] OFFERED = {NetworkClass.DEVNET, NetworkClass.TESTNET};

    private final Project project;
    private final JPanel root = new JPanel(new BorderLayout());
    private final JComboBox<NetworkClass> cls = new JComboBox<>(OFFERED);
    private final JBLabel bound = new JBLabel();
    /** Mainnet accounts kept while mainnet is not offered: named here, never selectable (M32-CDX-24). */
    private final com.intellij.ui.components.JBTextArea kept = new com.intellij.ui.components.JBTextArea();
    private JComponent keptPane;
    /** The latest refresh; an older one's result is discarded (M32-CDX-20). */
    private long refreshSeq;
    private final DefaultListModel<JoustAccounts.Entry> model = new DefaultListModel<>();
    private final JBList<JoustAccounts.Entry> list = new JBList<>(model);
    private final JButton create = new JButton("Create fresh");
    private final JButton importBtn = new JButton("Import…");
    private final JButton reveal = new JButton("Show recovery phrase…");
    private final JButton remove = new JButton("Remove");
    private final JButton migrate = new JButton("Migrate a legacy testnet account…");
    private @Nullable NetworkClass session;

    public JoustAccountsPanel(Project project) {
        this.project = project;
        list.setCellRenderer((l, e, i, sel, focus) -> {
            JBLabel c = new JBLabel(PlainText.label(e.name, 60) + "  " + PlainText.label(e.address, 120)
                    + (e.origin.equals("GENESIS") ? "  (devnet genesis)" : "")
                    + (e.unconfirmed() ? "  (unconfirmed: back up its recovery phrase)" : ""));
            c.setOpaque(sel);
            if (sel) {
                c.setBackground(l.getSelectionBackground());
                c.setForeground(l.getSelectionForeground());
            }
            return c;
        });
        // Two rows, stacked: each row gets its own height, so nothing is clipped in a narrow tool window (M32-CDX-10).
        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JBLabel("Network class:"));
        row1.add(cls);
        row1.add(bound);
        // A grid: its preferred height counts every row, so no button is cut off at any width (M32-CDX-23).
        JPanel row2 = new JPanel(new GridLayout(0, 2, 4, 4));
        for (JButton b : new JButton[]{create, importBtn, reveal, remove, migrate}) row2.add(b);
        rows.add(row1);
        rows.add(row2);
        kept.setEditable(false);
        kept.setOpaque(false);
        kept.setLineWrap(true);
        // A bounded, scrolling area: every name stays reachable without pushing the account list out (M32-CDX-40).
        JBScrollPane keptScroll = new JBScrollPane(kept);
        keptScroll.setPreferredSize(new Dimension(400, com.intellij.util.ui.JBUI.scale(70)));
        keptScroll.setVisible(false);
        keptPane = keptScroll;
        rows.add(keptScroll);
        root.add(rows, BorderLayout.NORTH);
        root.add(new JBScrollPane(list), BorderLayout.CENTER);
        cls.addActionListener(e -> refresh());
        create.addActionListener(e -> createFresh());
        importBtn.addActionListener(e -> importMnemonic());
        reveal.addActionListener(e -> revealSelected());
        remove.addActionListener(e -> removeSelected());
        migrate.addActionListener(e -> migrateLegacy());
        refresh();
        // Confirm phrases stored in an earlier session, and name mainnet accounts kept from elsewhere (L38, L39).
        background("Checking accounts", () -> {
            JoustAccounts.getInstance().confirmPending();
            return JoustAccounts.getInstance().mainnetEntries();
        }, mainnet -> {
            if (!mainnet.isEmpty()) {
                // Every name, one per line, in a read-only area (M32-CDX-35).
                StringBuilder sb = new StringBuilder("Mainnet accounts kept, usable once mainnet support exists:");
                for (var e : mainnet) sb.append("\n  ").append(PlainText.label(e.name, 100)).append("  ").append(PlainText.label(e.address, 120));
                kept.setText(sb.toString());
                keptPane.setVisible(true);
                root.revalidate();
            }
            refresh();
        });
    }

    public JComponent component() {
        return root;
    }

    /**
     * Binds the tab to a session's class ({@code null} when no session runs): it then shows that class only, and the
     * class cannot be switched (I12). The run panel calls this when a session starts and ends.
     */
    public void bindSession(@Nullable NetworkClass sessionClass) {
        session = sessionClass;
        if (sessionClass != null) cls.setSelectedItem(sessionClass);
        cls.setEnabled(sessionClass == null);
        bound.setText(sessionClass == null ? "" : "(the running session's class)");
        refresh();
    }

    NetworkClass selectedClass() {
        return session != null ? session : (NetworkClass) cls.getSelectedItem();
    }

    /** Refills the list in the background (genesis keys are derived once; the store is locked briefly). */
    void refresh() {
        NetworkClass c = selectedClass();
        importBtn.setEnabled(c != NetworkClass.DEVNET);   // devnet accounts are fresh only
        migrate.setEnabled(c == NetworkClass.TESTNET);
        // Cleared now, on the EDT: no row of another class stays visible or selected while the new list loads (M32-CDX-20).
        list.clearSelection();
        model.clear();
        long seq = ++refreshSeq;
        background("Listing accounts", () -> JoustAccounts.getInstance().list(c), entries -> {
            if (seq != refreshSeq || selectedClass() != c) return;   // superseded
            entries.forEach(model::addElement);
        });
    }

    private interface Work<T> {
        T run() throws JoustAccounts.Refused;
    }

    /** Runs store work off the EDT and delivers its result (or refusal) on the EDT. */
    private <T> void background(String title, Work<T> work, java.util.function.Consumer<T> done) {
        new Task.Backgroundable(project, "JOUST: " + title, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    T r = work.run();
                    ApplicationManager.getApplication().invokeLater(() -> done.accept(r));
                } catch (JoustAccounts.Refused e) {
                    String why = PlainText.literal(e.getMessage(), 500);
                    ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project, why, "JOUST Accounts"));
                }
            }
        }.queue();
    }

    private void createFresh() {
        NetworkClass c = selectedClass();
        String name = Messages.showInputDialog(project, "Account name", "New " + label(c) + " Account", null);
        if (name == null) return;
        background("Creating an account", () -> JoustAccounts.getInstance().createFreshForBackup(name, c), created -> {
            refresh();
            // Until a later session confirms the safe kept it, the user's own backup is the only proof (M32-CDX-12). The
            // phrase offered is the one just generated, held in memory through this decision only (M32-CDX-32).
            if (created.entry().unconfirmed() && Messages.showYesNoDialog(project, "Show the new account's recovery phrase now, to "
                    + "write it down? IntelliAda confirms the password safe kept it only after the IDE restarts.",
                    "Back Up the Recovery Phrase", Messages.getQuestionIcon()) == Messages.YES) {
                phraseDialog(created.phrase());
            }
        });
    }

    private void importMnemonic() {
        NetworkClass c = selectedClass();
        String name = Messages.showInputDialog(project, "Account name (stored in plain text; not the recovery phrase)",
                "Import " + label(c) + " Account", null);
        if (name == null) return;
        String phrase = Messages.showPasswordDialog(project, "Recovery phrase (it is kept in the IDE's password safe)",
                "Import " + label(c) + " Account", null);
        if (phrase == null || phrase.isBlank()) return;
        background("Importing an account", () -> JoustAccounts.getInstance().importMnemonic(name, c, phrase), e -> refresh());
    }

    private void revealSelected() {
        JoustAccounts.Entry e = list.getSelectedValue();
        if (e == null) return;
        int ok = Messages.showYesNoDialog(project, "Show the recovery phrase of \"" + PlainText.label(e.name, 60)
                + "\"? Anyone who sees it controls the account.", "Show Recovery Phrase", Messages.getWarningIcon());
        if (ok != Messages.YES) return;
        showPhrase(e.id);
    }

    private void showPhrase(String id) {
        background("Reading the recovery phrase", () -> JoustAccounts.getInstance().revealMnemonic(id), this::phraseDialog);
    }

    private void phraseDialog(String m) {
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
        // A migrated copy's legacy original stays, and is offered in the legacy lists again once this copy goes (M32-CDX-34).
        String what = e.origin.equals("MIGRATED")
                ? "Remove the JOUST copy of \"" + PlainText.label(e.name, 60) + "\"? Its recovery phrase leaves the password safe, but "
                        + "the legacy IntelliAda account it was copied from still holds the phrase and is offered in the legacy lists again."
                : "Remove \"" + PlainText.label(e.name, 60) + "\" and its recovery phrase from the IDE?";
        if (Messages.showYesNoDialog(project, what, "Remove Account", Messages.getWarningIcon()) != Messages.YES) return;
        background("Removing an account", () -> JoustAccounts.getInstance().remove(e.id), r -> refresh());
    }

    /** The legacy accounts that can be copied: testnet addresses with a recovery phrase (M32-SEC-1). Off the EDT. */
    static List<CardanoAccount> migratable() {
        return AccountService.getAccountService().getAccounts().stream()
                .filter(a -> a.getMnemonic() != null && !a.getMnemonic().isBlank() && a.getAddress() != null
                        && a.getAddress().startsWith("addr_test1"))
                .toList();
    }

    private void migrateLegacy() {
        // The legacy store is read and decrypted off the EDT (M32-CDX-21).
        background("Reading legacy accounts", () -> {
            // Ordinary candidates, plus migrated copies whose phrase was lost and can be repaired (M32-CDX-27).
            List<CardanoAccount> all = new java.util.ArrayList<>(migratable());
            for (CardanoAccount r : JoustAccounts.getInstance().repairable()) {
                if (all.stream().noneMatch(a -> a.getAddress().equals(r.getAddress()))) all.add(r);
            }
            return all;
        }, this::chooseLegacy);
    }

    private void chooseLegacy(List<CardanoAccount> eligible) {
        if (eligible.isEmpty()) {
            Messages.showInfoMessage(project, "No legacy testnet account with a recovery phrase to migrate.", "JOUST Accounts");
            return;
        }
        String[] names = eligible.stream().map(a -> PlainText.label(a.getName() + " " + a.getAddress(), 120)).toArray(String[]::new);
        int i = Messages.showChooseDialog(project, "Copy a legacy IntelliAda testnet account into the JOUST accounts. It then no "
                + "longer appears in IntelliAda's legacy account lists. Its recovery phrase also stays in the old account store, "
                + "which protects it less well than the password safe.", "Migrate Legacy Account", null, names, names[0]);
        if (i < 0) return;
        CardanoAccount chosen = eligible.get(i);
        background("Migrating an account", () -> JoustAccounts.getInstance().migrateLegacy(chosen), e -> {
            cls.setSelectedItem(NetworkClass.TESTNET);
            refresh();
            Messages.showInfoMessage(project, "Copied \"" + PlainText.label(e.name, 60) + "\": it is now a JOUST testnet account and "
                    + "no longer offered in the legacy account lists. The old store still holds its recovery phrase.", "JOUST Accounts");
        });
    }

    private static String label(NetworkClass c) {
        return switch (c) {
            case DEVNET -> "Devnet";
            case TESTNET -> "Testnet";
            case MAINNET -> "Mainnet";
        };
    }
}
