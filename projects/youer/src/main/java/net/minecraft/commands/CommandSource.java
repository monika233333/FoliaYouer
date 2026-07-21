package net.minecraft.commands;

import net.minecraft.network.chat.Component;

public interface CommandSource {
    CommandSource NULL = new CommandSource() {
        @Override
        public void sendSystemMessage(Component p_230799_) {
        }

        @Override
        public boolean acceptsSuccess() {
            return false;
        }

        @Override
        public boolean acceptsFailure() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        // CraftBukkit start
        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
            return io.papermc.paper.brigadier.NullCommandSender.INSTANCE; // Paper - expose a no-op CommandSender
        }
        // CraftBukkit end
    };

    void sendSystemMessage(Component p_230797_);

    boolean acceptsSuccess();

    boolean acceptsFailure();

    boolean shouldInformAdmins();

    default boolean alwaysAccepts() {
        return false;
    }

    default org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
        return NULL.getBukkitSender( wrapper); // Paper - expose a no-op CommandSender
    }
}
