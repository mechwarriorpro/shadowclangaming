package com.shadowclangaming.network;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.client.ClientApexQueue;
import net.minecraft.core.BlockPos;
import com.shadowclangaming.block.LaunchPadBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The mod's payload channel.
 *
 * Menus (the server-side half of a GUI) already give us one-way, low-frequency sync for free through
 * {@code ContainerData}: that is how the inscription draft is mirrored. What menus cannot do is carry a
 * chat-sized blob of text (a terminal transcript) or accept a free-form command line typed into a GUI.
 * Both of those are "just data", so they get one tiny payload pair rather than a menu per screen:
 *
 *   client -> server   {@link CommandPayload}   a line typed into the terminal or the launcher console
 *   server -> client   {@link TextPayload}      the lines to print back
 *
 * Both carry a {@link BlockPos} so one handler covers every block that wants a console. The server
 * re-resolves the block entity from the position and re-checks the distance: a payload handler is a
 * public entry point, and nothing a client sends is trusted.
 */
@EventBusSubscriber(modid = ShadowClanGaming.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ScgNet {

    public static final int MAX_LINES = 24;
    public static final int MAX_LINE_LENGTH = 220;

    /** A command line typed by a client into one of our screens. */
    public record CommandPayload(BlockPos pos, String line) implements CustomPacketPayload {
        public static final Type<CommandPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "console_command"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CommandPayload> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, CommandPayload::pos,
                        ByteBufCodecs.stringUtf8(256), CommandPayload::line,
                        CommandPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Lines for a client to append to a console. */
    public record TextPayload(BlockPos pos, java.util.List<String> lines) implements CustomPacketPayload {
        public static final Type<TextPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "console_text"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TextPayload> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, TextPayload::pos,
                        ByteBufCodecs.stringUtf8(MAX_LINE_LENGTH).<java.util.List<String>>apply(
                                ByteBufCodecs.list(MAX_LINES)), TextPayload::lines,
                        TextPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * "I am pointing at THIS block, on that blueprint table."
     *
     * The client is the only side that knows where the camera is pointing right now: the server's copy
     * of the player's rotation is at least one packet old, which is why corners used to land on the
     * table itself (the block being right-clicked) or on the block at the player's feet. Both made
     * 1x1x1 boxes. The client ships the answer instead, and the server checks it.
     */
    public record BlueprintCornerPayload(BlockPos table, BlockPos corner) implements CustomPacketPayload {
        public static final Type<BlueprintCornerPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "blueprint_corner"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintCornerPayload> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, BlueprintCornerPayload::table,
                        BlockPos.STREAM_CODEC, BlueprintCornerPayload::corner,
                        BlueprintCornerPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * "Name this spell."
     *
     * The inscription table's name box is a text field, and menu data slots are ints, so the name
     * travels as its own small payload rather than being smuggled through the draft array. The server
     * re-resolves the table from the position, checks the distance, and clamps the length.
     */
    public record SpellNamePayload(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<SpellNamePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "spell_name"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SpellNamePayload> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, SpellNamePayload::pos,
                        ByteBufCodecs.stringUtf8(48), SpellNamePayload::name,
                        SpellNamePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * "Set the warhead's radius to this."
     *
     * The pad's slider is the alternative to "compute the max radius the funding allows" - the player
     * picks the number, and may type an exact one. -1 clears the override.
     */
    public record PadRadiusPayload(BlockPos pos, int radius) implements CustomPacketPayload {
        public static final Type<PadRadiusPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "pad_radius"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PadRadiusPayload> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, PadRadiusPayload::pos,
                        ByteBufCodecs.VAR_INT, PadRadiusPayload::radius,
                        PadRadiusPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * "An apex warhead just went off here, and it looks like this."
     *
     * The whole seventeen-weapon animation is deterministic from these four numbers, so this packet is
     * sent ONCE per detonation instead of once per tick: the client replays the timeline locally from
     * {@code warhead/apex/ApexTimeline}. A negative {@code ticks} cancels an effect that is still running.
     *
     * The id is a string rather than an enum ordinal on purpose - a client with a different build must be
     * able to ignore a weapon it does not know instead of desyncing.
     */
    public record ApexFxPayload(String apex, double x, double y, double z, int radius, int ticks)
            implements CustomPacketPayload {
        public static final Type<ApexFxPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "apex_fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ApexFxPayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(48), ApexFxPayload::apex,
                        ByteBufCodecs.DOUBLE, ApexFxPayload::x,
                        ByteBufCodecs.DOUBLE, ApexFxPayload::y,
                        ByteBufCodecs.DOUBLE, ApexFxPayload::z,
                        ByteBufCodecs.VAR_INT, ApexFxPayload::radius,
                        ByteBufCodecs.VAR_INT, ApexFxPayload::ticks,
                        ApexFxPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> the players near one apex detonation. */
    public static void sendApexFx(ServerPlayer player,
                                  com.shadowclangaming.warhead.apex.Apex apex,
                                  net.minecraft.world.phys.Vec3 pos, int radius, int ticks) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new ApexFxPayload(apex.id(), pos.x, pos.y, pos.z, radius, ticks));
    }

    /**
     * "An ordinary warhead just went off here, and it looks like this."
     *
     * The apex weapons have their own packet because they replay a timeline; every other warhead - all 38 of
     * them, in every delivery form - sends this instead. One packet per detonation, no per-tick traffic: the
     * client owns the animation, so a 5-tick grenade and a 20-second Omega cost the server the same 40-odd
     * bytes.
     *
     * {@code style} is a string (see {@code warhead/BlastStyle}) rather than an ordinal so an older client
     * degrades to its fallback shader instead of rendering somebody else's warhead.
     */
    public record BlastFxPayload(String style, String warhead, double x, double y, double z, int radius,
                                 int ticks) implements CustomPacketPayload {
        public static final Type<BlastFxPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "blast_fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BlastFxPayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(32), BlastFxPayload::style,
                        ByteBufCodecs.stringUtf8(48), BlastFxPayload::warhead,
                        ByteBufCodecs.DOUBLE, BlastFxPayload::x,
                        ByteBufCodecs.DOUBLE, BlastFxPayload::y,
                        ByteBufCodecs.DOUBLE, BlastFxPayload::z,
                        ByteBufCodecs.VAR_INT, BlastFxPayload::radius,
                        ByteBufCodecs.VAR_INT, BlastFxPayload::ticks,
                        BlastFxPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Send one detonation's effect to everyone who could plausibly see it.
     *
     * The range is the blast radius plus the configured margin, so a nuclear warhead reaches players on the
     * horizon while a grenade only bothers the people standing next to it. Clamped by the server's own config
     * limit so a 4096-radius blast cannot try to send to the whole world at once.
     */
    public static void sendBlastFx(net.minecraft.server.level.ServerLevel level,
                                   com.shadowclangaming.warhead.BlastStyle style, String warheadId,
                                   net.minecraft.world.phys.Vec3 pos, int radius, int ticks) {
        if (!com.shadowclangaming.config.SCGConfig.fxPackets()) {
            return;
        }
        int margin = com.shadowclangaming.config.SCGConfig.fxRange();
        double reach = Math.min(4096.0D, Math.max(64.0D, radius + margin));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(level, null,
                pos.x, pos.y, pos.z, reach,
                new BlastFxPayload(style.id(), warheadId == null ? style.id() : warheadId,
                        pos.x, pos.y, pos.z, radius, ticks));
    }

    /**
     * "Fire everything on channel N at this point."
     *
     * The designator no longer stores a pad: it stores a frequency, and the server resolves the battery
     * from {@code FrequencyRegistry}. That is what makes a designator work against a silo that is sitting
     * on a moving Sable subgrid - the pad re-registers its current position on the channel every time it
     * moves, so the channel is always right even when a coordinate would not be.
     *
     * Nothing a client sends is trusted: the channel must exist, the pads must be loaded, each pad runs
     * its own structure/range/cooldown checks, and the player pays for the shot whether or not a missile
     * leaves the tube (the energy was already spent client-side; a refusal is reported, not refunded -
     * transmitting into an empty battery should cost you the call).
     */
    public record DesignatorFirePayload(int channel, int x, int y, int z) implements CustomPacketPayload {
        public static final Type<DesignatorFirePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "designator_fire"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DesignatorFirePayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, DesignatorFirePayload::channel,
                        ByteBufCodecs.INT, DesignatorFirePayload::x,
                        ByteBufCodecs.INT, DesignatorFirePayload::y,
                        ByteBufCodecs.INT, DesignatorFirePayload::z,
                        DesignatorFirePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(CommandPayload.TYPE, CommandPayload.CODEC, ScgNet::handleCommand);
        registrar.playToServer(BlueprintCornerPayload.TYPE, BlueprintCornerPayload.CODEC,
                ScgNet::handleBlueprintCorner);
        registrar.playToServer(SpellNamePayload.TYPE, SpellNamePayload.CODEC, ScgNet::handleSpellName);
        registrar.playToServer(PadRadiusPayload.TYPE, PadRadiusPayload.CODEC, ScgNet::handlePadRadius);
        registrar.playToClient(TextPayload.TYPE, TextPayload.CODEC, ScgNet::handleText);
        registrar.playToClient(ApexFxPayload.TYPE, ApexFxPayload.CODEC, ScgNet::handleApexFx);
        registrar.playToClient(BlastFxPayload.TYPE, BlastFxPayload.CODEC, ScgNet::handleBlastFx);
        registrar.playToServer(DesignatorFirePayload.TYPE, DesignatorFirePayload.CODEC,
                ScgNet::handleDesignatorFire);
    }

    // ---------------------------------------------------------------- server side

    /**
     * Runs a line a player typed in a screen.
     *
     * The block entity re-resolves the target, and the packet's position must be within 8 blocks of the
     * player: a client cannot reach across the world or address a block it is not standing at.
     */
    private static void handleCommand(CommandPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.blockPosition().distSqr(payload.pos()) > 64.0D) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(payload.pos());
        String line = payload.line() == null ? "" : payload.line().strip();
        if (line.length() > 200) {
            line = line.substring(0, 200);
        }
        if (line.isEmpty()) {
            return;
        }
        ConsoleHost host = entity instanceof ConsoleHost h ? h : null;
        if (host == null) {
            return;
        }
        java.util.List<String> out = host.runConsoleCommand(level, player, line);
        if (out != null && !out.isEmpty()) {
            context.reply(new TextPayload(payload.pos(),
                    out.size() > MAX_LINES ? out.subList(0, MAX_LINES) : out));
        }
    }

    /**
     * Mark a blueprint corner.
     *
     * Nothing a client sends is trusted: the table must exist and be a blueprint table, the player must
     * be standing near it, and the corner must be inside the table's marking reach measured from the
     * PLAYER (not from the table, so you cannot reach around a corner of the world with it).
     */
    private static void handlePadRadius(PadRadiusPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.blockPosition().distSqr(payload.pos()) > 64.0D) {
            return;
        }
        if (level.getBlockEntity(payload.pos())
                instanceof com.shadowclangaming.block.LaunchPadBlockEntity pad) {
            pad.setSliderRadius(payload.radius());
            // Echo it back so the slider and the readout agree even if the server clamped it.
            String line = payload.radius() < 0 ? "radius: warhead default"
                    : "radius set to " + payload.radius();
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(line)
                    .withStyle(net.minecraft.ChatFormatting.AQUA), true);
        }
    }

    private static void handleSpellName(SpellNamePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.blockPosition().distSqr(payload.pos()) > 64.0D) {
            return;
        }
        if (level.getBlockEntity(payload.pos())
                instanceof com.shadowclangaming.block.InscriptionTableBlockEntity table) {
            table.setDraftName(payload.name());
        }
    }

    private static void handleBlueprintCorner(BlueprintCornerPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.blockPosition().distSqr(payload.table()) > 64.0D) {
            return;
        }
        if (!(level.getBlockEntity(payload.table())
                instanceof com.shadowclangaming.block.BlueprintTableBlockEntity table)) {
            return;
        }
        BlockPos corner = payload.corner();
        if (corner.distSqr(player.blockPosition())
                > com.shadowclangaming.block.BlueprintTableBlockEntity.MARK_REACH
                * com.shadowclangaming.block.BlueprintTableBlockEntity.MARK_REACH) {
            return;
        }
        if (level.getBlockState(corner).isAir()) {
            // Air cannot be a corner - the packet is stale or the client is confused. Say so rather
            // than storing a corner that would silently do nothing.
            table.markCorner(player, null, true);
            return;
        }
        table.markCorner(player, corner, true);
    }

    /**
     * Client side: hand the effect to the queue the renderer drains.
     *
     * Like {@link #handleText}, this touches only a plain data holder, so the network class never loads a
     * client-only class on a dedicated server.
     */
    /**
     * Resolve a channel to its pads and fire the ones that can shoot.
     *
     * Reports the tally back to the player: "launched 2 of 3 on channel 7" tells them the battery exists
     * and one of it is not ready, which is exactly what they need to know; silence would not be.
     */
    private static void handleDesignatorFire(DesignatorFirePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos target = new BlockPos(payload.x(), payload.y(), payload.z());
        var pads = com.shadowclangaming.computer.FrequencyRegistry.get(level.getServer())
                .pads(payload.channel(), level.dimension());
        if (pads.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.shadowclangaming.designator_dead_channel", payload.channel())
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        int fired = 0;
        int considered = 0;
        String refusal = null;
        for (net.minecraft.core.GlobalPos at : pads) {
            if (!level.isLoaded(at.pos())) {
                continue;
            }
            considered++;
            if (!(level.getBlockEntity(at.pos()) instanceof com.shadowclangaming.block.LaunchPadBlockEntity pad)) {
                continue;
            }
            // The DESIGNATOR supplies the target: requiring the pad to already have one meant a silo that
            // had never been aimed refused every designator shot with "no target lock", which read as
            // "the designator is not linked" when it was in fact linked and simply not pre-aimed.
            if (!pad.hasMissile()) {
                refusal = "the silo has no missile loaded";
                continue;
            }
            pad.setTarget(target);
            if (!pad.structureComplete()) {
                refusal = pad.structure().summary();
                continue;
            }
            if (pad.fireAt(target)) {
                fired++;
            } else if (pad.lastStructureRefusal() != null) {
                refusal = pad.lastStructureRefusal();
            } else if (pad.lastRangeRefusal() > 0L) {
                refusal = "out of range by " + pad.lastRangeRefusal() + " blocks";
            } else {
                refusal = "reloading";
            }
        }
        if (fired > 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.shadowclangaming.designator_fired_channel", fired, considered,
                    payload.channel()).withStyle(net.minecraft.ChatFormatting.GOLD), true);
        } else {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.shadowclangaming.designator_no_shot", refusal == null ? "no pad" : refusal)
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
        }
    }

    private static void handleBlastFx(BlastFxPayload payload, IPayloadContext context) {
        // The payload callback is a network-thread callback. Queue mutation is consumed by the client tick
        // and renderer, so marshal it onto the client game thread before touching the ArrayList. Otherwise
        // the queue can race the render/tick loop and the packet appears to arrive (particles still spawn)
        // while no screen effect is ever rendered.
        context.enqueueWork(() -> {
            com.shadowclangaming.client.ClientBlastQueue.accept(
                    payload.style(), payload.warhead(), payload.x(), payload.y(), payload.z(),
                    payload.radius(), payload.ticks());
            com.shadowclangaming.debug.ShadowDebug.log("FX_PACKET",
                    "received type=blast style={} warhead={} radius={} ticks={}",
                    payload.style(), payload.warhead(), payload.radius(), payload.ticks());
        });
    }

    private static void handleApexFx(ApexFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientApexQueue.accept(payload.apex(), payload.x(), payload.y(),
                    payload.z(), payload.radius(), payload.ticks());
            com.shadowclangaming.debug.ShadowDebug.log("FX_PACKET",
                    "received type=apex id={} radius={} ticks={}",
                    payload.apex(), payload.radius(), payload.ticks());
        });
    }

    private static void handleText(TextPayload payload, IPayloadContext context) {
        // Client side: nothing to do here - the screen drains the queue in its own tick. Keeping the
        // handler empty (instead of a client-only class) is what keeps the network class safe to load on
        // a dedicated server.
        ClientConsoleQueue.accept(payload.pos(), payload.lines());
    }

    /** Implemented by every block entity that owns a console screen (terminal, launcher, panel). */
    public interface ConsoleHost {
        /**
         * Run one typed line.
         *
         * @return lines to echo back to the screen, or null/empty for "nothing to say"
         */
        java.util.List<String> runConsoleCommand(ServerLevel level, ServerPlayer player, String line);
    }

    private ScgNet() {
    }
}
