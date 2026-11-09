package com.shadowclangaming.menu;

import com.shadowclangaming.block.AbilityChamberBlockEntity;
import com.shadowclangaming.registry.SCGMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class AbilityChamberMenu extends AbstractContainerMenu {
    private final AbilityChamberBlockEntity be;
    public AbilityChamberMenu(int id, Inventory inv, AbilityChamberBlockEntity be){
        super(SCGMenus.ABILITY_CHAMBER.get(), id);
        this.be=be != null ? be : (be == null && inv.player.level().getBlockEntity(inv.player.blockPosition()) instanceof AbilityChamberBlockEntity c ? c : null);
    }
    public AbilityChamberBlockEntity blockEntity(){ return be; }
    @Override public ItemStack quickMoveStack(Player p, int i){ return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p){ return true; }
}
