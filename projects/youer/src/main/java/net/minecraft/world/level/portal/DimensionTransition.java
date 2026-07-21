package net.minecraft.world.level.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// CraftBukkit start
public record DimensionTransition(
    ServerLevel newLevel,
    Vec3 pos,
    Vec3 speed,
    float yRot,
    float xRot,
    boolean missingRespawnBlock,
    DimensionTransition.PostDimensionTransition postDimensionTransition,
    org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause
) {
    public DimensionTransition(ServerLevel newLevel, Vec3 pos, Vec3 speed, float yRot, float xRot, boolean missingRespawnBlock, DimensionTransition.PostDimensionTransition postDimensionTransition) {
        this(newLevel, pos, speed, yRot, xRot, missingRespawnBlock, postDimensionTransition, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }
    // CraftBukkit end

    public static final DimensionTransition.PostDimensionTransition DO_NOTHING = p_352417_ -> {
    };
    public static final DimensionTransition.PostDimensionTransition PLAY_PORTAL_SOUND = DimensionTransition::playPortalSound;
    public static final DimensionTransition.PostDimensionTransition PLACE_PORTAL_TICKET = DimensionTransition::placePortalTicket;

    public DimensionTransition(ServerLevel worldserver, Vec3 vec3d, Vec3 vec3d1, float f, float f1, DimensionTransition.PostDimensionTransition dimensiontransition_a) {
        // CraftBukkit start
        this(worldserver, vec3d, vec3d1, f, f1, dimensiontransition_a, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public DimensionTransition(ServerLevel worldserver, Vec3 vec3d, Vec3 vec3d1, float f, float f1, DimensionTransition.PostDimensionTransition dimensiontransition_a, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        this(worldserver, vec3d, vec3d1, f, f1, false, dimensiontransition_a, cause);
    }

    public DimensionTransition(ServerLevel worldserver, Entity entity, DimensionTransition.PostDimensionTransition dimensiontransition_a) {
        this(worldserver, entity, dimensiontransition_a, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public DimensionTransition(ServerLevel worldserver, Entity entity, DimensionTransition.PostDimensionTransition dimensiontransition_a, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        this(worldserver, findAdjustedSharedSpawnPos(worldserver, entity), Vec3.ZERO, worldserver.getSharedSpawnAngle(), 0.0F, false, dimensiontransition_a, cause); // Paper - MC-200092 - fix spawn pos yaw being ignored
        // CraftBukkit end
    }
    private static void playPortalSound(Entity p_352075_) {
        if (p_352075_ instanceof ServerPlayer serverplayer) {
            serverplayer.connection.send(new ClientboundLevelEventPacket(1032, BlockPos.ZERO, 0, false));
        }
    }

    private static void placePortalTicket(Entity p_352447_) {
        p_352447_.placePortalTicket(BlockPos.containing(p_352447_.position()));
    }

    public static DimensionTransition missingRespawnBlock(ServerLevel p_348517_, Entity p_352420_, DimensionTransition.PostDimensionTransition p_352305_) {
        return new DimensionTransition(p_348517_, findAdjustedSharedSpawnPos(p_348517_, p_352420_), Vec3.ZERO, p_348517_.getSharedSpawnAngle(), 0.0F, true, p_352305_); // Paper - MC-200092 - fix spawn pos yaw being ignored
    }

    private static Vec3 findAdjustedSharedSpawnPos(ServerLevel p_352080_, Entity p_352400_) {
        return p_352400_.adjustSpawnLocation(p_352080_, p_352080_.getSharedSpawnPos()).getBottomCenter();
    }

    @FunctionalInterface
    public interface PostDimensionTransition {
        void onTransition(Entity p_352279_);

        default DimensionTransition.PostDimensionTransition then(DimensionTransition.PostDimensionTransition p_352277_) {
            return p_352242_ -> {
                this.onTransition(p_352242_);
                p_352277_.onTransition(p_352242_);
            };
        }
    }
}
