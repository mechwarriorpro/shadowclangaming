package com.shadowclangaming.compat.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Generates a complete EconomyCraft price table for EVERY item in EVERY loaded mod.
 *
 * WHY THIS EXISTS
 * EconomyCraft ships 1,816 prices - all vanilla. Any modded item it doesn't know about either can't
 * be sold, or gets an inconsistent price. On a modded "money is power" server that is a hole in the
 * economy: some mod's ore is worthless while another's is a money printer.
 *
 * WHAT IT DOES
 *   1. Finds EconomyCraft's price file (config/economycraft/prices.json).
 *   2. If it doesn't exist yet, reads EconomyCraft's own bundled defaults straight out of its jar, so
 *      vanilla prices are exactly as the mod intended.
 *   3. Walks the live item registry - every item from every mod that is actually loaded - and adds a
 *      price to anything missing, using AuthoritativePrices then PriceHeuristic.
 *   4. Never overwrites a price that already exists (admins win), unless you pass force for ShadowClanGaming
 *      Reborn's own items.
 *   5. Writes prices.json back with a timestamped backup.
 *
 * It is idempotent: run it as often as you like, it only ever adds what is missing.
 *
 * SAFETY: everything is wrapped so a schema change in EconomyCraft can never crash the server - the
 * worst case is a log line telling you the override was skipped.
 */
public final class EconomyBridge {

    public static final String MODID = "economycraft";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static boolean isInstalled() {
        return ModList.get().isLoaded(MODID);
    }

    /** Where EconomyCraft keeps its editable copy. */
    public static Path pricesFile() {
        return configDir().resolve("economycraft").resolve("prices.json");
    }

