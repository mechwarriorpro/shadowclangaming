package com.sp.destruction.client;

import com.sp.destruction.DestructionEvent;
import com.sp.destruction.DestructionType;
import net.minecraft.client.multiplayer.ClientLevel;

public abstract class ClientDestructionEvent extends DestructionEvent {
    protected final ClientLevel level;
    protected ClientDestructionEvent(DestructionType type, int duration, ClientLevel level) {
        super(type, duration, true);
        this.level = level;
    }
}
