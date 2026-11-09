#!/usr/bin/env bash
# Compile and run the Lua interpreter harness.
#
#   bash tools/run_lua_harness.sh
#
# The Lua package deliberately has no Minecraft imports, so this needs nothing but a JDK 21: it compiles
# src/main/java/com/shadowclangaming/lua plus tools/harness/LuaHarness.java into a temp folder, runs ~40 real
# Lua programs through the interpreter and checks what they printed, then deletes the folder.
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
    "$ROOT/tools/harness/LuaHarness.java"

"$JAVA" -cp "$WORK/out" LuaHarness