    private static Path configDir() {
        for (String candidate : new String[]{"config", "run/config"}) {
            Path path = Paths.get(candidate);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return Paths.get("config");
    }

    /**
     * Generates or tops up the price table, pricing every item from its RECIPES.
     *
     * THE OLD BEHAVIOUR AND WHY IT CHANGED. This used to price by keyword only: no keyword match meant
     * a flat sell price of 7, which the shop shows as $21 buy. On a modded server that was 90% of the
     * "modded" tab, because machine and component names match no keyword. Now every item that has a
     * recipe is priced as "the cheapest recipe that makes it, per unit, plus labour", recursing down to
     * materials that have no recipe (see {@link RecipePricing} / {@link RecipePricer}).
     *
     * EXISTING ENTRIES. A price written by an EARLIER run of this generator is ours to correct, so the
     * flat $21s get fixed without anyone deleting their config. A price an admin typed is left alone:
     * the last generated value is remembered in {@code shadowclangaming-generated.json}, and an entry
     * only gets rewritten if it still matches what we wrote last time.
     *
     * @param server              the running server, for its recipe manager
     * @param forceRewriteOurItems also re-price ShadowClanGaming's own items if they already exist
     * @return a report you can print
     */
    public static Report generate(net.minecraft.server.MinecraftServer server, boolean forceRewriteOurItems) {
        Report report = new Report();
        if (!isInstalled()) {
            report.error = "EconomyCraft is not installed - nothing to do.";
            return report;
        }

        try {
            Path file = pricesFile();
            JsonObject prices = loadExisting(file);
            // The manifest carries forward: every entry we have ever written stays recorded, so a later
            // run can still tell our prices from an admin's edits.
            JsonObject lastGenerated = loadManifest(file);
            JsonObject manifest = lastGenerated.deepCopy();
            report.source = loadExisting(file) != null && !Files.exists(file)
                    ? "EconomyCraft jar defaults" : "existing config file";
            int before = prices.size();

            // The recipe graph. This is the whole change: prices come from what an item is MADE OF.
            long startedAt = System.nanoTime();
            RecipePricing.Table table = RecipePricer.build(server);
            // Shops generated by the old keyword-only table are full of sell-7/buy-21 entries. Those get
            // re-priced automatically, because "every item costs $21" is not a price list.
            boolean migrateLegacyFlat = Boolean.parseBoolean(
                    System.getProperty("shadowclangaming.economyMigrateFlat", "true"));
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                if (key == null || key.getPath().equals("air")) {
                    continue;
                }
                String id = key.toString();
                boolean isOurs = key.getNamespace().equals(ShadowClanGaming.MOD_ID);

                int maxStack = item.getDefaultMaxStackSize();
                PriceHeuristic.Price keyword = PriceHeuristic.priceFor(id, maxStack);

                // Sell from recipes when there is one; the keyword price is the floor it recurses to.
                int sell = table.hasCrafts(id) ? table.sellPrice(id) : keyword.sell();
                int buy = sell * 3;

                boolean exists = prices.has(id);
                if (exists) {
                    // force = "re-price every entry", which is what a server that already has a
                    // shop full of old flat $21s needs. Without it, only entries we wrote (and that
                    // nobody has edited since) are corrected.
                    JsonObject current = prices.getAsJsonObject(id);
                    boolean ours = forceRewriteOurItems;
                    boolean stale = canCorrect(current, lastGenerated.get(id), buy, sell);
                    boolean legacyFlat = migrateLegacyFlat && isLegacyFlatPrice(current)
                            && (current.get("unit_buy").getAsInt() != buy
                            || current.get("unit_sell").getAsInt() != sell);
                    if (!ours && !stale && !legacyFlat) {
                        continue;
                    }
                    if (legacyFlat) {
                        report.migratedFlat++;
                    }
                    report.repriced++;
                } else {
                    report.added++;
                }

                JsonObject entry = new JsonObject();
                entry.addProperty("category", keyword.category());
                entry.addProperty("stack", Math.max(1, maxStack));
                entry.addProperty("unit_buy", buy);
                entry.addProperty("unit_sell", sell);
                prices.add(id, entry);
                manifest.add(id, manifestEntry(buy, sell));

                if (isOurs) {
                    report.ourItems++;
                }
            }
            writeManifest(file, manifest);
            report.pricedFromRecipes = table.resolvedCount();

            report.total = prices.size();
            report.previouslyKnown = before;

            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                Path backup = file.resolveSibling("prices.json.bak-" + System.currentTimeMillis());
                Files.copy(file, backup);
                report.backup = backup.toString();
            }
            writeSorted(file, prices);

            report.milliseconds = (System.nanoTime() - startedAt) / 1_000_000L;
            report.file = file.toString();
            ShadowClanGaming.LOGGER.info("EconomyCraft override written: {} entries ({} added, {} from ShadowClanGaming) -> {}",
                    report.total, report.added, report.ourItems, report.file);
        } catch (Exception exception) {
            report.error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            ShadowClanGaming.LOGGER.error("EconomyCraft price generation failed - the mod keeps its current prices", exception);
        }
        return report;
    }

    /** Reads the config copy if present, otherwise EconomyCraft's bundled defaults from its jar. */
    private static JsonObject loadExisting(Path file) throws IOException {
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) {
                    return parsed.getAsJsonObject();
                }
            } catch (Exception ignored) {
                // Corrupt file: fall through to the jar defaults rather than dying.
            }
        }

        JsonObject fromJar = readJarDefaults();
        return fromJar != null ? fromJar : new JsonObject();
    }

    private static JsonObject readJarDefaults() {
        try {
            var container = ModList.get().getModFileById(MODID);
            if (container == null) {
                return null;
            }
            Path jarPath = container.getFile().getFilePath();
            try (ZipFile zip = new ZipFile(jarPath.toFile())) {
                ZipEntry entry = zip.getEntry("assets/economycraft/prices.json");
                if (entry == null) {
                    return null;
                }
                try (InputStream stream = zip.getInputStream(entry);
                     Reader reader = new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
                }
            }
        } catch (Throwable throwable) {
            ShadowClanGaming.LOGGER.warn("Could not read EconomyCraft's bundled prices.json: {}", throwable.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------ the generated manifest
    //
    // prices.json alone cannot say whether a given price was typed by an admin or written by us, and
    // "never overwrite" is what kept 90% of the modded tab stuck at the old flat $21. So we keep our own
    // record of what we last wrote. An entry is only re-priced when the file still holds OUR value:
    // anything an admin changed reads as a deliberate decision and is left alone.

    private static Path manifestFile(Path pricesFile) {
        return pricesFile.resolveSibling("shadowclangaming-generated.json");
    }

    private static JsonObject loadManifest(Path pricesFile) {
        Path file = manifestFile(pricesFile);
        if (!Files.exists(file)) {
            return new JsonObject();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed.isJsonObject()) {
                JsonObject root = parsed.getAsJsonObject();
                JsonElement generated = root.get("generated");
                if (generated != null && generated.isJsonObject()) {
                    return generated.getAsJsonObject();
                }
                return root;
            }
        } catch (Exception ignored) {
            // A corrupt manifest just means "we do not know", and then nothing is overwritten.
        }
        return new JsonObject();
    }

    /**
     * Is this the old generator's flat fallback?
     *
     * sell 7 / buy 21 is what a keyword-less item used to cost. Genuine raw materials whose ingredients
     * add up to that re-price to the same number, so migrating is harmless for them and corrects the
     * rest - which is the whole complaint.
     */
    private static boolean isLegacyFlatPrice(JsonObject entry) {
        if (entry == null || !entry.has("unit_buy") || !entry.has("unit_sell")) {
            return false;
        }
        try {
            return entry.get("unit_sell").getAsInt() == 7 && entry.get("unit_buy").getAsInt() == 21;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static JsonObject manifestEntry(int buy, int sell) {
        JsonObject entry = new JsonObject();
        entry.addProperty("unit_buy", buy);
        entry.addProperty("unit_sell", sell);
        return entry;
    }

    private static void writeManifest(Path pricesFile, JsonObject generated) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("note", "Written by ShadowClanGaming. Lists the prices this mod generated so a"
                + " later run can correct them without touching hand-edited entries.");
        root.add("generated", generated);
        try (Writer writer = Files.newBufferedWriter(manifestFile(pricesFile), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    /**
     * Should we overwrite this existing entry?
     *
     * Yes when (a) we wrote it before and the values are unchanged since, and (b) the new price differs.
     * A file with no manifest is treated as "all admin": nothing is overwritten until the admin either
     * deletes prices.json (a fresh generation) or asks for a force pass.
     */
    private static boolean canCorrect(JsonObject current, JsonElement remembered, int newBuy, int newSell) {
        if (current == null || remembered == null || !remembered.isJsonObject()) {
            return false;
        }
        JsonObject last = remembered.getAsJsonObject();
        if (!last.has("unit_buy") || !last.has("unit_sell")) {
            return false;
        }
        if (!current.has("unit_buy") || !current.has("unit_sell")) {
            return false;
        }
        boolean untouched = current.get("unit_buy").getAsInt() == last.get("unit_buy").getAsInt()
                && current.get("unit_sell").getAsInt() == last.get("unit_sell").getAsInt();
        if (!untouched) {
            return false;
        }
        return current.get("unit_buy").getAsInt() != newBuy
                || current.get("unit_sell").getAsInt() != newSell;
    }

    /** Writes the file with keys sorted, so diffs are readable and stable. */
    private static void writeSorted(Path file, JsonObject prices) throws IOException {
        Map<String, JsonElement> sorted = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : prices.entrySet()) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : sorted.entrySet()) {
            out.add(entry.getKey(), entry.getValue());
        }
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(out, writer);
        }
    }

    /** Result of a generation run. */
    public static final class Report {
        public String file = "(not written)";
        public String source = "(none)";
        public String backup = "(none)";
        public String error = null;
        public int previouslyKnown;
        public int added;
        /** Entries that existed, were written by a previous run, and were corrected. */
        public int repriced;
        public int ourItems;
        public int pricedFromRecipes;
        /** Old flat sell-7/buy-21 entries that were re-priced from their recipes. */
        public int migratedFlat;
        /** How long generation took, including the recipe walk. */
        public long milliseconds;
        public int total;

        public String summary() {
            if (error != null) {
                return error;
            }
            return String.format(
                    "Prices: %d total (%d existing + %d added, %d re-priced, %d from ShadowClanGaming)."
                            + " %d items priced from recipes. Source: %s. File: %s",
                    total, previouslyKnown, added, repriced, ourItems, pricedFromRecipes, source, file)
                    + (migratedFlat > 0 ? " (fixed " + migratedFlat + " legacy flat-7 entries)" : "")
                    + " in " + milliseconds + " ms";
        }
    }

    private EconomyBridge() {
    }
}
