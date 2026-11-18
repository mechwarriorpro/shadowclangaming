package com.shadowclangaming.computer.api;

import java.util.Arrays;

/**
 * The colour palette.
 *
 * Trusted code passes packed 24-bit RGB; scripts pass either a palette index (0-15, and 0-255 for the
 * extended range) or a {@code "0xRRGGBB"} string, which is what real programs use. All three land here so
 * there is exactly one place that can be wrong.
 */
public final class Colors {

    public static final int BLACK = 0x000000;
    public static final int WHITE = 0xFFFFFF;

    private static final int[] PALETTE_16 = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xAA5500, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private Colors() {
    }

    /** Palette lookup for 0-15, a 6-level cube for 16-231, greys for 232-255, mirroring the ANSI layout. */
    public static int fromIndex(int index) {
        if (index < 0) {
            return BLACK;
        }
        if (index < 16) {
            return PALETTE_16[index];
        }
        if (index < 232) {
            int value = index - 16;
            int r = value / 36;
            int g = (value / 6) % 6;
            int b = value % 6;
            return (level(r) << 16) | (level(g) << 8) | level(b);
        }
        if (index < 256) {
            int grey = 8 + (index - 232) * 10;
            return (grey << 16) | (grey << 8) | grey;
        }
        return WHITE;
    }

    private static int level(int step) {
        return step * 0x33;
    }

    /**
     * Turn whatever a script passed into packed RGB.
     *
     * Anything unparseable becomes white rather than raising: a colour is never worth killing a running
     * program over, and the screen immediately shows the mistake.
     */
    public static int parse(Object value, int fallback) {
        if (value instanceof Double number) {
            double raw = number;
            if (raw < 0) {
                return fallback;
            }
            if (raw <= 255D) {
                return fromIndex((int) raw);
            }
            return (int) raw & 0xFFFFFF;
        }
        if (value instanceof String text) {
            String trimmed = text.trim().toLowerCase(java.util.Locale.ROOT);
            if (trimmed.startsWith("0x")) {
                trimmed = trimmed.substring(2);
            } else if (trimmed.startsWith("#")) {
                trimmed = trimmed.substring(1);
            }
            try {
                return Integer.parseInt(trimmed, 16) & 0xFFFFFF;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /** Format a colour for {@code gpu.getForeground}, matching the 0xRRGGBB form scripts expect. */
    public static String format(int rgb) {
        return String.format(java.util.Locale.ROOT, "0x%06X", rgb & 0xFFFFFF);
    }

    /** The nearest palette index, used by the charcoal display and by tests. */
    public static int nearestIndex(int rgb) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < PALETTE_16.length; i++) {
            int distance = distance(PALETTE_16[i], rgb);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static int distance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db;
    }

    public static int[] palette() {
        return Arrays.copyOf(PALETTE_16, PALETTE_16.length);
    }
}
