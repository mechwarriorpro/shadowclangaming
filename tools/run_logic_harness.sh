#!/usr/bin/env bash
# Compile and run the pure-logic harness for the hardware / radar / cannon round.
#
#   bash tools/run_logic_harness.sh
#
# It copies only the classes that do not touch a Level (plus a ChatFormatting stub) into a temp folder,
# compiles them with a real JDK 21 and runs the assertions. Nothing in the mod is modified and the temp
# folder is deleted at the end. If JAVA_HOME points at a JDK 21, that is used; otherwise `java` on the
# PATH must be 21+ (the mod targets 21).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# JDK 21+ lookup lives in one place; see tools/jdk.sh for the search order and the override.
source "$ROOT/tools/jdk.sh"
JAVAC="${JDK_BIN}javac"
JAVA="${JDK_BIN}java"

mkdir -p "$WORK/src/com/shadowclangaming/computer" \
         "$WORK/src/com/shadowclangaming/blueprint" \
         "$WORK/src/com/shadowclangaming/radar" \
         "$WORK/src/com/shadowclangaming/compat/economy" \
         "$WORK/src/com/shadowclangaming/warhead" \
         "$WORK/src/com/shadowclangaming/entity" \
         "$WORK/src/com/shadowclangaming/cannon" \
         "$WORK/src/com/shadowclangaming/launcher" \
         "$WORK/src/com/shadowclangaming/warhead" \
         "$WORK/src/com/shadowclangaming/warhead/apex" \
         "$WORK/src/com/shadowclangaming/client" \
         "$WORK/src/com/shadowclangaming/client/gui" \
         "$WORK/src/net/minecraft" \
         "$WORK/out"

copy() { cp "$ROOT/src/main/java/$1" "$WORK/src/$1"; }

copy com/shadowclangaming/blueprint/BlueprintMath.java
copy com/shadowclangaming/blueprint/EmitterGraph.java
copy com/shadowclangaming/computer/CpuTier.java
copy com/shadowclangaming/computer/GpuTier.java
copy com/shadowclangaming/computer/ComputeBudget.java
copy com/shadowclangaming/radar/ContactKind.java
copy com/shadowclangaming/radar/RadarContact.java
copy com/shadowclangaming/radar/RadarMath.java
copy com/shadowclangaming/radar/TrackScanner.java
copy com/shadowclangaming/compat/economy/MoneyChunks.java
copy com/shadowclangaming/warhead/ConstructorBlast.java
copy com/shadowclangaming/entity/RamTiming.java
copy com/shadowclangaming/compat/economy/RecipePricing.java
copy com/shadowclangaming/cannon/CannonMath.java
copy com/shadowclangaming/launcher/ModuleTier.java
copy com/shadowclangaming/launcher/SiloStructure.java
copy com/shadowclangaming/launcher/ModuleKind.java
copy com/shadowclangaming/entity/ChickenTactics.java
copy com/shadowclangaming/warhead/KamikazePlan.java
copy com/shadowclangaming/warhead/ExplosivePackaging.java
copy com/shadowclangaming/warhead/BarragePlan.java
copy com/shadowclangaming/computer/BarragePlanner.java
copy com/shadowclangaming/computer/Channels.java
copy com/shadowclangaming/warhead/SplitRule.java
copy com/shadowclangaming/warhead/BlastStyle.java
copy com/shadowclangaming/warhead/BlastMotif.java
copy com/shadowclangaming/warhead/apex/SoulFurnacePlan.java
copy com/shadowclangaming/client/BlastGeometry.java
copy com/shadowclangaming/client/gui/ConsoleLayout.java
copy com/shadowclangaming/warhead/apex/Apex.java
copy com/shadowclangaming/warhead/apex/ApexTimeline.java
cp "$ROOT/tools/harness/stubs/net/minecraft/ChatFormatting.java" "$WORK/src/net/minecraft/"
cp "$ROOT/tools/harness/LogicHarness.java" "$WORK/src/"

echo "compiling with $JAVAC"
"$JAVAC" -d "$WORK/out" $(find "$WORK/src" -name '*.java')
"$JAVA" -cp "$WORK/out" LogicHarness
