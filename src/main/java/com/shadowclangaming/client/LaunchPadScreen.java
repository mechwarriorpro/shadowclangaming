package com.shadowclangaming.client;

import com.shadowclangaming.block.LaunchPadMenu;
import com.shadowclangaming.client.gui.ArcanePanel;
import com.shadowclangaming.client.gui.ConsoleLayout;
import com.shadowclangaming.client.gui.GemButton;
import com.shadowclangaming.computer.Channels;
import com.shadowclangaming.network.ClientConsoleQueue;
import com.shadowclangaming.network.ScgNet;
import com.shadowclangaming.warhead.MissilePayload;
import com.shadowclangaming.warhead.WarheadType;
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
 * The launch pad screen: a silo control board.
 *
 * Two columns. Left: what is loaded and what shape the silo is in. Right: the action buttons and a settings
 * block where the player types exact numbers - CHANNEL, SPLIT ALTITUDE and the target's X/Y/Z - instead of
 * having to remember console verbs. Bottom: the console, which still does everything the fields do.
 *
 * Sizes and positions come from {@link ConsoleLayout}, so the window is 320x180 rather than 350x214: small
 * enough for a 1366x768 client at GUI scale 4, and every box is proven not to overlap another by
 * {@code LogicHarness}.
 */
public class LaunchPadScreen extends AbstractContainerScreen<LaunchPadMenu> {

    private final List<String> console = new ArrayList<>();
    private EditBox input;
    /** Exact-radius entry: the typed half of "use a slider ... also set exact radius". */
    private EditBox radiusInput;
    /** The radius widget. It draws and handles itself; this field is only to keep it in sync. */
    private com.shadowclangaming.client.gui.RadiusSlider slider;

    // The custom-setting fields. Each one sends the same console verb a player would type, so the two paths
    // can never drift apart (and the server keeps the authority over what a value is allowed to be).
    private EditBox channelInput;
    private EditBox splitInput;
    private EditBox targetX;
    private EditBox targetY;
    private EditBox targetZ;

    public LaunchPadScreen(LaunchPadMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = ConsoleLayout.PAD_WIDTH;
        this.imageHeight = ConsoleLayout.PAD_HEIGHT;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        buildActionButtons();
        buildRadiusControls();
        buildSettingFields();

        ConsoleLayout.Rect log = ConsoleLayout.PAD_CONSOLE_INPUT;
        this.input = new EditBox(this.font, leftPos + log.x() + 4, topPos + log.y() + 2,
                log.w() - 10, 10, Component.literal("command"));
        this.input.setMaxLength(120);
        this.input.setBordered(false);
        this.input.setTextColor(ArcanePanel.TEXT);
        addRenderableWidget(this.input);
        drain();
        if (console.isEmpty()) {
            console.add("target <x> <y> <z>  /  split <y>  /  freq <n>  /  launch");
        }
    }

    private void buildActionButtons() {
        ConsoleLayout.Rect rail = ConsoleLayout.PAD_ACTIONS;
        String[] labels = {"FIRE", "Load FE", "Link panel", "Unload", "Clear cargo", "Board"};
        int[] ids = {LaunchPadMenu.BUTTON_LAUNCH, LaunchPadMenu.BUTTON_LOAD_FE,
                LaunchPadMenu.BUTTON_LINK_PANEL, LaunchPadMenu.BUTTON_UNLOAD,
                LaunchPadMenu.BUTTON_CLEAR_CARGO, LaunchPadMenu.BUTTON_BOARD};
        GemButton.Style[] styles = {GemButton.Style.DANGER, GemButton.Style.PLAIN, GemButton.Style.PLAIN,
                GemButton.Style.PLAIN, GemButton.Style.PLAIN, GemButton.Style.TOGGLE_OFF};
        ArcanePanel.Glyph[] glyphs = {ArcanePanel.Glyph.FLAME, ArcanePanel.Glyph.BOLT,
                ArcanePanel.Glyph.GEAR, ArcanePanel.Glyph.CHEVRON_DOWN, ArcanePanel.Glyph.CROSS,
                ArcanePanel.Glyph.CHEVRON_UP};
        for (int i = 0; i < labels.length; i++) {
            int column = i % 2;
            int row = i / 2;
            int x = leftPos + rail.x() + column * (ConsoleLayout.PAD_BUTTON_W + ConsoleLayout.PAD_BUTTON_GAP_X);
            int y = topPos + rail.y() + row * (ConsoleLayout.PAD_BUTTON_H + ConsoleLayout.PAD_BUTTON_GAP_Y);
            int id = ids[i];
            addRenderableWidget(new GemButton(x, y, ConsoleLayout.PAD_BUTTON_W, ConsoleLayout.PAD_BUTTON_H,
                    Component.literal(labels[i]), styles[i], () -> button(id)).glyph(glyphs[i]));
        }
    }

