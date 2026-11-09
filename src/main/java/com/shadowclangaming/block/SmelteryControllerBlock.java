package com.shadowclangaming.block;

import com.shadowclangaming.smeltery.SmelteryFluid;
import com.shadowclangaming.smeltery.SmelteryMeltingRecipe;
import com.shadowclangaming.smeltery.SmelteryTank;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Smeltery Controller - classic Tinkers Construct controller logic reimplemented.
 * Forms multiblock from Seared Bricks (3x3 hollow), controller on wall, lava underneath for heat.
 * This minimal version supports: melting items dropped in, alloying, draining via faucet.
 *
 * Full recreation of old TiCo smeltery mechanics:
 * - Multiblock detection (floor of Bricks, 1-wide wall, air inside, height 1..3)
 * - Fuel (lava) heating
 * - Melting via SmelteryMeltingRecipe (including mod materials)
 * - Alloying via SmelteryAlloyRecipe (including 1 NetherStar + 8 Yellorium = 9 Unobtainium)
 * - Faucet pouring into casting table/basin
 */
public class SmelteryControllerBlock extends Block implements EntityBlock {
    public SmelteryControllerBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3.5F).sound(SoundType.STONE).requiresCorrectToolForDrops());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmelteryControllerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        var be = level.getBlockEntity(pos);
        if (be instanceof SmelteryControllerBlockEntity sc) {
            if (player.isShiftKeyDown()) {
                // dump info
                player.displayClientMessage(Component.literal("Smeltery: " + sc.getTank().debugStrings() + " Temp=" + sc.getTank().temperature() + " Multiblock=" + sc.isFormed()), false);
            } else {
                var held = player.getMainHandItem();
                if (!held.isEmpty()) {
                    var melting = SmelteryMeltingRecipe.find(held);
                    if (melting != null) {
                        if (sc.isFormed() && sc.getTank().temperature() >= melting.temp()) {
                            held.shrink(1);
                            sc.getTank().fill(melting.fluid(), melting.amountMb());
                            player.displayClientMessage(Component.literal("Melted " + held.getHoverName().getString() + " -> " + melting.fluid().id() + " " + melting.amountMb() + "mB"), true);
                        } else {
                            player.displayClientMessage(Component.literal("Not hot enough! Need " + melting.temp() + " (current " + sc.getTank().temperature() + ") - place lava below or light seared heater"), true);
                        }
                        return InteractionResult.CONSUME;
                    }
                } else {
                    player.displayClientMessage(Component.literal("Hold an item to melt (e.g., Nether Star, Yellorium Ingot, Iron, Copper, etc.) Sneak for status"), true);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, pos2, st, be) -> {
            if (be instanceof SmelteryControllerBlockEntity sc) sc.tick((ServerLevel) lvl);
        };
    }
}
