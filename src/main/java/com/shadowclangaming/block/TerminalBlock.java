package com.shadowclangaming.block;

import com.shadowclangaming.computer.TerminalBlockEntity;
import com.shadowclangaming.computer.TerminalModule;
import com.shadowclangaming.item.FrequencyChipItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The terminal: a computer with no screen, driven by chat.
 *
 * Stand next to it and type /term &lt;command&gt;. That is the whole interface, and it is deliberate:
 * a command line does not need a GUI, a packet channel or a font renderer, and it works from a
 * command block, a datapack function or an operator's console just as well as from a player.
 *
 * Cards are installed by right-clicking the block with one; there is no slot and nothing can be
 * removed, so a cheater cannot pull a card out mid-raid either.
 */
public class TerminalBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<TerminalBlock> CODEC = simpleCodec(TerminalBlock::new);

    public TerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerminalBlockEntity(pos, state);
    }

    /** Ticks the terminal so scheduled redstone pulses end. Server side only. */
    @Nullable
    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity>
    net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof TerminalBlockEntity terminal) {
                if (tickLevel instanceof net.minecraft.server.level.ServerLevel server) {
                    terminal.tick(server, tickLevel.getGameTime());
                }
            }
        };
    }

    // ------------------------------------------------------------------ redstone output

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level,
                            BlockPos pos, net.minecraft.core.Direction direction) {
        return level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal
                ? terminal.output(direction)
                : 0;
    }

    /** Weak power too, so a wire can pick the signal up without a repeater. */
    @Override
    protected int getDirectSignal(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, net.minecraft.core.Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        // 1. a card
        TerminalModule module = cardFor(stack);
        if (module != null) {
            if (terminal.has(module)) {
                player.displayClientMessage(Component.translatable("message.shadowclangaming.card_already")
                        .withStyle(ChatFormatting.GRAY), true);
                return ItemInteractionResult.CONSUME;
            }
            terminal.install(module);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.translatable("message.shadowclangaming.card_installed",
                    Component.translatable(module.translationKey())).withStyle(ChatFormatting.GREEN), true);
            return ItemInteractionResult.CONSUME;
        }

        // 1b. hardware: a CPU or a GPU in the terminal's own sockets
        if (stack.getItem() instanceof com.shadowclangaming.item.HardwareItem hardware) {
            boolean fitted = hardware.isCpu()
                    ? terminal.installCpu(hardware.cpu())
                    : terminal.installGpu(hardware.gpu());
            if (!fitted) {
                player.displayClientMessage(Component.translatable("message.shadowclangaming.terminal_no_upgrade")
                        .withStyle(ChatFormatting.RED), true);
                return ItemInteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.translatable("message.shadowclangaming.terminal_fitted",
                    Component.translatable(hardware.isCpu() ? hardware.cpu().translationKey()
                            : hardware.gpu().translationKey())).withStyle(ChatFormatting.GREEN), true);
            return ItemInteractionResult.CONSUME;
        }

        // 2. a frequency chip, to tune the terminal
        if (stack.getItem() instanceof FrequencyChipItem chip) {
            int frequency = chip.frequencyOf(stack);
            terminal.setFrequency(frequency);
            player.displayClientMessage(Component.translatable("message.shadowclangaming.terminal_tuned",
                    frequency), true);
            return ItemInteractionResult.CONSUME;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Which card an item is, or null if it is not a card at all. */
    private static TerminalModule cardFor(ItemStack stack) {
        if (stack.getItem() == com.shadowclangaming.registry.SCGItems.TARGETING_CARD.get()) {
            return TerminalModule.TARGETING;
        }
        if (stack.getItem() == com.shadowclangaming.registry.SCGItems.MANA_CARD.get()) {
            return TerminalModule.MANA;
        }
        if (stack.getItem() == com.shadowclangaming.registry.SCGItems.NET_CARD.get()) {
            return TerminalModule.WEB;
        }
        if (stack.getItem() == com.shadowclangaming.registry.SCGItems.STORAGE_CARD.get()) {
            return TerminalModule.STORAGE;
        }
        if (stack.getItem() == com.shadowclangaming.registry.SCGItems.REDSTONE_CARD.get()) {
            return TerminalModule.REDSTONE;
        }
        if (stack.getItem() == com.shadowclangaming.registry.SCGItems.RADAR_CARD.get()) {
            return TerminalModule.RADAR;
        }
        if (stack.getItem() == com.shadowclangaming.registry.SCGItems.CANNON_CARD.get()) {
            return TerminalModule.CANNON;
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal)) {
            return InteractionResult.PASS;
        }
        // Empty hand: open the terminal screen. It is a computer - looking at it should show you the
        // screen, not print the last six lines into chat (that is what the log tail was).
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (containerId, inventory, p) -> new TerminalMenu(containerId, inventory, terminal, pos),
                            Component.translatable("block.shadowclangaming.terminal")),
                    buffer -> buffer.writeBlockPos(pos));
            // Push the backlog so the CRT is not empty on the first frame.
            var lines = terminal.lines();
            int from = Math.max(0, lines.size() - 20);
            if (lines.size() > from) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        new com.shadowclangaming.network.ScgNet.TextPayload(pos,
                                lines.subList(from, lines.size())));
            }
        }
        return InteractionResult.CONSUME;
    }
}
