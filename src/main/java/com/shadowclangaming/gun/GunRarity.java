package com.shadowclangaming.gun;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;

/**
 * Rarity is the spine of the whole weapon system: it decides how many good parts a gun can roll, how
 * hard its numbers are multiplied, and whether it gets an element at all.
 *
 * The names are ours. "Prototype" is not "Legendary" and nothing here uses another game's vocabulary.
 */
public enum GunRarity implements StringRepresentable {
    SCAVENGED("scavenged", ChatFormatting.GRAY, 1.00F, 0, 0.00F),
    FIELDED("fielded", ChatFormatting.WHITE, 1.08F, 1, 0.25F),
    TUNED("tuned", ChatFormatting.BLUE, 1.18F, 2, 0.50F),
    PROTOTYPE("prototype", ChatFormatting.DARK_PURPLE, 1.32F, 3, 0.75F),
    RELIC("relic", ChatFormatting.GOLD, 1.50F, 4, 1.00F);

    public static final Codec<GunRarity> CODEC = StringRepresentable.fromEnum(GunRarity::values);

    private final String id;
    private final ChatFormatting format;
    private final float statMultiplier;
    /** How many "upgraded" part slots this rarity is allowed to roll. */
    private final int upgrades;
    /** Chance the gun comes out with a non-kinetic element. */
    private final float elementChance;

    GunRarity(String id, ChatFormatting format, float statMultiplier, int upgrades, float elementChance) {
        this.id = id;
        this.format = format;
        this.statMultiplier = statMultiplier;
        this.upgrades = upgrades;
        this.elementChance = elementChance;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String id() {
        return id;
    }

    public ChatFormatting format() {
        return format;
    }

    public float statMultiplier() {
        return statMultiplier;
    }

    public int upgrades() {
        return upgrades;
    }

    public float elementChance() {
        return elementChance;
    }

    public String translationKey() {
        return "rarity.shadowclangaming." + id;
    }

    /** Roll a rarity. Relics are rare enough that seeing one is a story. */
    public static GunRarity roll(net.minecraft.util.RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.02F) {
            return RELIC;
        }
        if (roll < 0.12F) {
            return PROTOTYPE;
        }
        if (roll < 0.32F) {
            return TUNED;
        }
        if (roll < 0.65F) {
            return FIELDED;
        }
        return SCAVENGED;
    }

    public static GunRarity byId(String id) {
        for (GunRarity rarity : values()) {
            if (rarity.id.equalsIgnoreCase(id)) {
                return rarity;
            }
        }
        return SCAVENGED;
    }
}
