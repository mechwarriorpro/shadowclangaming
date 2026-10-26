package com.shadowclangaming.radar;

import com.shadowclangaming.computer.ComputeBudget;
import com.shadowclangaming.computer.HardwareCommands;
import com.shadowclangaming.computer.NetworkGraph;
import com.shadowclangaming.computer.TerminalBlockEntity;
import com.shadowclangaming.computer.TerminalModule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * /term radar - our radar, and whatever the Create: Radars dishes nearby will admit to.
 *
 *   radar                 sweep, then print the scope
 *   radar scan            same thing, spelled out
 *   radar list            the cached scope, no sweep
 *   radar status          range, dishes, auto-sweep, GPU pipelines
 *   radar track <n>       one contact in full
 *   radar lock <n>        hand that contact to the cannon director ("radar lock 0" releases)
 *   radar auto on|off     sweep once a second without being asked
 *   radar bridge          what Create: Radars says, if it is installed
 */
public final class RadarCommands {

    private RadarCommands() {
    }

    /** Nearest radar on this terminal's wire, else the nearest one within 8 blocks. */
    public static RadarBlockEntity findRadar(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal) {
        for (BlockPos member : NetworkGraph.walk(level, pos, terminal.frequency()).devices()) {
            if (level.getBlockEntity(member) instanceof RadarBlockEntity radar) {
                return radar;
            }
        }
        for (BlockPos cursor : BlockPos.betweenClosed(pos.offset(-8, -4, -8), pos.offset(8, 4, 8))) {
            if (level.getBlockEntity(cursor) instanceof RadarBlockEntity radar) {
                return radar;
            }
        }
        return null;
    }

    /** Contacts the machine's GPUs add to the sweep. */
    public static int gpuBonus(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal) {
        ComputeBudget budget = HardwareCommands.budget(level, pos, terminal);
        return budget.radarContactBonus();
    }

    public static void handle(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal, String[] parts,
                              List<String> out) {
        if (!require(terminal, out)) {
            return;
        }
        RadarBlockEntity radar = findRadar(level, pos, terminal);
        if (radar == null) {
            out.add("no radar on this wire (place a radar dish, then run a cable to it)");
            return;
        }
        String action = parts.length < 2 ? "scan" : parts[1].toLowerCase(java.util.Locale.ROOT);

        switch (action) {
            case "scan" -> {
                radar.sweep(level, gpuBonus(level, pos, terminal));
                out.addAll(radar.report());
                out.add(CreateRadarBridge.describe(level, radar.getBlockPos()));
            }
            case "list", "contacts" -> out.addAll(radar.report());
            case "status", "range" -> {
                out.add("radar at " + radar.getBlockPos().toShortString() + ": radius "
                        + (int) radar.radius() + " (" + radar.arrays() + " dish(es), max "
                        + RadarMath.MAX_RADIUS + "), tracks " + radar.contacts().size() + "/"
                        + RadarMath.maxTracks(radar.arrays(), gpuBonus(level, pos, terminal))
                        + ", auto-sweep " + (radar.autoSweep() ? "on" : "off")
                        + ", sweeps " + radar.sweeps());
                out.add("last: " + radar.lastNote());
                if (radar.selected() != 0) {
                    out.add("locked: track " + radar.selected());
                }
            }
            case "track" -> {
                if (parts.length < 3) {
                    out.add("usage: radar track <n>");
                    return;
                }
                RadarContact contact = radar.byTrack(parse(parts[2]));
                out.add(contact == null ? "no track " + parts[2] + " on the scope"
                        : radar.format(contact));
            }
            case "lock" -> {
                if (parts.length < 3) {
                    out.add("usage: radar lock <n>   (0 releases)");
                    return;
                }
                int track = parse(parts[2]);
                if (!radar.select(track)) {
                    out.add("no track " + track + " on the scope - sweep first");
                    return;
                }
                out.add(track == 0 ? "lock released" : "locked track " + track
                        + " for the cannon director");
            }
            case "auto" -> {
                if (parts.length < 3) {
                    out.add("usage: radar auto on|off");
                    return;
                }
                boolean on = parts[2].equalsIgnoreCase("on");
                radar.setAutoSweep(on);
                out.add("auto-sweep " + (on ? "on (one sweep per second)" : "off"));
            }
            case "bridge" -> {
                if (parts.length >= 3 && (parts[2].equalsIgnoreCase("verbose")
                        || parts[2].equalsIgnoreCase("dishes"))) {
                    out.addAll(CreateRadarBridge.diagnose(level, radar.getBlockPos()));
                } else {
                    out.add(CreateRadarBridge.describe(level, radar.getBlockPos()));
                }
            }
            case "create" -> {
                List<String> lines = new ArrayList<>();
                if (!CreateRadarBridge.installed()) {
                    out.add("create_radar: not installed");
                    return;
                }
                var points = CreateRadarBridge.readTracks(level, radar.getBlockPos());
                out.add(CreateRadarBridge.describe(level, radar.getBlockPos()));
                out.add("  radar block registered: " + CreateRadarBridge.hasRegisteredRadarBlock());
                int shown = 0;
                for (var point : points) {
                    if (shown++ >= 8) {
                        break;
                    }
                    lines.add(String.format(java.util.Locale.ROOT, "  create_radar track %.0f %.0f %.0f",
                            point.x, point.y, point.z));
                }
                out.addAll(lines);
            }
            default -> out.add("unknown radar action: " + action);
        }
    }

    private static boolean require(TerminalBlockEntity terminal, List<String> out) {
        if (terminal.has(TerminalModule.RADAR)) {
            return true;
        }
        out.add("this command needs the radar card");
        return false;
    }

    private static int parse(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
