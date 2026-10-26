package com.shadowclangaming.item;

import com.shadowclangaming.block.LaunchPadBlockEntity;
import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Target Designator - a remote firing device.
 *
 * HOW TO USE
 *   1. Right-click a Launch Pad with it        -> binds the designator to that pad.
 *   2. Right-click in the air                 -> fires the pad's loaded missile at whatever block you
 *                                                are looking at (up to RANGE blocks) and drains energy.
 *   3. Sneak + right-click with an RTG Pellet in your inventory -> recharge.
 *
 * The energy buffer is exposed through NeoForge's standard item energy capability, so any tech mod's
 * charger/cable will top it up as well.
 */
public class TargetDesignatorItem extends Item {
    public static final int MAX_ENERGY = 60_000;
    public static final int ENERGY_PER_SHOT = 6_000;
    public static final int ENERGY_PER_PELLET = 15_000;
    public static final double RANGE = 256.0D;

    public TargetDesignatorItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ energy (data component backed)

    public static int getEnergy(ItemStack stack) {
        Integer stored = stack.get(SCGDataComponents.ENERGY.get());
        return stored == null ? 0 : stored;
    }

    public static void setEnergy(ItemStack stack, int energy) {
        stack.set(SCGDataComponents.ENERGY.get(), Math.max(0, Math.min(MAX_ENERGY, energy)));
    }

    public static boolean consumeEnergy(ItemStack stack, int amount) {
        int stored = getEnergy(stack);
        if (stored < amount) {
            return false;
        }
        setEnergy(stack, stored - amount);
        return true;
    }

