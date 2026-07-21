package net.minecraft.world.item.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftStonecuttingRecipe;

public class StonecutterRecipe extends SingleItemRecipe {
    public StonecutterRecipe(String p_44479_, Ingredient p_44480_, ItemStack p_302318_) {
        super(RecipeType.STONECUTTING, RecipeSerializer.STONECUTTER, p_44479_, p_44480_, p_302318_);
    }

    public boolean matches(SingleRecipeInput p_344927_, Level p_345392_) {
        return this.ingredient.test(p_344927_.item());
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(Blocks.STONECUTTER);
    }

    // CraftBukkit start
    @Override
    public org.bukkit.inventory.Recipe toBukkitRecipe(NamespacedKey id) {
        CraftItemStack result = CraftItemStack.asCraftMirror(this.result);

        CraftStonecuttingRecipe recipe = new CraftStonecuttingRecipe(id, result, CraftRecipe.toBukkit(this.ingredient));
        recipe.setGroup(this.group);

        return recipe;
    }
    // CraftBukkit end
}
