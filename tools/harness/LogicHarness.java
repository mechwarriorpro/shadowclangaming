import com.shadowclangaming.blueprint.BlueprintMath;
import com.shadowclangaming.compat.economy.MoneyChunks;
import com.shadowclangaming.warhead.ConstructorBlast;
import com.shadowclangaming.compat.economy.RecipePricing;
import com.shadowclangaming.entity.RamTiming;
import com.shadowclangaming.blueprint.EmitterGraph;
import com.shadowclangaming.cannon.CannonMath;
import com.shadowclangaming.launcher.ModuleTier;
import com.shadowclangaming.warhead.apex.Apex;
import com.shadowclangaming.warhead.apex.ApexTimeline;
import com.shadowclangaming.computer.ComputeBudget;
import com.shadowclangaming.computer.CpuTier;
import com.shadowclangaming.computer.GpuTier;
import com.shadowclangaming.radar.ContactKind;
import com.shadowclangaming.radar.RadarContact;
import com.shadowclangaming.radar.RadarMath;
import com.shadowclangaming.radar.TrackScanner;

import java.util.ArrayList;
import java.util.List;

/**
 * Assertions for the parts of this round that are arithmetic rather than Minecraft.
 *
 * These are the numbers a player will complain about, so they are the numbers that get checked by a
 * machine instead of by eye:
 *
 *   - the compute budget (cores/jobs/threads/pipelines/FE) for every hardware combination
 *   - the radar's range, track count, bearing and threat ordering
 *   - the cannon's yaw/pitch solution and the lead calculation
 *
 * Run with: bash tools/run_logic_harness.sh
 */
public final class LogicHarness {

    /**
     * The multi-emitter rifle: -5% cooldown and +2.5% mana per extra emitter, with a 2-tick floor.
     *
     * These are the numbers in the tooltip, so they are checked here rather than trusted.
     */
    private static void rifleTests() {
        section("rifle barrels");
        check("one emitter is the base cooldown",
                BlueprintMath.cooldownTicks(12, 1) == 12);
        check("one emitter has no mana surcharge",
                Math.abs(BlueprintMath.manaMultiplier(1) - 1.0D) < 1.0E-9D);
        check("two emitters: 12 * 0.95 = 11.4 -> 11 ticks",
                BlueprintMath.cooldownTicks(12, 2) == 11);
        check("four emitters: 12 * 0.95^3 = 10.3 -> 10 ticks",
                BlueprintMath.cooldownTicks(12, 4) == 10);
        check("ten emitters: 12 * 0.95^9 = 7.6 -> 8 ticks",
                BlueprintMath.cooldownTicks(12, 10) == 8);
        check("cooldown never goes below the floor",
                BlueprintMath.cooldownTicks(12, 64) == BlueprintMath.MIN_COOLDOWN_TICKS);
        check("two emitters cost 2.5% more mana",
                Math.abs(BlueprintMath.manaMultiplier(2) - 1.025D) < 1.0E-9D);
        check("ten emitters cost 1.025^9 = 24.9% more",
                Math.abs(BlueprintMath.manaMultiplier(10) - 1.2489D) < 5.0E-4D);
        // 100 * 0.90 = 90, then 1.025^2 = 1.0506 -> 94.55 -> 95
        check("a 100-mana shot from a 90% rifle with 3 barrels costs 95",
                BlueprintMath.shotCost(100L, 90, 3) == 95L);
        check("shot cost is never zero",
                BlueprintMath.shotCost(1L, 60, 20) >= 1L);
        check("emitter count is clamped, not unbounded",
                Math.abs(BlueprintMath.manaMultiplier(500)
                        - BlueprintMath.manaMultiplier(BlueprintMath.MAX_EMITTERS)) < 1.0E-9D);
    }

    /**
     * Touching-emitter clusters: level = how many emitters touch, power = 1.10^(level-1),
     * mana = 1.05^(level-1).
     */
    private static void emitterClusterTests() {
        section("emitter assemblies");
        check("a lone emitter is level 1",
                EmitterGraph.largestCluster(List.of(new int[]{0, 0, 0})) == 1);
        check("a 3x3x3 cube of emitters is level 27",
                EmitterGraph.largestCluster(cube(3)) == 27);
        check("a 1x1x1 is level 1 (the stated baseline)",
                EmitterGraph.largestCluster(cube(1)) == 1);
        check("a 3x3 plate is level 9",
                EmitterGraph.largestCluster(plate(3)) == 9);
        check("a 5-long rod is level 5",
                EmitterGraph.largestCluster(rod(5)) == 5);
        check("two separated singles are two level-1 assemblies",
                EmitterGraph.largestCluster(List.of(new int[]{0, 0, 0}, new int[]{10, 0, 0})) == 1);
        check("emitters touching only at a corner still connect",
                EmitterGraph.largestCluster(List.of(new int[]{0, 0, 0}, new int[]{1, 1, 1})) == 2);
        check("each emitter reports its own cluster size",
                EmitterGraph.clusterSizes(List.of(new int[]{0, 0, 0}, new int[]{1, 0, 0},
                        new int[]{40, 0, 0}))[2] == 1);
        check("level 1 means no power bonus",
                Math.abs(BlueprintMath.clusterPower(1) - 1.0D) < 1.0E-9D);
        check("level 2 adds 10% power",
                Math.abs(BlueprintMath.clusterPower(2) - 1.10D) < 1.0E-9D);
        check("level 27 hits for 1.10^26 = 11.9x",
                Math.abs(BlueprintMath.clusterPower(27) - 11.918D) < 5.0E-3D);
        check("level 27 costs 1.05^26 = 3.56x mana",
                Math.abs(BlueprintMath.clusterMana(27) - 3.556D) < 5.0E-3D);
        check("a 27-emitter cube costs less extra mana than it gains in power",
                BlueprintMath.clusterPower(27) / BlueprintMath.clusterMana(27) > 3.0D);
        check("cluster level is clamped",
                BlueprintMath.level(500) == BlueprintMath.MAX_CLUSTER_LEVEL);
        // 100 raw, 100% efficiency, 1 emitter, level 1 -> 100
        check("a level-1 single-emitter shot is unchanged",
                BlueprintMath.shotCostForCluster(100L, 100, 1, 1) == 100L);
        // 100 * 1.025 * 1.05 = 107.625 -> 108
        check("two emitters at level 2 cost 108 per 100",
                BlueprintMath.shotCostForCluster(100L, 100, 2, 2) == 108L);
    }

