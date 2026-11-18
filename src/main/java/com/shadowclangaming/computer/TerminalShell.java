package com.shadowclangaming.computer;

import com.shadowclangaming.block.LaunchPadBlockEntity;
import com.shadowclangaming.magic.ManaApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * The command interpreter.
 *
 * Deliberately NOT Lua. A real scripting VM (that is what OpenComputers is) is a project in its own
 * right, and shipping a half-finished one would be worse than shipping a clear, small command set that
 * does exactly the three things this mod needs: aim, read mana, and reach the network.
 *
 * Every command prints to the terminal log and to the player's chat.
 */
public final class TerminalShell {

    /** Length of a redstone pulse, in ticks. Long enough for a repeater, short enough to be a pulse. */
    public static final int PULSE_TICKS = 20;
    /** How deep a subcommand may nest. Orbital nodes run subcommands; they must not recurse forever. */
    public static final int MAX_DEPTH = 4;

    /** Current nesting depth. The server is single-threaded, so a plain static counter is safe here. */
    private static int depth;

    private TerminalShell() {
    }

    /** Run one line. Output goes to the terminal log and back to the player. */
    public static void run(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal,
                           ServerPlayer player, String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        terminal.log("> " + trimmed);
        List<String> output = execute(level, pos, terminal, player, trimmed);
        if (!output.isEmpty()) {
            terminal.log(output);
            for (String out : output) {
                player.displayClientMessage(Component.literal("[term] ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(out).withStyle(ChatFormatting.GRAY)), false);
            }
        }
    }

    /**
     * Run one line for the GUI.
     *
     * Same code path as {@link #run}, except the output is RETURNED as well as logged: the screen needs
     * the lines to draw, and the chat copy would be noise while a player is looking at the CRT.
     */
    public static List<String> runForScreen(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal,
                                            ServerPlayer player, String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        terminal.log("> " + trimmed);
        List<String> output = execute(level, pos, terminal, player, trimmed);
        if (!output.isEmpty()) {
            terminal.log(output);
        }
        return output;
    }

    /** Run a line with nobody at the keyboard: output goes to the log only. */
    public static void runQuiet(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal, String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        terminal.log("> " + trimmed);
        terminal.log(execute(level, pos, terminal, null, trimmed));
    }

    /**
     * Run a line on behalf of an orbital processing node.
     *
     * The node supplies its own position (so "wire" and "redstone read" mean "at the node") but the
     * terminal supplies the cards, the channel and the identity. That is the whole safety story for
     * distributed computing in this mod: work moves, authority does not.
     */
    public static List<String> executeOnNode(ServerLevel level, BlockPos nodePos,
                                             TerminalBlockEntity controller, String line) {
        if (depth >= MAX_DEPTH) {
            return List.of("node: recursion limit reached (" + MAX_DEPTH + ")");
        }
        depth++;
        try {
            return execute(level, nodePos, controller, null, line);
        } finally {
            depth--;
        }
    }

    static List<String> execute(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal,
                                ServerPlayer player, String line) {
        String[] parts = line.split("\\s+");
        String command = parts[0].toLowerCase(java.util.Locale.ROOT);
        List<String> out = new ArrayList<>();

        switch (command) {
            case "help" -> {
                out.add("help | status | freq <n> | clear");
                out.add("prog save|run|list|show|delete <name>  (macro storage)");
                out.add("panel list|status|arm|disarm|target|fire  (control panels)");
                out.add("barrage <x> <z> [extent]           (every panel fires one cell of a carpet)");
                out.add("script barrage                    (print the carpet maths, in Lua)");
                out.add("pads | fire <x> <y> <z>          (targeting card)");
                out.add("mana                             (mana card)");
                out.add("net <url>                        (web card)");
                out.add("storage                          (storage card)");
                out.add("redstone read | out <side> <0-15> | pulse <side> | clear   (redstone card)");
                out.add("wire                             (cables this terminal can see)");
                out.add("cpu                              (cores, jobs, threads, radar pipelines)");
                out.add("rack status                      (hardware in reach)");
                out.add("node list | exec <n> <cmd> | queue <n> <cmd> | flush <n>");
                out.add("radar scan | status | track <n> | lock <n> | auto on|off | bridge   (radar card)");
                out.add("cannon list | arm | aim <x> <y> <z> | fire [x y z] | engage | auto on|off  (cannon card)");
            }
            case "status" -> {
                out.add("frequency: " + terminal.frequency());
                out.add("cards: " + (terminal.modules().isEmpty() ? "none" : terminal.modules()));
                out.add("cpu: " + terminal.cpu().id() + "  gpu: " + terminal.gpu().id()
                        + "  auto-engage: " + (terminal.autoEngage() ? "on" : "off"));
                out.add("log lines: " + terminal.lines().size());
            }
            case "clear" -> terminal.clearLog();
            case "freq" -> {
                if (parts.length < 2) {
                    out.add("usage: freq <number>");
                    break;
                }
                int wanted = com.shadowclangaming.computer.Channels.parse(parts[1]);
                if (wanted == com.shadowclangaming.computer.Channels.NONE && !parts[1].equals("none")) {
                    out.add("freq: expects a whole number (any sign)");
                    break;
                }
                terminal.setFrequency(wanted);
                out.add("tuned to " + com.shadowclangaming.computer.Channels.describe(terminal.frequency()));
            }
            case "pads" -> {
                if (!require(terminal, TerminalModule.TARGETING, out)) {
                    break;
                }
                var pads = FrequencyRegistry.get(level.getServer())
                        .pads(terminal.frequency(), level.dimension());
                if (pads.isEmpty()) {
                    out.add("no pads on " + terminal.frequency());
                }
                for (GlobalPos pad : pads) {
                    BlockPos at = pad.pos();
                    out.add("pad " + at.getX() + " " + at.getY() + " " + at.getZ()
                            + (level.isLoaded(at) ? "" : " (unloaded)"));
                }
            }
            case "fire" -> {
                if (!require(terminal, TerminalModule.TARGETING, out)) {
                    break;
                }
                if (parts.length < 4) {
                    out.add("usage: fire <x> <y> <z>");
                    break;
                }
                int x;
                int y;
                int z;
                try {
                    x = Integer.parseInt(parts[1]);
                    y = Integer.parseInt(parts[2]);
                    z = Integer.parseInt(parts[3]);
                } catch (NumberFormatException e) {
                    out.add("fire: coordinates must be whole numbers");
                    break;
                }
                BlockPos target = new BlockPos(x, y, z);
                int fired = 0;
                for (GlobalPos padPos : FrequencyRegistry.get(level.getServer())
                        .pads(terminal.frequency(), level.dimension())) {
                    if (!level.isLoaded(padPos.pos())) {
                        continue;
                    }
                    if (level.getBlockEntity(padPos.pos()) instanceof LaunchPadBlockEntity pad
                            && pad.isReady() && pad.fireAt(target)) {
                        fired++;
                    }
                }
                out.add("fired " + fired + " pad(s) at " + x + " " + y + " " + z);
            }
            case "mana" -> {
                if (!require(terminal, TerminalModule.MANA, out)) {
                    break;
                }
                List<ServerPlayer> nearby = level.getEntitiesOfClass(ServerPlayer.class,
                        new AABB(pos).inflate(16.0D), entity -> true);
                if (nearby.isEmpty()) {
                    out.add("no players within 16 blocks");
                }
                for (ServerPlayer subject : nearby) {
                    long current = ManaApi.current(subject);
                    long max = ManaApi.capacity(subject);
                    int percent = max <= 0 ? 0 : (int) (current * 100L / max);
                    out.add(subject.getGameProfile().getName() + ": " + current + "/" + max
                            + " (" + percent + "%) evil=" + ManaApi.isEvil(level.getServer(), subject.getUUID()));
                }
            }
            case "cpu", "gpu", "hw" -> HardwareCommands.budget(level, pos, terminal, out);
            case "rack" -> HardwareCommands.rack(level, pos, terminal, parts, out);
            case "node" -> HardwareCommands.node(level, pos, terminal, parts, out);
            case "radar" -> com.shadowclangaming.radar.RadarCommands.handle(level, pos, terminal, parts, out);
            case "cannon" -> com.shadowclangaming.cannon.CannonCommands.handle(level, pos, terminal, parts, out);
            case "storage" -> handleStorage(level, pos, terminal, out);
            case "redstone" -> handleRedstone(level, pos, terminal, parts, out);
            case "wire" -> handleWire(level, pos, terminal, out);
            case "prog" -> handleProgram(level, terminal, player, parts, out);
            case "panel" -> handlePanel(level, terminal, player, parts, out);
            case "barrage" -> handleBarrage(level, terminal, parts, out);
            case "script" -> handleScript(level, parts, out);
            case "net" -> {
                if (!require(terminal, TerminalModule.WEB, out)) {
                    break;
                }
                if (parts.length < 2) {
                    out.add("usage: net <url>");
                    break;
                }
                String url = line.substring(line.indexOf(' ') + 1).trim();
                String refusal = WebAccess.refusal(url);
                if (refusal != null) {
                    out.add("net: " + refusal);
                    break;
                }
                out.add("net: fetching " + url);
                WebAccess.fetch(level.getServer(), url, lines -> {
                    terminal.log(lines);
                    if (player != null) {
                        player.displayClientMessage(Component.literal("[net] ").withStyle(ChatFormatting.AQUA)
                                .append(Component.literal(lines.isEmpty() ? "" : lines.get(0))
                                        .withStyle(ChatFormatting.GRAY)), false);
                    }
                });
            }
            default -> out.add("unknown command: " + command + " (try help)");
        }
        return out;
    }

    // ------------------------------------------------------------------ storage

    /** Reads the storage bridge this terminal is wired to (AE2 via reflection, or plain containers). */
    private static void handleStorage(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal,
                                      List<String> out) {
        if (!require(terminal, TerminalModule.STORAGE, out)) {
            return;
        }
        BlockPos bridge = findBridge(level, pos, terminal.frequency());
        if (bridge == null) {
            out.add("no storage bridge on this wire (place one against your ME drive)");
            return;
        }
        Ae2Access.Report report = Ae2Access.read(level, bridge);
        if (level.getBlockEntity(bridge) instanceof StorageBridgeBlockEntity be) {
            be.record(report);
        }
        out.add(report.note());
        if (!report.ok()) {
            return;
        }
        for (Ae2Access.Entry entry : report.entries()) {
            out.add("  " + entry.count() + " x " + entry.name());
        }
    }

    /** Nearest storage bridge: on the wire first, then within 8 blocks. */
    private static BlockPos findBridge(ServerLevel level, BlockPos pos, int frequency) {
        for (BlockPos member : NetworkGraph.walk(level, pos, frequency).devices()) {
            if (level.getBlockEntity(member) instanceof StorageBridgeBlockEntity) {
                return member;
            }
        }
        for (BlockPos cursor : BlockPos.betweenClosed(pos.offset(-8, -4, -8), pos.offset(8, 4, 8))) {
            if (level.getBlockEntity(cursor) instanceof StorageBridgeBlockEntity) {
                return cursor.immutable();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ redstone

    /**
     *   redstone read              what is being fed into the terminal
     *   redstone out <side> <0-15> drive one output
     *   redstone pulse <side>      one second at 15, then back to 0
     *   redstone clear             every output back to 0
     *   redstone status            the six outputs
     */
    private static void handleRedstone(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal,
                                       String[] parts, List<String> out) {
        if (!require(terminal, TerminalModule.REDSTONE, out)) {
            return;
        }
        String action = parts.length < 2 ? "status" : parts[1].toLowerCase(java.util.Locale.ROOT);

        switch (action) {
            case "read" -> out.add("input: " + RedstonePort.input(level, pos));
            case "status" -> out.add("outputs: " + terminal.outputSummary());
            case "clear" -> {
                terminal.clearOutputs();
                out.add("all outputs cleared");
            }
            case "out" -> {
                if (parts.length < 4) {
                    out.add("usage: redstone out <up|down|north|south|east|west> <0-15>");
                    return;
                }
                var side = RedstonePort.side(parts[2]);
                if (side == null) {
                    out.add("redstone: unknown side " + parts[2]);
                    return;
                }
                try {
                    int value = RedstonePort.clamp(Integer.parseInt(parts[3]));
                    terminal.setOutput(side, value);
                    out.add(RedstonePort.name(side) + " = " + value);
                } catch (NumberFormatException e) {
                    out.add("redstone: strength must be a number 0-15");
                }
            }
            case "pulse" -> {
                if (parts.length < 3) {
                    out.add("usage: redstone pulse <side>");
                    return;
                }
                var side = RedstonePort.side(parts[2]);
                if (side == null) {
                    out.add("redstone: unknown side " + parts[2]);
                    return;
                }
                // A pulse is scheduled, never slept: the terminal's own ticker turns it back off one
                // second later. Blocking the server thread for a second would freeze the world.
                terminal.pulse(level.getGameTime(), side, PULSE_TICKS);
                out.add("pulse on " + RedstonePort.name(side) + " (" + (PULSE_TICKS / 20) + "s)");
            }
            default -> out.add("unknown redstone action: " + action);
        }
    }

    // ------------------------------------------------------------------ wire

    /** What the cables say this terminal can see. */
    private static void handleWire(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal,
                                   List<String> out) {
        NetworkGraph.Members members = NetworkGraph.walk(level, pos, terminal.frequency());
        out.add("channel " + terminal.frequency() + ": " + members.cables().size() + " cable(s), "
                + members.devices().size() + " device(s)"
                + (members.truncated() ? " (walk truncated)" : ""));
        for (BlockPos device : members.devices()) {
            if (device.equals(pos)) {
                continue;
            }
            var blockEntity = level.getBlockEntity(device);
            out.add("  " + NetworkGraph.describe(blockEntity) + " at "
                    + device.getX() + " " + device.getY() + " " + device.getZ());
        }
    }

    // ------------------------------------------------------------------ programs

    /**
     * Programs are macros: save the commands the terminal has run, then replay them.
     *
     *   prog save <name>   saves the commands since the last "prog save"/"prog run" (or the last 20)
     *   prog run <name>    runs each line in order
     *   prog list          names and sizes
     *   prog show <name>   the lines
     *   prog delete <name>
     */
    private static void handleProgram(ServerLevel level, TerminalBlockEntity terminal, ServerPlayer player,
                                      String[] parts, List<String> out) {
        if (parts.length < 2) {
            out.add("usage: prog save|run|list|show|delete <name>");
            return;
        }
        String action = parts[1].toLowerCase(java.util.Locale.ROOT);
        ProgramStore store = ProgramStore.get(level.getServer());
        // First terminal command of any kind installs the bundled examples. installExamples() never
        // overwrites an existing program, so a player who deleted one does not get it back.
        store.installExamples();

        if (action.equals("list")) {
            if (store.size() == 0) {
                out.add("no saved programs");
                return;
            }
            for (String name : store.names()) {
                out.add(name + " (" + store.lines(name).size() + " lines)");
            }
            return;
        }
        if (parts.length < 3) {
            out.add("usage: prog " + action + " <name>");
            return;
        }
        String name = parts[2];

        switch (action) {
            case "save" -> {
                // Everything in the log that looks like a command, oldest first, capped by ProgramStore.
                List<String> commands = new ArrayList<>();
                for (String logged : terminal.lines()) {
                    if (logged.startsWith("> ")) {
                        String command = logged.substring(2).trim();
                        if (!command.startsWith("prog")) {
                            commands.add(command);
                        }
                    }
                }
                String problem = store.save(name, commands);
                out.add(problem == null
                        ? "saved " + name + " (" + store.lines(name).size() + " lines)"
                        : "prog save: " + problem);
            }
            case "show" -> {
                if (!store.has(name)) {
                    out.add("no program called " + name);
                    return;
                }
                for (String line : store.lines(name)) {
                    out.add("  " + line);
                }
            }
            case "delete" -> out.add(store.delete(name) ? "deleted " + name : "no program called " + name);
            case "run" -> {
                if (!store.has(name)) {
                    out.add("no program called " + name);
                    return;
                }
                List<String> lines = store.lines(name);
                out.add("running " + name + " (" + lines.size() + " lines)");
                terminal.log(out);
                for (String line : lines) {
                    // Replay through the normal path, so a program can do exactly what a player can
                    // do and nothing more. A program that fires is a program that can be disarmed.
                    terminal.log(execute(level, terminal.getBlockPos(), terminal, player, line));
                }
                out.clear();
            }
            default -> out.add("unknown prog action: " + action);
        }
    }

    // ------------------------------------------------------------------ control panels

    /**
     * The terminal's view of the launcher control panels.
     *
     *   panel list                      panels on this terminal's channel
     *   panel status                    tiers, armed state, target, modules
     *   panel arm | disarm              master arm switch
     *   panel target <x> <y> <z>        same-dimension target
     *   panel target <dimension> <x> <y> <z>   interdimensional (needs the module)
     *   panel fire                      fire the linked pad at the panel's target
     */
    private static void handlePanel(ServerLevel level, TerminalBlockEntity terminal, ServerPlayer player,
                                    String[] parts, List<String> out) {
        if (!require(terminal, TerminalModule.TARGETING, out)) {
            return;
        }
        String action = parts.length < 2 ? "list" : parts[1].toLowerCase(java.util.Locale.ROOT);
        List<com.shadowclangaming.block.ControlPanelBlockEntity> panels =
                panelsOnChannel(level, terminal.frequency());

        if (action.equals("list")) {
            if (panels.isEmpty()) {
                out.add("no control panels on channel " + terminal.frequency());
            }
            for (var panel : panels) {
                out.add("panel " + panel.getBlockPos().getX() + " " + panel.getBlockPos().getY()
                        + " " + panel.getBlockPos().getZ() + " tier " + panel.tier()
                        + (panel.armed() ? " armed" : " DISARMED"));
            }
            return;
        }
        if (panels.isEmpty()) {
            out.add("no control panels on channel " + terminal.frequency());
            return;
        }

        for (var panel : panels) {
            switch (action) {
                case "status" -> out.addAll(com.shadowclangaming.block.ControlPanelBlock.status(level, panel));
                case "arm" -> {
                    panel.setArmed(true);
                    out.add("armed panel at " + panel.getBlockPos().toShortString());
                }
                case "disarm" -> {
                    panel.setArmed(false);
                    out.add("disarmed panel at " + panel.getBlockPos().toShortString());
                }
                case "target" -> setPanelTarget(level, panel, parts, out);
                case "fire" -> out.add(firePanel(level, panel));
                default -> out.add("unknown panel action: " + action);
            }
        }
    }

    private static void setPanelTarget(ServerLevel level, com.shadowclangaming.block.ControlPanelBlockEntity panel,
                                       String[] parts, List<String> out) {
        try {
            if (parts.length == 5) {
                panel.setTarget(level.dimension(), new BlockPos(
                        Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4])));
                out.add("target set (same dimension)");
                return;
            }
            if (parts.length == 6) {
                var key = net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(parts[2]));
                if (!panel.has(com.shadowclangaming.launcher.LauncherModule.INTERDIMENSIONAL)) {
                    out.add("interdimensional targeting needs the module on this panel");
                    return;
                }
                panel.setTarget(key, new BlockPos(Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]), Integer.parseInt(parts[5])));
                out.add("target set in " + key.location());
                return;
            }
            out.add("usage: panel target [dimension] <x> <y> <z>");
        } catch (NumberFormatException e) {
            out.add("panel target: coordinates must be whole numbers");
        } catch (IllegalArgumentException e) {
            out.add("panel target: bad dimension id");
        }
    }

