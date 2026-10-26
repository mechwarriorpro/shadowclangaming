#!/usr/bin/env bash
# Builds the mod and tells you exactly where the jar landed.
# Run: ./verify.sh
set -euo pipefail

echo "== Java version =="
java -version 2>&1 | head -3

if ! java -version 2>&1 | grep -qE '"2[1-9]'; then
  echo
  echo "!! You need Java 21 (or newer) to compile a 1.21.1 NeoForge mod."
  echo "   Install Temurin 21, then run this again."
  exit 1
fi

echo
echo "== Fast checks (no game, no build) =="
python3 tools/sweep.py
bash tools/run_logic_harness.sh
bash tools/run_lua_harness.sh
bash tools/run_api_harness.sh

echo
echo "== Building (first run downloads Minecraft + NeoForge, can take 5-15 min) =="
./gradlew build --no-daemon

echo
echo "== Done. Your jar: =="
ls -1 build/libs/*.jar
echo
echo "Copy the jar WITHOUT '-sources' in the name into your mods/ folder."
