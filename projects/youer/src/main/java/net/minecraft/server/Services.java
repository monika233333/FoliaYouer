package net.minecraft.server;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.ServicesKeyType;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.io.File;
import javax.annotation.Nullable;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.util.SignatureValidator;

public record Services(
    MinecraftSessionService sessionService, ServicesKeySet servicesKeySet, GameProfileRepository profileRepository, GameProfileCache profileCache, @javax.annotation.Nullable io.papermc.paper.configuration.PaperConfigurations paperConfigurations // Paper - add paper configuration files
) {
    // Paper start - add paper configuration files
    public Services(MinecraftSessionService sessionService, ServicesKeySet servicesKeySet, GameProfileRepository profileRepository, GameProfileCache profileCache) {
        this(sessionService, servicesKeySet, profileRepository, profileCache, null);
    }

    @Override
    public io.papermc.paper.configuration.PaperConfigurations paperConfigurations() {
        return java.util.Objects.requireNonNull(this.paperConfigurations);
    }
    // Paper end - add paper configuration files
    public static final String USERID_CACHE_FILE = "usercache.json"; // Paper - private -> public
    public static File userCacheFile;
    public static joptsimple.OptionSet optionSet;

    public static void setUserCacheFile(File userCacheFile, joptsimple.OptionSet optionSet) {
        Services.userCacheFile = userCacheFile;
        Services.optionSet = optionSet;
    }
    public static Services create(YggdrasilAuthenticationService p_214345_, File p_214346_) throws Exception { // Paper - add optionset to load paper config files; add userCacheFile parameter
        MinecraftSessionService minecraftsessionservice = p_214345_.createMinecraftSessionService();
        GameProfileRepository gameprofilerepository = p_214345_.createProfileRepository();
        GameProfileCache gameprofilecache = new GameProfileCache(gameprofilerepository, userCacheFile); // Paper - use specified user cache file
        // Paper start - load paper config files from cli options
        final java.nio.file.Path legacyConfigPath = ((File) optionSet.valueOf("paper-settings")).toPath();
        final java.nio.file.Path configDirPath = ((File) optionSet.valueOf("paper-settings-directory")).toPath();
        io.papermc.paper.configuration.PaperConfigurations paperConfigurations = io.papermc.paper.configuration.PaperConfigurations.setup(legacyConfigPath, configDirPath, p_214346_.toPath(), (File) optionSet.valueOf("spigot-settings"));
        return new Services(minecraftsessionservice, p_214345_.getServicesKeySet(), gameprofilerepository, gameprofilecache, paperConfigurations);
        // Paper end - load paper config files from cli options
    }

    @Nullable
    public SignatureValidator profileKeySignatureValidator() {
        return SignatureValidator.from(this.servicesKeySet, ServicesKeyType.PROFILE_KEY);
    }

    public boolean canValidateProfileKeys() {
        return !this.servicesKeySet.keys(ServicesKeyType.PROFILE_KEY).isEmpty();
    }
}
