package com.shadowclangaming.client;

import com.shadowclangaming.client.gui.ArcanePanel;
import com.shadowclangaming.warhead.CargoMissileMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The missile's hold - and, for a constructor, its radius.
 *
 * Deliberately a chest, not a control board: 3x9 slots, the player's inventory underneath, and a line of
 * text saying how much of the hold is in use. The point of the change was to stop the offhand being the
 * loading mechanism, so the screen should not ask the player to learn anything new.
 *
 * THE CONSTRUCTOR ADDS ONE THING. A constructor warhead's sphere size was previously only settable on the
 * launch pad, which meant walking back to the launcher every time you wanted a different hole in the
 * ground. Since the missile is already in your hand and already open, the slider lives here too: same log
 * scale and same 4096 ceiling as the pad's, because two sliders that disagree about what "half way" means
 * would be worse than no slider.
 *
 * The slider is a stream of clicks, so it rides the container button channel (button id = radius) rather
 * than inventing a packet per frame. Typing an exact number is still possible - "auto" clears the
 * override and lets the payload decide.
 */
public class CargoMissileScreen extends AbstractContainerScreen<CargoMissileMenu> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 168;
    private static final int HOLD_TOP = 18;
    private static final int HOLD_SIZE = CargoMissileMenu.ROWS * CargoMissileMenu.COLUMNS;

    /** Extra panel height when the radius row is drawn under the inventory. */
    private static final int SLIDER_PANEL = 52;

    private static final int SLIDER_WIDTH = 130;

    private EditBox radiusInput;
    private com.shadowclangaming.client.gui.RadiusSlider slider;

    public CargoMissileScreen(CargoMissileMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = menu.isConstructor() ? HEIGHT + SLIDER_PANEL : HEIGHT;
        this.inventoryLabelY = HEIGHT - 94;
    }

    @Override
    protected void init() {
        super.init();
        if (!menu.isConstructor()) {
            return;
        }
        int sliderX = leftPos + 19;
        int sliderY = topPos + HEIGHT + 8;

        slider = new com.shadowclangaming.client.gui.RadiusSlider(
                sliderX, sliderY, SLIDER_WIDTH, 10, Math.max(0, menu.radius()),
                radius -> sendRadius(radius <= 0 ? 0 : radius));
        addRenderableWidget(slider);

        radiusInput = new EditBox(font, sliderX, sliderY + 14, 62, 12, Component.literal("radius"));
        radiusInput.setMaxLength(4);
        int current = menu.radius();
        radiusInput.setValue(current >= 0 ? String.valueOf(current) : "");
        radiusInput.setHint(Component.literal("auto").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        radiusInput.setResponder(text -> {
            // Only complete, sane numbers are sent; anything else waits for the player to stop typing.
            String digits = text.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                return;
            }
            int value = Math.min(CargoMissileMenu.MAX_RADIUS, Integer.parseInt(digits));
            if (value != menu.radius()) {
                sendRadius(value);
            }
        });
        addRenderableWidget(radiusInput);

        addRenderableWidget(new com.shadowclangaming.client.gui.GemButton(
                sliderX + 66, sliderY + 14, 34, 12,
                Component.literal("auto"), com.shadowclangaming.client.gui.GemButton.Style.PLAIN, () -> {
                    radiusInput.setValue("");
                    sendRadius(0);
                }));
    }

    /** The slider and the exact-entry box both go through the menu's button channel. */
    private void sendRadius(int radius) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, radius);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Mirror the server's value into the widgets: the missile in hand is not a slot, so the data
        // slot is the only thing that can tell this screen what the radius really is.
        if (slider != null) {
            int radius = Math.max(0, menu.radius());
            if (slider.radius() != radius) {
                slider.setRadius(radius);
            }
        }
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // The frame: a panel for the hold, an inset per slot, and the player's inventory below.
        ArcanePanel.inset(graphics, x, y, imageWidth, imageHeight);
        ArcanePanel.heading(font, graphics,
                Component.literal(menu.isConstructor() ? "constructor payload" : "cargo hold"),
                x + 8, y + 6, ArcanePanel.ACCENT);

        for (int row = 0; row < CargoMissileMenu.ROWS; row++) {
            for (int column = 0; column < CargoMissileMenu.COLUMNS; column++) {
                int slotX = x + 7 + column * 18;
                int slotY = y + HOLD_TOP - 1 + row * 18;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, ArcanePanel.INSET);
            }
        }

        int inventoryTop = y + HOLD_TOP + HOLD_SIZE / CargoMissileMenu.COLUMNS * 18 + 14;
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

        // The slider draws itself; the panel just labels the row.
        if (menu.isConstructor()) {
            graphics.fill(leftPos + 17, topPos + HEIGHT + 6, leftPos + 17 + SLIDER_WIDTH + 4,
                    topPos + HEIGHT + 6 + 14, ArcanePanel.INSET);
        }
    }

    /** Sends only when the number actually changed: a drag would otherwise flood the channel. */
    private void applyRadius(int radius) {
        if (radius == menu.radius()) {
            return;
        }
        sendRadius(radius);
        if (radiusInput != null) {
            radiusInput.setValue(radius <= 0 ? "" : String.valueOf(radius));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Right-aligned usage counter, which is the one number worth knowing about a hold.
        String used = menu.usedSlots() + " / " + HOLD_SIZE;
        graphics.drawString(font, used, imageWidth - 8 - font.width(used), 6,
                ArcanePanel.TEXT_DIM, false);
        graphics.drawString(font, "right-click the missile to edit its hold",
                8, HEIGHT - 92, ArcanePanel.TEXT_FAINT, false);

        if (!menu.isConstructor()) {
            return;
        }
        int radius = menu.radius();
        String line = radius >= 0
                ? "radius " + radius + " blocks"
                : "radius: from payload";
        graphics.drawString(font, line, 19, HEIGHT + 30, ArcanePanel.ACCENT, false);

        // What that radius actually means: the sphere the placer will fill, and the stacks it needs.
        long blocks = radius >= 0 ? blocksInSphere(radius) : 0L;
        if (blocks > 0L) {
            String detail = String.format("%,d blocks (%s stacks)", blocks,
                    String.format("%,d", (blocks + 63L) / 64L));
            graphics.drawString(font, detail, 19, HEIGHT + 40, ArcanePanel.TEXT_DIM, false);
        }
    }

    /** Blocks in a sphere of this radius - the shared constructor geometry, not a second copy of it. */
    public static long blocksInSphere(int radius) {
        return com.shadowclangaming.warhead.ConstructorBlast.blocksInSphereLong(radius);
    }
}
