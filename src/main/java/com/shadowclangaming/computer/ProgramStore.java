package com.shadowclangaming.computer;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Saved terminal programs.
 *
 * A "program" is a short list of terminal commands with a name - no variables, no loops, no
 * conditionals. That is a deliberate line: this is a macro system, not a scripting language, and it is
 * honest about it. What it buys immediately is a one-word launch sequence:
 *
 *     /term prog save alpha           (saves the commands you have run into "alpha")
 *     /term prog run alpha            (runs them in order)
 *
 * Limits are hard and enforced in one place:
 *   name       1..16 chars, a-z 0-9 _ -
 *   64 programs per world
 *   20 lines per program, 96 chars per line
 */
public final class ProgramStore extends SavedData {

    private static final String FILE_ID = "shadowclangaming_programs";
    public static final int MAX_PROGRAMS = 64;
    public static final int MAX_LINES = 20;
    public static final int MAX_LINE_LENGTH = 96;
    public static final int MAX_NAME_LENGTH = 16;

    private final Map<String, List<String>> programs = new LinkedHashMap<>();

    public static ProgramStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ProgramStore::new, ProgramStore::load),
                FILE_ID);
    }

    /** @return null if the name is fine, otherwise the reason it is not. */
    public static String validateName(String name) {
        if (name == null || name.isEmpty()) {
            return "name is empty";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return "name too long (max " + MAX_NAME_LENGTH + ")";
        }
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) {
                return "names may only use a-z 0-9 _ -";
            }
        }
        return null;
    }

    /**
     * The programs every world starts with.
     *
     * These are EXAMPLES, shipped so a player can see the shape of the barrage command instead of reading
     * about it: {@code prog show barrage} prints them, {@code prog run barrage} runs them. They are
     * ordinary programs - editable, deletable, and indistinguishable from one a player typed - because a
     * tutorial you cannot change is a tutorial you cannot learn from.
     *
     * The interesting work happened when the programs were written, not when they run: each one is a
     * handful of lines because the grid arithmetic lives in {@code BarragePlanner} (and, in Lua form, in
     * {@code barrage.lua} beside these). That is the trade this terminal makes: no loops in macros, so the
     * loops live in the commands.
     */
    public static final Map<String, List<String>> EXAMPLES = Map.of(
            // The whole point of the feature: every armed panel on the channel fires one cell of a grid
            // whose spacing comes from its own blast radius, so the craters just overlap.
            "barrage", List.of(
                    "panel arm",
                    "barrage 0 0",
                    "panel status"),
            // The same thing, but pinned to a 96-block square: useful when the target area is a base and
            // you do not want craters outside it.
            "barrage96", List.of(
                    "panel arm",
                    "barrage 0 0 96",
                    "panel status"),
            // Reconnaissance before the fireworks: what is on this channel and can it shoot?
            "inspect", List.of(
                    "pads",
                    "panel list",
                    "panel status"));

    /**
     * Copy the examples in, once, without ever overwriting a program a player has edited.
     *
     * @return how many programs were added
     */
    public int installExamples() {
        int added = 0;
        for (Map.Entry<String, List<String>> example : EXAMPLES.entrySet()) {
            if (programs.containsKey(example.getKey())) {
                continue;
            }
            List<String> cleaned = new ArrayList<>();
            for (String line : example.getValue()) {
                cleaned.add(line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) : line);
            }
            programs.put(example.getKey(), cleaned);
            added++;
        }
        if (added > 0) {
            setDirty();
        }
        return added;
    }

    /** Save (or overwrite) a program. */
    public String save(String name, List<String> lines) {
        String problem = validateName(name);
        if (problem != null) {
            return problem;
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (!programs.containsKey(key) && programs.size() >= MAX_PROGRAMS) {
            return "program storage is full (" + MAX_PROGRAMS + ")";
        }
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            cleaned.add(line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) : line);
            if (cleaned.size() >= MAX_LINES) {
                break;
            }
        }
        if (cleaned.isEmpty()) {
            return "nothing to save";
        }
        programs.put(key, cleaned);
        setDirty();
        return null;
    }

    public List<String> lines(String name) {
        return programs.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
    }

    public boolean has(String name) {
        return programs.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public boolean delete(String name) {
        boolean removed = programs.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public List<String> names() {
        return List.copyOf(programs.keySet());
    }

    public int size() {
        return programs.size();
    }

    // ------------------------------------------------------------------ persistence

    public static ProgramStore load(CompoundTag tag, HolderLookup.Provider registries) {
        ProgramStore store = new ProgramStore();
        ListTag list = tag.getList("Programs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String name = entry.getString("Name");
            ListTag lineList = entry.getList("Lines", Tag.TAG_STRING);
            List<String> lines = new ArrayList<>();
            for (int l = 0; l < lineList.size() && lines.size() < MAX_LINES; l++) {
                lines.add(lineList.getString(l));
            }
            if (validateName(name) == null) {
                store.programs.put(name.toLowerCase(Locale.ROOT), lines);
            }
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, List<String>> entry : programs.entrySet()) {
            CompoundTag program = new CompoundTag();
            program.putString("Name", entry.getKey());
            ListTag lines = new ListTag();
            for (String line : entry.getValue()) {
                lines.add(StringTag.valueOf(line));
            }
            program.put("Lines", lines);
            list.add(program);
        }
        tag.put("Programs", list);
        return tag;
    }
}
