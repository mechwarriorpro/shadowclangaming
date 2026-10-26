package com.shadowclangaming.gun;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;

/**
 * What a bullet is made of.
 *
 * This is the Borderlands idea reimplemented from scratch: every gun rolls an element, the element
 * changes what happens on impact, and the rarity decides how likely an element is at all.
 *
 * No element here is copied from anywhere - these are five effects built on vanilla mechanics:
 *   KINETIC  plain damage, the only one every gun has
 *   EMBER    sets the target alight
 *   ARC      shocks: extra damage to anything already on fire, and stops machines
 *   ACID     corrodes: poison plus a chunk of armour ignored
 *   VOID     withers: slow, lasting damage, and it heals the shooter a little
 */
public enum GunElement implements StringRepresentable {
    KINETIC("kinetic", 0xE8E8E8, ChatFormatting.GRAY),
    EMBER("ember", 0xFF7A18, ChatFormatting.GOLD),
    ARC("arc", 0x66DDFF, ChatFormatting.AQUA),
    ACID("acid", 0x7BFF4D, ChatFormatting.GREEN),
    VOID("void", 0xB266FF, ChatFormatting.DARK_PURPLE);

    public static final Codec<GunElement> CODEC = StringRepresentable.fromEnum(GunElement::values);

    private final String id;
    private final int colour;
    private final ChatFormatting format;

    GunElement(String id, int colour, ChatFormatting format) {
        this.id = id;
        this.colour = colour;
        this.format = format;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String id() {
        return id;
    }

    /** 0xRRGGBB, used for the item name colour and the bullet tracer. */
    public int colour() {
        return colour;
    }

    public ChatFormatting format() {
        return format;
    }

    public String translationKey() {
        return "element.shadowclangaming." + id;
    }

    public static GunElement byId(String id) {
        for (GunElement element : values()) {
            if (element.id.equalsIgnoreCase(id)) {
                return element;
            }
        }
        return KINETIC;
    }
}
