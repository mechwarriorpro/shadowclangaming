package com.sp.destruction.server;

import com.sp.cca.InitializeComponents;
import com.sp.destruction.DestructionEvent;
import com.sp.destruction.DestructionType;
import com.sp.destruction.server.custom.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;

public abstract class ServerDestructionEvent extends DestructionEvent {
    private static final List<ServerDestructionEvent> INSTANCES = new ArrayList<>();
    protected final ServerLevel level;
    protected final BlockPos origin;
    protected ServerDestructionEvent(DestructionType type, int duration, ServerLevel level, BlockPos origin) {
        super(type, duration, false);
        this.level = level; this.origin = origin;
        INSTANCES.add(this);
    }
    public static List<DestructionEvent> getAllServerInstances() { return new ArrayList<>(INSTANCES); }
    public static ServerDestructionEvent create(DestructionType type, ServerLevel level, BlockPos origin) {
        return switch(type) {
            case NUKE -> new NukeDestructionServer(level, origin);
            case ORBITAL_LASER -> new LaserDestructionServer(level, origin);
            case BLACK_HOLE -> new BlackHoleDestructionServer(level, origin);
            case PLANET -> new PlanetDestructionServer(level, origin);
            case SUPERNOVA -> new SupernovaDestructionServer(level, origin);
        };
    }
    public abstract void start();
}
