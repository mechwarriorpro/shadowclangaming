package com.shadowclangaming.radar;

/**
 * One thing the radar can see, in plain numbers.
 *
 * No Vec3 and no Entity reference on purpose: a contact survives the entity being unloaded, a scan can
 * be unit tested outside Minecraft, and a track list is safe to hand to a cannon without holding a
 * reference to a level that may be gone by the time the shot is aimed.
 *
 * @param id       a small number, stable for as long as the thing stays on the scope
 * @param name     display name, already resolved at scan time
 * @param distance blocks from the antenna
 * @param closing  blocks per tick the contact is closing with the antenna (negative = leaving)
 */
public record RadarContact(int id, String name, ContactKind kind,
                           double x, double y, double z,
                           double vx, double vy, double vz,
                           double distance, double closing) {

    public boolean isMissile() {
        return kind == ContactKind.MISSILE;
    }

    /** 0..360, north is 0, clockwise. */
    public double bearing(double originX, double originZ) {
        return RadarMath.bearingDegrees(x - originX, z - originZ);
    }
}
