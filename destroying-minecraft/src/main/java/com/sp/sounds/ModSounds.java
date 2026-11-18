package com.sp.sounds;

import com.sp.DestroyingMinecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(Registries.SOUND_EVENT, DestroyingMinecraft.MOD_ID);

    // Core sounds referenced by ported destruction timelines — registered as DeferredHolders so .get() works even offline
    public static final DeferredHolder<SoundEvent, SoundEvent> LAVA_SPEW = REGISTRY.register("lava_spew", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("lava_spew")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_LANDING = REGISTRY.register("laser_landing", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("laser_landing")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_LOOP = REGISTRY.register("laser_loop", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("laser_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_CRACKING_INITIAL = REGISTRY.register("laser_cracking_initial", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("laser_cracking_initial")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_CRACKING_LOOP = REGISTRY.register("laser_cracking_loop", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("laser_cracking_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_END = REGISTRY.register("laser_end", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("laser_end")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_CHARGE = REGISTRY.register("laser_charge", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("laser_charge")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_PAUSE = REGISTRY.register("laser_pause", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("laser_pause")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PLANET_AMBIENCE = REGISTRY.register("planet_ambience", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("planet_ambience")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PLANET_RUMBLE = REGISTRY.register("planet_rumble", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("planet_rumble")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PLANET_IMPACT_INITIAL = REGISTRY.register("planet_impact_initial", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("planet_impact_initial")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PLANET_IMPACT = REGISTRY.register("planet_impact", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("planet_impact")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_HOLE_BRAAM1 = REGISTRY.register("black_hole_braam1", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("black_hole_braam1")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_HOLE_BRAAM2 = REGISTRY.register("black_hole_braam2", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("black_hole_braam2")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_HOLE_BRAAM3 = REGISTRY.register("black_hole_braam3", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("black_hole_braam3")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_HOLE_BRAAM_FINAL = REGISTRY.register("black_hole_braam_final", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("black_hole_braam_final")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_HOLE_DESTRUCTION_AMBIENCE = REGISTRY.register("black_hole_ambience", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("black_hole_ambience")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SNAP_SNAP_RUMBLE = REGISTRY.register("snap_rumble", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("snap_rumble")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SNAP_SNAP_BREAK_OFF = REGISTRY.register("snap_break_off", () -> SoundEvent.createVariableRangeEvent(DestroyingMinecraft.idOf("snap_break_off")));

    public static void registerSounds() {}
}
