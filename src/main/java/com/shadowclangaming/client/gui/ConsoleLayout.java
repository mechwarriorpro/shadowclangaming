package com.shadowclangaming.client.gui;

import java.util.List;

/**
 * Where everything goes on the two console screens.
 *
 * WHY THIS IS A SEPARATE, MINECRAFT-FREE CLASS. The first version of these screens placed each box with
 * hand-typed offsets sprinkled through the drawing code, and the results were exactly what you would
 * expect: the console strip overlapped the silo panel, a field was drawn over a button, and the whole window
 * was 350x214 - which does not fit a 1366x768 screen at GUI scale 4 (341x192), so it went off the bottom.
 *
 * Rectangles in one place fixes that: the harness asserts that every box is inside the window and that no
 * two boxes overlap, so a layout mistake is a failed test rather than a bug report. The compressing is done
 * by the numbers below, not by squeezing text.
 *
 * Coordinates are relative to the screen's top-left (add leftPos/topPos when drawing).
 */
public final class ConsoleLayout {

    private ConsoleLayout() {
    }

    /** A rectangle in screen space. */
    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean inside(int width, int height) {
            return x >= 0 && y >= 0 && right() <= width && bottom() <= height;
        }

        /** Overlap, excluding boxes that merely share an edge. */
        public boolean overlaps(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }

        public Rect inset(int dx, int dy) {
            return new Rect(x + dx, y + dy, w - dx * 2, h - dy * 2);
        }

        /** A sub-rectangle of this one, starting at an offset and taking a slice of the width. */
        public Rect column(int offsetX, int offsetY, int columnWidth, int columnHeight) {
            return new Rect(x + offsetX, y + offsetY, columnWidth, columnHeight);
        }
    }

    // ------------------------------------------------------------------ launch pad

    /**
     * 320x180. Fits a 1366x768 client at GUI scale 4 (341x192) with room to spare, and is 66 px shorter
     * than the 350x214 window it replaces.
     */
    public static final int PAD_WIDTH = 320;
    public static final int PAD_HEIGHT = 180;

    public static final Rect PAD_WARHEAD = new Rect(6, 18, 150, 70);
    public static final Rect PAD_SILO = new Rect(6, 92, 150, 48);
    public static final Rect PAD_CONSOLE_LOG = new Rect(6, 142, 308, 20);
    public static final Rect PAD_CONSOLE_INPUT = new Rect(6, 164, 308, 13);

    /** Six action buttons, two columns of three. */
    public static final Rect PAD_ACTIONS = new Rect(160, 18, 154, 44);
    public static final int PAD_BUTTON_W = 74;
    public static final int PAD_BUTTON_H = 13;
    public static final int PAD_BUTTON_GAP_X = 6;
    public static final int PAD_BUTTON_GAP_Y = 2;

    /** Channel, split altitude, target X/Y/Z and the auto buttons. */
    public static final Rect PAD_SETTINGS = new Rect(160, 66, 154, 72);
    public static final int PAD_FIELD_W = 70;
    public static final int PAD_FIELD_H = 12;
    public static final int PAD_FIELD_GAP_X = 4;
    public static final int PAD_FIELD_ROW = 15;

    /** Where each settings field lives, measured from the settings box. */
    public static final Rect PAD_FIELD_CHANNEL = PAD_SETTINGS.column(4, 15, PAD_FIELD_W, PAD_FIELD_H);
    public static final Rect PAD_FIELD_SPLIT = PAD_SETTINGS.column(4 + PAD_FIELD_W + PAD_FIELD_GAP_X, 15,
            PAD_FIELD_W, PAD_FIELD_H);
    public static final Rect PAD_FIELD_X = PAD_SETTINGS.column(4, 15 + PAD_FIELD_ROW, PAD_FIELD_W, PAD_FIELD_H);
    public static final Rect PAD_FIELD_Y = PAD_SETTINGS.column(4 + PAD_FIELD_W + PAD_FIELD_GAP_X,
            15 + PAD_FIELD_ROW, PAD_FIELD_W, PAD_FIELD_H);
    public static final Rect PAD_FIELD_Z = PAD_SETTINGS.column(4, 15 + PAD_FIELD_ROW * 2, PAD_FIELD_W,
            PAD_FIELD_H);
    public static final Rect PAD_FIELD_TARGET_APPLY = PAD_SETTINGS.column(4 + PAD_FIELD_W + PAD_FIELD_GAP_X,
            15 + PAD_FIELD_ROW * 2, PAD_FIELD_W, PAD_FIELD_H);

    /** The radius slider under the warhead readout (only drawn for sliders-own-radius warheads). */
    public static final Rect PAD_SLIDER = new Rect(10, 58, 130, 8);
    public static final Rect PAD_SLIDER_INPUT = new Rect(10, 70, 62, 12);
    public static final Rect PAD_SLIDER_AUTO = new Rect(76, 70, 40, 12);

    /** Top-level boxes: these must never overlap each other. */
    public static List<Rect> padRects() {
        return List.of(PAD_WARHEAD, PAD_SILO, PAD_CONSOLE_LOG, PAD_CONSOLE_INPUT, PAD_ACTIONS, PAD_SETTINGS);
    }

    /** Widgets that live INSIDE a parent box: these must stay inside it, but overlap their parent. */
    public static List<Rect[]> padChildren() {
        return List.of(
                new Rect[]{PAD_SLIDER, PAD_WARHEAD},
                new Rect[]{PAD_SLIDER_INPUT, PAD_WARHEAD},
                new Rect[]{PAD_SLIDER_AUTO, PAD_WARHEAD},
                new Rect[]{PAD_FIELD_CHANNEL, PAD_SETTINGS},
                new Rect[]{PAD_FIELD_SPLIT, PAD_SETTINGS},
                new Rect[]{PAD_FIELD_X, PAD_SETTINGS},
                new Rect[]{PAD_FIELD_Y, PAD_SETTINGS},
                new Rect[]{PAD_FIELD_Z, PAD_SETTINGS},
                new Rect[]{PAD_FIELD_TARGET_APPLY, PAD_SETTINGS});
    }

    // ------------------------------------------------------------------ control panel

    /** 300x168 - smaller than the pad screen because it has less to say. */
    public static final int PANEL_WIDTH = 300;
    public static final int PANEL_HEIGHT = 168;

    public static final Rect PANEL_ARM = new Rect(8, 18, 120, 18);
    public static final Rect PANEL_FIRE = new Rect(134, 18, 120, 18);
    public static final Rect PANEL_TARGET = new Rect(8, 42, 120, 62);
    public static final Rect PANEL_MODULES = new Rect(134, 42, 158, 62);
    public static final Rect PANEL_TIER = new Rect(8, 108, 120, 32);
    public static final Rect PANEL_PAD = new Rect(134, 108, 158, 32);
    public static final Rect PANEL_CONSOLE_LOG = new Rect(8, 144, 284, 12);
    public static final Rect PANEL_CONSOLE_INPUT = new Rect(8, 156, 284, 11);

    public static List<Rect> panelRects() {
        return List.of(PANEL_ARM, PANEL_FIRE, PANEL_TARGET, PANEL_MODULES, PANEL_TIER, PANEL_PAD,
                PANEL_CONSOLE_LOG, PANEL_CONSOLE_INPUT);
    }

    /** Smallest GUI-scale footprint either screen needs, for the docs and the tests. */
    public static int widest() {
        return Math.max(PAD_WIDTH, PANEL_WIDTH);
    }

    public static int tallest() {
        return Math.max(PAD_HEIGHT, PANEL_HEIGHT);
    }
}
