package com.shadowclangaming.computer.api;

/**
 * A screen component: the drawing surface programs actually see.
 *
 * In OpenComputers a screen is a component and a GPU is a separate card that drives it. We keep that split
 * because scripts bind, rebind and address several screens, but the drawing methods live here: the GPU card
 * decides *how big* a buffer a machine may drive (see {@link ScreenComponent#withTier}), and the screen owns
 * the pixels.
 */
public final class ScreenComponent implements Component {

    /** What a GPU card of a given tier allows. Mirrors the GPU tiers in computer/GpuTier.java. */
    public record Tier(String name, int maxColumns, int maxRows, int pipelines, int palette) {
    }

    public static final Tier BASIC = new Tier("basic", 80, 25, 1, 16);
    public static final Tier ADVANCED = new Tier("advanced", 100, 30, 2, 16);
    public static final Tier VECTOR = new Tier("vector", 120, 40, 4, 256);

    private final String address;
    private final TextBuffer buffer;
    private boolean touchEnabled;
    private boolean on = true;

    public ScreenComponent(String address, int columns, int rows) {
        this.address = address;
        this.buffer = new TextBuffer(columns, rows);
    }

    /** Narrow a requested resolution to what the installed GPU can drive. */
    public static ScreenComponent forTier(String address, Tier tier, int columns, int rows) {
        int width = Math.max(1, Math.min(columns, tier.maxColumns()));
        int height = Math.max(1, Math.min(rows, tier.maxRows()));
        return new ScreenComponent(address, width, height);
    }

    public TextBuffer buffer() {
        return buffer;
    }

    public void setTouchEnabled(boolean enabled) {
        this.touchEnabled = enabled;
    }

    public void setOn(boolean on) {
        this.on = on;
        if (!on) {
            buffer.clear();
        }
    }

    public boolean isOn() {
        return on;
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public String type() {
        return "screen";
    }

    @Override
    public String[] methods() {
        return new String[]{"getResolution", "setText", "getText", "fill", "clear", "getForeground",
                "getBackground", "setForeground", "setBackground", "isOn", "turnOn", "turnOff",
                "isTouchEnabled", "setTouchModeInverted", "getAspectRatio"};
    }

    @Override
    public Object[] invoke(String method, Object[] args) {
        switch (method) {
            case "getResolution" -> {
                return new Object[]{(double) buffer.columns(), (double) buffer.rows()};
            }
            case "setText" -> {
                int x = integer(args, 0);
                int y = integer(args, 1);
                String text = args.length > 2 && args[2] != null ? stringOf(args[2]) : "";
                boolean vertical = args.length > 3 && Boolean.TRUE.equals(args[3]);
                return new Object[]{(double) buffer.set(x, y, text, vertical)};
            }
            case "getText" -> {
                int x = integer(args, 0);
                int y = integer(args, 1);
                return new Object[]{String.valueOf(buffer.at(x, y))};
            }
            case "fill" -> {
                int x = integer(args, 0);
                int y = integer(args, 1);
                int width = integer(args, 2);
                int height = integer(args, 3);
                String ch = args.length > 4 ? stringOf(args[4]) : " ";
                char character = ch.isEmpty() ? ' ' : ch.charAt(0);
                return new Object[]{(double) buffer.fill(x, y, width, height, character)};
            }
            case "clear" -> {
                buffer.clear();
                return new Object[0];
            }
            case "getForeground" -> {
                return new Object[]{Colors.format(buffer.foreground())};
            }
            case "getBackground" -> {
                return new Object[]{Colors.format(buffer.background())};
            }
            case "setForeground", "setBackground" -> {
                boolean foreground = method.equals("setForeground");
                int colour = Colors.parse(args.length > 0 ? args[0] : null,
                        foreground ? buffer.foreground() : buffer.background());
                if (foreground) {
                    buffer.setForeground(colour);
                } else {
                    buffer.setBackground(colour);
                }
                return new Object[]{Colors.format(colour)};
            }
            case "isOn" -> {
                return new Object[]{on};
            }
            case "turnOn" -> {
                setOn(true);
                return new Object[0];
            }
            case "turnOff" -> {
                setOn(false);
                return new Object[0];
            }
            case "isTouchEnabled" -> {
                return new Object[]{touchEnabled};
            }
            case "setTouchModeInverted" -> {
                return new Object[]{touchEnabled};
            }
            case "getAspectRatio" -> {
                return new Object[]{buffer.columns() / (double) Math.max(1, buffer.rows())};
            }
            default -> throw new IllegalArgumentException("no such method: " + method);
        }
    }

    /** Kept because the Lua layer hands numbers through as Doubles. */
    static int integer(Object[] args, int index) {
        if (index >= args.length || !(args[index] instanceof Double value)) {
            return 0;
        }
        return (int) Math.round(value);
    }

    static String stringOf(Object value) {
        return com.shadowclangaming.lua.Vm.luaToString(value);
    }
}
