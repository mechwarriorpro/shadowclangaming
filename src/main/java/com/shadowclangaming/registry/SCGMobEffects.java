package com.shadowclangaming.registry;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.magic.CreepingEvilEffect;
import com.shadowclangaming.spell.ChronosplitEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Mob effects. Two of them, and neither is supposed to be a nice one. */
public final class SCGMobEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, ShadowClanGaming.MOD_ID);

    public static final DeferredHolder<MobEffect, CreepingEvilEffect> CREEPING_EVIL =
            EFFECTS.register("creeping_evil", CreepingEvilEffect::new);

    /**
     * The Chronosplit freeze. Fifteen seconds of not being allowed to do anything at all - see
     * {@link ChronosplitEffect} for what it locks and {@link com.shadowclangaming.spell.Chronosplit}
     * for the event hooks that go with it.
     */
    public static final DeferredHolder<MobEffect, ChronosplitEffect> CHRONOSPLIT =
            EFFECTS.register("chronosplit", ChronosplitEffect::new);

    private SCGMobEffects() {
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
