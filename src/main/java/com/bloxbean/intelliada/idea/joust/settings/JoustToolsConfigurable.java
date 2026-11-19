package com.bloxbean.intelliada.idea.joust.settings;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.core.NodeIdentity;
import com.bloxbean.intelliada.idea.joust.core.NodeVersionProbe;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.lsp.JoustServerControl;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Settings | Tools | JOUST (ADR-004 D4). Only Save and Detect… execute anything: {@code <node> --version}, on a
 * candidate that has passed every location check. Detection never sets a path silently: the user confirms one.
 */
public final class JoustToolsConfigurable implements SearchableConfigurable {

    private TextFieldWithBrowseButton nodeField;
    private TextFieldWithBrowseButton lspField;
    private JBCheckBox buildOnSave;
    private JBLabel status;
    private JPanel panel;

    @Override
    public @NotNull String getId() {
        return "com.bloxbean.intelliada.joust.tools";
    }

    @Override
    public @Nls String getDisplayName() {
        return "JOUST";
    }

    @Override
    public @Nullable JComponent createComponent() {
        nodeField = new TextFieldWithBrowseButton();
        nodeField.addBrowseFolderListener(null, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withTitle("Node Binary").withDescription("The absolute path of Node 22 or later"));
        lspField = new TextFieldWithBrowseButton();
        lspField.addBrowseFolderListener(null, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withTitle("joust-lsp").withDescription("The joust-lsp of a globally installed @julclang/joust"));
        JButton detectNode = new JButton("Detect…");
        detectNode.addActionListener(e -> detectNode());
        JButton detectLsp = new JButton("Detect…");
        detectLsp.addActionListener(e -> detectLsp());
        buildOnSave = new JBCheckBox("Build on save with the project's compiler");
        buildOnSave.setEnabled(false);
        buildOnSave.setToolTipText("Available once joust-lsp can pin a project's compiler (ADR-004 D5). Today the language "
                + "server always analyses with its own bundled compiler.");
        status = new JBLabel();

        JPanel nodeRow = new JPanel(new java.awt.BorderLayout(4, 0));
        nodeRow.add(nodeField, java.awt.BorderLayout.CENTER);
        nodeRow.add(detectNode, java.awt.BorderLayout.EAST);
        JPanel lspRow = new JPanel(new java.awt.BorderLayout(4, 0));
        lspRow.add(lspField, java.awt.BorderLayout.CENTER);
        lspRow.add(detectLsp, java.awt.BorderLayout.EAST);

        panel = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("<html>IntelliAda starts the .jsc language server you installed "
                        + "(<code>npm install -g @julclang/joust</code>) on the Node you name here, by absolute path. "
                        + "Paths inside a project or under <code>node_modules/.bin</code> are refused.</html>"))
                .addLabeledComponent("Node (22 or later):", nodeRow)
                .addLabeledComponent("joust-lsp:", lspRow)
                .addComponent(buildOnSave)
                .addComponent(status)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        JoustToolsState.Data d = JoustToolsState.getInstance().getState();
        return !Objects.equals(text(nodeField), nz(d.nodePath)) || !Objects.equals(text(lspField), nz(d.joustLspPath))
                || buildOnSave.isSelected() != d.buildOnSave || nodeChangedOnDisk(d);
    }

    @Override
    public void apply() throws ConfigurationException {
        String node = text(nodeField);
        String lsp = text(lspField);
        JoustToolsState state = JoustToolsState.getInstance();
        if (node.isEmpty() && lsp.isEmpty()) {
            state.getState().nodePath = "";
            state.getState().joustLspPath = "";
            state.setValidatedNode(null, null);
            state.getState().joustVersion = null;
            JoustServerControl.restartAll("JOUST tools were cleared");
            reset();
            return;
        }
        Validated v = validate(node, lsp);
        state.getState().nodePath = node;
        state.getState().joustLspPath = lsp;
        state.getState().buildOnSave = false;
        state.setValidatedNode(v.identity, v.nodeVersion);
        state.getState().joustVersion = v.joustVersion;
        JoustServerControl.restartAll("JOUST tools changed");
        reset();
    }

    @Override
    public void reset() {
        if (nodeField == null) return;
        JoustToolsState.Data d = JoustToolsState.getInstance().getState();
        nodeField.setText(nz(d.nodePath));
        lspField.setText(nz(d.joustLspPath));
        buildOnSave.setSelected(d.buildOnSave);
        if (JoustToolsState.getInstance().looksConfigured()) {
            status.setText(PlainText.html("Validated: Node " + nz(d.nodeVersion) + " at " + nz(d.nodeRealPath)
                    + "; @julclang/joust " + nz(d.joustVersion) + ". Mode: advisory (the server's bundled compiler)."));
        } else {
            status.setText(PlainText.html("Not configured: .jsc files get TextMate highlighting only."));
        }
    }

    private record Validated(NodeIdentity identity, String nodeVersion, String joustVersion) {
    }

    /** The Save-time validation: the only place besides Detect… that runs {@code node --version}. */
    private static Validated validate(String node, String lsp) throws ConfigurationException {
        List<Path> roots = JoustForbiddenRoots.ofOpenProjects();
        JoustPaths.Flavor flavor = JoustPaths.Flavor.current();
        JoustToolValidator.Result n = JoustToolValidator.checkNode(node, roots, flavor);
        if (!n.ok()) throw new ConfigurationException(n.error(), "Node");
        JoustToolValidator.Result l = JoustToolValidator.resolveJoustLsp(lsp, roots, flavor);
        if (!l.ok()) throw new ConfigurationException(l.error(), "joust-lsp");
        String version;
        NodeIdentity id;
        try {
            Object[] r = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    () -> new Object[]{NodeVersionProbe.probe(n.realPath()), JoustToolValidator.identityOf(n.realPath())},
                    "Checking Node", true, null);
            version = (String) r[0];
            id = (NodeIdentity) r[1];
        } catch (IOException e) {
            throw new ConfigurationException("Node could not be run: " + e.getMessage(), "Node");
        }
        int major = JoustToolValidator.nodeMajor(version);
        if (major < JoustToolValidator.MIN_NODE_MAJOR) {
            throw new ConfigurationException("joust-lsp needs Node " + JoustToolValidator.MIN_NODE_MAJOR
                    + " or later; " + n.realPath() + " answered \"" + PlainText.label(version, 40) + "\".", "Node");
        }
        return new Validated(id, PlainText.label(version, 40), PlainText.label(JoustToolValidator.readJoustVersion(l.realPath()), 40));
    }

    private void detectNode() {
        JoustPaths.Flavor flavor = JoustPaths.Flavor.current();
        List<Path> roots = JoustForbiddenRoots.ofOpenProjects();
        List<Path> found = JoustToolDetector.nodeCandidates(System.getenv(), userHome(), flavor);
        List<String> offers = new ArrayList<>();
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            for (Path p : found) {
                JoustToolValidator.Result r = JoustToolValidator.checkNode(p.toString(), roots, flavor);
                if (!r.ok()) continue;
                String v;
                try {
                    v = NodeVersionProbe.probe(r.realPath());
                } catch (IOException e) {
                    continue;
                }
                if (JoustToolValidator.nodeMajor(v) >= JoustToolValidator.MIN_NODE_MAJOR) {
                    offers.add(p + "  (" + PlainText.label(v, 20) + ")");
                }
            }
        }, "Detecting Node", true, null);
        choose(offers, "Node 22 or later", nodeField);
    }

    private void detectLsp() {
        JoustPaths.Flavor flavor = JoustPaths.Flavor.current();
        List<Path> roots = JoustForbiddenRoots.ofOpenProjects();
        List<String> offers = new ArrayList<>();
        for (Path p : JoustToolDetector.joustLspCandidates(System.getenv(), userHome(), flavor)) {
            JoustToolValidator.Result r = JoustToolValidator.resolveJoustLsp(p.toString(), roots, flavor);
            if (r.ok()) offers.add(p + "  (@julclang/joust " + PlainText.label(JoustToolValidator.readJoustVersion(r.realPath()), 30) + ")");
        }
        choose(offers, "joust-lsp", lspField);
    }

    private void choose(List<String> offers, String what, TextFieldWithBrowseButton field) {
        if (offers.isEmpty()) {
            Messages.showInfoMessage(panel, "No " + what + " was found outside your open projects.", "Detect " + what);
            return;
        }
        String picked = Messages.showEditableChooseDialog("Use this " + what + "?", "Detect " + what, null,
                offers.toArray(new String[0]), offers.get(0), null);
        if (picked == null) return;
        int cut = picked.indexOf("  (");
        field.setText(cut > 0 ? picked.substring(0, cut) : picked);
    }

    /**
     * True when the validated Node's size or modification time changed since it was validated: Settings then offers
     * Apply, which validates it again (M1-CDX-14). A stat only; the hash is recomputed on Apply.
     */
    static boolean nodeChangedOnDisk(JoustToolsState.Data d) {
        if (d.nodeRealPath == null) return false;
        try {
            Path p = Path.of(d.nodeRealPath);
            return java.nio.file.Files.size(p) != d.nodeSize || java.nio.file.Files.getLastModifiedTime(p).toMillis() != d.nodeModified;
        } catch (IOException | RuntimeException e) {
            return true;
        }
    }

    private static Path userHome() {
        String h = System.getProperty("user.home");
        return h == null ? null : Path.of(h);
    }

    private static String text(TextFieldWithBrowseButton f) {
        return f.getText() == null ? "" : f.getText().trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
