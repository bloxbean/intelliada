package com.bloxbean.intelliada.idea.joust.host;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Small metadata files read without following links, to a byte cap and under a deadline (M31A-CDX-23). */
final class HostIo {

    private HostIo() {
    }

    /**
     * The bytes of {@code file} (at most {@code max + 1}, so a caller can tell an overflow), opened without following a
     * link; a FIFO or device swapped in after a check cannot block past {@code timeoutMs}.
     */
    static byte[] readCapped(Path file, int max, long timeoutMs) throws IOException, InterruptedException {
        FutureTask<byte[]> task = new FutureTask<>(() -> {
            try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                return in.readNBytes(max + 1);
            }
        });
        Thread t = new Thread(task, "JOUST host metadata read");
        t.setDaemon(true);
        t.start();
        try {
            return task.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IOException("reading " + file.getFileName() + " did not finish in time");
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof IOException io) throw io;
            throw new IOException(c);
        } finally {
            if (!task.isDone()) {
                task.cancel(true);
            }
        }
    }
}
