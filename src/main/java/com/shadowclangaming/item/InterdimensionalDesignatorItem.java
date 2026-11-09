package com.shadowclangaming.item;

import com.shadowclangaming.block.LaunchPadBlockEntity;
import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Interdimensional Designator — upgraded designator that can store dimension+coords and fire
 * silos that have the interdimensional chip upgrade.
 *
 * UX:
 *  - Right-click Launch Pad / cable / terminal -> bind frequency (same as normal, works cross-dim)
 *  - Shift + Right-click Block -> store that block's pos + current dimension as TARGET
 *  - Right-click air (no shift) -> fire at stored TARGET if present, else at look target in current dimension
 *  - Shift + Right-click air -> clear stored target
 *  - Sneak + use with pellet -> recharge (same as normal)
 */
public class InterdimensionalDesignatorItem extends Item {
    public static final int MAX_ENERGY = 120_000;
    public static final int ENERGY_PER_SHOT = 6_000;
    public static final int ENERGY_PER_PELLET = 15_000;
    public static final double RANGE = 512.0D;

    public InterdimensionalDesignatorItem(Properties properties) { super(properties); }

    public static int getEnergy(ItemStack s) {
        Integer v = s.get(SCGDataComponents.ENERGY.get());
        return v == null ? 0 : v;
    }
    public static void setEnergy(ItemStack s, int e) {
        s.set(SCGDataComponents.ENERGY.get(), Math.max(0, Math.min(MAX_ENERGY, e)));
    }
    public static boolean consumeEnergy(ItemStack s, int a) {
        int cur = getEnergy(s);
        if (cur < a) return false;
        setEnergy(s, cur - a);
        return true;
    }

    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM,
                (stack, ctx) -> new net.neoforged.neoforge.energy.IEnergyStorage() {
                    @Override public int receiveEnergy(int toReceive, boolean sim){ int cur=getEnergy(stack); int acc=Math.min(MAX_ENERGY-cur, Math.max(0,toReceive)); if(!sim&&acc>0)setEnergy(stack,cur+acc); return acc; }
                    @Override public int extractEnergy(int toExtract, boolean sim){ int cur=getEnergy(stack); int rem=Math.min(cur, Math.max(0,toExtract)); if(!sim&&rem>0)setEnergy(stack,cur-rem); return rem; }
                    @Override public int getEnergyStored(){ return getEnergy(stack); }
                    @Override public int getMaxEnergyStored(){ return MAX_ENERGY; }
                    @Override public boolean canExtract(){ return true; }
                    @Override public boolean canReceive(){ return true; }
                },
                com.shadowclangaming.registry.SCGItems.INTERDIMENSIONAL_DESIGNATOR.get());
    }

    // Target storage
    public static boolean hasTarget(ItemStack s) { return s.has(SCGDataComponents.TARGET_POS.get()); }
    public static BlockPos targetPos(ItemStack s) { return s.get(SCGDataComponents.TARGET_POS.get()); }
    public static String targetDimId(ItemStack s) { return s.get(SCGDataComponents.TARGET_DIMENSION.get()); }
    public static ResourceKey<Level> targetDim(ItemStack s) {
        String id = targetDimId(s);
        if (id == null) return null;
        try { return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id)); } catch(Exception e){ return null; }
    }
    public static void setTarget(ItemStack s, BlockPos pos, ResourceKey<Level> dim) {
        s.set(SCGDataComponents.TARGET_POS.get(), pos);
        s.set(SCGDataComponents.TARGET_DIMENSION.get(), dim.location().toString());
    }
    public static void clearTarget(ItemStack s) {
        s.remove(SCGDataComponents.TARGET_POS.get());
        s.remove(SCGDataComponents.TARGET_DIMENSION.get());
    }

    // channel reuse from TargetDesignatorItem
    public static int channelOf(ItemStack s){ Integer v=s.get(SCGDataComponents.FREQUENCY.get()); return v==null? com.shadowclangaming.computer.Channels.NONE : com.shadowclangaming.computer.Channels.sanitise(v); }
    public static boolean hasChannel(ItemStack s){ return com.shadowclangaming.computer.Channels.isSet(channelOf(s)); }
    public static void setChannel(ItemStack s,int ch){ s.set(SCGDataComponents.FREQUENCY.get(), com.shadowclangaming.computer.Channels.sanitise(ch)); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        ItemStack stack = ctx.getItemInHand();
        Player player = ctx.getPlayer();
        BlockEntity be = level.getBlockEntity(pos);

        // Shift-click block -> store target (any dimension)
        if (player != null && player.isShiftKeyDown()) {
            if (be instanceof LaunchPadBlockEntity) {
                // Shift-click pad still handled as binding? Prioritize target store for interdimensional: shift+pad = store pad's location? But we want shift+block = target. For pad, shift should still allow binding? We'll treat shift+pad as target store if sneaking, but also allow bind via non-shift. So shiftpad = store pad pos as target (weird). Better: shift+pad still stores target at pad? Let's allow.
            }
            BlockPos tpos = pos;
            ResourceKey<Level> dim = level.dimension();
            setTarget(stack, tpos, dim);
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_target_set", tpos.toShortString(), dim.location().toString()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Non-shift click on pad/cable/terminal -> bind frequency (cross-dim capable)
        if (!(be instanceof LaunchPadBlockEntity)) {
            int channel = be instanceof com.shadowclangaming.computer.NetworkCableBlockEntity c ? c.channel() : be instanceof com.shadowclangaming.computer.TerminalBlockEntity t ? t.frequency() : com.shadowclangaming.computer.Channels.NONE;
            if (!com.shadowclangaming.computer.Channels.isSet(channel)) return InteractionResult.PASS;
            setChannel(stack, channel);
            if (player != null && !level.isClientSide) player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_bound", channel).withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        LaunchPadBlockEntity pad = (LaunchPadBlockEntity) be;
        if (!pad.hasChannel()) {
            int mine = channelOf(stack);
            if (hasChannel(stack) && !level.isClientSide) { pad.setFrequency(mine); if(player!=null) player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_tuned_pad", mine).withStyle(ChatFormatting.GREEN), true); return InteractionResult.sidedSuccess(false); }
            if (player != null && !level.isClientSide) player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_no_channel").withStyle(ChatFormatting.RED), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        setChannel(stack, pad.frequency());
        if (player != null && !level.isClientSide) player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_bound", pad.frequency()).withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            // Shift + air: clear target, else recharge if sneaking with pellet? We unify: shift+air without target? Check pellet recharge first if no target? Actually sneaking air to recharge
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            // If has target, shift+air clears target
            if (hasTarget(stack)) {
                clearTarget(stack);
                player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_target_cleared").withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.success(stack);
            }
            // Otherwise recharge
            if (getEnergy(stack) >= MAX_ENERGY) { player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_full").withStyle(ChatFormatting.GRAY), true); return InteractionResultHolder.success(stack); }
            if (!consumeOnePellet(player)) { player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_no_pellet").withStyle(ChatFormatting.RED), true); return InteractionResultHolder.success(stack); }
            setEnergy(stack, getEnergy(stack)+ENERGY_PER_PELLET);
            player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_charged", getEnergy(stack), MAX_ENERGY).withStyle(ChatFormatting.GREEN), true);
            return InteractionResultHolder.success(stack);
        }

        if (level.isClientSide) return InteractionResultHolder.success(stack);
        int channel = channelOf(stack);
        if (!hasChannel(stack)) { player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_unbound").withStyle(ChatFormatting.RED), true); return InteractionResultHolder.fail(stack); }
        if (getEnergy(stack) < ENERGY_PER_SHOT) { player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_no_energy", getEnergy(stack), ENERGY_PER_SHOT).withStyle(ChatFormatting.RED), true); return InteractionResultHolder.fail(stack); }

        BlockPos target;
        String dimId;
        ResourceKey<Level> targetDim = null;
        if (hasTarget(stack)) {
            target = targetPos(stack);
            dimId = targetDimId(stack);
            try { targetDim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimId)); } catch(Exception e){ targetDim = level.dimension(); dimId = targetDim.location().toString(); }
        } else {
            target = resolveLookedAtBlock(player);
            dimId = level.dimension().location().toString();
            targetDim = level.dimension();
        }

        // Send to server with dimension
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.shadowclangaming.network.ScgNet.DesignatorFirePayload(channel, target.getX(), target.getY(), target.getZ(), dimId));

        consumeEnergy(stack, ENERGY_PER_SHOT);
        if (hasTarget(stack)) player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_transmit_interdim", channel, target.toShortString(), dimId).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        else player.displayClientMessage(Component.translatable("message.shadowclangaming.designator_transmit", channel).withStyle(ChatFormatting.GOLD), true);
        return InteractionResultHolder.success(stack);
    }

    private static BlockPos resolveLookedAtBlock(Player player) {
        HitResult hit = player.pick(RANGE, 0.0F, false);
        if (hit.getType()==HitResult.Type.BLOCK && hit instanceof BlockHitResult b) return b.getBlockPos();
        Vec3 end = player.getEyePosition().add(player.getLookAngle().scale(RANGE));
        return BlockPos.containing(end.x,end.y,end.z);
    }
    private static boolean consumeOnePellet(Player p){ for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack c=p.getInventory().getItem(i); if(c.getItem()==com.shadowclangaming.registry.SCGItems.RTG_PELLET.get()){c.shrink(1); return true;}} return false; }

    @Override public void appendHoverText(ItemStack s, TooltipContext ctx, List<Component> t, TooltipFlag f){
        int e=getEnergy(s); t.add(Component.translatable("tooltip.shadowclangaming.designator.energy", e, MAX_ENERGY).withStyle(e>=ENERGY_PER_SHOT?ChatFormatting.GREEN:ChatFormatting.RED));
        t.add(Component.translatable("tooltip.shadowclangaming.designator.shots", e/ENERGY_PER_SHOT).withStyle(ChatFormatting.DARK_GRAY));
        int ch=channelOf(s); if(!hasChannel(s)) t.add(Component.translatable("tooltip.shadowclangaming.designator.unbound").withStyle(ChatFormatting.GRAY)); else t.add(Component.translatable("tooltip.shadowclangaming.designator.channel", ch).withStyle(ChatFormatting.AQUA));
        if(hasTarget(s)) t.add(Component.translatable("tooltip.shadowclangaming.designator.target_dim", targetPos(s).toShortString(), targetDimId(s)).withStyle(ChatFormatting.LIGHT_PURPLE));
        else t.add(Component.translatable("tooltip.shadowclangaming.interdim_designator.no_target").withStyle(ChatFormatting.DARK_GRAY));
        t.add(Component.translatable("tooltip.shadowclangaming.interdim_designator.usage").withStyle(ChatFormatting.DARK_GRAY));
        t.add(Component.translatable("tooltip.shadowclangaming.interdim_designator.usage2").withStyle(ChatFormatting.DARK_PURPLE));
    }
    @Override public boolean isBarVisible(ItemStack s){ return true; }
    @Override public int getBarWidth(ItemStack s){ return Math.round(13.0F*getEnergy(s)/MAX_ENERGY); }
    @Override public int getBarColor(ItemStack s){ return 0xAA45FF; }
}
