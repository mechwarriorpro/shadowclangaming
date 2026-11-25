package com.shadowclangaming.computer.api;

import java.util.List;

/**
 * The filesystem as a component ({@code component.list("filesystem")}).
 *
 * Programs that use the {@code fs} library never touch this, but installers do check for a filesystem to
 * decide whether there is anywhere to install to, and the panel lists disks through it. It is a thin
 * adapter: all the rules live in {@link FileSystem}.
 */
public final class DiskComponent implements Component {

    private final String address;
    private final FileSystem filesystem;
    private final FileHandles handles;

    public DiskComponent(String address, FileSystem filesystem, FileHandles handles) {
        this.address = address;
        this.filesystem = filesystem;
        this.handles = handles;
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public String type() {
        return "filesystem";
    }

    @Override
    public String[] methods() {
        return new String[]{"exists", "isDirectory", "size", "lastModified", "list", "read", "write",
                "open", "close", "remove", "makeDirectory", "rename", "getLabel", "setLabel", "space"};
    }

    @Override
    public Object[] invoke(String method, Object[] args) {
        String path = args.length > 0 && args[0] != null ? ScreenComponent.stringOf(args[0]) : "/";
        switch (method) {
            case "exists" -> {
                return new Object[]{filesystem.exists(path)};
            }
            case "isDirectory" -> {
                return new Object[]{filesystem.isDirectory(path)};
            }
            case "size" -> {
                return new Object[]{(double) filesystem.size(path)};
            }
            case "lastModified" -> {
                return new Object[]{(double) filesystem.lastModified(path)};
            }
            case "list" -> {
                List<String> names = filesystem.list(path);
                Object[] out = new Object[names.size()];
                for (int i = 0; i < names.size(); i++) {
                    out[i] = names.get(i);
                }
                return out;
            }
            case "read" -> {
                return new Object[]{filesystem.read(path)};
            }
            case "write" -> {
                String data = args.length > 1 && args[1] != null ? ScreenComponent.stringOf(args[1]) : "";
                return new Object[]{filesystem.write(path, data)};
            }
            case "open" -> {
                String mode = args.length > 1 && args[1] != null ? ScreenComponent.stringOf(args[1]) : "r";
                return handles.open(path, mode);
            }
            case "close" -> {
                return handles.close(ScreenComponent.stringOf(args.length > 0 ? args[0] : null));
            }
            case "remove" -> {
                return new Object[]{filesystem.delete(path)};
            }
            case "makeDirectory" -> {
                return new Object[]{filesystem.makeDirectory(path)};
            }
            case "rename" -> {
                String to = args.length > 1 && args[1] != null ? ScreenComponent.stringOf(args[1]) : "/";
                return new Object[]{filesystem.rename(path, to)};
            }
            case "getLabel" -> {
                return new Object[]{filesystem.label()};
            }
            case "setLabel" -> {
                String label = args.length > 0 && args[0] != null ? ScreenComponent.stringOf(args[0]) : "";
                filesystem.setLabel(label);
                return new Object[]{filesystem.label()};
            }
            case "space" -> {
                long capacity = filesystem instanceof MemoryFileSystem memory
                        ? memory.capacity() : 1_000_000L;
                long used = filesystem instanceof MemoryFileSystem memory
                        ? memory.used() : filesystem.size("/");
                return new Object[]{(double) capacity, (double) used};
            }
            default -> throw new IllegalArgumentException("no such method: " + method);
        }
    }
}
