package com.sp.world.destructionevent.custom;

import net.minecraft.server.level.ServerLevel;

public class BlackHoleDestruction {
    private static boolean startDestruction = false;
    public static void tick(ServerLevel level) {}
    public static boolean isStartDestruction() { return startDestruction; }
    public static void setStartDestruction(boolean v) { startDestruction = v; }
    public static int selectSurfaceBlocks(net.minecraft.core.BlockPos pos, ServerLevel level) { return 0; }
}
