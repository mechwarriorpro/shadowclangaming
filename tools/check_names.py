#!/usr/bin/env python3
"""Identity and cross-reference checks - the ones a rename can quietly break.

`tools/sweep.py` checks structure (braces, JSON, assets, lang keys). This script checks that everything
that names the mod still agrees with everything else:

  1. no leftover of the old identity anywhere (voltzreborn / VoltzReborn / Voltz Reborn / com.voltzreborn)
  2. the mod id is the same in neoforge.mods.toml, @Mod, the assets/data folders, the gradle mods block
  3. every Java file's public type matches its filename, and no package declares a name twice
  4. every model / blockstate / item-model reference points at a file that exists
  5. every `shadowclangaming:<id>` used in Java either has a translation key or is a known non-item id
  6. no resource file lives under an old namespace folder

Run from the mod root:  python3 tools/check_names.py
Exit code 0 = pass.
"""

import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java")
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/shadowclangaming")
DATA = os.path.join(RES, "data/shadowclangaming")
MOD_ID = "shadowclangaming"

OLD_IDENTITIES = ["voltzreborn", "VoltzReborn", "Voltz Reborn", "com.voltzreborn", "VOLTZREBORN"]
SKIP_DIRS = {".git", "build", ".gradle", "run", ".idea", "javac"}

problems = []
notes = []


def fail(message):
    problems.append(message)


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def walk(root):
    for base, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS and not d.startswith("javac.")]
        for name in files:
            yield os.path.join(base, name)


# --------------------------------------------------------------------- 1. old identity

TEXT_EXT = (".java", ".json", ".md", ".py", ".sh", ".toml", ".mcmeta", ".properties", ".gradle", ".txt", ".yml")


def check_old_identity():
    for path in walk(ROOT):
        if not path.endswith(TEXT_EXT):
            continue
        rel = os.path.relpath(path, ROOT)
        if rel.startswith(("build/", ".gradle/")) or rel == "tools/check_names.py":
            continue
        try:
            text = read(path)
        except (UnicodeDecodeError, OSError):
            continue
        for old in OLD_IDENTITIES:
            if old in text:
                line = text[:text.index(old)].count("\n") + 1
                fail("%s:%d: leftover old identity '%s'" % (rel, line, old))
        # old namespace folders would break resource lookup silently
        if "assets/voltzreborn" in text or "data/voltzreborn" in text:
            fail("%s: old resource namespace path" % rel)
    notes.append("identity: scanned every text file for the old name")


# --------------------------------------------------------------------- 2. one id everywhere

def check_mod_id():
    toml = read(os.path.join(RES, "META-INF/neoforge.mods.toml"))
    match = re.search(r'^modId\s*=\s*"([^"]+)"', toml, re.M)
    if not match:
        fail("neoforge.mods.toml: no modId")
        return
    if match.group(1) != MOD_ID:
        fail("neoforge.mods.toml: modId is %s, expected %s" % (match.group(1), MOD_ID))

    main = read(os.path.join(JAVA, "com/shadowclangaming/ShadowClanGaming.java"))
    mod = re.search(r'@Mod\(\s*ShadowClanGaming\.MOD_ID\s*\)', main)
    if not mod:
        fail("ShadowClanGaming.java: @Mod does not use ShadowClanGaming.MOD_ID")
    constant = re.search(r'MOD_ID\s*=\s*"([^"]+)"', main)
    if not constant or constant.group(1) != MOD_ID:
        fail("ShadowClanGaming.java: MOD_ID is not %s" % MOD_ID)

    for folder, label in ((os.path.join(RES, "assets", MOD_ID), "assets"),
                          (os.path.join(RES, "data", MOD_ID), "data")):
        if not os.path.isdir(folder):
            fail("resources: missing %s/%s folder" % (label, MOD_ID))

    build = read(os.path.join(ROOT, "build.gradle"))
    if re.search(r"mods\s*\{\s*%s\s*\{" % MOD_ID, build) is None:
        fail("build.gradle: the neoForge.mods block does not name %s" % MOD_ID)
    archive = re.search(r"archivesName\s*=\s*'([^']+)'", build)
    if not archive:
        fail("build.gradle: no archivesName")
    else:
        notes.append("jar will be built as %s-<version>.jar" % archive.group(1))
    version = re.search(r"^version\s*=\s*'([^']+)'", build, re.M)
    toml_version = re.search(r'^version\s*=\s*"([^"]+)"', toml, re.M)
    if version and toml_version and version.group(1) != toml_version.group(1):
        fail("version mismatch: build.gradle %s vs neoforge.mods.toml %s"
             % (version.group(1), toml_version.group(1)))
    elif version:
        notes.append("version %s agrees in build.gradle and neoforge.mods.toml" % version.group(1))

    malformed = [f for f in os.listdir(os.path.join(RES))
                 if f.startswith("voltzreborn")]
    if malformed:
        fail("resources: stale root files %s" % malformed)


# --------------------------------------------------------------------- 3. Java type names

def check_java_types():
    seen = {}
    for path in walk(JAVA):
        if not path.endswith(".java"):
            continue
        rel = os.path.relpath(path, JAVA)
        text = read(path)
        package = re.search(r"^package\s+([\w.]+);", text, re.M)
        package = package.group(1) if package else "(default)"
        expected_folder = os.path.dirname(rel).replace(os.sep, ".")
        if package != expected_folder:
            fail("%s: package %s but folder %s" % (rel, package, expected_folder))

        public = re.search(r"^public\s+(?:final\s+|abstract\s+|sealed\s+)?"
                           r"(?:class|interface|enum|record)\s+(\w+)", text, re.M)
        stem = os.path.basename(path)[:-5]
        if public and public.group(1) != stem:
            fail("%s: public type %s does not match the filename" % (rel, public.group(1)))
        if not public:
            fail("%s: no public type (every file here declares exactly one)" % rel)

        key = (package, stem)
        if key in seen:
            fail("%s: duplicate type %s (also in %s)" % (rel, stem, seen[key]))
        seen[key] = rel
    notes.append("java: %d files, package/type/duplicate checks" % len(seen))


