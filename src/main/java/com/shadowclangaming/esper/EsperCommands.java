package com.shadowclangaming.esper;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid="shadowclangaming")
public final class EsperCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("esper")
            .then(Commands.literal("choose").then(Commands.argument("category", StringArgumentType.word()).executes(ctx -> {
                String cat = StringArgumentType.getString(ctx, "category");
                var player = ctx.getSource().getPlayerOrException();
                var category = EsperCategory.byId(cat);
                var data = EsperHolder.get(player);
                data.setCategory(category);
                EsperHolder.set(player, data);
                ctx.getSource().sendSuccess(() -> Component.literal("Class set to "+category.display()).withStyle(category.color()), true);
                return 1;
            })))
            .then(Commands.literal("info").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException();
                var d = EsperHolder.get(p);
                ctx.getSource().sendSuccess(() -> Component.literal(d.category().display()+" Lv"+d.level()+" XP "+d.xp()+"/"+d.xpForNext()+" Unlocked: "+d.unlocked()), false);
                return 1;
            }))
            .then(Commands.literal("use").then(Commands.argument("skill", StringArgumentType.word()).executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException();
                if(!(p instanceof ServerPlayer sp)) return 0;
                var d = EsperHolder.get(sp);
                var skill = EsperSkill.byId(StringArgumentType.getString(ctx,"skill"));
                if(!skill.canUse(d)){ ctx.getSource().sendFailure(Component.literal("Skill locked or not enough level").withStyle(ChatFormatting.RED)); return 0; }
                skill.use(sp.serverLevel(), sp, d);
                return 1;
            })))
            .then(Commands.literal("addxp").then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,1000)).executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException();
                var d = EsperHolder.get(p);
                int amt = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx,"amount");
                d.addXp(amt); EsperHolder.set(p,d);
                ctx.getSource().sendSuccess(()->Component.literal("Added "+amt+" esper XP. Now Lv"+d.level()), true); return 1;
            })))
        );
    }
}
