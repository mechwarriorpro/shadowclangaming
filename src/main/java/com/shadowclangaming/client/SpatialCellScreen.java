package com.shadowclangaming.client;

import com.shadowclangaming.client.gui.ArcanePanel;
import com.shadowclangaming.warhead.SpatialCellMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The spatial missile's cell drive.
 *
 * Deliberately shaped like AE2's own drive: a row of cell bays, each with a status pip, so people who
 * already know how a drive looks do not have to learn anything. The pips are the interesting part - a bay
 * with a cell is lit, an empty bay is dark, and the panel says outright when AE2 is not installed, because
 * "the slots refuse everything" is a confusing way to find that out.
 */
public class SpatialCellScreen extends AbstractContainerScreen<SpatialCellMenu> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 156;

    public SpatialCellScreen(SpatialCellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        ArcanePanel.inset(graphics, x, y, imageWidth, imageHeight);
        ArcanePanel.heading(font, graphics, Component.literal("spatial drive"), x + 8, y + 6,
                ArcanePanel.ACCENT);

        // Bays: an outer recess, an inner slot well, and a status pip in the corner of each.
        for (int bay = 0; bay < SpatialCellMenu.BAYS; bay++) {
            int column = bay % SpatialCellMenu.COLUMNS;
            int row = bay / SpatialCellMenu.COLUMNS;
            int bayX = x + 22 + column * 26;
            int bayY = y + 20 + row * 26;
            graphics.fill(bayX, bayY, bayX + 22, bayY + 22, ArcanePanel.FRAME_MID);
            graphics.fill(bayX + 2, bayY + 2, bayX + 20, bayY + 20, ArcanePanel.INSET);

            boolean filled = !menu.slots.get(bay).getItem().isEmpty();
            int pip = filled ? ArcanePanel.GOOD
                    : menu.ae2Available() ? ArcanePanel.INSET_EDGE : ArcanePanel.BAD;
            graphics.fill(bayX + 17, bayY + 17, bayX + 20, bayY + 20, pip);
        }

        int inventoryTop = y + 26 + SpatialCellMenu.ROWS * 26 + 12;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int slotX = x + 7 + column * 18;
                int slotY = inventoryTop - 1 + row * 18;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, ArcanePanel.INSET);
            }
        }
        for (int column = 0; column < 9; column++) {
            int slotX = x + 7 + column * 18;
            int slotY = inventoryTop + 57;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, ArcanePanel.INSET);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String loaded = menu.loaded() + " / " + SpatialCellMenu.BAYS;
        graphics.drawString(font, loaded, imageWidth - 8 - font.width(loaded), 6,
                ArcanePanel.TEXT_DIM, false);

        String state = menu.ae2Available()
                ? "spatial storage cells only"
                : "AE2 not installed - nothing can be stored";
        graphics.drawString(font, state, 8, 20 + SpatialCellMenu.ROWS * 26 + 2,
                menu.ae2Available() ? ArcanePanel.TEXT_FAINT : ArcanePanel.BAD, false);
    }
}
