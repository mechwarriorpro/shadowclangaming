package com.shadowclangaming.cannon;

import com.shadowclangaming.computer.NetworkGraph;
import com.shadowclangaming.computer.TerminalBlockEntity;
import com.shadowclangaming.computer.TerminalModule;
import com.shadowclangaming.radar.RadarBlockEntity;
import com.shadowclangaming.radar.RadarCommands;
import com.shadowclangaming.radar.RadarContact;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * /term cannon - point a Create Big Cannons mount at a track and fire it.
 *
 *   cannon list                     directors on this wire
 *   cannon status [n]               mount, lay, armed state, bridge answer
 *   cannon link [n]                 link a director to the nearest mount
 *   cannon arm|disarm [n]
 *   cannon velocity <blocks/tick>   what the propellant does; default 6.0
 *   cannon aim <x> <y> <z>
 *   cannon fire [<x> <y> <z>]       with no coordinates: fire at the locked radar track
 *   cannon engage                   sweep, pick the worst contact, lead it, fire
 *   cannon auto on|off              let the terminal engage by itself every two seconds
 *
 * Safety, in order: the director must be armed, the mount must be linked and assembled, and an
 * engage will only shoot at something the radar actually classified as a missile unless it was told the
 * exact coordinates.
 */
public final class CannonCommands {

    /** Ticks between unattended engage attempts. */
    public static final int AUTO_ENGAGE_TICKS = 40;

    private CannonCommands() {
    }

    public static List<CannonDirectorBlockEntity> directors(ServerLevel level, BlockPos pos,
                                                           TerminalBlockEntity terminal) {
        List<CannonDirectorBlockEntity> found = new ArrayList<>();
        for (BlockPos member : NetworkGraph.walk(level, pos, terminal.frequency()).devices()) {
            if (level.getBlockEntity(member) instanceof CannonDirectorBlockEntity director
                    && !found.contains(director)) {
                found.add(director);
            }
        }
        if (found.isEmpty()) {
            for (BlockPos cursor : BlockPos.betweenClosed(pos.offset(-8, -4, -8), pos.offset(8, 4, 8))) {
                if (level.getBlockEntity(cursor) instanceof CannonDirectorBlockEntity director
                        && !found.contains(director)) {
                    found.add(director);
                }
            }
        }
        return found;
    }

    public static void handle(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal, String[] parts,
                              List<String> out) {
        if (!terminal.has(TerminalModule.CANNON)) {
            out.add("this command needs the cannon card");
            return;
        }
        String action = parts.length < 2 ? "list" : parts[1].toLowerCase(java.util.Locale.ROOT);
        List<CannonDirectorBlockEntity> directors = directors(level, pos, terminal);

        if (action.equals("list")) {
            if (directors.isEmpty()) {
                out.add("no cannon directors on this wire");
            }
            int index = 1;
            for (CannonDirectorBlockEntity director : directors) {
                out.add("cannon " + index + " at " + director.getBlockPos().toShortString() + ": "
                        + (director.armed() ? "ARMED" : "disarmed")
                        + (director.auto() ? ", auto" : "")
                        + ", mount " + (director.mountPos() == null ? "none"
                                : director.mountPos().toShortString())
                        + " - " + director.lastResult());
                index++;
            }
            out.add(CannonMountBridge.describe(level, pos));
            return;
        }

        if (action.equals("auto")) {
            if (parts.length < 3) {
                out.add("usage: cannon auto on|off");
                return;
            }
            boolean on = parts[2].equalsIgnoreCase("on");
            terminal.setAutoEngage(on);
            for (CannonDirectorBlockEntity director : directors) {
                director.setAuto(on);
            }
            out.add("auto-engage " + (on ? "on (every " + (AUTO_ENGAGE_TICKS / 20) + "s while armed)"
                    : "off"));
            return;
        }

        if (directors.isEmpty()) {
            out.add("no cannon directors on this wire (place one against a CBC cannon mount)");
            return;
        }

        // A trailing number picks one director - but only when the line is not carrying coordinates,
        // otherwise "fire 100 64 -20" would read -20 as a director number.
        int only = 0;
        if (coordinates(parts) == null && parts.length >= 3 && isNumber(parts[parts.length - 1])) {
            only = Integer.parseInt(parts[parts.length - 1]);
        }

        int index = 1;
        for (CannonDirectorBlockEntity director : directors) {
            int mine = index++;
            if (only != 0 && only != mine) {
                continue;
            }
            switch (action) {
                case "status" -> out.addAll(director.status());
                case "link" -> out.add(director.link(level));
                case "unlink" -> {
                    director.unlink();
                    out.add("cannon " + mine + ": unlinked");
                }
                case "arm" -> {
                    director.setArmed(true);
                    out.add("cannon " + mine + ": armed");
                }
                case "disarm" -> {
                    director.setArmed(false);
                    out.add("cannon " + mine + ": disarmed");
                }
                case "velocity" -> {
                    if (parts.length < 3) {
                        out.add("usage: cannon velocity <blocks per tick>");
                        break;
                    }
                    try {
                        director.setVelocity(Double.parseDouble(parts[2]));
                        out.add(String.format(java.util.Locale.ROOT, "cannon %d: velocity %.2f b/t",
                                mine, director.velocity()));
                    } catch (NumberFormatException e) {
                        out.add("cannon velocity: not a number");
                    }
                }
                case "aim" -> out.add(aim(level, director, parts));
                case "fire" -> out.add(fire(level, pos, terminal, director, parts));
                case "engage" -> out.add(engage(level, pos, terminal, director));
                default -> out.add("unknown cannon action: " + action);
            }
        }
    }

