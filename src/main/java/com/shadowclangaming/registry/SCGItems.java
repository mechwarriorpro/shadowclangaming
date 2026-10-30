package com.shadowclangaming.registry;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.armor.ArmorSet;
import com.shadowclangaming.computer.CpuTier;
import com.shadowclangaming.computer.GpuTier;
import com.shadowclangaming.item.BallisticsCalculatorItem;
import com.shadowclangaming.item.HardwareItem;
import com.shadowclangaming.item.FrequencyChipItem;
import com.shadowclangaming.item.TrackerItem;
import com.shadowclangaming.item.GunItem;
import com.shadowclangaming.item.SetArmorItem;
import com.shadowclangaming.item.UpgradeKitItem;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import com.shadowclangaming.item.BankCardItem;
import com.shadowclangaming.item.ChalkItem;
import com.shadowclangaming.item.ExplosiveGrenadeItem;
import com.shadowclangaming.item.HandLauncherItem;
import com.shadowclangaming.item.WarheadMinecartItem;
import com.shadowclangaming.item.HolyNecklaceItem;
import com.shadowclangaming.item.KamikazeVestItem;
import com.shadowclangaming.item.HolyWaterItem;
import com.shadowclangaming.item.ElementVialItem;
import com.shadowclangaming.item.MissileItem;
import com.shadowclangaming.item.MissileModuleItem;
import com.shadowclangaming.launcher.ModuleTier;
import com.shadowclangaming.item.SpellRifleItem;
import com.shadowclangaming.item.TargetDesignatorItem;
import com.shadowclangaming.item.TraderBlockItem;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registration.
 *
 * Missiles are all MissileItem(properties, warhead, cruiseSpeed[, powerScale]).
 * cruiseSpeed is blocks-per-tick steering speed: 0.75 is slow and heavy, 1.2 is a sprint.
 */
