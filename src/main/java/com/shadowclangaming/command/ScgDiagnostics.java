package com.shadowclangaming.command;

import com.mojang.brigadier.context.CommandContext;
import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.debug.ShadowDebug;
import com.shadowclangaming.warhead.BlastMotif;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable diagnostics for {@code /shadowclangaming diag|test|audit}.
 *
 * <p>Every warhead (122) is emitted as one JSON object per line to
 * {@code logs/shadow_debug.log} via {@link ShadowDebug} under category
 * {@code DIAG_WARHEAD_JSON} / {@code DIAG_PHOTOREAL_JSON}, plus a
 * mirrored line to {@code LOGGER.info} and a structured file
 * {@code logs/scg_diag.jsonl} for direct {@code jq} / harness consumption.
 *
 * <p>Photoreal mapping is taken from {@link com.shadowclangaming.client.photoreal.PhotorealFx#modeFor}
 * so the log proves which of the 4 photoreal shaders (NUKE/BEAM/CRACKS/STAR)
 * each warhead will actually march, not just its theme. The volume palette
 * luminance and every feature weight are included so a flat palette or
 * missing signature is grep-able without opening the game.
 *
 * <p>This class lives in common (not client) and intentionally avoids
 * direct references to client-only shader classes; volumetric availability
 * is read through the published supplier on {@link SCGCommands}.
 */
public final class ScgDiagnostics {

    private ScgDiagnostics() {}

    // ---- Brigadier entry points ------------------------------------------------

    public static int diagAll(CommandContext<CommandSourceStack> ctx) {
        return audit(ctx.getSource(), "all");
    }

    public static int diagWarheads(CommandContext<CommandSourceStack> ctx) {
        return audit(ctx.getSource(), "warheads");
    }

    public static int diagPhotoreal(CommandContext<CommandSourceStack> ctx) {
        return audit(ctx.getSource(), "photoreal");
    }

    public static int diagMotifs(CommandContext<CommandSourceStack> ctx) {
        return audit(ctx.getSource(), "motifs");
    }

    public static int diagFull(CommandContext<CommandSourceStack> ctx) {
        return audit(ctx.getSource(), "full");
    }

    public static int diagScoped(CommandContext<CommandSourceStack> ctx) {
        String scope = "all";
        try {
            scope = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "scope");
        } catch (Exception ignored) {
        }
        return audit(ctx.getSource(), scope);
    }

    // ---- core audit ------------------------------------------------------------

    private static int audit(CommandSourceStack source, String scope) {
        long startNanos = System.nanoTime();
        Instant now = Instant.now();
        String ts = now.toString();
        ServerLevel level = null;
        try {
            level = source.getLevel();
        } catch (Exception ignored) {
        }
        String dim = level == null ? "unknown" : level.dimension().location().toString();
        long gameTime = level == null ? -1L : level.getGameTime();

        // Header: one JSON line that identifies this run for harness correlation.
        String headerJson = "{\"diag\":\"header\",\"scope\":\"" + jsonEscape(scope) + "\",\"timestamp\":\"" + ts
                + "\",\"dimension\":\"" + jsonEscape(dim) + "\",\"gameTime\":" + gameTime
                + ",\"server\":\"" + jsonEscape(source.getServer() == null ? "unknown" : source.getServer().getServerModName()) + "\"}";
        emit("DIAG_HEADER_JSON", headerJson);

        // Volumetric / photoreal runtime status (client-published supplier if present).
        String volumetricLine = "not_reported";
        try {
            var sup = SCGCommands.volumetricStatus;
            volumetricLine = sup == null ? "dedicated_server_no_client_hook" : sup.get();
        } catch (Throwable t) {
            volumetricLine = "error_reading_status: " + t.getClass().getSimpleName() + ":" + t.getMessage();
        }
        String runtimeJson = "{\"diag\":\"runtime\",\"volumetric\":\"" + jsonEscape(volumetricLine.replace("\n", " | ")) + "\"}";
        emit("DIAG_RUNTIME_JSON", runtimeJson);

        List<String> roster = BlastMotif.ROSTER;
        int total = roster.size();

        // Per-theme and per-photoreal tallies for the summary.
        Map<String, Integer> byTheme = new LinkedHashMap<>();
        Map<String, Integer> byPhotoreal = new LinkedHashMap<>();
        Map<String, Integer> byHasPhotoreal = new LinkedHashMap<>();

        int emittedWarheads = 0;
        int emittedPhotoreal = 0;
        int failures = 0;

        // Dedicated machine-readable file for this run: logs/scg_diag.jsonl (append, not overwrite).
        Path diagFile = null;
        try {
            var logs = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("logs");
            Files.createDirectories(logs);
            diagFile = logs.resolve("scg_diag.jsonl");
            Files.writeString(diagFile, headerJson + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(diagFile, runtimeJson + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            ShadowDebug.log("DIAG_FILE_WARN", "could not open scg_diag.jsonl: {}", ex.getMessage());
        }

        for (String warheadId : roster) {
            try {
                BlastMotif.Motif motif = BlastMotif.forWarhead(warheadId);
                BlastMotif.Volume vol = motif.volume();
                BlastMotif.Theme theme = motif.theme();

                byTheme.merge(theme == null ? "null" : theme.name(), 1, Integer::sum);

                // Photoreal mapping (safe server-side, no client class).
                String photorealMode = "NONE";
                String shaderLoc = "null";
                boolean needsCircles = false;
                try {
                    var mode = com.shadowclangaming.client.photoreal.PhotorealFx.modeFor(warheadId, theme);
                    photorealMode = mode == null ? "NONE" : mode.name();
                    var shader = com.shadowclangaming.client.photoreal.PhotorealFx.shaderFor(mode);
                    shaderLoc = shader == null ? "null" : shader.toString();
                    needsCircles = com.shadowclangaming.client.photoreal.PhotorealFx.needsMagicCircles(warheadId, theme);
                    byPhotoreal.merge(photorealMode, 1, Integer::sum);
                    byHasPhotoreal.merge(photorealMode.equals("NONE") ? "plain" : "photoreal", 1, Integer::sum);
                } catch (Throwable t) {
                    photorealMode = "ERROR:" + t.getClass().getSimpleName();
                    byPhotoreal.merge(photorealMode, 1, Integer::sum);
                }

                // Luminance of the thermal ramp (hot vs cool).
                double hotLum = luminance(vol.colorA());
                double coolLum = luminance(vol.colorC());
                double deltaLum = hotLum - coolLum;

                // Build specs array JSON for this motif.
                StringBuilder specsJson = new StringBuilder("[");
                List<BlastMotif.Spec> specs = motif.specs();
                for (int i = 0; i < specs.size(); i++) {
                    BlastMotif.Spec s = specs.get(i);
                    if (i > 0) specsJson.append(",");
                    specsJson.append("{")
                            .append("\"layer\":\"").append(jsonEscape(s.layer().name())).append("\",")
                            .append("\"from\":").append(fmt(s.from())).append(",")
                            .append("\"to\":").append(fmt(s.to())).append(",")
                            .append("\"scale\":").append(fmt(s.scale())).append(",")
                            .append("\"argb\":\"").append(argbHex(s.argb())).append("\",")
                            .append("\"count\":").append(s.count()).append(",")
                            .append("\"spin\":").append(fmt(s.spin())).append(",")
                            .append("\"phase\":\"").append(jsonEscape(s.phase().name())).append("\"")
                            .append("}");
                }
                specsJson.append("]");

                // Charge specs (if any) similarly.
                StringBuilder chargeJson = new StringBuilder("[");
                try {
                    var charge = motif.chargeSpecs();
                    for (int i = 0; i < charge.size(); i++) {
                        var s = charge.get(i);
                        if (i > 0) chargeJson.append(",");
                        chargeJson.append("{")
                                .append("\"layer\":\"").append(jsonEscape(s.layer().name())).append("\",")
                                .append("\"from\":").append(fmt(s.from())).append(",")
                                .append("\"to\":").append(fmt(s.to())).append(",")
                                .append("\"scale\":").append(fmt(s.scale())).append(",")
                                .append("\"argb\":\"").append(argbHex(s.argb())).append("\",")
                                .append("\"count\":").append(s.count()).append(",")
                                .append("\"spin\":").append(fmt(s.spin())).append(",")
                                .append("\"phase\":\"").append(jsonEscape(s.phase().name())).append("\"")
                                .append("}");
                    }
                } catch (Throwable ignored) {
                }
                chargeJson.append("]");

                // Volume features (all 32 FEATURES-equivalent fields from BlastMotif.Volume).
                StringBuilder feats = new StringBuilder("{");
                feats.append("\"beam\":").append(fmt(vol.beam())).append(",");
                feats.append("\"ring\":").append(fmt(vol.ring())).append(",");
                feats.append("\"skyRing\":").append(fmt(vol.skyRing())).append(",");
                feats.append("\"implode\":").append(fmt(vol.implode())).append(",");
                feats.append("\"turb\":").append(fmt(vol.turb())).append(",");
                feats.append("\"rise\":").append(fmt(vol.rise())).append(",");
                feats.append("\"streaks\":").append(fmt(vol.streaks())).append(",");
                feats.append("\"dark\":").append(fmt(vol.dark())).append(",");
                feats.append("\"glow\":").append(fmt(vol.glow())).append(",");
                feats.append("\"swirl\":").append(fmt(vol.swirl())).append(",");
                feats.append("\"seed\":").append(fmt(vol.seed())).append(",");
                feats.append("\"hole\":").append(fmt(vol.hole())).append(",");
                feats.append("\"star\":").append(fmt(vol.star())).append(",");
                feats.append("\"rift\":").append(fmt(vol.rift())).append(",");
                feats.append("\"waves\":").append(fmt(vol.waves())).append(",");
                feats.append("\"meteors\":").append(fmt(vol.meteors())).append(",");
                feats.append("\"lightning\":").append(fmt(vol.lightning())).append(",");
                feats.append("\"glyphs\":").append(fmt(vol.glyphs())).append(",");
                feats.append("\"portals\":").append(fmt(vol.portals())).append(",");
                feats.append("\"jet\":").append(fmt(vol.jet())).append(",");
                feats.append("\"coins\":").append(fmt(vol.coins())).append(",");
                feats.append("\"smoke\":").append(fmt(vol.smoke())).append(",");
                feats.append("\"subCharges\":").append(fmt(vol.subCharges())).append(",");
                feats.append("\"flash\":").append(fmt(vol.flash())).append(",");
                feats.append("\"dome\":").append(fmt(vol.dome())).append(",");
                feats.append("\"embers\":").append(fmt(vol.embers())).append(",");
                feats.append("\"cap\":").append(fmt(vol.cap())).append(",");
                feats.append("\"debris\":").append(fmt(vol.debris())).append(",");
                feats.append("\"dust\":").append(fmt(vol.dust())).append(",");
                feats.append("\"vortex\":").append(fmt(vol.vortex())).append(",");
                feats.append("\"cracks\":").append(fmt(vol.cracks())).append(",");
                feats.append("\"sparks\":").append(fmt(vol.sparks())).append(",");
                feats.append("\"halo\":").append(fmt(vol.halo())).append(",");
                feats.append("\"disc\":").append(fmt(vol.disc())).append(",");
                feats.append("\"lens\":").append(fmt(vol.lens())).append(",");
                feats.append("\"granule\":").append(fmt(vol.granule())).append(",");
                feats.append("\"prominence\":").append(fmt(vol.prominence())).append(",");
                feats.append("\"shell\":").append(fmt(vol.shell())).append(",");
                feats.append("\"nebula\":").append(fmt(vol.nebula())).append(",");
                feats.append("\"cone\":").append(fmt(vol.cone())).append(",");
                feats.append("\"circle\":").append(fmt(vol.circle())).append(",");
                feats.append("\"sky\":").append(fmt(vol.sky())).append(",");
                feats.append("\"shaft\":").append(fmt(vol.shaft()));
                feats.append("}");

                // Palette luminance + alpha checks.
                String paletteJson = "{\"colorA\":\"" + argbHex(vol.colorA()) + "\","
                        + "\"colorB\":\"" + argbHex(vol.colorB()) + "\","
                        + "\"colorC\":\"" + argbHex(vol.colorC()) + "\","
                        + "\"hotLum\":" + fmt(hotLum) + ","
                        + "\"coolLum\":" + fmt(coolLum) + ","
                        + "\"deltaLum\":" + fmt(deltaLum) + ","
                        + "\"opaque\":" + (((vol.colorA() >>> 24) == 0xFF && (vol.colorB() >>> 24) == 0xFF && (vol.colorC() >>> 24) == 0xFF) ? "true" : "false") + "}";

                String signature = "";
                try {
                    signature = vol.signature();
                } catch (Throwable t) {
                    signature = "error:" + t.getMessage();
                }

                boolean isPhotoreal = !"NONE".equals(photorealMode);
                if (isPhotoreal) emittedPhotoreal++;

                // One JSON object per warhead: fully machine-parseable, greppable by warhead id.
                String warheadJson = "{"
                        + "\"diag\":\"warhead\","
                        + "\"timestamp\":\"" + ts + "\","
                        + "\"warhead\":\"" + jsonEscape(warheadId) + "\","
                        + "\"theme\":\"" + jsonEscape(theme == null ? "null" : theme.name()) + "\","
                        + "\"photorealMode\":\"" + jsonEscape(photorealMode) + "\","
                        + "\"photorealShader\":" + (shaderLoc.equals("null") ? "null" : "\"" + jsonEscape(shaderLoc) + "\"") + ","
                        + "\"needsMagicCircles\":" + needsCircles + ","
                        + "\"isPhotoreal\":" + isPhotoreal + ","
                        + "\"motif\":{"
                        + "\"warheadId\":\"" + jsonEscape(motif.warheadId()) + "\","
                        + "\"baseTicks\":" + motif.baseTicks() + ","
                        + "\"finaleScale\":" + fmt(motif.finaleScale()) + ","
                        + "\"hasBlastFinale\":" + motif.hasBlastFinale() + ","
                        + "\"specCount\":" + specs.size() + ","
                        + "\"specs\":" + specsJson + ","
                        + "\"chargeCount\":" + motif.chargeSpecs().size() + ","
                        + "\"charge\":" + chargeJson
                        + "},"
                        + "\"volume\":{"
                        + "\"palette\":" + paletteJson + ","
                        + "\"features\":" + feats + ","
                        + "\"signature\":\"" + jsonEscape(signature) + "\""
                        + "},"
                        + "\"scope\":\"" + jsonEscape(scope) + "\""
                        + "}";

                emit("DIAG_WARHEAD_JSON", warheadJson);
                // Photoreal-specific mirror category for easy filtering (grep DIAG_PHOTOREAL_JSON).
                if (isPhotoreal) {
                    String photoJson = "{\"diag\":\"photoreal\",\"timestamp\":\"" + ts + "\","
                            + "\"warhead\":\"" + jsonEscape(warheadId) + "\","
                            + "\"theme\":\"" + jsonEscape(theme.name()) + "\","
                            + "\"mode\":\"" + jsonEscape(photorealMode) + "\","
                            + "\"shader\":\"" + jsonEscape(shaderLoc) + "\","
                            + "\"needsMagicCircles\":" + needsCircles + ","
                            + "\"hotLum\":" + fmt(hotLum) + ","
                            + "\"coolLum\":" + fmt(coolLum) + ","
                            + "\"glow\":" + fmt(vol.glow()) + ","
                            + "\"shaft\":" + fmt(vol.shaft()) + ","
                            + "\"sky\":" + fmt(vol.sky()) + ","
                            + "\"signature\":\"" + jsonEscape(signature) + "\"}";
                    emit("DIAG_PHOTOREAL_JSON", photoJson);
                    if (diagFile != null) {
                        try {
                            Files.writeString(diagFile, photoJson + System.lineSeparator(), StandardCharsets.UTF_8,
                                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        } catch (Exception ignored2) {}
                    }
                }

                if (diagFile != null) {
                    try {
                        Files.writeString(diagFile, warheadJson + System.lineSeparator(), StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (Exception ignored2) {}
                }

                // Also emit per-layer detail lines for deep harness inspection.
                for (BlastMotif.Spec spec : specs) {
                    String layerJson = "{\"diag\":\"motif_layer\",\"timestamp\":\"" + ts + "\","
                            + "\"warhead\":\"" + jsonEscape(warheadId) + "\","
                            + "\"layer\":\"" + jsonEscape(spec.layer().name()) + "\","
                            + "\"from\":" + fmt(spec.from()) + ",\"to\":" + fmt(spec.to())
                            + ",\"scale\":" + fmt(spec.scale())
                            + ",\"argb\":\"" + argbHex(spec.argb()) + "\""
                            + ",\"count\":" + spec.count()
                            + ",\"spin\":" + fmt(spec.spin())
                            + ",\"phase\":\"" + jsonEscape(spec.phase().name()) + "\"}";
                    ShadowDebug.log("DIAG_MOTIF_LAYER_JSON", layerJson);
                }

                emittedWarheads++;

            } catch (Throwable ex) {
                failures++;
                String errJson = "{\"diag\":\"warhead_error\",\"timestamp\":\"" + ts + "\",\"warhead\":\"" + jsonEscape(warheadId)
                        + "\",\"error\":\"" + jsonEscape(ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage())) + "\"}";
                emit("DIAG_WARHEAD_ERROR_JSON", errJson);
                ShadowDebug.logThrowable("DIAG_WARHEAD_ERROR", errJson, ex);
                ShadowClanGaming.LOGGER.error("SCG diag failed for warhead {}", warheadId, ex);
            }
        }

        // Summary JSON: counts by theme and by photoreal mode, plus timing and file locations.
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        StringBuilder themeCounts = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : byTheme.entrySet()) {
            if (!first) themeCounts.append(",");
            themeCounts.append("\"").append(jsonEscape(e.getKey())).append("\":").append(e.getValue());
            first = false;
        }
        themeCounts.append("}");
        StringBuilder photoCounts = new StringBuilder("{");
        first = true;
        for (Map.Entry<String, Integer> e : byPhotoreal.entrySet()) {
            if (!first) photoCounts.append(",");
            photoCounts.append("\"").append(jsonEscape(e.getKey())).append("\":").append(e.getValue());
            first = false;
        }
        photoCounts.append("}");

        // Probe detailed motif/roster summary for motifs scope.
        String rosterJson = "null";
        if ("motifs".equals(scope) || "all".equals(scope) || "full".equals(scope)) {
            StringBuilder ro = new StringBuilder("[");
            for (int i = 0; i < roster.size(); i++) {
                if (i > 0) ro.append(",");
                ro.append("\"").append(jsonEscape(roster.get(i))).append("\"");
            }
            ro.append("]");
            rosterJson = ro.toString();
        }

        String summaryJson = "{"
                + "\"diag\":\"summary\","
                + "\"timestamp\":\"" + ts + "\","
                + "\"scope\":\"" + jsonEscape(scope) + "\","
                + "\"dimension\":\"" + jsonEscape(dim) + "\","
                + "\"gameTime\":" + gameTime + ","
                + "\"totalWarheads\":" + total + ","
                + "\"emittedWarheads\":" + emittedWarheads + ","
                + "\"emittedPhotoreal\":" + emittedPhotoreal + ","
                + "\"failures\":" + failures + ","
                + "\"byTheme\":" + themeCounts + ","
                + "\"byPhotoreal\":" + photoCounts + ","
                + "\"elapsedMs\":" + elapsedMs + ","
                + "\"roster\":" + rosterJson + ","
                + "\"logs\":{"
                + "\"shadow_debug\":\"logs/shadow_debug.log\","
                + "\"scg_diag\":\"logs/scg_diag.jsonl\","
                + "\"latest\":\"logs/latest.log\""
                + "},"
                + "\"notes\":\"grep DIAG_WARHEAD_JSON logs/shadow_debug.log | jq .  ;  cat logs/scg_diag.jsonl | jq -s\""
                + "}";

        emit("DIAG_SUMMARY_JSON", summaryJson);
        if (diagFile != null) {
            try {
                Files.writeString(diagFile, summaryJson + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {}
        }

        // Also emit a compact human-readable summary plus machine hint to chat.
        final int fEmittedWarheads = emittedWarheads; final int fTotal = total; final int fEmittedPhotoreal = emittedPhotoreal; final int fFailures = failures; final long fElapsedMs = elapsedMs; final var fThemeCounts = themeCounts; final var fPhotoCounts = photoCounts;
        source.sendSuccess(() -> Component.literal(
                "SCG diag [" + scope + "] done: " + fEmittedWarheads + "/" + fTotal + " warheads, "
                        + fEmittedPhotoreal + " photoreal, " + fFailures + " failures in " + fElapsedMs + "ms. "
                        + "See logs/shadow_debug.log (DIAG_*_JSON) and logs/scg_diag.jsonl (jq)."), false);
        source.sendSuccess(() -> Component.literal(
                "  byTheme " + fThemeCounts + "  byPhotoreal " + fPhotoCounts), false);
        source.sendSuccess(() -> Component.literal(
                "  photoreal shaders: NUKE/BEAM/CRACKS/STAR mapped per warhead; grep DIAG_PHOTOREAL_JSON for machine read."), false);

        // For full scope, also dump a few system-level checks as JSON.
        if ("full".equals(scope) || "all".equals(scope)) {
            try {
                String sysJson = buildSystemJson(ts, level);
                emit("DIAG_SYSTEM_JSON", sysJson);
                if (diagFile != null) {
                    Files.writeString(diagFile, sysJson + System.lineSeparator(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            } catch (Throwable t) {
                ShadowDebug.log("DIAG_SYSTEM_ERROR", "failed: {}", t.getMessage());
            }
        }

        return emittedWarheads;
    }

    private static String buildSystemJson(String ts, ServerLevel level) {
        List<String> parts = new ArrayList<>();
        parts.add("\"diag\":\"system\"");
        parts.add("\"timestamp\":\"" + ts + "\"");
        try {
            parts.add("\"blastMotifRoster\":" + BlastMotif.ROSTER.size());
        } catch (Throwable t) {
            parts.add("\"blastMotifRoster\":null");
        }
        try {
            int warheadCount = com.shadowclangaming.warhead.WarheadType.values().length;
            parts.add("\"warheadTypeCount\":" + warheadCount);
        } catch (Throwable t) {
            parts.add("\"warheadTypeCount\":null");
        }
        try {
            int styleCount = com.shadowclangaming.warhead.BlastStyle.values().length;
            parts.add("\"blastStyleCount\":" + styleCount);
        } catch (Throwable t) {
            parts.add("\"blastStyleCount\":null");
        }
        try {
            var apexVals = com.shadowclangaming.warhead.apex.Apex.values();
            parts.add("\"apexCount\":" + apexVals.length);
        } catch (Throwable t) {
            parts.add("\"apexCount\":null");
        }
        parts.add("\"level\":" + (level == null ? "null" : "\"" + jsonEscape(level.dimension().location().toString()) + "\""));
        // Config snapshot (safe server-side).
        try {
            parts.add("\"config\":{\"fxPackets\":" + com.shadowclangaming.config.SCGConfig.fxPackets()
                    + ",\"fxRange\":" + com.shadowclangaming.config.SCGConfig.fxRange()
                    + ",\"particleScale\":" + com.shadowclangaming.config.SCGConfig.particleScale()
                    + ",\"blastThreads\":" + com.shadowclangaming.config.SCGConfig.blastThreads()
                    + ",\"shadersEnabled\":" + com.shadowclangaming.config.SCGConfig.shadersEnabled()
                    + ",\"shaderStrength\":" + com.shadowclangaming.config.SCGConfig.shaderStrength()
                    + "}");
        } catch (Throwable t) {
            parts.add("\"config\":null");
        }
        // Volumetric status again for system record.
        try {
            var sup = SCGCommands.volumetricStatus;
            String vol = sup == null ? "no_hook" : sup.get();
            parts.add("\"volumetricStatus\":\"" + jsonEscape(vol.replace("\n", " | ")) + "\"");
        } catch (Throwable t) {
            parts.add("\"volumetricStatus\":null");
        }
        return "{" + String.join(",", parts) + "}";
    }

    private static void emit(String category, String json) {
        ShadowDebug.log(category, json);
        ShadowClanGaming.LOGGER.info("[SCG_DIAG] {} {}", category, json);
    }

    private static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "\"" + v + "\"";
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }

    private static String argbHex(int argb) {
        return String.format(java.util.Locale.ROOT, "#%08X", argb);
    }

    private static double luminance(int argb) {
        double r = ((argb >> 16) & 0xFF) / 255.0;
        double g = ((argb >> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static String jsonEscape(String s) {
        if (s == null) return "null";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
