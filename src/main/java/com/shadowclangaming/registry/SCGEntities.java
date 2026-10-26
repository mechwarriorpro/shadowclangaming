package com.shadowclangaming.registry;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.entity.MissileEntity;
import com.shadowclangaming.entity.SpellProjectileEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SCGEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ShadowClanGaming.MOD_ID);

    /**
     * One entity type, three missile items. Which missile is flying is decided by the ItemStack
     * stored in the entity's synched data (see MissileEntity#getItem).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> MISSILE =
            ENTITIES.register("missile", () -> EntityType.Builder
                    .<MissileEntity>of(MissileEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .clientTrackingRange(12)
                    .updateInterval(2)   // send position 10x/second so flight looks smooth
                    .build("missile"));

    /** A spell in flight. One entity type for every projectile-shaped spell. */
    public static final DeferredHolder<EntityType<?>, EntityType<SpellProjectileEntity>> SPELL_PROJECTILE =
            ENTITIES.register("spell_projectile", () -> EntityType.Builder
                    .<SpellProjectileEntity>of(SpellProjectileEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build("spell_projectile"));

    /**
     * A turret chicken.
     *
     * Registered as {@code EntityType<Chicken>} ON PURPOSE: the entity extends Chicken, so declaring the
     * type as Chicken lets us reuse vanilla's own ChickenRenderer and its dimensions instead of
     * writing a model. The spawn factory still builds our subclass, which is where the AI difference
     * lives.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<net.minecraft.world.entity.animal.Chicken>>
            TURRET_CHICKEN = ENTITIES.register("turret_chicken", () -> EntityType.Builder
            .<net.minecraft.world.entity.animal.Chicken>of(
                    com.shadowclangaming.entity.TurretChickenEntity::new, MobCategory.CREATURE)
            .sized(0.4F, 0.7F)
            .clientTrackingRange(10)
            .build("turret_chicken"));

    /** The egg a turret chicken fires. Registered as ThrownEgg so the vanilla egg renderer applies. */
    public static final DeferredHolder<EntityType<?>,
            EntityType<net.minecraft.world.entity.projectile.ThrownEgg>> TURRET_EGG =
            ENTITIES.register("turret_egg", () -> EntityType.Builder
                    .<net.minecraft.world.entity.projectile.ThrownEgg>of(
                            com.shadowclangaming.entity.TurretEggEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(6)
                    .updateInterval(10)
                    .build("turret_egg"));

    /**
     * Beef Supreme.
     *
     * Registered as EntityType<Cow> so vanilla's CowRenderer draws it - a boss with 5000 health and a
     * boss bar is still, visibly, a cow. The spawn factory builds our subclass with the real attributes.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<net.minecraft.world.entity.animal.Cow>>
            BEEF_SUPREME = ENTITIES.register("beef_supreme", () -> EntityType.Builder
            .<net.minecraft.world.entity.animal.Cow>of(
                    com.shadowclangaming.entity.BeefSupremeEntity::new, MobCategory.CREATURE)
            .sized(0.9F, 1.4F)
            .fireImmune()
            .clientTrackingRange(10)
            .build("beef_supreme"));

    /** One thrown-warhead entity for all 38 grenades; the warhead rides on the stack. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.shadowclangaming.entity.GrenadeEntity>>
            GRENADE = ENTITIES.register("grenade", () -> EntityType.Builder
            .<com.shadowclangaming.entity.GrenadeEntity>of(
                    com.shadowclangaming.entity.GrenadeEntity::new, MobCategory.MISC)
            .sized(0.25F, 0.25F)
            .clientTrackingRange(6)
            .updateInterval(10)
            .build("grenade"));

    /**
     * One minecart for all 38 warheads. Registered as MinecartTNT so vanilla's own minecart renderer draws
     * it - a warhead cart looks exactly like a TNT cart, which is the point.
     */
    public static final DeferredHolder<EntityType<?>,
            EntityType<com.shadowclangaming.entity.WarheadMinecart>>
            EXPLOSIVE_MINECART = ENTITIES.register("explosive_minecart", () -> EntityType.Builder
            .<com.shadowclangaming.entity.WarheadMinecart>of(
                    com.shadowclangaming.entity.WarheadMinecart::new, MobCategory.MISC)
            .sized(0.98F, 0.7F)
            .clientTrackingRange(8)
            .build("explosive_minecart"));

    private SCGEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        // EVERY living entity type this mod registers needs an entry here, or constructing it throws.
        //
        // This is not optional bookkeeping. LivingEntity's constructor does
        // `new AttributeMap(DefaultAttributes.getSupplier(type))`, and that supplier table is filled from
        // THIS event - a type with no entry gets a null supplier, and the first `getMaxHealth()` call
        // (which the constructor itself makes, via setHealth) is a NullPointerException that takes the
        // whole server down. The turret chicken shipped without one, so a single Clucker warhead crashed
        // the world on impact.
        //
        // Beef Supreme also overrides the numbers: 5000 health and 30 damage are attributes, not values
        // applied in a damage hook, so armour and everything else in the game stay honest.
        modEventBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) -> {
            event.put(BEEF_SUPREME.get(),
                    com.shadowclangaming.entity.BeefSupremeEntity.createAttributes().build());
            // A turret chicken is a chicken: same health, same speed, same knockback resistance. Taking
            // vanilla's supplier rather than hand-writing numbers means a datapack or another mod that
            // retunes chickens retunes these too.
            event.put(TURRET_CHICKEN.get(),
                    net.minecraft.world.entity.animal.Chicken.createAttributes().build());
        });
    }
}
