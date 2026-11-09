package com.shadowclangaming.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Drawing helpers for the mod's screens.
 *
 * ──────────────────── THE LOOK ────────────────────
 *
 * Every screen here is drawn with primitives, not a GUI texture sheet. That is a deliberate trade: a
 * texture sheet means every layout change is an art task, and it means one missing pixel breaks the whole
 * window. Drawing gives us the Ars Magica 2 vocabulary - warm parchment inside a dark engraved frame,
 * glowing arcane trim, inlaid gem buttons, small caps headings - at any size, with no assets to keep in
 * sync.
 *
 * The vocabulary, in one place so every screen agrees:
 *
 *   frame        near-black slate with a lighter bevel and a rune band along the top
 *   body         dark violet parchment, subtly vignetted
 *   gem button   a rounded lozenge; violet idle, brighter and haloed on hover, grey when disabled
 *   heading      small-caps, letter-spaced, in the accent colour
 *   bar          forked mana/energy gauges with an inner highlight line
 */
public final class ArcanePanel {

    // ---- palette ------------------------------------------------------------------------------
    //
    // LIGHT parchment, dark ink. The first pass of these screens was near-black (0xFF120E18 frames
    // over 0xFF191324 bodies) with pale text, which looks fine in isolation and is unreadable on a
    // normal monitor: the whole window reads as a dark smear. Ars Magica's desk was a lit page, so the
    // body is parchment, the ink is near-black, and colour is used for accents only - never to make
    // text readable against a dark background.
    public static final int FRAME_DARK = 0xFF3A2A1A;
    public static final int FRAME_MID = 0xFF5C4326;
    public static final int FRAME_LIGHT = 0xFF8A6A3E;
    public static final int RUNE = 0xFFB08B52;

    public static final int BODY = 0xFFEFE4CB;
    public static final int BODY_LIGHT = 0xFFF8F0DC;
    public static final int INSET = 0xFFDFD2B4;
    public static final int INSET_EDGE = 0xFFB8A47B;

    public static final int TEXT = 0xFF241B10;
    public static final int TEXT_DIM = 0xFF55452C;
    public static final int TEXT_FAINT = 0xFF7C6A48;
    public static final int ACCENT = 0xFF5B2E93;
    public static final int ACCENT_2 = 0xFF16637A;
    public static final int GOLD = 0xFF8A5A00;
    public static final int GOOD = 0xFF1C6B2A;
    public static final int BAD = 0xFF9E1B1B;

    public static final int MANA = 0xFF6B3FA0;
    public static final int MANA_LIGHT = 0xFF8F5FCB;
    public static final int ENERGY = 0xFF9A6A00;
    public static final int XP = 0xFF2E7A2E;

    private ArcanePanel() {
    }

    // ---- frame ---------------------------------------------------------------------------------

