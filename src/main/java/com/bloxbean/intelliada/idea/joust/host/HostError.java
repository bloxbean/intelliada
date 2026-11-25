package com.bloxbean.intelliada.idea.joust.host;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * A terminal error the host answered (protocol 1 "Errors and schemas"): {@code kind} and {@code code} are what a client
 * branches on, never the message text (ADR-005 D16).
 */
public final class HostError extends Exception {

    /** One message-map site: the {@code .jsc} position a refusal points at. */
    public record Site(String file, int line, int col, String kind, String in, String what) {
    }

    private final String kind;
    private final String code;
    private final List<Site> sites;
    private final Long retryAtSlot;

    public HostError(String kind, String code, String message, List<Site> sites, Long retryAtSlot) {
        super(message, null, false, false);
        this.kind = kind;
        this.code = code;
        this.sites = List.copyOf(sites);
        this.retryAtSlot = retryAtSlot;
    }

    /** {@code bad}, {@code refusal}, {@code unsupported} or {@code internal}; {@code ""} when the host sent none. */
    public String kind() {
        return kind;
    }

    public String code() {
        return code;
    }

    public List<Site> sites() {
        return sites;
    }

    public Long retryAtSlot() {
        return retryAtSlot;
    }

    public boolean is(String kind, String code) {
        return this.kind.equals(kind) && this.code.equals(code);
    }

    /** Reads a JSON-RPC error object; fields the host omitted or mistyped become empty, never a guess. */
    static HostError of(JsonNode error) {
        JsonNode data = error.path("data");
        String kind = text(data.path("kind"));
        String code = text(data.path("code"));
        String message = data.path("message").isTextual() ? data.path("message").textValue() : text(error.path("message"));
        List<Site> sites = new ArrayList<>();
        JsonNode s = data.path("sites");
        if (s.isArray()) {
            for (int i = 0; i < s.size() && i < 64; i++) {
                JsonNode e = s.get(i);
                if (!e.isObject()) continue;
                sites.add(new Site(text(e.path("file")), e.path("line").asInt(0), e.path("col").asInt(0), text(e.path("kind")),
                        text(e.path("in")), text(e.path("what"))));
            }
        }
        JsonNode r = data.path("retryAtSlot");
        Long retry = r.isIntegralNumber() && r.canConvertToLong() && r.longValue() >= 0 ? r.longValue() : null;
        return new HostError(kind, code, message, sites, retry);
    }

    private static String text(JsonNode n) {
        return n.isTextual() ? n.textValue() : "";
    }

    @Override
    public String toString() {
        return kind + "/" + code + ": " + getMessage();
    }
}
