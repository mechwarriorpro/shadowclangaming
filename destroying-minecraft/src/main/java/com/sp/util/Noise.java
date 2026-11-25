package com.sp.util;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector4d;

public class Noise {
    public static float getCrackNoise(Vec3 v) {
        float noise = (fbm(v.scale(0.5), 7)) * 2.0f - 1.0f;
        float noise2 = -noise;
        return Math.min(Math.max(noise, noise2) + fbm(v.scale(0.3), 1), 1);
    }

    private static Vector4d mod289(Vector4d x) {
        Vector4d r = new Vector4d();
        r.x = x.x - Math.floor(x.x * (1.0 / 289.0)) * 289.0;
        r.y = x.y - Math.floor(x.y * (1.0 / 289.0)) * 289.0;
        r.z = x.z - Math.floor(x.z * (1.0 / 289.0)) * 289.0;
        r.w = x.w - Math.floor(x.w * (1.0 / 289.0)) * 289.0;
        return r;
    }

    private static Vector4d perm(Vector4d x) {
        Vector4d t = new Vector4d(x); t.mul(34.0); t.add(1.0, 1.0, 1.0, 1.0); t.mul(x);
        return mod289(t);
    }

    public static float perlinNoise(Vec3 p) {
        Vector3d a = new Vector3d(Math.floor(p.x), Math.floor(p.y), Math.floor(p.z));
        Vector3d d = new Vector3d(p.x - a.x, p.y - a.y, p.z - a.z);
        Vector3d d2 = new Vector3d(d).mul(d);
        Vector3d tmp = new Vector3d(3.0 - 2.0 * d.x, 3.0 - 2.0 * d.y, 3.0 - 2.0 * d.z);
        d = new Vector3d(d2).mul(tmp);
        Vector4d b = new Vector4d(a.x, a.x, a.y, a.y); b.add(new Vector4d(0.0, 1.0, 0.0, 1.0));
        Vector4d k1 = perm(new Vector4d(b.x, b.y, b.x, b.y));
        Vector4d k1xy = new Vector4d(k1.x, k1.y, k1.x, k1.y);
        Vector4d bzzww = new Vector4d(b.z, b.z, b.w, b.w);
        Vector4d k2 = perm(new Vector4d(k1xy).add(bzzww));
        Vector4d c = new Vector4d(k2); c.add(new Vector4d(a.z, a.z, a.z, a.z));
        Vector4d k3 = perm(c);
        Vector4d k4 = perm(new Vector4d(c).add(1.0, 1.0, 1.0, 1.0));
        Vector4d o1 = fract(new Vector4d(k3).mul(1.0 / 41.0));
        Vector4d o2 = fract(new Vector4d(k4).mul(1.0 / 41.0));
        Vector4d o3 = new Vector4d(); o3.add(new Vector4d(o2).mul(d.z)); o3.add(new Vector4d(o1).mul(1.0 - d.z));
        Vector2d o4 = new Vector2d(); o4.x = o3.y * d.x + o3.x * (1.0 - d.x); o4.y = o3.w * d.x + o3.z * (1.0 - d.x);
        return (float)(o4.y * d.y + o4.x * (1.0 - d.y));
    }

    public static float fbm(Vec3 x, int iter) {
        float v = 0.0f; float a = 0.5f; Vec3 shift = new Vec3(100, 100, 100);
        for (int i = 0; i < iter; ++i) { v += a * perlinNoise(x); x = x.scale(2.0).add(shift); a *= 0.5f; }
        return v;
    }

    private static Vector4d fract(Vector4d v) {
        Vector4d r = new Vector4d(); r.x = v.x - Math.floor(v.x); r.y = v.y - Math.floor(v.y); r.z = v.z - Math.floor(v.z); r.w = v.w - Math.floor(v.w);
        return r;
    }
}