    /**
     * The radius slider and its exact-entry box.
     *
     * Only shown for warheads whose radius is set by the player rather than bought with currency
     * (Constructor, Spatial, cluster split patterns) - a Numismatic warhead's radius is a price, and
     * pretending a slider could set it would be a lie.
     */
    private void buildRadiusControls() {
        int warhead = menu.warhead();
        if (warhead < 0 || warhead >= WarheadType.values().length) {
            return;
        }
        WarheadType type = WarheadType.values()[warhead];
        if (!type.usesRadiusSlider()) {
            return;
        }
        ConsoleLayout.Rect track = ConsoleLayout.PAD_SLIDER;
        slider = new com.shadowclangaming.client.gui.RadiusSlider(
                leftPos + track.x(), topPos + track.y(), track.w(), track.h(),
                Math.max(0, menu.sliderRadius()), radius -> sendRadius(radius <= 0 ? -1 : radius));
        addRenderableWidget(slider);

        ConsoleLayout.Rect entry = ConsoleLayout.PAD_SLIDER_INPUT;
        radiusInput = new EditBox(font, leftPos + entry.x(), topPos + entry.y(), entry.w(), entry.h() - 1,
                Component.literal("radius"));
        radiusInput.setMaxLength(4);
        radiusInput.setValue(menu.sliderRadius() >= 0 ? String.valueOf(menu.sliderRadius()) : "");
        radiusInput.setHint(Component.literal("exact").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        radiusInput.setResponder(text -> {
            String digits = text.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                return;
            }
            int value = Math.min(com.shadowclangaming.client.gui.RadiusSlider.MAX_RADIUS,
                    Integer.parseInt(digits));
            if (value != menu.sliderRadius()) {
                sendRadius(value);
            }
        });
        addRenderableWidget(radiusInput);

        ConsoleLayout.Rect auto = ConsoleLayout.PAD_SLIDER_AUTO;
        addRenderableWidget(new GemButton(leftPos + auto.x(), topPos + auto.y(), auto.w(), auto.h() - 1,
                Component.literal("auto"), GemButton.Style.PLAIN, () -> {
                    radiusInput.setValue("");
                    sendRadius(-1);
                })).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Clear the override: derive the radius from the payload")));
    }

