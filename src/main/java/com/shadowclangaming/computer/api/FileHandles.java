package com.shadowclangaming.computer.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Open file handles, as {@code fs.open} / {@code fs.read} / {@code fs.write} / {@code fs.close} see them.
 *
 * A handle is a string ("h1", "h2", ...) rather than a number, because that is what scripts print, compare
 * and store. Handles are capped so a loop that opens files without closing them cannot exhaust the server.
 */
public final class FileHandles {

    public static final int MAX_OPEN = 16;

    private static final class Handle {
        final String path;
        final boolean writing;
        final StringBuilder buffer = new StringBuilder();
        int position;

        Handle(String path, boolean writing) {
            this.path = path;
            this.writing = writing;
        }
    }

    private final FileSystem filesystem;
    private final Map<String, Handle> open = new LinkedHashMap<>();
    private int next = 1;

    public FileHandles(FileSystem filesystem) {
        this.filesystem = filesystem;
    }

    /** Open a path. Returns the handle, or {@code (null, reason)} just like the scripts expect. */
    public Object[] open(String path, String mode) {
        String normalised = FileSystem.Paths.normalise(path);
        String actual = mode == null || mode.isEmpty() ? "r" : mode;
        boolean writing = actual.startsWith("w") || actual.startsWith("a");
        if (writing && filesystem.isDirectory(normalised)) {
            return new Object[]{null, "is a directory"};
        }
        if (!writing && !filesystem.exists(normalised)) {
            return new Object[]{null, "no such file"};
        }
        if (open.size() >= MAX_OPEN) {
            return new Object[]{null, "too many open files"};
        }
        Handle handle = new Handle(normalised, writing);
        if (writing && actual.startsWith("a")) {
            String existing = filesystem.read(normalised);
            if (existing != null) {
                handle.buffer.append(existing);
            }
        }
        if (!writing) {
            String existing = filesystem.read(normalised);
            handle.buffer.append(existing == null ? "" : existing);
        }
        String id = "h" + next++;
        open.put(id, handle);
        return new Object[]{id};
    }

    /** Read up to {@code count} characters, or everything when count is 0. Null at end of file. */
    public Object[] read(String id, int count) {
        Handle handle = open.get(id);
        if (handle == null) {
            return new Object[]{null, "bad file descriptor"};
        }
        if (handle.writing) {
            return new Object[]{null, "file not opened for reading"};
        }
        int remaining = handle.buffer.length() - handle.position;
        if (remaining <= 0) {
            return new Object[]{null};
        }
        int take = count <= 0 ? remaining : Math.min(count, remaining);
        String chunk = handle.buffer.substring(handle.position, handle.position + take);
        handle.position += take;
        return new Object[]{chunk};
    }

    public Object[] write(String id, String data) {
        Handle handle = open.get(id);
        if (handle == null) {
            return new Object[]{null, "bad file descriptor"};
        }
        if (!handle.writing) {
            return new Object[]{null, "file not opened for writing"};
        }
        handle.buffer.append(data == null ? "" : data);
        // Flush on every write: a computer that is broken mid-script must not lose the last line.
        if (!filesystem.write(handle.path, handle.buffer.toString())) {
            return new Object[]{null, "disk full"};
        }
        return new Object[]{Boolean.TRUE};
    }

    public Object[] close(String id) {
        Handle handle = open.remove(id);
        if (handle == null) {
            return new Object[]{null, "bad file descriptor"};
        }
        if (handle.writing && !filesystem.write(handle.path, handle.buffer.toString())) {
            return new Object[]{null, "disk full"};
        }
        return new Object[]{Boolean.TRUE};
    }

    /** {@code whence} is "cur", "set" or "end". */
    public Object[] seek(String id, String whence, int offset) {
        Handle handle = open.get(id);
        if (handle == null) {
            return new Object[]{null, "bad file descriptor"};
        }
        int base = switch (whence == null ? "cur" : whence) {
            case "set" -> 0;
            case "end" -> handle.buffer.length();
            default -> handle.position;
        };
        handle.position = Math.max(0, Math.min(handle.buffer.length(), base + offset));
        return new Object[]{(double) handle.position};
    }

    public int openCount() {
        return open.size();
    }

    public void closeAll() {
        open.clear();
    }
}
