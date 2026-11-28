package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.joust.accounts.JoustAccounts;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ADR-005 D15 (M3.3b3): the form for one call, built from the contract's description as IntelliAda read it from the
 * snapshot (I26), never from the host. Arguments are passed as text in the description's encoding (I16: IntelliAda never
 * encodes Plutus data); a value that is a JSON object or array is passed as that JSON. Every string from the description
 * is shown as plain text (I15). Keyed calls (M3c) are not offered.
 */
final class JoustCallDialog extends DialogWrapper {

    /** One call of the description. */
    record Call(String name, String kind, List<String> args, boolean payable) {
        boolean needsInstance() {
            return !kind.equals("init");
        }

        boolean needsObject() {
            return kind.equals("ownedAction") || kind.equals("ownedDestroy");
        }

        @Override
        public String toString() {
            return PlainText.label(name + " (" + kind + ")", 120);
        }
    }

    /** The user's choice: the call request (without {@code contract} and {@code address}), the caller and co-signers. */
    record Choice(ObjectNode call, String callerId, List<String> coSignerIds) {
    }

    private final List<Call> calls;
    private final List<JoustAccounts.Entry> accounts;
    private final JComboBox<Call> callBox;
    private final JComboBox<AccountItem> callerBox = new JComboBox<>();
    private final DefaultListModel<AccountItem> coSignerModel = new DefaultListModel<>();
    private final JBList<AccountItem> coSignerList = new JBList<>(coSignerModel);
    private final JBTextField instance = new JBTextField();
    private final JBTextField object = new JBTextField();
    private final JBTextField pay = new JBTextField();
    private final JPanel argsPanel = new JPanel(new GridBagLayout());
    private final Map<String, JBTextField> argFields = new LinkedHashMap<>();
    private final JPanel form = new JPanel(new BorderLayout());

    record AccountItem(JoustAccounts.Entry e) {
        @Override
        public String toString() {
            return PlainText.label((e.name == null || e.name.isBlank() ? e.id : e.name + " (" + e.id + ")") + " · "
                    + abbreviate(e.address), 120);
        }
    }

    static String abbreviate(@Nullable String address) {
        if (address == null) return "";
        return address.length() <= 24 ? address : address.substring(0, 14) + "…" + address.substring(address.length() - 8);
    }

    /** The calls the panel offers from a description's {@code x-joust}: creations and actions, without keyed ones. */
    static List<Call> calls(JsonNode xjoust, boolean initOnly) {
        List<Call> out = new ArrayList<>();
        for (String section : List.of("creations", "actions")) {
            for (JsonNode c : xjoust.path(section)) {
                String name = c.path("name").textValue(), kind = c.path("kind").textValue();
                if (name == null || kind == null || kind.startsWith("keyed")) continue;
                if (initOnly && !kind.equals("init")) continue;
                List<String> args = new ArrayList<>();
                for (JsonNode a : c.path("args")) if (a.path("name").isTextual()) args.add(a.path("name").textValue());
                out.add(new Call(name, kind, args, c.path("payable").asBoolean(false)));
            }
        }
        return out;
    }

    JoustCallDialog(@Nullable Project project, String title, List<Call> calls, List<JoustAccounts.Entry> accounts,
                    @Nullable String selectedInstance, @Nullable String lastCaller) {
        super(project, true);
        this.calls = calls;
        this.accounts = accounts;
        this.callBox = new JComboBox<>(calls.toArray(new Call[0]));
        setTitle(title);
        setOKButtonText("Prepare and Sign…");
        for (JoustAccounts.Entry e : accounts) {
            AccountItem item = new AccountItem(e);
            callerBox.addItem(item);
            coSignerModel.addElement(item);
            if (e.id.equals(lastCaller)) callerBox.setSelectedItem(item);
        }
        coSignerList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        coSignerList.setVisibleRowCount(4);
        instance.setText(selectedInstance == null ? "" : selectedInstance);
        pay.setText("0");
        callBox.addActionListener(e -> rebuild());
        init();
        rebuild();
    }

