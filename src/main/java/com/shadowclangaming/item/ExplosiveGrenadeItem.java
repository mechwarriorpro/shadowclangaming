package com.shadowclangaming.item;

import com.shadowclangaming.entity.GrenadeEntity;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A warhead in a can: {@code grenade_<warhead>}.
 *
 * Right-click to throw. The warhead id is written into the stack's component as well as being a field on
 * the item, so a grenade that gets its item swapped (or produced by a mod that copies stacks around) still
 * detonates as the warhead it looks like.
 */
public class ExplosiveGrenadeItem extends Item {

    private final WarheadType warhead;

    public ExplosiveGrenadeItem(WarheadType warhead, Properties properties) {
        super(properties);
        this.warhead = warhead;
    }

    public WarheadType warhead() {
        return warhead;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            GrenadeEntity grenade = new GrenadeEntity(level, player,
                    withWarhead(stack.copyWithCount(1)));
            grenade.setItem(withWarhead(stack.copyWithCount(1)));
            grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.1F, 0.6F);
            level.addFreshEntity(grenade);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.6F, 0.7F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** The one place the warhead id is stamped, so the entity and the item can never disagree. */
    private ItemStack withWarhead(ItemStack stack) {
        stack.set(SCGDataComponents.MODULE_WARHEAD.get(), warhead.id());
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(warhead.translationKey()).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.grenade.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
        // The fuse is the warhead's own effect length, so it differs per grenade and is worth showing.
        tooltip.add(Component.translatable("tooltip.shadowclangaming.fuse",
                String.format(java.util.Locale.ROOT, "%.1f",
                        com.shadowclangaming.warhead.ExplosivePackaging.fuseSeconds(
                                warhead.id(), warhead.basePower(),
                                com.shadowclangaming.warhead.ExplosivePackaging.Form.GRENADE)))
                .withStyle(ChatFormatting.GRAY));
    }
}
