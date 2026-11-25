package com.bloxbean.intelliada.idea.joust.run;

import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Nullable;

/**
 * The continuation of an {@code instances} or {@code objects} listing (protocol 1: {@code listing}, {@code next}),
 * tied to the selection that requested it (M31B-CDX-10, -19, -20): it is applied, and followed, only while the panel
 * still shows that session, contract and (for objects) instance.
 */
record JoustRunPager(Object session, String method, String contract, @Nullable String instance, String listing, long after) {

    /** The next page of {@code result}, or null when the host gave none. */
    static @Nullable JoustRunPager next(JsonNode result, Object session, String method, String contract, @Nullable String instance) {
        JsonNode next = result.path("next");
        String listing = result.path("listing").textValue();
        if (!next.isIntegralNumber() || !next.canConvertToLong() || next.longValue() < 0 || listing == null) return null;
        return new JoustRunPager(session, method, contract, instance, listing, next.longValue());
    }

    /** Whether this continuation belongs to what the panel shows now. */
    boolean belongsTo(@Nullable Object session, @Nullable String contract, @Nullable String instance) {
        return this.session == session && this.contract.equals(contract)
                && (method.equals("instances") || java.util.Objects.equals(this.instance, instance));
    }
}
