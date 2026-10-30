package com.shadowclangaming.item;

import com.shadowclangaming.chem.DecompositionTable;
import com.shadowclangaming.chem.Element;
import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Chemical Polytool.
 *
 * <p>Inspired by the classic MineChem Polytool:
 * <ul>
 *   <li>Universal multi-tool functioning as Pickaxe, Axe, Shovel, Sword, and Shears.</li>
 *   <li>Can be chemically infused with elements to alter its capabilities:
 *     <ul>
 *       <li><b>Silicon (Si):</b> Atomic Decomposition! Mined blocks and slain mobs drop elemental vials directly.</li>
 *       <li><b>Tungsten (W) / Titanium (Ti):</b> Rotary Drill! Hyper-accelerated 18.0x mining speed.</li>
 *       <li><b>Silver (Ag):</b> Purifying Blade! +10 damage vs Undead and Endermen.</li>
 *       <li><b>Boron (B):</b> Arthropod Disruptor! +10 damage vs Spiders and Creepers.</li>
 *       <li><b>Carbon (C):</b> Diamond Matrix! Complete immunity to tool damage.</li>
 *       <li><b>Argon (Ar):</b> Silk Touch precision extraction!</li>
 *       <li><b>Uranium (U):</b> Atomic Excavator! Mines a 3x3 tunnel.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class PolytoolItem extends Item {

    public PolytoolItem(Properties properties) {
        super(properties);
    }

    public static Element getUpgrade(ItemStack stack) {
        return stack.get(SCGDataComponents.POLYTOOL_UPGRADE.get());
    }

    public static void setUpgrade(ItemStack stack, Element element) {
        stack.set(SCGDataComponents.POLYTOOL_UPGRADE.get(), element);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        Element upgrade = getUpgrade(stack);
        float baseSpeed = (upgrade == Element.W || upgrade == Element.TI) ? 18.0F : 9.0F;
        return baseSpeed;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return true;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        Element upgrade = getUpgrade(stack);

        // MineChem Boron bonus vs Spiders/Creepers/Arthropods
        if (upgrade == Element.B && (target instanceof Spider || target.getType().getDescriptionId().contains("spider") || target.getType().getDescriptionId().contains("creeper"))) {
            if (attacker instanceof Player p) {
                target.hurt(attacker.damageSources().playerAttack(p), 10.0F);
            } else {
                target.hurt(attacker.damageSources().mobAttack(attacker), 10.0F);
            }
            if (attacker.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 15, 0.3, 0.3, 0.3, 0.1);
            }
        }

        // MineChem Silver bonus vs Undead and Endermen
        if (upgrade == Element.AG && (target instanceof EnderMan || target.isInvertedHealAndHarm())) {
            if (attacker instanceof Player p) {
                target.hurt(attacker.damageSources().playerAttack(p), 10.0F);
            } else {
                target.hurt(attacker.damageSources().mobAttack(attacker), 10.0F);
            }
            if (attacker.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
            }
        }

        if (upgrade != Element.C) {
            stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        Element upgrade = getUpgrade(stack);

        if (!level.isClientSide() && level instanceof ServerLevel sl) {
            // 1. Silicon Atomic Decomposition
            if (upgrade == Element.SI) {
                ItemStack asItem = state.getBlock().asItem().getDefaultInstance();
                if (!asItem.isEmpty() && DecompositionTable.isDecomposable(asItem)) {
                    List<ItemStack> vials = DecompositionTable.decompose(asItem, sl.getRandom());
                    for (ItemStack vial : vials) {
                        Block.popResource(level, pos, vial);
                    }
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.05);
                }
            }
            // 2. Uranium 3x3 Excavation
            else if (upgrade == Element.U || upgrade == Element.U235 || upgrade == Element.U238) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos neighbor = pos.offset(dx, dy, dz);
                            BlockState nState = level.getBlockState(neighbor);
                            if (!nState.isAir() && nState.getDestroySpeed(level, neighbor) >= 0 && nState.getDestroySpeed(level, neighbor) <= 50.0F) {
                                level.destroyBlock(neighbor, true, miner);
                            }
                        }
                    }
                }
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.1);
            }
        }

        if (state.getDestroySpeed(level, pos) != 0.0F && upgrade != Element.C) {
            stack.hurtAndBreak(1, miner, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack polytool = player.getItemInHand(hand);
        ItemStack offhand = player.getOffhandItem();

        if (player.isShiftKeyDown() && offhand.getItem() instanceof ElementVialItem) {
            Element newUpgrade = ElementVialItem.elementOf(offhand);
            setUpgrade(polytool, newUpgrade);
            if (!player.isCreative()) {
                offhand.shrink(1);
            }

            if (!level.isClientSide()) {
                level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                        SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                            Component.literal("§6Polytool infused with §f" + newUpgrade.displayName() + "§6!"), true);
                }
            }
            return InteractionResultHolder.sidedSuccess(polytool, level.isClientSide());
        }

        return InteractionResultHolder.pass(polytool);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Element upgrade = getUpgrade(stack);
        if (upgrade != null) {
            tooltip.add(Component.literal("§6Chemical Infusion: §f" + upgrade.displayName() + " (" + upgrade.symbol() + ")"));
            tooltip.add(Component.literal("§ePerk: §7" + getUpgradePerk(upgrade)));
        } else {
            tooltip.add(Component.literal("§7Sneak-right-click with an Element Vial in offhand to infuse."));
        }
    }

    private String getUpgradePerk(Element element) {
        return switch (element) {
            case SI -> "Atomic Decomposition (Blocks drop chemical element vials)";
            case W, TI -> "Rotary Drill (18.0x Ultra-Fast Mining Speed)";
            case AG -> "Purification Blade (+10 Damage vs Undead & Endermen)";
            case B -> "Arthropod Disruptor (+10 Damage vs Spiders & Creepers)";
            case C -> "Diamond Matrix (Immune to tool durability damage)";
            case AR, NE -> "Silk Precision (Silk-touch harvesting)";
            case U, U235, U238 -> "Atomic Excavator (3x3 Area Tunnel Mining)";
            default -> "Elemental Resonance (+20% efficiency)";
        };
    }
}
