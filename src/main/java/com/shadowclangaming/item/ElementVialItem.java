package com.shadowclangaming.item;

import com.shadowclangaming.chem.Element;
import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * One vial of one element.
 *
 * The element is stored in a DATA COMPONENT, not in NBT. That means:
 *   - two vials of carbon stack together, a carbon and a uranium do not
 *   - the element survives crafting, chests, and item frames automatically
 *   - you never touch CompoundTag by hand
 */
public class ElementVialItem extends Item {
    public ElementVialItem(Properties properties) {
        super(properties);
    }

    /** Builds a stack of {@code count} vials of {@code element}. */
    public static ItemStack create(Element element, int count) {
        ItemStack stack = new ItemStack(com.shadowclangaming.registry.SCGItems.ELEMENT_VIAL.get(), count);
        stack.set(SCGDataComponents.ELEMENT.get(), element);
        return stack;
    }

    /** @return the element inside, or {@link Element#H} if the component is somehow missing. */
    public static Element elementOf(ItemStack stack) {
        Element element = stack.get(SCGDataComponents.ELEMENT.get());
        return element == null ? Element.H : element;
    }

    /** Makes the item name read "Uranium-238 Vial" instead of "Element Vial". */
    @Override
    public Component getName(ItemStack stack) {
        Element element = stack.get(SCGDataComponents.ELEMENT.get());
        if (element == null) {
            return Component.translatable("item.shadowclangaming.element_vial.empty");
        }
        return Component.translatable("item.shadowclangaming.element_vial.named",
                Component.translatable(element.translationKey()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Element element = stack.get(SCGDataComponents.ELEMENT.get());
        if (element == null) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.shadowclangaming.element.symbol",
                element.symbol(), element.atomicNumber()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.element.mass", element.atomicMass())
                .withStyle(ChatFormatting.DARK_GRAY));
        if (element.fissile()) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.element.fissile").withStyle(ChatFormatting.GREEN));
        }
        if (element.fusionFuel()) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.element.fusion").withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Fissile material glints, because it should feel dangerous in the inventory.
        return elementOf(stack).fissile();
    }
}
