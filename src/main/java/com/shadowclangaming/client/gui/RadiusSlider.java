package com.shadowclangaming.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;

/**
 * The radius slider, as a real widget.
 *
 * THE BUG THIS FIXES. The first version of the launch pad's slider was a custom-drawn bar with hand-rolled
 * {@code mouseClicked}/{@code mouseDragged} hit-testing, and it was the classic version of that mistake:
 * the hit area was {@code sliderY..sliderY+6} while the bar was never actually drawn there, so there was
 * nothing visible to grab and clicks landed on the panel instead. It read as "the slider does not work".
 *
 * This is the fix in the shape it should have had: a subclass of vanilla's {@link AbstractSliderButton},
 * which already owns click capture, drag capture, the arrow keys, and the "value changed" callback. Only
 * two things are replaced - the drawing (so it matches the arcane panels) and the value mapping (log
 * scale, because radii people want are 4, 16, 64, 256 and 4096, not evenly spaced).
 *
 * Radius 0 means "auto": the warhead derives its size from the payload or the funding, and the label says
 * so instead of pretending zero blocks is a size.
 */
public class RadiusSlider extends AbstractSliderButton {

    public static final int MAX_RADIUS = 4096;

    private final IntConsumer onChange;
    private int lastRadius;

    public RadiusSlider(int x, int y, int width, int height, int radius, IntConsumer onChange) {
        super(x, y, width, height, Component.empty(), fractionOf(radius));
        this.onChange = onChange;
        this.lastRadius = clampRadius(radius);
        updateMessage();
    }

    /** Radius as a 0..1 slider position. Log scale, so the useful low end is reachable by hand. */
    public static double fractionOf(int radius) {
        int clamped = clampRadius(radius);
        if (clamped <= 0) {
            return 0.0D;
        }
        return Math.min(1.0D, Math.log10(clamped + 1.0D) / Math.log10(MAX_RADIUS + 1.0D));
    }

    /** Slider position back to a radius, rounded to the nearest whole block. */
    public static int radiusOf(double fraction) {
        if (fraction <= 0.0D) {
            return 0;
        }
        int value = (int) Math.round(
                Math.pow(10.0D, Math.min(1.0D, fraction) * Math.log10(MAX_RADIUS + 1.0D)) - 1.0D);
        return clampRadius(value);
    }

    public static int clampRadius(int radius) {
        return Mth.clamp(radius, 0, MAX_RADIUS);
    }

    @Override
    protected void updateMessage() {
        int radius = radiusOf(this.value);
        setMessage(radius <= 0
                ? Component.literal("radius: auto")
                : Component.literal("radius: " + radius + " blocks"));
    }

    @Override
    protected void applyValue() {
        int radius = radiusOf(this.value);
        if (radius == lastRadius) {
            return;
        }
        lastRadius = radius;
        // One callbacks per actual change, not per pixel dragged.
        this.onChange.accept(radius);
    }

    /** Push a value in from the server (or a typed entry) without firing the change callback. */
    public void setRadius(int radius) {
        this.lastRadius = clampRadius(radius);
        this.value = fractionOf(radius);
        updateMessage();
    }

    public int radius() {
        return radiusOf(this.value);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();

        // Track: an inset channel with the filled part in the panel accent.
        graphics.fill(x, y + height / 2 - 2, x + width, y + height / 2 + 2, ArcanePanel.INSET_EDGE);
        graphics.fill(x, y + height / 2 - 1, x + width, y + height / 2 + 1, ArcanePanel.BODY);

        int filled = (int) Math.round(this.value * width);
        if (this.value > 0.0D) {
            graphics.fill(x, y + height / 2 - 1, x + filled, y + height / 2 + 1, ArcanePanel.ACCENT);
        }

        // Knob: a raised handle, wider than the track so it reads as grabbable.
        int knobX = x + Mth.clamp(filled, 2, width - 3);
        int knobTop = y + height / 2 - 5;
        graphics.fill(knobX - 2, knobTop, knobX + 3, knobTop + 10, ArcanePanel.FRAME_DARK);
        graphics.fill(knobX - 1, knobTop + 1, knobX + 2, knobTop + 9,
                isHovered() || isFocused() ? ArcanePanel.ACCENT : ArcanePanel.RUNE);

        // Label, centred on the widget.
        Minecraft minecraft = Minecraft.getInstance();
        int colour = isHovered() || isFocused() ? ArcanePanel.TEXT : ArcanePanel.TEXT_DIM;
        graphics.drawCenteredString(minecraft.font, getMessage(),
                x + width / 2, y - 9, ArcanePanel.TEXT_DIM);
        if (colour != ArcanePanel.TEXT_DIM) {
            graphics.drawCenteredString(minecraft.font, getMessage(),
                    x + width / 2, y - 9, colour);
        }
    }
}
