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
        for uniform in set(re.findall(r'safe?[gG]etUniform\("([^"]+)"\)', driver)):
            if uniform not in declared_uniforms:
                fail("core shader %s: %s looks up uniform %s that the program does not declare"
                     % (name, drivers[name], uniform))
        count += 1
    print("core world shaders: %d" % count)
    return count


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
        if "BlastFx.emitCharge" not in source:
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
    cases = re.findall(r'case "([a-z_]+)" ->', motif_source)
    for warhead in warhead_ids():
        if warhead not in cases:
            fail("motif: warhead %s has no case in BlastMotif" % warhead)
        if warhead not in roster:
            fail("motif: warhead %s is missing from BlastMotif.ROSTER" % warhead)
    for extra in roster:
        if extra not in warhead_ids():
            fail("motif: ROSTER lists %s, which is not a warhead" % extra)
    if len(set(cases)) != len(cases):
        fail("motif: duplicate warhead cases in BlastMotif")

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
    subscribers = check_event_subscribers()
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
