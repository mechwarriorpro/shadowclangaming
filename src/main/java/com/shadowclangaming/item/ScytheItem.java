package com.shadowclangaming.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Short-haft short-blade Crimson Reaper — photorealistic dual-wield kama/scythes.
 *
 * Single scythe is strong on its own. Dual-wielding (one in each hand)
 * grants stacking bonuses: faster attack, lifesteal, sweep, and movement.
 *
 * Also registered as "dual_scythe" — a paired inventory item that visually
 * represents both blades at once for players who prefer one slot.
 */
public class ScytheItem extends Item {

    public static final int DUAL_CHECK_TICKS = 10;
    public static final float SINGLE_DAMAGE = 8.0F;
    public static final float DUAL_DAMAGE_BONUS = 4.0F;
    public static final float DUAL_SPEED_BONUS = 0.8F;

    public ScytheItem(Properties properties) {
        super(properties);
    }

    /** Build properties with sword-like combat attributes. */
    public static Properties combatProps(boolean isDual) {
        float damage = isDual ? SINGLE_DAMAGE + DUAL_DAMAGE_BONUS : SINGLE_DAMAGE;
        // attack speed: base -2.4 is sword-like; dual is faster (-1.6)
        float speed = isDual ? -1.6F : -2.2F;
        ItemAttributeModifiers mods = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                // offhand scythe also contributes when swinging offhand
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage * 0.6F, AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.OFFHAND)
                .build();
        return new Properties()
                .stacksTo(1)
                .durability(isDual ? 2500 : 1800)
                .attributes(mods)
                .component(DataComponents.TOOL, createToolProperties());
    }

    private static net.minecraft.world.item.component.Tool createToolProperties() {
        // Can harvest like sword + scythe: cobwebs, plants, etc.
        return new net.minecraft.world.item.component.Tool(
                List.of(
                        net.minecraft.world.item.component.Tool.Rule.minesAndDrops(
                                List.of(net.minecraft.world.level.block.Blocks.COBWEB), 15.0F),
                        net.minecraft.world.item.component.Tool.Rule.overrideSpeed(
                                net.minecraft.tags.BlockTags.SWORD_EFFICIENT, 1.8F)
                ), 1.0F, 1);
    }

    public static boolean isScythe(ItemStack stack) {
        return stack.getItem() instanceof ScytheItem;
    }

    public static boolean isDualWielding(Player player) {
        return isScythe(player.getMainHandItem()) && isScythe(player.getOffhandItem());
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean dual = attacker instanceof Player p && isDualWielding(p);
        float bonus = dual ? DUAL_DAMAGE_BONUS : 0;

        // Lifesteal: heal attacker
        if (attacker instanceof Player player) {
            float heal = 1.0F + (dual ? 2.0F : 0.5F);
            player.heal(heal);
            if (dual) {
                // Sweep: hit nearby entities
                Level level = attacker.level();
                float sweepRadius = 3.0F;
                for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(sweepRadius))) {
                    if (nearby != attacker && nearby != target && nearby.isAlive()) {
                        nearby.hurt(level.damageSources().playerAttack(player), 4.0F + bonus);
                        nearby.knockback(0.4, attacker.getX() - nearby.getX(), attacker.getZ() - nearby.getZ());
                        nearby.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                    }
                }
                if (level instanceof ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                            target.getX(), target.getY(0.6), target.getZ(), 1, 0, 0, 0, 0);
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                            target.getX(), target.getY(0.6), target.getZ(), 6, 0.3, 0.3, 0.3, 0.2);
                }
            }
        }

        // Wither / bleed on crit
        if (attacker.getRandom().nextFloat() < 0.35F) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
        }

        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        // Efficient on plants, leaves, cobwebs — no durability loss on soft blocks
        if (state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.SWORD_EFFICIENT) || state.is(net.minecraft.world.level.block.Blocks.COBWEB)) {
            return true;
        }
        stack.hurtAndBreak(1, miner, EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Right-click: Reaper Dash — lunge forward + sweep
        if (!level.isClientSide()) {
            boolean dual = isDualWielding(player);
            double dash = dual ? 1.35 : 0.9;
            var look = player.getLookAngle();
            player.setDeltaMovement(
                    player.getDeltaMovement().x + look.x * dash,
                    0.18 + (dual ? 0.12 : 0),
                    player.getDeltaMovement().z + look.z * dash);
            player.hurtMarked = true;

            // Sweep damage in front
            var box = player.getBoundingBox().inflate(dual ? 4.5 : 3.0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (e != player && e.isAlive() && player.hasLineOfSight(e)) {
                    e.hurt(level.damageSources().playerAttack(player), dual ? 10.0F : 6.0F);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
                    e.knockback(1.0, player.getX() - e.getX(), player.getZ() - e.getZ());
                }
            }

            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, dual ? 0.9F : 1.2F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                        player.getX() + look.x, player.getY(0.5) + look.y, player.getZ() + look.z,
                        dual ? 4 : 2, 0.4, 0.2, 0.4, 0.0);
            }

            player.getCooldowns().addCooldown(this, dual ? 30 : 50);
            // Slight durability cost for dash
            stack.hurtAndBreak(2, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide() || level.getGameTime() % DUAL_CHECK_TICKS != 0) return;
        if (!(entity instanceof Player player)) return;
        if (!isScythe(player.getMainHandItem()) && !isScythe(player.getOffhandItem())) return;

        boolean dual = isDualWielding(player);
        if (dual) {
            // Dual-wield passive: haste + speed while both scythes are held
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 1, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 30, 0, true, false, false));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean dualItem = stack.getItem() == com.shadowclangaming.registry.SCGItems.DUAL_SCYTHE.get();
        if (dualItem) {
            tooltip.add(Component.literal("Twin Short Scythes — Paired Set").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            tooltip.add(Component.literal("PAIRED: 12 dmg • Very Fast • Sweep + Lifesteal + Dash").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Crimson Short-Scythe (Kama)").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            tooltip.add(Component.literal("8 dmg • Fast • Lifesteal • Sweep ready").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Dual-wield: one per hand → +4 dmg, AOE sweep, speed/haste").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        tooltip.add(Component.literal("Right-click: Reaper Dash — lunge & reap foes ahead").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Short crimson haft • Mirror silver blade • Photoreal").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 18;
    }
}
