package com.shadowclangaming.client;

import com.shadowclangaming.block.InscriptionMenu;
import com.shadowclangaming.client.gui.ArcanePanel;
import com.shadowclangaming.client.gui.GemButton;
import com.shadowclangaming.spell.Spell;
import com.shadowclangaming.spell.SpellComponent;
import com.shadowclangaming.spell.SpellModifier;
import com.shadowclangaming.spell.SpellShape;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * The inscription table, drawn the way the old Ars Magica 2 table was: ONE spell, written as a strip.
 *
 * ──────────────────── WHAT CHANGED ────────────────────
 *
 * There used to be a fixed run of "steps": you picked a shape, added effects under it, picked another
 * shape, up to eight. That is gone. A spell is a single flat list of PARTS in order:
 *
 *     [PR][ Lightning ][ Fire ][ZO][ Lightning ][ Random Teleport ][ AoE ][RU][ Heal ]
 *     shape ---- effects of the projectile ---- shape ---- effects of the zone ---- ...
 *
 * A shape part is a delivery; every part after it belongs to it until the next shape. There is no limit
 * on how many effects sit under a shape, and no eight-slot budget of shapes: drop as many parts, in any
 * order, as you like. Mana is the limit - the cost panel is pinned and always states the price and what
 * INSCRIBE would write onto.
 *
 * The left half is the palette, as in AM2: three bookmarks (SHAPE / COMPONENT / MODIFIER) over a grid of
 * glyph tiles. Click a tile to append it; drag it onto the strip to insert it at that exact position;
 * drag a chip along the strip to reorder; right-click a chip to delete it.
 */
public class InscriptionScreen extends AbstractContainerScreen<InscriptionMenu> {

    private static final int WIDTH = 372;
    private static final int HEIGHT = 339;

    // ---- geometry ----
    private static final int TABS_Y = 22;
    private static final int TAB_H = 13;
    private static final int GRID_TOP = 40;
    private static final int GRID_COLUMNS = 8;
    private static final int GRID_ROWS = 5;
    private static final int TILE = 16;
    private static final int TILE_GAP = 2;

    private static final int STRIP_TOP = 42;
    private static final int STRIP_COLUMNS = 9;
    private static final int STRIP_ROWS = 5;
    private static final int CHIP = 16;
    private static final int CHIP_GAP = 2;
    private static final int SEARCH_Y = 136;
    private static final int NAME_Y = 152;
    private static final int COST_TOP = 176;

    private static final int TAB_SHAPE = 0;
    private static final int TAB_COMPONENT = 1;
    private static final int TAB_MODIFIER = 2;

    private static final int DRAG_NONE = 0;

    private int tab = TAB_COMPONENT;
    private int paletteScroll;
    /** The search box filters the ACTIVE bookmark. Purely client-side: nothing is sent to the server. */
    private String search = "";
    private EditBox searchBox;
    private EditBox nameBox;
    /** The name last sent to the server, so the box does not spam a packet per keystroke. */
    private String sentName = "";
    private List<Integer> filtered = new ArrayList<>();
    private int stripScroll;

    private final List<GemButton> tabButtons = new ArrayList<>();
    private final List<GemButton> actionButtons = new ArrayList<>();

    /** Chip boxes from the last frame: {x1, y1, x2, y2, flatIndex}. */
    private final List<int[]> chips = new ArrayList<>();
    /** The whole strip of parts, refreshed from the menu each frame. */
    private List<int[]> parts = new ArrayList<>();

    private String draftSignature = "";

    private int dragKind = DRAG_NONE;
    private SpellComponent dragComponent;
    private SpellShape dragShape;
    /** Flat index the drag started at, or -1 when the drag began in the palette. */
    private int dragFromFlat = -1;
    private double dragX;
    private double dragY;

    public InscriptionScreen(InscriptionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        buildTabs();
        addActionButtons();
        addSearchAndName();
    }

