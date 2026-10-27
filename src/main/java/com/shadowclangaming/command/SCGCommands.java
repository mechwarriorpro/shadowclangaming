package com.shadowclangaming.command;

import com.shadowclangaming.perf.StrategicBlasts;
import com.shadowclangaming.warhead.Funding;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator tooling for a "money is power" server.
 *
 *   /shadowclangaming economy generate   - write/refresh EconomyCraft prices for every item in every mod
 *   /shadowclangaming economy status     - where the price file is and how many entries it has
 *
 *   /shadowclangaming power <player>          - how many units a player has converted out of FE
 *   /shadowclangaming power reset <player>    - clear that counter (gives them the cheap curve again)
 *   /shadowclangaming power resetall          - clear every player's counter
 *
 *   /shadowclangaming curse <player>       - Creeping Evil level, time left, necklace state
 *   /shadowclangaming curse cure <player>  - break the curse (restores hearts and chalk access)
 *
 *   /shadowclangaming mana <player>        - current / max, and where the max came from
 *   /shadowclangaming mana <player> reset  - forget a player's pool (back to 100 base, full)
 *   /shadowclangaming mana <player> add <n>     - raise that player's MAXIMUM by n, then fill
 *   /shadowclangaming mana <player> remove <n>  - lower the MAXIMUM by n (never below the 100 base)
 *   /shadowclangaming mana <player> set <n>     - set the MAXIMUM to exactly n, then fill
 *
 *   ...and every one of those also runs on YOURSELF with the player left out:
 *   /shadowclangaming mana add 500, /shadowclangaming mana set 5000, /shadowclangaming mana reset
 *
 * The number typed is MAXIMUM MANA - the figure the HUD and the inscription table show - not the
 * hidden "growth" term. The 100 base and the Mana Capacity enchantment are already folded in, so a
 * player told "set 5000" sees 5000 on the bar even if they are wearing gear.
 *
 *   /shadowclangaming field list|clear  - live Bulwark domes
 *   /shadowclangaming plague list       - carriers, and the worst generation out there
 *   /shadowclangaming plague cure <player> | clear
 *
 *   /shadowclangaming scar list          - every recorded blast scar, biggest first
 *   /shadowclangaming scar clear         - remove ALL scars (chunks already wiped stay wiped)
 *   /shadowclangaming scar clear <index> - remove one scar
 *
 * Scar clearing cannot restore terrain - the blocks are gone and the game has no record of what was
 * there. It only stops FUTURE chunk loads inside that sphere from being wiped. That distinction is
 * important: it is an "stop the bleeding" tool, not an undo button.
 */
public final class SCGCommands {

    /**
     * Whether the volumetric raymarcher is actually drawing, published by the client at startup.
     *
     * <p>A supplier rather than a direct call because {@code VolumetricFx} pulls in
     * {@code net.minecraft.client}, which does not exist on a dedicated server - and this class is loaded
     * on both sides. Every other client reference in this mod lives under {@code client/} for the same
     * reason; the client publishes, common code only reads.
     */
    public static volatile java.util.function.Supplier<String> volumetricStatus;

