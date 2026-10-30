package com.shadowclangaming.block;

import com.shadowclangaming.chem.DecompositionTable;
import com.shadowclangaming.chem.Element;
import com.shadowclangaming.item.ChemistJournalItem;
import com.shadowclangaming.registry.SCGBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Tabletop optical electron microscope for inspecting chemical compositions.
 *
 * <p>Inspired by the classic MineChem Microscope:
 * Place an item on the specimen stage to analyze its elemental breakdown.
 * If the player carries a Chemist's Journal, the recipe is automatically logged.
 */
public class MicroscopeBlockEntity extends BlockEntity {

    private ItemStack specimen = ItemStack.EMPTY;

    public MicroscopeBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.MICROSCOPE.get(), pos, state);
    }

    public ItemStack getSpecimen() {
        return specimen;
    }

    public boolean inspect(ItemStack stack, Player player) {
        if (stack.isEmpty()) {
            return false;
        }

        Level level = getLevel();
        if (level == null) {
            return false;
        }

        // Place on stage
        this.specimen = stack.copyWithCount(1);
        setChanged();

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            // Sound and optics visual
            serverLevel.playSound(null, getBlockPos(), SoundEvents.ENCHANTMENT_TABLE_USE,
                    SoundSource.BLOCKS, 1.0F, 1.4F);
            serverLevel.playSound(null, getBlockPos(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS, 0.7F, 1.6F);
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    getBlockPos().getX() + 0.5, getBlockPos().getY() + 0.8, getBlockPos().getZ() + 0.5,
                    15, 0.3, 0.2, 0.3, 0.1);

            List<DecompositionTable.Yield> yields = DecompositionTable.yieldsFor(specimen);
            if (yields.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("message.shadowclangaming.microscope.unknown",
                                specimen.getHoverName()).withStyle(ChatFormatting.GRAY), false);
            } else {
                MutableComponent msg = Component.literal("🔬 ")
                        .append(Component.translatable("message.shadowclangaming.microscope.header",
                                specimen.getHoverName()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                        .append(Component.literal("\n   "));

                for (int i = 0; i < yields.size(); i++) {
                    DecompositionTable.Yield y = yields.get(i);
                    Element elem = y.element();
                    String countStr = y.min() == y.max() ? String.valueOf(y.min()) : (y.min() + "-" + y.max());
                    msg.append(Component.literal(countStr + "x ").withStyle(ChatFormatting.WHITE))
                       .append(Component.literal(elem.symbol()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                       .append(Component.literal(" (" + elem.displayName() + ", Z=" + elem.atomicNumber() + ")")
                               .withStyle(ChatFormatting.GRAY));
                    if (i < yields.size() - 1) {
                        msg.append(Component.literal(", "));
                    }
                }
                player.displayClientMessage(msg, false);

                // Check for Chemist's Journal in inventory to record discovery
                ChemistJournalItem.recordDiscovery(player, specimen);
            }
        }
        return true;
    }

    public boolean removeSpecimen(Player player) {
        if (specimen.isEmpty()) {
            return false;
        }
        if (!player.getInventory().add(specimen)) {
            player.drop(specimen, false);
        }
        this.specimen = ItemStack.EMPTY;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.playSound(null, getBlockPos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return true;
    }

    public void dropContents(Level level, BlockPos pos) {
        if (!specimen.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, specimen);
            this.specimen = ItemStack.EMPTY;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!specimen.isEmpty()) {
            tag.put("Specimen", specimen.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Specimen")) {
            specimen = ItemStack.parse(registries, tag.getCompound("Specimen")).orElse(ItemStack.EMPTY);
        } else {
            specimen = ItemStack.EMPTY;
        }
    }
}
