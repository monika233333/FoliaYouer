package net.minecraft.world.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.InventoryHolder;

public class ResultContainer implements Container, RecipeCraftingHolder {
    private final NonNullList<ItemStack> itemStacks = NonNullList.withSize(1, ItemStack.EMPTY);
    @Nullable
    private RecipeHolder<?> recipeUsed;

    // CraftBukkit start
    private int maxStack = MAX_STACK;

    public List<ItemStack> getContents() {
        return this.itemStacks;
    }

    public InventoryHolder getOwner() {
        return null; // Result slots don't get an owner
    }

    // Don't need a transaction; the InventoryCrafting keeps track of it for us
    public void onOpen(CraftHumanEntity who) {}
    public void onClose(CraftHumanEntity who) {}
    public List<HumanEntity> getViewers() {
        return new ArrayList<>();
    }

    @Override
    public int getMaxStackSize() {
        return Math.max(Container.super.getMaxStackSize(), maxStack);
    }

    @Override
    public void setMaxStackSize(int size) {
        maxStack = size;
    }

    @Override
    public Location getLocation() {
        return null;
    }
    // CraftBukkit end

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.itemStacks) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int p_40147_) {
        return this.itemStacks.get(0);
    }

    @Override
    public ItemStack removeItem(int p_40149_, int p_40150_) {
        return ContainerHelper.takeItem(this.itemStacks, 0);
    }

    @Override
    public ItemStack removeItemNoUpdate(int p_40160_) {
        return ContainerHelper.takeItem(this.itemStacks, 0);
    }


    // Youer start
    AtomicBoolean fakeSetItem = new AtomicBoolean(false);
    AtomicReference<ItemStack> setItem$itemStack = new AtomicReference<>(null);
    public void fakeSetItem() {
        fakeSetItem.set(true);
    }
    @Nullable
    public ItemStack setItem$itemStack() {
        return setItem$itemStack.getAndSet(null);
    }
    @Override
    public void setItem(int p_40152_, ItemStack p_40153_) {
        if (fakeSetItem.getAndSet(false)){
            setItem$itemStack.set(p_40153_);
            return;
        }
        // Youer end
        this.itemStacks.set(0, p_40153_);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player p_40155_) {
        return true;
    }

    @Override
    public void clearContent() {
        this.itemStacks.clear();
    }

    @Override
    public void setRecipeUsed(@Nullable RecipeHolder<?> p_301012_) {
        this.recipeUsed = p_301012_;
    }

    @Nullable
    @Override
    public RecipeHolder<?> getRecipeUsed() {
        return this.recipeUsed;
    }
}
