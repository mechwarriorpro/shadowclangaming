package com.shadowclangaming.block;

import com.shadowclangaming.gun.GunForge;
import com.shadowclangaming.gun.GunStats;
import com.shadowclangaming.item.GunItem;
import com.shadowclangaming.registry.SCGItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The fabricator: scrap in, a rolled gun out.
 *
 * Deliberately not a GUI. One scrap, one roll, and the roll is announced in chat - because the fun of
 * a loot system is the moment it prints, not a screen you have to click through. Keep feeding it scrap
 * until something good comes out; that is the loop.
 */
public class FabricatorBlock extends Block {

    public static final int SCRAP_PER_ROLL = 1;

    public FabricatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() != SCGItems.GUN_SCRAP.get()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || stack.getCount() < SCRAP_PER_ROLL) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        stack.shrink(SCRAP_PER_ROLL);

        GunStats stats = GunForge.roll(serverLevel.getRandom());
        ItemStack gun = GunItem.create(stats);
        if (!player.getInventory().add(gun)) {
            net.minecraft.world.Containers.dropItemStack(serverLevel, pos.getX() + 0.5D, pos.getY() + 1.0D,
                    pos.getZ() + 0.5D, gun);
        }

        player.displayClientMessage(Component.translatable("message.shadowclangaming.fabricated",
                        Component.literal(GunForge.name(stats)).withStyle(stats.rarity().format()),
                        Component.translatable(stats.rarity().translationKey()))
                .withStyle(ChatFormatting.GRAY), false);

        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D, 24, 0.3D, 0.2D, 0.3D, 0.1D);
        serverLevel.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 1.3F);
        return ItemInteractionResult.CONSUME;
    }
}
