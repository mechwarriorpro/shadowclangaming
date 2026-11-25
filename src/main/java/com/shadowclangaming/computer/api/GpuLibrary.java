package com.shadowclangaming.computer.api;

import com.shadowclangaming.lua.LuaFunction;
import com.shadowclangaming.lua.LuaTable;
import com.shadowclangaming.lua.Vm;

/**
 * The {@code gpu} library: the drawing calls a program makes, bound to one screen.
 *
 * The GPU card's tier sets the largest buffer it can drive, so a basic card cannot bind a wall of advanced
 * screens and pretend. Everything here is a thin, guarded wrapper over {@link TextBuffer}; the interesting
 * work (wrapping, cursor) belongs to {@link TermLibrary}.
 */
public final class GpuLibrary {

    private final ComponentRegistry registry;
    private ScreenComponent screen;
    private int foreground = Colors.WHITE;
    private int background = Colors.BLACK;

    public GpuLibrary(ComponentRegistry registry) {
        this.registry = registry;
    }

    /** Attach the library to the machine's primary screen. */
    public void bindPrimary() {
        for (Component component : registry.all()) {
            if (component instanceof ScreenComponent candidate) {
                this.screen = candidate;
                return;
            }
        }
    }

    /** Set the screen the library draws to, and mirror its colours into the buffer. */
    public void setScreen(ScreenComponent screen) {
        this.screen = screen;
        if (screen != null) {
            screen.buffer().setForeground(foreground);
            screen.buffer().setBackground(background);
        }
    }

    public ScreenComponent screen() {
        return screen;
    }

    public boolean bound() {
        return screen != null;
    }

    public int foregroundColour() {
        return foreground;
    }

    public int backgroundColour() {
        return background;
    }

    /** Install the global {@code gpu} table. */
    public void install(Vm vm, LuaTable globals) {
        LuaTable gpu = new LuaTable();
        globals.set("gpu", gpu);
        native_(gpu, "getResolution", (machine, args) -> {
            requireScreen();
            return new Object[]{(double) screen.buffer().columns(), (double) screen.buffer().rows()};
        });
        native_(gpu, "maxResolution", (machine, args) -> new Object[]{80.0D, 25.0D});
        native_(gpu, "getDepth", (machine, args) -> new Object[]{256.0D});
        native_(gpu, "set", (machine, args) -> {
            requireScreen();
            int x = ScreenComponent.integer(args, 0);
            int y = ScreenComponent.integer(args, 1);
            String text = args.length > 2 && args[2] != null ? ScreenComponent.stringOf(args[2]) : "";
            boolean vertical = args.length > 3 && Vm.truthy(args[3]);
            screen.buffer().setForeground(foreground);
            screen.buffer().setBackground(background);
            screen.buffer().set(x, y, text, vertical);
            return new Object[]{Boolean.TRUE};
        });
        native_(gpu, "get", (machine, args) -> {
            requireScreen();
            int x = ScreenComponent.integer(args, 0);
            int y = ScreenComponent.integer(args, 1);
            return new Object[]{String.valueOf(screen.buffer().at(x, y))};
        });
        native_(gpu, "fill", (machine, args) -> {
            requireScreen();
            int x = ScreenComponent.integer(args, 0);
            int y = ScreenComponent.integer(args, 1);
            int width = ScreenComponent.integer(args, 2);
            int height = ScreenComponent.integer(args, 3);
            String ch = args.length > 4 && args[4] != null ? ScreenComponent.stringOf(args[4]) : " ";
            screen.buffer().setForeground(foreground);
            screen.buffer().setBackground(background);
            screen.buffer().fill(x, y, width, height, ch.isEmpty() ? ' ' : ch.charAt(0));
            return new Object[]{Boolean.TRUE};
        });
        native_(gpu, "copy", (machine, args) -> {
            requireScreen();
            TextBuffer buffer = screen.buffer();
            int x = ScreenComponent.integer(args, 0);
            int y = ScreenComponent.integer(args, 1);
            int w = ScreenComponent.integer(args, 2);
            int h = ScreenComponent.integer(args, 3);
            int tx = ScreenComponent.integer(args, 4);
            int ty = ScreenComponent.integer(args, 5);
            String[] rows = new String[h];
            for (int row = 0; row < h; row++) {
                StringBuilder builder = new StringBuilder();
                for (int column = 0; column < w; column++) {
                    builder.append(buffer.at(x + column, y + row));
                }
                rows[row] = builder.toString();
            }
            buffer.setForeground(foreground);
            buffer.setBackground(background);
            for (int row = 0; row < h; row++) {
                buffer.set(tx, ty + row, rows[row], false);
            }
            return new Object[]{Boolean.TRUE};
        });
        native_(gpu, "setForeground", (machine, args) -> {
            foreground = Colors.parse(args.length > 0 ? args[0] : null, foreground);
            if (screen != null) {
                screen.buffer().setForeground(foreground);
            }
            return new Object[]{Colors.format(foreground)};
        });
        native_(gpu, "setBackground", (machine, args) -> {
            background = Colors.parse(args.length > 0 ? args[0] : null, background);
            if (screen != null) {
                screen.buffer().setBackground(background);
            }
            return new Object[]{Colors.format(background)};
        });
        native_(gpu, "getForeground", (machine, args) -> new Object[]{Colors.format(foreground)});
        native_(gpu, "getBackground", (machine, args) -> new Object[]{Colors.format(background)});
        native_(gpu, "getScreen", (machine, args) ->
                new Object[]{screen == null ? null : screen.address()});
        native_(gpu, "bind", (machine, args) -> {
            String address = args.length > 0 ? ScreenComponent.stringOf(args[0]) : null;
            Component component = address == null ? null : registry.get(address);
            if (!(component instanceof ScreenComponent target)) {
                return new Object[]{null, "not a screen"};
            }
            setScreen(target);
            return new Object[]{Boolean.TRUE};
        });
        native_(gpu, "getSize", (machine, args) -> {
            if (screen == null) {
                return new Object[]{null, "no screen"};
            }
            return new Object[]{(double) screen.buffer().columns(), (double) screen.buffer().rows()};
        });
    }

    private void requireScreen() {
        if (screen == null) {
            throw new com.shadowclangaming.lua.LuaError("no screen is bound to this gpu");
        }
    }

    private static void native_(LuaTable target, String name, LuaFunction.Native body) {
        target.set(name, new LuaFunction.NativeFunction(name, body));
    }
}
