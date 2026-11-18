package com.shadowclangaming.item;

import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * The tracker: lock a target, then hand the lock to a launcher.
 *
 * Two-step, entirely on the item, no GUI:
 *   right-click an entity      -> lock it (stored as a UUID, so it survives death and restarts)
 *   right-click a launch pad   -> the pad's next missile follows that entity
 *   sneak + right-click air    -> clear the lock
 *
 * The lock is a UUID, not an entity reference: an unloaded chunk must not lose a lock, and a target
 * that logs out and back in is still the same target.
 */
public class TrackerItem extends Item {

    /** How far you can lock from. */
    public static final double LOCK_RANGE = 24.0D;

    public TrackerItem(Properties properties) {
        super(properties);
    }

    public static UUID lockedTarget(ItemStack stack) {
        String raw = stack.get(SCGDataComponents.TRACKED_ENTITY.get());
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void lock(ItemStack stack, UUID target) {
        stack.set(SCGDataComponents.TRACKED_ENTITY.get(), target == null ? "" : target.toString());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        if (player.isShiftKeyDown()) {
            lock(stack, null);
            player.displayClientMessage(Component.translatable("message.shadowclangaming.tracker_cleared")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        // Lock whatever is in front of the player: nearest living entity within a short cone.
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(LOCK_RANGE));
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, end).inflate(1.0D),
                entity -> entity != player && entity.isAlive())) {
            var clip = candidate.getBoundingBox().inflate(0.4D).clip(eye, end);
            if (clip.isPresent()) {
                double score = eye.distanceToSqr(clip.get());
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }

        if (best == null) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.tracker_nothing")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        lock(stack, best.getUUID());
        player.displayClientMessage(Component.translatable("message.shadowclangaming.tracker_locked",
                best.getDisplayName()).withStyle(ChatFormatting.RED), false);
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide || !(level instanceof ServerLevel) || context.getPlayer() == null) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        UUID target = lockedTarget(stack);
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof com.shadowclangaming.block.LaunchPadBlockEntity pad)) {
            return InteractionResult.PASS;
        }
        if (target == null) {
            context.getPlayer().displayClientMessage(Component.translatable(
                    "message.shadowclangaming.tracker_no_lock").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        pad.setTrackedEntity(target);
        context.getPlayer().displayClientMessage(Component.translatable(
                "message.shadowclangaming.tracker_assigned").withStyle(ChatFormatting.RED), false);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        UUID locked = lockedTarget(stack);
        tooltip.add((locked == null
                ? Component.translatable("tooltip.shadowclangaming.tracker.none")
                : Component.translatable("tooltip.shadowclangaming.tracker.locked", locked.toString()
                .substring(0, 8))).withStyle(locked == null ? ChatFormatting.GRAY : ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.tracker.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
