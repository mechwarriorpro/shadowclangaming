package com.sp.block;

import com.sp.DestroyingMinecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(Registries.BLOCK, DestroyingMinecraft.MOD_ID);
    public static void init() {}
}
