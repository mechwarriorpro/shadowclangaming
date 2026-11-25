package com.shadowclangaming.warhead;

import com.shadowclangaming.entity.MissileEntity;
import com.shadowclangaming.item.MissileItem;
import com.shadowclangaming.item.MissileModuleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * What happens when the vest goes off.
 *
 * Three things, in this order, because the order is what makes it read as a chain reaction rather than a
 * pile of simultaneous events:
 *
 *   1. the blast - an HE detonation on the warhead scale, which is the blast that kills you if you are
 *      standing next to the bomber;
 *   2. the missiles - every missile in the inventory is launched, in a random direction, on its own arc;
 *   3. the explosives - every piece of TNT (and anything a datapack tags as {@code c:explosives}) is lit
 *      and thrown away from the corpse, so the pile scatters before it cooks off.
 *
 * The items are consumed before the death drops are generated, so a bomber does not leave a loot pile of
 * live ordnance on top of the crater.
 */
public final class Kamikaze {

    private Kamikaze() {
    }

    /**
     * Wiring: did a player wearing the vest just die?
     *
     * Checked on the chest slot rather than "anywhere in the inventory", because the vest is armour and
     * only counts when it is worn. A vest in your backpack is just a heavy shirt.
     */
    public static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack chest = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof com.shadowclangaming.item.KamikazeVestItem)) {
            return;
        }
        detonate(player);
    }

    /**
     * Run the vest.
     *
     * Called from the death listener. Deliberately tolerant: if anything is missing it still detonates,
     * because failing to explode is a worse bug than exploding imperfectly.
     */
    public static void detonate(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = player.position();
        Inventory inventory = player.getInventory();

        // 1. The blast. The warhead comes from whatever block-form explosive was loaded into the suit's
        //    payload slot; an unloaded suit falls back to its built-in HE charge. The clearing radius
        //    scales with the payload so a loaded antimatter suit does not make a crater the size of the
        //    default charge.
        WarheadType warhead = payloadOf(player);
        float power = warhead == WarheadType.HE
                ? KamikazePlan.VEST_POWER
                : com.shadowclangaming.warhead.ExplosivePackaging.power(warhead.basePower(),
                        com.shadowclangaming.warhead.ExplosivePackaging.Form.BLOCK);
        double clearing = KamikazePlan.VEST_CLEARING * Math.max(1.0D, power / KamikazePlan.VEST_POWER);
        warhead.detonate(level, at, power, 0L, null, List.of(), -1, 0L);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.9D, at.z, 3,
                clearing * 0.5D, 1.0D, clearing * 0.5D, 0.0D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0F, 0.5F);

        // 2. Every missile, in a random direction.
        fireMissiles(level, player, at);

        // 3. Every explosive, lit and thrown.
        throwExplosives(level, player, at, inventory);

        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.shadowclangaming.kamikaze_triggered"), false);
    }

    /**
     * Which warhead the worn vest is loaded with.
     *
     * The payload is a data component on the chest slot, so the vest keeps its load through death, storage
     * and dimension travel. No component = the built-in HE charge.
     */
    public static WarheadType payloadOf(net.minecraft.world.entity.LivingEntity wearer) {
        ItemStack chest = wearer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        String id = chest.get(com.shadowclangaming.registry.SCGDataComponents.VEST_PAYLOAD.get());
        if (id == null || id.isBlank()) {
            return WarheadType.HE;
        }
        return WarheadType.byId(id);
    }

    /**
     * Load a warhead into a suit of armour. Returns the resulting stack.
     *
     * Kept here rather than on the item so both the vest's own right-click and any future loader
     * (dispenser, machine) go through one code path.
     */
    public static ItemStack load(ItemStack armour, WarheadType warhead) {
        ItemStack copy = armour.copyWithCount(1);
        copy.set(com.shadowclangaming.registry.SCGDataComponents.VEST_PAYLOAD.get(), warhead.id());
        return copy;
    }

    /**
     * Launch the carried missiles.
     *
     * Any {@link MissileItem} counts, module or not: a rocket is a rocket. They are aimed with
     * {@link KamikazePlan#salvoTarget} - a random bearing and distance - so the result is a fan of
     * contrails going everywhere, some of which will land on something the player never saw.
     */
    private static void fireMissiles(ServerLevel level, ServerPlayer player, Vec3 at) {
        Inventory inventory = player.getInventory();
        long seed = level.getGameTime() * 31L + player.getUUID().getMostSignificantBits();
        java.util.Random random = new java.util.Random(seed);

        int launched = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && launched < KamikazePlan.MAX_SALVO; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof MissileItem missileItem)) {
                continue;
            }
            // Launch every missile in the stack, but respect the salvo cap.
            int fromStack = Math.min(stack.getCount(), KamikazePlan.MAX_SALVO - launched);
            for (int i = 0; i < fromStack; i++) {
                if (!launchOne(level, at, stack, missileItem, random, seed + launched)) {
                    break;
                }
                launched++;
            }
            stack.shrink(fromStack);
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        if (launched > 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.shadowclangaming.kamikaze_salvo", launched), false);
        }
    }

    /** One missile, aimed somewhere at random. */
    private static boolean launchOne(ServerLevel level, Vec3 at, ItemStack stack,
                                     MissileItem item, java.util.Random random, long seed) {
        double[] target = KamikazePlan.salvoTarget(at.x, at.y, at.z, random);
        BlockPos aim = BlockPos.containing(target[0], target[1], target[2]);

        WarheadType warhead = stack.getItem() instanceof MissileModuleItem
                ? MissileModuleItem.warheadOf(stack)
                : item.warhead();

        MissileEntity missile = new MissileEntity(level,
                at.x + (random.nextDouble() - 0.5D) * 1.2D,
                at.y + 1.0D,
                at.z + (random.nextDouble() - 0.5D) * 1.2D,
                aim,
                stack.copyWithCount(1),
                warhead,
                item.powerFor(stack),
                item.cruiseSpeed(),
                MissileItem.fundingOf(stack),
                MissileItem.spellOf(stack),
                MissilePayload.of(stack),
                null,
                null,
                MissilePayload.radiusOf(stack),
                seed);
        level.addFreshEntity(missile);
        return true;
    }

    /**
     * Light and throw every explosive.
     *
     * TNT becomes a live {@link PrimedTnt} with a short randomised fuse, thrown outward - so the pile
     * scatters instead of all going off inside the crater the vest just made. Anything else that a
     * datapack says is explosive is simply flung away as an item, which at least gets it out of the blast.
     */
    private static void throwExplosives(ServerLevel level, ServerPlayer player, Vec3 at, Inventory inventory) {
        java.util.Random random = new java.util.Random(
                level.getGameTime() * 17L + player.getUUID().getLeastSignificantBits());
        List<ItemStack> flung = new ArrayList<>();
        int tnt = 0;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            boolean isTnt = stack.is(Items.TNT);
            boolean tagged = stack.is(KamikazeTags.EXPLOSIVES);
            if (!isTnt && !tagged) {
                continue;
            }
            double bearing = random.nextDouble() * Math.PI * 2.0D;
            double dirX = Math.cos(bearing);
            double dirZ = Math.sin(bearing);
            if (isTnt) {
                for (int i = 0; i < stack.getCount(); i++) {
                    PrimedTnt primed = new PrimedTnt(level, at.x, at.y + 1.0D, at.z, null);
                    primed.setFuse(KamikazePlan.tntFuse(random));
                    double[] velocity = KamikazePlan.throwVelocity(dirX, dirZ, random);
                    primed.setDeltaMovement(velocity[0], velocity[1], velocity[2]);
                    level.addFreshEntity(primed);
                    tnt++;
                }
            } else {
                // Not a TNT item: it cannot "activate", so it is thrown clear instead of cooking off in the
                // player's pockets. Better a scattered item than a duplicated one.
                flung.add(stack.copy());
            }
            inventory.setItem(slot, ItemStack.EMPTY);
        }

        for (ItemStack stack : flung) {
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    level, at.x, at.y + 1.0D, at.z, stack);
            double bearing = random.nextDouble() * Math.PI * 2.0D;
            double[] velocity = KamikazePlan.throwVelocity(Math.cos(bearing), Math.sin(bearing), random);
            drop.setDeltaMovement(velocity[0], velocity[1], velocity[2]);
            level.addFreshEntity(drop);
        }

        if (tnt > 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.shadowclangaming.kamikaze_tnt", tnt), false);
        }
    }
}
