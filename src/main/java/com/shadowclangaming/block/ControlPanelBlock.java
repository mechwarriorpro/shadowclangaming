package com.shadowclangaming.block;

import com.shadowclangaming.launcher.LauncherLevel;
import com.shadowclangaming.launcher.LauncherModule;
import com.shadowclangaming.launcher.UpgradeKit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The control panel block.
 *
 * Interactions, all on the block, no GUI:
 *   Upgrade Kit        raise the panel's tier by exactly one
 *   module item        try to install (and say why not if the launcher is not good enough)
 *   empty hand         print the full launcher report: tiers, armed state, target, modules
 */
public class ControlPanelBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<ControlPanelBlock> CODEC =
            simpleCodec(ControlPanelBlock::new);

    public ControlPanelBlock(Properties properties) {
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
        return new ControlPanelBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ControlPanelBlockEntity panel)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 1. upgrade kit
        Integer kitTier = stack.get(com.shadowclangaming.registry.SCGDataComponents.KIT_TIER.get());
        if (kitTier != null) {
            UpgradeKit.Result result = UpgradeKit.apply(panel.tier(), kitTier);
            switch (result) {
                case UPGRADED -> {
                    panel.setTier(kitTier);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    player.displayClientMessage(Component.translatable("message.shadowclangaming.panel_upgraded",
                            panel.tier()).withStyle(ChatFormatting.GREEN), true);
                }
                case ALREADY_MAX -> player.displayClientMessage(
                        Component.translatable("message.shadowclangaming.panel_max").withStyle(ChatFormatting.GRAY),
                        true);
                default -> player.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.kit_wrong_tier", kitTier, panel.tier() + 1)
                        .withStyle(ChatFormatting.RED), true);
            }
            return ItemInteractionResult.CONSUME;
        }

        // 2. a launcher module
        LauncherModule module = LauncherModule.byId("");
        boolean isModule = false;
        if (stack.getItem() == com.shadowclangaming.registry.SCGItems.INTERDIMENSIONAL_MODULE.get()) {
            module = LauncherModule.INTERDIMENSIONAL;
            isModule = true;
        }
        if (isModule) {
            LauncherLevel.Report report = report(serverLevel, panel);
            String refusal = module.refusal(report);
            if (refusal != null) {
                player.displayClientMessage(Component.translatable(refusal, module.requiredLevel())
                        .withStyle(ChatFormatting.RED), false);
                player.displayClientMessage(Component.literal(report.summary())
                        .withStyle(ChatFormatting.DARK_GRAY), false);
                return ItemInteractionResult.CONSUME;
            }
            if (panel.has(module)) {
                player.displayClientMessage(Component.translatable("message.shadowclangaming.module_already")
                        .withStyle(ChatFormatting.GRAY), true);
                return ItemInteractionResult.CONSUME;
            }
            panel.install(module);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.translatable("message.shadowclangaming.module_installed",
                    Component.translatable(module.translationKey())).withStyle(ChatFormatting.DARK_PURPLE),
                    false);
            return ItemInteractionResult.CONSUME;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof ControlPanelBlockEntity panel)) {
            return InteractionResult.PASS;
        }
        // Empty hand: open the cockpit. The status lines that used to be printed to chat are now the
        // screen's readouts; /term panel status still prints them for a terminal or a command block.
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (containerId, inventory, p) -> new ControlPanelMenu(containerId, inventory, panel, pos),
                            Component.translatable("block.shadowclangaming.control_panel")),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    /** The launcher report for a panel, using its linked pad if it has one. */
    public static LauncherLevel.Report report(ServerLevel level, ControlPanelBlockEntity panel) {
        BlockPos pad = panel.linkedPad();
        int padTier = panel.tier();
        if (pad != null && level.getBlockEntity(pad) instanceof LaunchPadBlockEntity launchPad) {
            padTier = launchPad.tier();
        }
        return LauncherLevel.scan(level, pad != null ? pad : panel.getBlockPos(), padTier);
    }

    /** The lines both the block and the terminal print. */
    public static java.util.List<String> status(ServerLevel level, ControlPanelBlockEntity panel) {
        LauncherLevel.Report report = report(level, panel);
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("control panel tier " + panel.tier() + (panel.armed() ? ", ARMED" : ", disarmed"));
        lines.add("launcher: " + report.summary());
        lines.add("pad: " + (panel.linkedPad() == null ? "not linked"
                : panel.linkedPad().getX() + " " + panel.linkedPad().getY() + " "
                + panel.linkedPad().getZ()));
        lines.add("target: " + (panel.target() == null ? "none"
                : panel.target().getX() + " " + panel.target().getY() + " " + panel.target().getZ()
                + (panel.targetDimension() == null ? "" : " in " + panel.targetDimension().location())));
        lines.add("modules: " + (panel.modules().isEmpty() ? "none" : panel.modules()));
        return lines;
    }
}
