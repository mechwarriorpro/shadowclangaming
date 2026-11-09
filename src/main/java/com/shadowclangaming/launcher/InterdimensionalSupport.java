package com.shadowclangaming.launcher;

import com.shadowclangaming.registry.SCGBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Which addons contribute to an interdimensional launch, and how they are checked.
 *
 * One line per addon family (chemistry, nuclear, magic, logistics, esper, orbital). A launch pad
 * does not need every single block — it needs at least one representative of each family within 16
 * blocks, because the fiction is that the silo is plumbed into the base. That is the interlink:
 * chemistry feeds the warhead, the nuclear core powers the lift, the magic circle stabilises the
 * breach, logistics carries the payload, esper tech locks the target, orbital eyes watch the sky.
 *
 * The list is data, not hard-coded if-chains, so adding a new addon is one entry.
 */
public final class InterdimensionalSupport {

    public enum Category {
        CHEMISTRY("chemistry", List.of(
                SCGBlocks.DECOMPOSER, SCGBlocks.SYNTHESIZER,
                SCGBlocks.CHEMICAL_REACTOR, SCGBlocks.CENTRIFUGE, SCGBlocks.MICROSCOPE)),
        NUCLEAR("nuclear", List.of(
                SCGBlocks.NUCLEAR_REACTOR, SCGBlocks.SMELTERY_CONTROLLER,
                SCGBlocks.SEARED_TANK, SCGBlocks.SEARED_HEATER, SCGBlocks.SEARED_TANK_GAUGE, SCGBlocks.SEARED_DRAIN, SCGBlocks.UNOBTAINIUM_BLOCK)),
        MAGIC("magic", List.of(
                SCGBlocks.CRUCIBLE_OF_SOULS, SCGBlocks.ESSENTIA_JAR,
                SCGBlocks.CHALK_CIRCLE, SCGBlocks.BLOOD_CIRCLE,
                SCGBlocks.INSCRIPTION_TABLE, SCGBlocks.EMITTER, SCGBlocks.BLUEPRINT_TABLE)),
        LOGISTICS("logistics", List.of(
                SCGBlocks.PIPE_BC, SCGBlocks.TRANSLOCATOR, SCGBlocks.ASSEMBLY_BENCH,
                SCGBlocks.POWER_CABLE, SCGBlocks.FABRICATOR)),
        ESPER("esper", List.of(
                SCGBlocks.ABILITY_CHAMBER)),
        ORBITAL("orbital", List.of(
                SCGBlocks.ORBITAL_NODE, SCGBlocks.RADAR, SCGBlocks.CANNON_DIRECTOR, SCGBlocks.NETWORK_RACK, SCGBlocks.TERMINAL));

        public final String id;
        public final List<net.neoforged.neoforge.registries.DeferredBlock<?>> blocks;

        Category(String id, List<net.neoforged.neoforge.registries.DeferredBlock<?>> blocks) {
            this.id = id;
            this.blocks = blocks;
        }
    }

    public record Report(Map<Category, BlockPos> found, int supported, int required, boolean hasSupport, List<String> missing) {}

    private static final int RADIUS = 16;

    private InterdimensionalSupport() {}

    public static Report scan(ServerLevel level, BlockPos padPos) {
        Map<Category, BlockPos> found = new EnumMap<>(Category.class);
        List<String> missing = new ArrayList<>();
        for (Category cat : Category.values()) {
            BlockPos hit = findOne(level, padPos, cat);
            if (hit != null) found.put(cat, hit);
            else missing.add(cat.id);
        }
        int supported = found.size();
        int required = Category.values().length;
        // Require all but one: a silo missing a single family can still launch at reduced reliability,
        // but missing two is a hard stop. Keeps small bases viable without letting a lone pad go interdimensional.
        boolean hasSupport = supported >= required - 1;
        return new Report(found, supported, required, hasSupport, missing);
    }

    private static BlockPos findOne(ServerLevel level, BlockPos origin, Category cat) {
        for (BlockPos p : BlockPos.betweenClosed(
                origin.offset(-RADIUS, -RADIUS, -RADIUS),
                origin.offset(RADIUS, RADIUS, RADIUS))) {
            BlockState st = level.getBlockState(p);
            Block b = st.getBlock();
            for (var deferred : cat.blocks) {
                try {
                    if (b == deferred.get()) return p.immutable();
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    public static boolean hasSupport(ServerLevel level, BlockPos padPos) {
        return scan(level, padPos).hasSupport();
    }

    public static String describe(Report r) {
        return "addon support " + r.supported() + "/" + r.required() + (r.hasSupport() ? " OK" : " missing: " + String.join(",", r.missing()));
    }
}
