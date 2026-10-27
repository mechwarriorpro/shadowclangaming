#!/usr/bin/env python3
"""Mechanical verification for the ShadowClanGaming sources.

This is NOT a compiler. There is no JDK in the build sandbox, so this script is the safety net that
catches the mistakes that actually happen when a lot of Java is written by hand in one sitting:

  1. unbalanced braces / parens / brackets      (a class that will never parse)
  2. string literals that break naive counting  (reported separately, they are legal Java)
  3. package line disagreeing with folder
  4. Thread.sleep on the server thread, appeng imports, Lua stubs
  5. every JSON file parses
  6. every registered block has a blockstate, a model and a loot table
  7. every registered item has an item model
  8. every translation key used in Java exists in en_us.json
  9. no translation key ends with a dot (the pack format rejects those)
 10. every TerminalModule / CpuTier / GpuTier has its lang key and its item

Run from the mod root:  python3 tools/sweep.py
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
LANG = os.path.join(ASSETS, "lang/en_us.json")
MOD_ID = "shadowclangaming"

# Blocks whose model is multipart or hand-built: the plain <name>.json model does not exist on purpose.
SKIP_MODELS = {"chalk_circle", "blood_circle", "hyperconductor_cable", "network_cable"}

problems = []


def fail(message):
    problems.append(message)


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def java_files():
    for base, _dirs, files in os.walk(JAVA):
        for name in sorted(files):
            if name.endswith(".java"):
                yield os.path.join(base, name)


def strip_strings_and_comments(source):
    """Blank out comments and string/char literals so bracket counting means something."""
    out = []
    i = 0
    length = len(source)
    while i < length:
        ch = source[i]
        two = source[i:i + 2]
        if two == "//":
            while i < length and source[i] != "\n":
                i += 1
        elif two == "/*":
            i += 2
            while i < length and source[i:i + 2] != "*/":
                if source[i] == "\n":
                    out.append("\n")
                i += 1
            i += 2
        elif two == '"""':
            i += 3
            while i < length and source[i:i + 3] != '"""':
                if source[i] == "\n":
                    out.append("\n")
                i += 1
            i += 3
        elif ch in "\"'":
            quote = ch
            i += 1
            while i < length:
                if source[i] == "\\":
                    i += 2
                    continue
                if source[i] == quote:
                    i += 1
                    break
                i += 1
            out.append(" ")
        else:
            out.append(ch)
            i += 1
    return "".join(out)


def check_java():
    count = 0
    for path in java_files():
        count += 1
        raw = read(path)
        stripped = strip_strings_and_comments(raw)
        for open_ch, close_ch, label in (("{", "}", "braces"), ("(", ")", "parens"), ("[", "]", "brackets")):
            if stripped.count(open_ch) != stripped.count(close_ch):
                fail("%s: unbalanced %s (%d vs %d)" % (
                    os.path.relpath(path, ROOT), label,
                    stripped.count(open_ch), stripped.count(close_ch)))
        # package must match the folder
        package = re.search(r"^package\s+([\w.]+);", raw, re.M)
        expected = os.path.dirname(os.path.relpath(path, JAVA)).replace(os.sep, ".")
        if not package:
            fail("%s: no package declaration" % os.path.relpath(path, ROOT))
        elif package.group(1) != expected:
            fail("%s: package %s but folder says %s" % (
                os.path.relpath(path, ROOT), package.group(1), expected))
        if "Thread.sleep" in raw:
            fail("%s: Thread.sleep on the server thread is forbidden" % os.path.relpath(path, ROOT))
        if re.search(r"^import\s+appeng", raw, re.M):
            fail("%s: appeng must stay reflective, no compile import" % os.path.relpath(path, ROOT))
    return count


# Entity classes that extend a LivingEntity: constructed through EntityType, so they need an entry in
# EntityAttributeCreationEvent or the constructor NPEs on its first getMaxHealth() and kills the server.
LIVING_BASES = ("LivingEntity", "Mob", "Animal", "Chicken", "Cow", "PathfinderMob", "Monster",
                "AgeableMob", "TamableAnimal", "WaterAnimal", "AbstractHorse", "Raider")


def check_entities():
    """Every registered entity whose factory builds a living entity must have an attribute supplier."""
    PKG = os.path.join(JAVA, "com/shadowclangaming")
    registry = read(os.path.join(PKG, "registry", "SCGEntities.java"))
    registry_flat = re.sub(r"\s+", " ", registry)

    # field = ENTITIES.register("name", () -> EntityType.Builder.<Generic>of(Impl::new, ...)
    registered = re.findall(
        r"(\w+)\s*=\s*ENTITIES\.register\(\s*\"[a-z_]+\",\s*\(\)\s*->\s*EntityType\.Builder"
        r"\s*\.\s*<[^>]+>\s*of\(\s*([\w.]+)::new", registry_flat)
    put = re.findall(r"event\.put\((\w+)\.get\(\)", registry_flat)

    entity_dir = os.path.join(PKG, "entity")
    living = {}
    for name in sorted(os.listdir(entity_dir)):
        if not name.endswith(".java"):
            continue
        base = re.search(r"class\s+\w+\s+extends\s+([\w.]+)", read(os.path.join(entity_dir, name)))
        if base and base.group(1).split(".")[-1] in LIVING_BASES:
            living[name[:-5]] = base.group(1).split(".")[-1]

    for field, impl in registered:
        impl = impl.split(".")[-1]
        if impl in living and field not in put:
            fail("entity %s (%s, extends %s) has no EntityAttributeCreationEvent entry - "
                 "constructing it will NPE and crash the server"
                 % (impl, field, living[impl]))
    return len(registered)


def check_particles():
    """
    ItemParticleOption refuses an empty stack - and it throws on the CLIENT, inside the packet handler.

    Any `setItem(ItemStack.EMPTY)` on a vanilla projectile (ThrownEgg, Snowball, ...) is therefore a
    crash waiting for that projectile to break; vanilla builds the break particles from getItem().
    """
    for path in java_files():
        raw = read(path)
        if re.search(r"\.setItem\(\s*(?:net\.minecraft\.world\.item\.)?ItemStack\.EMPTY\s*\)", raw):
            fail("%s: setItem(ItemStack.EMPTY) - vanilla particle effects reject empty stacks and crash "
                 "the client" % os.path.relpath(path, ROOT))


