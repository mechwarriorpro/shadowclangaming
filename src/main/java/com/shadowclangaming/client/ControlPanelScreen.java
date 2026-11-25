package com.shadowclangaming.client;

import com.shadowclangaming.block.ControlPanelMenu;
import com.shadowclangaming.client.gui.ArcanePanel;
import com.shadowclangaming.client.gui.GemButton;
import com.shadowclangaming.launcher.LauncherModule;
import com.shadowclangaming.network.ClientConsoleQueue;
import com.shadowclangaming.network.ScgNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The control panel screen: the launcher's cockpit.
 *
 * Top row is the arm switch - a big, unmistakable toggle, because "is this thing armed" is the one
 * question a launch officer asks constantly. Below it: the target readout, the linked pad, the module
 * rack and the tier ladder. The bottom strip is the same command line the terminal uses, so
 * "target 400 90 -1200", "link" and "launch" all work here without a button.
 */
public class ControlPanelScreen extends AbstractContainerScreen<ControlPanelMenu> {

    private static final int WIDTH = com.shadowclangaming.client.gui.ConsoleLayout.PANEL_WIDTH;
    private static final int HEIGHT = com.shadowclangaming.client.gui.ConsoleLayout.PANEL_HEIGHT;

    private final List<String> console = new ArrayList<>();
    private EditBox input;
    private GemButton armButton;

    public ControlPanelScreen(ControlPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        // Every position comes from ConsoleLayout.PANEL_*, which the harness proves is inside the window
        // and free of overlaps. The window is 300x168: the old 360x210 did not fit a 1280x720 client.
        var arm = com.shadowclangaming.client.gui.ConsoleLayout.PANEL_ARM;
        var fire = com.shadowclangaming.client.gui.ConsoleLayout.PANEL_FIRE;
        var tier = com.shadowclangaming.client.gui.ConsoleLayout.PANEL_TIER;

        this.armButton = new GemButton(leftPos + arm.x(), topPos + arm.y(), arm.w(), arm.h(),
                Component.empty(),
                menu.armed() ? GemButton.Style.TOGGLE_ON : GemButton.Style.TOGGLE_OFF,
                () -> button(ControlPanelMenu.BUTTON_TOGGLE_ARMED));
        armButton.glyph(ArcanePanel.Glyph.SHIELD);
        addRenderableWidget(armButton);

        addRenderableWidget(new GemButton(leftPos + fire.x(), topPos + fire.y(), fire.w(), fire.h(),
                Component.literal("FIRE"), GemButton.Style.DANGER,
                () -> button(ControlPanelMenu.BUTTON_LAUNCH))
                .glyph(ArcanePanel.Glyph.FLAME));

        // Target + link live in the tier box's row: two small buttons instead of a second rail.
        addRenderableWidget(new GemButton(leftPos + tier.x(), topPos + tier.y() + 16, 56, 14,
                Component.literal("Set target"), GemButton.Style.PLAIN,
                () -> button(ControlPanelMenu.BUTTON_TARGET_LOOK))
                .glyph(ArcanePanel.Glyph.STAR));
        addRenderableWidget(new GemButton(leftPos + tier.x() + 60, topPos + tier.y() + 16, 56, 14,
                Component.literal("Link pad"), GemButton.Style.PLAIN,
                () -> button(ControlPanelMenu.BUTTON_LINK_PAD))
                .glyph(ArcanePanel.Glyph.GEAR));

        var inputBox = com.shadowclangaming.client.gui.ConsoleLayout.PANEL_CONSOLE_INPUT;
        this.input = new EditBox(this.font, leftPos + inputBox.x() + 4, topPos + inputBox.y() + 1,
                inputBox.w() - 8, 9, Component.literal("command"));
        this.input.setMaxLength(120);
        this.input.setBordered(false);
        this.input.setTextColor(ArcanePanel.TEXT);
        addRenderableWidget(this.input);

        drain();
        if (console.isEmpty()) {
            console.add("target <x> <y> <z>  /  arm  /  launch  /  status");
        }
    }

