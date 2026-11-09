#!/usr/bin/env bash
# Rebuilds /home/user/.cache/scg from nothing: JDK 21, the Minecraft 1.21.1 classpath, and cp.txt.
#
#   bash tools/bootstrap_toolchain.sh          # reuse whatever is already downloaded
#   bash tools/bootstrap_toolchain.sh --fresh  # delete the cache first
#
# The cache is not part of the workspace snapshot, so it can vanish between sessions. Everything this
# script does was worked out by hand once (see docs/REBUILD.md); it is here so that never has to
# happen again. It takes about 90 seconds on a cold cache.
set -euo pipefail

CACHE=/home/user/.cache/scg
JDK="$CACHE/jdk/bin"
MVN=https://maven.neoforged.net/releases/net/neoforged
NEOFORM="$MVN/neoform/1.21.1-20240808.144430/neoform-1.21.1-20240808.144430.zip"
NFRT="$MVN/neoform-runtime/2.0.31/neoform-runtime-2.0.31-all.jar"
USERDEV="$MVN/neoforge/21.1.264/neoforge-21.1.264-userdev.jar"
INSTALLER="$MVN/neoforge/21.1.264/neoforge-21.1.264-installer.jar"
UNIVERSAL="$MVN/neoforge/21.1.264/neoforge-21.1.264-universal.jar"
BINPATCH="https://maven.neoforged.net/releases/net/minecraftforge/binarypatcher/1.1.1/binarypatcher-1.1.1-fatjar.jar"
ANNOTATIONS="https://maven.neoforged.net/releases/org/jetbrains/annotations/24.1.0/annotations-24.1.0.jar"

if [ "${1:-}" = "--fresh" ]; then
  rm -rf "$CACHE"
fi
mkdir -p "$CACHE/dl" "$CACHE/libs"

# 1. JDK 21 -------------------------------------------------------------------------------------
if [ ! -x "$JDK/javac" ]; then
  echo "[1/5] JDK 21"
  curl -sL -o "$CACHE/dl/jdk.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar xzf "$CACHE/dl/jdk.tar.gz" -C "$CACHE"
  mv "$CACHE"/jdk-21* "$CACHE/jdk"
else
  echo "[1/5] JDK 21 present"
fi

# 2. downloads ----------------------------------------------------------------------------------
echo "[2/5] downloads"
fetch() { [ -s "$CACHE/dl/$2" ] || curl -sfL -o "$CACHE/dl/$2" "$1"; }
fetch "$NEOFORM" neoform.zip
fetch "$NFRT" nfrt.jar
fetch "$USERDEV" userdev.jar
fetch "$INSTALLER" installer.jar
fetch "$UNIVERSAL" universal.jar
fetch "$BINPATCH" binarypatcher.jar
fetch "$ANNOTATIONS" annotations.jar

# 3. trimmed neoform config + rename ------------------------------------------------------------------
# Only the steps up to "rename" are kept: we want official->named names, not a decompile.
# The config's `steps` must be a map of dist -> array; a flat dict fails with Expected BEGIN_ARRAY.
if [ ! -s "$CACHE/rename.jar" ]; then
  echo "[3/5] rename pass"
  python3 - "$CACHE" <<'PY'
import json, sys, zipfile
cache = sys.argv[1]
src = zipfile.ZipFile(cache + "/dl/neoform.zip")
config = json.loads(src.read("config.json"))
for dist in ("joined", "client", "server"):
    steps = config["steps"][dist]
    index = [i for i, s in enumerate(steps) if s.get("type") == "rename"][0]
    trimmed = steps[:index + 1]
    trimmed.append({"type": "strip", "name": "decompile", "input": "{renameOutput}"})
    trimmed.append({"type": "strip", "name": "inject", "input": "{decompileOutput}"})
    trimmed.append({"type": "strip", "name": "patch", "input": "{injectOutput}"})
    config["steps"][dist] = trimmed
with zipfile.ZipFile(cache + "/neoform-nfrt2.zip", "w", zipfile.ZIP_DEFLATED) as out:
    for entry in src.infolist():
        body = json.dumps(config).encode() if entry.filename == "config.json" else src.read(entry.filename)
        out.writestr(entry.filename, body)
PY
  # Absolute paths are required: relative ones give "Exec failed, error: 2".
  # NFRT writes its result into the CURRENT directory, so run it from the cache.
  ( cd "$CACHE" && "$JDK/java" -jar "$CACHE/dl/nfrt.jar" run --neoform "$CACHE/neoform-nfrt2.zip" \
      --dist joined --write-result=vanillaDeobfuscated:rename.jar \
      --home-dir "$CACHE/nfhome" --work-dir "$CACHE/nfwork" --java-home "$CACHE/jdk" >/dev/null )
else
  echo "[3/5] rename.jar present"
fi

# 4. patched classes + the game's libraries -----------------------------------------------------------
if [ ! -s "$CACHE/patched.jar" ]; then
  echo "[4/5] binary patch"
  ( cd "$CACHE" && unzip -oq dl/userdev.jar joined.lzma )
  "$JDK/java" -jar "$CACHE/dl/binarypatcher.jar" --clean "$CACHE/rename.jar" \
    --output "$CACHE/patched.jar" --apply "$CACHE/joined.lzma" >/dev/null
fi
unzip -oq "$CACHE/dl/installer.jar" version.json -d "$CACHE/dl/inst"
python3 - "$CACHE" <<'PY'
import json, os, subprocess, sys, urllib.request
cache = sys.argv[1]
meta = json.load(open(cache + "/dl/inst/version.json"))
paths = [lib["downloads"]["artifact"]["path"] for lib in meta["libraries"]
         if "downloads" in lib and "artifact" in lib["downloads"]]
for path in paths:
    target = os.path.join(cache, "libs", os.path.basename(path))
    if not os.path.exists(target):
        try:
            urllib.request.urlretrieve("https://maven.neoforged.net/releases/" + path, target)
        except Exception as error:
            print("  skip", path, error)
logging = os.path.join(cache, "libs", "logging-1.2.7.jar")
if not os.path.exists(logging):
    urllib.request.urlretrieve("https://libraries.minecraft.net/com/mojang/logging/1.2.7/logging-1.2.7.jar", logging)
PY

# 5. classpath ----------------------------------------------------------------------------------------
# rename.jar has to stay on the classpath: the binary patcher output alone is not a full client.
echo "[5/5] classpath"
python3 - "$CACHE" <<'PY'
import os, sys
cache = sys.argv[1]
jars = [os.path.join(cache, "patched.jar"), os.path.join(cache, "rename.jar"),
        os.path.join(cache, "dl", "universal.jar"), os.path.join(cache, "dl", "annotations.jar")]
for folder in ("libs", "nfhome/artifacts"):
    for root, _, files in os.walk(os.path.join(cache, folder)):
        for name in sorted(files):
            if name.endswith(".jar"):
                jars.append(os.path.join(root, name))
open(os.path.join(cache, "cp.txt"), "w").write(":".join(jars))
print("  ", len(jars), "jars")
PY
echo "toolchain ready: $(wc -c < "$CACHE/cp.txt") bytes of classpath"
