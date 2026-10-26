package com.shadowclangaming.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The button of the mod's screens: an inlaid gem lozenge rather than vanilla's grey slab.
 *
 * It is a plain widget on purpose. Subclassing {@code Button} would work, but Button re-implements its own
 * drawing and we would spend the class fighting it; a widget with explicit {@link #onPress()} is shorter
 * and does exactly what a gem button should: violet when idle, brighter with a halo on hover, desaturated
 * when disabled, flashing when {@link #flash} is called (used for "you cannot afford this").
 */
public class GemButton extends AbstractWidget implements Renderable {

    public enum Style { PLAIN, PRIMARY, DANGER, TOGGLE_ON, TOGGLE_OFF }

    private final Runnable onPress;
    private Style style;
    private boolean selected;
    private int flashTicks;
    /** Optional drawn glyph, or null for text only. */
    private ArcanePanel.Glyph glyph;

    public GemButton(int x, int y, int width, int height, Component message, Style style,
                     Runnable onPress) {
        super(x, y, width, height, message);
        this.style = style;
        this.onPress = onPress;
    }

    public GemButton glyph(ArcanePanel.Glyph glyph) {
        this.glyph = glyph;
        return this;
    }

    public GemButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public boolean selected() {
        return selected;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    public void flash() {
        this.flashTicks = 12;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (active) {
            onPress.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (flashTicks > 0) {
            flashTicks--;
        }
        boolean hovered = this.isHoveredOrFocused() && this.active;

        int base = switch (style) {
            case PRIMARY -> 0xFFB79BE0;
            case DANGER -> 0xFFD79A9A;
            case TOGGLE_ON -> 0xFF9CCBD8;
            case TOGGLE_OFF -> 0xFFCFC6B4;
            case PLAIN -> 0xFFDCCFB0;
        };
        int edge = switch (style) {
            case PRIMARY -> 0xFF4A1F80;
            case DANGER -> 0xFF8E2020;
            case TOGGLE_ON -> 0xFF125A70;
            case TOGGLE_OFF -> 0xFF7C6A48;
            case PLAIN -> 0xFF8A7550;
        };
        if (selected) {
            base = ArcanePanel.lighten(base, 0.10F);
            edge = ArcanePanel.GOLD;
        }
        if (hovered) {
            base = ArcanePanel.lighten(base, 0.14F);
            edge = ArcanePanel.lighten(edge, 0.25F);
        }
        if (!this.active) {
            base = 0xFFDBD4C6;
            edge = 0xFFA49A86;
        }

        int x = getX();
        int y = getY();
        int right = x + width;
        int bottom = y + height;

        // 1. halo, only when it means something
        if (hovered || flashTicks > 0) {
            int halo = flashTicks > 0 ? 0x60C02020 : 0x505B2E93;
            graphics.fill(x - 2, y - 1, right + 2, bottom + 1, halo);
        }

        // 2. shadow and body
        graphics.fill(x + 1, y + 1, right + 1, bottom + 1, 0x80000000);
        graphics.fill(x, y, right, bottom, base);

        // 3. gem bevel: light top, dark bottom
        graphics.fill(x, y, right, y + 1, ArcanePanel.lighten(edge, 0.20F));
        graphics.fill(x, y, x + 1, bottom, ArcanePanel.lighten(edge, 0.05F));
        graphics.fill(x, bottom - 1, right, bottom, 0xFF6B5B3E);
        graphics.fill(right - 1, y, right, bottom, 0xFF6B5B3E);

        // 4. label
        Minecraft minecraft = Minecraft.getInstance();
        int textColour = this.active ? ArcanePanel.TEXT : ArcanePanel.TEXT_FAINT;
        if (selected && this.active) {
            textColour = ArcanePanel.GOLD;
        }
        String label = ArcanePanel.fit(minecraft.font, getMessage().getString(), width - 8);
        int textX = x + (width - minecraft.font.width(label)) / 2;
        int textY = y + (height - 8) / 2;
        if (glyph != null) {
            int glyphX = x + 3;
            ArcanePanel.glyph(graphics, glyph, glyphX, y + (height - 8) / 2, textColour);
            label = ArcanePanel.fit(minecraft.font, getMessage().getString(), width - 16);
            textX = x + 12 + (width - 14 - minecraft.font.width(label)) / 2;
        }
        graphics.drawString(minecraft.font, label, textX, textY, textColour, false);

        // 5. underline hint for the selected entry
        if (selected) {
            graphics.fill(x + 3, bottom - 3, right - 3, bottom - 2, ArcanePanel.GOLD);
        }
    }

    /** Convenience: a button that also pulses when the server refused the action. */
    public void nudge() {
        flash(Mth.clamp(6, 1, 20));
    }

    private void flash(int ticks) {
        this.flashTicks = ticks;
    }
}
