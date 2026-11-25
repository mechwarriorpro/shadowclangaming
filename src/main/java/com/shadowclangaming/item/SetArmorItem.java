package com.shadowclangaming.item;

import com.shadowclangaming.armor.ArmorSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * One class for all twelve armour pieces.
 *
 * The piece knows which set it belongs to, which is all the tooltip needs to show the set bonus without
 * the player having to look it up. The bonus itself is applied by {@link com.shadowclangaming.armor.ArmorSets},
 * once a second, only when all four pieces are worn.
 */
public class SetArmorItem extends ArmorItem {

    private final ArmorSet set;

    public SetArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties, ArmorSet set) {
        super(material, type, properties);
        this.set = set;
    }

    public ArmorSet set() {
        return set;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(set.translationKey()).withStyle(set.format()));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.armor.set_bonus",
                Component.translatable("tooltip.shadowclangaming.armor.set_bonus." + set.id()))
                .withStyle(ChatFormatting.GRAY));
        if (set.sunlightPenalty() > 0.0F) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.armor.sunlight")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }
}
