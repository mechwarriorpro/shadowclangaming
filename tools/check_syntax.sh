#!/usr/bin/env bash
# Syntax check of every Java file in src/main/java, with no Minecraft on the classpath.
#
#   bash tools/check_syntax.sh
#
# javac cannot *resolve* anything without the game on the classpath, so a plain run produces thousands of
# "package net.minecraft does not exist" errors that say nothing about our code. This script runs javac once
# over all sources and then reports only the errors that are syntax: a stray semicolon, an unbalanced
# brace, a malformed generic, a bad annotation, a duplicate class. Those are exactly the mistakes that
# happen when files are edited by a script, and they are the ones that survive to break a real build.
#
# It is not a substitute for `./gradlew build` (only that checks the types), but it is fast and it is honest.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# JDK 21 lookup lives in one place. Parsing with an older javac reports pattern-matching instanceof
# ("if (x instanceof Foo f)") as a syntax error, which is a false alarm, not a broken file.
source "$ROOT/tools/jdk.sh"
JAVAC="${JDK_BIN}javac"
OUT="$(mktemp)"
WORK="$(mktemp -d)"
trap 'rm -f "$OUT"; rm -rf "$WORK"' EXIT

"$JAVAC" -proc:none -nowarn -Xmaxerrs 100000 -d "$WORK" $(find "$ROOT/src/main/java" -name '*.java') > "$OUT" 2>&1
TOTAL=$(grep -c "error:" "$OUT" || true)

# Messages javac emits while parsing, i.e. the ones that are our fault alone.
PATTERN="expected|illegal start|illegal character|illegal escape|not a statement|reached end of file while parsing|invalid method declaration|unclosed string literal|unclosed character literal|unclosed comment|class, interface, enum, or record expected|duplicate class|not allowed here|malformed|unexpected type|orphaned|> expected|invalid input"
SYNTAX=$(grep -E "error: (.*($PATTERN))" "$OUT" || true)

if [ -n "$SYNTAX" ]; then
  echo "$SYNTAX"
  echo
  echo "SYNTAX FAIL"
  exit 1
fi

echo "SYNTAX PASS ($(find "$ROOT/src/main/java" -name '*.java' | wc -l) files parsed; $TOTAL unresolved-symbol messages ignored - they need the game on the classpath)"
