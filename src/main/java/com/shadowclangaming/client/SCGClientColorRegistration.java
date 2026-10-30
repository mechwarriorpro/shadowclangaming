package com.shadowclangaming.client;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.chem.Element;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.registry.SCGItems;
import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Registers client-side item color providers.
 *
 * <p>Tints the liquid layer (layer 0) of the element vial according to the contained
 * {@link Element#colour()}. Layer 1 is the untinted glass bottle.
 */
@EventBusSubscriber(modid = ShadowClanGaming.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SCGClientColorRegistration {
    private SCGClientColorRegistration() {}

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex == 0) {
                Element element = stack.get(SCGDataComponents.ELEMENT.get());
                if (element == null) {
                    return 0x00000000; // empty: completely clear liquid
                }
                return FastColor.ARGB32.opaque(element.colour());
            }
            return -1; // untinted container frame on layer 1
        }, SCGItems.ELEMENT_VIAL.get(), SCGItems.CHEMICAL_BUCKET.get());

        event.register((stack, tintIndex) -> {
            if (tintIndex == 0) {
                Compound compound = stack.get(SCGDataComponents.COMPOUND.get());
                if (compound == null) {
                    return 0x00000000;
                }
                return FastColor.ARGB32.opaque(compound.colour());
            }
            return -1;
        }, SCGItems.COMPOUND_VIAL.get());
    }
}
