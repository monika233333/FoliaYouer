package net.minecraft.world.item.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftBlastingRecipe;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;

public class BlastingRecipe extends AbstractCookingRecipe {
    public BlastingRecipe(String p_251053_, CookingBookCategory p_249936_, Ingredient p_251550_, ItemStack p_251027_, float p_250843_, int p_249841_) {
        super(RecipeType.BLASTING, p_251053_, p_249936_, p_251550_, p_251027_, p_250843_, p_249841_);
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(Blocks.BLAST_FURNACE);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.BLASTING_RECIPE;
    }

    // CraftBukkit start
    @Override
    public org.bukkit.inventory.Recipe toBukkitRecipe(NamespacedKey id) {
        CraftItemStack result = CraftItemStack.asCraftMirror(this.result);

        CraftBlastingRecipe recipe = new CraftBlastingRecipe(id, result, CraftRecipe.toBukkit(this.ingredient), this.experience, this.cookingTime);
        recipe.setGroup(this.group);
        recipe.setCategory(CraftRecipe.getCategory(this.category()));

        return recipe;
    }
    // CraftBukkit end
}
