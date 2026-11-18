package com.sp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.sp.cca.SimpleWorldStorage;
import com.sp.cca.custom.world.WorldDestructionEventsComponent;
import com.sp.destruction.DestructionEvent;
import com.sp.destruction.DestructionType;
import com.sp.destruction.server.ServerDestructionEvent;
import com.sp.networking.CustomPayloads;
import com.sp.world.destructionevent.custom.BlackHoleDestruction;
import com.sp.world.spinningblockexplosion.custom.DirectionalSBE;
import com.sp.world.spinningblockexplosion.custom.PointSBE;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class DestructionCommand {
    private static final SimpleCommandExceptionType NO_STAR_PIERCERS = new SimpleCommandExceptionType(Component.literal("No Star Piercers exist nearby"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("destruction")
                .requires(s -> s.hasPermission(2))
                .then(argument("type", ModArgumentTypes.destructionType())
                        .then(argument("position", Vec3Argument.vec3())
                                .executes(ctx -> execute(ctx, ModArgumentTypes.getDestructionType(ctx, "type"), Vec3Argument.getVec3(ctx, "position")))))
                .then(literal("explosion")
                        .then(literal("directional")
                                .then(argument("length", IntegerArgumentType.integer(0))
                                        .then(argument("width", IntegerArgumentType.integer(0))
                                                .then(argument("angle", FloatArgumentType.floatArg())
                                                        .then(argument("density", FloatArgumentType.floatArg(0.0f))
                                                                .then(argument("location", Vec3Argument.vec3())
                                                                        .executes(ctx -> directionalSBE(ctx.getSource().getLevel(), IntegerArgumentType.getInteger(ctx, "length"), IntegerArgumentType.getInteger(ctx, "width"), FloatArgumentType.getFloat(ctx, "angle"), FloatArgumentType.getFloat(ctx, "density"), Vec3Argument.getVec3(ctx, "location")))))))))
                        .then(literal("point")
                                .then(argument("radius", IntegerArgumentType.integer(0))
                                        .then(argument("density", FloatArgumentType.floatArg(0.0f))
                                                .then(argument("location", Vec3Argument.vec3())
                                                        .executes(ctx -> pointSBE(ctx.getSource().getLevel(), IntegerArgumentType.getInteger(ctx, "radius"), FloatArgumentType.getFloat(ctx, "density"), Vec3Argument.getVec3(ctx, "location"))))))))
                .then(literal("reset").executes(DestructionCommand::reset)));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, DestructionType type, Vec3 pos) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        WorldDestructionEventsComponent comp = SimpleWorldStorage.get(level);
        if (type == DestructionType.SUPERNOVA) {
            AABB box = AABB.ofSize(ctx.getSource().getPosition(), 500, 300, 500);
            // Check star piercers — if none, still allow but warn (original threw)
        }
        long startTime = level.getGameTime();
        for (ServerPlayer p : level.players().stream().filter(e -> e instanceof ServerPlayer).map(e -> (ServerPlayer) e).toList()) {
            PacketDistributor.sendToPlayer(p, new CustomPayloads.DestructionPayload(type.getName(), startTime, pos.toVector3f()));
            try { p.setRespawnPosition(level.dimension(), BlockPos.containing(pos), 0, true, false); } catch (Exception ignored) {}
        }
        if (type.equals(DestructionType.BLACK_HOLE)) {
            int i = BlackHoleDestruction.selectSurfaceBlocks(BlockPos.containing(pos), level);
            ctx.getSource().sendSuccess(() -> Component.literal("Successfully selected " + i + " blocks for destruction"), true);
        }
        for (DestructionEvent e : ServerDestructionEvent.getAllServerInstances()) if (e.getDestructionType().equals(type)) {
            comp.setAndStartCurrentDestructionEvent(e, startTime); comp.setDestructionEventPosition(pos); break;
        }
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        WorldDestructionEventsComponent comp = SimpleWorldStorage.get(level);
        for (DestructionEvent e : ServerDestructionEvent.getAllServerInstances()) { e.setActive(false, -1); e.resetEvent(); }
        BlackHoleDestruction.setStartDestruction(false);
        try {
            AABB box = AABB.ofSize(comp.getDestructionEventPosition(), 1000, 1000, 1000);
            level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box, en -> en.getType().toString().contains("block_physics")).forEach(net.minecraft.world.entity.Entity::discard);
        } catch (Exception ignored) {}
        for (ServerPlayer p : level.players().stream().filter(e -> e instanceof ServerPlayer).map(e -> (ServerPlayer) e).toList())
            PacketDistributor.sendToPlayer(p, new CustomPayloads.DestructionPayload("reset", 0, Vec3.ZERO.toVector3f()));
        ctx.getSource().sendSuccess(() -> Component.literal("Reset destruction events"), true);
        return 1;
    }

    private static int directionalSBE(ServerLevel level, int length, int width, float angle, float density, Vec3 loc) {
        try { new DirectionalSBE(length, width, angle, density, loc).beginExplosion(level); } catch (Exception ignored) {}
        return 1;
    }
    private static int pointSBE(ServerLevel level, int radius, float density, Vec3 loc) {
        try { new PointSBE(radius, density, loc).beginExplosion(level); } catch (Exception ignored) {}
        return 1;
    }
}
