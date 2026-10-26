package com.shadowclangaming.warhead;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The server half of "make it look good": announce a detonation so every nearby client can play its shader.
 *
 * Every delivery form ends here - a missile impact, a placeable charge, a thrown grenade, a minecart, a
 * designator strike, the kamikaze vest - because they all detonate through
 * {@link WarheadType#detonate}. One call site, so a warhead can never visually "not exist" just because it
 * arrived by an unusual route.
 *
 * The packet carries a STYLE, not a shader: the client decides whether that becomes a post chain, the
 * fallback chain, or a HUD flash (see {@code client/BlastShaderFx}). That is also what keeps this class free
 * of anything client-only, so a dedicated server can run it.
 */
public final class BlastFx {

    private BlastFx() {
    }

    /** How big the effect is drawn, in blocks, from the power this form actually delivers. */
    public static int radius(float power) {
        return Math.max(8, Math.min(4096, Math.round(Math.abs(power) * 6.0F) + 8));
    }

    /**
     * Announce the anticipation.
     *
     * <p>THE EFFECT IS THE FUSE. A primed charge used to sit there ticking for its own fuse length and then
     * go off, so the only thing to look at during the wait was a wisp of smoke; now the warhead's effect
     * plays *during* the wait, at exactly the same length as the fuse, and for the warheads that end in a
     * blast the blast lands on the frame the effect finishes. One clock, one packet, no silent gap.
     *
     * @param ticks how long the weapon has left before it goes off - the form's fuse, in ticks
     */
    public static void emitCharge(ServerLevel level, WarheadType type, Vec3 pos, float power, int ticks) {
        BlastStyle style = BlastStyle.forWarhead(type.id());
        BlastMotif.Motif motif = BlastMotif.forWarhead(type.id());
        int radius = radius(power);
        com.shadowclangaming.debug.ShadowDebug.log("BLAST_FX",
                "phase=charge warhead={} style={} theme={} pos=({}, {}, {}) radius={} ticks={} layers={} power={}",
                type.id(), style.id(), motif.theme(), pos.x, pos.y, pos.z, radius, ticks,
                motif.chargeSpecs().size(), power);
        com.shadowclangaming.network.ScgNet.sendBlastFx(level, style, type.id(), pos, radius,
                Math.max(1, ticks), com.shadowclangaming.network.ScgNet.BlastFxPayload.PHASE_CHARGE);
    }

    /**
     * Send the effect for one detonation.
     *
     * @param power the warhead's power for this blast, in the same units as {@code WarheadType#basePower}
     */
    public static void emit(ServerLevel level, WarheadType type, Vec3 pos, float power) {
        BlastStyle style = BlastStyle.forWarhead(type.id());
        BlastMotif.Motif motif = BlastMotif.forWarhead(type.id());
        int radius = radius(power);
        // The motif owns the duration: it is per-warhead, it is what the client draws for, and it is the
        // same number ExplosivePackaging turns into this warhead's fuse.
        int ticks = motif.durationTicks(power);
        com.shadowclangaming.debug.ShadowDebug.log("BLAST_FX",
                "phase=impact warhead={} style={} theme={} pos=({}, {}, {}) radius={} ticks={} layers={} power={}",
                type.id(), style.id(), motif.theme(), pos.x, pos.y, pos.z, radius, ticks,
                motif.specs().size(), power);
        com.shadowclangaming.network.ScgNet.sendBlastFx(level, style, type.id(), pos, radius, ticks);

        // A little extra spice on the biggest styles: a light flash tells the eye "this one mattered" even
        // if the shader chain is unavailable on this machine.
        if (style == BlastStyle.VOID || style == BlastStyle.COIN) {
            com.shadowclangaming.perf.FxBudget.send(level, ParticleTypes.END_ROD, pos, 24,
                    Math.min(24.0D, radius * 0.2D), 3.0D, Math.min(24.0D, radius * 0.2D), 0.06D);
        }
    }
}
