#!/usr/bin/env bash
set -e
# Build both NeoForge jars for side-by-side visual comparison.
# Requires JDK 21 (./gradlew will auto-provision via foojay if needed).
# Run from repo root: ./build-both.sh

echo "=== Building ShadowClanGaming (NeoForge) ==="
./gradlew jar --rerun-tasks
echo ""
echo "=== Building Destroying Minecraft (NeoForge port) ==="
./gradlew :destroying-minecraft:jar --rerun-tasks
echo ""
echo "=== Both jars ==="
ls -lh build/libs/*.jar 2>/dev/null || echo "  (shadowclangaming jar not found — check build/libs)"
ls -lh destroying-minecraft/build/libs/*.jar 2>/dev/null || echo "  (destroying-minecraft jar not found — check destroying-minecraft/build/libs)"
echo ""
echo "Drop both into your NeoForge 1.21.1 mods folder (NeoForge 21.1.264):"
echo "  - ShadowClanGaming-1.0.0.jar"
echo "  - destroying-minecraft-1.0.3.jar"
echo "ShadowClanGaming's creative tab has destroying_nuke/laser/black_hole/planet/supernova (photoreal reimplementation);"
echo "Destroying Minecraft's tab has nuke_trigger/laser_trigger/... (original Veil shaders) — same world, 1:1 compare."
