package com.shadowclangaming.block;

import com.shadowclangaming.blueprint.BlueprintMath;
import com.shadowclangaming.blueprint.BuildShape;
import com.shadowclangaming.registry.SCGBlockEntities;
import com.shadowclangaming.registry.SCGBlocks;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.registry.SCGItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The rifle bench: ONE block, an inventory, and a craft button.
 *
 * ──────────────────── WHY IT CHANGED ────────────────────
 *
 * The old table made you mark two corners of a real multi-block build and sneak-click to consume it.
 * The idea - "a rifle is worth what you built it from" - is good, and the materials still decide tier
 * and efficiency. The workflow was the problem: two corner marks, a bounding box that had to be
 * exactly right, a build that had to contain an Emitter, and no way to see what the table thought it
 * was looking at until after it had eaten your base.
 *
 * So the build is now a 3x9 inventory. Throw the blocks in, pick how many barrels and how strong they
 * are (1..128 each), press CRAFT. The materials still decide the frame's tier and efficiency; the two
 * dials decide everything else.
 *
 *   tier        1..5    which component tiers the frame can hold
 *   efficiency  60..95  what percentage of a spell's mana cost you pay
 *   barrels     1..128  -5% cooldown each, +2.5% mana each, +2% power each
 *   strength    1..128  +3% power and +2% mana each, +1% cooldown each
 *
 * At 128/128 that is 17x power for roughly 80x the mana per shot - the gun is a cannon and the bill is
 * a cannon's bill.
 */
public class BlueprintTableBlockEntity extends BlockEntity implements Container {

    public static final int SLOTS = 27;
    public static final int ROWS = 3;
    public static final int COLUMNS = 9;

    public static final int MIN_BARRELS = 1;
    public static final int MAX_BARRELS = BlueprintMath.MAX_BARRELS;
    public static final int MIN_STRENGTH = 1;
    public static final int MAX_STRENGTH = BlueprintMath.MAX_STRENGTH;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private int barrels = 1;
    private int strength = 1;

