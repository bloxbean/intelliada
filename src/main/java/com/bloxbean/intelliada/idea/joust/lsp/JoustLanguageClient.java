package com.bloxbean.intelliada.idea.joust.lsp;

import com.bloxbean.intelliada.idea.joust.core.JoustLocationPolicy;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.core.ReadOnlyFeatureSet;
import com.bloxbean.intelliada.idea.joust.settings.JoustForbiddenRoots;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.bloxbean.intelliada.idea.joust.editor.JoustSafeOpen;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ADR-004 D3a items 2 to 5 and I25: in M1–M3 the client accepts no edit and renders no server text as HTML, whatever the
 * server sends.
 *
 * <ul>
 *   <li>{@code workspace/applyEdit} is answered {@code applied: false}.</li>
 *   <li>{@code client/registerCapability} is accepted only for the read-only set R; anything else refuses the whole
 *   request.</li>
 *   <li>{@code window/showDocument} never opens externally; only a {@code file:} URI whose real path is inside the
 *   project opens.</li>
 *   <li>{@code window/showMessage} and {@code showMessageRequest} are escaped plain text, with no URL listener; action
 *   items are labels.</li>
 *   <li>{@code workspace/workspaceFolders} answers only in-base folders.</li>
 * </ul>
 */
public class JoustLanguageClient extends LanguageClientImpl {

    private static final Logger LOG = Logger.getInstance(JoustLanguageClient.class);
    static final String APPLY_EDIT_REFUSAL =
            "IntelliAda applies no edit from the JOUST language server: .jsc support is read-only (ADR-004 D3a).";

    public JoustLanguageClient(Project project) {
        super(project);
    }

    /**
     * M1-CDX-24: diagnostics only for a local {@code file:} document this server has open. LSP4IJ would otherwise
     * resolve any server-named URI through the VFS before checking it, so an unsolicited or remote URI is dropped from
     * its text, before any lookup. A local file the user opened outside the project is still connected, so its advisory
     * diagnostics stay.
     */
    @Override
    public void publishDiagnostics(org.eclipse.lsp4j.PublishDiagnosticsParams params) {
        if (params == null || JoustLocationPolicy.localPathOf(params.getUri()) == null) return;
        java.net.URI uri;
        try {
            uri = new java.net.URI(params.getUri());
        } catch (java.net.URISyntaxException e) {
            return;
        }
        var features = getClientFeatures();
        var wrapper = features == null ? null : features.getServerWrapper();
        if (wrapper == null || !wrapper.isConnectedTo(uri)) return;
        super.publishDiagnostics(params);
    }

    /**
     * ADR-004 §12.2 E24 (UP-CDX-6): IntelliAda sends the JOUST server no settings. LSP4IJ's own handler would answer from
     * the configuration a user may have stored for this server in LSP4IJ's settings; every item is answered null.
     */
    @Override
    public CompletableFuture<List<Object>> configuration(org.eclipse.lsp4j.ConfigurationParams params) {
        int n = params == null || params.getItems() == null ? 0 : params.getItems().size();
        return CompletableFuture.completedFuture(java.util.Collections.nCopies(n, null));   // one per item (UP-CDX-6)
    }

