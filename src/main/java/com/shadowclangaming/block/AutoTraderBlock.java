package com.shadowclangaming.block;

import com.mojang.serialization.MapCodec;
import com.shadowclangaming.compat.economy.Money;
import com.shadowclangaming.compat.economy.EconomyCraftPrices;
import com.shadowclangaming.economy.ExchangeLedger;
import com.shadowclangaming.economy.PowerExchange;
import com.shadowclangaming.item.BankCardItem;
import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * AUTO-TRADER block. See {@link AutoTraderBlockEntity} for the mechanics.
 *
 * Controls, in full (there is no GUI):
 *   right-click with an item        -> put it in the sell slot (sell price, credited to the card owner)
 *   sneak + right-click with item   -> set the BUY TEMPLATE to that item
 *   right-click with a Bank Card    -> install the card (the machine's account)
 *   right-click empty hand          -> collect purchases + print status
 *   sneak + right-click empty hand  -> eject the Bank Card
 *
 * Pipes work on every side: push items in to sell them, pull items out to get purchases.
 */
public class AutoTraderBlock extends BaseEntityBlock {
    public static final MapCodec<AutoTraderBlock> CODEC = simpleCodec(AutoTraderBlock::new);

    public AutoTraderBlock(Properties properties) {
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
        return new AutoTraderBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof AutoTraderBlockEntity trader) {
                AutoTraderBlockEntity.serverTick(tickLevel, pos, tickState, trader);
            }
        };
    }

    /** Capabilities: any side can feed items and FE, and any side can pull finished purchases. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                com.shadowclangaming.registry.SCGBlockEntities.AUTO_TRADER.get(),
                (blockEntity, side) -> blockEntity.items());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                com.shadowclangaming.registry.SCGBlockEntities.AUTO_TRADER.get(),
                (blockEntity, side) -> blockEntity.energy());
    }

    // ------------------------------------------------------------------ interaction

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AutoTraderBlockEntity trader)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 1. install a Bank Card
        if (stack.getItem() instanceof BankCardItem) {
            if (!level.isClientSide) {
                BankCardItem.signFor(stack, player);
                ItemStack replaced = trader.getSlot(AutoTraderBlockEntity.SLOT_CARD);
                trader.setSlot(AutoTraderBlockEntity.SLOT_CARD, stack.copyWithCount(1));
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                if (!replaced.isEmpty() && !player.getInventory().add(replaced)) {
                    player.drop(replaced, false);
                }
                player.displayClientMessage(Component.translatable("message.shadowclangaming.trader_card_in",
                        player.getDisplayName()), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // 2. sneak: set the buy template
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                trader.setSlot(AutoTraderBlockEntity.SLOT_TEMPLATE, stack.copyWithCount(1));
                player.displayClientMessage(Component.translatable("message.shadowclangaming.trader_template",
                        stack.getHoverName()), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // 3. plain: put it in the sell slot
        if (!level.isClientSide) {
            ItemStack sell = trader.getSlot(AutoTraderBlockEntity.SLOT_SELL);
            if (sell.isEmpty()) {
                trader.setSlot(AutoTraderBlockEntity.SLOT_SELL, stack.copy());
                if (!player.getAbilities().instabuild) {
                    stack.shrink(stack.getCount());
                }
                player.displayClientMessage(Component.translatable("message.shadowclangaming.trader_sell_in"),
                        true);
            } else {
                player.displayClientMessage(Component.translatable("message.shadowclangaming.trader_sell_busy"),
                        true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AutoTraderBlockEntity trader)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // sneak + empty hand: give the card back
        if (player.isShiftKeyDown()) {
            ItemStack card = trader.getSlot(AutoTraderBlockEntity.SLOT_CARD);
            if (!card.isEmpty()) {
                trader.setSlot(AutoTraderBlockEntity.SLOT_CARD, ItemStack.EMPTY);
                if (!player.getInventory().add(card)) {
                    player.drop(card, false);
                }
                player.displayClientMessage(Component.translatable("message.shadowclangaming.trader_card_out"),
                        true);
            }
            return InteractionResult.SUCCESS;
        }

        // empty hand: collect purchases, then print the machine's status
        ItemStack output = trader.getSlot(AutoTraderBlockEntity.SLOT_OUTPUT);
        if (!output.isEmpty()) {
            if (!player.getInventory().add(output)) {
                player.drop(output, false);
            }
            trader.setSlot(AutoTraderBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        }
        // Never swallow something the economy has no price for: hand it straight back.
        ItemStack unsellable = trader.getSlot(AutoTraderBlockEntity.SLOT_SELL);
        if (!unsellable.isEmpty()
                && EconomyCraftPrices.sellValueOf(level.getServer(), unsellable) <= 0L) {
            trader.setSlot(AutoTraderBlockEntity.SLOT_SELL, ItemStack.EMPTY);
            if (!player.getInventory().add(unsellable)) {
                player.drop(unsellable, false);
            }
            player.displayClientMessage(Component.translatable("message.shadowclangaming.trader_no_price"), true);
        }

        printStatus((ServerLevel) level, trader, player);
        return InteractionResult.SUCCESS;
    }

    private static void printStatus(ServerLevel level, AutoTraderBlockEntity trader, Player player) {
        UUID owner = trader.ownerId();
        if (owner == null) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.trader_no_card"), false);
            return;
        }
        long units = ExchangeLedger.get(level).unitsCredited(owner);
        long balance = Money.balance(level.getServer(), owner);

        player.displayClientMessage(Component.translatable("message.shadowclangaming.trader_status",
                Money.format(level.getServer(), balance),
                String.format("%,d", trader.energy().getEnergyStored()),
                String.format("%,d", PowerExchange.feForNextUnit(units)),
                String.format("%,d", units)), false);

        player.displayClientMessage(Component.translatable("message.shadowclangaming.trader.status_mana",
                String.format("%,d", com.shadowclangaming.magic.ManaApi.currentFor(level.getServer(), owner)),
                String.format("%,d", com.shadowclangaming.magic.ManaApi.capacityFor(level.getServer(), owner))),
                false);

        ItemStack template = trader.getSlot(AutoTraderBlockEntity.SLOT_TEMPLATE);
        if (!template.isEmpty()) {
            long price = EconomyCraftPrices.buyValueOf(level.getServer(), template);
            player.displayClientMessage(Component.translatable("message.shadowclangaming.trader_status_buy",
                    template.getHoverName(), price < 0 ? "?" : Money.format(level.getServer(), price)),
                    false);
        }
    }

    // ------------------------------------------------------------------ housekeeping

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AutoTraderBlockEntity trader) {
            trader.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Comparators read the buffer level, so you can automate "full enough" shutoff. */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AutoTraderBlockEntity trader) {
            return Math.min(15, trader.energy().getEnergyStored() * 15 / AutoTraderBlockEntity.ENERGY_CAPACITY);
        }
        return 0;
    }
}
