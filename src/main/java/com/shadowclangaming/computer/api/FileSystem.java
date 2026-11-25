package com.shadowclangaming.computer.api;

import java.util.List;

/**
 * The machine's disk, as the scripts see it: Unix-shaped paths, a single root, directories.
 *
 * Paths are normalised in one place ({@link Paths}) so a script cannot escape the root with {@code ..} and
 * two filesystems cannot disagree about what {@code "/a/b/"} means. Everything here is pure Java; the
 * in-world implementation wraps a {@code SavedData}, and tests use {@link MemoryFileSystem}.
 */
public interface FileSystem {

    boolean exists(String path);

    boolean isDirectory(String path);

    /** File contents, or null when the path is missing or a directory. */
    String read(String path);

    boolean write(String path, String data);

    /** Deletes a file, or a directory and everything under it. */
    boolean delete(String path);

    boolean makeDirectory(String path);

    boolean rename(String from, String to);

    /** Bytes for a file, total for a directory, 0 when missing. */
    long size(String path);

    long lastModified(String path);

    /** Child names, direct only, sorted. Empty for a missing path. */
    List<String> list(String path);

    String label();

    void setLabel(String label);

    /** Paths are absolute and normalised before they reach an implementation. */
    final class Paths {

        private Paths() {
        }

        public static String normalise(String path) {
            if (path == null) {
                return "/";
            }
            String cleaned = path.replace('\\', '/').trim();
            if (cleaned.isEmpty()) {
                return "/";
            }
            java.util.Deque<String> parts = new java.util.ArrayDeque<>();
            for (String part : cleaned.split("/")) {
                if (part.isEmpty() || part.equals(".")) {
                    continue;
                }
                if (part.equals("..")) {
                    if (!parts.isEmpty()) {
                        parts.removeLast();
                    }
                    continue;
                }
                parts.addLast(part);
            }
            if (parts.isEmpty()) {
                return "/";
            }
            return "/" + String.join("/", parts);
        }

        public static String parent(String path) {
            String normalised = normalise(path);
            int cut = normalised.lastIndexOf('/');
            return cut <= 0 ? "/" : normalised.substring(0, cut);
        }

        public static String name(String path) {
            String normalised = normalise(path);
            int cut = normalised.lastIndexOf('/');
            return cut < 0 ? normalised : normalised.substring(cut + 1);
        }

        public static String child(String directory, String name) {
            String base = normalise(directory);
            return base.equals("/") ? "/" + name : base + "/" + name;
        }

        public static boolean isRoot(String path) {
            return "/".equals(normalise(path));
        }
    }
}
