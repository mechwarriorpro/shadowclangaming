package com.shadowclangaming.item;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.chem.DecompositionTable;
import com.shadowclangaming.chem.Element;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Event handlers for Polytool behaviors matching MineChem 1.6.4 PolytoolEventHandler:
 * <ul>
 *   <li><b>Beryllium (Be):</b> Beheading - chance to drop mob skulls (Skeleton, Wither Skeleton, Zombie, Creeper, Player).</li>
 *   <li><b>Calcium (Ca):</b> Extra bone drops from skeletons.</li>
 *   <li><b>Nitrogen (N):</b> Preservation - replaces zombie rotten flesh drops with cooked beef.</li>
 *   <li><b>Titanium (Ti):</b> Better chance to collect equipment from mob drops.</li>
 *   <li><b>Silicon (Si):</b> Advanced circuitry decomposes mob drops into chemical element vials.</li>
 * </ul>
 */
@EventBusSubscriber(modid = ShadowClanGaming.MOD_ID)
public final class PolytoolEvents {

    private PolytoolEvents() {}

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }

        ItemStack mainhand = player.getMainHandItem();
        if (!(mainhand.getItem() instanceof PolytoolItem)) {
            return;
        }

        LivingEntity victim = event.getEntity();
        Collection<ItemEntity> drops = event.getDrops();
        RandomSource rand = victim.level().getRandom();

        // 1. Silicon (Si): Decompose mob drops into constituent elements
        float powerSi = PolytoolItem.getPower(mainhand, Element.SI);
        if (powerSi > 0 && !drops.isEmpty() && victim.level() instanceof ServerLevel sl) {
            List<ItemEntity> decomposedDrops = new ArrayList<>();
            for (ItemEntity dropEntity : drops) {
                ItemStack item = dropEntity.getItem();
                if (DecompositionTable.isDecomposable(item)) {
                    List<ItemStack> vials = DecompositionTable.decompose(item, rand);
                    for (ItemStack vial : vials) {
                        decomposedDrops.add(new ItemEntity(sl, dropEntity.getX(), dropEntity.getY(), dropEntity.getZ(), vial));
                    }
                } else {
                    decomposedDrops.add(dropEntity);
                }
            }
            drops.clear();
            drops.addAll(decomposedDrops);
        }

        // 2. Nitrogen (N): Preservation - replace zombie rotten flesh with cooked beef
        float powerN = PolytoolItem.getPower(mainhand, Element.N);
        if (powerN > 0 && victim instanceof Zombie) {
            int beefCount = Math.max(1, (int) Math.ceil(rand.nextDouble() * powerN));
            drops.removeIf(entity -> entity.getItem().is(Items.ROTTEN_FLESH));
            drops.add(new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(),
                    new ItemStack(Items.COOKED_BEEF, beefCount)));
        }

        // 3. Calcium (Ca): Extra bones from skeletons
        float powerCa = PolytoolItem.getPower(mainhand, Element.CA);
        if (powerCa > 0 && victim instanceof Skeleton) {
            int extraBones = Math.max(1, (int) Math.ceil(rand.nextDouble() * powerCa));
            drops.add(new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(),
                    new ItemStack(Items.BONE, extraBones)));
        }

        // 4. Titanium (Ti): Better chance to collect equipment from mob drops
        float powerTi = PolytoolItem.getPower(mainhand, Element.TI);
        if (powerTi > 0) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack equipped = victim.getItemBySlot(slot);
                if (!equipped.isEmpty() && rand.nextFloat() < 0.25F * powerTi) {
                    drops.add(new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(), equipped.copy()));
                }
            }
        }

        // 5. Beryllium (Be): Beheading - drop mob skulls
        float powerBe = PolytoolItem.getPower(mainhand, Element.BE);
        if (powerBe > 0) {
            float beheadingChance = Math.min(1.0F, 0.15F * powerBe);
            if (rand.nextFloat() < beheadingChance) {
                ItemStack skull = ItemStack.EMPTY;
                if (victim instanceof WitherSkeleton) {
                    skull = new ItemStack(Items.WITHER_SKELETON_SKULL);
                } else if (victim instanceof Skeleton) {
                    skull = new ItemStack(Items.SKELETON_SKULL);
                } else if (victim instanceof Zombie) {
                    skull = new ItemStack(Items.ZOMBIE_HEAD);
                } else if (victim instanceof Creeper) {
                    skull = new ItemStack(Items.CREEPER_HEAD);
                } else if (victim instanceof Player p) {
                    skull = new ItemStack(Items.PLAYER_HEAD);
                }
                if (!skull.isEmpty()) {
                    drops.add(new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(), skull));
                }
            }
        }
    }
}