def check_shaders():
    """Every blast style must have all three of its files, and both JSONs must parse.

    A style whose post chain is missing does not crash: Minecraft logs "couldn't load post effect" and the
    warhead simply has no screen effect - which is exactly the bug this check exists to catch before a player
    reports "explosives have no shaders" again.
    """
    source = read(os.path.join(JAVA, "com/shadowclangaming/warhead/BlastStyle.java"))
    # enum constants: NAME("blast_x", "id", 0xFFRRGGBB, ...
    shaders = re.findall(r'^\s{4}[A-Z_]+\("(blast_[a-z_]+)"', source, re.M)
    if not shaders:
        fail("BlastStyle.java: no shader names found - did the enum formatting change?")
        return 0
    for name in shaders:
        effect = os.path.join(ASSETS, "post_effect", name + ".json")
        program = os.path.join(ASSETS, "shaders/program/post", name + ".json")
        fragment = os.path.join(ASSETS, "shaders/post", name + ".fsh")
        for path, what in ((effect, "post effect"), (program, "program"), (fragment, "fragment shader")):
            if not os.path.isfile(path):
                fail("blast style %s: missing %s %s" % (name, what, os.path.relpath(path, RES)))
        if os.path.isfile(effect):
            try:
                data = json.loads(read(effect))
                if not data.get("passes"):
                    fail("post effect %s: no passes" % name)
            except Exception as error:  # noqa: BLE001
                fail("post effect %s: invalid JSON (%s)" % (name, error))
        if os.path.isfile(program):
            try:
                data = json.loads(read(program))
                uniform_names = {entry.get("name") for entry in data.get("uniforms", [])}
                for needed in ("Strength", "Time"):
                    if needed not in uniform_names:
                        fail("program %s: no %s uniform - the Java side sets it every frame"
                             % (name, needed))
                if not data.get("vertex") or not data.get("fragment"):
                    fail("program %s: needs a vertex and a fragment shader" % name)
            except Exception as error:  # noqa: BLE001
                fail("program %s: invalid JSON (%s)" % (name, error))
    # Minecraft 1.21.1 EffectInstance resolves source files from shaders/program/<id> and treats
    # unqualified IDs as minecraft:, so each declared program must use this mod's namespace and have source
    # files in the program directory.
    program_dir = os.path.join(ASSETS, "shaders/program/post")
    for filename in sorted(os.listdir(program_dir)):
        if not filename.endswith(".json"):
            continue
        program_path = os.path.join(program_dir, filename)
        data = json.loads(read(program_path))
        for key, extension in (("vertex", ".vsh"), ("fragment", ".fsh")):
            shader_id = data.get(key, "")
            if not shader_id.startswith("shadowclangaming:"):
                fail("%s: %s shader id must be namespaced, got %r" %
                     (os.path.relpath(program_path, RES), key, shader_id))
                continue
            rel = shader_id.split(":", 1)[1]
            if not os.path.isfile(os.path.join(ASSETS, "shaders/program", rel + extension)):
                fail("%s: %s source missing: shaders/program/%s%s" %
                     (os.path.relpath(program_path, RES), key, rel, extension))

    # smoothstep is undefined when edge0 >= edge1 on GLSL drivers. A few reversed edges can return NaN or
    # black pixels, so reject numeric cases in all shipped fragment shaders.
    for filename in sorted(os.listdir(program_dir)):
        if not filename.endswith(".fsh"):
            continue
        shader_path = os.path.join(program_dir, filename)
        shader_text = read(shader_path)
        for match in re.finditer(r"smoothstep\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*,", shader_text):
            edge0, edge1 = float(match.group(1)), float(match.group(2))
            if edge0 >= edge1:
                fail("%s: reversed/flat smoothstep edges (%s >= %s) are undefined in GLSL"
                     % (os.path.relpath(shader_path, RES), edge0, edge1))

    # Apex.shader() returns a suffix such as "runes" or "shock". ApexShaderFx prepends "apex_" before
    # loading it; verify that every declared apex suffix has the correspondingly prefixed resource.
    apex_source = read(os.path.join(JAVA, "com/shadowclangaming/warhead/apex/Apex.java"))
    apex_names = re.findall(r'^\s{4}[A-Z_]+\("[a-z_]+",\s*\d+,\s*Front\.\w+,\s*Style\.\w+,\s*"([a-z_]+)"',
                            apex_source, re.M)
    for suffix in sorted(set(apex_names)):
        effect = os.path.join(ASSETS, "post_effect", "apex_" + suffix + ".json")
        if not os.path.isfile(effect):
            fail("apex shader %s: missing post effect %s" %
                 (suffix, os.path.relpath(effect, RES)))
    # Explosive effects are rendered as depth-tested geometry in the level, not a GUI overlay/post pass.
    world_program = os.path.join(ASSETS, "shaders/core/world_blast.json")
    if not os.path.isfile(world_program):
        fail("world explosive core shader missing: shaders/core/world_blast.json")
    else:
        try:
            data = json.loads(read(world_program))
            for key, extension in (("vertex", ".vsh"), ("fragment", ".fsh")):
                shader_id = data.get(key, "")
                if shader_id != "shadowclangaming:world_blast":
                    fail("world explosive program has unexpected %s shader id %r" % (key, shader_id))
                elif not os.path.isfile(os.path.join(ASSETS, "shaders/core", shader_id.split(":", 1)[1] + extension)):
                    fail("world explosive %s source missing" % key)
            if not {"Position", "UV0", "Color"}.issubset(set(data.get("attributes", []))):
                fail("world explosive shader must consume position, UV and colour")
            fragment = read(os.path.join(ASSETS, "shaders/core/world_blast.fsh"))
            for token in ("discard", "shock=", "turbulence", "Progress"):
                if token not in fragment:
                    fail("world explosive fragment shader is missing %s" % token)
            renderer = read(os.path.join(JAVA, "com/shadowclangaming/client/WorldBlastShader.java"))
            if "AFTER_PARTICLES" not in renderer or "BufferUploader.drawWithShader" not in renderer:
                fail("world explosive shader must render level geometry after particles")
            events = read(os.path.join(JAVA, "com/shadowclangaming/client/BlastClientEvents.java"))
            if "BlastOverlay.render(" in events or "ApexOverlay.render(" in events:
                fail("GUI blast overlays are not allowed; fallback must remain in world space")
        except (ValueError, KeyError, TypeError) as error:
            fail("world explosive shader JSON invalid: %s" % error)

    # The per-warhead cinematic geometry has its own, deliberately trivial program: vertex colour straight
    # out. If it is missing or misnamed the renderer silently degrades to vanilla shading, so check it.
    geom_program = os.path.join(ASSETS, "shaders/core/world_blast_geom.json")
    if not os.path.isfile(geom_program):
        fail("cinematic geometry core shader missing: shaders/core/world_blast_geom.json")
    else:
        try:
            data = json.loads(read(geom_program))
            for key, extension in (("vertex", ".vsh"), ("fragment", ".fsh")):
                shader_id = data.get(key, "")
                if shader_id != "shadowclangaming:world_blast_geom":
                    fail("cinematic geometry program has unexpected %s shader id %r" % (key, shader_id))
                elif not os.path.isfile(os.path.join(ASSETS, "shaders/core",
                                                    shader_id.split(":", 1)[1] + extension)):
                    fail("cinematic geometry %s source missing" % key)
            if set(data.get("attributes", [])) != {"Position", "Color"}:
                fail("cinematic geometry shader must consume exactly position and colour")
            renderer = read(os.path.join(JAVA, "com/shadowclangaming/client/CinematicFx.java"))
            if "AFTER_PARTICLES" not in renderer or "BufferUploader.drawWithShader" not in renderer:
                fail("cinematic geometry must be drawn as level geometry after particles")
        except (ValueError, KeyError, TypeError) as error:
            fail("cinematic geometry shader JSON invalid: %s" % error)
    return len(shaders) + 2


