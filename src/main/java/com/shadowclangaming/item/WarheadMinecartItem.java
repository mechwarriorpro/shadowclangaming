package com.shadowclangaming.item;

import com.shadowclangaming.entity.WarheadMinecart;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/**
 * A minecart loaded with a warhead: {@code minecart_<warhead>}.
 *
 * Vanilla placement logic, copied deliberately rather than reused, because {@code MinecartItem} constructs
 * a hard-coded vanilla cart: it is a single class with a static factory, so a subclass cannot change what
 * gets spawned. This does the same rail checks and spawns {@link WarheadMinecart} instead.
 */
public class WarheadMinecartItem extends Item {

    private final WarheadType warhead;

    public WarheadMinecartItem(WarheadType warhead, Properties properties) {
        super(properties);
        this.warhead = warhead;
    }

    public WarheadType warhead() {
        return warhead;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.RAILS)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (!level.isClientSide) {
            RailShape shape = state.getBlock() instanceof BaseRailBlock rail
                    ? rail.getRailDirection(state, level, pos, null)
                    : RailShape.NORTH_SOUTH;
            double y = shape.isAscending() ? 0.5D : 0.0D;
            double x = pos.getX() + 0.5D;
            double z = pos.getZ() + 0.5D;
            if (shape == RailShape.ASCENDING_EAST) {
                x += 0.5D;
            } else if (shape == RailShape.ASCENDING_WEST) {
                x -= 0.5D;
            } else if (shape == RailShape.ASCENDING_NORTH) {
                z -= 0.5D;
            } else if (shape == RailShape.ASCENDING_SOUTH) {
                z += 0.5D;
            }
            WarheadMinecart cart = new WarheadMinecart(level, x, y + 0.0625D, z, warhead, this);
            cart.setYRot(context.getHorizontalDirection().getAxis() == Direction.Axis.Z
                    ? 180.0F : 0.0F);
            level.addFreshEntity(cart);
            level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, pos);
            stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(warhead.translationKey()).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.minecart_warhead.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
        // An activator rail, a collision or a broken cart lights it; this is how long it then cooks for,
        // on top of the four seconds vanilla's own priming takes.
        tooltip.add(Component.translatable("tooltip.shadowclangaming.fuse_minecart",
                String.format(java.util.Locale.ROOT, "%.1f",
                        com.shadowclangaming.warhead.ExplosivePackaging.fuseSeconds(
                                warhead.id(), warhead.basePower(),
                                com.shadowclangaming.warhead.ExplosivePackaging.Form.MINECART)))
                .withStyle(ChatFormatting.GRAY));
    }
}
