package com.shadowclangaming.item;

import com.shadowclangaming.magic.ManaApi;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.registry.SCGItems;
import com.shadowclangaming.spell.Spell;
import com.shadowclangaming.spell.SpellCasting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The spell blueprint: a spell you wrote down, which is also a thing in the world.
 *
 * ──────────────────── THE THING THE USER ASKED FOR ────────────────────
 *
 * The inscription table does not care what it is writing on. Put a BOOK AND QUILL on it, inscribe, and
 * you get one of these - a written spell that exists as an item. That is the whole point: a blueprint
 * can be
 *
 *   1. LOADED INTO A RIFLE   (sneak-use while holding the rifle: the spell is bound to the gun)
 *   2. LOADED INTO A WARHEAD (sneak-use while holding a spell warhead: the missile casts it)
 *   3. USED BY ITSELF        (plain use: you cast it, straight out of the page, for full mana)
 *
 * Case 3 is the one that makes this a real item rather than a wrapper: a player with no rifle and no
 * missile can still throw a spell, and a blueprint is tradeable, so spells become things you can give
 * away.
 *
 * Stack size is 16 because a blueprint is paper, not a unique artefact - the spell is in the component,
 * so two blueprints of the same spell still stack.
 */
public class SpellBlueprintItem extends Item {

    /** Cooldown after casting from a blueprint: slower than a rifle, because a page is not a gun. */
    public static final int COOLDOWN_TICKS = 30;

    public SpellBlueprintItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack blueprint = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(blueprint);
        }
        Spell spell = blueprint.get(SCGDataComponents.SPELL.get());
        if (spell == null || spell.isEmpty()) {
            actionBar(player, Component.translatable("message.shadowclangaming.blueprint.blank")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.success(blueprint);
        }

        if (player.isShiftKeyDown()) {
            return load(serverPlayer, blueprint, spell, hand);
        }
        return cast(serverPlayer, blueprint, spell);
    }

    /** Sneak-use: push the spell into the rifle or warhead in the other hand. */
    private InteractionResultHolder<ItemStack> load(ServerPlayer player, ItemStack blueprint,
                                                    Spell spell, InteractionHand hand) {
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (other.isEmpty()) {
            actionBar(player, Component.translatable("message.shadowclangaming.blueprint.load_hint")
                    .withStyle(ChatFormatting.YELLOW));
            return InteractionResultHolder.success(blueprint);
        }

        if (other.getItem() instanceof SpellRifleItem) {
            other.set(SCGDataComponents.SPELL.get(), spell);
            if (player.getAbilities().instabuild) {
                return InteractionResultHolder.success(blueprint);
            }
            // The page is spent: the spell now lives in the gun.
            blueprint.shrink(1);
            actionBar(player, Component.translatable("message.shadowclangaming.blueprint.loaded_rifle",
                    spell.componentCount()).withStyle(ChatFormatting.GOLD));
            return InteractionResultHolder.success(blueprint);
        }

        if (other.getItem() == SCGItems.SPELL_MISSILE.get()) {
            other.set(SCGDataComponents.SPELL.get(), spell);
            if (player.getAbilities().instabuild) {
                return InteractionResultHolder.success(blueprint);
            }
            blueprint.shrink(1);
            actionBar(player, Component.translatable("message.shadowclangaming.blueprint.loaded_warhead",
                    spell.componentCount()).withStyle(ChatFormatting.GOLD));
            return InteractionResultHolder.success(blueprint);
        }

        actionBar(player, Component.translatable("message.shadowclangaming.blueprint.load_hint")
                .withStyle(ChatFormatting.YELLOW));
        return InteractionResultHolder.success(blueprint);
    }

    /** Plain use: cast it yourself. Full mana price, no frame efficiency, no gun power. */
    private InteractionResultHolder<ItemStack> cast(ServerPlayer player, ItemStack blueprint, Spell spell) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.success(blueprint);
        }
        long demand = spell.capacityDemand();
        if (ManaApi.capacity(player) < demand) {
            actionBar(player, Component.translatable("message.shadowclangaming.inscribe_need_capacity",
                    demand, ManaApi.capacity(player)).withStyle(ChatFormatting.RED));
            return InteractionResultHolder.success(blueprint);
        }
        long cost = spell.rawManaCost();
        if (!ManaApi.spend(player, cost)) {
            actionBar(player, Component.translatable("message.shadowclangaming.inscribe_need_mana", cost)
                    .withStyle(ChatFormatting.RED));
            return InteractionResultHolder.success(blueprint);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        if (!SpellCasting.castFromRifle(player, spell, 1.0F)) {
            ManaApi.addMana(player, cost);
            return InteractionResultHolder.success(blueprint);
        }
        ManaApi.onSpellCast(player, Math.min(9, spell.componentCount()));
        return InteractionResultHolder.success(blueprint);
    }

    private static void actionBar(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    // ------------------------------------------------------------------ presentation

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        Spell spell = stack.get(SCGDataComponents.SPELL.get());
        if (spell == null || spell.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.blueprint.blank")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        // The name typed at the table, if there is one.
        tooltip.add(Component.literal(spell.displayName()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.blueprint.summary",
                        spell.stepCount(), spell.componentCount())
                .withStyle(ChatFormatting.AQUA));
        for (int i = 0; i < spell.steps().size(); i++) {
            Spell.Step step = spell.steps().get(i);
            StringBuilder line = new StringBuilder("  ").append(i + 1).append(". ")
                    .append(step.shape().id());
            for (int c = 0; c < step.components().size(); c++) {
                line.append(c == 0 ? "  " : ", ")
                        .append(step.components().get(c).id().replace('_', ' '));
            }
            tooltip.add(Component.literal(line.toString()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.shadowclangaming.blueprint.cost",
                        spell.rawManaCost(), spell.capacityDemand())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.blueprint.use")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.blueprint.load")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        Spell spell = stack.get(SCGDataComponents.SPELL.get());
        return spell != null && !spell.isEmpty();
    }
}
