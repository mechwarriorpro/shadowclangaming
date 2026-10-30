package com.shadowclangaming.chem;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Chemical compounds / molecules inspired by classic MineChem 1.6.4:
 * <ul>
 *   <li>Composed of stoichiometric element ratios (e.g. Water = 2x H + 1x O; Sulfuric Acid = 2x H + 1x S + 4x O).</li>
 *   <li>Can be synthesized from elements in the Synthesizer and decomposed back in the Decomposer.</li>
 *   <li>Possess authentic chemical hit effects for weapon coatings and pharmacological food ingestion effects.</li>
 * </ul>
 */
public enum Compound implements StringRepresentable {

    WATER("Water", "H₂O", 0xFF3D78E0, false, false, false, false,
            Element.H, 2, Element.O, 1),

    CARBON_DIOXIDE("Carbon Dioxide", "CO₂", 0xFFB0B0B0, false, false, false, false,
            Element.C, 1, Element.O, 2),

    NITROGEN_DIOXIDE("Nitrogen Dioxide", "NO₂", 0xFFD06020, true, false, false, false,
            Element.N, 1, Element.O, 2),

    TOLUENE("Toluene", "C₇H₈", 0xFFD8D8E0, true, false, false, false,
            Element.C, 7, Element.H, 8),

    POTASSIUM_NITRATE("Potassium Nitrate", "KNO₃", 0xFFF0F0F0, false, false, false, false,
            Element.K, 1, Element.N, 1, Element.O, 3),

    TNT("Trinitrotoluene", "C₇H₅N₃O₆", 0xFFE04020, false, true, false, false,
            Element.C, 7, Element.H, 5, Element.N, 3, Element.O, 6),

    SILICON_DIOXIDE("Silicon Dioxide", "SiO₂", 0xFFFFFFFF, false, false, false, false,
            Element.SI, 1, Element.O, 2),

    CALCIUM_CARBONATE("Calcium Carbonate", "CaCO₃", 0xFFEAEAE0, false, false, false, false,
            Element.CA, 1, Element.C, 1, Element.O, 3),

    SULFURIC_ACID("Sulfuric Acid", "H₂SO₄", 0xFFE8D030, true, false, false, true,
            Element.H, 2, Element.S, 1, Element.O, 4),

    HYDROCHLORIC_ACID("Hydrochloric Acid", "HCl", 0xFFB8E070, true, false, false, true,
            Element.H, 1, Element.CL, 1),

    CYANIDE("Potassium Cyanide", "KCN", 0xFF303030, true, false, false, false,
            Element.K, 1, Element.C, 1, Element.N, 1),

    AMMONIA("Ammonia", "NH₃", 0xFFC0E0FF, true, false, false, false,
            Element.N, 1, Element.H, 3),

    PHOSGENE("Phosgene", "COCl₂", 0xFF80A040, true, false, false, true,
            Element.C, 1, Element.O, 1, Element.CL, 2),

    SARIN("Sarin", "C₄H₁₀FO₂P", 0xFF408060, true, false, false, false,
            Element.C, 4, Element.H, 10, Element.F, 1, Element.O, 2, Element.P, 1),

    PENICILLIN("Penicillin", "C₁₆H₁₈N₂O₄S", 0xFFFFFFFF, false, false, true, false,
            Element.C, 16, Element.H, 18, Element.N, 2, Element.O, 4, Element.S, 1),

    TESTOSTERONE("Testosterone", "C₁₉H₂₈O₂", 0xFFFF80C0, false, false, true, false,
            Element.C, 19, Element.H, 28, Element.O, 2),

    ASPIRIN("Aspirin", "C₉H₈O₄", 0xFFFAFAFA, false, false, true, false,
            Element.C, 9, Element.H, 8, Element.O, 4),

    CAFFEINE("Caffeine", "C₈H₁₀N₄O₂", 0xFF503020, false, false, true, false,
            Element.C, 8, Element.H, 10, Element.N, 4, Element.O, 2),

    SUCROSE("Sucrose", "C₁₂H₂₂O₁₁", 0xFFFFFFFF, false, false, false, false,
            Element.C, 12, Element.H, 22, Element.O, 11),

    CELLULOSE("Cellulose", "C₆H₁₀O₅", 0xFF60A030, false, false, false, false,
            Element.C, 6, Element.H, 10, Element.O, 5),

    ETHANOL("Ethanol", "C₂H₅OH", 0xFF90C0FF, false, false, false, false,
            Element.C, 2, Element.H, 6, Element.O, 1),

    SALT("Sodium Chloride", "NaCl", 0xFFF5F5F5, false, false, false, false,
            Element.NA, 1, Element.CL, 1),

    TETRODOTOXIN("Tetrodotoxin", "C₁₁H₁₇N₃O₈", 0xFF402060, true, false, false, false,
            Element.C, 11, Element.H, 17, Element.N, 3, Element.O, 8),

    MYCOTOXIN("Mycotoxin", "C₂₄H₃₄O₉", 0xFF804020, true, false, false, false,
            Element.C, 24, Element.H, 34, Element.O, 9),

