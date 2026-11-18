package com.shadowclangaming.computer;

import net.minecraft.ChatFormatting;

/**
 * The plug-in cards a terminal accepts.
 *
 * A bare terminal can only talk about itself. Each card unlocks one family of commands, so a machine
 * that can fire missiles is a machine somebody deliberately built - not something that happens by
 * accident when a player places a block next to a launch pad.
 *
 *   TARGETING  talks to launch pads on the same frequency: list them, fire them
 *   MANA       reads mana pools - current and maximum - for anyone nearby
 *   WEB        fetches text from the internet through an allowlist
 *   STORAGE    reads an AE2 network (or plain containers) through a storage bridge
 *   REDSTONE   reads the input signal and drives six outputs
 *   RADAR      sees the radar on the wire: sweep, track, lock
 *   CANNON     lays and fires a cannon director
 */
public enum TerminalModule {
    TARGETING("targeting", ChatFormatting.RED),
    MANA("mana", ChatFormatting.LIGHT_PURPLE),
    WEB("web", ChatFormatting.AQUA),
    /** Reads an AE2 network (or plain containers) through a storage bridge. */
    STORAGE("storage", ChatFormatting.LIGHT_PURPLE),
    /** Six redstone outputs, one input. */
    REDSTONE("redstone", ChatFormatting.RED),
    /** Radar: sweep, track, lock, and read Create: Radars dishes on the wire. */
    RADAR("radar", ChatFormatting.GREEN),
    /** Cannon director: lay and fire a Create Big Cannons mount. */
    CANNON("cannon", ChatFormatting.DARK_RED);

    private final String id;
    private final ChatFormatting format;

    TerminalModule(String id, ChatFormatting format) {
        this.id = id;
        this.format = format;
    }

    public String id() {
        return id;
    }

    public ChatFormatting format() {
        return format;
    }

    public String translationKey() {
        return "module.shadowclangaming." + id;
    }

    public static TerminalModule byId(String id) {
        for (TerminalModule module : values()) {
            if (module.id.equalsIgnoreCase(id)) {
                return module;
            }
        }
        return TARGETING;
    }
}
