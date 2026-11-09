package com.shadowclangaming.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

/** Turns element vials into useful items (enriched uranium, tritium, plutonium, gunpowder...). */
public class SynthesizerBlock extends ProcessorBlock {
    public static final MapCodec<SynthesizerBlock> CODEC = simpleCodec(SynthesizerBlock::new);

    public SynthesizerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public ProcessorKind kind() {
        return ProcessorKind.SYNTHESIZER;
    }
}
