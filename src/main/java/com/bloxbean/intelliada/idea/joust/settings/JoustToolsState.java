package com.bloxbean.intelliada.idea.joust.settings;

import com.bloxbean.intelliada.idea.joust.core.JoustLaunchPlanner;
import com.bloxbean.intelliada.idea.joust.core.NodeIdentity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/**
 * ADR-004 D4: the application-level JOUST tool settings. Nothing under a project or {@code .idea/} can name an
 * executable, a path or an argument (I7). Machine-local: never roamed.
 */
@State(name = "IntelliAdaJoustTools", storages = @Storage(value = "intelliada-joust.xml", roamingType = RoamingType.DISABLED))
public final class JoustToolsState implements PersistentStateComponent<JoustToolsState.Data> {

    /** The persisted fields. */
    public static final class Data {
        public String nodePath = "";
        public String joustLspPath = "";
        /** D3: build on save with the project's compiler; off by default, and without effect until D5's pin release. */
        public boolean buildOnSave = false;
        public String nodeRealPath;
        public long nodeSize;
        public long nodeModified;
        public String nodeSha256;
        public String nodeVersion;
        public String joustVersion;
        /**
         * ADR-004 D9: the user's native {@code joust} (the CLI the new-project wizard runs), an absolute path validated
         * as in D4. Application-level only: nothing under a project sets it (I7).
         */
        public String joustCliPath = "";
        /** The identity of the native {@code joust} when it was validated on Save (real path, size, mtime, SHA-256). */
        public String joustCliRealPath;
        public long joustCliSize;
        public long joustCliModified;
        public String joustCliSha256;
        public String joustCliVersion;
        /** ADR-005 D13: show the review modal before signing a devnet transaction (off by default on devnets). */
        public boolean confirmDevnetTransactions = false;
        /** ADR-005 D14b: the largest fee IntelliAda signs, in lovelace (also the checker's {@code maxFee}). */
        public long maxFeeLovelace = 5_000_000;
    }

    private Data data = new Data();

    public static JoustToolsState getInstance() {
        return ApplicationManager.getApplication().getService(JoustToolsState.class);
    }

    @Override
    public @NotNull Data getState() {
        return data;
    }

    @Override
    public void loadState(@NotNull Data state) {
        this.data = state;
    }

    public NodeIdentity validatedNode() {
        if (data.nodeRealPath == null || data.nodeSha256 == null) return null;
        return new NodeIdentity(data.nodeRealPath, data.nodeSize, data.nodeModified, data.nodeSha256);
    }

    public void setValidatedNode(NodeIdentity id, String version) {
        data.nodeRealPath = id == null ? null : id.realPath();
        data.nodeSize = id == null ? 0 : id.size();
        data.nodeModified = id == null ? 0 : id.modifiedMillis();
        data.nodeSha256 = id == null ? null : id.sha256();
        data.nodeVersion = version;
    }

    /** The native {@code joust} as validated on Save (D9), or {@code null}. */
    public NodeIdentity validatedJoustCli() {
        if (data.joustCliRealPath == null || data.joustCliSha256 == null) return null;
        return new NodeIdentity(data.joustCliRealPath, data.joustCliSize, data.joustCliModified, data.joustCliSha256);
    }

    public void setValidatedJoustCli(NodeIdentity id, String version) {
        data.joustCliRealPath = id == null ? null : id.realPath();
        data.joustCliSize = id == null ? 0 : id.size();
        data.joustCliModified = id == null ? 0 : id.modifiedMillis();
        data.joustCliSha256 = id == null ? null : id.sha256();
        data.joustCliVersion = version;
    }

    public JoustLaunchPlanner.Settings settings() {
        return new JoustLaunchPlanner.Settings(data.nodePath, data.joustLspPath, validatedNode());
    }

    /** A cheap check for LSP4IJ's enablement: both paths set and Node validated. The full check runs at start. */
    public boolean looksConfigured() {
        return data.nodePath != null && !data.nodePath.isBlank() && data.joustLspPath != null && !data.joustLspPath.isBlank()
                && validatedNode() != null;
    }
}