    private void button(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void submit(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        console.add("> " + line);
        trim();
        PacketDistributor.sendToServer(new ScgNet.CommandPayload(menu.pos(), line));
    }

    private void drain() {
        List<String> incoming = ClientConsoleQueue.drain(menu.pos());
        if (!incoming.isEmpty()) {
            console.addAll(incoming);
            trim();
        }
    }

    private void trim() {
        while (console.size() > 6) {
            console.remove(0);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            String line = this.input.getValue();
            this.input.setValue("");
            submit(line);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drain();
        // the arm switch has to say what it is, not just look different
        this.armButton.setMessage(Component.literal(menu.armed() ? "ARMED" : "DISARMED"));
        this.armButton.setStyle(menu.armed() ? GemButton.Style.TOGGLE_ON : GemButton.Style.TOGGLE_OFF);

        renderBackground(graphics, mouseX, mouseY, partialTick);
        ArcanePanel.window(graphics, leftPos, topPos, imageWidth, imageHeight);
        ArcanePanel.heading(font, graphics, Component.literal("control panel"), leftPos + 6,
                topPos + 3, ArcanePanel.TEXT);
        String tierText = "tier " + menu.tier() + " / " + menu.maxTier();
        ArcanePanel.heading(font, graphics, Component.literal(tierText),
                leftPos + imageWidth - 6 - font.width(tierText), topPos + 3, ArcanePanel.GOLD);

        drawTargetPanel(graphics);
        drawModuleRack(graphics);
        drawTierLadder(graphics);
        drawPadPanel(graphics);
        drawConsole(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // window drawn in render()
    }

    private void drawTargetPanel(GuiGraphics graphics) {
        var box = com.shadowclangaming.client.gui.ConsoleLayout.PANEL_TARGET;
        int x = leftPos + box.x();
        int y = topPos + box.y();
        int w = box.w();
        ArcanePanel.inset(graphics, x, y, w, box.h());
        ArcanePanel.heading(font, graphics, Component.literal("target"), x + 4, y + 4, ArcanePanel.ACCENT);

        BlockPos target = menu.target();
        if (target == null) {
            graphics.drawString(font, "no target set", x + 4, y + 18, ArcanePanel.TEXT_FAINT, false);
            graphics.drawString(font, ArcanePanel.fit(font,
                            "or type: target x y z", w - 8), x + 4, y + 30, ArcanePanel.TEXT_FAINT, false);
        } else {
            graphics.drawString(font, "x " + target.getX(), x + 4, y + 17, ArcanePanel.TEXT, false);
            graphics.drawString(font, "y " + target.getY(), x + 4, y + 28, ArcanePanel.TEXT, false);
            graphics.drawString(font, "z " + target.getZ(), x + 4, y + 39, ArcanePanel.TEXT, false);
            if (menu.interdimensional()) {
                graphics.drawString(font, "INTERDIMENSIONAL", x + w - 6 - font.width("INTERDIMENSIONAL"),
                        y + 17, 0xFFA83400, false);
            }
        }

        BlockPos pad = menu.linkedPad();
        int py = y + 50;
        graphics.drawString(font, "pad", x + 4, py, ArcanePanel.TEXT_FAINT, false);
        graphics.drawString(font, pad == null ? "not linked"
                        : pad.getX() + " " + pad.getY() + " " + pad.getZ(),
                x + 30, py, pad == null ? ArcanePanel.TEXT_FAINT : ArcanePanel.TEXT, false);
    }

    private void drawModuleRack(GuiGraphics graphics) {
        var box = com.shadowclangaming.client.gui.ConsoleLayout.PANEL_MODULES;
        int x = leftPos + box.x();
        int y = topPos + box.y();
        int w = box.w();
        ArcanePanel.inset(graphics, x, y, w, box.h());
        ArcanePanel.heading(font, graphics, Component.literal("modules"), x + 4, y + 4, ArcanePanel.ACCENT);
        int line = 0;
        for (LauncherModule module : LauncherModule.values()) {
            int row = line % 2;
            int column = line / 2;
            int mx = x + 6 + column * (w / 2 - 4);
            int my = y + 17 + row * 10;
            boolean installed = menu.modules().contains(module);
            graphics.fill(mx, my + 1, mx + 2, my + 7,
                    installed ? ArcanePanel.GOOD : 0xFFB8A47B);
            graphics.drawString(font, ArcanePanel.fit(font, module.id(), w / 2 - 12), mx + 5, my,
                    installed ? ArcanePanel.TEXT : ArcanePanel.TEXT_FAINT, false);
            line++;
        }
        // LauncherModule is a short list, so "none installed" can only happen if the enum is empty - which
        // is not worth a branch, and the dead helper that used to test it is gone.
    }

    private void drawTierLadder(GuiGraphics graphics) {
        var box = com.shadowclangaming.client.gui.ConsoleLayout.PANEL_TIER;
        int x = leftPos + box.x();
        int y = topPos + box.y();
        int w = box.w();
        ArcanePanel.inset(graphics, x, y, w, box.h());
        ArcanePanel.heading(font, graphics, Component.literal("tier"), x + 4, y + 3, ArcanePanel.ACCENT);
        int max = menu.maxTier();
        int lit = menu.tier();
        int step = Math.max(2, (w - 12) / Math.max(1, max));
        for (int i = 0; i < max; i++) {
            boolean on = i < lit;
            graphics.fill(x + 5 + i * step, y + 13, x + 4 + i * step + step - 1, y + 17,
                    on ? ArcanePanel.GOLD : 0xFFB8A47B);
        }
    }

    /** The linked pad readout, so "which silo am I looking at" is answerable without the terminal. */
    private void drawPadPanel(GuiGraphics graphics) {
        var box = com.shadowclangaming.client.gui.ConsoleLayout.PANEL_PAD;
        int x = leftPos + box.x();
        int y = topPos + box.y();
        int w = box.w();
        ArcanePanel.inset(graphics, x, y, w, box.h());
        ArcanePanel.heading(font, graphics, Component.literal("pad"), x + 4, y + 3, ArcanePanel.ACCENT);
        BlockPos pad = menu.linkedPad();
        String text = pad == null ? "not linked"
                : "channel " + com.shadowclangaming.computer.Channels.describe(menu.padChannel());
        graphics.drawString(font, ArcanePanel.fit(font, text, w - 8), x + 4, y + 14,
                pad == null ? ArcanePanel.TEXT_FAINT : ArcanePanel.TEXT, false);
        graphics.drawString(font, pad == null ? "run: link"
                        : pad.getX() + " " + pad.getY() + " " + pad.getZ(), x + 4, y + 23,
                ArcanePanel.TEXT_FAINT, false);
    }

    private void drawConsole(GuiGraphics graphics) {
        var log = com.shadowclangaming.client.gui.ConsoleLayout.PANEL_CONSOLE_LOG;
        int x = leftPos + log.x();
        int y = topPos + log.y();
        ArcanePanel.inset(graphics, x, y, log.w(), log.h());
        // One line: the panel's window is deliberately small, and the terminal is where you read history.
        if (!console.isEmpty()) {
            String line = console.get(console.size() - 1);
            int colour = line.startsWith("> ") ? ArcanePanel.ACCENT_2
                    : line.startsWith("!") ? ArcanePanel.BAD : ArcanePanel.TEXT_DIM;
            graphics.drawString(font, ArcanePanel.fit(font, line, log.w() - 8), x + 4, y + 2, colour, false);
        }
        var inputBox = com.shadowclangaming.client.gui.ConsoleLayout.PANEL_CONSOLE_INPUT;
        ArcanePanel.inset(graphics, leftPos + inputBox.x(), topPos + inputBox.y(),
                inputBox.w(), inputBox.h());
        graphics.drawString(font, ">", leftPos + inputBox.x() + 2, topPos + inputBox.y() + 1,
                ArcanePanel.ACCENT_2, false);
    }

}
