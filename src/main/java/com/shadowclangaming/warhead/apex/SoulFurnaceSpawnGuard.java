package com.shadowclangaming.warhead.apex;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Stops the world repopulating a Soul Furnace while it is eating.
 *
 * <p>The furnace is defined by its availability check: it grows while there is something left to consume
 * and releases on the first check that finds nothing. In a chunk with a normal spawn rate that first empty
 * check never arrives. Something spawns back inside the sphere during the five seconds between checks, so
 * every check finds food, the furnace grows to its ceiling and then burns for the entire safety maximum -
 * which is what was being reported, and why a furnace appeared to never go off.
 *
 * <p>So while a furnace is burning, no new mob may appear within
 * {@link SoulFurnacePlan#SUPPRESS_MULTIPLIER} of it. Mobs already inside are still eaten on schedule; only
 * the ones that would walk in are refused. The veto is a plain position check failure, the same answer the
 * game gives for "this block is full" or "the light level is wrong", so nothing downstream sees a
 * half-created entity.
 *
 * <p>This refuses every spawn type, not just natural ones: a spawner or a spawn egg dropped inside the
 * sphere would keep it alive just as effectively. The suppression lasts exactly as long as the furnace -
 * see {@link SoulFurnaceStages#suppressesSpawning} - and there is nothing to undo when it ends.
 */
@EventBusSubscriber(modid = ShadowClanGaming.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class SoulFurnaceSpawnGuard {

    private SoulFurnaceSpawnGuard() {
    }

    /**
     * Server-only by contract: {@code PositionCheck} is fired on the logical server, after spawn placement
     * rules have already been evaluated.
     */
    @SubscribeEvent
    public static void onSpawnPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getResult() != MobSpawnEvent.PositionCheck.Result.DEFAULT) {
            // Another mod has already forced this spawn one way or the other. Leave it alone rather than
            // overriding a decision that was made deliberately.
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!SoulFurnaceStages.suppressesSpawning(level, event.getX(), event.getY(), event.getZ())) {
            return;
        }
        event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        com.shadowclangaming.debug.ShadowDebug.log("SOUL_FURNACE_SPAWN_BLOCKED",
                "pos=({}, {}, {}) type={} level={}",
                event.getX(), event.getY(), event.getZ(), event.getSpawnType(),
                level.dimension().location());
    }
}
