package com.bloxbean.intelliada.idea.joust.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The client side of one dev-host session (ADR-005 D11, I22; protocol 1 "Launch and framing").
 * <ul>
 *   <li><b>One forward call at a time.</b> A second call while one runs (or while a cancelled one unwinds) is refused here,
 *   before it reaches the host.</li>
 *   <li><b>Reverse reads</b> ({@code chain/read}) are accepted only while a forward call is in flight, with session-unique
 *   string ids, and answered on a pooled thread by {@link Reads}. A read that cannot be answered cancels the call.</li>
 *   <li><b>Violations</b> end the session and stop the host: an unsolicited or second response, a reverse request outside
 *   a call or with a reused id, any other host message, stray bytes, a malformed frame, end of output during a call, a
 *   frame (either direction) unfinished ten seconds after its first byte, or a call past its hard timeout.</li>
 * </ul>
 * The host process itself is owned by the caller; {@code stop} must end it (destroy the process tree it launched).
 */
public final class HostSession implements AutoCloseable {

    /** Answers one {@code chain/read}: the result object ({@code tip} and exactly one outcome), or throws to cancel. */
    @FunctionalInterface
    public interface Reads {
        ObjectNode read(String id, JsonNode params) throws Exception;
    }

    /** The session ended because the peer broke the protocol (I22). */
    public static final class Violation extends Exception {
        public Violation(String why) {
            super(why, null, false, false);
        }
    }

    /** The session is over (stopped, exited, or ended by a violation): no further call can run. */
    public static final class Ended extends Exception {
        public Ended(String why) {
            super(why, null, false, false);
        }
    }

    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final long CANCEL_GRACE_MS = 3_000;

    private final HostFrames.Reader reader;
    private final HostFrames.Writer writer;
    private final Runnable stop;
    private final Reads reads;
    private final Consumer<String> onEnd;
    private final ExecutorService readPool = Executors.newFixedThreadPool(2, daemon("JOUST host read"));
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(daemon("JOUST host watchdog"));
    private final Set<String> reverseIds = ConcurrentHashMap.newKeySet();
    private final Object lock = new Object();
    private Call current;
    private long nextId = 1;
    private volatile String ended;
    private volatile boolean violated;
    private final Thread readerThread;
    private final long stallNanos;

    private static final class Call {
        final long id;
        final CompletableFuture<JsonNode> result = new CompletableFuture<>();
        boolean answered;

        Call(long id) {
            this.id = id;
        }
    }

    /**
     * @param hostOut the host's stdout (frames only)
     * @param hostIn  the host's stdin
     * @param stop    ends the host process; called once, on the first end of the session
     * @param reads   answers reverse reads
     * @param onEnd   told why the session ended (a violation's reason, "the host exited", "stopped")
     */
    public HostSession(InputStream hostOut, OutputStream hostIn, Runnable stop, Reads reads, Consumer<String> onEnd) {
        this(hostOut, hostIn, stop, reads, onEnd, HostFrames.STALL_NANOS);
    }

    /** As above, with the frame stall limit given (tests). */
    HostSession(InputStream hostOut, OutputStream hostIn, Runnable stop, Reads reads, Consumer<String> onEnd, long stallNanos) {
        this.stallNanos = stallNanos;
        this.reader = new HostFrames.Reader(hostOut);
        this.writer = new HostFrames.Writer(hostIn);
        this.stop = stop;
        this.reads = reads;
        this.onEnd = onEnd;
        this.readerThread = new Thread(this::readLoop, "JOUST host reader");
        readerThread.setDaemon(true);
        readerThread.start();
        watchdog.scheduleWithFixedDelay(this::checkStalls, 250, 250, TimeUnit.MILLISECONDS);
    }

    /** Why the session ended, or null while it is live. */
    public @Nullable String ended() {
        return ended;
    }

    /** True when the session ended because the host broke the protocol (I22). */
    public boolean violated() {
        return violated;
    }

