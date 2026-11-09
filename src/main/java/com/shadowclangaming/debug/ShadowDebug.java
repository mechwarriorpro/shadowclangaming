package com.shadowclangaming.debug;

import com.shadowclangaming.ShadowClanGaming;
import net.neoforged.fml.loading.FMLPaths;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * High-detail, always-on diagnostic log for reproducing gameplay and rendering bugs.
 *
 * Writes every event (including every client render frame) to {@code <game directory>/logs/shadow_debug.log}.
 * Appends are queued to one daemon writer so deliberately high-rate render tracing cannot block the render
 * thread. The file rotates only at 256 MiB to preserve the requested full-rate samples.
 */
public final class ShadowDebug {
    private static final long MAX_BYTES = 256L * 1024L * 1024L;
    private static final LinkedBlockingQueue<String> LINES = new LinkedBlockingQueue<>();
    private static volatile boolean started;
    private static volatile boolean writeFailureReported;
    private static Path file;

    private ShadowDebug() {
    }

    /** Initialize the log and record the environment once per JVM launch. */
    public static synchronized void start() {
        if (started) return;
        started = true;
        try {
            Path logs = FMLPaths.GAMEDIR.get().resolve("logs");
            Files.createDirectories(logs);
            file = logs.resolve("shadow_debug.log");
            if (Files.exists(file) && Files.size(file) > MAX_BYTES) rotate();
            Thread writer = new Thread(ShadowDebug::writeLoop, "SCG-shadow-debug-writer");
            writer.setDaemon(true);
            writer.start();
        } catch (Exception exception) {
            file = null;
            reportWriteFailure(exception);
        }
        log("BOOT", "ShadowClanGaming 1.0.0 full-rate debug session started; Java={} OS={} {}",
                System.getProperty("java.version"), System.getProperty("os.name"),
                System.getProperty("os.version"));
    }

    /** Append one event. Use {} placeholders, as with SLF4J. */
    public static void log(String category, String format, Object... values) {
        if (!started) start();
        if (file == null) return;
        String message = format;
        try {
            for (Object value : values) {
                int slot = message.indexOf("{}");
                if (slot < 0) break;
                String printable = safe(String.valueOf(value));
                message = message.substring(0, slot) + printable + message.substring(slot + 2);
            }
        } catch (RuntimeException ignored) {
            // Diagnostics must never break a gameplay/render path due to a broken toString().
        }
        LINES.offer(Instant.now() + " [" + Thread.currentThread().getName() + "] ["
                + safe(category) + "] " + safe(message) + System.lineSeparator());
    }

    /** Include the full exception stack rather than just its type/message. */
    public static void logThrowable(String category, String message, Throwable throwable) {
        StringWriter buffer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(buffer));
        log(category, "{}", message);
        for (String line : buffer.toString().split("\\R")) {
            log(category + "_TRACE", "{}", line);
        }
    }

    private static void writeLoop() {
        while (true) {
            try {
                String first = LINES.take();
                StringBuilder batch = new StringBuilder(first);
                // Drain the batch explicitly: every sampled frame is retained in order.
                String line;
                while ((line = LINES.poll()) != null) batch.append(line);
                synchronized (ShadowDebug.class) {
                    if (file != null) {
                        if (Files.exists(file) && Files.size(file) + batch.length() > MAX_BYTES) rotate();
                        Files.writeString(file, batch, StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                reportWriteFailure(exception);
            }
        }
    }

    private static void rotate() throws java.io.IOException {
        if (file == null || !Files.exists(file)) return;
        Path old = file.resolveSibling("shadow_debug.log.1");
        Files.deleteIfExists(old);
        Files.move(file, old);
    }

    private static String safe(String value) {
        return value == null ? "null" : value.replace('\r', ' ').replace('\n', ' ');
    }

    private static void reportWriteFailure(Exception exception) {
        if (!writeFailureReported) {
            writeFailureReported = true;
            ShadowClanGaming.LOGGER.warn("Could not write logs/shadow_debug.log", exception);
        }
    }
}
