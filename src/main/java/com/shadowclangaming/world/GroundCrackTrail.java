package com.shadowclangaming.world;

import com.shadowclangaming.registry.SCGBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Leaves scorched/cracked crust on exposed surfaces after any blast. 2-3 minute lifetime via block tick.
 * Called from WarheadType for all warheads where it makes sense; beams use BeamSweepManager for motion.
 */
public final class GroundCrackTrail {
    private GroundCrackTrail(){}
    public static void leaveCracks(ServerLevel level, Vec3 pos, int radius){
        if(radius<=0) return;
        BlockPos centre = BlockPos.containing(pos);
        int r = Math.min(radius, 48); // cap to avoid lag; apex already larger carver handles big ones
        int placed=0;
        for(BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -r/2, -r), centre.offset(r, r/2, r))){
            if(p.distSqr(centre) > r*r) continue;
            if(level.getRandom().nextFloat() > 0.18F) continue; // sparse crack pattern, not solid carpet
            var state = level.getBlockState(p);
            if(state.isAir() || state.is(Blocks.BEDROCK) || state.is(SCGBlocks.SCORCHED_GROUND.get())) continue;
            // must be exposed: air above or destructible top surface
            BlockPos above = p.above();
            if(!level.getBlockState(above).isAir()) continue;
            if(state.getDestroySpeed(level,p) < 0 || state.getDestroySpeed(level,p) > 60) continue;
            level.setBlockAndUpdate(above, SCGBlocks.SCORCHED_GROUND.get().defaultBlockState());
            placed++;
            if(placed>600) break; // budget
        }
    }
    /** For beam sweep: leave narrow line of cracks along line */
    public static void leaveLine(ServerLevel level, Vec3 from, Vec3 to, int width){
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if(len < 0.5) return;
        Vec3 n = dir.normalize();
        int steps = (int)Math.ceil(len);
        for(int i=0;i<=steps;i++){
            Vec3 c = from.add(n.scale(i));
            for(int dx=-width; dx<=width; dx++) for(int dz=-width; dz<=width; dz++){
                if(dx*dx+dz*dz > width*width) continue;
                BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(c.x+dx, c.y, c.z+dz));
                if(level.getBlockState(top).isAir() && !level.getBlockState(top.below()).isAir()){
                    level.setBlockAndUpdate(top, SCGBlocks.SCORCHED_GROUND.get().defaultBlockState());
                }
            }
        }
    }
}