    /** Published from the client at mod construction, same pattern as {@link #volumetricStatus}. */
    public static volatile Runnable volumetricProbe;

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("shadowclangaming")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("economy")
                        .then(Commands.literal("status").executes(SCGCommands::economyStatus))
                        .then(Commands.literal("generate")
                                .executes(context -> economyGenerate(context, false))
                                .then(Commands.literal("force")
                                        .executes(context -> economyGenerate(context, true))))
                        // Why does this one item cost what it costs? Prints the recipe the price came
                        // from, which is the only honest way to debug a shop full of flat prices.
                        .then(Commands.literal("explain")
                                .then(Commands.argument("item",
                                                net.minecraft.commands.arguments.item.ItemArgument.item(
                                                        event.getBuildContext()))
                                        .executes(SCGCommands::economyExplain))))
                .then(Commands.literal("power")
                        .then(Commands.literal("resetall").executes(SCGCommands::powerResetAll))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(SCGCommands::powerShow)
                                .then(Commands.literal("reset").executes(SCGCommands::powerReset))))
                .then(Commands.literal("mana")
                        // Self forms: the player argument left out means "me".
                        .executes(SCGCommands::manaSelf)
                        .then(Commands.literal("reset").executes(SCGCommands::manaResetSelf))
                        .then(manaAmountBranch("add", 1))
                        .then(manaAmountBranch("remove", -1))
                        .then(manaAmountBranch("set", 0))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(SCGCommands::manaShow)
                                .then(Commands.literal("reset").executes(SCGCommands::manaReset))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("amount", MANA_AMOUNT)
                                                .executes(context -> manaAdjust(context, 1))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("amount", MANA_AMOUNT)
                                                .executes(context -> manaAdjust(context, -1))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("amount", MANA_AMOUNT)
                                                .executes(SCGCommands::manaSet)))))
                .then(Commands.literal("curse")
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(SCGCommands::curseShow)
                                .then(Commands.literal("cure").executes(SCGCommands::curseCure))))
                .then(Commands.literal("field")
                        .then(Commands.literal("list").executes(SCGCommands::fieldList))
                        .then(Commands.literal("clear").executes(SCGCommands::fieldClear)))
                .then(Commands.literal("plague")
                        .then(Commands.literal("list").executes(SCGCommands::plagueList))
                        .then(Commands.literal("clear").executes(SCGCommands::plagueClear))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .then(Commands.literal("cure").executes(SCGCommands::plagueCure))))
                .then(Commands.literal("fx")
                        .then(Commands.literal("status").executes(SCGCommands::fxStatus))
                        .then(Commands.literal("motifs").executes(SCGCommands::fxMotifs))
                        .then(Commands.literal("probe").executes(SCGCommands::fxProbe))
                        .then(Commands.literal("fuses").executes(SCGCommands::fxFuses))
                        .then(Commands.literal("test")
                                .executes(context -> fxTest(context, "flash"))
                                .then(Commands.argument("style", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(context -> fxTest(context,
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(
                                                        context, "style"))))))
                .then(Commands.literal("scar")
                        .then(Commands.literal("list").executes(SCGCommands::list))
                        .then(Commands.literal("clear")
                                .executes(context -> clearAll(context))
                                .then(Commands.argument("index", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                        .executes(SCGCommands::clearOne)))));
    }

    private static int economyStatus(CommandContext<CommandSourceStack> context) {
        if (!com.shadowclangaming.compat.economy.EconomyBridge.isInstalled()) {
            context.getSource().sendFailure(Component.literal("EconomyCraft is not installed."));
            return 0;
        }
        var file = com.shadowclangaming.compat.economy.EconomyBridge.pricesFile();
        String state = java.nio.file.Files.exists(file) ? "exists" : "missing (run generate)";
        context.getSource().sendSuccess(() -> Component.literal(
                "EconomyCraft price file: " + file.toAbsolutePath() + " [" + state + "]"), false);
        return 1;
    }

    private static int manaShow(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return showMana(context, net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player"));
    }

    /** The readout both {@code /scg mana} and {@code /scg mana <player>} print. */
    private static int showMana(CommandContext<CommandSourceStack> context,
                                net.minecraft.server.level.ServerPlayer player) {
        long current = com.shadowclangaming.magic.ManaApi.current(player);
        long capacity = com.shadowclangaming.magic.ManaApi.capacity(player);
        long base = com.shadowclangaming.magic.ManaPool.BASE_CAPACITY;
        long gear = com.shadowclangaming.magic.ManaApi.enchantBonus(player);
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "%s: mana %,d / %,d  (base %,d + growth %,d + gear %,d)",
                player.getGameProfile().getName(), current, capacity, base,
                Math.max(0L, capacity - base - gear), gear)),
                false);

        // No natural regeneration: say where this player's mana actually comes from right now.
        long now = context.getSource().getLevel().getGameTime();
        long amethyst = com.shadowclangaming.magic.ManaSources.amethystSources(player, now);
        long pactLeft = com.shadowclangaming.magic.ManaApi.pactExpiresAt(context.getSource().getServer(),
                player.getUUID()) - now;
        boolean evil = com.shadowclangaming.magic.ManaApi.isEvil(context.getSource().getServer(), player.getUUID());

        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "  sources: amethyst %d in range (+%d/s) | pact %s | chalk %s",
                amethyst,
                amethyst > 0 ? com.shadowclangaming.magic.ManaSources.AMETHYST_MANA_PER_SECOND : 0,
                pactLeft > 0 ? String.format("%.1fs left (+%d/s)", pactLeft / 20.0D,
                        com.shadowclangaming.magic.ManaSources.PACT_MANA_PER_SECOND) : "none",
                evil ? "REFUSED (blood pact)" : "available")), false);
        return (int) Math.min(Integer.MAX_VALUE, current);
    }

    // ---- maximum mana: the QoL commands -------------------------------------------------------
    //
    // WHY THESE EXIST: capacity is permanent, uncapped growth driven by casting, and there was no way
    // back. A player who wanted to test a 6 552-mana spell either ground for days or edited NBT. These
    // four verbs are the missing dial: raise someone, take it away, set an exact figure, start over.

    /**
     * The amount argument: 1 up to a quadrillion. Brigadier argument types are stateless, so the four
     * registrations share this one instance.
     *
     * The floor is 1, not 0 - a maximum of zero mana is not a playable state - and the ceiling is only
     * there so a fat-fingered number cannot overflow the arithmetic on the way to the pool.
     */
    private static final com.mojang.brigadier.arguments.LongArgumentType MANA_AMOUNT =
            com.mojang.brigadier.arguments.LongArgumentType.longArg(1L, 1_000_000_000_000_000L);

    /** {@code mana add|remove|set <amount>} acting on the command sender. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> manaAmountBranch(
            String literal, int sign) {
        return Commands.literal(literal)
                .then(Commands.argument("amount", MANA_AMOUNT)
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            return sign == 0 ? applyManaSet(context, player)
                                    : applyManaAdjust(context, player, sign);
                        }));
    }

    private static int manaSelf(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return showMana(context, context.getSource().getPlayerOrException());
    }

    /** {@code mana <player> add|remove <amount>} */
    private static int manaAdjust(CommandContext<CommandSourceStack> context, int sign)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return applyManaAdjust(context, net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player"), sign);
    }

    /** {@code mana <player> set <amount>} */
    private static int manaSet(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return applyManaSet(context, net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player"));
    }

    private static int applyManaAdjust(CommandContext<CommandSourceStack> context,
                                       net.minecraft.server.level.ServerPlayer player, int sign) {
        long amount = com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "amount");
        var server = context.getSource().getServer();
        long before = com.shadowclangaming.magic.ManaApi.capacity(player);
        long growth = com.shadowclangaming.magic.ManaApi.growth(server, player.getUUID());
        long next = sign > 0
                ? com.shadowclangaming.magic.ManaApi.addGrowthSaturating(growth, amount)
                : Math.max(0L, growth - amount);
        com.shadowclangaming.magic.ManaApi.setGrowth(server, player.getUUID(), next);
        if (sign > 0) {
            // Raising a maximum and then showing an empty bar would be a strange thing to do.
            com.shadowclangaming.magic.ManaApi.refill(player);
        } else {
            com.shadowclangaming.magic.ManaApi.clampToMaximum(player);
        }
        long after = com.shadowclangaming.magic.ManaApi.capacity(player);
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "%s: maximum mana %,d -> %,d%s", player.getGameProfile().getName(), before, after,
                sign > 0 ? " (filled)" : " (clamped)")), true);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, after));
    }

    private static int applyManaSet(CommandContext<CommandSourceStack> context,
                                    net.minecraft.server.level.ServerPlayer player) {
        long amount = com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "amount");
        var server = context.getSource().getServer();
        long before = com.shadowclangaming.magic.ManaApi.capacity(player);
        // "set 5000" means 5000, gear and all - so the growth figure is whatever gets us there.
        long growth = com.shadowclangaming.magic.ManaApi.growthFor(player, amount);
        com.shadowclangaming.magic.ManaApi.setGrowth(server, player.getUUID(), growth);
        com.shadowclangaming.magic.ManaApi.refill(player);
        long after = com.shadowclangaming.magic.ManaApi.capacity(player);
        String note = after < amount
                ? String.format(" (gear already adds %,d, so %s is the floor)",
                        com.shadowclangaming.magic.ManaApi.enchantBonus(player), after)
                : "";
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "%s: maximum mana %,d -> %,d%s", player.getGameProfile().getName(), before, after, note)),
                true);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, after));
    }

    /** {@code mana reset} on the sender. */
    private static int manaResetSelf(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return resetMana(context, context.getSource().getPlayerOrException());
    }

    private static int resetMana(CommandContext<CommandSourceStack> context,
                                 net.minecraft.server.level.ServerPlayer player) {
        boolean removed = com.shadowclangaming.magic.ManaPool.get(context.getSource().getLevel())
                .reset(player.getUUID());
        com.shadowclangaming.magic.ManaApi.clampToMaximum(player);
        context.getSource().sendSuccess(() -> Component.literal(removed
                ? "Reset mana for " + player.getGameProfile().getName() + " (100 base, full)."
                : player.getGameProfile().getName() + " had no mana record."), true);
        return removed ? 1 : 0;
    }

    private static int manaReset(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return resetMana(context, net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player"));
    }

    private static int curseShow(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
        String description = com.shadowclangaming.magic.CreepingEvil.describe(
                context.getSource().getServer(), player);
        context.getSource().sendSuccess(() -> Component.literal(
                player.getGameProfile().getName() + ": " + description), false);
        return 1;
    }

    private static int curseCure(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
        boolean cured = com.shadowclangaming.magic.CreepingEvil.breakCurse(player);
        context.getSource().sendSuccess(() -> Component.literal(cured
                ? "Curse broken for " + player.getGameProfile().getName()
                + " (hearts restored, chalk reopened)."
                : player.getGameProfile().getName() + " was not cursed."), true);
        return cured ? 1 : 0;
    }

    /**
     * {@code /scg fx status} - what the effects system is doing, on this side.
     *
     * The interesting question after "explosives have no shaders" is *which* of the three degradation steps
     * a client is on, so the answer names the styles, the loaded chains and the config.
     */
    private static int fxStatus(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        var styles = com.shadowclangaming.warhead.BlastStyle.values();
        source.sendSuccess(() -> Component.literal("Blast effect styles: " + styles.length), false);
        source.sendSuccess(() -> Component.literal(
                "  shader packets: " + com.shadowclangaming.config.SCGConfig.fxPackets()
                        + "   range: " + com.shadowclangaming.config.SCGConfig.fxRange() + " blocks"),
                false);
        source.sendSuccess(() -> Component.literal(
                "  particles x" + com.shadowclangaming.config.SCGConfig.particleScale()
                        + "   blast threads: " + com.shadowclangaming.config.SCGConfig.blastThreads()),
                false);
        source.sendSuccess(() -> Component.literal(
                "  client: shaders " + com.shadowclangaming.config.SCGConfig.shadersEnabled()
                        + ", strength " + com.shadowclangaming.config.SCGConfig.shaderStrength()
                        + ", shake " + com.shadowclangaming.config.SCGConfig.screenShake()
                        + ", overlay fallback " + com.shadowclangaming.config.SCGConfig.fallbackOverlay()),
                false);
        java.util.function.Supplier<String> raymarcher = volumetricStatus;
        source.sendSuccess(() -> Component.literal("  volumetric raymarcher: " + (raymarcher == null
                ? "not reported - this command is running on a dedicated server"
                : raymarcher.get())), false);
        for (var style : styles) {
            source.sendSuccess(() -> Component.literal(String.format("  %-10s chain %s  colour #%06X  shake %.2f",
                    style.id(), style.shader(), style.colour() & 0xFFFFFF, style.shake())), false);
        }
        source.sendSuccess(() -> Component.literal(
                "Try it: /scg fx test void   (or fire anything at all)"), false);
        return styles.length;
    }

    /**
     * {@code /scg fx motifs} - the per-warhead world geometry table.
     *
     * Every warhead has its own shape now (see {@code warhead/BlastMotif}); this prints the whole table so
     * "the effects all look the same" is a claim that can be checked instead of argued about.
     */
    /**
     * Draw a solid magenta cube for a few seconds through vanilla's own position-colour shader.
     *
     * <p>The raymarcher reports itself running, so loading is ruled out but drawing is not - and a cube
     * that is depth-rejected, culled, mis-matrixed or projected off-screen is indistinguishable from a
     * march that computes nothing. If the cube appears, the geometry path is fine and the fault is in the
     * shader's maths; if it does not, the march is not where to look.
     */
    private static int fxProbe(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        Runnable probe = volumetricProbe;
        if (probe == null) {
            source.sendFailure(Component.literal(
                    "The client never published a probe hook, so this is a dedicated server or the client "
                            + "mod did not construct."));
            return 0;
        }
        probe.run();
        source.sendSuccess(() -> Component.literal(
                "Probe cube drawn for 5 seconds. If you can see a magenta cube, the proxy geometry reaches "
                        + "the screen and the fault is inside the raymarch; if you cannot, it is not."),
                false);
        return 1;
    }

    private static int fxMotifs(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        source.sendSuccess(() -> Component.literal(
                "Warhead motifs (world geometry, not screen effects): "
                        + com.shadowclangaming.warhead.BlastMotif.ROSTER.size()), false);
        for (String id : com.shadowclangaming.warhead.BlastMotif.ROSTER) {
            var motif = com.shadowclangaming.warhead.BlastMotif.forWarhead(id);
            String line = String.format("  %-22s %-10s %4dt  %d layers  finale x%.2f",
                    id, motif.theme(), motif.baseTicks(), motif.specs().size(), motif.finaleScale());
            source.sendSuccess(() -> Component.literal(line), false);
        }
        source.sendSuccess(() -> Component.literal(
                "finale x0.00 = this warhead is not a bomb and ends some other way."), false);
        return com.shadowclangaming.warhead.BlastMotif.ROSTER.size();
    }

    /**
     * {@code /scg fx fuses} - the per-warhead, per-form fuse table.
     *
     * Fuses are derived from the same duration the effect uses, so a charge burns for as long as its own
     * warhead takes to play out. Printed rather than documented because the numbers move when the motifs
     * are retuned, and a table read from the code cannot be stale.
     */
    private static int fxFuses(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        source.sendSuccess(() -> Component.literal(
                "Fuses, in seconds (block / grenade / minecart):"), false);
        for (String id : com.shadowclangaming.warhead.BlastMotif.ROSTER) {
            var type = com.shadowclangaming.warhead.WarheadType.byId(id);
            float power = type.basePower();
            double block = com.shadowclangaming.warhead.ExplosivePackaging.fuseSeconds(id, power,
                    com.shadowclangaming.warhead.ExplosivePackaging.Form.BLOCK);
            double grenade = com.shadowclangaming.warhead.ExplosivePackaging.fuseSeconds(id, power,
                    com.shadowclangaming.warhead.ExplosivePackaging.Form.GRENADE);
            double cart = com.shadowclangaming.warhead.ExplosivePackaging.fuseSeconds(id, power,
                    com.shadowclangaming.warhead.ExplosivePackaging.Form.MINECART);
            String line = String.format("  %-22s %6.1f  %6.1f  %6.1f", id, block, grenade, cart);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return com.shadowclangaming.warhead.BlastMotif.ROSTER.size();
    }

    /** Fire one style of screen effect at the caller, so a shader can be checked without a warhead. */
    private static int fxTest(CommandContext<CommandSourceStack> context, String styleId) {
        var source = context.getSource();
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Run this as a player: it sends you the effect packet."));
            return 0;
        }
        var style = com.shadowclangaming.warhead.BlastStyle.byId(styleId);
        var level = (net.minecraft.server.level.ServerLevel) player.level();
        com.shadowclangaming.network.ScgNet.sendBlastFx(level, style, style.id(), player.position(), 24,
                style.ticksFor(20.0F));
        source.sendSuccess(() -> Component.literal("Sent the " + style.id()
                + " effect (" + style.shader() + "). If the screen does nothing, run /scg fx status:"
                + " shaders may be off in config/shadowclangaming-client.toml."), false);
        return 1;
    }

    private static int fieldList(CommandContext<CommandSourceStack> context) {
        var fields = com.shadowclangaming.warhead.ForceFields.get(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal(
                fields.count() + " Bulwark dome(s) active."), false);
        long now = context.getSource().getLevel().getGameTime();
        for (var field : fields.all()) {
            long left = Math.max(0L, field.expiresAt() - now);
            context.getSource().sendSuccess(() -> Component.literal(String.format(
                    "  %s  radius %d  %.1fs left  funding %,d",
                    field.centre(), field.radius(), left / 20.0D, field.funding())), false);
        }
        return fields.count();
    }

    private static int fieldClear(CommandContext<CommandSourceStack> context) {
        var fields = com.shadowclangaming.warhead.ForceFields.get(context.getSource().getLevel());
        int count = fields.count();
        fields.clear();
        context.getSource().sendSuccess(() -> Component.literal("Dismissed " + count + " dome(s)."), true);
        return count;
    }

    private static int plagueList(CommandContext<CommandSourceStack> context) {
        var outbreak = com.shadowclangaming.warhead.Outbreak.get(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal(
                outbreak.count() + " carrier(s), worst generation " + outbreak.highestGeneration()
                        + ", spreading radius " + (int) com.shadowclangaming.warhead.Outbreak.SPREAD_RADIUS
                        + " blocks, pulses every "
                        + com.shadowclangaming.warhead.Outbreak.PULSE_TICKS + " ticks"), false);
        return outbreak.count();
    }

    private static int plagueClear(CommandContext<CommandSourceStack> context) {
        int removed = com.shadowclangaming.warhead.Outbreak.get(context.getSource().getLevel()).clear();
        context.getSource().sendSuccess(() -> Component.literal(
                "Cured " + removed + " carrier(s)."), true);
        return removed;
    }

    private static int plagueCure(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
        boolean cured = com.shadowclangaming.warhead.Outbreak.get(context.getSource().getLevel())
                .cure(player.getUUID());
        player.removeAllEffects();
        context.getSource().sendSuccess(() -> Component.literal(cured
                ? "Cured " + player.getGameProfile().getName() + "."
                : player.getGameProfile().getName() + " was not a carrier; effects cleared anyway."), true);
        return cured ? 1 : 0;
    }

    private static int powerShow(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
        long units = com.shadowclangaming.economy.ExchangeLedger.get(context.getSource().getLevel())
                .unitsCredited(player.getUUID());
        long next = com.shadowclangaming.economy.PowerExchange.feForNextUnit(units);
        context.getSource().sendSuccess(() -> Component.literal(
                player.getGameProfile().getName() + ": " + String.format("%,d", units)
                        + " units earned from power; next unit costs "
                        + (next == Long.MAX_VALUE ? "more than a long can hold" : String.format("%,d", next) + " FE")),
                false);
        return 1;
    }

    private static int powerReset(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
        boolean removed = com.shadowclangaming.economy.ExchangeLedger.get(context.getSource().getLevel())
                .reset(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal(removed
                ? "Cleared the power counter for " + player.getGameProfile().getName()
                : player.getGameProfile().getName() + " had no power conversions on record."), false);
        return removed ? 1 : 0;
    }

    private static int powerResetAll(CommandContext<CommandSourceStack> context) {
        int count = com.shadowclangaming.economy.ExchangeLedger.get(context.getSource().getLevel()).resetAll();
        context.getSource().sendSuccess(() -> Component.literal(
                "Cleared power counters for " + count + " player(s)."), true);
        return count;
    }

    private static int economyGenerate(CommandContext<CommandSourceStack> context, boolean force) {
        var report = com.shadowclangaming.compat.economy.EconomyBridge.generate(
                context.getSource().getServer(), force);
        if (report.error != null) {
            context.getSource().sendFailure(Component.literal(report.error));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(report.summary()), true);
        if (!"(none)".equals(report.backup)) {
            context.getSource().sendSuccess(() -> Component.literal("Backup: " + report.backup), false);
        }
        return report.added;
    }

    /**
     * Why is this item priced the way it is?
     *
     * Rebuilds the recipe graph and prints the cheapest recipe the price came from. Expensive (it walks
     * every recipe in the pack), which is why it is an operator command and not a tooltip.
     */
    private static int economyExplain(CommandContext<CommandSourceStack> context) {
        var input = net.minecraft.commands.arguments.item.ItemArgument.getItem(context, "item");
        var item = input.getItem();
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        if (key == null) {
            context.getSource().sendFailure(Component.literal("That item has no registry id."));
            return 0;
        }
        String id = key.toString();
        var table = com.shadowclangaming.compat.economy.RecipePricer.build(context.getSource().getServer());
        String line = table.explain(id);
        int sell = table.sellPrice(id);
        int buy = table.buyPrice(id);
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "shop: buys for " + buy + ", sells for " + sell), false);
        return sell;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getServer().overworld();
        var scars = StrategicBlasts.Scars.get(level).all();
        if (scars.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No blast scars recorded."), false);
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Blast scars (" + scars.size() + "):"), false);
        for (int i = 0; i < scars.size(); i++) {
            int index = i;
            var scar = scars.get(i);
            long columns = (long) (Math.PI * scar.radius() * scar.radius()) / 256L;
            context.getSource().sendSuccess(() -> Component.literal(String.format(
                    "  [%d] x=%.0f y=%.0f z=%.0f  radius=%.0f  (~%,d chunk columns)  age=%dt",
                    index, scar.centre().x, scar.centre().y, scar.centre().z,
                    scar.radius(), columns, scar.createdAt())), false);
        }
        return scars.size();
    }

    private static int clearAll(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getServer().overworld();
        int removed = StrategicBlasts.Scars.get(level).all().size();
        StrategicBlasts.Scars.get(level).clear();
        context.getSource().sendSuccess(() -> Component.literal(
                "Removed " + removed + " blast scar(s). Previously wiped chunks stay wiped."), true);
        return removed;
    }

    private static int clearOne(CommandContext<CommandSourceStack> context) {
        int index = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "index");
        ServerLevel level = context.getSource().getServer().overworld();
        boolean removed = StrategicBlasts.Scars.get(level).remove(index);
        if (!removed) {
            context.getSource().sendFailure(Component.literal("No scar with index " + index + "."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Removed scar [" + index + "]. Previously wiped chunks stay wiped."), true);
        return 1;
    }

    private SCGCommands() {
    }
}
