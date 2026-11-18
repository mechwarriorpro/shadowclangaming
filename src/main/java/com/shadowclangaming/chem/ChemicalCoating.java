package com.shadowclangaming.chem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/**
 * Stores a chemical element or compound coating applied to a weapon, along with remaining hit charges.
 */
public record ChemicalCoating(Element element, Compound compound, int charges) {

    public static ChemicalCoating ofElement(Element element, int charges) {
        return new ChemicalCoating(element, null, charges);
    }

    public static ChemicalCoating ofCompound(Compound compound, int charges) {
        return new ChemicalCoating(null, compound, charges);
    }

    public boolean isCompound() {
        return compound != null;
    }

    public boolean isElement() {
        return element != null;
    }

    public ChemicalCoating withCharges(int newCharges) {
        return new ChemicalCoating(element, compound, newCharges);
    }

    public static final Codec<ChemicalCoating> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf("element").forGetter(c -> Optional.ofNullable(c.element).map(Element::getSerializedName)),
                    Codec.STRING.optionalFieldOf("compound").forGetter(c -> Optional.ofNullable(c.compound).map(Compound::getSerializedName)),
                    Codec.INT.fieldOf("charges").forGetter(ChemicalCoating::charges)
            ).apply(instance, (elemOpt, compOpt, charges) -> new ChemicalCoating(
                    elemOpt.map(Element::byName).orElse(null),
                    compOpt.map(Compound::byName).orElse(null),
                    charges
            )));

    public static final StreamCodec<ByteBuf, ChemicalCoating> STREAM_CODEC = StreamCodec.of(
            (buf, c) -> {
                buf.writeBoolean(c.element != null);
                if (c.element != null) ByteBufCodecs.STRING_UTF8.encode(buf, c.element.getSerializedName());
                buf.writeBoolean(c.compound != null);
                if (c.compound != null) ByteBufCodecs.STRING_UTF8.encode(buf, c.compound.getSerializedName());
                ByteBufCodecs.VAR_INT.encode(buf, c.charges);
            },
            buf -> {
                boolean hasElem = buf.readBoolean();
                Element elem = hasElem ? Element.byName(ByteBufCodecs.STRING_UTF8.decode(buf)) : null;
                boolean hasComp = buf.readBoolean();
                Compound comp = hasComp ? Compound.byName(ByteBufCodecs.STRING_UTF8.decode(buf)) : null;
                int charges = ByteBufCodecs.VAR_INT.decode(buf);
                return new ChemicalCoating(elem, comp, charges);
            }
    );
}