    /**
     * Registers the NeoForge energy capability for this item. Called from the mod constructor via
     * RegisterCapabilitiesEvent - that is the modern way to give an item an FE buffer.
     */
    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerItem(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM,
                (stack, context) -> new net.neoforged.neoforge.energy.IEnergyStorage() {
                    @Override
                    public int receiveEnergy(int toReceive, boolean simulate) {
                        int stored = getEnergy(stack);
                        int accepted = Math.min(MAX_ENERGY - stored, Math.max(0, toReceive));
                        if (!simulate && accepted > 0) {
                            setEnergy(stack, stored + accepted);
                        }
                        return accepted;
                    }

                    @Override
                    public int extractEnergy(int toExtract, boolean simulate) {
                        int stored = getEnergy(stack);
                        int removed = Math.min(stored, Math.max(0, toExtract));
                        if (!simulate && removed > 0) {
                            setEnergy(stack, stored - removed);
                        }
                        return removed;
                    }

                    @Override
                    public int getEnergyStored() {
                        return getEnergy(stack);
                    }

                    @Override
                    public int getMaxEnergyStored() {
                        return MAX_ENERGY;
                    }

                    @Override
                    public boolean canExtract() {
                        return true;
                    }

                    @Override
                    public boolean canReceive() {
                        return true;
                    }
                },
                com.shadowclangaming.registry.SCGItems.TARGET_DESIGNATOR.get());
    }

    // ------------------------------------------------------------------ binding

    /**
     * Right-click a launch pad to COPY ITS FREQUENCY.
     *
     * WHY A CHANNEL AND NOT A COORDINATE. A designator used to store the pad's BlockPos, which is fine
     * until the pad moves - and pads move now: on a Sable subgrid the whole silo can be a ship, and a
     * stored coordinate would keep pointing at empty water. A frequency is a name, and names survive
     * motion: the pad re-registers its current position on its channel every time it moves (see
     * LaunchPadBlockEntity#tickServer), so the designator only has to remember the number.
     *
     * It also means one designator can address a *battery*: every pad on channel 7 answers.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();

        BlockEntity be = level.getBlockEntity(pos);

        // Anything that carries a channel can tune the designator: a pad, a cable, a terminal. That is how
        // a player sets an exact channel (including a negative one) without a pad within reach - the cable's
        // channel comes straight from a Frequency Chip, and the terminal takes "freq -193" as a command.
        if (!(be instanceof LaunchPadBlockEntity)) {
            int channel = be instanceof com.shadowclangaming.computer.NetworkCableBlockEntity cable
                    ? cable.channel()
                    : be instanceof com.shadowclangaming.computer.TerminalBlockEntity terminal
                    ? terminal.frequency()
                    : com.shadowclangaming.computer.Channels.NONE;
            if (!com.shadowclangaming.computer.Channels.isSet(channel)) {
                return InteractionResult.PASS;
            }
            setChannel(stack, channel);
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                                "message.shadowclangaming.designator_bound", channel)
                        .withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        LaunchPadBlockEntity pad = (LaunchPadBlockEntity) be;
        if (!pad.hasChannel()) {
            // The pad has no channel yet. If the DESIGNATOR has one, push it onto the pad: that is the
            // painless order of operations (tune the designator, then tap the silo) and it means a player
            // never has to own a Frequency Chip to pair a lone silo.
            int mine = channelOf(stack);
            if (hasChannel(stack) && !level.isClientSide) {
                pad.setFrequency(mine);
                if (player != null) {
                    player.displayClientMessage(Component.translatable(
                            "message.shadowclangaming.designator_tuned_pad", mine)
                            .withStyle(ChatFormatting.GREEN), true);
                }
                return InteractionResult.sidedSuccess(false);
            }
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.designator_no_channel")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        setChannel(stack, pad.frequency());
        if (player != null && !level.isClientSide) {
            player.displayClientMessage(
                    Component.translatable("message.shadowclangaming.designator_bound",
                            pad.frequency()).withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * The channel this designator transmits on, or {@link Channels#NONE} if it has never been paired.
     *
     * Note the sentinel: "unset" is NOT "-1", because -1 is a perfectly good channel. Reading "channel < 0
     * means unbound" is what made a designator tuned to -193 claim it was never tuned at all.
     */
    public static int channelOf(ItemStack stack) {
        Integer value = stack.get(SCGDataComponents.FREQUENCY.get());
        return value == null ? com.shadowclangaming.computer.Channels.NONE
                : com.shadowclangaming.computer.Channels.sanitise(value);
    }

    /** Has this designator been tuned to anything? Negative channels are real channels. */
    public static boolean hasChannel(ItemStack stack) {
        return com.shadowclangaming.computer.Channels.isSet(channelOf(stack));
    }

    public static void setChannel(ItemStack stack, int channel) {
        stack.set(SCGDataComponents.FREQUENCY.get(),
                com.shadowclangaming.computer.Channels.sanitise(channel));
    }

    // ------------------------------------------------------------------ firing / recharging

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Recharge: sneak + right click, consuming one RTG pellet from the inventory.
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                return InteractionResultHolder.success(player.getItemInHand(hand));
            }
            if (getEnergy(stack) >= MAX_ENERGY) {
                player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_full")
                        .withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.success(player.getItemInHand(hand));
            }
            if (!consumeOnePellet(player)) {
                player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_no_pellet")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.success(player.getItemInHand(hand));
            }
            setEnergy(stack, getEnergy(stack) + ENERGY_PER_PELLET);
            player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_charged",
                    getEnergy(stack), MAX_ENERGY).withStyle(ChatFormatting.GREEN), true);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        if (level.isClientSide) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        int channel = channelOf(stack);
        if (!hasChannel(stack)) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_unbound")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }

        if (getEnergy(stack) < ENERGY_PER_SHOT) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_no_energy",
                    getEnergy(stack), ENERGY_PER_SHOT).withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }

        // The client knows where it is looking; the server owns the pads. So the client sends the channel
        // and the target, and the server resolves the battery - which is also what makes this work when
        // the pads are on a moving grid somewhere the client is not.
        BlockPos target = resolveLookedAtBlock(player);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.shadowclangaming.network.ScgNet.DesignatorFirePayload(
                        channel, target.getX(), target.getY(), target.getZ()));

        // The energy is spent optimistically here and re-checked on the server: a shot that is refused
        // (no pad ready, out of range) is refunded there. Losing a shot to a dead channel is worse.
        consumeEnergy(stack, ENERGY_PER_SHOT);
        player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_transmit",
                channel).withStyle(ChatFormatting.GOLD), true);
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    /**
     * Raycast from the player's eyes. If we hit nothing, use the point RANGE blocks ahead and drop it
     * to a block position, so a missile can still be aimed at open sky.
     */
    private static BlockPos resolveLookedAtBlock(Player player) {
        HitResult hit = player.pick(RANGE, 0.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos();
        }
        Vec3 end = player.getEyePosition().add(player.getLookAngle().scale(RANGE));
        return BlockPos.containing(end.x, end.y, end.z);
    }

    private static boolean consumeOnePellet(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.getItem() == com.shadowclangaming.registry.SCGItems.RTG_PELLET.get()) {
                candidate.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ tooltip

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int energy = getEnergy(stack);
        tooltip.add(Component.translatable("tooltip.shadowclangaming.designator.energy", energy, MAX_ENERGY)
                .withStyle(energy >= ENERGY_PER_SHOT ? ChatFormatting.GREEN : ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.designator.shots",
                energy / ENERGY_PER_SHOT).withStyle(ChatFormatting.DARK_GRAY));

        int channel = channelOf(stack);
        if (!hasChannel(stack)) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.designator.unbound")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.designator.channel",
                    channel).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.shadowclangaming.designator.battery")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.shadowclangaming.designator.usage").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getEnergy(stack) / MAX_ENERGY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x38D66B;
    }
}
