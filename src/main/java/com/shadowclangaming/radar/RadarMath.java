package com.shadowclangaming.radar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Radar arithmetic: range, bearing, threat, and which contacts a sweep keeps.
 *
 * All of it is pure so it can be tested without a world, which matters because "why did my radar not
 * see that missile" is exactly the kind of question that is impossible to answer by watching a server.
 *
 * RANGE. One radar block sees {@link #BASE_RADIUS} blocks - already twice what a Create: Radars
 * bearing manages on a good day - and every additional radar block within {@link #ARRAY_RANGE} of the
 * first widens the picture by {@link #STEP_RADIUS}. The cap is {@link #MAX_RADIUS} (4096 blocks, i.e. 31
 * dishes at the unchanged 128-block step). The cap exists because a sensor does not need to see the whole
 * dimension, and an unbounded query radius is how a server dies - unlike a warhead, a radar reports every
 * entity it can find.
 */
public final class RadarMath {

    public static final int BASE_RADIUS = 256;
    public static final int STEP_RADIUS = 128;
    public static final int MAX_RADIUS = 4096;
    /** How far a second radar dish may sit from the first and still count as part of the array. */
    public static final int ARRAY_RANGE = 4;
    public static final int BASE_TRACKS = 8;
    public static final int TRACKS_PER_ARRAY = 4;
    public static final int MAX_TRACKS = 128;

    private RadarMath() {
    }

    /** Range of an array of {@code arrays} dishes (1 = a lone radar). */
    public static int radiusFor(int arrays) {
        int count = Math.max(1, arrays);
        return Math.min(MAX_RADIUS, BASE_RADIUS + STEP_RADIUS * (count - 1));
    }

    /** How many contacts a sweep may hold. GPU pipelines and extra dishes both widen the track list. */
    public static int maxTracks(int arrays, int gpuBonus) {
        int count = Math.max(1, arrays);
        int tracks = BASE_TRACKS + TRACKS_PER_ARRAY * (count - 1) + Math.max(0, gpuBonus);
        return Math.min(MAX_TRACKS, tracks);
    }

    /** Compass bearing from the antenna to a point: north is 0, clockwise, 0..360. */
    public static double bearingDegrees(double dx, double dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        return (degrees + 360.0D) % 360.0D;
    }

    /** Angle above the horizon, degrees. */
    public static double elevationDegrees(double dx, double dy, double dz) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return Math.toDegrees(Math.atan2(dy, horizontal));
    }

    /** 0 = innermost third of the scope, 3 = the edge. Used for the ring display and for threat. */
    public static int rangeBand(double distance, double radius) {
        if (radius <= 0.0D) {
            return 3;
        }
        double fraction = Math.max(0.0D, Math.min(1.0D, distance / radius));
        if (fraction < 1.0D / 3.0D) {
            return 0;
        }
        if (fraction < 2.0D / 3.0D) {
            return 1;
        }
        if (fraction < 0.97D) {
            return 2;
        }
        return 3;
    }

    /**
     * 0..100. Kind first (a missile is always the interesting thing), then how deep inside the scope it
     * is, then whether it is coming towards you.
     */
    public static int threat(ContactKind kind, double distance, double radius) {
        return threat(kind, distance, radius, 0.0D);
    }

    public static int threat(ContactKind kind, double distance, double radius, double closing) {
        if (kind == null) {
            return 0;
        }
        double closeness = radius <= 0.0D ? 1.0D : Math.max(0.0D, 1.0D - distance / radius);
        int score = kind.baseThreat() + (int) Math.round(closeness * 20.0D);
        if (closing > 0.0D) {
            score += 10;
        }
        return Math.max(0, Math.min(100, score));
    }

    /** The biggest array this sweep can resolve, from the number of dishes it counted. */
    public static int clampArrays(int arrays) {
        return Math.max(1, Math.min(MAX_RADIUS / STEP_RADIUS + 1, arrays));
    }

    /**
     * Which contacts a sweep keeps: in range, most threatening first, ties broken by distance.
     *
     * This is the one piece of radar behaviour that has a wrong answer which still looks plausible, so
     * it lives here and is asserted by a harness rather than trusted.
     */
    public static List<RadarContact> select(Collection<RadarContact> contacts, double radius, int maxTracks) {
        List<RadarContact> kept = new ArrayList<>();
        if (contacts == null || maxTracks <= 0) {
            return kept;
        }
        List<RadarContact> sorted = new ArrayList<>();
        for (RadarContact contact : contacts) {
            if (contact != null && contact.distance() <= radius) {
                sorted.add(contact);
            }
        }
        sorted.sort(Comparator
                .comparingInt((RadarContact contact) -> -threat(contact.kind(), contact.distance(), radius,
                        contact.closing()))
                .thenComparingDouble(RadarContact::distance)
                .thenComparingInt(RadarContact::id));
        for (int i = 0; i < sorted.size() && i < maxTracks; i++) {
            kept.add(sorted.get(i));
        }
        return kept;
    }

    /** Threat of one contact, using the same rules as {@link #select}. */
    public static int threatOf(RadarContact contact, double radius) {
        return contact == null ? 0 : threat(contact.kind(), contact.distance(), radius, contact.closing());
    }
}
