package com.shadowclangaming.item;

import com.shadowclangaming.chem.Compound;
import com.shadowclangaming.chem.Element;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.registry.SCGItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Map;

/**
 * A test tube / vial containing a chemical compound or molecule.
 *
 * <p>Inspired by the classic MineChem molecule test tubes.
 */
public class CompoundVialItem extends Item {

    public CompoundVialItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Compound compound, int count) {
        ItemStack stack = new ItemStack(SCGItems.COMPOUND_VIAL.get(), count);
        stack.set(SCGDataComponents.COMPOUND.get(), compound);
        return stack;
    }

    public static Compound compoundOf(ItemStack stack) {
        Compound c = stack.get(SCGDataComponents.COMPOUND.get());
        return c == null ? Compound.WATER : c;
    }

    @Override
    public Component getName(ItemStack stack) {
        Compound compound = stack.get(SCGDataComponents.COMPOUND.get());
        if (compound == null) {
            return Component.translatable("item.shadowclangaming.compound_vial.empty");
        }
        return Component.translatable("item.shadowclangaming.compound_vial.named",
                Component.translatable(compound.translationKey()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Compound compound = stack.get(SCGDataComponents.COMPOUND.get());
        if (compound == null) return;

        tooltip.add(Component.literal(compound.formula()).withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));

        StringBuilder sb = new StringBuilder();
        int idx = 0;
        for (Map.Entry<Element, Integer> entry : compound.elements().entrySet()) {
            if (idx > 0) sb.append(", ");
            sb.append(entry.getValue()).append("x ").append(entry.getKey().symbol());
            idx++;
        }
        tooltip.add(Component.literal("Composition: " + sb).withStyle(ChatFormatting.DARK_GRAY));

        if (compound.isAcid()) {
            tooltip.add(Component.literal("Caustic Acid").withStyle(ChatFormatting.YELLOW));
        }
        if (compound.isToxic()) {
            tooltip.add(Component.literal("Toxic Compound").withStyle(ChatFormatting.RED));
        }
        if (compound.isExplosive()) {
            tooltip.add(Component.literal("High Explosive").withStyle(ChatFormatting.GOLD));
        }
        if (compound.isMedicinal()) {
            tooltip.add(Component.literal("Medicinal / Stimulant").withStyle(ChatFormatting.GREEN));
        }
    }
}
