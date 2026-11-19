package com.bloxbean.intelliada.idea.joust.core;

/**
 * The Node binary the user validated (ADR-004 D4): its real path, size, modification time and SHA-256. At server start
 * IntelliAda re-checks this identity without executing anything; any difference stops the start.
 */
public record NodeIdentity(String realPath, long size, long modifiedMillis, String sha256) {
}
