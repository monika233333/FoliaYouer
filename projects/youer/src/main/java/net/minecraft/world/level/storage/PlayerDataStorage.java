package net.minecraft.world.level.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.slf4j.Logger;

public class PlayerDataStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final File playerDir;
    protected final DataFixer fixerUpper;
    private static final DateTimeFormatter FORMATTER = FileNameDateFormatter.create();

    public PlayerDataStorage(LevelStorageSource.LevelStorageAccess p_78430_, DataFixer p_78431_) {
        this.fixerUpper = p_78431_;
        this.playerDir = p_78430_.getLevelPath(LevelResource.PLAYER_DATA_DIR).toFile();
        this.playerDir.mkdirs();
    }

    public void save(Player p_78434_) {
        if (org.spigotmc.SpigotConfig.disablePlayerDataSaving) return; // Spigot
        try {
            CompoundTag compoundtag = p_78434_.saveWithoutId(new CompoundTag());
            Path path = this.playerDir.toPath();
            Path path1 = Files.createTempFile(path, p_78434_.getStringUUID() + "-", ".dat");
            NbtIo.writeCompressed(compoundtag, path1);
            Path path2 = path.resolve(p_78434_.getStringUUID() + ".dat");
            Path path3 = path.resolve(p_78434_.getStringUUID() + ".dat_old");
            Util.safeReplaceFile(path2, path1, path3);
            net.neoforged.neoforge.event.EventHooks.firePlayerSavingEvent(p_78434_, playerDir, p_78434_.getStringUUID());
        } catch (Exception exception) {
            LOGGER.warn("Failed to save player data for {}", p_78434_.getScoreboardName(),  exception); // Paper - Print exception
        }
    }

    private void backup(Player p_316529_, String p_316776_) {
        Path path = this.playerDir.toPath();
        Path path1 = path.resolve(p_316529_.getStringUUID() + p_316776_);
        Path path2 = path.resolve(p_316529_.getStringUUID() + "_corrupted_" + LocalDateTime.now().format(FORMATTER) + p_316776_);
        if (Files.isRegularFile(path1)) {
            try {
                Files.copy(path1, path2, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (Exception exception) {
                LOGGER.warn("Failed to copy the player.dat file for {}", p_316529_.getName().getString(), exception);
            }
        }
    }

    private void backup(String name, String s1, String s) { // name, uuid, extension
        Path path = this.playerDir.toPath();
        Path path1 = path.resolve(s1 + s);
        Path path2 = path.resolve(s1 + "_corrupted_" + LocalDateTime.now().format(FORMATTER) + s);
        if (Files.isRegularFile(path1)) {
            try {
                Files.copy(path1, path2, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (Exception exception) {
                LOGGER.warn("Failed to copy the player.dat file for {}", name, exception);
            }
        }
    }

    public Optional<CompoundTag> load(Player p_316534_, String p_316666_) {
        File file1 = new File(this.playerDir, p_316534_.getStringUUID() + p_316666_);
        if (file1.exists() && file1.isFile()) {
            try {
                return Optional.of(NbtIo.readCompressed(file1.toPath(), NbtAccounter.unlimitedHeap()));
            } catch (Exception exception) {
                LOGGER.warn("Failed to load player data for {}", p_316534_.getName().getString());
            }
        }

        return Optional.empty();
    }

    private Optional<CompoundTag> load(String name, String s1, String s) { // name, uuid, extension
        // CraftBukkit end
        File file = this.playerDir;
        // String s1 = entityhuman.getStringUUID(); // CraftBukkit - used above
        File file1 = new File(file, s1 + s);
        // Spigot Start
        boolean usingWrongFile = false;
        if ( org.bukkit.Bukkit.getOnlineMode() && !file1.exists() ) // Paper - Check online mode first
        {
            file1 = new File( file, java.util.UUID.nameUUIDFromBytes( ( "OfflinePlayer:" + name ).getBytes( java.nio.charset.StandardCharsets.UTF_8 ) ).toString() + s );
            if ( file1.exists() )
            {
                usingWrongFile = true;
                org.bukkit.Bukkit.getServer().getLogger().warning( "Using offline mode UUID file for player " + name + " as it is the only copy we can find." );
            }
        }
        // Spigot End

        if (file1.exists() && file1.isFile()) {
            try {
                // Spigot Start
                Optional<CompoundTag> optional = Optional.of(NbtIo.readCompressed(file1.toPath(), NbtAccounter.unlimitedHeap()));
                if ( usingWrongFile )
                {
                    file1.renameTo( new File( file1.getPath() + ".offline-read" ) );
                }
                return optional;
                // Spigot End
            } catch (Exception exception) {
                PlayerDataStorage.LOGGER.warn("Failed to load player data for {}", name); // CraftBukkit
            }
        }

        return Optional.empty();
    }

    public Optional<CompoundTag> load(Player p_78436_) {
        Optional<CompoundTag> optional = this.load(p_78436_, ".dat");
        if (optional.isEmpty()) {
            this.backup(p_78436_, ".dat");
        }

        return optional.or(() -> this.load(p_78436_.getName().getString(), p_78436_.getStringUUID())).map(p_316252_ -> {
            if (p_78436_ instanceof ServerPlayer) {
                CraftPlayer player1 = (CraftPlayer) p_78436_.getBukkitEntity();
                // Only update first played if it is older than the one we have
                long modified = new File(this.playerDir, p_78436_.getStringUUID() + ".dat").lastModified();
                if (modified < player1.getFirstPlayed()) {
                    player1.setFirstPlayed(modified);
                }
            }
            p_78436_.load(p_316252_);
            net.neoforged.neoforge.event.EventHooks.firePlayerLoadingEvent(p_78436_, playerDir, p_78436_.getStringUUID());
            return p_316252_;
        });
    }

    public Optional<CompoundTag> load(String p_78436_, String uuid) {
        Optional<CompoundTag> optional = this.load(p_78436_, uuid, ".dat");
        if (optional.isEmpty()) {
            this.backup(p_78436_, uuid, ".dat");
        }

        return optional.or(() -> this.load(p_78436_, uuid, ".dat_old")).map(p_316252_ -> {
            int i = NbtUtils.getDataVersion(p_316252_, -1);
            p_316252_ = DataFixTypes.PLAYER.updateToCurrentVersion(this.fixerUpper, p_316252_, i);
            // p_78436_.load(p_316252_);
            return p_316252_;
        });
    }

    public File getPlayerDir() {
        return playerDir;
    }
}