    public BlueprintTableBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.BLUEPRINT_TABLE.get(), pos, state);
    }

    // ------------------------------------------------------------------ the two dials

    public int barrels() {
        return barrels;
    }

    public int strength() {
        return strength;
    }

    public void setBarrels(int value) {
        barrels = Math.max(MIN_BARRELS, Math.min(MAX_BARRELS, value));
        setChanged();
    }

    public void setStrength(int value) {
        strength = Math.max(MIN_STRENGTH, Math.min(MAX_STRENGTH, value));
        setChanged();
    }

    // ------------------------------------------------------------------ what is on the bench

    /** One block's contribution to the frame, gathered from the inventory. */
    public record Materials(int blocks, long score, int emitters, List<BuildShape.Part> parts) {
    }

    /** Walk the inventory and add up what is in it. Pure: nothing is consumed here. */
    public Materials materials() {
        int blocks = 0;
        long score = 0L;
        int emitters = 0;
        List<BuildShape.Part> parts = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int count = Math.min(stack.getCount(), 8);
            for (int i = 0; i < count; i++) {
                blocks++;
                BlockState state;
                if (stack.getItem() instanceof BlockItem blockItem) {
                    Block block = blockItem.getBlock();
                    state = block.defaultBlockState();
                    if (block == SCGBlocks.EMITTER.get()) {
                        emitters++;
                    } else {
                        score += BlueprintMath.materialValue(
                                BuiltInRegistries.BLOCK.getKey(block).getPath());
                    }
                } else {
                    // A non-block item still counts as material, just unremarkable material.
                    state = SCGBlocks.EMITTER.get().defaultBlockState();
                    score += 1L;
                }
                // Lay the bench out as a line of blocks: the gun still renders as what it was made of.
                parts.add(new BuildShape.Part(parts.size(), 0, 0, state));
            }
        }
        return new Materials(blocks, score, emitters, parts);
    }

    /** The tier/efficiency the current bench contents would produce. Null when there is nothing valid. */
    public BlueprintMath.RifleSpec previewSpec() {
        Materials materials = materials();
        if (materials.blocks() < BlueprintMath.MIN_BLOCKS || materials.emitters() == 0) {
            return null;
        }
        return BlueprintMath.spec(materials.blocks(), materials.score());
    }

    // ------------------------------------------------------------------ crafting

    /**
     * Consume the bench and hand over a rifle.
     *
     * Every check happens before a single item is removed, so a rejected craft cannot eat a stack.
     */
    public boolean craft(ServerPlayer player) {
        Materials materials = materials();
        if (materials.blocks() < BlueprintMath.MIN_BLOCKS) {
            say(player, Component.translatable("message.shadowclangaming.bench.too_small",
                    materials.blocks(), BlueprintMath.MIN_BLOCKS).withStyle(ChatFormatting.RED));
            return false;
        }
        if (materials.emitters() == 0) {
            say(player, Component.translatable("message.shadowclangaming.bench.no_emitter")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        BlueprintMath.RifleSpec spec = BlueprintMath.spec(materials.blocks(), materials.score());
        BuildShape shape = BuildShape.of(materials.parts());

        ItemStack rifle = new ItemStack(SCGItems.SPELL_RIFLE.get());
        rifle.set(SCGDataComponents.RIFLE_TIER.get(), spec.tier());
        rifle.set(SCGDataComponents.RIFLE_EFFICIENCY.get(), spec.efficiencyPercent());
        rifle.set(SCGDataComponents.RIFLE_BUILD.get(), shape);
        rifle.set(SCGDataComponents.RIFLE_EMITTERS.get(), barrels);
        rifle.set(SCGDataComponents.RIFLE_BARREL.get(), 0);
        rifle.set(SCGDataComponents.RIFLE_STRENGTH.get(), strength);

        // Now, and only now, take the materials.
        for (int slot = 0; slot < SLOTS; slot++) {
            items.set(slot, ItemStack.EMPTY);
        }
        setChanged();

        if (!player.getInventory().add(rifle)) {
            if (level instanceof ServerLevel serverLevel) {
                net.minecraft.world.Containers.dropItemStack(serverLevel,
                        worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D,
                        worldPosition.getZ() + 0.5D, rifle);
            }
        }

        say(player, Component.translatable("message.shadowclangaming.bench.crafted",
                spec.tier(), spec.efficiencyPercent(), barrels, strength).withStyle(ChatFormatting.GOLD));
        if (level != null) {
            level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.6F);
        }
        return true;
    }

    private static void say(Player player, Component message) {
        player.displayClientMessage(message, false);
    }

    // ------------------------------------------------------------------ legacy corner API
    //
    // The corner-marking flow is gone from the block's UI, but the payload that used to drive it is
    // still registered, and a client that sends it should not crash a server. These three methods are
    // therefore kept as no-ops with the same signatures; they simply explain that the bench changed.

    /** Retained signature for the old corner payload. Now just tells the player where the bench is. */
    public void markCorner(Player player, BlockPos corner, boolean fromClient) {
        say(player, Component.translatable("message.shadowclangaming.bench.use_gui")
                .withStyle(ChatFormatting.YELLOW));
    }

    /** What the player is pointing at, clamped to reach. Unchanged from the corner build. */
    public static BlockPos lookedAt(net.minecraft.world.level.Level level, Player player, BlockPos table) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(MARK_REACH));
        BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getBlockPos();
    }

    /** How far a corner may be marked from. Kept for {@link #lookedAt}. */
    public static final double MARK_REACH = 32.0D;

    // ------------------------------------------------------------------ Container

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    // ------------------------------------------------------------------ save / load

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Barrels", barrels);
        tag.putInt("Strength", strength);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        barrels = tag.contains("Barrels") ? tag.getInt("Barrels") : 1;
        strength = tag.contains("Strength") ? tag.getInt("Strength") : 1;
        barrels = Math.max(MIN_BARRELS, Math.min(MAX_BARRELS, barrels));
        strength = Math.max(MIN_STRENGTH, Math.min(MAX_STRENGTH, strength));
    }
}
