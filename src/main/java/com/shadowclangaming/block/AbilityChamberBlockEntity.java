package com.shadowclangaming.block;

import com.shadowclangaming.esper.EsperData;
import com.shadowclangaming.esper.EsperHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AbilityChamberBlockEntity extends BlockEntity {
    private int storedLevels = 0;
    public AbilityChamberBlockEntity(BlockPos p, BlockState s){ super(com.shadowclangaming.registry.SCGBlockEntities.ABILITY_CHAMBER.get(), p, s); }

    public int storedLevels(){ return storedLevels; }
    public void addLevels(int v){ storedLevels+=v; setChanged(); }

    /** Player spends 1 vanilla XP level to gain 10 esper XP (less grindy than AcademyCraft). */
    public boolean upgrade(Player player){
        // AcademyCraft XP dupe fix: check creative, check level, deduct atomically, prevent lag double-click
        if(player.isCreative()) return false;
        if(player.experienceLevel < 1 && player.totalExperience < getXpForLevel(1)) return false;
        // Atomically consume 1 level only if still >=1 after sync (prevents lag dupe where two clicks both see 1 level)
        if(!tryConsumeLevel(player)) return false;
        var data = EsperHolder.get(player);
        data.addXp(10);
        EsperHolder.set(player, data);
        storedLevels++;
        setChanged();
        return true;
    }
    /** Craftable skill change: consume skill card and vanilla level to unlock. */
    public boolean unlockSkill(Player player, String skillId){
        var data = EsperHolder.get(player);
        if(data.unlocked().contains(skillId)) return false;
        if(player.isCreative()) return false;
        if(player.experienceLevel < 1) return false;
        // Must have skill card in inventory - prevents unlock without craft (fixes Blood Magic will dupe pattern)
        boolean hasCard = false;
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            var s = player.getInventory().getItem(i);
            if(s.is(com.shadowclangaming.registry.SCGItems.SKILL_CARD_BLANK.get()) ||
               s.is(com.shadowclangaming.registry.SCGItems.SKILL_CARD_ELECTRO.get()) ||
               s.is(com.shadowclangaming.registry.SCGItems.SKILL_CARD_TELEPORT.get()) ||
               s.is(com.shadowclangaming.registry.SCGItems.SKILL_CARD_VECTOR.get()) ||
               s.is(com.shadowclangaming.registry.SCGItems.SKILL_CARD_MELT.get()) ||
               s.is(com.shadowclangaming.registry.SCGItems.SKILL_CARD_GRAVITY.get())){
                s.shrink(1); hasCard=true; break;
            }
        }
        if(!hasCard) return false;
        if(!tryConsumeLevel(player)) {
            // refund card if level consume failed (lag safety)
            player.getInventory().add(new net.minecraft.world.item.ItemStack(com.shadowclangaming.registry.SCGItems.SKILL_CARD_BLANK.get()));
            return false;
        }
        boolean ok = data.unlock(skillId, 1);
        if(ok) EsperHolder.set(player, data);
        else {
            // refund on failure (already unlocked edge)
            player.giveExperienceLevels(1);
            player.getInventory().add(new net.minecraft.world.item.ItemStack(com.shadowclangaming.registry.SCGItems.SKILL_CARD_BLANK.get()));
        }
        setChanged();
        return ok;
    }
    private static boolean tryConsumeLevel(Player player){
        // Matches vanilla negative-level handling but prevents negative total XP bug (HBM/IC2 XP dupe)
        int lvl = player.experienceLevel;
        if(lvl < 1) return false;
        // Use vanilla method which correctly handles progress -> level math
        player.giveExperienceLevels(-1);
        // Verify it actually went down (lag/sync safety)
        return player.experienceLevel == lvl - 1 || (lvl == 1 && player.experienceLevel == 0);
    }
    private static int getXpForLevel(int level){
        // vanilla XP curve: 7 + level*2 for first 16, etc. Simplified for check
        return level <= 16 ? 7 + level*2 : level <= 31 ? 9 + level*3 : 25 + level*7;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r){ super.saveAdditional(tag,r); tag.putInt("stored", storedLevels); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r){ super.loadAdditional(tag,r); storedLevels=tag.getInt("stored"); }
}
