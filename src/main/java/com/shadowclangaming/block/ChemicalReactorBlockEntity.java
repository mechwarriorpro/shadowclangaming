package com.shadowclangaming.block;

import com.shadowclangaming.chem.Element;
import com.shadowclangaming.item.ElementVialItem;
import com.shadowclangaming.registry.SCGBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Chemical Fission & Fusion Reactor.
 *
 * <p>Inspired by the classic MineChem Fusion & Fission chambers:
 * <ul>
 *   <li><b>Fission Mode:</b> Splits an element with atomic number Z into two daughter elements
 *       with Z1 = floor(Z/2) and Z2 = ceil(Z/2). (e.g. Fe(26) -> 2x Al(13); U(92) -> 2x Pd(46)).</li>
 *   <li><b>Fusion Mode:</b> Combines two elements Z_A + Z_B into a heavier element with Z_sum
 *       (e.g. H(1) + H(1) -> He(2); C(6) + O(8) -> Si(14)).</li>
 * </ul>
 */
public class ChemicalReactorBlockEntity extends BlockEntity {

    public enum Mode {
        FISSION, FUSION
    }

    public static final int REACTION_TIME = 80;

    private Mode mode = Mode.FISSION;
    private final List<Element> inputBuffer = new ArrayList<>();
    private final List<ItemStack> outputBuffer = new ArrayList<>();
    private int progress = 0;

    public ChemicalReactorBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.CHEMICAL_REACTOR.get(), pos, state);
    }

    public Mode getMode() {
        return mode;
    }

    public void toggleMode(Player player) {
        this.mode = (mode == Mode.FISSION) ? Mode.FUSION : Mode.FISSION;
        this.progress = 0;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.playSound(null, getBlockPos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.0F, 1.2F);
            player.displayClientMessage(
                    Component.translatable("message.shadowclangaming.reactor.mode_switched", mode.name())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        }
    }

    public boolean insert(ItemStack stack, Player player) {
        if (!(stack.getItem() instanceof ElementVialItem)) {
            return false;
        }

        Element element = ElementVialItem.elementOf(stack);
        if (mode == Mode.FISSION) {
            if (element.atomicNumber() < 2) {
                if (level != null && !level.isClientSide()) {
                    player.displayClientMessage(
                            Component.translatable("message.shadowclangaming.reactor.cannot_fission")
                                    .withStyle(ChatFormatting.RED), true);
                }
                return false;
            }
            if (inputBuffer.size() >= 1) {
                return false;
            }
        } else { // FUSION
            if (inputBuffer.size() >= 2) {
                return false;
            }
        }

        inputBuffer.add(element);
        stack.shrink(1);
        setChanged();

        if (level != null && !level.isClientSide()) {
            level.playSound(null, getBlockPos(), SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8F, 1.4F);
            player.displayClientMessage(
                    Component.translatable("message.shadowclangaming.reactor.inserted",
                            element.displayName(), mode.name())
                            .withStyle(ChatFormatting.AQUA), true);
        }
        return true;
    }

    public boolean collect(Player player) {
        if (outputBuffer.isEmpty()) {
            if (level != null && !level.isClientSide()) {
                String status = inputBuffer.isEmpty() ? "Empty" : "Loaded (" + inputBuffer.size() + ")";
                player.displayClientMessage(
                        Component.translatable("message.shadowclangaming.reactor.status", mode.name(), status)
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChemicalReactorBlockEntity reactor) {
        if (level.isClientSide()) return;

        boolean canRun = false;
        if (reactor.mode == Mode.FISSION && !reactor.inputBuffer.isEmpty()) {
            canRun = true;
        } else if (reactor.mode == Mode.FUSION && reactor.inputBuffer.size() >= 2) {
            int zSum = reactor.inputBuffer.get(0).atomicNumber() + reactor.inputBuffer.get(1).atomicNumber();
            if (zSum <= 118) {
                canRun = true;
            }
        }

        if (canRun) {
            reactor.progress++;
            if (level instanceof ServerLevel serverLevel && reactor.progress % 10 == 0) {
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                        4, 0.2, 0.2, 0.2, 0.05);
                serverLevel.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.3F, 1.8F);
            }

            if (reactor.progress >= REACTION_TIME) {
                reactor.progress = 0;
                reactor.completeReaction((ServerLevel) level, pos);
            }
            reactor.setChanged();
        } else {
            if (reactor.progress > 0) {
                reactor.progress = 0;
                reactor.setChanged();
            }
        }
    }

    private void completeReaction(ServerLevel level, BlockPos pos) {
        if (mode == Mode.FISSION && !inputBuffer.isEmpty()) {
            Element in = inputBuffer.remove(0);
            int z = in.atomicNumber();
            int z1 = z / 2;
            int z2 = z - z1;

            Element e1 = findByAtomicNumber(z1);
            Element e2 = findByAtomicNumber(z2);

            outputBuffer.add(ElementVialItem.create(e1, 1));
            outputBuffer.add(ElementVialItem.create(e2, 1));

            level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.5F, 1.8F);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                    20, 0.3, 0.3, 0.3, 0.05);
        } else if (mode == Mode.FUSION && inputBuffer.size() >= 2) {
            Element in1 = inputBuffer.remove(0);
            Element in2 = inputBuffer.remove(0);
            int zSum = in1.atomicNumber() + in2.atomicNumber();

            Element product = findByAtomicNumber(zSum);
            outputBuffer.add(ElementVialItem.create(product, 1));

            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.5F);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                    25, 0.3, 0.3, 0.3, 0.1);
        }
    }

    public static Element findByAtomicNumber(int z) {
        for (Element e : Element.values()) {
            // Find base elements, matching atomic number
            if (e.atomicNumber() == z && e.name().length() <= 2) {
                return e;
            }
        }
        for (Element e : Element.values()) {
            if (e.atomicNumber() == z) return e;
        }
        return Element.H;
    }

    public void dropContents(Level level, BlockPos pos) {
        for (Element elem : inputBuffer) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    ElementVialItem.create(elem, 1));
        }
        inputBuffer.clear();
        for (ItemStack out : outputBuffer) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, out);
        }
        outputBuffer.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Mode", mode.name());
        tag.putInt("Progress", progress);
        tag.putInt("InputCount", inputBuffer.size());
        for (int i = 0; i < inputBuffer.size(); i++) {
            tag.putString("Input" + i, inputBuffer.get(i).name());
        }
        tag.putInt("OutputCount", outputBuffer.size());
        for (int i = 0; i < outputBuffer.size(); i++) {
            tag.put("Output" + i, outputBuffer.get(i).save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Mode")) {
            mode = Mode.valueOf(tag.getString("Mode"));
        }
        progress = tag.getInt("Progress");
        inputBuffer.clear();
        int inCount = tag.getInt("InputCount");
        for (int i = 0; i < inCount; i++) {
            inputBuffer.add(Element.byName(tag.getString("Input" + i)));
        }
        outputBuffer.clear();
        int outCount = tag.getInt("OutputCount");
        for (int i = 0; i < outCount; i++) {
            outputBuffer.add(ItemStack.parse(registries, tag.getCompound("Output" + i)).orElse(ItemStack.EMPTY));
        }
    }
}
