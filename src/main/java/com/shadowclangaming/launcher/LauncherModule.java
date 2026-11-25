package com.shadowclangaming.launcher;

import net.minecraft.ChatFormatting;

/**
 * Modules are the things you bolt onto a finished launcher. Each one names the level it needs, so the
 * rule is data rather than an if-chain buried in an interaction handler.
 *
 * INTERDIMENSIONAL is the one that exists today, and it is deliberately the hardest thing in the mod
 * to build: level 5 pad, level 5 control panel, two level 5 support rods. That is a lot of obsidian and
 * a lot of Upgrade Kits, and it buys you the ability to put a warhead through the floor of the world.
 */
public enum LauncherModule {
    INTERDIMENSIONAL("interdimensional",
            "module.shadowclangaming.interdimensional.desc", 5, ChatFormatting.DARK_PURPLE);

    private final String id;
    private final String descriptionKey;
    private final int requiredLevel;
    private final ChatFormatting format;

    LauncherModule(String id, String descriptionKey, int requiredLevel, ChatFormatting format) {
        this.id = id;
        this.descriptionKey = descriptionKey;
        this.requiredLevel = requiredLevel;
        this.format = format;
    }

    public String id() {
        return id;
    }

    public String descriptionKey() {
        return descriptionKey;
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    public ChatFormatting format() {
        return format;
    }

    public String translationKey() {
        return "module.shadowclangaming.launcher." + id;
    }

    /**
     * Can this module go on this launcher?
     *
     * @param report the structure scan for the pad
     * @return null if it can, or a translation key explaining why not
     */
    public String refusal(LauncherLevel.Report report) {
        if (LauncherLevel.meets(report, requiredLevel)) {
            return null;
        }
        if (report.padTier() < requiredLevel) {
            return "message.shadowclangaming.module_pad_tier";
        }
        if (report.panelTier() < requiredLevel) {
            return "message.shadowclangaming.module_panel_tier";
        }
        return "message.shadowclangaming.module_rods";
    }

    public static LauncherModule byId(String id) {
        for (LauncherModule module : values()) {
            if (module.id.equalsIgnoreCase(id)) {
                return module;
            }
        }
        return INTERDIMENSIONAL;
    }
}
