package com.shadowclangaming.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.shadowclangaming.chem.Element;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Stores an installed element upgrade on the Chemical Polytool along with its power level.
 * Matches original MineChem 1.6.4 upgrade mechanic.
 */
public record PolytoolUpgrade(Element element, float power) {

    public static final Codec<PolytoolUpgrade> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Element.CODEC.fieldOf("element").forGetter(PolytoolUpgrade::element),
                    Codec.FLOAT.fieldOf("power").forGetter(PolytoolUpgrade::power)
            ).apply(instance, PolytoolUpgrade::new));

    public static final StreamCodec<ByteBuf, PolytoolUpgrade> STREAM_CODEC = StreamCodec.composite(
            Element.STREAM_CODEC, PolytoolUpgrade::element,
            ByteBufCodecs.FLOAT, PolytoolUpgrade::power,
            PolytoolUpgrade::new);
}
