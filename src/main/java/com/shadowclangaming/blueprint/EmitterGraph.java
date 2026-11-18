package com.shadowclangaming.blueprint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Which emitters are touching which, and how big the resulting cluster is.
 *
 * ──────────────────── THE RULE ────────────────────
 *
 * Emitters that touch are one barrel assembly, and the assembly's LEVEL is simply how many emitters it
 * contains:
 *
 *     one emitter          level 1
 *     a 3x3x3 block of 27  level 27
 *     a 3x3 plate of 9     level 9
 *
 * "Touching" means any of the 26 surrounding positions - faces, edges and corners - because that is what
 * a player means when they stack emitters into a block. A cluster is found by flood fill, so two separate
 * piles of emitters in the same gun are two assemblies with their own levels, and firing from one does not
 * borrow the power of the other.
 *
 * Deliberately Minecraft-free: this is pure integer geometry, so the harness can test it without a game
 * (see tools/harness/LogicHarness.java). BuildShape calls into it.
 */
public final class EmitterGraph {

    /** Emitters beyond this count are ignored for clustering (a 96-block build cannot hold more). */
    public static final int MAX_EMITTERS = 64;

    private EmitterGraph() {
    }

    /**
     * Cluster size for each emitter, in the same order as the input.
     *
     * @param positions emitter positions, each an {x, y, z} triple, in build-local space
     * @return an array where element i is the size of the cluster emitter i belongs to
     */
    public static int[] clusterSizes(List<int[]> positions) {
        int count = Math.min(positions.size(), MAX_EMITTERS);
        int[] sizes = new int[count];
        boolean[] visited = new boolean[count];

        for (int start = 0; start < count; start++) {
            if (visited[start]) {
                continue;
            }
            // Breadth-first flood fill over "touching" neighbours.
            List<Integer> members = new ArrayList<>();
            Deque<Integer> frontier = new ArrayDeque<>();
            frontier.add(start);
            visited[start] = true;
            while (!frontier.isEmpty()) {
                int current = frontier.poll();
                members.add(current);
                int[] here = positions.get(current);
                for (int other = 0; other < count; other++) {
                    if (visited[other]) {
                        continue;
                    }
                    if (touching(here, positions.get(other))) {
                        visited[other] = true;
                        frontier.add(other);
                    }
                }
            }
            for (int member : members) {
                sizes[member] = members.size();
            }
        }
        return sizes;
    }

    /** True when two emitters share a face, an edge or a corner. */
    public static boolean touching(int[] a, int[] b) {
        int dx = Math.abs(a[0] - b[0]);
        int dy = Math.abs(a[1] - b[1]);
        int dz = Math.abs(a[2] - b[2]);
        return dx <= 1 && dy <= 1 && dz <= 1 && (dx + dy + dz) > 0;
    }

    /** The largest cluster in the build - used for the tooltip's headline number. */
    public static int largestCluster(List<int[]> positions) {
        int largest = 0;
        for (int size : clusterSizes(positions)) {
            largest = Math.max(largest, size);
        }
        return largest;
    }

    /** Convenience for the table: how many barrels sit in a cluster of the given size or bigger. */
    public static int countInClustersOfAtLeast(List<int[]> positions, int level) {
        int found = 0;
        for (int size : clusterSizes(positions)) {
            if (size >= level) {
                found++;
            }
        }
        return found;
    }
}
