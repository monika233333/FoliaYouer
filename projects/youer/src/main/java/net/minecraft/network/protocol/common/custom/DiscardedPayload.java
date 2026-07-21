package net.minecraft.network.protocol.common.custom;

import com.mohistmc.youer.bukkit.messaging.PluginsPayload;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public final class DiscardedPayload implements CustomPacketPayload, PluginsPayload {
    private final ResourceLocation id;

    public DiscardedPayload(ResourceLocation id) {
        this.id = id;
    }

    private ByteBuf data;

    public DiscardedPayload(ResourceLocation id, ByteBuf data) {
        this.id = id;
        this.data = data;
    }
    // CraftBukkit - store data

    public static <T extends FriendlyByteBuf> StreamCodec<T, CustomPacketPayload> codec(ResourceLocation p_320106_, int p_319929_) {
        return PluginsPayload.dcodec(p_320106_, p_319929_);
    }

    @Override
    public Type<DiscardedPayload> type() {
        return new Type<>(this.id);
    }

    public ResourceLocation id() {
        return id;
    }

    public ByteBuf data() {
        return data;
    }

    @Override
    public ByteBuf getData() {
        return data();
    }

    @Override
    public void setData(ByteBuf data) {
        this.data = data;
    }
}
