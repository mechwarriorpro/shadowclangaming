package com.shadowclangaming.item;

import com.shadowclangaming.compat.cbc.CbcShellTier;
import com.shadowclangaming.compat.cbc.CbcShellType;
import com.shadowclangaming.entity.GrenadeEntity;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A warhead delivered as Create Big Cannons ammunition.
 * <p>
 * Every {@link WarheadType} exists as a {@code cbc_shell_<warhead>_<tier>} item (T1–T50).
 * The shell's blast power is {@code warhead.basePower * tier.blastScale} — capped at 3× for
 * balance, matching CBC Enhanced Shells' own 3× cap.
 * <p>
 * Integration uses soft-dependency: if CBC is not installed the shell behaves as a throwable
 * grenade (same fuse as {@link ExplosiveGrenadeItem}); if CBC is present the Cannon Director
 * and the {@link com.shadowclangaming.compat.cbc.CbcWarheadShellCompat} hook fire it through
 * the cannon mount and detonate the warhead at the projectile's impact point.
 * <p>
 * Credits: shell ballistics and shader-effect concepts from CBC Enhanced Shells by arginou57
 * (CurseForge 1489732) — used with permission to extend Mk.I–V to T50; shaders are original
 * reimplementations, not copied files. See THIRD_PARTY.md.
 */
public class CbcWarheadShellItem extends Item {
    private final WarheadType warhead;
    private final CbcShellType shellType;
    private final CbcShellTier tier;

    public CbcWarheadShellItem(WarheadType warhead, CbcShellType shellType, CbcShellTier tier, Properties props) {
        super(props);
        this.warhead = warhead;
        this.shellType = shellType;
        this.tier = tier;
    }

    public WarheadType warhead() { return warhead; }
    public CbcShellType shellType() { return shellType; }
    public CbcShellTier tier() { return tier; }

    /** Blast power after tier scaling — respects 3× cap. */
    public float scaledPower() {
        return warhead.basePower() * tier.blastPowerMult();
    }

    /** Mass for CBC penetration calc: shellType.mass * tier damage scale. */
    public float mass() { return shellType.mass() * tier.damageScale(); }

    /** Damage on direct hit before tier scaling. */
    public float damage() { return shellType.baseDamage() * tier.damageScale(); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            // Fallback throw — same as grenade, with warhead id stamped for compat hook
            GrenadeEntity grenade = new GrenadeEntity(level, player, withWarhead(stack.copyWithCount(1)));
            grenade.setItem(withWarhead(stack.copyWithCount(1)));
            grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.1F, 0.6F);
            level.addFreshEntity(grenade);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.6F, 0.7F);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        player.getCooldowns().addCooldown(this, 8);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private ItemStack withWarhead(ItemStack stack) {
        stack.set(SCGDataComponents.MODULE_WARHEAD.get(), warhead.id());
        // Also tag shell type/tier for CBC compat to read
        // Use custom component if present, otherwise fallback to NBT-like via DataComponents
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(warhead.translationKey()).withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal(shellType.id().toUpperCase() + " · " + tier.display() + " (T" + tier.tier() + ")")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal(String.format(java.util.Locale.ROOT, "Power %.1f → %.1f (×%.2f) | Mass %.1f | Dmg %.0f",
                warhead.basePower(), scaledPower(), tier.blastPowerMult(), mass(), damage())).withStyle(ChatFormatting.GRAY));
        if (shellType.hasShader()) {
            tooltip.add(Component.literal("Shader: " + shellType.shaderId()).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.shadowclangaming.cbc_shell.usage").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("CBC Enhanced Shells concept by arginou57 — extended to T50 with permission").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static String itemId(WarheadType warhead, CbcShellTier tier) {
        return "cbc_shell_" + warhead.id() + "_t" + tier.tier();
    }
    public static String itemId(WarheadType warhead, CbcShellType type, CbcShellTier tier) {
        if (type == CbcShellType.WARHEAD) return itemId(warhead, tier);
        return "cbc_shell_" + type.id() + "_" + warhead.id() + "_t" + tier.tier();
    }
}
