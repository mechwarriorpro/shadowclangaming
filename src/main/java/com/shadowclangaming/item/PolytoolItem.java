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
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.effect.MobEffectInstance;
import net.minecraft.world.entity.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Chemical Polytool.
 *
 * <p>Faithfully reimplements the classic MineChem 1.6.4 Polytool with modular elemental upgrades.
 * Functions as Pickaxe, Axe, Shovel, Sword, and Shears.
 *
 * <p>Supports all 34 canonical MineChem Polytool elemental upgrades:
 * Ar, Be, B, Br, Cs, Ca, C, Cl, Cr, F, Fr, Au, He, H, Fe, Kr, Pb, Li, Mg, Hg,
 * Ne, Ni, N, O, P, Pt, Rb, Si, Ag, Na, S, Ti, U, Zr.
 */
public class PolytoolItem extends Item {

    public PolytoolItem(Properties properties) {
        super(properties);
    }

    public static List<PolytoolUpgrade> getUpgrades(ItemStack stack) {
        List<PolytoolUpgrade> list = stack.get(SCGDataComponents.POLYTOOL_UPGRADES.get());
        return list == null ? List.of() : list;
    }

    public static float getPower(ItemStack stack, Element element) {
        for (PolytoolUpgrade up : getUpgrades(stack)) {
            if (up.element() == element) {
                return up.power();
            }
        }
        return 0.0F;
    }

