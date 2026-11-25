package com.shadowclangaming.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A blood circle: the ritual site for the sacrifice route.
 *
 * It gives no mana by itself. It is a *place*, and its whole job is to be somewhere specific that a
 * witch, a baby villager and a cat can all die within 16 blocks of, on a full moon, while the killer
 * holds a Totem of Undying. Getting the pact switches {@link #ACTIVE} on so the circle visibly wakes
 * up, and from then on it is also the altar you keep feeding with villagers.
 */
public class BloodCircleBlock extends CircleBlock {
    public static final MapCodec<BloodCircleBlock> CODEC = simpleCodec(BloodCircleBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public BloodCircleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, Boolean.FALSE));
    }

    @Override
    public boolean isBlood() {
        return true;
    }

    /** Deliberately does NOT call super: a blood circle has no size property, so its blockstate is 2 variants. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
