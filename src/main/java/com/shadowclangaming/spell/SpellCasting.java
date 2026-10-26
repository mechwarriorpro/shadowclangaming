package com.shadowclangaming.spell;

import com.shadowclangaming.entity.SpellProjectileEntity;

import com.shadowclangaming.compat.sable.SableRagdolls;
import com.shadowclangaming.magic.ManaApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Turns a {@link Spell} into things happening in the world.
 *
 * The shape decides where the components land; the components themselves do not care how they got
 * there. That split is what lets the same spell be fired from a rifle, thrown by a spell warhead, or
 * applied by a command with no caster at all.
 *
 * Every entry point is server-side only.
 */
public final class SpellCasting {

    /** Multiplier applied when a spell warhead casts its spell at the impact point. */
    public static final float WARHEAD_SCALE = 3.0F;

    // ------------------------------------------------------------------ entry points

    /** Fire a spell from a rifle at its baseline power. */
    public static boolean castFromRifle(ServerPlayer caster, Spell spell) {
        return castFromRifle(caster, spell, 1.0F);
    }

    /**
     * Fire a spell from a rifle.
     *
     * @param caster the shooter - used for aiming, as the damage source, and for life tap/blink
     * @param power  the emitter assembly's multiplier: 1.0 for a single emitter, 11.9 for a 3x3x3 cube.
     * @return true if the spell was actually cast (false = nothing to cast)
     */
    public static boolean castFromRifle(ServerPlayer caster, Spell spell, float power) {
        if (spell.isEmpty()) {
            return false;
        }
        ServerLevel level = caster.serverLevel();
        return run(level, caster, null, caster.getEyePosition(), spell, 0, power,
                caster.getLookAngle());
    }

    /**
     * Cast a spell at a point in the world with no caster - this is what the spell warhead does.
     * The whole sequence runs from the impact point, so a warhead with a zone step really does leave a
     * burning area behind.
     */
    public static void castAtPoint(ServerLevel level, Spell spell, Vec3 point, float scale) {
        if (spell.isEmpty()) {
            return;
        }
        run(level, null, null, point, spell, 0, scale, new Vec3(0.0D, -1.0D, 0.0D));
    }

