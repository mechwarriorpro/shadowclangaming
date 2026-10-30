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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Chemical compounds / molecules faithfully ported from classic MineChem 1.6.4 MoleculeEnum
 * and PotionPharmacologyEffect.
 */
public enum Compound implements StringRepresentable {

    CELLULOSE("Cellulose", "C₆H₁₀O₅", 0xFF00FF00, PharmacologyEffect.NONE, Element.C, 6, Element.H, 10, Element.O, 5),
    WATER("Water", "H₂O", 0xFF0000FF, PharmacologyEffect.WATER, Element.H, 2, Element.O, 1),
    CARBON_DIOXIDE("Carbon Dioxide", "CO₂", 0xFF7F7F7F, PharmacologyEffect.DEFAULT_TOXIC, Element.C, 1, Element.O, 2),
    NITROGEN_DIOXIDE("Nitrogen Dioxide", "NO₂", 0xFFFFA500, PharmacologyEffect.DEFAULT_TOXIC, Element.N, 1, Element.O, 2),
    TOLUENE("Toluene", "C₇H₈", 0xFFFFFFFF, PharmacologyEffect.DEFAULT_TOXIC, Element.C, 7, Element.H, 8),
    POTASSIUM_NITRATE("Potassium Nitrate", "KNO₃", 0xFFE5E5E5, PharmacologyEffect.DEFAULT_TOXIC, Element.K, 1, Element.N, 1, Element.O, 3),
    TNT("Trinitrotoluene", "C₁₃H₁₀N₃O₆", 0xFFFFFF00, PharmacologyEffect.TNT, Element.C, 13, Element.H, 10, Element.N, 3, Element.O, 6),
    SILICON_DIOXIDE("Silicon Dioxide", "O₂SI", 0xFFFFFFFF, PharmacologyEffect.DEFAULT_TOXIC, Element.O, 2, Element.SI, 1),
    CALCITE("Calcite", "CCAO₃", 0xFF708090, PharmacologyEffect.NONE, Element.C, 1, Element.CA, 1, Element.O, 3),
    PYRITE("Pyrite", "FES₂", 0xFF708090, PharmacologyEffect.NONE, Element.FE, 1, Element.S, 2),
    NEPHELINE("Nepheline", "ALO₄SI", 0xFF708090, PharmacologyEffect.NONE, Element.AL, 1, Element.O, 4, Element.SI, 1),
    SULFATE("Sulfate (ion)", "O₄S", 0xFF708090, PharmacologyEffect.NONE, Element.O, 4, Element.S, 1),
    NOSELITE("Noselite", "AL₆NA₈O₂₈SSI₆", 0xFF708090, PharmacologyEffect.NONE, Element.AL, 6, Element.NA, 8, Element.O, 28, Element.S, 1, Element.SI, 6),
    SODALITE("Sodalite", "AL₆CL₂NA₈O₂₄SI₆", 0xFF708090, PharmacologyEffect.NONE, Element.AL, 6, Element.CL, 2, Element.NA, 8, Element.O, 24, Element.SI, 6),
    NITRATE("Nitrate (ion)", "NO₃", 0xFF708090, PharmacologyEffect.NONE, Element.N, 1, Element.O, 3),
    CARBONATE("Carbonate (ion)", "CO₃", 0xFF708090, PharmacologyEffect.NONE, Element.C, 1, Element.O, 3),
    CYANIDE("Potassium Cyanide", "CKN", 0xFF708090, PharmacologyEffect.CYANIDE, Element.C, 1, Element.K, 1, Element.N, 1),
    PHOSPHATE("Phosphate (ion)", "O₄P", 0xFF708090, PharmacologyEffect.NONE, Element.O, 4, Element.P, 1),
    ACETATE("Acetate (ion)", "C₂H₃O₂", 0xFF708090, PharmacologyEffect.NONE, Element.C, 2, Element.H, 3, Element.O, 2),
    CHROMATE("Chromate (ion)", "CRO₄", 0xFF708090, PharmacologyEffect.NONE, Element.CR, 1, Element.O, 4),
    HYDROXIDE("Hydroxide (ion)", "HO", 0xFF708090, PharmacologyEffect.NONE, Element.H, 1, Element.O, 1),
    AMMONIUM("Ammonium (ion)", "H₄N", 0xFF708090, PharmacologyEffect.NONE, Element.H, 4, Element.N, 1),
    HYDRONIUM("Hydronium (ion)", "H₃O", 0xFF708090, PharmacologyEffect.NONE, Element.H, 3, Element.O, 1),
    PEROXIDE("Peroxide (ion)", "O₂", 0xFF708090, PharmacologyEffect.NONE, Element.O, 2),
    CALCIUM_OXIDE("Calcium Oxide", "CAO", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.CA, 1, Element.O, 1),
    CALCIUM_CARBONATE("Calcium Carbonate", "CCAO₃", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.C, 1, Element.CA, 1, Element.O, 3),
    MAGNESIUM_CARBONATE("Magnesium Carbonate", "CMGO₃", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.C, 1, Element.MG, 1, Element.O, 3),
    LAZURITE("Lazurite", "ALNA₈O₈SSI", 0xFF708090, PharmacologyEffect.NONE, Element.AL, 1, Element.NA, 8, Element.O, 8, Element.S, 1, Element.SI, 1),
    ISOPRENE("Isoprene", "C₅H₈", 0xFF708090, PharmacologyEffect.NONE, Element.C, 5, Element.H, 8),
    BUTENE("Butene", "C₄H₈", 0xFF708090, PharmacologyEffect.NONE, Element.C, 4, Element.H, 8),
    POLYISOBUTYLENE("Polyisobutylene Rubber", "C₆₉H₁₃₆", 0xFF708090, PharmacologyEffect.NONE, Element.C, 69, Element.H, 136),
    MALIC_ACID("Malic Acid", "C₄H₆O₅", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.C, 4, Element.H, 6, Element.O, 5),
    VINYL_CHLORIDE("Vinyl Chloride Monomer", "C₂H₃CL", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.C, 2, Element.H, 3, Element.CL, 1),
    POLYVINYL_CHLORIDE("Polyvinyl Chloride", "C₁₂₈H₁₉₂CL₆₄", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.C, 128, Element.H, 192, Element.CL, 64),
    METHAMPHETAMINE("Methamphetamine", "C₁₀H₁₅N", 0xFF708090, PharmacologyEffect.METHAMPHETAMINE, Element.C, 10, Element.H, 15, Element.N, 1),
    PSILOCYBIN("Psilocybin", "C₁₂H₁₇N₂O₄P", 0xFF708090, PharmacologyEffect.PSILOCYBIN, Element.C, 12, Element.H, 17, Element.N, 2, Element.O, 4, Element.P, 1),
    IRON3OXIDE("Iron (III) Oxide", "FE₂O₃", 0xFF708090, PharmacologyEffect.NONE, Element.FE, 2, Element.O, 3),
    STRONTIUM_NITRATE("Strontium Nitrate", "N₂O₆SR", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.N, 2, Element.O, 6, Element.SR, 1),
    MAGNETITE("Magnetite", "FE₃O₄", 0xFF708090, PharmacologyEffect.NONE, Element.FE, 3, Element.O, 4),
    MAGNESIUM_OXIDE("Magnesium Oxide", "MGO", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.MG, 1, Element.O, 1),
    CUCURBITACIN("Cucurbitacin", "C₃₀H₄₂O₇", 0xFF708090, PharmacologyEffect.NONE, Element.C, 30, Element.H, 42, Element.O, 7),
    ASPARTIC_ACID("Aspartic Acid", "C₄H₇NO₄", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 4, Element.H, 7, Element.N, 1, Element.O, 4),
    HYDROXYLAPATITE("Hydroxylapatite", "HCA₅O₁₃P₃", 0xFF708090, PharmacologyEffect.NONE, Element.H, 1, Element.CA, 5, Element.O, 13, Element.P, 3),
    ALANINE("Alanine", "C₃H₇NO₂", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 3, Element.H, 7, Element.N, 1, Element.O, 2),
    GLYCINE("Glycine", "C₂H₅NO₂", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 2, Element.H, 5, Element.N, 1, Element.O, 2),
    SERINE("Serine", "C₃H₇NO₃", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 3, Element.H, 7, Element.N, 1, Element.O, 3),
    MESCALINE("Mescaline", "C₁₁H₁₇NO₃", 0xFF708090, PharmacologyEffect.MESCALINE, Element.C, 11, Element.H, 17, Element.N, 1, Element.O, 3),
    METHYL("Methyl (ion)", "CH₃", 0xFF708090, PharmacologyEffect.NONE, Element.C, 1, Element.H, 3),
    METHYLENE("Methylene (ion)", "CH₂", 0xFF708090, PharmacologyEffect.NONE, Element.C, 1, Element.H, 2),
    MEMETHACRYLATE("Methyl Methacrylate", "C₅H₉O₂", 0xFF708090, PharmacologyEffect.NONE, Element.C, 5, Element.H, 9, Element.O, 2),
    PMMA("Poly(methyl methacrylate)", "C₁₅H₂₇O₆", 0xFF708090, PharmacologyEffect.NONE, Element.C, 15, Element.H, 27, Element.O, 6),
    RED_PIGMENT("Cobalt(II) Nitrate", "CON₂O₆", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.CO, 1, Element.N, 2, Element.O, 6),
    ORANGE_PIGMENT("Potassium Dichromate", "CR₂K₂O₇", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.CR, 2, Element.K, 2, Element.O, 7),
    YELLOW_PIGMENT("Potassium Chromate", "CRK₂O₄", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.CR, 1, Element.K, 2, Element.O, 4),
    LIME_PIGMENT("Nickel(II) Chloride", "CL₂NI", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.CL, 2, Element.NI, 1),
    LIGHTBLUE_PIGMENT("Copper(II) Sulfate", "CUO₄S", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.CU, 1, Element.O, 4, Element.S, 1),
    PURPLE_PIGMENT("Potassium Permanganate", "KMNO₄", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.K, 1, Element.MN, 1, Element.O, 4),
    GREEN_PIGMENT("Zinc Green", "COO₂ZN", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.CO, 1, Element.O, 2, Element.ZN, 1),
    BLACK_PIGMENT("Carbon Black", "CH₂O", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.C, 1, Element.H, 2, Element.O, 1),
    WHITE_PIGMENT("Titanium Dioxide", "O₂TI", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.O, 2, Element.TI, 1),
    METASILICATE("Metasilicate", "O₃SI", 0xFF708090, PharmacologyEffect.NONE, Element.O, 3, Element.SI, 1),
    BERYL("Beryl", "AL₂BE₃O₁₈SI₆", 0xFF708090, PharmacologyEffect.NONE, Element.AL, 2, Element.BE, 3, Element.O, 18, Element.SI, 6),
    ETHANOL("Ethyl Alcohol", "C₂H₆O", 0xFF708090, PharmacologyEffect.ETHANOL, Element.C, 2, Element.H, 6, Element.O, 1),
    AMPHETAMINE("Amphetamine", "C₉H₁₃N", 0xFF708090, PharmacologyEffect.AMPHETAMINE, Element.C, 9, Element.H, 13, Element.N, 1),
    THEOBROMINE("Theobromine", "C₇H₈N₄O₂", 0xFF708090, PharmacologyEffect.THEOBROMINE, Element.C, 7, Element.H, 8, Element.N, 4, Element.O, 2),
    STARCH("Starch", "C₁₈H₃₀O₁₅", 0xFF708090, PharmacologyEffect.STARCH, Element.C, 18, Element.H, 30, Element.O, 15),
    SUCROSE("Sucrose", "C₁₂H₂₂O₁₁", 0xFF708090, PharmacologyEffect.SUCROSE, Element.C, 12, Element.H, 22, Element.O, 11),
    PANTHERINE("Pantherine", "C₄H₆N₂O₂", 0xFF708090, PharmacologyEffect.PANTHERINE, Element.C, 4, Element.H, 6, Element.N, 2, Element.O, 2),
    ALUMINIUM_OXIDE("Aluminium Oxide", "AL₂O₃", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.AL, 2, Element.O, 3),
    FULLERENE("Carbon Nanotubes", "C₂₅₆", 0xFF777777, PharmacologyEffect.NONE, Element.C, 256),
    VALINE("Valine", "C₅H₁₁NO₂", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 5, Element.H, 11, Element.N, 1, Element.O, 2),
    PENICILLIN("Penicillin", "C₁₆H₁₈N₂O₄S", 0xFF708090, PharmacologyEffect.PENICILLIN, Element.C, 16, Element.H, 18, Element.N, 2, Element.O, 4, Element.S, 1),
    TESTOSTERONE("Testosterone", "C₁₉H₂₈O₂", 0xFF708090, PharmacologyEffect.TESTOSTERONE, Element.C, 19, Element.H, 28, Element.O, 2),
    KAOLINITE("Kaolinite", "H₄AL₂O₉SI₂", 0xFF708090, PharmacologyEffect.NONE, Element.H, 4, Element.AL, 2, Element.O, 9, Element.SI, 2),
    FINGOLIMOD("Fingolimod", "C₁₉H₃₃NO₂", 0xFF708090, PharmacologyEffect.FINGOLIMOD, Element.C, 19, Element.H, 33, Element.N, 1, Element.O, 2),
    ARGININE("Arginine", "C₆H₁₄N₄O₂", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 6, Element.H, 14, Element.N, 4, Element.O, 2),
    SHIKIMIC_ACID("Shikimic Acid", "C₇H₁₀O₅", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.C, 7, Element.H, 10, Element.O, 5),
    SULFURIC_ACID("Sulfuric Acid", "H₂O₄S", 0xFF708090, PharmacologyEffect.ACID_FIRE, Element.H, 2, Element.O, 4, Element.S, 1),
    GLYPHOSATE("Glyphosate", "C₃H₈NO₅P", 0xFF708090, PharmacologyEffect.NONE, Element.C, 3, Element.H, 8, Element.N, 1, Element.O, 5, Element.P, 1),
    ASPIRIN("Aspirin", "C₉H₈O₄", 0xFF708090, PharmacologyEffect.ASPIRIN, Element.C, 9, Element.H, 8, Element.O, 4),
    DDT("DDT", "C₁₄H₉CL₅", 0xFF708090, PharmacologyEffect.NONE, Element.C, 14, Element.H, 9, Element.CL, 5),
    DOTA("DOTA", "C₁₆H₂₈N₄O₈", 0xFF708090, PharmacologyEffect.NONE, Element.C, 16, Element.H, 28, Element.N, 4, Element.O, 8),
    MYCOTOXIN("T-2 Mycotoxin", "C₂₄H₃₄O₉", 0xFFE2D311, PharmacologyEffect.MYCOTOXIN, Element.C, 24, Element.H, 34, Element.O, 9),
    SALT("Salt", "CLNA", 0xFF708090, PharmacologyEffect.NONE, Element.CL, 1, Element.NA, 1),
    AMMONIA("Ammonia", "H₃N", 0xFF708090, PharmacologyEffect.NONE, Element.H, 3, Element.N, 1),
    NODULARIN("Nodularin", "C₄₁H₆₀N₈O₁₀", 0xFF708090, PharmacologyEffect.NODULARIN, Element.C, 41, Element.H, 60, Element.N, 8, Element.O, 10),
    TETRODOTOXIN("Tetrodotoxin", "C₁₁H₁₁N₃O₈", 0xFF708090, PharmacologyEffect.TETRODOTOXIN, Element.C, 11, Element.H, 11, Element.N, 3, Element.O, 8),
    THC("THC", "C₂₁H₃₀O₂", 0xFF708090, PharmacologyEffect.THC, Element.C, 21, Element.H, 30, Element.O, 2),
    MT("Methylcyclopentadienyl Manganese Tricarbonyl", "C₉H₇MNO₃", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.C, 9, Element.H, 7, Element.MN, 1, Element.O, 3),
    BULI("Tert-Butyllithium", "C₄H₉LI", 0xFF708090, PharmacologyEffect.ACID_FIRE, Element.C, 4, Element.H, 9, Element.LI, 1),
    PLAT("Chloroplatinic Acid", "H₂CL₆PT", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.H, 2, Element.CL, 6, Element.PT, 1),
    PHOSGENE("Phosgene", "CCL₂O", 0xFF708090, PharmacologyEffect.ACID_FIRE, Element.C, 1, Element.CL, 2, Element.O, 1),
    AALC("Allyl Alcohol", "C₃H₆O", 0xFF708090, PharmacologyEffect.ACID_FIRE, Element.C, 3, Element.H, 6, Element.O, 1),
    HIST("Diphenhydramine", "C₁₇H₂₁NO", 0xFF708090, PharmacologyEffect.HIST, Element.C, 17, Element.H, 21, Element.N, 1, Element.O, 1),
    PAL2("Batrachotoxin", "C₃₁H₄₂N₂O₆", 0xFF708090, PharmacologyEffect.PAL2, Element.C, 31, Element.H, 42, Element.N, 2, Element.O, 6),
    RETINOL("Retinol", "C₂₀H₃₀O", 0xFF708090, PharmacologyEffect.RETINOL, Element.C, 20, Element.H, 30, Element.O, 1),
    XYLITOL("Xylitol", "C₅H₁₂O₅", 0xFF708090, PharmacologyEffect.XYLITOL, Element.C, 5, Element.H, 12, Element.O, 5),
    WEEDEX("Aminocyclopyrachlor", "C₈H₈CLN₃O₂", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.C, 8, Element.H, 8, Element.CL, 1, Element.N, 3, Element.O, 2),
    XANAX("Alprazolam", "C₁₇H₁₃CLN₄", 0xFF708090, PharmacologyEffect.XANAX, Element.C, 17, Element.H, 13, Element.CL, 1, Element.N, 4),
    HYDROCHLORIC_ACID("Hydrogen Chloride", "HCL", 0xFF708090, PharmacologyEffect.ACID_FIRE, Element.H, 1, Element.CL, 1),
    COCAINE("Cocaine", "C₁₇H₂₁NO₄", 0xFF708090, PharmacologyEffect.COCAINE, Element.C, 17, Element.H, 21, Element.N, 1, Element.O, 4),
    COCAINE_HCL("Cocaine Hydrochloride", "C₁₇H₂₂CLNO₄", 0xFF708090, PharmacologyEffect.COCAINE_HCL, Element.C, 17, Element.H, 22, Element.CL, 1, Element.N, 1, Element.O, 4),
    BLUEORGODYE("Guaiazulene", "C₁₅H₁₈", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.C, 15, Element.H, 18),
    REDORGODYE("Pelargonidin", "C₁₅H₁₁O₁₁", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.C, 15, Element.H, 11, Element.O, 11),
    PURPLEORGODYE("Delphinidin", "C₁₅H₁₁O₇", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.C, 15, Element.H, 11, Element.O, 7),
    OLIVINE("Olivine", "FE₂O₄SI", 0xFF708090, PharmacologyEffect.NONE, Element.FE, 2, Element.O, 4, Element.SI, 1),
    METBLUE("Methylene Blue", "C₁₆H₁₈CLN₃S", 0xFF708090, PharmacologyEffect.METBLUE, Element.C, 16, Element.H, 18, Element.CL, 1, Element.N, 3, Element.S, 1),
    MEOH("Methyl Alcohol", "CH₄O", 0xFF708090, PharmacologyEffect.MEOH, Element.C, 1, Element.H, 4, Element.O, 1),
    LCD("Cholesteryl Benzoate", "C₃₄H₅₀O₂", 0xFF708090, PharmacologyEffect.NONE, Element.C, 34, Element.H, 50, Element.O, 2),
    RADCHLOR("Radium Chloride", "CL₂RA", 0xFF708090, PharmacologyEffect.RADCHLOR, Element.CL, 2, Element.RA, 1),
    CAULERPENYNE("Caulerpenyne", "C₂₁H₂₆O₆", 0xFF708090, PharmacologyEffect.CAULERPENYNE, Element.C, 21, Element.H, 26, Element.O, 6),
    LATROPINE("Hyoscyamine", "C₁₇H₂₃NO₄", 0xFF708090, PharmacologyEffect.LATROPINE, Element.C, 17, Element.H, 23, Element.N, 1, Element.O, 4),
    GALLIC_ACID("Gallic Acid", "C₇H₁₇O₅", 0xFF708090, PharmacologyEffect.NONE, Element.C, 7, Element.H, 17, Element.O, 5),
    GLUCOSE("Glucose", "C₆H₁₂O₆", 0xFF708090, PharmacologyEffect.NONE, Element.C, 6, Element.H, 12, Element.O, 6),
    TANNIC_ACID("Tannic Acid", "C₇₆H₁₈₂O₅₆", 0xFF708090, PharmacologyEffect.NONE, Element.C, 76, Element.H, 182, Element.O, 56),
    HPEROX("Hydrogen Peroxide", "H₂O₂", 0xFF708090, PharmacologyEffect.NONE, Element.H, 2, Element.O, 2),
    GALLIUMARSENIDE("Gallium Arsenide", "ASGA", 0xFF708090, PharmacologyEffect.NONE, Element.AS, 1, Element.GA, 1),
    FIBROIN("Fibroin", "C₁₅H₃₆N₆O₁₃", 0xFF708090, PharmacologyEffect.NONE, Element.C, 15, Element.H, 36, Element.N, 6, Element.O, 13),
    ALUMINIUM_PHOSPHATE("Aluminium Phosphate", "ALO₄P", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.AL, 1, Element.O, 4, Element.P, 1),
    POTASSIUM_OXIDE("Potassium Oxide", "K₂O", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.K, 2, Element.O, 1),
    SODIUM_OXIDE("Sodium Oxide", "NA₂O", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.NA, 2, Element.O, 1),
    PLAGIOCLASE_ANORTHITE("Anorthite", "AL₂CAO₈SI₂", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.AL, 2, Element.CA, 1, Element.O, 8, Element.SI, 2),
    PLAGIOCLASE_ALBITE("Albite", "AL₂NAO₈SI₃", 0xFF708090, PharmacologyEffect.DEFAULT_TOXIC, Element.AL, 2, Element.NA, 1, Element.O, 8, Element.SI, 3),
    ORTHOCLASE("Orthoclase", "ALKO₈SI₃", 0xFF708090, PharmacologyEffect.NONE, Element.AL, 1, Element.K, 1, Element.O, 8, Element.SI, 3),
    BIOTITE("Biotite", "ALF₂FE₃KO₁₀SI₃", 0xFF708090, PharmacologyEffect.NONE, Element.AL, 1, Element.F, 2, Element.FE, 3, Element.K, 1, Element.O, 10, Element.SI, 3),
    AUGITE("Augite", "AL₂FENAO₆", 0xFF708090, PharmacologyEffect.NONE, Element.AL, 2, Element.FE, 1, Element.NA, 1, Element.O, 6),
    TALC("Talc", "MG₃O₁₀SI₄", 0xFF708090, PharmacologyEffect.NONE, Element.MG, 3, Element.O, 10, Element.SI, 4),
    PROPANE("Propane", "C₃H₈", 0xFF708090, PharmacologyEffect.NONE, Element.C, 3, Element.H, 8),
    PERIDOT("Peridot", "MG₂O₄SI", 0xFF708090, PharmacologyEffect.NONE, Element.MG, 2, Element.O, 4, Element.SI, 1),
    TOPAZ("Topaz", "AL₂F₂O₄", 0xFF708090, PharmacologyEffect.NONE, Element.AL, 2, Element.F, 2, Element.O, 4),
    ZOISITE("Zoisite", "HAL₃CA₂O₁₃SI₃", 0xFF708090, PharmacologyEffect.NONE, Element.H, 1, Element.AL, 3, Element.CA, 2, Element.O, 13, Element.SI, 3),
    CYSTEINE("Cysteine", "C₃H₇NO₂S", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 3, Element.H, 7, Element.N, 1, Element.O, 2, Element.S, 1),
    THREONINE("Threonine", "C₄H₉NO₃", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 4, Element.H, 9, Element.N, 1, Element.O, 3),
    LYSINE("Lysine", "C₆H₁₄N₂O₂", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 6, Element.H, 14, Element.N, 2, Element.O, 2),
    METHIONINE("Methionine", "C₅H₁₁NO₂S", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 5, Element.H, 11, Element.N, 1, Element.O, 2, Element.S, 1),
    TYROSINE("Tyrosine", "C₉H₁₁NO₃", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 9, Element.H, 11, Element.N, 1, Element.O, 3),
    HISTIDINE("Histidine", "C₆H₉N₃O₂", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 6, Element.H, 9, Element.N, 3, Element.O, 2),
    PHENYLALANINE("Phenylalanine", "C₉H₁₁NO₂", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 9, Element.H, 11, Element.N, 1, Element.O, 2),
    GLUTAMINE("Glutamine", "C₅H₁₀N₂O₃", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 5, Element.H, 10, Element.N, 2, Element.O, 3),
    PROLINE("Proline", "C₅H₉NO₂", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 5, Element.H, 9, Element.N, 1, Element.O, 2),
    LEUCINE("Leucine", "C₆H₁₃NO₂", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 6, Element.H, 13, Element.N, 1, Element.O, 2),
    TRYPTOPHAN("Tryptophan", "C₁₁H₁₂N₂O₂", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 11, Element.H, 12, Element.N, 2, Element.O, 2),
    ASPARTATE("Aspartate", "C₄H₇NO₄", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 4, Element.H, 7, Element.N, 1, Element.O, 4),
    ISOLEUCINE("Isoleucine", "C₆H₁₃NO₂", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 6, Element.H, 13, Element.N, 1, Element.O, 2),
    GLUTAMATES("Glutamic Acid", "C₅H₉NO₄", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 5, Element.H, 9, Element.N, 1, Element.O, 4),
    ASPARAGINE("Asparagine", "C₄H₈N₂O₃", 0xFF708090, PharmacologyEffect.AMINO_ACID, Element.C, 4, Element.H, 8, Element.N, 2, Element.O, 3),
    KERATIN("Keratin", "C₂₉H₅₉N₇O₁₆S₂", 0xFF708090, PharmacologyEffect.NONE, Element.C, 29, Element.H, 59, Element.N, 7, Element.O, 16, Element.S, 2),
    SARIN("Sarin", "C₄H₁₀FO₂P", 0xFF408060, PharmacologyEffect.ACID_FIRE, Element.C, 4, Element.H, 10, Element.F, 1, Element.O, 2, Element.P, 1),
    CAFFEINE("Caffeine", "C₈H₁₀N₄O₂", 0xFF503020, PharmacologyEffect.THEOBROMINE, Element.C, 8, Element.H, 10, Element.N, 4, Element.O, 2);

