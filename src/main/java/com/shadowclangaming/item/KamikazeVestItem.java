package com.shadowclangaming.item;

import com.shadowclangaming.warhead.KamikazePlan;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;

import java.util.List;

/**
 * The kamikaze vest: a chestplate that turns its wearer into a launch site.
 *
 * Wearing it does nothing at all while you are alive - no protection worth the name, no aura, no ticking
 * effect. It pays out exactly once, on death ({@code warhead/Kamikaze#onDeath}): the vest detonates, every
 * missile in your inventory is fired in a random direction, and every explosive you were carrying is lit
 * and thrown away from the corpse.
 *
 * The design intent is that it is a *deliberate* choice: you wear it knowing the only way it pays for
 * itself is that you lose. That is why it has the protection of a leather shirt - it must not be a
 * strictly better chestplate that also explodes. It is a bomb with sleeves.
 */
public class KamikazeVestItem extends ArmorItem {

    public KamikazeVestItem(Holder<ArmorMaterial> material, Properties properties) {
        super(material, Type.CHESTPLATE, properties);
    }

    /**
     * Load the suit.
     *
     * Right-click a placed explosive block with the vest in hand and the suit absorbs that warhead: the
     * block is consumed, the payload is written to the stack's component, and the vest now detonates as
     * that warhead. Sneak-right-click with an empty hand clears the load back to the built-in charge.
     *
     * Using the BLOCK form deliberately - the vest is a walking charge, so the block version is the one
     * whose power and behaviour match what a player can see sitting on the ground.
     */
    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        net.minecraft.world.level.Level level = context.getLevel();
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(context.getClickedPos());
        if (!(state.getBlock() instanceof com.shadowclangaming.block.ExplosiveBlock explosive)) {
            return InteractionResult.PASS;
        }
        net.minecraft.world.entity.player.Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack loaded = com.shadowclangaming.warhead.Kamikaze.load(
                    context.getItemInHand(), explosive.warhead());
            player.setItemInHand(context.getHand(), loaded);
            level.removeBlock(context.getClickedPos(), false);
            level.playSound(null, context.getClickedPos(),
                    net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
            player.displayClientMessage(Component.translatable(
                    "message.shadowclangaming.vest_loaded",
                    Component.translatable(explosive.warhead().translationKey())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
                                                 net.minecraft.world.entity.player.Player player,
                                                 InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String id = stack.get(com.shadowclangaming.registry.SCGDataComponents.VEST_PAYLOAD.get());
        if (id == null || id.isBlank() || !player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            stack.remove(com.shadowclangaming.registry.SCGDataComponents.VEST_PAYLOAD.get());
            player.displayClientMessage(Component.translatable(
                    "message.shadowclangaming.vest_cleared"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.shadowclangaming.kamikaze.warning")
                .withStyle(ChatFormatting.DARK_RED));
        String payload = stack.get(com.shadowclangaming.registry.SCGDataComponents.VEST_PAYLOAD.get());
        if (payload != null && !payload.isBlank()) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.kamikaze.payload",
                    Component.translatable(com.shadowclangaming.warhead.WarheadType.byId(payload)
                            .translationKey())).withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.shadowclangaming.kamikaze.payload.clear")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.kamikaze.payload.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.shadowclangaming.kamikaze.blast",
                Math.round(KamikazePlan.VEST_POWER)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.kamikaze.salvo",
                KamikazePlan.MAX_SALVO).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.kamikaze.tnt")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.kamikaze.broken")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
