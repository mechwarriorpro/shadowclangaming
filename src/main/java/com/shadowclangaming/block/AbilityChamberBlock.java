package com.shadowclangaming.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class AbilityChamberBlock extends Block implements EntityBlock {
    public AbilityChamberBlock(Properties props){ super(props); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s){ return new AbilityChamberBlockEntity(p,s); }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit){
        if(level.isClientSide) return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if(be instanceof AbilityChamberBlockEntity chamber){
            player.openMenu(new MenuProvider(){
                public Component getDisplayName(){ return Component.literal("Ability Chamber"); }
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p){ return new com.shadowclangaming.menu.AbilityChamberMenu(id, inv, chamber); }
            }, pos);
        }
        return InteractionResult.CONSUME;
    }
}