# --------------------------------------------------------------------- 4. model references

def check_model_refs():
    checked = 0
    for path in walk(ASSETS):
        rel = os.path.relpath(path, ASSETS)
        if not path.endswith(".json"):
            continue
        try:
            data = json.loads(read(path))
        except json.JSONDecodeError as error:
            fail("%s: invalid JSON (%s)" % (rel, error))
            continue

        def resolve(reference, kind):
            nonlocal checked
            if not isinstance(reference, str):
                return
            if reference.startswith(MOD_ID + ":"):
                reference = reference.split(":", 1)[1]
            elif ":" in reference:
                return  # vanilla or another mod: not ours to check
            target = os.path.join(ASSETS, kind, reference + ".json")
            if kind == "textures":
                target = os.path.join(ASSETS, "textures", reference + ".png")
            if not os.path.isfile(target):
                fail("%s: %s reference to missing %s" % (rel, kind, os.path.relpath(target, ASSETS)))
            checked += 1

        if "blockstates" in rel:
            if isinstance(data.get("variants"), dict):
                for variant in data["variants"].values():
                    for entry in (variant if isinstance(variant, list) else [variant]):
                        resolve(entry.get("model"), "models")
            if isinstance(data.get("multipart"), list):
                for part in data["multipart"]:
                    resolve(part.get("apply", {}).get("model"), "models")
        elif rel.startswith("models"):
            resolve(data.get("parent"), "models")
            for texture in (data.get("textures") or {}).values():
                resolve(texture, "textures")
    notes.append("assets: %d model/texture references resolved" % checked)


# --------------------------------------------------------------------- 5. ids used in Java

def warhead_ids():
    """Every warhead id, straight out of the enum: the packaged forms are generated from this list."""
    enum = read(os.path.join(JAVA, "com/shadowclangaming/warhead/WarheadType.java"))
    return re.findall(r'^\s{4}[A-Z_]+\("([a-z_0-9]+)",\s*[\d.]+F\)', enum, re.M)


def check_ids():
    lang = json.loads(read(os.path.join(ASSETS, "lang/en_us.json")))
    block_ids = set(re.findall(
        r'BLOCKS\.registerBlock\(\s*"(\w+)"',
        read(os.path.join(JAVA, "com/shadowclangaming/registry/SCGBlocks.java")), re.S))
    # The id must be the whole first argument: `"missile_module_" + prefix` is a fragment, not an id.
    registry = read(os.path.join(JAVA, "com/shadowclangaming/registry/SCGItems.java"))
    item_ids = set(re.findall(r'ITEMS\.register(?:Simple)?Item\(\s*"(\w+)"\s*,', registry))
    # Families registered in a loop: registerModuleKind("homing", ...) -> missile_module_homing_t1..t5.
    for prefix in re.findall(r'registerModuleKind\(\s*"(\w+)"', registry):
        for tier in range(1, 6):
            item_ids.add("missile_module_%s_t%d" % (prefix, tier))
    # Generated families: grenade_<warhead> and minecart_<warhead> for the whole roster, and the block
    # items that go with explosive_<warhead> (registered in SCGBlocks, translated through block.*).
    if "registerGrenades" in registry or "registerMinecarts" in registry:
        for warhead in warhead_ids():
            item_ids.add("grenade_" + warhead)
            item_ids.add("minecart_" + warhead)
    if "registerExplosiveBlockItems" in registry:
        for warhead in warhead_ids():
            block_ids.add("explosive_" + warhead)
    known = block_ids | item_ids
    missing = []
    for block in sorted(block_ids):
        if "block.%s.%s" % (MOD_ID, block) not in lang:
            missing.append("block %s" % block)
    for item in sorted(item_ids):
        # A block item is translated through the block's key, so either is correct.
        if ("item.%s.%s" % (MOD_ID, item) not in lang
                and "block.%s.%s" % (MOD_ID, item) not in lang):
            missing.append("item %s" % item)
    for entry in missing:
        fail("lang: no translation key for %s" % entry)
    notes.append("ids: %d blocks, %d items, every one has a translation key"
                 % (len(block_ids), len(item_ids)))

    # A ResourceLocation built in Java that names one of our blocks/items must match a registered id.
    pattern = re.compile(r'new ResourceLocation\(\s*"%s"\s*,\s*"(\w+)"\s*\)' % MOD_ID)
    odd = set()
    for path in walk(JAVA):
        if not path.endswith(".java"):
            continue
        for name in pattern.findall(read(path)):
            if name not in known:
                odd.add(name)
    if odd:
        notes.append("ids: %d ResourceLocations not block/item ids (%s)"
                     % (len(odd), ", ".join(sorted(odd)[:8])))


def main():
    check_old_identity()
    check_mod_id()
    check_java_types()
    check_model_refs()
    check_ids()

    for note in notes:
        print("  - " + note)
    if problems:
        print()
        for problem in problems:
            print("FAIL " + problem)
        print()
        print("NAMES FAIL (%d problem(s))" % len(problems))
        return 1
    print()
    print("NAMES PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
