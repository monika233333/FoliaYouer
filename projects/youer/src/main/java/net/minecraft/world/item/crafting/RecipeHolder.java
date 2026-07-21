package net.minecraft.world.item.crafting;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;

public record RecipeHolder<T extends Recipe<?>>(ResourceLocation id, T value) {
    // CraftBukkit start
    public final org.bukkit.inventory.Recipe toBukkitRecipe() {
        return this.value.toBukkitRecipe(CraftNamespacedKey.fromMinecraft(this.id));
    }
    // CraftBukkit end

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeHolder<?>> STREAM_CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC, RecipeHolder::id, Recipe.STREAM_CODEC, RecipeHolder::value, RecipeHolder::new
    );

    @Override
    public boolean equals(Object p_301091_) {
        if (this == p_301091_) {
            return true;
        } else {
            if (p_301091_ instanceof RecipeHolder<?> recipeholder && this.id.equals(recipeholder.id)) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {
        return this.id.toString();
    }
}
