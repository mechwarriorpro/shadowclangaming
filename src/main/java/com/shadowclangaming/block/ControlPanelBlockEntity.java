package com.shadowclangaming.block;

import com.shadowclangaming.launcher.LauncherLevel;
import com.shadowclangaming.launcher.LauncherModule;
import com.shadowclangaming.registry.SCGBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;

/**
 * The control panel: the launcher's brain, and the thing a terminal talks to.
 *
 * It knows three things:
 *   tier         1..5, raised with Upgrade Kits. The launcher is capped by it (see LauncherLevel).
 *   target       where the pad should fire, optionally in ANOTHER DIMENSION
 *   armed        whether the pad will accept a launch order at all
 *
 * The panel is also the module carrier: an interdimensional module bolted to the panel is what makes
 * the whole launcher able to shoot through the floor of the world.
 */
public class ControlPanelBlockEntity extends BlockEntity implements com.shadowclangaming.network.ScgNet.ConsoleHost {

    private int tier = 1;
    private boolean armed = true;
    private BlockPos target;
    private ResourceKey<Level> targetDimension;
    private final Set<LauncherModule> modules = EnumSet.noneOf(LauncherModule.class);
    /** The pad this panel is wired to. Set the first time a pad is clicked with a Tracker or chip. */
    private BlockPos linkedPad;