def check_json():
    count = 0
    for base, _dirs, files in os.walk(RES):
        for name in sorted(files):
            if name.endswith(".json"):
                path = os.path.join(base, name)
                count += 1
                try:
                    json.loads(read(path))
                except Exception as error:  # noqa: BLE001 - report, do not crash
                    fail("%s: invalid JSON (%s)" % (os.path.relpath(path, ROOT), error))
    return count


def warhead_ids():
    """Every warhead id, straight out of the enum: the packaged forms are generated from this list."""
    text = read(os.path.join(JAVA, "com/shadowclangaming/warhead/WarheadType.java"))
    return re.findall(r'^\s{4}[A-Z_]+\("([a-z_0-9]+)",\s*[\d.]+F\)', text, re.M)


def explosive_block_id(warhead):
    return "explosive_" + warhead


def block_ids():
    source = read(os.path.join(JAVA, "com/shadowclangaming/registry/SCGBlocks.java"))
    pairs = re.findall(r"public static final DeferredBlock<[^>]+>\s+(\w+)\s*=\s*BLOCKS\.registerBlock\(\s*\"(\w+)\"",
                       source, re.S)
    # Generated family: the EXPLOSIVE_BLOCKS loop registers explosive_<warhead> for the whole roster.
    if "registerExplosiveBlocks" in source:
        for warhead in warhead_ids():
            pairs.append(("EXPLOSIVE_" + warhead.upper(), explosive_block_id(warhead)))
    return dict(pairs)


def item_ids():
    source = read(os.path.join(JAVA, "com/shadowclangaming/registry/SCGItems.java"))
    # The id must be the WHOLE first argument: `"missile_module_" + prefix` is a fragment, not an id.
    simple = re.findall(r"ITEMS\.register(?:Simple)?Item\(\s*\"(\w+)\"\s*,", source)
    # Generated families: registerModuleKind("homing", ...) registers missile_module_homing_t1..t5.
    # Expanded here rather than writing 20 literal ids in the registry, which would be 20 chances to
    # copy-paste the wrong tier.
    for prefix in re.findall(r"registerModuleKind\(\s*\"(\w+)\"", source):
        for tier in range(1, 6):
            simple.append("missile_module_%s_t%d" % (prefix, tier))
    # Generated families: registerGrenades / registerMinecarts loop over WarheadType.values().
    if "registerGrenades" in source:
        for warhead in warhead_ids():
            simple.append("grenade_" + warhead)
    if "registerMinecarts" in source:
        for warhead in warhead_ids():
            simple.append("minecart_" + warhead)
    block_items = re.findall(r"registerSimpleBlockItem\(SCGBlocks\.(\w+)\)", source)
    return simple, block_items


def check_assets():
    blocks = block_ids()
    for constant, block_id in blocks.items():
        state = os.path.join(ASSETS, "blockstates", block_id + ".json")
        if not os.path.isfile(state):
            fail("block %s (%s): missing blockstate" % (block_id, constant))
        if block_id not in SKIP_MODELS:
            model = os.path.join(ASSETS, "models/block", block_id + ".json")
            if not os.path.isfile(model):
                fail("block %s: missing block model (add to SKIP_MODELS if multipart on purpose)"
                     % block_id)
        loot = os.path.join(DATA, "loot_table/blocks", block_id + ".json")
        if not os.path.isfile(loot):
            fail("block %s: missing loot table" % block_id)

    simple, block_items = item_ids()
    for item_id in simple:
        model = os.path.join(ASSETS, "models/item", item_id + ".json")
        if not os.path.isfile(model):
            fail("item %s: missing item model" % item_id)
    for constant in block_items:
        block_id = blocks.get(constant)
        if block_id is None:
            fail("block item refers to unknown block constant %s" % constant)
            continue
        model = os.path.join(ASSETS, "models/item", block_id + ".json")
        if not os.path.isfile(model):
            fail("block item %s: missing item model" % block_id)
    return len(blocks), len(simple) + len(block_items)


def check_recipes():
    """Shaped-recipe sanity: every key symbol must appear in the pattern, and every result must exist.

    Minecraft silently drops a recipe that has an unused key symbol ("Key defines symbols that aren't
    used in pattern"), so an item simply never appears in the recipe book and nobody is told why.
    This check exists because nine armour pieces shipped like that once.
    """
    directory = os.path.join(RES, "data/shadowclangaming/recipe")
    # Any id spelled out in a registry class is a real id. Registry files build some entries in loops
    # (armour pieces, cables, tiers), so the literals are the only reliable offline source of truth.
    known = set()
    for registry in ("SCGItems.java", "SCGBlocks.java"):
        text = read(os.path.join(JAVA, "com/shadowclangaming/registry", registry))
        known.update(re.findall(r'"([a-z0-9_]+)"', text))
    # ...plus anything built programmatically, which only item_ids() knows how to expand.
    simple, _block_items = item_ids()
    known.update(simple)
    # Block items (explosive_<warhead>, and anything else registered from a block) are craftable results
    # too, and their ids come from SCGBlocks rather than SCGItems.
    known.update(block_ids().values())
    count = 0
    if not os.path.isdir(directory):
        return 0
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".json"):
            continue
        count += 1
        path = os.path.join(directory, name)
        try:
            data = json.loads(read(path))
        except Exception:  # noqa: BLE001 - check_json already reported it
            continue
        pattern = data.get("pattern")
        key = data.get("key")
        if pattern and key:
            used = {ch for row in pattern for ch in row if ch != " "}
            for symbol in key:
                if symbol not in used:
                    fail("recipe %s: key %r is never used in the pattern" % (name, symbol))
        result = data.get("result")
        if isinstance(result, dict):
            item = result.get("id", "")
            if item.startswith(MOD_ID + ":"):
                short = item.split(":", 1)[1]
                if short not in known:
                    fail("recipe %s: result %s is not a registered item" % (name, item))
    return count


