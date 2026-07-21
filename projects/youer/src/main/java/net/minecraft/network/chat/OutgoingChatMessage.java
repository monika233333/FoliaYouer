package net.minecraft.network.chat;

import net.minecraft.server.level.ServerPlayer;

public interface OutgoingChatMessage {
    Component content();

    void sendToPlayer(ServerPlayer p_250979_, boolean p_249307_, ChatType.Bound p_252281_);

    // Paper start
    default void sendToPlayer(ServerPlayer sender, boolean filterMaskEnabled, ChatType.Bound params, @javax.annotation.Nullable Component unsigned) {
        this.sendToPlayer(sender, filterMaskEnabled, params);
    }
    // Paper end

    static OutgoingChatMessage create(PlayerChatMessage p_249173_) {
        return (OutgoingChatMessage)(p_249173_.isSystem()
            ? new OutgoingChatMessage.Disguised(p_249173_.decoratedContent())
            : new OutgoingChatMessage.Player(p_249173_));
    }

    public static record Disguised(Component content) implements OutgoingChatMessage {
        @Override
        public void sendToPlayer(ServerPlayer p_249237_, boolean p_249574_, ChatType.Bound p_250880_) {
            // Paper start
            this.sendToPlayer(p_249237_, p_249574_, p_250880_, null);
        }

        public void sendToPlayer(ServerPlayer sender, boolean filterMaskEnabled, ChatType.Bound params, @javax.annotation.Nullable Component unsigned) {
            sender.connection.sendDisguisedChatMessage(unsigned != null ? unsigned : this.content, params);
            // Paper end
        }
    }

    public static record Player(PlayerChatMessage message) implements OutgoingChatMessage {
        @Override
        public Component content() {
            return this.message.decoratedContent();
        }

        @Override
        public void sendToPlayer(ServerPlayer p_249642_, boolean p_251123_, ChatType.Bound p_251482_) {
            this.sendToPlayer(p_249642_, p_251123_, p_251482_, null);
        }

        public void sendToPlayer(ServerPlayer sender, boolean filterMaskEnabled, ChatType.Bound params, @javax.annotation.Nullable Component unsigned) {
            // Paper end
            PlayerChatMessage playerChatMessage = this.message.filter(filterMaskEnabled);
            playerChatMessage = unsigned != null ? playerChatMessage.withUnsignedContent(unsigned) : playerChatMessage; // Paper
            if (!playerChatMessage.isFullyFiltered()) {
                sender.connection.sendPlayerChatMessage(playerChatMessage, params);
            }
        }
    }
}
