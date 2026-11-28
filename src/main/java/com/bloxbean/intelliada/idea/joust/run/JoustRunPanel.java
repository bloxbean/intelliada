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
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * ADR-005 M3.1b, M3.3b3: the JOUST Run tool window (D15). Contracts are listed from the module's built descriptions
 * before any host runs; Start binds the chosen devnet (D14), starts the project's host (D11) and shows each contract's
 * calls, deployment, prerequisites, instances and decoded state. Deploy and Call prepare, check, sign and submit through
 * the session (M3.3b2), with the review modal when the setting asks for it; History shows the session's journal. Every
 * session call runs off the EDT. Every string from the project, the host or the node is shown as plain text (I15).
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
    private final JBTextArea history = new JBTextArea();
    private final javax.swing.Timer historyTimer = new javax.swing.Timer(5_000, e -> refreshHistory());
    /** The account last used to sign, preselected in the next form (EDT only). */
    private @Nullable String lastCaller;
    /** A write (Deploy or Call) in progress: one at a time (EDT only). */
    private boolean writing;
    /** TTL of the bodies the panel prepares, in slots, within the network's maximum (D14). */
    static final long TTL_SLOTS = 300;

    static long ttl(JoustRunSession s) {
        return Math.max(1, Math.min(TTL_SLOTS, com.bloxbean.intelliada.idea.joust.journal.TimePolicy.maxTtlSlots(s.binding().identity()) - 1));
    }
    private volatile JoustRunSession session;
    private final com.bloxbean.intelliada.idea.joust.accounts.JoustAccountsPanel accounts;

    public JoustRunPanel(Project project) {
        this.project = project;
        this.accounts = new com.bloxbean.intelliada.idea.joust.accounts.JoustAccountsPanel(project);
        details.setEditable(false);
        console.setEditable(false);
        history.setEditable(false);
        history.setFont(JBUI.Fonts.create(java.awt.Font.MONOSPACED, JBUI.Fonts.label().getSize()));
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
        actions.add(button("Calls", (s, c, i, pg) -> s.describe(c)));
        actions.add(button("Deployment", (s, c, i, pg) -> s.deployment(c)));
        actions.add(button("Prerequisites", (s, c, i, pg) -> s.prerequisites(c)));
        actions.add(button("Instances", (s, c, i, pg) -> listInstances(s, c)));
        actions.add(button("State of instance", true, (s, c, i, pg) -> s.view(c, required(i), null, null)));
        actions.add(button("Objects of instance", true, (s, c, i, pg) -> listObjects(s, c, required(i))));
        // Instance-specific only when the pending listing is an objects listing (M32-CDX-9, -DES-10).
        // One pager captured at the click serves both the request and its result guard (M32-CDX-22).
        actions.add(button("Next page", pg -> pg != null && pg.method().equals("objects"), (s, c, i, pg) -> nextPage(s, c, i, pg)));
        actions.add(button("Named view…", true, (s, c, i, pg) -> {
            String[] in = ask("View name", "Arguments (JSON object in the description's encoding)", "{}");
            if (in == null) return null;
            JsonNode args = HostJson.parse(in[1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!args.isObject()) throw new IllegalArgumentException("the arguments must be a JSON object");
            return s.view(c, required(i), in[0], (com.fasterxml.jackson.databind.node.ObjectNode) args);
        }));
        actions.add(button("Decode datum…", (s, c, i, pg) -> {
            String[] in = ask("Datum (hex CBOR)", null, null);
            if (in == null) return null;
            if (!in[0].matches("(?:[0-9a-fA-F]{2}){1,65536}")) throw new IllegalArgumentException("not hex CBOR");
            return s.decode(c, in[0].toLowerCase());
        }));
        actions.add(writeButton("Deploy…", true));
        actions.add(writeButton("Call…", false));
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
        tabs.addTab("History", new JBScrollPane(history));
        tabs.addTab("JOUST host", new JBScrollPane(console));
        tabs.addTab("Accounts", accounts.component());
        JBSplitter split = new JBSplitter(false, 0.3f);
        split.setFirstComponent(left);
        split.setSecondComponent(tabs);
        root.add(split, BorderLayout.CENTER);

        start.addActionListener(e -> startHost());
        stop.addActionListener(e -> stopHost());
        modules.addActionListener(e -> listDescriptions());
        contractList.addListSelectionListener(e -> {
            instances.clear();
            pager = null;
        });
        instanceList.addListSelectionListener(e -> {
            if (pager != null && pager.method().equals("objects")) pager = null;
        });
        refreshChoices();
        updateButtons();
    }

    /** The continuation of the last listing; read and written on the EDT only (M31B-CDX-19). */
    private JoustRunPager pager;
    private volatile boolean launching;
    private volatile JoustRunLaunch launch;
    /** The launch whose host the current session is (M31B-CDX-31). */
    private JoustRunLaunch sessionLaunch;
    /** The latest action: only it may write the result pane (EDT only; M31B-CDX-30). */
    private long actionSeq;

    private static String required(@Nullable String instance) {
        if (instance == null) throw new IllegalStateException("select an instance (Instances first)");
        return instance;
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
        JoustProjectLayout cached = JoustProjectService.getInstance(project).cachedLayout();
        if (cached != null) {
            applyChoices(cached);
            return;
        }
        details.setText("Scanning the project…");
        // One scan, applied once: a project with no readable base shows an empty state, and is scanned again only on an
        // explicit Refresh (M31B-CDX-25).
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            JoustProjectLayout scanned = JoustProjectService.getInstance(project).layout();
            ui(() -> {
                if (session == null && !launching) applyChoices(scanned);
            });
        });
    }

    private void applyChoices(@Nullable JoustProjectLayout layout) {
        modules.removeAllItems();
        if (layout != null) for (JoustProjectLayout.GradleModule m : layout.gradleModules()) modules.addItem(new ModuleItem(m.dir()));
        if (layout == null) details.setText("The project could not be read: no JOUST modules to list.");
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
        JoustRunLaunch l = new JoustRunLaunch();
        launch = l;
        launching = true;
        updateButtons();   // the selections are frozen for the whole launch (M31B-CDX-8)
        new Task.Backgroundable(project, "JOUST: starting the dev host", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                JoustHostService service = JoustHostService.getInstance(project);
                JoustRunSession s = null;
                String failure = null;
                try {
                    // Stop at any point cancels this launch: before the host starts it is never started, and a host
                    // that started meanwhile is stopped (M31B-CDX-17).
                    s = l.run(x -> JoustRunSession.bind(n.node()),
                            // New or reused, the panel observes the end of the host it adopts (M31B-CDX-36); a new host is
                            // classified against IntelliAda's journal first and restored from it (D17).
                            b -> JoustRunSession.start(service, m.dir(), b, null,
                                    (init, key, reads) -> service.start(guard, m.dir(), init, key, reads, why -> { }),
                                    r -> r.session().onEnded(why -> ui(() -> ended(l, why)))),
                            started -> service.stop(started.running().moduleDir()),
                            indicator::isCanceled);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failure = "Stopped before the host started.";
                } catch (Exception e) {
                    String m2 = JoustRunLaunch.message(e);
                    failure = m2 != null ? m2 : "The host did not start: " + PlainText.literal(String.valueOf(e.getMessage()), 2000);
                } finally {
                    // Every terminal path restores the controls.
                    JoustRunSession ok = s;
                    String why = ok == null && failure == null ? "The host did not start." : failure;
                    ui(() -> {
                        if (launch == l) launch = null;
                        launching = false;
                        if (ok != null && !l.publishable(ok.running().session().ended())) {
                            // The host ended (or Stop came) before this completion reached the EDT: never shown as
                            // running (M31B-CDX-31).
                            status.setText("Not running");
                            String e = l.ended() != null ? l.ended() : ok.running().session().ended();
                            details.setText(e == null ? "Stopped." : "The host ended: " + PlainText.literal(e, 2000));   // M31B-DES-18
                            listDescriptions();
                            updateButtons();
                        } else if (ok != null) {
                            sessionLaunch = l;
                            started(ok);
                        } else {
                            status.setText("Not running");
                            details.setText(why);
                            updateButtons();
                        }
                    });
                }
            }
        }.queue();
    }

    private void started(JoustRunSession s) {
        launching = false;
        session = s;
        accounts.bindSession(s.binding().identity().networkClass());   // I12: the tab shows this session's class only
        // D13: the review modal before signing, when the user asked for it (devnets: off by default).
        s.reviewer(summary -> !com.bloxbean.intelliada.idea.joust.settings.JoustToolsState.getInstance().getState().confirmDevnetTransactions
                || onEdtAny(() -> new com.bloxbean.intelliada.idea.joust.review.JoustReviewDialog(project, summary).showAndGet()));
        historyTimer.start();
        refreshHistory();
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

    /** The end callback of launch {@code l}'s host, on the EDT: recorded on the launch; clears only its own session. */
    private void ended(JoustRunLaunch l, String why) {
        l.markEnded(why);
        if (sessionLaunch != l) return;   // not published yet (the completion sees the mark), or an older host
        sessionLaunch = null;
        session = null;
        historyTimer.stop();
        accounts.bindSession(null);
        status.setText("Not running");
        details.setText("The host ended: " + PlainText.literal(why, 2000));
        listDescriptions();
        updateButtons();
    }

    private void stopHost() {
        JoustRunSession s = session;
        JoustRunLaunch l = launch;
        ModuleItem m = (ModuleItem) modules.getSelectedItem();
        if (l != null) l.cancel();   // binding, or a start in progress: recorded before anything else (M31B-CDX-17)
        Path dir = s != null ? s.running().moduleDir() : l != null && m != null ? m.dir() : null;
        if (dir == null) return;
        // The service refuses a start whose slot is being stopped, or stops the process it just created.
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

    /** One action, with the session, contract and instance captured on the EDT when it was pressed (M31B-CDX-30). */
    private interface ContractCall {
        @Nullable JsonNode apply(JoustRunSession session, String contract, @Nullable String instance, @Nullable JoustRunPager pager)
                throws Exception;
    }

    private JButton button(String label, ContractCall call) {
        return button(label, pg -> false, call);
    }

    private JButton button(String label, boolean perInstance, ContractCall call) {
        return button(label, pg -> perInstance, call);
    }

    /**
     * An action; a result decided {@code perInstance} (when the action is pressed) is shown only while its instance is
     * still selected (M31B-CDX-35).
     */
    private JButton button(String label, java.util.function.Predicate<JoustRunPager> perInstanceFor, ContractCall call) {
        JButton b = new JButton(label);
        b.addActionListener(e -> {
            // The session, contract and instance are captured here, on the EDT (M31B-CDX-19).
            String c = contractList.getSelectedValue();
            JoustRunSession s = session;
            if (c == null || s == null) {
                details.setText(s == null ? "Start the host first." : "Select a contract.");
                return;
            }
            String inst = instanceList.getSelectedValue();
            JoustRunPager pagerAtClick = pager;
            boolean perInstance = perInstanceFor.test(pagerAtClick);
            long seq = ++actionSeq;
            details.setText(label + "…");
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                String text;
                try {
                    JsonNode result = call.apply(s, c, inst, pagerAtClick);
                    if (result == null) {
                        ui(() -> {
                            if (owns(seq, s, c, perInstance, inst)) details.setText("");
                        });
                        return;
                    }
                    text = HostJson.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
                } catch (HostError he) {
                    text = describe(he);
                } catch (Exception ex) {
                    text = "Failed: " + ex.getMessage();
                }
                String shown = PlainText.literal(text, 200_000);
                ui(() -> {
                    if (owns(seq, s, c, perInstance, inst)) details.setText(shown);   // a stale result never replaces a newer one
                });
            });
        });
        return b;
    }

    // ---- deploy and call (M3.3b3) --------------------------------------------------------------------------------

    /** Deploy… (init after the prerequisites) or Call… (any call the description offers). */
    private JButton writeButton(String label, boolean deploy) {
        JButton b = new JButton(label);
        b.addActionListener(e -> {
            String c = contractList.getSelectedValue();
            JoustRunSession s = session;
            if (c == null || s == null) {
                details.setText(s == null ? "Start the host first." : "Select a contract.");
                return;
            }
            if (writing) {
                details.setText("A transaction is in progress; wait for it to finish.");
                return;
            }
            String inst = instanceList.getSelectedValue();
            details.setText(label);
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                List<JoustCallDialog.Call> calls;
                List<com.bloxbean.intelliada.idea.joust.accounts.JoustAccounts.Entry> signers;
                try {
                    calls = JoustCallDialog.calls(s.callsOf(c), deploy);
                    signers = s.signers();
                } catch (Exception ex) {
                    String m = PlainText.literal("Failed: " + ex.getMessage(), 2000);
                    ui(() -> details.setText(m));
                    return;
                }
                ui(() -> {
                    if (session != s) return;
                    if (calls.isEmpty()) {
                        details.setText(deploy ? "This contract has no init call: Deploy creates instances with init." : "No call to make.");
                        return;
                    }
                    if (signers.isEmpty()) {
                        details.setText("No account can sign on this network: create or import one in the Accounts tab.");
                        return;
                    }
                    JoustCallDialog d = new JoustCallDialog(project, deploy ? "Deploy " + PlainText.label(c, 80) : "Call "
                            + PlainText.label(c, 80), calls, signers, inst, lastCaller);
                    if (!d.showAndGet()) {
                        details.setText("Cancelled.");
                        return;
                    }
                    JoustCallDialog.Choice choice;
                    try {
                        choice = d.choice();
                    } catch (Exception ex) {
                        details.setText(PlainText.literal("Failed: " + ex.getMessage(), 2000));
                        return;
                    }
                    lastCaller = choice.callerId();
                    write(s, c, deploy, choice);
                });
            });
        });
        return b;
    }

    /** Runs one Deploy or Call in a background task, reporting each step, and waits for it to land. */
    private void write(JoustRunSession s, String contract, boolean deploy, JoustCallDialog.Choice choice) {
        writing = true;
        details.setText("");
        new Task.Backgroundable(project, (deploy ? "JOUST: deploying " : "JOUST: calling ") + PlainText.label(contract, 60), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                StringBuilder out = new StringBuilder();
                java.util.function.Consumer<String> step = line -> {
                    out.append(line).append('\n');
                    String shown = PlainText.literal(out.toString(), 200_000);
                    indicator.setText(PlainText.title(line, 120));
                    ui(() -> details.setText(shown));
                };
                try {
                    String address = s.signers().stream().filter(a -> a.id.equals(choice.callerId())).map(a -> a.address)
                            .findFirst().orElseThrow(() -> new IllegalStateException("the caller is no longer an account of this network"));
                    JoustRunSession.Submitted last;
                    if (deploy) {
                        ObjectNode init = HostJson.object().put("method", choice.call().path("method").asText());
                        init.set("args", choice.call().path("args"));
                        if (choice.call().has("pay")) init.set("pay", choice.call().get("pay"));
                        List<JoustRunSession.Submitted> done;
                        try {
                            done = s.deploy(contract, choice.callerId(), init, ttl(s), java.time.Duration.ofMinutes(5), step::accept);
                        } catch (JoustRunSession.DeployStopped stopped) {
                            for (var x : stopped.submitted()) step.accept("Submitted " + x.txHash());
                            throw stopped;
                        }
                        for (var x : done) step.accept("Submitted " + x.txHash());
                        last = done.isEmpty() ? null : done.get(done.size() - 1);
                    } else {
                        ObjectNode call = choice.call().deepCopy().put("contract", contract).put("address", address);
                        step.accept("Preparing " + call.path("method").asText() + "…");
                        last = s.submit("prepare", HostJson.object().put("ttlSlots", ttl(s)).set("call", call), choice.callerId(),
                                choice.coSignerIds());
                        if (last != null) step.accept("Submitted " + last.txHash());
                    }
                    if (last != null) {
                        if (last.instance() != null) step.accept("Instance " + last.instance());
                        step.accept("Waiting for it to land…");
                        boolean landed = s.awaitInclusion(last.txHash(), java.time.Duration.ofMinutes(3));
                        step.accept(landed ? "Landed on chain." : "Not on chain yet: History shows when it settles.");
                    }
                } catch (HostError he) {
                    step.accept(describe(he));
                } catch (Exception ex) {
                    step.accept("Failed: " + ex.getMessage());
                } finally {
                    ui(() -> {
                        writing = false;
                        refreshHistory();
                    });
                }
            }
        }.queue();
    }

    /** History (L7): read off the EDT, shown as plain text. */
    private void refreshHistory() {
        JoustRunSession s = session;
        if (s == null) return;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String text;
            try {
                text = historyText(s.history());
            } catch (Exception e) {
                text = "History is unavailable: " + e.getMessage();
            }
            String shown = PlainText.literal(text, 200_000);
            ui(() -> {
                if (session == s && !shown.equals(history.getText())) history.setText(shown);
            });
        });
    }

    static String historyText(List<com.bloxbean.intelliada.idea.joust.journal.JournalStore.Entry> entries) {
        if (entries.isEmpty()) return "No transactions in this session's journal yet.";
        StringBuilder sb = new StringBuilder();
        for (int i = entries.size() - 1; i >= 0; i--) {
            var e = entries.get(i);
            sb.append(String.format("%-10s %-28s %s", e.state(), PlainText.label(e.label(), 28), e.txHash()));
            if (e.inclusion() != null) sb.append("  slot ").append(e.inclusion().slot());
            else sb.append("  valid until ").append(e.validTo());
            if (!e.events().isEmpty()) sb.append("  ").append(PlainText.label(e.events().get(e.events().size() - 1), 60));
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Runs {@code f} on the EDT in any modality (a background task may wait on it); returns its value. */
    private static <T> T onEdtAny(java.util.function.Supplier<T> f) {
        Object[] out = {null};
        ApplicationManager.getApplication().invokeAndWait(() -> out[0] = f.get(), com.intellij.openapi.application.ModalityState.any());
        @SuppressWarnings("unchecked") T t = (T) out[0];
        return t;
    }

    /** Whether action {@code seq} still owns the result pane: the latest action, on the same session and contract. */
    private boolean owns(long seq, JoustRunSession s, String contract, boolean perInstance, @Nullable String instance) {
        return seq == actionSeq && session == s && contract.equals(contractList.getSelectedValue())
                && (!perInstance || java.util.Objects.equals(instance, instanceList.getSelectedValue()));
    }

    /** The first page of a contract's instances; the list and the continuation are replaced together on the EDT. */
    private JsonNode listInstances(JoustRunSession s, String contract) throws Exception {
        JsonNode r = s.instances(contract, 100);
        JoustRunPager next = JoustRunPager.next(r, s, "instances", contract, null);
        List<String> refs = instanceRefs(r);
        ui(() -> {
            if (session != s || !contract.equals(contractList.getSelectedValue())) return;   // a stale listing (M31B-CDX-19)
            instances.clear();
            refs.forEach(instances::addElement);
            pager = next;
        });
        return r;
    }

    /** The first page of an instance's objects, with its own continuation (M31B-CDX-20). */
    private JsonNode listObjects(JoustRunSession s, String contract, String instance) throws Exception {
        JsonNode r = s.objects(contract, instance, 100);
        JoustRunPager next = JoustRunPager.next(r, s, "objects", contract, instance);
        ui(() -> {
            if (session == s && contract.equals(contractList.getSelectedValue()) && instance.equals(instanceList.getSelectedValue())) {
                pager = next;
            }
        });
        return r;
    }

    /** The next page of the last listing, while it still belongs to the selection; instances are appended. */
    private JsonNode nextPage(JoustRunSession s, String contract, @Nullable String inst, @Nullable JoustRunPager p) throws Exception {
        if (p == null || !p.belongsTo(s, contract, inst)) throw new IllegalStateException("no further page for this selection");
        JsonNode r = s.nextPage(p.method(), p.listing(), p.after(), 100);
        JoustRunPager next = JoustRunPager.next(r, s, p.method(), contract, p.instance());
        List<String> refs = p.method().equals("instances") ? instanceRefs(r) : List.of();
        ui(() -> {
            if (pager != p || session != s || !contract.equals(contractList.getSelectedValue())) return;
            refs.stream().filter(x -> !instances.contains(x)).forEach(instances::addElement);
            pager = next;
        });
        return r;
    }

    private static List<String> instanceRefs(JsonNode r) {
        List<String> refs = new ArrayList<>();
        for (JsonNode it : r.path("items")) {
            String inst = it.path("instance").textValue();
            if (inst != null && inst.matches("[0-9a-f]{64}") && !refs.contains(inst)) refs.add(inst);
        }
        return refs;
    }

    private static <T> T onEdt(java.util.function.Supplier<T> f) {
        Object[] out = {null};
        ApplicationManager.getApplication().invokeAndWait(() -> out[0] = f.get());
        @SuppressWarnings("unchecked") T t = (T) out[0];
        return t;
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
        historyTimer.stop();
        session = null;
    }

    @Nullable JoustRunSession currentSession() {
        return session;
    }
}
