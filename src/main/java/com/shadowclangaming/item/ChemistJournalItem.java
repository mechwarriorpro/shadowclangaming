package com.shadowclangaming.item;

import com.shadowclangaming.chem.DecompositionTable;
import com.shadowclangaming.chem.Element;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.registry.SCGItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * The Chemist's Field Journal.
 *
 * <p>Inspired by the classic MineChem Chemical Journal:
 * <ul>
 *   <li>Records all chemical formulas discovered using the Microscope or Pocket Microscope.</li>
 *   <li>Right-click to open a comprehensive scientific field compendium displaying the Periodic Table,
 *       element categories, and every unlocked formula.</li>
 * </ul>
 */
public class ChemistJournalItem extends Item {

    public ChemistJournalItem(Properties properties) {
        super(properties);
    }

    public static List<String> getDiscoveries(ItemStack stack) {
        List<String> list = stack.get(SCGDataComponents.JOURNAL_DISCOVERIES.get());
        return list == null ? List.of() : list;
    }

    /**
     * Checks if player has a journal in inventory, and records the item if not already known.
     */
    public static void recordDiscovery(Player player, ItemStack item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
        if (id == null) return;
        String idStr = id.toString();

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(SCGItems.CHEMIST_JOURNAL.get())) {
                List<String> current = new ArrayList<>(getDiscoveries(stack));
                if (!current.contains(idStr)) {
                    current.add(idStr);
                    stack.set(SCGDataComponents.JOURNAL_DISCOVERIES.get(), current);
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.2F);
                        serverPlayer.displayClientMessage(
                                Component.translatable("message.shadowclangaming.journal.recorded", item.getHoverName())
                                        .withStyle(ChatFormatting.GREEN), true);
                    }
                }
                return;
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            ItemStack book = createBookView(held);
            serverPlayer.openItemGui(book, hand);
            serverPlayer.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
    }

    /**
     * Generates a book view populated with the Periodic Table and all recorded chemical recipes.
     */
    private ItemStack createBookView(ItemStack journal) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<String> discoveries = getDiscoveries(journal);

        List<Component> pages = new ArrayList<>();

        // Page 1: Title & Overview
        MutableComponent p1 = Component.literal("§1§lCHEMIST'S JOURNAL§r\n\n")
                .append(Component.literal("§8Field Reference & Lab Notebook§r\n\n"))
                .append(Component.literal("Total Elements: §1118§r\n"))
                .append(Component.literal("Recipes Logged: §2" + discoveries.size() + "§r\n\n"))
                .append(Component.literal("§7Use a Microscope or Pocket Microscope on items to transcribe their atomic structures here.§r"));
        pages.add(p1);

        // Page 2: Periodic Table Key
        MutableComponent p2 = Component.literal("§1§lPERIODIC TABLE§r\n\n")
                .append(Component.literal("§9■ Nonmetals & Gases§r\n"))
                .append(Component.literal("§5■ Alkali & Alkaline§r\n"))
                .append(Component.literal("§8■ Transition Metals§r\n"))
                .append(Component.literal("§6■ Halogens§r\n"))
                .append(Component.literal("§2■ Actinides (Fissile)§r\n\n"))
                .append(Component.literal("§4Caution: §8Radioactive elements decay over time. Use lead shielding.§r"));
        pages.add(p2);

        // Pages for discovered recipes
        if (discoveries.isEmpty()) {
            pages.add(Component.literal("§8§oNo formulas recorded yet. Place specimens into a Microscope to log discoveries.§r"));
        } else {
            MutableComponent curPage = Component.literal("§1§lFORMULAS (1)§r\n\n");
            int entriesOnPage = 0;
            int pageNum = 1;

            for (String disc : discoveries) {
                ResourceLocation res = ResourceLocation.tryParse(disc);
                if (res == null) continue;
                Item item = BuiltInRegistries.ITEM.get(res);
                if (item == Items.AIR) continue;

                ItemStack sample = new ItemStack(item);
                List<DecompositionTable.Yield> yields = DecompositionTable.yieldsFor(sample);
                if (yields.isEmpty()) continue;

                if (entriesOnPage >= 3) {
                    pages.add(curPage);
                    pageNum++;
                    curPage = Component.literal("§1§lFORMULAS (" + pageNum + ")§r\n\n");
                    entriesOnPage = 0;
                }

                curPage.append(Component.literal("§0§l" + sample.getHoverName().getString() + "§r\n  §8-> "));
                for (int i = 0; i < yields.size(); i++) {
                    DecompositionTable.Yield y = yields.get(i);
                    String count = y.min() == y.max() ? String.valueOf(y.min()) : (y.min() + "-" + y.max());
                    curPage.append(Component.literal("§1" + count + "x " + y.element().symbol() + "§8"));
                    if (i < yields.size() - 1) curPage.append(Component.literal(", "));
                }
                curPage.append(Component.literal("\n\n"));
                entriesOnPage++;
            }
            if (entriesOnPage > 0) {
                pages.add(curPage);
            }
        }

        WrittenBookContent content = new WrittenBookContent(
                net.minecraft.server.network.Filterable.passThrough("Chemist's Journal"),
                "ShadowClan Chemistry Lab",
                0,
                pages.stream().map(net.minecraft.server.network.Filterable::passThrough).toList(),
                true
        );
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return book;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        List<String> list = getDiscoveries(stack);
        tooltip.add(Component.translatable("tooltip.shadowclangaming.journal.discoveries", list.size())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.journal.hint")
                .withStyle(ChatFormatting.GRAY));
    }
}
