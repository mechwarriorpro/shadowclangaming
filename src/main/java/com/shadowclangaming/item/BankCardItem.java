package com.shadowclangaming.item;

import com.shadowclangaming.block.LaunchPadBlockEntity;
import com.shadowclangaming.compat.economy.Money;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.warhead.Funding;
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
 * BANK CARD - pays for warheads straight out of the player's bank balance.
 *
 * Which bank is decided by {@link Money}: EconomyCraft if it is installed, otherwise Create:
 * Numismatics. The card used to hardcode EconomyCraft and refused to work on a Numismatics server,
 * which is the bug this indirection exists to prevent.
 *
 * This is the "money is power" primary path. Coins (War Bonds / Numismatics) remain a fully supported
 * optional extra: some players would rather hand over physical currency than let a card near their
 * savings, and coins work when EconomyCraft's API changes.
 *
 * CONTROLS
 *   Right-click a launch pad  -> withdraw the selected amount and load it into the Numismatic warhead
 *   Sneak + right-click       -> cycle the withdrawal tier: 1k / 10k / 100k / 1M / "next radius step"
 *
 * No GUI: the tier is stored on the card, so four sneak-clicks reaches a million units. Every action
 * reports the balance left and the resulting blast radius in chat.
 */
public class BankCardItem extends Item {

    /** Withdrawal amounts, in the smallest currency unit. -1 means "exactly enough for +1 radius". */
    public static final long[] TIERS = {1_000L, 10_000L, 100_000L, 1_000_000L, -1L};

    public static final String[] TIER_LABELS = {"1,000", "10,000", "100,000", "1,000,000", "next radius step"};

    public BankCardItem(Properties properties) {
        super(properties);
    }

    /**
     * "Signs" the card with its owner the first time it is used.
     *
     * The Auto-Trader needs to know whose balance to credit, and the card is the only thing that
     * travels between the player and the machine, so the UUID rides on the card. Signing is
     * idempotent and deliberately does not let you re-sign an already signed card - a stolen card
     * stays attached to its original owner's account, which is the interesting failure mode.
     *
     * @return true if the card was unsigned and is now bound to {@code player}.
     */
    public static boolean signFor(ItemStack stack, Player player) {
        String existing = stack.get(SCGDataComponents.CARD_OWNER.get());
        if (existing != null && !existing.isEmpty()) {
            return false;
        }
        stack.set(SCGDataComponents.CARD_OWNER.get(), player.getUUID().toString());
        return true;
    }

    private static int tierIndex(ItemStack stack) {
        Integer index = stack.get(SCGDataComponents.CARD_TIER.get());
        return index == null ? 0 : Math.floorMod(index, TIERS.length);
    }

    private static void setTierIndex(ItemStack stack, int index) {
        stack.set(SCGDataComponents.CARD_TIER.get(), Math.floorMod(index, TIERS.length));
    }

    private static long lastBalance(ItemStack stack) {
        Long balance = stack.get(SCGDataComponents.CARD_BALANCE.get());
        return balance == null ? -1L : balance;
    }

    // ------------------------------------------------------------------ cycling the amount

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        signFor(stack, player);

        // Right-click in air: cycle tier. (Right-clicking a pad is handled in useOn.)
        if (!player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.shadowclangaming.card_hint").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        if (!level.isClientSide) {
            int next = tierIndex(stack) + 1;
            setTierIndex(stack, next);
            player.displayClientMessage(Component.translatable("message.shadowclangaming.card_tier",
                    TIER_LABELS[Math.floorMod(next, TIERS.length)]).withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    // ------------------------------------------------------------------ paying for a warhead

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack card = context.getItemInHand();

        if (player != null) {
            signFor(card, player);
        }

        if (player == null || !(level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!Money.available()) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.card_no_api")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (!pad.hasMissile()) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_no_missile")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (!(pad.getMissile().getItem() instanceof MissileItem missile)
                || !missile.warhead().acceptsFunding()) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.funding_wrong_warhead")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        long amount = TIERS[tierIndex(card)];
        return fund((ServerLevel) level, player, card, pad, amount);
    }

    /**
     * The payment itself, shared by this card and by a Create: Numismatics Bank Card.
     *
     * Kept static and free of our own components so a foreign card can use it: the only thing that
     * differs between the two is which item identifies the account, and {@link Money} handles that.
     *
     * @param requested the amount to withdraw, or -1 for "exactly enough for the next radius step"
     */
    public static InteractionResult fund(ServerLevel level, Player player, ItemStack payer,
                                         LaunchPadBlockEntity pad, long requested) {
        if (!Money.available()) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.card_no_api")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (!pad.hasMissile()) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_no_missile")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (!(pad.getMissile().getItem() instanceof MissileItem missile)
                || !missile.warhead().acceptsFunding()) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.funding_wrong_warhead")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        long current = MissileItem.fundingOf(pad.getMissile());
        long amount = requested;
        if (amount <= 0L) {
            // Exactly enough to gain the next block of radius - the cheapest useful purchase.
            // The warhead picks its own curve (blast radius vs dome radius).
            int currentRadius = missile.warhead().radiusForFunding(current);
            amount = Math.max(1L, missile.warhead().fundingForRadius(currentRadius + 1) - current);
        }

        long balance = Money.balance(level.getServer(), player, payer);
        if (balance < amount) {
            // Offer the largest amount they can actually afford, so the click still does something.
            if (balance <= 0L) {
                player.displayClientMessage(Component.translatable("message.shadowclangaming.card_broke")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            amount = balance;
        }

        if (!Money.spend(level.getServer(), player, payer, amount)) {
            player.displayClientMessage(
                    Component.translatable("message.shadowclangaming.card_broke").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        pad.addFunding(amount);
        long funding = MissileItem.fundingOf(pad.getMissile());
        int radius = missile.warhead().radiusForFunding(funding);

        long after = Money.balance(level.getServer(), player, payer);
        // Only OUR card carries a cached balance; a Numismatics card has nowhere to put one.
        if (payer.getItem() instanceof BankCardItem) {
            payer.set(SCGDataComponents.CARD_BALANCE.get(), after);
        }

        player.displayClientMessage(Component.translatable("message.shadowclangaming.card_charged",
                Money.format(level.getServer(), amount),
                Money.format(level.getServer(), after),
                String.format("%,d", funding),
                radius).withStyle(ChatFormatting.GOLD), false);

        // Money is power: announce strategic warheads server-wide.
        if (radius >= com.shadowclangaming.block.LaunchPadBlock.WARNING_RADIUS) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.shadowclangaming.strategic_warning",
                            player.getDisplayName(), radius), false);
        }
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ tooltip

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.shadowclangaming.card_tier",
                TIER_LABELS[tierIndex(stack)]).withStyle(ChatFormatting.GOLD));

        long balance = lastBalance(stack);
        if (balance >= 0L) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.card_balance",
                    String.format("%,d", balance)).withStyle(ChatFormatting.GREEN));
        }
        String owner = stack.get(SCGDataComponents.CARD_OWNER.get());
        if (owner != null && !owner.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.card_owner",
                    owner.substring(0, Math.min(8, owner.length())))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }

        tooltip.add(Component.translatable(Money.available()
                        ? "tooltip.shadowclangaming.card.linked"
                        : "tooltip.shadowclangaming.card.no_economy")
                .withStyle(Money.available() ? ChatFormatting.DARK_GRAY : ChatFormatting.RED));
        if (Money.available()) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.card.bank", Money.label())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.shadowclangaming.card.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
