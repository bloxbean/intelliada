package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.host.HostError;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.JoustHostService;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectService;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.bloxbean.intelliada.idea.joust.trust.JoustTrust;
import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * ADR-005 M3.1b: the read-only JOUST Run tool window (D15). Contracts are listed from the module's built descriptions
 * before any host runs; Start binds the chosen devnet (D14), starts the project's host (D11) and shows each contract's
 * calls, deployment, prerequisites, instances and decoded state. Nothing here signs or submits (M3.3b). Every string
 * from the project, the host or the node is shown as plain text (I15).
 */
public final class JoustRunPanel implements Disposable {

    static final int CONSOLE_CAP = 256 * 1024;

    private final Project project;
    private final JPanel root = new JPanel(new BorderLayout());
    private final JComboBox<ModuleItem> modules = new JComboBox<>();
    private final JComboBox<NodeItem> nodes = new JComboBox<>();
    private final JButton start = new JButton("Start host");
    private final JButton stop = new JButton("Stop");
    private final JBLabel status = new JBLabel("Not running");
    private final DefaultListModel<String> contracts = new DefaultListModel<>();
    private final JBList<String> contractList = new JBList<>(contracts);
    private final DefaultListModel<String> instances = new DefaultListModel<>();
    private final JBList<String> instanceList = new JBList<>(instances);
    private final JBTextArea details = new JBTextArea();
    private final JBTextArea console = new JBTextArea();
    private volatile JoustRunSession session;

    public JoustRunPanel(Project project) {
        this.project = project;
        details.setEditable(false);
        console.setEditable(false);
        contractList.setCellRenderer(plainRenderer());
        instanceList.setCellRenderer(plainRenderer());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JBLabel("Module:"));
        top.add(modules);
        top.add(new JBLabel("Network:"));
        top.add(nodes);
        top.add(start);
        top.add(stop);
        top.add(status);
        root.add(top, BorderLayout.NORTH);

        JPanel actions = new JPanel(new GridLayout(0, 1, 0, JBUI.scale(2)));
        actions.add(button("Calls", c -> session().describe(c)));
        actions.add(button("Deployment", c -> session().deployment(c)));
        actions.add(button("Prerequisites", c -> session().prerequisites(c)));
        actions.add(button("Instances", c -> listInstances(c, false)));
        actions.add(button("More instances", c -> listInstances(c, true)));
        actions.add(button("State of instance", c -> session().view(c, selectedInstance(), null, null)));
        actions.add(button("Objects of instance", c -> session().objects(c, selectedInstance(), 100)));
        actions.add(button("Named view…", c -> {
            String[] in = ask("View name", "Arguments (JSON object in the description's encoding)", "{}");
            if (in == null) return null;
            JsonNode args = HostJson.parse(in[1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!args.isObject()) throw new IllegalArgumentException("the arguments must be a JSON object");
            return session().view(c, selectedInstance(), in[0], (com.fasterxml.jackson.databind.node.ObjectNode) args);
        }));
        actions.add(button("Decode datum…", c -> {
            String[] in = ask("Datum (hex CBOR)", null, null);
            if (in == null) return null;
            if (!in[0].matches("(?:[0-9a-fA-F]{2}){1,65536}")) throw new IllegalArgumentException("not hex CBOR");
            return session().decode(c, in[0].toLowerCase());
        }));
        JButton refresh = new JButton("Refresh modules and networks");
        refresh.addActionListener(e -> refreshChoices());
        actions.add(refresh);
        JPanel left = new JPanel(new BorderLayout());
        left.add(new JBScrollPane(contractList), BorderLayout.CENTER);
        JPanel leftSouth = new JPanel(new BorderLayout());
        leftSouth.add(actions, BorderLayout.NORTH);
        leftSouth.add(new JBScrollPane(instanceList), BorderLayout.CENTER);
        left.add(leftSouth, BorderLayout.SOUTH);

        JBTabbedPane tabs = new JBTabbedPane();
        tabs.addTab("Result", new JBScrollPane(details));
        tabs.addTab("JOUST host", new JBScrollPane(console));
        JBSplitter split = new JBSplitter(false, 0.3f);
        split.setFirstComponent(left);
        split.setSecondComponent(tabs);
        root.add(split, BorderLayout.CENTER);

        start.addActionListener(e -> startHost());
        stop.addActionListener(e -> stopHost());
        modules.addActionListener(e -> listDescriptions());
        contractList.addListSelectionListener(e -> {
            instances.clear();
            nextListing = null;
        });
        refreshChoices();
        updateButtons();
    }

    private volatile String nextListing;
    private volatile int nextAfter;
    private volatile boolean launching;

    private String selectedInstance() {
        String inst = instanceList.getSelectedValue();
        if (inst == null) throw new IllegalStateException("select an instance (Instances first)");
        return inst;
    }

