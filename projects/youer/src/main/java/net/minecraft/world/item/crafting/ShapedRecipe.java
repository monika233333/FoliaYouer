package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftShapedRecipe;
import org.bukkit.inventory.RecipeChoice;

public class ShapedRecipe extends io.papermc.paper.inventory.recipe.RecipeBookExactChoiceRecipe<CraftingInput> implements CraftingRecipe {
    public final ShapedRecipePattern pattern;
    final ItemStack result;
    final String group;
    final CraftingBookCategory category;
    final boolean showNotification;

    public ShapedRecipe(String p_272759_, CraftingBookCategory p_273506_, ShapedRecipePattern p_312827_, ItemStack p_272852_, boolean p_312010_) {
        this.group = p_272759_;
        this.category = p_273506_;
        this.pattern = p_312827_;
        this.result = p_272852_;
        this.showNotification = p_312010_;
        this.checkExactIngredients(); // Paper - improve exact recipe choices
    }

    public ShapedRecipe(String p_250221_, CraftingBookCategory p_250716_, ShapedRecipePattern p_312814_, ItemStack p_248581_) {
        this(p_250221_, p_250716_, p_312814_, p_248581_, true);
    }

    // CraftBukkit start
    @Override
    public org.bukkit.inventory.ShapedRecipe toBukkitRecipe(NamespacedKey id) {
        CraftItemStack result = CraftItemStack.asCraftMirror(this.result);
        CraftShapedRecipe recipe = new CraftShapedRecipe(id, result, this);
        recipe.setGroup(this.group);
        recipe.setCategory(CraftRecipe.getCategory(this.category()));

        if (this.pattern == null) {
            return recipe;
        }

        java.util.Optional<ShapedRecipePattern.Data> data = this.pattern.data();
        if (data.isPresent()) {
            recipe.shape(data.get().pattern().toArray(new String[0]));
            String[] shape = recipe.getShape();
            for (java.util.Map.Entry<Character, Ingredient> entry : data.get().key().entrySet()) {
                char c = entry.getKey();
                if (c == ' ') continue; // CraftBukkit - space is reserved

                RecipeChoice choice = CraftRecipe.toBukkit(entry.getValue());
                if (choice != RecipeChoice.empty()) {
                    // CraftBukkit start - check if the char is in the shape
                    boolean found = false;
                    for (String row : shape) {
                        if (row.indexOf(c) != -1) {
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        recipe.setIngredient(c, choice);
                    }
                    // CraftBukkit end
                }
            }
        } else {
            switch (this.pattern.height()) {
                case 1:
                    switch (this.pattern.width()) {
                        case 1:
                            recipe.shape("a");
                            break;
                        case 2:
                            recipe.shape("ab");
                            break;
                        case 3:
                            recipe.shape("abc");
                            break;
                    }
                    break;
                case 2:
                    switch (this.pattern.width()) {
                        case 1:
                            recipe.shape("a","b");
                            break;
                        case 2:
                            recipe.shape("ab","cd");
                            break;
                        case 3:
                            recipe.shape("abc","def");
                            break;
                    }
                    break;
                case 3:
                    switch (this.pattern.width()) {
                        case 1:
                            recipe.shape("a","b","c");
                            break;
                        case 2:
                            recipe.shape("ab","cd","ef");
                            break;
                        case 3:
                            recipe.shape("abc","def","ghi");
                            break;
                    }
                    break;
            }
            String[] shape = recipe.getShape(); // CraftBukkit
            char c = 'a';
            for (Ingredient list : this.pattern.ingredients()) {
                RecipeChoice choice = CraftRecipe.toBukkit(list);
                if (choice != RecipeChoice.empty()) { // Paper
                    // CraftBukkit start - check if the char is in the shape
                    boolean found = false;
                    for (String row : shape) {
                        if (row.indexOf(c) != -1) {
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        recipe.setIngredient(c, choice);
                    }
                    // CraftBukkit end
                }

                c++;
            }
        }
        return recipe;
    }
    // CraftBukkit end

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SHAPED_RECIPE;
    }

    @Override
    public String getGroup() {
        return this.group;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider p_335668_) {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.pattern.ingredients();
    }

    @Override
    public boolean showNotification() {
        return this.showNotification;
    }

    @Override
    public boolean canCraftInDimensions(int p_44161_, int p_44162_) {
        return p_44161_ >= this.pattern.width() && p_44162_ >= this.pattern.height();
    }

    public boolean matches(CraftingInput p_345040_, Level p_44167_) {
        return this.pattern.matches(p_345040_);
    }

    public ItemStack assemble(CraftingInput p_345201_, HolderLookup.Provider p_335688_) {
        return this.getResultItem(p_335688_).copy();
    }

    public int getWidth() {
        return this.pattern.width();
    }

    public int getHeight() {
        return this.pattern.height();
    }

    @Override
    public boolean isIncomplete() {
        NonNullList<Ingredient> nonnulllist = this.getIngredients();
        return nonnulllist.isEmpty() || nonnulllist.stream().filter(p_151277_ -> !p_151277_.isEmpty()).anyMatch(Ingredient::hasNoItems);
    }

    public static class Serializer implements RecipeSerializer<ShapedRecipe> {
        public static final MapCodec<ShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(
            p_340778_ -> p_340778_.group(
                        Codec.STRING.optionalFieldOf("group", "").forGetter(p_311729_ -> p_311729_.group),
                        CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(p_311732_ -> p_311732_.category),
                        ShapedRecipePattern.MAP_CODEC.forGetter(p_311733_ -> p_311733_.pattern),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(p_311730_ -> p_311730_.result),
                        Codec.BOOL.optionalFieldOf("show_notification", Boolean.valueOf(true)).forGetter(p_311731_ -> p_311731_.showNotification)
                    )
                    .apply(p_340778_, ShapedRecipe::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, ShapedRecipe> STREAM_CODEC = StreamCodec.of(
            ShapedRecipe.Serializer::toNetwork, ShapedRecipe.Serializer::fromNetwork
        );

        @Override
        public MapCodec<ShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ShapedRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static ShapedRecipe fromNetwork(RegistryFriendlyByteBuf p_319998_) {
            String s = p_319998_.readUtf();
            CraftingBookCategory craftingbookcategory = p_319998_.readEnum(CraftingBookCategory.class);
            ShapedRecipePattern shapedrecipepattern = ShapedRecipePattern.STREAM_CODEC.decode(p_319998_);
            ItemStack itemstack = ItemStack.STREAM_CODEC.decode(p_319998_);
            boolean flag = p_319998_.readBoolean();
            return new ShapedRecipe(s, craftingbookcategory, shapedrecipepattern, itemstack, flag);
        }

        private static void toNetwork(RegistryFriendlyByteBuf p_320738_, ShapedRecipe p_320586_) {
            p_320738_.writeUtf(p_320586_.group);
            p_320738_.writeEnum(p_320586_.category);
            ShapedRecipePattern.STREAM_CODEC.encode(p_320738_, p_320586_.pattern);
            ItemStack.STREAM_CODEC.encode(p_320738_, p_320586_.result);
            p_320738_.writeBoolean(p_320586_.showNotification);
        }
    }
}
