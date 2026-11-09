package com.shadowclangaming.client;

import com.shadowclangaming.block.TerminalMenu;
import com.shadowclangaming.client.gui.ArcanePanel;
import com.shadowclangaming.client.gui.GemButton;
import com.shadowclangaming.computer.TerminalModule;
import com.shadowclangaming.network.ClientConsoleQueue;
import com.shadowclangaming.network.ScgNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The terminal screen: a CRT in a brass frame.
 *
 * ──────────────────── HOW THE TEXT GETS HERE ────────────────────
 *
 * The terminal's transcript lives on the server (players can walk up to it, command blocks can drive it,
 * an orbital node can run subcommands on it). It is free-form text, which menus do not carry, so it comes
 * over the payload channel as {@code TextPayload} lines and lands in {@link ClientConsoleQueue}. This
 * screen drains that queue once per frame, appends to its local transcript, and echoes what you type
 * optimistically so the prompt feels instant.
 *
 * Typing sends a {@code CommandPayload}; the server re-resolves the block entity, checks you are within
 * eight blocks, runs the line through the shell and replies with however many lines it wants to.
 *
 * The rail on the right is pure ContainerData (frequency, CPU, GPU, cards) so it updates whenever the
 * server changes something - install a card with a right-click and it appears here without reopening.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {

    private static final int WIDTH = 372;
    private static final int HEIGHT = 224;
    private static final int MAX_LINES = 400;
    private static final int LINE_H = 9;

    private final List<String> transcript = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private int historyCursor;
    private EditBox input;
    private int blink;

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        int screenX = leftPos + 8;
        int screenY = topPos + ArcanePanel.RUNE_BAND + 6;
        int screenW = crtWidth();
        int screenH = crtHeight();

        int promptY = screenY + screenH + 4;
        this.input = new EditBox(this.font, screenX + 12, promptY, screenW - 18, 12,
                Component.literal("command"));
        this.input.setMaxLength(200);
        this.input.setBordered(false);
        this.input.setTextColor(0xFFB6F0C8);
        this.input.setResponder(text -> {
        });
        addRenderableWidget(this.input);
        setInitialFocus(this.input);

        buildRail();
        // The server pushes the backlog when the menu opens; drain it once so the screen is never blank.
        drainQueue();
        if (transcript.isEmpty()) {
            transcript.add("shadowclangaming terminal " + menu.frequency());
            transcript.add("type 'help' for the command list");
        }
    }

    private int crtWidth() {
        return imageWidth - 8 - 100 - 6;
    }

    private int crtHeight() {
        return HEIGHT - ArcanePanel.RUNE_BAND - 6 - 26;
    }

    private void buildRail() {
        int railX = leftPos + imageWidth - 104;
        int railY = topPos + ArcanePanel.RUNE_BAND + 8;
        String[][] quick = {
                {"help", "Help"},
                {"hw", "Hardware"},
                {"radar", "Radar"},
                {"prog list", "Programs"},
                {"panel status", "Panel"},
                {"clear", "Clear"},
        };
        for (int i = 0; i < quick.length; i++) {
            String command = quick[i][0];
            GemButton button = new GemButton(railX, railY + i * 17, 96, 15,
                    Component.literal(quick[i][1]),
                    i == 0 ? GemButton.Style.PRIMARY : GemButton.Style.PLAIN,
                    () -> submit(command));
            addRenderableWidget(button);
        }
    }

    // ------------------------------------------------------------------ console plumbing

    private void submit(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        transcript.add("> " + line);
        if (history.isEmpty() || !history.get(history.size() - 1).equals(line)) {
            history.add(line);
        }
        historyCursor = history.size();
        trim();
        PacketDistributor.sendToServer(new ScgNet.CommandPayload(menu.pos(), line));
    }

    private void drainQueue() {
        List<String> incoming = ClientConsoleQueue.drain(menu.pos());
        if (incoming.isEmpty()) {
            return;
        }
        transcript.addAll(incoming);
        trim();
    }

    private void trim() {
        while (transcript.size() > MAX_LINES) {
            transcript.remove(0);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // enter / keypad enter
            String line = this.input.getValue();
            this.input.setValue("");
            submit(line);
            return true;
        }
        if (keyCode == 265) { // up
            if (!history.isEmpty()) {
                historyCursor = Math.max(0, historyCursor - 1);
                this.input.setValue(history.get(Math.min(historyCursor, history.size() - 1)));
            }
            return true;
        }
        if (keyCode == 264) { // down
            if (!history.isEmpty()) {
                historyCursor = Math.min(history.size(), historyCursor + 1);
                this.input.setValue(historyCursor >= history.size() ? "" : history.get(historyCursor));
            }
            return true;
        }
        if (keyCode == 258) { // tab: complete a command name from the ones we know
            String typed = this.input.getValue();
            String match = complete(typed);
            if (match != null) {
                this.input.setValue(match);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static String complete(String typed) {
        if (typed == null || typed.isEmpty()) {
            return null;
        }
        String[] verbs = {"help", "hw", "cpu", "gpu", "rack", "node", "radar", "cannon", "prog", "panel",
                "wire", "lua", "clear", "status"};
        for (String verb : verbs) {
            if (verb.startsWith(typed)) {
                return verb;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Clicking anywhere in the CRT focuses the prompt, like any terminal.
        if (mouseX >= leftPos + 8 && mouseX <= leftPos + 8 + crtWidth()
                && mouseY >= topPos + ArcanePanel.RUNE_BAND && mouseY <= topPos + imageHeight - 20) {
            setFocused(this.input);
            return super.mouseClicked(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drainQueue();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        ArcanePanel.window(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawCrt(graphics);
        drawPrompt(graphics);
        drawRail(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // window drawn in render()
    }

    private void drawCrt(GuiGraphics graphics) {
        int x = leftPos + 8;
        int y = topPos + ArcanePanel.RUNE_BAND + 6;
        int w = crtWidth();
        int h = crtHeight();

        // bezel
        graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF0B0910);
        graphics.fill(x, y, x + w, y + h, 0xFF07110C);

        // scanlines + a slow bright band, so it reads as a phosphor display rather than a text box
        for (int sy = y; sy < y + h; sy += 3) {
            graphics.fill(x, sy, x + w, sy + 1, 0x1800FF88);
        }
        int band = (int) ((System.currentTimeMillis() / 12) % (h + 40)) - 20;
        graphics.fill(x, y + band, x + w, y + Math.min(h, band + 14), 0x10AAFFCC);

        int linesFit = (h - 6) / LINE_H;
        int from = Math.max(0, transcript.size() - linesFit);
        int ty = y + 3;
        for (int i = from; i < transcript.size(); i++) {
            String line = transcript.get(i);
            int colour = colourFor(line);
            graphics.drawString(font, ArcanePanel.fit(font, line, w - 8), x + 4, ty, colour, false);
            ty += LINE_H;
        }
    }

    private static int colourFor(String line) {
        if (line.startsWith("> ")) {
            return 0xFF7FE3F0;
        }
        if (line.startsWith("!")) {
            return 0xFFFF8A8A;
        }
        if (line.startsWith("--")) {
            return 0xFF8FFF9E;
        }
        return 0xFFB6F0C8;
    }

    private void drawPrompt(GuiGraphics graphics) {
        int x = leftPos + 8;
        int y = topPos + ArcanePanel.RUNE_BAND + 6 + crtHeight() + 4;
        graphics.fill(x, y - 1, x + crtWidth(), y + 13, 0xFF0B1410);
        graphics.drawString(font, ">", x + 3, y + 2, 0xFF7FE3F0, false);
        // a blinking block cursor after the text, when the box is empty
        blink++;
        if (input != null && input.getValue().isEmpty() && (blink / 20) % 2 == 0) {
            graphics.fill(x + 12, y + 1, x + 18, y + 11, 0xFF7FE3F0);
        }
        if (input != null) {
            input.render(graphics, 0, 0, 0);
        }
    }

    private void drawRail(GuiGraphics graphics) {
        int x = leftPos + imageWidth - 104;
        int y = topPos + ArcanePanel.RUNE_BAND + 8;

        ArcanePanel.heading(font, graphics, Component.literal("status"), x, y - 1, ArcanePanel.GOLD);
        y += 12;
        stat(graphics, x, y, "channel", String.valueOf(menu.frequency()));
        stat(graphics, x, y + 10, "cpu", menu.cpu().id());
        stat(graphics, x, y + 20, "gpu", menu.gpu().id());
        stat(graphics, x, y + 30, "out", menu.outputSummary());
        stat(graphics, x, y + 40, "auto", menu.autoEngage() ? "on" : "off");
        y += 58;

        // card list
        ArcanePanel.inset(graphics, x, y, 96, 58);
        graphics.drawString(font, "CARDS", x + 4, y + 3, ArcanePanel.TEXT_FAINT, false);
        int line = 0;
        for (TerminalModule module : TerminalModule.values()) {
            if (!menu.hasModule(module)) {
                continue;
            }
            if (line >= 5) {
                graphics.drawString(font, "+more", x + 4, y + 14 + line * 9, ArcanePanel.TEXT_FAINT, false);
                break;
            }
            graphics.fill(x + 4, y + 15 + line * 9, x + 6, y + 21 + line * 9, ArcanePanel.GOOD);
            graphics.drawString(font, ArcanePanel.fit(font, module.id(), 84),
                    x + 9, y + 14 + line * 9, ArcanePanel.TEXT, false);
            line++;
        }
        if (line == 0) {
            graphics.drawString(font, "none installed", x + 4, y + 14, ArcanePanel.TEXT_FAINT, false);
        }
    }

    private void stat(GuiGraphics graphics, int x, int y, String label, String value) {
        graphics.drawString(font, label, x, y, ArcanePanel.TEXT_FAINT, false);
        int width = font.width(value);
        graphics.drawString(font, value, x + 96 - width, y, ArcanePanel.TEXT, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