    /**
     * E24 (UP-CDX-7): inline values are outside R. LSP4IJ 0.30.1's handler rebuilds the views of every suspended debug
     * session in the project, whoever's it is; for the JOUST server a refresh does nothing.
     */
    @Override
    public CompletableFuture<Void> refreshInlineValues() {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * ADR-004 §12.2 E25 (UP-SEC-6, UP-CDX-10): in a headless application (tests, and headless runs such as command-line
     * inspection), server progress is not handed to LSP4IJ. LSP4IJ 0.30.1 runs a progress task through
     * {@code ProgressManager.run}, and {@code Task.isHeadless()} makes that synchronous on the single message-listener
     * thread; the task then waits for the next progress message, which that thread can no longer read, and the
     * connection stops. With a UI the task runs in the background. The token request is answered without registering a
     * token, so none accumulate. Progress text is tested through {@code JoustClientFeatures.SafeProgressFeature}.
     */
    static boolean progressStallsListener() {
        var app = ApplicationManager.getApplication();
        return app.isUnitTestMode() || app.isHeadlessEnvironment();
    }

    @Override
    public void notifyProgress(org.eclipse.lsp4j.ProgressParams params) {
        if (progressStallsListener()) return;
        super.notifyProgress(params);
    }

    @Override
    public CompletableFuture<Void> createProgress(org.eclipse.lsp4j.WorkDoneProgressCreateParams params) {
        if (progressStallsListener()) return CompletableFuture.completedFuture(null);
        return super.createProgress(params);
    }

    /** E24: pull diagnostics are outside R (the server's {@code diagnosticProvider} is dropped), so a refresh does nothing. */
    @Override
    public CompletableFuture<Void> refreshDiagnostics() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        ApplyWorkspaceEditResponse r = new ApplyWorkspaceEditResponse(false);
        r.setFailureReason(APPLY_EDIT_REFUSAL);
        return CompletableFuture.completedFuture(r);
    }

    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        List<Registration> regs = params == null || params.getRegistrations() == null ? List.of() : params.getRegistrations();
        for (Registration r : regs) {
            if (!ReadOnlyFeatureSet.allowsRegistration(r.getMethod())) {
                LOG.info("Refused a dynamic registration of " + PlainText.label(r.getMethod(), 80) + " from the JOUST server");
                return CompletableFuture.failedFuture(new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidRequest,
                        "IntelliAda accepts only read-only registrations from the JOUST server; refused: "
                                + PlainText.label(r.getMethod(), 80), null)));
            }
        }
        if (regs.isEmpty()) return CompletableFuture.completedFuture(null);
        return acceptRegistration(params);
    }

    /** Hands a registration that passed the filter to LSP4IJ; a test seam for the filter. */
    protected CompletableFuture<Void> acceptRegistration(RegistrationParams params) {
        return super.registerCapability(params);
    }

    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
        if (params == null || Boolean.TRUE.equals(params.getExternal())) {
            return CompletableFuture.completedFuture(new ShowDocumentResult(false));
        }
        Project project = getProject();
        Path base = JoustForbiddenRoots.baseReal(project);
        Path target = JoustLocationPolicy.allowedTarget(params.getUri(), base, JoustForbiddenRoots.baseSpelled(project));
        if (target == null) return CompletableFuture.completedFuture(new ShowDocumentResult(false));
        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target);
        if (vf == null) return CompletableFuture.completedFuture(new ShowDocumentResult(false));
        int line = params.getSelection() == null || params.getSelection().getStart() == null ? 0
                : Math.max(0, params.getSelection().getStart().getLine());
        int col = params.getSelection() == null || params.getSelection().getStart() == null ? 0
                : Math.max(0, params.getSelection().getStart().getCharacter());
        boolean focus = !Boolean.FALSE.equals(params.getTakeFocus());
        CompletableFuture<ShowDocumentResult> done = new CompletableFuture<>();
        ApplicationManager.getApplication().invokeLater(() -> {
            boolean opened = !project.isDisposed() && JoustSafeOpen.open(project, vf, line, col, focus);
            done.complete(new ShowDocumentResult(opened));
        });
        return done;
    }

    @Override
    public void showMessage(MessageParams params) {
        if (params == null) return;
        JoustNotifications.show(getProject(), "JOUST language server", params.getMessage(), typeOf(params.getType()));
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams params) {
        CompletableFuture<MessageActionItem> answer = new CompletableFuture<>();
        if (params == null) {
            answer.complete(null);
            return answer;
        }
        Notification n = JoustNotifications.create("JOUST language server", params.getMessage(), typeOf(params.getType()));
        AtomicBoolean answered = new AtomicBoolean();
        List<MessageActionItem> actions = params.getActions() == null ? List.of() : params.getActions();
        for (MessageActionItem item : actions.subList(0, Math.min(actions.size(), 5))) {
            String label = PlainText.label(item.getTitle(), 40);
            if (label.isEmpty()) continue;
            n.addAction(NotificationAction.create(label, (event, notification) -> {
                // Answer first: expiring runs whenExpired synchronously, which would otherwise answer null (M1-CDX-17).
                if (answered.compareAndSet(false, true)) answer.complete(item);
                notification.expire();
            }));
        }
        n.whenExpired(() -> {
            if (answered.compareAndSet(false, true)) answer.complete(null);
        });
        n.notify(getProject());
        return answer;
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        return CompletableFuture.completedFuture(JoustProjectFolders.confined(getProject()));
    }

    /** joust-lsp pushes {@code joust/status}; IntelliAda keeps it for the status bar (labels only, I15). */
    @JsonNotification("joust/status")
    public void joustStatus(java.util.Map<String, Object> status) {
        JoustStatusCache.getInstance(getProject()).update(status);
    }

    private static NotificationType typeOf(MessageType t) {
        if (t == MessageType.Error) return NotificationType.ERROR;
        if (t == MessageType.Warning) return NotificationType.WARNING;
        return NotificationType.INFORMATION;
    }
}
