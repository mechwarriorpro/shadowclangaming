package com.shadowclangaming.block;

import com.shadowclangaming.chem.Element;
import com.shadowclangaming.item.ElementVialItem;
import com.shadowclangaming.registry.SCGBlockEntities;
import com.shadowclangaming.registry.SCGItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Centrifuge / Isotope Separator machine.
 *
 * <p>Inspired by the classic MineChem & Nuclear Science centrifuges:
 * High-speed rotational separation of elements into pure isotopes and components:
 * <ul>
 *   <li>Uranium -> U-238 & U-235</li>
 *   <li>Hydrogen -> H & Tritium (T)</li>
 *   <li>Raw Uranium ore -> concentrated U-238 & U-235 vials</li>
 * </ul>
 */
public class CentrifugeBlockEntity extends BlockEntity {

    public static final int SPIN_TIME = 80;

    private ItemStack inputStack = ItemStack.EMPTY;
    private final List<ItemStack> outputBuffer = new ArrayList<>();
    private int progress = 0;

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.CENTRIFUGE.get(), pos, state);
    }

    public boolean insert(ItemStack stack, Player player) {
        if (stack.isEmpty() || !isCentrifugable(stack)) {
            if (level != null && !level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("message.shadowclangaming.centrifuge.invalid")
                                .withStyle(ChatFormatting.GRAY), true);
            }
            return false;
        }

        if (!inputStack.isEmpty()) {
            return false;
        }

        this.inputStack = stack.copyWithCount(1);
        stack.shrink(1);
        this.progress = 0;
        setChanged();

        if (level != null && !level.isClientSide()) {
            level.playSound(null, getBlockPos(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
            player.displayClientMessage(
                    Component.translatable("message.shadowclangaming.centrifuge.started", inputStack.getHoverName())
                            .withStyle(ChatFormatting.AQUA), true);
        }
        return true;
    }

    public boolean collect(Player player) {
        if (outputBuffer.isEmpty()) {
            if (level != null && !level.isClientSide()) {
                String status = inputStack.isEmpty() ? "Empty" : "Spinning (" + (progress * 100 / SPIN_TIME) + "%)";
                player.displayClientMessage(
                        Component.translatable("message.shadowclangaming.centrifuge.status", status)
                                .withStyle(ChatFormatting.GRAY), true);
            }
            return false;
        }

        for (ItemStack out : new ArrayList<>(outputBuffer)) {
            if (player.getInventory().add(out)) {
                outputBuffer.remove(out);
            } else {
                player.drop(out, false);
                outputBuffer.remove(out);
            }
        }
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.playSound(null, getBlockPos(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.8F, 1.2F);
        }
        return true;
    }

    public static boolean isCentrifugable(ItemStack stack) {
        if (stack.is(SCGItems.RAW_URANIUM.get()) || stack.is(SCGItems.URANIUM_ORE.get())) {
            return true;
        }
        if (stack.is(Items.GLOWSTONE_DUST) || stack.is(Items.REDSTONE)) {
            return true;
        }
        if (stack.getItem() instanceof ElementVialItem) {
            Element e = ElementVialItem.elementOf(stack);
            return e == Element.U || e == Element.U238 || e == Element.H || e == Element.C;
        }
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CentrifugeBlockEntity centrifuge) {
        if (level.isClientSide()) return;

        if (!centrifuge.inputStack.isEmpty()) {
            centrifuge.progress++;

            if (level instanceof ServerLevel serverLevel && centrifuge.progress % 8 == 0) {
                serverLevel.playSound(null, pos, SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, 0.4F, 1.8F);
                serverLevel.sendParticles(ParticleTypes.CLOUD,
                        pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                        3, 0.15, 0.1, 0.15, 0.02);
            }

            if (centrifuge.progress >= SPIN_TIME) {
                centrifuge.progress = 0;
                centrifuge.completeSpin((ServerLevel) level, pos);
            }
            centrifuge.setChanged();
        }
    }

    private void completeSpin(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        ItemStack in = this.inputStack;
        this.inputStack = ItemStack.EMPTY;

        if (in.is(SCGItems.RAW_URANIUM.get()) || in.is(SCGItems.URANIUM_ORE.get())) {
            outputBuffer.add(ElementVialItem.create(Element.U238, 2 + random.nextInt(2)));
            if (random.nextFloat() < 0.35F) {
                outputBuffer.add(ElementVialItem.create(Element.U235, 1));
            }
            outputBuffer.add(ElementVialItem.create(Element.PB, 1));
        } else if (in.getItem() instanceof ElementVialItem) {
            Element elem = ElementVialItem.elementOf(in);
            if (elem == Element.U || elem == Element.U238) {
                outputBuffer.add(ElementVialItem.create(Element.U238, 1));
                if (random.nextFloat() < 0.25F) {
                    outputBuffer.add(ElementVialItem.create(Element.U235, 1));
                }
            } else if (elem == Element.H) {
                outputBuffer.add(ElementVialItem.create(Element.H, 1));
                if (random.nextFloat() < 0.15F) {
                    outputBuffer.add(ElementVialItem.create(Element.T, 1));
                }
            } else if (elem == Element.C) {
                outputBuffer.add(ElementVialItem.create(Element.C, 1));
            }
        } else if (in.is(Items.GLOWSTONE_DUST)) {
            outputBuffer.add(ElementVialItem.create(Element.P, 2));
            outputBuffer.add(ElementVialItem.create(Element.HE, 1));
            if (random.nextFloat() < 0.2F) {
                outputBuffer.add(ElementVialItem.create(Element.NE, 1));
            }
        } else if (in.is(Items.REDSTONE)) {
            outputBuffer.add(ElementVialItem.create(Element.FE, 1));
            outputBuffer.add(ElementVialItem.create(Element.CU, 1));
            outputBuffer.add(ElementVialItem.create(Element.SI, 1));
        }

        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7F, 1.8F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                10, 0.2, 0.2, 0.2, 0.05);
    }

    public void dropContents(Level level, BlockPos pos) {
        if (!inputStack.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, inputStack);
            inputStack = ItemStack.EMPTY;
        }
        for (ItemStack out : outputBuffer) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, out);
        }
        outputBuffer.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Progress", progress);
        if (!inputStack.isEmpty()) {
            tag.put("Input", inputStack.save(registries));
        }
        tag.putInt("OutputCount", outputBuffer.size());
        for (int i = 0; i < outputBuffer.size(); i++) {
            tag.put("Output" + i, outputBuffer.get(i).save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt("Progress");
        if (tag.contains("Input")) {
            inputStack = ItemStack.parse(registries, tag.getCompound("Input")).orElse(ItemStack.EMPTY);
        } else {
            inputStack = ItemStack.EMPTY;
        }
        outputBuffer.clear();
        int outCount = tag.getInt("OutputCount");
        for (int i = 0; i < outCount; i++) {
            outputBuffer.add(ItemStack.parse(registries, tag.getCompound("Output" + i)).orElse(ItemStack.EMPTY));
        }
    }
}
