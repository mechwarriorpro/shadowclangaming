package com.shadowclangaming.armor;

import com.shadowclangaming.registry.SCGItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The once-a-second pass that makes the sets work.
 *
 * Counts how many pieces of each set a player is wearing, hands the result to the set, and applies the
 * sunlight penalty for the dark set. Everything is idempotent: effects are refreshed with a 2-second
 * duration every second, so taking a piece off removes them within a second or two with no bookkeeping.
 */
public final class ArmorSets {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private ArmorSets() {
    }

    public static void tickPlayer(ServerPlayer player) {
        int[] counts = new int[ArmorSet.values().length];
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack worn = player.getItemBySlot(slot);
            ArmorSet set = setOf(worn.getItem());
            if (set != null) {
                counts[set.ordinal()]++;
            }
        }

        for (ArmorSet set : ArmorSet.values()) {
            set.applyBonus(player, counts[set.ordinal()]);
        }

        // The price of the infamy set: daylight burns you, scaled to your max health so it never
        // becomes a one-shot on a low-health player.
        ArmorSet worn = heaviestSet(counts);
        if (worn != null && worn.sunlightPenalty() > 0.0F && counts[worn.ordinal()] >= ArmorSet.FULL_SET
                && player.level().isDay() && player.level().canSeeSky(player.blockPosition())
                && !player.fireImmune()) {
            player.hurt(player.damageSources().onFire(), player.getMaxHealth() * worn.sunlightPenalty());
        }
    }

    private static ArmorSet heaviestSet(int[] counts) {
        ArmorSet best = null;
        int bestCount = 0;
        for (ArmorSet set : ArmorSet.values()) {
            if (counts[set.ordinal()] > bestCount) {
                bestCount = counts[set.ordinal()];
                best = set;
            }
        }
        return best;
    }

    /** Which set an item belongs to, or null for anything that is not ours. */
    public static ArmorSet setOf(Item item) {
        if (item == SCGItems.INFAMY_HELMET.get() || item == SCGItems.INFAMY_CHESTPLATE.get()
                || item == SCGItems.INFAMY_LEGGINGS.get() || item == SCGItems.INFAMY_BOOTS.get()) {
            return ArmorSet.INFAMY;
        }
        if (item == SCGItems.SENTINEL_HELMET.get() || item == SCGItems.SENTINEL_CHESTPLATE.get()
                || item == SCGItems.SENTINEL_LEGGINGS.get() || item == SCGItems.SENTINEL_BOOTS.get()) {
            return ArmorSet.SENTINEL;
        }
        if (item == SCGItems.RAIDER_HELMET.get() || item == SCGItems.RAIDER_CHESTPLATE.get()
                || item == SCGItems.RAIDER_LEGGINGS.get() || item == SCGItems.RAIDER_BOOTS.get()) {
            return ArmorSet.RAIDER;
        }
        return null;
    }
}
