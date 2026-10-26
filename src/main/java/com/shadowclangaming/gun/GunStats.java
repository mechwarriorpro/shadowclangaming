package com.shadowclangaming.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything that makes one gun *that* gun: the roll, the maker, the six parts, and the numbers that
 * come out of them.
 *
 * Immutable, and fully serialised: a gun is a data component, so it survives death, chests, trading
 * and a server restart with exactly the numbers it had when it dropped.
 *
 * The stream codec is written by hand rather than composed. Eleven fields is past the point where
 * composite() overloads stop reading clearly, and a hand-written one is both shorter and impossible
 * to get the field order wrong on without noticing.
 */
public record GunStats(
        long seed,
        GunMaker maker,
        GunRarity rarity,
        GunElement element,
        int style,
        float damage,
        float fireRate,
        float accuracy,
        int magazine,
        int reloadTicks,
        List<GunPart> parts) {

    public static final Codec<GunStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("seed").forGetter(GunStats::seed),
            GunMaker.CODEC.fieldOf("maker").forGetter(GunStats::maker),
            GunRarity.CODEC.fieldOf("rarity").forGetter(GunStats::rarity),
            GunElement.CODEC.fieldOf("element").forGetter(GunStats::element),
            Codec.INT.fieldOf("style").forGetter(GunStats::style),
            Codec.FLOAT.fieldOf("damage").forGetter(GunStats::damage),
            Codec.FLOAT.fieldOf("fire_rate").forGetter(GunStats::fireRate),
            Codec.FLOAT.fieldOf("accuracy").forGetter(GunStats::accuracy),
            Codec.INT.fieldOf("magazine").forGetter(GunStats::magazine),
            Codec.INT.fieldOf("reload_ticks").forGetter(GunStats::reloadTicks),
            GunPart.CODEC.listOf().fieldOf("parts").forGetter(GunStats::parts)
    ).apply(instance, GunStats::new));

    public static final StreamCodec<ByteBuf, GunStats> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public GunStats decode(ByteBuf buf) {
            long seed = buf.readLong();
            GunMaker maker = GunMaker.byId(ByteBufCodecs.STRING_UTF8.decode(buf));
            GunRarity rarity = GunRarity.byId(ByteBufCodecs.STRING_UTF8.decode(buf));
            GunElement element = GunElement.byId(ByteBufCodecs.STRING_UTF8.decode(buf));
            int style = buf.readByte();
            float damage = buf.readFloat();
            float fireRate = buf.readFloat();
            float accuracy = buf.readFloat();
            int magazine = buf.readShort();
            int reloadTicks = buf.readShort();
            int partCount = buf.readByte();
            List<GunPart> parts = new ArrayList<>(partCount);
            for (int i = 0; i < partCount; i++) {
                parts.add(GunPart.byId(ByteBufCodecs.STRING_UTF8.decode(buf)));
            }
            return new GunStats(seed, maker, rarity, element, style, damage, fireRate, accuracy,
                    magazine, reloadTicks, parts);
        }

        @Override
        public void encode(ByteBuf buf, GunStats stats) {
            buf.writeLong(stats.seed);
            ByteBufCodecs.STRING_UTF8.encode(buf, stats.maker.id());
            ByteBufCodecs.STRING_UTF8.encode(buf, stats.rarity.id());
            ByteBufCodecs.STRING_UTF8.encode(buf, stats.element.id());
            buf.writeByte(stats.style);
            buf.writeFloat(stats.damage);
            buf.writeFloat(stats.fireRate);
            buf.writeFloat(stats.accuracy);
            buf.writeShort(stats.magazine);
            buf.writeShort(stats.reloadTicks);
            buf.writeByte(stats.parts.size());
            for (GunPart part : stats.parts) {
                ByteBufCodecs.STRING_UTF8.encode(buf, part.id());
            }
        }
    };

    public GunStats {
        parts = List.copyOf(parts);
    }

    /** Ticks between shots, derived from shots-per-second. Never below 1. */
    public int cooldownTicks() {
        return Math.max(1, Math.round(20.0F / Math.max(0.2F, fireRate)));
    }

    /** Index into the item's custom_model_data overrides: style x 5 elements + element. */
    public int modelVariant() {
        return style * GunElement.values().length + element.ordinal() + 1;
    }

    /** One line per part, for the tooltip. */
    public List<GunPart> partsBySlot() {
        List<GunPart> ordered = new ArrayList<>(6);
        for (GunPart.Slot slot : GunPart.Slot.values()) {
            for (GunPart part : parts) {
                if (part.slot() == slot) {
                    ordered.add(part);
                    break;
                }
            }
        }
        return ordered;
    }

    public GunPart partIn(GunPart.Slot slot) {
        for (GunPart part : parts) {
            if (part.slot() == slot) {
                return part;
            }
        }
        return null;
    }
}
