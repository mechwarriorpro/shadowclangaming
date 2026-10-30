#!/usr/bin/env python3
"""Regenerates chem/Element.java with the full periodic table, and the matching lang keys.

    python3 tools/gen_elements.py            # writes both files
    python3 tools/gen_elements.py --check    # verify only, write nothing

WHY A GENERATOR. 122 enum entries with eight fields each is too much to hand-type without a
mistake, and the mistakes here are silent: a duplicated symbol breaks StringRepresentable at
class-load, a wrong atomic number is just wrong. Generating from one table means the data can be
asserted on - sequential Z, unique symbols, no Java keyword collisions - before a line of Java
is written.

The 19 entries that already existed keep their EXACT name, symbol, Z, mass, colour, fissile and
fusion-fuel values, parsed out of the current file rather than retyped, so DecompositionTable,
SynthesisTable and every vial already in a world are unaffected.
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
ELEMENT_JAVA = ROOT / "src/main/java/com/shadowclangaming/chem/Element.java"
LANG = ROOT / "src/main/resources/assets/shadowclangaming/lang/en_us.json"

# symbol, name, atomic number, standard atomic mass, category
# Categories drive the vial colour, so a whole group reads as a family at a glance.
ELEMENTS = [
    ("H",  "Hydrogen",       1,   1.008, "nonmetal"),
    ("He", "Helium",         2,   4.003, "noble"),
    ("Li", "Lithium",        3,   6.94,  "alkali"),
    ("Be", "Beryllium",      4,   9.012, "alkaline"),
    ("B",  "Boron",          5,  10.81,  "metalloid"),
    ("C",  "Carbon",         6,  12.011, "nonmetal"),
    ("N",  "Nitrogen",       7,  14.007, "nonmetal"),
    ("O",  "Oxygen",         8,  15.999, "nonmetal"),
    ("F",  "Fluorine",       9,  18.998, "halogen"),
    ("Ne", "Neon",          10,  20.180, "noble"),
    ("Na", "Sodium",        11,  22.990, "alkali"),
    ("Mg", "Magnesium",     12,  24.305, "alkaline"),
    ("Al", "Aluminium",     13,  26.982, "post"),
    ("Si", "Silicon",       14,  28.085, "metalloid"),
    ("P",  "Phosphorus",    15,  30.974, "nonmetal"),
    ("S",  "Sulfur",        16,  32.06,  "nonmetal"),
    ("Cl", "Chlorine",      17,  35.45,  "halogen"),
    ("Ar", "Argon",         18,  39.948, "noble"),
    ("K",  "Potassium",     19,  39.098, "alkali"),
    ("Ca", "Calcium",       20,  40.078, "alkaline"),
    ("Sc", "Scandium",      21,  44.956, "transition"),
    ("Ti", "Titanium",      22,  47.867, "transition"),
    ("V",  "Vanadium",      23,  50.942, "transition"),
    ("Cr", "Chromium",      24,  51.996, "transition"),
    ("Mn", "Manganese",     25,  54.938, "transition"),
    ("Fe", "Iron",          26,  55.845, "transition"),
    ("Co", "Cobalt",        27,  58.933, "transition"),
    ("Ni", "Nickel",        28,  58.693, "transition"),
    ("Cu", "Copper",        29,  63.546, "transition"),
    ("Zn", "Zinc",          30,  65.38,  "transition"),
    ("Ga", "Gallium",       31,  69.723, "post"),
    ("Ge", "Germanium",     32,  72.630, "metalloid"),
    ("As", "Arsenic",       33,  74.922, "metalloid"),
    ("Se", "Selenium",      34,  78.971, "nonmetal"),
    ("Br", "Bromine",       35,  79.904, "halogen"),
    ("Kr", "Krypton",       36,  83.798, "noble"),
    ("Rb", "Rubidium",      37,  85.468, "alkali"),
    ("Sr", "Strontium",     38,  87.62,  "alkaline"),
    ("Y",  "Yttrium",       39,  88.906, "transition"),
    ("Zr", "Zirconium",     40,  91.224, "transition"),
    ("Nb", "Niobium",       41,  92.906, "transition"),
    ("Mo", "Molybdenum",    42,  95.95,  "transition"),
    ("Tc", "Technetium",    43,  98.0,   "transition"),
    ("Ru", "Ruthenium",     44, 101.07,  "transition"),
    ("Rh", "Rhodium",       45, 102.91,  "transition"),
    ("Pd", "Palladium",     46, 106.42,  "transition"),
    ("Ag", "Silver",        47, 107.87,  "transition"),
    ("Cd", "Cadmium",       48, 112.41,  "transition"),
    ("In", "Indium",        49, 114.82,  "post"),
    ("Sn", "Tin",           50, 118.71,  "post"),
    ("Sb", "Antimony",      51, 121.76,  "metalloid"),
    ("Te", "Tellurium",     52, 127.60,  "metalloid"),
    ("I",  "Iodine",        53, 126.90,  "halogen"),
    ("Xe", "Xenon",         54, 131.29,  "noble"),
    ("Cs", "Caesium",       55, 132.91,  "alkali"),
    ("Ba", "Barium",        56, 137.33,  "alkaline"),
    ("La", "Lanthanum",     57, 138.91,  "lanthanide"),
    ("Ce", "Cerium",        58, 140.12,  "lanthanide"),
    ("Pr", "Praseodymium",  59, 140.91,  "lanthanide"),
    ("Nd", "Neodymium",     60, 144.24,  "lanthanide"),
    ("Pm", "Promethium",    61, 145.0,   "lanthanide"),
    ("Sm", "Samarium",      62, 150.36,  "lanthanide"),
    ("Eu", "Europium",      63, 151.96,  "lanthanide"),
    ("Gd", "Gadolinium",    64, 157.25,  "lanthanide"),
    ("Tb", "Terbium",       65, 158.93,  "lanthanide"),
    ("Dy", "Dysprosium",    66, 162.50,  "lanthanide"),
    ("Ho", "Holmium",       67, 164.93,  "lanthanide"),
    ("Er", "Erbium",        68, 167.26,  "lanthanide"),
    ("Tm", "Thulium",       69, 168.93,  "lanthanide"),
    ("Yb", "Ytterbium",     70, 173.05,  "lanthanide"),
    ("Lu", "Lutetium",      71, 174.97,  "lanthanide"),
    ("Hf", "Hafnium",       72, 178.49,  "transition"),
    ("Ta", "Tantalum",      73, 180.95,  "transition"),
    ("W",  "Tungsten",      74, 183.84,  "transition"),
    ("Re", "Rhenium",       75, 186.21,  "transition"),
    ("Os", "Osmium",        76, 190.23,  "transition"),
    ("Ir", "Iridium",       77, 192.22,  "transition"),
    ("Pt", "Platinum",      78, 195.08,  "transition"),
    ("Au", "Gold",          79, 196.97,  "transition"),
    ("Hg", "Mercury",       80, 200.59,  "transition"),
    ("Tl", "Thallium",      81, 204.38,  "post"),
    ("Pb", "Lead",          82, 207.2,   "post"),
    ("Bi", "Bismuth",       83, 208.98,  "post"),
    ("Po", "Polonium",      84, 209.0,   "metalloid"),
    ("At", "Astatine",      85, 210.0,   "halogen"),
    ("Rn", "Radon",         86, 222.0,   "noble"),
    ("Fr", "Francium",      87, 223.0,   "alkali"),
    ("Ra", "Radium",        88, 226.0,   "alkaline"),
    ("Ac", "Actinium",      89, 227.0,   "actinide"),
    ("Th", "Thorium",       90, 232.04,  "actinide"),
    ("Pa", "Protactinium",  91, 231.04,  "actinide"),
    ("U",  "Uranium",       92, 238.03,  "actinide"),
    ("Np", "Neptunium",     93, 237.0,   "actinide"),
    ("Pu", "Plutonium",     94, 244.0,   "actinide"),
    ("Am", "Americium",     95, 243.0,   "actinide"),
    ("Cm", "Curium",        96, 247.0,   "actinide"),
    ("Bk", "Berkelium",     97, 247.0,   "actinide"),
    ("Cf", "Californium",   98, 251.0,   "actinide"),
    ("Es", "Einsteinium",   99, 252.0,   "actinide"),
    ("Fm", "Fermium",      100, 257.0,   "actinide"),
    ("Md", "Mendelevium",  101, 258.0,   "actinide"),
    ("No", "Nobelium",     102, 259.0,   "actinide"),
    ("Lr", "Lawrencium",   103, 266.0,   "actinide"),
    ("Rf", "Rutherfordium",104, 267.0,   "transactinide"),
    ("Db", "Dubnium",      105, 268.0,   "transactinide"),
    ("Sg", "Seaborgium",   106, 269.0,   "transactinide"),
    ("Bh", "Bohrium",      107, 270.0,   "transactinide"),
    ("Hs", "Hassium",      108, 277.0,   "transactinide"),
    ("Mt", "Meitnerium",   109, 278.0,   "transactinide"),
    ("Ds", "Darmstadtium", 110, 281.0,   "transactinide"),
    ("Rg", "Roentgenium",  111, 282.0,   "transactinide"),
    ("Cn", "Copernicium",  112, 285.0,   "transactinide"),
    ("Nh", "Nihonium",     113, 286.0,   "transactinide"),
    ("Fl", "Flerovium",    114, 289.0,   "transactinide"),
    ("Mc", "Moscovium",    115, 290.0,   "transactinide"),
    ("Lv", "Livermorium",  116, 293.0,   "transactinide"),
    ("Ts", "Tennessine",   117, 294.0,   "transactinide"),
    ("Og", "Oganesson",    118, 294.0,   "transactinide"),
]

# Isotopes the tech tree already depends on. They are entries in the same table because a vial of
# U-235 and a vial of U-238 are different items with different half-lives and different uses.
ISOTOPES = [
    ("T",     "Tritium",         1,   3.0, "nonmetal",  144000, False, True),
    ("U235",  "Uranium-235",    92, 235.0, "actinide",  240000, True,  False),
    ("U238",  "Uranium-238",    92, 238.0, "actinide",  240000, False, False),
    ("PU239", "Plutonium-239",  94, 239.0, "actinide",  240000, True,  False),
]

# Gameplay-scaled decay, in ticks. These are NOT real half-lives - U-238 is 4.5 billion years and
# no play session would ever see it decay. They are scaled so that hoarding the dangerous stuff has
# a consequence on a timescale a player actually experiences, and so that the transactinides, which
# really do vanish in seconds, feel appropriately unstable.
HALF_LIFE = {
    "Tc": 72000, "Pm": 72000, "Bi": 240000,
    "Po": 12000, "At": 3000, "Rn": 6000, "Fr": 3000, "Ra": 48000,
    "Ac": 48000, "Th": 240000, "Pa": 72000, "U": 240000, "Np": 120000, "Pu": 240000,
    "Am": 120000, "Cm": 120000, "Bk": 72000, "Cf": 72000,
    "Es": 24000, "Fm": 24000, "Md": 24000, "No": 24000, "Lr": 24000,
}
for _sym in ("Rf", "Db", "Sg", "Bh", "Hs", "Mt", "Ds", "Rg", "Cn",
             "Nh", "Fl", "Mc", "Lv", "Ts", "Og"):
    HALF_LIFE[_sym] = 6000

# Base ARGB per category. Members of a group share a hue and are spread in lightness, so a vial is
# identifiable by family at a glance and still distinct from its neighbours.
CATEGORY_COLOUR = {
    "nonmetal":       0xFFE8F4FF,
    "noble":          0xFFB0E0FF,
    "alkali":         0xFFD9C8FF,
    "alkaline":       0xFFC8E8C0,
    "transition":     0xFFD0D4DA,
    "post":           0xFFB8C4CC,
    "metalloid":      0xFFA8A890,
    "halogen":        0xFFE8E07A,
    "lanthanide":     0xFFE0B8D8,
    "actinide":       0xFF8FE08F,
    "transactinide":  0xFFC0A0E8,
}

JAVA_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
    "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
    "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
    "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
    "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
    "volatile", "while", "var", "yield", "record", "sealed", "permits",
}


def existing_entries():
    """Parse the entries already in Element.java so their values are preserved exactly."""
    text = ELEMENT_JAVA.read_text()
    body = text.split("public enum Element implements StringRepresentable {", 1)[1]
    body = body.split(";", 1)[0]
    found = {}
    # The trailing half-life group is optional so this parses BOTH the 7-argument table that shipped
    # and the 8-argument one this script writes. Without that, re-running the generator on its own
    # output parses zero entries and regenerates the whole table as "new", silently discarding the
    # hand-tuned colours and the fissile/fusion flags - which is exactly what happened.
    pattern = re.compile(
        r'^\s*([A-Z][A-Z0-9_]*)\s*\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*(\d+)\s*,\s*([\d.]+)\s*,'
        r'\s*(0x[0-9A-Fa-f]+)\s*,\s*(true|false)\s*,\s*(true|false)\s*(?:,\s*(-?\d+)\s*)?\)', re.M)
    for m in pattern.finditer(body):
        found[m.group(1)] = {
            "name": m.group(2), "symbol": m.group(3), "z": int(m.group(4)),
            "mass": m.group(5), "colour": m.group(6),
            "fissile": m.group(7) == "true", "fusion": m.group(8) == "true",
            "half": int(m.group(9)) if m.group(9) else 0,
        }
    return found


def category_colour(symbol, category, index_in_category):
    base = CATEGORY_COLOUR[category]
    r, g, b = (base >> 16) & 0xFF, (base >> 8) & 0xFF, base & 0xFF
    # Spread members of a group in lightness. Kept inside [70,250] so no vial goes near-black
    # (invisible against the slot background) or pure white (indistinguishable from an empty one).
    spread = (index_in_category % 6) - 2.5
    step = 11
    r = max(70, min(250, int(r + spread * step)))
    g = max(70, min(250, int(g + spread * step)))
    b = max(70, min(250, int(b + spread * step * 0.6)))
    return 0xFF000000 | (r << 16) | (g << 8) | b


def build():
    old = existing_entries()
    if not old:
        raise SystemExit(
            "REFUSING TO RUN: parsed zero existing entries out of Element.java. Either the file is "
            "not the committed table or the parser regex has drifted from its format. Continuing "
            "would regenerate all 122 entries as new and discard the hand-tuned colours and the "
            "fissile/fusion flags.")
    rows = []
    counters = {}
    for symbol, name, z, mass, category in ELEMENTS:
        const = symbol.upper()
        assert const not in JAVA_KEYWORDS, "enum constant %s is a Java keyword" % const
        idx = counters.get(category, 0)
        counters[category] = idx + 1
        if const in old:
            e = old[const]
            # Preserve everything the shipped table already had.
            rows.append({
                "const": const, "name": e["name"], "symbol": e["symbol"], "z": e["z"],
                "mass": e["mass"], "colour": e["colour"], "fissile": e["fissile"],
                "fusion": e["fusion"], "half": HALF_LIFE.get(symbol, 0), "new": False,
                "isotope": False,
            })
        else:
            rows.append({
                "const": const, "name": name, "symbol": symbol, "z": z,
                "mass": ("%g" % mass), "colour": "0x%08X" % category_colour(symbol, category, idx),
                "fissile": False, "fusion": False, "half": HALF_LIFE.get(symbol, 0), "new": True,
                "isotope": False,
            })
    for const, name, z, mass, category, half, fissile, fusion in ISOTOPES:
        if const in old:
            e = old[const]
            rows.append({
                "const": const, "name": e["name"], "symbol": e["symbol"], "z": e["z"],
                "mass": e["mass"], "colour": e["colour"], "fissile": e["fissile"],
                "fusion": e["fusion"], "half": half, "new": False, "isotope": True,
            })
        else:
            rows.append({
                "const": const, "name": name, "symbol": symbol, "z": z, "mass": ("%g" % mass),
                "colour": "0x%08X" % category_colour(symbol, category, 0),
                "fissile": fissile, "fusion": fusion, "half": half, "new": True, "isotope": True,
            })
    return rows, old


def validate(rows):
    problems = []
    elements = [r for r in rows if not r["isotope"]]
    zs = sorted(r["z"] for r in elements)
    if zs != list(range(1, 119)):
        missing = sorted(set(range(1, 119)) - set(zs))
        dupes_z = sorted({z for z in zs if zs.count(z) > 1})
        problems.append("atomic numbers not exactly 1-118 once each; missing=%s duplicated=%s"
                        % (missing, dupes_z))
    consts = [r["const"] for r in rows]
    dupes = sorted({c for c in consts if consts.count(c) > 1})
    if dupes:
        problems.append("duplicate enum constants: %s" % dupes)
    names = [r["name"] for r in rows]
    dupe_names = sorted({n for n in names if names.count(n) > 1})
    if dupe_names:
        problems.append("duplicate display names: %s" % dupe_names)
    for r in rows:
        if r["half"] < 0:
            problems.append("negative half-life on %s" % r["const"])
    return problems


HEADER = '''package com.shadowclangaming.chem;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The periodic table, as this mod understands it: all 118 elements, plus the four isotopes the tech
 * tree cares about as their own entries.
 *
 * IMPORTANT: this is OUR table, written from scratch. Invented mechanics that happen to resemble
 * older chemistry mods are fine; copied code and art are not. Nothing here comes from any other mod.
 * The atomic numbers and standard atomic weights are real-world facts, which are not anybody's
 * intellectual property; the colours, the half-life scaling and the balance are ours.
 *
 * THIS FILE IS GENERATED by tools/gen_elements.py. Edit the table there, not here - the generator
 * asserts that atomic numbers run 1-118 with no gaps and that no constant collides with a Java
 * keyword, which is easy to get wrong by hand across 122 entries.
 *
 * Adding an element = one row in the generator + a lang entry ("element.shadowclangaming.&lt;name&gt;")
 * + (optional) putting it in DecompositionTable / SynthesisTable.
 */
public enum Element implements StringRepresentable {
'''

FOOTER = '''
    public static final Codec<Element> CODEC = StringRepresentable.fromEnum(Element::values);
    public static final StreamCodec<ByteBuf, Element> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(Element::byName, Element::getSerializedName);

    private final String displayName;
    private final String symbol;
    private final int atomicNumber;
    private final double atomicMass;
    private final int colour;
    private final boolean fissile;
    private final boolean fusionFuel;
    private final int halfLifeTicks;

    Element(String displayName, String symbol, int atomicNumber, double atomicMass,
            int colour, boolean fissile, boolean fusionFuel, int halfLifeTicks) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.atomicNumber = atomicNumber;
        this.atomicMass = atomicMass;
        this.colour = colour;
        this.fissile = fissile;
        this.fusionFuel = fusionFuel;
        this.halfLifeTicks = halfLifeTicks;
    }

    public String displayName() {
        return displayName;
    }

    public String symbol() {
        return symbol;
    }

    public int atomicNumber() {
        return atomicNumber;
    }

    public double atomicMass() {
        return atomicMass;
    }

    /** 0xAARRGGBB - used for laser colours, vial tints, potion effects, later GUI. */
    public int colour() {
        return colour;
    }

    /** Can this split in a chain reaction? Used by warhead yield maths (M1). */
    public boolean fissile() {
        return fissile;
    }

    /** Can this fuse? Used by fusion warheads. */
    public boolean fusionFuel() {
        return fusionFuel;
    }

    /**
     * Gameplay half-life in ticks, or 0 for a stable element.
     *
     * <p>This is NOT a real half-life. U-238 is 4.5 billion years and the transactinides are seconds;
     * neither is a number a play session can use. These are scaled so that hoarding the dangerous
     * things has a consequence on a timescale a player actually experiences.
     */
    public int halfLifeTicks() {
        return halfLifeTicks;
    }

    /** Does this decay at all? Drives vial glow, exposure effects and the decay ticker. */
    public boolean radioactive() {
        return halfLifeTicks > 0;
    }

    /** Translation key for this element's display name. */
    public String translationKey() {
        return "element.shadowclangaming." + getSerializedName();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Element byName(String name) {
        for (Element element : values()) {
            if (element.getSerializedName().equalsIgnoreCase(name)) {
                return element;
            }
        }
        return H;
    }
}
'''


def render(rows):
    out = [HEADER]
    out.append("    //  name                  symbol    Z     mass    colour      fissile fusion  halfLife(ticks)")
    for r in rows:
        # Pad the QUOTED TOKEN, never the text inside it. "%-16s" applied to the raw name emits
        # "Hydrogen        " - trailing spaces inside the string literal, which changes what
        # displayName() and symbol() actually return.
        name_lit = '"%s",' % r["name"]
        sym_lit = '"%s",' % r["symbol"]
        out.append("    %-7s(%-20s %-8s %3d, %7s, %s, %-5s, %-5s, %6d)," % (
            r["const"], name_lit, sym_lit, r["z"], r["mass"], r["colour"],
            str(r["fissile"]).lower(), str(r["fusion"]).lower(), r["half"]))
    out.append("    ;")
    out.append(FOOTER)
    return "\n".join(out)


def main():
    check_only = "--check" in sys.argv
    rows, old = build()
    problems = validate(rows)
    print("entries: %d (118 elements + %d isotopes), %d preserved from the shipped table, %d new"
          % (len(rows), len(ISOTOPES), sum(1 for r in rows if not r["new"]),
             sum(1 for r in rows if r["new"])))
    radioactive = [r["const"] for r in rows if r["half"] > 0]
    print("radioactive entries: %d" % len(radioactive))
    if problems:
        for p in problems:
            print("PROBLEM: %s" % p)
        return 1
    print("validation: atomic numbers complete 1-118, no duplicate constants or names")

    if check_only:
        return 0

    # Verify every preserved entry really did keep its values.
    rendered = render(rows)
    for const, e in old.items():
        row = next(r for r in rows if r["const"] == const)
        for key in ("name", "symbol", "z"):
            assert str(row[key]) == str(e[key]), "%s.%s changed" % (const, key)
        assert row["mass"] == e["mass"], "%s.mass changed: %s -> %s" % (const, e["mass"], row["mass"])
        assert int(row["colour"], 16) == int(e["colour"], 16), "%s.colour changed" % const
        assert row["fissile"] == e["fissile"], "%s.fissile changed" % const
        assert row["fusion"] == e["fusion"], "%s.fusion changed" % const
    print("preservation check: all %d shipped entries unchanged" % len(old))

    ELEMENT_JAVA.write_text(rendered)
    print("wrote %s" % ELEMENT_JAVA.relative_to(ROOT))

    # Re-parse what was actually written and compare it against the values that shipped. Checking
    # the in-memory rows is not enough - a formatting bug can corrupt the file while leaving the
    # rows correct, which is exactly what happened the first time this ran.
    written = existing_entries()
    for const, e in old.items():
        w = written.get(const)
        if w is None:
            raise SystemExit("REGRESSION: %s is missing from the written file" % const)
        for key in ("name", "symbol"):
            if w[key] != e[key]:
                raise SystemExit("REGRESSION: %s.%s %r -> %r" % (const, key, e[key], w[key]))
        if w["z"] != e["z"] or w["mass"] != e["mass"]:
            raise SystemExit("REGRESSION: %s numeric changed" % const)
        if int(w["colour"], 16) != int(e["colour"], 16):
            raise SystemExit("REGRESSION: %s.colour changed" % const)
        if w["fissile"] != e["fissile"] or w["fusion"] != e["fusion"]:
            raise SystemExit("REGRESSION: %s flags changed" % const)
    if len(written) != len(rows):
        raise SystemExit("REGRESSION: wrote %d entries, re-parsed %d" % (len(rows), len(written)))
    print("written-file check: all %d entries re-parse with exact names and symbols" % len(written))

    # Lang: insert the new element keys next to the existing ones, keeping the file sorted enough
    # to diff. Unknown keys in the file are left alone - they belong to other enums.
    import json
    import collections
    lang = json.loads(LANG.read_text(), object_pairs_hook=collections.OrderedDict)
    added = 0
    out = collections.OrderedDict()
    for key, value in lang.items():
        out[key] = value
        if key == "element.shadowclangaming.h":
            for r in rows:
                lk = "element.shadowclangaming." + r["const"].lower()
                if lk not in lang:
                    out[lk] = r["name"]
                    added += 1
    LANG.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n")
    print("lang: %d element keys added (%d total element keys)"
          % (added, sum(1 for k in out if k.startswith("element.shadowclangaming."))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
