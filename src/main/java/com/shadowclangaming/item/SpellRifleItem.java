package com.shadowclangaming.item;

import com.shadowclangaming.blueprint.BlueprintMath;
import com.shadowclangaming.magic.ManaApi;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.spell.Spell;
import com.shadowclangaming.spell.SpellCasting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A magic rifle.
 *
 * It is NOT crafted. It is built: mark a box with the blueprint table, build the frame out of blocks
 * with an Emitter as the muzzle, and the table eats the build and hands you this. Everything about
 * the gun - how exotic a spell it can hold, and what that spell costs to fire - comes from what you
 * made it out of (see BlueprintMath).
 *
 * The spell itself is inscribed at the inscription table and stored in a data component, so a rifle
 * can be handed to another player, put in a chest, or dropped in lava with its spell intact.
 */
public class SpellRifleItem extends Item {

    /** Ticks between shots for a single-emitter rifle. Every extra emitter takes 5% off this. */
    public static final int COOLDOWN_TICKS = 12;

    public SpellRifleItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ firing

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Purely server-side: the client shows nothing, the server decides everything.
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        ItemStack stack = player.getItemInHand(hand);
        Spell spell = stack.get(SCGDataComponents.SPELL.get());
        if (spell == null || spell.isEmpty()) {
            actionBar(player, "message.shadowclangaming.rifle_empty", ChatFormatting.GRAY);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        // No spell levels any more: the gate is whether the caster's POOL can hold one full cast of this
        // spell. A rifle cannot be loaded with something bigger than its owner, and this re-checks in
        // case the capacity was lost after inscribing (blood pact, holy water, a respec).
        long demand = spell.capacityDemand();
        if (ManaApi.capacity(serverPlayer) < demand) {
            actionBar(player, "message.shadowclangaming.rifle_overheat", ChatFormatting.RED);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        int emitters = emittersOf(stack);
        int barrel = barrelOf(stack);
        int assemblyLevel = clusterLevelOf(stack, barrel);
        int strength = strengthOf(stack);
        // Power and price both come from the assembly the shot fires out of: a 27-emitter cube hits for
        // 11.9x and costs 3.56x, a lone emitter is the baseline 1.0/1.0. On top of that sit the bench's
        // two dials: barrels (+2% power each) and strength (+3% power, +2% mana each).
        float power = (float) (BlueprintMath.clusterPower(assemblyLevel)
                * BlueprintMath.barrelPower(emitters)
                * BlueprintMath.strengthPower(strength));
        long cost = Math.round(BlueprintMath.shotCostForCluster(spell.rawManaCost(), efficiencyOf(stack),
                emitters, assemblyLevel) * BlueprintMath.strengthMana(strength));
        if (!ManaApi.spend(serverPlayer, cost)) {
            actionBar(player, "message.shadowclangaming.rifle_no_mana", ChatFormatting.RED);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        player.getCooldowns().addCooldown(stack.getItem(), cooldownTicksOf(stack));
        // Multi-barrel rotation: the NEXT shot leaves from the next emitter. Stored on the stack so the
        // cycle survives being dropped, traded or put in a chest - it is part of the gun, not the player.
        stack.set(SCGDataComponents.RIFLE_BARREL.get(), (barrel + 1) % emitters);
        if (!SpellCasting.castFromRifle(serverPlayer, spell, power)) {
            // Nothing to cast after all - refund, because a wasted cast should not cost mana.
            ManaApi.addMana(serverPlayer, cost);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        // Casting is how a wizard grows (see ManaApi.onSpellCast).
        ManaApi.onSpellCast(serverPlayer, Math.min(9, spell.componentCount()));
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    private static void actionBar(Player player, String key, ChatFormatting colour) {
        player.displayClientMessage(Component.translatable(key).withStyle(colour), true);
    }

    // ------------------------------------------------------------------ stats

    /** How exotic a spell this frame can hold. Set by the blueprint table. */
    public static int tierOf(ItemStack stack) {
        Integer tier = stack.get(SCGDataComponents.RIFLE_TIER.get());
        return tier == null ? BlueprintMath.MIN_TIER : tier;
    }

    /** How many Emitters the build had - the barrel count. Always at least 1. */
    public static int emittersOf(ItemStack stack) {
        Integer emitters = stack.get(SCGDataComponents.RIFLE_EMITTERS.get());
        if (emitters != null && emitters > 0) {
            return emitters;
        }
        // Older rifles (or ones made before the field existed) read it back off the recorded build.
        com.shadowclangaming.blueprint.BuildShape shape = stack.get(SCGDataComponents.RIFLE_BUILD.get());
        return shape == null ? 1 : shape.emitters();
    }

    /** Which emitter fires next - the rotation index, 0..emitters-1. */
    public static int barrelOf(ItemStack stack) {
        Integer barrel = stack.get(SCGDataComponents.RIFLE_BARREL.get());
        int emitters = emittersOf(stack);
        return barrel == null ? 0 : Math.floorMod(barrel, emitters);
    }

    /**
     * How big the assembly behind barrel {@code index} is.
     *
     * Emitters that touch form one cluster, and the cluster size is the level: 1 for a lone emitter, 9
     * for a 3x3 plate, 27 for a 3x3x3 cube. Firing from a big cluster hits harder and costs more.
     */
    public static int clusterLevelOf(ItemStack stack, int barrelIndex) {
        start:
        {
            com.shadowclangaming.blueprint.BuildShape shape = buildOf(stack);
            int[] levels = shape.emitterLevels();
            if (levels.length == 0) {
                break start;
            }
            return levels[Math.floorMod(barrelIndex, levels.length)];
        }
        // A rifle with no recorded build (creative, commands) has no geometry to cluster: gate it to 1.
        return 1;
    }

    /** The biggest assembly in the whole gun - the headline number on the tooltip. */
    public static int largestClusterOf(ItemStack stack) {
        return buildOf(stack).largestEmitterLevel();
    }

    /** Ticks between shots for this particular rifle. */
    public static int cooldownOf(ItemStack stack) {
        return BlueprintMath.cooldownTicks(COOLDOWN_TICKS, emittersOf(stack));
    }

    /** Barrel strength set at the bench: 1..128. Heavier barrels hit harder and cycle slower. */
    public static int strengthOf(ItemStack stack) {
        Integer strength = stack.get(SCGDataComponents.RIFLE_STRENGTH.get());
        return strength == null || strength < 1 ? 1 : Math.min(BlueprintMath.MAX_STRENGTH, strength);
    }

    /** Cooldown including the strength penalty: a 128-strength barrel is 2.27x slower to cycle. */
    public static int cooldownTicksOf(ItemStack stack) {
        int base = (int) Math.round(COOLDOWN_TICKS
                * BlueprintMath.strengthCooldown(strengthOf(stack)));
        return BlueprintMath.cooldownTicks(base, emittersOf(stack));
    }

    /** The build this rifle was made from, or a stand-in shape for a rifle that was never built. */
    public static com.shadowclangaming.blueprint.BuildShape buildOf(ItemStack stack) {
        com.shadowclangaming.blueprint.BuildShape shape = stack.get(SCGDataComponents.RIFLE_BUILD.get());
        return shape == null || shape.isEmpty()
                ? com.shadowclangaming.blueprint.BuildShape.defaultRifle()
                : shape;
    }

    /** Percentage of the spell's cost this frame actually charges. Set by the blueprint table. */
    public static int efficiencyOf(ItemStack stack) {
        Integer efficiency = stack.get(SCGDataComponents.RIFLE_EFFICIENCY.get());
        return efficiency == null ? BlueprintMath.MAX_EFFICIENCY : efficiency;
    }

    // ------------------------------------------------------------------ tooltip

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.shadowclangaming.rifle.tier", tierOf(stack))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.rifle.efficiency", efficiencyOf(stack))
                .withStyle(ChatFormatting.GOLD));
        int emitters = emittersOf(stack);
        int strength = strengthOf(stack);
        tooltip.add(Component.translatable("tooltip.shadowclangaming.rifle.barrels", emitters,
                        cooldownTicksOf(stack))
                .withStyle(emitters > 1 ? ChatFormatting.AQUA : ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.rifle.strength", strength,
                        String.format("x%.2f", BlueprintMath.strengthPower(strength)),
                        String.format("x%.2f", BlueprintMath.strengthMana(strength)))
                .withStyle(strength > 1 ? ChatFormatting.RED : ChatFormatting.GOLD));
        if (emitters > 1) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.rifle.barrel_surcharge",
                            String.format("%.1f", (BlueprintMath.manaMultiplier(emitters) - 1.0D) * 100.0D),
                            barrelOf(stack) + 1, emitters)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        int largest = largestClusterOf(stack);
        int firing = clusterLevelOf(stack, barrelOf(stack));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.rifle.assembly",
                        firing, String.format("%.2f", BlueprintMath.clusterPower(firing)))
                .withStyle(firing > 1 ? ChatFormatting.RED : ChatFormatting.GOLD));
        if (largest > firing) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.rifle.assembly_largest", largest)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.shadowclangaming.rifle.cluster_cost",
                        String.format("%.2f", BlueprintMath.clusterMana(firing)))
                .withStyle(ChatFormatting.DARK_GRAY));

        Spell spell = stack.get(SCGDataComponents.SPELL.get());
        if (spell == null || spell.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.rifle.no_spell")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        // The sequence, top to bottom: "projectile: fire damage > zone: ... > rune: ignite".
        tooltip.add(Component.translatable("tooltip.shadowclangaming.rifle.sequence",
                        spell.stepCount(), spell.componentCount())
                .withStyle(ChatFormatting.AQUA));
        for (int stepIndex = 0; stepIndex < spell.stepCount() && tooltip.size() < 24; stepIndex++) {
            Spell.Step step = spell.steps().get(stepIndex);
            StringBuilder line = new StringBuilder("  ").append(stepIndex + 1).append(". ")
                    .append(step.shape().id());
            if (!step.components().isEmpty()) {
                line.append(": ");
                for (int i = 0; i < step.components().size(); i++) {
                    if (i > 0) {
                        line.append(", ");
                    }
                    line.append(step.components().get(i).id().replace('_', ' '));
                }
            }
            tooltip.add(Component.literal(line.toString()).withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("tooltip.shadowclangaming.rifle.capacity", spell.capacityDemand())
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.rifle.cost",
                        BlueprintMath.shotCostForCluster(spell.rawManaCost(), efficiencyOf(stack),
                                emitters, clusterLevelOf(stack, barrelOf(stack))))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
