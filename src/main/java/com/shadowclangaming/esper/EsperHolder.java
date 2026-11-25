package com.shadowclangaming.esper;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

/**
 * Player attachment for EsperData. Uses NeoForge attachments (1.21.1) so it persists.
 */
public final class EsperHolder {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "shadowclangaming");

    public static final Supplier<AttachmentType<EsperData>> ESPER = ATTACHMENTS.register("esper_data",
            () -> AttachmentType.builder(() -> new EsperData()).serialize(EsperData.CODEC).copyOnDeath().build());

    private EsperHolder(){}

    public static EsperData get(Player p){
        var a = p.getData(ESPER.get());
        return a==null? new EsperData() : a;
    }
    public static void set(Player p, EsperData d){ p.setData(ESPER.get(), d); }
}
