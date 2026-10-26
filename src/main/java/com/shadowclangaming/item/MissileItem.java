package com.shadowclangaming.item;

import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.spell.Spell;
import com.shadowclangaming.warhead.ForceFields;
import com.shadowclangaming.warhead.Funding;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.shadowclangaming.warhead.MissilePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A missile, as an item.
 *
 * The item is a shell: what it *does* when it lands is decided by its {@link WarheadType}. All the
 * variants in the mod are the same class with a different enum entry, so adding a warhead is one line
 * in SCGItems.
 *
 * powerScale lets one warhead type cover several strengths (small/medium/heavy HE, for example).
 */
public class MissileItem extends Item {
    private final WarheadType warhead;
    private final double cruiseSpeed;
    private final float powerScale;

    public MissileItem(Properties properties, WarheadType warhead, double cruiseSpeed) {
        this(properties, warhead, cruiseSpeed, 1.0F);
    }

    public MissileItem(Properties properties, WarheadType warhead, double cruiseSpeed, float powerScale) {
        super(properties);
        this.warhead = warhead;
        this.cruiseSpeed = cruiseSpeed;
        this.powerScale = powerScale;
    }

    public WarheadType warhead() {
        return warhead;
    }

    /**
     * The plain missile item that carries this warhead.
     *
     * Found by scanning the registry rather than by a switch, so adding a warhead item never means
     * editing a second table. Modules are skipped: pulling a warhead out of a module must give back a
     * missile, not another module.
     */
    @Nullable
    public static net.minecraft.world.item.Item itemFor(WarheadType type) {
        for (net.minecraft.world.item.Item candidate : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (candidate instanceof MissileItem missile
                    && !(candidate instanceof com.shadowclangaming.item.MissileModuleItem)
                    && missile.warhead() == type) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Rebuild a plain missile from a fitted module, carrying the warhead's own contents with it.
     *
     * Used when a player pulls a warhead back out of a module: the money, the spell and the payload
     * belong to the warhead, not the airframe, so they travel back with it.
     */
    public static ItemStack plainFrom(ItemStack module) {
        net.minecraft.world.item.Item item = itemFor(
                com.shadowclangaming.item.MissileModuleItem.warheadOf(module));
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack out = new ItemStack(item);
        Long funding = module.get(SCGDataComponents.FUNDING.get());
        if (funding != null) {
            out.set(SCGDataComponents.FUNDING.get(), funding);
        }
        Spell spell = module.get(SCGDataComponents.SPELL.get());
        if (spell != null) {
            out.set(SCGDataComponents.SPELL.get(), spell);
        }
        List<ItemStack> payload = module.get(SCGDataComponents.PAYLOAD.get());
        if (payload != null && !payload.isEmpty()) {
            out.set(SCGDataComponents.PAYLOAD.get(), List.copyOf(payload));
        }
        return out;
    }

    /** Blocks per tick the missile steers towards. ~1.0 is fast. */
    public double cruiseSpeed() {
        return cruiseSpeed;
    }

    /**
     * The final explosion power for this missile.
     * Numismatic warheads ignore the fixed power and use the loaded currency instead.
     */
    /** The spell inside a SPELL warhead, or null for any other warhead. */
    public static Spell spellOf(ItemStack stack) {
        return stack.get(SCGDataComponents.SPELL.get());
    }

    public float powerFor(ItemStack stack) {
        if (warhead == WarheadType.NUMISMATIC) {
            return Funding.powerForFunding(fundingOf(stack));
        }
        if (warhead == WarheadType.BULWARK) {
            return 0.0F; // a dome is not an explosion
        }
        return warhead.basePower() * powerScale;
    }

    public static long fundingOf(ItemStack stack) {
        Long funding = stack.get(SCGDataComponents.FUNDING.get());
        return funding == null ? 0L : funding;
    }

    public static void setFunding(ItemStack stack, long funding) {
        stack.set(SCGDataComponents.FUNDING.get(), Math.max(0L, funding));
    }

    /**
     * RIGHT CLICK A CARGO MISSILE TO OPEN ITS HOLD.
     *
     * This replaced the offhand-loading gesture. A chest GUI is what "load a missile" should look like:
     * 27 slots, shift-click in and out, and you can see what is in there - and because the hold is
     * written per slot through a {@link com.shadowclangaming.warhead.CargoContainer}, an empty slot is
     * simply not persisted, which removes the class of bug where inserted items came back as air.
     *
     * Non-cargo warheads have no hold: they say so and point at the launch pad.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack missile = player.getItemInHand(hand);

        // The spatial warhead carries CELLS, so its hold is a drive bay grid rather than a chest. It is
        // still a payload, just a specialised one - and it is the only warhead whose items come from a
        // specific mod, so it gets the interface that mod's players already know.
        if (warhead == WarheadType.SPATIAL) {
            if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer server) {
                server.openMenu(new net.minecraft.world.SimpleMenuProvider(
                                (containerId, inventory, ignored) ->
                                        new com.shadowclangaming.warhead.SpatialCellMenu(
                                                containerId, inventory, missile),
                                Component.translatable("container.shadowclangaming.spatial_cells")),
                        buffer -> buffer.writeBoolean(hand == InteractionHand.MAIN_HAND));
            }
            return InteractionResultHolder.sidedSuccess(missile, level.isClientSide);
        }

        if (!acceptsPayload()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.missile_no_inventory"), true);
            }
            return InteractionResultHolder.success(missile);
        }

        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer server) {
            server.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (containerId, inventory, ignored) ->
                            new com.shadowclangaming.warhead.CargoMissileMenu(containerId, inventory, missile),
                    Component.translatable(warhead == WarheadType.CONSTRUCTOR
                            ? "container.shadowclangaming.constructor_missile"
                            : "container.shadowclangaming.cargo_missile")),
                    buffer -> buffer.writeBoolean(hand == InteractionHand.MAIN_HAND));
        }
        return InteractionResultHolder.sidedSuccess(missile, level.isClientSide);
    }

    /**
     * Which warheads carry an inventory.
     *
     * CARGO and CONSTRUCTOR. A cargo missile is a courier - its whole identity is "what is inside it",
     * and the constructor is the same object with a different job: it is a delivery of BUILDING
     * MATERIALS. Both deserve a chest you open and fill; making the constructor's payload reachable only
     * through the launch pad was the odd one out.
     *
     * SPATIAL still does not, deliberately: its payload is a single AE2 spatial cell, which arrives
     * through the pad's hold so the cell is dropped whole rather than unpacked. A 27-slot grid for one
     * item would be a worse UI than the one it replaces.
     */
    public boolean acceptsPayload() {
        return warhead == WarheadType.CARGO || warhead == WarheadType.CONSTRUCTOR;
    }

    private boolean canHold(ItemStack stack) {
        if (!acceptsPayload()) {
            return false;
        }
        // A spatial warhead only wants a spatial cell; everything else takes any item.
        if (warhead == WarheadType.SPATIAL) {
            return com.shadowclangaming.warhead.Ae2Cells.isSpatialCell(stack);
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(warhead.translationKey()).withStyle(ChatFormatting.LIGHT_PURPLE));

        // Apex warheads explain themselves: seventeen weapons that all look like "a big missile" need a
        // line of prose or the choice is guesswork.
        com.shadowclangaming.warhead.apex.Apex apex =
                com.shadowclangaming.warhead.apex.Apex.byId(warhead.id());
        if (apex != null) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.apex." + apex.id())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.shadowclangaming.apex.warning")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        if (warhead == WarheadType.NUMISMATIC) {
            long funding = fundingOf(stack);
            int radius = Funding.radiusForFunding(funding);
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.funding", funding)
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.funding_radius", radius)
                    .withStyle(radius > 0 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.funding_next",
                    String.format("%,d", Funding.costForRadius(radius + 1L))).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.funding_4096",
                    String.format("%,d", Funding.TARGET_COST),
                    String.format("%.1f", Funding.sunsFor(Funding.TARGET_COST)))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (warhead == WarheadType.BULWARK) {
            long funding = fundingOf(stack);
            int radius = ForceFields.radiusForFunding(funding);
            int ticks = ForceFields.durationTicksForFunding(funding);
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.funding", funding)
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.field",
                    radius, String.format("%.1f", ticks / 20.0D))
                    .withStyle(funding > 0L ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.field_next",
                    String.format("%,d", ForceFields.fundingForRadius(radius + 1)))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (warhead == WarheadType.CONTAGION) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.contagion")
                    .withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.power", powerFor(stack))
                    .withStyle(ChatFormatting.GRAY));
        } else if (warhead.basePower() > 0.0F) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.power", powerFor(stack))
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.special")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (acceptsPayload()) {
            int held = MissilePayload.count(stack);
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.payload",
                    held, MissilePayload.MAX_STACKS)
                    .withStyle(held > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
        if (warhead == WarheadType.CONSTRUCTOR) {
            // Its own 27-slot hold, plus the radius slider that lives in the same screen.
            int chosen = MissilePayload.radiusOf(stack);
            tooltip.add(Component.translatable(chosen >= 0
                            ? "tooltip.shadowclangaming.missile.sphere_override"
                            : "tooltip.shadowclangaming.missile.sphere_slider",
                    chosen >= 0 ? chosen : -1).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.sphere_gui")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (warhead == WarheadType.SPATIAL) {
            // The spatial warhead opens its own cell drive: right-click to load cells.
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.cell_spatial")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.cell_drive")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.usage").withStyle(ChatFormatting.DARK_GRAY));
        if (acceptsPayload()) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.load_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