def check_lang():
    lang = json.loads(read(LANG))
    keys = set(lang)
    used = set()
    for path in java_files():
        raw = read(path)
        for key in re.findall(r"translatable\(\s*\"([\w.\-]+)\"", raw):
            # Keys are sometimes built by concatenation: "foo.bar." + suffix. Those are prefixes, not
            # keys, and checking them would only produce noise.
            if not key.endswith("."):
                used.add(key)
    for key in sorted(used):
        if key not in keys:
            fail("lang: key used in Java but missing: %s" % key)
    for key in sorted(keys):
        if key.endswith("."):
            fail("lang: key ends with a dot: %s" % key)

    # dynamic keys built by the enums
    module_ids = re.findall(r"^\s{4}([A-Z_]+)\(\"(\w+)\"", read(
        os.path.join(JAVA, "com/shadowclangaming/computer/TerminalModule.java")), re.M)
    for _constant, module_id in module_ids:
        if "module.shadowclangaming." + module_id not in keys:
            fail("lang: missing module key for %s" % module_id)
    for enum_name, prefix in (("CpuTier", "cpu.shadowclangaming."), ("GpuTier", "gpu.shadowclangaming.")):
        source = read(os.path.join(JAVA, "com/shadowclangaming/computer/%s.java" % enum_name))
        for constant in re.findall(r"^\s{4}([A-Z_]+)\(\"(\w+)\"", source, re.M):
            if prefix + constant[1] not in keys:
                fail("lang: missing %s key for %s" % (enum_name, constant[0]))
            item_id = constant[1]
            if enum_name == "CpuTier":
                item_id = "cpu_" + item_id
            elif item_id == "off":
                continue  # there is no "no graphics card" item
            else:
                item_id = "gpu_" + item_id
            if "item.shadowclangaming." + item_id not in keys:
                fail("lang: missing item key for %s" % item_id)
    return len(keys)



def check_world_shaders():
    """The in-world effect programs: files, attributes, uniforms, and the Java that drives them.

    These are core shaders, not post chains, and a mistake in one is silent: Minecraft logs a warning and
    the warhead draws nothing at all. The checks that matter are the joins - every attribute the JSON
    declares must exist in the vertex shader, every uniform the Java sets by name must exist in the JSON
    (a typo would leave the effect permanently at its default), and the varyings must line up.
    """
    programs = ("world_blast_geom", "world_blast_volume")
    drivers = {
        "world_blast_geom": "com/shadowclangaming/client/CinematicFx.java",
        "world_blast_volume": "com/shadowclangaming/client/VolumetricFx.java",
    }
    count = 0
    for name in programs:
        meta = os.path.join(ASSETS, "shaders/core", name + ".json")
        vsh = os.path.join(ASSETS, "shaders/core", name + ".vsh")
        fsh = os.path.join(ASSETS, "shaders/core", name + ".fsh")
        for path, what in ((meta, "program JSON"), (vsh, "vertex shader"), (fsh, "fragment shader")):
            if not os.path.isfile(path):
                fail("core shader %s: missing %s" % (name, what))
        if not (os.path.isfile(meta) and os.path.isfile(vsh) and os.path.isfile(fsh)):
            continue
        try:
            data = json.loads(read(meta))
        except Exception as error:  # noqa: BLE001
            fail("core shader %s: invalid JSON (%s)" % (name, error))
            continue

        vertex_source = read(vsh)
        fragment_source = read(fsh)
        declared_attributes = [entry for entry in data.get("attributes", [])]
        for attribute in declared_attributes:
            if not re.search(r"^in\s+\w+\s+%s;" % attribute, vertex_source, re.M):
                fail("core shader %s: JSON declares attribute %s that the vertex shader does not"
                     % (name, attribute))
        for declared in re.findall(r"^in\s+\w+\s+(\w+);", vertex_source, re.M):
            if declared not in declared_attributes:
                fail("core shader %s: vertex shader reads %s but the JSON does not declare it"
                     % (name, declared))

        declared_uniforms = {entry.get("name") for entry in data.get("uniforms", [])}
        glsl_uniforms = set(re.findall(r"^uniform\s+\w+\s+(\w+);", vertex_source, re.M))
        glsl_uniforms |= set(re.findall(r"^uniform\s+\w+\s+(\w+);", fragment_source, re.M))
        for uniform in sorted(declared_uniforms):
            if uniform not in glsl_uniforms:
                fail("core shader %s: JSON declares uniform %s that no shader stage uses" % (name, uniform))
        for uniform in sorted(glsl_uniforms):
            if uniform not in declared_uniforms:
                fail("core shader %s: shader uses uniform %s that the JSON does not declare, so Java "
                     "can never set it" % (name, uniform))

        for varying in re.findall(r"^out\s+\w+\s+(\w+);", vertex_source, re.M):
            if not re.search(r"^in\s+\w+\s+%s;" % varying, fragment_source, re.M):
                fail("core shader %s: vertex shader writes varying %s the fragment shader never reads"
                     % (name, varying))
        if "out vec4 fragColor;" not in fragment_source:
            fail("core shader %s: fragment shader has no fragColor output" % name)

        driver = read(os.path.join(JAVA, drivers[name]))
        # Both idioms this package uses: safeGetUniform("X") directly, and the private set(program, "X", ...)
        # helpers that wrap it so no file here has to name the uniform class.
        looked_up = set(re.findall(r'safe?[gG]etUniform\("([^"]+)"\)', driver))
        looked_up |= set(re.findall(r'\bset\(program, "([^"]+)"', driver))
        for uniform in sorted(looked_up):
            if uniform not in declared_uniforms:
                fail("core shader %s: %s looks up uniform %s that the program does not declare"
                     % (name, drivers[name], uniform))
        # The reverse join matters more. A uniform nobody sets just keeps its JSON default - silently,
        # with no warning anywhere - so a feature weight Java forgets to push leaves that warhead
        # missing the one thing its description asked for. ColorModulator is exempt: ShaderInstance
        # fills that one itself from RenderSystem.getShaderColor() and never lets the mod touch it.
        for uniform in sorted(declared_uniforms - {"ColorModulator"}):
            if uniform not in looked_up:
                fail("core shader %s: nothing in %s ever sets uniform %s, so it is stuck at its "
                     "JSON default" % (name, drivers[name], uniform))
        count += 1
    print("core world shaders: %d" % count)
    return count


