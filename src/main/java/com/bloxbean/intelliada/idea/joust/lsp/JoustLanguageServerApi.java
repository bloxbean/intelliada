package com.bloxbean.intelliada.idea.joust.lsp;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageServer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The read-only custom requests IntelliAda sends to {@code joust-lsp} (ADR-004 D3, F15). Each answers any client and
 * writes nothing. IntelliAda never sends {@code joust/rename}, {@code joust/trust} or {@code joust/settings}.
 *
 * <p>Answers are plain {@code java.util.Map}s, never Gson types: IntelliAda bundles its own Gson, and a Gson class from
 * LSP4IJ's class loader is a different class from IntelliAda's.
 *
 * <p>{@code joust/status} is not requested: joust-lsp also <i>pushes</i> {@code joust/status} as a notification, which
 * {@link JoustLanguageClient} receives, and LSP4J keys methods by name in one table for both directions, so declaring
 * the request too would make its answer parse as the notification's (void).
 */
public interface JoustLanguageServerApi extends LanguageServer {

    @JsonRequest("joust/restartAnalysis")
    CompletableFuture<Map<String, Object>> joustRestartAnalysis(Map<String, Object> params);

    @JsonRequest("joust/generatedJava")
    CompletableFuture<Map<String, Object>> joustGeneratedJava(Map<String, Object> params);

    @JsonRequest("joust/generatedContent")
    CompletableFuture<Map<String, Object>> joustGeneratedContent(Map<String, Object> params);

    @JsonRequest("joust/checkBuild")
    CompletableFuture<Map<String, Object>> joustCheckBuild(Map<String, Object> params);
}
