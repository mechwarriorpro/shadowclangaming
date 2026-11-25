package com.shadowclangaming.computer;

import net.minecraft.nbt.CompoundTag;

/**
 * One subcommand waiting to be run by an orbital processing node.
 *
 * Kept deliberately tiny and NBT-round-trippable: a queue of these is the whole "distributed computing"
 * feature, and a queue that loses its contents on a chunk unload would be a bug nobody could debug.
 */
public record Subcommand(String command, boolean done, String result) {

    public static Subcommand pending(String command) {
        return new Subcommand(command, false, "");
    }

    public Subcommand finished(String result) {
        return new Subcommand(command, true, result == null ? "" : result);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Command", command);
        tag.putBoolean("Done", done);
        tag.putString("Result", result);
        return tag;
    }

    public static Subcommand load(CompoundTag tag) {
        return new Subcommand(tag.getString("Command"), tag.getBoolean("Done"), tag.getString("Result"));
    }
}
