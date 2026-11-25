-- barrage.lua -- the carpet maths, in Lua.
--
-- WHAT THIS IS
--   The reference implementation of the barrage grid: given a blast radius and an area, work out where
--   every shot goes so neighbouring craters just overlap. Run it with the terminal's Lua engine:
--
--       script barrage            (prints the plan for a 12-block warhead over a 64-block area)
--       script barrage 12 64      (same, with your numbers)
--
--   In-game the same arithmetic is built into the shell as "barrage <x> <z> [extent]", and the bundled
--   program "barrage" (prog run barrage) arms the channel and calls it. This file exists because the
--   grid is the part people want to copy into their own programs, and a Lua function is easier to lift
--   than a Java one.
--
-- THE MATHS, IN ONE PARAGRAPH
--   Put shots on a square grid, spacing s apart. The worst-covered point of a cell is its corner, which
--   is s/sqrt(2) from the nearest shot. For the craters to touch at that point you need
--   s/sqrt(2) <= r, i.e. s = r * sqrt(2) is exact cover with zero overlap. Multiply by (1 - overlap) to
--   pull the shots closer and get the overlap the brief asked for: a little, not a lot, so the carpet
--   looks like one big blast rather than fifty separate ones.
--
--   overlap 0.25 -> s = r * sqrt(2) * 0.75. For r = 12 that is 12.7 blocks.

local sqrt2 = 1.4142135623730951
local OVERLAP = 0.25
local MAX_SHOTS = 4096

--- Spacing between neighbouring shots, in blocks.
function spacing(radius)
    if radius == nil or radius <= 0 then
        return 1
    end
    return math.max(1, radius * sqrt2 * (1 - OVERLAP))
end

--- The plan: a list of {x, z} aim points, centre first.
--
--   cx, cz   centre of the area
--   radius   blast radius of the warhead that will be fired
--   extent   how far from the centre to cover (0 or nil = one cell)
function plan(cx, cz, radius, extent)
    local step = math.floor(spacing(radius) + 0.5)
    local steps = 0
    if extent ~= nil and extent > 0 then
        steps = math.floor(extent / step)
    end
    local shots = {}
    -- Chebyshev rings, so the middle of the target is hit first and the carpet grows outward.
    for ring = 0, steps do
        for i = -ring, ring do
            for j = -ring, ring do
                if math.max(math.abs(i), math.abs(j)) == ring then
                    shots[#shots + 1] = { x = cx + i * step, z = cz + j * step }
                    if #shots >= MAX_SHOTS then
                        return shots
                    end
                end
            end
        end
    end
    return shots
end

--- Split a plan over the launchers that are available, round-robin.
--
--   One pad fires one missile, so "shots per launcher" is really "which launchers fire, and at what".
function distribute(shots, launchers)
    local out = {}
    for i = 1, math.max(1, launchers) do
        out[i] = {}
    end
    for i = 1, #shots do
        local bucket = out[(i - 1) % #out + 1]
        bucket[#bucket + 1] = shots[i]
    end
    return out
end

--- The furthest any aim point sits from the centre, per axis.
local function reach(list)
    local best = 0
    for _, shot in ipairs(list) do
        local r = math.max(math.abs(shot.x), math.abs(shot.z))
        if r > best then
            best = r
        end
    end
    return best
end

local radius = tonumber((...)) or 12
local extent = 64
local shots = plan(0, 0, radius, extent)
local step = math.floor(spacing(radius) + 0.5)

print(string.format("barrage: radius %d, overlap %d%%, spacing %d block(s)",
    radius, math.floor(OVERLAP * 100), step))
print(string.format("  %d shot(s) to cover %d blocks from the centre", #shots, extent))
print(string.format("  widest reach: %d blocks", reach(shots)))

for i = 1, math.min(#shots, 8) do
    print(string.format("  shot %d: %d %d", i, shots[i].x, shots[i].z))
end
if #shots > 8 then
    print(string.format("  ... and %d more", #shots - 8))
end