def check_emp_drain():
    """An EMP must empty every machine in its radius, and the way in has to actually work.

    Three separate things have to line up here, and any one of them can silently break the others:

    1. Our own launch pad and auto-trader advertise a *receive-only* energy capability - their
       extractEnergy returns 0 on purpose, so nothing can pull power back out of them. An EMP that only
       used the capability would therefore drain every other mod's machine and none of ours, which is the
       opposite of the requirement. So empPulse has to call empDrain() on those two types directly.
    2. Everything else is drained through the capability, which is what makes "any machine" true rather
       than "any machine this mod happens to know about".
    3. The pad's internal EnergyStorage must not be built with maxExtract 0. fundFromEnergy() spends the
       buffer with extractEnergy, and with maxExtract 0 NeoForge hands back 0 - the launch paid out
       funding for energy it never consumed, every launch, for free. The same 0 would also make
       empDrain() a no-op.
    """
    before = len(problems)
    warhead_type = read(os.path.join(JAVA, "com/shadowclangaming/warhead/WarheadType.java"))
    pulse = warhead_type[warhead_type.index("private static void empPulse("):]
    pulse = pulse[:pulse.index("\n    private static ") if "\n    private static " in pulse[10:] else len(pulse)]

    for typed, call in (("LaunchPadBlockEntity", "pad.empDrain()"),
                        ("AutoTraderBlockEntity", "trader.empDrain()")):
        if call not in pulse:
            fail("empPulse never calls %s, so an EMP cannot empty a %s - the capability it advertises "
                 "is receive-only" % (call, typed))
    if "drainEnergy(level, p, blockEntity)" not in pulse:
        fail("empPulse no longer drains through the energy capability, so other mods' machines in the "
             "radius keep their FE")

    for name in ("LaunchPadBlockEntity", "AutoTraderBlockEntity"):
        source = read(os.path.join(JAVA, "com/shadowclangaming/block/%s.java" % name))
        if "public int empDrain()" not in source:
            fail("%s has no empDrain(); empPulse calls it" % name)

    pad = read(os.path.join(JAVA, "com/shadowclangaming/block/LaunchPadBlockEntity.java"))
    storage = re.search(r"new EnergyStorage\(([^)]*)\)", pad)
    if storage is None:
        fail("LaunchPadBlockEntity no longer constructs an EnergyStorage; check_emp_drain cannot verify "
             "maxExtract")
    else:
        args = [argument.strip() for argument in storage.group(1).split(",")]
        if len(args) < 3 or args[2] in ("0", "0L"):
            fail("LaunchPadBlockEntity builds its EnergyStorage with maxExtract %s: fundFromEnergy() "
                 "cannot spend the buffer (free funding on every launch) and empDrain() cannot empty it"
                 % (args[2] if len(args) >= 3 else "<missing>"))
    if len(problems) == before:
        print("emp drain: typed for 2 of our machines, capability for everything else")
    return 1


def check_shape_authoring():
    """BlastMotif.Volume has two switches over warheads that must not be mixed up.

    Shape.baseline assigns fields on a local Shape ('s.dust = 0.5F'). Volume.author chains fluent setters
    ('shape.dust(0.5F)'). They sit a few lines apart and read alike, so it is easy to put one form in the
    other's switch - which is a compile error, and this workspace has no compiler, so the mistake only
    surfaces in CI a minute and a half later. Worth checking here:

      * every 's.X =' in the file is inside Shape.baseline, and X is a real Shape field;
      * every '.X(' in Volume.author is a real setter on Shape;
      * every Shape field is reachable from some setter, or it can never be authored.
    """
    source = read(os.path.join(JAVA, "com/shadowclangaming/warhead/BlastMotif.java"))
    for marker in ("static Shape baseline(", "private static void author(", "Shape beam(float v)"):
        if marker not in source:
            fail("check_shape_authoring cannot find %r in BlastMotif" % marker)
            return 0
    baseline = source[source.index("static Shape baseline("):source.index("Shape beam(float v)")]
    author = source[source.index("private static void author("):source.index("One string per warhead")]
    # From the Shape declaration through the end of its last setter line. The fields come before the
    # setters, so slicing from the first setter would find nothing to check; and the cut has to land
    # after the setter BODY, not just its signature, or the last setter looks like it assigns nothing.
    shape_cls = source[source.index("private static final class Shape {"):]
    setter_starts = [m.end() for m in re.finditer(r"Shape \w+\(float v\)", shape_cls)]
    if not setter_starts:
        fail("check_shape_authoring found no Shape setters; the layout it expects has changed")
        return 0
    shape_cls = shape_cls[:shape_cls.index("\n", max(setter_starts))]

    fields = set(re.findall(r"^\s{12}float (\w+);", shape_cls, re.M))
    setters = set(re.findall(r"^\s{12}Shape (\w+)\(float v\)", shape_cls, re.M))
    set_fields = set(re.findall(r"Shape \w+\(float v\) \{ (\w+) = v;", shape_cls))
    if not fields or not setters:
        fail("check_shape_authoring found no Shape fields or setters; the layout it expects has changed")
        return 0

    for field in sorted(set(re.findall(r"\bs\.(\w+)\s*=", baseline)) - fields):
        fail("Shape.baseline assigns s.%s, which is not a field of Shape" % field)
    for field in sorted(set(re.findall(r"\bs\.(\w+)\s*=", author))):
        fail("Volume.author assigns s.%s directly - that switch chains setters (shape.%s(...)), and 's' "
             "does not exist there" % (field, field))
    for name in sorted(set(re.findall(r"\.([a-zA-Z]+)\(", author)) - setters - {"of", "id", "get"}):
        fail("Volume.author calls .%s(...), which is not a setter on Shape" % name)
    for field in sorted(fields - set_fields):
        fail("Shape.%s has no setter that assigns it, so no warhead can author it" % field)
    print("shape authoring: %d fields, %d setters, both switches in their own form"
          % (len(fields), len(setters)))
    return 1


