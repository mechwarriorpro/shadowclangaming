package com.shadowclangaming.registry;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/**
 * Three custom armour materials.
 *
 * In 1.21.1 a material is a record: a defence number per slot, an enchantability, the equip sound, a
 * repair ingredient, and a list of texture Layers. The Layer's asset name is the file name under
 * assets/&lt;namespace&gt;/textures/models/armor/, so "shadowclangaming:infamy" means the game looks for
 * infamy_layer_1.png and infamy_layer_2.png in this mod's folder. No renderer, no registration of
 * model files - just those two PNGs.
 *
 * (1.21.2 moved this to a separate equipment-asset JSON system. On 1.21.1 the code above is correct
 * and no equipment JSON is needed.)
 */
public final class SCGArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, ShadowClanGaming.MOD_ID);

    private static ArmorMaterial.Layer layer(String name) {
        return new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, name));
    }

    /** The dark set: softer than netherite, but the strongest damage bonus of the three. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> INFAMY = MATERIALS.register(
            "infamy", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.HELMET, 3, ArmorItem.Type.CHESTPLATE, 8,
                            ArmorItem.Type.LEGGINGS, 6, ArmorItem.Type.BOOTS, 3),
                    18,
                    SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.of(Items.OBSIDIAN),
                    List.of(layer("infamy")),
                    2.0F,
                    0.05F));

    /** The wall: the highest protection of the three, and the heaviest. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SENTINEL = MATERIALS.register(
            "sentinel", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.HELMET, 4, ArmorItem.Type.CHESTPLATE, 9,
                            ArmorItem.Type.LEGGINGS, 7, ArmorItem.Type.BOOTS, 4),
                    12,
                    SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.of(Items.IRON_INGOT),
                    List.of(layer("sentinel")),
                    3.5F,
                    0.15F));

    /** The fast set: the least protection, the most mobility. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> RAIDER = MATERIALS.register(
            "raider", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.HELMET, 2, ArmorItem.Type.CHESTPLATE, 6,
                            ArmorItem.Type.LEGGINGS, 5, ArmorItem.Type.BOOTS, 2),
                    22,
                    SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.of(Items.LEATHER),
                    List.of(layer("raider")),
                    0.5F,
                    0.0F));

    /**
     * The kamikaze vest's material.
     *
     * Deliberately BAD armour: leather-grade protection, no toughness, no knockback resistance. It must not
     * be a strictly better chestplate that also explodes, or everyone would wear it all the time. The whole
     * item is a trade: you give up a real chestplate slot for a bomb you will only ever use once.
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> KAMIKAZE = MATERIALS.register(
            "kamikaze", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.HELMET, 1, ArmorItem.Type.CHESTPLATE, 4,
                            ArmorItem.Type.LEGGINGS, 3, ArmorItem.Type.BOOTS, 1),
                    5,
                    SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.of(Items.TNT),
                    List.of(layer("kamikaze")),
                    0.0F,
                    0.0F));

    private SCGArmorMaterials() {
    }

    public static void register(IEventBus modEventBus) {
        MATERIALS.register(modEventBus);
    }
}