    /**
     * Runs one forward call and returns its result. Blocks the calling thread (never the EDT) until the host answers,
     * {@code cancelled} turns true (a {@code $/cancelRequest} is sent and the call's terminal answer still awaited), or the
     * hard timeout passes (the host is then stopped).
     *
     * @throws HostError for the host's terminal error (a cancelled call ends as {@code internal/cancelled})
     * @throws Ended     when the session is or becomes over
     */
    public JsonNode call(String method, JsonNode params, Duration hardTimeout, BooleanSupplier cancelled)
            throws HostError, Ended, InterruptedException {
        Call c;
        synchronized (lock) {
            if (ended != null) throw new Ended(ended);
            if (current != null) throw new IllegalStateException("a host call is already in flight");
            c = current = new Call(nextId++);
        }
        try {
            ObjectNode req = F.objectNode().put("jsonrpc", "2.0").put("id", c.id).put("method", method);
            req.set("params", params == null ? F.objectNode() : params);
            send(req);
            long deadline = System.nanoTime() + hardTimeout.toNanos();
            boolean cancelSent = false;
            while (true) {
                try {
                    return c.result.get(100, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    long now = System.nanoTime();
                    if (!cancelSent && (cancelled.getAsBoolean() || now >= deadline)) {
                        cancelSent = true;
                        send(F.objectNode().put("jsonrpc", "2.0").put("method", "$/cancelRequest")
                                .set("params", F.objectNode().put("id", c.id)));
                        deadline = Math.min(deadline, now) + TimeUnit.MILLISECONDS.toNanos(CANCEL_GRACE_MS);
                    } else if (cancelSent && now >= deadline) {
                        end("the host did not answer " + method + " in time", true);
                    }
                } catch (ExecutionException e) {
                    Throwable t = e.getCause();
                    if (t instanceof HostError he) throw he;
                    if (t instanceof Ended en) throw en;
                    throw new Ended(String.valueOf(t.getMessage()));
                } catch (CancellationException e) {
                    throw new Ended(ended == null ? "the call was abandoned" : ended);
                }
            }
        } finally {
            synchronized (lock) {
                if (current == c) current = null;
            }
        }
    }

    /** Sends a notification ({@code exit}); best effort. */
    public void notify(String method) {
        try {
            send(F.objectNode().put("jsonrpc", "2.0").put("method", method));
        } catch (Ended ignored) {
            // the session is already over
        }
    }

    /**
     * {@code shutdown}, then {@code exit} and end of the host's input; then keeps reading until the host's output ends (or
     * {@code timeout}), so a frame the host still sends is reported as a violation rather than ignored (I20, I22).
     */
    public void shutdown(Duration timeout) throws InterruptedException {
        try {
            call("shutdown", F.objectNode(), timeout, () -> false);
        } catch (HostError | Ended ignored) {
            // the host is stopped regardless
        }
        notify("exit");
        writer.close();
        long end = System.nanoTime() + timeout.toNanos();
        while (ended == null && System.nanoTime() < end) Thread.sleep(20);
        end("stopped", false);
    }

    @Override
    public void close() {
        end("stopped", false);
    }

    private void send(JsonNode msg) throws Ended {
        if (ended != null) throw new Ended(ended);
        byte[] body = HostJson.write(msg);
        try {
            writer.write(body);
        } catch (IOException e) {
            end("the host stopped reading its input", true);
            throw new Ended(ended);
        }
    }

    private void readLoop() {
        try {
            while (ended == null) {
                byte[] body = reader.next();
                if (body == null) {
                    boolean inCall;
                    synchronized (lock) {
                        inCall = current != null;
                    }
                    end(inCall ? "the host's output ended during a call" : "the host exited", inCall);
                    return;
                }
                handle(HostJson.parse(body));
            }
        } catch (HostFrames.Violation | HostJson.Malformed | Violation e) {
            end(e.getMessage(), true);
        } catch (IOException e) {
            end("the host's output failed: " + e.getMessage(), true);
        } catch (RuntimeException e) {
            end("the host channel failed: " + e, true);
        }
    }

    private void handle(JsonNode m) throws Violation {
        if (!m.isObject() || !"2.0".equals(m.path("jsonrpc").textValue())) throw new Violation("a frame that is not a JSON-RPC 2.0 object");
        JsonNode method = m.get("method");
        if (method != null) {
            JsonNode id = m.get("id");
            if (!"chain/read".equals(method.textValue()) || id == null || !id.isTextual()) {
                throw new Violation("an unexpected message from the host");
            }
            if (m.size() != 4 || !m.path("params").isObject()) throw new Violation("a malformed chain/read request");
            synchronized (lock) {
                if (current == null) throw new Violation("a chain/read outside a call");
            }
            if (!reverseIds.add(id.textValue())) throw new Violation("a reused chain/read id");
            answer(id.textValue(), m.get("params"));
            return;
        }
        JsonNode id = m.get("id");
        boolean hasResult = m.has("result"), hasError = m.has("error");
        if (id == null || !id.canConvertToLong() || !id.isIntegralNumber() || hasResult == hasError || m.size() != 3) {
            throw new Violation("a malformed response");
        }
        Call c;
        synchronized (lock) {
            c = current;
            if (c == null || c.id != id.longValue() || c.answered) throw new Violation("an unsolicited or second response");
            c.answered = true;
        }
        if (hasResult) {
            c.result.complete(m.get("result"));
        } else {
            JsonNode e = m.get("error");
            if (!e.isObject()) throw new Violation("a malformed error response");
            c.result.completeExceptionally(HostError.of(e));
        }
    }

    private final java.util.concurrent.atomic.AtomicInteger readsInFlight = new java.util.concurrent.atomic.AtomicInteger();

    /** Waits until every reverse read handed to {@link Reads} has been answered or abandoned (tests and orderly stops). */
    boolean awaitReadsIdle(Duration timeout) throws InterruptedException {
        long end = System.nanoTime() + timeout.toNanos();
        while (readsInFlight.get() > 0) {
            if (System.nanoTime() > end) return false;
            Thread.sleep(10);
        }
        return true;
    }

    private void answer(String id, JsonNode params) {
        readsInFlight.incrementAndGet();
        readPool.execute(() -> {
          try {
            ObjectNode result;
            try {
                result = reads.read(id, params);
            } catch (Exception | LinkageError e) {
                cancelCurrent();
                return;
            }
            ObjectNode resp = F.objectNode().put("jsonrpc", "2.0").put("id", id);
            resp.set("result", result);
            try {
                send(resp);
            } catch (Ended ignored) {
                // nothing to answer any more
            }
          } finally {
            readsInFlight.decrementAndGet();
          }
        });
    }

    private void cancelCurrent() {
        Call c;
        synchronized (lock) {
            c = current;
        }
        if (c == null) return;
        try {
            send(F.objectNode().put("jsonrpc", "2.0").put("method", "$/cancelRequest").set("params", F.objectNode().put("id", c.id)));
        } catch (Ended ignored) {
            // over already
        }
    }

    private void checkStalls() {
        long now = System.nanoTime();
        long r = reader.startedAt(), w = writer.writingSince();
        if (r != 0 && now - r >= stallNanos) end("the host's frame stalled", true);
        else if (w != 0 && now - w >= stallNanos) end("the host stopped reading its input", true);
    }

    private void end(String why, boolean violation) {
        Call c;
        synchronized (lock) {
            if (ended != null) return;
            violated = violation;
            ended = why;
            c = current;
        }
        try {
            stop.run();
        } finally {
            writer.close();
            if (c != null) c.result.completeExceptionally(new Ended(why));
            readPool.shutdownNow();
            watchdog.shutdownNow();
            try {
                onEnd.accept(violation ? "protocol violation: " + why : why);
            } catch (RuntimeException ignored) {
                // a listener's failure does not keep the session alive
            }
        }
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
