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

/**
 * The Blockfrost REST shape (ADR-005 D18) that Yaci Store (DevKit), Yano and Blockfrost serve: GETs only for reads,
 * {@code POST /tx/submit} for submission. Strict: no redirects at all, a per-request timeout, a response size cap, and a
 * {@code project_id} sent only to the configured HTTPS host and never logged. A 404 is reported as {@code null}; every
 * other non-2xx answer, timeout or malformed body is a {@link Failed}, never an empty answer (T8).
 */
public final class BlockfrostApi {

    /** A provider failure; its message is safe to show and to send to the host as {@code failed.message}. */
    public static final class Failed extends Exception {
        public Failed(String why) {
            super(why, null, false, false);
        }
    }

    static final int MAX_RESPONSE = 16 * 1024 * 1024;
    private final URI base;
    private final @Nullable String projectId;
    private final HttpClient http;
    private final Duration timeout;
    private volatile long bytesRead;

    /**
     * @param baseUrl   the API root, e.g. {@code http://localhost:8080/api/v1} or {@code https://cardano-preprod.blockfrost.io/api/v0}
     * @param projectId the Blockfrost project id, or null; refused over plain HTTP
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
        this.http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(timeout).build();
    }

    /** Bytes of response bodies read so far (for a call's byte budget). */
    public long bytesRead() {
        return bytesRead;
    }

    /** GET {@code path} (already validated and encoded by the caller); null on 404. */
    public @Nullable JsonNode get(String path) throws Failed, InterruptedException {
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(base + path)).timeout(timeout).GET()
                .header("Accept", "application/json");
        if (projectId != null) rb.header("project_id", projectId);
        HttpResponse<InputStream> r;
        try {
            r = http.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new Failed("the provider could not be reached (" + e.getClass().getSimpleName() + ")");
        }
        byte[] body;
        try (InputStream in = r.body()) {
            body = in.readNBytes(MAX_RESPONSE + 1);
        } catch (IOException e) {
            throw new Failed("the provider's answer was cut off");
        }
        bytesRead += body.length;
        int status = r.statusCode();
        if (status == 404) return null;
        if (status < 200 || status > 299) throw new Failed("the provider answered HTTP " + status + " for " + kindOf(path));
        if (body.length > MAX_RESPONSE) throw new Failed("the provider's answer is larger than 16 MiB");
        try {
            return HostJson.MAPPER.readTree(body);
        } catch (IOException e) {
            throw new Failed("the provider's answer is not JSON");
        }
    }

    /** The first path segment, for a message that names no address or hash. */
    private static String kindOf(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        int i = p.indexOf('/');
        return i < 0 ? p : p.substring(0, i);
    }
}