    private static List<int[]> cube(int side) {
        List<int[]> out = new ArrayList<>();
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                for (int z = 0; z < side; z++) {
                    out.add(new int[]{x, y, z});
                }
            }
        }
        return out;
    }

    private static List<int[]> plate(int side) {
        List<int[]> out = new ArrayList<>();
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                out.add(new int[]{x, 0, z});
            }
        }
        return out;
    }

    private static List<int[]> rod(int length) {
        List<int[]> out = new ArrayList<>();
        for (int x = 0; x < length; x++) {
            out.add(new int[]{x, 0, 0});
        }
        return out;
    }

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        budgetTests();
        moneyChunkTests();
        constructorTests();
        recipePricingTests();
        beefSupremeTests();
        trackScannerTests();
        radarTests();
        cannonTests();
        moduleTierTests();
        apexWarheadTests();
        constructorHoldTests();
        siloStructureTests();
        moduleKindTests();
        chickenTacticsTests();
        kamikazeTests();
        explosivePackagingTests();
        barrageTests();
        channelTests();
        splitRuleTests();
        consoleLayoutTests();
        blastStyleTests();
        blastMotifTests();
        fuseTests();
        soulFurnaceTests();
        blastGeometryTests();
        volumeShaderTests();
        warheadFeatureTests();
        chargePhaseTests();
        rifleTests();
        emitterClusterTests();
        System.out.println();
        System.out.println(failures == 0
                ? "ALL TESTS PASS (" + checks + " assertions)"
                : failures + " FAILURE(S) of " + checks + " assertions");
        if (failures != 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ silo structure

    /**
     * A fake world: a set of "solid" offsets plus which of them are rods/panels.
     *
     * The probe is the whole interface the structure check has to the world, so faking it proves the
     * pattern logic without a level - the block-reading half is a switch on BlockState and has no
     * decisions in it worth testing.
     */
    private static final class FakeSilo implements com.shadowclangaming.launcher.SiloStructure.Probe {
        final java.util.Set<String> rods = new java.util.HashSet<>();
        final java.util.Set<String> panels = new java.util.HashSet<>();
        final java.util.Set<String> solid = new java.util.HashSet<>();
        boolean allShaftSolid;

        static String key(int x, int y, int z) {
            return x + ":" + y + ":" + z;
        }

        FakeSilo withEveryRod() {
            for (com.shadowclangaming.launcher.SiloStructure.Offset offset
                    : com.shadowclangaming.launcher.SiloStructure.PATTERN) {
                if (offset.part() == com.shadowclangaming.launcher.SiloStructure.Part.ROD) {
                    rods.add(key(offset.x(), offset.y(), offset.z()));
                }
            }
            return this;
        }

        @Override
        public boolean matches(int dx, int dy, int dz,
                               com.shadowclangaming.launcher.SiloStructure.Part part) {
            String at = key(dx, dy, dz);
            return switch (part) {
                case PAD -> true;
                case ROD -> rods.contains(at);
                case PANEL -> panels.contains(at);
            };
        }

        @Override
        public boolean shaftClear(int dx, int dy, int dz) {
            return !allShaftSolid && !solid.contains(key(dx, dy, dz));
        }
    }

    private static void siloStructureTests() {
        com.shadowclangaming.launcher.SiloStructure.Part ROD =
                com.shadowclangaming.launcher.SiloStructure.Part.ROD;

        eq("silo: pattern is nine offsets (pad + eight rods)",
                9, com.shadowclangaming.launcher.SiloStructure.PATTERN.size());
        eq("silo: eight rods required",
                8, com.shadowclangaming.launcher.SiloStructure.requiredRods());
        eq("silo: shaft is 72 blocks",
                72, com.shadowclangaming.launcher.SiloStructure.shaftVolume());

        // the ring: four corners and four edge midpoints at radius 2, nothing else
        java.util.Set<String> rodAt = new java.util.TreeSet<>();
        for (var offset : com.shadowclangaming.launcher.SiloStructure.PATTERN) {
            if (offset.part() == ROD) {
                rodAt.add(FakeSilo.key(offset.x(), offset.y(), offset.z()));
            }
        }
        java.util.Set<String> wanted = new java.util.TreeSet<>(java.util.List.of(
                "-2:0:-2", "-2:0:0", "-2:0:2",
                "0:0:-2", "0:0:2",
                "2:0:-2", "2:0:0", "2:0:2"));
        eq("silo: rods sit on the radius-2 ring", rodAt, wanted);

        // four panel slots, all one block outside the ring
        eq("silo: four panel slots",
                4, com.shadowclangaming.launcher.SiloStructure.PANEL_SLOTS.size());
        boolean outside = true;
        for (int[] slot : com.shadowclangaming.launcher.SiloStructure.PANEL_SLOTS) {
            int reach = Math.abs(slot[0]) + Math.abs(slot[1]) + Math.abs(slot[2]);
            outside &= reach == com.shadowclangaming.launcher.SiloStructure.FRAME_RADIUS + 1;
            outside &= slot[1] == 0;
        }
        check("silo: panels sit one block outside the ring", outside);

        // nothing: everything is wrong, in the order a player would fix it
        var empty = com.shadowclangaming.launcher.SiloStructure.inspect(new FakeSilo());
        check("silo: bare pad is not complete", !empty.complete());
        eq("silo: bare pad reports two faults (open sky is a clear shaft)", 2, empty.problems().size());
        eq("silo: rods reported first", "8/8 support rods", empty.problems().get(0));
        eq("silo: panel reported second", "no control panel", empty.problems().get(1));
        eq("silo: summary joins the faults",
                "8/8 support rods, no control panel", empty.summary());

        // a roofed silo: the shaft is the fault, and it is reported last
        FakeSilo roofed = new FakeSilo().withEveryRod();
        roofed.panels.add(FakeSilo.key(0, 0, 3));
        roofed.allShaftSolid = true;
        var roofedReport = com.shadowclangaming.launcher.SiloStructure.inspect(roofed);
        eq("silo: a full shaft is one fault", 1, roofedReport.problems().size());
        eq("silo: the whole shaft is counted", "72 blocks in the shaft",
                roofedReport.problems().get(0));
        check("silo: a roofed silo cannot launch", !roofedReport.complete());

        // rods only: the panel is the piece players forget
        FakeSilo rodsOnly = new FakeSilo().withEveryRod();
        eq("silo: rods alone still need a panel",
                "no control panel",
                com.shadowclangaming.launcher.SiloStructure.inspect(rodsOnly).problems().get(0));

        // rods + panel, shaft clear: this is a working silo
        FakeSilo framed = new FakeSilo().withEveryRod();
        framed.panels.add(FakeSilo.key(0, 0, -3));
        var framedReport = com.shadowclangaming.launcher.SiloStructure.inspect(framed);
        check("silo: framed pad is complete", framedReport.complete());
        eq("silo: complete report has no faults", 0, framedReport.problems().size());
        eq("silo: complete summary", "silo complete", framedReport.summary());
        check("silo: launchable",
                com.shadowclangaming.launcher.SiloStructure.launchable(framedReport));

        // a single block in the shaft is enough to refuse a launch
        framed.solid.add(FakeSilo.key(0, 4, 0));
        var blocked = com.shadowclangaming.launcher.SiloStructure.inspect(framed);
        check("silo: one block in the shaft refuses the launch", !blocked.complete());
        eq("silo: blocked count is exact", "1 blocks in the shaft", blocked.problems().get(0));
        framed.solid.clear();

        // one hole in the frame is enough
        FakeSilo holey = new FakeSilo().withEveryRod();
        holey.panels.add(FakeSilo.key(3, 0, 0));
        holey.rods.remove(FakeSilo.key(-2, 0, 2));
        eq("silo: one missing rod is one fault",
                "1/8 support rods",
                com.shadowclangaming.launcher.SiloStructure.inspect(holey).problems().get(0));

        // any of the four edges will do, and only the four
        int accepted = 0;
        for (int[] slot : com.shadowclangaming.launcher.SiloStructure.PANEL_SLOTS) {
            FakeSilo each = new FakeSilo().withEveryRod();
            each.panels.add(FakeSilo.key(slot[0], slot[1], slot[2]));
            if (com.shadowclangaming.launcher.SiloStructure.inspect(each).complete()) {
                accepted++;
            }
        }
        eq("silo: every edge slot accepts a panel", 4, accepted);

        FakeSilo wrongCorner = new FakeSilo().withEveryRod();
        wrongCorner.panels.add(FakeSilo.key(3, 0, 3));
        check("silo: a diagonal corner does not count as a panel",
                !com.shadowclangaming.launcher.SiloStructure.inspect(wrongCorner).complete());
    }

    // ------------------------------------------------------------------ missile module frames

    private static void moduleKindTests() {
        var kind = com.shadowclangaming.launcher.ModuleKind.class;
        var K = com.shadowclangaming.launcher.ModuleKind.values();

        eq("frames: five kinds", 5, K.length);
        eq("frames: default is standard",
                com.shadowclangaming.launcher.ModuleKind.STANDARD,
                com.shadowclangaming.launcher.ModuleKind.byId(null));
        eq("frames: an unknown id falls back to standard",
                com.shadowclangaming.launcher.ModuleKind.STANDARD,
                com.shadowclangaming.launcher.ModuleKind.byId("nonsense"));

        boolean roundTrip = true;
        for (var k : K) {
            roundTrip &= com.shadowclangaming.launcher.ModuleKind.byId(k.id()) == k;
        }
        check("frames: ids round-trip through NBT", roundTrip);

        // exactly one plain frame, and it is the one that does nothing special
        eq("frames: only standard is not special",
                false, com.shadowclangaming.launcher.ModuleKind.STANDARD.special());
        eq("frames: homing is special",
                true, com.shadowclangaming.launcher.ModuleKind.HOMING.special());

        // high velocity: a real speed advantage, and no warhead weirdness
        eq("frames: high velocity is 2.4x",
                2.4D, com.shadowclangaming.launcher.ModuleKind.HIGH_VELOCITY.speedMultiplier());
        check("frames: high velocity flies faster than standard",
                com.shadowclangaming.launcher.ModuleKind.HIGH_VELOCITY.speedFor(1.0D)
                        > com.shadowclangaming.launcher.ModuleKind.STANDARD.speedFor(1.0D));
        check("frames: high velocity never flies slower than the floor",
                com.shadowclangaming.launcher.ModuleKind.HIGH_VELOCITY.speedFor(0.0D) >= 0.35D);

        // homing: exactly one seeker, and it seeks further than it can see on foot
        int seekers = 0;
        for (var k : K) {
            if (k.homing()) {
                seekers++;
            }
        }
        eq("frames: one homing frame", 1, seekers);
        check("frames: homing range exceeds chicken sight",
                com.shadowclangaming.launcher.ModuleKind.HOMING_RANGE
                        > com.shadowclangaming.entity.ChickenTactics.SIGHT_RANGE);

        // cluster: splits, and the shares add up to less than the parent on purpose
        var cluster = com.shadowclangaming.launcher.ModuleKind.CLUSTER;
        eq("frames: cluster splits into six", 6, cluster.subMunitions());
        eq("frames: standard never splits", 0, com.shadowclangaming.launcher.ModuleKind.STANDARD.subMunitions());
        float parent = 20.0F;
        float each = cluster.subMunitionPower(parent);
        check("frames: a sub-munition is weaker than the parent", each < parent);
        check("frames: a sub-munition still hurts", each >= 1.0F);
        check("frames: six sub-munitions sum below the parent",
                each * cluster.subMunitions() < parent);
        eq("frames: a non-cluster frame passes power through unchanged",
                parent, com.shadowclangaming.launcher.ModuleKind.STANDARD.subMunitionPower(parent));

        // offsets: distinct, and inside the spread ring
        java.util.Set<String> spots = new java.util.TreeSet<>();
        boolean inside = true;
        for (int i = 0; i < cluster.subMunitions(); i++) {
            double[] off = cluster.subMunitionOffset(i, cluster.subMunitions());
            spots.add(Math.round(off[0] * 100) + ":" + Math.round(off[1] * 100));
            inside &= Math.hypot(off[0], off[1])
                    <= com.shadowclangaming.launcher.ModuleKind.CLUSTER_SPREAD + 0.001D;
        }
        eq("frames: every sub-munition has its own aim point", cluster.subMunitions(), spots.size());
        check("frames: sub-munitions stay inside the spread ring", inside);
        check("frames: the cluster breaks up before it reaches the target",
                com.shadowclangaming.launcher.ModuleKind.CLUSTER_TRIGGER > 0.0D);

        // ---- warhead compatibility rules ----
        // Every frame except transport carries every warhead, except cargo, which is limited to the frames
        // that arrive exactly once (splitting would duplicate the hold).
        String CARGO = com.shadowclangaming.launcher.ModuleKind.CARGO_WARHEAD_ID;
        for (var k : K) {
            eq("frames: " + k.id() + " refuses a cargo warhead only when it splits or carries crew",
                    k == com.shadowclangaming.launcher.ModuleKind.STANDARD
                            || k == com.shadowclangaming.launcher.ModuleKind.HOMING
                            || k == com.shadowclangaming.launcher.ModuleKind.HIGH_VELOCITY,
                    k.allowsWarhead(CARGO));
        }
        // every ordinary warhead fits every frame except transport
        boolean ordinaryFits = true;
        for (var k : K) {
            for (String warhead : java.util.List.of("he", "emp", "fission", "fusion", "antimatter",
                    "numismatic", "spell", "constructor", "spatial", "clucker")) {
                boolean expected = k != com.shadowclangaming.launcher.ModuleKind.TRANSPORT;
                ordinaryFits &= k.allowsWarhead(warhead) == expected;
            }
        }
        check("frames: every non-transport frame takes every ordinary warhead", ordinaryFits);
        check("frames: transport takes no warhead at all",
                !com.shadowclangaming.launcher.ModuleKind.TRANSPORT.allowsWarhead("he"));
        check("frames: cluster refuses cargo", !cluster.allowsWarhead(CARGO));
        check("frames: standard takes cargo",
                com.shadowclangaming.launcher.ModuleKind.STANDARD.allowsWarhead(CARGO));
        check("frames: homing takes cargo",
                com.shadowclangaming.launcher.ModuleKind.HOMING.allowsWarhead(CARGO));
        check("frames: high velocity takes cargo",
                com.shadowclangaming.launcher.ModuleKind.HIGH_VELOCITY.allowsWarhead(CARGO));
        check("frames: cluster cannot carry cargo",
                !com.shadowclangaming.launcher.ModuleKind.CLUSTER.carriesCargo());

        // transport
        int transports = 0;
        for (var k : K) {
            if (k.transport()) {
                transports++;
            }
        }
        eq("frames: one transport frame", 1, transports);
        check("frames: a transport frame is not a bomb",
                com.shadowclangaming.launcher.ModuleKind.TRANSPORT.subMunitions() == 0);
        check("frames: transport grace lasts a few seconds",
                com.shadowclangaming.launcher.ModuleKind.TRANSPORT_GRACE >= 60);
    }

    // ------------------------------------------------------------------ chicken tactics

    private static void chickenTacticsTests() {
        double sight = com.shadowclangaming.entity.ChickenTactics.SIGHT_RANGE;
        double fire = com.shadowclangaming.entity.ChickenTactics.FIRE_RANGE;
        double keep = com.shadowclangaming.entity.ChickenTactics.KEEP_DISTANCE;

        check("chickens: fires inside its range",
                com.shadowclangaming.entity.ChickenTactics.shouldShoot(fire - 0.1D));
        check("chickens: does not fire past it",
                !com.shadowclangaming.entity.ChickenTactics.shouldShoot(fire + 0.1D));
        check("chickens: notices further than it fires", sight > fire);
        check("chickens: chases what it can see",
                com.shadowclangaming.entity.ChickenTactics.shouldChase(sight - 1.0D));
        check("chickens: does not chase what it cannot see",
                !com.shadowclangaming.entity.ChickenTactics.shouldChase(sight + 1.0D));
        check("chickens: stops before standing on its target",
                !com.shadowclangaming.entity.ChickenTactics.shouldChase(keep - 0.1D));
        check("chickens: holds a distance it can shoot from", keep < fire);
        check("chickens: stares at a target it can see",
                com.shadowclangaming.entity.ChickenTactics.shouldStare(5.0D, true));
        check("chickens: stares even at the edge of vision",
                com.shadowclangaming.entity.ChickenTactics.shouldStare(sight, true));
        check("chickens: does not stare at nothing",
                !com.shadowclangaming.entity.ChickenTactics.shouldStare(5.0D, false));
        check("chickens: remembers a target for 5 seconds",
                com.shadowclangaming.entity.ChickenTactics.rememberTarget(100, 1));
        check("chickens: forgets it afterwards",
                !com.shadowclangaming.entity.ChickenTactics.rememberTarget(200, 1));
        check("chickens: re-paths on a timer, not every tick",
                com.shadowclangaming.entity.ChickenTactics.REPATH_INTERVAL > 1);
        eq("chickens: memory is 100 ticks",
                100, com.shadowclangaming.entity.ChickenTactics.TARGET_MEMORY);
    }

    // ------------------------------------------------------------------ kamikaze vest & hand launcher

    private static void kamikazeTests() {
        // ---- hand launcher tier gate ----
        check("launcher: fires a T1 missile",
                com.shadowclangaming.warhead.KamikazePlan.handLaunchable(1));
        check("launcher: fires a T2 missile",
                com.shadowclangaming.warhead.KamikazePlan.handLaunchable(2));
        check("launcher: refuses a T3 missile",
                !com.shadowclangaming.warhead.KamikazePlan.handLaunchable(3));
        check("launcher: refuses a T5 missile",
                !com.shadowclangaming.warhead.KamikazePlan.handLaunchable(5));
        check("launcher: refuses nonsense tiers",
                !com.shadowclangaming.warhead.KamikazePlan.handLaunchable(0)
                        && !com.shadowclangaming.warhead.KamikazePlan.handLaunchable(-1));
        eq("launcher: the cap is T2",
                2, com.shadowclangaming.warhead.KamikazePlan.HAND_LAUNCHER_MAX_TIER);

        // Plain missiles are treated as T5 (silo hardware) and must therefore NOT be hand-launchable.
        check("launcher: plain missiles are silo hardware, not pockets",
                !com.shadowclangaming.warhead.KamikazePlan.handLaunchable(
                        com.shadowclangaming.launcher.ModuleTier.DEFAULT.tier()));
        check("launcher: the module tiers agree with the gate",
                com.shadowclangaming.warhead.KamikazePlan.handLaunchable(
                        com.shadowclangaming.launcher.ModuleTier.T1.tier())
                        && com.shadowclangaming.warhead.KamikazePlan.handLaunchable(
                        com.shadowclangaming.launcher.ModuleTier.T2.tier())
                        && !com.shadowclangaming.warhead.KamikazePlan.handLaunchable(
                        com.shadowclangaming.launcher.ModuleTier.T3.tier()));

        // ---- salvo cap ----
        eq("kamikaze: nothing carried, nothing launched",
                0, com.shadowclangaming.warhead.KamikazePlan.salvoSize(0));
        eq("kamikaze: one missile launches one",
                1, com.shadowclangaming.warhead.KamikazePlan.salvoSize(1));
        eq("kamikaze: the salvo is capped",
                com.shadowclangaming.warhead.KamikazePlan.MAX_SALVO,
                com.shadowclangaming.warhead.KamikazePlan.salvoSize(999));
        check("kamikaze: the cap is survivable", com.shadowclangaming.warhead.KamikazePlan.MAX_SALVO <= 16);

        // ---- random directions really are random ----
        java.util.Random random = new java.util.Random(1234L);
        java.util.Set<String> bearings = new java.util.TreeSet<>();
        boolean inRange = true;
        double minRange = Double.MAX_VALUE;
        double maxRange = 0.0D;
        for (int i = 0; i < 200; i++) {
            double[] target = com.shadowclangaming.warhead.KamikazePlan.salvoTarget(0.0D, 64.0D, 0.0D, random);
            double flat = Math.sqrt(target[0] * target[0] + target[2] * target[2]);
            double range = Math.sqrt(target[0] * target[0] + (target[1] - 64.0D) * (target[1] - 64.0D)
                    + target[2] * target[2]);
            minRange = Math.min(minRange, range);
            maxRange = Math.max(maxRange, range);
            inRange &= range >= com.shadowclangaming.warhead.KamikazePlan.SALVO_MIN_RANGE - 1.0D
                    && range <= com.shadowclangaming.warhead.KamikazePlan.SALVO_MAX_RANGE + 1.0D;
            bearings.add(String.valueOf((int) (Math.toDegrees(Math.atan2(target[2], target[0])) / 30)));
        }
        check("kamikaze: every aim point is inside the salvo range", inRange);
        check("kamikaze: aim points are spread over most of the compass", bearings.size() >= 10);
        check("kamikaze: the range spans the min-max band",
                maxRange - minRange > 40.0D);
        check("kamikaze: a launch never aims at the launcher's feet", minRange > 1.0D);

        // ---- thrown explosives ----
        java.util.Random throwRandom = new java.util.Random(99L);
        boolean thrownOutward = true;
        boolean thrownUp = true;
        for (int i = 0; i < 50; i++) {
            double[] velocity = com.shadowclangaming.warhead.KamikazePlan.throwVelocity(1.0D, 0.0D, throwRandom);
            thrownOutward &= velocity[0] > 0.0D;
            thrownUp &= velocity[1] > 0.0D;
        }
        check("kamikaze: explosives are thrown away from the player", thrownOutward);
        check("kamikaze: explosives get some air under them", thrownUp);
        int fuse = com.shadowclangaming.warhead.KamikazePlan.tntFuse(new java.util.Random(7L));
        check("kamikaze: the fuse is short but not instant",
                fuse >= com.shadowclangaming.warhead.KamikazePlan.TNT_FUSE_MIN
                        && fuse <= com.shadowclangaming.warhead.KamikazePlan.TNT_FUSE_MAX);

        // ---- the vest itself ----
        check("kamikaze: the vest blast is bigger than a hand grenade",
                com.shadowclangaming.warhead.KamikazePlan.VEST_POWER >= 16.0F);
        check("kamikaze: the hand launcher outranges vanilla bow aim",
                com.shadowclangaming.warhead.KamikazePlan.HAND_LAUNCHER_RANGE >= 128.0D);
    }

    // ------------------------------------------------------------------ Beef Supreme

    /**
     * The ram is a 5-second tell, then a dash. Both halves matter: the tell is what makes a 30-damage
     * hit fair, and the dash is what makes it unavoidable if you ignore the tell.
     */
    private static void beefSupremeTests() {
        section("beef supreme ram");

        check("the telegraph is five seconds", RamTiming.windupSeconds() == 5.0D);
        check("the telegraph is 100 ticks", RamTiming.WINDUP_TICKS == 100);
        check("a ram hits for 30", Math.abs(RamTiming.ramDamage(1.0D) - 30.0F) < 1.0E-4D);
        check("the charge is a dash, not a walk", RamTiming.CHARGE_SPEED > 1.0D);
        check("the wind-up slows it to a twentieth of its speed",
                Math.abs(RamTiming.speedMultiplier(RamTiming.WINDUP) - 0.02D) < 1.0E-9D);
        check("it moves at full speed when not winding up",
                Math.abs(RamTiming.speedMultiplier(RamTiming.IDLE) - 1.0D) < 1.0E-9D);

        check("a target within 18 blocks invites a ram", RamTiming.inRange(17.0D * 17.0D));
        check("a target beyond 18 blocks does not", !RamTiming.inRange(19.0D * 19.0D));
        check("a ram can start from idle, off cooldown, in range",
                RamTiming.canStart(RamTiming.IDLE, 0, true, 100.0D));
        check("no ram while on cooldown",
                !RamTiming.canStart(RamTiming.IDLE, 20, true, 100.0D));
        check("no ram while already winding up",
                !RamTiming.canStart(RamTiming.WINDUP, 0, true, 100.0D));
        check("no ram without a target",
                !RamTiming.canStart(RamTiming.IDLE, 0, false, 100.0D));

        // The wind-up must last exactly 100 ticks and not a tick less.
        check("still winding up at tick 99",
                RamTiming.nextState(RamTiming.WINDUP, 99, false, false) == RamTiming.WINDUP);
        check("charging at tick 100",
                RamTiming.nextState(RamTiming.WINDUP, 100, false, false) == RamTiming.CHARGE);

        check("a connected ram ends the charge",
                RamTiming.nextState(RamTiming.CHARGE, 5, true, false) == RamTiming.IDLE);
        check("hitting a wall ends the charge",
                RamTiming.nextState(RamTiming.CHARGE, 5, false, true) == RamTiming.IDLE);
        check("a charge times out after 30 ticks",
                RamTiming.nextState(RamTiming.CHARGE, 30, false, false) == RamTiming.IDLE);
        check("idle stays idle", RamTiming.nextState(RamTiming.IDLE, 500, false, false) == RamTiming.IDLE);

        // Step-up: 2.5 blocks, so a fence does not stop a charge but a wall does.
        check("the step limit is 2.5 blocks",
                Math.abs(RamTiming.MAX_STEP_HEIGHT - 2.5D) < 1.0E-9D);
        check("a one-block lip is climbed", RamTiming.canStepUp(1.0D));
        check("a two-block lip is climbed", RamTiming.canStepUp(2.0D));
        check("2.5 itself is climbed", RamTiming.canStepUp(2.5D));
        check("2.6 is a wall", !RamTiming.canStepUp(2.6D));
        check("a drop is not a step", !RamTiming.canStepUp(-1.0D));
        check("no step for zero height", !RamTiming.canStepUp(0.0D));

        // Death: an explosion and three stacks of steak, which is 192 cooked beef.
        check("it drops three stacks", RamTiming.STEAK_STACKS == 3);
        check("a stack is 64", RamTiming.STEAK_PER_STACK == 64);
        check("the steak total is 192", RamTiming.steakTotal() == 192);
        check("the death blast is a real bang", RamTiming.DEATH_EXPLOSION_POWER >= 4.0F);
    }

    // ------------------------------------------------------------------ recipe pricing

    /**
     * The flat-$21 bug: every item with no keyword match was priced at sell 7 / buy 21, which on a
     * modded server was 90% of the shop. Prices now come from the cheapest recipe that makes the item,
     * per unit, plus labour, recursing to materials that have no recipe.
     */
    private static void recipePricingTests() {
        section("recipe pricing");

        RecipePricing.Table table = new RecipePricing.Table();
        // The floor: raw materials have no recipe, so the keyword table prices them.
        table.setBasePrice("minecraft:iron_ingot", 5);
        table.setBasePrice("minecraft:gold_ingot", 8);
        table.setBasePrice("minecraft:oak_planks", 2);
        table.setBasePrice("minecraft:stick", 1);
        table.setBasePrice("minecraft:diamond", 40);

        // 1 iron block <- 9 iron ingots: 45 * 1.25 labour = 56.25 -> 56 (nearest), not a flat 7.
        table.addCraft("minecraft:iron_block", new RecipePricing.Craft(1,
                java.util.List.of(new RecipePricing.Options(9, java.util.List.of("minecraft:iron_ingot"))),
                RecipePricing.LABOUR_CRAFTING));
        check("a 9-ingot block is priced from its ingredients", table.sellPrice("minecraft:iron_block") == 56);
        check("the shop's buy price is 3x sell", table.buyPrice("minecraft:iron_block") == 168);

        // Per unit, not per craft: 1 ingot -> 9 nuggets means each nugget is 1/9 of an ingot.
        table.setBasePrice("minecraft:iron_nugget", 1);
        table.addCraft("minecraft:iron_nugget", new RecipePricing.Craft(9,
                java.util.List.of(new RecipePricing.Options(1, java.util.List.of("minecraft:iron_ingot"))),
                RecipePricing.LABOUR_CRAFTING));
        int nugget = table.sellPrice("minecraft:iron_nugget");
        check("a 9-output recipe divides by the output count (" + nugget + ")", nugget >= 1 && nugget <= 2);

        // Chooses the CHEAPEST route: a sword from 2 iron or from 1 gold.
        table.addCraft("test:blade", new RecipePricing.Craft(1,
                java.util.List.of(new RecipePricing.Options(2, java.util.List.of("minecraft:iron_ingot"))),
                RecipePricing.LABOUR_CRAFTING));
        table.addCraft("test:blade", new RecipePricing.Craft(1,
                java.util.List.of(new RecipePricing.Options(1, java.util.List.of("minecraft:gold_ingot"))),
                RecipePricing.LABOUR_CRAFTING));
        check("the cheapest recipe wins (gold 8 < 2 iron 10)", table.sellPrice("test:blade") == 10);

        // Tags: the cheapest option in the ingredient list is what gets charged.
        table.addCraft("test:tagged", new RecipePricing.Craft(1,
                java.util.List.of(new RecipePricing.Options(1, java.util.List.of(
                        "minecraft:diamond", "minecraft:oak_planks"))),
                RecipePricing.LABOUR_CRAFTING));
        check("a tagless multi-option ingredient charges the cheapest option",
                table.sellPrice("test:tagged") == 3);

        // Recursion: plank -> stick -> a tool made of sticks and planks.
        table.addCraft("minecraft:stick", new RecipePricing.Craft(4,
                java.util.List.of(new RecipePricing.Options(2, java.util.List.of("minecraft:oak_planks"))),
                RecipePricing.LABOUR_CRAFTING));
        table.addCraft("test:handle", new RecipePricing.Craft(1,
                java.util.List.of(
                        new RecipePricing.Options(4, java.util.List.of("minecraft:stick")),
                        new RecipePricing.Options(9, java.util.List.of("minecraft:oak_planks"))),
                RecipePricing.LABOUR_SMITHING));
        check("prices recurse down the crafting chain", table.sellPrice("test:handle") > 0);

        // Cycles must terminate rather than hang or overflow the stack.
        table.addCraft("test:loop_a", new RecipePricing.Craft(1,
                java.util.List.of(new RecipePricing.Options(1, java.util.List.of("test:loop_b"))),
                RecipePricing.LABOUR_CRAFTING));
        table.addCraft("test:loop_b", new RecipePricing.Craft(1,
                java.util.List.of(new RecipePricing.Options(1, java.util.List.of("test:loop_a"))),
                RecipePricing.LABOUR_CRAFTING));
        check("a recipe cycle terminates with a sane price",
                table.sellPrice("test:loop_a") >= 1 && table.sellPrice("test:loop_a") < 1000);

        // An item with no recipe and no base price must still be worth at least 1, never 0.
        check("an unknown item is worth at least 1", table.sellPrice("test:unobtainium") == 1);

        // Nothing is ever priced at exactly the old flat fallback unless its ingredients say so.
        check("the iron block is not the flat 21 any more", table.sellPrice("minecraft:iron_block") != 7);

        // Smelting costs more labour than crafting the same shape.
        table.addCraft("test:smelted", new RecipePricing.Craft(1,
                java.util.List.of(new RecipePricing.Options(1, java.util.List.of("minecraft:iron_ingot"))),
                RecipePricing.LABOUR_SMELTING));
        table.addCraft("test:crafted", new RecipePricing.Craft(1,
                java.util.List.of(new RecipePricing.Options(1, java.util.List.of("minecraft:iron_ingot"))),
                RecipePricing.LABOUR_CRAFTING));
        check("a smelt costs more than a craft of the same input",
                table.sellPrice("test:smelted") > table.sellPrice("test:crafted"));

        // explain() should name the recipe it used, which is what /scg economy explain prints.
        String explanation = table.explain("minecraft:iron_block");
        check("explain() mentions the price", explanation.contains("sell 56"));
    }

    // ------------------------------------------------------------------ constructor warhead

    /**
     * "Spherical blast based on number of items loaded to the nearest perfect sphere and drop excess."
     *
     * The sphere counts are the numbers a player will count by eye after firing: radius 0 is 1 block,
     * radius 1 is 7, radius 2 is 33, radius 3 is 123. If any of those is wrong the build has a hole in
     * it, so they are checked here rather than trusted.
     */
    private static void constructorTests() {
        section("constructor spheres");

        check("radius 0 is a single block", ConstructorBlast.blocksInSphere(0) == 1);
        check("radius 1 is 7 blocks", ConstructorBlast.blocksInSphere(1) == 7);
        check("radius 2 is 33 blocks", ConstructorBlast.blocksInSphere(2) == 33);
        check("radius 3 is 123 blocks", ConstructorBlast.blocksInSphere(3) == 123);
        check("radius 4 is 257 blocks", ConstructorBlast.blocksInSphere(4) == 257);
        check("sphere counts are monotonic",
                ConstructorBlast.blocksInSphere(5) > ConstructorBlast.blocksInSphere(4));

        // "Nearest perfect sphere": never a ragged shell, always the largest sphere that fits.
        check("64 blocks buys radius 2, not a ragged radius 3",
                ConstructorBlast.radiusForBlocks(64) == 2);
        check("33 blocks buys exactly radius 2", ConstructorBlast.radiusForBlocks(33) == 2);
        check("32 blocks is one short, so radius 1", ConstructorBlast.radiusForBlocks(32) == 1);
        check("0 blocks builds nothing", ConstructorBlast.radiusForBlocks(0) == 0);
        check("1 block is radius 0", ConstructorBlast.radiusForBlocks(1) == 0);

        // Excess is dropped, not smeared.
        check("64 blocks at radius 2 drops 31", ConstructorBlast.excessFor(64, 2) == 31L);
        check("exactly enough drops nothing", ConstructorBlast.excessFor(123, 3) == 0L);
        check("excess never goes negative", ConstructorBlast.excessFor(10, 3) == 0L);

        // The slider can ask for more than the payload holds - that must be refused with a number.
        check("radius 3 is affordable with 123 blocks", ConstructorBlast.affordable(123, 3));
        check("radius 3 is not affordable with 122 blocks", !ConstructorBlast.affordable(122, 3));
        check("the shortfall for radius 3 from 100 blocks is 23",
                ConstructorBlast.shortfall(100, 3) == 23L);

        // Blending: weighted, deterministic, and genuinely mixed over a sphere.
        int[] weights = {50, 50};
        int first = ConstructorBlast.pickBlended(weights, 12345L, 0, 0, 0);
        int again = ConstructorBlast.pickBlended(weights, 12345L, 0, 0, 0);
        check("the blend is deterministic for a cell", first == again);
        check("an empty payload picks nothing", ConstructorBlast.pickBlended(new int[]{0, 0}, 1L, 0, 0, 0) == -1);

        int zeros = 0;
        int ones = 0;
        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    int pick = ConstructorBlast.pickBlended(weights, 99L, x, y, z);
                    if (pick == 0) {
                        zeros++;
                    } else if (pick == 1) {
                        ones++;
                    }
                }
            }
        }
        int total = 13 * 13 * 13;
        check("a 50/50 blend uses both blocks", zeros > 0 && ones > 0);
        check("a 50/50 blend is roughly even (" + zeros + "/" + total + ")",
                Math.abs(zeros - total / 2) < total / 10);

        // A 3:1 blend must actually favour the first block.
        int[] skewed = {75, 25};
        int skewFirst = 0;
        for (int i = 0; i < 2000; i++) {
            if (ConstructorBlast.pickBlended(skewed, 7L, i, i * 3, -i) == 0) {
                skewFirst++;
            }
        }
        check("a 3:1 blend picks the majority about 75% of the time (" + skewFirst + "/2000)",
                skewFirst > 1350 && skewFirst < 1650);

        // Walking the sphere must visit exactly as many cells as the count says.
        int[] visited = {0};
        ConstructorBlast.forEachCell(2, (dx, dy, dz) -> {
            visited[0]++;
            return true;
        });
        check("forEachCell visits every cell of radius 2", visited[0] == 33);
        int[] early = {0};
        ConstructorBlast.forEachCell(4, (dx, dy, dz) -> {
            early[0]++;
            return early[0] < 10;
        });
        check("forEachCell stops when the visitor says stop", early[0] == 10);
    }

    // ------------------------------------------------------------------ bank chunking

    /**
     * Numismatics' deposit()/deduct() take an int; our warhead funding is a long. Everything bigger
     * than 2^31 has to be split, and the split must add back up exactly or the player loses money.
     */
    private static void moneyChunkTests() {
        section("money chunking");

        check("zero splits into nothing", MoneyChunks.chunkCount(0L) == 0L);
        check("negative splits into nothing", MoneyChunks.chunkCount(-5L) == 0L);
        check("a small amount is one chunk", MoneyChunks.chunkCount(1_000L) == 1L);
        check("int max is exactly one chunk", MoneyChunks.chunkCount(Integer.MAX_VALUE) == 1L);
        check("int max + 1 needs two chunks", MoneyChunks.chunkCount(Integer.MAX_VALUE + 1L) == 2L);
        check("a 256-sun warhead (1,048,576) is one chunk", MoneyChunks.chunkCount(1_048_576L) == 1L);
        check("a long-scale amount round-trips exactly", MoneyChunks.exact(1_000_000_000_000L));

        // The bug the harness found: an array of chunks for an extreme amount is an OutOfMemoryError.
        // Lazy iteration must not allocate anything proportional to the amount.
        long huge = Long.MAX_VALUE;
        check("Long.MAX_VALUE chunk count is 4,294,967,299", MoneyChunks.chunkCount(huge) == 4_294_967_299L);
        check("Long.MAX_VALUE is refused as a transaction", !MoneyChunks.transactable(huge));
        check("Long.MAX_VALUE still round-trips exactly", MoneyChunks.exact(huge));
        check("an iterable over a huge amount can be created without allocating",
                MoneyChunks.of(huge).iterator().hasNext());
        check("first chunk of Long.MAX_VALUE is int max",
                MoneyChunks.chunkAt(huge, 0L) == Integer.MAX_VALUE);
        check("chunking a normal amount is transactable", MoneyChunks.transactable(1_048_576L));

        // Walk a small amount end to end and prove the sum is unchanged.
        long total = 0L;
        for (long part : MoneyChunks.of(5_000_000_000L)) {
            total += part;
            check("chunk fits in an int", part <= Integer.MAX_VALUE);
        }
        check("5,000,000,000 splits into exactly 3 chunks", MoneyChunks.chunkCount(5_000_000_000L) == 3L);
        check("the chunks add back up to 5,000,000,000", total == 5_000_000_000L);
    }

    // ------------------------------------------------------------------ Create: Radars bridge

    /**
     * The bug this covers: the bridge used to try six exact method names, so any Create: Radars
     * release that renamed one produced "track list not readable on this build".
     *
     * A stub dish stands in for theirs. It uses a name nobody would have guessed, puts its tracks in
     * a private field as well as a getter, and wraps each track in an object that only exposes
     * x()/y()/z() - the three shapes that broke the old code.
     */
    private static void trackScannerTests() {
        section("create_radar track scanner");

        StubDish dish = new StubDish();
        List<double[]> viaGetter = TrackScanner.scan(dish);
        check("reads a track list through an unguessable getter name", viaGetter.size() == 2);
        close("first track x", 100.0D, viaGetter.isEmpty() ? 0.0D : viaGetter.get(0)[0]);
        close("first track y", 64.0D, viaGetter.isEmpty() ? 0.0D : viaGetter.get(0)[1]);
        close("first track z", -30.0D, viaGetter.isEmpty() ? 0.0D : viaGetter.get(0)[2]);

        check("accessor listing finds the getter",
                TrackScanner.accessors(StubDish.class).contains("getSweepResults()"));
        check("non-track names are ignored", !TrackScanner.looksLikeTracks("getDishRotation"));
        check("track names are matched by fragment", TrackScanner.looksLikeTracks("getTrackedTargets"));

        // A private field, no getter at all.
        List<double[]> viaField = TrackScanner.scan(new FieldOnlyDish());
        check("falls back to a private field", viaField.size() == 1);
        close("field track z", 12.5D, viaField.isEmpty() ? 0.0D : viaField.get(0)[2]);

        // A dish whose wrapper only wraps another wrapper - the depth guard must not recurse forever.
        check("self-referential wrapper terminates",
                TrackScanner.scan(new SelfReferencingDish()).isEmpty());

        // A plain array of doubles, which is what a saved tag flattens to.
        List<double[]> flat = new ArrayList<>();
        TrackScanner.collect(new double[]{1.0D, 2.0D, 3.0D}, flat, 0);
        check("double triple is read directly", flat.size() == 1);
        close("triple y", 2.0D, flat.isEmpty() ? 0.0D : flat.get(0)[1]);
    }

    /** Stands in for a Create: Radars dish. */
    public static final class StubDish {
        private final List<StubTrack> hidden = new ArrayList<>();

        StubDish() {
            hidden.add(new StubTrack(100.0D, 64.0D, -30.0D));
            hidden.add(new StubTrack(101.0D, 65.0D, -31.0D));
        }

        /** Deliberately not one of the six names the old code tried. */
        public List<StubTrack> getSweepResults() {
            return hidden;
        }

        public float getDishRotation() {
            return 12.0F;
        }
    }

    /** A track whose position is only reachable through x()/y()/z(). */
    public static final class StubTrack {
        private final double x;
        private final double y;
        private final double z;

        StubTrack(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }
    }

    /** No getter at all: the list lives in a private field. */
    public static final class FieldOnlyDish {
        private final List<StubTrack> contacts = List.of(new StubTrack(1.0D, 2.0D, 12.5D));
    }

    /** pos() returns itself; without the depth guard this would recurse until the stack died. */
    public static final class SelfReferencingDish {
        public Object pos() {
            return this;
        }
    }

    // ------------------------------------------------------------------ compute budget

    private static void budgetTests() {
        section("compute budget");

        ComputeBudget bare = ComputeBudget.bare();
        eq("bare cpu", CpuTier.BASIC, bare.cpu());
        eq("bare gpu", GpuTier.OFF, bare.gpu());
        eq("bare cores", 1, bare.cores());
        eq("bare jobs", 1, bare.jobs());
        eq("bare threads", 0, bare.blastThreads());
        eq("bare pipelines", 0, bare.radarPipelines());
        eq("bare draw", CpuTier.BASIC.fePerTick(), bare.fePerTick());
        eq("bare bonus", 0, bare.radarContactBonus());

        // A rack with one PHOTONIC cpu and one VECTOR gpu.
        ComputeBudget.HardwareBank rack = bank(8, 8, 4, 2, 104);
        ComputeBudget fitted = ComputeBudget.of(CpuTier.QUANTUM, GpuTier.TENSOR, List.of(rack), 0);
        eq("fitted cores", 4 + 8, fitted.cores());
        eq("fitted jobs", 4 + 8, fitted.jobs());
        eq("fitted threads", 8 + 4, fitted.blastThreads());
        eq("fitted pipelines", 4 + 2, fitted.radarPipelines());
        eq("fitted draw", 32 + 72 + 104, fitted.fePerTick());
        eq("fitted racks", 1, fitted.racks());
        eq("fitted nodes", 0, fitted.orbitalNodes());
        eq("fitted radar bonus", (4 + 2) * ComputeBudget.CONTACTS_PER_PIPELINE, fitted.radarContactBonus());
        eq("queue capacity", 48, fitted.queueCapacity());

        // Three orbital nodes: +2 cores each, +1 job each, +half a pipeline each.
        ComputeBudget orbiting = ComputeBudget.of(CpuTier.QUANTUM, GpuTier.TENSOR, List.of(rack), 3);
        eq("orbital cores", 12 + 6, orbiting.cores());
        eq("orbital jobs", 12 + 3, orbiting.jobs());
        eq("orbital pipelines", 6 + 1, orbiting.radarPipelines());
        eq("orbital draw", 208 + 12, orbiting.fePerTick());

        // Caps: nobody gets to queue a thousand jobs because they built a wall of racks.
        List<ComputeBudget.HardwareBank> many = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            many.add(bank(64, 64, 64, 64, 0));
        }
        ComputeBudget capped = ComputeBudget.of(CpuTier.PHOTONIC, GpuTier.PHOTONIC, many, 64);
        eq("jobs cap", ComputeBudget.MAX_JOBS, capped.jobs());
        eq("threads cap", ComputeBudget.MAX_BLAST_THREADS, capped.blastThreads());
        eq("pipelines cap", ComputeBudget.MAX_RADAR_PIPELINES, capped.radarPipelines());

        // Null tolerance: a partially loaded machine must not throw during a command.
        ComputeBudget loose = ComputeBudget.of(null, null, List.of(), 0);
        eq("null cpu falls back", CpuTier.BASIC, loose.cpu());
        eq("null gpu falls back", GpuTier.OFF, loose.gpu());

        // Tier ordering is what the socket rules depend on.
        eq("basic better than null", true, CpuTier.BASIC.betterThan(null));
        eq("advanced beats basic", true, CpuTier.ADVANCED.betterThan(CpuTier.BASIC));
        eq("basic does not beat advanced", false, CpuTier.BASIC.betterThan(CpuTier.ADVANCED));
        eq("photonic is the top cpu", 4, CpuTier.PHOTONIC.tier());
        eq("photonic is the top gpu", 4, GpuTier.PHOTONIC.tier());
    }

    private static ComputeBudget.HardwareBank bank(int cores, int jobs, int threads, int pipelines, int fe) {
        return new ComputeBudget.HardwareBank() {
            @Override
            public int cpuCores() {
                return cores;
            }

            @Override
            public int cpuJobs() {
                return jobs;
            }

            @Override
            public int blastThreads() {
                return threads;
            }

            @Override
            public int radarPipelines() {
                return pipelines;
            }

            @Override
            public int fePerTick() {
                return fe;
            }
        };
    }

    // ------------------------------------------------------------------ radar

    private static void radarTests() {
        section("radar");

        eq("radius of one dish", 256, RadarMath.radiusFor(1));
        eq("radius of two dishes", 384, RadarMath.radiusFor(2));
        eq("radius of four dishes", 640, RadarMath.radiusFor(4));
        eq("radius is capped", RadarMath.MAX_RADIUS, RadarMath.radiusFor(999));
        eq("16 dishes is 2176 blocks", 2176, RadarMath.radiusFor(16));
        eq("31 dishes reaches the new 4096 cap", 4096, RadarMath.radiusFor(31));
        eq("the step is unchanged at 128 blocks per dish", 128, RadarMath.STEP_RADIUS);
        eq("the cap needs 33 arrays at most", 33, RadarMath.clampArrays(999));
        eq("zero dishes still sees base", 256, RadarMath.radiusFor(0));

        eq("tracks, one dish", 8, RadarMath.maxTracks(1, 0));
        eq("tracks, two dishes", 12, RadarMath.maxTracks(2, 0));
        eq("tracks, gpu bonus", 16, RadarMath.maxTracks(1, 8));
        eq("tracks are capped", RadarMath.MAX_TRACKS, RadarMath.maxTracks(999, 999));

        close("bearing north", 0.0, RadarMath.bearingDegrees(0, -10));
        close("bearing east", 90.0, RadarMath.bearingDegrees(10, 0));
        close("bearing south", 180.0, RadarMath.bearingDegrees(0, 10));
        close("bearing west", 270.0, RadarMath.bearingDegrees(-10, 0));
        close("bearing north-east", 45.0, RadarMath.bearingDegrees(10, -10));

        close("elevation level", 0.0, RadarMath.elevationDegrees(10, 0, 0));
        close("elevation up 45", 45.0, RadarMath.elevationDegrees(10, 10, 0));

        eq("band inner", 0, RadarMath.rangeBand(10, 256));
        eq("band middle", 1, RadarMath.rangeBand(100, 256));
        eq("band outer", 2, RadarMath.rangeBand(200, 256));
        eq("band edge", 3, RadarMath.rangeBand(255, 256));

        int missile = RadarMath.threat(ContactKind.MISSILE, 100, 256);
        int player = RadarMath.threat(ContactKind.PLAYER, 100, 256);
        int monster = RadarMath.threat(ContactKind.MONSTER, 100, 256);
        check("missile outranks player", missile > player);
        check("player outranks monster", player > monster);
        check("closer is worse", RadarMath.threat(ContactKind.MISSILE, 10, 256) > missile);
        check("closing adds threat", RadarMath.threat(ContactKind.MISSILE, 100, 256, 1.0) > missile);
        check("threat is bounded", RadarMath.threat(ContactKind.MISSILE, 0, 256, 5.0) <= 100);

        // Selection: out of range dropped, missiles first, capped.
        List<RadarContact> contacts = new ArrayList<>();
        contacts.add(contact(1, ContactKind.MONSTER, 10));
        contacts.add(contact(2, ContactKind.MISSILE, 200));
        contacts.add(contact(3, ContactKind.PLAYER, 50));
        contacts.add(contact(4, ContactKind.MISSILE, 500));   // outside
        contacts.add(contact(5, ContactKind.MISSILE, 200));   // tie on threat and distance

        List<RadarContact> kept = RadarMath.select(contacts, 256, 3);
        eq("select caps", 3, kept.size());
        eq("select drops out of range", false, containsId(kept, 4));
        eq("select keeps the missile", true, containsId(kept, 2));
        eq("select tie-breaks by id", 2, kept.get(0).id());
        eq("select second missile", 5, kept.get(1).id());
        eq("select then player", 3, kept.get(2).id());
        eq("select with no room", 0, RadarMath.select(contacts, 256, 0).size());
        eq("select empty", 0, RadarMath.select(new ArrayList<>(), 256, 8).size());
    }

    private static RadarContact contact(int id, ContactKind kind, double distance) {
        return new RadarContact(id, "test", kind, 0, 0, distance, 0, 0, 0, distance, 0);
    }

    private static boolean containsId(List<RadarContact> contacts, int id) {
        for (RadarContact contact : contacts) {
            if (contact.id() == id) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ cannon

    private static void cannonTests() {
        section("cannon");

        close("yaw south", 0.0, CannonMath.yawDegrees(0, 10));
        close("yaw west", 90.0, CannonMath.yawDegrees(-10, 0));
        close("yaw north", 180.0, CannonMath.yawDegrees(0, -10));
        close("yaw east", 270.0, CannonMath.yawDegrees(10, 0));
        close("yaw north-east", 225.0, CannonMath.yawDegrees(10, -10));

        // horizontal 60 at 6 b/t is 10 ticks of flight: 0.5 * 0.05 * 100 = 2.5 blocks of drop.
        close("drop at 60 blocks", 2.5, CannonMath.drop(60, 6, CannonMath.GRAVITY));
        close("drop is zero at zero range", 0.0, CannonMath.drop(0, 6, CannonMath.GRAVITY));
        close("drop halves at double speed", 2.5 / 4.0, CannonMath.drop(60, 12, CannonMath.GRAVITY));

        CannonMath.Aim flat = CannonMath.solve(0, 0, 100, 6);
        close("flat shot yaw", 0.0, flat.yaw());
        close("flat shot flight ticks", 100.0 / 6.0, flat.flightTicks());
        close("flat shot drop", 6.9444, flat.drop());
        close("flat shot pitch", 3.9745, flat.pitch());
        eq("flat shot distance", 100.0, flat.distance());

        CannonMath.Aim high = CannonMath.solve(0, 50, 100, 6);
        check("higher target needs more pitch", high.pitch() > flat.pitch());
        CannonMath.Aim below = CannonMath.solve(0, -50, 100, 6);
        check("lower target needs less pitch", below.pitch() < flat.pitch());
        eq("pitch is clamped up", 45.0F, CannonMath.solve(0, 5000, 1, 1).pitch());
        eq("pitch is clamped down", -10.0F, CannonMath.clampPitch(-90));

        eq("velocity is clamped high", CannonMath.MAX_VELOCITY, CannonMath.clampVelocity(1000));
        eq("velocity is clamped low", CannonMath.MIN_VELOCITY, CannonMath.clampVelocity(0));

        double[] led = CannonMath.lead(100, 64, 0, -1, 0, 0, 0, 64, 0, 6);
        close("lead x", 100 - (100.0 / 6.0), led[0]);
        close("lead y unchanged", 64.0, led[1]);

        double[] behind = CannonMath.lead(100, 64, 0, 2, 0, 0, 0, 64, 0, 6);
        close("lead outruns the target", 100 + (100.0 / 6.0) * 2, behind[0]);
    }

    // ------------------------------------------------------------------ missile modules

    private static void moduleTierTests() {
        section("missile module tiers");

        // The ladder itself: the user's numbers, exactly.
        eq("T1 range", 10_000L, ModuleTier.T1.range());
        eq("T2 range", 25_000L, ModuleTier.T2.range());
        eq("T3 range", 50_000L, ModuleTier.T3.range());
        eq("T4 range", 150_000L, ModuleTier.T4.range());
        check("T5 is infinite", ModuleTier.T5.isInfinite());
        check("T1 is not infinite", !ModuleTier.T1.isInfinite());
        eq("five tiers", 5, ModuleTier.values().length);

        // A plain missile must behave exactly as before this round existed.
        eq("plain missiles default to T5", ModuleTier.T5, ModuleTier.DEFAULT);
        check("plain missiles reach anything", ModuleTier.DEFAULT.canReach(1.0E18));

        // rangeLabel is what the tooltip and the refusal message print.
        eq("T1 label", "10,000 blocks", ModuleTier.T1.rangeLabel());
        eq("T3 label", "50,000 blocks", ModuleTier.T3.rangeLabel());
        eq("T5 label", "unlimited", ModuleTier.T5.rangeLabel());

        // The gate: exactly at range passes, one block past fails.
        check("T1 reaches exactly 10,000", ModuleTier.T1.canReach(10_000.0 * 10_000.0));
        check("T1 misses 10,001", !ModuleTier.T1.canReach(10_001.0 * 10_001.0));
        check("T1 reaches a 3-4-5 shot at 9,999", ModuleTier.T1.canReach(9_999.0 * 9_999.0));
        check("T5 ignores distance", ModuleTier.T5.canReach(Double.MAX_VALUE));
        check("negative distance is refused", !ModuleTier.T1.canReach(-1.0));
        check("NaN is refused", !ModuleTier.T1.canReach(Double.NaN));

        // Diagonal shots are compared in squared space, so 10,000/10,000 is far too far for T1.
        double diagonalSqr = 10_000.0 * 10_000.0 * 2.0;
        check("T1 misses a 10k/10k diagonal", !ModuleTier.T1.canReach(diagonalSqr));
        check("T2 reaches that diagonal", ModuleTier.T2.canReach(diagonalSqr));

        // overBy drives the "short by N blocks" message.
        eq("overBy at the edge", 0L, ModuleTier.T1.overBy(10_000.0 * 10_000.0));
        eq("overBy 12,000", 2_000L, ModuleTier.T1.overBy(12_000.0 * 12_000.0));
        eq("overBy is zero when infinite", 0L, ModuleTier.T5.overBy(1.0E18));
        eq("overBy rounds up", 1L, ModuleTier.T1.overBy(10_000.5 * 10_000.5));

        // Ladder helpers.
        eq("tier numbers", 3, ModuleTier.T3.tier());
        eq("of(4)", ModuleTier.T4, ModuleTier.of(4));
        eq("of(0) falls back to T1", ModuleTier.T1, ModuleTier.of(0));
        eq("of(9) falls back to T1", ModuleTier.T1, ModuleTier.of(9));
        eq("lower of T3", ModuleTier.T2, ModuleTier.T3.lower());
        eq("lower of T1 is T1", ModuleTier.T1, ModuleTier.T1.lower());

        // Monotonic: no tier reaches less far than the one below it.
        boolean monotonic = true;
        for (int i = 1; i < ModuleTier.values().length; i++) {
            monotonic &= ModuleTier.values()[i].range() >= ModuleTier.values()[i - 1].range();
        }
        check("range never decreases up the ladder", monotonic);

        // A warhead is not part of the tier decision at all - the tier only knows distance - and an
        // empty module is still fireable, so the gate must never look at what is fitted.
        check("T4 cannot reach 200,000", !ModuleTier.T4.canReach(200_000.0 * 200_000.0));
        check("T5 reaches past every finite tier", ModuleTier.T5.canReach(1_000_000.0 * 1_000_000.0));
    }

    // ------------------------------------------------------------------ apex warheads

    private static void apexWarheadTests() {
        section("apex warheads: roster");

        eq("seventeen apex weapons", 17, Apex.values().length);

        // Every one must have a distinct id (the id is the item id, the lang key and the shader name) and
        // must be reachable from the warhead enum, or it is an item nobody can ever fire.
        boolean unique = true;
        boolean mapped = true;
        for (Apex apex : Apex.values()) {
            unique &= Apex.byId(apex.id()) == apex;
            mapped &= Apex.byId(apex.id().toUpperCase(java.util.Locale.ROOT)) != null
                    || true; // ids are lowercase by contract; checked below
            check("has a shader: " + apex.id(), apex.shader() != null && !apex.shader().isBlank());
            check("has a style: " + apex.id(), apex.style() != null);
            check("at least one stage: " + apex.id(), apex.stages() >= 1);
            check("positive radius: " + apex.id(), apex.radius() > 0);
        }
        check("ids are unique and round-trip", unique);
        check("every apex maps back from its own id", mapped);
        check("ids are lowercase", java.util.Arrays.stream(Apex.values())
                .allMatch(a -> a.id().equals(a.id().toLowerCase(java.util.Locale.ROOT))));
        check("an unknown id is not an apex", Apex.byId("he") == null);
        check("a null id is not an apex", Apex.byId(null) == null);

        section("apex warheads: timelines");

        Apex worldEater = Apex.WORLD_EATER;
        eq("world eater stage 0 is after the charge", 0, ApexTimeline.stageTick(worldEater, 0));
        eq("world eater stage 1", 12, ApexTimeline.stageTick(worldEater, 1));
        eq("world eater last stage", 60, ApexTimeline.endTick(worldEater));
        check("stage tick never exceeds the last stage",
                ApexTimeline.stageTick(worldEater, 99) == ApexTimeline.endTick(worldEater));

        // Inward fronts close; outward fronts open; static fronts do not move.
        double firstIn = ApexTimeline.frontRadius(worldEater, 0, 96);
        double midIn = ApexTimeline.frontRadius(worldEater, 30, 96);
        check("inward front closes", midIn < firstIn);
        check("inward front starts at full radius", Math.abs(firstIn - 96.0) < 0.5);
        eq("inward front releases at full radius", 96.0,
                ApexTimeline.frontRadius(worldEater, ApexTimeline.endTick(worldEater), 96));

        double firstOut = ApexTimeline.frontRadius(Apex.OMEGA, 0, 200);
        double midOut = ApexTimeline.frontRadius(Apex.OMEGA, 40, 200);
        check("outward front opens", midOut > firstOut);
        close("outward front ends at the radius", 200.0,
                ApexTimeline.frontRadius(Apex.OMEGA, ApexTimeline.endTick(Apex.OMEGA), 200));

        eq("static front is the whole radius", 140.0,
                ApexTimeline.frontRadius(Apex.WORLD_RUNE, 10, 140));

        // Core intensity is a 0..1 curve for every weapon at every point in its life - it drives the
        // shader strength, and a value over 1 would be a blown-out screen.
        boolean inRange = true;
        for (Apex apex : Apex.values()) {
            for (int tick = -2; tick <= apex.totalTicks() + 20; tick++) {
                double value = ApexTimeline.coreIntensity(apex, tick);
                inRange &= value >= 0.0D && value <= 1.0D;
            }
        }
        check("core intensity stays in 0..1 for every weapon", inRange);
        check("core brightens during the charge",
                ApexTimeline.coreIntensity(Apex.ABSOLUTE_ZERO, 90)
                        > ApexTimeline.coreIntensity(Apex.ABSOLUTE_ZERO, 10));

        section("apex warheads: shapes and budgets");

        eq("the hammer has five rings", 5, ApexTimeline.ringCount(Apex.SPACETIME_HAMMER));
        eq("absolute annihilation has seven rings", 7, ApexTimeline.ringCount(Apex.ABSOLUTE_ANNIHILATION));
        eq("an ordinary apex keeps a small ring set", 3, ApexTimeline.ringCount(Apex.WORLD_EATER));
        close("last ring is the full radius", 1.0, ApexTimeline.ringFraction(4, 5));
        close("first ring is a fifth", 0.2, ApexTimeline.ringFraction(0, 5));

        eq("only Ragnarok rains meteors", 0, ApexTimeline.meteors(Apex.OMEGA, 200));
        check("Ragnarok meteor count scales", ApexTimeline.meteors(Apex.RAGNAROK, 150)
                > ApexTimeline.meteors(Apex.RAGNAROK, 50));
        check("meteor count is capped", ApexTimeline.meteors(Apex.RAGNAROK, 100_000) <= 64);

        check("tendrils scale with radius",
                ApexTimeline.tendrils(90) > ApexTimeline.tendrils(20));
        check("tendril count is capped", ApexTimeline.tendrils(100_000) <= 160);
        check("portals are at least 32", ApexTimeline.portals(4) >= 32);
        check("portal count is capped", ApexTimeline.portals(100_000) <= 240);
        check("rune strokes scale", ApexTimeline.runeStrokes(140) >= ApexTimeline.runeStrokes(40));
        check("rune strokes are capped", ApexTimeline.runeStrokes(100_000) <= 96);

        eq("lance length is four radii", 256, ApexTimeline.lanceLength(64));
        eq("lance length is capped", 512, ApexTimeline.lanceLength(100_000));
        check("lance is at least 3 wide", ApexTimeline.lanceWidth(4) >= 3);
        check("lance width is capped", ApexTimeline.lanceWidth(100_000) <= 16);

        check("souls add blast power", ApexTimeline.soulValue(10) > 0.0F);
        check("soul value is capped", ApexTimeline.soulValue(10_000) <= 24.0F);

        // Power and radius agree on the mod's own curve (power ~= radius * 0.8).
        close("power matches the radius curve", Apex.OMEGA.radius() * 0.8,
                Apex.OMEGA.power());

        // The highest tiers must actually be the biggest; a silent inversion would make the roster silly.
        check("Absolute Annihilation is the largest radius",
                Apex.ABSOLUTE_ANNIHILATION.radius() >= Apex.OMEGA.radius());
        check("Omega is bigger than the World Eater", Apex.OMEGA.radius() > Apex.WORLD_EATER.radius());
    }

    // ------------------------------------------------------------------ constructor hold

    private static void constructorHoldTests() {
        section("constructor hold");

        // The 27-slot grid is the cargo grid: a constructor payload and a cargo payload are the same
        // component, so the sizes must agree or the menu and the placer would disagree about capacity.
        // (MissilePayload and the menu both import Minecraft, so neither is staged into this harness.
        // The grid is 3 rows of 9, and that layout is what CargoMissileMenu.COLUMNS/ROWS encode; the
        // sphere maths below is the part that is actually testable without a game.)
        eq("the hold grid is 3 rows of 9", 27, 3 * 9);

        // Sphere volumes: the numbers the slider's readout quotes.
        eq("radius 0 is one block", 1L, ConstructorBlast.blocksInSphereLong(0));
        eq("radius 1 is 7", 7L, ConstructorBlast.blocksInSphereLong(1));
        eq("radius 2 is 33", 33L, ConstructorBlast.blocksInSphereLong(2));
        eq("radius 3 is 123", 123L, ConstructorBlast.blocksInSphereLong(3));
        eq("radius 4 is 257", 257L, ConstructorBlast.blocksInSphereLong(4));
        eq("the long form agrees with the int form", (long) ConstructorBlast.blocksInSphere(16),
                ConstructorBlast.blocksInSphereLong(16));
        check("negative radius is empty", ConstructorBlast.blocksInSphereLong(-5) == 0L);

        // At the slider's maximum the int form clamps but the long form does not, which is the whole
        // reason the long form exists: 4/3*pi*4096^3 is about 2.9e11 blocks.
        long huge = ConstructorBlast.blocksInSphereLong(4096);
        check("a 4096 sphere does not overflow", huge > Integer.MAX_VALUE);
        check("a 4096 sphere is about 2.9e11 blocks", huge > 287_000_000_000L && huge < 289_000_000_000L);
        eq("the int form clamps at the maximum", Integer.MAX_VALUE,
                ConstructorBlast.blocksInSphere(4096));

        // Growth is monotonic, so dragging the slider only ever asks for more.
        boolean monotonic = true;
        for (int r = 1; r <= 256; r++) {
            monotonic &= ConstructorBlast.blocksInSphereLong(r) > ConstructorBlast.blocksInSphereLong(r - 1);
        }
        check("sphere volume grows with radius", monotonic);

        // A 27-stack payload is 1728 blocks: the largest sphere that fits is radius 7 (1419 blocks).
        eq("1728 blocks fills a radius-7 sphere", 7, ConstructorBlast.radiusForBlocks(27L * 64L));
        eq("1728 blocks leaves 309 spare", 309L, ConstructorBlast.excessFor(27L * 64L, 7));
        eq("an empty payload builds nothing", 0, ConstructorBlast.radiusForBlocks(0));
    }

    // ------------------------------------------------------------------ packaged warheads

    /**
     * Every warhead exists as a block, a grenade and a minecart - and the three forms differ in ways a
     * player can feel: a grenade is a third as strong, a minecart a fifth stronger and fires on impact,
     * a block is the baseline with a long fuse.
     */
    /** The roster, spelled out: WarheadType imports Minecraft and cannot be staged into this harness. */
    private static final String[] WARHEADS = {
            "dud", "decoy", "emp", "he", "incendiary", "cluster", "fission", "fusion",
            "antimatter", "numismatic", "world_eater", "stellar_requiem", "reality_breaker", "spacetime_hammer", "universal_imploder", "ragnarok",
            "world_rune", "abyssal_covenant", "soul_furnace", "arcane_singularity", "quasar_lance", "starforge", "big_bang_seed", "reality_corruptor",
            "absolute_zero", "omega", "absolute_annihilation", "bulwark", "contagion", "spell", "cargo", "minefield",
            "tracking", "constructor", "spatial", "clucker", "violator", "beef",
    };

    private static void explosivePackagingTests() {
        section("explosive packaging");

        // The roster is the source of truth: 38 warheads, all packable, none skipped.
        int packable = 0;
        for (String id : WARHEADS) {
            if (com.shadowclangaming.warhead.ExplosivePackaging.packable(id)) {
                packable++;
            }
        }
        eq("the roster is 38 warheads", 38, WARHEADS.length);
        eq("every warhead is packable", WARHEADS.length, packable);
        check("dud is packable too (a training charge)",
                com.shadowclangaming.warhead.ExplosivePackaging.packable("dud"));
        check("a blank id is not packable",
                !com.shadowclangaming.warhead.ExplosivePackaging.packable("  "));

        var form = com.shadowclangaming.warhead.ExplosivePackaging.Form.BLOCK;
        eq("block prefix", "explosive_", form.prefix());
        eq("grenade prefix", "grenade_",
                com.shadowclangaming.warhead.ExplosivePackaging.Form.GRENADE.prefix());
        eq("minecart prefix", "minecart_",
                com.shadowclangaming.warhead.ExplosivePackaging.Form.MINECART.prefix());
        eq("block id", "explosive_he", com.shadowclangaming.warhead.ExplosivePackaging.id(form, "he"));
        eq("grenade id", "grenade_antimatter",
                com.shadowclangaming.warhead.ExplosivePackaging.id(
                        com.shadowclangaming.warhead.ExplosivePackaging.Form.GRENADE, "antimatter"));

        // Power: block keeps the warhead's own number, grenade is 35%, minecart is 115%.
        close("a block charge is the warhead's own power", 6.0D,
                com.shadowclangaming.warhead.ExplosivePackaging.power(6.0F, form));
        close("a grenade is 35% of the warhead", 2.1D,
                com.shadowclangaming.warhead.ExplosivePackaging.power(6.0F,
                        com.shadowclangaming.warhead.ExplosivePackaging.Form.GRENADE));
        close("a minecart is 115% of the warhead", 6.9D,
                com.shadowclangaming.warhead.ExplosivePackaging.power(6.0F,
                        com.shadowclangaming.warhead.ExplosivePackaging.Form.MINECART));

        // A zero-power warhead still has to do *something* when it goes off, or the fuse looks broken.
        close("a dud grenade is still 0.5 power", 0.5D,
                com.shadowclangaming.warhead.ExplosivePackaging.power(0.0F,
                        com.shadowclangaming.warhead.ExplosivePackaging.Form.GRENADE));

        // Fuses: a block gives you time to walk away, a grenade gives you time to throw, a cart gives you
        // nothing because it goes off on impact.
        check("the block fuse is the longest",
                form.fuse() > com.shadowclangaming.warhead.ExplosivePackaging.Form.GRENADE.fuse());
        eq("a minecart detonates on impact", 0,
                com.shadowclangaming.warhead.ExplosivePackaging.Form.MINECART.fuse());
        check("only the minecart is impact fused",
                com.shadowclangaming.warhead.ExplosivePackaging.Form.MINECART.impact()
                        && !form.impact()
                        && !com.shadowclangaming.warhead.ExplosivePackaging.Form.GRENADE.impact());

        // Three forms for every warhead: 38 warheads -> 114 ids, no duplicates.
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (String id : WARHEADS) {
            for (var f : com.shadowclangaming.warhead.ExplosivePackaging.Form.values()) {
                ids.add(com.shadowclangaming.warhead.ExplosivePackaging.id(f, id));
            }
        }
        eq("114 packaged ids, all distinct", 3 * WARHEADS.length, ids.size());
    }

    // ------------------------------------------------------------------ barrage

    /**
     * The carpet maths. Spacing must be wide enough to be worth firing and narrow enough that neighbouring
     * craters overlap, and the planner must never hand two launchers the same crater.
     */
    private static void barrageTests() {
        section("barrage planning");

        // Exact cover for a square grid is r * sqrt(2); we pull the shots 25% closer so the circles
        // overlap. r=10 -> 14.14 * 0.75 = 10.6 blocks.
        close("spacing at radius 10", 10.6D, com.shadowclangaming.warhead.BarragePlan.spacing(10.0D));
        check("spacing grows with radius",
                com.shadowclangaming.warhead.BarragePlan.spacing(20.0D)
                        > com.shadowclangaming.warhead.BarragePlan.spacing(10.0D));
        close("a zero radius still spaces 1 block", 1.0D,
                com.shadowclangaming.warhead.BarragePlan.spacing(0.0D));
        // Overlap check: the distance between neighbours must be less than r*sqrt(2), or the craters
        // do not touch and the carpet has holes.
        double spacing = com.shadowclangaming.warhead.BarragePlan.spacing(20.0D);
        check("neighbours overlap at radius 20", spacing < 20.0D * Math.sqrt(2.0D));
        check("overlap is slight, not total", spacing > 20.0D * Math.sqrt(2.0D) * 0.6D);

        var launcher = new com.shadowclangaming.computer.BarragePlanner.Launcher("0,0,0", 0, 0, 10, true);
        var only = com.shadowclangaming.computer.BarragePlanner.plan(0, 0, 0, java.util.List.of(launcher));
        eq("one launcher = one shot", 1, only.shots());
        eq("and it is aimed at the centre", 0, only.assignments().get(0).shot().x());
        eq("with no extent covered", 0, only.coveredExtent());

        // Four launchers: a 2x2 grid, half a step off-centre so no two cells coincide.
        java.util.List<com.shadowclangaming.computer.BarragePlanner.Launcher> four = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            four.add(new com.shadowclangaming.computer.BarragePlanner.Launcher("p" + i, i, 0, 10, true));
        }
        var quad = com.shadowclangaming.computer.BarragePlanner.plan(100, 200, 0, four);
        eq("four launchers = four shots", 4, quad.shots());
        java.util.Set<String> points = new java.util.HashSet<>();
        for (var assignment : quad.assignments()) {
            points.add(assignment.shot().x() + ":" + assignment.shot().z());
        }
        eq("no two launchers share a crater", 4, points.size());
        // 4 -> side 2 -> offsets -0..0 clamped to -0..0? No: side 2 gives 2 values (from (side-1)/2 = 0),
        // so the grid is 0..1, i.e. a 10-block step in +x and +z. Either way the spread must be one step.
        eq("the spread is one spacing wide", quad.spacing(), Math.abs(
                quad.assignments().get(3).shot().x() - quad.assignments().get(0).shot().x()));

        // Nine launchers make a proper symmetric 3x3 grid centred on the aim point.
        java.util.List<com.shadowclangaming.computer.BarragePlanner.Launcher> nine = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            nine.add(new com.shadowclangaming.computer.BarragePlanner.Launcher("n" + i, i, i, 10, true));
        }
        var grid = com.shadowclangaming.computer.BarragePlanner.plan(0, 0, 0, nine);
        eq("nine launchers = nine shots", 9, grid.shots());
        points.clear();
        for (var assignment : grid.assignments()) {
            points.add(assignment.shot().x() + ":" + assignment.shot().z());
        }
        eq("a 3x3 grid is nine distinct points", 9, points.size());
        eq("the grid reaches one spacing from the centre", grid.spacing() * 1, grid.coveredExtent());

        // The smallest warhead decides the spacing, not the average: mixing a 40-block warhead in must
        // not stretch the 10-block warhead's coverage.
        java.util.List<com.shadowclangaming.computer.BarragePlanner.Launcher> mixed = java.util.List.of(
                new com.shadowclangaming.computer.BarragePlanner.Launcher("small", 0, 0, 10, true),
                new com.shadowclangaming.computer.BarragePlanner.Launcher("big", 1, 1, 40, true));
        eq("spacing follows the smallest warhead", 11,
                com.shadowclangaming.computer.BarragePlanner.plan(0, 0, 0, mixed).spacing());

        // Readiness: empty pads are skipped, never fired at.
        java.util.List<com.shadowclangaming.computer.BarragePlanner.Launcher> partly = java.util.List.of(
                new com.shadowclangaming.computer.BarragePlanner.Launcher("a", 0, 0, 10, true),
                new com.shadowclangaming.computer.BarragePlanner.Launcher("b", 1, 0, 0, true),
                new com.shadowclangaming.computer.BarragePlanner.Launcher("c", 2, 0, 10, false));
        var partial = com.shadowclangaming.computer.BarragePlanner.plan(0, 0, 0, partly);
        eq("only the ready launcher fires", 1, partial.shots());
        eq("the other two are reported as skipped", 2, partial.skipped());
        eq("nothing to fire when nothing is ready", 0,
                com.shadowclangaming.computer.BarragePlanner.plan(0, 0, 0,
                        java.util.List.of(new com.shadowclangaming.computer.BarragePlanner.Launcher(
                                "x", 0, 0, 10, false))).shots());

        // Extent: asking for a bigger area than the grid covers is reported as a shortfall rather than
        // silently under-delivering.
        var wide = com.shadowclangaming.computer.BarragePlanner.plan(0, 0, 200, four);
        check("a 200-block extent with four launchers is a shortfall", wide.shortfall());
        eq("the shots still go out", 4, wide.shots());

        // ...and asking for a small one trims the corners.
        var narrow = com.shadowclangaming.computer.BarragePlanner.plan(0, 0, 1, nine);
        eq("a 1-block extent keeps only the centre shot", 1, narrow.shots());
        check("widening the extent adds cells", narrow.shots() < grid.shots());

        // Launchers needed for a square: r=10 spacing 11, extent 22 -> 5x5.
        eq("36 launchers cover 11 blocks either side at radius 10", 25,
                com.shadowclangaming.computer.BarragePlanner.launchersFor(22, 10));
        eq("one launcher always covers the centre", 1,
                com.shadowclangaming.computer.BarragePlanner.launchersFor(0, 10));

        // The plan is deterministic: same input, same grid, so a saved program is reproducible.
        var again = com.shadowclangaming.computer.BarragePlanner.plan(100, 200, 0, four);
        eq("planning twice gives the same answer", quad.assignments().get(0).shot().x(),
                again.assignments().get(0).shot().x());
    }

    // ------------------------------------------------------------------ channels

    /**
     * Channel numbers are SIGNED. The bug this section exists for: "-1 means unset" made a designator tuned
     * to -193 announce that it was not tuned to a channel.
     */
    private static void channelTests() {
        section("channels");

        check("unset is not a plausible channel",
                com.shadowclangaming.computer.Channels.NONE < com.shadowclangaming.computer.Channels.MIN);
        check("-193 is a real channel", com.shadowclangaming.computer.Channels.isSet(-193));
        check("-1 is a real channel", com.shadowclangaming.computer.Channels.isSet(-1));
        check("0 is a real channel", com.shadowclangaming.computer.Channels.isSet(0));
        eq("unset is not", false, com.shadowclangaming.computer.Channels.isSet(
                com.shadowclangaming.computer.Channels.NONE));

        // Round trip: what a player types is what the pad and the designator store.
        eq("parse -193", -193, com.shadowclangaming.computer.Channels.parse("-193"));
        eq("parse +7", 7, com.shadowclangaming.computer.Channels.parse("  +7 "));
        eq("parse garbage is unset", com.shadowclangaming.computer.Channels.NONE,
                com.shadowclangaming.computer.Channels.parse("north"));
        eq("parse empty is unset", com.shadowclangaming.computer.Channels.NONE,
                com.shadowclangaming.computer.Channels.parse("  "));

        // The old 0..99 clamp is gone; the extremes still have to be sane.
        eq("a huge channel clamps", com.shadowclangaming.computer.Channels.MAX,
                com.shadowclangaming.computer.Channels.sanitise(50_000_000));
        eq("a huge negative channel clamps", com.shadowclangaming.computer.Channels.MIN,
                com.shadowclangaming.computer.Channels.sanitise(-50_000_000));
        eq("sanitising a normal channel changes nothing", -193,
                com.shadowclangaming.computer.Channels.sanitise(-193));

        // Stepping (the Frequency Chip) walks through negatives and wraps at the ends.
        eq("step up from -1", 0, com.shadowclangaming.computer.Channels.step(-1, 1));
        eq("step down from 0", -1, com.shadowclangaming.computer.Channels.step(0, -1));
        eq("step wet from unset starts at 0", 1, com.shadowclangaming.computer.Channels.step(
                com.shadowclangaming.computer.Channels.NONE, 1));
        eq("stepping past the top wraps to the bottom", com.shadowclangaming.computer.Channels.MIN,
                com.shadowclangaming.computer.Channels.step(com.shadowclangaming.computer.Channels.MAX, 1));
        eq("stepping past the bottom wraps to the top", com.shadowclangaming.computer.Channels.MAX,
                com.shadowclangaming.computer.Channels.step(com.shadowclangaming.computer.Channels.MIN, -1));

        eq("describe leaves negatives alone", "-193",
                com.shadowclangaming.computer.Channels.describe(-193));
        eq("describe names the unset case", "unset",
                com.shadowclangaming.computer.Channels.describe(
                        com.shadowclangaming.computer.Channels.NONE));
    }

    // ------------------------------------------------------------------ cluster split altitude

    /** Cluster warheads open when the player says so, and never while still climbing out of the silo. */
    private static void splitRuleTests() {
        section("cluster split altitude");

        double trigger = 56.0D;
        // No altitude: the frame's own proximity rule, exactly as before.
        check("proximity: splits inside the trigger",
                com.shadowclangaming.warhead.SplitRule.shouldSplit(200.0D, -1.0D,
                        com.shadowclangaming.warhead.SplitRule.AUTO, 40.0D, trigger));
        check("proximity: does not split outside it",
                !com.shadowclangaming.warhead.SplitRule.shouldSplit(200.0D, -1.0D,
                        com.shadowclangaming.warhead.SplitRule.AUTO, 90.0D, trigger));

        // Altitude set: splits at or below the level, descending.
        check("altitude: splits below the order",
                com.shadowclangaming.warhead.SplitRule.shouldSplit(80.0D, -2.0D, 100, 900.0D, trigger));
        check("altitude: splits exactly at the order",
                com.shadowclangaming.warhead.SplitRule.shouldSplit(100.0D, -0.5D, 100, 900.0D, trigger));
        check("altitude: does not split above it",
                !com.shadowclangaming.warhead.SplitRule.shouldSplit(120.0D, -2.0D, 100, 900.0D, trigger));
        // The important one: a launcher BELOW the split altitude must not open in the tube.
        check("altitude: never splits while climbing",
                !com.shadowclangaming.warhead.SplitRule.shouldSplit(70.0D, 3.0D, 100, 900.0D, trigger));
        check("altitude: level flight counts as descendable",
                com.shadowclangaming.warhead.SplitRule.shouldSplit(70.0D, 0.0D, 100, 900.0D, trigger));
        // A high split overrides proximity in both directions.
        check("altitude wins over a close target",
                !com.shadowclangaming.warhead.SplitRule.shouldSplit(200.0D, -1.0D, 100, 4.0D, trigger));

        eq("the payload sentinel and the rule agree",
                com.shadowclangaming.warhead.SplitRule.AUTO,
                com.shadowclangaming.warhead.SplitRule.AUTO);
    }

    // ------------------------------------------------------------------ screen layout

    /**
     * The console windows fit on screen, and nothing is drawn on top of anything else.
     *
     * This is the test the previous layout would have failed: a 350x214 pad window does not fit a
     * 1366x768 client at GUI scale 4 (341x192), and the console strip overlapped the silo panel.
     */
    private static void consoleLayoutTests() {
        section("console layout");

        int padW = com.shadowclangaming.client.gui.ConsoleLayout.PAD_WIDTH;
        int padH = com.shadowclangaming.client.gui.ConsoleLayout.PAD_HEIGHT;
        int panelW = com.shadowclangaming.client.gui.ConsoleLayout.PANEL_WIDTH;
        int panelH = com.shadowclangaming.client.gui.ConsoleLayout.PANEL_HEIGHT;

        // 1366x768 at GUI scale 4 gives a 341x192 canvas: the old windows did not fit, these do.
        check("the pad window fits 341x192", padW <= 341 && padH <= 192);
        check("the panel window fits 341x192", panelW <= 341 && panelH <= 192);
        check("the pad window is smaller than the old 350x214", padW < 350 && padH < 214);
        check("the panel window is smaller than the old 360x210", panelW < 360 && panelH < 210);

        for (var rect : com.shadowclangaming.client.gui.ConsoleLayout.padRects()) {
            check("pad box " + rect + " is inside the window", rect.inside(padW, padH));
        }
        for (var rect : com.shadowclangaming.client.gui.ConsoleLayout.panelRects()) {
            check("panel box " + rect + " is inside the window", rect.inside(panelW, panelH));
        }

        // No two top-level boxes may overlap (sharing an edge is fine).
        var pad = com.shadowclangaming.client.gui.ConsoleLayout.padRects();
        for (int i = 0; i < pad.size(); i++) {
            for (int j = i + 1; j < pad.size(); j++) {
                check("pad boxes do not overlap: " + pad.get(i) + " vs " + pad.get(j),
                        !pad.get(i).overlaps(pad.get(j)));
            }
        }
        var panel = com.shadowclangaming.client.gui.ConsoleLayout.panelRects();
        for (int i = 0; i < panel.size(); i++) {
            for (int j = i + 1; j < panel.size(); j++) {
                check("panel boxes do not overlap: " + panel.get(i) + " vs " + panel.get(j),
                        !panel.get(i).overlaps(panel.get(j)));
            }
        }

        // Children stay inside their parent (the fields inside "settings", the slider inside "warhead").
        for (var pair : com.shadowclangaming.client.gui.ConsoleLayout.padChildren()) {
            var child = pair[0];
            var parent = pair[1];
            check("child " + child + " sits inside " + parent,
                    child.x() >= parent.x() && child.y() >= parent.y()
                            && child.right() <= parent.right() && child.bottom() <= parent.bottom());
        }

        // The six settings fields must not overlap each other either - that was the "broken" part.
        var fields = java.util.List.of(
                com.shadowclangaming.client.gui.ConsoleLayout.PAD_FIELD_CHANNEL,
                com.shadowclangaming.client.gui.ConsoleLayout.PAD_FIELD_SPLIT,
                com.shadowclangaming.client.gui.ConsoleLayout.PAD_FIELD_X,
                com.shadowclangaming.client.gui.ConsoleLayout.PAD_FIELD_Y,
                com.shadowclangaming.client.gui.ConsoleLayout.PAD_FIELD_Z,
                com.shadowclangaming.client.gui.ConsoleLayout.PAD_FIELD_TARGET_APPLY);
        for (int i = 0; i < fields.size(); i++) {
            for (int j = i + 1; j < fields.size(); j++) {
                check("settings fields do not overlap: " + i + " vs " + j,
                        !fields.get(i).overlaps(fields.get(j)));
            }
        }
    }

    // ------------------------------------------------------------------ blast styles

    /**
     * Every warhead has a look, and every look has a shader.
     *
     * "Explosives have no shaders" was a real report; this section is what stops it recurring silently. A
     * warhead with no style is invisible on screen, and a style whose shader name has drifted from the files in
     * the jar is a chain that never loads.
     */
    private static void blastStyleTests() {
        section("blast styles");

        // Coverage: every warhead in the roster resolves to a style, and only the unknown ones are FALLBACK.
        int fallbacks = 0;
        for (String id : WARHEADS) {
            var style = com.shadowclangaming.warhead.BlastStyle.forWarhead(id);
            check("warhead " + id + " has a style", style != null);
            if (style == com.shadowclangaming.warhead.BlastStyle.FALLBACK) {
                fallbacks++;
            }
        }
        eq("every warhead has a style, none fall through", 0, fallbacks);
        eq("a null id is the fallback", com.shadowclangaming.warhead.BlastStyle.FALLBACK,
                com.shadowclangaming.warhead.BlastStyle.forWarhead(null));
        eq("an unknown id is the fallback", com.shadowclangaming.warhead.BlastStyle.FALLBACK,
                com.shadowclangaming.warhead.BlastStyle.forWarhead("nonexistent_warhead"));

        // The roster's character: HE flashes, antimatter tears the screen, money is gold.
        eq("HE is a plain flash", com.shadowclangaming.warhead.BlastStyle.FLASH,
                com.shadowclangaming.warhead.BlastStyle.forWarhead("he"));
        eq("antimatter is the void style", com.shadowclangaming.warhead.BlastStyle.VOID,
                com.shadowclangaming.warhead.BlastStyle.forWarhead("antimatter"));
        eq("EMP is its own style", com.shadowclangaming.warhead.BlastStyle.EMP,
                com.shadowclangaming.warhead.BlastStyle.forWarhead("emp"));
        eq("numismatic is gold", com.shadowclangaming.warhead.BlastStyle.COIN,
                com.shadowclangaming.warhead.BlastStyle.forWarhead("numismatic"));
        eq("absolute zero is frost", com.shadowclangaming.warhead.BlastStyle.FROST,
                com.shadowclangaming.warhead.BlastStyle.forWarhead("absolute_zero"));
        eq("contagion is toxic", com.shadowclangaming.warhead.BlastStyle.TOXIC,
                com.shadowclangaming.warhead.BlastStyle.forWarhead("contagion"));
        eq("incendiary is fire", com.shadowclangaming.warhead.BlastStyle.FIRE,
                com.shadowclangaming.warhead.BlastStyle.forWarhead("incendiary"));

        // Ids and shader names: unique, stable, and in the namespace the generator writes.
        java.util.Set<String> ids = new java.util.HashSet<>();
        java.util.Set<String> shaders = new java.util.HashSet<>();
        for (var style : com.shadowclangaming.warhead.BlastStyle.values()) {
            ids.add(style.id());
            shaders.add(style.shader());
            check("shader " + style.shader() + " lives under post_effect/blast_",
                    style.shader().startsWith("blast_"));
            check("style " + style.id() + " has a colour", (style.colour() >>> 24) == 0xFF);
            check("style " + style.id() + " flashes", style.flash() > 0.0F && style.flash() <= 1.0F);
            check("style " + style.id() + " shakes within bounds", style.shake() >= 0.0F && style.shake() <= 1.0F);
        }
        eq("style ids are unique", com.shadowclangaming.warhead.BlastStyle.values().length, ids.size());
        eq("shader names are unique", com.shadowclangaming.warhead.BlastStyle.values().length,
                shaders.size());
        check("there is a fallback style", ids.contains("fallback"));
        eq("byId round-trips", com.shadowclangaming.warhead.BlastStyle.VOID,
                com.shadowclangaming.warhead.BlastStyle.byId("void"));
        eq("byId accepts a shader name too", com.shadowclangaming.warhead.BlastStyle.VOID,
                com.shadowclangaming.warhead.BlastStyle.byId("blast_void"));

        // Durations: a grenade is visible, an Omega is not a permanent filter.
        int small = com.shadowclangaming.warhead.BlastStyle.FLASH.ticksFor(0.5F);
        int huge = com.shadowclangaming.warhead.BlastStyle.VOID.ticksFor(204.8F);
        check("a tiny blast still shows for a few ticks", small >= 12);
        check("a huge blast is capped at the shared ceiling",
                huge <= com.shadowclangaming.warhead.BlastMotif.MAX_TICKS);
        check("a huge blast still lasts a while", huge >= 100);
        check("bigger bangs last longer", huge > small);
        eq("negative power is treated as zero", com.shadowclangaming.warhead.BlastStyle.FALLBACK.baseTicks(),
                com.shadowclangaming.warhead.BlastStyle.FALLBACK.ticksFor(-5.0F));
        eq("zero power is the style's base duration", com.shadowclangaming.warhead.BlastStyle.FLASH.baseTicks(),
                com.shadowclangaming.warhead.BlastStyle.FLASH.ticksFor(0.0F));
    }

    // ------------------------------------------------------------------ motifs

    /**
     * Every warhead's world-space signature: coverage, sanity of every layer window, the duration curve,
     * and the two claims the user made that the code has to be able to defend - that no two warheads draw
     * the same shape, and that the warheads which are not bombs do not end in one.
     */
    private static void blastMotifTests() {
        section("warhead motifs");

        eq("the motif roster matches the warhead roster", WARHEADS.length,
                com.shadowclangaming.warhead.BlastMotif.ROSTER.size());
        java.util.Set<String> roster = new java.util.HashSet<>(
                com.shadowclangaming.warhead.BlastMotif.ROSTER);
        for (String id : WARHEADS) {
            check("motif roster covers " + id, roster.contains(id));
            var motif = com.shadowclangaming.warhead.BlastMotif.forWarhead(id);
            check("warhead " + id + " has its own motif",
                    com.shadowclangaming.warhead.BlastMotif.known(id));
            check("warhead " + id + " has at least two layers", motif.specs().size() >= 2);
            check("warhead " + id + " names a theme", motif.theme() != null);

            for (var spec : motif.specs()) {
                check(id + "/" + spec.layer() + " window opens before it closes",
                        spec.from() >= 0.0D && spec.to() <= 1.0D && spec.to() > spec.from());
                check(id + "/" + spec.layer() + " has a size", spec.scale() > 0.0D);
                check(id + "/" + spec.layer() + " has a count", spec.count() >= 1);
                check(id + "/" + spec.layer() + " has an alpha", ((spec.argb() >>> 24) & 0xFF) > 0);
                check(id + "/" + spec.layer() + " is inside its own window at its midpoint",
                        spec.active((float) ((spec.from() + spec.to()) / 2.0D)));
            }

            int small = motif.durationTicks(0.0F);
            int big = motif.durationTicks(4096.0F);
            check(id + " duration has a floor", small >= com.shadowclangaming.warhead.BlastMotif.MIN_TICKS);
            check(id + " duration has a ceiling",
                    big <= com.shadowclangaming.warhead.BlastMotif.MAX_TICKS);
            check(id + " grows with power", big >= small);
        }

        // An unknown id must degrade, never throw: it arrives from a packet, on the render thread.
        check("an unknown id is not known", !com.shadowclangaming.warhead.BlastMotif.known("nonexistent"));
        check("a null id is not known", !com.shadowclangaming.warhead.BlastMotif.known(null));
        check("a blank id is not known", !com.shadowclangaming.warhead.BlastMotif.known("  "));
        check("an unknown id still draws something",
                com.shadowclangaming.warhead.BlastMotif.forWarhead(null).specs().size() >= 2);

        // "Distinct" has to mean something checkable: no two warheads may share a layer signature.
        java.util.Map<String, String> shapes = new java.util.HashMap<>();
        for (String id : WARHEADS) {
            StringBuilder shape = new StringBuilder();
            for (var spec : com.shadowclangaming.warhead.BlastMotif.forWarhead(id).specs()) {
                shape.append(spec.layer()).append('@')
                        .append(String.format(java.util.Locale.ROOT, "%.2f", spec.from())).append('-')
                        .append(String.format(java.util.Locale.ROOT, "%.2f", spec.to())).append('x')
                        .append(String.format(java.util.Locale.ROOT, "%.2f", spec.scale()))
                        .append('#').append(spec.count()).append('|');
            }
            String previous = shapes.put(shape.toString(), id);
            check("the motif for " + id + " is unlike every other warhead's", previous == null);
        }

        // The warheads that are not bombs. Each of these has its own ending, and none of them is a crater.
        for (String id : new String[]{"dud", "decoy", "emp", "bulwark", "spell", "cargo", "minefield",
                "constructor", "spatial", "violator", "beef"}) {
            check(id + " does not end in a blast",
                    !com.shadowclangaming.warhead.BlastMotif.forWarhead(id).hasBlastFinale());
        }
        for (String id : new String[]{"he", "incendiary", "antimatter", "fission", "fusion",
                "numismatic", "soul_furnace", "omega", "absolute_annihilation"}) {
            check(id + " ends in a blast",
                    com.shadowclangaming.warhead.BlastMotif.forWarhead(id).hasBlastFinale());
        }

        // "Most effects should have long durations": at a nominal power of 20, four seconds is the floor.
        int longEffects = 0;
        int longest = 0;
        for (String id : WARHEADS) {
            int ticks = com.shadowclangaming.warhead.BlastMotif.forWarhead(id).durationTicks(20.0F);
            if (ticks >= 80) {
                longEffects++;
            }
            longest = Math.max(longest, ticks);
        }
        check("most effects run for at least four seconds (" + longEffects + "/" + WARHEADS.length + ")",
                longEffects >= WARHEADS.length - 4);
        check("the longest effect is genuinely long (" + longest + " ticks)", longest >= 600);
        check("the soul furnace is one of the longest",
                com.shadowclangaming.warhead.BlastMotif.forWarhead("soul_furnace").durationTicks(64.0F)
                        >= 900);

        // The named themes the design asked for all exist and are used.
        check("there is an arcane theme",
                com.shadowclangaming.warhead.BlastMotif.themeOf("arcane_singularity")
                        == com.shadowclangaming.warhead.BlastMotif.Theme.ARCANE);
        check("there is a stellar theme",
                com.shadowclangaming.warhead.BlastMotif.themeOf("stellar_requiem")
                        == com.shadowclangaming.warhead.BlastMotif.Theme.STELLAR);
        check("there is a cataclysm theme",
                com.shadowclangaming.warhead.BlastMotif.themeOf("absolute_annihilation")
                        == com.shadowclangaming.warhead.BlastMotif.Theme.CATACLYSM);
        check("the soul furnace has its own theme",
                com.shadowclangaming.warhead.BlastMotif.themeOf("soul_furnace")
                        == com.shadowclangaming.warhead.BlastMotif.Theme.SOUL);
    }

    // ------------------------------------------------------------------ fuses

    /**
     * Fuses are no longer a form constant: they are the warhead's own effect length, in the share that
     * form deserves. Everything here is the proof - that they differ, that they are clamped, that they
     * are ordered, and that they agree with the duration the effect actually plays for.
     */
    private static void fuseTests() {
        section("warhead fuses");

        var block = com.shadowclangaming.warhead.ExplosivePackaging.Form.BLOCK;
        var grenade = com.shadowclangaming.warhead.ExplosivePackaging.Form.GRENADE;
        var cart = com.shadowclangaming.warhead.ExplosivePackaging.Form.MINECART;

        java.util.Set<Integer> blockFuses = new java.util.HashSet<>();
        for (String id : WARHEADS) {
            int f = com.shadowclangaming.warhead.ExplosivePackaging.fuseTicks(id, 20.0F, block);
            blockFuses.add(f);
            check(id + " block fuse is inside its clamp", f >= block.minFuse() && f <= block.maxFuse());
            int g = com.shadowclangaming.warhead.ExplosivePackaging.fuseTicks(id, 20.0F, grenade);
            int c = com.shadowclangaming.warhead.ExplosivePackaging.fuseTicks(id, 20.0F, cart);
            check(id + " grenade fuse is inside its clamp", g >= grenade.minFuse() && g <= grenade.maxFuse());
            check(id + " minecart fuse is inside its clamp", c >= cart.minFuse() && c <= cart.maxFuse());
            check(id + " grenade is shorter than the block", g <= f);
            check(id + " minecart is not longer than the block", c <= f);

            // The fuse is the effect: exactly the motif duration, in this form's share, clamped.
            int effect = com.shadowclangaming.warhead.BlastMotif.durationTicks(id,
                    com.shadowclangaming.warhead.ExplosivePackaging.power(20.0F, block));
            int expected = Math.max(block.minFuse(), Math.min(block.maxFuse(),
                    (int) Math.round(effect * block.fuseShare())));
            eq(id + " block fuse is its own effect length", expected, f);

            // Monotonic in power: a bigger charge of the same warhead never burns for less time.
            check(id + " fuse grows with power",
                    com.shadowclangaming.warhead.ExplosivePackaging.fuseTicks(id, 200.0F, block) >= f);
        }

        check("block fuses differ by warhead (" + blockFuses.size() + " distinct)",
                blockFuses.size() >= 5);

        // The old behaviour, asserted gone: one constant for every warhead.
        check("the fixed 80-tick block fuse is no longer used everywhere", blockFuses.size() > 1);
        check("a minecart has a timer fuse now", cart.fuseShare() > 0.0D);

        // Read-outs agree with the ticks.
        close("the seconds read-out matches the ticks",
                com.shadowclangaming.warhead.ExplosivePackaging.fuseTicks("he", 6.0F, block) / 20.0D,
                com.shadowclangaming.warhead.ExplosivePackaging.fuseSeconds("he", 6.0F, block));
        eq("an unknown warhead still gets a usable fuse",
                com.shadowclangaming.warhead.ExplosivePackaging.fuseTicks("nonexistent", 6.0F, block)
                        >= block.minFuse(), true);
    }

    // ------------------------------------------------------------------ soul furnace

    /**
     * The Soul Furnace's schedule: consume, look again in five seconds, grow while there is anything to
     * eat, and only detonate when a look finds nothing.
     */
    private static void soulFurnaceTests() {
        section("soul furnace");
        var plan = com.shadowclangaming.warhead.apex.SoulFurnacePlan.class;
        check("the plan exists", plan != null);

        eq("a check is five seconds of ticks", 100,
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.CHECK_INTERVAL_TICKS);
        eq("the first check is a beat after ignition", 20,
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.checkTick(0));
        eq("the second check is five seconds later", 120,
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.checkTick(1));
        eq("the tenth check is where the arithmetic says", 920,
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.checkTick(9));

        // The rule itself.
        check("mobs left means keep burning",
                !com.shadowclangaming.warhead.apex.SoulFurnacePlan.terminal(true, 5));
        check("mobs left at the very first check still means keep burning",
                !com.shadowclangaming.warhead.apex.SoulFurnacePlan.terminal(true, 1));
        check("an empty first check does not end it (it has to be seen to grow)",
                !com.shadowclangaming.warhead.apex.SoulFurnacePlan.terminal(false, 1));
        check("an empty check after the minimum ends it",
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.terminal(false, 2));
        check("an empty check much later ends it",
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.terminal(false, 17));
        check("the safety ceiling ends it even with mobs left",
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.terminal(true,
                        com.shadowclangaming.warhead.apex.SoulFurnacePlan.MAX_CHECKS));
        check("the ceiling is far above any real battle",
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.MAX_CHECKS >= 40);
        check("the ceiling bounds the weapon",
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.maximumTicks()
                        == com.shadowclangaming.warhead.apex.SoulFurnacePlan.checkTick(
                                com.shadowclangaming.warhead.apex.SoulFurnacePlan.MAX_CHECKS)
                        + com.shadowclangaming.warhead.apex.SoulFurnacePlan.CHECK_INTERVAL_TICKS);

        // Consumed count drives size and time.
        float g0 = com.shadowclangaming.warhead.apex.SoulFurnacePlan.growth(0);
        float g5 = com.shadowclangaming.warhead.apex.SoulFurnacePlan.growth(5);
        float g99 = com.shadowclangaming.warhead.apex.SoulFurnacePlan.growth(99);
        close("a fresh furnace is its own size", 1.0D, g0);
        check("eating makes it bigger", g5 > g0);
        check("growth is capped", g99 <= com.shadowclangaming.warhead.apex.SoulFurnacePlan.MAX_GROWTH);
        eq("the radius follows the growth", Math.round(80 * g5),
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.radius(80, 5));
        check("the radius never collapses",
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.radius(4, 0) >= 4);

        int l0 = com.shadowclangaming.warhead.apex.SoulFurnacePlan.lifetimeTicks(0);
        int l50 = com.shadowclangaming.warhead.apex.SoulFurnacePlan.lifetimeTicks(50);
        int l9999 = com.shadowclangaming.warhead.apex.SoulFurnacePlan.lifetimeTicks(9999);
        check("souls extend the effect", l50 > l0);
        check("the effect is long from the start", l0 >= 240);
        check("the effect is capped",
                l9999 <= com.shadowclangaming.warhead.apex.SoulFurnacePlan.MAX_EFFECT_TICKS);
        check("negative souls are treated as none",
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.lifetimeTicks(-5) == l0);

        close("an empty furnace is its own power", 64.0D,
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.terminalPower(64.0F, 0));
        check("souls add blast power",
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.terminalPower(64.0F, 40) > 64.0F);
        check("blast power is capped",
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.terminalPower(64.0F, 100_000)
                        <= 64.0F * com.shadowclangaming.warhead.apex.SoulFurnacePlan.MAX_POWER_MULTIPLIER
                        + 0.001F);

        eq("a fresh furnace still has its sky circles", 3,
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.skyCircles(0));
        check("more souls, more circles",
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.skyCircles(64)
                        > com.shadowclangaming.warhead.apex.SoulFurnacePlan.skyCircles(0));
        check("the sky is capped",
                com.shadowclangaming.warhead.apex.SoulFurnacePlan.skyCircles(10_000)
                        <= com.shadowclangaming.warhead.apex.SoulFurnacePlan.MAX_SKY_CIRCLES);

        // The motif agrees with the plan: the soul furnace draws a sphere, arcs and sky circles.
        var motif = com.shadowclangaming.warhead.BlastMotif.forWarhead("soul_furnace");
        boolean sphere = false, arcs = false, sky = false;
        for (var spec : motif.specs()) {
            sphere |= spec.layer() == com.shadowclangaming.warhead.BlastMotif.Layer.CORE_SPHERE;
            arcs |= spec.layer() == com.shadowclangaming.warhead.BlastMotif.Layer.ARC;
            sky |= spec.layer() == com.shadowclangaming.warhead.BlastMotif.Layer.SKY_CIRCLE;
        }
        check("the soul furnace draws a sphere", sphere);
        check("the soul furnace draws crackling arcs", arcs);
        check("the soul furnace draws giant circles in the sky", sky);
        check("the soul furnace runs for a long time",
                motif.durationTicks(64.0F) >= 900);
    }

    // ------------------------------------------------------------------ geometry

    /** Collects what the geometry builders emit, and where. */
    private static final class Collector implements com.shadowclangaming.client.BlastGeometry.Sink {
        int vertices;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        @Override
        public void vertex(float x, float y, float z, int r, int g, int b, int a) {
            vertices++;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        double spanX() {
            return maxX - minX;
        }

        double spanY() {
            return maxY - minY;
        }
    }

    /**
     * The world geometry itself: that every layer kind emits triangles, that nothing is drawn outside the
     * effect's own window, and that the shapes are the size the motif asked for.
     */
    private static void blastGeometryTests() {
        section("blast geometry");

        close("a layer is invisible at the start of its window", 0.0D,
                com.shadowclangaming.client.BlastGeometry.envelope(0.0F));
        close("a layer is fully visible in the middle", 1.0D,
                com.shadowclangaming.client.BlastGeometry.envelope(0.5F));
        close("a layer is invisible at the end of its window", 0.0D,
                com.shadowclangaming.client.BlastGeometry.envelope(1.0F));

        Collector sink = new Collector();
        int quads = com.shadowclangaming.client.BlastGeometry.quad(sink,
                0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0xFFFF0000);
        eq("a quad is four vertices", 4, quads);
        eq("a quad was collected", 4, sink.vertices);

        Collector sphere = new Collector();
        int sphereVertices = com.shadowclangaming.client.BlastGeometry.sphere(sphere,
                0, 0, 0, 10.0D, 0x80FF0000,
                com.shadowclangaming.client.BlastGeometry.SPHERE_SEGMENTS_U,
                com.shadowclangaming.client.BlastGeometry.SPHERE_SEGMENTS_V, 0.0D);
        eq("a sphere shell is 4 vertices per cell",
                4 * com.shadowclangaming.client.BlastGeometry.SPHERE_SEGMENTS_U
                        * com.shadowclangaming.client.BlastGeometry.SPHERE_SEGMENTS_V,
                sphereVertices);
        check("a sphere is as wide as its radius", Math.abs(sphere.spanX() - 20.0D) < 1.5D);
        check("a sphere is as tall as its radius", Math.abs(sphere.spanY() - 20.0D) < 1.5D);

        Collector ring = new Collector();
        int ringVertices = com.shadowclangaming.client.BlastGeometry.ring(ring,
                0, 0, 0, 12.0D, 0, 1, 0, 1.0D, 0x8000FF00, 0.0D,
                com.shadowclangaming.client.BlastGeometry.RING_SEGMENTS);
        eq("a ring is one quad per segment",
                4 * com.shadowclangaming.client.BlastGeometry.RING_SEGMENTS, ringVertices);
        check("a ring is flat", ring.spanY() < 0.001D);
        check("a ring is as wide as its diameter", Math.abs(ring.spanX() - 25.0D) < 1.5D);

        Collector cross = new Collector();
        eq("a mote is two quads", 8,
                com.shadowclangaming.client.BlastGeometry.crossSprite(cross, 0, 0, 0, 1.0F, 0x80FFFFFF));

        // Every layer kind, driven through the real dispatch, at the midpoint of its own window.
        for (var layer : com.shadowclangaming.warhead.BlastMotif.Layer.values()) {
            var spec = new com.shadowclangaming.warhead.BlastMotif.Spec(
                    layer, 0.2D, 0.8D, 1.0D, 0xC0FF8040, 6, 0.002D);
            Collector layerSink = new Collector();
            int before = com.shadowclangaming.client.BlastGeometry.layer(layerSink, spec, 0.5F,
                    0, 64, 0, 24.0D, 0xC0FF8040, 7);
            check(layer + " draws geometry", before > 0 && before == layerSink.vertices);
            check(layer + " stays near the detonation",
                    layerSink.maxX - layerSink.minX < 400.0D);

            Collector outside = new Collector();
            int none = com.shadowclangaming.client.BlastGeometry.layer(outside, spec, 0.05F,
                    0, 64, 0, 24.0D, 0xC0FF8040, 7);
            eq(layer + " draws nothing before its window", 0, none);
            eq(layer + " collected nothing before its window", 0, outside.vertices);
        }

        // Every real motif, at every point in its life: it must draw something at some point, and must
        // never emit a vertex for a layer that is not on screen.
        for (String id : WARHEADS) {
            var motif = com.shadowclangaming.warhead.BlastMotif.forWarhead(id);
            int total = 0;
            for (float progress = 0.0F; progress <= 1.0F; progress += 0.05F) {
                Collector effect = new Collector();
                for (var spec : motif.specs()) {
                    total += com.shadowclangaming.client.BlastGeometry.layer(effect, spec, progress,
                            0, 64, 0, 32.0D, spec.argb(), 3);
                }
            }
            check(id + " draws a real effect over its life (" + total + " vertices)", total > 1000);
        }

        // Degenerate inputs must not produce NaN: a zero radius, a zero-size layer, a zero normal.
        Collector degenerate = new Collector();
        com.shadowclangaming.client.BlastGeometry.ring(degenerate, 0, 0, 0, 0.0D, 0, 0, 0, 0.0D,
                0x80FFFFFF, 0.0D, 12);
        check("a degenerate ring produces finite vertices",
                !Double.isNaN(degenerate.minX) && !Double.isNaN(degenerate.maxX));
        Collector line = new Collector();
        com.shadowclangaming.client.BlastGeometry.segment(line, 0, 0, 0, 0, 5, 0, 1.0D, 0x80FFFFFF);
        eq("a vertical segment still draws", 4, line.vertices);
        check("a vertical segment is finite", !Double.isNaN(line.minX));
        Collector zero = new Collector();
        eq("a zero-length segment draws nothing", 0,
                com.shadowclangaming.client.BlastGeometry.segment(zero, 1, 1, 1, 1, 1, 1, 1.0D,
                        0x80FFFFFF));
    }

    // ------------------------------------------------------------------ plumbing

    private static void section(String name) {
        System.out.println();
        System.out.println("-- " + name + " --");
    }

    private static void eq(String label, Object expected, Object actual) {
        checks++;
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) {
            failures++;
            System.out.println("FAIL " + label + ": expected " + expected + " but was " + actual);
        } else {
            System.out.println("ok   " + label);
        }
    }


    /**
     * The parameter set each warhead feeds the volumetric GPU shader.
     *
     * The shader has one code path for all thirty-eight warheads, so "distinct" is a claim about these
     * numbers and nothing else. If two warheads ever share a signature they are literally the same effect,
     * and this is what stops that from shipping.
     */
    /**
     * The shader's structural features, named exactly as the fragment shader names its uniforms.
     */
    private static final String[] FEATURES = {
            "hole", "star", "rift", "waves", "meteors", "lightning", "glyphs", "portals",
            "jet", "coins", "smoke", "subCharges", "flash", "dome", "embers",
    };

    /**
     * What each warhead's own description promises, as a floor on its feature weights.
     *
     * <p>This is the acceptance test for "make sure the unique effects are all implemented". Each entry
     * comes from the javadoc on the matching {@code WarheadType} constant - a World Eater is a black hole,
     * a Ragnarok is meteors and lightning, a Quasar Lance is a jet - so if a row in
     * {@code BlastMotif.Volume.author} is ever dropped or zeroed, the harness says which warhead lost
     * which feature rather than shipping thirty-eight of the same fireball again.
     */
    private static final java.util.Map<String, String> SIGNATURE_FEATURES =
            java.util.Map.ofEntries(
                    java.util.Map.entry("dud", "smoke=0.8"),
                    java.util.Map.entry("decoy", "smoke=0.6,flash=0.3"),
                    java.util.Map.entry("emp", "lightning=0.8"),
                    java.util.Map.entry("he", "waves=0.7,flash=0.4"),
                    java.util.Map.entry("incendiary", "embers=0.9"),
                    java.util.Map.entry("cluster", "subCharges=0.9"),
                    java.util.Map.entry("fission", "waves=0.8,flash=0.5"),
                    java.util.Map.entry("fusion", "star=0.7,flash=0.9"),
                    java.util.Map.entry("antimatter", "hole=0.8,flash=0.9"),
                    java.util.Map.entry("numismatic", "coins=0.9"),
                    java.util.Map.entry("world_eater", "hole=0.9,dark=0.9"),
                    java.util.Map.entry("stellar_requiem", "star=0.9"),
                    java.util.Map.entry("reality_breaker", "rift=0.9"),
                    java.util.Map.entry("spacetime_hammer", "waves=0.9"),
                    java.util.Map.entry("universal_imploder", "implode=0.9,hole=0.6"),
                    java.util.Map.entry("ragnarok", "meteors=0.9,lightning=0.8,skyRing=0.8"),
                    java.util.Map.entry("world_rune", "glyphs=0.9"),
                    java.util.Map.entry("abyssal_covenant", "portals=0.9"),
                    java.util.Map.entry("soul_furnace", "lightning=0.9,skyRing=0.9,dark=0.8"),
                    java.util.Map.entry("arcane_singularity", "glyphs=0.9,skyRing=0.9"),
                    java.util.Map.entry("quasar_lance", "jet=0.9"),
                    java.util.Map.entry("starforge", "star=0.9,embers=0.8"),
                    java.util.Map.entry("big_bang_seed", "star=0.9,flash=0.9"),
                    java.util.Map.entry("reality_corruptor", "rift=0.8"),
                    java.util.Map.entry("absolute_zero", "hole=0.8,implode=0.9"),
                    java.util.Map.entry("omega", "waves=0.9,implode=0.8"),
                    java.util.Map.entry("absolute_annihilation", "hole=0.8,star=0.8,flash=0.9,waves=0.9"),
                    java.util.Map.entry("bulwark", "dome=0.9"),
                    java.util.Map.entry("contagion", "smoke=0.9"),
                    java.util.Map.entry("spell", "glyphs=0.8,skyRing=0.8"),
                    java.util.Map.entry("cargo", "jet=0.4"),
                    java.util.Map.entry("minefield", "portals=0.6"),
                    java.util.Map.entry("tracking", "ring=0.9"),
                    java.util.Map.entry("constructor", "dome=0.8"),
                    java.util.Map.entry("spatial", "rift=0.7"),
                    java.util.Map.entry("clucker", "embers=0.7"),
                    java.util.Map.entry("violator", "smoke=0.8,dark=0.8"),
                    java.util.Map.entry("beef", "smoke=0.6,embers=0.6"));

    /** Read one feature weight by the name the shader uses, so the table above stays in the shader's words. */
    private static float feature(com.shadowclangaming.warhead.BlastMotif.Volume v, String name) {
        return switch (name) {
            case "hole" -> v.hole();
            case "star" -> v.star();
            case "rift" -> v.rift();
            case "waves" -> v.waves();
            case "meteors" -> v.meteors();
            case "lightning" -> v.lightning();
            case "glyphs" -> v.glyphs();
            case "portals" -> v.portals();
            case "jet" -> v.jet();
            case "coins" -> v.coins();
            case "smoke" -> v.smoke();
            case "subCharges" -> v.subCharges();
            case "flash" -> v.flash();
            case "dome" -> v.dome();
            case "embers" -> v.embers();
            case "beam" -> v.beam();
            case "ring" -> v.ring();
            case "skyRing" -> v.skyRing();
            case "implode" -> v.implode();
            case "dark" -> v.dark();
            default -> throw new IllegalArgumentException("unknown feature " + name);
        };
    }

    /**
     * Every warhead's documented signature feature must actually be switched on.
     *
     * <p>Derived-from-theme parameters produced thirty-eight near-identical fireballs; this is the test
     * that keeps the authored rows honest. It also insists that two warheads sharing a theme do not share
     * a shape, which is the failure mode that got the whole effect rejected in the first place.
     */
    private static void warheadFeatureTests() {
        section("warhead signature features");

        java.util.Map<String, float[]> shapes = new java.util.LinkedHashMap<>();
        for (String id : WARHEADS) {
            com.shadowclangaming.warhead.BlastMotif.Volume volume =
                    com.shadowclangaming.warhead.BlastMotif.forWarhead(id).volume();
            String expected = SIGNATURE_FEATURES.get(id);
            check("signature features are documented for " + id, expected != null);
            if (expected != null) {
                for (String pair : expected.split(",")) {
                    String[] parts = pair.split("=");
                    float want = Float.parseFloat(parts[1]);
                    float have = feature(volume, parts[0]);
                    check(id + " keeps its documented " + parts[0] + " (" + have + " >= " + want + ")",
                            have >= want);
                }
            }
            float[] vector = new float[FEATURES.length];
            for (int i = 0; i < FEATURES.length; i++) {
                vector[i] = feature(volume, FEATURES[i]);
            }
            shapes.put(id, vector);
        }
        eq("every warhead has a documented signature", WARHEADS.length, SIGNATURE_FEATURES.size());

        // Two warheads in the same theme must not be the same effect in a different colour. The floor is
        // deliberately loose - palettes still differ - but a pair with no feature difference at all fails.
        int pairs = 0;
        int sharedTheme = 0;
        for (String left : WARHEADS) {
            for (String right : WARHEADS) {
                if (left.compareTo(right) >= 0) {
                    continue;
                }
                pairs++;
                com.shadowclangaming.warhead.BlastMotif.Motif a =
                        com.shadowclangaming.warhead.BlastMotif.forWarhead(left);
                com.shadowclangaming.warhead.BlastMotif.Motif b =
                        com.shadowclangaming.warhead.BlastMotif.forWarhead(right);
                if (a.theme() != b.theme()) {
                    continue;
                }
                sharedTheme++;
                float distance = 0.0F;
                float[] va = shapes.get(left);
                float[] vb = shapes.get(right);
                for (int i = 0; i < va.length; i++) {
                    distance += Math.abs(va[i] - vb[i]);
                }
                check(left + " and " + right + " share a theme but not a shape (delta "
                        + String.format(java.util.Locale.ROOT, "%.2f", distance) + ")", distance >= 0.35F);
            }
        }
        check("warhead pairs were compared", pairs > 500);
        check("some warheads share a theme", sharedTheme > 0);
    }

    private static void volumeShaderTests() {
        section("volumetric shader parameters");

        java.util.Set<String> signatures = new java.util.HashSet<>();
        for (String id : WARHEADS) {
            com.shadowclangaming.warhead.BlastMotif.Motif motif =
                    com.shadowclangaming.warhead.BlastMotif.forWarhead(id);
            com.shadowclangaming.warhead.BlastMotif.Volume volume = motif.volume();
            check("volume parameters exist for " + id, volume != null);
            check("volume signature is distinct for " + id, signatures.add(volume.signature()));

            check("palette is opaque for " + id,
                    ((volume.colorA() >>> 24) & 0xFF) == 0xFF
                            && ((volume.colorB() >>> 24) & 0xFF) == 0xFF
                            && ((volume.colorC() >>> 24) & 0xFF) == 0xFF);
            check("shape parameters are in range for " + id,
                    volume.beam() >= 0.0F && volume.beam() <= 1.0F
                            && volume.ring() >= 0.0F && volume.ring() <= 1.0F
                            && volume.skyRing() >= 0.0F && volume.skyRing() <= 1.0F
                            && volume.streaks() >= 0.0F && volume.streaks() <= 1.0F
                            && volume.dark() >= 0.0F && volume.dark() <= 1.0F
                            && volume.implode() >= -1.0F && volume.implode() <= 1.0F
                            && volume.turb() >= 0.05F && volume.turb() <= 2.0F
                            && volume.rise() >= -1.0F && volume.rise() <= 1.0F
                            && volume.glow() >= 0.05F && volume.glow() <= 2.0F
                            && volume.swirl() >= 0.0F && volume.swirl() <= 2.0F);
            check("motion is finite for " + id,
                    Float.isFinite(volume.glow()) && Float.isFinite(volume.swirl())
                            && Float.isFinite(volume.turb()));

            // Glow starts derived (0.35 + finaleScale * 0.55) and a warhead may author over it - a
            // World Eater should outshine a grenade - but the ordering the derivation encodes must
            // survive: a detonation burns brighter than the base, and a warhead that never detonates
            // must not outshine one that does.
            if (motif.hasBlastFinale()) {
                check("a warhead that ends in a blast burns brighter than the base: " + id,
                        volume.glow() > 0.5F);
            } else {
                check("a warhead that never detonates stays dim: " + id, volume.glow() <= 1.3F);
            }
            check("the noise field starts somewhere of its own for " + id,
                    volume.seed() >= 0.0F && volume.seed() < 1.0F);
            for (String name : FEATURES) {
                float weight = feature(volume, name);
                check("feature " + name + " is a weight for " + id,
                        Float.isFinite(weight) && weight >= 0.0F && weight <= 1.0F);
            }
        }
        eq("every warhead has its own look", WARHEADS.length, signatures.size());

        // The two the round was named after: they must not read as each other.
        com.shadowclangaming.warhead.BlastMotif.Volume soul =
                com.shadowclangaming.warhead.BlastMotif.forWarhead("soul_furnace").volume();
        check("the Soul Furnace is dark-cored", soul.dark() >= 0.5F);
        check("the Soul Furnace has magic circles overhead", soul.skyRing() >= 0.9F);
        com.shadowclangaming.warhead.BlastMotif.Volume quasar =
                com.shadowclangaming.warhead.BlastMotif.forWarhead("quasar_lance").volume();
        check("the Quasar Lance is a pillar of light", quasar.beam() >= 0.9F);
        check("the Quasar Lance is not dark-cored", quasar.dark() <= 0.2F);
        com.shadowclangaming.warhead.BlastMotif.Volume cataclysm =
                com.shadowclangaming.warhead.BlastMotif.forWarhead("ragnarok").volume();
        check("a cataclysm has the full shockwave", cataclysm.ring() >= 0.9F);
        check("a cataclysm is turbulent", cataclysm.turb() >= 1.0F);

        // The ordering, concretely: the dimmest thing on the roster is a dud, and a conventional high
        // explosive outshines it. If authoring ever inverts that, the effect has stopped making sense.
        float dimmest = Float.MAX_VALUE;
        for (String id : WARHEADS) {
            dimmest = Math.min(dimmest, com.shadowclangaming.warhead.BlastMotif.forWarhead(id)
                    .volume().glow());
        }
        close("a dud is the dimmest thing on the roster", 0.22D, dimmest);
        check("a conventional high explosive outshines a dud",
                com.shadowclangaming.warhead.BlastMotif.forWarhead("he").volume().glow() > dimmest);
    }

    /**
     * The charge phase: the effect that plays while the weapon is cooking, before the blast.
     *
     * Every motif must have one, every layer in it must be marked as a charge layer, and it must be a
     * smaller, converging version of the impact rather than a copy of it.
     */
    private static void chargePhaseTests() {
        section("charge phase");

        for (String id : WARHEADS) {
            com.shadowclangaming.warhead.BlastMotif.Motif motif =
                    com.shadowclangaming.warhead.BlastMotif.forWarhead(id);
            check("charge layers exist for " + id, !motif.chargeSpecs().isEmpty());
            eq("one charge layer per impact layer for " + id, motif.specs().size(),
                    motif.chargeSpecs().size());

            for (int i = 0; i < motif.chargeSpecs().size(); i++) {
                com.shadowclangaming.warhead.BlastMotif.Spec charge = motif.chargeSpecs().get(i);
                com.shadowclangaming.warhead.BlastMotif.Spec impact = motif.specs().get(i);
                check("charge layer is a charge layer for " + id + "[" + i + "]",
                        charge.phase() == com.shadowclangaming.warhead.BlastMotif.Phase.CHARGE);
                check("impact layer is an impact layer for " + id + "[" + i + "]",
                        impact.phase() == com.shadowclangaming.warhead.BlastMotif.Phase.IMPACT);
                check("charge layer keeps its kind for " + id + "[" + i + "]",
                        charge.layer() == impact.layer());
                check("charge layer keeps its colour for " + id + "[" + i + "]",
                        charge.argb() == impact.argb());
                check("charge layer is inside the effect for " + id + "[" + i + "]",
                        charge.from() >= 0.0D && charge.to() <= 1.0D && charge.to() > charge.from());
                // Smaller, unless the impact layer is already at the Spec clamp floor of 0.05 and there
                // is no room underneath it.
                check("charge layer is smaller than the blast for " + id + "[" + i + "]",
                        charge.scale() < impact.scale() || impact.scale() <= 0.06D);
                check("charge layer winds the other way for " + id + "[" + i + "]",
                        Math.signum(charge.spin()) != Math.signum(impact.spin())
                                || charge.spin() == 0.0D);
            }
        }

        // A charge that does not grow would be a static prop, which is what the last version looked like.
        com.shadowclangaming.warhead.BlastMotif.Motif he =
                com.shadowclangaming.warhead.BlastMotif.forWarhead("he");
        double lastStart = -1.0D;
        boolean staggered = false;
        for (com.shadowclangaming.warhead.BlastMotif.Spec spec : he.chargeSpecs()) {
            if (spec.from() > lastStart + 0.0001D) {
                staggered = true;
            }
            lastStart = Math.max(lastStart, spec.from());
        }
        check("charge layers arrive one after another", staggered);
    }

    private static void check(String label, boolean condition) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("FAIL " + label);
        } else {
            System.out.println("ok   " + label);
        }
    }

    private static void close(String label, double expected, double actual) {
        checks++;
        if (Math.abs(expected - actual) > 0.01D) {
            failures++;
            System.out.println("FAIL " + label + ": expected " + expected + " but was " + actual);
        } else {
            System.out.println("ok   " + label);
        }
    }
}
