package com.shadowclangaming.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.BlockState;

public class SmelteryFaucetBlock extends Block {
    public SmelteryFaucetBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.5F).sound(SoundType.STONE).noOcclusion());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        // Find smeltery controller adjacent (NSEW)
        SmelteryControllerBlockEntity smeltery = null;
        for (var dir : net.minecraft.core.Direction.values()) {
            var be = level.getBlockEntity(pos.relative(dir));
            if (be instanceof SmelteryControllerBlockEntity sc) { smeltery = sc; break; }
            // also check 2 away for wall thickness
            var be2 = level.getBlockEntity(pos.relative(dir, 2));
            if (be2 instanceof SmelteryControllerBlockEntity sc2) { smeltery = sc2; break; }
        }
        if (smeltery == null) {
            player.displayClientMessage(Component.literal("No smeltery controller nearby"), true);
            return InteractionResult.FAIL;
        }
        // Determine casting target below faucet
        BlockPos below = pos.below();
        var belowState = level.getBlockState(below);
        var belowId = belowState.getBlock().builtInRegistryHolder().key().location().toString();
        boolean isTable = belowId.contains("casting_table") || belowState.is(Blocks.STONE) || belowState.is(Blocks.CRAFTING_TABLE);
        boolean isBasin = belowId.contains("casting_basin") || belowState.is(Blocks.CAULDRON);

        var tank = smeltery.getTank();
        if (tank.totalAmount() == 0) {
            player.displayClientMessage(Component.literal("Smeltery empty"), true);
            return InteractionResult.FAIL;
        }
        // Pick first fluid with >=144mB to pour
        com.shadowclangaming.smeltery.SmelteryFluid toPour = null;
        for (var e : tank.view().entrySet()) if (e.getValue() >= 144) { toPour = e.getKey(); break; }
        if (toPour == null) {
            player.displayClientMessage(Component.literal("Not enough fluid to cast (need 144mB)"), true);
            return InteractionResult.FAIL;
        }
        // Check cast target: table needs cast, basin for blocks
        ItemStack cast = ItemStack.EMPTY;
        var targetBe = level.getBlockEntity(below);
        // Try to get cast from table/basin BE if present (stub: check persistent data)
        // For now, pour directly into player's held bucket or create ingot
        int toDrain = isBasin ? 1296 : 144;
        if (tank.get(toPour) < toDrain) {
            player.displayClientMessage(Component.literal("Need " + toDrain + "mB for " + (isBasin?"block":"ingot")), true);
            return InteractionResult.FAIL;
        }
        tank.drain(toPour, toDrain);
        ItemStack result = mapFluidToItem(toPour, isBasin);
        if (!result.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource((ServerLevel)level, below.above(1), result);
            player.displayClientMessage(Component.literal("Poured " + toPour.id() + " -> " + result.getHoverName().getString()), true);
            smeltery.setChanged();
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    private ItemStack mapFluidToItem(com.shadowclangaming.smeltery.SmelteryFluid f, boolean block) {
        return switch (f) {
            case MOLTEN_IRON -> block ? new ItemStack(Blocks.IRON_BLOCK) : new ItemStack(Items.IRON_INGOT);
            case MOLTEN_GOLD -> block ? new ItemStack(Blocks.GOLD_BLOCK) : new ItemStack(Items.GOLD_INGOT);
            case MOLTEN_COPPER -> new ItemStack(Items.COPPER_INGOT, block?9:1);
            case MOLTEN_YELLORIUM -> {
                var yIng = com.shadowclangaming.registry.SCGItems.YELLORIUM_INGOT.get();
                yield block ? new ItemStack(com.shadowclangaming.registry.SCGBlocks.UNOBTAINIUM_BLOCK.get()) : new ItemStack(yIng);
            }
            case MOLTEN_NETHER_STAR -> new ItemStack(Items.NETHER_STAR);
            case MOLTEN_UNOBTAINIUM -> block ? new ItemStack(com.shadowclangaming.registry.SCGBlocks.UNOBTAINIUM_BLOCK.get()) : new ItemStack(com.shadowclangaming.registry.SCGItems.UNOBTAINIUM_INGOT.get());
            case MOLTEN_MANYULLYN -> new ItemStack(Items.NETHERITE_INGOT);
            default -> new ItemStack(Items.IRON_NUGGET);
        };
    }
}
