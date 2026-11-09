package com.shadowclangaming.item;

import com.shadowclangaming.chem.Element;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.registry.SCGItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * A bucket containing liquid or concentrated chemical elements.
 *
 * <p>Inspired by the classic MineChem chemical fluid buckets:
 * <ul>
 *   <li>Contains 8 units of any chemical element (e.g. Liquid Nitrogen, Mercury, Chlorine, Uranium).</li>
 *   <li>Pouring in the world triggers authentic chemical reactions:
 *     <ul>
 *       <li><b>Liquid Nitrogen / Oxygen (N, O):</b> Rapid cryogenic freezing (freezes water into ice, freezes mobs).</li>
 *       <li><b>Chlorine / Fluorine / Acid (Cl, F, S):</b> Caustic acid burn (dissolves plant matter, caustic acid damage).</li>
 *       <li><b>Alkali Metals (Na, K, Cs):</b> Violent exothermic water reaction (detonates on contact with water/rain).</li>
 *       <li><b>Radioactive Elements (U, Pu, Ra, Po):</b> Acute radioactive contamination pool.</li>
 *       <li><b>Mercury / Lead (Hg, Pb):</b> Dense neurotoxic pool (heavy poison and slowness).</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class ChemicalBucketItem extends Item {

    public ChemicalBucketItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Element element) {
        ItemStack stack = new ItemStack(SCGItems.CHEMICAL_BUCKET.get());
        stack.set(SCGDataComponents.ELEMENT.get(), element);
        return stack;
    }

    public static Element elementOf(ItemStack stack) {
        Element e = stack.get(SCGDataComponents.ELEMENT.get());
        return e == null ? Element.H : e;
    }

    @Override
    public Component getName(ItemStack stack) {
        Element element = stack.get(SCGDataComponents.ELEMENT.get());
        if (element == null) {
            return Component.translatable("item.shadowclangaming.chemical_bucket.empty");
        }
        return Component.translatable("item.shadowclangaming.chemical_bucket.named",
                Component.translatable(element.translationKey()));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        Player player = context.getPlayer();
        ItemStack held = context.getItemInHand();
        Element element = elementOf(held);

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            triggerChemicalSpill(serverLevel, pos, element, player);
            if (player != null && !player.isCreative()) {
                context.getItemInHand().shrink(1);
                ItemStack emptyBucket = new ItemStack(Items.BUCKET);
                if (!player.getInventory().add(emptyBucket)) {
                    player.drop(emptyBucket, false);
                }
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        BlockPos pos = player.blockPosition();
        Element element = elementOf(held);

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            triggerChemicalSpill(serverLevel, pos, element, player);
            if (!player.isCreative()) {
                held.shrink(1);
                ItemStack emptyBucket = new ItemStack(Items.BUCKET);
                if (!player.getInventory().add(emptyBucket)) {
                    player.drop(emptyBucket, false);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
    }

    private void triggerChemicalSpill(ServerLevel level, BlockPos pos, Element element, Player user) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);

        // 1. Cryogenic elements (N, O, He, Ne, Ar)
        if (element == Element.N || element == Element.O || element == Element.HE || element == Element.NE || element == Element.AR) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.2F, 0.8F);
            level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 40, 1.5, 0.5, 1.5, 0.05);

            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
                BlockState state = level.getBlockState(p);
                if (state.is(Blocks.WATER)) {
                    level.setBlockAndUpdate(p, Blocks.ICE.defaultBlockState());
                } else if (state.is(Blocks.FIRE)) {
                    level.removeBlock(p, false);
                }
            }
            AABB box = new AABB(pos).inflate(3.0);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
                target.setTicksFrozen(200);
                target.hurt(level.damageSources().freeze(), 6.0F);
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 160, 3));
            }
            return;
        }

        // 2. Corrosive / Acid elements (Cl, F, S, Br)
        if (element == Element.CL || element == Element.F || element == Element.S || element == Element.BR) {
            level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.2F, 1.4F);
            level.sendParticles(ParticleTypes.ITEM_SLIME, x, y, z, 35, 1.2, 0.4, 1.2, 0.05);

            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                BlockState state = level.getBlockState(p);
                if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) {
                    level.setBlockAndUpdate(p, Blocks.COARSE_DIRT.defaultBlockState());
                } else if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)) {
                    level.removeBlock(p, false);
                }
            }
            AABB box = new AABB(pos).inflate(2.5);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
                target.hurt(level.damageSources().magic(), 8.0F);
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.POISON, 120, 1));
            }
            return;
        }

        // 3. Alkali metals (Na, K, Cs, Fr, Li) - Violent water reaction
        if (element == Element.NA || element == Element.K || element == Element.CS || element == Element.FR || element == Element.LI) {
            boolean hasWater = level.isRainingAt(pos) || level.getBlockState(pos).is(Blocks.WATER) || level.getBlockState(pos.below()).is(Blocks.WATER);
            if (hasWater) {
                level.explode(null, x, y, z, 3.5F, Level.ExplosionInteraction.MOB);
            } else {
                level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.sendParticles(ParticleTypes.LAVA, x, y, z, 20, 0.8, 0.4, 0.8, 0.1);
                AABB box = new AABB(pos).inflate(2.0);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
                    target.setRemainingFireTicks(100);
                    target.hurt(level.damageSources().onFire(), 5.0F);
                }
            }
            return;
        }

        // 4. Radioactive elements (U, Pu, Ra, Po, Th)
        if (element.radioactive()) {
            level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.6F);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 40, 1.5, 0.5, 1.5, 0.1);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 20, 1.0, 0.3, 1.0, 0.05);

            AABB box = new AABB(pos).inflate(4.0);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
                target.hurt(level.damageSources().magic(), 6.0F);
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.WITHER, 160, 1));
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.GLOWING, 200, 0));
            }
            return;
        }

        // 5. Heavy metals & neurotoxins (Hg, Pb, As)
        if (element == Element.HG || element == Element.PB || element == Element.AS) {
            level.playSound(null, pos, SoundEvents.SPLASH_POTION_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
            level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 30, 1.2, 0.3, 1.2, 0.05);

            AABB box = new AABB(pos).inflate(3.0);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.POISON, 200, 2));
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 200, 2));
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.CONFUSION, 160, 0));
            }
            return;
        }

        // Default spill
        level.sendParticles(ParticleTypes.SPLASH, x, y, z, 25, 0.8, 0.3, 0.8, 0.1);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Element element = stack.get(SCGDataComponents.ELEMENT.get());
        if (element == null) return;

        tooltip.add(Component.translatable("tooltip.shadowclangaming.element.symbol",
                element.symbol(), element.atomicNumber()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.chemical_bucket.capacity", 8)
                .withStyle(ChatFormatting.DARK_AQUA));
        if (element.radioactive()) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.chemical_bucket.radioactive")
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}
