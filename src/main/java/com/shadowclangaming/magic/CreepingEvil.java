package com.shadowclangaming.magic;

import com.shadowclangaming.registry.SCGMobEffects;
import com.shadowclangaming.registry.SCGItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * CREEPING EVIL: the bill for the blood pact, arriving slowly.
 *
 * ──────────────────── HOW IT BEHAVES ────────────────────
 *
 * A hidden status effect that counts down like any other, except that reaching zero does not end it -
 * it promotes itself:
 *
 *     level 1   6 hours   -1 heart    (starts the moment the pact is formed)
 *     level 2   6 hours   -2 hearts
 *     level 3   6 hours   -3 hearts
 *     ...
 *
 * Six hours of REAL time per level: 432,000 ticks. It is deliberately long, because it is not supposed
 * to kill anybody in one session - it is supposed to still be there next week, one heart worse.
 *
 * The effect instance is applied with visible/icon flags off, so nothing appears in the HUD. The only
 * feedback is the hearts going missing.
 *
 * ──────────────────── THE TWO COUNTERS ────────────────────
 *
 *   HOLY NECKLACE (carried)   freezes the timer. The curse does not advance while you carry it, but it
 *                             does not go away either, and it blocks ALL mana regeneration while held.
 *   HOLY WATER (cleric)       consumed at midday while carrying the necklace, breaks the curse
 *                             permanently: level to zero, hearts restored, and chalk circles work again.
 *
 * ──────────────────── WHY THE ATTRIBUTE MODIFIER ────────────────────
 *
 * Hearts are removed with a MAX_HEALTH modifier carrying a fixed id, recomputed from scratch every
 * time. That makes the state self-healing: whatever happens (death, dimension change, another mod
 * touching the attribute), the next tick sets the correct value rather than stacking a second penalty.
 */
public final class CreepingEvil {

    public static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath("shadowclangaming", "creeping_evil");
    public static final ResourceLocation MODIFIER_ID_SUPPRESSED =
            ResourceLocation.fromNamespaceAndPath("shadowclangaming", "creeping_evil_suppressed");

    /** Six hours of real time per level. */
    public static final int TICKS_PER_LEVEL = 6 * 60 * 60 * 20;

    /** Health removed per level (one heart = 2 HP). */
    public static final double HEALTH_PER_LEVEL = 2.0D;

    /** The curse never takes the last heart: below this the player would simply drop dead. */
    public static final double MIN_MAX_HEALTH = 2.0D;

    /** Midday window, as a fraction of the day: 0.25 is noon. +/- 0.02 is about 48 seconds. */
    public static final float MIDDAY_CENTRE = 0.25F;
    public static final float MIDDAY_HALF_WIDTH = 0.02F;

    // ------------------------------------------------------------------ progression

    /** Called once per second for each online player from the mana tick listener. */
    public static void tickPlayer(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();

        if (!ManaApi.isEvil(server, player.getUUID())) {
            // Not cursed: make sure nothing is left over (cure, or an op command).
            clearModifier(player);
            if (player.hasEffect(SCGMobEffects.CREEPING_EVIL)) {
                player.removeEffect(SCGMobEffects.CREEPING_EVIL);
            }
            return;
        }

        // A brand new curse starts at level 1 with a full timer.
        int level = ManaApi.evilLevel(server, player.getUUID());
        long ticks = ManaApi.evilTicks(server, player.getUUID());
        if (level <= 0) {
            level = 1;
            ticks = TICKS_PER_LEVEL;
            ManaApi.setCurse(server, player.getUUID(), level, ticks);
        }

        boolean suppressed = carriesNecklace(player);

        if (!suppressed) {
            ticks -= 20L; // this method runs once a second
            if (ticks <= 0L) {
                level++;
                ticks = TICKS_PER_LEVEL;
                player.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.creeping_evil_level", level, level), true);
                player.serverLevel().playSound(null, player.blockPosition(),
                        SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.8F, 0.5F);
            }
            ManaApi.setCurse(server, player.getUUID(), level, ticks);
        }

        applyModifier(player, level);
        applyEffect(player, ticks, level);
        glowIfMidday(player, suppressed);
    }

