package com.shadowclangaming.item;

import com.shadowclangaming.warhead.Funding;
import com.shadowclangaming.warhead.Minefield;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The ballistics calculator.
 *
 * Use it on a launch pad and it prints a full read-out: which warhead is loaded, how much funding is
 * loaded as currency, how much FE is in the pad (which becomes funding at 1 unit per FE), and - the
 * number everybody actually wants - the resulting blast radius.
 *
 * Use it in the air and it prints a table: what funding buys which radius, for the fundable warheads.
 * That is the "how much money do I need for a 64" question, answered without guesswork.
 */
public class BallisticsCalculatorItem extends Item {

    private static final int[] SHOWN_RADII = {8, 16, 32, 64, 96, 128, 256, 512, 1024, 4096};

    public BallisticsCalculatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel) || context.getPlayer() == null) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof com.shadowclangaming.block.LaunchPadBlockEntity pad)) {
            return InteractionResult.PASS;
        }

        ItemStack missile = pad.getMissile();
        if (missile.isEmpty() || !(missile.getItem() instanceof MissileItem item)) {
            say(player, "message.shadowclangaming.calc_empty", ChatFormatting.GRAY);
            return InteractionResult.CONSUME;
        }

        WarheadType warhead = item.warhead();
        long funding = MissileItem.fundingOf(missile);
        long energy = pad.energy().getEnergyStored();
        long total = funding + (pad.energyAcceptsFunding() ? energy : 0L);

        say(player, Component.translatable("message.shadowclangaming.calc_header",
                Component.translatable(warhead.translationKey())).getString(), ChatFormatting.GOLD);
        say(player, Component.translatable("message.shadowclangaming.calc_funding",
                String.format("%,d", funding)).getString(), ChatFormatting.YELLOW);
        if (pad.energyAcceptsFunding()) {
            say(player, Component.translatable("message.shadowclangaming.calc_energy",
                    String.format("%,d", energy)).getString(), ChatFormatting.AQUA);
        }
        if (warhead.acceptsFunding()) {
            int radius = warhead.fundedRadius(total);
            say(player, Component.translatable("message.shadowclangaming.calc_radius",
                    String.format("%,d", radius)).getString(), ChatFormatting.LIGHT_PURPLE);
            if (warhead == WarheadType.MINEFIELD) {
                say(player, Component.translatable("message.shadowclangaming.calc_mines",
                        Minefield.mineBudget(radius), Minefield.expectedMines(radius))
                        .getString(), ChatFormatting.DARK_GREEN);
            }
            int next = radius + 1;
            long need = warhead.fundingForRadius(next) - total;
            if (need > 0) {
                say(player, Component.translatable("message.shadowclangaming.calc_next",
                        String.format("%,d", need), next).getString(), ChatFormatting.DARK_GRAY);
            }
        } else {
            say(player, Component.translatable("message.shadowclangaming.calc_fixed",
                    String.format("%.1f", item.powerFor(missile))).getString(), ChatFormatting.GRAY);
        }
        if (pad.trackedEntity() != null) {
            say(player, Component.translatable("message.shadowclangaming.calc_tracking").getString(),
                    ChatFormatting.RED);
        }
        say(player, Component.translatable("message.shadowclangaming.calc_launcher",
                pad.report().summary()).getString(), ChatFormatting.DARK_AQUA);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        say(player, Component.translatable("message.shadowclangaming.calc_table_header").getString(),
                ChatFormatting.GOLD);
        for (int radius : SHOWN_RADII) {
            long cost = Funding.costForRadius(radius);
            say(player, Component.translatable("message.shadowclangaming.calc_table_row",
                    radius, String.format("%,d", cost)).getString(), ChatFormatting.GRAY);
        }
        say(player, Component.translatable("message.shadowclangaming.calc_minefield_header").getString(),
                ChatFormatting.DARK_GREEN);
        for (int radius : new int[]{32, 33, 42, 64, 96}) {
            say(player, Component.translatable("message.shadowclangaming.calc_minefield_row",
                    radius, String.format("%,d", Minefield.fundingForRadius(radius)),
                    Minefield.mineBudget(radius)).getString(), ChatFormatting.DARK_GREEN);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    private static void say(Player player, String literal, ChatFormatting colour) {
        player.displayClientMessage(Component.literal(literal).withStyle(colour), false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.shadowclangaming.calc.use_on_pad").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.calc.use_air").withStyle(ChatFormatting.DARK_GRAY));
    }
}
