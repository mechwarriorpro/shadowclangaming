package com.shadowclangaming.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

/** Turns items into element vials. */
public class DecomposerBlock extends ProcessorBlock {
    public static final MapCodec<DecomposerBlock> CODEC = simpleCodec(DecomposerBlock::new);

    public DecomposerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public ProcessorKind kind() {
        return ProcessorKind.DECOMPOSER;
    }
}