    @Override
    protected JComponent createCenterPanel() {
        form.setPreferredSize(JBUI.size(560, 380));
        return form;
    }

    private void rebuild() {
        Call c = (Call) callBox.getSelectedItem();
        argFields.clear();
        FormBuilder b = FormBuilder.createFormBuilder()
                .addLabeledComponent("Call:", callBox)
                .addLabeledComponent("Caller:", callerBox);
        if (c != null) {
            if (c.needsInstance()) b.addLabeledComponent("Instance:", instance);
            if (c.needsObject()) b.addLabeledComponent("Object:", object);
            for (String a : c.args()) {
                JBTextField f = new JBTextField();
                f.getEmptyText().setText("text, or a JSON object or array");
                argFields.put(a, f);
                b.addLabeledComponent(PlainText.label(a, 60) + ":", f);
            }
            if (c.payable()) b.addLabeledComponent("Pay (lovelace):", pay);
        }
        if (c != null && !c.kind().equals("init")) b.addLabeledComponent("Co-signers (optional):", new JBScrollPane(coSignerList));
        b.addComponent(new JBLabel("IntelliAda checks the transaction with JOUST's checker before it signs."));
        form.removeAll();
        form.add(b.getPanel(), BorderLayout.NORTH);
        form.revalidate();
        form.repaint();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        Call c = (Call) callBox.getSelectedItem();
        if (c == null) return new ValidationInfo("This contract has no call the panel can make.", callBox);
        if (callerBox.getSelectedItem() == null) return new ValidationInfo("Create or import an account for this network first.", callerBox);
        if (c.needsInstance() && !instance.getText().strip().matches("[0-9a-f]{64}")) {
            return new ValidationInfo("An instance is 64 hex characters (list the instances and select one).", instance);
        }
        if (c.needsObject() && object.getText().strip().isEmpty()) return new ValidationInfo("Name the object.", object);
        if (c.payable() && !pay.getText().strip().matches("[0-9]{1,20}")) return new ValidationInfo("A whole number of lovelace.", pay);
        for (var f : argFields.entrySet()) {
            String t = f.getValue().getText().strip();
            if (t.isEmpty()) return new ValidationInfo("Fill in " + PlainText.label(f.getKey(), 60) + ".", f.getValue());
            if ((t.startsWith("{") || t.startsWith("[")) && json(t) == null) return new ValidationInfo("Not valid JSON.", f.getValue());
        }
        return null;
    }

    private static @Nullable JsonNode json(String t) {
        try {
            return HostJson.parse(t.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    /** The choice, after OK. */
    Choice choice() throws Exception {
        Call c = (Call) callBox.getSelectedItem();
        AccountItem caller = (AccountItem) callerBox.getSelectedItem();
        if (c == null || caller == null) throw new IllegalStateException("nothing chosen");
        ObjectNode call = HostJson.object().put("kind", c.kind()).put("method", c.name());
        if (c.needsInstance()) call.put("instance", instance.getText().strip());
        if (c.needsObject()) call.put("object", object.getText().strip());
        ObjectNode args = call.putObject("args");
        for (var f : argFields.entrySet()) {
            String t = f.getValue().getText().strip();
            JsonNode j = t.startsWith("{") || t.startsWith("[") ? json(t) : null;
            if (j != null) args.set(f.getKey(), j);
            else args.put(f.getKey(), t);
        }
        if (c.payable()) call.putObject("pay").put("lovelace", pay.getText().strip()).putArray("assets");
        List<String> co = new ArrayList<>();
        var hashes = call.putArray("cosigners");
        for (AccountItem a : c.kind().equals("init") ? List.<AccountItem>of() : coSignerList.getSelectedValuesList()) {
            if (a.e().id.equals(caller.e().id)) continue;
            co.add(a.e().id);
            String h = com.bloxbean.intelliada.idea.joust.sign.JoustSigner.paymentKey(a.e().address);
            if (h != null) hashes.add(h);
        }
        if (co.isEmpty()) call.remove("cosigners");
        return new Choice(call, caller.e().id, co);
    }
}
