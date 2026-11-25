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
        actions.add(button("Instances", this::listInstances));
        actions.add(button("State of instance", c -> {
            String inst = instanceList.getSelectedValue();
            if (inst == null) throw new IllegalStateException("select an instance (Instances first)");
            return session().view(c, inst, null);
        }));
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
        refreshChoices();
        updateButtons();
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

    void refreshChoices() {
        modules.removeAllItems();
        JoustProjectLayout layout = JoustProjectService.getInstance(project).cachedLayout();
        if (layout != null) for (JoustProjectLayout.GradleModule m : layout.gradleModules()) modules.addItem(new ModuleItem(m.dir()));
        nodes.removeAllItems();
        for (RemoteNode n : RemoteNodeState.getInstance().getRemoteNodes()) nodes.addItem(new NodeItem(n));
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
        start.setEnabled(false);
        new Task.Backgroundable(project, "JOUST: starting the dev host", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    JoustRunSession.Binding b = JoustRunSession.bind(n.node());
                    JoustHostService.Running r = JoustHostService.getInstance(project).start(guard, m.dir(),
                            JoustRunSession.initParams(b.identity()), String.valueOf(n.node().getId()),
                            JoustRunSession.reads(b), why -> ui(() -> ended(why)));
                    JoustRunSession s = new JoustRunSession(b, r);
                    r.stderr().listen(text -> ui(() -> appendConsole(text)));
                    ui(() -> started(s));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    ui(() -> {
                        status.setText("Not running");
                        details.setText("The host did not start: " + PlainText.literal(String.valueOf(e.getMessage()), 2000));
                        updateButtons();
                    });
                }
            }
        }.queue();
    }

    private void started(JoustRunSession s) {
        session = s;
        appendConsole(s.running().stderr().text());
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
        if (s == null) return;
        Path dir = s.running().moduleDir();
        ApplicationManager.getApplication().executeOnPooledThread(() -> JoustHostService.getInstance(project).stop(dir));
    }

    private void updateButtons() {
        boolean running = session != null;
        start.setEnabled(!running);
        stop.setEnabled(running);
        modules.setEnabled(!running);
        nodes.setEnabled(!running);
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
                    text = HostJson.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(call.apply(c));
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

    private JsonNode listInstances(String contract) throws Exception {
        JsonNode r = session().instances(contract, 100);
        List<String> refs = new ArrayList<>();
        for (JsonNode it : r.path("items")) {
            String inst = it.path("instance").textValue();
            if (inst != null && inst.matches("[0-9a-f]{64}") && !refs.contains(inst)) refs.add(inst);
        }
        ui(() -> {
            instances.clear();
            refs.forEach(instances::addElement);
        });
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