    ATROPINE("Atropine", "C₁₇H₂₃NO₃", 0xFFE0C0FF, false, false, true, false,
            Element.C, 17, Element.H, 23, Element.N, 1, Element.O, 3),

    METHAMPHETAMINE("Methamphetamine", "C₁₀H₁₅N", 0xFF80D0FF, false, false, true, false,
            Element.C, 10, Element.H, 15, Element.N, 1),

    PSILOCYBIN("Psilocybin", "C₁₂H₁₇N₂O₄P", 0xFF704090, false, false, true, false,
            Element.C, 12, Element.H, 17, Element.N, 2, Element.O, 4, Element.P, 1),

    MESCALINE("Mescaline", "C₁₁H₁₇NO₃", 0xFF906040, false, false, true, false,
            Element.C, 11, Element.H, 17, Element.N, 1, Element.O, 3);

    public static final Codec<Compound> CODEC = StringRepresentable.fromEnum(Compound::values);
    public static final StreamCodec<ByteBuf, Compound> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(Compound::byName, Compound::getSerializedName);

    private final String displayName;
    private final String formula;
    private final int colour;
    private final boolean toxic;
    private final boolean explosive;
    private final boolean medicinal;
    private final boolean acid;
    private final Map<Element, Integer> elements;

    Compound(String displayName, String formula, int colour,
             boolean toxic, boolean explosive, boolean medicinal, boolean acid,
             Object... elementPairs) {
        this.displayName = displayName;
        this.formula = formula;
        this.colour = colour;
        this.toxic = toxic;
        this.explosive = explosive;
        this.medicinal = medicinal;
        this.acid = acid;

        Map<Element, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < elementPairs.length; i += 2) {
            map.put((Element) elementPairs[i], (Integer) elementPairs[i + 1]);
        }
        this.elements = Collections.unmodifiableMap(map);
    }

    public String displayName() {
        return displayName;
    }

    public String formula() {
        return formula;
    }

    public int colour() {
        return colour;
    }

    public boolean isToxic() {
        return toxic;
    }

    public boolean isExplosive() {
        return explosive;
    }

    public boolean isMedicinal() {
        return medicinal;
    }

    public boolean isAcid() {
        return acid;
    }

    public Map<Element, Integer> elements() {
        return elements;
    }

    public String translationKey() {
        return "compound.shadowclangaming." + getSerializedName();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Compound byName(String name) {
        for (Compound c : values()) {
            if (c.getSerializedName().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return WATER;
    }

    /**
     * Applies the compound's chemical effect when striking an entity with a coated weapon
     * or when ingested in spiked food.
     */
    public void applyEffect(LivingEntity target) {
        Level level = target.level();
        double x = target.getX();
        double y = target.getY() + 1.0;
        double z = target.getZ();

        switch (this) {
            case SULFURIC_ACID, HYDROCHLORIC_ACID -> {
                target.hurt(level.damageSources().magic(), 7.0F);
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
                if (level instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.ITEM_SLIME, x, y, z, 15, 0.3, 0.3, 0.3, 0.05);
                    sl.playSound(null, x, y, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
                }
            }
            case CYANIDE -> {
                target.hurt(level.damageSources().magic(), 12.0F);
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 160, 2));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2));
                if (level instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 20, 0.3, 0.3, 0.3, 0.05);
                    sl.playSound(null, x, y, z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
                }
            }
            case PHOSGENE, SARIN -> {
                target.hurt(level.damageSources().magic(), 9.0F);
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 2));
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 1));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 2));
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0));
                if (level instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 20, 0.3, 0.3, 0.3, 0.05);
                }
            }
            case TETRODOTOXIN -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 6)); // Paralysis
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2));
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 160, 1));
                if (level instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 15, 0.2, 0.2, 0.2, 0.02);
                }
            }
            case TNT -> {
                level.explode(null, x, y, z, 1.8F, Level.ExplosionInteraction.NONE);
            }
            case PENICILLIN -> {
                target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
                target.removeEffect(MobEffects.POISON);
                target.removeEffect(MobEffects.WITHER);
            }
            case TESTOSTERONE -> {
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, 1));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 1));
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 0));
            }
            case ASPIRIN -> {
                target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 0));
                target.removeEffect(MobEffects.POISON);
            }
            case CAFFEINE -> {
                target.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 400, 1));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
            }
            case SUCROSE -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, 0));
            }
            case ETHANOL -> {
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 2));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 1));
            }
            case MYCOTOXIN -> {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 160, 1));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
            }
            case ATROPINE -> {
                target.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0));
                target.removeEffect(MobEffects.CONFUSION);
                target.removeEffect(MobEffects.BLINDNESS);
            }
            case METHAMPHETAMINE -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 3));
                target.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 400, 2));
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 1));
                target.hurt(level.damageSources().generic(), 2.0F);
            }
            case PSILOCYBIN, MESCALINE -> {
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 3));
                target.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0));
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
            }
            default -> {
                // Mild effect
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0));
            }
        }
    }
}
