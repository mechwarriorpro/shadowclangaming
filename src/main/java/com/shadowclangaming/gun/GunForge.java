package com.shadowclangaming.gun;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The generator. Give it a seed and a rarity and it produces a whole gun - parts, numbers, element and
 * paint job - with no Minecraft objects involved beyond the RNG interface, so it can be unit tested.
 *
 * THE DESIGN RULE: every number on the tooltip is the product of things the player can read. There is
 * no hidden "+12% because the RNG felt like it". If a gun hits hard, you can see which barrel did it.
 */
public final class GunForge {

    public static final float BASE_DAMAGE = 9.0F;
    public static final float BASE_FIRE_RATE = 2.2F;
    public static final float BASE_ACCURACY = 0.80F;
    public static final int BASE_MAGAZINE = 18;
    public static final int BASE_RELOAD_TICKS = 40;

    /** How many different silhouettes the art has. Every element gets its own palette on top. */
    public static final int STYLES = 5;

    private GunForge() {
    }

    /** Roll a gun whose rarity is rolled for you (this is what the fabricator and loot use). */
    public static GunStats roll(RandomSource random) {
        return roll(random, GunRarity.roll(random));
    }

    /** Roll a gun at a known rarity. */
    public static GunStats roll(RandomSource random, GunRarity rarity) {
        long seed = random.nextLong();
        GunMaker maker = GunMaker.pick(random);

        // Six slots, one part each. Higher rarity re-rolls its best slots, which is why a relic feels
        // hand-built: it had four extra tries at every position.
        List<GunPart> parts = new ArrayList<>(6);
        for (GunPart.Slot slot : GunPart.Slot.values()) {
            GunPart[] options = GunPart.forSlot(slot);
            GunPart best = options[random.nextInt(options.length)];
            for (int attempt = 0; attempt < rarity.upgrades(); attempt++) {
                GunPart candidate = options[random.nextInt(options.length)];
                if (score(candidate) > score(best)) {
                    best = candidate;
                }
            }
            parts.add(best);
        }

        float damageMult = 1.0F;
        float rateMult = 1.0F;
        float accuracyMult = 1.0F;
        float magazineMult = 1.0F;
        for (GunPart part : parts) {
            damageMult *= part.damageMult();
            rateMult *= part.rateMult();
            accuracyMult *= part.accuracyMult();
            magazineMult *= part.magazineMult();
        }

        float damage = BASE_DAMAGE * maker.damageBias() * damageMult * rarity.statMultiplier();
        float fireRate = clamp(BASE_FIRE_RATE * maker.rateBias() * rateMult, 0.4F, 9.0F);
        float accuracy = clamp(BASE_ACCURACY * maker.accuracyBias() * accuracyMult, 0.25F, 0.995F);
        int magazine = Math.max(3, Math.min(300,
                Math.round(BASE_MAGAZINE * maker.magazineBias() * magazineMult)));
        int reload = Math.max(10, Math.min(140,
                Math.round(BASE_RELOAD_TICKS * (float) Math.sqrt(magazine / (float) BASE_MAGAZINE))));

        GunElement element = GunElement.KINETIC;
        if (random.nextFloat() < rarity.elementChance()) {
            GunElement[] options = GunElement.values();
            element = options[1 + random.nextInt(options.length - 1)];
        }

        int style = random.nextInt(STYLES);
        return new GunStats(seed, maker, rarity, element, style, damage, fireRate, accuracy,
                magazine, reload, parts);
    }

    /** Rough "how good is this part" score, used only to pick the better of two rolls. */
    private static float score(GunPart part) {
        return part.damageMult() * 1.2F + part.rateMult() + part.accuracyMult() * 0.9F
                + part.magazineMult() * 0.3F;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * The name. Maker + element (or rarity, if kinetic) + a noun taken from the barrel, so
     * "Vulcan Ember Lance" reads like a gun and not like a database row.
     */
    public static String name(GunStats stats) {
        String maker = titleCase(stats.maker().id());
        String middle = stats.element() == GunElement.KINETIC
                ? titleCase(stats.rarity().id())
                : titleCase(stats.element().id());
        GunPart barrel = stats.partIn(GunPart.Slot.BARREL);
        String noun = barrel == null ? "Rifle" : nounFor(barrel);
        return maker + " " + middle + " " + noun;
    }

    private static String nounFor(GunPart barrel) {
        return switch (barrel) {
            case BARREL_SMOOTH -> "Rifle";
            case BARREL_RIFLED -> "Carbine";
            case BARREL_SAWN -> "Scattergun";
            case BARREL_LONG -> "Lance";
            case BARREL_SPLIT -> "Splitter";
            default -> "Rifle";
        };
    }

    public static String titleCase(String id) {
        String[] words = id.split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
