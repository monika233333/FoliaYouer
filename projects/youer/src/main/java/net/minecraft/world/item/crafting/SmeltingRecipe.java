package net.minecraft.world.item.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftFurnaceRecipe;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;

public class SmeltingRecipe extends AbstractCookingRecipe {
    public SmeltingRecipe(String p_250200_, CookingBookCategory p_251114_, Ingredient p_250340_, ItemStack p_250306_, float p_249577_, int p_250030_) {
        super(RecipeType.SMELTING, p_250200_, p_251114_, p_250340_, p_250306_, p_249577_, p_250030_);
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(Blocks.FURNACE);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SMELTING_RECIPE;
    }

    // CraftBukkit start
    @Override
    public org.bukkit.inventory.Recipe toBukkitRecipe(NamespacedKey id) {
        CraftItemStack result = CraftItemStack.asCraftMirror(this.result);

        CraftFurnaceRecipe recipe = new CraftFurnaceRecipe(id, result, CraftRecipe.toBukkit(this.ingredient), this.experience, this.cookingTime);
        recipe.setGroup(this.group);
        recipe.setCategory(CraftRecipe.getCategory(this.category()));

        return recipe;
    }
    // CraftBukkit end
}
