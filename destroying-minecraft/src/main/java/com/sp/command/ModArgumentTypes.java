package com.sp.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sp.destruction.DestructionType;
import net.minecraft.commands.CommandSourceStack;

public class ModArgumentTypes {
    public static ArgumentType<DestructionType> destructionType() {
        return new ArgumentType<>() {
            @Override public DestructionType parse(com.mojang.brigadier.StringReader reader) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                String s = reader.readUnquotedString();
                return DestructionType.getFromName(s).orElseThrow(() -> com.mojang.brigadier.exceptions.CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().create());
            }
        };
    }
    public static DestructionType getDestructionType(CommandContext<CommandSourceStack> ctx, String name) { return ctx.getArgument(name, DestructionType.class); }
    public static void registerModArgumentTypes() {}
}