    /** Asks for one or two values on the EDT; null when cancelled. */
    private static @Nullable String[] ask(String first, @Nullable String second, @Nullable String secondDefault) throws Exception {
        String[][] out = {null};
        ApplicationManager.getApplication().invokeAndWait(() -> {
            String a = com.intellij.openapi.ui.Messages.showInputDialog(first, "JOUST Run", null);
            if (a == null || a.isBlank()) return;
            String b = null;
            if (second != null) {
                b = com.intellij.openapi.ui.Messages.showInputDialog(second, "JOUST Run", null, secondDefault, null);
                if (b == null) return;
            }
            out[0] = new String[]{a.strip(), b};
        });
        return out[0];
    }

    public JComponent component() {
        return root;
    }

    // ---- choices ---------------------------------------------------------------------------------------------------

    record ModuleItem(Path dir) {
        @Override
        public String toString() {
            return PlainText.label(String.valueOf(dir.getFileName()), 80);
        }
    }

    record NodeItem(RemoteNode node) {
        @Override
        public String toString() {
            return PlainText.label(node.getName() + " (" + node.getNodeType() + ")", 80);
        }
    }

    /** Refills the choices: the project layout is computed off the EDT when not cached yet (M31B-CDX-7). */
    void refreshChoices() {
        if (session != null || launching) return;
        JoustProjectLayout layout = JoustProjectService.getInstance(project).cachedLayout();
        if (layout == null) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                JoustProjectService.getInstance(project).layout();
                ui(this::refreshChoices);
            });
        }
        modules.removeAllItems();
        if (layout != null) for (JoustProjectLayout.GradleModule m : layout.gradleModules()) modules.addItem(new ModuleItem(m.dir()));
        nodes.removeAllItems();
        // Only the networks this milestone serves (M31B-CDX-7).
        for (RemoteNode n : RemoteNodeState.getInstance().getRemoteNodes()) {
            var t = n.getNodeType();
            if (t == com.bloxbean.intelliada.idea.core.util.NodeType.YaciDevKit
                    || t == com.bloxbean.intelliada.idea.core.util.NodeType.LocalYaciDevKit) {
                nodes.addItem(new NodeItem(n));
            }
        }
        listDescriptions();
    }

    /** The contracts of the selected module's build output, before any host runs (D15; ADR5-DES-11). */
    private void listDescriptions() {
        if (session != null) return;
        contracts.clear();
        ModuleItem m = (ModuleItem) modules.getSelectedItem();
        if (m == null) return;
        for (String name : builtContracts(m.dir())) contracts.addElement(name);
        if (contracts.isEmpty()) details.setText("No built JOUST descriptions in this module: build it first.");
    }

    /** {@code <pkg>.<Contract>} for each {@code build/classes/java/main/META-INF/joust/*.json}, without following links. */
    static List<String> builtContracts(Path moduleDir) {
        List<String> out = new ArrayList<>();
        Path dir = moduleDir.resolve("build/classes/java/main/META-INF/joust");
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) return out;
        try (var list = Files.list(dir)) {
            list.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json") && n.matches("[A-Za-z0-9_.$]{1,300}\\.json"))
                    .map(n -> n.substring(0, n.length() - 5))
                    .sorted()
                    .limit(500)
                    .forEach(out::add);
        } catch (IOException ignored) {
            // nothing listed
        }
        return out;
    }

    // ---- host lifecycle -----------------------------------------------------------------------------------------

    private void startHost() {
        ModuleItem m = (ModuleItem) modules.getSelectedItem();
        NodeItem n = (NodeItem) nodes.getSelectedItem();
        if (m == null || n == null) return;
        if (!JoustTrust.isTrusted(project)) {
            details.setText("Trust the project to start its JOUST dev host (ADR-004 D1).");
            return;
        }
        JoustRunGuard guard = JoustRunGuard.capture(project);   // bound now, while trusted
        status.setText("Starting…");
        launching = true;
        updateButtons();   // the selections are frozen for the whole launch (M31B-CDX-8)
        new Task.Backgroundable(project, "JOUST: starting the dev host", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    JoustRunSession.Binding b = JoustRunSession.bind(n.node());
                    JoustHostService.Running r = JoustHostService.getInstance(project).start(guard, m.dir(),
                            JoustRunSession.initParams(b.identity()), b.key(),
                            JoustRunSession.reads(b), why -> ui(() -> ended(why)));
                    JoustRunSession s = new JoustRunSession(b, r);
                    ui(() -> started(s));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    ui(() -> {
                        launching = false;
                        status.setText("Not running");
                        details.setText("The host did not start: " + PlainText.literal(String.valueOf(e.getMessage()), 2000));
                        updateButtons();
                    });
                }
            }
        }.queue();
    }

    private void started(JoustRunSession s) {
        launching = false;
        session = s;
        appendConsole(s.running().stderr().text());
        // Registered after the snapshot of the text so far, so early lines are not shown twice (M31B-DES-9).
        s.running().stderr().listen(text -> ui(() -> appendConsole(text)));
        contracts.clear();
        for (JsonNode c : s.running().initialize().path("contracts")) {
            String name = c.path("name").textValue();
            if (name != null) contracts.addElement(name);
        }
        String version = s.running().joustVersion();
        status.setText(PlainText.label("Running: JOUST " + (version == null ? "?" : version) + " on magic "
                + s.binding().identity().magic(), 120));
        details.setText("Host started. Select a contract.");
        updateButtons();
    }

    private void ended(String why) {
        session = null;
        status.setText("Not running");
        details.setText("The host ended: " + PlainText.literal(why, 2000));
        listDescriptions();
        updateButtons();
    }

    private void stopHost() {
        JoustRunSession s = session;
        ModuleItem m = (ModuleItem) modules.getSelectedItem();
        Path dir = s != null ? s.running().moduleDir() : launching && m != null ? m.dir() : null;
        if (dir == null) return;
        // Stop also reaches a launch in progress: the service refuses it before or as its process starts.
        ApplicationManager.getApplication().executeOnPooledThread(() -> JoustHostService.getInstance(project).stop(dir));
    }

    private void updateButtons() {
        boolean busy = session != null || launching;
        start.setEnabled(!busy);
        stop.setEnabled(busy);
        modules.setEnabled(!busy);
        nodes.setEnabled(!busy);
    }

    // ---- calls --------------------------------------------------------------------------------------------------

    private interface ContractCall {
        JsonNode apply(String contract) throws Exception;
    }

    private JButton button(String label, ContractCall call) {
        JButton b = new JButton(label);
        b.addActionListener(e -> {
            String c = contractList.getSelectedValue();
            if (c == null || session == null) {
                details.setText(session == null ? "Start the host first." : "Select a contract.");
                return;
            }
            details.setText(label + "…");
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                String text;
                try {
                    JsonNode result = call.apply(c);
                    if (result == null) {
                        ui(() -> details.setText(""));
                        return;
                    }
                    text = HostJson.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
                } catch (HostError he) {
                    text = describe(he);
                } catch (Exception ex) {
                    text = "Failed: " + ex.getMessage();
                }
                String shown = PlainText.literal(text, 200_000);
                ui(() -> details.setText(shown));
            });
        });
        return b;
    }

    /** The first page, or with {@code more} the next page of the last listing (protocol continuation; M31B-CDX-10). */
    private JsonNode listInstances(String contract, boolean more) throws Exception {
        JsonNode r;
        if (more) {
            if (nextListing == null) throw new IllegalStateException("no further page: list the instances first");
            r = session().nextPage("instances", nextListing, nextAfter, 100);
        } else {
            r = session().instances(contract, 100);
            ui(instances::clear);
        }
        JsonNode next = r.path("next");
        nextListing = next.isIntegralNumber() ? r.path("listing").textValue() : null;
        nextAfter = next.isIntegralNumber() ? next.intValue() : 0;
        List<String> refs = new ArrayList<>();
        for (JsonNode it : r.path("items")) {
            String inst = it.path("instance").textValue();
            if (inst != null && inst.matches("[0-9a-f]{64}") && !refs.contains(inst)) refs.add(inst);
        }
        ui(() -> refs.stream().filter(x -> !instances.contains(x)).forEach(instances::addElement));
        return r;
    }

    /** A host error by kind and code (D16), with any message-map sites. */
    static String describe(HostError e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.kind()).append('/').append(e.code()).append(": ").append(e.getMessage());
        for (HostError.Site s : e.sites()) {
            sb.append("\n  at ").append(s.file()).append(':').append(s.line()).append(':').append(s.col());
            if (!s.in().isEmpty()) sb.append(" in ").append(s.in());
            if (!s.what().isEmpty()) sb.append(" — ").append(s.what());
        }
        if (e.retryAtSlot() != null) sb.append("\n  retry at slot ").append(e.retryAtSlot());
        return sb.toString();
    }

    private JoustRunSession session() {
        JoustRunSession s = session;
        if (s == null) throw new IllegalStateException("the host is not running");
        return s;
    }

    private void appendConsole(String text) {
        console.append(text);
        int over = console.getDocument().getLength() - CONSOLE_CAP;
        if (over > 0) console.replaceRange("", 0, over);
    }

    private static void ui(Runnable r) {
        ApplicationManager.getApplication().invokeLater(r);
    }

    private static ListCellRenderer<Object> plainRenderer() {
        DefaultListCellRenderer d = new DefaultListCellRenderer();
        return (list, value, index, selected, focus) ->
                d.getListCellRendererComponent(list, PlainText.literal(String.valueOf(value), 300), index, selected, focus);
    }

    @Override
    public void dispose() {
        session = null;
    }

    @Nullable JoustRunSession currentSession() {
        return session;
    }
}