def check_impact_fx_reachable():
    """Every warhead must announce its detonation, including the ones that hand off and return early.

    The seventeen apex warheads return from inside WarheadType.detonate to run their own staged timeline.
    The impact effect used to be announced *below* that branch, so it never ran for any of them: the World
    Eater, Ragnarok and the Quasar Lance each played a charge effect for the whole fuse and then detonated
    invisibly. It compiles, it runs, the warhead does its damage - and the one thing you are watching for
    does not happen, with nothing in any log to say so.

    So: BlastFx.emit must appear before the first return in the method that owns the switch.
    """
    source = read(os.path.join(JAVA, "com/shadowclangaming/warhead/WarheadType.java"))
    marker = "int sliderRadius, long buildSeed) {"
    if marker not in source:
        fail("WarheadType.detonate no longer has the (sliderRadius, buildSeed) overload; "
             "check_impact_fx_reachable cannot find the switch owner")
        return 0
    body = source[source.index(marker):]
    emit = body.find("BlastFx.emit(")
    early = body.find("\n            return;")
    if emit < 0:
        fail("WarheadType.detonate never calls BlastFx.emit, so no warhead announces its detonation")
        return 0
    if 0 <= early < emit:
        fail("WarheadType.detonate returns before BlastFx.emit: any warhead taking that branch detonates "
             "with no impact effect (this is how the seventeen apex warheads went silent)")
        return 0
    print("impact effect: announced before the first return in WarheadType.detonate")
    return 1


def check_glsl_defined():
    """smoothstep(edge0, edge1, x) is undefined when edge0 > edge1, and drivers disagree about what it does.

    Some ramp backwards, some return 0, some return NaN - and a NaN in a density field turns one warhead
    into black or white pixels on one graphics card and works fine on the next. The result only shows up
    in game, on the user's machine, so it is worth catching here over a regex.
    """
    problems = 0
    checked = 0
    for path in sorted(os.listdir(os.path.join(ASSETS, "shaders/core"))):
        if not path.endswith(".fsh"):
            continue
        source = read(os.path.join(ASSETS, "shaders/core", path))
        for match in re.finditer(r"smoothstep\(\s*([-\d.e]+)\s*,\s*([-\d.e]+)\s*,", source):
            checked += 1
            edge0, edge1 = float(match.group(1)), float(match.group(2))
            if edge0 > edge1:
                line = source[:match.start()].count("\n") + 1
                fail("%s line %d: smoothstep(%g, %g, ...) is undefined when edge0 > edge1"
                     % (path, line, edge0, edge1))
                problems += 1
    print("glsl smoothstep edges: %d checked, %d reversed" % (checked, problems))
    return checked


def check_proxy_box():
    """The proxy cube the fragment shader marches must be the cube the Java draws.

    Both files define the half-extent in blast radii and nothing connects them, so if one moves the
    shader either marches empty space around a smaller cube (a visible box edge on every blast) or
    clips the effect to the inside of a bigger one. Cheap to check, miserable to debug in game.
    """
    fsh = read(os.path.join(ASSETS, "shaders/core/world_blast_volume.fsh"))
    driver = read(os.path.join(JAVA, "com/shadowclangaming/client/VolumetricFx.java"))
    shader_box = re.search(r"const\s+float\s+PROXY_BOX\s*=\s*([\d.]+);", fsh)
    java_box = re.search(r"double\s+PROXY_BOX\s*=\s*([\d.]+)D;", driver)
    if shader_box is None or java_box is None:
        fail("check_proxy_box cannot find PROXY_BOX in %s"
             % ("the fragment shader" if shader_box is None else "VolumetricFx"))
        return 0
    if abs(float(shader_box.group(1)) - float(java_box.group(1))) > 0.0001:
        fail("the volumetric proxy cube disagrees with itself: the shader marches %s radii but "
             "VolumetricFx draws %s" % (shader_box.group(1), java_box.group(1)))
    print("volumetric proxy box: %s radii" % shader_box.group(1))
    return 1


def check_event_subscribers():
    """Every @EventBusSubscriber class must actually have a listener, and no renderer may draw twice.

    NeoForge's automatic subscriber registers the class and then throws
    "has no @SubscribeEvent methods, but register was called anyway" - during mod construction, before
    anything else in the mod loads. That is not a warning in a log; it is the whole mod failing to start,
    and it is what the first real launch of the volumetric build did.

    The second half is the same mistake from the other side: a renderer that subscribes itself AND is
    called from the fallback path draws the effect twice, and the legacy billboard that stayed subscribed
    after being replaced drew a flat disc underneath every effect for several rounds.
    """
    subscribers = []
    for path in java_files():
        source = read(path)
        annotated = bool(re.search(r"^\s*@EventBusSubscriber\b", source, re.M))
        if not annotated:
            continue
        name = os.path.relpath(path, JAVA)
        listeners = len(re.findall(r"^\s*@SubscribeEvent\b", source, re.M))
        if listeners == 0:
            fail("%s is annotated @EventBusSubscriber but has no @SubscribeEvent method - NeoForge "
                 "refuses to load the mod" % name)
        subscribers.append((name, listeners))

    events = read(os.path.join(JAVA, "com/shadowclangaming/client/BlastClientEvents.java"))
    called = set(re.findall(r"\b(\w+)\.render\(event\)", events))
    for name, _ in subscribers:
        if os.path.basename(name)[:-5] in called:
            fail("%s subscribes itself to the render stage and is also called from BlastClientEvents - "
                 "the effect would be drawn twice" % name)
    for renderer in ("VolumetricFx", "CinematicFx"):
        if renderer not in called:
            fail("BlastClientEvents never calls %s.render - the world effect would never be drawn" % renderer)
    return len(subscribers)

# Yarn names for things this codebase refers to by their Mojmap names. The mod is Mojmap, so a Yarn
# name is a compile error rather than a warning - and this workspace has no compiler, so it costs a
# full CI round to find out. Every entry here has already been paid for once.
# name -> (pattern that only the Yarn usage matches, the Mojmap replacement)
YARN_NAMES = {
    # A bare identifier: `age % 4`, `age;`, `age++`. Not `.age` - that is the mod's own Entry field.
    "age": (r"(?<![\w.])age\b", "tickCount"),
    # A call site, whatever it is called on: `getWorld()`, `this.getWorld()`, `entity.getWorld()`.
    "getWorld": (r"\bgetWorld\s*\(", "level()"),
}


