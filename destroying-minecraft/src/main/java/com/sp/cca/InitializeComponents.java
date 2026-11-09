package com.sp.cca;

import com.sp.cca.custom.world.WorldDestructionEventsComponent;
import com.sp.cca.custom.entity.PlayerComponent;

// NeoForge port: CCA is runtime-only (Cardinal Components). For offline CI we provide
// a trivial HashMap-backed storage that has the same API as the original CCA component.
public class InitializeComponents {
    public static void register() {}

    // Stubbed ComponentKeys — replaced by SimpleWorldStorage at runtime
    public static final Object EVENTS = new Object();
    public static final Object PLAYERS = new Object();
}