    public ControlPanelBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.CONTROL_PANEL.get(), pos, state);
    }

    // ------------------------------------------------------------------ console host

    /**
     * The panel's command line, as typed into the cockpit screen.
     *
     *   status            tier, arm state, pad, target, modules
     *   arm / disarm      the safety, said out loud
     *   target x y z      aim point (~ relative to the player, like a teleport)
     *   link              link the pad you are looking at (24 blocks)
     *   launch            fire the linked pad at the target, if armed
     *   tier n            read or raise the panel's tier (never lowers it)
     */
    @Override
    public java.util.List<String> runConsoleCommand(net.minecraft.server.level.ServerLevel level,
                                                    net.minecraft.server.level.ServerPlayer player,
                                                    String line) {
        String[] parts = line.trim().split("\\s+");
        String verb = parts[0].toLowerCase(java.util.Locale.ROOT);
        java.util.List<String> out = new java.util.ArrayList<>();
        switch (verb) {
            case "status" -> out.addAll(ControlPanelBlock.status(level, this));
            case "arm" -> {
                setArmed(true);
                out.add("panel ARMED");
            }
            case "disarm", "safe" -> {
                setArmed(false);
                out.add("panel disarmed");
            }
            case "target" -> {
                if (parts.length < 4) {
                    out.add("! usage: target <x> <y> <z>");
                    break;
                }
                try {
                    BlockPos aim = new BlockPos(
                            coordinate(parts[1], player.getX()),
                            coordinate(parts[2], player.getY()),
                            coordinate(parts[3], player.getZ()));
                    setTarget(level.dimension(), aim);
                    out.add("target set to " + aim.getX() + " " + aim.getY() + " " + aim.getZ());
                } catch (NumberFormatException error) {
                    out.add("! target expects three numbers");
                }
            }
            case "link" -> {
                var hit = player.pick(24.0D, 1.0F, false);
                if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit
                        && level.getBlockEntity(blockHit.getBlockPos())
                        instanceof LaunchPadBlockEntity launchPad) {
                    linkPad(launchPad.getBlockPos());
                    out.add("linked pad " + launchPad.getBlockPos().getX() + " "
                            + launchPad.getBlockPos().getY() + " " + launchPad.getBlockPos().getZ());
                } else {
                    out.add("! look at a launch pad (within 24 blocks) and run link");
                }
            }
            case "launch", "fire" -> {
                if (!armed) {
                    out.add("! panel is disarmed");
                    break;
                }
                if (linkedPad == null || !(level.getBlockEntity(linkedPad) instanceof LaunchPadBlockEntity pad)) {
                    out.add("! no pad linked - run link while looking at one");
                    break;
                }
                BlockPos aim = target != null ? target
                        : BlockPos.containing(player.pick(512.0D, 1.0F, false).getLocation());
                out.add(pad.fireAt(aim) ? "launching at " + aim.getX() + " " + aim.getY() + " " + aim.getZ()
                        : "! the pad could not fire (empty or reloading)");
            }
            case "freq", "channel", "split" -> {
                // Forwarded to the pad: a channel and a split altitude live on the pad and its missile, and
                // duplicating them on the panel would give two places to disagree.
                if (linkedPad == null || !(level.getBlockEntity(linkedPad) instanceof LaunchPadBlockEntity pad)) {
                    out.add("! no pad linked - run link while looking at one");
                    break;
                }
                String rest = line.substring(verb.length()).trim();
                out.addAll(pad.runConsoleCommand(level, player, verb + (rest.isEmpty() ? "" : " " + rest)));
            }
            case "tier" -> {
                if (parts.length > 1) {
                    try {
                        int wanted = Integer.parseInt(parts[1]);
                        if (wanted > tier) {
                            setTier(wanted);
                        }
                    } catch (NumberFormatException error) {
                        out.add("! tier expects a number");
                        break;
                    }
                }
                out.add("panel tier " + tier + " (max " + LauncherLevel.MAX + ")");
            }
            default -> out.add("! unknown command '" + verb
                    + "' - try status, arm, disarm, target, link, launch, tier, freq, split");
        }
        return out;
    }

    private static int coordinate(String text, double origin) {
        if (text.startsWith("~")) {
            String rest = text.substring(1);
            return (int) Math.floor(origin + (rest.isEmpty() ? 0.0D : Double.parseDouble(rest)));
        }
        return (int) Math.floor(Double.parseDouble(text));
    }

    // ------------------------------------------------------------------ menu view

    /** Live numbers for the control panel screen. */
    public net.minecraft.world.inventory.ContainerData data() {
        return new net.minecraft.world.inventory.ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> tier;
                    case 1 -> armed ? 1 : 0;
                    case 2 -> target == null ? 0 : 1;
                    case 3 -> target == null ? 0 : target.getX();
                    case 4 -> target == null ? 0 : target.getY();
                    case 5 -> target == null ? 0 : target.getZ();
                    case 6 -> linkedPad == null ? 0 : 1;
                    case 7 -> linkedPad == null ? 0 : linkedPad.getX();
                    case 8 -> linkedPad == null ? 0 : linkedPad.getY();
                    case 9 -> linkedPad == null ? 0 : linkedPad.getZ();
                    case 10 -> {
                        int mask = 0;
                        for (LauncherModule module : modules) {
                            mask |= 1 << module.ordinal();
                        }
                        yield mask;
                    }
                    case 11 -> targetDimension == null ? 0 : 1;
                    case 12 -> LauncherLevel.MIN;
                    case 13 -> LauncherLevel.MAX;
                    // 14: the linked pad's channel, so the panel screen can name the silo it is wired to.
                    case 14 -> level != null && linkedPad != null
                            && level.getBlockEntity(linkedPad) instanceof LaunchPadBlockEntity pad
                            ? pad.frequency()
                            : com.shadowclangaming.computer.Channels.NONE;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // read-only
            }

            @Override
            public int getCount() {
                return 15;
            }
        };
    }

    public int tier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = Math.max(LauncherLevel.MIN, Math.min(LauncherLevel.MAX, tier));
        setChanged();
    }

    public boolean armed() {
        return armed;
    }

    public void setArmed(boolean armed) {
        this.armed = armed;
        setChanged();
    }

    public BlockPos target() {
        return target;
    }

    public ResourceKey<Level> targetDimension() {
        return targetDimension;
    }

    public void setTarget(ResourceKey<Level> dimension, BlockPos target) {
        this.targetDimension = dimension;
        this.target = target;
        setChanged();
    }

    public BlockPos linkedPad() {
        return linkedPad;
    }

    public void linkPad(BlockPos pad) {
        this.linkedPad = pad;
        setChanged();
    }

    public Set<LauncherModule> modules() {
        return Set.copyOf(modules);
    }

    public boolean has(LauncherModule module) {
        return modules.contains(module);
    }

    public void install(LauncherModule module) {
        modules.add(module);
        setChanged();
    }

    /** Is the target in a different dimension than the panel? */
    public boolean targetIsInterdimensional() {
        return target != null && targetDimension != null && level != null
                && !targetDimension.equals(level.dimension());
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Tier", tier);
        tag.putBoolean("Armed", armed);
        if (target != null) {
            tag.putLong("Target", target.asLong());
        }
        if (targetDimension != null) {
            tag.putString("TargetDimension", targetDimension.location().toString());
        }
        if (linkedPad != null) {
            tag.putLong("LinkedPad", linkedPad.asLong());
        }
        ListTag moduleList = new ListTag();
        for (LauncherModule module : modules) {
            moduleList.add(StringTag.valueOf(module.id()));
        }
        tag.put("Modules", moduleList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tier = Math.max(1, Math.min(LauncherLevel.MAX, tag.getInt("Tier")));
        armed = !tag.contains("Armed") || tag.getBoolean("Armed");
        target = tag.contains("Target") ? BlockPos.of(tag.getLong("Target")) : null;
        targetDimension = tag.contains("TargetDimension")
                ? ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(tag.getString("TargetDimension")))
                : null;
        linkedPad = tag.contains("LinkedPad") ? BlockPos.of(tag.getLong("LinkedPad")) : null;
        modules.clear();
        ListTag moduleList = tag.getList("Modules", Tag.TAG_STRING);
        for (int i = 0; i < moduleList.size(); i++) {
            modules.add(LauncherModule.byId(moduleList.getString(i)));
        }
    }
}