    /**
     * The Search and Name boxes, straight from the reference layout.
     *
     * Search filters the active bookmark by component id (client-side only). Name is the spell's name:
     * it is flushed to the server on Enter, on leaving the box, or on closing the screen, so typing does
     * not cost a packet per keystroke.
     */
    private void addSearchAndName() {
        int width = palettePanelWidth();
        searchBox = new EditBox(font, paletteLeft(), topPos + SEARCH_Y, width, 12,
                Component.literal("Search"));
        searchBox.setMaxLength(24);
        searchBox.setValue(search);
        searchBox.setHint(Component.literal("Search").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setResponder(value -> {
            search = value;
            paletteScroll = 0;
        });
        addRenderableWidget(searchBox);

        nameBox = new EditBox(font, paletteLeft(), topPos + NAME_Y, width - 48, 12,
                Component.literal("Name"));
        nameBox.setMaxLength(Spell.MAX_NAME_LENGTH);
        nameBox.setValue(sentName);
        nameBox.setHint(Component.literal("Name").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(nameBox);

        GemButton resetName = new GemButton(paletteLeft() + width - 46, topPos + NAME_Y, 46, 12,
                Component.literal("Reset Name"), GemButton.Style.PLAIN, () -> {
                    nameBox.setValue("");
                    flushName();
                });
        resetName.setTooltip(Tooltip.create(Component.literal("Clear the spell's name")));
        addRenderableWidget(resetName);
    }

    /** Send the name to the server if it has actually changed. */
    private void flushName() {
        if (nameBox == null) {
            return;
        }
        String value = nameBox.getValue().strip();
        if (value.equals(sentName)) {
            return;
        }
        sentName = value;
        if (this.minecraft != null && this.minecraft.player != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.shadowclangaming.network.ScgNet.SpellNamePayload(blockPos(), value));
        }
    }

    /** The table this screen belongs to. The menu only carries a position for us, so read it back. */
    private net.minecraft.core.BlockPos blockPos() {
        return this.menu.tablePos();
    }

    @Override
    public void onClose() {
        flushName();
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && nameBox != null && nameBox.isFocused()) {
            flushName();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (nameBox != null && !nameBox.isMouseOver(mouseX, mouseY) && nameBox.isFocused()) {
            flushName();
        }
        return clickInner(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------ geometry

    private int paletteLeft() {
        return leftPos + 8;
    }

    private int paletteTop() {
        return topPos + GRID_TOP;
    }

    private int pageLeft() {
        return leftPos + 180;
    }

    private int pageWidth() {
        return imageWidth - 188;
    }

    private int palettePanelWidth() {
        return pageLeft() - paletteLeft() - 8;
    }

    private int stripTop() {
        return topPos + STRIP_TOP;
    }

    private int chipPitch() {
        return CHIP + CHIP_GAP;
    }

    // ------------------------------------------------------------------ the three bookmarks

    private void buildTabs() {
        for (GemButton button : tabButtons) {
            removeWidget(button);
        }
        tabButtons.clear();

        int x = leftPos + 8;
        int width = (palettePanelWidth() - 4) / 3;
        String[] labels = {"SHAPE", "COMPONENT", "MODIFIER"};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            GemButton button = new GemButton(x + i * (width + 2), topPos + TABS_Y, width, TAB_H,
                    Component.literal(labels[i]),
                    i == tab ? GemButton.Style.PRIMARY : GemButton.Style.PLAIN,
                    () -> {
                        tab = index;
                        paletteScroll = 0;
                        buildTabs();
                    });
            button.setTooltip(Tooltip.create(Component.literal(switch (i) {
                case TAB_SHAPE -> "Shapes: how the spell is delivered.\nClick to append, drag to insert.";
                case TAB_COMPONENT -> "Components: what the spell does.\nClick to append, drag to insert.";
                default -> "Modifiers: how the selected shape behaves.\nClick to toggle.";
            })));
            addRenderableWidget(button);
            tabButtons.add(button);
        }
    }

    private int paletteSize() {
        return filtered.size();
    }

    /**
     * The active bookmark's entries that match the search box.
     *
     * Filtering is by registry id, which is what a player ends up typing ("lightning", "zone", "wide").
     * An empty search shows everything, so the box costs nothing until it is used.
     */
    private List<Integer> paletteEntries() {
        List<Integer> entries = new ArrayList<>();
        String needle = search.strip().toLowerCase(java.util.Locale.ROOT);
        switch (tab) {
            case TAB_SHAPE -> {
                SpellShape[] values = SpellShape.values();
                for (int i = 0; i < values.length; i++) {
                    if (needle.isEmpty() || values[i].id().contains(needle)) {
                        entries.add(i);
                    }
                }
            }
            case TAB_MODIFIER -> {
                SpellModifier[] values = SpellModifier.values();
                for (int i = 0; i < values.length; i++) {
                    if (needle.isEmpty() || values[i].id().contains(needle)) {
                        entries.add(i);
                    }
                }
            }
            default -> {
                SpellComponent[] values = SpellComponent.values();
                for (int i = 0; i < values.length; i++) {
                    if (needle.isEmpty() || values[i].id().contains(needle)) {
                        entries.add(i);
                    }
                }
            }
        }
        return entries;
    }

    private int paletteVisible() {
        return GRID_COLUMNS * GRID_ROWS;
    }

    /** Index in the active bookmark under the pointer, or -1. */
    private int paletteIndexAt(double mouseX, double mouseY) {
        if (mouseX < paletteLeft() || mouseY < paletteTop()) {
            return -1;
        }
        int pitch = TILE + TILE_GAP;
        int column = (int) ((mouseX - paletteLeft()) / pitch);
        int row = (int) ((mouseY - paletteTop()) / pitch);
        if (column < 0 || column >= GRID_COLUMNS || row < 0 || row >= GRID_ROWS) {
            return -1;
        }
        int slot = paletteScroll + row * GRID_COLUMNS + column;
        if (slot < 0 || slot >= filtered.size()) {
            return -1;
        }
        return filtered.get(slot);
    }

    // ------------------------------------------------------------------ the strip

    private boolean insideStrip(double mouseX, double mouseY) {
        return mouseX >= pageLeft() && mouseX < pageLeft() + pageWidth()
                && mouseY >= stripTop() && mouseY < stripTop() + STRIP_ROWS * chipPitch();
    }

    /** Flat index of the chip under the pointer, or -1. */
    private int chipAt(double mouseX, double mouseY) {
        for (int[] chip : chips) {
            if (mouseX >= chip[0] && mouseX < chip[2] && mouseY >= chip[1] && mouseY < chip[3]) {
                return chip[4];
            }
        }
        return -1;
    }

    /**
     * Where a drop at these coordinates would land, as a flat index: dropping before a chip's midpoint
     * lands in front of it, anything past the end of the strip lands at the end.
     */
    private int dropFlatIndex(double mouseX, double mouseY) {
        if (!insideStrip(mouseX, mouseY)) {
            return -1;
        }
        for (int[] chip : chips) {
            int centreX = (chip[0] + chip[2]) / 2;
            int centreY = (chip[1] + chip[3]) / 2;
            if (mouseY < chip[1] || (mouseY >= chip[1] && mouseY < chip[3] && mouseX < centreX)) {
                return chip[4];
            }
            if (mouseY >= chip[3] && mouseY < chip[3] + CHIP_GAP
                    && mouseX < centreX) {
                return chip[4];
            }
            if (mouseY >= chip[3] && mouseX >= centreX && mouseX < chip[2]) {
                return chip[4] + 1;
            }
            if (mouseY >= chip[1] && mouseY < chip[3] && mouseX >= centreX) {
                return chip[4] + 1;
            }
        }
        return parts.size();
    }

    /** The (step, index) a component would be inserted at, for a flat insertion point. */
    private int[] componentSlotForFlat(int flat) {
        if (parts.isEmpty()) {
            return new int[]{0, 0};
        }
        int index = Mth.clamp(flat, 0, parts.size());
        if (index >= parts.size()) {
            int[] last = parts.get(parts.size() - 1);
            int step = last[2];
            return new int[]{step, this.menu.stepComponentCount(step)};
        }
        int[] part = parts.get(index);
        if (part[0] == 0) {
            // Dropped in front of a shape: it becomes the first effect of that delivery.
            return new int[]{part[2], 0};
        }
        return new int[]{part[2], part[3]};
    }

    /** The delivery index a NEW shape dropped at this flat position should take. */
    private int stepIndexForFlat(int flat) {
        if (parts.isEmpty()) {
            return 0;
        }
        int index = Mth.clamp(flat, 0, parts.size());
        if (index >= parts.size()) {
            return this.menu.stepCount();
        }
        return parts.get(index)[2];
    }

    // ------------------------------------------------------------------ widgets

    private void addActionButtons() {
        for (GemButton button : actionButtons) {
            removeWidget(button);
        }
        actionButtons.clear();

        int bottom = topPos + HEIGHT - 22;
        GemButton undo = new GemButton(leftPos + 8, bottom, 46, 15,
                Component.literal("Undo"), GemButton.Style.PLAIN,
                () -> send(InscriptionMenu.BUTTON_POP));
        undo.setTooltip(Tooltip.create(Component.literal("Remove the last part of the selected shape")));
        addRenderableWidget(undo);
        actionButtons.add(undo);

        GemButton clear = new GemButton(leftPos + 56, bottom, 46, 15,
                Component.literal("Clear"), GemButton.Style.DANGER,
                () -> send(InscriptionMenu.BUTTON_CLEAR));
        clear.glyph(ArcanePanel.Glyph.CROSS);
        clear.setTooltip(Tooltip.create(Component.literal("Empty the spell, keep the shapes")));
        addRenderableWidget(clear);
        actionButtons.add(clear);

        GemButton reset = new GemButton(leftPos + 104, bottom, 46, 15,
                Component.literal("Reset"), GemButton.Style.DANGER,
                () -> send(InscriptionMenu.BUTTON_RESET));
        reset.setTooltip(Tooltip.create(Component.literal("Start over with one empty projectile")));
        addRenderableWidget(reset);
        actionButtons.add(reset);

        GemButton scrollUp = new GemButton(pageLeft(), bottom, 14, 15, Component.empty(),
                GemButton.Style.PLAIN, () -> scrollStrip(-1));
        scrollUp.glyph(ArcanePanel.Glyph.CHEVRON_UP);
        addRenderableWidget(scrollUp);
        actionButtons.add(scrollUp);

        GemButton scrollDown = new GemButton(pageLeft() + 16, bottom, 14, 15, Component.empty(),
                GemButton.Style.PLAIN, () -> scrollStrip(1));
        scrollDown.glyph(ArcanePanel.Glyph.CHEVRON_DOWN);
        addRenderableWidget(scrollDown);
        actionButtons.add(scrollDown);

        GemButton inscribe = new GemButton(pageLeft() + 34, bottom,
                leftPos + imageWidth - 8 - pageLeft() - 34, 15,
                Component.literal("INSCRIBE"), GemButton.Style.PRIMARY,
                () -> send(InscriptionMenu.BUTTON_INSCRIBE));
        inscribe.glyph(ArcanePanel.Glyph.STAR);
        addRenderableWidget(inscribe);
        actionButtons.add(inscribe);
    }

    private void scrollStrip(int rows) {
        int totalRows = Math.max(1, (parts.size() + STRIP_COLUMNS - 1) / STRIP_COLUMNS);
        stripScroll = Mth.clamp(stripScroll + rows, 0, Math.max(0, totalRows - STRIP_ROWS));
    }

    private void send(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    // ------------------------------------------------------------------ input

    private boolean clickInner(double mouseX, double mouseY, int button) {
        int flat = chipAt(mouseX, mouseY);
        if (flat >= 0 && flat < parts.size()) {
            int[] part = parts.get(flat);
            if (button == 1) {
                // Right-click a chip: delete it. A shape takes its delivery with it.
                if (part[0] == 0) {
                    send(InscriptionMenu.BUTTON_REMOVE_STEP_BASE + part[2]);
                } else {
                    send(InscriptionMenu.BUTTON_REMOVE_AT_BASE
                            + InscriptionMenu.pack3(0, part[2], part[3]));
                }
                return true;
            }
            if (button == 0) {
                if (part[0] == 0) {
                    dragShape = SpellShape.values()[part[1]];
                    dragComponent = null;
                } else {
                    dragComponent = SpellComponent.values()[part[1]];
                    dragShape = null;
                }
                dragFromFlat = flat;
                dragKind = 1;
                dragX = mouseX;
                dragY = mouseY;
                send(InscriptionMenu.BUTTON_SELECT_STEP_BASE + part[2]);
                return true;
            }
        }

        int index = paletteIndexAt(mouseX, mouseY);
        if (index >= 0 && button == 0) {
            switch (tab) {
                case TAB_SHAPE -> {
                    dragShape = SpellShape.values()[index];
                    dragComponent = null;
                    dragFromFlat = -1;
                    dragKind = 1;
                    dragX = mouseX;
                    dragY = mouseY;
                }
                case TAB_MODIFIER -> send(InscriptionMenu.BUTTON_MODIFIER_BASE
                        + SpellModifier.values()[index].ordinal());
                default -> {
                    dragComponent = SpellComponent.values()[index];
                    dragShape = null;
                    dragFromFlat = -1;
                    dragKind = 1;
                    dragX = mouseX;
                    dragY = mouseY;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragKind != DRAG_NONE) {
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragKind == DRAG_NONE) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        SpellComponent component = dragComponent;
        SpellShape shape = dragShape;
        int fromFlat = dragFromFlat;
        dragKind = DRAG_NONE;
        dragComponent = null;
        dragShape = null;
        dragFromFlat = -1;

        int dropFlat = dropFlatIndex(mouseX, mouseY);
        boolean overPalette = paletteIndexAt(mouseX, mouseY) >= 0 && dropFlat < 0;

        // ---- a shape ----
        if (shape != null) {
            if (dropFlat >= 0) {
                send(InscriptionMenu.BUTTON_INSERT_STEP_AT_BASE
                        + InscriptionMenu.pack3(shape.ordinal(), stepIndexForFlat(dropFlat), 0));
            } else if (overPalette || fromFlat < 0) {
                // Click (or a drop back on the palette): append a new delivery at the end.
                send(InscriptionMenu.BUTTON_ADD_STEP_BASE + shape.ordinal());
            } else if (this.menu.stepCount() > 1) {
                send(InscriptionMenu.BUTTON_REMOVE_STEP_BASE + parts.get(fromFlat)[2]);
            }
            return true;
        }

        if (component == null) {
            return true;
        }

        // ---- a component dragged from the strip ----
        if (fromFlat >= 0) {
            if (dropFlat >= 0) {
                int[] slot = componentSlotForFlat(dropFlat);
                int[] source = parts.get(fromFlat);
                if (source[0] == 1) {
                    send(InscriptionMenu.BUTTON_MOVE_BASE
                            + InscriptionMenu.pack4(source[2], source[3], slot[0], slot[1]));
                }
            } else {
                int[] source = parts.get(fromFlat);
                if (source[0] == 1) {
                    send(InscriptionMenu.BUTTON_REMOVE_AT_BASE
                            + InscriptionMenu.pack3(0, source[2], source[3]));
                }
            }
            return true;
        }

        // ---- a component dragged out of the palette ----
        if (dropFlat >= 0) {
            int[] slot = componentSlotForFlat(dropFlat);
            send(InscriptionMenu.BUTTON_INSERT_BASE
                    + InscriptionMenu.pack3(component.ordinal(), slot[0], slot[1]));
        } else if (overPalette) {
            // Plain click: append to the last delivery, creating one if the spell is empty.
            if (this.menu.stepCount() == 0) {
                send(InscriptionMenu.BUTTON_ADD_STEP_BASE + SpellShape.SELF.ordinal());
            }
            send(InscriptionMenu.BUTTON_SELECT_STEP_BASE + Math.max(0, this.menu.stepCount() - 1));
            send(InscriptionMenu.BUTTON_COMPONENT_BASE + component.ordinal());
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int direction = scrollY > 0 ? -1 : 1;
        if (mouseX >= paletteLeft() && mouseX < pageLeft() - 4
                && mouseY >= paletteTop() && mouseY <= topPos + COST_TOP - 6) {
            int max = Math.max(0, paletteSize() - paletteVisible());
            paletteScroll = Mth.clamp(paletteScroll + direction * GRID_COLUMNS, 0, max);
            return true;
        }
        if (insideStrip(mouseX, mouseY)) {
            scrollStrip(direction);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        filtered = paletteEntries();
        syncDraft();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        ArcanePanel.window(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawHeadings(graphics);
        drawPalette(graphics, mouseX, mouseY);
        drawStrip(graphics, mouseX, mouseY);
        drawCosts(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawDragged(graphics);
        drawHover(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // window drawn in render()
    }

    private void drawHeadings(GuiGraphics graphics) {
        ArcanePanel.heading(font, graphics, Component.literal("inscription table"),
                leftPos + 8, topPos + 5, ArcanePanel.TEXT);
        String capacity = "MANA " + this.menu.casterMana() + " / " + this.menu.casterCapacity();
        ArcanePanel.heading(font, graphics, Component.literal(capacity),
                leftPos + imageWidth - 9 - font.width(capacity), topPos + 5, ArcanePanel.GOLD);
        ArcanePanel.heading(font, graphics, Component.literal("the spell, in order"),
                pageLeft() + 2, topPos + STRIP_TOP - 10, ArcanePanel.ACCENT_2);
    }

    /** The palette: a grid of tiles for the active bookmark, scrolled by the wheel. */
    private void drawPalette(GuiGraphics graphics, int mouseX, int mouseY) {
        int pitch = TILE + TILE_GAP;
        ArcanePanel.inset(graphics, paletteLeft() - 2, paletteTop() - 2,
                GRID_COLUMNS * pitch + 2, GRID_ROWS * pitch + 2);

        int count = paletteSize();
        int max = Math.max(0, count - paletteVisible());
        paletteScroll = Mth.clamp(paletteScroll, 0, max);

        for (int slot = 0; slot < paletteVisible(); slot++) {
            int entry = paletteScroll + slot;
            if (entry >= filtered.size()) {
                break;
            }
            int index = filtered.get(entry);
            int x = paletteLeft() + (slot % GRID_COLUMNS) * pitch;
            int y = paletteTop() + (slot / GRID_COLUMNS) * pitch;
            boolean hovered = mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE;

            String glyph;
            int colour;
            boolean on = false;
            switch (tab) {
                case TAB_SHAPE -> {
                    SpellShape shape = SpellShape.values()[index];
                    glyph = shortShape(shape);
                    colour = shapeColour(shape);
                }
                case TAB_MODIFIER -> {
                    SpellModifier modifier = SpellModifier.values()[index];
                    glyph = shortModifier(modifier);
                    colour = 0xFF8A5A00;
                    on = modifier.in(this.menu.stepModifiers(this.menu.selectedStep()));
                }
                default -> {
                    SpellComponent component = SpellComponent.values()[index];
                    glyph = shortComponent(component);
                    colour = categoryColour(component);
                }
            }
            tile(graphics, x, y, glyph, colour, hovered, on);
        }

        if (count > paletteVisible()) {
            int height = GRID_ROWS * pitch;
            int knob = Math.max(10, height * paletteVisible() / count);
            int travel = height - knob;
            int offset = max == 0 ? 0 : travel * paletteScroll / max;
            int x = paletteLeft() + GRID_COLUMNS * pitch + 1;
            graphics.fill(x, paletteTop(), x + 3, paletteTop() + height, 0x40B8A47B);
            graphics.fill(x, paletteTop() + offset, x + 3, paletteTop() + offset + knob, 0xFF8A6A3E);
        }
    }

    /** One palette tile: a coloured frame, a two-letter glyph, a highlight when hovered or toggled. */
    private void tile(GuiGraphics graphics, int x, int y, String glyph, int colour,
                      boolean hovered, boolean on) {
        tile(graphics, x, y, glyph, colour, hovered, on, false);
    }

    private void tile(GuiGraphics graphics, int x, int y, String glyph, int colour,
                      boolean hovered, boolean on, boolean thick) {
        graphics.fill(x, y, x + CHIP, y + CHIP, 0x60181008);
        int edge = thick ? 2 : 1;
        graphics.fill(x, y, x + CHIP, y + edge, colour);
        graphics.fill(x, y + CHIP - edge, x + CHIP, y + CHIP, colour);
        graphics.fill(x, y, x + edge, y + CHIP, colour);
        graphics.fill(x + CHIP - edge, y, x + CHIP, y + CHIP, colour);
        if (on) {
            graphics.fill(x + edge, y + edge, x + CHIP - edge, y + CHIP - edge, 0x60C8A24A);
        } else if (hovered) {
            graphics.fill(x + edge, y + edge, x + CHIP - edge, y + CHIP - edge, 0x40FFFFFF);
        }
        int width = font.width(glyph);
        graphics.drawString(font, glyph, x + (CHIP - width) / 2, y + 4,
                hovered ? 0xFFFFF3D0 : 0xFFE0D2B4, false);
    }

    /**
     * The spell: one flat strip of chips, in order.
     *
     * Shape chips are drawn with a thick gold frame and a notch under them; everything to the right of a
     * shape chip belongs to that delivery. This is the whole "no eight slots" idea made visible - the
     * strip simply keeps going.
     */
    private void drawStrip(GuiGraphics graphics, int mouseX, int mouseY) {
        chips.clear();
        parts = this.menu.flatParts();
        int pitch = chipPitch();
        int visible = STRIP_COLUMNS * STRIP_ROWS;
        int totalRows = Math.max(1, (parts.size() + STRIP_COLUMNS - 1) / STRIP_COLUMNS);
        stripScroll = Mth.clamp(stripScroll, 0, Math.max(0, totalRows - STRIP_ROWS));

        ArcanePanel.inset(graphics, pageLeft() - 2, stripTop() - 2,
                STRIP_COLUMNS * pitch + 2, STRIP_ROWS * pitch + 2);

        int selected = this.menu.selectedStep();
        int first = stripScroll * STRIP_COLUMNS;
        for (int slot = 0; slot < visible; slot++) {
            int flat = first + slot;
            if (flat >= parts.size()) {
                // A faint "drop here" cell at the end, so the strip has an obvious tail.
                if (flat == parts.size() && slot % STRIP_COLUMNS != 0) {
                    int x = pageLeft() + (slot % STRIP_COLUMNS) * pitch;
                    int y = stripTop() + (slot / STRIP_COLUMNS) * pitch;
                    graphics.fill(x, y, x + CHIP, y + CHIP, 0x30B8A47B);
                }
                break;
            }
            int[] part = parts.get(flat);
            int x = pageLeft() + (slot % STRIP_COLUMNS) * pitch;
            int y = stripTop() + (slot / STRIP_COLUMNS) * pitch;
            boolean hovered = mouseX >= x && mouseX < x + CHIP && mouseY >= y && mouseY < y + CHIP;
            boolean active = part[2] == selected;

            if (part[0] == 0) {
                SpellShape shape = SpellShape.values()[Math.floorMod(part[1], SpellShape.values().length)];
                tile(graphics, x, y, shortShape(shape), shapeColour(shape), hovered, active, true);
                // Modifier dots: one per modifier on this delivery.
                int mask = this.menu.stepModifiers(part[2]);
                int dot = 0;
                for (SpellModifier modifier : SpellModifier.values()) {
                    if (modifier.in(mask) && dot < 4) {
                        graphics.fill(x + 2 + dot * 4, y + CHIP - 4, x + 4 + dot * 4, y + CHIP - 2,
                                0xFFC8A24A);
                        dot++;
                    }
                }
            } else {
                SpellComponent component = SpellComponent.values()[
                        Math.floorMod(part[1], SpellComponent.values().length)];
                tile(graphics, x, y, shortComponent(component), categoryColour(component),
                        hovered, false);
            }
            chips.add(new int[]{x, y, x + CHIP, y + CHIP, flat});
        }

        if (totalRows > STRIP_ROWS) {
            int height = STRIP_ROWS * pitch;
            int knob = Math.max(10, height * STRIP_ROWS / totalRows);
            int travel = height - knob;
            int offset = travel * stripScroll / Math.max(1, totalRows - STRIP_ROWS);
            int x = pageLeft() + STRIP_COLUMNS * pitch + 1;
            graphics.fill(x, stripTop(), x + 3, stripTop() + height, 0x40B8A47B);
            graphics.fill(x, stripTop() + offset, x + 3, stripTop() + offset + knob, 0xFF8A6A3E);
        }
    }

    private void drawDragged(GuiGraphics graphics) {
        if (dragKind == DRAG_NONE || (dragComponent == null && dragShape == null)) {
            return;
        }
        int x = (int) dragX - CHIP / 2;
        int y = (int) dragY - CHIP / 2;
        if (dragShape != null) {
            tile(graphics, x, y, shortShape(dragShape), shapeColour(dragShape), true, false, true);
        } else {
            tile(graphics, x, y, shortComponent(dragComponent), categoryColour(dragComponent),
                    true, false);
        }
    }

    private void drawHover(GuiGraphics graphics, int mouseX, int mouseY) {
        if (dragKind != DRAG_NONE) {
            return;
        }
        int flat = chipAt(mouseX, mouseY);
        if (flat >= 0 && flat < parts.size()) {
            int[] part = parts.get(flat);
            if (part[0] == 0) {
                SpellShape shape = SpellShape.values()[Math.floorMod(part[1], SpellShape.values().length)];
                graphics.renderTooltip(font, Component.translatable(shape.translationKey())
                        .append(Component.literal("\n" + shape.baseCost() + " mana base")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("\ndelivery " + (part[2] + 1) + ": "
                                        + this.menu.stepComponentCount(part[2]) + " effects, "
                                        + this.menu.previewStepCost(part[2]) + " mana")
                                .withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(modifierSuffix(part[2])).withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("\ndrag to move  -  right-click to remove")
                                .withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
            } else {
                SpellComponent component = SpellComponent.values()[
                        Math.floorMod(part[1], SpellComponent.values().length)];
                graphics.renderTooltip(font, Component.translatable(component.translationKey())
                        .append(Component.literal("\ntier " + component.tier() + "  -  "
                                + component.manaCost() + " mana").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("\ndrag to move  -  right-click to remove")
                                .withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
            }
            return;
        }
        int index = paletteIndexAt(mouseX, mouseY);
        if (index >= 0) {
            graphics.renderTooltip(font, paletteTooltip(index), mouseX, mouseY);
        }
    }

    private Component paletteTooltip(int index) {
        switch (tab) {
            case TAB_SHAPE -> {
                SpellShape shape = SpellShape.values()[index];
                return Component.translatable(shape.translationKey())
                        .append(Component.literal("\n" + shape.baseCost() + " mana base")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("\nclick to append, drag onto the strip to insert")
                                .withStyle(ChatFormatting.DARK_GRAY));
            }
            case TAB_MODIFIER -> {
                SpellModifier modifier = SpellModifier.values()[index];
                return Component.translatable(modifier.translationKey())
                        .append(Component.literal("  " + (modifier.manaPercent() >= 0 ? "+" : "")
                                        + modifier.manaPercent() + "% cost")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("\n" + modifierEffect(modifier))
                                .withStyle(ChatFormatting.DARK_GRAY));
            }
            default -> {
                SpellComponent component = SpellComponent.values()[index];
                return Component.translatable(component.translationKey())
                        .append(Component.literal("\ntier " + component.tier() + "  -  "
                                        + component.manaCost() + " mana  -  "
                                        + component.category().name().toLowerCase(java.util.Locale.ROOT))
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("\nclick to append, drag onto the strip to insert")
                                .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    /**
     * The cost panel - pinned to the bottom of the window, always visible: what the draft costs, what
     * capacity it demands, and what pressing INSCRIBE would write onto.
     */
    private void drawCosts(GuiGraphics graphics) {
        Spell spell = this.menu.previewSpell();
        int x = pageLeft();
        int y = topPos + COST_TOP;
        int width = pageWidth();
        int height = topPos + HEIGHT - 26 - y;

        long cost = spell.rawManaCost();
        long capacity = Math.max(0L, this.menu.casterCapacity());
        long mana = Math.max(0L, this.menu.casterMana());

        ArcanePanel.inset(graphics, x, y, width, height);
        // The name box's current text, so the panel echoes what you are about to inscribe.
        String name = nameBox == null ? "" : nameBox.getValue().strip();
        graphics.drawString(font, name.isEmpty() ? spell.displayName() : name, x + 4, y + 2,
                ArcanePanel.GOLD, false);
        drawStat(graphics, x, y + 12, "mana cost", String.valueOf(cost), ArcanePanel.MANA_LIGHT);
        drawStat(graphics, x, y + 22, "capacity demand", String.valueOf(spell.capacityDemand()),
                capacity >= spell.capacityDemand() ? ArcanePanel.GOOD : ArcanePanel.BAD);
        drawStat(graphics, x, y + 32, "shapes / effects",
                spell.stepCount() + " / " + spell.componentCount(), ArcanePanel.TEXT_DIM);

        double fraction = capacity <= 0 ? 0.0D : (double) spell.capacityDemand() / (double) capacity;
        ArcanePanel.bar(graphics, x + 3, y + 42, width - 8, 6, fraction,
                fraction > 1.0D ? 0xFFA81E1E : ArcanePanel.MANA, ArcanePanel.INSET_EDGE);
        graphics.drawString(font, "you: " + mana + " / " + capacity, x + 4, y + 50,
                mana >= cost ? ArcanePanel.GOOD : ArcanePanel.TEXT_FAINT, false);
        Component target = Component.translatable(switch (this.menu.inscribeTarget()) {
            case InscriptionMenu.TARGET_BOOK -> "message.shadowclangaming.inscribe_target.book";
            case InscriptionMenu.TARGET_BLUEPRINT -> "message.shadowclangaming.inscribe_target.blueprint";
            case InscriptionMenu.TARGET_RIFLE -> "message.shadowclangaming.inscribe_target.rifle";
            case InscriptionMenu.TARGET_WARHEAD -> "message.shadowclangaming.inscribe_target.warhead";
            default -> "message.shadowclangaming.inscribe_target.none";
        });
        graphics.drawString(font, target, x + 4, y + 60,
                this.menu.inscribeTarget() == InscriptionMenu.TARGET_NONE
                        ? ArcanePanel.BAD : ArcanePanel.TEXT_DIM, false);
    }

    private void drawStat(GuiGraphics graphics, int x, int y, String label, String value, int colour) {
        graphics.drawString(font, label, x + 4, y, ArcanePanel.TEXT_DIM, false);
        int valueWidth = font.width(value);
        graphics.drawString(font, value, x + pageWidth() - 8 - valueWidth, y, colour, false);
        int from = x + 6 + font.width(label);
        int to = x + pageWidth() - 10 - valueWidth;
        for (int dot = from + 2; dot < to; dot += 4) {
            graphics.fill(dot, y + 5, dot + 1, y + 6, 0x30000000);
        }
    }

    // ------------------------------------------------------------------ labels

    private static int shapeColour(SpellShape shape) {
        return shape.isPersistent() ? 0xFF7A4FBF : 0xFF3A6FA8;
    }

    private static String shortShape(SpellShape shape) {
        return switch (shape) {
            case SELF -> "Se";
            case TOUCH -> "To";
            case PROJECTILE -> "Pr";
            case BEAM -> "Be";
            case AURA -> "Au";
            case ZONE -> "Zo";
            case RUNE -> "Ru";
        };
    }

    /** Two letters from the component's own name. AM2 used pictures; letters survive any resolution. */
    private static String shortComponent(SpellComponent component) {
        String[] words = component.id().split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (builder.length() >= 2 || word.isEmpty()) {
                break;
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
        }
        if (builder.length() < 2) {
            String id = component.id();
            builder.append(Character.toUpperCase(id.charAt(Math.min(1, id.length() - 1))));
        }
        return builder.substring(0, Math.min(2, builder.length()));
    }

    private static String shortModifier(SpellModifier modifier) {
        String id = modifier.id().replace("_", " ");
        return "" + Character.toUpperCase(id.charAt(0))
                + Character.toUpperCase(id.charAt(Math.min(1, id.length() - 1)));
    }

    private static String componentName(SpellComponent component) {
        String id = component.id().replace('_', ' ');
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    private String modifierSuffix(int stepIndex) {
        int mask = this.menu.stepModifiers(stepIndex);
        if (mask == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder("\nmodifiers: ");
        boolean first = true;
        for (SpellModifier modifier : SpellModifier.unpack(mask)) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(modifier.id().replace('_', ' '));
            first = false;
        }
        return builder.toString();
    }

    private static String modifierEffect(SpellModifier modifier) {
        return switch (modifier) {
            case EMPOWERED -> "Damage and healing from this step are x1.5.";
            case WIDE -> "Areas reach x1.6 further.";
            case PIERCING -> "Damage ignores armour and the victim's damage cooldown.";
            case VAMPIRIC -> "You heal 35% of the damage this step deals.";
            case TWINNED -> "The step resolves twice.";
            case SWIFT -> "Projectiles fly at double speed.";
            case LASTING -> "Zones and runes last 50% longer.";
            case SILENT -> "No sound, a quarter of the particles.";
            case MAXIMIZE -> "Damage and healing are taken at their top roll: x1.35, every time.";
            case TRIPLET -> "The step resolves three times. Beats Twinned.";
            case BOOST -> "x1.25 damage and x1.25 duration on anything that persists.";
            case DELAY -> "The step lands 3 seconds after you cast it.";
            case CHANT -> "25% cheaper, but the step lands 2 seconds late.";
            case SEEKER -> "Bolts turn toward the nearest living thing within 24 blocks.";
            case ECHO -> "The step fires again, at half power, two seconds later.";
            case SIPHON -> "You gain 6 mana for every point of damage this step deals.";
            case BASTION -> "You gain absorption worth half the damage this step deals.";
            case EXECUTE -> "x1.6 everywhere and x2.25 against anything under 35% health.";
            case CATACLYSM -> "x2.0 damage and x1.5 area at once. The loudest modifier there is.";
            case ETERNAL -> "Zones and runes last three times as long.";
            case LEGION -> "The step resolves four times. Beats Triplet.";
            case EPICENTRE -> "Area x2.2. Beats Wide.";
            case CRIPPLE -> "Victims are left weak, slow and unable to dig for 8 seconds.";
            case DISPEL -> "Strips every beneficial effect before the damage lands.";
            case NOVA -> "The step detonates: a block-safe blast, radius 2 + 2 per power.";
            case HEX -> "A random heavy curse: wither, poison, blindness, nausea or levitation.";
            case BIND -> "Victims are rooted for 3 seconds - no walking, no jumping.";
            case BLOOD_PRICE -> "70% cheaper, but you pay 25% of your maximum health to cast it.";
        };
    }

    private static int categoryColour(SpellComponent component) {
        return switch (component.category()) {
            case DAMAGE -> 0xFFA81E1E;
            case HEAL -> 0xFF1C6B2A;
            case BUFF -> 0xFF16637A;
            case DEBUFF -> 0xFF6B2E8A;
            case UTILITY -> 0xFF8A5A00;
            case AREA -> 0xFF8A2B63;
        };
    }

    /** Refresh the bookmark strip when the draft changes (modifier toggles show as lit tiles). */
    private void syncDraft() {
        String signature = draftSignature();
        if (!signature.equals(this.draftSignature)) {
            this.draftSignature = signature;
            buildTabs();
        }
    }

    /** A signature of the whole draft, component ORDINALS included, so a move is noticed. */
    private String draftSignature() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.menu.stepCount()).append('|').append(this.menu.selectedStep())
                .append('|').append(tab);
        for (int i = 0; i < this.menu.stepCount(); i++) {
            builder.append('|').append(this.menu.stepShape(i).ordinal())
                    .append('m').append(this.menu.stepModifiers(i)).append(':');
            int components = this.menu.stepComponentCount(i);
            for (int c = 0; c < components; c++) {
                builder.append(this.menu.stepComponentAt(i, c).ordinal()).append(',');
            }
        }
        return builder.toString();
    }
}