    /** Fire the panel's linked pad, respecting the arm switch and the interdimensional rules. */
    private static String firePanel(ServerLevel level, com.shadowclangaming.block.ControlPanelBlockEntity panel) {
        if (!panel.armed()) {
            return "panel is disarmed";
        }
        BlockPos padPos = panel.linkedPad();
        if (padPos == null) {
            return "panel has no pad linked";
        }
        if (!(level.getBlockEntity(padPos) instanceof com.shadowclangaming.block.LaunchPadBlockEntity pad)) {
            return "linked pad is gone";
        }
        if (panel.target() == null) {
            return "panel has no target";
        }
        if (!pad.isReady()) {
            return "pad is not ready (no missile or no target)";
        }
        if (panel.targetIsInterdimensional()) {
            var report = com.shadowclangaming.launcher.LauncherLevel.scan(level, padPos, pad.tier());
            if (!pad.hasInterdimensionalClearance(panel, report)) {
                return "interdimensional launch refused: launcher is not level 5 all through";
            }
            pad.setTargetDimension(panel.targetDimension());
        }
        return pad.fireAt(panel.target()) ? "fired at " + panel.target().toShortString() : "launch refused";
    }

    /**
     * Print one of the bundled example programs.
     *
     * The examples live in the jar as real files ({@code assets/shadowclangaming/terminal/*.lua}) rather
     * than as strings in a class, so they can be edited, diffed and read like code. Printing them to the
     * terminal log means a player can copy the maths out of the game without opening the jar.
     */
    private static void handleScript(ServerLevel level, String[] parts, List<String> out) {
        String name = parts.length >= 2 ? parts[1].toLowerCase(java.util.Locale.ROOT) : "barrage";
        if (!name.equals("barrage")) {
            out.add("scripts: barrage");
            return;
        }
        var resource = level.getServer().getResourceManager().getResource(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        com.shadowclangaming.ShadowClanGaming.MOD_ID, "terminal/barrage.lua"));
        if (resource.isEmpty()) {
            out.add("script barrage: not found in the mod jar");
            return;
        }
        try (var stream = resource.get().open()) {
            List<String> lines = new String(stream.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).lines().toList();
            out.add("--- terminal/barrage.lua (" + lines.size() + " lines) ---");
            out.addAll(lines);
            out.add("--- end --- run it with: script barrage <radius> ---");
        } catch (java.io.IOException e) {
            out.add("script barrage: " + e.getMessage());
        }
    }

    /**
     * Carpet an area: every armed panel on this channel fires one missile into its own cell of a grid.
     *
     * The spacing is derived from the smallest blast radius in the group ({@link
     * com.shadowclangaming.computer.BarragePlanner}), so neighbouring craters overlap slightly and no
     * strip of ground survives between them. With one launcher you get one crater; the covered square
     * grows as you build more silos, which is the point of the exercise.
     *
     *     barrage <x> <z>            cover as much as the launchers can
     *     barrage <x> <z> <extent>   cover at most <extent> blocks either side of the centre
     */
    private static void handleBarrage(ServerLevel level, TerminalBlockEntity terminal, String[] parts,
                                      List<String> out) {
        if (!require(terminal, TerminalModule.TARGETING, out)) {
            return;
        }
        if (parts.length < 3) {
            out.add("usage: barrage <x> <z> [extent]");
            return;
        }
        int x;
        int z;
        int extent = 0;
        try {
            x = Integer.parseInt(parts[1]);
            z = Integer.parseInt(parts[2]);
            if (parts.length >= 4) {
                extent = Math.max(0, Integer.parseInt(parts[3]));
            }
        } catch (NumberFormatException e) {
            out.add("barrage: coordinates and extent must be whole numbers");
            return;
        }

        List<com.shadowclangaming.computer.BarragePlanner.Launcher> launchers = new ArrayList<>();
        List<com.shadowclangaming.block.ControlPanelBlockEntity> panels = panelsOnChannel(level,
                terminal.frequency());
        for (var panel : panels) {
            BlockPos padPos = panel.linkedPad();
            if (padPos == null || !level.isLoaded(padPos)
                    || !(level.getBlockEntity(padPos) instanceof com.shadowclangaming.block.LaunchPadBlockEntity pad)) {
                continue;
            }
            int radius = 0;
            if (!pad.getMissile().isEmpty()) {
                radius = com.shadowclangaming.warhead.MissilePayload.radiusOf(pad.getMissile());
                if (radius <= 0) {
                    // No slider set: fall back to what the warhead itself would do, so an untouched
                    // pad still contributes a sane cell size instead of being skipped.
                    radius = pad.getMissile().getItem()
                            instanceof com.shadowclangaming.item.MissileItem missileItem
                            ? Math.round(missileItem.warhead().basePower()) : 0;
                }
            }
            launchers.add(new com.shadowclangaming.computer.BarragePlanner.Launcher(
                    padPos.toShortString(), padPos.getX(), padPos.getZ(), radius,
                    panel.armed() && pad.isReady()));
        }
        if (launchers.isEmpty()) {
            out.add("no control panels with a pad on channel " + terminal.frequency());
            return;
        }

        var plan = com.shadowclangaming.computer.BarragePlanner.plan(x, z, extent, launchers);
        if (plan.shots() == 0) {
            out.add("barrage: nothing to fire (" + plan.skipped() + " launcher(s) not ready)");
            return;
        }

        int fired = 0;
        for (var assignment : plan.assignments()) {
            var panel = panelNear(panels, assignment.launcher().id());
            BlockPos aim = new BlockPos(assignment.shot().x(),
                    level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            assignment.shot().x(), assignment.shot().z()),
                    assignment.shot().z());
            if (panel == null) {
                continue;
            }
            panel.setTarget(level.dimension(), aim);
            String result = firePanel(level, panel);
            if (result.startsWith("fired")) {
                fired++;
            } else {
                out.add("cell " + aim.toShortString() + ": " + result);
            }
        }

        out.add("barrage: " + fired + "/" + plan.shots() + " launched, spacing " + plan.spacing()
                + " block(s), covering " + plan.coveredExtent() + " blocks from centre");
        if (plan.shortfall()) {
            int needed = 0;
            for (var launcher : launchers) {
                needed = Math.max(needed,
                        com.shadowclangaming.computer.BarragePlanner.launchersFor(extent,
                                Math.max(1, launcher.blastRadius())));
            }
            out.add("  extent " + extent + " would need " + needed
                    + " launchers to cover all of it; build more silos to widen the carpet");
        }
        if (plan.surplus() > 0) {
            out.add("  " + plan.surplus() + " launcher(s) had no cell - the area is already covered");
        }
        if (plan.skipped() > 0) {
            out.add("  " + plan.skipped() + " launcher(s) skipped (disarmed, empty or broken silo)");
        }
    }

    /** Match a planner launcher id back to the panel that owns it. */
    private static com.shadowclangaming.block.ControlPanelBlockEntity panelNear(
            List<com.shadowclangaming.block.ControlPanelBlockEntity> panels, String padId) {
        for (var panel : panels) {
            if (panel.linkedPad() != null && panel.linkedPad().toShortString().equals(padId)) {
                return panel;
            }
        }
        return null;
    }

    /** Every control panel tuned to a channel. */
    private static List<com.shadowclangaming.block.ControlPanelBlockEntity> panelsOnChannel(ServerLevel level,
                                                                                        int frequency) {
        List<com.shadowclangaming.block.ControlPanelBlockEntity> found = new ArrayList<>();
        for (var globalPos : FrequencyRegistry.get(level.getServer()).pads(frequency, level.dimension())) {
            BlockPos pos = globalPos.pos();
            if (!level.isLoaded(pos)) {
                continue;
            }
            // A panel next to a registered pad is "on the channel" - that is how the panel finds its
            // pad too, so the two always agree.
            for (BlockPos cursor : BlockPos.betweenClosed(pos.offset(-6, -6, -6), pos.offset(6, 6, 6))) {
                if (level.getBlockEntity(cursor) instanceof com.shadowclangaming.block.ControlPanelBlockEntity panel
                        && !found.contains(panel)) {
                    if (panel.linkedPad() == null) {
                        panel.linkPad(pos.immutable());
                    }
                    found.add(panel);
                }
            }
        }
        return found;
    }

    private static boolean require(TerminalBlockEntity terminal, TerminalModule module, List<String> out) {
        if (terminal.has(module)) {
            return true;
        }
        out.add("this command needs the " + module.id() + " card");
        return false;
    }
}
