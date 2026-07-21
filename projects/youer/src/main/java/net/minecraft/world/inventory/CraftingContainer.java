package net.minecraft.world.inventory;

import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;

public interface CraftingContainer extends Container, StackedContentsCompatible {
    int getWidth();

    int getHeight();

    List<ItemStack> getItems();

    // CraftBukkit start
    default RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> getCurrentRecipe() {
        return null;
    }

    default void setCurrentRecipe(RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> recipe) {
    }
    // CraftBukkit end

    default CraftingInput asCraftInput() {
        return this.asPositionedCraftInput().input();
    }

    default CraftingInput.Positioned asPositionedCraftInput() {
        return CraftingInput.ofPositioned(this.getWidth(), this.getHeight(), this.getItems());
    }
}
