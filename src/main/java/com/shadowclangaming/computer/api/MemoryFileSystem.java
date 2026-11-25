package com.shadowclangaming.computer.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An in-memory disk.
 *
 * Used by the harnesses, and as the fallback backing store when a machine has no persistent medium
 * installed: a computer with no disk still gets a working {@code /}, it just forgets it when it is broken.
 *
 * The capacity limit is deliberate. MineOS checks free space before installing, so a disk that reports
 * unlimited space would let a player fill the save with a loop that appends to a file.
 */
public final class MemoryFileSystem implements FileSystem {

    private static final class Node {
        boolean directory;
        String content = "";
        long modified;
        final Map<String, Node> children = new LinkedHashMap<>();
    }

    private final Node root = new Node();
    private final long capacity;
    private String label;
    private long clock;

    public MemoryFileSystem() {
        this(256L * 1024L, "memory");
    }

    public MemoryFileSystem(long capacityBytes, String label) {
        this.capacity = capacityBytes;
        this.label = label;
        root.directory = true;
    }

    @Override
    public boolean exists(String path) {
        return find(path) != null;
    }

    @Override
    public boolean isDirectory(String path) {
        Node node = find(path);
        return node != null && node.directory;
    }

    @Override
    public String read(String path) {
        Node node = find(path);
        return node == null || node.directory ? null : node.content;
    }

    @Override
    public boolean write(String path, String data) {
        String normalised = FileSystem.Paths.normalise(path);
        if (FileSystem.Paths.isRoot(normalised) || data == null) {
            return false;
        }
        if (used() + data.length() > capacity) {
            return false;
        }
        if (!makeDirectory(FileSystem.Paths.parent(normalised))) {
            return false;
        }
        Node node = findOrCreate(normalised);
        if (node.directory) {
            return false;
        }
        node.content = data;
        node.modified = ++clock;
        return true;
    }

    @Override
    public boolean delete(String path) {
        String normalised = FileSystem.Paths.normalise(path);
        if (FileSystem.Paths.isRoot(normalised)) {
            return false;
        }
        String parent = FileSystem.Paths.parent(normalised);
        Node parentNode = find(parent);
        if (parentNode == null) {
            return false;
        }
        return parentNode.children.remove(FileSystem.Paths.name(normalised)) != null;
    }

    @Override
    public boolean makeDirectory(String path) {
        String normalised = FileSystem.Paths.normalise(path);
        if (exists(normalised)) {
            return isDirectory(normalised);
        }
        Node node = findOrCreate(normalised);
        node.directory = true;
        node.modified = ++clock;
        return true;
    }

    @Override
    public boolean rename(String from, String to) {
        String source = FileSystem.Paths.normalise(from);
        String target = FileSystem.Paths.normalise(to);
        Node node = find(source);
        if (node == null || FileSystem.Paths.isRoot(source) || exists(target)) {
            return false;
        }
        Node parent = find(FileSystem.Paths.parent(source));
        if (parent == null || !makeDirectory(FileSystem.Paths.parent(target))) {
            return false;
        }
        parent.children.remove(FileSystem.Paths.name(source));
        Node targetParent = find(FileSystem.Paths.parent(target));
        targetParent.children.put(FileSystem.Paths.name(target), node);
        node.modified = ++clock;
        return true;
    }

    @Override
    public long size(String path) {
        Node node = find(path);
        if (node == null) {
            return 0L;
        }
        if (!node.directory) {
            return node.content.length();
        }
        long total = 0L;
        for (Node child : node.children.values()) {
            total += sizeOf(child);
        }
        return total;
    }

    private long sizeOf(Node node) {
        if (!node.directory) {
            return node.content.length();
        }
        long total = 0L;
        for (Node child : node.children.values()) {
            total += sizeOf(child);
        }
        return total;
    }

    @Override
    public long lastModified(String path) {
        Node node = find(path);
        return node == null ? 0L : node.modified;
    }

    @Override
    public List<String> list(String path) {
        Node node = find(path);
        List<String> names = new ArrayList<>();
        if (node == null || !node.directory) {
            return names;
        }
        names.addAll(node.children.keySet());
        java.util.Collections.sort(names);
        return names;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public void setLabel(String label) {
        this.label = label == null ? "" : label.length() > 32 ? label.substring(0, 32) : label;
    }

    /** Bytes used, for {@code filesystem.space}. */
    public long used() {
        return size("/");
    }

    public long capacity() {
        return capacity;
    }

    private Node find(String path) {
        String normalised = FileSystem.Paths.normalise(path);
        if ("/".equals(normalised)) {
            return root;
        }
        Node current = root;
        for (String part : normalised.substring(1).split("/")) {
            if (!current.directory) {
                return null;
            }
            current = current.children.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Node findOrCreate(String normalised) {
        if ("/".equals(normalised)) {
            return root;
        }
        Node current = root;
        for (String part : normalised.substring(1).split("/")) {
            Node next = current.children.get(part);
            if (next == null) {
                next = new Node();
                next.modified = ++clock;
                current.children.put(part, next);
            }
            current = next;
        }
        return current;
    }
}