    // ------------------------------------------------------------------ hearts

    /** Recomputes the heart penalty from scratch. Idempotent by construction. */
    private static void applyModifier(ServerPlayer player, int level) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        double base = attribute.getBaseValue();
        double penalty = Math.min(HEALTH_PER_LEVEL * level, Math.max(0.0D, base - MIN_MAX_HEALTH));

        AttributeModifier existing = attribute.getModifier(MODIFIER_ID);
        if (existing != null && Math.abs(existing.amount() - -penalty) < 1.0E-6D) {
            return; // already correct, do not churn the attribute map
        }
        attribute.removeModifier(MODIFIER_ID);
        if (penalty > 0.0D) {
            attribute.addTransientModifier(
                    new AttributeModifier(MODIFIER_ID, -penalty, AttributeModifier.Operation.ADD_VALUE));
        }
        // Never leave the player above their new maximum.
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void clearModifier(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null && attribute.getModifier(MODIFIER_ID) != null) {
            attribute.removeModifier(MODIFIER_ID);
        }
    }

    private static void applyEffect(ServerPlayer player, long ticks, int level) {
        int duration = (int) Math.min(Integer.MAX_VALUE - 1, Math.max(20L, ticks));
        MobEffectInstance current = player.getEffect(SCGMobEffects.CREEPING_EVIL);
        if (current != null && current.getDuration() > duration - 40 && current.getAmplifier() == level) {
            return;
        }
        // ambient = false, visible = false, showIcon = false -> completely absent from the HUD.
        player.removeEffect(SCGMobEffects.CREEPING_EVIL);
        player.addEffect(new MobEffectInstance(SCGMobEffects.CREEPING_EVIL, duration, level, false, false, false));
    }

    // ------------------------------------------------------------------ the necklace

    /** "Wearing" the necklace means carrying it anywhere in the inventory. See docs/MAGIC.md. */
    public static boolean carriesNecklace(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(SCGItems.HOLY_NECKLACE.get())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isMidday(ServerLevel level) {
        float time = level.getTimeOfDay(0.0F);
        return Math.abs(time - MIDDAY_CENTRE) <= MIDDAY_HALF_WIDTH;
    }

    /** The necklace used to glow at midday. That glow is the only clue the cure window is open. */
    private static void glowIfMidday(ServerPlayer player, boolean suppressed) {
        if (!suppressed) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!isMidday(level)) {
            return;
        }
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.2D, player.getZ(), 4, 0.35D, 0.4D, 0.35D, 0.01D);
        if (level.getGameTime() % 200L == 0L) {
            player.displayClientMessage(
                    Component.translatable("message.shadowclangaming.necklace_glows"), true);
        }
    }

    // ------------------------------------------------------------------ the cure

    /** @return true if the curse was actually broken. */
    public static boolean breakCurse(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();
        if (!ManaApi.isEvil(server, player.getUUID())) {
            return false;
        }
        ManaApi.markEvil(server, player.getUUID(), false);
        ManaApi.setCurse(server, player.getUUID(), 0, 0L);
        ManaApi.endPact(server, player.getUUID());
        ManaApi.setRitual(server, player.getUUID(), 0, 0L);

        player.removeEffect(SCGMobEffects.CREEPING_EVIL);
        clearModifier(player);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }

        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1.0D, player.getZ(), 80, 0.5D, 1.0D, 0.5D, 0.25D);
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.curse_broken", player.getDisplayName()), false);
        return true;
    }

    /** Fully read-only description, used by the /shadowclangaming curse command. */
    public static String describe(MinecraftServer server, ServerPlayer player) {
        if (!ManaApi.isEvil(server, player.getUUID())) {
            return "not cursed";
        }
        int level = ManaApi.evilLevel(server, player.getUUID());
        long ticks = ManaApi.evilTicks(server, player.getUUID());
        boolean suppressed = carriesNecklace(player);
        return String.format("Creeping Evil %d (%.2f h left%s) - hearts -%d",
                level, ticks / 20.0D / 3600.0D,
                suppressed ? ", FROZEN by necklace" : "",
                (int) Math.round(HEALTH_PER_LEVEL * level / 2.0D));
    }

    private CreepingEvil() {
    }
}
