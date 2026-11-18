package com.shadowclangaming.esper;

import net.minecraft.nbt.CompoundTag;
import java.util.*;

public final class EsperData {
    public static final com.mojang.serialization.Codec<EsperData> CODEC = net.minecraft.nbt.CompoundTag.CODEC.xmap(EsperData::load, EsperData::save);
    private EsperCategory category;
    private int level; // 1-5
    private int xp; // internal esper xp (from Ability Chamber)
    private final Set<String> unlocked = new LinkedHashSet<>();
    private final java.util.Map<String, Long> cooldowns = new java.util.LinkedHashMap<>(); // server ticks per skill, survives relog (fixes persistentData reset exploit)

    public EsperData(EsperCategory category, int level){
        this.category=category; this.level=level;
        // auto-unlock first skill
        if(!category.skills().isEmpty()) unlocked.add(category.skills().get(0));
    }
    public EsperData(){ this(EsperCategory.ELECTROMASTER,1); }
    public long cooldownAt(String skillId){ return cooldowns.getOrDefault(skillId, 0L); }
    public void setCooldown(String skillId, long tick){ cooldowns.put(skillId, tick); }

    public EsperCategory category(){ return category; }
    public int level(){ return level; }
    public int xp(){ return xp; }
    public Set<String> unlocked(){ return Collections.unmodifiableSet(unlocked); }

    public void setCategory(EsperCategory c){ this.category=c; unlocked.clear(); if(!c.skills().isEmpty()) unlocked.add(c.skills().get(0)); level=1; xp=0; }
    public void addXp(int v){ xp+=v; while(xp>= xpForNext() && level<5){ xp-=xpForNext(); level++; // unlock next skill on level up
            int idx = (level-1)*2;
            if(idx < category.skills().size()) unlocked.add(category.skills().get(idx));
            if(idx+1 < category.skills().size()) unlocked.add(category.skills().get(idx+1));
        } }
    public int xpForNext(){ return level*25; } // less grindy: 25,50,75,100 to L5
    public boolean unlock(String skillId, int vanillaLevels){
        if(unlocked.contains(skillId)) return false;
        if(!category.skills().contains(skillId)) return false;
        unlocked.add(skillId); return true;
    }

    public CompoundTag save(){
        CompoundTag t=new CompoundTag(); t.putString("cat", category.id()); t.putInt("lvl", level); t.putInt("xp", xp);
        t.putInt("uc", unlocked.size()); int i=0; for(String s:unlocked) t.putString("u"+i++, s);
        int j=0; t.putInt("cdc", cooldowns.size()); for(var e:cooldowns.entrySet()){ t.putString("cdk"+j, e.getKey()); t.putLong("cdv"+j, e.getValue()); j++; }
        return t;
    }
    public static EsperData load(CompoundTag t){
        EsperCategory c = EsperCategory.byId(t.getString("cat"));
        int lvl = t.getInt("lvl"); int xp=t.getInt("xp");
        EsperData d=new EsperData(c, Math.max(1,lvl)); d.xp=xp; d.unlocked.clear();
        int uc=t.getInt("uc"); for(int i=0;i<uc;i++) d.unlocked.add(t.getString("u"+i));
        if(d.unlocked.isEmpty() && !c.skills().isEmpty()) d.unlocked.add(c.skills().get(0));
        int cdc=t.getInt("cdc"); for(int k=0;k<cdc;k++) d.cooldowns.put(t.getString("cdk"+k), t.getLong("cdv"+k));
        return d;
    }
}
