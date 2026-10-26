package com.shadowclangaming.block;

import com.mojang.serialization.MapCodec;
import com.shadowclangaming.item.BankCardItem;
import com.shadowclangaming.item.MissileItem;
import com.shadowclangaming.item.TargetDesignatorItem;
import com.shadowclangaming.util.CurrencyValues;
import com.shadowclangaming.warhead.Funding;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Launch pad.
 *
 *   right click with a missile        -> load it
 *   right click with a designator      -> bind that designator to this pad
 *   right click with currency          -> load the Numismatic warhead with money
 *   sneak + right click empty hand     -> unload the missile
 *   redstone signal                    -> fire at the last target
 */
public class LaunchPadBlock extends BaseEntityBlock {

    /**
     * FE into the pad. The pad has no output - it is a capacitor, not a battery - and whatever is in
     * it becomes funding on the next launch (see LaunchPadBlockEntity#fundFromEnergy).
     */
    public static void registerCapabilities(
            net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                com.shadowclangaming.registry.SCGBlockEntities.LAUNCH_PAD.get(),
                (blockEntity, side) -> blockEntity.energy());
        // The 16-slot feed inventory. This one registration is the whole of "hoppers, AE2 import
        // buses and any other IItemHandler can load the pad": NeoForge resolves the capability, the
        // other mod does not need to know this block exists.
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.shadowclangaming.registry.SCGBlockEntities.LAUNCH_PAD.get(),
                (blockEntity, side) -> blockEntity.feed());
    }

    /**
     * The pad ticks - once a second, to turn feed currency into funding. Cheap: with an empty feed it
     * is an array check and a counter.
     */
    @org.jetbrains.annotations.Nullable
    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity>
            net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof LaunchPadBlockEntity pad) {
                pad.tickFeed();
                // Keeps the docked missile entity alive, the silo structure checked and the pad's
                // frequency registration current (a moving subgrid drifts without block updates).
                pad.tickServer();
            }
        };
    }
    public static final MapCodec<LaunchPadBlock> CODEC = simpleCodec(LaunchPadBlock::new);

    /** Loading a warhead this big broadcasts a warning to the whole server. */
    public static final int WARNING_RADIUS = 512;

    public LaunchPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchPadBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // (Boarding the missile used to be a sneak + empty-hand branch here. It never ran: with an empty
        //  hand Minecraft calls useWithoutItem, not useItemOn, so the branch was dead code. Riding is now
        //  a button on the launch console, and the empty-hand gestures on the pad are theirs again.)

        // 0. Bank Card: pays from the player's EconomyCraft balance. Delegate so there is exactly
        //    one implementation of the payment logic (see BankCardItem#useOn).
        if (stack.getItem() instanceof BankCardItem card) {
            InteractionResult delegated = card.useOn(new UseOnContext(player, hand, hit));
            return delegated.consumesAction()
                    ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 1. load a missile
        if (stack.getItem() instanceof MissileItem) {
            if (pad.loadMissile(stack)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            return ItemInteractionResult.FAIL;
        }

        // 2. bind a designator to this pad
        //
        // DELEGATED, like the Bank Card above, and for a reason that cost a round: this branch used to write
        // the pad's COORDINATES into the designator and return success, which meant the designator's own
        // useOn() - the one that copies the pad's FREQUENCY - never ran. The designator then had no channel at
        // all, and every right-click in the air answered "not tuned to a channel" even after the pad had been
        // chipped. One implementation of "tune the designator", and it lives on the item.
        if (stack.getItem() instanceof TargetDesignatorItem designator) {
            InteractionResult delegated = designator.useOn(new UseOnContext(player, hand, hit));
            return delegated.consumesAction()
                    ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 2b. an Upgrade Kit raises the pad's tier by exactly one
        Integer kitTier = stack.get(com.shadowclangaming.registry.SCGDataComponents.KIT_TIER.get());
        if (kitTier != null) {
            com.shadowclangaming.launcher.UpgradeKit.Result result =
                    com.shadowclangaming.launcher.UpgradeKit.apply(pad.tier(), kitTier);
            if (result == com.shadowclangaming.launcher.UpgradeKit.Result.UPGRADED) {
                pad.setTier(kitTier);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                player.displayClientMessage(Component.translatable("message.shadowclangaming.pad_upgraded",
                        pad.tier()).withStyle(net.minecraft.ChatFormatting.GREEN), true);
            } else if (result == com.shadowclangaming.launcher.UpgradeKit.Result.ALREADY_MAX) {
                player.displayClientMessage(Component.translatable("message.shadowclangaming.panel_max")
                        .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            } else {
                player.displayClientMessage(Component.translatable("message.shadowclangaming.kit_wrong_tier",
                        kitTier, pad.tier() + 1).withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            return ItemInteractionResult.CONSUME;
        }

        // 2c. cargo: any other item goes into a CARGO warhead's hold
        if (pad.hasMissile() && pad.getMissile().getItem() instanceof MissileItem loaded
                && loaded.warhead() == com.shadowclangaming.warhead.WarheadType.CARGO
                && !(stack.getItem() instanceof MissileItem)
                // our own tools keep working on a cargo pad: a tracker must still lock, a calculator
                // must still calculate, instead of being posted into the hold
                && !(stack.getItem() instanceof com.shadowclangaming.item.TrackerItem)
                && !(stack.getItem() instanceof com.shadowclangaming.item.BallisticsCalculatorItem)
                && !(stack.getItem() instanceof com.shadowclangaming.item.UpgradeKitItem)) {
            if (pad.loadCargo(stack)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                player.displayClientMessage(Component.translatable("message.shadowclangaming.cargo_loaded",
                        pad.cargoSlotsUsed()).withStyle(net.minecraft.ChatFormatting.GREEN), true);
                return ItemInteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable("message.shadowclangaming.cargo_full")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return ItemInteractionResult.CONSUME;
        }

        // 3. fund a Numismatic warhead
        //    Right-click loads one coin. SNEAK + right-click loads the whole stack - at 256 Suns a
        //    doomsday missile, nobody wants to click a thousand times.
        long unitValue = CurrencyValues.valueOf(stack);
        if (unitValue > 0 && pad.hasMissile()) {
            int count = player.isShiftKeyDown() ? stack.getCount() : 1;
            long value = unitValue * count;
            if (pad.addFunding(value)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(count);
                }
                ItemStack loaded = pad.getMissile();
                long funding = MissileItem.fundingOf(loaded);
                int radius = loaded.getItem() instanceof MissileItem loadedMissile
                        ? loadedMissile.warhead().radiusForFunding(funding)
                        : Funding.radiusForFunding(funding);
                player.displayClientMessage(Component.translatable("message.shadowclangaming.funding_loaded",
                        String.format("%,d", funding), String.format("%,d", radius)), true);

                // Money is power: once a warhead is big enough to end a base, everybody hears about it.
                if (radius >= WARNING_RADIUS && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                            Component.translatable("message.shadowclangaming.strategic_warning",
                                    player.getDisplayName(), radius), false);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            player.displayClientMessage(Component.translatable("message.shadowclangaming.funding_wrong_warhead"), true);
            return ItemInteractionResult.FAIL;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            // Shift with a missile in hand: take it back off the pad.
            if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
                if (pad.hasMissile()) {
                    ItemStack removed = pad.unloadMissile();
                    if (!removed.isEmpty() && !player.getInventory().add(removed)) {
                        player.drop(removed, false);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            } else {
                // Shift + empty hand: the SILO HOLD. The pad has had a real 27-slot cargo inventory since
                // round 1 - hoppers and AE2 buses could fill it - but no player-facing way in, which made
                // "load the silo" a console command. This is that way in.
                if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer server) {
                    server.openMenu(new net.minecraft.world.SimpleMenuProvider(
                                    (containerId, inventory, p) ->
                                            new com.shadowclangaming.block.SiloHoldMenu(
                                                    containerId, inventory, pad, pos),
                                    Component.translatable("container.shadowclangaming.silo_hold")),
                            buffer -> buffer.writeBlockPos(pos));
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // Right-click with an empty hand opens the launch console.
        if (!level.isClientSide) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                                (containerId, inventory, p) -> new LaunchPadMenu(containerId, inventory, pad, pos),
                                Component.translatable("block.shadowclangaming.launch_pad")),
                        buffer -> buffer.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && level.hasNeighborSignal(pos)) {
            if (level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) {
                pad.tryLaunch();
            }
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (blockEntity instanceof LaunchPadBlockEntity pad && pad.hasMissile()) {
            popResource(level, pos, pad.unloadMissile());
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad && pad.hasMissile() ? 15 : 0;
    }
}
