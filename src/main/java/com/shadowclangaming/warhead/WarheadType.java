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
    BEEF("beef", 0.0F),

    // ------------------------------------------------------------------ optional mod integration warheads

    /** Create rotational overtorque implosion: kinetic shockwave flinging entities and shearing machinery. */
    KINETIC("kinetic", 28.0F),
    /** Mekanism supercritical phase reactor breach: radioactive fallout, polonium ionizing blast, and energy drain. */
    SUPERCRITICAL("supercritical", 36.0F),
    /** Applied Energistics 2 matter condenser: gravitational matter compression, ME cable wipeout, and singularity formation. */
    MATTER_CONDENSER("matter_condenser", 32.0F),
    /** Dyson Cube Project solar collector beam: focused orbital death-ray vaporizing a shaft from sky to bedrock. */
    STELLAR_SWARM("stellar_swarm", 42.0F),
    /** ProjectE transmutation burst: matter-to-EMC conversion wave that siphons alchemical energy into condensers. */
    TRANSMUTATION("transmutation", 30.0F),
    /** Project EX colossal star rupture: astronomical alchemical supernova unleashing unformatted infinity EMC. */
    COLOSSAL_STAR("colossal_star", 50.0F),
    /** Railcraft steam boiler catastrophe: superheated locomotive steam explosion scalding living targets and shattering tracks. */
    STEAM_RUPTURE("steam_rupture", 24.0F),
    /** RedPower / ProjectRed blulectric overload: voltage surge burning out logic circuits, alloy wires, and redstone gates. */
    VOLTMETER("voltmeter", 18.0F),
    /** Witchery / Bewitchment coven hex: dark voodoo malediction inflicting crippling curses, phantom shades, and eclipse. */
    VOODOO("voodoo", 20.0F),
    /** Thermal Series entropy cataclysm: dual-phase Pyrotheum and Cryotheum thermal shockwave shattering matter. */
    THERMAL_ENTROPY("thermal_entropy", 26.0F),
    /** Botania mana pool overcharge: hyper-condensed mana discharge releasing shimmering pixies and floral shockwaves. */
    MANA_OVERLOAD("mana_overload", 22.0F),
    /** Draconic Evolution reactor core failure: apocalyptic chaos containment failure disintegrating everything in its wake. */
    DRACONIC_REACTOR("draconic_reactor", 60.0F),
    /** Blood Magic sanguine sacrifice: vampiric essence harvest siphoning life force and inscribing demonic blood glyphs. */
    SANGUINE("sanguine", 25.0F);

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
            case KINETIC -> detonateKinetic(level, pos, power);
            case SUPERCRITICAL -> detonateSupercritical(level, pos, power);
            case MATTER_CONDENSER -> detonateMatterCondenser(level, pos, power);
            case STELLAR_SWARM -> detonateStellarSwarm(level, pos, power);
            case TRANSMUTATION -> detonateTransmutation(level, pos, power);
            case COLOSSAL_STAR -> detonateColossalStar(level, pos, power);
            case STEAM_RUPTURE -> detonateSteamRupture(level, pos, power);
            case VOLTMETER -> detonateVoltmeter(level, pos, power);
            case VOODOO -> detonateVoodoo(level, pos, power);
            case THERMAL_ENTROPY -> detonateThermalEntropy(level, pos, power);
            case MANA_OVERLOAD -> detonateManaOverload(level, pos, power);
            case DRACONIC_REACTOR -> detonateDraconicReactor(level, pos, power);
            case SANGUINE -> detonateSanguine(level, pos, power);
        }
    }

    private static void detonateKinetic(ServerLevel level, Vec3 pos, float power) {
        boom(level, pos, power);
        int radius = Math.max(8, Math.round(power * 1.2F));
        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, Entity::isAlive)) {
            double distance = Math.sqrt(entity.position().distanceToSqr(pos));
            if (distance > radius || distance < 0.01) {
                continue;
            }
            double falloff = 1.0 - distance / radius;
            Vec3 away = entity.position().subtract(pos).normalize();
            Vec3 torque = new Vec3(-away.z, 0.4, away.x).scale(falloff * 2.0);
            Vec3 push = away.scale(falloff * 3.5).add(torque);
            entity.push(push.x, Math.min(2.5, push.y + 0.5), push.z);
            entity.hurt(level.damageSources().generic(), (float) (power * falloff * 2.5F));
        }
        BlockPos centre = BlockPos.containing(pos.x, pos.y, pos.z);
        int blockRadius = Math.min(radius, 24);
        for (BlockPos p : BlockPos.betweenClosed(
                centre.offset(-blockRadius, -blockRadius / 2, -blockRadius),
                centre.offset(blockRadius, blockRadius / 2, blockRadius))) {
            if (level.getRandom().nextFloat() > 0.35F) {
                continue;
            }
            var state = level.getBlockState(p);
            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            if (name.contains("shaft") || name.contains("cog") || name.contains("gear") || name.contains("belt")
                    || name.contains("bearing") || name.contains("piston") || name.contains("kinetic")
                    || name.contains("flywheel") || name.contains("glass")) {
                level.destroyBlock(p, true);
            }
        }
        FxBudget.send(level, ParticleTypes.CRIT, pos, 80, radius * 0.5, radius * 0.3, radius * 0.5, 0.4);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 2.0F, 0.8F);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.kinetic_cataclysm"), false);
    }

    private static void detonateSupercritical(ServerLevel level, Vec3 pos, float power) {
        boom(level, pos, power);
        int radius = Math.max(10, Math.round(power * 1.5F));
        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, e -> e instanceof Player)) {
            if (entity instanceof Player player) {
                player.addEffect(new MobEffectInstance(MobEffects.WITHER, 300, 3));
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1200, 0));
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 1));
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 300, 1));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
            }
        }
        BlockPos centre = BlockPos.containing(pos.x, pos.y, pos.z);
        int meltRadius = Math.min(radius / 2, 16);
        for (BlockPos p : BlockPos.betweenClosed(
                centre.offset(-meltRadius, -meltRadius / 2, -meltRadius),
                centre.offset(meltRadius, meltRadius / 2, meltRadius))) {
            if (level.getRandom().nextFloat() < 0.25F && !level.getBlockState(p).isAir()
                    && level.getBlockState(p.above()).isAir()) {
                level.setBlockAndUpdate(p, Blocks.MAGMA_BLOCK.defaultBlockState());
            }
        }
        FxBudget.send(level, ParticleTypes.SCULK_SOUL, pos, 100, 4.0, 3.0, 4.0, 0.2);
        FxBudget.send(level, ParticleTypes.ELECTRIC_SPARK, pos, 80, 5.0, 4.0, 5.0, 0.3);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 2.0F, 0.7F);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.supercritical_detonation"), false);
    }

    private static void detonateMatterCondenser(ServerLevel level, Vec3 pos, float power) {
        int radius = Math.max(8, Math.round(power * 1.0F));
        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, Entity::isAlive)) {
            Vec3 toward = pos.subtract(entity.position());
            double dist = toward.length();
            if (dist > 0.1) {
                entity.push(toward.x * 0.3, toward.y * 0.3 + 0.2, toward.z * 0.3);
            }
            entity.hurt(level.damageSources().magic(), power * 1.2F);
        }
        boom(level, pos, power * 0.8F);
        BlockPos dropPos = BlockPos.containing(pos.x, pos.y + 1, pos.z);
        level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level,
                dropPos.getX() + 0.5, dropPos.getY() + 0.5, dropPos.getZ() + 0.5,
                new ItemStack(Blocks.IRON_BLOCK, 4)));
        FxBudget.send(level, ParticleTypes.REVERSE_PORTAL, pos, 120, 3.0, 3.0, 3.0, 0.3);
        FxBudget.send(level, ParticleTypes.DRAGON_BREATH, pos, 60, 4.0, 2.0, 4.0, 0.1);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.8F, 1.4F);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.matter_condenser_collapse"), false);
    }

    private static void detonateStellarSwarm(ServerLevel level, Vec3 pos, float power) {
        int radius = Math.max(4, Math.round(power * 0.2F));
        int maxY = level.getMaxBuildHeight();
        int minY = level.getMinBuildHeight();
        BlockPos centre = BlockPos.containing(pos.x, pos.y, pos.z);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    for (int y = maxY; y >= minY; y--) {
                        BlockPos p = new BlockPos(centre.getX() + x, y, centre.getZ() + z);
                        var state = level.getBlockState(p);
                        if (!state.isAir() && state.getBlock() != Blocks.BEDROCK) {
                            if (y == minY + 1 || level.getRandom().nextFloat() < 0.1F) {
                                level.setBlockAndUpdate(p, Blocks.MAGMA_BLOCK.defaultBlockState());
                            } else {
                                level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                }
            }
        }
        boom(level, pos, power);
        ignite(level, pos, (int) (power * 1.5F));
        blindPlayers(level, pos, 128, 240);
        FxBudget.send(level, ParticleTypes.FLASH, pos, 2, 0, 0, 0, 0);
        FxBudget.send(level, ParticleTypes.LAVA, pos, 80, radius * 1.5, 4.0, radius * 1.5, 0.2);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 4.0F, 1.2F);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.stellar_swarm_beam"), false);
    }

    private static void detonateTransmutation(ServerLevel level, Vec3 pos, float power) {
        boom(level, pos, power * 0.8F);
        int radius = Math.max(8, Math.round(power * 1.2F));
        BlockPos centre = BlockPos.containing(pos.x, pos.y, pos.z);
        for (BlockPos p : BlockPos.betweenClosed(
                centre.offset(-radius, -radius / 3, -radius),
                centre.offset(radius, radius / 3, radius))) {
            double d2 = p.distSqr(centre);
            if (d2 < 16 || d2 > radius * radius || level.getRandom().nextFloat() > 0.20F) {
                continue;
            }
            var state = level.getBlockState(p);
            if (state.is(Blocks.STONE) || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COBBLESTONE)) {
                float roll = level.getRandom().nextFloat();
                if (roll < 0.05F) {
                    level.setBlockAndUpdate(p, Blocks.DIAMOND_BLOCK.defaultBlockState());
                } else if (roll < 0.20F) {
                    level.setBlockAndUpdate(p, Blocks.GOLD_BLOCK.defaultBlockState());
                } else if (roll < 0.50F) {
                    level.setBlockAndUpdate(p, Blocks.REDSTONE_BLOCK.defaultBlockState());
                } else {
                    level.setBlockAndUpdate(p, Blocks.OBSIDIAN.defaultBlockState());
                }
            }
        }
        if (com.shadowclangaming.compat.projecte.ProjectEEmc.isAvailable()) {
            for (BlockPos p : BlockPos.betweenClosed(centre.offset(-16, -8, -16), centre.offset(16, 8, 16))) {
                var be = level.getBlockEntity(p);
                if (be != null && com.shadowclangaming.compat.projecte.ProjectEEmc.isEmcStorage(be)) {
                    com.shadowclangaming.compat.projecte.ProjectEEmc.insertEmc(be, 204800L, null);
                }
            }
        }
        FxBudget.send(level, ParticleTypes.ENCHANT, pos, 120, radius * 0.5, radius * 0.3, radius * 0.5, 0.3);
        FxBudget.send(level, ParticleTypes.WITCH, pos, 60, radius * 0.4, radius * 0.2, radius * 0.4, 0.1);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 2.0F, 1.2F);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.transmutation_nova"), false);
    }

    private static void detonateColossalStar(ServerLevel level, Vec3 pos, float power) {
        boom(level, pos, power * 0.6F);
        boom(level, pos, power);
        int radius = Math.max(12, Math.round(power * 1.4F));
        BlockPos centre = BlockPos.containing(pos.x, pos.y, pos.z);
        for (BlockPos p : BlockPos.betweenClosed(
                centre.offset(-radius, -radius / 3, -radius),
                centre.offset(radius, radius / 3, radius))) {
            double d2 = p.distSqr(centre);
            if (d2 > radius * radius || level.getRandom().nextFloat() > 0.15F) {
                continue;
            }
            var state = level.getBlockState(p);
            if (!state.isAir() && state.getBlock() != Blocks.BEDROCK) {
                level.setBlockAndUpdate(p, Blocks.GLOWSTONE.defaultBlockState());
            }
        }
        blindPlayers(level, pos, (int) (power * 3.0F), 300);
        FxBudget.send(level, ParticleTypes.TOTEM_OF_UNDYING, pos, 150, 8.0, 6.0, 8.0, 0.5);
        FxBudget.send(level, ParticleTypes.FLASH, pos, 4, 0, 0, 0, 0);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 3.0F, 1.0F);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.colossal_star_rupture"), false);
    }

    private static void detonateSteamRupture(ServerLevel level, Vec3 pos, float power) {
        boom(level, pos, power);
        int radius = Math.max(10, Math.round(power * 1.3F));
        BlockPos centre = BlockPos.containing(pos.x, pos.y, pos.z);
        for (BlockPos p : BlockPos.betweenClosed(
                centre.offset(-radius, -radius / 2, -radius),
                centre.offset(radius, radius / 2, radius))) {
            var state = level.getBlockState(p);
            if (state.is(net.minecraft.tags.BlockTags.RAILS)) {
                level.destroyBlock(p, true);
            }
        }
        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, Entity::isAlive)) {
            entity.hurt(level.damageSources().onFire(), power * 1.5F);
            if (entity instanceof Player player) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 240, 0));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2));
            }
        }
        FxBudget.send(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos, 140, radius * 0.6, 3.0, radius * 0.6, 0.05);
        FxBudget.send(level, ParticleTypes.CLOUD, pos, 100, radius * 0.5, 2.5, radius * 0.5, 0.08);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 2.5F, 0.6F);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.steam_rupture_catastrophe"), false);
    }

    private static void detonateVoltmeter(ServerLevel level, Vec3 pos, float power) {
        int radius = Math.max(8, Math.round(power * 1.2F));
        BlockPos centre = BlockPos.containing(pos.x, pos.y, pos.z);
        for (BlockPos p : BlockPos.betweenClosed(
                centre.offset(-radius, -radius / 2, -radius),
                centre.offset(radius, radius / 2, radius))) {
            var state = level.getBlockState(p);
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            if (path.contains("redstone") || path.contains("repeater") || path.contains("comparator")
                    || path.contains("wire") || path.contains("gate") || path.contains("torch")) {
                level.destroyBlock(p, true);
            }
        }
        int bolts = 6;
        for (int i = 0; i < bolts; i++) {
            double angle = (Math.PI * 2.0 / bolts) * i;
            double dist = 2.0 + level.getRandom().nextDouble() * (radius * 0.7);
            BlockPos strike = BlockPos.containing(pos.x + Math.cos(angle) * dist, pos.y, pos.z + Math.sin(angle) * dist);
            var lightning = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
            if (lightning != null) {
                lightning.moveTo(Vec3.atBottomCenterOf(strike));
                level.addFreshEntity(lightning);
            }
        }
        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, Entity::isAlive)) {
            entity.hurt(level.damageSources().lightningBolt(), power * 1.5F);
            if (entity instanceof Player player) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 300, 2));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 2));
            }
        }
        FxBudget.send(level, ParticleTypes.ELECTRIC_SPARK, pos, 120, radius * 0.5, 3.0, radius * 0.5, 0.4);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 2.0F, 1.4F);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.voltmeter_discharge"), false);
    }

    private static void detonateVoodoo(ServerLevel level, Vec3 pos, float power) {
        boom(level, pos, power * 0.5F);
        int radius = Math.max(10, Math.round(power * 1.5F));
        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, Entity::isAlive)) {
            entity.hurt(level.damageSources().magic(), power * 1.2F);
            if (entity instanceof Player player) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 400, 0));
                player.addEffect(new MobEffectInstance(MobEffects.WITHER, 300, 2));
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 400, 3));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 2));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 2));
            }
        }
        BlockPos centre = BlockPos.containing(pos.x, pos.y, pos.z);
        for (BlockPos p : BlockPos.betweenClosed(
                centre.offset(-radius, -radius / 3, -radius),
                centre.offset(radius, radius / 3, radius))) {
            var state = level.getBlockState(p);
            if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN)) {
                level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
        }
        for (int i = 0; i < 4; i++) {
            var phantom = net.minecraft.world.entity.EntityType.PHANTOM.create(level);
            if (phantom != null) {
                phantom.moveTo(pos.x + (level.getRandom().nextDouble() - 0.5) * 6, pos.y + 3, pos.z + (level.getRandom().nextDouble() - 0.5) * 6);
                level.addFreshEntity(phantom);
            }
        }
        FxBudget.send(level, ParticleTypes.SOUL, pos, 80, radius * 0.4, 2.0, radius * 0.4, 0.05);
        FxBudget.send(level, ParticleTypes.SQUID_INK, pos, 80, radius * 0.5, 2.0, radius * 0.5, 0.1);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WITHER_DEATH, SoundSource.BLOCKS, 2.0F, 0.5F);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.voodoo_curse_storm"), false);
    }

    private static void detonateThermalEntropy(ServerLevel level, Vec3 pos, float power) {
        int radius = Math.max(8, Math.round(power * 1.1F));
        BlockPos centre = BlockPos.containing(pos.x, pos.y, pos.z);
        for (BlockPos p : BlockPos.betweenClosed(
                centre.offset(-radius, -radius / 3, -radius),
                centre.offset(radius, radius / 3, radius))) {
            var state = level.getBlockState(p);
            if (state.is(Blocks.WATER)) {
                level.setBlockAndUpdate(p, Blocks.PACKED_ICE.defaultBlockState());
            } else if (state.is(Blocks.LAVA)) {
                level.setBlockAndUpdate(p, Blocks.OBSIDIAN.defaultBlockState());
            }
        }
        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, Entity::isAlive)) {
            entity.setTicksFrozen(300);
            entity.hurt(level.damageSources().freeze(), power * 1.2F);
        }
        boom(level, pos, power);
        ignite(level, pos, (int) (power * 1.2F));
        FxBudget.send(level, ParticleTypes.SNOWFLAKE, pos, 100, radius * 0.5, 3.0, radius * 0.5, 0.1);
        FxBudget.send(level, ParticleTypes.FLAME, pos, 100, radius * 0.5, 3.0, radius * 0.5, 0.2);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2.0F, 0.7F);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.thermal_entropy_burst"), false);
    }

    private static void detonateManaOverload(ServerLevel level, Vec3 pos, float power) {
        boom(level, pos, power * 0.75F);
        int radius = Math.max(8, Math.round(power * 1.2F));
        BlockPos centre = BlockPos.containing(pos.x, pos.y, pos.z);
        for (BlockPos p : BlockPos.betweenClosed(
                centre.offset(-radius, -radius / 3, -radius),
                centre.offset(radius, radius / 3, radius))) {
            var state = level.getBlockState(p);
            if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)) {
                if (level.getRandom().nextFloat() < 0.25F && level.getBlockState(p.above()).isAir()) {
                    level.setBlockAndUpdate(p.above(), Blocks.POPPY.defaultBlockState());
                }
            }
        }
        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, Entity::isAlive)) {
            if (entity instanceof Player player) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 1));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1));
            } else {
                entity.hurt(level.damageSources().magic(), power * 2.0F);
            }
        }
        FxBudget.send(level, ParticleTypes.END_ROD, pos, 100, radius * 0.5, 3.0, radius * 0.5, 0.15);
        FxBudget.send(level, ParticleTypes.GLOW, pos, 80, radius * 0.4, 2.5, radius * 0.4, 0.1);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 1.2F);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.mana_overload_burst"), false);
    }

    private static void detonateDraconicReactor(ServerLevel level, Vec3 pos, float power) {
        boom(level, pos, power * 0.5F);
        boom(level, pos, power);
        int radius = Math.max(14, Math.round(power * 1.6F));
        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, Entity::isAlive)) {
            Vec3 away = entity.position().subtract(pos).normalize();
            entity.push(away.x * 2.5, 2.0, away.z * 2.5);
            entity.hurt(level.damageSources().dragonBreath(), power * 2.5F);
        }
        FxBudget.send(level, ParticleTypes.DRAGON_BREATH, pos, 150, radius * 0.5, 4.0, radius * 0.5, 0.2);
        FxBudget.send(level, ParticleTypes.EXPLOSION_EMITTER, pos, 20, 6.0, 4.0, 6.0, 0.0);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.BLOCKS, 3.0F, 0.8F);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.draconic_reactor_meltdown"), false);
    }

    private static void detonateSanguine(ServerLevel level, Vec3 pos, float power) {
        boom(level, pos, power * 0.6F);
        int radius = Math.max(8, Math.round(power * 1.2F));
        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, Entity::isAlive)) {
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                float siphon = Math.max(4.0F, living.getHealth() * 0.5F);
                living.hurt(level.damageSources().magic(), siphon);
                living.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
            }
        }
        FxBudget.send(level, ParticleTypes.CRIMSON_SPORE, pos, 120, radius * 0.5, 2.5, radius * 0.5, 0.2);
        FxBudget.send(level, ParticleTypes.DAMAGE_INDICATOR, pos, 40, radius * 0.3, 1.5, radius * 0.3, 0.1);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.RAVAGER_ROAR, SoundSource.BLOCKS, 2.0F, 0.7F);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.sanguine_sacrifice"), false);
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
