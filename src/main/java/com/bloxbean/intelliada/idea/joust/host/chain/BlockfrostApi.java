package com.bloxbean.intelliada.idea.joust.host.chain;

import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The Blockfrost REST shape (ADR-005 D18) that Yaci Store (DevKit), Yano and Blockfrost serve. Strict: no redirects at
 * all, a monotonic deadline covering headers <b>and</b> body (M31B-CDX-2), a response size cap applied while receiving,
 * and a {@code project_id} sent only to the configured HTTPS host and never logged. A 404 is reported as {@code null};
 * every other non-2xx answer, timeout or malformed body is a {@link Failed}, never an empty answer (T8).
 */
public final class BlockfrostApi {

    /** A provider failure; its message is safe to show and to send to the host as {@code failed.message}. */
    public static final class Failed extends Exception {
        public Failed(String why) {
            super(why, null, false, false);
        }
    }

    static final int MAX_RESPONSE = 16 * 1024 * 1024;
    /** Daemon threads for the client and for body reads (never the shared common pool, whose threads a stall would hold). */
    private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "JOUST chain reads");
        t.setDaemon(true);
        return t;
    });
    private final URI base;
    private final @Nullable String projectId;
    private final HttpClient http;
    private final Duration timeout;
    private final java.util.concurrent.atomic.AtomicLong bytesRead = new java.util.concurrent.atomic.AtomicLong();

    /**
     * @param baseUrl   the API root, e.g. {@code http://localhost:8080/api/v1} or {@code https://cardano-preprod.blockfrost.io/api/v0}
     * @param projectId the Blockfrost project id, or null; refused over plain HTTP
     * @param timeout   the default deadline of one request (headers and body)
     */
    public BlockfrostApi(String baseUrl, @Nullable String projectId, Duration timeout) {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.base = URI.create(b);
        String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw new IllegalArgumentException("not an http(s) endpoint");
        if (projectId != null && !projectId.isEmpty() && !scheme.equals("https")) {
            throw new IllegalArgumentException("a Blockfrost project id is sent over HTTPS only");
        }
        this.projectId = projectId == null || projectId.isEmpty() ? null : projectId;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(timeout)
                .version(HttpClient.Version.HTTP_1_1).executor(IO).build();
    }

    /** The configured endpoint (for a binding's identity). */
    public URI base() {
        return base;
    }

    /** Bytes of response bodies read so far through this instance (for a call's byte budget). */
    public long bytesRead() {
        return bytesRead.get();
    }

    /**
     * Another client for the same endpoint and credentials with its own byte count: the host's reads are charged only
     * for their own requests, never the panel's (M31B-DES-12).
     */
    public BlockfrostApi copy() {
        return new BlockfrostApi(base.toString(), projectId, timeout);
    }

    /** GET {@code path} within this API's default timeout; null on 404. */
    public @Nullable JsonNode get(String path) throws Failed, InterruptedException {
        return get(path, System.nanoTime() + timeout.toNanos());
    }

    /**
     * GET {@code path} (already validated and encoded by the caller) before the monotonic {@code deadlineNanos}: the
     * headers and the whole body must arrive by then. Null on 404.
     */
    public @Nullable JsonNode get(String path, long deadlineNanos) throws Failed, InterruptedException {
        return get(path, deadlineNanos, MAX_RESPONSE);
    }

    /** As {@link #get(String, long)}, reading at most {@code maxBytes} of body (a call's remaining budget; M31B-SEC-6). */
    public @Nullable JsonNode get(String path, long deadlineNanos, long maxBytes) throws Failed, InterruptedException {
        int cap = (int) Math.max(0, Math.min(MAX_RESPONSE, maxBytes));
        long left = deadlineNanos - System.nanoTime();
        if (left <= 0) throw new Failed("the read did not finish in time");
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofNanos(left)).GET()
                .header("Accept", "application/json");
        if (projectId != null) rb.header("project_id", projectId);
        CompletableFuture<HttpResponse<InputStream>> f = http.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
        HttpResponse<InputStream> r;
        try {
            r = f.get(Math.max(1, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            throw new Failed("the provider did not answer " + kindOf(path) + " in time");
        } catch (ExecutionException e) {
            throw new Failed("the provider could not be reached (" + e.getCause().getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            // The waiting thread was interrupted (a host shutdown): abandon the exchange, and close a body that may
            // still arrive, before propagating (M31B-CDX-24).
            f.cancel(true);
            f.thenAccept(late -> closeQuietly(late.body()));
            throw e;
        }
        InputStream in = r.body();
        CompletableFuture<byte[]> body = CompletableFuture.supplyAsync(() -> {
            try (in) {
                return in.readNBytes(cap + 1);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, IO);
        byte[] b;
        try {
            b = body.get(Math.max(1, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            closeQuietly(in);
            body.cancel(true);
            throw new Failed("the provider's answer to " + kindOf(path) + " did not arrive in time");
        } catch (ExecutionException e) {
            closeQuietly(in);
            throw new Failed("the provider's answer was cut off");
        } catch (InterruptedException e) {
            closeQuietly(in);   // ends the body read on the I/O thread (M31B-CDX-24)
            body.cancel(true);
            throw e;
        }
        bytesRead.addAndGet(b.length);
        int status = r.statusCode();
        if (status == 404) return null;
        if (status < 200 || status > 299) throw new Failed("the provider answered HTTP " + status + " for " + kindOf(path));
        if (b.length > cap) throw new Failed(cap < MAX_RESPONSE ? "the answer exceeds this call's read budget" : "the provider's answer is larger than 16 MiB");
        try {
            return HostJson.MAPPER.readTree(b);
        } catch (IOException e) {
            throw new Failed("the provider's answer is not JSON");
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // aborting a body
        }
    }

    /** The first path segment, for a message that names no address or hash. */
    private static String kindOf(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        int i = p.indexOf('/');
        int q = p.indexOf('?');
        int end = i < 0 ? (q < 0 ? p.length() : q) : i;
        return p.substring(0, end);
    }
}
