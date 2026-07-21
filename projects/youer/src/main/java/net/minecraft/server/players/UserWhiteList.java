package net.minecraft.server.players;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.util.Objects;

public class UserWhiteList extends StoredUserList<GameProfile, UserWhiteListEntry> {
    public UserWhiteList(File p_11449_) {
        super(p_11449_);
    }

    @Override
    protected StoredUserEntry<GameProfile> createEntry(JsonObject p_11452_) {
        return new UserWhiteListEntry(p_11452_);
    }

    public boolean isWhiteListed(GameProfile p_11454_) {
        return this.contains(p_11454_);
    }

    @Override
    public String[] getUserList() {
        return this.getEntries().stream().map(StoredUserEntry::getUser).filter(Objects::nonNull).map(GameProfile::getName).toArray(String[]::new);
    }

    protected String getKeyForUser(GameProfile p_11458_) {
        return p_11458_.getId().toString();
    }

    // Paper start - Add whitelist events
    @Override
    public void add(UserWhiteListEntry entry) {
        if (!new io.papermc.paper.event.server.WhitelistStateUpdateEvent(com.destroystokyo.paper.profile.CraftPlayerProfile.asBukkitCopy(entry.getUser()), io.papermc.paper.event.server.WhitelistStateUpdateEvent.WhitelistStatus.ADDED).callEvent()) {
            return;
        }

        super.add(entry);
    }

    @Override
    public void remove(GameProfile profile) {
        if (!new io.papermc.paper.event.server.WhitelistStateUpdateEvent(com.destroystokyo.paper.profile.CraftPlayerProfile.asBukkitCopy(profile), io.papermc.paper.event.server.WhitelistStateUpdateEvent.WhitelistStatus.REMOVED).callEvent()) {
            return;
        }

        super.remove(profile);
    }
    // Paper end - Add whitelist events
}