    /** The whole window: engraved frame, rune band, parchment body. */
    public static void window(GuiGraphics graphics, int x, int y, int width, int height) {
        // drop shadow
        graphics.fill(x - 3, y - 3, x + width + 3, y + height + 3, 0x50000000);
        // frame
        graphics.fill(x, y, x + width, y + height, FRAME_DARK);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, FRAME_MID);
        // bevel: light on the top-left, dark on the bottom-right - the "engraved" read
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, FRAME_LIGHT);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFF2E2540);
        // rune band
        runeBand(graphics, x + 2, y + 2, width - 4);
        // parchment body
        body(graphics, x + 3, y + RUNE_BAND + 3, width - 6, height - RUNE_BAND - 6);
    }

    public static final int RUNE_BAND = 14;

    /** The strip of glyphs across the top of every window. Deterministic, so it does not flicker. */
    public static void runeBand(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + RUNE_BAND - 1, 0xFF3A2A1A);
        int step = 7;
        for (int i = 0, gx = x + 4; gx < x + width - 4; i++, gx += step) {
            int h = 3 + (i * 5) % 5;
            int colour = (i % 3 == 0) ? ACCENT : RUNE;
            graphics.fill(gx, y + (RUNE_BAND - 1 - h) / 2, gx + 1, y + (RUNE_BAND - 1 - h) / 2 + h, colour);
        }
        graphics.fill(x, y + RUNE_BAND - 2, x + width, y + RUNE_BAND - 1, 0xFF20160D);
    }

    /** The inset working area. */
    public static void body(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BODY);
        // soft warm vignette so the centre reads as lit parchment
        graphics.fill(x, y, x + width, y + 5, 0x18A08050);
        graphics.fill(x, y + height - 6, x + width, y + height, 0x18A08050);
        // faint grid, like graph paper under the ink
        for (int gx = x + 8; gx < x + width - 4; gx += 16) {
            graphics.fill(gx, y + 2, gx + 1, y + height - 2, 0x10000000);
        }
        for (int gy = y + 8; gy < y + height - 4; gy += 16) {
            graphics.fill(x + 2, gy, x + width - 2, gy + 1, 0x10000000);
        }
    }

    /** A sunken well for lists, previews and log output. */
    public static void inset(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, INSET);
        graphics.fill(x, y, x + width, y + 1, INSET_EDGE);
        graphics.fill(x, y, x + 1, y + height, INSET_EDGE);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF9C8A62);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF9C8A62);
    }

    /** A smaller sunken chip, for a single value readout. */
    public static void chip(GuiGraphics graphics, int x, int y, int width, int height, int colour) {
        graphics.fill(x, y, x + width, y + height, INSET);
        graphics.fill(x, y, x + width, y + 1, colour);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF9C8A62);
    }

    // ---- text ----------------------------------------------------------------------------------

    /** Small-caps heading with letter spacing. Returns the width drawn. */
    public static int heading(Font font, GuiGraphics graphics, Component text, int x, int y, int colour) {
        String raw = text.getString().toUpperCase(java.util.Locale.ROOT);
        int cursor = x;
        for (int i = 0; i < raw.length(); i++) {
            String ch = String.valueOf(raw.charAt(i));
            graphics.drawString(font, ch, cursor, y, colour, false);
            cursor += font.width(ch) + 1;
        }
        return cursor - x;
    }

    /** A divider line with a diamond in the middle - the "section break" of the look. */
    public static void divider(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, 0x30000000);
        int mid = x + width / 2;
        graphics.fill(mid - 2, y - 1, mid + 2, y + 2, ACCENT);
        graphics.fill(mid - 1, y - 2, mid + 1, y + 3, ACCENT);
    }

    /** Truncates with an ellipsis rather than clipping mid-glyph. */
    public static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (font.width(out.toString() + text.charAt(i) + "...") > width) {
                break;
            }
            out.append(text.charAt(i));
        }
        return out + "...";
    }

    // ---- bars ----------------------------------------------------------------------------------

    /**
     * A horizontal gauge.
     *
     * @param fraction 0..1
     * @param colour   fill colour; the bar draws a lighter highlight line near the top of the fill
     */
    public static void bar(GuiGraphics graphics, int x, int y, int width, int height,
                           double fraction, int colour, int background) {
        graphics.fill(x, y, x + width, y + height, background);
        double clamped = Mth.clamp(fraction, 0.0D, 1.0D);
        int filled = (int) Math.round(clamped * (width - 2));
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, colour);
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + 2, lighten(colour, 0.35F));
        }
        graphics.fill(x, y, x + width, y + 1, 0xFF9C8A62);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF9C8A62);
    }

    public static int lighten(int argb, float amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = Mth.clamp((int) (((argb >> 16) & 0xFF) + 255 * amount), 0, 255);
        int g = Mth.clamp((int) (((argb >> 8) & 0xFF) + 255 * amount), 0, 255);
        int b = Mth.clamp((int) ((argb & 0xFF) + 255 * amount), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ---- glyphs --------------------------------------------------------------------------------

    /** A tiny drawn icon so buttons do not need a font glyph or an asset. */
    public enum Glyph {
        PLUS, MINUS, CROSS, CHECK, CHEVRON_LEFT, CHEVRON_RIGHT, CHEVRON_UP, CHEVRON_DOWN,
        STAR, FLAME, BOLT, DROP, SHIELD, SKULL, DIAMOND, GEAR
    }

    public static void glyph(GuiGraphics graphics, Glyph glyph, int x, int y, int colour) {
        switch (glyph) {
            case PLUS -> {
                graphics.fill(x + 3, y, x + 5, y + 8, colour);
                graphics.fill(x, y + 3, x + 8, y + 5, colour);
            }
            case MINUS -> graphics.fill(x, y + 3, x + 8, y + 5, colour);
            case CROSS -> {
                for (int i = 0; i < 8; i++) {
                    graphics.fill(x + i, y + i, x + i + 1, y + i + 1, colour);
                    graphics.fill(x + 7 - i, y + i, x + 8 - i, y + i + 1, colour);
                }
            }
            case CHECK -> {
                for (int i = 0; i < 4; i++) {
                    graphics.fill(x + i, y + 4 + i, x + i + 1, y + 5 + i, colour);
                }
                for (int i = 0; i < 5; i++) {
                    graphics.fill(x + 3 + i, y + 7 - i, x + 4 + i, y + 8 - i, colour);
                }
            }
            case CHEVRON_LEFT -> {
                for (int i = 0; i < 4; i++) {
                    graphics.fill(x + 4 - i, y + 4 - i, x + 5 - i, y + 5 - i, colour);
                    graphics.fill(x + 4 - i, y + 4 + i, x + 5 - i, y + 5 + i, colour);
                }
            }
            case CHEVRON_RIGHT -> {
                for (int i = 0; i < 4; i++) {
                    graphics.fill(x + 4 + i, y + 4 - i, x + 5 + i, y + 5 - i, colour);
                    graphics.fill(x + 4 + i, y + 4 + i, x + 5 + i, y + 5 + i, colour);
                }
            }
            case CHEVRON_UP -> {
                for (int i = 0; i < 4; i++) {
                    graphics.fill(x + 4 - i, y + 4 + i, x + 5 - i, y + 5 + i, colour);
                    graphics.fill(x + 4 + i, y + 4 + i, x + 5 + i, y + 5 + i, colour);
                }
            }
            case CHEVRON_DOWN -> {
                for (int i = 0; i < 4; i++) {
                    graphics.fill(x + 4 - i, y + 4 - i, x + 5 - i, y + 5 - i, colour);
                    graphics.fill(x + 4 + i, y + 4 - i, x + 5 + i, y + 5 - i, colour);
                }
            }
            case STAR -> {
                graphics.fill(x + 3, y, x + 5, y + 8, colour);
                graphics.fill(x, y + 2, x + 8, y + 4, colour);
                graphics.fill(x + 1, y + 5, x + 3, y + 7, colour);
                graphics.fill(x + 5, y + 5, x + 7, y + 7, colour);
            }
            case FLAME -> {
                graphics.fill(x + 3, y, x + 5, y + 2, colour);
                graphics.fill(x + 2, y + 2, x + 6, y + 5, colour);
                graphics.fill(x + 1, y + 4, x + 7, y + 8, colour);
            }
            case BOLT -> {
                graphics.fill(x + 4, y, x + 7, y + 4, colour);
                graphics.fill(x + 2, y + 3, x + 6, y + 5, colour);
                graphics.fill(x + 1, y + 4, x + 4, y + 8, colour);
            }
            case DROP -> {
                graphics.fill(x + 3, y, x + 5, y + 3, colour);
                graphics.fill(x + 2, y + 2, x + 6, y + 5, colour);
                graphics.fill(x + 1, y + 4, x + 7, y + 8, colour);
            }
            case SHIELD -> {
                graphics.fill(x + 1, y, x + 7, y + 5, colour);
                graphics.fill(x + 2, y + 5, x + 6, y + 7, colour);
                graphics.fill(x + 3, y + 7, x + 5, y + 8, colour);
            }
            case SKULL -> {
                graphics.fill(x + 1, y + 1, x + 7, y + 6, colour);
                graphics.fill(x + 2, y + 6, x + 6, y + 8, colour);
                graphics.fill(x + 2, y + 3, x + 3, y + 5, 0xFF000000);
                graphics.fill(x + 5, y + 3, x + 6, y + 5, 0xFF000000);
            }
            case DIAMOND -> {
                graphics.fill(x + 3, y, x + 5, y + 8, colour);
                graphics.fill(x + 2, y + 1, x + 6, y + 7, colour);
                graphics.fill(x + 1, y + 2, x + 7, y + 6, colour);
            }
            case GEAR -> {
                graphics.fill(x + 2, y + 2, x + 6, y + 6, colour);
                graphics.fill(x + 3, y, x + 5, y + 8, colour);
                graphics.fill(x, y + 3, x + 8, y + 5, colour);
                graphics.fill(x + 3, y + 3, x + 5, y + 5, 0xFF000000);
            }
        }
    }
}
