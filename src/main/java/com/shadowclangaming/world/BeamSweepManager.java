package com.shadowclangaming.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Moving beam effect: beam sweeps across ground leaving 2-3 minute crack trail.
 * Tick-driven; visuals are the photoreal_beam volume + scorched ground trail that persists after sweep.
 */
public final class BeamSweepManager {
    private BeamSweepManager(){}
    private static final List<Sweep> ACTIVE = new ArrayList<>();

    private static class Sweep {
        ServerLevel level;
        Vec3 start, end;
        Vec3 current;
        int age, lifetime; // sweep travel time (~200 ticks = 10s)
        int width;
        String warheadId;
        Sweep(ServerLevel l, Vec3 s, Vec3 e, int life, int w, String id){
            level=l; start=s; end=e; current=s; lifetime=Math.max(1,life); width=w; warheadId=id;
        }
        boolean tick(){
            if(level==null || level.getServer()==null) return true;
            double t = Math.min(1.0, age / (double)lifetime);
            Vec3 next = start.lerp(end, t);
            // leave trail segment from current to next
            GroundCrackTrail.leaveLine(level, current, next, width);
            // also carve shallow trench visual via particles
            current = next;
            age++;
            return age > lifetime; // done
        }
    }

    public static void startSweep(ServerLevel level, Vec3 center, int radius, String warheadId){
        if(level==null || center==null) return;
        // sweep distance = diameter, direction random, duration scales with radius
        double angle = level.getRandom().nextDouble()*Math.PI*2;
        double dist = Math.max(12, radius*1.6);
        Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        Vec3 start = center.add(dir.scale(-dist/2)).add(0,0.5,0);
        Vec3 end = center.add(dir.scale(dist/2)).add(0,0.5,0);
        // snap y to terrain
        start = new Vec3(start.x, center.y, start.z);
        end = new Vec3(end.x, center.y, end.z);
        int life = Math.max(60, Math.min(200, radius*3)); // 3-10s sweep
        int width = Math.max(1, radius/12 + 1);
        ACTIVE.add(new Sweep(level, start, end, life, width, warheadId));
    }

    public static void tick(net.minecraft.server.MinecraftServer server){
        if(ACTIVE.isEmpty()) return;
        Iterator<Sweep> it = ACTIVE.iterator();
        while(it.hasNext()){
            Sweep s = it.next();
            if(s.tick()) it.remove();
        }
    }
    public static void clear(){ ACTIVE.clear(); }
}