    public enum PharmacologyEffect {
        WATER, STARCH, XYLITOL, SUCROSE, PSILOCYBIN, AMPHETAMINE, METHAMPHETAMINE,
        MYCOTOXIN, ETHANOL, CYANIDE, PENICILLIN, TESTOSTERONE, XANAX, PANTHERINE,
        MESCALINE, ASPIRIN, ACID_FIRE, TETRODOTOXIN, FINGOLIMOD, THC, NODULARIN,
        HIST, PAL2, THEOBROMINE, RETINOL, AMINO_ACID, COCAINE, COCAINE_HCL,
        METBLUE, MEOH, RADCHLOR, CAULERPENYNE, LATROPINE, TNT, NONE, DEFAULT_TOXIC
    }

    public static final Codec<Compound> CODEC = StringRepresentable.fromEnum(Compound::values);
    public static final StreamCodec<ByteBuf, Compound> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(Compound::byName, Compound::getSerializedName);

    private final String displayName;
    private final String formula;
    private final int colour;
    private final PharmacologyEffect pharmacology;
    private final Map<Element, Integer> elements;

    Compound(String displayName, String formula, int colour,
             PharmacologyEffect pharmacology, Object... elementPairs) {
        this.displayName = displayName;
        this.formula = formula;
        this.colour = colour;
        this.pharmacology = pharmacology;

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

    public PharmacologyEffect pharmacology() {
        return pharmacology;
    }

    public boolean isAcid() {
        return pharmacology == PharmacologyEffect.ACID_FIRE;
    }

    public boolean isToxic() {
        return pharmacology == PharmacologyEffect.CYANIDE || pharmacology == PharmacologyEffect.MYCOTOXIN ||
               pharmacology == PharmacologyEffect.TETRODOTOXIN || pharmacology == PharmacologyEffect.NODULARIN ||
               pharmacology == PharmacologyEffect.PAL2 || pharmacology == PharmacologyEffect.RADCHLOR ||
               pharmacology == PharmacologyEffect.MEOH || pharmacology == PharmacologyEffect.DEFAULT_TOXIC;
    }

    public boolean isExplosive() {
        return pharmacology == PharmacologyEffect.TNT;
    }

    public boolean isMedicinal() {
        return pharmacology == PharmacologyEffect.PENICILLIN || pharmacology == PharmacologyEffect.ASPIRIN ||
               pharmacology == PharmacologyEffect.TESTOSTERONE || pharmacology == PharmacologyEffect.AMINO_ACID ||
               pharmacology == PharmacologyEffect.RETINOL || pharmacology == PharmacologyEffect.FINGOLIMOD ||
               pharmacology == PharmacologyEffect.THEOBROMINE;
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
     * Applies the compound's chemical pharmacology effect when striking an entity
     * or when ingested in laced food, faithfully matching MineChem 1.6.4 PotionPharmacologyEffect.
     */
    public void applyEffect(LivingEntity target) {
        Level level = target.level();
        double x = target.getX();
        double y = target.getY() + 1.0;
        double z = target.getZ();

        switch (this.pharmacology) {
            case WATER -> {
                if (target instanceof Player p) {
                    p.getFoodData().eat(1, 0.1F);
                }
            }
            case STARCH, XYLITOL -> {
                if (target instanceof Player p) {
                    p.getFoodData().eat(6, 0.2F);
                }
            }
            case SUCROSE -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50 * 20, 1));
                if (target instanceof Player p) {
                    p.getFoodData().eat(1, 0.1F);
                }
            }
            case PSILOCYBIN -> {
                target.hurt(level.damageSources().generic(), 2.0F);
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60 * 20, 5));
                target.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 60 * 20, 5));
            }
            case AMPHETAMINE -> {
                target.hurt(level.damageSources().generic(), 4.0F);
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 30 * 20, 5));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60 * 20, 15));
            }
            case METHAMPHETAMINE -> {
                target.hurt(level.damageSources().generic(), 4.0F);
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40 * 20, 5));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60 * 20, 7));
            }
            case MYCOTOXIN -> {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 120 * 20, 7));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120 * 20, 3));
            }
            case ETHANOL -> {
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 30 * 20, 5));
                if (target instanceof Player p) {
                    p.getFoodData().eat(1, 0.1F);
                }
            }
            case CYANIDE -> {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 120 * 20, 3));
            }
            case PENICILLIN -> {
                target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300 * 20, 1));
            }
            case TESTOSTERONE -> {
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 180 * 20, 1));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 180 * 20, 1));
            }
            case XANAX -> {
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200 * 20, 3));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200 * 20, 3));
            }
            case PANTHERINE -> {
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200 * 20, 5));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200 * 20, 2));
            }
            case MESCALINE -> {
                target.hurt(level.damageSources().generic(), 2.0F);
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60 * 20, 4));
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30 * 20, 4));
            }
            case ASPIRIN -> {
                target.removeAllEffects();
                target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 180 * 20, 1));
            }
            case ACID_FIRE -> {
                target.igniteForSeconds(50);
                target.hurt(level.damageSources().magic(), 6.0F);
                if (level instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.ITEM_SLIME, x, y, z, 15, 0.3, 0.3, 0.3, 0.05);
                    sl.playSound(null, x, y, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
                }
            }
            case TETRODOTOXIN -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30 * 20, 8));
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 30 * 20, 1));
            }
            case FINGOLIMOD -> {
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60 * 20, 1));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60 * 20, 1));
                target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60 * 20, 1));
                target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 300 * 20, 1));
            }
            case THC -> {
                target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60 * 20, 1));
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 3600 * 20, 5));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3600 * 20, 3));
                target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 7200 * 20, 20));
            }
            case NODULARIN -> {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 60 * 20, 3));
                target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 480 * 20, 3));
            }
            case HIST -> {
                target.removeAllEffects();
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 20, 5));
            }
            case PAL2 -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60 * 20, 8));
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60 * 20, 2));
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60 * 20, 2));
            }
            case THEOBROMINE -> {
                target.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 300 * 20, 10));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300 * 20, 5));
            }
            case RETINOL -> {
                target.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300 * 20, 0));
            }
            case AMINO_ACID -> {
                if (target instanceof Player p) {
                    p.getFoodData().eat(2, 0.1F);
                }
                target.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 120 * 20, 3));
                target.addEffect(new MobEffectInstance(MobEffects.JUMP, 120 * 20, 3));
            }
            case COCAINE -> {
                target.hurt(level.damageSources().generic(), 4.0F);
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120 * 20, 5));
                target.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 120 * 20, 5));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120 * 20, 12));
            }
            case COCAINE_HCL -> {
                target.hurt(level.damageSources().generic(), 4.0F);
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 3600 * 20, 5));
                target.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600 * 20, 5));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 3600 * 20, 10));
            }
            case METBLUE -> {
                target.removeAllEffects();
                target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30 * 20, 4));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30 * 20, 6));
            }
            case MEOH -> {
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120 * 20, 6));
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 120 * 20, 2));
            }
            case RADCHLOR -> {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60 * 20, 6));
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 60 * 20, 6));
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 30 * 20, 1));
            }
            case CAULERPENYNE -> {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120 * 20, 6));
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120 * 20, 4));
            }
            case LATROPINE -> {
                target.hurt(level.damageSources().generic(), 4.0F);
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120 * 20, 2));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120 * 20, 2));
            }
            case TNT -> {
                level.explode(null, target.getX(), target.getY(), target.getZ(), 2.0F, Level.ExplosionInteraction.NONE);
            }
            case NONE -> {
                // Mineral / inert molecule: no effect
            }
            default -> {
                // Hazardous unhandled chemical: "Don't drink random shit kids"
                target.hurt(level.damageSources().generic(), 5.0F);
            }
        }
    }
}
