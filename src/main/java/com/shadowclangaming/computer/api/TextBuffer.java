package com.shadowclangaming.computer.api;

/**
 * The screen model: a fixed grid of characters, each with a foreground and a background colour.
 *
 * This is the part of a computer that has to be exactly right, because every program draws through it and
 * a one-off-by-one scroll shows up as a corrupted terminal. It is therefore plain Java with no Minecraft
 * types, and it is tested directly by tools/run_api_harness.sh.
 *
 * Coordinates are 1-based, as Lua expects. Writes are clipped, never wrapped here: wrapping is the
 * terminal's job, because a raw {@code gpu.set} must be able to touch any cell without moving the cursor.
 */
public final class TextBuffer {

    private final int columns;
    private final int rows;
    private final char[] chars;
    private final int[] fg;
    private final int[] bg;

    private int foreground = Colors.WHITE;
    private int background = Colors.BLACK;

    public TextBuffer(int columns, int rows) {
        if (columns < 1 || rows < 1) {
            throw new IllegalArgumentException("buffer must be at least 1x1");
        }
        this.columns = Math.min(columns, 320);
        this.rows = Math.min(rows, 120);
        int cells = this.columns * this.rows;
        this.chars = new char[cells];
        this.fg = new int[cells];
        this.bg = new int[cells];
        java.util.Arrays.fill(chars, ' ');
        java.util.Arrays.fill(fg, Colors.WHITE);
        java.util.Arrays.fill(bg, Colors.BLACK);
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public int foreground() {
        return foreground;
    }

    public int background() {
        return background;
    }

    public void setForeground(int rgb) {
        this.foreground = rgb & 0xFFFFFF;
    }

    public void setBackground(int rgb) {
        this.background = rgb & 0xFFFFFF;
    }

    private int index(int x, int y) {
        return (y - 1) * columns + (x - 1);
    }

    private boolean inside(int x, int y) {
        return x >= 1 && x <= columns && y >= 1 && y <= rows;
    }

    /**
     * Write text starting at (x, y).
     *
     * @param vertical when true, each character goes one row down instead of one column right
     * @return how many characters were written
     */
    public int set(int x, int y, String text, boolean vertical) {
        if (text == null) {
            return 0;
        }
        int written = 0;
        for (int i = 0; i < text.length(); i++) {
            int cx = vertical ? x : x + i;
            int cy = vertical ? y + i : y;
            if (!inside(cx, cy)) {
                continue;
            }
            char ch = text.charAt(i);
            int cell = index(cx, cy);
            chars[cell] = ch;
            fg[cell] = foreground;
            bg[cell] = background;
            written++;
        }
        return written;
    }

    /** Read the characters of one row, 1-based. */
    public String line(int row) {
        if (row < 1 || row > rows) {
            return "";
        }
        int base = (row - 1) * columns;
        return new String(chars, base, columns);
    }

    public char at(int x, int y) {
        return inside(x, y) ? chars[index(x, y)] : ' ';
    }

    public int foregroundAt(int x, int y) {
        return inside(x, y) ? fg[index(x, y)] : foreground;
    }

    public int backgroundAt(int x, int y) {
        return inside(x, y) ? bg[index(x, y)] : background;
    }

    /** Fill a rectangle with a single character (a space clears it). Out-of-range areas are clipped. */
    public int fill(int x, int y, int width, int height, char ch) {
        int filled = 0;
        for (int row = Math.max(1, y); row < y + height && row <= rows; row++) {
            for (int column = Math.max(1, x); column < x + width && column <= columns; column++) {
                int cell = index(column, row);
                chars[cell] = ch;
                fg[cell] = foreground;
                bg[cell] = background;
                filled++;
            }
        }
        return filled;
    }

    public void clear() {
        java.util.Arrays.fill(chars, ' ');
        java.util.Arrays.fill(fg, foreground);
        java.util.Arrays.fill(bg, background);
    }

    /**
     * Scroll the whole screen up by {@code lines} rows, filling the bottom with blanks.
     * A negative count scrolls down.
     */
    public void scroll(int lines) {
        if (lines == 0) {
            return;
        }
        if (Math.abs(lines) >= rows) {
            clear();
            return;
        }
        int keep = rows - Math.abs(lines);
        if (lines > 0) {
            for (int row = 1; row <= keep; row++) {
                copyRow(row + lines, row);
            }
            blankRows(keep + 1, rows);
        } else {
            int down = -lines;
            for (int row = rows; row > down; row--) {
                copyRow(row - down, row);
            }
            blankRows(1, down);
        }
    }

    private void copyRow(int from, int to) {
        if (from < 1 || from > rows || to < 1 || to > rows) {
            return;
        }
        int source = (from - 1) * columns;
        int target = (to - 1) * columns;
        System.arraycopy(chars, source, chars, target, columns);
        System.arraycopy(fg, source, fg, target, columns);
        System.arraycopy(bg, source, bg, target, columns);
    }

    private void blankRows(int from, int to) {
        for (int row = from; row <= to; row++) {
            int base = (row - 1) * columns;
            java.util.Arrays.fill(chars, base, base + columns, ' ');
            java.util.Arrays.fill(fg, base, base + columns, foreground);
            java.util.Arrays.fill(bg, base, base + columns, background);
        }
    }

    /** Snapshot for the client packet and for tests: rows joined by newlines, trailing blanks trimmed. */
    public String text() {
        StringBuilder builder = new StringBuilder();
        for (int row = 1; row <= rows; row++) {
            if (row > 1) {
                builder.append('\n');
            }
            builder.append(line(row).replaceAll(" +$", ""));
        }
        return builder.toString();
    }

    /** True when every cell is a space, so the block entity can skip sending an unchanged frame. */
    public boolean blank() {
        for (char ch : chars) {
            if (ch != ' ') {
                return false;
            }
        }
        return true;
    }
}
