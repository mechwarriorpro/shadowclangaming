package com.sp.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sp.destruction.DestructionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge registration for the custom {@link DestructionTypeArgument}.
 *
 * <ol>
 *   <li>{@link DestructionTypeArgument} — the {@code ArgumentType} implementation (parse/suggest).</li>
 *   <li>{@link SingletonArgumentInfo} via {@link ArgumentTypeInfos#registerByClass} — the
 *       {@code ArgumentTypeInfo} (Template) that knows how to serialize the argument for
 *       command-tree sync. Uses {@code contextFree} because this type has no parameters.</li>
 *   <li>Registered to {@code Registries.COMMAND_ARGUMENT_TYPE} via {@link DeferredRegister}
 *       — NeoForge's required path for 1.21.1. The vanilla registry dispatches
 *       {@code serializeToNetwork}/{@code deserializeFromNetwork} (FriendlyByteBuf) and
 *       {@code serializeToJson} (JSON) through the looked-up info.</li>
 *   <li>Registration is on the MOD bus via {@code modEventBus} in {@code DestroyingMinecraft},
 *       so it runs on both the dedicated server and the integrated server (both load the mod
 *       and fire the RegisterEvent).</li>
 *   <li>1.21.1 compatible: {@code SingletonArgumentInfo} writes no payload (empty buffer),
 *       matching the vanilla {@code StringArgumentType}/{@code IntegerArgumentType} singleton
 *       pattern — client and server agree on an empty template.</li>
 * </ol>
 */
public class ModArgumentTypes {

    /** DeferredRegister for {@code ArgumentTypeInfo} — must be registered on the mod bus. */
    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> COMMAND_ARGUMENT_TYPES =
            DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, "destroying_minecraft");

    /**
     * The registered info. {@code registerByClass} does two things atomically:
     * (a) puts the class→info mapping into {@code ArgumentTypeInfos.BY_CLASS} so
     *     {@code ArgumentTypeInfos.byClass(argument.getClass())} finds it at command-tree
     *     serialization time, and (b) returns the info to be registered in the vanilla
     *     {@code COMMAND_ARGUMENT_TYPE} registry (where {@code byId} lookup happens on
     *     deserialization).
     */
    public static final DeferredHolder<ArgumentTypeInfo<?, ?>, SingletonArgumentInfo<DestructionTypeArgument>> DESTRUCTION_TYPE =
            COMMAND_ARGUMENT_TYPES.register("destruction_type",
                    () -> ArgumentTypeInfos.registerByClass(
                            DestructionTypeArgument.class,
                            SingletonArgumentInfo.contextFree(DestructionTypeArgument::destructionType)));

    /** Factory for command trees — returns a concrete {@link DestructionTypeArgument} (not anonymous). */
    public static ArgumentType<DestructionType> destructionType() {
        return DestructionTypeArgument.destructionType();
    }

    public static DestructionType getDestructionType(CommandContext<CommandSourceStack> ctx, String name) {
        return ctx.getArgument(name, DestructionType.class);
    }

    /** Legacy stub kept for compat — actual registration is via {@link #COMMAND_ARGUMENT_TYPES}. */
    public static void registerModArgumentTypes() {}
}
