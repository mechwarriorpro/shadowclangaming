package com.shadowclangaming.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class NuclearReactorBlockEntity extends BlockEntity implements net.neoforged.neoforge.energy.IEnergyStorage {
    private int heat=0, maxHeat=1000;
    private int energy=0;
    private boolean rodsIn=true;
    private int burnTicks=0;
    public NuclearReactorBlockEntity(BlockPos pos, BlockState state){ super(com.shadowclangaming.registry.SCGBlockEntities.NUCLEAR_REACTOR.get(), pos, state); }
    public int heat(){return heat;}
    public int maxHeat(){return maxHeat;}
    public int energy(){return energy;}
    public boolean rodsIn(){return rodsIn;}
    public void toggleRods(){ rodsIn=!rodsIn; setChanged(); }
    private boolean exploded=false; // ICBM infinite recursion fix
    public void tick(){
        if(level==null||level.isClientSide || exploded) return;
        // HBM radiation hazmat + wall shielding: ray-trace from reactor centre to player eye; a solid block blocks it
        if(!rodsIn && level.getGameTime() % 20 == 0){
            net.minecraft.world.phys.Vec3 from = net.minecraft.world.phys.Vec3.atCenterOf(worldPosition);
            for(var p: level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(8))){
                boolean hasHazmat = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(com.shadowclangaming.registry.SCGItems.INFAMY_HELMET.get()) ||
                                    p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(com.shadowclangaming.registry.SCGItems.INFAMY_CHESTPLATE.get());
                if(hasHazmat || p.isCreative() || p.isSpectator()) continue;
                var hit = level.clip(new net.minecraft.world.level.ClipContext(from, p.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
                if(hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue; // wall blocks radiation
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 100, 0));
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 80, 0));
            }
        }
        if(!rodsIn){
            heat = Math.min(maxHeat, heat+8);
            energy = Math.min(1000000, energy+120);
            burnTicks++;
            if(heat >= maxHeat && !exploded){
                exploded=true; // prevent re-entry if block update reschedules tick (ICBM recursion)
                // Remove block first, then explode, then stop ticking (HBM missing heat check fix: ensure exploded only once)
                level.removeBlock(worldPosition, false);
                if(level instanceof net.minecraft.server.level.ServerLevel sl){
                    // Radiation burst: wall-shielded, hazmat-checked
                    net.minecraft.world.phys.Vec3 fromB = net.minecraft.world.phys.Vec3.atCenterOf(worldPosition);
                    for(var e: sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(12))){
                        boolean hasHazmat2 = e instanceof net.minecraft.world.entity.player.Player pl &&
                            pl.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(com.shadowclangaming.registry.SCGItems.INFAMY_HELMET.get()) &&
                            pl.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(com.shadowclangaming.registry.SCGItems.INFAMY_CHESTPLATE.get());
                        if(hasHazmat2) continue;
                        var hit2 = sl.clip(new net.minecraft.world.level.ClipContext(fromB, e.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, e));
                        if(hit2.getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
                        e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 300, 1));
                    }
                    sl.explode(null, worldPosition.getX()+0.5, worldPosition.getY()+0.5, worldPosition.getZ()+0.5, 8, net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);
                }
                return; // don't push FE after meltdown
            }
        } else {
            heat = Math.max(0, heat-6);
        }
        // push FE to neighbors
        for(var dir: net.minecraft.core.Direction.values()){
            var cap = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, worldPosition.relative(dir), dir.getOpposite());
            if(cap!=null && cap.canReceive()){
                int sent = cap.receiveEnergy(Math.min(800, energy), false);
                energy-=sent;
                if(energy<=0) break;
            }
        }
        setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r){ super.saveAdditional(tag,r); tag.putInt("heat",heat); tag.putInt("energy",energy); tag.putBoolean("rods",rodsIn);}
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r){ super.loadAdditional(tag,r); heat=tag.getInt("heat"); energy=tag.getInt("energy"); rodsIn=tag.getBoolean("rods");}
    // IEnergyStorage as buffer for FE
    public int receiveEnergy(int max, boolean sim){ int r=Math.min(1000000-energy, max); if(!sim) energy+=r; return r; }
    public int extractEnergy(int max, boolean sim){ int e=Math.min(energy, max); if(!sim) energy-=e; return e; }
    public int getEnergyStored(){return energy;}
    public int getMaxEnergyStored(){return 1000000;}
    public boolean canExtract(){return true;}
    public boolean canReceive(){return true;}
}
