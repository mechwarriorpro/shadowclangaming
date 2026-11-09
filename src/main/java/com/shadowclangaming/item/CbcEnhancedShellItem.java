package com.shadowclangaming.item;

import com.shadowclangaming.compat.cbc.CbcShellTier;
import com.shadowclangaming.compat.cbc.CbcShellType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * T50 extension of CBC Enhanced Shells — direct Mk.I–V → T50 progression.
 * <p>
 * Original: 9 standard shell types + 5 shader exotics × 5 tiers (Mk.I–V) by arginou57.
 * Extended: same 14 types × 50 tiers (T1–T50) with linear scaling and original 3× blast cap.
 * Each tier is a distinct item: {@code cbc_enhanced_<type>_t<tier>}.
 * <p>
 * Stats scale as: damage = baseDamage * tier.damageScale, blast = baseBlast * tier.blastPowerMult()
 * (capped 3×), velocity = base * tier.velocityMult(). Color tints follow original palette:
 * orange→red→violet→cyan→gold→white at T50.
 * <p>
 * Shader types (plasma/gravitational/vortex/disintegration/singularity) additionally trigger
 * their GLSL post effect on detonation via {@link com.shadowclangaming.compat.cbc.CbcShaderFx}.
 * <p>
 * Credits: concept, balancing and shader effect designs from CBC Enhanced Shells by arginou57
 * (CurseForge 1489732) — extended to T50 with explicit permission 2026-11-09. This file is
 * original code; shader GLSL is reimplemented from described technique, not copied.
 * See THIRD_PARTY.md for full attribution.
 */
public class CbcEnhancedShellItem extends Item {
    private final CbcShellType type;
    private final CbcShellTier tier;

    public CbcEnhancedShellItem(CbcShellType type, CbcShellTier tier, Properties props) {
        super(props);
        this.type = type;
        this.tier = tier;
    }
    public CbcShellType type() { return type; }
    public CbcShellTier tier() { return tier; }

    public float scaledDamage() { return type.baseDamage() * tier.damageScale(); }
    public float scaledBlast() { return type.baseBlast() * tier.blastPowerMult(); }
    public float scaledMass() { return type.mass() * tier.damageScale(); }
    public float scaledVelocity() { return tier.velocityMult(); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(type.id().toUpperCase() + " · " + tier.display() + " (T" + tier.tier() + ")").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal(String.format(java.util.Locale.ROOT, "Blast %.1f → %.1f (×%.2f) | Dmg %.0f | Mass %.1f | Vel ×%.2f",
                type.baseBlast(), scaledBlast(), tier.blastPowerMult(), scaledDamage(), scaledMass(), scaledVelocity())).withStyle(ChatFormatting.GRAY));
        if (type.hasShader()) tooltip.add(Component.literal("Shader: " + type.shaderId() + " — intensity " + String.format("%.0f%%", tier.tier()/50f*100)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Fuze: " + type.fuze().name()).withStyle(ChatFormatting.DARK_GRAY));
        if (tier.tier() > 5) tooltip.add(Component.literal("T50 extension — beyond Mk.V, with permission from arginou57").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("CBC Enhanced Shells by arginou57 — extended to T50 with permission").withStyle(ChatFormatting.DARK_GRAY));
    }
}
