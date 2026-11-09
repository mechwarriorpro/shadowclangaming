package com.sp.destruction.client.custom;

import com.sp.destruction.DestructionType;
import com.sp.destruction.client.ClientDestructionEvent;
import com.sp.util.keyframes.KeyframeAnimation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

public class PlanetDestructionClient extends ClientDestructionEvent {
    public PlanetDestructionClient(ClientLevel level) { super(DestructionType.PLANET, 1200, level); }
    @Override protected KeyframeAnimation initAnimations(Level level) { return null; }
}
