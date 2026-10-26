package com.shadowclangaming.client;

import com.shadowclangaming.block.BlueprintTableBlockEntity;
import com.shadowclangaming.block.BlueprintTableMenu;
import com.shadowclangaming.blueprint.BlueprintMath;
import com.shadowclangaming.client.gui.ArcanePanel;
import com.shadowclangaming.client.gui.GemButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The rifle bench screen: put the blocks in, set the dials, hit CRAFT.
 *
 * The two dials are the whole point of this screen. Barrels change the rate of fire and the mana bill;
 * strength changes what a shot actually hits for. Both go to 128, and the readout underneath says what
 * the current pair adds up to BEFORE the materials are eaten - which is the thing the old
 * corner-marking flow could never tell you.
 */
public class BlueprintTableScreen extends AbstractContainerScreen<BlueprintTableMenu> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 234;

    private static final int DIAL_BARRELS_Y = 20;
    private static final int DIAL_STRENGTH_Y = 38;
    private static final int CRAFT_Y = 58;
    private static final int STATS_Y = 78;

    public BlueprintTableScreen(BlueprintTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        addDial(DIAL_BARRELS_Y, BlueprintTableMenu.BUTTON_BARRELS, "barrels",
                "How many barrels fire in rotation.\nEach one: -5% cooldown, +2.5% mana, +2% power.");
        addDial(DIAL_STRENGTH_Y, BlueprintTableMenu.BUTTON_STRENGTH, "strength",
                "How hard each barrel hits.\nEach point: +3% power, +2% mana, +1% cooldown.");

        GemButton craft = new GemButton(leftPos + 8, topPos + CRAFT_Y, WIDTH - 16, 16,
                Component.literal("CRAFT"), GemButton.Style.PRIMARY,
                () -> send(BlueprintTableMenu.BUTTON_CRAFT));
        craft.glyph(ArcanePanel.Glyph.GEAR);
        craft.setTooltip(Tooltip.create(Component.literal(
                "Turn the bench into a rifle.\nConsumes every block in the 27 slots.")));
        addRenderableWidget(craft);
    }

    private void addDial(int y, int baseButton, String label, String tooltip) {
        int x = leftPos + 8;
        GemButton down8 = new GemButton(x + 62, topPos + y, 14, 14, Component.literal("--"),
                GemButton.Style.PLAIN,
                () -> send(baseButton + 3));
        down8.setTooltip(Tooltip.create(Component.literal(label + ": minus 8")));
        addRenderableWidget(down8);

        GemButton down = new GemButton(x + 78, topPos + y, 14, 14, Component.literal("-"),
                GemButton.Style.PLAIN,
                () -> send(baseButton + 1));
        down.setTooltip(Tooltip.create(Component.literal(tooltip)));
        addRenderableWidget(down);

        GemButton up = new GemButton(x + 112, topPos + y, 14, 14, Component.literal("+"),
                GemButton.Style.PLAIN,
                () -> send(baseButton));
        up.setTooltip(Tooltip.create(Component.literal(tooltip)));
        addRenderableWidget(up);

        GemButton up8 = new GemButton(x + 128, topPos + y, 14, 14, Component.literal("++"),
                GemButton.Style.PLAIN,
                () -> send(baseButton + 2));
        up8.setTooltip(Tooltip.create(Component.literal(label + ": plus 8")));
        addRenderableWidget(up8);

        GemButton max = new GemButton(x + 144, topPos + y, 16, 14, Component.literal("128"),
                GemButton.Style.DANGER,
                () -> send(baseButton + 4));
        max.setTooltip(Tooltip.create(Component.literal(label + ": max (128)")));
        addRenderableWidget(max);
    }

    private void send(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        ArcanePanel.window(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawSlotBacks(graphics);
        drawLabels(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawReadout(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // window drawn in render()
    }

    private void drawSlotBacks(GuiGraphics graphics) {
        for (Slot slot : this.menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x60201810);
            graphics.fill(x - 1, y - 1, x + 17, y, 0x80_8A6A3E);
        }
    }

    private void drawLabels(GuiGraphics graphics) {
        ArcanePanel.heading(this.font, graphics, Component.literal("rifle bench"),
                leftPos + 8, topPos + 5, ArcanePanel.TEXT);

        graphics.drawString(this.font, "BARRELS", leftPos + 8, topPos + DIAL_BARRELS_Y + 3,
                ArcanePanel.ACCENT_2, false);
        graphics.drawString(this.font, "STRENGTH", leftPos + 8, topPos + DIAL_STRENGTH_Y + 3,
                ArcanePanel.ACCENT_2, false);

        String barrels = String.valueOf(this.menu.barrels());
        graphics.drawString(this.font, barrels,
                leftPos + 96 - this.font.width(barrels) / 2, topPos + DIAL_BARRELS_Y + 3,
                ArcanePanel.GOLD, false);
        String strength = String.valueOf(this.menu.strength());
        graphics.drawString(this.font, strength,
                leftPos + 96 - this.font.width(strength) / 2, topPos + DIAL_STRENGTH_Y + 3,
                ArcanePanel.GOLD, false);
    }

    /** What the current dials and bench materials would produce. */
    private void drawReadout(GuiGraphics graphics) {
        int x = leftPos + 8;
        int y = topPos + STATS_Y;
        if (!this.menu.valid()) {
            graphics.drawString(this.font, "add blocks + an Emitter", x, y, ArcanePanel.BAD, false);
            graphics.drawString(this.font, this.menu.data().get(BlueprintTableMenu.DATA_BLOCKS)
                    + " / " + BlueprintMath.MIN_BLOCKS + " blocks", x, y + 10,
                    ArcanePanel.TEXT_FAINT, false);
            return;
        }
        int tier = this.menu.data().get(BlueprintTableMenu.DATA_TIER);
        int efficiency = this.menu.data().get(BlueprintTableMenu.DATA_EFFICIENCY);
        double power = BlueprintMath.barrelPower(this.menu.barrels())
                * BlueprintMath.strengthPower(this.menu.strength());
        double mana = BlueprintMath.manaMultiplier(this.menu.barrels())
                * BlueprintMath.strengthMana(this.menu.strength());
        int cooldown = BlueprintMath.barrelCooldownTicks(
                (int) Math.round(12.0D * BlueprintMath.strengthCooldown(this.menu.strength())),
                this.menu.barrels());

        graphics.drawString(this.font, "tier " + tier + "   efficiency " + efficiency + "%",
                x, y, ArcanePanel.TEXT, false);
        graphics.drawString(this.font, String.format("power x%.2f   mana x%.1f   %d t/shot",
                        power, mana, cooldown), x, y + 10, ArcanePanel.MANA_LIGHT, false);
    }
}
