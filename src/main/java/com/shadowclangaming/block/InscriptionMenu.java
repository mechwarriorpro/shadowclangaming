package com.shadowclangaming.block;

import com.shadowclangaming.compat.economy.Money;
import com.shadowclangaming.magic.ManaApi;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.registry.SCGItems;
import com.shadowclangaming.registry.SCGMenus;
import com.shadowclangaming.spell.Spell;
import com.shadowclangaming.spell.SpellComponent;
import com.shadowclangaming.spell.SpellModifier;
import com.shadowclangaming.spell.SpellShape;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The inscription screen's menu.
 *
 * There are NO item slots. A spell is made of mana and mana capacity, not reagents: the rifle (or a
 * warhead) is read straight out of the player's hand when they press "inscribe", so nothing can be
 * smuggled in or duplicated through a slot.
 *
 * ──────────────────── THE TWO BUGS THIS CLASS USED TO HAVE ────────────────────
 *
 * 1. The client could not see the draft. The packet constructor resolved the block entity on the CLIENT
 *    (where one exists, but is a mirror that never receives edits) and built the data view from it. A
 *    client-side {@code InscriptionTableBlockEntity} does not update when the server edits its draft, so
 *    every component the player added was invisible. The client now always gets a
 *    {@link SimpleContainerData}, which is the only kind of ContainerData that accepts writes from the
 *    network - twenty thousand vanilla screens rely on exactly this.
 *
 * 2. Nothing forced the sync. Vanilla only ships data slots whose values it notices have changed, and a
 *    read-only ContainerData is the case where that detection is easiest to get wrong. The menu now
 *    pushes changed slots itself in {@link #broadcastChanges()} with explicit packets, so the draft is
 *    guaranteed to be on the client before the next frame is drawn.
 *
 * Every button press goes through {@link #clickMenuButton}, which runs on the server - the client can
 * never add a component the server did not agree to.
 */
public class InscriptionMenu extends AbstractContainerMenu {

    // ---- button ids ---------------------------------------------------------------------------
    public static final int BUTTON_INSCRIBE = 0;
    public static final int BUTTON_CLEAR = 1;
    public static final int BUTTON_POP = 2;
    /** 3..: remove one of the steps. */
    public static final int BUTTON_REMOVE_STEP_BASE = 10;
    /** 30..: select a step as the edit target. */
    public static final int BUTTON_SELECT_STEP_BASE = 30;
    /** 100..: append a NEW step of the shape with that ordinal. */
    public static final int BUTTON_ADD_STEP_BASE = 100;
    /** 130..: add the component with that ordinal to the selected step. */
    public static final int BUTTON_COMPONENT_BASE = 130;
    /** Kept for older call sites: jump the first step to this shape. */
    public static final int BUTTON_SHAPE_BASE = 200;
    public static final int BUTTON_RESET = 250;
    /** 300..: toggle the modifier with that ordinal on the selected step. */
    public static final int BUTTON_MODIFIER_BASE = 300;
    /** Clears every modifier on the selected step. */
    public static final int BUTTON_CLEAR_MODIFIERS = 290;

    // ---- drag and drop ------------------------------------------------------------------------
    //
    // A drag has three dimensions (what, which step, which position) and a menu button carries one
    // int, so the payload is packed. The three ranges are far apart and each is bounded by
    // MAX_PER_STEP, which is what keeps them from ever colliding.
    //
    //   pack3(a, b, c) = ((a * MAX_STEPS) + b) * MAX_PER_STEP + c
    //
    /** The four drag ranges. Each payload is under 17 million, so 32-million spacing cannot collide. */
    public static final int BUTTON_INSERT_BASE = 100_000_000;
    public static final int BUTTON_REMOVE_AT_BASE = 200_000_000;
    public static final int BUTTON_MOVE_BASE = 300_000_000;
    public static final int BUTTON_SHAPE_AT_BASE = 400_000_000;
    /** 500_000_000+: insert a NEW delivery of shape {@code a} at position {@code b}. */
    public static final int BUTTON_INSERT_STEP_AT_BASE = 500_000_000;
    /** 600_000_000+: move the whole delivery {@code a} to position {@code b}. */
    public static final int BUTTON_MOVE_STEP_BASE = 600_000_000;
    /** How wide one payload range may be. */
    private static final int DRAG_RANGE = 32_000_000;

    /** Slots of room for components: 1024 is more than the enum will ever hold. */
    private static final int PACK_FIRST = 1024;
    private static final int PACK_INDEX = InscriptionTableBlockEntity.MAX_PER_STEP + 1;

    /** Pack "what, which step, which position" into one button id. */
    public static int pack3(int first, int second, int third) {
        int a = Math.max(0, Math.min(PACK_FIRST - 1, first));
        int b = Math.max(0, Math.min(InscriptionTableBlockEntity.MAX_STEPS - 1, second));
        int c = Math.max(0, Math.min(PACK_INDEX - 1, third));
        return (a * InscriptionTableBlockEntity.MAX_STEPS + b) * PACK_INDEX + c;
    }

    /** Inverse of {@link #pack3}. */
    public static int[] unpack3(int packed) {
        int c = packed % PACK_INDEX;
        int rest = packed / PACK_INDEX;
        return new int[]{rest / InscriptionTableBlockEntity.MAX_STEPS,
                rest % InscriptionTableBlockEntity.MAX_STEPS, c};
    }

    /**
     * Pack a move into one button id: from step/index to step/index.
     *
     * Two steps (3 bits each) and two positions (9 bits each) is 24 bits, which is why the payload
     * needs its own 32-million range rather than being folded into {@link #pack3}.
     */
    public static int pack4(int fromStep, int fromIndex, int toStep, int toIndex) {
        int fs = Math.max(0, Math.min(InscriptionTableBlockEntity.MAX_STEPS - 1, fromStep));
        int ts = Math.max(0, Math.min(InscriptionTableBlockEntity.MAX_STEPS - 1, toStep));
        int fi = Math.max(0, Math.min(PACK_INDEX - 1, fromIndex));
        int ti = Math.max(0, Math.min(PACK_INDEX - 1, toIndex));
        return ((fs * InscriptionTableBlockEntity.MAX_STEPS + ts) * PACK_INDEX + fi) * PACK_INDEX + ti;
    }

    /** Inverse of {@link #pack4}. */
    public static int[] unpack4(int packed) {
        int ti = packed % PACK_INDEX;
        int rest = packed / PACK_INDEX;
        int fi = rest % PACK_INDEX;
        int steps = rest / PACK_INDEX;
        return new int[]{steps / InscriptionTableBlockEntity.MAX_STEPS,
                fi, steps % InscriptionTableBlockEntity.MAX_STEPS, ti};
    }

    /** Index of the caster's current mana inside the synced array (after the table's own slots). */
    public static final int MANA_SLOT = InscriptionTableBlockEntity.DATA_SLOTS;
    /** Index of the caster's mana CAPACITY - the thing that gates a spell now. */
    public static final int CAPACITY_SLOT = InscriptionTableBlockEntity.DATA_SLOTS + 1;
    /** What pressing INSCRIBE would write onto, so the panel can say so before you commit. */
    public static final int TARGET_SLOT = InscriptionTableBlockEntity.DATA_SLOTS + 2;
    private static final int DATA_SIZE = InscriptionTableBlockEntity.DATA_SLOTS + 3;

    /** Values for {@link #TARGET_SLOT}. */
    public static final int TARGET_NONE = 0;
    public static final int TARGET_BOOK = 1;
    public static final int TARGET_BLUEPRINT = 2;
    public static final int TARGET_RIFLE = 3;
    public static final int TARGET_WARHEAD = 4;

    private final @Nullable InscriptionTableBlockEntity table;
    /** Where the table is, so the screen can address it in a payload. Null on the client until read. */
    private net.minecraft.core.BlockPos tablePos = net.minecraft.core.BlockPos.ZERO;
    private final ContainerData data;
    private final Inventory inventory;
    /** Last values pushed to this menu's viewers, so only real changes cost a packet. */
    private final int[] sent = new int[DATA_SIZE];
    private boolean sentAnything;

    /** Server side: opened by the block. */
    public InscriptionMenu(int containerId, Inventory inventory, @Nullable InscriptionTableBlockEntity table) {
        super(SCGMenus.INSCRIPTION.get(), containerId);
        this.table = table;
        if (table != null) {
            this.tablePos = table.getBlockPos();
        }
        this.inventory = inventory;
        this.data = table != null ? serverData(table) : new SimpleContainerData(DATA_SIZE);
        addDataSlots(this.data);
    }

    /** Plugin-free shape the MenuType requires; never used at run time. */
    public InscriptionMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, (InscriptionTableBlockEntity) null);
    }

    /**
     * Client side: rebuilt from the position the server wrote when it opened the screen.
     *
     * Deliberately passes NO table: see note 1 on the class. The client's data view must be a
     * SimpleContainerData so the network can write into it.
     */
    public InscriptionMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, (InscriptionTableBlockEntity) null);
        // The server writes its position into the open-screen buffer; keep it so the name box can
        // address the right table.
        if (buffer != null && buffer.readableBytes() >= 8) {
            this.tablePos = buffer.readBlockPos();
        }
    }

    /**
     * The server's view: the table's own slots, followed by two live values read from the player.
     *
     * Mana lives on the player, not on the block, but the screen still wants to draw "this page costs
     * 400 of your 620 capacity". Reading them here makes them live with no extra packet type.
     */
    private ContainerData serverData(InscriptionTableBlockEntity table) {
        ContainerData tableData = table.data();
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (index < InscriptionTableBlockEntity.DATA_SLOTS) {
                    return tableData.get(index);
                }
                if (!(inventory.player instanceof ServerPlayer serverPlayer)) {
                    return 0;
                }
                if (index == TARGET_SLOT) {
                    return targetOf(serverPlayer);
                }
                long value = index == MANA_SLOT ? ManaApi.current(serverPlayer)
                        : ManaApi.capacity(serverPlayer);
                // Split-safe: capacity can exceed Integer.MAX_VALUE only for absurd pools, and the
                // screen only needs a comparison, so clamp rather than overflow.
                return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
            }

            @Override
            public void set(int index, int value) {
                if (index < InscriptionTableBlockEntity.DATA_SLOTS) {
                    tableData.set(index, value);
                }
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
    }

    // ------------------------------------------------------------------ explicit sync

    /**
     * Push every changed slot to this menu's viewers.
     *
     * Called by the server once per tick and after every button press. Sending only the slots that
     * actually changed keeps a 258-slot array cheap; sending them explicitly means the draft cannot be
     * invisible, which is exactly what the old build managed to do.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(inventory.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        // Every slot, INCLUDING the two mana readouts at the end. Those are read live from the player on
        // the server, so the client only learns them from this push - leave them out and the cost panel
        // permanently reports "you: 0 / 0" and paints every spell as unaffordable.
        for (int slot = 0; slot < DATA_SIZE; slot++) {
            int value = data.get(slot);
            if (!sentAnything || sent[slot] != value) {
                sent[slot] = value;
                serverPlayer.connection.send(
                        new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(
                                this.containerId, slot, value));
            }
        }
        sentAnything = true;
    }

    // ------------------------------------------------------------------ read-only views for the screen

    /** The table's position, for client-to-server payloads. */
    public net.minecraft.core.BlockPos tablePos() {
        return tablePos;
    }

    public ContainerData data() {
        return data;
    }

    public int stepCount() {
        return Math.max(0, Math.min(InscriptionTableBlockEntity.MAX_STEPS,
                data.get(InscriptionTableBlockEntity.STEP_COUNT_SLOT)));
    }

    public int selectedStep() {
        return Math.max(0, Math.min(Math.max(0, stepCount() - 1),
                data.get(InscriptionTableBlockEntity.SELECTED_SLOT)));
    }

    public SpellShape stepShape(int stepIndex) {
        if (stepIndex < 0 || stepIndex >= stepCount()) {
            return SpellShape.PROJECTILE;
        }
        return SpellShape.values()[InscriptionTableBlockEntity.headerShape(
                data.get(InscriptionTableBlockEntity.STEP_HEADER_BASE + stepIndex))];
    }

    public int stepComponentCount(int stepIndex) {
        if (stepIndex < 0 || stepIndex >= stepCount()) {
            return 0;
        }
        return InscriptionTableBlockEntity.headerCount(
                data.get(InscriptionTableBlockEntity.STEP_HEADER_BASE + stepIndex));
    }

    /** The selected-step modifier bitmask for one step. */
    public int stepModifiers(int stepIndex) {
        if (stepIndex < 0 || stepIndex >= stepCount()) {
            return 0;
        }
        return data.get(InscriptionTableBlockEntity.MODIFIER_BASE_SLOT + stepIndex);
    }

    public SpellComponent stepComponentAt(int stepIndex, int within) {
        if (stepIndex < 0 || stepIndex >= stepCount() || within < 0) {
            return SpellComponent.PHYSICAL_DAMAGE;
        }
        int slot = InscriptionTableBlockEntity.COMPONENT_BASE_SLOT
                + stepIndex * InscriptionTableBlockEntity.MAX_PER_STEP + within;
        if (slot >= DATA_SIZE) {
            return SpellComponent.PHYSICAL_DAMAGE;
        }
        return SpellComponent.values()[Math.floorMod(data.get(slot), SpellComponent.values().length)];
    }

    /** Rebuilds the whole draft spell from the synced array. */
    public Spell previewSpell() {
        List<Spell.Step> steps = new ArrayList<>();
        for (int step = 0; step < stepCount(); step++) {
            List<SpellComponent> components = new ArrayList<>();
            for (int i = 0; i < stepComponentCount(step); i++) {
                components.add(stepComponentAt(step, i));
            }
            steps.add(new Spell.Step(stepShape(step), components, stepModifiers(step)));
        }
        return new Spell(steps);
    }

    /**
     * The spell as a FLAT list of parts, which is how it is drawn and how it is edited.
     *
     * Each entry is {@code {kind, ordinal, step, index}} with kind 0 = a shape part and 1 = a component
     * part. A shape part is drawn as a divider in the strip: everything after it belongs to it.
     */
    public List<int[]> flatParts() {
        List<int[]> parts = new ArrayList<>();
        for (int step = 0; step < stepCount(); step++) {
            parts.add(new int[]{0, stepShape(step).ordinal(), step, -1});
            int components = stepComponentCount(step);
            for (int i = 0; i < components; i++) {
                parts.add(new int[]{1, stepComponentAt(step, i).ordinal(), step, i});
            }
        }
        return parts;
    }

    /** One step's price, for the number drawn on its card. */
    public long previewStepCost(int stepIndex) {
        if (stepIndex < 0 || stepIndex >= stepCount()) {
            return 0L;
        }
        return previewSpell().steps().get(stepIndex).manaCost();
    }

    /** The caster's mana, as last synced. Client-safe: reads the synced array, never the server. */
    public long casterMana() {
        return data.get(MANA_SLOT);
    }

    /** What INSCRIBE would write onto: one of the TARGET_ constants. */
    public int inscribeTarget() {
        return data.get(TARGET_SLOT);
    }

    /** The caster's mana capacity, as last synced. */
    public long casterCapacity() {
        return data.get(CAPACITY_SLOT);
    }

    // ------------------------------------------------------------------ buttons

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (table == null) {
            return false;
        }
        if (id >= BUTTON_INSERT_BASE && id < BUTTON_INSERT_BASE + DRAG_RANGE) {
            int[] parts = unpack3(id - BUTTON_INSERT_BASE);
            table.insert(SpellComponent.values()[Math.floorMod(parts[0], SpellComponent.values().length)],
                    parts[1], parts[2]);
        } else if (id >= BUTTON_REMOVE_AT_BASE && id < BUTTON_REMOVE_AT_BASE + DRAG_RANGE) {
            int[] parts = unpack3(id - BUTTON_REMOVE_AT_BASE);
            table.removeAt(parts[1], parts[2]);
        } else if (id >= BUTTON_MOVE_BASE && id < BUTTON_MOVE_BASE + DRAG_RANGE) {
            int[] parts = unpack4(id - BUTTON_MOVE_BASE);
            table.move(parts[0], parts[1], parts[2], parts[3]);
        } else if (id >= BUTTON_SHAPE_AT_BASE && id < BUTTON_SHAPE_AT_BASE + DRAG_RANGE) {
            int[] parts = unpack3(id - BUTTON_SHAPE_AT_BASE);
            table.setShape(parts[1], SpellShape.values()[
                    Math.floorMod(parts[0], SpellShape.values().length)]);
        } else if (id >= BUTTON_INSERT_STEP_AT_BASE && id < BUTTON_INSERT_STEP_AT_BASE + DRAG_RANGE) {
            int[] parts = unpack3(id - BUTTON_INSERT_STEP_AT_BASE);
            table.insertStepAt(parts[1], SpellShape.values()[
                    Math.floorMod(parts[0], SpellShape.values().length)]);
        } else if (id >= BUTTON_MOVE_STEP_BASE && id < BUTTON_MOVE_STEP_BASE + DRAG_RANGE) {
            int[] parts = unpack3(id - BUTTON_MOVE_STEP_BASE);
            table.moveStep(parts[0], parts[1]);
        } else if (id >= BUTTON_ADD_STEP_BASE && id < BUTTON_ADD_STEP_BASE + SpellShape.values().length) {
            table.addStep(SpellShape.values()[id - BUTTON_ADD_STEP_BASE]);
        } else if (id >= BUTTON_COMPONENT_BASE
                && id < BUTTON_COMPONENT_BASE + SpellComponent.values().length) {
            table.add(SpellComponent.values()[id - BUTTON_COMPONENT_BASE]);
        } else if (id >= BUTTON_SELECT_STEP_BASE && id < BUTTON_SELECT_STEP_BASE + InscriptionTableBlockEntity.MAX_STEPS) {
            table.select(id - BUTTON_SELECT_STEP_BASE);
        } else if (id >= BUTTON_MODIFIER_BASE && id < BUTTON_MODIFIER_BASE + SpellModifier.values().length) {
            table.toggleModifier(SpellModifier.values()[id - BUTTON_MODIFIER_BASE]);
        } else if (id == BUTTON_CLEAR_MODIFIERS) {
            table.clearModifiers();
        } else if (id >= BUTTON_REMOVE_STEP_BASE && id < BUTTON_REMOVE_STEP_BASE + InscriptionTableBlockEntity.MAX_STEPS) {
            table.removeStep(id - BUTTON_REMOVE_STEP_BASE);
        } else if (id >= BUTTON_SHAPE_BASE && id < BUTTON_SHAPE_BASE + SpellShape.values().length) {
            // Legacy path: retarget the FIRST step. Kept because the old screen and any macro that used
            // it still sends these ids.
            int shape = id - BUTTON_SHAPE_BASE;
            if (table.steps().size() > 0) {
                table.select(0);
                table.addStep(SpellShape.values()[shape]);
                table.removeStep(0);
            }
        } else {
            switch (id) {
                case BUTTON_CLEAR -> table.clear();
                case BUTTON_POP -> table.pop();
                case BUTTON_RESET -> table.reset();
                case BUTTON_INSCRIBE -> inscribe(player);
                default -> {
                    return false;
                }
            }
        }
        broadcastChanges();
        return true;
    }

    /**
     * Write the draft onto whatever the player is holding.
     *
     * ──────────────────── THE PRICE ────────────────────
     *
     * The old build gated this on "spell level vs rifle tier" plus XP levels. That is gone: a spell is
     * now limited by MANA CAPACITY, which is what the user asked for. If the spell costs more than your
     * pool can hold you cannot write it down, and the fix is to grow your capacity (the Mana Capacity
     * enchantment), not to grind levels.
     *
     *   rifle   -> the spell is bound to the gun. Costs mana (full price) and demands the capacity to
     *              hold one full cast.
     *   warhead -> a COPY of the spell is loaded for firing. Costs money (quadratic in size) plus the
     *              same capacity demand.
     */
    private void inscribe(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        // The table's name box travels with the spell: blueprint, rifle and warhead all keep it.
        Spell spell = table.toSpell().withName(table.draftName());
        if (spell.isEmpty()) {
            say(player, "message.shadowclangaming.inscribe_empty", ChatFormatting.RED);
            return;
        }

        ItemStack held = heldTarget(serverPlayer);
        if (held.isEmpty()) {
            say(player, "message.shadowclangaming.inscribe_hold_rifle", ChatFormatting.RED);
            return;
        }

        long demand = spell.capacityDemand();
        long capacity = ManaApi.capacity(serverPlayer);

        // A BOOK AND QUILL becomes a spell blueprint. This is the intended path: the table writes the
        // spell down, and the page can then go into a rifle, a warhead, or nowhere at all.
        if (isBook(held)) {
            if (capacity < demand) {
                player.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.inscribe_need_capacity", demand, capacity)
                        .withStyle(ChatFormatting.RED), false);
                return;
            }
            if (!ManaApi.spend(serverPlayer, demand)) {
                player.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.inscribe_need_mana", demand)
                        .withStyle(ChatFormatting.RED), false);
                return;
            }
            held.shrink(1);
            ItemStack blueprint = blueprintFor(spell);
            if (!player.getInventory().add(blueprint)) {
                player.drop(blueprint, false);
            }
            player.displayClientMessage(Component.translatable(
                    "message.shadowclangaming.inscribed_blueprint",
                    spell.componentCount(), spell.stepCount()).withStyle(ChatFormatting.GOLD), false);
            return;
        }

        // An existing blueprint is simply rewritten in place.
        if (held.getItem() == SCGItems.SPELL_BLUEPRINT.get()) {
            if (capacity < demand) {
                player.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.inscribe_need_capacity", demand, capacity)
                        .withStyle(ChatFormatting.RED), false);
                return;
            }
            if (!ManaApi.spend(serverPlayer, demand)) {
                player.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.inscribe_need_mana", demand)
                        .withStyle(ChatFormatting.RED), false);
                return;
            }
            held.set(SCGDataComponents.SPELL.get(), spell);
            player.displayClientMessage(Component.translatable(
                    "message.shadowclangaming.inscribed_blueprint",
                    spell.componentCount(), spell.stepCount()).withStyle(ChatFormatting.GOLD), false);
            return;
        }

        if (held.getItem() instanceof com.shadowclangaming.item.SpellRifleItem) {
            if (capacity < demand) {
                player.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.inscribe_need_capacity", demand, capacity)
                        .withStyle(ChatFormatting.RED), false);
                return;
            }
            if (!ManaApi.spend(serverPlayer, demand)) {
                player.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.inscribe_need_mana", demand).withStyle(ChatFormatting.RED), false);
                return;
            }
            held.set(SCGDataComponents.SPELL.get(), spell);
            player.displayClientMessage(Component.translatable("message.shadowclangaming.inscribed_rifle",
                    spell.componentCount(), spell.stepCount()).withStyle(ChatFormatting.GOLD), false);
            return;
        }

        if (held.getItem() == SCGItems.SPELL_MISSILE.get()) {
            long fee = spell.copyFee();
            if (capacity < demand) {
                player.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.inscribe_need_capacity", demand, capacity)
                        .withStyle(ChatFormatting.RED), false);
                return;
            }
            if (!payFee(serverPlayer, fee)) {
                player.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.inscribe_need_money", fee).withStyle(ChatFormatting.RED), false);
                return;
            }
            held.set(SCGDataComponents.SPELL.get(), spell);
            player.displayClientMessage(Component.translatable("message.shadowclangaming.inscribed_warhead",
                    spell.componentCount(), fee).withStyle(ChatFormatting.GOLD), false);
            return;
        }

        say(player, "message.shadowclangaming.inscribe_hold_rifle", ChatFormatting.RED);
    }

    /** Money fee: whichever bank is installed, otherwise War Bonds (1 bond = 100 units). */
    private static boolean payFee(ServerPlayer player, long fee) {
        if (Money.available()) {
            return Money.spend(player.serverLevel().getServer(), player.getUUID(), fee);
        }
        int bondsNeeded = (int) Math.ceil(fee / 100.0D);
        int bonds = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == SCGItems.WAR_BOND.get()) {
                bonds += stack.getCount();
            }
        }
        if (bonds < bondsNeeded) {
            return false;
        }
        int toRemove = bondsNeeded;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && toRemove > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == SCGItems.WAR_BOND.get()) {
                int take = Math.min(toRemove, stack.getCount());
                stack.shrink(take);
                toRemove -= take;
            }
        }
        return true;
    }

    /**
     * What the spell is being written onto: the held item, or - if you are holding something useless -
     * any rifle or spell warhead in the hotbar. Quality-of-life, not a loophole: only the player's own
     * hotbar is searched.
     */
    private static ItemStack heldTarget(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof com.shadowclangaming.item.SpellRifleItem
                || held.getItem() == SCGItems.SPELL_MISSILE.get()
                || held.getItem() == SCGItems.SPELL_BLUEPRINT.get()
                || isBook(held)) {
            return held;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.getItem() instanceof com.shadowclangaming.item.SpellRifleItem
                    || candidate.getItem() == SCGItems.SPELL_MISSILE.get()) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
    }

    /** A book and quill, signed or not: both are acceptable to write on. */
    private static boolean isBook(ItemStack stack) {
        return stack.is(net.minecraft.world.item.Items.WRITABLE_BOOK)
                || stack.is(net.minecraft.world.item.Items.WRITTEN_BOOK);
    }

    /** Build the item a book becomes. */
    private static ItemStack blueprintFor(Spell spell) {
        ItemStack blueprint = new ItemStack(SCGItems.SPELL_BLUEPRINT.get());
        blueprint.set(SCGDataComponents.SPELL.get(), spell);
        return blueprint;
    }

    /** What INSCRIBE would write onto right now. Read by the screen's cost panel. */
    public static int targetOf(ServerPlayer player) {
        ItemStack held = heldTarget(player);
        if (held.isEmpty()) {
            return TARGET_NONE;
        }
        if (isBook(held)) {
            return TARGET_BOOK;
        }
        if (held.getItem() == SCGItems.SPELL_BLUEPRINT.get()) {
            return TARGET_BLUEPRINT;
        }
        if (held.getItem() instanceof com.shadowclangaming.item.SpellRifleItem) {
            return TARGET_RIFLE;
        }
        return held.getItem() == SCGItems.SPELL_MISSILE.get() ? TARGET_WARHEAD : TARGET_NONE;
    }

    private static void say(Player player, String key, ChatFormatting colour) {
        player.displayClientMessage(Component.translatable(key).withStyle(colour), false);
    }

    // ------------------------------------------------------------------ container plumbing

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return table == null || table.getLevel() != null
                && player.distanceToSqr(table.getBlockPos().getX() + 0.5D,
                table.getBlockPos().getY() + 0.5D, table.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }
}
