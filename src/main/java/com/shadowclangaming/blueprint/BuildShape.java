package com.shadowclangaming.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * What a built rifle actually looks like.
 *
 * The blueprint table used to eat the build and return a faceless item. Now it records every block it
 * consumed - position and block state - and that list travels with the rifle as a data component, so the
 * item renders as the thing the player built: a netherite cannon looks like netherite, a plank pop gun
 * looks like planks, and the emitters stay where they were put.
 *
 * Positions are stored RELATIVE TO THE BUILD'S CENTRE, not to world coordinates, and as whole blocks.
 * Two consequences that matter:
 *
 *   - the model is autocentred: the renderer translates by the recorded centre, so a build that sat
 *     off to one side of its marked box still appears centred in the hand;
 *   - a rifle made in one base and carried to another renders identically, because nothing in the
 *     component refers to where it was built.
 */
public record BuildShape(List<Part> parts) {

    /** One consumed block: where it was, relative to the build centre, and what it was. */
    public record Part(int x, int y, int z, BlockState state) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("x").forGetter(Part::x),
                Codec.INT.fieldOf("y").forGetter(Part::y),
                Codec.INT.fieldOf("z").forGetter(Part::z),
                BlockState.CODEC.fieldOf("block").forGetter(Part::state)
        ).apply(instance, Part::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Part> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, Part::x,
                        ByteBufCodecs.VAR_INT, Part::y,
                        ByteBufCodecs.VAR_INT, Part::z,
                        ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), Part::state,
                        Part::new);
    }

    /**
     * Hard limit on stored parts.
     *
     * A 32x32x32 box could hold 32,768 blocks; shipping that in an item component would be absurd (and
     * would make the client render a solid cube). The table keeps the first {@code MAX_PARTS} blocks in
     * scan order, which for any real rifle shape - a barrel, a stock, a receiver - is everything.
     */
    public static final int MAX_PARTS = 96;

    public static final Codec<BuildShape> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Part.CODEC.listOf().fieldOf("parts").forGetter(BuildShape::parts)
            ).apply(instance, BuildShape::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildShape> STREAM_CODEC =
            StreamCodec.composite(
                    Part.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PARTS)), BuildShape::parts,
                    BuildShape::new);

    public static final BuildShape EMPTY = new BuildShape(List.of());

    public BuildShape {
        parts = List.copyOf(parts);
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    /** How many Emitters the build contains - one per muzzle, and each one shortens the cooldown. */
    public int emitters() {
        return Math.max(1, emitterPositions().size());
    }

    /** Is this block one of our emitters? Compared by registry id, so any block instance works. */
    public static boolean isEmitter(BlockState state) {
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null && key.getNamespace().equals("shadowclangaming") && key.getPath().equals("emitter");
    }

    /** Where each emitter sits, in build-local coordinates - the cycle point for a multi-barrel gun. */
    public List<int[]> emitterPositions() {
        List<int[]> shells = new ArrayList<>();
        for (Part part : parts) {
            if (isEmitter(part.state())) {
                shells.add(new int[]{part.x(), part.y(), part.z()});
            }
        }
        return shells;
    }

    /**
     * Cluster level for each emitter, in the same order as {@link #emitterPositions()}.
     *
     * This is what makes a block of emitters more than the sum of its parts: 27 emitters stacked in a
     * cube are one assembly at level 27, so every shot out of it is 1.10^26 times as powerful (and
     * 1.05^26 times as expensive) as a single emitter.
     */
    public int[] emitterLevels() {
        return com.shadowclangaming.blueprint.EmitterGraph.clusterSizes(emitterPositions());
    }

    /** The biggest assembly in the build - the number the tooltip leads with. */
    public int largestEmitterLevel() {
        return Math.max(1, com.shadowclangaming.blueprint.EmitterGraph.largestCluster(emitterPositions()));
    }

    /** The centre of the recorded blocks, used to autocentre the render. */
    public double[] centre() {
        if (parts.isEmpty()) {
            return new double[]{0.0D, 0.0D, 0.0D};
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Part part : parts) {
            minX = Math.min(minX, part.x());
            minY = Math.min(minY, part.y());
            minZ = Math.min(minZ, part.z());
            maxX = Math.max(maxX, part.x());
            maxY = Math.max(maxY, part.y());
            maxZ = Math.max(maxZ, part.z());
        }
        return new double[]{
                (minX + maxX + 1) / 2.0D,
                (minY + maxY + 1) / 2.0D,
                (minZ + maxZ + 1) / 2.0D
        };
    }

    /** Longest edge in blocks - the renderer scales by this so any build fits the item's bounds. */
    public int longestEdge() {
        if (parts.isEmpty()) {
            return 1;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Part part : parts) {
            minX = Math.min(minX, part.x());
            minY = Math.min(minY, part.y());
            minZ = Math.min(minZ, part.z());
            maxX = Math.max(maxX, part.x());
            maxY = Math.max(maxY, part.y());
            maxZ = Math.max(maxZ, part.z());
        }
        return Math.max(1, Math.max(maxX - minX + 1, Math.max(maxY - minY + 1, maxZ - minZ + 1)));
    }

    /**
     * Builds the shape from a scan, converting world positions to centre-relative ones and capping the
     * part list.
     */
    public static BuildShape of(List<Part> worldParts) {
        if (worldParts.isEmpty()) {
            return EMPTY;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Part part : worldParts) {
            minX = Math.min(minX, part.x());
            minY = Math.min(minY, part.y());
            minZ = Math.min(minZ, part.z());
            maxX = Math.max(maxX, part.x());
            maxY = Math.max(maxY, part.y());
            maxZ = Math.max(maxZ, part.z());
        }
        int cx = (minX + maxX) / 2;
        int cy = (minY + maxY) / 2;
        int cz = (minZ + maxZ) / 2;
        List<Part> local = new ArrayList<>(Math.min(worldParts.size(), MAX_PARTS));
        for (Part part : worldParts) {
            if (local.size() >= MAX_PARTS) {
                break;
            }
            local.add(new Part(part.x() - cx, part.y() - cy, part.z() - cz, part.state()));
        }
        return new BuildShape(local);
    }

    /** A placeholder shape for rifles that were never built (creative, commands, old saves). */
    public static BuildShape defaultRifle() {
        BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();
        BlockState emitter = com.shadowclangaming.registry.SCGBlocks.EMITTER.get().defaultBlockState();
        List<Part> parts = new ArrayList<>();
        parts.add(new Part(-1, 0, 0, iron));
        parts.add(new Part(0, 0, 0, iron));
        parts.add(new Part(1, 0, 0, iron));
        parts.add(new Part(2, 0, 0, emitter));
        parts.add(new Part(-1, -1, 0, iron));
        parts.add(new Part(0, -1, 0, iron));
        return new BuildShape(parts);
    }
}
