package com.shadowclangaming.radar;

import net.minecraft.ChatFormatting;

/**
 * What a contact is.
 *
 * The radar only cares about five answers, because a radar that told you "there is an entity" would be
 * useless and one that told you its UUID would be a wall of noise. Missiles come first because that is
 * the whole point of an air-defence radar.
 */
public enum ContactKind {
    MISSILE("missile", ChatFormatting.RED, 80),
    PLAYER("player", ChatFormatting.YELLOW, 50),
    VEHICLE("vehicle", ChatFormatting.GOLD, 40),
    MONSTER("monster", ChatFormatting.LIGHT_PURPLE, 30),
    OTHER("other", ChatFormatting.GRAY, 10);

    private final String id;
    private final ChatFormatting format;
    private final int baseThreat;

    ContactKind(String id, ChatFormatting format, int baseThreat) {
        this.id = id;
        this.format = format;
        this.baseThreat = baseThreat;
    }

    public String id() {
        return id;
    }

    public ChatFormatting format() {
        return format;
    }

    public int baseThreat() {
        return baseThreat;
    }
}