    // ------------------------------------------------------------------ verbs

    private static String aim(ServerLevel level, CannonDirectorBlockEntity director, String[] parts) {
        double[] point = coordinates(parts);
        if (point == null) {
            return "usage: cannon aim <x> <y> <z>";
        }
        return director.aimAt(level, point[0], point[1], point[2]);
    }

    private static String fire(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal,
                               CannonDirectorBlockEntity director, String[] parts) {
        double[] point = coordinates(parts);
        if (point != null) {
            return director.fireAt(level, point[0], point[1], point[2]);
        }
        // No coordinates: use the radar's locked track, led by the shell's flight time.
        RadarBlockEntity radar = RadarCommands.findRadar(level, pos, terminal);
        if (radar == null) {
            return director.fire(level);
        }
        RadarContact contact = radar.selectedContact();
        if (contact == null) {
            return "no locked track (radar lock <n>) - firing on the current lay: " + director.fire(level);
        }
        double[] led = lead(director, contact);
        return director.fireAt(level, led[0], led[1], led[2]);
    }

    /** Sweep, take the worst contact the radar can see, lead it, shoot it. */
    public static String engage(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal,
                                CannonDirectorBlockEntity director) {
        if (!terminal.has(TerminalModule.RADAR)) {
            return "engage needs the radar card as well as the cannon card";
        }
        RadarBlockEntity radar = RadarCommands.findRadar(level, pos, terminal);
        if (radar == null) {
            return "engage: no radar on this wire";
        }
        radar.sweep(level, RadarCommands.gpuBonus(level, pos, terminal));
        RadarContact contact = radar.selectedContact();
        if (contact == null) {
            contact = radar.highestThreat();
        }
        if (contact == null) {
            return "engage: scope is clear";
        }
        boolean selfDirected = contact.isMissile();
        if (!selfDirected && contact.kind() != com.shadowclangaming.radar.ContactKind.PLAYER) {
            return "engage: worst contact is " + contact.kind().id() + " at "
                    + (int) contact.distance() + " blocks - not engaging unclassified traffic";
        }
        double[] led = lead(director, contact);
        return director.fireAt(level, led[0], led[1], led[2]);
    }

    private static double[] lead(CannonDirectorBlockEntity director, RadarContact contact) {
        double originX = director.getBlockPos().getX() + 0.5D;
        double originY = director.getBlockPos().getY() + 1.5D;
        double originZ = director.getBlockPos().getZ() + 0.5D;
        return CannonMath.lead(contact.x(), contact.y(), contact.z(),
                contact.vx(), contact.vy(), contact.vz(),
                originX, originY, originZ, director.velocity());
    }

    /**
     * Unattended air defence, called from the terminal's own ticker.
     *
     * It is deliberately quiet: only a shot that actually happened is written to the log, because a
     * terminal that printed "scope clear" forty times a minute would be unreadable.
     */
    public static void autoEngage(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal) {
        if (!terminal.has(TerminalModule.CANNON) || !terminal.has(TerminalModule.RADAR)) {
            return;
        }
        for (CannonDirectorBlockEntity director : directors(level, pos, terminal)) {
            if (!director.auto() || !director.armed() || director.mountPos() == null) {
                continue;
            }
            String result = engage(level, pos, terminal, director);
            if (result.startsWith("fired")) {
                terminal.log("[auto] " + result);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Read three doubles from the tail of a command, e.g. "cannon fire 100 64 -20". */
    private static double[] coordinates(String[] parts) {
        if (parts.length < 5) {
            return null;
        }
        try {
            int at = parts.length - 3;
            return new double[]{
                    Double.parseDouble(parts[at]),
                    Double.parseDouble(parts[at + 1]),
                    Double.parseDouble(parts[at + 2])
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isNumber(String text) {
        try {
            Integer.parseInt(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
