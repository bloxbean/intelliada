package com.bloxbean.intelliada.idea.joust.run;

import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * One launch of the run panel (M31B-CDX-17): binding, then starting the host. Stop (or the progress indicator's cancel)
 * marks it cancelled at any point; a launch cancelled before the host starts never starts it, and one cancelled while
 * the host was starting stops the host it got. The token survives the handoff from binding to the host service.
 */
final class JoustRunLaunch {

    interface Step<I, O> {
        O apply(I in) throws Exception;
    }

    /** The launch was cancelled; nothing is left running. */
    static final class Cancelled extends Exception {
        Cancelled() {
            super("the launch was stopped", null, false, false);
        }
    }

    private volatile boolean cancelled;
    private volatile String ended;

    /**
     * The host of this launch ended (its end callback, M31B-CDX-31). The callback may reach the EDT before or after
     * the launch completes; a launch whose host already ended is never published as running.
     */
    void markEnded(String why) {
        ended = why;
    }

    @Nullable String ended() {
        return ended;
    }

    /** Whether a completed launch may be shown as running: neither stopped nor already ended. */
    boolean publishable(@Nullable String sessionEnded) {
        return !cancelled && ended == null && sessionEnded == null;
    }

    void cancel() {
        cancelled = true;
    }

    boolean cancelled() {
        return cancelled;
    }

    /**
     * Binds, then starts; checks for cancellation between the steps and after the start, stopping what was started when
     * the launch was cancelled meanwhile.
     */
    <B, R> R run(Step<Void, B> bind, Step<B, R> start, Consumer<R> stop, BooleanSupplier indicatorCancelled) throws Exception {
        check(indicatorCancelled);
        B b = bind.apply(null);
        check(indicatorCancelled);
        R r = start.apply(b);
        if (cancelled || indicatorCancelled.getAsBoolean()) {
            stop.accept(r);
            throw new Cancelled();
        }
        return r;
    }

    private void check(BooleanSupplier indicatorCancelled) throws Cancelled {
        if (cancelled || indicatorCancelled.getAsBoolean()) throw new Cancelled();
    }

    static @Nullable String message(Exception e) {
        return e instanceof Cancelled ? "Stopped before the host started." : null;
    }
}
