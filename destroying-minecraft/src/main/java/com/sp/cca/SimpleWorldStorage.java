package com.sp.cca;

import com.sp.cca.custom.world.WorldDestructionEventsComponent;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;

public class SimpleWorldStorage {
    private static final Map<Level, WorldDestructionEventsComponent> MAP = new HashMap<>();
    public static WorldDestructionEventsComponent get(Level level) {
        return MAP.computeIfAbsent(level, WorldDestructionEventsComponent::new);
    }
}
