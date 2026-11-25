package com.sp.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sp.destruction.DestructionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Brigadier ArgumentType for {@link DestructionType}.
 *
 * <p>Fabric original used the anonymous ArgType in {@code ModArgumentTypes} without
 * explicit registration — Fabric's command registration auto-synced via
 * {@code PayloadTypeRegistry}. NeoForge requires an explicit
 * {@code ArgumentTypeInfo} registered to {@code Registries.COMMAND_ARGUMENT_TYPE}
 * so the server can serialize the command tree to clients (required for both
 * dedicated and integrated servers on 1.21.1).</p>
 */
public class DestructionTypeArgument implements ArgumentType<DestructionType> {

    private static final DynamicCommandExceptionType ERROR_UNKNOWN_TYPE = new DynamicCommandExceptionType(
            name -> Component.translatableEscape("commands.destroying_minecraft.destruction.unknown", name)
    );

    private static final Collection<String> EXAMPLES = Arrays.stream(DestructionType.values())
            .map(DestructionType::getName)
            .toList();

    /** Factory used by {@link ModArgumentTypes} and by {@code SingletonArgumentInfo}. */
    public static DestructionTypeArgument destructionType() {
        return new DestructionTypeArgument();
    }

    @Override
    public DestructionType parse(StringReader reader) throws CommandSyntaxException {
        String s = reader.readUnquotedString();
        return DestructionType.getFromName(s)
                .orElseThrow(() -> ERROR_UNKNOWN_TYPE.createWithContext(reader, s));
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        for (DestructionType type : DestructionType.values()) {
            if (type.getName().toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(type.getName());
            }
        }
        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
