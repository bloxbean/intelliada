package com.bloxbean.intelliada.idea.joust;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Every process the test JVM starts, from JFR's {@code jdk.ProcessStart} event (M1-CDX-10, M1-DES-4): unlike snapshots
 * of live descendants, it also sees a process that started and exited between two observations.
 */
public final class JoustProcessStarts implements AutoCloseable {
    private final Recording recording = new Recording();

    public JoustProcessStarts() {
        recording.enable("jdk.ProcessStart");
        recording.start();
    }

    /** The command lines started so far. */
    public List<String> commands() throws Exception {
        Path f = Files.createTempFile("joust-process-starts", ".jfr");
        try {
            recording.dump(f);
            List<String> out = new ArrayList<>();
            for (RecordedEvent e : RecordingFile.readAllEvents(f)) {
                if (e.getEventType().getName().equals("jdk.ProcessStart")) out.add(e.getString("command"));
            }
            return out;
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Override
    public void close() {
        recording.close();
    }
}
