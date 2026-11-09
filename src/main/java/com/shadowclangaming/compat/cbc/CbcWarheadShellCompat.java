package com.shadowclangaming.compat.cbc;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.item.CbcWarheadShellItem;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Bridges our CBC warhead shells with the actual Create Big Cannons projectile.
 * <p>
 * Soft-dependency: all calls are reflective so the jar loads and runs without CBC.
 * When CBC is present we:
 * <ul>
 *   <li>listen for {@code AbstractCannonProjectile} spawns; if its ItemStack is one of ours,
 *       tag the entity with warhead+tier so impact can be identified even if the item is consumed.</li>
 *   <li>listen for explosion/impact events from CBC projectiles and replace the vanilla
 *       {@code power 8} blast with our warhead's full {@link WarheadType#detonate}.</li>
 *   <li>provide a manual fire path via the Cannon Director: {@link #fireWarheadShell} can be called
 *       to spawn a fake projectile and detonate after ballistic flight.</li>
 * </ul>
 * Shader shells (plasma/gravitational/vortex/disintegration/singularity) additionally trigger
 * their post-processing effect via {@link CbcShaderFx#trigger(ServerLevel, Vec3, CbcShellType, CbcShellTier)}.
 */
public final class CbcWarheadShellCompat {
    public static final String MODID = "createbigcannons";
    private static boolean registered;
    private static final Map<Integer, ShellPayload> inflight = new HashMap<>();

    public record ShellPayload(WarheadType warhead, CbcShellType shellType, CbcShellTier tier, ItemStack stack) {}

    private CbcWarheadShellCompat() {}

    public static boolean installed() { return ModList.get().isLoaded(MODID); }

    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(CbcWarheadShellCompat::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(CbcWarheadShellCompat::onExplosionDetonate);
        ShadowClanGaming.LOGGER.info("CBC Warhead Shell compat registered (installed={})", installed());
    }

    /** Called when any entity joins the level — tag CBC projectiles that carry our shells. */
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        String cls = entity.getClass().getName();
        if (!cls.contains("Cannon") && !cls.contains("Projectile") && !cls.contains("Shell")) return;
        // Try to read the projectile's ItemStack via common CBC accessors
        ItemStack stack = readProjectileStack(entity);
        if (stack == null || stack.isEmpty()) return;
        ShellPayload payload = payloadFromStack(stack);
        if (payload == null) return;
        inflight.put(entity.getId(), payload);
        // Schedule impact watch: CBC projectiles call discard() shortly after impact; we poll position
        level.getServer().execute(() -> watchProjectile(level, entity, payload));
    }

    private static void watchProjectile(ServerLevel level, Entity projectile, ShellPayload payload) {
        // Poll for 10 seconds; when projectile is removed or stationary near ground, detonate
        final int[] ticks = {0};
        final Vec3[] lastPos = {projectile.position()};
        level.getServer().execute(new Runnable() {
            @Override public void run() {
                if (ticks[0]++ > 200 || !projectile.isAlive() || projectile.isRemoved()) {
                    Vec3 pos = projectile.position();
                    if (pos.distanceTo(lastPos[0]) < 0.3 || !projectile.isAlive()) {
                        detonate(level, pos, payload);
                        inflight.remove(projectile.getId());
                        return;
                    }
                }
                lastPos[0] = projectile.position();
                if (ticks[0] < 200 && projectile.isAlive()) {
                    level.getServer().execute(this);
                } else if (projectile.isAlive()) {
                    // Timeout — still detonate at current pos
                    detonate(level, projectile.position(), payload);
                    inflight.remove(projectile.getId());
                }
            }
        });
    }

    private static void detonate(ServerLevel level, Vec3 pos, ShellPayload payload) {
        WarheadType warhead = payload.warhead();
        float power = warhead.basePower() * payload.tier().blastPowerMult();
        // Scale by shell type blast as well (e.g., nuclear shells already have huge baseBlast)
        if (payload.shellType() != CbcShellType.WARHEAD) {
            power = Math.max(power, payload.shellType().baseBlast() * payload.tier().blastPowerMult() * 0.5f);
        }
        // Use the warhead's own detonate — this gives full thematic effects, not just boom
        try {
            warhead.detonate(level, pos, power, 0L);
        } catch (Throwable t) {
            ShadowClanGaming.LOGGER.error("CBC warhead detonate failed for {}", warhead.id(), t);
            level.explode(null, pos.x, pos.y, pos.z, power, Level.ExplosionInteraction.TNT);
        }
        // Shader exotic effect
        if (payload.shellType().hasShader()) {
            CbcShaderFx.trigger(level, pos, payload.shellType(), payload.tier());
        }
        // For warhead shells that map to shader types, also trigger generic shader intensity by tier
        int intensity = payload.tier().tier(); // 1–50 maps to shader strength
        if (intensity > 5) {
            // T50 intensity — broadcast for client post-processing (uses same channel as apex shaders)
            level.getServer().getPlayerList().broadcastSystemMessage(
                net.minecraft.network.chat.Component.literal("§b[CBC T" + intensity + "] " + warhead.id() + " shell impact — shader intensity " + (intensity/50f)), false);
        }
    }

    /** Intercept CBC explosions that would otherwise be vanilla power 8 — let our warhead replace it. */
    private static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity cause = event.getExplosion().getDirectSourceEntity();
        if (cause == null) return;
        ShellPayload payload = inflight.get(cause.getId());
        if (payload == null) return;
        // Cancel vanilla block damage — our detonate already handled it
        event.getAffectedBlocks().clear();
        event.getAffectedEntities().clear();
    }

    private static ItemStack readProjectileStack(Entity projectile) {
        // Try common CBC accessors reflectively
        for (String m : new String[]{"getProjectileStack", "getItem", "getStack", "getDisplayedItem", "getProjectileItem"}) {
            try {
                Method method = findMethod(projectile.getClass(), m);
                if (method != null) {
                    Object o = method.invoke(projectile);
                    if (o instanceof ItemStack s) return s;
                }
            } catch (Throwable ignored) {}
        }
        // Try field
        for (String f : new String[]{"projectileStack", "itemStack", "stack", "shellStack"}) {
            try {
                var field = findField(projectile.getClass(), f);
                if (field != null) {
                    Object o = field.get(projectile);
                    if (o instanceof ItemStack s) return s;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static ShellPayload payloadFromStack(ItemStack stack) {
        if (stack.getItem() instanceof CbcWarheadShellItem shell) {
            return new ShellPayload(shell.warhead(), shell.shellType(), shell.tier(), stack);
        }
        // Also check data component warhead id for cases where item was copied
        String wid = stack.get(SCGDataComponents.MODULE_WARHEAD.get());
        if (wid != null) {
            WarheadType wt = WarheadType.byId(wid);
            if (wt != null) {
                // Default to WARHEAD type T1 if no tier info
                return new ShellPayload(wt, CbcShellType.WARHEAD, CbcShellTier.T1, stack);
            }
        }
        return null;
    }

    /** Manual fire path for Director — spawns a visual projectile and detonates after ballistic flight. */
    public static boolean fireWarheadShell(ServerLevel level, BlockPos cannonPos, Vec3 target, WarheadType warhead, CbcShellTier tier) {
        if (warhead == null) return false;
        double dx = target.x - (cannonPos.getX() + 0.5);
        double dy = target.y - (cannonPos.getY() + 1.5);
        double dz = target.z - (cannonPos.getZ() + 0.5);
        // Spawn visual projectile for feedback
        ItemStack visual = new ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
        var reg = com.shadowclangaming.registry.SCGItems.CBC_WARHEAD_SHELLS.get(warhead.id());
        if (reg != null) visual = new ItemStack(reg.get());
        ItemEntity ent = new ItemEntity(level, cannonPos.getX()+0.5, cannonPos.getY()+1.5, cannonPos.getZ()+0.5, visual);
        ent.setDeltaMovement(dx*0.02, 0.4, dz*0.02);
        level.addFreshEntity(ent);
        // Schedule delayed detonation — use simple tick counter appended to server queue
        int flightTicks = (int)Math.ceil(Math.sqrt(dx*dx + dz*dz) / 20.0) + 20;
        PendingDetonation.queue(level, target, warhead, tier, flightTicks);
        return true;
    }

    // Simple pending detonation queue — ticked from ShadowClanGaming server tick
    private static final java.util.List<PendingDetonation> PENDING = new java.util.ArrayList<>();
    private record PendingDetonation(ServerLevel level, Vec3 target, WarheadType warhead, CbcShellTier tier, int ticksLeft) {
        static void queue(ServerLevel level, Vec3 target, WarheadType warhead, CbcShellTier tier, int delay) {
            synchronized (PENDING) { PENDING.add(new PendingDetonation(level, target, warhead, tier, delay)); }
        }
        static void tick(net.minecraft.server.MinecraftServer server) {
            synchronized (PENDING) {
                var it = PENDING.iterator();
                while (it.hasNext()) {
                    var p = it.next();
                    int left = p.ticksLeft - 1;
                    if (left <= 0) {
                        if (p.level.isLoaded(BlockPos.containing(p.target))) {
                            float power = p.warhead.basePower() * p.tier.blastPowerMult();
                            p.warhead.detonate(p.level, p.target, power, 0L);
                        }
                        it.remove();
                    } else {
                        // replace with decremented
                        it.remove();
                        PENDING.add(new PendingDetonation(p.level, p.target, p.warhead, p.tier, left));
                        break; // avoid concurrent mod; will process next tick
                    }
                }
            }
        }
    }
    public static void tickPending(net.minecraft.server.MinecraftServer server) { PendingDetonation.tick(server); }

    private static Method findMethod(Class<?> cls, String name) {
        for (Method m : cls.getMethods()) if (m.getName().equals(name) && m.getParameterCount()==0) return m;
        for (Method m : cls.getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterCount()==0) return m;
        Class<?> sup = cls.getSuperclass();
        if (sup != null && sup != Object.class) return findMethod(sup, name);
        return null;
    }
    private static java.lang.reflect.Field findField(Class<?> cls, String name) {
        try { var f = cls.getDeclaredField(name); f.setAccessible(true); return f; } catch (Throwable e) {}
        try { var f = cls.getField(name); f.setAccessible(true); return f; } catch (Throwable e) {}
        Class<?> sup = cls.getSuperclass();
        if (sup != null && sup != Object.class) return findField(sup, name);
        return null;
    }
}