    /**
     * The three custom settings, as typed fields.
     *
     * They exist because a player asked to be able to set "custom frequency, coords and split altitude"
     * without learning the console. Each field writes through the console verb when the player presses
     * ENTER or leaves the field, so the server validates exactly the same way a typed command would.
     */
    private void buildSettingFields() {
        channelInput = field(ConsoleLayout.PAD_FIELD_CHANNEL, "channel",
                Channels.isSet(menu.frequency()) ? String.valueOf(menu.frequency()) : "", true);
        // Committed as the player types, but only when the number is complete and has actually changed:
        // a channel is a small integer, and waiting for ENTER made the field feel broken.
        channelInput.setResponder(text -> {
            String trimmed = text.trim();
            if (!trimmed.matches("-?\\d{1,7}")) {
                return;
            }
            int value = Channels.sanitise(Integer.parseInt(trimmed));
            if (value != menu.frequency()) {
                send("freq " + value);
            }
        });

        splitInput = field(ConsoleLayout.PAD_FIELD_SPLIT, "split y",
                menu.splitAltitude() == MissilePayload.NO_SPLIT_ALTITUDE ? ""
                        : String.valueOf(menu.splitAltitude()), true);
        splitInput.setResponder(text -> {
            String trimmed = text.trim();
            if (!trimmed.matches("-?\\d{1,6}")) {
                return;
            }
            int value = Integer.parseInt(trimmed);
            if (value != menu.splitAltitude()) {
                send("split " + value);
            }
        });

        BlockPos target = menu.target();
        targetX = field(ConsoleLayout.PAD_FIELD_X, "target x",
                target == null ? "" : String.valueOf(target.getX()), true);
        targetY = field(ConsoleLayout.PAD_FIELD_Y, "target y",
                target == null ? "" : String.valueOf(target.getY()), true);
        targetZ = field(ConsoleLayout.PAD_FIELD_Z, "target z",
                target == null ? "" : String.valueOf(target.getZ()), true);

        ConsoleLayout.Rect apply = ConsoleLayout.PAD_FIELD_TARGET_APPLY;
        addRenderableWidget(new GemButton(leftPos + apply.x(), topPos + apply.y(), apply.w(),
                apply.h() - 1, Component.literal("set target"), GemButton.Style.PLAIN,
                this::sendTarget).glyph(ArcanePanel.Glyph.STAR)).setTooltip(
                net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal("Apply the X / Y / Z fields as the aim point")));
    }

    /** One labelled numeric field. Empty means "leave it alone"; the server reports what it did. */
    private EditBox field(ConsoleLayout.Rect where, String hint, String initial, boolean signed) {
        EditBox box = new EditBox(font, leftPos + where.x(), topPos + where.y(), where.w(), where.h() - 1,
                Component.literal(hint));
        box.setMaxLength(9);
        box.setValue(initial);
        box.setHint(Component.literal(hint).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        box.setFilter(text -> text.isEmpty() || text.matches(signed ? "-?\\d{0,7}" : "\\d{0,7}"));
        addRenderableWidget(box);
        return box;
    }

    /** Send the three coordinate fields as one console line. */
    private void sendTarget() {
        String x = targetX.getValue().trim();
        String y = targetY.getValue().trim();
        String z = targetZ.getValue().trim();
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            console.add("! set all three of x, y and z first");
            trim();
            return;
        }
        send("target " + x + " " + y + " " + z);
    }

    private void send(String line) {
        submit(line);
    }

    /** Tell the server what the slider says. */
    private void sendRadius(int radius) {
        PacketDistributor.sendToServer(new ScgNet.PadRadiusPayload(menu.pos(), radius));
    }

    /** The gauge's fill, using the slider's own log mapping so the two can never disagree. */
    private double sliderFraction(int radius) {
        return com.shadowclangaming.client.gui.RadiusSlider.fractionOf(radius);
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
        while (console.size() > 8) {
            console.remove(0);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ENTER commits the field being typed in, rather than firing whatever the command box happens to
        // hold - otherwise typing a channel and pressing enter would submit the stale console line.
        if (keyCode == 257 || keyCode == 335) {
            if (this.getFocused() instanceof EditBox box && box != this.input) {
                if (box == channelInput) {
                    send("freq " + box.getValue().trim());
                } else if (box == splitInput) {
                    send("split " + (box.getValue().isBlank() ? "auto" : box.getValue().trim()));
                } else if (box == targetX || box == targetY || box == targetZ) {
                    sendTarget();
                }
                return true;
            }
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
        renderBackground(graphics, mouseX, mouseY, partialTick);
        ArcanePanel.window(graphics, leftPos, topPos, imageWidth, imageHeight);

        ArcanePanel.heading(font, graphics, Component.literal("launch control"), leftPos + 6,
                topPos + 3, ArcanePanel.TEXT);
        String tier = "tier " + menu.tier();
        ArcanePanel.heading(font, graphics, Component.literal(tier),
                leftPos + imageWidth - 6 - font.width(tier), topPos + 3, ArcanePanel.GOLD);

        if (slider != null) {
            int wanted = Math.max(0, menu.sliderRadius());
            if (slider.radius() != wanted) {
                slider.setRadius(wanted);
            }
        }
        drawWarheadColumn(graphics);
        drawSiloColumn(graphics);
        drawSettings(graphics);
        drawConsole(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // window drawn in render()
    }

    private void drawWarheadColumn(GuiGraphics graphics) {
        ConsoleLayout.Rect box = ConsoleLayout.PAD_WARHEAD;
        int x = leftPos + box.x();
        int y = topPos + box.y();
        int w = box.w();

        ArcanePanel.inset(graphics, x, y, w, box.h());
        ArcanePanel.heading(font, graphics, Component.literal("warhead"), x + 4, y + 4, ArcanePanel.ACCENT);

        int warhead = menu.warhead();
        if (warhead < 0) {
            graphics.drawString(font, "no missile loaded", x + 4, y + 18, ArcanePanel.TEXT_FAINT, false);
            graphics.drawString(font, "right-click the pad", x + 4, y + 30, ArcanePanel.TEXT_FAINT, false);
            graphics.drawString(font, "with a missile", x + 4, y + 40, ArcanePanel.TEXT_FAINT, false);
            return;
        }
        WarheadType type = WarheadType.values()[Math.floorMod(warhead, WarheadType.values().length)];
        int colour = MissileRenderer.tintFor(warhead);
        graphics.fill(x + 3, y + 16, x + 5, y + 54, colour);

        graphics.drawString(font, ArcanePanel.fit(font, type.id().toUpperCase(java.util.Locale.ROOT), w - 12),
                x + 8, y + 16, colour, false);
        graphics.drawString(font, "power " + type.basePower(), x + 8, y + 26, ArcanePanel.TEXT_DIM, false);

        long funding = menu.funding();
        graphics.drawString(font, "funding " + String.format("%,d", funding),
                x + 8, y + 36, ArcanePanel.GOLD, false);

        int radius = menu.radius();
        graphics.drawString(font, "radius", x + 8, y + 46, ArcanePanel.TEXT_DIM, false);
        String radiusText = String.valueOf(radius);
        graphics.drawString(font, radiusText, x + w - 6 - font.width(radiusText), y + 46,
                radius >= 4096 ? 0xFFA83400 : ArcanePanel.TEXT, false);

        int sliderRadius = menu.sliderRadius();
        boolean hasSlider = type.usesRadiusSlider();
        int shown = hasSlider && sliderRadius >= 0 ? sliderRadius : radius;
        if (!hasSlider) {
            // A radius gauge: log10 scale, because 1 -> 4096 linearly is a bar that never moves.
            ArcanePanel.bar(graphics, x + 8, y + 58, w - 16, 5, sliderFraction(shown),
                    shown >= 4096 ? 0xFFA83400 : ArcanePanel.MANA, ArcanePanel.INSET);
        }
    }

    private void drawSiloColumn(GuiGraphics graphics) {
        ConsoleLayout.Rect box = ConsoleLayout.PAD_SILO;
        int x = leftPos + box.x();
        int y = topPos + box.y();
        int w = box.w();

        ArcanePanel.inset(graphics, x, y, w, box.h());
        ArcanePanel.heading(font, graphics, Component.literal("silo"), x + 4, y + 4, ArcanePanel.ACCENT);

        long energy = menu.energy();
        ArcanePanel.bar(graphics, x + 4, y + 16, w - 8, 5, energy / 4_000_000.0D,
                ArcanePanel.ENERGY, ArcanePanel.INSET);
        graphics.drawString(font, String.format("%,d FE", energy), x + 4, y + 22, ArcanePanel.TEXT_DIM, false);

        int cooldown = menu.cooldown();
        stat(graphics, x, w, y + 32, "cargo " + menu.cargoSlots() + " / feed " + menu.feedSlots(),
                cooldown > 0 ? cooldown / 20 + "s" : "ready");
        stat(graphics, x, w, y + 41, "panel", menu.hasPanel() ? "linked" : "none");
    }

    /** The settings block: headings plus the live values behind each field. */
    private void drawSettings(GuiGraphics graphics) {
        ConsoleLayout.Rect box = ConsoleLayout.PAD_SETTINGS;
        int x = leftPos + box.x();
        int y = topPos + box.y();
        ArcanePanel.inset(graphics, x, y, box.w(), box.h());
        ArcanePanel.heading(font, graphics, Component.literal("settings"), x + 4, y + 4, ArcanePanel.ACCENT);

        // The readout strip under the fields: what the server currently believes.
        BlockPos target = menu.target();
        String aim = target == null ? "target none"
                : "target " + target.getX() + " " + target.getY() + " " + target.getZ();
        graphics.drawString(font, ArcanePanel.fit(font, aim, box.w() - 8), x + 4, y + 60,
                target == null ? ArcanePanel.TEXT_FAINT : ArcanePanel.TEXT, false);

    }

    private void stat(GuiGraphics graphics, int x, int w, int y, String label, String value) {
        graphics.drawString(font, ArcanePanel.fit(font, label, w - 12), x + 4, y, ArcanePanel.TEXT_FAINT, false);
        int valueWidth = font.width(value);
        graphics.drawString(font, value, x + w - 6 - valueWidth, y, ArcanePanel.TEXT, false);
    }

    /** The last few console lines, where the player can actually see the server's answers. */
    private void drawConsole(GuiGraphics graphics) {
        ConsoleLayout.Rect log = ConsoleLayout.PAD_CONSOLE_LOG;
        int x = leftPos + log.x();
        int y = topPos + log.y();
        ArcanePanel.inset(graphics, x, y, log.w(), log.h());
        int linesFit = 2;
        int from = Math.max(0, console.size() - linesFit);
        int ty = y + 2;
        for (int i = from; i < console.size(); i++) {
            String line = console.get(i);
            int colour = line.startsWith("> ") ? ArcanePanel.ACCENT_2
                    : line.startsWith("!") ? ArcanePanel.BAD : ArcanePanel.TEXT_DIM;
            graphics.drawString(font, ArcanePanel.fit(font, line, log.w() - 8), x + 4, ty, colour, false);
            ty += 9;
        }
        ConsoleLayout.Rect inputBox = ConsoleLayout.PAD_CONSOLE_INPUT;
        ArcanePanel.inset(graphics, leftPos + inputBox.x(), topPos + inputBox.y(),
                inputBox.w(), inputBox.h());
        graphics.drawString(font, ">", leftPos + inputBox.x() + 2, topPos + inputBox.y() + 2,
                ArcanePanel.ACCENT_2, false);
    }
}
