package com.shadowclangaming.item;

import com.shadowclangaming.chem.DecompositionTable;
import com.shadowclangaming.chem.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Handheld field microscope / chemist's lens.
 *
 * <p>Allows field inspection of chemical compositions:
 * <ul>
 *   <li>Right-click a block in the world to analyze its material composition.</li>
 *   <li>Right-click in air while holding an item in the offhand to analyze that item.</li>
 *   <li>Automatically transcribes discoveries into any Chemist's Journal in inventory.</li>
 * </ul>
 */
public class PocketMicroscopeItem extends Item {

    public PocketMicroscopeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack asItem = state.getBlock().asItem().getDefaultInstance();

        if (player == null || asItem.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            analyzeAndDisplay(player, serverLevel, asItem, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack target = player.getItemInHand(otherHand);

        if (target.isEmpty()) {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("message.shadowclangaming.pocket_microscope.hint")
                                .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.pass(held);
        }

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            analyzeAndDisplay(player, serverLevel, target, player.getX(), player.getY() + 1.2, player.getZ());
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
    }

    private void analyzeAndDisplay(Player player, ServerLevel level, ItemStack target, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.5F);
        level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 10, 0.2, 0.2, 0.2, 0.05);

        List<DecompositionTable.Yield> yields = DecompositionTable.yieldsFor(target);
        if (yields.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.shadowclangaming.microscope.unknown",
                            target.getHoverName()).withStyle(ChatFormatting.GRAY), false);
        } else {
            MutableComponent msg = Component.literal("🔍 ")
                    .append(Component.translatable("message.shadowclangaming.microscope.header",
                            target.getHoverName()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                    .append(Component.literal("\n   "));

            for (int i = 0; i < yields.size(); i++) {
                DecompositionTable.Yield yieldDef = yields.get(i);
                Element elem = yieldDef.element();
                String countStr = yieldDef.min() == yieldDef.max() ? String.valueOf(yieldDef.min()) : (yieldDef.min() + "-" + yieldDef.max());
                msg.append(Component.literal(countStr + "x ").withStyle(ChatFormatting.WHITE))
                   .append(Component.literal(elem.symbol()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                   .append(Component.literal(" (" + elem.displayName() + ", Z=" + elem.atomicNumber() + ")")
                           .withStyle(ChatFormatting.GRAY));
                if (i < yields.size() - 1) {
                    msg.append(Component.literal(", "));
                }
            }
            player.displayClientMessage(msg, false);

            ChemistJournalItem.recordDiscovery(player, target);
        }
    }
}
