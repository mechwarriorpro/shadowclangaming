package com.shadowclangaming.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * A chalk circle. Stand in the middle and it hands you mana.
 *
 * Four sizes, drawn one step at a time: right-click an existing circle with more chalk to make it
 * bigger. Size is everything - radius and mana both scale:
 *
 *     size 0   radius 2.0   +5 mana/s
 *     size 1   radius 3.5   +15 mana/s
 *     size 2   radius 5.0   +40 mana/s
 *     size 3   radius 7.0   +100 mana/s
 *
 * Players who took the blood pact get NOTHING from any of them, forever. See
 * {@link com.shadowclangaming.magic.Sacrifices}.
 */
public class ChalkCircleBlock extends CircleBlock {
    public static final MapCodec<ChalkCircleBlock> CODEC = simpleCodec(ChalkCircleBlock::new);

    public ChalkCircleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SIZE, 0));
    }

    @Override
    public boolean isBlood() {
        return false;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
