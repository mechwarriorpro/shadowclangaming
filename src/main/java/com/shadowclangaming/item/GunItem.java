package com.shadowclangaming.item;

import com.shadowclangaming.gun.GunElement;
import com.shadowclangaming.gun.GunForge;
import com.shadowclangaming.gun.GunPart;
import com.shadowclangaming.gun.GunShooting;
import com.shadowclangaming.gun.GunStats;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.registry.SCGItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * A procedurally generated gun.
 *
 * Nothing about it is hard-coded: the item reads a {@link GunStats} component and obeys it. Two items
 * of this class can be completely different weapons - different maker, rarity, element, six different
 * parts and five different silhouettes with five palettes each.
 *
 * Ammo lives in its own component so a gun keeps its magazine while it is in a chest, and reloading is
 * a real action with a real cost: one Cell from the inventory and a wait.
 */
public class GunItem extends Item {

    public GunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        ItemStack stack = player.getItemInHand(hand);
        GunStats stats = stack.get(SCGDataComponents.GUN.get());
        if (stats == null) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        int ammo = ammoOf(stack);
        if (ammo <= 0) {
            if (!reload(serverPlayer, stack, stats)) {
                player.displayClientMessage(Component.translatable("message.shadowclangaming.gun_no_cells")
                        .withStyle(ChatFormatting.RED), true);
                player.getCooldowns().addCooldown(stack.getItem(), 6);
            }
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        GunShooting.fire(serverPlayer.serverLevel(), serverPlayer, stats);
        setAmmo(stack, ammo - 1);
        player.getCooldowns().addCooldown(stack.getItem(), stats.cooldownTicks());
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    /** Build a gun item from a roll. The one place a gun stack is ever assembled. */
    public static ItemStack create(GunStats stats) {
        ItemStack stack = new ItemStack(SCGItems.GUN.get());
        stack.set(SCGDataComponents.GUN.get(), stats);
        stack.set(SCGDataComponents.GUN_AMMO.get(), stats.magazine());
        // 1.21.1 still uses the integer custom model data component; the model file has one override
        // per silhouette x element, so the right texture is picked with no rendering code at all.
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(stats.modelVariant()));
        return stack;
    }

    // ------------------------------------------------------------------ ammo

    public static int ammoOf(ItemStack stack) {
        Integer ammo = stack.get(SCGDataComponents.GUN_AMMO.get());
        return ammo == null ? 0 : Math.max(0, ammo);
    }

    public static void setAmmo(ItemStack stack, int ammo) {
        stack.set(SCGDataComponents.GUN_AMMO.get(), Math.max(0, ammo));
    }

    /** Pulls one Cell out of the inventory and fills the magazine. */
    private static boolean reload(ServerPlayer player, ItemStack gun, GunStats stats) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.getItem() != SCGItems.GUN_CELL.get()) {
                continue;
            }
            candidate.shrink(1);
            setAmmo(gun, stats.magazine());
            player.getCooldowns().addCooldown(gun.getItem(), stats.reloadTicks());
            player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                    SoundSource.PLAYERS, 0.7F, 1.4F);
            player.displayClientMessage(Component.translatable("message.shadowclangaming.gun_reloaded",
                    stats.magazine()).withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ presentation

    @Override
    public Component getName(ItemStack stack) {
        GunStats stats = stack.get(SCGDataComponents.GUN.get());
        if (stats == null) {
            return super.getName(stack);
        }
        return Component.literal(GunForge.name(stats)).withStyle(stats.rarity().format());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GunStats stats = stack.get(SCGDataComponents.GUN.get());
        if (stats == null) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.gun.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.shadowclangaming.gun.maker",
                Component.translatable(stats.maker().translationKey())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.gun.rarity",
                Component.translatable(stats.rarity().translationKey())).withStyle(stats.rarity().format()));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.gun.element",
                        Component.translatable(stats.element().translationKey()))
                .withStyle(stats.element().format()));

        tooltip.add(Component.translatable("tooltip.shadowclangaming.gun.damage",
                String.format("%.1f", stats.damage())).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.gun.rate",
                String.format("%.2f", stats.fireRate())).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.gun.accuracy",
                Math.round(stats.accuracy() * 100)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.gun.ammo",
                ammoOf(stack), stats.magazine()).withStyle(ChatFormatting.GOLD));

        for (GunPart part : stats.partsBySlot()) {
            tooltip.add(Component.literal("  ")
                    .append(Component.translatable(part.translationKey()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Rarity also drives the item's glow, so a relic is visible in a chest without hovering it. */
    @Override
    public boolean isFoil(ItemStack stack) {
        GunStats stats = stack.get(SCGDataComponents.GUN.get());
        return stats != null && stats.rarity() == com.shadowclangaming.gun.GunRarity.RELIC;
    }

    /** Element colour, used by the name and available to anything that wants to tint a UI. */
    public static GunElement elementOf(ItemStack stack) {
        GunStats stats = stack.get(SCGDataComponents.GUN.get());
        return stats == null ? GunElement.KINETIC : stats.element();
    }
}
