package com.shadowclangaming.armor;

import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * The three armour sets. Each one is a personality, and each one only pays out when you wear the
 * whole thing - that is the deal the old set-bonus armour made, and it is a good one because it forces
 * a choice instead of letting players stack the best piece of everything.
 *
 *   INFAMY     the dark set. Resistance and Strength, but the sun hates you: burning in daylight is
 *              the price of wearing it. Four pieces and you also hit harder the lower your health.
 *   SENTINEL    the wall. Regeneration and knockback resistance; slow, but extremely hard to kill.
 *   RAIDER      the fast one. Speed, jump, night vision; less protection, and it eats hunger.
 *
 * None of these names, colours or mechanics are borrowed.
 */
public enum ArmorSet {
    INFAMY("infamy", ChatFormatting.DARK_RED, 0.02F),
    SENTINEL("sentinel", ChatFormatting.AQUA, 0.0F),
    RAIDER("raider", ChatFormatting.GOLD, 0.0F);

    /** Pieces needed before the set bonus wakes up. */
    public static final int FULL_SET = 4;

    private final String id;
    private final ChatFormatting format;
    /** Fraction of max health taken per second as sunlight damage, 0 = none. */
    private final float sunlightPenalty;

    ArmorSet(String id, ChatFormatting format, float sunlightPenalty) {
        this.id = id;
        this.format = format;
        this.sunlightPenalty = sunlightPenalty;
    }

    public String id() {
        return id;
    }

    public ChatFormatting format() {
        return format;
    }

    public float sunlightPenalty() {
        return sunlightPenalty;
    }

    public String translationKey() {
        return "set.shadowclangaming." + id;
    }

    /**
     * The once-a-second tick for a player wearing this set. Called only when all four pieces are on,
     * with the effects refreshed rather than stacked (duration is always just over a second, so
     * taking a piece off removes them almost immediately).
     */
    public void applyBonus(Player player, int pieces) {
        if (pieces < FULL_SET) {
            return;
        }
        switch (this) {
            case INFAMY -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0));
                // The dark set thrives at low health: below a third, it hits much harder.
                if (player.getHealth() < player.getMaxHealth() / 3.0F) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 2));
                }
            }
            case SENTINEL -> {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            }
            case RAIDER -> {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 1));
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0));
            }
        }
    }

    public static ArmorSet byId(String id) {
        for (ArmorSet set : values()) {
            if (set.id.equalsIgnoreCase(id)) {
                return set;
            }
        }
        return INFAMY;
    }
}