    /**
     * ──────────────────── THE INTERPRETER ────────────────────
     *
     * Runs a spell from step {@code fromStep}, starting at {@code point}. Each step resolves and then
     * either continues to the next one (self/touch/beam/aura), hands the rest of the spell to a
     * projectile, or leaves the rest of the spell parked in a zone or a rune.
     *
     * @param caster  may be null (a warhead's spell has no shooter)
     * @param target  what the previous step hit, if anything
     * @param point   where the spell currently is
     * @param look    the direction an aimed step should use
     * @return true if anything actually happened
     */
    public static boolean run(ServerLevel level, LivingEntity caster, LivingEntity target, Vec3 point,
                              Spell spell, int fromStep, float power, Vec3 look) {
        if (spell.isEmpty()) {
            return false;
        }
        List<Spell.Step> steps = spell.steps();
        boolean didSomething = false;
        boolean bloodPaid = false;

        for (int index = Math.max(0, fromStep); index < steps.size(); index++) {
            final Spell.Step step = steps.get(index);
            final SpellShape shape = step.shape();
            final List<SpellComponent> components = step.components();
            final int modifiers = step.modifiers();
            final boolean silent = SpellModifier.SILENT.in(modifiers);
            final int repeats = SpellModifier.repeats(modifiers);
            final double areaScale = SpellModifier.areaMultiplier(modifiers);
            final double durationScale = SpellModifier.durationMultiplier(modifiers);

            // ECHO: a half-power copy of this step, two seconds from now. The copy has ECHO itself
            // cleared, so it cannot echo forever - one repeat, not an infinite regress.
            if (SpellModifier.ECHO.in(modifiers)) {
                Spell echoed = withoutEcho(spell, index);
                if (echoed != null) {
                    SpellDelays.schedule(level, caster, point, echoed, index, power * 0.5F, look,
                            power * 0.5F, 40);
                }
            }

            // BLOOD PRICE is paid once per cast, not once per step and not once per repeat.
            if (!bloodPaid && SpellModifier.bloodPrice(modifiers) && caster != null) {
                bloodPaid = true;
                caster.hurt(level.damageSources().magic(), caster.getMaxHealth() * 0.25F);
            }

            // NOVA detonates where the step resolves. A projectile step defers its own detonation to
            // the impact (see SpellProjectileEntity); every other shape pops here, once.
            if (SpellModifier.NOVA.in(modifiers) && shape != SpellShape.PROJECTILE) {
                detonate(level, caster, point, power);
            }

            // DELAY and CHANT hand this step (and everything after it) to the delay queue, which
            // fires it a couple of seconds later. That is the whole point of a delayed spell: it is
            // cast now and lands when you want it to.
            int delay = SpellModifier.delayTicks(modifiers);
            if (delay > 0) {
                SpellDelays.schedule(level, caster, point, spell, index, power, look,
                        (float) (power), delay);
                return true;
            }

            // TWINNED / TRIPLET resolve the whole step two or three times. Every shape below is
            // idempotent enough for that: two bolts really do fly, two zones really do appear, two
            // touches really do land.
            //
            // Repeats FAN OUT rather than stacking on one point. Three bolts fired down the same line
            // would all land in the same tick, and vanilla invulnerability means only the first would
            // be felt - so a Triplet volley is aimed as a spread, and three zones are parked a little
            // apart. That is also what makes it worth 180 %: it hits three places, not one place thrice.
            boolean handedOff = false;
            for (int repeat = 0; repeat < repeats; repeat++) {
            final double lane = repeat - (repeats - 1) / 2.0D;
            switch (shape) {
                case SELF -> {
                    LivingEntity self = caster != null ? caster : target;
                    if (self != null) {
                        applyAll(level, caster, self, self.position(), components, power, modifiers);
                        didSomething = true;
                    }
                }
                case TOUCH, BEAM -> {
                    Hit hit = trace(level, caster, point, look, shape.range());
                    if (hit.entity() != null) {
                        applyAll(level, caster, hit.entity(), hit.point(), components, power, modifiers);
                        target = hit.entity();
                    } else {
                        applyAll(level, caster, null, hit.point(), components, power, modifiers);
                    }
                    point = hit.point();
                    didSomething = true;
                    if (!silent) {
                        beamFx(level, caster != null ? caster.getEyePosition() : point, point);
                        level.playSound(null, BlockPos.containing(point), SoundEvents.WITCH_DRINK,
                                SoundSource.PLAYERS, 0.7F, 1.2F);
                    }
                }
                case AURA -> {
                    double radius = shape.range() * areaScale;
                    List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                            new AABB(point, point).inflate(radius),
                            entity -> entity != caster && entity.isAlive());
                    if (!hasHarmful(components) && caster != null) {
                        applyAll(level, caster, caster, caster.position(), components, power, modifiers);
                    }
                    for (LivingEntity entity : nearby) {
                        applyAll(level, caster, entity, entity.position(), components, power, modifiers);
                    }
                    // Block-facing effects (ignite, light, dig) still need a place to land.
                    applyAll(level, caster, null, point, components, power, modifiers);
                    if (!silent) {
                        level.sendParticles(ParticleTypes.ENCHANT, point.x, point.y + 1.0D, point.z,
                                60, radius * 0.5D, 0.8D, radius * 0.5D, 0.05D);
                    }
                    didSomething = true;
                }
                case PROJECTILE -> {
                    // The bolt carries the REST of the spell to whatever it hits.
                    //
                    // A volley CONVERGES: every bolt is launched from a point fanned sideways but is
                    // aimed at one shared point down the caster's sightline, so the shots look like a
                    // closing fan rather than a shotgun. Two bolts that hit different things still
                    // cover two places; two bolts that hit the same thing arrive together.
                    Vec3 aimAt = convergePoint(level, caster, target, point, look, shape.range());
                    Vec3 from = lane == 0.0D
                            ? point
                            : sideOf(sideOf(point, look, lane * 0.85D), new Vec3(0.0D, 1.0D, 0.0D),
                                    lane * 0.25D);
                    Vec3 aim = aimAt.subtract(from);
                    SpellProjectileEntity.launch(level, caster, spell, from,
                            aim.lengthSqr() < 1.0E-4D ? look : aim,
                            power, index + 1, components, modifiers,
                            // SWIFT doubles the flight speed, which is most of what makes a bolt dodgeable.
                            SpellModifier.SWIFT.in(modifiers) ? 3.2D : 1.6D,
                            SpellModifier.SEEKER.in(modifiers)
                                    ? nearestTargetId(level, caster, aimAt, SEEK_RADIUS) : -1);
                    handedOff = true;
                }
                case ZONE -> {
                    // Park the rest of the spell in a 30-second zone that pulses every 5 seconds.
                    double zoneRadius = shape.range() * areaScale;
                    Vec3 centre = lane == 0.0D ? point : sideOf(point, look, lane * 1.5D);
                    SpellZones.addZone(level, centre, spell, index + 1, power,
                            caster == null ? null : caster.getUUID(), zoneRadius, durationScale);
                    if (!silent) {
                        level.sendParticles(ParticleTypes.ENCHANT, centre.x, centre.y + 0.5D, centre.z,
                                80, zoneRadius * 0.4D, 0.6D, zoneRadius * 0.4D, 0.08D);
                        level.playSound(null, BlockPos.containing(centre), SoundEvents.ENCHANTMENT_TABLE_USE,
                                SoundSource.PLAYERS, 1.0F, 0.8F);
                    }
                    handedOff = true;
                }
                case RUNE -> {
                    Vec3 mark = lane == 0.0D ? point : sideOf(point, look, lane * 1.5D);
                    SpellZones.addRune(level, mark, spell, index + 1, power,
                            caster == null ? null : caster.getUUID(), durationScale);
                    if (!silent) {
                        level.sendParticles(ParticleTypes.WITCH, mark.x, mark.y + 0.2D, mark.z,
                                30, 0.5D, 0.2D, 0.5D, 0.02D);
                    }
                    handedOff = true;
                }
            }
            if (handedOff) {
                return true;
            }
            }
        }
        return didSomething;
    }

    // ------------------------------------------------------------------ delivery helpers

    /** What a ray hit: an entity (point = its chest) or a block (point = the hit face). */
    public record Hit(LivingEntity entity, Vec3 point) {
    }

    @org.jetbrains.annotations.Nullable
    private static Hit trace(ServerLevel level, @org.jetbrains.annotations.Nullable LivingEntity caster,
                             Vec3 from, Vec3 look, double range) {
        Vec3 to = from.add(look.scale(range));

        // Entities first: a beam should hit the zombie in front of the wall, not the wall.
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(from, to).inflate(1.0D),
                entity -> entity != caster && entity.isAlive() && entity.isPickable());
        LivingEntity bestEntity = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            var clip = candidate.getBoundingBox().inflate(0.3D).clip(from, to);
            if (clip.isPresent()) {
                double distance = from.distanceToSqr(clip.get());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestEntity = candidate;
                }
            }
        }

        net.minecraft.world.phys.shapes.CollisionContext collision =
                caster == null
                        ? net.minecraft.world.phys.shapes.CollisionContext.empty()
                        : net.minecraft.world.phys.shapes.CollisionContext.of(caster);
        BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, collision));
        double blockDistance = blockHit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE
                : from.distanceToSqr(blockHit.getLocation());

        if (bestEntity != null && bestDistance <= blockDistance) {
            return new Hit(bestEntity, bestEntity.getBoundingBox().getCenter());
        }
        Vec3 point = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();
        return new Hit(null, point);
    }

    private static void beamFx(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        int steps = (int) Math.min(120, delta.length() * 2.0D);
        for (int i = 0; i <= steps; i++) {
            Vec3 at = from.add(delta.scale(steps == 0 ? 0.0D : (double) i / steps));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static boolean hasHarmful(List<SpellComponent> components) {
        for (SpellComponent component : components) {
            if (component.category() == SpellComponent.Category.DAMAGE
                    || component.category() == SpellComponent.Category.DEBUFF) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ component dispatch

    /** Applies a step's components in order. Target and caster may both be null (bare ground). */
    public static void applyAll(ServerLevel level, LivingEntity caster, LivingEntity target, Vec3 point,
                                List<SpellComponent> components, float scale) {
        applyAll(level, caster, target, point, components, scale, 0);
    }

    /**
     * The real entry point: applies a step's components with its MODIFIERS in force.
     *
     * Modifiers change the numbers, not the list: EMPOWERED multiplies damage and healing by 1.5,
     * PIERCING strips armour and the damage cooldown, VAMPIRIC feeds the caster. That keeps every
     * component above ignorant of modifiers, which is the only way a list this long stays maintainable.
     */
    public static void applyAll(ServerLevel level, LivingEntity caster, LivingEntity target, Vec3 point,
                                List<SpellComponent> components, float scale, int modifiers) {
        float boosted = (float) (scale * SpellModifier.powerMultiplier(modifiers));
        // DISPEL strips the victim's protection BEFORE anything lands, which is the whole point of it.
        if (target != null && SpellModifier.DISPEL.in(modifiers)) {
            stripBuffs(target);
        }
        for (SpellComponent component : components) {
            apply(level, caster, target, point, component, boosted, modifiers);
        }
        if (target != null) {
            applyVictimCurse(level, target, modifiers);
        }
    }

    /** DISPEL: remove every beneficial effect. Copies first - removing while iterating is a crash. */
    private static void stripBuffs(LivingEntity target) {
        List<MobEffectInstance> active = new java.util.ArrayList<>(target.getActiveEffects());
        for (MobEffectInstance instance : active) {
            if (instance.getEffect().value().getCategory()
                    == net.minecraft.world.effect.MobEffectCategory.BENEFICIAL) {
                target.removeEffect(instance.getEffect());
            }
        }
    }

    /** The per-victim curses: CRIPPLE, HEX and BIND. */
    private static void applyVictimCurse(ServerLevel level, LivingEntity target, int modifiers) {
        if (SpellModifier.CRIPPLE.in(modifiers)) {
            effect(target, MobEffects.WEAKNESS, 160, 1);
            effect(target, MobEffects.MOVEMENT_SLOWDOWN, 160, 1);
            effect(target, MobEffects.DIG_SLOWDOWN, 160, 1);
        }
        if (SpellModifier.BIND.in(modifiers)) {
            // Slowness 6 is close enough to rooted that you cannot walk out of it; jump boost 128 is
            // the vanilla trick for "cannot jump at all".
            effect(target, MobEffects.MOVEMENT_SLOWDOWN, 60, 6);
            effect(target, MobEffects.JUMP, 60, 128);
            target.setDeltaMovement(0.0D, target.getDeltaMovement().y, 0.0D);
            target.hurtMarked = true;
        }
        if (SpellModifier.HEX.in(modifiers)) {
            switch (target.getRandom().nextInt(5)) {
                case 0 -> effect(target, MobEffects.WITHER, 200, 1);
                case 1 -> effect(target, MobEffects.POISON, 200, 2);
                case 2 -> effect(target, MobEffects.BLINDNESS, 200, 0);
                case 3 -> effect(target, MobEffects.CONFUSION, 300, 0);
                default -> effect(target, MobEffects.LEVITATION, 100, 1);
            }
        }
    }

    /** Convenience for a whole spell (every step's components), used by the warhead's area pass. */
    public static void applyAll(ServerLevel level, LivingEntity caster, LivingEntity target, Vec3 point,
                                Spell spell, float scale) {
        int modifiers = 0;
        for (Spell.Step step : spell.steps()) {
            modifiers |= step.modifiers();
        }
        applyAll(level, caster, target, point, spell.components(), scale, modifiers);
    }

    private static void apply(ServerLevel level, LivingEntity caster, LivingEntity target, Vec3 point,
                              SpellComponent component, float scale, int modifiers) {
        switch (component) {
            // ---- damage ----
            case PHYSICAL_DAMAGE -> hurt(level, target, level.damageSources().magic(), 4.0F * scale);
            case FIRE_DAMAGE -> {
                hurt(level, target, level.damageSources().onFire(), 3.0F * scale);
                if (target != null) {
                    target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(),
                            (int) (100 * scale)));
                }
            }
            case FROST_DAMAGE -> {
                hurt(level, target, level.damageSources().magic(), 3.0F * scale);
                if (target != null) {
                    target.setTicksFrozen(target.getTicksRequiredToFreeze() + (int) (140 * scale));
                }
            }
            case LIGHTNING_DAMAGE -> {
                hurt(level, target, level.damageSources().lightningBolt(), 6.0F * scale);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z,
                        20, 0.6D, 0.6D, 0.6D, 0.2D);
            }
            case MAGIC_DAMAGE -> hurt(level, target, magicSource(level, caster), 8.0F * scale);
            case ANNIHILATE -> hurt(level, target, magicSource(level, caster), 20.0F * scale);
            case LIFESTEAL -> {
                if (target != null) {
                    target.hurt(magicSource(level, caster), 4.0F * scale);
                    if (caster != null) {
                        caster.heal(2.0F * scale);
                    }
                }
            }
            case LIGHTNING_STRIKE -> strike(level, point);
            // Random Teleport: shove the victim up to 16 blocks in a random direction, but only into a
            // place a body actually fits. See randomTeleport() for the search.
            case RANDOM_TELEPORT -> {
                randomTeleport(level, target);
                if (target != null) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1.0D,
                            target.getZ(), 40, 0.3D, 0.7D, 0.3D, 0.15D);
                }
            }
            // Chronosplit: fifteen seconds of nothing at all.
            case CHRONOSPLIT -> Chronosplit.freeze(target);
            // Cluster Strike is a property of a projectile, not an effect on a body: the bolt does the
            // splitting (see SpellProjectileEntity#impact). Landing it on a target directly does nothing.
            case CLUSTER_STRIKE -> {
            }

            // ---- healing and buffs ----
            case HEAL -> {
                if (target != null) {
                    target.heal(4.0F * scale);
                }
            }
            case REGENERATION -> effect(target, MobEffects.REGENERATION, 200, 1);
            case ABSORPTION -> effect(target, MobEffects.ABSORPTION, 1200, 1);
            case RESISTANCE -> effect(target, MobEffects.DAMAGE_RESISTANCE, 600, 1);
            case STRENGTH -> effect(target, MobEffects.DAMAGE_BOOST, 600, 1);
            case SPEED -> effect(target, MobEffects.MOVEMENT_SPEED, 600, 1);
            case HASTE -> effect(target, MobEffects.DIG_SPEED, 600, 1);
            case JUMP_BOOST -> effect(target, MobEffects.JUMP, 600, 1);
            case SLOW_FALL -> effect(target, MobEffects.SLOW_FALLING, 600, 0);
            case NIGHT_VISION -> effect(target, MobEffects.NIGHT_VISION, 1200, 0);
            case WATER_BREATHING -> effect(target, MobEffects.WATER_BREATHING, 1200, 0);
            case INVISIBILITY -> effect(target, MobEffects.INVISIBILITY, 400, 0);

            // ---- debuffs ----
            case SLOWNESS -> effect(target, MobEffects.MOVEMENT_SLOWDOWN, 300, 1);
            case WEAKNESS -> effect(target, MobEffects.WEAKNESS, 300, 1);
            case POISON -> effect(target, MobEffects.POISON, 200, 1);
            case WITHER -> effect(target, MobEffects.WITHER, 200, 1);
            case BLINDNESS -> effect(target, MobEffects.BLINDNESS, 200, 0);
            case NAUSEA -> effect(target, MobEffects.CONFUSION, 300, 0);
            case HUNGER -> effect(target, MobEffects.HUNGER, 400, 1);
            case DARKNESS -> effect(target, MobEffects.DARKNESS, 200, 0);
            case LEVITATION -> effect(target, MobEffects.LEVITATION, 200, 1);

            // ---- movement ----
            case KNOCKBACK -> push(target, point, 1.2D * scale);
            case PULL -> push(target, point, -1.0D * scale);
            case BLINK -> {
                if (caster instanceof ServerPlayer player) {
                    player.teleportTo(point.x, point.y, point.z);
                    level.sendParticles(ParticleTypes.PORTAL, point.x, point.y, point.z,
                            40, 0.4D, 0.6D, 0.4D, 0.2D);
                }
            }

            // ---- world ----
            case IGNITE -> igniteAround(level, point, (int) Math.max(1, 2 * scale));
            case EXTINGUISH -> extinguishAround(level, point, (int) Math.max(1, 2 * scale));
            case PLACE_LIGHT -> placeLight(level, point);
            case DIG -> dig(level, point);
            case DISPEL -> {
                if (target != null) {
                    target.removeAllEffects();
                }
            }
            case LIFE_TAP -> {
                if (caster instanceof ServerPlayer player) {
                    player.hurt(level.damageSources().magic(), 2.0F);
                    ManaApi.addMana(player, 100L);
                }
            }

            // ==================================================================================
            // THE SECOND SCHOOL
            // ==================================================================================

            // ---- tier 1 cantrips ----
            case SPARK -> {
                hurt(level, caster, target, level.damageSources().magic(), 2.0F * scale, modifiers);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y + 0.8D, point.z,
                        12, 0.3D, 0.4D, 0.3D, 0.1D);
            }
            case CHILL -> {
                hurt(level, caster, target, level.damageSources().magic(), 1.5F * scale, modifiers);
                effect(target, MobEffects.MOVEMENT_SLOWDOWN, 80, 1);
                if (target != null) {
                    target.setTicksFrozen(target.getTicksRequiredToFreeze() + (int) (60 * scale));
                }
            }
            case FEATHER -> effect(target, MobEffects.SLOW_FALLING, 600, 0);
            case SATIATE -> {
                if (target instanceof net.minecraft.world.entity.player.Player player) {
                    player.getFoodData().eat(8, 0.6F);
                } else {
                    effect(target, MobEffects.SATURATION, 40, 0);
                }
            }

            // ---- tier 2 ----
            // Entangle: roots. Slowness V plus momentum zeroed on the spot - a rooted thing can still
            // fight, it just cannot leave.
            case ENTANGLE -> {
                effect(target, MobEffects.MOVEMENT_SLOWDOWN, 120, 4);
                effect(target, MobEffects.DIG_SLOWDOWN, 120, 2);
                if (target != null) {
                    target.setDeltaMovement(Vec3.ZERO);
                    target.hurtMarked = true;
                }
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, point.x, point.y + 0.3D, point.z,
                        20, 0.8D, 0.2D, 0.8D, 0.02D);
            }
            case SEARING_LIGHT -> {
                hurt(level, caster, target, level.damageSources().magic(), 3.0F * scale, modifiers);
                effect(target, MobEffects.BLINDNESS, 80, 0);
                level.sendParticles(ParticleTypes.END_ROD, point.x, point.y + 1.0D, point.z,
                        24, 0.4D, 0.5D, 0.4D, 0.05D);
            }
            case MAGIC_SHIELD -> {
                effect(target, MobEffects.ABSORPTION, 1200, 2);
                effect(target, MobEffects.DAMAGE_RESISTANCE, 600, 1);
            }
            case MIND_FOG -> {
                effect(target, MobEffects.CONFUSION, 300, 0);
                effect(target, MobEffects.DARKNESS, 200, 0);
            }
            // Silence: the victim cannot use items for five seconds (the same lock Chronosplit uses).
            case SILENCE -> {
                if (target != null) {
                    target.addEffect(new MobEffectInstance(
                            com.shadowclangaming.registry.SCGMobEffects.CHRONOSPLIT, 100, 0, false, false, true));
                    level.sendParticles(ParticleTypes.SCULK_SOUL, point.x, point.y + 1.0D, point.z,
                            18, 0.3D, 0.5D, 0.3D, 0.01D);
                }
            }

            // ---- tier 3 ----
            case FROST_NOVA -> {
                double radius = 4.0D * SpellModifier.areaMultiplier(modifiers);
                for (LivingEntity victim : area(level, point, radius, caster)) {
                    hurt(level, caster, victim, level.damageSources().magic(), 4.0F * scale, modifiers);
                    victim.setTicksFrozen(victim.getTicksRequiredToFreeze() + 200);
                    effect(victim, MobEffects.MOVEMENT_SLOWDOWN, 100, 2);
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, point.x, point.y + 0.5D, point.z,
                        80, radius * 0.5D, 0.5D, radius * 0.5D, 0.05D);
            }
            case NECROTIC_DRAIN -> {
                hurt(level, caster, target, magicSource(level, caster), 5.0F * scale, modifiers);
                effect(target, MobEffects.WITHER, 120, 0);
                if (caster != null) {
                    caster.heal(2.0F * scale);
                }
            }
            case DISARM -> {
                if (target instanceof LivingEntity living) {
                    ItemStack held = living.getMainHandItem();
                    if (!held.isEmpty()) {
                        ItemStack dropped = held.copy();
                        living.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        net.minecraft.world.entity.item.ItemEntity item =
                                new net.minecraft.world.entity.item.ItemEntity(level,
                                        living.getX(), living.getY() + 1.0D, living.getZ(), dropped);
                        item.setPickUpDelay(40);
                        level.addFreshEntity(item);
                        level.playSound(null, living.blockPosition(), SoundEvents.ITEM_BREAK,
                                SoundSource.PLAYERS, 1.0F, 1.2F);
                    }
                }
            }
            case ARCANE_WARD -> {
                if (target != null) {
                    target.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.MILK);
                }
                effect(target, MobEffects.DAMAGE_RESISTANCE, 300, 1);
                effect(target, MobEffects.FIRE_RESISTANCE, 600, 0);
            }
            // Warp Step: a blink with real range. The caster steps along their own line of sight, and
            // only into a spot they could actually stand in - the same safe-landing search the random
            // teleport uses, so this can never post you into a wall.
            case WARP_STEP -> {
                if (caster instanceof ServerPlayer player) {
                    Vec3 endpoint = player.getEyePosition().add(player.getLookAngle().scale(12.0D));
                    BlockPos landing = safeLanding(level, endpoint);
                    if (landing != null) {
                        Vec3 destination = Vec3.atBottomCenterOf(landing);
                        player.teleportTo(level, destination.x, destination.y, destination.z,
                                player.getYRot(), player.getXRot());
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, destination.x,
                                destination.y + 1.0D, destination.z, 30, 0.3D, 0.6D, 0.3D, 0.2D);
                        level.playSound(null, landing, SoundEvents.ENDERMAN_TELEPORT,
                                SoundSource.PLAYERS, 0.8F, 1.4F);
                    }
                }
            }
            // Blood Magic, imported: two hearts for 400 mana. Self only, by construction.
            case BLOOD_SACRIFICE -> {
                if (caster instanceof ServerPlayer player) {
                    player.hurt(level.damageSources().magic(), 4.0F);
                    ManaApi.addMana(player, 400L);
                    level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, point.x, point.y + 1.0D, point.z,
                            12, 0.3D, 0.5D, 0.3D, 0.1D);
                }
            }
            // Detonate: a small clean explosion. No block damage, no fire - a spell, not a grenade.
            case DETONATE -> {
                level.explode(caster, point.x, point.y, point.z, (float) (2.5D * scale),
                        net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            }

            // ---- tier 4 ----
            // Starstrike: the Ars Magica classic. Something heavy arrives.
            case STARSTRIKE -> {
                hurt(level, caster, target, magicSource(level, caster), 12.0F * scale, modifiers);
                strike(level, point);
                igniteAround(level, point, (int) (2 * SpellModifier.areaMultiplier(modifiers)));
                level.sendParticles(ParticleTypes.FLASH, point.x, point.y + 1.0D, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                level.playSound(null, BlockPos.containing(point), SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.PLAYERS, 0.7F, 1.3F);
            }
            case HOLY_SMITE -> {
                float amount = 10.0F * scale;
                if (target != null && target.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
                    amount *= 2.0F;
                }
                hurt(level, caster, target, magicSource(level, caster), amount, modifiers);
                effect(target, MobEffects.GLOWING, 200, 0);
                level.sendParticles(ParticleTypes.END_ROD, point.x, point.y + 1.2D, point.z,
                        30, 0.5D, 0.8D, 0.5D, 0.02D);
            }
            // Blood Lance: the worse the caster's state, the harder it hits - and it costs a heart.
            case BLOOD_LANCE -> {
                float missing = caster == null ? 0.0F
                        : (caster.getMaxHealth() - caster.getHealth()) / 4.0F;
                float amount = (4.0F + missing) * scale;
                hurt(level, caster, target, magicSource(level, caster), amount, modifiers);
                if (caster instanceof ServerPlayer player) {
                    player.hurt(level.damageSources().magic(), 2.0F);
                }
                level.sendParticles(ParticleTypes.DUST_PLUME, point.x, point.y + 1.0D, point.z,
                        20, 0.4D, 0.5D, 0.4D, 0.02D);
            }
            case CHAIN_LIGHTNING -> {
                if (target != null) {
                    double range = 8.0D * SpellModifier.areaMultiplier(modifiers);
                    hurt(level, caster, target, level.damageSources().lightningBolt(), 6.0F * scale, modifiers);
                    Vec3 from = target.position();
                    int jumps = 0;
                    for (LivingEntity victim : area(level, from, range, caster)) {
                        if (victim == target || jumps >= 4) {
                            continue;
                        }
                        hurt(level, caster, victim, level.damageSources().lightningBolt(),
                                4.0F * scale, modifiers);
                        beamFx(level, from, victim.position());
                        jumps++;
                    }
                } else {
                    strike(level, point);
                }
            }
            case GRAVITY_FLIP -> {
                double radius = 6.0D * SpellModifier.areaMultiplier(modifiers);
                for (LivingEntity victim : area(level, point, radius, caster)) {
                    victim.setDeltaMovement(victim.getDeltaMovement().add(0.0D, 1.2D, 0.0D));
                    victim.hurtMarked = true;
                }
                level.sendParticles(ParticleTypes.PORTAL, point.x, point.y + 1.0D, point.z,
                        60, radius * 0.4D, 0.6D, radius * 0.4D, 0.3D);
            }
            case TIME_SLOW -> {
                double radius = 6.0D * SpellModifier.areaMultiplier(modifiers);
                for (LivingEntity victim : area(level, point, radius, caster)) {
                    effect(victim, MobEffects.MOVEMENT_SLOWDOWN, 160, 3);
                    effect(victim, MobEffects.DIG_SLOWDOWN, 160, 2);
                }
                level.sendParticles(ParticleTypes.CRIMSON_SPORE, point.x, point.y + 1.0D, point.z,
                        50, radius * 0.4D, 0.6D, radius * 0.4D, 0.01D);
            }
            case FLIGHT -> {
                effect(target, MobEffects.LEVITATION, 120, 1);
                effect(target, MobEffects.SLOW_FALLING, 400, 0);
            }
            case SANCTUARY -> {
                double radius = 6.0D * SpellModifier.areaMultiplier(modifiers);
                for (LivingEntity victim : area(level, point, radius, caster)) {
                    victim.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.MILK);
                    victim.heal(6.0F * scale);
                    victim.setRemainingFireTicks(0);
                }
                if (caster != null) {
                    caster.heal(6.0F * scale);
                }
                level.sendParticles(ParticleTypes.HEART, point.x, point.y + 1.0D, point.z,
                        30, radius * 0.3D, 0.5D, radius * 0.3D, 0.02D);
            }
            case PLAGUE_BEARER -> {
                hurt(level, caster, target, magicSource(level, caster), 3.0F * scale, modifiers);
                double radius = 5.0D * SpellModifier.areaMultiplier(modifiers);
                for (LivingEntity victim : area(level, point, radius, caster)) {
                    effect(victim, MobEffects.POISON, 200, 1);
                    effect(victim, MobEffects.HUNGER, 300, 2);
                    effect(victim, MobEffects.WITHER, 120, 0);
                }
                level.sendParticles(ParticleTypes.ITEM_SLIME, point.x, point.y + 0.6D, point.z,
                        40, radius * 0.35D, 0.4D, radius * 0.35D, 0.02D);
            }

            // ---- tier 5 ----
            case STORM_CALL -> {
                double radius = 8.0D * SpellModifier.areaMultiplier(modifiers);
                for (int bolt = 0; bolt < 6; bolt++) {
                    double angle = level.random.nextDouble() * Math.PI * 2.0D;
                    double distance = level.random.nextDouble() * radius;
                    BlockPos landing = BlockPos.containing(point.x + Math.cos(angle) * distance,
                            point.y, point.z + Math.sin(angle) * distance);
                    strike(level, Vec3.atBottomCenterOf(landing));
                }
            }
            case THAUMIC_RIFT -> {
                double radius = 7.0D * SpellModifier.areaMultiplier(modifiers);
                for (LivingEntity victim : area(level, point, radius, caster)) {
                    Vec3 towards = point.subtract(victim.position());
                    if (towards.lengthSqr() > 0.5D) {
                        victim.setDeltaMovement(victim.getDeltaMovement().add(towards.normalize().scale(0.5D)));
                        victim.hurtMarked = true;
                    }
                    effect(victim, MobEffects.WITHER, 200, 1);
                    hurt(level, caster, victim, magicSource(level, caster), 3.0F * scale, modifiers);
                }
                level.sendParticles(ParticleTypes.SOUL, point.x, point.y + 1.0D, point.z,
                        70, radius * 0.4D, 0.8D, radius * 0.4D, 0.02D);
            }
            case SOUL_HARVEST -> {
                if (target != null) {
                    float before = target.getHealth();
                    hurt(level, caster, target, magicSource(level, caster), 8.0F * scale, modifiers);
                    boolean killed = !target.isAlive();
                    if (caster != null) {
                        caster.heal(killed ? 6.0F : 3.0F);
                    }
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y + 1.0D, point.z,
                            24, 0.4D, 0.6D, 0.4D, 0.03D);
                }
            }
            case BANISH -> {
                if (target != null && !(target instanceof net.minecraft.world.entity.player.Player)) {
                    randomTeleport(level, target, 64.0D);
                    effect(target, MobEffects.BLINDNESS, 100, 0);
                    effect(target, MobEffects.CONFUSION, 200, 1);
                }
            }

            // ==================================================================================
            // OVERLORD
            // ==================================================================================
            case MAGIC_ARROW -> {
                hurt(level, caster, target, magicSource(level, caster), 3.5F * scale, modifiers);
                level.sendParticles(ParticleTypes.CRIT, point.x, point.y + 0.9D, point.z,
                        10, 0.2D, 0.3D, 0.2D, 0.1D);
            }
            case FLASH -> {
                effect(target, MobEffects.BLINDNESS, 80, 0);
                level.sendParticles(ParticleTypes.FLASH, point.x, point.y + 1.0D, point.z,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            case HELL_FLAME -> {
                hurt(level, caster, target, level.damageSources().onFire(), 7.0F * scale, modifiers);
                if (target != null) {
                    // Hell Flame burns through fire resistance: the burn is applied as a direct
                    // fire tick, not as a fire source the target can be immune to.
                    target.setRemainingFireTicks(400);
                }
                igniteAround(level, point, (int) (2 * SpellModifier.areaMultiplier(modifiers)));
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y + 0.8D, point.z,
                        40, 0.6D, 0.6D, 0.6D, 0.05D);
            }
            case CRY_OF_THE_BANSHEE -> {
                double radius = 10.0D * SpellModifier.areaMultiplier(modifiers);
                for (LivingEntity victim : area(level, point, radius, caster)) {
                    effect(victim, MobEffects.DARKNESS, 300, 0);
                    effect(victim, MobEffects.WEAKNESS, 300, 2);
                    effect(victim, MobEffects.MOVEMENT_SLOWDOWN, 200, 1);
                    hurt(level, caster, victim, magicSource(level, caster), 3.0F * scale, modifiers);
                }
                level.sendParticles(ParticleTypes.SCULK_SOUL, point.x, point.y + 1.0D, point.z,
                        80, radius * 0.4D, 0.8D, radius * 0.4D, 0.02D);
                level.playSound(null, BlockPos.containing(point), SoundEvents.ELDER_GUARDIAN_CURSE,
                        SoundSource.PLAYERS, 1.0F, 0.7F);
            }
            // Grasp Heart: a finisher. Below 30 % it simply kills; above it, it hurts badly.
            case GRASP_HEART -> {
                if (target != null) {
                    if (target.getHealth() <= target.getMaxHealth() * 0.30F && target.canBeSeenAsEnemy()) {
                        target.hurt(magicSource(level, caster), target.getMaxHealth() * 10.0F);
                    }
                    hurt(level, caster, target, magicSource(level, caster), 10.0F * scale, modifiers);
                    effect(target, MobEffects.WEAKNESS, 200, 2);
                    effect(target, MobEffects.WITHER, 100, 1);
                }
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, point.x, point.y + 1.0D, point.z,
                        24, 0.4D, 0.4D, 0.4D, 0.1D);
                level.playSound(null, BlockPos.containing(point), SoundEvents.WARDEN_HEARTBEAT,
                        SoundSource.PLAYERS, 1.0F, 0.6F);
            }
            case FALLEN_DOWN -> {
                hurt(level, caster, target, magicSource(level, caster), 18.0F * scale, modifiers);
                if (target != null) {
                    target.setRemainingFireTicks(200);
                }
                igniteAround(level, point, (int) (3 * SpellModifier.areaMultiplier(modifiers)));
                strike(level, point);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y + 1.5D, point.z,
                        100, 1.0D, 2.0D, 1.0D, 0.08D);
            }
            case NEGATIVE_BURST -> {
                double radius = 7.0D * SpellModifier.areaMultiplier(modifiers);
                for (LivingEntity victim : area(level, point, radius, caster)) {
                    hurt(level, caster, victim, magicSource(level, caster), 6.0F * scale, modifiers);
                    effect(victim, MobEffects.WITHER, 160, 2);
                    effect(victim, MobEffects.WEAKNESS, 160, 1);
                }
                if (caster != null) {
                    caster.heal(3.0F * scale);
                }
                level.sendParticles(ParticleTypes.SQUID_INK, point.x, point.y + 1.0D, point.z,
                        70, radius * 0.4D, 0.8D, radius * 0.4D, 0.01D);
            }
            case BLACK_HOLE -> {
                double radius = 8.0D * SpellModifier.areaMultiplier(modifiers);
                for (LivingEntity victim : area(level, point, radius, caster)) {
                    Vec3 towards = point.subtract(victim.position());
                    if (towards.lengthSqr() > 0.5D) {
                        victim.setDeltaMovement(victim.getDeltaMovement()
                                .add(towards.normalize().scale(0.85D)));
                        victim.hurtMarked = true;
                    }
                    hurt(level, caster, victim, magicSource(level, caster), 4.0F * scale, modifiers);
                    effect(victim, MobEffects.WITHER, 120, 1);
                }
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, point.x, point.y + 1.0D, point.z,
                        100, radius * 0.5D, radius * 0.5D, radius * 0.5D, 0.4D);
                level.playSound(null, BlockPos.containing(point), SoundEvents.END_PORTAL_SPAWN,
                        SoundSource.PLAYERS, 0.6F, 0.5F);
            }

            // ==================================================================================
            // KONOSUBA
            // ==================================================================================
            case CREATE_WATER -> {
                extinguishAround(level, point, (int) (3 * SpellModifier.areaMultiplier(modifiers)));
                BlockPos centre = BlockPos.containing(point);
                for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, 0, -1), centre.offset(1, 1, 1))) {
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
                    }
                }
                // Flame creatures really do not enjoy this.
                for (LivingEntity victim : area(level, point, 4.0D, caster)) {
                    if (victim.getType() == net.minecraft.world.entity.EntityType.BLAZE
                            || victim.getType() == net.minecraft.world.entity.EntityType.MAGMA_CUBE) {
                        hurt(level, caster, victim, level.damageSources().magic(), 6.0F * scale, modifiers);
                    }
                    victim.setRemainingFireTicks(0);
                }
                level.sendParticles(ParticleTypes.SPLASH, point.x, point.y + 0.6D, point.z,
                        60, 1.2D, 0.6D, 1.2D, 0.1D);
            }
            case WIND_BLAST -> {
                hurt(level, caster, target, level.damageSources().magic(), 2.5F * scale, modifiers);
                push(target, point, 1.8D * scale);
                level.sendParticles(ParticleTypes.CLOUD, point.x, point.y + 0.8D, point.z,
                        30, 0.5D, 0.5D, 0.5D, 0.2D);
            }
            case LURK -> {
                effect(target, MobEffects.INVISIBILITY, 160, 0);
                effect(target, MobEffects.MOVEMENT_SPEED, 160, 1);
            }
            // Drain Touch: health and mana both move, and the caster gets both.
            case DRAIN_TOUCH -> {
                if (target != null) {
                    float before = target.getHealth();
                    hurt(level, caster, target, magicSource(level, caster), 4.0F * scale, modifiers);
                    float dealt = Math.max(0.0F, before - target.getHealth());
                    if (caster != null) {
                        caster.heal(dealt * 0.8F);
                    }
                    if (caster instanceof ServerPlayer player) {
                        ManaApi.addMana(player, (long) (dealt * 40.0F));
                    }
                    level.sendParticles(ParticleTypes.SOUL, point.x, point.y + 1.0D, point.z,
                            20, 0.3D, 0.5D, 0.3D, 0.02D);
                }
            }
            case CURSED_CRYSTAL_PRISON -> {
                hurt(level, caster, target, level.damageSources().magic(), 6.0F * scale, modifiers);
                if (target != null) {
                    target.setTicksFrozen(target.getTicksRequiredToFreeze() + 300);
                    effect(target, MobEffects.MOVEMENT_SLOWDOWN, 200, 6);
                    effect(target, MobEffects.DIG_SLOWDOWN, 200, 3);
                    target.setDeltaMovement(Vec3.ZERO);
                    target.hurtMarked = true;
                }
                level.sendParticles(ParticleTypes.ITEM_SNOWBALL, point.x, point.y + 1.0D, point.z,
                        50, 0.6D, 0.9D, 0.6D, 0.05D);
            }
            case TURN_UNDEAD -> {
                float amount = 6.0F * scale;
                if (target != null && target.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
                    amount = 24.0F * scale;
                    effect(target, MobEffects.GLOWING, 200, 0);
                    if (target.getType() == net.minecraft.world.entity.EntityType.ZOMBIE
                            || target.getType() == net.minecraft.world.entity.EntityType.SKELETON) {
                        target.setRemainingFireTicks(100);
                    }
                }
                hurt(level, caster, target, magicSource(level, caster), amount, modifiers);
                level.sendParticles(ParticleTypes.END_ROD, point.x, point.y + 1.2D, point.z,
                        30, 0.5D, 0.8D, 0.5D, 0.03D);
            }
            case PURIFICATION -> {
                double radius = 7.0D * SpellModifier.areaMultiplier(modifiers);
                for (LivingEntity victim : area(level, point, radius, caster)) {
                    if (victim.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
                        hurt(level, caster, victim, magicSource(level, caster), 10.0F * scale, modifiers);
                        victim.setRemainingFireTicks(120);
                    } else {
                        victim.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.MILK);
                        victim.setRemainingFireTicks(0);
                    }
                }
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, point.x, point.y + 1.0D, point.z,
                        60, radius * 0.4D, 0.6D, radius * 0.4D, 0.02D);
            }
            // EXPLOSION. Megumin's. Enormous, clean, and expensive.
            case EXPLOSION -> {
                double radius = 12.0D * SpellModifier.areaMultiplier(modifiers);
                for (LivingEntity victim : area(level, point, radius, caster)) {
                    hurt(level, caster, victim, magicSource(level, caster), 26.0F * scale, modifiers);
                    victim.setRemainingFireTicks(120);
                }
                // The crater is cosmetic: no block damage, or this would be a mining tool.
                level.explode(caster, point.x, point.y, point.z, (float) radius,
                        net.minecraft.world.level.Level.ExplosionInteraction.NONE);
                if (caster instanceof ServerPlayer player && !player.isCreative()) {
                    // Megumin's tax: the caster is left winded, not dead.
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                }
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, point.x, point.y + 1.0D, point.z,
                        4, radius * 0.4D, 0.5D, radius * 0.4D, 0.0D);
            }
            case GOD_REQUIEM -> {
                double radius = 9.0D * SpellModifier.areaMultiplier(modifiers);
                for (LivingEntity victim : area(level, point, radius, caster)) {
                    if (victim.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
                        hurt(level, caster, victim, magicSource(level, caster), 16.0F * scale, modifiers);
                        effect(victim, MobEffects.GLOWING, 200, 0);
                    } else {
                        victim.heal(8.0F * scale);
                        victim.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.MILK);
                    }
                }
                if (caster != null) {
                    caster.heal(8.0F * scale);
                }
                level.sendParticles(ParticleTypes.END_ROD, point.x, point.y + 1.0D, point.z,
                        90, radius * 0.4D, 1.0D, radius * 0.4D, 0.05D);
            }

            case BAD_GAS -> {
                // Bad Gas: the stink itself does very little. The flight is the spell.
                if (target != null) {
                    hurt(level, caster, target, magicSource(level, caster), 2.0F * scale, modifiers);
                    effect(target, MobEffects.CONFUSION, 200, 0);
                    effect(target, MobEffects.HUNGER, 200, 1);
                    fling(level, caster, target, scale);
                }
                int puffs = (int) Math.min(80.0F, 24.0F * Math.max(1.0F, scale));
                level.sendParticles(ParticleTypes.SNEEZE, point.x, point.y + 0.6D, point.z,
                        puffs, 0.9D, 0.7D, 0.9D, 0.04D);
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, point.x, point.y + 0.4D, point.z,
                        puffs / 2, 1.1D, 0.5D, 1.1D, 0.02D);
                if (!SpellModifier.SILENT.in(modifiers)) {
                    level.playSound(null, BlockPos.containing(point), SoundEvents.SLIME_SQUISH,
                            SoundSource.PLAYERS, 1.0F, 0.6F);
                }
            }

            // ---- areas ----
            case LINGERING_ZONE -> zone(level, point, (int) (100 * scale));
            // The ordinary firestorm: six seconds, i-frames respected, ground set alight.
            case FIRE_STORM -> SpellStorms.addFireStorm(level, point, 8.0D + scale, 3.0F * scale,
                    (int) (120 * Math.min(2.0F, scale)), caster);
            // The upgrade: nearly twice the footprint, more than twice the damage per pulse, no i-frames,
            // and nothing ignites - so it will not burn the loot it creates.
            case HELLSTORM -> SpellStorms.addHellStorm(level, point, 14.0D + scale * 2.0F, 7.0F * scale,
                    (int) (160 * Math.min(2.0F, scale)), caster);
            case GRAVITY_WELL -> gravity(level, point, (int) (2 * scale));
            case WITHER_STORM -> {
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(point, point).inflate(3.0D + scale), Entity::isAlive)) {
                    victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 240, 2));
                    victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
                }
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y, point.z,
                        80, 1.5D, 1.0D, 1.5D, 0.02D);
            }
        }
    }

    // ------------------------------------------------------------------ small helpers

    private static net.minecraft.world.damagesource.DamageSource magicSource(ServerLevel level, LivingEntity caster) {
        return caster == null ? level.damageSources().magic()
                : level.damageSources().indirectMagic(caster, caster);
    }

    private static void hurt(ServerLevel level, LivingEntity target,
                             net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (target == null || !target.isAlive()) {
            return;
        }
        target.hurt(source, amount);
    }

    /**
     * Modifier-aware damage: the version every new component uses.
     *
     * PIERCING clears the victim's damage cooldown and deals the damage as magic (which armour cannot
     * reduce); VAMPIRIC heals the caster for a third of whatever lands.
     */
    private static void hurt(ServerLevel level, LivingEntity caster, LivingEntity target,
                             net.minecraft.world.damagesource.DamageSource source, float amount,
                             int modifiers) {
        if (target == null || !target.isAlive() || amount <= 0.0F) {
            return;
        }
        net.minecraft.world.damagesource.DamageSource applied = SpellModifier.PIERCING.in(modifiers)
                ? (caster == null ? level.damageSources().magic() : level.damageSources().indirectMagic(caster, caster))
                : source;
        if (SpellModifier.PIERCING.in(modifiers)) {
            target.invulnerableTime = 0;
            target.hurtTime = 0;
        }
        // EXECUTE: doubled down on anything already below a third of its health.
        if (SpellModifier.EXECUTE.in(modifiers)
                && target.getHealth() < target.getMaxHealth() * 0.35F) {
            amount = (float) (amount * SpellModifier.executeMultiplier(modifiers));
        }
        float before = target.getHealth();
        target.hurt(applied, amount);
        float dealt = Math.max(0.0F, before - target.getHealth());
        if (dealt <= 0.0F) {
            return;
        }
        if (SpellModifier.VAMPIRIC.in(modifiers) && caster != null) {
            caster.heal(dealt * 0.35F);
        }
        // SIPHON: magic is power, and power is magic. 6 mana per point of damage.
        if (SpellModifier.SIPHON.in(modifiers) && caster instanceof ServerPlayer player) {
            ManaApi.addMana(player, (long) (dealt * 6.0F));
        }
        // BASTION: half the damage dealt becomes absorption, capped at a full health bar.
        if (SpellModifier.BASTION.in(modifiers) && caster != null) {
            float shield = Math.min(caster.getMaxHealth(),
                    caster.getAbsorptionAmount() + dealt * 0.5F);
            caster.setAbsorptionAmount(shield);
        }
    }

    private static void effect(LivingEntity target, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                               int ticks, int amplifier) {
        if (target == null) {
            return;
        }
        target.addEffect(new MobEffectInstance(effect, ticks, amplifier));
    }

    private static void push(LivingEntity target, Vec3 from, double strength) {
        if (target == null) {
            return;
        }
        Vec3 delta = target.position().subtract(from);
        if (delta.lengthSqr() < 1.0E-4D) {
            delta = new Vec3(0.0D, 1.0D, 0.0D);
        }
        target.setDeltaMovement(target.getDeltaMovement().add(delta.normalize().scale(strength)));
        target.hurtMarked = true;
    }

    private static void strike(ServerLevel level, Vec3 point) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(BlockPos.containing(point)));
            level.addFreshEntity(bolt);
        }
    }

    private static void igniteAround(ServerLevel level, Vec3 point, int radius) {
        BlockPos centre = BlockPos.containing(point);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -1, -radius),
                centre.offset(radius, radius, radius))) {
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
            }
        }
    }

    private static void extinguishAround(ServerLevel level, Vec3 point, int radius) {
        BlockPos centre = BlockPos.containing(point);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -1, -radius),
                centre.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                level.removeBlock(pos, false);
            }
        }
    }

    private static void placeLight(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        if (level.getBlockState(pos).isAir()) {
            level.setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        }
    }

    /** One block per cast, and never a block entity: this must not become a mining machine. */
    private static void dig(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F) {
            return;
        }
        level.destroyBlock(pos, true);
    }

    /**
     * Throw an entity somewhere else.
     *
     * Up to 16 blocks away, never less than 4 (a "teleport" that moves them one block is a taunt), in a
     * random compass direction with some vertical spread. Up to 24 candidate spots are tried and the
     * first one that is a legal standing position wins; if none is, the spell does nothing at all
     * rather than burying the victim in stone. That failure mode is deliberate: a spell that suffocates
     * people is a bug report, not a feature.
     */
    private static void randomTeleport(ServerLevel level, LivingEntity target) {
        randomTeleport(level, target, 16.0D);
    }

    /** Every living thing inside a sphere, excluding the caster. */
    private static List<LivingEntity> area(ServerLevel level, Vec3 centre, double radius, LivingEntity caster) {
        return level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre, centre).inflate(radius),
                entity -> entity.isAlive() && entity != caster);
    }

    /** @param reach the maximum distance of the jump, in blocks */
    private static void randomTeleport(ServerLevel level, LivingEntity target, double reach) {
        if (target == null || !target.isAlive()) {
            return;
        }
        net.minecraft.util.RandomSource random = level.getRandom();
        Vec3 from = target.position();
        for (int attempt = 0; attempt < 24; attempt++) {
            double maximum = Math.max(8.0D, reach);
            double distance = maximum * 0.25D + random.nextDouble() * maximum * 0.75D;
            double angle = random.nextDouble() * (Math.PI * 2.0D);
            double dx = Math.cos(angle) * distance;
            double dz = Math.sin(angle) * distance;
            double dy = (random.nextDouble() - 0.4D) * Math.min(40.0D, maximum);
            Vec3 candidate = new Vec3(from.x + dx, from.y + dy, from.z + dz);
            BlockPos landing = safeLanding(level, candidate);
            if (landing == null) {
                continue;
            }
            Vec3 destination = Vec3.atBottomCenterOf(landing);
            level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0D, from.z, 30, 0.3D, 0.6D, 0.3D, 0.3D);
            if (target instanceof ServerPlayer player) {
                // Explicit server-level teleport: this also resets the client's position, which a bare
                // setPos would not do.
                player.teleportTo(level, destination.x, destination.y, destination.z, player.getYRot(),
                        player.getXRot());
            } else {
                target.teleportTo(destination.x, destination.y, destination.z);
            }
            level.sendParticles(ParticleTypes.PORTAL, destination.x, destination.y + 1.0D, destination.z,
                    30, 0.3D, 0.6D, 0.3D, 0.3D);
            level.playSound(null, BlockPos.containing(destination), SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.PLAYERS, 0.8F, 1.3F);
            return;
        }
    }

    /**
     * The nearest legal standing position to {@code candidate}: two blocks of air with something solid
     * underneath, within eight blocks above or below the candidate's own height.
     */
    private static BlockPos safeLanding(ServerLevel level, Vec3 candidate) {
        BlockPos start = BlockPos.containing(candidate);
        if (!level.isInWorldBounds(start)) {
            return null;
        }
        for (int offset = 0; offset <= 8; offset++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                BlockPos pos = start.above(offset * sign);
                if (offset == 0 && sign < 0) {
                    continue;
                }
                if (!level.isInWorldBounds(pos)) {
                    continue;
                }
                if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                        && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                        && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static void zone(ServerLevel level, Vec3 point, int duration) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, point.x, point.y, point.z);
        cloud.setRadius(3.0F);
        cloud.setDuration(duration);
        cloud.setRadiusPerTick(0.02F);
        cloud.setParticle(ParticleTypes.WITCH);
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
        cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        level.addFreshEntity(cloud);
    }

    private static void gravity(ServerLevel level, Vec3 point, int amplifier) {
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(point, point).inflate(8.0D), Entity::isAlive)) {
            Vec3 towards = point.subtract(victim.position());
            if (towards.lengthSqr() > 0.25D) {
                victim.setDeltaMovement(victim.getDeltaMovement().add(towards.normalize().scale(0.35D)));
                victim.hurtMarked = true;
            }
            if (amplifier > 0) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, amplifier));
            }
        }
        level.sendParticles(ParticleTypes.PORTAL, point.x, point.y, point.z, 60, 3.0D, 2.0D, 3.0D, 0.4D);
    }

    private SpellCasting() {
    }

    // ---- volley spread ------------------------------------------------------------------------
    //
    // A unit vector perpendicular to {@code look}, so repeats can be laid out side by side instead of
    // stacked. Degenerate looks (straight up or down) fall back to the X axis.

    private static Vec3 sideAxis(Vec3 look) {
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-8D) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }
        return flat.normalize().cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
    }

    /** Nudge an origin sideways by {@code amount} blocks. */
    private static Vec3 sideOf(Vec3 point, Vec3 look, double amount) {
        return point.add(sideAxis(look).scale(amount));
    }

    /**
     * The point every bolt of a volley is aimed at: the entity the caster is pointing at if there is
     * one, otherwise the first block along the sightline, otherwise the end of the shape's range.
     * Launching from offset origins toward a shared point is what makes the volley converge.
     */
    private static Vec3 convergePoint(ServerLevel level, LivingEntity caster, LivingEntity target,
                                      Vec3 point, Vec3 look, double range) {
        if (target != null && target.isAlive()) {
            return target.getBoundingBox().getCenter();
        }
        Vec3 dir = look.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, -1.0D, 0.0D) : look.normalize();
        Vec3 end = point.add(dir.scale(range));
        BlockHitResult hit = level.clip(new ClipContext(point, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    /** How far a Seeker bolt will look for something to lock onto. */
    public static final double SEEK_RADIUS = 24.0D;

    /** Entity id of the closest living thing to {@code around}, or -1 if the field is empty. */
    private static int nearestTargetId(ServerLevel level, LivingEntity caster, Vec3 around, double radius) {
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(around, around).inflate(radius),
                entity -> entity.isAlive() && entity != caster && !(entity instanceof ServerPlayer));
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : nearby) {
            double distance = entity.position().distanceToSqr(around);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best == null ? -1 : best.getId();
    }

    /**
     * BAD GAS' throw.
     *
     * The velocity is up AND along the direction the VICTIM is facing - not away from the caster. That
     * detail is the whole joke: a zombie shambling toward you is launched over your head and out the
     * back of the fight, and a mob running away is thrown further away.
     *
     * With Sable: Ragdolls installed the body becomes a real physics ragdoll carrying this velocity.
     * Without it, the velocity is applied directly and the mob's AI is switched off for three seconds
     * so it tumbles instead of instantly walking it off (see {@link FlungBodies}).
     */
    public static void fling(ServerLevel level, LivingEntity caster, LivingEntity target, float scale) {
        if (target == null || !target.isAlive()) {
            return;
        }
        float clamped = Math.min(3.0F, Math.max(0.6F, scale));
        Vec3 facing = Vec3.directionFromRotation(0.0F, target.getYRot());
        double forward = 1.6D * clamped;
        double upward = 1.25D + 0.35D * clamped;
        Vec3 velocity = new Vec3(facing.x * forward, upward, facing.z * forward);
        if (SableRagdolls.ragdoll(level, target, velocity)) {
            return;
        }
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        if (target instanceof Mob mob) {
            FlungBodies.schedule(mob, 60);
        }
    }

    /**
     * NOVA: a blast that breaks nothing. {@code ExplosionInteraction.NONE} means no block damage and
     * no fire - only the shove and the light. Radius grows with the emitter assembly's power, so a
     * Nova from a 3x3x3 cube is genuinely large.
     */
    public static void detonate(ServerLevel level, LivingEntity caster, Vec3 point, float power) {
        level.explode(caster, point.x, point.y, point.z,
                (float) Math.min(24.0D, 2.0D + 2.0D * power),
                net.minecraft.world.level.Level.ExplosionInteraction.NONE);
    }

    /** A copy of {@code spell} with ECHO cleared on one step, so an echo cannot echo. */
    private static Spell withoutEcho(Spell spell, int index) {
        List<Spell.Step> steps = spell.steps();
        if (index < 0 || index >= steps.size()) {
            return null;
        }
        List<Spell.Step> copy = new java.util.ArrayList<>(steps);
        Spell.Step step = steps.get(index);
        copy.set(index, step.withModifiers(step.modifiers() & ~SpellModifier.ECHO.bit()));
        return new Spell(copy, spell.name());
    }
}
