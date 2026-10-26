#!/usr/bin/env bash
# Compile and run the computer API harness (screen, term, fs, component, event, thread).
#
#   bash tools/run_api_harness.sh
#
# Like the Lua harness this needs only a JDK 21: the API layer is Minecraft-free by design.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# JDK 21+ lookup lives in one place; see tools/jdk.sh for the search order and the override.
source "$ROOT/tools/jdk.sh"
JAVAC="${JDK_BIN}javac"
JAVA="${JDK_BIN}java"

mkdir -p "$WORK/out"

echo "compiling with $JAVAC"
"$JAVAC" -Xlint:none -d "$WORK/out" \
    $(find "$ROOT/src/main/java/com/shadowclangaming/lua" -name '*.java') \
    $(find "$ROOT/src/main/java/com/shadowclangaming/computer/api" -name '*.java') \
    "$ROOT/tools/harness/ApiHarness.java"

"$JAVA" -cp "$WORK/out" ApiHarness
