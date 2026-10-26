package com.shadowclangaming.item;

import com.shadowclangaming.compat.economy.Money;
import com.shadowclangaming.launcher.ModuleKind;
import com.shadowclangaming.launcher.ModuleTier;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.spell.Spell;
import com.shadowclangaming.warhead.Ae2Cells;
import com.shadowclangaming.warhead.Funding;
import com.shadowclangaming.warhead.MissilePayload;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A missile module: the airframe. The warhead is what you fit into it.
 *
 * THE SHAPE OF THE SYSTEM
 *   module tier  ->  how far it can fly        (T1 10,000 blocks ... T5 unlimited)
 *   warhead     ->  what happens when it lands (any warhead fits any tier)
 *
 * That separation is the whole point: a fat warhead on a T1 frame is a short-range sledgehammer, and a
 * cheap warhead on a T5 frame is a strategic threat. Neither choice locks the other.
 *
 * WHY IT EXTENDS {@link MissileItem}. Everything downstream - the launch pad, the flight entity, the
 * funding and payload components, the cargo GUI - already speaks {@code MissileItem}. A module is a
 * missile whose warhead happens to live in a component instead of a field, so subclassing keeps one
 * code path for flight and puts the difference where it belongs: {@link #warhead()} and
 * {@link #powerFor(ItemStack)}.
 *
 * FITTING A WARHEAD. Right-click the module with a missile in the other hand: the missile is consumed and
 * its warhead, funding, spell and payload move onto the module. Right-click with an empty hand to see
 * what is fitted; sneak + empty hand to pull the warhead back out as the original missile item.
 *
 * Firing an EMPTY module is allowed - see {@link WarheadType#DUD}.
 */
public class MissileModuleItem extends MissileItem {

    private final ModuleTier tier;
    private final ModuleKind kind;

    public MissileModuleItem(Properties properties, ModuleTier tier) {
        this(properties, tier, ModuleKind.STANDARD);
    }

    public MissileModuleItem(Properties properties, ModuleTier tier, ModuleKind kind) {
        // The warhead passed to the superclass is only a fallback for call sites that ask the ITEM rather
        // than the STACK; the real answer is warheadOf(stack).
        super(properties, WarheadType.DUD, 1.0D);
        this.tier = tier;
        this.kind = kind == null ? ModuleKind.STANDARD : kind;
    }

    public ModuleTier tier() {
        return tier;
    }

    /** What this frame is for: standard, homing, cluster, high-velocity or transport. */
    public ModuleKind kind() {
        return kind;
    }

    /**
     * The kind of an arbitrary stack.
     *
     * Everything that flies asks this one question, so a plain missile (no frame at all) and a T3 airframe
     * both answer safely with {@link ModuleKind#STANDARD}.
     */
    public static ModuleKind kindOf(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof MissileModuleItem module) {
            return module.kind();
        }
        return ModuleKind.STANDARD;
    }

    /** The warhead fitted to this stack, or {@link WarheadType#DUD} when the module is empty. */
    public static WarheadType warheadOf(ItemStack stack) {
        String id = stack.get(SCGDataComponents.MODULE_WARHEAD.get());
        return id == null || id.isBlank() ? WarheadType.DUD : WarheadType.byId(id);
    }

    public static boolean isFitted(ItemStack stack) {
        return warheadOf(stack) != WarheadType.DUD;
    }

    /** Modules have no fixed warhead: this reads the stack when there is one, else reports empty. */
    @Override
    public WarheadType warhead() {
        return WarheadType.DUD;
    }

    /**
     * Explosion power for the FITTED warhead.
     *
     * Mirrors {@link MissileItem#powerFor(ItemStack)} but reads the warhead from the component, which is
     * the only behavioural difference between a module and a plain missile.
     */
    @Override
    public float powerFor(ItemStack stack) {
        WarheadType type = warheadOf(stack);
        return switch (type) {
            case NUMISMATIC -> Funding.powerForFunding(fundingOf(stack));
            case BULWARK, DUD, CARGO, MINEFIELD, SPELL, CONSTRUCTOR, SPATIAL, CLUCKER,
                 VIOLATOR, BEEF, DECOY, EMP -> 0.0F;
            default -> type.basePower();
        };
    }

    // ------------------------------------------------------------------ fitting a warhead

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack module = player.getItemInHand(hand);
        InteractionHand other = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack incoming = player.getItemInHand(other);

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(module, true);
        }

        // Empty hand + sneak: pull the warhead back out as a plain missile.
        if (incoming.isEmpty() && player.isShiftKeyDown()) {
            if (!isFitted(module)) {
                player.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.module_empty"), true);
                return InteractionResultHolder.success(module);
            }
            ItemStack extracted = MissileItem.plainFrom(module);
            if (!player.getInventory().add(extracted)) {
                player.drop(extracted, false);
            }
            module.remove(SCGDataComponents.MODULE_WARHEAD.get());
            module.remove(SCGDataComponents.FUNDING.get());
            module.remove(SCGDataComponents.SPELL.get());
            module.remove(SCGDataComponents.PAYLOAD.get());
            player.displayClientMessage(Component.translatable(
                    "message.shadowclangaming.module_unfitted", extracted.getHoverName()), true);
            return InteractionResultHolder.success(module);
        }

        if (incoming.getItem() instanceof MissileItem donor && !(incoming.getItem() instanceof MissileModuleItem)) {
            WarheadType type = donor.warhead();

            // The frame has a say. Cargo on a cluster frame would deliver the hold once per sub-munition
            // (item duplication with extra steps), and a transport frame carries people, not warheads.
            // Refusing at the bench - with the reason - beats refusing at the impact point.
            if (!kind.allowsWarhead(type.id())) {
                player.displayClientMessage(Component.translatable(
                        kind == ModuleKind.TRANSPORT
                                ? "message.shadowclangaming.module_transport_no_warhead"
                                : "message.shadowclangaming.module_no_cargo_warhead",
                        Component.translatable(kind.translationKey())), true);
                return InteractionResultHolder.success(module);
            }
            module.set(SCGDataComponents.MODULE_WARHEAD.get(), type.id());

            // The donor's contents come along: funding, spell and payload are the warhead's, not the
            // frame's, so a funded Numismatic warhead keeps its money when it is moved into a module.
            Long funding = incoming.get(SCGDataComponents.FUNDING.get());
            if (funding != null) {
                module.set(SCGDataComponents.FUNDING.get(), funding);
            }
            Spell spell = incoming.get(SCGDataComponents.SPELL.get());
            if (spell != null) {
                module.set(SCGDataComponents.SPELL.get(), spell);
            }
            List<ItemStack> payload = incoming.get(SCGDataComponents.PAYLOAD.get());
            if (payload != null && !payload.isEmpty()) {
                module.set(SCGDataComponents.PAYLOAD.get(), List.copyOf(payload));
            }
            if (!player.getAbilities().instabuild) {
                incoming.shrink(1);
            }
            player.displayClientMessage(Component.translatable(
                    "message.shadowclangaming.module_fitted",
                    Component.translatable(type.translationKey()), tier.rangeLabel()), true);
            return InteractionResultHolder.success(module);
        }

        // Nothing useful in hand: report the state.
        player.displayClientMessage(Component.translatable(isFitted(module)
                        ? "message.shadowclangaming.module_state"
                        : "message.shadowclangaming.module_empty",
                Component.translatable(warheadOf(module).translationKey()), tier.rangeLabel()), true);
        return InteractionResultHolder.success(module);
    }

    // ------------------------------------------------------------------ tooltip

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        WarheadType type = warheadOf(stack);
        tooltip.add(Component.translatable("tooltip.shadowclangaming.module.tier", tier.tier())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.module.range", tier.rangeLabel())
                .withStyle(tier.isInfinite() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        if (kind.special()) {
            tooltip.add(Component.translatable(kind.translationKey())
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable(kind.translationKey() + ".desc")
                    .withStyle(ChatFormatting.GRAY));
        }

        if (isFitted(stack)) {
            tooltip.add(Component.translatable(type.translationKey()).withStyle(ChatFormatting.LIGHT_PURPLE));
            long funding = MissileItem.fundingOf(stack);
            if (funding > 0L) {
                tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.funding", funding)
                        .withStyle(ChatFormatting.GOLD));
            }
            int payload = MissilePayload.count(stack);
            if (payload > 0) {
                tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.payload",
                        payload, MissilePayload.MAX_STACKS).withStyle(ChatFormatting.GREEN));
            }
            if (Ae2Cells.containsCell(MissilePayload.of(stack))) {
                tooltip.add(Component.translatable("tooltip.shadowclangaming.missile.cell_dropped")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        } else if (kind.transport()) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.module.transport_hold")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.module.blank")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        tooltip.add(Component.translatable("tooltip.shadowclangaming.module.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.module.ride")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Convenience for the pad's display strings: warhead, frame and range on one line. */
    public static String describe(ItemStack stack) {
        WarheadType type = warheadOf(stack);
        String fitted = type == WarheadType.DUD ? "empty" : type.id();
        if (!(stack.getItem() instanceof MissileModuleItem module)) {
            return fitted + " / unlimited";
        }
        String kindPart = module.kind().special() ? " / " + module.kind().id() : "";
        return fitted + kindPart + " / " + module.tier().rangeLabel();
    }

    /** Money helper for callers that format the same refusal strings. */
    static String money(net.minecraft.server.MinecraftServer server, long amount) {
        return Money.format(server, amount);
    }
}
