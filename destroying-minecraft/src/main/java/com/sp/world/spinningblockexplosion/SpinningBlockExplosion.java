package com.sp.world.spinningblockexplosion;

import net.minecraft.world.level.Level;
import java.util.List;

public class SpinningBlockExplosion {
    private Level level;
    public Level getWorld() { return level; }
    public void explode() {}
    public static List<SpinningBlockExplosion> getExplosions() { return List.of(); }
}