def check_sky_box_reaches_the_big_ones():
    """The sky cube must be able to get outside the blast cube, or the sky is never drawn.

    skyBoxRadii() returns 0 - "draw nothing" - when the block cap leaves the sky inside the blast's own
    cube, and that cube is radius * PROXY_BOX. So a cap below radius * PROXY_BOX silently deletes the
    skybox, with no error and no log. At 3000 blocks that started happening above 882 blocks of blast
    radius, which is the entire stellar tier, and the report was "still no skybox effects".

    Asserted against the largest radius BlastFx.radius can produce rather than a typical one: if it holds
    at 4096 it holds everywhere.
    """
    path = os.path.join(JAVA, "com", "shadowclangaming", "client", "VolumetricFx.java")
    source = read(path)
    cap = re.search(r"SKY_BOX_MAX_BLOCKS\s*=\s*([\d.]+)D", source)
    proxy = re.search(r"PROXY_BOX\s*=\s*([\d.]+)D", source)
    if not cap or not proxy:
        fail("cannot read SKY_BOX_MAX_BLOCKS / PROXY_BOX from VolumetricFx")
        return 1
    cap_v, proxy_v = float(cap.group(1)), float(proxy.group(1))
    widest = 4096.0  # BlastFx.radius clamps to this
    need = widest * proxy_v
    if cap_v <= need:
        fail("SKY_BOX_MAX_BLOCKS=%.0f cannot get outside a %.0f-block blast cube (needs > %.0f); "
             "the stellar tier would get no skybox" % (cap_v, need, need))
        return 1
    print("sky box: %.0f blocks clears a %.0f-block blast cube (max radius %.0f)"
          % (cap_v, need, widest))
    return 0


def check_soul_furnace_spawn_guard():
    """The Soul Furnace spawn veto is wired end to end.

    The furnace releases on the first availability check that finds nothing, so anything that lets mobs
    walk back in between checks makes it grow forever. Two halves have to hold for that not to happen -
    something has to veto the spawn, and the veto has to consult the furnace list. Either half on its own
    compiles and does nothing.
    """
    path = os.path.join(JAVA, "com", "shadowclangaming", "warhead", "apex",
                        "SoulFurnaceSpawnGuard.java")
    if not os.path.exists(path):
        fail("SoulFurnaceSpawnGuard.java is missing; the furnace can be repopulated between checks")
        return 1
    source = read(path)
    need = {
        "setResult(MobSpawnEvent.PositionCheck.Result.FAIL)": "vetoes the spawn",
        "SoulFurnaceStages.suppressesSpawning": "consults the burning-furnace list",
        "getSpawnType": "logs which spawn was refused",
    }
    missing = [what for needle, what in need.items() if needle not in source]
    if missing:
        for what in missing:
            fail("SoulFurnaceSpawnGuard no longer %s" % what)
        return len(missing)
    if not re.search(r"@SubscribeEvent\s+public static void onSpawnPositionCheck\s*\(\s*"
                     r"MobSpawnEvent\.PositionCheck", source):
        fail("SoulFurnaceSpawnGuard's handler is not bound to MobSpawnEvent.PositionCheck")
        return 1
    print("soul furnace spawn guard: vetoes spawns inside a burning furnace")
    return 0


def check_mojmap_names():
    """No Yarn-only *entity* identifiers: this is Mojmap and `age` is not `Entity.age` here.

    `age` for an entity's tick count and `getWorld()` for its level both read perfectly plausibly if
    you have spent time in Fabric, and both are `cannot find symbol` in this project.

    Scoped, because `age` is a perfectly good name for the mod's OWN fields - ClientBlastQueue.Entry,
    ApexStages and half a dozen others have one, and they are not entities. So a file is only checked
    if it does not declare `age` itself, and comments and strings are stripped first: prose about "the
    effect's age" is not an identifier.
    """
    hits = 0
    for path in java_files():
        source = read(path)
        code = re.sub(r"/\*.*?\*/", " ", source, flags=re.S)
        code = re.sub(r"//[^\n]*", "", code)
        code = re.sub(r'"(?:[^"\\]|\\.)*"', '""', code)
        for yarn, (pattern, mojmap) in YARN_NAMES.items():
            if re.search(r"(?:int|long|float|double|var)\s+%s\b" % yarn, code):
                continue  # this file has its own field, local or parameter of that name
            for match in re.finditer(pattern, code):
                line = code[:match.start()].count("\n") + 1
                fail("%s line %d uses the Yarn name `%s`; this is Mojmap, use %s"
                     % (os.path.relpath(path, JAVA), line, yarn, mojmap))
                hits += 1
    if hits == 0:
        print("mojmap names: no Yarn-only identifiers")
    return hits


def check_fuse_ownership():
    """No delivery form may count its fuse on an entity that can be removed mid-count.

    A grenade used to do exactly that. super.onHit() runs first, vanilla's ThrowableItemProjectile
    .onHitBlock ends by discarding a throwable that lands on a block, so tick() was never called again:
    the grenade armed, played its whole charge effect, and then did not exist. Nothing in any log said
    so, because the code was correct - it was just attached to an entity that had already been removed.

    The rule: a fuse is either a scheduled block tick, or it belongs to ChargeSequence (a server-side
    list). A mutable countdown field decremented in an entity's tick() is the failure mode.
    """
    entities = os.path.join(JAVA, "com/shadowclangaming/entity")
    problems = 0
    for name in sorted(os.listdir(entities)):
        if not name.endswith(".java"):
            continue
        source = read(os.path.join(entities, name))
        counts_down = re.search(r"^\s*(?:fuse|scgFuse|ticksLeft|fuseLeft)\s*--;", source, re.M)
        if counts_down is None:
            continue
        line = source[:counts_down.start()].count("\n") + 1
        # WarheadMinecart is the deliberate exception: nothing discards it, and it says so.
        if name == "WarheadMinecart.java" and "nothing is discarded here" in source:
            print("fuse ownership: %s counts its own fuse and documents why that is safe" % name)
            continue
        fail("%s line %d decrements a fuse inside an entity tick - if vanilla removes the entity "
             "mid-count the warhead never detonates. Use ChargeSequence or a scheduled block tick."
             % (name, line))
        problems += 1
    if problems == 0:
        print("fuse ownership: no entity counts a fuse it could lose")
    return problems


