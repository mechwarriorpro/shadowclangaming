#!/usr/bin/env bash
# Shared JDK lookup for the harness scripts.
#
#   source tools/jdk.sh
#
# Sets JDK_HOME (no trailing slash) and JDK_BIN (with trailing slash) to a JDK 21 or newer.
# Order of preference:
#   1. $SCG_JDK                     - explicit override, e.g. SCG_JDK=/opt/jdk-21.0.5
#   2. $JAVA_HOME                   - if it is 21+
#   3. a few usual install spots    - /tmp/jdk-21*, /usr/lib/jvm/*21*, ~/.jdks/*, ~/.gradle/jdks/*
#   4. javac on PATH                - if it is 21+
# Exits with a readable message if nothing suitable is found: the mod is Java 21, so a JDK 21 is required.

scg_jdk_major() {
    # $1 = a javac path. Prints the major version, or nothing if it cannot run.
    "$1" -version 2>&1 | sed -n 's/^javac \([0-9][0-9]*\).*/\1/p' | head -n 1
}

scg_pick_jdk() {
    local candidate major
    local -a candidates=()
    [[ -n "${SCG_JDK:-}" ]] && candidates+=("${SCG_JDK%/}")
    [[ -n "${JAVA_HOME:-}" ]] && candidates+=("${JAVA_HOME%/}")
    # Globs are deliberately unquoted: they may match nothing, in which case bash leaves them literal
    # and the executable check below rejects them.
    candidates+=(/tmp/jdk-21* /tmp/jdk-2[2-9]*)
    candidates+=(/usr/lib/jvm/*21* /usr/lib/jvm/*2[2-9]*)
    candidates+=("$HOME"/.jdks/* "$HOME"/.gradle/jdks/* "$HOME"/.cache/scg/jdk)
    candidates+=(/usr/lib/jvm/jdk-11) # last resort, will be rejected by the version check

    for candidate in "${candidates[@]}"; do
        [[ -x "$candidate/bin/javac" ]] || continue
        major="$(scg_jdk_major "$candidate/bin/javac")"
        if [[ -n "$major" ]] && (( major >= 21 )); then
            JDK_HOME="$candidate"
            JDK_BIN="$candidate/bin/"
            return 0
        fi
    done

    if command -v javac >/dev/null 2>&1; then
        major="$(scg_jdk_major "$(command -v javac)")"
        if [[ -n "$major" ]] && (( major >= 21 )); then
            JDK_HOME="$(dirname "$(dirname "$(command -v javac)")")"
            JDK_BIN="$(command -v javac | sed 's#javac$##')"
            return 0
        fi
    fi

    echo "No JDK 21+ found. The mod targets Java 21 (NeoForge 1.21.1)." >&2
    echo "Install one, or point at an existing one:  SCG_JDK=/path/to/jdk-21 bash $0" >&2
    echo "Checked: \$SCG_JDK, \$JAVA_HOME, /tmp/jdk-21*, /usr/lib/jvm/*21*, ~/.jdks/*, ~/.gradle/jdks/*, ~/.cache/scg/jdk, PATH." >&2
    return 1
}

scg_pick_jdk
