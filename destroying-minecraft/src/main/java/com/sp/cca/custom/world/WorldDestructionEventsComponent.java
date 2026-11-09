package com.sp.cca.custom.world;

import com.sp.destruction.DestructionEvent;
import net.minecraft.world.level.Level;

public class WorldDestructionEventsComponent {
    private final Level level;
    private DestructionEvent current;
    public WorldDestructionEventsComponent(Level level) { this.level = level; }
    public DestructionEvent getCurrentDestructionEvent() { return current; }
    public void setAndStartCurrentDestructionEvent(DestructionEvent event, long time) { this.current = event; if (event != null) event.setActive(true, time); }
    public void syncLight() {}
}