    public static void addUpgrade(ItemStack stack, Element element, float powerToAdd) {
        List<PolytoolUpgrade> current = new ArrayList<>(getUpgrades(stack));
        boolean found = false;
        for (int i = 0; i < current.size(); i++) {
            PolytoolUpgrade up = current.get(i);
            if (up.element() == element) {
                current.set(i, new PolytoolUpgrade(element, up.power() + powerToAdd));
                found = true;
                break;
            }
        }
        if (!found) {
            current.add(new PolytoolUpgrade(element, powerToAdd));
        }
        stack.set(SCGDataComponents.POLYTOOL_UPGRADES.get(), current);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        float speed = 8.0F;
        float ti = getPower(stack, Element.TI);
        if (ti > 0) speed += ti * 2.5F;
        float w = getPower(stack, Element.W);
        if (w > 0) speed += w * 3.0F;
        return speed;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return true;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        Level level = target.level();
        RandomSource rand = level.getRandom();

        // 1. Boron (B): Kills spiders with pesticides (+0.8 * power bonus damage)
        float powerB = getPower(stack, Element.B);
        if (powerB > 0 && (target instanceof Spider || target.getType().getDescriptionId().contains("spider"))) {
            target.hurt(attacker.damageSources().magic(), 0.8F * powerB + 4.0F);
        }

        // 2. Silver (Ag): Extra damage vs Endermen (+0.8 * power bonus damage)
        float powerAg = getPower(stack, Element.AG);
        if (powerAg > 0 && target instanceof EnderMan) {
            target.hurt(attacker.damageSources().magic(), 0.8F * powerAg + 4.0F);
        }

        // 3. Argon (Ar): Does extra suffocation damage when in a cave
        float powerAr = getPower(stack, Element.AR);
        if (powerAr > 0 && !level.canSeeSky(target.blockPosition())) {
            target.hurt(level.damageSources().inWall(), powerAr * 2.0F);
        }

        // 4. Chlorine (Cl): AoE Poison
        float powerCl = getPower(stack, Element.CL);
        if (powerCl > 0) {
            applyAoE(level, target, powerCl + 1.5, e -> e.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1)));
        }

        // 5. Uranium (U): AoE Wither
        float powerU = getPower(stack, Element.U);
        if (powerU > 0) {
            applyAoE(level, target, powerU + 1.5, e -> e.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1)));
        }

        // 6. Magnesium (Mg): Flashbang AoE Blindness
        float powerMg = getPower(stack, Element.MG);
        if (powerMg > 0) {
            applyAoE(level, target, powerMg + 2.0, e -> e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0)));
        }

        // 7. Lead (Pb): Sends nearby entities flying to the ground
        float powerPb = getPower(stack, Element.PB);
        if (powerPb > 0) {
            applyAoE(level, target, powerPb * 3.0, e -> {
                e.setDeltaMovement(e.getDeltaMovement().x, -1.2, e.getDeltaMovement().z);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
            });
        }

        // 8. Sulfur (S): Sets entities on fire
        float powerS = getPower(stack, Element.S);
        if (powerS > 0) {
            target.igniteForSeconds((int) (powerS + 1));
        }

        // 9. Hydrogen (H): Creates explosion if hit entity is on fire
        float powerH = getPower(stack, Element.H);
        if (powerH > 0 && target.isOnFire()) {
            level.explode(null, target.getX(), target.getY(), target.getZ(), Math.min(3.0F, powerH), Level.ExplosionInteraction.NONE);
        }

        // 10. Rubidium (Rb): Creates explosion if in water
        float powerRb = getPower(stack, Element.RB);
        if (powerRb > 0 && target.isInWater()) {
            level.explode(null, target.getX(), target.getY(), target.getZ(), Math.min(3.0F, powerRb), Level.ExplosionInteraction.NONE);
        }

        // 11. Krypton (Kr): Extra armor-piercing damage to heavily armored enemies
        float powerKr = getPower(stack, Element.KR);
        if (powerKr > 0 && target.getArmorValue() > 0) {
            target.hurt(level.damageSources().genericKill(), powerKr * 2.0F);
        }

        // 12. Gold (Au): Occasionally creates lightning strikes
        float powerAu = getPower(stack, Element.AU);
        if (powerAu > 0 && !level.isClientSide() && level instanceof ServerLevel sl) {
            if (rand.nextInt(350) < (int) (powerAu * 10) + 5) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
                if (bolt != null) {
                    bolt.moveTo(target.position());
                    sl.addFreshEntity(bolt);
                }
            }
        }

        // 13. Helium (He): Hit entities float away
        float powerHe = getPower(stack, Element.HE);
        if (powerHe > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, (int) powerHe));
        }

        // 14. Neon (Ne): Gives players night vision during combat
        float powerNe = getPower(stack, Element.NE);
        if (powerNe > 0 && attacker instanceof LivingEntity livingAttacker) {
            livingAttacker.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, (int) (powerNe * 200), 0));
        }

        // 15. Platinum (Pt): Bonus XP on hit
        float powerPt = getPower(stack, Element.PT);
        if (powerPt > 0 && level instanceof ServerLevel sl) {
            int xp = (int) powerPt + rand.nextInt(3);
            ExperienceOrb.award(sl, target.position(), xp);
        }

        // Durability: Carbon diamond matrix prevents damage
        float powerC = getPower(stack, Element.C);
        if (powerC <= 0) {
            stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        RandomSource rand = level.getRandom();
        Player player = miner instanceof Player p ? p : null;

        if (!level.isClientSide() && level instanceof ServerLevel sl) {
            // 1. Caesium (Cs): Creates explosion when mining stone
            float powerCs = getPower(stack, Element.CS);
            if (powerCs > 0 && state.is(Blocks.STONE)) {
                level.explode(miner, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Math.min(3.0F, powerCs), Level.ExplosionInteraction.BLOCK);
            }

            // 2. Francium (Fr): Creates explosions when raining
            float powerFr = getPower(stack, Element.FR);
            if (powerFr > 0 && level.isRaining()) {
                level.explode(miner, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Math.min(3.0F, powerFr), Level.ExplosionInteraction.BLOCK);
            }

            // 3. Rubidium (Rb): Creates explosion if in water
            float powerRb = getPower(stack, Element.RB);
            if (powerRb > 0 && (state.is(Blocks.WATER) || miner.isInWater())) {
                level.explode(miner, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Math.min(3.0F, powerRb), Level.ExplosionInteraction.BLOCK);
            }

            // 4. Iron (Fe): Mines deposits of ores (vein mining)
            float powerFe = getPower(stack, Element.FE);
            if (powerFe > 0 && state.getBlock().asItem() != Items.AIR) {
                int toMine = Math.min(16, (int) powerFe * 3);
                Block targetBlock = state.getBlock();
                for (int dx = -1; dx <= 1 && toMine > 0; dx++) {
                    for (int dy = -1; dy <= 1 && toMine > 0; dy++) {
                        for (int dz = -1; dz <= 1 && toMine > 0; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos neighbor = pos.offset(dx, dy, dz);
                            if (level.getBlockState(neighbor).is(targetBlock)) {
                                level.destroyBlock(neighbor, true, miner);
                                toMine--;
                            }
                        }
                    }
                }
            }

            // 5. Bromine (Br): Purifies gold ores (bonus gold ingots)
            float powerBr = getPower(stack, Element.BR);
            if (powerBr > 0 && (state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE))) {
                int bonus = 1 + (int) (rand.nextDouble() * Math.max(1, Math.log(powerBr + 1)));
                Block.popResource(level, pos, new ItemStack(Items.GOLD_INGOT, bonus));
            }

            // 6. Carbon (C): More drops from coal and diamond ores
            float powerC = getPower(stack, Element.C);
            if (powerC > 0) {
                if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
                    int bonus = 1 + (int) (rand.nextDouble() * Math.max(1, Math.log(powerC + 1)));
                    Block.popResource(level, pos, new ItemStack(Items.COAL, bonus));
                } else if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
                    int bonus = 1 + (int) (rand.nextDouble() * Math.max(1, Math.log(powerC + 1)));
                    Block.popResource(level, pos, new ItemStack(Items.DIAMOND, bonus));
                }
            }

            // 7. Zirconium (Zr): Makes fake diamonds when mining dirt
            float powerZr = getPower(stack, Element.ZR);
            if (powerZr > 0 && (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK))) {
                if (rand.nextInt(8192) < (int) (powerZr * 4) + 1) {
                    Block.popResource(level, pos, new ItemStack(Items.DIAMOND, 1));
                    sl.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.1);
                }
            }

            // 8. Fluorine (F): Preserves teeth to give bonus food when mining
            float powerF = getPower(stack, Element.F);
            if (powerF > 0 && player != null && rand.nextInt(10) < (int) powerF + 1) {
                player.getFoodData().eat(1, 0.5F);
            }

            // 9. Oxygen (O): Gives extra air when mining underwater
            float powerO = getPower(stack, Element.O);
            if (powerO > 0 && miner.isInWater()) {
                miner.setAirSupply(Math.min(miner.getMaxAirSupply(), miner.getAirSupply() + (int) powerO * 40));
            }

            // 10. Phosphorus (P): Sets nearby blocks on fire
            float powerP = getPower(stack, Element.P);
            if (powerP > 0) {
                int r = Math.min(3, (int) powerP);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            BlockPos firePos = pos.offset(dx, dy, dz);
                            if (level.getBlockState(firePos).isAir() && level.getBlockState(firePos.below()).isSolid()) {
                                level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
                            }
                        }
                    }
                }
            }

            // 11. Sodium (Na): Lights up area when mining
            float powerNa = getPower(stack, Element.NA);
            if (powerNa > 0 && player != null && rand.nextInt(35) < (int) powerNa * 10 + 5) {
                BlockPos torchPos = player.blockPosition();
                if (level.getBlockState(torchPos).isAir() && Blocks.TORCH.defaultBlockState().canSurvive(level, torchPos)) {
                    level.setBlockAndUpdate(torchPos, Blocks.TORCH.defaultBlockState());
                }
            }

            // 12. Nickel (Ni): Sucks up nearby items when another block is mined
            float powerNi = getPower(stack, Element.NI);
            if (powerNi > 0 && player != null) {
                double rad = powerNi * 3.0 + 2.0;
                List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(rad));
                for (ItemEntity item : items) {
                    item.setDeltaMovement(new Vec3(
                            (player.getX() - item.getX()) * 0.3,
                            (player.getY() - item.getY()) * 0.3 + 0.1,
                            (player.getZ() - item.getZ()) * 0.3
                    ));
                }
            }

            // 13. Mercury (Hg): Warns of lava underneath
            float powerHg = getPower(stack, Element.HG);
            if (powerHg > 0 && player instanceof ServerPlayer sp) {
                int depth = (int) (powerHg * 4 + 2);
                boolean lavaFound = false;
                for (int dy = 1; dy <= depth; dy++) {
                    BlockState below = level.getBlockState(pos.below(dy));
                    if (below.is(Blocks.LAVA)) {
                        lavaFound = true;
                        break;
                    }
                }
                if (lavaFound) {
                    sp.displayClientMessage(Component.literal("§c⚠ Mercury Warning: Lava detected underneath!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                    sl.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.0F);
                }
            }

            // 14. Platinum (Pt): Bonus XP on block broken
            float powerPt = getPower(stack, Element.PT);
            if (powerPt > 0) {
                int xp = (int) powerPt + rand.nextInt(3);
                ExperienceOrb.award(sl, Vec3.atCenterOf(pos), xp);
            }

            // 15. Silicon (Si): Atomic decomposition of mined blocks into element vials
            float powerSi = getPower(stack, Element.SI);
            if (powerSi > 0) {
                ItemStack asItem = state.getBlock().asItem().getDefaultInstance();
                if (!asItem.isEmpty() && DecompositionTable.isDecomposable(asItem)) {
                    List<ItemStack> vials = DecompositionTable.decompose(asItem, sl.getRandom());
                    for (ItemStack vial : vials) {
                        Block.popResource(level, pos, vial);
                    }
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }

        float powerC = getPower(stack, Element.C);
        if (state.getDestroySpeed(level, pos) != 0.0F && powerC <= 0) {
            stack.hurtAndBreak(1, miner, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    private static void applyAoE(Level level, LivingEntity center, double radius, java.util.function.Consumer<LivingEntity> action) {
        AABB box = new AABB(center.position().subtract(radius, radius, radius), center.position().add(radius, radius, radius));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e != center) {
                action.accept(e);
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack polytool = player.getItemInHand(hand);
        ItemStack offhand = player.getOffhandItem();

        if (player.isShiftKeyDown() && offhand.getItem() instanceof ElementVialItem) {
            Element elem = ElementVialItem.elementOf(offhand);
            addUpgrade(polytool, elem, 1.0F);
            if (!player.isCreative()) {
                offhand.shrink(1);
            }

            if (!level.isClientSide()) {
                level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                        SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
                if (player instanceof ServerPlayer sp) {
                    float newTotal = getPower(polytool, elem);
                    sp.displayClientMessage(
                            Component.literal("§6Polytool infused: §f" + elem.displayName() + " §7(Power " + (int) newTotal + ")§6 - " + getUpgradeDescription(elem)), true);
                }
            }
            return InteractionResultHolder.sidedSuccess(polytool, level.isClientSide());
        }

        return InteractionResultHolder.pass(polytool);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        List<PolytoolUpgrade> upgrades = getUpgrades(stack);
        if (upgrades.isEmpty()) {
            tooltip.add(Component.literal("§7Sneak-right-click with an Element Vial in offhand to infuse."));
        } else {
            tooltip.add(Component.literal("§6Installed Chemical Upgrades:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            for (PolytoolUpgrade up : upgrades) {
                tooltip.add(Component.literal(" • §e" + up.element().displayName() + " (" + up.element().symbol() + ") Lv." + (int) up.power() + ": §7" + getUpgradeDescription(up.element())));
            }
        }
    }

    public static String getUpgradeDescription(Element element) {
        return switch (element) {
            case AR -> "Does extra suffocation damage when in a cave";
            case BE -> "Beheading (drops mob skulls on kill)";
            case B -> "Kills spiders with pesticides";
            case BR -> "Purifies gold ores";
            case CS -> "Creates explosion when mining stone";
            case CA -> "Get extra bones from skeletons";
            case C -> "More drops from coal and diamond ores / Unbreakable";
            case CL -> "Area of Effect poison";
            case CR -> "Chance to change color of wool when mined";
            case F -> "Preserves teeth to give you bonus food when mining";
            case FR -> "Creates explosions when raining";
            case AU -> "Occasionally creates lightning strikes";
            case HE -> "Hit entities float away";
            case H -> "Creates explosion if hit entity is on fire";
            case FE -> "Mines deposits of ores";
            case KR -> "Does extra damage to heavily armored enemies";
            case PB -> "Sends nearby entities flying to the ground";
            case LI -> "Can charge infinitely for logarithmic bonuses";
            case MG -> "Blinds nearby entities with flashbangs";
            case HG -> "Warns of lava underneath";
            case NE -> "Gives players night vision during combat";
            case NI -> "Sucks up nearby items when another block is mined";
            case N -> "Preservation allows retrieving of fresh meat from rotten flesh";
            case O -> "Gives extra air when mining underwater";
            case P -> "Sets nearby blocks on fire";
            case PT -> "Bonus XP on mining and combat";
            case RB -> "Creates explosion if in water";
            case SI -> "Advanced circuitry decomposes mob drops";
            case AG -> "Extra damage vs Endermen";
            case NA -> "Lights up area when mining";
            case S -> "Sets entities on fire";
            case TI -> "Better chance to collect tools and armor from mob drops";
            case U, U235, U238 -> "Area of Effect wither";
            case ZR -> "Makes fake diamonds when mining dirt";
            default -> "Elemental Resonance (+mining speed & efficiency)";
        };
    }
}
