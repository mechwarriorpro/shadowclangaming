package com.sp.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

public class MathUtil {
    private static final RandomSource random = RandomSource.create();

    public static <E> E randomValueInList(List<E> list) {
        return list.get(random.nextIntBetweenInclusive(0, list.size() - 1));
    }

    public static <E> E randomValueInList(E[] array) {
        return array[random.nextIntBetweenInclusive(0, array.length - 1)];
    }

    public static float nextBetween(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    public static float Lerp(float source, float destination, float smoothingFactor, float delta) {
        return Mth.lerp(1.0f - (float) Math.pow(smoothingFactor, delta), source, destination);
    }

    public static Vec3 getCenterPos(List<BlockPos> blocks) {
        double sumX = 0, sumY = 0, sumZ = 0;
        for (BlockPos pos : blocks) { sumX += pos.getX(); sumY += pos.getY(); sumZ += pos.getZ(); }
        int count = blocks.size();
        sumX /= count; sumY /= count; sumZ /= count;
        return new Vec3(sumX + 0.5, sumY + 0.5, sumZ + 0.5);
    }

    public static Vector3d toVector3d(Vec3 v) { return new Vector3d(v.x, v.y, v.z); }
    public static Vec3 toVec3d(Vector3d vec) { return new Vec3(vec.x, vec.y, vec.z); }
    public static Vec3 toVec3d(Vector3f vec) { return new Vec3(vec.x, vec.y, vec.z); }
}