public final class SCGItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShadowClanGaming.MOD_ID);

    // ------------------------------------------------------------------ missiles

    /** Short range HE. Cheap, small boom. */
    public static final DeferredItem<MissileItem> SRBM = ITEMS.registerItem(
            "srbm", props -> new MissileItem(props, WarheadType.HE, 0.75D, 0.5F),
            new Item.Properties().stacksTo(16));

    /** Medium range HE - the workhorse. */
    public static final DeferredItem<MissileItem> MRBM = ITEMS.registerItem(
            "mrbm", props -> new MissileItem(props, WarheadType.HE, 0.9D, 1.0F),
            new Item.Properties().stacksTo(8));

    /** Intercontinental HE. */
    public static final DeferredItem<MissileItem> ICBM = ITEMS.registerItem(
            "icbm", props -> new MissileItem(props, WarheadType.HE, 1.05D, 2.0F),
            new Item.Properties().stacksTo(4));

    /** Zero damage, full noise. For making people panic on the radar. */
    public static final DeferredItem<MissileItem> DECOY_MISSILE = ITEMS.registerItem(
            "decoy_missile", props -> new MissileItem(props, WarheadType.DECOY, 0.95D),
            new Item.Properties().stacksTo(16));

    /** No block damage; kills launch pads, chemistry machines and designators in the area. */
    public static final DeferredItem<MissileItem> EMP_MISSILE = ITEMS.registerItem(
            "emp_missile", props -> new MissileItem(props, WarheadType.EMP, 0.9D),
            new Item.Properties().stacksTo(8));

    /** Small blast, big fire. */
    public static final DeferredItem<MissileItem> INCENDIARY_MISSILE = ITEMS.registerItem(
            "incendiary_missile", props -> new MissileItem(props, WarheadType.INCENDIARY, 0.9D),
            new Item.Properties().stacksTo(8));

    /** Six bomblets in a ring. */
    public static final DeferredItem<MissileItem> CLUSTER_MISSILE = ITEMS.registerItem(
            "cluster_missile", props -> new MissileItem(props, WarheadType.CLUSTER, 0.9D),
            new Item.Properties().stacksTo(8));

    /** Built from Enriched Uranium. Big blast, fire, lingering sickness. */
    public static final DeferredItem<MissileItem> FISSION_MISSILE = ITEMS.registerItem(
            "fission_missile", props -> new MissileItem(props, WarheadType.FISSION, 0.95D),
            new Item.Properties().stacksTo(8));

    /** Built from Tritium. Bigger, blinding. */
    public static final DeferredItem<MissileItem> FUSION_MISSILE = ITEMS.registerItem(
            "fusion_missile", props -> new MissileItem(props, WarheadType.FUSION, 1.15D),
            new Item.Properties().stacksTo(4));

    /** The end of the tech tree. mind the TPS. */
    public static final DeferredItem<MissileItem> ANTIMATTER_MISSILE = ITEMS.registerItem(
            "antimatter_missile", props -> new MissileItem(props, WarheadType.ANTIMATTER, 1.2D),
            new Item.Properties().stacksTo(2));

    /** Blast radius = how much currency you loaded. See warhead/Funding. */
    public static final DeferredItem<MissileItem> NUMISMATIC_MISSILE = ITEMS.registerItem(
            "numismatic_missile", props -> new MissileItem(props, WarheadType.NUMISMATIC, 0.95D),
            new Item.Properties().stacksTo(4));

    /** Money buys a containment dome: size and duration both scale with funding. See warhead/ForceFields. */
    public static final DeferredItem<MissileItem> BULWARK_MISSILE = ITEMS.registerItem(
            "bulwark_missile", props -> new MissileItem(props, WarheadType.BULWARK, 0.95D),
            new Item.Properties().stacksTo(4));

    /** Modest blast, permanent problem: a plague that spreads and escalates. See warhead/Outbreak. */
    public static final DeferredItem<MissileItem> CONTAGION_MISSILE = ITEMS.registerItem(
            "contagion_missile", props -> new MissileItem(props, WarheadType.CONTAGION, 0.9D),
            new Item.Properties().stacksTo(4));

    /**
     * The spell warhead. Does no damage of its own: it fires a COPY of an inscribed spell at the
     * impact point, scaled up. Loading the spell costs money and XP at the inscription table.
     */
    public static final DeferredItem<MissileItem> SPELL_MISSILE = ITEMS.registerItem(
            "spell_missile", props -> new MissileItem(props, WarheadType.SPELL, 0.95D),
            new Item.Properties().stacksTo(8));

    /**
     * A written spell. Inscribed at the table from a BOOK AND QUILL, then either loaded into a rifle or
     * a spell warhead, or cast straight out of the page.
     */
    public static final DeferredItem<com.shadowclangaming.item.SpellBlueprintItem> SPELL_BLUEPRINT =
            ITEMS.registerItem("spell_blueprint", com.shadowclangaming.item.SpellBlueprintItem::new,
                    new Item.Properties().stacksTo(16));

    // ------------------------------------------------------------------ guns

    /**
     * The procedural gun. Every one of these is a different weapon - see gun/GunForge. This item
     * exists once in the registry; the variety lives in the GUN data component.
     */
    public static final DeferredItem<GunItem> GUN = ITEMS.registerItem(
            "gun", GunItem::new, new Item.Properties().stacksTo(1));

    /** Feed one of these to the Fabricator for a roll. */
    public static final DeferredItem<Item> GUN_SCRAP = ITEMS.registerSimpleItem(
            "gun_scrap", new Item.Properties().stacksTo(64));

    /** One reload. Consumed from the inventory when a gun runs dry. */
    public static final DeferredItem<Item> GUN_CELL = ITEMS.registerSimpleItem(
            "gun_cell", new Item.Properties().stacksTo(64));

    // ------------------------------------------------------------------ computers

    public static final DeferredItem<BlockItem> TERMINAL = ITEMS.registerSimpleBlockItem(SCGBlocks.TERMINAL);

    /** Puts a launch pad and a terminal on the same channel. */
    public static final DeferredItem<FrequencyChipItem> FREQUENCY_CHIP = ITEMS.registerItem(
            "frequency_chip", FrequencyChipItem::new, new Item.Properties().stacksTo(1));

    /** Cards. Each one unlocks one family of terminal commands. */
    public static final DeferredItem<Item> TARGETING_CARD = ITEMS.registerSimpleItem(
            "targeting_card", new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> MANA_CARD = ITEMS.registerSimpleItem(
            "mana_card", new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> NET_CARD = ITEMS.registerSimpleItem(
            "net_card", new Item.Properties().stacksTo(1));

    /** Reads AE2 (or plain containers) through a storage bridge. */
    public static final DeferredItem<Item> STORAGE_CARD = ITEMS.registerSimpleItem(
            "storage_card", new Item.Properties().stacksTo(1));

    /** Six redstone outputs and one input. */
    public static final DeferredItem<Item> REDSTONE_CARD = ITEMS.registerSimpleItem(
            "redstone_card", new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> RADAR_CARD = ITEMS.registerSimpleItem(
            "radar_card", new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> CANNON_CARD = ITEMS.registerSimpleItem(
            "cannon_card", new Item.Properties().stacksTo(1));

    // ------------------------------------------------------------------ hardware
    //
    // Eight modules, four CPUs and four GPUs. They are ordinary items: the numbers live in CpuTier and
    // GpuTier, so adding a tier later is an enum line and a lang key rather than a new class.

    public static final DeferredItem<HardwareItem> CPU_BASIC = ITEMS.registerItem("cpu_basic",
            props -> HardwareItem.cpu(CpuTier.BASIC, props), new Item.Properties().stacksTo(16));
    public static final DeferredItem<HardwareItem> CPU_ADVANCED = ITEMS.registerItem("cpu_advanced",
            props -> HardwareItem.cpu(CpuTier.ADVANCED, props), new Item.Properties().stacksTo(16));
    public static final DeferredItem<HardwareItem> CPU_QUANTUM = ITEMS.registerItem("cpu_quantum",
            props -> HardwareItem.cpu(CpuTier.QUANTUM, props), new Item.Properties().stacksTo(16));
    public static final DeferredItem<HardwareItem> CPU_PHOTONIC = ITEMS.registerItem("cpu_photonic",
            props -> HardwareItem.cpu(CpuTier.PHOTONIC, props), new Item.Properties().stacksTo(16));

    public static final DeferredItem<HardwareItem> GPU_BASIC = ITEMS.registerItem("gpu_basic",
            props -> HardwareItem.gpu(GpuTier.BASIC, props), new Item.Properties().stacksTo(16));
    public static final DeferredItem<HardwareItem> GPU_VECTOR = ITEMS.registerItem("gpu_vector",
            props -> HardwareItem.gpu(GpuTier.VECTOR, props), new Item.Properties().stacksTo(16));
    public static final DeferredItem<HardwareItem> GPU_TENSOR = ITEMS.registerItem("gpu_tensor",
            props -> HardwareItem.gpu(GpuTier.TENSOR, props), new Item.Properties().stacksTo(16));
    public static final DeferredItem<HardwareItem> GPU_PHOTONIC = ITEMS.registerItem("gpu_photonic",
            props -> HardwareItem.gpu(GpuTier.PHOTONIC, props), new Item.Properties().stacksTo(16));

    // ------------------------------------------------------------------ armour sets

    private static DeferredItem<SetArmorItem> setArmor(String id, ArmorItem.Type type,
                                                       Holder<ArmorMaterial> material, ArmorSet set) {
        return ITEMS.registerItem(id, props -> new SetArmorItem(material, type, props, set),
                new Item.Properties().durability(type.getDurability(28)));
    }

    public static final DeferredItem<SetArmorItem> INFAMY_HELMET =
            setArmor("infamy_helmet", ArmorItem.Type.HELMET, SCGArmorMaterials.INFAMY, ArmorSet.INFAMY);
    public static final DeferredItem<SetArmorItem> INFAMY_CHESTPLATE =
            setArmor("infamy_chestplate", ArmorItem.Type.CHESTPLATE, SCGArmorMaterials.INFAMY, ArmorSet.INFAMY);
    public static final DeferredItem<SetArmorItem> INFAMY_LEGGINGS =
            setArmor("infamy_leggings", ArmorItem.Type.LEGGINGS, SCGArmorMaterials.INFAMY, ArmorSet.INFAMY);
    public static final DeferredItem<SetArmorItem> INFAMY_BOOTS =
            setArmor("infamy_boots", ArmorItem.Type.BOOTS, SCGArmorMaterials.INFAMY, ArmorSet.INFAMY);

    public static final DeferredItem<SetArmorItem> SENTINEL_HELMET =
            setArmor("sentinel_helmet", ArmorItem.Type.HELMET, SCGArmorMaterials.SENTINEL, ArmorSet.SENTINEL);
    public static final DeferredItem<SetArmorItem> SENTINEL_CHESTPLATE =
            setArmor("sentinel_chestplate", ArmorItem.Type.CHESTPLATE, SCGArmorMaterials.SENTINEL, ArmorSet.SENTINEL);
    public static final DeferredItem<SetArmorItem> SENTINEL_LEGGINGS =
            setArmor("sentinel_leggings", ArmorItem.Type.LEGGINGS, SCGArmorMaterials.SENTINEL, ArmorSet.SENTINEL);
    public static final DeferredItem<SetArmorItem> SENTINEL_BOOTS =
            setArmor("sentinel_boots", ArmorItem.Type.BOOTS, SCGArmorMaterials.SENTINEL, ArmorSet.SENTINEL);

    public static final DeferredItem<SetArmorItem> RAIDER_HELMET =
            setArmor("raider_helmet", ArmorItem.Type.HELMET, SCGArmorMaterials.RAIDER, ArmorSet.RAIDER);
    public static final DeferredItem<SetArmorItem> RAIDER_CHESTPLATE =
            setArmor("raider_chestplate", ArmorItem.Type.CHESTPLATE, SCGArmorMaterials.RAIDER, ArmorSet.RAIDER);
    public static final DeferredItem<SetArmorItem> RAIDER_LEGGINGS =
            setArmor("raider_leggings", ArmorItem.Type.LEGGINGS, SCGArmorMaterials.RAIDER, ArmorSet.RAIDER);
    public static final DeferredItem<SetArmorItem> RAIDER_BOOTS =
            setArmor("raider_boots", ArmorItem.Type.BOOTS, SCGArmorMaterials.RAIDER, ArmorSet.RAIDER);

    // ------------------------------------------------------------------ new warheads

    /** Drops a chest of cargo where it lands. Destroys nothing. */
    public static final DeferredItem<MissileItem> CARGO_MISSILE = ITEMS.registerItem(
            "cargo_missile", props -> new MissileItem(props, WarheadType.CARGO, 0.85D),
            new Item.Properties().stacksTo(4));

    /** Radius 32 + funding/250, 10% of ground columns get a mine. */
    public static final DeferredItem<MissileItem> MINEFIELD_MISSILE = ITEMS.registerItem(
            "minefield_missile", props -> new MissileItem(props, WarheadType.MINEFIELD, 0.9D),
            new Item.Properties().stacksTo(4));

    /** Fills a sphere with the blocks loaded into it. Radius from the payload or the pad slider. */
    public static final DeferredItem<MissileItem> CONSTRUCTOR_MISSILE = ITEMS.registerItem(
            "constructor_missile", props -> new MissileItem(props, WarheadType.CONSTRUCTOR, 0.9D),
            new Item.Properties().stacksTo(4));

    /** Loads the ground it lands on into an AE2 spatial cell. */
    public static final DeferredItem<MissileItem> SPATIAL_MISSILE = ITEMS.registerItem(
            "spatial_missile", props -> new MissileItem(props, WarheadType.SPATIAL, 0.9D),
            new Item.Properties().stacksTo(4));

    /** The Mother Clucker: fifty turret chickens and the eggs they lay. */
    public static final DeferredItem<MissileItem> CLUCKER_MISSILE = ITEMS.registerItem(
            "clucker_missile", props -> new MissileItem(props, WarheadType.CLUCKER, 0.95D),
            new Item.Properties().stacksTo(4));

    /** The Violator: a triple-health wither whose skulls hit five times as hard. */
    public static final DeferredItem<MissileItem> VIOLATOR_MISSILE = ITEMS.registerItem(
            "violator_missile", props -> new MissileItem(props, WarheadType.VIOLATOR, 0.85D),
            new Item.Properties().stacksTo(4));

    /** Beef Supreme: a 5000-health cow with a boss bar and a telegraph.
     *  Built through the registered entity type so its attributes are the ones we registered. */
    public static final DeferredItem<MissileItem> BEEF_MISSILE = ITEMS.registerItem(
            "beef_missile", props -> new MissileItem(props, WarheadType.BEEF, 0.8D),
            new Item.Properties().stacksTo(4));

    // ------------------------------------------------------------------ missile modules
    //
    // A module is an EMPTY airframe: it flies, it has a tier, and what it does when it lands depends
    // entirely on the warhead fitted to it. T1 reaches 10,000 blocks; T5 has no range limit at all.
    // Any warhead fits any module, so the tier is a range decision and nothing else.

    /** 10,000 blocks. The cheap frame. */
    public static final DeferredItem<MissileModuleItem> MISSILE_MODULE_T1 = ITEMS.registerItem(
            "missile_module_t1", props -> new MissileModuleItem(props, ModuleTier.T1),
            new Item.Properties().stacksTo(4));

    /** 25,000 blocks. */
    public static final DeferredItem<MissileModuleItem> MISSILE_MODULE_T2 = ITEMS.registerItem(
            "missile_module_t2", props -> new MissileModuleItem(props, ModuleTier.T2),
            new Item.Properties().stacksTo(4));

    /** 50,000 blocks. */
    public static final DeferredItem<MissileModuleItem> MISSILE_MODULE_T3 = ITEMS.registerItem(
            "missile_module_t3", props -> new MissileModuleItem(props, ModuleTier.T3),
            new Item.Properties().stacksTo(4));

    /** 150,000 blocks. */
    public static final DeferredItem<MissileModuleItem> MISSILE_MODULE_T4 = ITEMS.registerItem(
            "missile_module_t4", props -> new MissileModuleItem(props, ModuleTier.T4),
            new Item.Properties().stacksTo(4));

    /** No limit. Fire it at anything, in any direction, and it gets there. */
    public static final DeferredItem<MissileModuleItem> MISSILE_MODULE_T5 = ITEMS.registerItem(
            "missile_module_t5", props -> new MissileModuleItem(props, ModuleTier.T5),
            new Item.Properties().stacksTo(4));

    // ------------------------------------------------------------------ special frames
    //
    // Every frame kind comes in all five tiers. That is the point: "what does it do" and "how far does it
    // go" are independent choices, so a T5 homing frame is a strategic weapon that follows you, and a T1
    // homing frame is a short-range one that does the same thing badly. 25 modules in total, 5 kinds x 5
    // tiers, and the only other rule is which warheads a frame will carry (see launcher/ModuleKind).

    /** The tier order used when registering a kind's five airframes. */
    private static final com.shadowclangaming.launcher.ModuleTier[] MODULE_TIERS = {
            com.shadowclangaming.launcher.ModuleTier.T1,
            com.shadowclangaming.launcher.ModuleTier.T2,
            com.shadowclangaming.launcher.ModuleTier.T3,
            com.shadowclangaming.launcher.ModuleTier.T4,
            com.shadowclangaming.launcher.ModuleTier.T5};

    /**
     * Register one frame kind at all five tiers: {@code missile_module_<kind>_t1 ... _t5}.
     *
     * A loop rather than 20 fields, because 20 near-identical fields is 20 chances to copy-paste the wrong
     * tier. The id carries both facts (kind and tier) so a datapack author can write recipes without
     * hunting through the registry.
     */
    private static java.util.List<DeferredItem<MissileModuleItem>> registerModuleKind(
            String prefix, com.shadowclangaming.launcher.ModuleKind kind) {
        java.util.List<DeferredItem<MissileModuleItem>> list = new java.util.ArrayList<>(5);
        for (int i = 0; i < MODULE_TIERS.length; i++) {
            final com.shadowclangaming.launcher.ModuleTier tier = MODULE_TIERS[i];
            list.add(ITEMS.registerItem("missile_module_" + prefix + "_t" + (i + 1),
                    props -> new MissileModuleItem(props, tier, kind),
                    new Item.Properties().stacksTo(4)));
        }
        return java.util.List.copyOf(list);
    }

    /** Seeker frames: re-aim at a moving target in flight. T1 10k ... T5 unlimited. */
    public static final java.util.List<DeferredItem<MissileModuleItem>> MISSILE_MODULE_HOMING =
            registerModuleKind("homing", com.shadowclangaming.launcher.ModuleKind.HOMING);

    /** Area frames: break into six sub-munitions before impact. */
    public static final java.util.List<DeferredItem<MissileModuleItem>> MISSILE_MODULE_CLUSTER =
            registerModuleKind("cluster", com.shadowclangaming.launcher.ModuleKind.CLUSTER);

    /** Sprint frames: 2.4x the speed. */
    public static final java.util.List<DeferredItem<MissileModuleItem>> MISSILE_MODULE_HIGH_VELOCITY =
            registerModuleKind("high_velocity", com.shadowclangaming.launcher.ModuleKind.HIGH_VELOCITY);

    /** Personnel frames: carry crew, not a warhead. No blast, no fall damage. */
    public static final java.util.List<DeferredItem<MissileModuleItem>> MISSILE_MODULE_TRANSPORT =
            registerModuleKind("transport", com.shadowclangaming.launcher.ModuleKind.TRANSPORT);

    /** Every special frame, for the creative tab and for anything that iterates the line. */
    public static final java.util.List<java.util.List<DeferredItem<MissileModuleItem>>> MISSILE_MODULE_SPECIALS =
            java.util.List.of(MISSILE_MODULE_HOMING, MISSILE_MODULE_CLUSTER,
                    MISSILE_MODULE_HIGH_VELOCITY, MISSILE_MODULE_TRANSPORT);

    /** Homes onto whatever a Tracker locked onto the pad. */
    public static final DeferredItem<MissileItem> TRACKING_MISSILE = ITEMS.registerItem(
            "tracking_missile", props -> new MissileItem(props, WarheadType.TRACKING, 1.1D),
            new Item.Properties().stacksTo(8));

    // ------------------------------------------------------------------ apex warheads
    //
    // Seventeen endgame weapons. Each one is a staged detonation (see warhead/apex), so the item is the
    // boring part - the behaviour is behind WarheadType's apex hand-off. Stack size 1: these are not
    // ammunition, they are events.

    /** A black hole that eats the battlefield. */
    public static final DeferredItem<MissileItem> WORLD_EATER_MISSILE = ITEMS.registerItem(
            "world_eater_missile", props -> new MissileItem(props, WarheadType.WORLD_EATER, 0.8D),
            new Item.Properties().stacksTo(1));

    /** An artificial star that brightens and detonates. */
    public static final DeferredItem<MissileItem> STELLAR_REQUIEM_MISSILE = ITEMS.registerItem(
            "stellar_requiem_missile", props -> new MissileItem(props, WarheadType.STELLAR_REQUIEM, 0.8D),
            new Item.Properties().stacksTo(1));

    /** A tear in spacetime. */
    public static final DeferredItem<MissileItem> REALITY_BREAKER_MISSILE = ITEMS.registerItem(
            "reality_breaker_missile", props -> new MissileItem(props, WarheadType.REALITY_BREAKER, 0.8D),
            new Item.Properties().stacksTo(1));

    /** Successive gravitational shockwaves. */
    public static final DeferredItem<MissileItem> SPACETIME_HAMMER_MISSILE = ITEMS.registerItem(
            "spacetime_hammer_missile", props -> new MissileItem(props, WarheadType.SPACETIME_HAMMER, 0.8D),
            new Item.Properties().stacksTo(1));

    /** A spherical collapse into a core, then a pulse. */
    public static final DeferredItem<MissileItem> UNIVERSAL_IMPLODER_MISSILE = ITEMS.registerItem(
            "universal_imploder_missile", props -> new MissileItem(props, WarheadType.UNIVERSAL_IMPLODER, 0.8D),
            new Item.Properties().stacksTo(1));

    /** Meteors, spectral lightning, and a blood-red sky. */
    public static final DeferredItem<MissileItem> RAGNAROK_MISSILE = ITEMS.registerItem(
            "ragnarok_missile", props -> new MissileItem(props, WarheadType.RAGNAROK, 0.8D),
            new Item.Properties().stacksTo(1));

    /** A rune engraved into the land, then detonated. */
    public static final DeferredItem<MissileItem> WORLD_RUNE_MISSILE = ITEMS.registerItem(
            "world_rune_missile", props -> new MissileItem(props, WarheadType.WORLD_RUNE, 0.8D),
            new Item.Properties().stacksTo(1));

    /** Portals into somewhere else, and tendrils that drag the world through. */
    public static final DeferredItem<MissileItem> ABYSSAL_COVENANT_MISSILE = ITEMS.registerItem(
            "abyssal_covenant_missile", props -> new MissileItem(props, WarheadType.ABYSSAL_COVENANT, 0.8D),
            new Item.Properties().stacksTo(1));

    /** A vortex that eats the living and grows with every soul. */
    public static final DeferredItem<MissileItem> SOUL_FURNACE_MISSILE = ITEMS.registerItem(
            "soul_furnace_missile", props -> new MissileItem(props, WarheadType.SOUL_FURNACE, 0.8D),
            new Item.Properties().stacksTo(1));

    /** A magical sphere ringed with spell circles. */
    public static final DeferredItem<MissileItem> ARCANE_SINGULARITY_MISSILE = ITEMS.registerItem(
            "arcane_singularity_missile", props -> new MissileItem(props, WarheadType.ARCANE_SINGULARITY, 0.8D),
            new Item.Properties().stacksTo(1));

    /** A quasar jet that carves a canyon. */
    public static final DeferredItem<MissileItem> QUASAR_LANCE_MISSILE = ITEMS.registerItem(
            "quasar_lance_missile", props -> new MissileItem(props, WarheadType.QUASAR_LANCE, 0.8D),
            new Item.Properties().stacksTo(1));

    /** An artificial star whose corona melts the ground. */
    public static final DeferredItem<MissileItem> STARFORGE_MISSILE = ITEMS.registerItem(
            "starforge_missile", props -> new MissileItem(props, WarheadType.STARFORGE, 0.8D),
            new Item.Properties().stacksTo(1));

    /** A point of light that becomes a sphere of energy. */
    public static final DeferredItem<MissileItem> BIG_BANG_SEED_MISSILE = ITEMS.registerItem(
            "big_bang_seed_missile", props -> new MissileItem(props, WarheadType.BIG_BANG_SEED, 0.8D),
            new Item.Properties().stacksTo(1));

    /** Corrupted geometry and deleted land. */
    public static final DeferredItem<MissileItem> REALITY_CORRUPTOR_MISSILE = ITEMS.registerItem(
            "reality_corruptor_missile", props -> new MissileItem(props, WarheadType.REALITY_CORRUPTOR, 0.8D),
            new Item.Properties().stacksTo(1));

    /** A motionless core that absorbs everything, then releases it. */
    public static final DeferredItem<MissileItem> ABSOLUTE_ZERO_MISSILE = ITEMS.registerItem(
            "absolute_zero_missile", props -> new MissileItem(props, WarheadType.ABSOLUTE_ZERO, 0.8D),
            new Item.Properties().stacksTo(1));

    /** An annihilation front and a secondary implosion. */
    public static final DeferredItem<MissileItem> OMEGA_MISSILE = ITEMS.registerItem(
            "omega_missile", props -> new MissileItem(props, WarheadType.OMEGA, 0.8D),
            new Item.Properties().stacksTo(1));

    /** Seven rings, dimensional fractures, and a great deal of missing map. */
    public static final DeferredItem<MissileItem> ABSOLUTE_ANNIHILATION_MISSILE = ITEMS.registerItem(
            "absolute_annihilation_missile", props -> new MissileItem(props, WarheadType.ABSOLUTE_ANNIHILATION, 0.8D),
            new Item.Properties().stacksTo(1));

    // ------------------------------------------------------------------ launcher hardware

    /** Show it a launch pad: full read-out. Use it in the air: the funding/radius table. */
    public static final DeferredItem<BallisticsCalculatorItem> BALLISTICS_CALCULATOR = ITEMS.registerItem(
            "ballistics_calculator", BallisticsCalculatorItem::new, new Item.Properties().stacksTo(1));

    /** Lock an entity, then hand the lock to a pad. */
    public static final DeferredItem<TrackerItem> TRACKER = ITEMS.registerItem(
            "tracker", TrackerItem::new, new Item.Properties().stacksTo(1));

    /** Raise a pad, control panel or support rod one tier. Right-click air to set the kit's tier. */
    public static final DeferredItem<Item> UPGRADE_KIT = ITEMS.registerItem(
            "upgrade_kit", UpgradeKitItem::new, new Item.Properties().stacksTo(1));

    /** Level 5 launcher only: lets a warhead be aimed at another dimension. */
    public static final DeferredItem<Item> INTERDIMENSIONAL_MODULE = ITEMS.registerSimpleItem(
            "interdimensional_module", new Item.Properties().stacksTo(1));

    // ------------------------------------------------------------------ magic

    /**
     * Not craftable. Built block by block inside a blueprint volume (see BlueprintTableBlockEntity),
     * which is the only thing that decides its tier and efficiency.
     */
    public static final DeferredItem<SpellRifleItem> SPELL_RIFLE = ITEMS.registerItem(
            "spell_rifle", SpellRifleItem::new, new Item.Properties().stacksTo(1));

    // ------------------------------------------------------------------ packaged warheads
    //
    // Every warhead also exists as a block, a grenade and a minecart. All three families are generated from
    // WarheadType.values() so the roster is the single source of truth: add a warhead, and it gets all
    // three forms with no extra bookkeeping. Blocks get their BlockItems here rather than in SCGBlocks
    // because an item is what a player holds.

    /** {@code grenade_<warhead-id>} for every packable warhead. */
    public static final java.util.List<DeferredItem<ExplosiveGrenadeItem>> EXPLOSIVE_GRENADES =
            registerGrenades();

    /** {@code minecart_<warhead-id>} for every packable warhead. */
    public static final java.util.Map<String, DeferredItem<WarheadMinecartItem>> EXPLOSIVE_MINECARTS =
            registerMinecarts();

    /** The block item for each {@code explosive_<warhead-id>} block. */
    public static final java.util.Map<String, DeferredItem<net.minecraft.world.item.BlockItem>>
            EXPLOSIVE_BLOCK_ITEMS = registerExplosiveBlockItems();

    private static java.util.List<DeferredItem<ExplosiveGrenadeItem>> registerGrenades() {
        java.util.List<DeferredItem<ExplosiveGrenadeItem>> list = new java.util.ArrayList<>();
        for (com.shadowclangaming.warhead.WarheadType warhead
                : com.shadowclangaming.warhead.WarheadType.values()) {
            if (!com.shadowclangaming.warhead.ExplosivePackaging.packable(warhead.id())) {
                continue;
            }
            String id = com.shadowclangaming.warhead.ExplosivePackaging.id(
                    com.shadowclangaming.warhead.ExplosivePackaging.Form.GRENADE, warhead.id());
            list.add(ITEMS.registerItem(id, props -> new ExplosiveGrenadeItem(warhead, props),
                    new Item.Properties().stacksTo(16)));
        }
        return java.util.List.copyOf(list);
    }

    private static java.util.Map<String, DeferredItem<WarheadMinecartItem>> registerMinecarts() {
        java.util.Map<String, DeferredItem<WarheadMinecartItem>> map = new java.util.LinkedHashMap<>();
        for (com.shadowclangaming.warhead.WarheadType warhead
                : com.shadowclangaming.warhead.WarheadType.values()) {
            if (!com.shadowclangaming.warhead.ExplosivePackaging.packable(warhead.id())) {
                continue;
            }
            String id = com.shadowclangaming.warhead.ExplosivePackaging.id(
                    com.shadowclangaming.warhead.ExplosivePackaging.Form.MINECART, warhead.id());
            map.put(warhead.id(), ITEMS.registerItem(id,
                    props -> new WarheadMinecartItem(warhead, props),
                    new Item.Properties().stacksTo(1)));
        }
        return java.util.Map.copyOf(map);
    }

    private static java.util.Map<String, DeferredItem<net.minecraft.world.item.BlockItem>>
            registerExplosiveBlockItems() {
        java.util.Map<String, DeferredItem<net.minecraft.world.item.BlockItem>> map =
                new java.util.LinkedHashMap<>();
        for (var entry : SCGBlocks.EXPLOSIVE_BLOCKS.entrySet()) {
            String blockId = com.shadowclangaming.warhead.ExplosivePackaging.id(
                    com.shadowclangaming.warhead.ExplosivePackaging.Form.BLOCK, entry.getKey());
            map.put(entry.getKey(), ITEMS.registerItem(blockId,
                    props -> new net.minecraft.world.item.BlockItem(entry.getValue().get(), props),
                    new Item.Properties()));
        }
        return java.util.Map.copyOf(map);
    }

    // ------------------------------------------------------------------ kamikaze & handheld

    /**
     * The vest. Does nothing while you live; on death it becomes a firework display
     * (see warhead/Kamikaze).
     */
    public static final DeferredItem<KamikazeVestItem> KAMIKAZE_VEST = ITEMS.registerItem(
            "kamikaze_vest",
            props -> new KamikazeVestItem(SCGArmorMaterials.KAMIKAZE, props),
            new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(12)));

    /** Shoulder tube: fires T1 and T2 missiles out of your inventory. */
    public static final DeferredItem<HandLauncherItem> HAND_LAUNCHER = ITEMS.registerItem(
            "hand_launcher", HandLauncherItem::new, new Item.Properties().stacksTo(1).durability(96));

    // ------------------------------------------------------------------ tools & consumables

    /** Remote firing device. Binds to a pad, fires at what you look at, runs on FE. */
    public static final DeferredItem<TargetDesignatorItem> TARGET_DESIGNATOR = ITEMS.registerItem(
            "target_designator", TargetDesignatorItem::new, new Item.Properties().stacksTo(1));

    /** Charges a designator (15,000 FE each). Crafted from the nuclear chain. */
    public static final DeferredItem<Item> RTG_PELLET = ITEMS.registerSimpleItem(
            "rtg_pellet", new Item.Properties());

    /**
     * Pays for Numismatic warheads straight from the player's EconomyCraft balance.
     * Coins remain a fully supported optional extra.
     */
    public static final DeferredItem<BankCardItem> BANK_CARD = ITEMS.registerItem(
            "bank_card", BankCardItem::new, new Item.Properties().stacksTo(1));

    /** Draws chalk circles; right-click a circle to make it bigger. */
    public static final DeferredItem<ChalkItem> CHALK = ITEMS.registerItem(
            "chalk", ChalkItem::new, new Item.Properties().stacksTo(16));

    /** Freezes Creeping Evil while carried. Blocks all mana regeneration while carried. */
    public static final DeferredItem<HolyNecklaceItem> HOLY_NECKLACE = ITEMS.registerItem(
            "holy_necklace", HolyNecklaceItem::new, new Item.Properties().stacksTo(1));

    /** Cleric trade. With the necklace, at midday, ends the curse for good. */
    public static final DeferredItem<HolyWaterItem> HOLY_WATER = ITEMS.registerItem(
            "holy_water", HolyWaterItem::new, new Item.Properties().stacksTo(8));

    /** The ritual site for the blood pact. */
    public static final DeferredItem<BlockItem> BLOOD_CIRCLE = ITEMS.registerSimpleBlockItem(SCGBlocks.BLOOD_CIRCLE);

    /** Currency that works without Create: Numismatics. Worth 100 units. */
    public static final DeferredItem<Item> WAR_BOND = ITEMS.registerSimpleItem(
            "war_bond", new Item.Properties().stacksTo(64));

    // ------------------------------------------------------------------ chemistry

    public static final DeferredItem<ElementVialItem> ELEMENT_VIAL = ITEMS.registerItem(
            "element_vial", ElementVialItem::new, new Item.Properties().stacksTo(64));

    /** Liquid chemical bucket containing 8 units of an element. */
    public static final DeferredItem<com.shadowclangaming.item.ChemicalBucketItem> CHEMICAL_BUCKET =
            ITEMS.registerItem("chemical_bucket",
                    com.shadowclangaming.item.ChemicalBucketItem::new,
                    new Item.Properties().stacksTo(1).craftRemainder(net.minecraft.world.item.Items.BUCKET));

    public static final DeferredItem<Item> RAW_URANIUM = ITEMS.registerSimpleItem(
            "raw_uranium", new Item.Properties());

    public static final DeferredItem<Item> ENRICHED_URANIUM = ITEMS.registerSimpleItem(
            "enriched_uranium", new Item.Properties());

    public static final DeferredItem<Item> TRITIUM_CELL = ITEMS.registerSimpleItem(
            "tritium_cell", new Item.Properties());

    /** Handheld Pocket Microscope / Chemist's Lens. */
    public static final DeferredItem<com.shadowclangaming.item.PocketMicroscopeItem> POCKET_MICROSCOPE =
            ITEMS.registerItem("pocket_microscope",
                    com.shadowclangaming.item.PocketMicroscopeItem::new,
                    new Item.Properties().stacksTo(1));

    /** The Chemist's Field Journal for logging discovered chemical recipes. */
    public static final DeferredItem<com.shadowclangaming.item.ChemistJournalItem> CHEMIST_JOURNAL =
            ITEMS.registerItem("chemist_journal",
                    com.shadowclangaming.item.ChemistJournalItem::new,
                    new Item.Properties().stacksTo(1));

    // ------------------------------------------------------------------ block items

    public static final DeferredItem<BlockItem> LAUNCH_PAD = ITEMS.registerSimpleBlockItem(SCGBlocks.LAUNCH_PAD);

    /** Hyperconductor Cable: long-scale power transport with automatic protocol conversion. */
    public static final DeferredItem<BlockItem> POWER_CABLE = ITEMS.registerSimpleBlockItem(SCGBlocks.POWER_CABLE);

    /** Auto-Trader: sells what you pipe in, buys what you tell it to, converts FE at a cubic rate. */
    public static final DeferredItem<TraderBlockItem> AUTO_TRADER = ITEMS.registerItem(
            "auto_trader",
            props -> new TraderBlockItem(SCGBlocks.AUTO_TRADER.get(), props),
            new Item.Properties());
    public static final DeferredItem<BlockItem> DECOMPOSER = ITEMS.registerSimpleBlockItem(SCGBlocks.DECOMPOSER);
    public static final DeferredItem<BlockItem> SYNTHESIZER = ITEMS.registerSimpleBlockItem(SCGBlocks.SYNTHESIZER);
    public static final DeferredItem<BlockItem> MICROSCOPE = ITEMS.registerSimpleBlockItem(SCGBlocks.MICROSCOPE);
    public static final DeferredItem<BlockItem> CHEMICAL_REACTOR = ITEMS.registerSimpleBlockItem(SCGBlocks.CHEMICAL_REACTOR);
    public static final DeferredItem<BlockItem> CENTRIFUGE = ITEMS.registerSimpleBlockItem(SCGBlocks.CENTRIFUGE);
    public static final DeferredItem<BlockItem> URANIUM_ORE = ITEMS.registerSimpleBlockItem(SCGBlocks.URANIUM_ORE);
    public static final DeferredItem<BlockItem> FABRICATOR = ITEMS.registerSimpleBlockItem(SCGBlocks.FABRICATOR);
    public static final DeferredItem<BlockItem> CONTROL_PANEL = ITEMS.registerSimpleBlockItem(SCGBlocks.CONTROL_PANEL);
    public static final DeferredItem<BlockItem> SUPPORT_ROD = ITEMS.registerSimpleBlockItem(SCGBlocks.SUPPORT_ROD);
    public static final DeferredItem<BlockItem> MINE = ITEMS.registerSimpleBlockItem(SCGBlocks.MINE);
    public static final DeferredItem<BlockItem> NETWORK_CABLE = ITEMS.registerSimpleBlockItem(SCGBlocks.NETWORK_CABLE);
    public static final DeferredItem<BlockItem> STORAGE_BRIDGE =
            ITEMS.registerSimpleBlockItem(SCGBlocks.STORAGE_BRIDGE);
    public static final DeferredItem<BlockItem> NETWORK_RACK =
            ITEMS.registerSimpleBlockItem(SCGBlocks.NETWORK_RACK);
    public static final DeferredItem<BlockItem> ORBITAL_NODE =
            ITEMS.registerSimpleBlockItem(SCGBlocks.ORBITAL_NODE);
    public static final DeferredItem<BlockItem> RADAR =
            ITEMS.registerSimpleBlockItem(SCGBlocks.RADAR);
    public static final DeferredItem<BlockItem> CANNON_DIRECTOR =
            ITEMS.registerSimpleBlockItem(SCGBlocks.CANNON_DIRECTOR);

    public static final DeferredItem<BlockItem> EMITTER = ITEMS.registerSimpleBlockItem(SCGBlocks.EMITTER);
    public static final DeferredItem<BlockItem> BLUEPRINT_TABLE =
            ITEMS.registerSimpleBlockItem(SCGBlocks.BLUEPRINT_TABLE);
    public static final DeferredItem<BlockItem> INSCRIPTION_TABLE =
            ITEMS.registerSimpleBlockItem(SCGBlocks.INSCRIPTION_TABLE);

    private SCGItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