def check_charge_wiring():
    """The effect plays first; the blast lands when it ends. Prove that is still wired up.

    Two ways this silently breaks. A delivery form lights its fuse without announcing the effect, so the
    player waits through a silent gap and the warhead "has no charge". Or a renderer exists but nothing
    subscribes to it, which is what happened to the world geometry once already - the class compiled, the
    shader registered, and not one frame was ever drawn.
    """
    for path, form in (("block/ExplosiveBlock.java", "Form.BLOCK"),
                       ("entity/GrenadeEntity.java", "Form.GRENADE"),
                       ("entity/WarheadMinecart.java", "Form.MINECART")):
        source = read(os.path.join(JAVA, "com/shadowclangaming/" + path))
        # Either it announces the effect itself, or it hands the whole thing to ChargeSequence, which
        # announces it and holds the clock. Both are correct; a form that does neither plays in silence.
        if "BlastFx.emitCharge" not in source and "ChargeSequence.start" not in source:
            fail("%s never announces its charge effect - the fuse would play in silence" % path)
        if form not in source:
            fail("%s does not derive its fuse from %s" % (path, form))

    missile = read(os.path.join(JAVA, "com/shadowclangaming/entity/MissileEntity.java"))
    if "ChargeSequence.start" not in missile:
        fail("MissileEntity detonates on impact instead of playing its effect first")

    main = read(os.path.join(JAVA, "com/shadowclangaming/ShadowClanGaming.java"))
    for call in ("ChargeSequence.tick", "ChargeSequence.clear"):
        if call not in main:
            fail("ShadowClanGaming never calls %s" % call)

    events = read(os.path.join(JAVA, "com/shadowclangaming/client/BlastClientEvents.java"))
    if "VolumetricFx.render" not in events:
        fail("nothing subscribes the volumetric renderer to a render stage - it would never draw")
    if "CinematicFx.render" not in events:
        fail("nothing falls back to the geometry renderer when the volumetric one is unavailable")
    if "RenderLevelStageEvent" not in events:
        fail("BlastClientEvents has no RenderLevelStageEvent listener")

    queue = read(os.path.join(JAVA, "com/shadowclangaming/client/ClientBlastQueue.java"))
    if "Phase phase" not in queue:
        fail("ClientBlastQueue entries carry no phase, so a charge would be drawn as a detonation")
    network = read(os.path.join(JAVA, "com/shadowclangaming/network/ScgNet.java"))
    if "PHASE_CHARGE" not in network:
        fail("the blast FX packet cannot carry a charge phase")

def check_motifs():
    """Every warhead must own a world-space motif, and the renderer must know every layer kind."""
    motif_source = read(os.path.join(JAVA, "com/shadowclangaming/warhead/BlastMotif.java"))
    geometry_source = read(os.path.join(JAVA, "com/shadowclangaming/client/BlastGeometry.java"))

    layers = re.findall(r"^\s{8}([A-Z_]+),?$",
                        motif_source[motif_source.index("public enum Layer")
                                     :motif_source.index("public enum Theme")], re.M)
    if len(layers) < 8:
        fail("motif layers: expected a full layer list, found %d" % len(layers))
    roster_section = motif_source[motif_source.index("public static Motif forWarhead")
                                  :motif_source.index("spec builders")]
    for layer in layers:
        if "case %s ->" % layer not in geometry_source:
            fail("motif layer %s has no geometry in BlastGeometry.layer" % layer)
        # Each layer kind is built by one private helper; the helper must actually be called by a row.
        builders = re.findall(r"private static Spec (\w+)\([^)]*\)\s*\{[^{}]*"
                              r"new Spec\(Layer\.%s," % layer, motif_source)
        if not builders:
            fail("motif layer %s has no spec builder" % layer)
            continue
        for builder in builders:
            if not re.search(r"\b%s\(" % builder, roster_section):
                fail("motif layer %s (builder %s) is never used by any warhead" % (layer, builder))

    roster = re.findall(r'"([a-z_]+)"',
                        motif_source[motif_source.index("ROSTER = List.of(")
                                     :motif_source.index(");", motif_source.index("ROSTER = List.of("))])
    # BlastMotif now switches over warhead id twice: once to build the motif, once in Volume.author to
    # say what that warhead's effect actually looks like. Count them separately, and insist on exactly
    # one of each - a warhead that silently falls through Volume.author's default keeps only its theme
    # baseline, which is precisely how thirty-eight warheads end up looking like one another.
    author_start = motif_source.index("private static void author(")
    author_end = motif_source.index("One string per warhead", author_start)
    author_section = motif_source[author_start:author_end]
    motif_section = motif_source[:author_start] + motif_source[author_end:]
    cases = re.findall(r'case "([a-z_]+)" ->', motif_section)
    authored = re.findall(r'case "([a-z_]+)" ->', author_section)
    for warhead in warhead_ids():
        if warhead not in cases:
            fail("motif: warhead %s has no case in BlastMotif" % warhead)
        if warhead not in roster:
            fail("motif: warhead %s is missing from BlastMotif.ROSTER" % warhead)
        if warhead not in authored:
            fail("motif: warhead %s has no row in Volume.author, so it inherits its theme baseline and "
                 "will read as the same fireball as its neighbours" % warhead)
    for extra in roster:
        if extra not in warhead_ids():
            fail("motif: ROSTER lists %s, which is not a warhead" % extra)
    if len(set(cases)) != len(cases):
        fail("motif: duplicate warhead cases in BlastMotif")
    if len(set(authored)) != len(authored):
        fail("motif: duplicate warhead cases in Volume.author")
    print("authored warhead looks: %d" % len(set(authored)))

    # The fuse is the effect: ExplosivePackaging must derive it from the motif, not from a constant.
    packaging = read(os.path.join(JAVA, "com/shadowclangaming/warhead/ExplosivePackaging.java"))
    if "BlastMotif.durationTicks" not in packaging:
        fail("fuses must be derived from the warhead's effect duration")
    for caller, form in (("block/ExplosiveBlock.java", "Form.BLOCK"),
                         ("entity/GrenadeEntity.java", "Form.GRENADE"),
                         ("entity/WarheadMinecart.java", "Form.MINECART")):
        source = read(os.path.join(JAVA, "com/shadowclangaming/" + caller))
        if "ExplosivePackaging.fuseTicks" not in source:
            fail("%s must schedule the per-warhead fuse (%s)" % (caller, form))
    return len(roster)


def main():
    java_count = check_java()
    json_count = check_json()
    shaders = check_shaders()
    check_world_shaders()
    check_charge_wiring()
    check_fuse_ownership()
    check_mojmap_names()
    check_soul_furnace_spawn_guard()
    check_sky_box_reaches_the_big_ones()
    subscribers = check_event_subscribers()
    check_proxy_box()
    check_glsl_defined()
    check_emp_drain()
    check_shape_authoring()
    check_impact_fx_reachable()
    blocks, items = check_assets()
    check_entities()
    check_particles()
    lang_count = check_lang()
    recipe_count = check_recipes()
    motif_count = check_motifs()

    print("java files: %d" % java_count)
    print("json files: %d" % json_count)
    print("blocks: %d, items: %d" % (blocks, items))
    print("lang keys: %d" % lang_count)
    print("recipes: %d" % recipe_count)
    print("shader chains: %d" % shaders)
    print("event subscriber classes: %d" % subscribers)
    print("warhead motifs: %d" % motif_count)
    if problems:
        print("\nFAIL (%d problem(s)):" % len(problems))
        for problem in problems:
            print("  - " + problem)
        return 1
    print("\nPASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
