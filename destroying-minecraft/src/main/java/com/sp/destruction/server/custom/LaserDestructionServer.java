package com.sp.destruction.server.custom;

import com.sp.destruction.DestructionType;
import com.sp.destruction.server.ServerDestructionEvent;
import com.sp.util.keyframes.KeyframeAnimation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public class LaserDestructionServer extends ServerDestructionEvent {
    public LaserDestructionServer(ServerLevel level, BlockPos origin) { super(DestructionType.ORBITAL_LASER, 1200, level, origin); }
    @Override public void start() { setActive(true, level.getGameTime()); }
    @Override protected KeyframeAnimation initAnimations(Level level) { return null; }
}
