package com.shadowclangaming.client;

import com.shadowclangaming.block.SiloHoldMenu;
import com.shadowclangaming.client.gui.ArcanePanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The silo's hold, drawn as a chest.
 *
 * Same bones as the cargo missile's hold - 3x9 grid, player inventory underneath - because it is the same
 * job. What it adds is context: a line telling you what the pad will do with what you put in (a
 * constructor builds with it, a cargo warhead delivers it), so the same grid is not ambiguous.
 *
 * Opened by shift + right-clicking the pad with an empty hand. The launch console is a console; this is
 * the container the pad always had and never showed.
 */
public class SiloHoldScreen extends AbstractContainerScreen<SiloHoldMenu> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 168;
    private static final int HOLD_TOP = 18;
    private static final int HOLD_SIZE = SiloHoldMenu.ROWS * SiloHoldMenu.COLUMNS;

    public SiloHoldScreen(SiloHoldMenu menu, Inventory inventory, Component title) {
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
        ArcanePanel.heading(font, graphics, Component.literal("silo hold"), x + 8, y + 6,
                ArcanePanel.ACCENT);

        for (int row = 0; row < SiloHoldMenu.ROWS; row++) {
            for (int column = 0; column < SiloHoldMenu.COLUMNS; column++) {
                int slotX = x + 7 + column * 18;
                int slotY = y + HOLD_TOP - 1 + row * 18;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, ArcanePanel.INSET);
            }
        }

        int inventoryTop = y + HOLD_TOP + HOLD_SIZE / SiloHoldMenu.COLUMNS * 18 + 14;
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
        String used = menu.usedSlots() + " / " + HOLD_SIZE;
        graphics.drawString(font, used, imageWidth - 8 - font.width(used), 6,
                ArcanePanel.TEXT_DIM, false);
        graphics.drawString(font, "blocks for a constructor, cargo for a cargo warhead",
                8, HEIGHT - 92, ArcanePanel.TEXT_FAINT, false);
    }
}
