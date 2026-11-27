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
 *   <li><b>One forward call at a time.</b> Calls queue (fairly) behind the running one and reach the host only after its
 *   terminal answer, even when a waiting caller gives up.</li>
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

        /**
         * As {@link #read(String, JsonNode)}, with the JSON-RPC id of the forward call that owned the read when it arrived
         * (captured at dispatch, before any worker runs; M33B1-CDX-24).
         */
        default ObjectNode read(String id, JsonNode params, long call) throws Exception {
            return read(id, params);
        }
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
    /** Further end listeners: a panel that adopts a running host observes its end too (M31B-CDX-36). */
    private final java.util.List<Consumer<String>> endListeners = new java.util.ArrayList<>();
    private String endMessage;
    private final ExecutorService readPool = Executors.newFixedThreadPool(2, daemon("JOUST host read"));
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(daemon("JOUST host watchdog"));
    private final Set<String> reverseIds = ConcurrentHashMap.newKeySet();
    private final Object lock = new Object();
    private Call current;
    private long nextId = 1;
    private volatile long lastCall;

    /** The JSON-RPC id of the last forward call dispatched (calls are serial: the one that just returned). */
    public long lastCallId() {
        return lastCall;
    }
    /** One forward call at a time, in arrival order (ADR-005 D11 serial calls); released when a call's terminal answer arrives. */
    private final java.util.concurrent.Semaphore turn = new java.util.concurrent.Semaphore(1, true);
    private volatile boolean initialized;
    /** The highest read generation seen: each call's must be greater than every earlier call's (M31A-DES-12). */
    private long lastGeneration;
    private volatile boolean initializing;
    private final java.util.concurrent.atomic.AtomicBoolean initClaimed = new java.util.concurrent.atomic.AtomicBoolean();
    /** Set by {@link #shutdown}: queued calls give up so that shutdown takes the next turn (M31A-CDX-20). */
    private volatile boolean stopping;
    /** initialize's {@code limits.readsPerCall}: more reverse reads in one call is a violation (M31A-SEC-7). */
    private volatile int readsPerCall = 256;
    private volatile String ended;
    private volatile boolean violated;
    private final Thread readerThread;
    private final long stallNanos;

    private static final class Call {
        final long id;
        final CompletableFuture<JsonNode> result = new CompletableFuture<>();
        final String method;
        boolean answered;
        int reads;
        /** Monotonic deadlines the watchdog enforces, whoever is waiting (M31A-CDX-16): cancel at the first, stop at the second. */
        volatile long cancelAt;
        volatile long stopAt;
        volatile boolean cancelSent;
        final java.util.concurrent.CountDownLatch written = new java.util.concurrent.CountDownLatch(1);
        /** True once the request is fully written: only then may the host's reads belong to it (M31A-DES-12). */
        boolean sent;
        /** The generation its reverse ids carry ({@code <generation>.<sequence>}), fixed by its first read. */
        long generation;

        Call(long id, String method, long cancelAt) {
            this.id = id;
            this.method = method;
            this.cancelAt = cancelAt;
            this.stopAt = Long.MAX_VALUE;
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
        // The watchdog first, so an immediate EOF cannot shut it down before it is scheduled (M31A-CDX-21).
        watchdog.scheduleWithFixedDelay(this::checkStalls, 100, 100, TimeUnit.MILLISECONDS);
        readerThread.start();
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
        if (!initialized && !method.equals("initialize") && !method.equals("shutdown")) {
            throw new IllegalStateException("the host session is not initialized");
        }
        if (method.equals("initialize")) {
            throw new IllegalStateException("initialize is sent once, through initialize(...)");   // M31A-DES-14, -CDX-43
        }
        return dispatch(method, params, hardTimeout, cancelled);
    }

    /** The one path that writes forward calls; {@code initialize} reaches it only from {@link #initialize}. */
    private JsonNode dispatch(String method, JsonNode params, Duration hardTimeout, BooleanSupplier cancelled)
            throws HostError, Ended, InterruptedException {
        // Wait for our turn: calls queue rather than reach the host while another runs (bad/busy). A cancelled or
        // stopping wait gives up without writing anything (M31A-CDX-19, -20).
        long deadline = System.nanoTime() + hardTimeout.toNanos();
        boolean isShutdown = method.equals("shutdown");
        while (!turn.tryAcquire(50, TimeUnit.MILLISECONDS)) {
            if (ended != null) throw new Ended(ended);
            if (stopping && !isShutdown) throw new Ended("the host is stopping");
            if (cancelled.getAsBoolean()) throw new Ended("cancelled before it was sent");
            if (System.nanoTime() >= deadline) throw new Ended("another host call did not finish in time");
        }
        Call c;
        synchronized (lock) {
            String why = ended != null ? ended : stopping && !isShutdown ? "the host is stopping"
                    : cancelled.getAsBoolean() ? "cancelled before it was sent" : null;
            if (why != null) {
                turn.release();
                throw new Ended(why);
            }
            c = current = new Call(nextId++, method, System.nanoTime() + hardTimeout.toNanos());
            lastCall = c.id;
        }
        // The turn and the in-flight slot are held until the host's terminal answer (or the session's end), even if this
        // thread stops waiting: an interrupted caller must not let another request onto the wire (M31A-CDX-6). The
        // deadline is the watchdog's, so it still holds after the caller leaves (M31A-CDX-16).
        c.result.whenComplete((r, t) -> {
            synchronized (lock) {
                if (current == c) current = null;
            }
            turn.release();
        });
        ObjectNode req = F.objectNode().put("jsonrpc", "2.0").put("id", c.id).put("method", method);
        req.set("params", params == null ? F.objectNode() : params);
        // Marked sent once the frame is written; a read the host asks the moment it has the frame waits in the reader for
        // this write to finish, so it is neither refused nor credited before the request exists (M31A-CDX-36, -44,
        // M31A-DES-12, -20).
        try {
            send(req);
        } finally {
            synchronized (lock) {
                c.sent = true;
                c.written.countDown();
            }
        }
        while (true) {
            try {
                return c.result.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                if (cancelled.getAsBoolean()) cancel(c);
            } catch (InterruptedException e) {
                cancel(c);
                throw e;
            } catch (ExecutionException e) {
                Throwable t = e.getCause();
                if (t instanceof HostError he) throw he;
                if (t instanceof Ended en) throw en;
                throw new Ended(String.valueOf(t.getMessage()));
            } catch (CancellationException e) {
                throw new Ended(ended == null ? "the call was abandoned" : ended);
            }
        }
    }

    /** Sends {@code $/cancelRequest} once and starts the grace period after which the watchdog stops the host. */
    private void cancel(Call c) {
        synchronized (lock) {
            if (c.cancelSent || c.answered) return;
            c.cancelSent = true;
            c.stopAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CANCEL_GRACE_MS);
        }
        // Written by a thread of its own: a blocked writer can never stall the watchdog, which still stops the host at
        // stopAt (M31A-CDX-29).
        try {
            cancelSender.execute(() -> sendCancel(c));
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // the session is over
        }
    }

    private final ExecutorService cancelSender = Executors.newSingleThreadExecutor(daemon("JOUST host cancel"));

    /**
     * The protocol's handshake (ADR-005 D11 step 7, I24): {@code initialize}, then the selected protocol must be 1, the
     * echoed network the one sent, and the fingerprint the digest IntelliAda computed from its snapshot copies. Anything
     * else stops the host; until it succeeds no other call is accepted (M31A-CDX-1).
     */
    public JsonNode initialize(ObjectNode params, String expectedDigest, Duration timeout)
            throws HostError, Ended, InterruptedException {
        // One handshake per session, claimed atomically; the negotiated read limit is committed only on success
        // (M31A-CDX-39). Until then a provisional limit from the client's own parameters applies.
        if (!initClaimed.compareAndSet(false, true)) throw new IllegalStateException("initialize is sent once per session");
        JsonNode limit = params.path("limits").path("readsPerCall");
        int negotiated = limit.isIntegralNumber() && limit.canConvertToInt() && limit.intValue() > 0 ? limit.intValue() : readsPerCall;
        JsonNode r;
        initializing = true;
        try {
            r = dispatch("initialize", params, timeout, () -> false);
        } catch (HostError e) {
            initClaimed.set(false);   // the host refused this handshake: the client may try again (as the kit's Bank session does)
            throw e;
        } finally {
            initializing = false;
        }
        String why = null;
        if (!r.path("protocol").isIntegralNumber() || !r.path("protocol").canConvertToInt() || r.path("protocol").intValue() != 1) {
            why = "the host selected another protocol";
        } else if (!expectedDigest.equals(r.path("fingerprint").textValue())) {
            why = "the host's build fingerprint differs from the snapshot IntelliAda verified";
        } else if (!r.path("network").equals(NUMERIC, params.path("network"))) {
            why = "the host echoed another network";
        } else if (!r.path("contracts").isArray()) {
            why = "the host listed no contracts";
        }
        if (why != null) {
            end(why, true);
            throw new Ended(why);
        }
        readsPerCall = negotiated;
        initialized = true;
        return r;
    }

    /** For unit tests with a scripted peer that skips the handshake. */
    void markInitializedForTests() {
        initialized = true;
    }

    private static final java.util.regex.Pattern READ_ID = java.util.regex.Pattern.compile("([1-9][0-9]{0,17})\\.([1-9][0-9]{0,17})");

    /** JSON equality with numbers compared by value (an int sent, a BigInteger parsed). */
    private static final java.util.Comparator<JsonNode> NUMERIC = (a, b) -> {
        if (a.isNumber() && b.isNumber()) return a.decimalValue().compareTo(b.decimalValue());
        return a.equals(b) ? 0 : 1;
    };

    private void sendCancel(Call c) {
        try {
            send(F.objectNode().put("jsonrpc", "2.0").put("method", "$/cancelRequest").set("params", F.objectNode().put("id", c.id)));
        } catch (Ended ignored) {
            // over already
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
        stopping = true;
        Call running;
        synchronized (lock) {
            running = current;
        }
        if (running != null) cancel(running);   // Stop during a call (M31A-DES-5)
        try {
            call("shutdown", F.objectNode(), timeout, () -> false);
        } catch (HostError | Ended | IllegalStateException ignored) {
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
            Call owner;
            synchronized (lock) {
                owner = current;
            }
            // A read can only follow a request the host has received: wait (bounded by the stall limit) for a write still
            // in progress before classifying it.
            if (owner != null && !owner.answered) {
                try {
                    if (!owner.written.await(stallNanos, TimeUnit.NANOSECONDS)) throw new Violation("a chain/read before its call was sent");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new Violation("interrupted");
                }
            }
            synchronized (lock) {
                owner = current;
                // A read after the call's terminal answer is outside any call (M31A-CDX-7).
                if (owner == null || owner.answered || !owner.sent) throw new Violation("a chain/read outside a call");
                var gm = READ_ID.matcher(id.textValue());
                if (!gm.matches()) throw new Violation("a malformed chain/read id");
                long g;
                try {
                    g = Long.parseLong(gm.group(1));
                } catch (NumberFormatException e) {
                    throw new Violation("a malformed chain/read id");
                }
                if (owner.generation == 0) {
                    if (g <= lastGeneration) throw new Violation("a chain/read of an earlier call");
                    owner.generation = g;
                    lastGeneration = g;
                } else if (g != owner.generation) {
                    throw new Violation("a chain/read of another call");
                }
                if (++owner.reads > readsPerCall) throw new Violation("more reverse reads in one call than its limit");
            }
            if (!reverseIds.add(id.textValue())) throw new Violation("a reused chain/read id");
            answer(id.textValue(), m.get("params"), owner);
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
            // JSON-RPC 2.0: an error has an integer code and a string message (M31A-CDX-10).
            if (!e.isObject() || !e.path("code").isIntegralNumber() || !e.path("code").canConvertToInt()
                    || !e.path("message").isTextual()) {
                throw new Violation("a malformed error response");
            }
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

    private void answer(String id, JsonNode params, Call owner) {
        readsInFlight.incrementAndGet();
        readPool.execute(() -> {
          try {
            ObjectNode result;
            try {
                result = reads.read(id, params, owner.id);
            } catch (Exception | LinkageError e) {
                // Cancel only the call that asked, and only while it is still running (M31A-CDX-7).
                boolean live;
                synchronized (lock) {
                    live = current == owner && !owner.answered;
                }
                if (live) cancel(owner);
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

    private void checkStalls() {
        long now = System.nanoTime();
        Call c;
        synchronized (lock) {
            c = current;
        }
        // Stalls first; the watchdog never writes (cancels go through their own thread), so a host that stops reading
        // its input cannot block these checks (M31A-SEC-12, M31A-CDX-29).
        long r = reader.startedAt(), w = writer.writingSince();
        if (r != 0 && now - r >= stallNanos) {
            end("the host's frame stalled", true);
            return;
        }
        if (w != 0 && now - w >= stallNanos) {
            end("the host stopped reading its input", true);
            return;
        }
        if (c != null && !c.answered) {
            if (!c.cancelSent && now >= c.cancelAt) cancel(c);
            else if (c.cancelSent && now >= c.stopAt) end("the host did not answer " + c.method + " in time", true);
        }
    }

    /** Tells {@code listener} why the session ended: when it ends, or at once if it already has. */
    public void onEnded(Consumer<String> listener) {
        String already;
        synchronized (lock) {
            already = endMessage;
            if (already == null) endListeners.add(listener);
        }
        if (already != null) notify(listener, already);
    }

    private static void notify(Consumer<String> l, String why) {
        try {
            l.accept(why);
        } catch (RuntimeException ignored) {
            // a listener's failure does not keep the session alive
        }
    }

    private void end(String why, boolean violation) {
        Call c;
        java.util.List<Consumer<String>> listeners;
        String message = violation ? "protocol violation: " + why : why;
        synchronized (lock) {
            if (ended != null) return;
            violated = violation;
            ended = why;
            endMessage = message;
            c = current;
            listeners = java.util.List.copyOf(endListeners);
            endListeners.clear();
        }
        try {
            stop.run();
        } finally {
            writer.close();
            if (c != null) c.result.completeExceptionally(new Ended(why));
            readPool.shutdownNow();
            watchdog.shutdownNow();
            cancelSender.shutdownNow();
            notify(onEnd, message);
            for (Consumer<String> l : listeners) notify(l, message);
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
