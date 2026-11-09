package com.shadowclangaming.computer.api;

import com.shadowclangaming.lua.LuaError;
import com.shadowclangaming.lua.LuaFunction;
import com.shadowclangaming.lua.LuaTable;
import com.shadowclangaming.lua.Vm;

/**
 * The {@code term} library: a cursor over the bound screen, with wrapping and scrolling.
 *
 * This is the difference between a plotter and a terminal. {@code gpu.set} puts a character exactly where
 * you say; {@code term.write} is a printer head that advances, wraps at the right margin and scrolls when it
 * runs off the bottom - which is what every shell, log and MineOS window actually uses.
 */
public final class TermLibrary {

    private final GpuLibrary gpu;
    private int cursorX = 1;
    private int cursorY = 1;
    /** Bytes/Lua-facing state kept out of the buffer: a program may set these without touching a screen. */
    private int foreground = Colors.WHITE;
    private int background = Colors.BLACK;

    public TermLibrary(GpuLibrary gpu) {
        this.gpu = gpu;
    }

    public void install(Vm vm, LuaTable globals) {
        LuaTable term = new LuaTable();
        globals.set("term", term);

        native_(term, "write", (machine, args) -> {
            for (Object argument : args) {
                write(ScreenComponent.stringOf(argument));
            }
            return new Object[0];
        });
        native_(term, "clear", (machine, args) -> {
            TextBuffer buffer = buffer();
            if (buffer != null) {
                buffer.setForeground(foreground);
                buffer.setBackground(background);
                buffer.clear();
            }
            cursorX = 1;
            cursorY = 1;
            return new Object[0];
        });
        native_(term, "clearLine", (machine, args) -> {
            TextBuffer buffer = buffer();
            if (buffer != null) {
                buffer.fill(cursorX, cursorY, buffer.columns() - cursorX + 1, 1, ' ');
            }
            return new Object[0];
        });
        native_(term, "setCursor", (machine, args) -> {
            TextBuffer buffer = buffer();
            int x = Math.max(1, ScreenComponent.integer(args, 0));
            int y = Math.max(1, ScreenComponent.integer(args, 1));
            if (buffer != null) {
                x = Math.min(x, buffer.columns());
                y = Math.min(y, buffer.rows());
            }
            cursorX = x;
            cursorY = y;
            return new Object[0];
        });
        native_(term, "getCursor", (machine, args) -> {
            TextBuffer buffer = buffer();
            double x = buffer == null ? cursorX : Math.min(cursorX, buffer.columns());
            return new Object[]{x, (double) cursorY};
        });
        native_(term, "getSize", (machine, args) -> {
            TextBuffer buffer = buffer();
            if (buffer == null) {
                return new Object[]{null, "no screen"};
            }
            return new Object[]{(double) buffer.columns(), (double) buffer.rows()};
        });
        native_(term, "scroll", (machine, args) -> {
            TextBuffer buffer = buffer();
            int lines = ScreenComponent.integer(args, 0);
            if (buffer != null) {
                buffer.scroll(lines);
            }
            return new Object[0];
        });
        native_(term, "setForeground", (machine, args) -> {
            foreground = Colors.parse(args.length > 0 ? args[0] : null, foreground);
            return new Object[0];
        });
        native_(term, "setBackground", (machine, args) -> {
            background = Colors.parse(args.length > 0 ? args[0] : null, background);
            return new Object[0];
        });
        native_(term, "getForeground", (machine, args) -> new Object[]{Colors.format(foreground)});
        native_(term, "getBackground", (machine, args) -> new Object[]{Colors.format(background)});
        native_(term, "bind", (machine, args) -> {
            Object screen = gpu.screen();
            Object[] result = gpu.screen() == null ? new Object[]{null, "no screen"} : new Object[]{Boolean.TRUE};
            cursorX = 1;
            cursorY = 1;
            return result;
        });
    }

    private TextBuffer buffer() {
        return gpu.screen() == null ? null : gpu.screen().buffer();
    }

    /**
     * Advance the printer head, wrapping at the right margin and scrolling at the bottom.
     *
     * Wrapping keeps the cursor on the same column budget as the screen: writing exactly `columns`
     * characters leaves the cursor at the start of the next row, which is what scripts assume.
     */
    public void write(String text) {
        TextBuffer buffer = buffer();
        if (buffer == null) {
            throw new LuaError("no screen is bound to this gpu");
        }
        buffer.setForeground(foreground);
        buffer.setBackground(background);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                newline(buffer);
                continue;
            }
            // Deferred wrap: the cursor is allowed to sit one past the right margin, and the row changes
            // when the next character actually arrives. Eager wrapping scrolls a full line early on the
            // bottom row, which every full-screen program would show as a jump.
            if (cursorX > buffer.columns()) {
                newline(buffer);
            }
            buffer.set(cursorX, cursorY, String.valueOf(ch), false);
            cursorX++;
        }
    }

    private void newline(TextBuffer buffer) {
        cursorX = 1;
        cursorY++;
        if (cursorY > buffer.rows()) {
            buffer.scroll(1);
            cursorY = buffer.rows();
        }
    }

    public int cursorX() {
        return cursorX;
    }

    public int cursorY() {
        return cursorY;
    }

    private static void native_(LuaTable target, String name, LuaFunction.Native body) {
        target.set(name, new LuaFunction.NativeFunction(name, body));
    }
}
