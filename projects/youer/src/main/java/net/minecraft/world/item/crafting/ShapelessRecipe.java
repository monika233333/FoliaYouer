package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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
import org.bukkit.craftbukkit.inventory.CraftShapelessRecipe;

public class ShapelessRecipe extends io.papermc.paper.inventory.recipe.RecipeBookExactChoiceRecipe<CraftingInput> implements CraftingRecipe {
    final String group;
    final CraftingBookCategory category;
    final ItemStack result;
    final NonNullList<Ingredient> ingredients;
    private final boolean isSimple;
    private final boolean isBukkit; // Pufferfish

    public ShapelessRecipe(String p_249640_, CraftingBookCategory p_249390_, ItemStack p_252071_, NonNullList<Ingredient> p_250689_) {
        this(p_249640_, p_249390_, p_252071_, p_250689_, false);
    }
    public ShapelessRecipe(String p_249640_, CraftingBookCategory p_249390_, ItemStack p_252071_, NonNullList<Ingredient> p_250689_, boolean isBukkit) {
        this.isBukkit = isBukkit; // Pufferfish end
        this.group = p_249640_;
        this.category = p_249390_;
        this.result = p_252071_;
        this.ingredients = p_250689_;
        this.isSimple = p_250689_.stream().allMatch(Ingredient::isSimple);
        this.checkExactIngredients(); // Paper - improve exact recipe choices
    }

    // CraftBukkit start
    @SuppressWarnings("unchecked")
    @Override
    public org.bukkit.inventory.ShapelessRecipe toBukkitRecipe(NamespacedKey id) {
        CraftItemStack result = CraftItemStack.asCraftMirror(this.result);
        CraftShapelessRecipe recipe = new CraftShapelessRecipe(id, result, this);
        recipe.setGroup(this.group);
        recipe.setCategory(CraftRecipe.getCategory(this.category()));

        for (Ingredient list : this.ingredients) {
            recipe.addIngredient(CraftRecipe.toBukkit(list));
        }
        return recipe;
    }
    // CraftBukkit end

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SHAPELESS_RECIPE;
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
    public ItemStack getResultItem(HolderLookup.Provider p_335606_) {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.ingredients;
    }

    public boolean matches(CraftingInput p_346123_, Level p_44263_) {
        if (p_346123_.ingredientCount() != this.ingredients.size()) {
            return false;
        } else if (!isSimple) {
            var nonEmptyItems = new java.util.ArrayList<ItemStack>(p_346123_.ingredientCount());
            for (var item : p_346123_.items())
                if (!item.isEmpty())
                    nonEmptyItems.add(item);
            return net.neoforged.neoforge.common.util.RecipeMatcher.findMatches(nonEmptyItems, this.ingredients) != null;
        } else {
            // Pufferfish start
            if (!this.isBukkit) {
                java.util.List<Ingredient> ingredients = com.google.common.collect.Lists.newArrayList(this.ingredients.toArray(new Ingredient[0]));

                inventory: for (int index = 0; index < p_346123_.size(); index++) {
                    ItemStack itemStack = p_346123_.getItem(index);

                    if (!itemStack.isEmpty()) {
                        for (int i = 0; i < ingredients.size(); i++) {
                            if (ingredients.get(i).test(itemStack)) {
                                ingredients.remove(i);
                                continue inventory;
                            }
                        }
                        return false;
                    }
                }

                return ingredients.isEmpty();
            }
            // Pufferfish end
            // Paper start - unwrap ternary & better exact choice recipes
            if (p_346123_.size() == 1 && this.ingredients.size() == 1) {
                return this.ingredients.getFirst().test(p_346123_.getItem(0));
            }
            p_346123_.stackedContents().initializeExtras(this, p_346123_); // setup stacked contents for this recipe
            final boolean canCraft = p_346123_.stackedContents().canCraft(this, null);
            p_346123_.stackedContents().resetExtras();
            return canCraft;
            // Paper end - unwrap ternary & better exact choice recipes
        }
    }

    public ItemStack assemble(CraftingInput p_345555_, HolderLookup.Provider p_335725_) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int p_44252_, int p_44253_) {
        return p_44252_ * p_44253_ >= this.ingredients.size();
    }

    public static class Serializer implements RecipeSerializer<ShapelessRecipe> {
        private static final MapCodec<ShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(
            p_340779_ -> p_340779_.group(
                        Codec.STRING.optionalFieldOf("group", "").forGetter(p_301127_ -> p_301127_.group),
                        CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(p_301133_ -> p_301133_.category),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(p_301142_ -> p_301142_.result),
                        Ingredient.CODEC_NONEMPTY
                            .listOf()
                            .fieldOf("ingredients")
                            .flatXmap(
                                p_301021_ -> {
                                    Ingredient[] aingredient = p_301021_.toArray(Ingredient[]::new); // Neo skip the empty check and immediately create the array.
                                    if (aingredient.length == 0) {
                                        return DataResult.error(() -> "No ingredients for shapeless recipe");
                                    } else {
                                        return aingredient.length > ShapedRecipePattern.maxHeight * ShapedRecipePattern.maxWidth
                                            ? DataResult.error(() -> "Too many ingredients for shapeless recipe. The maximum is: %s".formatted(ShapedRecipePattern.maxHeight * ShapedRecipePattern.maxWidth))
                                            : DataResult.success(NonNullList.of(Ingredient.EMPTY, aingredient));
                                    }
                                },
                                DataResult::success
                            )
                            .forGetter(p_300975_ -> p_300975_.ingredients)
                    )
                    .apply(p_340779_, ShapelessRecipe::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, ShapelessRecipe> STREAM_CODEC = StreamCodec.of(
            ShapelessRecipe.Serializer::toNetwork, ShapelessRecipe.Serializer::fromNetwork
        );

        @Override
        public MapCodec<ShapelessRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ShapelessRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static ShapelessRecipe fromNetwork(RegistryFriendlyByteBuf p_319905_) {
            String s = p_319905_.readUtf();
            CraftingBookCategory craftingbookcategory = p_319905_.readEnum(CraftingBookCategory.class);
            int i = p_319905_.readVarInt();
            NonNullList<Ingredient> nonnulllist = NonNullList.withSize(i, Ingredient.EMPTY);
            nonnulllist.replaceAll(p_319735_ -> Ingredient.CONTENTS_STREAM_CODEC.decode(p_319905_));
            ItemStack itemstack = ItemStack.STREAM_CODEC.decode(p_319905_);
            return new ShapelessRecipe(s, craftingbookcategory, itemstack, nonnulllist);
        }

        private static void toNetwork(RegistryFriendlyByteBuf p_320371_, ShapelessRecipe p_320323_) {
            p_320371_.writeUtf(p_320323_.group);
            p_320371_.writeEnum(p_320323_.category);
            p_320371_.writeVarInt(p_320323_.ingredients.size());

            for (Ingredient ingredient : p_320323_.ingredients) {
                Ingredient.CONTENTS_STREAM_CODEC.encode(p_320371_, ingredient);
            }

            ItemStack.STREAM_CODEC.encode(p_320371_, p_320323_.result);
        }
    }
}
