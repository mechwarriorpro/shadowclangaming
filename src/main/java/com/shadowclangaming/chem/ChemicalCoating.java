package com.shadowclangaming.chem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Stores a chemical element coating applied to a weapon, along with remaining hit charges.
 */
public record ChemicalCoating(Element element, int charges) {

    public static final Codec<ChemicalCoating> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Element.CODEC.fieldOf("element").forGetter(ChemicalCoating::element),
                    Codec.INT.fieldOf("charges").forGetter(ChemicalCoating::charges)
            ).apply(instance, ChemicalCoating::new));

    public static final StreamCodec<ByteBuf, ChemicalCoating> STREAM_CODEC = StreamCodec.composite(
            Element.STREAM_CODEC, ChemicalCoating::element,
            ByteBufCodecs.VAR_INT, ChemicalCoating::charges,
            ChemicalCoating::new);

    public ChemicalCoating withCharges(int newCharges) {
        return new ChemicalCoating(element, newCharges);
    }
}
