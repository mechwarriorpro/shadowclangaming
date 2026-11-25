package com.shadowclangaming.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.shadowclangaming.computer.TerminalBlockEntity;
import com.shadowclangaming.computer.TerminalShell;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /term &lt;command&gt; - the terminal's keyboard.
 *
 * The terminal is whichever one you are looking at (up to 8 blocks), falling back to the nearest one
 * within 4 blocks. That means no "which computer am I on" state to store, no session to leak, and a
 * player with two terminals can pick one simply by aiming at it.
 *
 * It also works from a command block: a command block is not a player, so it uses its own position and
 * finds the nearest terminal in that dimension.
 */
public final class TerminalCommand {

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("term")
                .then(Commands.argument("line", StringArgumentType.greedyString())
                        .executes(context -> run(context, StringArgumentType.getString(context, "line")))));
    }

    private static int run(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                           String line) {
        CommandSourceStack source = context.getSource();
        BlockPos origin = BlockPos.containing(source.getPosition());

        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            source.sendFailure(Component.literal("term: needs a server level"));
            return 0;
        }

        BlockPos terminalPos = findTerminal(source, level, origin);
        if (terminalPos == null) {
            source.sendFailure(Component.translatable("message.shadowclangaming.terminal_not_found"));
            return 0;
        }
        if (!(level.getBlockEntity(terminalPos) instanceof TerminalBlockEntity terminal)) {
            source.sendFailure(Component.translatable("message.shadowclangaming.terminal_not_found"));
            return 0;
        }

        // A player types; a command block "speaks" as the nearest player, or as nobody.
        ServerPlayer speaker = source.getEntity() instanceof ServerPlayer player
                ? player
                : level.getNearestPlayer(origin.getX(), origin.getY(), origin.getZ(), 16.0D, false)
                        instanceof ServerPlayer nearby ? nearby : null;
        if (speaker == null) {
            // Still run it - the log is the output channel when nobody is holding the keyboard.
            TerminalShell.runQuiet(level, terminalPos, terminal, line);
            return 1;
        }

        TerminalShell.run(level, terminalPos, terminal, speaker, line);
        return 1;
    }

    /** Look-at first, then nearest. */
    private static BlockPos findTerminal(CommandSourceStack source, net.minecraft.server.level.ServerLevel level,
                                         BlockPos origin) {
        Vec3 from = source.getPosition();
        Vec3 look = Vec3.directionFromRotation(source.getRotation());
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(look.scale(8.0D)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.BLOCK
                && level.getBlockEntity(hit.getBlockPos()) instanceof TerminalBlockEntity) {
            return hit.getBlockPos();
        }

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-4, -4, -4), origin.offset(4, 4, 4))) {
            if (!(level.getBlockEntity(candidate) instanceof TerminalBlockEntity)) {
                continue;
            }
            double distance = candidate.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate.immutable();
            }
        }
        return best;
    }

    private TerminalCommand() {
    }
}
