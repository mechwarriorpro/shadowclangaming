package com.shadowclangaming.warhead;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.block.AutoTraderBlockEntity;
import com.shadowclangaming.block.LaunchPadBlockEntity;
import com.shadowclangaming.perf.BlastScheduler;
import com.shadowclangaming.perf.FxBudget;
import com.shadowclangaming.block.ProcessorBlockEntity;
import com.shadowclangaming.item.TargetDesignatorItem;
import com.shadowclangaming.spell.Spell;
import com.shadowclangaming.spell.SpellCasting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Every warhead in the mod. One enum entry = one item (see SCGItems).
 *
 * {@link #detonate} is the whole personality of a warhead. Adding a new one is a single enum entry plus
 * a branch here - no new classes, no new entity, no new item class.
 *
 * Clean-room note: these are our own designs and numbers. "EMP does no blast damage", "decoy makes the
 * sound of a real strike", "antimatter flattens the grid" are game-design ideas, not anyone's code.
 */
public enum WarheadType {
    /**
     * An EMPTY module: no warhead fitted.
     *
     * "Also add the ability to fire empty modules." A module with nothing in it still flies, still
     * arrives, and does nothing when it gets there beyond a puff of dust - that is a legitimate thing to
     * want (a test shot, a range check, or a decoy that costs no explosives), so it is a warhead type
     * rather than an error.
     */
    DUD("dud", 0.0F),
    /** Makes the noise and smoke of a real strike, does absolutely nothing. Psychological warfare. */
    DECOY("decoy", 0.0F),
    /** No block damage. Kills electronics: pads, chemistry machines, designators, and player vision. */
    EMP("emp", 0.0F),
    /** Standard high explosive. */
    HE("he", 6.0F),
    /** Smaller blast, sets the area alight. */
    INCENDIARY("incendiary", 4.0F),
    /** Splits into a spread of small detonations. */
    CLUSTER("cluster", 3.0F),
    /** Fission: big blast, fire, lingering sickness. */
    FISSION("fission", 14.0F),
    /** Fusion: bigger blast, blinding flash, fire. */
    FUSION("fusion", 22.0F),
    /** Antimatter: the big one. Uses almost all the power budget on purpose. */
    ANTIMATTER("antimatter", 45.0F),
    /** Blast radius is bought with currency. See {@link Funding}. */
    NUMISMATIC("numismatic", 0.0F),

    // ------------------------------------------------------------------ apex warheads
    //
    // Everything from here down is one of the seventeen endgame weapons. They are declared as warhead
    // types so they own an item and a slot in the roster, but their behaviour lives in
    // warhead/apex/ApexWarheads - see Apex for what each one is and ApexTimeline for its shape in time.
    //
    // The power value is the warhead's own default blast power; the pad slider still overrides the radius.

    /** A black hole that eats the battlefield. */
    WORLD_EATER("world_eater", 76.8F),
    /** An artificial star that brightens, then detonates. */
    STELLAR_REQUIEM("stellar_requiem", 102.4F),
    /** A tear in spacetime. */
    REALITY_BREAKER("reality_breaker", 96.0F),
    /** Successive gravitational shockwaves. */
    SPACETIME_HAMMER("spacetime_hammer", 88.0F),
    /** A spherical collapse, then one final pulse. */
    UNIVERSAL_IMPLODER("universal_imploder", 80.0F),
    /** Meteors, spectral lightning, and a blood-red sky. */
    RAGNAROK("ragnarok", 120.0F),
    /** A rune engraved into the land, which then detonates. */
    WORLD_RUNE("world_rune", 112.0F),
    /** Hundreds of portals into somewhere else. */
    ABYSSAL_COVENANT("abyssal_covenant", 72.0F),
    /** A spectral vortex that eats the living and grows. */
    SOUL_FURNACE("soul_furnace", 64.0F),
    /** A magical sphere ringed with spell circles. */
    ARCANE_SINGULARITY("arcane_singularity", 84.0F),
    /** A quasar jet that carves a canyon. */
    QUASAR_LANCE("quasar_lance", 51.2F),
    /** An artificial star whose corona melts the ground. */
    STARFORGE("starforge", 104.0F),
    /** A point of light that becomes a sphere of energy. */
    BIG_BANG_SEED("big_bang_seed", 128.0F),
    /** Corrupted geometry and deleted land. */
    REALITY_CORRUPTOR("reality_corruptor", 76.0F),
    /** A motionless core that absorbs everything, then releases it. */
    ABSOLUTE_ZERO("absolute_zero", 68.0F),
    /** An annihilation front and a secondary implosion. */
    OMEGA("omega", 160.0F),
    /** The last weapon on the roster. */
    ABSOLUTE_ANNIHILATION("absolute_annihilation", 204.8F),

    /**
     * No explosion at all. Money buys a containment dome where it lands: size and duration both come
     * from the funding. See {@link ForceFields}.
     */
    BULWARK("bulwark", 0.0F),
    /**
     * A modest blast that leaves a plague behind: random symptoms, which spread from victim to victim
     * and get worse with every jump. See {@link Outbreak}.
     */
    CONTAGION("contagion", 4.0F),
    /**
     * No explosion of its own. Fires a copy of an inscribed spell at the impact point, scaled up.
     * Loading the spell costs money and XP at the inscription table - see Spell#copyFee.
     */
    SPELL("spell", 0.0F),
    /**
     * A courier, not a weapon. Drops a chest of whatever was loaded into it on the ground below the
     * impact and destroys nothing. See CargoDelivery.
     */
    CARGO("cargo", 0.0F),
    /**
     * The mine layer. Radius = 32 + funding/250, and every ground column in that radius has a 10%
     * chance of getting a mine. See Minefield.
     */
    MINEFIELD("minefield", 0.0F),
    /**
     * Homes onto the entity a Tracker locked for it. Small blast; the point is that it follows you.
     */
    TRACKING("tracking", 7.0F),
    /**
     * Builds instead of destroying: fills a solid sphere with the blocks loaded into it, filling holes
     * and leaving anything already built alone. Radius comes from the payload (the largest sphere it
     * can afford) or from the pad's radius slider. See ConstructorBlast / ConstructorPlacer.
     */
    CONSTRUCTOR("constructor", 0.0F),
    /**
     * Loads the volume it lands in into an AE2 spatial storage cell and carries it away. Destroys
     * nothing by itself. See Ae2Cells.
     */
    SPATIAL("spatial", 0.0F),
    /** The Mother Clucker: 50 turret chickens that shoot eggs, whose eggs hatch more turret chickens. */
    CLUCKER("clucker", 0.0F),
    /** The Violator: a wither with 3x health and 5x skulls that bursts five seconds in every thirty. */
    VIOLATOR("violator", 0.0F),
    /**
     * Beef Supreme: a cow with 5000 health, a boss bar, and a 30-damage ram it telegraphs for five
     * seconds. See BeefSupremeEntity.
     */
    BEEF("beef", 0.0F);

    private final String id;
    private final float basePower;

    WarheadType(String id, float basePower) {
        this.id = id;
        this.basePower = basePower;
    }

    public String id() {
        return id;
    }

    public float basePower() {
        return basePower;
    }

    /**
     * "Radius" means different things per funded warhead: blast radius for Numismatic, dome radius
     * for Bulwark. Every HUD message goes through here so the pad and the Bank Card never quote the
     * wrong curve.
     */
    public int radiusForFunding(long funding) {
        return switch (this) {
            case BULWARK -> ForceFields.radiusForFunding(funding);
            case MINEFIELD -> Minefield.radiusForFunding(funding);
            default -> Funding.radiusForFunding(funding);
        };
    }

    /**
     * True for warheads whose radius the player sets directly (the pad's slider) instead of buying it
     * with currency: Constructor builds to that radius, Spatial stores that many blocks per side.
     */
    public boolean usesRadiusSlider() {
        return this == CONSTRUCTOR || this == SPATIAL;
    }

    /** True for warheads whose power comes from what was loaded into them rather than their item. */
    public boolean usesPayload() {
        return this == CONSTRUCTOR || this == SPATIAL || this == CLUCKER;
    }

    /** Total funding needed to reach {@code radius}, on this warhead's own curve. */
    public long fundingForRadius(int radius) {
        return switch (this) {
            case BULWARK -> ForceFields.fundingForRadius(radius);
            case MINEFIELD -> Minefield.fundingForRadius(radius);
            default -> Funding.costForRadius(radius);
        };
    }

    /**
     * Warheads whose strength is bought with money rather than fixed in the item. Minefield is on the
     * list because its radius is funded; the loading itself is still currency or FE, on any warhead.
     */
    public boolean acceptsFunding() {
        return this == NUMISMATIC || this == BULWARK || this == MINEFIELD;
    }

    /** Radius from funding, for any warhead with a funded radius. Returns -1 for the others. */
    public int fundedRadius(long funding) {
        return switch (this) {
            case NUMISMATIC -> Funding.radiusForFunding(funding);
            case BULWARK -> ForceFields.radiusForFunding(funding);
            case MINEFIELD -> Minefield.radiusForFunding(funding);
            default -> -1;
        };
    }

    /** True if this warhead's radius curve is its own rather than the shared blast curve. */
    public boolean hasOwnRadiusCurve() {
        return this == BULWARK || this == MINEFIELD;
    }

    public String translationKey() {
        return "warhead.shadowclangaming." + id;
    }

    public static WarheadType byId(String id) {
        for (WarheadType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return HE;
    }

    /**
     * Actually fire the warhead.
     *
     * @param level   server level
     * @param pos     impact position
     * @param power   final explosion power (already scaled by the missile item)
     * @param funding currency loaded into a NUMISMATIC warhead (ignored by the others)
     */
    public void detonate(ServerLevel level, Vec3 pos, float power, long funding) {
        detonate(level, pos, power, funding, null, null);
    }

    public void detonate(ServerLevel level, Vec3 pos, float power, long funding, Spell spell) {
        detonate(level, pos, power, funding, spell, null);
    }

    /**
     * @param spell the spell carried by a SPELL warhead, or null for every other warhead.
     * @param cargo the items carried by a CARGO warhead, or null for every other warhead.
     */
    public void detonate(ServerLevel level, Vec3 pos, float power, long funding, Spell spell,
                         java.util.List<ItemStack> cargo) {
        detonate(level, pos, power, funding, spell, cargo, -1, 0L);
    }

    /**
     * @param radius     the pad's slider setting, or -1 for "derive it from the payload"
     * @param buildSeed  stable per-missile seed so a constructor blend is reproducible
     */
    public void detonate(ServerLevel level, Vec3 pos, float power, long funding, Spell spell,
                         java.util.List<ItemStack> cargo, int sliderRadius, long buildSeed) {
        // The seventeen apex warheads are staged weapons: they own a timeline, so they hand off to
        // ApexWarheads instead of doing their work in this switch. One line covers all of them.
        // EVERY warhead announces its detonation here, before anything can return. This line used to sit
        // below the apex hand-off, and the seventeen endgame weapons all return from inside it - so the
        // World Eater, Ragnarok, the Quasar Lance and the rest played their charge effect for the whole
        // fuse and then went off with no impact effect at all. The weapons most worth watching were the
        // only ones with nothing to watch.
        com.shadowclangaming.warhead.apex.Apex apex =
                com.shadowclangaming.warhead.apex.Apex.byId(id);
        if (apex != null) {
            // Sized to the radius this weapon will actually carve, which is the pad's slider when it is
            // set - not the warhead's base power, or a 512-block black hole would draw as a 96-block one.
            int apexRadius = Math.max(4, sliderRadius > 0 ? sliderRadius : apex.radius());
            com.shadowclangaming.warhead.BlastFx.emit(level, this, pos, (apexRadius - 8) / 6.0F);
            com.shadowclangaming.warhead.apex.ApexWarheads.fire(level, pos, apex, sliderRadius,
                    buildSeed);
            return;
        }

        com.shadowclangaming.warhead.BlastFx.emit(level, this, pos, power);

        switch (this) {
            case DUD -> {
                // Empty module: no explosion, no damage, no blocks. Just the sound of a wasted rocket.
                FxBudget.send(level, ParticleTypes.CLOUD, pos, 30, 1.2, 0.8, 1.2, 0.03);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_BREAK,
                        SoundSource.BLOCKS, 1.2F, 1.0F);
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("message.shadowclangaming.dud_impact"), false);
            }
            case DECOY -> {
                // Sound and smoke of a genuine strike. No damage, no blocks broken.
                FxBudget.send(level, ParticleTypes.LARGE_SMOKE, pos, 80, 2.5, 1.5, 2.5, 0.02);
                FxBudget.send(level, ParticleTypes.EXPLOSION, pos, 3, 1.5, 1.0, 1.5, 0.0);
                playBoom(level, pos, 3.0F);
            }
            case EMP -> empPulse(level, pos);
            case HE -> boom(level, pos, power);
            case INCENDIARY -> {
                boom(level, pos, power * 0.6F);
                ignite(level, pos, (int) (power * 1.6F));
            }
            case CLUSTER -> {
                boom(level, pos, power * 0.5F);
                int bomblets = 6;
                for (int i = 0; i < bomblets; i++) {
                    double angle = (Math.PI * 2.0 / bomblets) * i;
                    double distance = 4.0 + level.getRandom().nextDouble() * 6.0;
                    Vec3 at = pos.add(Math.cos(angle) * distance, 0.5, Math.sin(angle) * distance);
                    boom(level, at, power * 0.45F);
                }
            }
            case FISSION -> {
                boom(level, pos, power);
                ignite(level, pos, (int) (power * 2.0F));
                irradiate(level, pos, (int) (power * 2.5F));
                FxBudget.send(level, ParticleTypes.EXPLOSION_EMITTER, pos, 6, 4.0, 3.0, 4.0, 0.0);
            }
            case FUSION -> {
                boom(level, pos, power);
                FxBudget.send(level, ParticleTypes.FLASH, pos, 1, 0, 0, 0, 0);
                FxBudget.send(level, ParticleTypes.EXPLOSION_EMITTER, pos, 10, 5.0, 4.0, 5.0, 0.0);
                ignite(level, pos, (int) (power * 2.5F));
                irradiate(level, pos, (int) (power * 3.0F));
                blindPlayers(level, pos, (int) (power * 3.0F), 200);
            }
            case ANTIMATTER -> {
                // Two-stage for the visual: a wide outer shell, then the core.
                boom(level, pos, power * 0.7F);
                boom(level, pos, power);
                FxBudget.send(level, ParticleTypes.EXPLOSION_EMITTER, pos, 24, 8.0, 5.0, 8.0, 0.0);
                FxBudget.send(level, ParticleTypes.END_ROD, pos, 120, 4.0, 3.0, 4.0, 0.3);
                ignite(level, pos, (int) (power * 2.0F));
                blindPlayers(level, pos, (int) (power * 4.0F), 300);
                playBoom(level, pos, 6.0F);
            }
            case BULWARK -> {
                // Destroys nothing. Turns the impact point into a place you cannot cross.
                ForceFields.deploy(level, pos, funding);
                FxBudget.send(level, ParticleTypes.END_ROD, pos, 40, 3.0, 2.0, 3.0, 0.15);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE,
                        SoundSource.BLOCKS, 1.4F, 0.7F);
            }
            case CONTAGION -> {
                // Real blast - that is how it finds its first victims.
                boom(level, pos, power);
                int radius = Math.max(4, Math.round(power / 0.8F));
                int infected = Outbreak.infect(level, pos, radius * 1.5D);
                FxBudget.send(level, ParticleTypes.ITEM_SLIME, pos, 60, radius, radius * 0.6, radius, 0.05);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WITCH_DRINK,
                        SoundSource.BLOCKS, 1.6F, 0.6F);
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("message.shadowclangaming.plague_released", infected), false);
            }
            case SPELL -> {
                if (spell == null || spell.isEmpty()) {
                    // A warhead with no spell is a dud. Say so loudly rather than quietly doing nothing.
                    FxBudget.send(level, ParticleTypes.LARGE_SMOKE, pos, 40, 1.5, 1.0, 1.5, 0.02);
                    level.getServer().getPlayerList().broadcastSystemMessage(
                            Component.translatable("message.shadowclangaming.spell_warhead_dud"), false);
                    return;
                }
                SpellCasting.castAtPoint(level, spell, pos, SpellCasting.WARHEAD_SCALE);
                FxBudget.send(level, ParticleTypes.END_ROD, pos, 60, 3.0, 2.0, 3.0, 0.1);
                FxBudget.send(level, ParticleTypes.ENCHANT, pos, 60, 3.0, 2.0, 3.0, 0.1);
                playBoom(level, pos, 2.0F);
            }
            case CARGO -> {
                // Nothing is destroyed. The chest is the whole point.
                java.util.List<ItemStack> loaded = cargo == null ? java.util.List.of() : cargo;
                int delivered = CargoDelivery.deliver(level, BlockPos.containing(pos), loaded);
                FxBudget.send(level, ParticleTypes.CLOUD, pos, 30, 1.5, 1.0, 1.5, 0.02);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_PICKUP,
                        SoundSource.BLOCKS, 1.0F, 0.8F);
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("message.shadowclangaming.cargo_delivered", delivered), false);
            }
            case MINEFIELD -> {
                int radius = Minefield.radiusForFunding(funding);
                int placed = Minefield.deploy(level, BlockPos.containing(pos), radius);
                FxBudget.send(level, ParticleTypes.FIREWORK, pos, 40, radius * 0.4, 1.0, radius * 0.4, 0.02);
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("message.shadowclangaming.minefield_laid", placed, radius), false);
            }
            case TRACKING -> {
                boom(level, pos, power);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 1.2F, 0.7F);
            }
            case CONSTRUCTOR -> {
                java.util.List<ItemStack> loaded = cargo == null ? java.util.List.of() : cargo;
                int seed = (int) (buildSeed ^ (buildSeed >>> 32));
                ConstructorPlacer.Report report = ConstructorPlacer.build(level,
                        BlockPos.containing(pos), loaded,
                        sliderRadius, seed);
                if (report.isCell()) {
                    // The cell is the payload: drop it whole, exactly as if it had been a cargo drop.
                    CargoDelivery.deliver(level, BlockPos.containing(pos), loaded);
                    level.getServer().getPlayerList().broadcastSystemMessage(
                            Component.translatable("message.shadowclangaming.constructor_cell"), false);
                    return;
                }
                if (!report.ok()) {
                    // Say why, and drop everything back so nothing is lost to a failed build.
                    level.getServer().getPlayerList().broadcastSystemMessage(
                            Component.translatable("message.shadowclangaming.constructor_failed",
                                    report.problem()), false);
                    CargoDelivery.deliver(level, BlockPos.containing(pos), loaded);
                    return;
                }
                // Anything that was not a block comes back as cargo; the sphere takes the rest.
                if (!report.nonBlockItems().isEmpty()) {
                    CargoDelivery.deliver(level, BlockPos.containing(pos), report.nonBlockItems());
                }
                FxBudget.send(level, ParticleTypes.END_ROD, pos, 50, report.radius(), 1.0, report.radius(), 0.05);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE,
                        SoundSource.BLOCKS, 1.8F, 1.2F);
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("message.shadowclangaming.constructor_built",
                                ConstructorBlast.blocksInSphere(report.radius())), false);
            }
            case SPATIAL -> {
                java.util.List<ItemStack> loaded = cargo == null ? java.util.List.of() : cargo;
                ItemStack cell = ItemStack.EMPTY;
                for (ItemStack stack : loaded) {
                    if (Ae2Cells.isSpatialCell(stack)) {
                        cell = stack;
                        break;
                    }
                }
                if (cell.isEmpty()) {
                    level.getServer().getPlayerList().broadcastSystemMessage(
                            Component.translatable("message.shadowclangaming.spatial_no_cell"), false);
                    return;
                }
                int side = sliderRadius > 0 ? sliderRadius : 8;
                String problem = Ae2Cells.storeRegion(level, cell, BlockPos.containing(pos), side);
                if (problem != null) {
                    // Nothing is destroyed and nothing is stored: the cell goes back as cargo.
                    CargoDelivery.deliver(level, BlockPos.containing(pos), loaded);
                    level.getServer().getPlayerList().broadcastSystemMessage(
                            Component.translatable("message.shadowclangaming.spatial_failed", problem), false);
                    return;
                }
                // The cell itself is the only thing that survives - it now holds the terrain.
                CargoDelivery.deliver(level, BlockPos.containing(pos), java.util.List.of(cell));
                FxBudget.send(level, ParticleTypes.PORTAL, pos, 120, side, side * 0.5, side, 0.4);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.BLOCKS, 2.0F, 0.5F);
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("message.shadowclangaming.spatial_stored", side * 2 + 1), false);
            }
            case CLUCKER -> {
                int spawned = CluckerStrike.release(level, pos);
                // A small kick so the chickens are not standing in a crater.
                boom(level, pos, Math.min(2.0F, power));
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("message.shadowclangaming.clucker_released", spawned), false);
            }
            case BEEF -> {
                var cow = com.shadowclangaming.registry.SCGEntities.BEEF_SUPREME.get().create(level);
                if (cow instanceof com.shadowclangaming.entity.BeefSupremeEntity supreme) {
                    supreme.moveTo(pos.x, pos.y + 1.0D, pos.z,
                            level.getRandom().nextFloat() * 360.0F, 0.0F);
                    level.addFreshEntity(supreme);
                    FxBudget.send(level, ParticleTypes.EXPLOSION_EMITTER, pos, 2, 2.0, 1.0, 2.0, 0.0);
                    level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.RAVAGER_ROAR,
                            SoundSource.HOSTILE, 3.0F, 0.5F);
                    level.getServer().getPlayerList().broadcastSystemMessage(
                            Component.translatable("message.shadowclangaming.beef_arrived"), false);
                } else {
                    level.getServer().getPlayerList().broadcastSystemMessage(
                            Component.translatable("message.shadowclangaming.beef_failed"), false);
                }
            }
            case VIOLATOR -> {
                net.minecraft.world.entity.boss.wither.WitherBoss wither = ViolatorWither.spawn(level, pos);
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable(wither != null
                                        ? "message.shadowclangaming.violator_arrived"
                                        : "message.shadowclangaming.violator_failed"),
                        false);
            }
            case NUMISMATIC -> {
                // power already came from Funding.powerForFunding(funding)
                boom(level, pos, power);
                int radius = Funding.radiusForFunding(funding);
                if (radius >= 32) {
                    FxBudget.send(level, ParticleTypes.EXPLOSION_EMITTER, pos, 8, 6.0, 4.0, 6.0, 0.0);
                }
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("message.shadowclangaming.numismatic_detonation",
                                String.format("%,d", funding), radius), false);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The one line that decides how a blast destroys blocks.
     *
     * Small blasts  -> vanilla explode(): already fast, handles drops, damage, fire and sound.
     * Large blasts  -> BlastScheduler: geometry planned on worker threads, blocks cleared on the server
     *                  thread a few sections per tick, so a 96-block warhead does not freeze the server.
     *
     * Because the scheduler does not call vanilla explode(), we apply entity damage ourselves first.
     */
    /**
     * Public form of {@link #boom} for the apex warhead layer, which is in a subpackage and so cannot see
     * a private method. Everything it does - the multithreaded planner, the persistent scar path for
     * continent-scale radii, entity damage - is the same code the ordinary warheads use.
     */
    public static void blast(ServerLevel level, Vec3 pos, float power) {
        boom(level, pos, power);
    }

    private static void boom(ServerLevel level, Vec3 pos, float power) {
        if (power <= 0.0F) {
            return;
        }

        // power ~= radius * 0.8 (see Funding.powerForRadius), so this is the equivalent block radius.
        int radius = Math.max(1, Math.round(power / 0.8F));

        if (radius > com.shadowclangaming.perf.StrategicBlasts.SCAR_THRESHOLD) {
            // Continent-scale: record a persistent scar and scrub what is loaded. See StrategicBlasts.
            applyBlastDamage(level, pos, radius, power);
            long columns = com.shadowclangaming.perf.StrategicBlasts.detonate(level, pos, radius);
            ShadowClanGaming.LOGGER.info("Strategic blast radius {} - {} chunk columns in the scar",
                    radius, String.format("%,d", columns));
            playBoom(level, pos, 8.0F);
            return;
        }

        if (radius > BlastScheduler.VANILLA_THRESHOLD) {
            applyBlastDamage(level, pos, radius, power);
            BlastScheduler.INSTANCE.submit(level, pos, radius, power);
            playBoom(level, pos, Math.min(6.0F, 1.0F + power * 0.1F));
            return;
        }

        level.explode(null, pos.x, pos.y, pos.z, power, Level.ExplosionInteraction.TNT);
        playBoom(level, pos, Math.min(6.0F, 1.0F + power * 0.1F));
    }

    /**
     * Damage + knockback for scheduled blasts (vanilla does this inside Explosion).
     * Falloff matches vanilla: roughly power * (1 - distance/radius).
     */
    private static void applyBlastDamage(ServerLevel level, Vec3 pos, int radius, float power) {
        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, e -> e.isAlive())) {
            double distance = Math.sqrt(entity.position().distanceToSqr(pos));
            if (distance > radius) {
                continue;
            }
            double falloff = 1.0 - distance / radius;
            float damage = (float) (power * falloff * falloff * 2.0F);
            if (damage <= 0.0F) {
                continue;
            }
            entity.hurt(level.damageSources().explosion(null, null), damage);
            Vec3 push = entity.position().subtract(pos).normalize().scale(falloff * 1.5);
            entity.push(push.x, push.y + 0.3, push.z);
        }
    }

    private static void playBoom(ServerLevel level, Vec3 pos, float volume) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, volume, 0.8F + level.getRandom().nextFloat() * 0.3F);
    }

    /** Sets fire on exposed surfaces in a radius. Cheap and dramatic - do not make the radius huge. */
    private static void ignite(ServerLevel level, Vec3 pos, int radius) {
        BlockPos centre = BlockPos.containing(pos.x, pos.y, pos.z);
        int clamped = Math.min(radius, 40);
        for (BlockPos p : BlockPos.betweenClosed(
                centre.offset(-clamped, -clamped / 2, -clamped),
                centre.offset(clamped, clamped / 2, clamped))) {
            if (!level.getBlockState(p).isAir()) {
                continue;
            }
            if (level.getBlockState(p.below()).isAir()) {
                continue;
            }
            if (level.getRandom().nextFloat() < 0.12F) {
                level.setBlockAndUpdate(p, Blocks.FIRE.defaultBlockState());
            }
        }
    }

    /** Light crowd-control: sick, glowing, and briefly blinded. No continuous-damage system yet. */
    private static void irradiate(ServerLevel level, Vec3 pos, int radius) {
        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, e -> e instanceof Player)) {
            if (entity instanceof Player player) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1200, 0));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0));
            }
        }
    }

    private static void blindPlayers(ServerLevel level, Vec3 pos, int radius, int ticks) {
        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, e -> e instanceof Player)) {
            if (entity instanceof Player player) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0));
            }
        }
    }

    /**
     * EMP: no blocks destroyed. Instead it scrambles everything electric in the area.
     *
     * This is intentionally the "counter-battery" weapon of the pack: it doesn't kill people, it
     * disarms them - launch pads go on a long cooldown, chemistry machines stop, and every target
     * designator nearby is drained flat.
     */
    private static void empPulse(ServerLevel level, Vec3 pos) {
        final int radius = 14;
        BlockPos centre = BlockPos.containing(pos.x, pos.y, pos.z);

        long drained = 0;
        int drainedMachines = 0;
        for (BlockPos p : BlockPos.betweenClosed(
                centre.offset(-radius, -radius / 2, -radius),
                centre.offset(radius, radius / 2, radius))) {
            BlockEntity blockEntity = level.getBlockEntity(p);
            if (blockEntity == null) {
                continue;
            }
            int taken;
            if (blockEntity instanceof LaunchPadBlockEntity pad) {
                pad.applyEmp(200);
                taken = pad.empDrain();
            } else if (blockEntity instanceof AutoTraderBlockEntity trader) {
                taken = trader.empDrain();
            } else {
                // Everything else electrical in range loses its stored energy, including other mods'
                // machines: this goes through the capability, not through our own classes. Our pads and
                // traders are handled above because the capability they advertise is receive-only.
                taken = drainEnergy(level, p, blockEntity);
            }
            if (blockEntity instanceof ProcessorBlockEntity processor) {
                processor.applyEmp(200);
            }
            if (taken > 0) {
                drained += taken;
                drainedMachines++;
            }
        }
        if (drainedMachines > 0) {
            com.shadowclangaming.debug.ShadowDebug.log("EMP_DRAIN",
                    "pos=({}, {}, {}) radius={} machines={} fe={}",
                    pos.x, pos.y, pos.z, radius, drainedMachines, drained);
        }

        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, e -> e instanceof Player)) {
            if (entity instanceof Player player) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 160, 0));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 300, 1));
                // Burn out any designator they are carrying.
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack.getItem() instanceof TargetDesignatorItem) {
                        TargetDesignatorItem.setEnergy(stack, 0);
                    }
                }
                player.displayClientMessage(
                        Component.translatable("message.shadowclangaming.emp_hit"), true);
                if (drained > 0L) {
                    player.displayClientMessage(Component.translatable(
                            "message.shadowclangaming.emp_drained", drained, drainedMachines), false);
                }
            }
        }

        FxBudget.send(level, ParticleTypes.END_ROD, pos, 60, radius * 0.6, radius * 0.3, radius * 0.6, 0.05);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.BLOCKS, 1.2F, 1.6F);
    }


    /**
     * Pull every joule out of one block's energy storage.
     *
     * <p>Through the capability, not through our own block entities, so a Create dynamo, an AE2 charger or
     * anything else in the modpack is drained exactly like a launch pad. Every face is tried, because a
     * machine that only exposes its storage on one side is not unusual, and the extraction loops until the
     * storage says there is nothing left - some implementations cap a single call at their own transfer
     * rate, which would otherwise leave a big buffer mostly full.
     *
     * @return the FE actually removed
     */
    private static int drainEnergy(ServerLevel level, BlockPos pos, BlockEntity blockEntity) {
        net.neoforged.neoforge.energy.IEnergyStorage storage =
                level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                        pos, null);
        if (storage == null) {
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                storage = level.getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, pos, side);
                if (storage != null) {
                    break;
                }
            }
        }
        if (storage == null || storage.getEnergyStored() <= 0) {
            return 0;
        }
        int total = 0;
        for (int attempt = 0; attempt < 64; attempt++) {
            int taken = storage.extractEnergy(Integer.MAX_VALUE, false);
            if (taken <= 0) {
                break;
            }
            total += taken;
            if (storage.getEnergyStored() <= 0) {
                break;
            }
        }
        if (total > 0) {
            blockEntity.setChanged();
        }
        return total;
    }

    @Override
    public String toString() {
        return id.toLowerCase(Locale.ROOT);
    }
}
