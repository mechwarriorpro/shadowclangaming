package com.sp.component;

import com.sp.DestroyingMinecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponentTypes {
    public static final DeferredRegister<DataComponentType<?>> REGISTRY = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, DestroyingMinecraft.MOD_ID);
    public static void registerDataComponentTypes() {}
}
