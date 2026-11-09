package com.shadowclangaming.warhead;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * The item tag the kamikaze vest reads.
 *
 * Uses the common {@code c:explosives} convention rather than a private tag, so any mod or datapack that
 * already publishes its explosives is picked up for free, and a pack author can add their own without
 * touching this mod. Shipped with TNT, TNT minecarts and end crystals in it.
 */
public final class KamikazeTags {

    public static final TagKey<Item> EXPLOSIVES = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "explosives"));

    private KamikazeTags() {
    }
}
