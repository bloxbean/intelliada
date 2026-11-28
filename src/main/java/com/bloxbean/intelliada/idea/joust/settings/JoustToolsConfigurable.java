package com.bloxbean.intelliada.idea.joust.settings;

import com.bloxbean.intelliada.idea.joust.core.JoustCliVersionProbe;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.core.NodeIdentity;
import com.bloxbean.intelliada.idea.joust.core.NodeVersionProbe;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.lsp.JoustServerControl;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.application.ApplicationManager;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Settings | Tools | JOUST (ADR-004 D4). Only Save and Detect… execute anything: {@code <node> --version}, on a
 * candidate that has passed every location check. Detection never sets a path silently: the user confirms one.
 *
 * <p>ADR-004 D9 adds the native {@code joust} the new-project wizard runs. It is validated by D4's rule
 * ({@link JoustToolValidator#checkExecutable}); its Detect… only reads directories, and the only execution is
 * {@code joust version} on Save ({@link JoustCliVersionProbe}).
 */
public final class JoustToolsConfigurable implements SearchableConfigurable {

    private TextFieldWithBrowseButton nodeField;
    private TextFieldWithBrowseButton lspField;
    private TextFieldWithBrowseButton cliField;
    private JBLabel cliStatus;
    private JBCheckBox buildOnSave;
    private JBCheckBox confirmDevnet;
    private com.intellij.ui.components.JBTextField maxFeeAda;
    private JBLabel status;
    private JPanel panel;
    /** Set by Validate Node Again or a confirmed Detect: Apply validates Node even if its path is unchanged (M1-CDX-14). */
    boolean pendingRevalidation;

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
        JButton revalidate = new JButton("Validate Node Again");
        revalidate.setToolTipText("Record the Node at this path again, after it was upgraded in place");
        revalidate.addActionListener(e -> requestRevalidation());
        detectNode.addActionListener(e -> detectNode());
        JButton detectLsp = new JButton("Detect…");
        detectLsp.addActionListener(e -> detectLsp());
        cliField = new TextFieldWithBrowseButton();
        cliField.addBrowseFolderListener(null, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withTitle("joust").withDescription("The native joust command line (brew install bloxbean/tap/joust)"));
        JButton detectCli = new JButton("Detect…");
        detectCli.addActionListener(e -> detectCli());
        cliStatus = new JBLabel();
        buildOnSave = new JBCheckBox("Build on save with the project's compiler");
        buildOnSave.setEnabled(false);
        buildOnSave.setToolTipText("Available once joust-lsp can pin a project's compiler (ADR-004 D5). Today the language "
                + "server always analyses with its own bundled compiler.");
        status = new JBLabel();
        confirmDevnet = new JBCheckBox("Confirm every devnet transaction (show the review before signing)");
        maxFeeAda = new com.intellij.ui.components.JBTextField(8);

        JPanel nodeRow = new JPanel(new java.awt.BorderLayout(4, 0));
        nodeRow.add(nodeField, java.awt.BorderLayout.CENTER);
        JPanel nodeButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        nodeButtons.add(detectNode);
        nodeButtons.add(revalidate);
        nodeRow.add(nodeButtons, java.awt.BorderLayout.EAST);
        JPanel lspRow = new JPanel(new java.awt.BorderLayout(4, 0));
        lspRow.add(lspField, java.awt.BorderLayout.CENTER);
        lspRow.add(detectLsp, java.awt.BorderLayout.EAST);
        JPanel cliRow = new JPanel(new java.awt.BorderLayout(4, 0));
        cliRow.add(cliField, java.awt.BorderLayout.CENTER);
        cliRow.add(detectCli, java.awt.BorderLayout.EAST);

        panel = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("<html>IntelliAda starts the .jsc language server you installed "
                        + "(<code>npm install -g @julclang/joust</code>) on the Node you name here, by absolute path. "
                        + "Paths inside a project or under <code>node_modules/.bin</code> are refused.</html>"))
                .addLabeledComponent("Node (22 or later):", nodeRow)
                .addLabeledComponent("joust-lsp:", lspRow)
                .addComponent(buildOnSave)
                .addComponent(status)
                .addSeparator()
                .addComponent(new JBLabel("<html>New JOUST projects (File | New | Project… | JOUST) are created by the "
                        + "native <code>joust</code> you name here, by absolute path. Save runs <code>joust version</code>"
                        + " once to check it.</html>"))
                .addLabeledComponent("joust (native CLI):", cliRow)
                .addComponent(cliStatus)
                .addSeparator()
                .addComponent(new JBLabel("JOUST Run: deploying and calling contracts"))
                .addComponent(confirmDevnet)
                .addLabeledComponent("Largest fee to sign (ADA):", maxFeeAda)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    /** Validate Node Again: Apply records Node again even with its path, size and mtime unchanged (M1-CDX-14). */
    public void requestRevalidation() {
        pendingRevalidation = true;
        if (status != null) status.setText(PlainText.html("Node will be checked again when you click Apply."));
    }

    @Override
    public boolean isModified() {
        JoustToolsState.Data d = JoustToolsState.getInstance().getState();
        return toolsModified(d) || cliModified() || runModified(d);
    }

    private boolean runModified(JoustToolsState.Data d) {
        return confirmDevnet.isSelected() != d.confirmDevnetTransactions || !Objects.equals(text(maxFeeAda), ada(d.maxFeeLovelace));
    }

    static String ada(long lovelace) {
        return java.math.BigDecimal.valueOf(lovelace, 6).stripTrailingZeros().toPlainString();
    }

    /** ADA with at most 6 decimals, more than 0 and at most 1000 (a devnet or test network fee). */
    static long lovelace(String ada) throws ConfigurationException {
        try {
            java.math.BigDecimal v = new java.math.BigDecimal(ada.strip());
            if (v.scale() > 6 || v.signum() <= 0 || v.compareTo(java.math.BigDecimal.valueOf(1000)) > 0) throw new NumberFormatException();
            return v.movePointRight(6).longValueExact();
        } catch (RuntimeException e) {
            throw new ConfigurationException("The largest fee must be an amount of ADA above 0 and at most 1000.");
        }
    }

    private boolean toolsModified(JoustToolsState.Data d) {
        return !Objects.equals(text(nodeField), nz(d.nodePath)) || !Objects.equals(text(lspField), nz(d.joustLspPath))
                || buildOnSave.isSelected() != d.buildOnSave || nodeChangedOnDisk(d) || pendingRevalidation;
    }

    /**
     * The native joust needs saving when its path changed, or when the file no longer matches what was validated (a
     * cheap check of real path, size and modification time; Save re-hashes it), so the user can validate it again.
     */
    private boolean cliModified() {
        JoustToolsState state = JoustToolsState.getInstance();
        String cli = text(cliField);
        if (!Objects.equals(cli, nz(state.getState().joustCliPath))) return true;
        if (cli.isEmpty()) return false;
        NodeIdentity id = state.validatedJoustCli();
        if (id == null) return true;
        try {
            Path real = Path.of(cli).toRealPath();
            return !real.toString().equals(id.realPath()) || Files.size(real) != id.size()
                    || Files.getLastModifiedTime(real).toMillis() != id.modifiedMillis();
        } catch (IOException | RuntimeException e) {
            return true;
        }
    }

    @Override
    public void apply() throws ConfigurationException {
        JoustToolsState state = JoustToolsState.getInstance();
        boolean toolsChanged = toolsModified(state.getState());
        boolean cliChanged = cliModified();
        long maxFee = lovelace(text(maxFeeAda));   // validated before anything is applied
        // D9: the native joust is validated first, so a refusal leaves every setting as it was.
        String cli = text(cliField);
        ValidatedCli vc = cliChanged && !cli.isEmpty() ? validateCli(cli) : null;
        state.getState().confirmDevnetTransactions = confirmDevnet.isSelected();
        state.getState().maxFeeLovelace = maxFee;
        if (toolsChanged) applyTools(state);
        if (cliChanged) {
            state.getState().joustCliPath = cli;
            state.setValidatedJoustCli(vc == null ? null : vc.identity(), vc == null ? null : vc.version());
        }
        reset();
    }

    private void applyTools(JoustToolsState state) throws ConfigurationException {
        String node = text(nodeField);
        String lsp = text(lspField);
        if (node.isEmpty() && lsp.isEmpty()) {
            state.getState().nodePath = "";
            state.getState().joustLspPath = "";
            state.setValidatedNode(null, null);
            state.getState().joustVersion = null;
            pendingRevalidation = false;
            JoustServerControl.restartAll("JOUST tools were cleared");
            return;
        }
        Validated v = validate(node, lsp);
        state.getState().nodePath = node;
        state.getState().joustLspPath = lsp;
        state.getState().buildOnSave = false;
        state.setValidatedNode(v.identity, v.nodeVersion);
        state.getState().joustVersion = v.joustVersion;
        pendingRevalidation = false;
        JoustServerControl.restartAll("JOUST tools changed");
    }

    @Override
    public void reset() {
        pendingRevalidation = false;
        if (nodeField == null) return;
        JoustToolsState.Data d = JoustToolsState.getInstance().getState();
        nodeField.setText(nz(d.nodePath));
        lspField.setText(nz(d.joustLspPath));
        buildOnSave.setSelected(d.buildOnSave);
        cliField.setText(nz(d.joustCliPath));
        confirmDevnet.setSelected(d.confirmDevnetTransactions);
        maxFeeAda.setText(ada(d.maxFeeLovelace));
        if (JoustToolsState.getInstance().validatedJoustCli() != null) {
            cliStatus.setText(PlainText.html("Validated: joust " + nz(d.joustCliVersion) + " at " + nz(d.joustCliRealPath) + "."));
        } else {
            cliStatus.setText(PlainText.html("Not configured: the JOUST new-project wizard needs it."));
        }
        String unsupported = com.bloxbean.intelliada.idea.joust.lsp.JoustLsp4jCompat.problem();
        if (unsupported != null) {
            status.setText(PlainText.html(unsupported));   // E22 (UP-DES-6): the tools may be valid, but nothing will start
        } else if (JoustToolsState.getInstance().looksConfigured()) {
            status.setText(PlainText.html("Validated: Node " + nz(d.nodeVersion) + " at " + nz(d.nodeRealPath)
                    + "; @julclang/joust " + nz(d.joustVersion) + ". Mode: advisory (the server's bundled compiler)."));
        } else {
            status.setText(PlainText.html("Not configured: .jsc files get TextMate highlighting only."));
        }
    }

    private record Validated(NodeIdentity identity, String nodeVersion, String joustVersion) {
    }

    private record ValidatedCli(NodeIdentity identity, String version) {
    }

    /**
     * D9's Save-time validation of the native joust: D4's rule, then {@code joust version} (the only execution), then
     * its identity. Refused when the answer is not a {@code joust <version>} line.
     */
    private static ValidatedCli validateCli(String cli) throws ConfigurationException {
        List<Path> roots = JoustForbiddenRoots.ofOpenProjects();
        JoustToolValidator.Result r = JoustToolValidator.checkExecutable(cli, "joust", roots, JoustPaths.Flavor.current());
        if (!r.ok()) throw new ConfigurationException(r.error(), "joust");
        ProbedCli probed;
        try {
            // M2-CDX-11: a private directory under the IDE's own temporary directory, checked before it is created.
            Path tempBase = Path.of(com.intellij.openapi.application.PathManager.getTempPath());
            // M2-CDX-17: the identity (a SHA-256 of a binary that can be hundreds of MB) is taken in the same
            // background task as the probe; only the result comes back to the EDT.
            probed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    () -> probeCli(r.realPath(), tempBase, roots), "Checking joust", true, null);
        } catch (IOException e) {
            throw new ConfigurationException("joust could not be run: " + e.getMessage(), "joust");
        }
        String line = probed.line();
        NodeIdentity id = probed.identity();
        String version = JoustCliVersionProbe.versionOf(line);
        if (version == null) {
            throw new ConfigurationException(r.realPath() + " does not look like JOUST's native joust: it answered \""
                    + PlainText.label(line, 60) + "\".", "joust");
        }
        return new ValidatedCli(id, PlainText.label(version, 40));
    }

    /** What {@code joust version} answered, and the identity of the file that answered. */
    record ProbedCli(String line, NodeIdentity identity) {
    }

    /**
     * The background half of D9's Save-time validation (M2-CDX-17), never on the EDT: the identity is taken before the
     * probe and checked after it, as for Node (M1-DES-9), so what was run is what is recorded; a file that changed in
     * between is refused.
     */
    static ProbedCli probeCli(Path realJoust, Path tempBase, List<Path> roots) throws IOException {
        ApplicationManager.getApplication().assertIsNonDispatchThread();
        NodeIdentity before = JoustToolValidator.identityOf(realJoust);
        ProgressManager.checkCanceled();
        String line = JoustCliVersionProbe.probe(realJoust, tempBase, roots);
        ProgressManager.checkCanceled();
        NodeIdentity after = JoustToolValidator.identityOf(realJoust);
        if (!before.equals(after)) throw new IOException("joust changed while it was being checked");
        return new ProbedCli(line, after);
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
            // The identity is taken before the probe and checked after it: what was run is what is recorded (M1-DES-9).
            Object[] r = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
                var ind = ProgressManager.getInstance().getProgressIndicator();
                NodeIdentity before = JoustToolValidator.identityOf(n.realPath());
                ProgressManager.checkCanceled();
                String v = NodeVersionProbe.probe(n.realPath(), () -> ind != null && ind.isCanceled());
                ProgressManager.checkCanceled();
                NodeIdentity after = JoustToolValidator.identityOf(n.realPath());
                if (!before.equals(after)) throw new IOException("Node changed while it was being checked");
                return new Object[]{v, after};
            }, "Checking Node", true, null);
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
                ProgressManager.checkCanceled();
                JoustToolValidator.Result r = JoustToolValidator.checkNode(p.toString(), roots, flavor);
                if (!r.ok()) continue;
                String v;
                try {
                    var ind = ProgressManager.getInstance().getProgressIndicator();
                    v = NodeVersionProbe.probe(r.realPath(), () -> ind != null && ind.isCanceled());
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

    /** D9: candidates for the native joust, by reading directories only; nothing is run until Save. */
    private void detectCli() {
        JoustPaths.Flavor flavor = JoustPaths.Flavor.current();
        List<Path> roots = JoustForbiddenRoots.ofOpenProjects();
        List<String> offers = new ArrayList<>();
        for (Path p : JoustToolDetector.joustCliCandidates(System.getenv(), userHome(), flavor)) {
            if (JoustToolValidator.checkExecutable(p.toString(), "joust", roots, flavor).ok()) offers.add(p.toString());
        }
        choose(offers, "joust", cliField);
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
        if (field == nodeField) pendingRevalidation = true;
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

    private static String text(javax.swing.text.JTextComponent f) {
        return f.getText() == null ? "" : f.getText().strip();
    }

    private static String text(TextFieldWithBrowseButton f) {
        return f.getText() == null ? "" : f.getText().trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
