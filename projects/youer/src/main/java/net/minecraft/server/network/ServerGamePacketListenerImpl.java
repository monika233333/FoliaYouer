package net.minecraft.server.network;

import com.google.common.collect.Lists;
import com.google.common.primitives.Floats;
import com.mohistmc.youer.YouerConfig;
import com.mohistmc.youer.bukkit.inventory.InventoryOwner;
import com.mohistmc.youer.feature.ban.bans.BanItem;
import com.mohistmc.youer.util.LambdaFix;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.logging.LogUtils;
import io.papermc.paper.adventure.ChatProcessor;
import io.papermc.paper.adventure.PaperAdventure;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.LastSeenMessagesValidator;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.chat.SignableCommand;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundDebugSampleSubscriptionPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType; // Folia - region threading
import net.minecraft.util.FutureChain;
import net.minecraft.util.Mth;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Unit; // Folia - region threading
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemType;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.craftbukkit.util.LazyPlayerSet;
import org.bukkit.craftbukkit.util.Waitable;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryView;
import org.slf4j.Logger;
import org.spigotmc.AsyncCatcher;

public class ServerGamePacketListenerImpl
    extends ServerCommonPacketListenerImpl
    implements ServerGamePacketListener,
    ServerPlayerConnection,
    TickablePacketListener {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_BLOCK_UPDATES_TO_ACK = -1;
    private static final int TRACKED_MESSAGE_DISCONNECT_THRESHOLD = 4096;
    private static final int MAXIMUM_FLYING_TICKS = 80;
    private static final Component CHAT_VALIDATION_FAILED = Component.translatable("multiplayer.disconnect.chat_validation_failed");
    private static final Component INVALID_COMMAND_SIGNATURE = Component.translatable("chat.disabled.invalid_command_signature").withStyle(ChatFormatting.RED);
    private static final int MAX_COMMAND_SUGGESTIONS = 1000;
    public ServerPlayer player;

    // Folia start - region threading - disconnect ticket management
    public static final TicketType<Long> DISCONNECT_TICKET = TicketType.create("disconnect", Long::compareTo);
    public BlockPos disconnectPos;
    public Long disconnectTicketId;
    // Folia end - region threading - disconnect ticket management

    public final PlayerChunkSender chunkSender;
    private int tickCount;
    private int ackBlockChangesUpTo = -1;
    // CraftBukkit start - multithreaded fields
    private final AtomicInteger chatSpamTickCount = new AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger tabSpamLimiter = new java.util.concurrent.atomic.AtomicInteger(); // Paper - configurable tab spam limits
    private final java.util.concurrent.atomic.AtomicInteger recipeSpamPackets =  new java.util.concurrent.atomic.AtomicInteger(); // Paper - auto recipe limit
    // CraftBukkit end
    private int dropSpamTickCount;
    private double firstGoodX;
    private double firstGoodY;
    private double firstGoodZ;
    private double lastGoodX;
    private double lastGoodY;
    private double lastGoodZ;
    @Nullable
    private Entity lastVehicle;
    private double vehicleFirstGoodX;
    private double vehicleFirstGoodY;
    private double vehicleFirstGoodZ;
    private double vehicleLastGoodX;
    private double vehicleLastGoodY;
    private double vehicleLastGoodZ;
    @Nullable
    private Vec3 awaitingPositionFromClient;
    private int awaitingTeleport;
    private int awaitingTeleportTime;
    private boolean clientIsFloating;
    private int aboveGroundTickCount;
    private boolean clientVehicleIsFloating;
    private int aboveGroundVehicleTickCount;
    private int receivedMovePacketCount;
    private int knownMovePacketCount;
    @Nullable
    private RemoteChatSession chatSession;
    private SignedMessageChain.Decoder signedMessageDecoder;
    private final LastSeenMessagesValidator lastSeenMessages = new LastSeenMessagesValidator(20);
    private final MessageSignatureCache messageSignatureCache = MessageSignatureCache.createDefault();
    public final FutureChain chatMessageChain;
    public boolean waitingForSwitchToConfig; // Folia - region threading - public
    // CraftBukkit start - add fields and methods
    private int lastTick = MinecraftServer.currentTick;
    private int allowedPlayerTicks = 1;
    private int lastDropTick = MinecraftServer.currentTick;
    private int lastBookTick  = MinecraftServer.currentTick;
    private int dropCount = 0;

    private boolean hasMoved = false;
    private double lastPosX = Double.MAX_VALUE;
    private double lastPosY = Double.MAX_VALUE;
    private double lastPosZ = Double.MAX_VALUE;
    private float lastPitch = Float.MAX_VALUE;
    private float lastYaw = Float.MAX_VALUE;
    private boolean justTeleported = false;
    // CraftBukkit end

    public ServerGamePacketListenerImpl(MinecraftServer p_9770_, Connection p_9771_, ServerPlayer p_9772_, CommonListenerCookie p_301978_) {
        super(p_9770_, p_9771_, p_301978_);
        this.chunkSender = new PlayerChunkSender(p_9771_.isMemoryConnection());
        this.player = p_9772_;
        p_9772_.connection = this;
        p_9772_.getTextFilter().join();
        this.signedMessageDecoder = SignedMessageChain.Decoder.unsigned(p_9772_.getUUID(), p_9770_::enforceSecureProfile);
        this.chatMessageChain = new FutureChain(server.chatExecutor); // CraftBukkit - async chat
        setPlayer(p_9772_);
    }

    // Purpur start
    private final com.google.common.cache.LoadingCache<CraftPlayer, Boolean> kickPermissionCache = com.google.common.cache.CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, java.util.concurrent.TimeUnit.MINUTES)
            .build(
                    new com.google.common.cache.CacheLoader<>() {
                        @Override
                        public Boolean load(CraftPlayer player) {
                            return player.hasPermission("purpur.bypassIdleKick");
                        }
                    }
            );
    // Purpur end

    @Override
    public void tick() {
        if (this.ackBlockChangesUpTo > -1) {
            this.send(new ClientboundBlockChangedAckPacket(this.ackBlockChangesUpTo));
            this.ackBlockChangesUpTo = -1;
        }

        this.resetPosition();
        this.player.xo = this.player.getX();
        this.player.yo = this.player.getY();
        this.player.zo = this.player.getZ();
        this.player.doTick();
        this.player.absMoveTo(this.firstGoodX, this.firstGoodY, this.firstGoodZ, this.player.getYRot(), this.player.getXRot());
        this.tickCount++;
        this.knownMovePacketCount = this.receivedMovePacketCount;
        if (this.clientIsFloating && !this.player.isSleeping() && !this.player.isPassenger() && !this.player.isDeadOrDying()) {
            if (++this.aboveGroundTickCount > this.getMaximumFlyingTicks(this.player)) {
                LOGGER.warn("{} was kicked for floating too long!", this.player.getName().getString());
                this.disconnect(io.papermc.paper.configuration.GlobalConfiguration.get().messages.kick.flyingPlayer, org.bukkit.event.player.PlayerKickEvent.Cause.FLYING_PLAYER); // Paper - use configurable kick message & kick event cause
                return;
            }
        } else {
            this.clientIsFloating = false;
            this.aboveGroundTickCount = 0;
        }

        this.lastVehicle = this.player.getRootVehicle();
        if (this.lastVehicle != this.player && this.lastVehicle.getControllingPassenger() == this.player) {
            this.vehicleFirstGoodX = this.lastVehicle.getX();
            this.vehicleFirstGoodY = this.lastVehicle.getY();
            this.vehicleFirstGoodZ = this.lastVehicle.getZ();
            this.vehicleLastGoodX = this.lastVehicle.getX();
            this.vehicleLastGoodY = this.lastVehicle.getY();
            this.vehicleLastGoodZ = this.lastVehicle.getZ();
            if (this.clientVehicleIsFloating && this.lastVehicle.getControllingPassenger() == this.player) {
                if (++this.aboveGroundVehicleTickCount > this.getMaximumFlyingTicks(this.lastVehicle)) {
                    LOGGER.warn("{} was kicked for floating a vehicle too long!", this.player.getName().getString());
                    this.disconnect(io.papermc.paper.configuration.GlobalConfiguration.get().messages.kick.flyingVehicle, org.bukkit.event.player.PlayerKickEvent.Cause.FLYING_VEHICLE); // Paper - use configurable kick message & kick event cause
                    return;
                }
            } else {
                this.clientVehicleIsFloating = false;
                this.aboveGroundVehicleTickCount = 0;
            }
        } else {
            this.lastVehicle = null;
            this.clientVehicleIsFloating = false;
            this.aboveGroundVehicleTickCount = 0;
        }

        this.keepConnectionAlive();
        // CraftBukkit start
        for (int spam; (spam = this.chatSpamTickCount.get()) > 0 && !this.chatSpamTickCount.compareAndSet(spam, spam - 1); ) ;
        if (tabSpamLimiter.get() > 0) tabSpamLimiter.getAndDecrement(); // Paper - configurable tab spam limits
        if (recipeSpamPackets.get() > 0) recipeSpamPackets.getAndDecrement(); // Paper - auto recipe limit
        /* Use thread-safe field access instead
        if (this.chatSpamTickCount > 0) {
            --this.chatSpamTickCount;
        }
        */
        // CraftBukkit end

        if (this.dropSpamTickCount > 0) {
            this.dropSpamTickCount--;
        }

        if (this.player.getLastActionTime() > 0L
            && this.server.getPlayerIdleTimeout() > 0
            && Util.getMillis() - this.player.getLastActionTime() > (long)this.server.getPlayerIdleTimeout() * 1000L * 60L && !this.player.wonGame) { // Paper - Prevent AFK kick while watching end credits
            // Purpur start
            this.player.setAfk(true);
            if (!this.player.level().purpurConfig.idleTimeoutKick || (!Boolean.parseBoolean(System.getenv("PURPUR_FORCE_IDLE_KICK")) && kickPermissionCache.getUnchecked(this.player.getBukkitEntity()))) {
                return;
            }
            // Purpur end
            this.player.resetLastActionTime(); // CraftBukkit - SPIGOT-854
            this.disconnect(Component.translatable("multiplayer.disconnect.idling"), org.bukkit.event.player.PlayerKickEvent.Cause.IDLING); // Paper - kick event cause
        }
    }

    private int getMaximumFlyingTicks(Entity p_326388_) {
        double d0 = p_326388_.getGravity();
        if (d0 < 1.0E-5F) {
            return Integer.MAX_VALUE;
        } else {
            double d1 = 0.08 / d0;
            return Mth.ceil(80.0 * Math.max(d1, 1.0));
        }
    }

    public void resetPosition() {
        this.firstGoodX = this.player.getX();
        this.firstGoodY = this.player.getY();
        this.firstGoodZ = this.player.getZ();
        this.lastGoodX = this.player.getX();
        this.lastGoodY = this.player.getY();
        this.lastGoodZ = this.player.getZ();
    }

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected() && !this.waitingForSwitchToConfig;
    }

    @Override
    public boolean shouldHandleMessage(Packet<?> p_295183_) {
        return super.shouldHandleMessage(p_295183_)
            ? true
            : this.waitingForSwitchToConfig && this.connection.isConnected() && p_295183_ instanceof ServerboundConfigurationAcknowledgedPacket;
    }

    @Override
    protected GameProfile playerProfile() {
        return this.player.getGameProfile();
    }

    private <T, R> CompletableFuture<R> filterTextPacket(T p_243240_, BiFunction<TextFilter, T, CompletableFuture<R>> p_243271_) {
        return p_243271_.apply(this.player.getTextFilter(), p_243240_).thenApply(p_264862_ -> {
            if (!this.isAcceptingMessages()) {
                LOGGER.debug("Ignoring packet due to disconnection");
                throw new CancellationException("disconnected");
            } else {
                return (R)p_264862_;
            }
        });
    }

    public CompletableFuture<FilteredText> filterTextPacket(String p_243213_) {
        return this.filterTextPacket(p_243213_, TextFilter::processStreamMessage);
    }

    private CompletableFuture<List<FilteredText>> filterTextPacket(List<String> p_243258_) {
        return this.filterTextPacket(p_243258_, TextFilter::processMessageBundle);
    }

    @Override
    public void handlePlayerInput(ServerboundPlayerInputPacket p_9893_) {
        PacketUtils.ensureRunningOnSameThread(p_9893_, this, this.player.serverLevel());
        this.player.setPlayerInput(p_9893_.getXxa(), p_9893_.getZza(), p_9893_.isJumping(), p_9893_.isShiftKeyDown());
    }

    private static boolean containsInvalidValues(double p_143664_, double p_143665_, double p_143666_, float p_143667_, float p_143668_) {
        return Double.isNaN(p_143664_) || Double.isNaN(p_143665_) || Double.isNaN(p_143666_) || !Floats.isFinite(p_143668_) || !Floats.isFinite(p_143667_);
    }

    private static double clampHorizontal(double p_143610_) {
        return Mth.clamp(p_143610_, -3.0E7, 3.0E7);
    }

    private static double clampVertical(double p_143654_) {
        return Mth.clamp(p_143654_, -2.0E7, 2.0E7);
    }

    double handleMoveVehicle$prevX;
    double handleMoveVehicle$prevY;
    double handleMoveVehicle$prevZ;
    float handleMoveVehicle$prevYaw;
    float handleMoveVehicle$prevPitch;

    @Override
    public void handleMoveVehicle(ServerboundMoveVehiclePacket p_9876_) {
        PacketUtils.ensureRunningOnSameThread(p_9876_, this, this.player.serverLevel());
        if (containsInvalidValues(p_9876_.getX(), p_9876_.getY(), p_9876_.getZ(), p_9876_.getYRot(), p_9876_.getXRot())) {
            this.disconnect(Component.translatable("multiplayer.disconnect.invalid_vehicle_movement"), org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_VEHICLE_MOVEMENT); // Paper - kick event cause
        } else if (!this.updateAwaitingTeleport()) {
            Entity entity = this.player.getRootVehicle();
            // Paper start - Don't allow vehicle movement from players while teleporting
            if (this.awaitingPositionFromClient != null || this.player.isImmobile() || entity.isRemoved()) {
                return;
            }
            // Paper end - Don't allow vehicle movement from players while teleporting
            if (entity != this.player && entity.getControllingPassenger() == this.player && entity == this.lastVehicle) {
                ServerLevel serverlevel = this.player.serverLevel();
                // CraftBukkit - store current player position
                handleMoveVehicle$prevX = this.player.getX();
                handleMoveVehicle$prevY = this.player.getY();
                handleMoveVehicle$prevZ = this.player.getZ();
                handleMoveVehicle$prevYaw = this.player.getYRot();
                handleMoveVehicle$prevPitch = this.player.getXRot();
                // CraftBukkit end
                double d0 = entity.getX();
                double d1 = entity.getY();
                double d2 = entity.getZ();
                double d3 = clampHorizontal(p_9876_.getX());
                double d4 = clampVertical(p_9876_.getY());
                double d5 = clampHorizontal(p_9876_.getZ());
                float f = Mth.wrapDegrees(p_9876_.getYRot());
                float f1 = Mth.wrapDegrees(p_9876_.getXRot());
                double d6 = d3 - this.vehicleFirstGoodX;
                double d7 = d4 - this.vehicleFirstGoodY;
                double d8 = d5 - this.vehicleFirstGoodZ;
                double d9 = entity.getDeltaMovement().lengthSqr();
                double d10 = d6 * d6 + d7 * d7 + d8 * d8;

                // CraftBukkit start - handle custom speeds and skipped ticks
                this.allowedPlayerTicks += (System.currentTimeMillis() / 50) - this.lastTick;
                this.allowedPlayerTicks = Math.max(this.allowedPlayerTicks, 1);
                this.lastTick = (int) (System.currentTimeMillis() / 50);
                ++this.receivedMovePacketCount;
                int i = this.receivedMovePacketCount - this.knownMovePacketCount;
                if (i > Math.max(this.allowedPlayerTicks, 5)) {
                    LOGGER.debug(this.player.getScoreboardName() + " is sending move packets too frequently (" + i + " packets since last tick)");
                    i = 1;
                }
                if (d10 > 0) {
                    allowedPlayerTicks -= 1;
                } else {
                    allowedPlayerTicks = 20;
                }
                double speed;
                if (player.getAbilities().flying) {
                    speed = player.getAbilities().flyingSpeed * 20f;
                } else {
                    speed = player.getAbilities().walkingSpeed * 10f;
                }
                speed *= 2f; // TODO: Get the speed of the vehicle instead of the player
                if (d10 - d9 > Math.max(100.0D, Math.pow((double) (org.spigotmc.SpigotConfig.movedTooQuicklyMultiplier * (float) i * speed), 2)) && !this.isSingleplayerOwner()) {
                    // CraftBukkit end
                    LOGGER.warn("{} (vehicle of {}) moved too quickly! {},{},{}", entity.getName().getString(), this.player.getName().getString(), d6, d7, d8);
                    this.send(new ClientboundMoveVehiclePacket(entity));
                    return;
                }

                boolean flag = serverlevel.noCollision(entity, entity.getBoundingBox().deflate(0.0625));
                d6 = d3 - this.vehicleLastGoodX;
                d7 = d4 - this.vehicleLastGoodY - 1.0E-6;
                d8 = d5 - this.vehicleLastGoodZ;
                boolean flag1 = entity.verticalCollisionBelow;
                if (entity instanceof LivingEntity livingentity && livingentity.onClimbable()) {
                    livingentity.resetFallDistance();
                }

                entity.move(MoverType.PLAYER, new Vec3(d6, d7, d8));
                double d11 = d7;
                d6 = d3 - entity.getX();
                d7 = d4 - entity.getY();
                if (d7 > -0.5 || d7 < 0.5) {
                    d7 = 0.0;
                }

                d8 = d5 - entity.getZ();
                d10 = d6 * d6 + d7 * d7 + d8 * d8;
                boolean flag2 = false;
                if (d10 > org.spigotmc.SpigotConfig.movedWronglyThreshold) { // Spigot
                    flag2 = true;
                    LOGGER.warn("{} (vehicle of {}) moved wrongly! {}", entity.getName().getString(), this.player.getName().getString(), Math.sqrt(d10));
                }

                entity.absMoveTo(d3, d4, d5, f, f1);
                resyncPlayerWithVehicle(entity); // Neo - Resync player position on vehicle moving
                boolean flag3 = serverlevel.noCollision(entity, entity.getBoundingBox().deflate(0.0625));
                if (flag && (flag2 || !flag3)) {
                    entity.absMoveTo(d0, d1, d2, f, f1);
                    resyncPlayerWithVehicle(entity); // Neo - Resync player position on vehicle moving
                    this.send(new ClientboundMoveVehiclePacket(entity));
                    return;
                }

                // CraftBukkit start - fire PlayerMoveEvent
                org.bukkit.entity.Player player = this.getCraftPlayer();
                if (!this.hasMoved) {
                    this.lastPosX = handleMoveVehicle$prevX;
                    this.lastPosY = handleMoveVehicle$prevY;
                    this.lastPosZ = handleMoveVehicle$prevZ;
                    this.lastYaw = handleMoveVehicle$prevYaw;
                    this.lastPitch = handleMoveVehicle$prevPitch;
                    this.hasMoved = true;
                }
                org.bukkit.Location from = new org.bukkit.Location(player.getWorld(), lastPosX, lastPosY, lastPosZ, lastYaw, lastPitch); // Get the Players previous Event location.
                org.bukkit.Location to = player.getLocation().clone(); // Start off the To location as the Players current location.
                // If the packet contains movement information then we update the To location with the correct XYZ.
                to.setX(p_9876_.getX());
                to.setY(p_9876_.getY());
                to.setZ(p_9876_.getZ());
                // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
                to.setYaw(p_9876_.getYRot());
                to.setPitch(p_9876_.getXRot());
                // Prevent 40 event-calls for less than a single pixel of movement >.>
                double delta = Math.pow(this.lastPosX - to.getX(), 2) + Math.pow(this.lastPosY - to.getY(), 2) + Math.pow(this.lastPosZ - to.getZ(), 2);
                float deltaAngle = Math.abs(this.lastYaw - to.getYaw()) + Math.abs(this.lastPitch - to.getPitch());
                if ((delta > 1f / 256 || deltaAngle > 10f) && !this.player.isImmobile()) {
                    this.lastPosX = to.getX();
                    this.lastPosY = to.getY();
                    this.lastPosZ = to.getZ();
                    this.lastYaw = to.getYaw();
                    this.lastPitch = to.getPitch();

                    if (!to.getWorld().getUID().equals(from.getWorld().getUID()) || to.getBlockX() != from.getBlockX() || to.getBlockY() != from.getBlockY() || to.getBlockZ() != from.getBlockZ() || to.getYaw() != from.getYaw() || to.getPitch() != from.getPitch()) this.player.resetLastActionTime(); // Purpur

                    org.bukkit.Location oldTo = to.clone();
                    org.bukkit.event.player.PlayerMoveEvent event = new org.bukkit.event.player.PlayerMoveEvent(player, from, to);
                    this.cserver.getPluginManager().callEvent(event);
                    // If the event is cancelled we move the player back to their old location.
                    if (event.isCancelled()) {
                        teleport(from);
                        return;
                    }
                    // If a Plugin has changed the To destination then we teleport the Player
                    // there to avoid any 'Moved wrongly' or 'Moved too quickly' errors.
                    // We only do this if the Event was not cancelled.
                    if (!oldTo.equals(event.getTo()) && !event.isCancelled()) {
                        this.player.getBukkitEntity().teleport(event.getTo(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                        return;
                    }
                    // Check to see if the Players Location has some how changed during the call of the event.
                    // This can happen due to a plugin teleporting the player instead of using .setTo()
                    if (!from.equals(this.getCraftPlayer().getLocation()) && this.justTeleported) {
                        this.justTeleported = false;
                        return;
                    }
                }
                // CraftBukkit end

                this.player.serverLevel().getChunkSource().move(this.player);
                Vec3 vec3 = new Vec3(entity.getX() - d0, entity.getY() - d1, entity.getZ() - d2);
                this.player.setKnownMovement(vec3);
                this.player.checkMovementStatistics(vec3.x, vec3.y, vec3.z);
                this.player.checkRidingStatistics(vec3.x, vec3.y, vec3.z); // Neo: check riding stats too as vanilla checks them in rideTick based on the assumption that Entity#rideTick will move the entity, which we break
                this.clientVehicleIsFloating = d11 >= -0.03125
                    && !flag1
                    && !this.server.isFlightAllowed()
                    && !entity.isNoGravity()
                    && this.noBlocksAround(entity);
                this.vehicleLastGoodX = entity.getX();
                this.vehicleLastGoodY = entity.getY();
                this.vehicleLastGoodZ = entity.getZ();
            }
        }
    }

    private void resyncPlayerWithVehicle(Entity vehicle) {
        Vec3 oldPos = this.player.position();
        float yRot = this.player.getYRot();
        float xRot = this.player.getXRot();
        float yHeadRot = this.player.getYHeadRot();

        vehicle.positionRider(this.player);

        // preserve old rotation and store old position in xo/yo/zo
        this.player.setYRot(yRot);
        this.player.setXRot(xRot);
        this.player.setYHeadRot(yHeadRot);
        this.player.xo = oldPos.x;
        this.player.yo = oldPos.y;
        this.player.zo = oldPos.z;
    }

    private boolean noBlocksAround(Entity p_9794_) {
        return p_9794_.level()
            .getBlockStates(p_9794_.getBoundingBox().inflate(0.0625).expandTowards(0.0, -0.55, 0.0))
            .allMatch(BlockBehaviour.BlockStateBase::isAir);
    }

    @Override
    public void handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket p_9835_) {
        PacketUtils.ensureRunningOnSameThread(p_9835_, this, this.player.serverLevel());
        if (p_9835_.getId() == this.awaitingTeleport) {
            if (this.awaitingPositionFromClient == null) {
                this.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_movement"), org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PLAYER_MOVEMENT); // Paper - kick event cause
                return;
            }

            this.player
                .moveTo(
                    this.awaitingPositionFromClient.x,
                    this.awaitingPositionFromClient.y,
                    this.awaitingPositionFromClient.z,
                    this.player.getYRot(),
                    this.player.getXRot()
                ); // Paper - Fix Entity Teleportation and cancel velocity if teleported
            this.lastGoodX = this.awaitingPositionFromClient.x;
            this.lastGoodY = this.awaitingPositionFromClient.y;
            this.lastGoodZ = this.awaitingPositionFromClient.z;
            if (this.player.isChangingDimension()) {
                this.player.hasChangedDimension();
            }

            this.awaitingPositionFromClient = null;
            this.player.serverLevel().getChunkSource().move(this.player); // CraftBukkit
        }
    }

    @Override
    public void handleRecipeBookSeenRecipePacket(ServerboundRecipeBookSeenRecipePacket p_9897_) {
        PacketUtils.ensureRunningOnSameThread(p_9897_, this, this.player.serverLevel());
        this.server.getRecipeManager().byKey(p_9897_.getRecipe()).ifPresent(this.player.getRecipeBook()::removeHighlight);
    }

    @Override
    public void handleRecipeBookChangeSettingsPacket(ServerboundRecipeBookChangeSettingsPacket p_9895_) {
        PacketUtils.ensureRunningOnSameThread(p_9895_, this, this.player.serverLevel());
        CraftEventFactory.callRecipeBookSettingsEvent(this.player, p_9895_.getBookType(), p_9895_.isOpen(), p_9895_.isFiltering()); // CraftBukkit
        this.player.getRecipeBook().setBookSetting(p_9895_.getBookType(), p_9895_.isOpen(), p_9895_.isFiltering());
    }

    @Override
    public void handleSeenAdvancements(ServerboundSeenAdvancementsPacket p_9903_) {
        PacketUtils.ensureRunningOnSameThread(p_9903_, this, this.player.serverLevel());
        if (p_9903_.getAction() == ServerboundSeenAdvancementsPacket.Action.OPENED_TAB) {
            ResourceLocation resourcelocation = Objects.requireNonNull(p_9903_.getTab());
            AdvancementHolder advancementholder = this.server.getAdvancements().get(resourcelocation);
            if (advancementholder != null) {
                this.player.getAdvancements().setSelectedTab(advancementholder);
            }
        }
    }

    // Paper start - AsyncTabCompleteEvent
    private static final java.util.concurrent.ExecutorService TAB_COMPLETE_EXECUTOR = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    // Paper end - AsyncTabCompleteEvent
    @Override
    public void handleCustomCommandSuggestions(ServerboundCommandSuggestionPacket p_9847_) {
        // PacketUtils.ensureRunningOnSameThread(p_9847_, this, this.player.serverLevel());
        // CraftBukkit start
        if (this.chatSpamTickCount.addAndGet(io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.tabSpamIncrement) > io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.tabSpamLimit && !this.server.getPlayerList().isOp(this.player.getGameProfile())) { // Paper - configurable tab spam limits
            this.disconnectAsync(Component.translatable("disconnect.spam"), org.bukkit.event.player.PlayerKickEvent.Cause.SPAM); // Paper - Kick event cause // Paper - add proper async disconnect
            return;
        }
        // CraftBukkit end
        // Paper start - Don't suggest if tab-complete is disabled
        if (org.spigotmc.SpigotConfig.tabComplete < 0) {
            return;
        }
        // Paper end - Don't suggest if tab-complete is disabled
        // Paper start
        final int index;
        if (p_9847_.getCommand().length() > 64 && ((index = p_9847_.getCommand().indexOf(' ')) == -1 || index >= 64)) {
            this.disconnectAsync(Component.translatable("disconnect.spam"), org.bukkit.event.player.PlayerKickEvent.Cause.SPAM); // Paper - add proper async disconnect
            return;
        }
        // Paper end
        // Paper start - AsyncTabCompleteEvent
        TAB_COMPLETE_EXECUTOR.execute(() -> this.handleCustomCommandSuggestions0(p_9847_));
    }

    private void handleCustomCommandSuggestions0(final ServerboundCommandSuggestionPacket packet) {
        // CraftBukkit end
        StringReader stringreader = new StringReader(packet.getCommand());
        if (stringreader.canRead() && stringreader.peek() == '/') {
            stringreader.skip();
        }

        final com.destroystokyo.paper.event.server.AsyncTabCompleteEvent event = new com.destroystokyo.paper.event.server.AsyncTabCompleteEvent(this.getCraftPlayer(), packet.getCommand(), true, null);
        event.callEvent();
        final List<com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion> completions = event.isCancelled() ? com.google.common.collect.ImmutableList.of() : event.completions();
        // If the event isn't handled, we can assume that we have no completions, and so we'll ask the server
        if (!event.isHandled()) {
            if (event.isCancelled()) {
                return;
            }

            // This needs to be on main
            LambdaFix.lambda$handleCustomCommandSuggestions0$2(this, packet, stringreader);
        } else if (!completions.isEmpty()) {
            final com.mojang.brigadier.suggestion.SuggestionsBuilder builder0 = new com.mojang.brigadier.suggestion.SuggestionsBuilder(packet.getCommand(), stringreader.getTotalLength());
            final com.mojang.brigadier.suggestion.SuggestionsBuilder builder = builder0.createOffset(builder0.getInput().lastIndexOf(' ') + 1);
            for (final com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion completion : completions) {
                final Integer intSuggestion = com.google.common.primitives.Ints.tryParse(completion.suggestion());
                if (intSuggestion != null) {
                    builder.suggest(intSuggestion, PaperAdventure.asVanilla(completion.tooltip()));
                } else {
                    builder.suggest(completion.suggestion(), PaperAdventure.asVanilla(completion.tooltip()));
                }
            }
            // Paper start - Brigadier API
            com.mojang.brigadier.suggestion.Suggestions suggestions = builder.buildFuture().join();
            com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent suggestEvent = new com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent(this.getCraftPlayer(), suggestions, packet.getCommand());
            suggestEvent.setCancelled(suggestions.isEmpty());
            if (suggestEvent.callEvent()) {
                this.connection.send(new ClientboundCommandSuggestionsPacket(packet.getId(), limitTo(suggestEvent.getSuggestions(), ServerGamePacketListenerImpl.MAX_COMMAND_SUGGESTIONS)));
            }
            // Paper end - Brigadier API
        }
    }
    // Paper start - brig API
    private static Suggestions limitTo(final Suggestions suggestions, final int size) {
        return suggestions.getList().size() <= size ? suggestions : new Suggestions(suggestions.getRange(), suggestions.getList().subList(0, size));
    }
    // Paper end - brig API

    public void sendServerSuggestions(final ServerboundCommandSuggestionPacket packet, final StringReader stringreader) {
        // Paper end - AsyncTabCompleteEvent
        ParseResults<CommandSourceStack> parseresults = this.server.getCommands().getDispatcher().parse(stringreader, this.player.createCommandSourceStack());
        // Paper start - Handle non-recoverable exceptions
        if (!parseresults.getExceptions().isEmpty()
                && parseresults.getExceptions().values().stream().anyMatch(new Predicate<CommandSyntaxException>() {
            @Override
            public boolean test(CommandSyntaxException e) {
                return e instanceof io.papermc.paper.brigadier.TagParseCommandSyntaxException;
            }
        })) {
            this.disconnect(Component.translatable("disconnect.spam"), org.bukkit.event.player.PlayerKickEvent.Cause.SPAM);
            return;
        }
        // Paper end - Handle non-recoverable exceptions
        this.server
            .getCommands()
            .getDispatcher()
            .getCompletionSuggestions(parseresults)
            .thenAccept(
                    new Consumer<Suggestions>() {
                        @Override
                        public void accept(Suggestions suggestions) {
                            // Paper start - Don't tab-complete namespaced commands if send-namespaced is false
                            if (!org.spigotmc.SpigotConfig.sendNamespaced && suggestions.getRange().getStart() <= 1) {
                                suggestions.getList().removeIf(suggestion -> suggestion.getText().contains(":"));
                            }
                            // Paper end - Don't tab-complete namespaced commands if send-namespaced is false
                            // Paper start - Brigadier API
                            com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent suggestEvent = new com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent(ServerGamePacketListenerImpl.this.getCraftPlayer(), suggestions, packet.getCommand());
                            suggestEvent.setCancelled(suggestions.isEmpty());
                            if (suggestEvent.callEvent()) {
                                ServerGamePacketListenerImpl.this.send(new ClientboundCommandSuggestionsPacket(packet.getId(), limitTo(suggestEvent.getSuggestions(), ServerGamePacketListenerImpl.MAX_COMMAND_SUGGESTIONS)));
                            }
                            // Paper end - Brigadier API
                        }
                    }
            );
    }

    @Override
    public void handleSetCommandBlock(ServerboundSetCommandBlockPacket p_9911_) {
        PacketUtils.ensureRunningOnSameThread(p_9911_, this, this.player.serverLevel());
        if (!this.server.isCommandBlockEnabled()) {
            this.player.sendSystemMessage(Component.translatable("advMode.notEnabled"));
        } else if (!this.player.canUseGameMasterBlocks() && (!this.player.isCreative() || !this.player.getBukkitEntity().hasPermission("minecraft.commandblock"))) { // Paper - command block permission
            this.player.sendSystemMessage(Component.translatable("advMode.notAllowed"));
        } else {
            BaseCommandBlock basecommandblock = null;
            CommandBlockEntity commandblockentity = null;
            BlockPos blockpos = p_9911_.getPos();
            BlockEntity blockentity = this.player.level().getBlockEntity(blockpos);
            if (blockentity instanceof CommandBlockEntity) {
                commandblockentity = (CommandBlockEntity)blockentity;
                basecommandblock = commandblockentity.getCommandBlock();
            }

            String s = p_9911_.getCommand();
            boolean flag = p_9911_.isTrackOutput();
            if (basecommandblock != null) {
                CommandBlockEntity.Mode commandblockentity$mode = commandblockentity.getMode();
                BlockState blockstate = this.player.level().getBlockState(blockpos);
                Direction direction = blockstate.getValue(CommandBlock.FACING);

                BlockState blockstate1 = switch (p_9911_.getMode()) {
                    case SEQUENCE -> Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState();
                    case AUTO -> Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState();
                    default -> Blocks.COMMAND_BLOCK.defaultBlockState();
                };
                BlockState blockstate2 = blockstate1.setValue(CommandBlock.FACING, direction)
                    .setValue(CommandBlock.CONDITIONAL, Boolean.valueOf(p_9911_.isConditional()));
                if (blockstate2 != blockstate) {
                    this.player.level().setBlock(blockpos, blockstate2, 2);
                    blockentity.setBlockState(blockstate2);
                    this.player.level().getChunkAt(blockpos).setBlockEntity(blockentity);
                }

                basecommandblock.setCommand(s);
                basecommandblock.setTrackOutput(flag);
                if (!flag) {
                    basecommandblock.setLastOutput(null);
                }

                commandblockentity.setAutomatic(p_9911_.isAutomatic());
                if (commandblockentity$mode != p_9911_.getMode()) {
                    commandblockentity.onModeSwitch();
                }

                basecommandblock.onUpdated();
                if (!StringUtil.isNullOrEmpty(s)) {
                    this.player.sendSystemMessage(Component.translatable("advMode.setCommand.success", s));
                }
            }
        }
    }

    @Override
    public void handleSetCommandMinecart(ServerboundSetCommandMinecartPacket p_9913_) {
        PacketUtils.ensureRunningOnSameThread(p_9913_, this, this.player.serverLevel());
        if (!this.server.isCommandBlockEnabled()) {
            this.player.sendSystemMessage(Component.translatable("advMode.notEnabled"));
        } else if (!this.player.canUseGameMasterBlocks() && (!this.player.isCreative() || !this.player.getBukkitEntity().hasPermission("minecraft.commandblock"))) { // Paper - command block permission
            this.player.sendSystemMessage(Component.translatable("advMode.notAllowed"));
        } else {
            BaseCommandBlock basecommandblock = p_9913_.getCommandBlock(this.player.level());
            if (basecommandblock != null) {
                basecommandblock.setCommand(p_9913_.getCommand());
                basecommandblock.setTrackOutput(p_9913_.isTrackOutput());
                if (!p_9913_.isTrackOutput()) {
                    basecommandblock.setLastOutput(null);
                }

                basecommandblock.onUpdated();
                this.player.sendSystemMessage(Component.translatable("advMode.setCommand.success", p_9913_.getCommand()));
            }
        }
    }

    @Override
    public void handlePickItem(ServerboundPickItemPacket p_9880_) {
        PacketUtils.ensureRunningOnSameThread(p_9880_, this, this.player.serverLevel());
        this.player.getInventory().pickSlot(p_9880_.getSlot());
        // Paper start - validate pick item position
        if (!(p_9880_.getSlot() >= 0 && p_9880_.getSlot() < this.player.getInventory().items.size())) {
            ServerGamePacketListenerImpl.LOGGER.warn("{} tried to set an invalid carried item", this.player.getName().getString());
            this.disconnect(Component.literal("Invalid hotbar selection (Hacking?)"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // Paper - kick event cause
            return;
        }
        // Paper end - validate pick item position
        // Paper start - Add PlayerPickItemEvent
        org.bukkit.entity.Player bukkitPlayer = this.player.getBukkitEntity();
        int targetSlot = this.player.getInventory().getSuitableHotbarSlot();
        int sourceSlot = p_9880_.getSlot();

        io.papermc.paper.event.player.PlayerPickItemEvent event = new io.papermc.paper.event.player.PlayerPickItemEvent(bukkitPlayer, targetSlot, sourceSlot);
        if (!event.callEvent()) return;

        this.player.getInventory().pickSlot(event.getSourceSlot(), event.getTargetSlot());
        // Paper end - Add PlayerPickItemEvent
        this.player
            .connection
            .send(
                new ClientboundContainerSetSlotPacket(
                    -2, 0, this.player.getInventory().selected, this.player.getInventory().getItem(this.player.getInventory().selected)
                )
            );
        this.player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, p_9880_.getSlot(), this.player.getInventory().getItem(p_9880_.getSlot())));
        this.player.connection.send(new ClientboundSetCarriedItemPacket(this.player.getInventory().selected));
    }

    @Override
    public void handleRenameItem(ServerboundRenameItemPacket p_9899_) {
        PacketUtils.ensureRunningOnSameThread(p_9899_, this, this.player.serverLevel());
        if (this.player.containerMenu instanceof AnvilMenu anvilmenu) {
            if (!anvilmenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, anvilmenu);
                return;
            }

            anvilmenu.setItemName(p_9899_.getName());
        }
    }

    @Override
    public void handleSetBeaconPacket(ServerboundSetBeaconPacket p_9907_) {
        PacketUtils.ensureRunningOnSameThread(p_9907_, this, this.player.serverLevel());
        if (this.player.containerMenu instanceof BeaconMenu beaconmenu) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
                return;
            }

            beaconmenu.updateEffects(p_9907_.primary(), p_9907_.secondary());
        }
    }

    @Override
    public void handleSetStructureBlock(ServerboundSetStructureBlockPacket p_9919_) {
        PacketUtils.ensureRunningOnSameThread(p_9919_, this, this.player.serverLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockpos = p_9919_.getPos();
            BlockState blockstate = this.player.level().getBlockState(blockpos);
            if (this.player.level().getBlockEntity(blockpos) instanceof StructureBlockEntity structureblockentity) {
                structureblockentity.setMode(p_9919_.getMode());
                structureblockentity.setStructureName(p_9919_.getName());
                structureblockentity.setStructurePos(p_9919_.getOffset());
                structureblockentity.setStructureSize(p_9919_.getSize());
                structureblockentity.setMirror(p_9919_.getMirror());
                structureblockentity.setRotation(p_9919_.getRotation());
                structureblockentity.setMetaData(p_9919_.getData());
                structureblockentity.setIgnoreEntities(p_9919_.isIgnoreEntities());
                structureblockentity.setShowAir(p_9919_.isShowAir());
                structureblockentity.setShowBoundingBox(p_9919_.isShowBoundingBox());
                structureblockentity.setIntegrity(p_9919_.getIntegrity());
                structureblockentity.setSeed(p_9919_.getSeed());
                if (structureblockentity.hasStructureName()) {
                    String s = structureblockentity.getStructureName();
                    if (p_9919_.getUpdateType() == StructureBlockEntity.UpdateType.SAVE_AREA) {
                        if (structureblockentity.saveStructure()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.save_success", s), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.save_failure", s), false);
                        }
                    } else if (p_9919_.getUpdateType() == StructureBlockEntity.UpdateType.LOAD_AREA) {
                        if (!structureblockentity.isStructureLoadable()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_not_found", s), false);
                        } else if (structureblockentity.placeStructureIfSameSize(this.player.serverLevel())) {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_success", s), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_prepare", s), false);
                        }
                    } else if (p_9919_.getUpdateType() == StructureBlockEntity.UpdateType.SCAN_AREA) {
                        if (structureblockentity.detectSize()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.size_success", s), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.size_failure"), false);
                        }
                    }
                } else {
                    this.player.displayClientMessage(Component.translatable("structure_block.invalid_structure_name", p_9919_.getName()), false);
                }

                structureblockentity.setChanged();
                this.player.level().sendBlockUpdated(blockpos, blockstate, blockstate, 3);
            }
        }
    }

    @Override
    public void handleSetJigsawBlock(ServerboundSetJigsawBlockPacket p_9917_) {
        PacketUtils.ensureRunningOnSameThread(p_9917_, this, this.player.serverLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockpos = p_9917_.getPos();
            BlockState blockstate = this.player.level().getBlockState(blockpos);
            if (this.player.level().getBlockEntity(blockpos) instanceof JigsawBlockEntity jigsawblockentity) {
                jigsawblockentity.setName(p_9917_.getName());
                jigsawblockentity.setTarget(p_9917_.getTarget());
                jigsawblockentity.setPool(ResourceKey.create(Registries.TEMPLATE_POOL, p_9917_.getPool()));
                jigsawblockentity.setFinalState(p_9917_.getFinalState());
                jigsawblockentity.setJoint(p_9917_.getJoint());
                jigsawblockentity.setPlacementPriority(p_9917_.getPlacementPriority());
                jigsawblockentity.setSelectionPriority(p_9917_.getSelectionPriority());
                jigsawblockentity.setChanged();
                this.player.level().sendBlockUpdated(blockpos, blockstate, blockstate, 3);
            }
        }
    }

    @Override
    public void handleJigsawGenerate(ServerboundJigsawGeneratePacket p_9868_) {
        PacketUtils.ensureRunningOnSameThread(p_9868_, this, this.player.serverLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockpos = p_9868_.getPos();
            if (this.player.level().getBlockEntity(blockpos) instanceof JigsawBlockEntity jigsawblockentity) {
                jigsawblockentity.generate(this.player.serverLevel(), p_9868_.levels(), p_9868_.keepJigsaws());
            }
        }
    }

    @Override
    public void handleSelectTrade(ServerboundSelectTradePacket p_9905_) {
        PacketUtils.ensureRunningOnSameThread(p_9905_, this, this.player.serverLevel());
        int i = p_9905_.getItem();
        if (this.player.containerMenu instanceof MerchantMenu merchantmenu) {
            // CraftBukkit start
            final org.bukkit.event.inventory.TradeSelectEvent tradeSelectEvent = CraftEventFactory.callTradeSelectEvent(this.player, i, merchantmenu);
            if (tradeSelectEvent.isCancelled()) {
                this.player.containerMenu.sendAllDataToRemote();
                return;
            }
            // CraftBukkit end
            if (!merchantmenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, merchantmenu);
                return;
            }

            merchantmenu.setSelectionHint(i);
            merchantmenu.tryMoveItems(i);
        }
    }

    @Override
    public void handleEditBook(ServerboundEditBookPacket p_9862_) {
        if (!gg.pufferfish.pufferfish.PufferfishConfig.enableBooks && !this.player.getBukkitEntity().hasPermission("pufferfish.usebooks")) return; // Pufferfish
        // CraftBukkit start
        if (this.lastBookTick + 20 > MinecraftServer.currentTick) {
            this.disconnectAsync(Component.literal("Book edited too quickly!"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // Paper - kick event cause
            return;
        }
        this.lastBookTick = MinecraftServer.currentTick;
        // CraftBukkit end
        int i = p_9862_.slot();
        if (Inventory.isHotbarSlot(i) || i == 40) {
            List<String> list = Lists.newArrayList();
            Optional<String> optional = p_9862_.title();
            optional.ifPresent(list::add);
            p_9862_.pages().stream().limit(100L).forEach(list::add);
            Consumer<List<FilteredText>> consumer = optional.isPresent()
                ? p_238198_ -> this.signBook(p_238198_.get(0), p_238198_.subList(1, p_238198_.size()), i)
                : p_143627_ -> this.updateBookContents(p_143627_, i);
            this.filterTextPacket(list).thenAcceptAsync(consumer, this.server);
        }
    }

    private void updateBookContents(List<FilteredText> p_9813_, int p_9814_) {
        // CraftBukkit start
        ItemStack handItem = this.player.getInventory().getItem(p_9814_);
        ItemStack itemstack = handItem.copy();
        // CraftBukkit end
        if (itemstack.is(Items.WRITABLE_BOOK)) {
            List<Filterable<String>> list = p_9813_.stream().map(this::filterableFromOutgoing).toList();
            itemstack.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(list));
            this.player.getInventory().setItem(p_9814_, CraftEventFactory.handleEditBookEvent(this.player, p_9814_, handItem, itemstack)); // CraftBukkit // Paper - Don't ignore result (see other callsite for handleEditBookEvent)
        }
    }

    private void signBook(FilteredText p_215209_, List<FilteredText> p_215210_, int p_215211_) {
        ItemStack itemstack = this.player.getInventory().getItem(p_215211_);
        if (itemstack.is(Items.WRITABLE_BOOK)) {
            ItemStack itemstack1 = itemstack.transmuteCopy(Items.WRITTEN_BOOK);
            itemstack1.remove(DataComponents.WRITABLE_BOOK_CONTENT);
            List<Filterable<Component>> list = p_215210_.stream().map(p_329965_ -> this.filterableFromOutgoing(p_329965_).<Component>map(Component::literal)).toList();
            itemstack1.set(
                DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(this.filterableFromOutgoing(p_215209_), this.player.getName().getString(), 0, list, true)
            );
            CraftEventFactory.handleEditBookEvent(player, p_215211_, itemstack, itemstack1); // CraftBukkit
            this.player.getInventory().setItem(p_215211_, itemstack); // CraftBukkit - event factory updates the hand book
        }
    }

    private Filterable<String> filterableFromOutgoing(FilteredText p_332041_) {
        return this.player.isTextFilteringEnabled() ? Filterable.passThrough(p_332041_.filteredOrEmpty()) : Filterable.from(p_332041_);
    }

    @Override
    public void handleEntityTagQuery(ServerboundEntityTagQueryPacket p_320066_) {
        PacketUtils.ensureRunningOnSameThread(p_320066_, this, this.player.serverLevel());
        if (this.player.hasPermissions(2)) {
            Entity entity = this.player.level().getEntity(p_320066_.getEntityId());
            if (entity != null) {
                CompoundTag compoundtag = entity.saveWithoutId(new CompoundTag());
                this.player.connection.send(new ClientboundTagQueryPacket(p_320066_.getTransactionId(), compoundtag));
            }
        }
    }

    @Override
    public void handleContainerSlotStateChanged(ServerboundContainerSlotStateChangedPacket p_307480_) {
        PacketUtils.ensureRunningOnSameThread(p_307480_, this, this.player.serverLevel());
        if (!this.player.isSpectator() && p_307480_.containerId() == this.player.containerMenu.containerId) {
            if (this.player.containerMenu instanceof CrafterMenu craftermenu && craftermenu.getContainer() instanceof CrafterBlockEntity crafterblockentity) {
                crafterblockentity.setSlotState(p_307480_.slotId(), p_307480_.newState());
            }
        }
    }

    @Override
    public void handleBlockEntityTagQuery(ServerboundBlockEntityTagQueryPacket p_320124_) {
        PacketUtils.ensureRunningOnSameThread(p_320124_, this, this.player.serverLevel());
        if (this.player.hasPermissions(2)) {
            BlockEntity blockentity = this.player.level().getBlockEntity(p_320124_.getPos());
            CompoundTag compoundtag = blockentity != null ? blockentity.saveWithoutMetadata(this.player.registryAccess()) : null;
            this.player.connection.send(new ClientboundTagQueryPacket(p_320124_.getTransactionId(), compoundtag));
        }
    }
    double handleMovePlayer$prevX;
    double handleMovePlayer$prevY;
    double handleMovePlayer$prevZ;
    float handleMovePlayer$prevYaw;
    float handleMovePlayer$prevPitch;
    double handleMovePlayer$currDeltaX;
    double handleMovePlayer$currDeltaY;
    double handleMovePlayer$currDeltaZ;
    double handleMovePlayer$otherFieldX;
    double handleMovePlayer$otherFieldY;
    double handleMovePlayer$otherFieldZ;
    double handleMovePlayer$speed;
    @Override
    public void handleMovePlayer(ServerboundMovePlayerPacket p_9874_) {
        PacketUtils.ensureRunningOnSameThread(p_9874_, this, this.player.serverLevel());
        if (containsInvalidValues(p_9874_.getX(0.0), p_9874_.getY(0.0), p_9874_.getZ(0.0), p_9874_.getYRot(0.0F), p_9874_.getXRot(0.0F))) {
            this.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_movement"), org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PLAYER_MOVEMENT); // Paper - kick event cause
        } else {
            ServerLevel serverlevel = this.player.serverLevel();
            if (!this.player.wonGame && !this.player.isImmobile()) { // CraftBukkit
                if (this.tickCount == 0) {
                    this.resetPosition();
                }

                if (!this.updateAwaitingTeleport()) {
                    double d0 = clampHorizontal(p_9874_.getX(this.player.getX()));
                    double d1 = clampVertical(p_9874_.getY(this.player.getY()));
                    double d2 = clampHorizontal(p_9874_.getZ(this.player.getZ()));
                    float f = Mth.wrapDegrees(p_9874_.getYRot(this.player.getYRot()));
                    float f1 = Mth.wrapDegrees(p_9874_.getXRot(this.player.getXRot()));
                    if (this.player.isPassenger()) {
                        this.player.absMoveTo(this.player.getX(), this.player.getY(), this.player.getZ(), f, f1);
                        this.player.serverLevel().getChunkSource().move(this.player);
                        this.allowedPlayerTicks = 20; // CraftBukkit
                    } else {
                        // CraftBukkit - Make sure the move is valid but then reset it for plugins to modify
                        handleMovePlayer$prevX = this.player.getX();
                        handleMovePlayer$prevY = this.player.getY();
                        handleMovePlayer$prevZ = this.player.getZ();
                        handleMovePlayer$prevYaw = this.player.getYRot();
                        handleMovePlayer$prevPitch = this.player.getXRot();
                        // CraftBukkit end
                        double d3 = this.player.getX();
                        double d4 = this.player.getY();
                        double d5 = this.player.getZ();
                        double d6 = d0 - this.firstGoodX;
                        double d7 = d1 - this.firstGoodY;
                        double d8 = d2 - this.firstGoodZ;
                        double d9 = this.player.getDeltaMovement().lengthSqr();
                        // Paper start - fix large move vectors killing the server
                        handleMovePlayer$currDeltaX = d0 - handleMovePlayer$prevX;
                        handleMovePlayer$currDeltaY = d1 - handleMovePlayer$prevY;
                        handleMovePlayer$currDeltaZ = d2 - handleMovePlayer$prevZ;
                        double d10 = Math.max(d6 * d6 + d7 * d7 + d8 * d8, (handleMovePlayer$currDeltaX * handleMovePlayer$currDeltaX + handleMovePlayer$currDeltaY * handleMovePlayer$currDeltaY + handleMovePlayer$currDeltaZ * handleMovePlayer$currDeltaZ) - 1);
                        handleMovePlayer$otherFieldX = d0 - this.lastGoodX;
                        handleMovePlayer$otherFieldY = d1 - this.lastGoodY;
                        handleMovePlayer$otherFieldZ = d2 - this.lastGoodZ;
                        d10 = Math.max(d10, (handleMovePlayer$otherFieldX * handleMovePlayer$otherFieldX + handleMovePlayer$otherFieldY * handleMovePlayer$otherFieldY + handleMovePlayer$otherFieldZ * handleMovePlayer$otherFieldZ) - 1);
                        // Paper end - fix large move vectors killing the server
                        if (this.player.isSleeping()) {
                            if (d10 > 1.0) {
                                this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), f, f1);
                            }
                        } else {
                            boolean flag = this.player.isFallFlying();
                            if (serverlevel.tickRateManager().runsNormally()) {
                                this.receivedMovePacketCount++;
                                int i = this.receivedMovePacketCount - this.knownMovePacketCount;
                                // CraftBukkit start - handle custom speeds and skipped ticks
                                this.allowedPlayerTicks += (System.currentTimeMillis() / 50) - this.lastTick;
                                this.allowedPlayerTicks = Math.max(this.allowedPlayerTicks, 1);
                                this.lastTick = (int) (System.currentTimeMillis() / 50);
                                if (i > Math.max(this.allowedPlayerTicks, 5)) {
                                    LOGGER.debug("{} is sending move packets too frequently ({} packets since last tick)", this.player.getName().getString(), i);
                                    i = 1;
                                }

                                if (p_9874_.hasRot || d10 > 0) {
                                    allowedPlayerTicks -= 1;
                                } else {
                                    allowedPlayerTicks = 20;
                                }

                                if (player.getAbilities().flying) {
                                    handleMovePlayer$speed = player.getAbilities().flyingSpeed * 20f;
                                } else {
                                    handleMovePlayer$speed = player.getAbilities().walkingSpeed * 10f;
                                }

                                // Paper start - Prevent moving into unloaded chunks
                                if (this.player.level().paperConfig().chunks.preventMovingIntoUnloadedChunks && (this.player.getX() != d0 || this.player.getZ() != d2)
                                        && !serverlevel.areChunksLoadedForMove(this.player.getBoundingBox().expandTowards(new Vec3(d0, d1, d2).subtract(this.player.position())))) {
                                    // Paper start - Add fail move event
                                    io.papermc.paper.event.player.PlayerFailMoveEvent event = fireFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent.FailReason.MOVED_INTO_UNLOADED_CHUNK,
                                            d0, d1, d2, f, f1, false);
                                    if (!event.isAllowed()) {
                                        this.internalTeleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot(), Collections.emptySet());
                                        return;
                                    }
                                    // Paper end - Add fail move event
                                }
                                // Paper end - Prevent moving into unloaded chunks

                                if (!this.player.isChangingDimension()
                                    && (!this.player.level().getGameRules().getBoolean(GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK) || !flag)) {
                                    float f2 = flag ? 300.0F : 100.0F;
                                    if (d10 - d9 > Math.max(f2, Math.pow((double) (org.spigotmc.SpigotConfig.movedTooQuicklyMultiplier * (float) i * handleMovePlayer$speed), 2)) && !this.isSingleplayerOwner()) {
                                        // CraftBukkit end
                                        // Paper start - Add fail move event
                                        io.papermc.paper.event.player.PlayerFailMoveEvent event = fireFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY,
                                                d0, d1, d2, f, f1, true);
                                        if (!event.isAllowed()) {
                                            if (event.getLogWarning())
                                                ServerGamePacketListenerImpl.LOGGER.warn("{} moved too quickly! {},{},{}", new Object[]{this.player.getName().getString(), d6, d7, d8});
                                            this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot());
                                            return;
                                        }
                                        // Paper end - Add fail move event
                                    }
                                }
                            }

                            AABB aabb = this.player.getBoundingBox();
                            d6 = d0 - this.lastGoodX;
                            d7 = d1 - this.lastGoodY;
                            d8 = d2 - this.lastGoodZ;
                            boolean flag4 = d7 > 0.0;
                            if (this.player.onGround() && !p_9874_.isOnGround() && flag4) {
                                // Paper start - Add PlayerJumpEvent
                                org.bukkit.entity.Player player = this.getCraftPlayer();
                                Location from = new Location(player.getWorld(), lastPosX, lastPosY, lastPosZ, lastYaw, lastPitch); // Get the Players previous Event location.
                                Location to = player.getLocation().clone(); // Start off the To location as the Players current location.

                                // If the packet contains movement information then we update the To location with the correct XYZ.
                                if (p_9874_.hasPos) {
                                    to.setX(p_9874_.x);
                                    to.setY(p_9874_.y);
                                    to.setZ(p_9874_.z);
                                }

                                // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
                                if (p_9874_.hasRot) {
                                    to.setYaw(p_9874_.yRot);
                                    to.setPitch(p_9874_.xRot);
                                }

                                com.destroystokyo.paper.event.player.PlayerJumpEvent event = new com.destroystokyo.paper.event.player.PlayerJumpEvent(player, from, to);

                                if (event.callEvent()) {
                                    this.player.jumpFromGround();
                                } else {
                                    from = event.getFrom();
                                    this.internalTeleport(from.getX(), from.getY(), from.getZ(), from.getYaw(), from.getPitch(), Collections.emptySet());
                                    return;
                                }
                                // Paper end - Add PlayerJumpEvent
                            }

                            boolean flag1 = this.player.verticalCollisionBelow;
                            this.player.move(MoverType.PLAYER, new Vec3(d6, d7, d8));
                            this.player.onGround = p_9874_.isOnGround(); // CraftBukkit - SPIGOT-5810, SPIGOT-5835, SPIGOT-6828: reset by this.player.move
                            // Paper start - prevent position desync
                            if (this.awaitingPositionFromClient != null) {
                                return; // ... thanks Mojang for letting move calls teleport across dimensions.
                            }
                            // Paper end - prevent position desync
                            d6 = d0 - this.player.getX();
                            d7 = d1 - this.player.getY();
                            if (d7 > -0.5 || d7 < 0.5) {
                                d7 = 0.0;
                            }

                            d8 = d2 - this.player.getZ();
                            d10 = d6 * d6 + d7 * d7 + d8 * d8;
                            boolean movedWrongly = false; // Paper - Add fail move event; rename
                            if (!this.player.isChangingDimension()
                                && d10 > org.spigotmc.SpigotConfig.movedWronglyThreshold
                                && !this.player.isSleeping()
                                && !this.player.gameMode.isCreative()
                                && this.player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                                // Paper start - Add fail move event
                                io.papermc.paper.event.player.PlayerFailMoveEvent event = fireFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent.FailReason.MOVED_WRONGLY,
                                        d0, d1, d2, f, f1, true);
                                if (!event.isAllowed()) {
                                    movedWrongly = true;
                                    if (event.getLogWarning())
                                        // Paper end
                                        ServerGamePacketListenerImpl.LOGGER.warn("{} moved wrongly!, ({})", this.player.getName().getString(), d7); // Purpur
                                } // Paper
                            }

                            if (this.player.noPhysics
                                || this.player.isSleeping()
                                || (!movedWrongly || !serverlevel.noCollision(this.player, aabb))
                                    && !this.isPlayerCollidingWithAnythingNew(serverlevel, aabb, d0, d1, d2)) {
                                // CraftBukkit start - fire PlayerMoveEvent
                                // Reset to old location first
                                this.player.absMoveTo(handleMovePlayer$prevX, handleMovePlayer$prevY, handleMovePlayer$prevZ, handleMovePlayer$prevYaw, handleMovePlayer$prevPitch);
                                org.bukkit.entity.Player player = this.getCraftPlayer();
                                if (!this.hasMoved) {
                                    this.lastPosX = handleMovePlayer$prevX;
                                    this.lastPosY = handleMovePlayer$prevY;
                                    this.lastPosZ = handleMovePlayer$prevZ;
                                    this.lastYaw = handleMovePlayer$prevYaw;
                                    this.lastPitch = handleMovePlayer$prevPitch;
                                    this.hasMoved = true;
                                }
                                org.bukkit.Location from = new org.bukkit.Location(player.getWorld(), lastPosX, lastPosY, lastPosZ, lastYaw, lastPitch); // Get the Players previous Event location.
                                org.bukkit.Location to = player.getLocation().clone(); // Start off the To location as the Players current location.
                                // If the packet contains movement information then we update the To location with the correct XYZ.
                                if (p_9874_.hasPos) {
                                    to.setX(p_9874_.x);
                                    to.setY(p_9874_.y);
                                    to.setZ(p_9874_.z);
                                }
                                // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
                                if (p_9874_.hasRot) {
                                    to.setYaw(p_9874_.yRot);
                                    to.setPitch(p_9874_.xRot);
                                }
                                // Prevent 40 event-calls for less than a single pixel of movement >.>
                                double delta = Math.pow(this.lastPosX - to.getX(), 2) + Math.pow(this.lastPosY - to.getY(), 2) + Math.pow(this.lastPosZ - to.getZ(), 2);
                                float deltaAngle = Math.abs(this.lastYaw - to.getYaw()) + Math.abs(this.lastPitch - to.getPitch());
                                if ((delta > 1f / 256 || deltaAngle > 10f) && !this.player.isImmobile()) {
                                    this.lastPosX = to.getX();
                                    this.lastPosY = to.getY();
                                    this.lastPosZ = to.getZ();
                                    this.lastYaw = to.getYaw();
                                    this.lastPitch = to.getPitch();
                                    if (!to.getWorld().getUID().equals(from.getWorld().getUID()) || to.getBlockX() != from.getBlockX() || to.getBlockY() != from.getBlockY() || to.getBlockZ() != from.getBlockZ() || to.getYaw() != from.getYaw() || to.getPitch() != from.getPitch()) this.player.resetLastActionTime(); // Purpur
                                    org.bukkit.Location oldTo = to.clone();
                                    org.bukkit.event.player.PlayerMoveEvent event = new org.bukkit.event.player.PlayerMoveEvent(player, from, to);
                                    this.cserver.getPluginManager().callEvent(event);
                                    // If the event is cancelled we move the player back to their old location.
                                    if (event.isCancelled()) {
                                        teleport(from);
                                        return;
                                    }
                                    // If a Plugin has changed the To destination then we teleport the Player
                                    // there to avoid any 'Moved wrongly' or 'Moved too quickly' errors.
                                    // We only do this if the Event was not cancelled.
                                    if (!oldTo.equals(event.getTo()) && !event.isCancelled()) {
                                        this.player.getBukkitEntity().teleport(event.getTo(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                                        return;
                                    }
                                    // Check to see if the Players Location has some how changed during the call of the event.
                                    // This can happen due to a plugin teleporting the player instead of using .setTo()
                                    if (!from.equals(this.getCraftPlayer().getLocation()) && this.justTeleported) {
                                        this.justTeleported = false;
                                        return;
                                    }
                                }
                                // CraftBukkit end
                                this.player.absMoveTo(d0, d1, d2, f, f1);
                                boolean flag3 = this.player.isAutoSpinAttack();
                                this.clientIsFloating = d7 >= -0.03125
                                    && !flag1
                                    && this.player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                                    && !this.server.isFlightAllowed()
                                    && !this.player.mayFly()
                                    && !this.player.hasEffect(MobEffects.LEVITATION)
                                    && !flag
                                    && !flag3
                                    && this.noBlocksAround(this.player);
                                this.player.serverLevel().getChunkSource().move(this.player);
                                Vec3 vec3 = new Vec3(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5);
                                this.player.setOnGroundWithMovement(p_9874_.isOnGround(), vec3);
                                this.player.doCheckFallDamage(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5, p_9874_.isOnGround());
                                this.player.setKnownMovement(vec3);
                                if (flag4) {
                                    this.player.resetFallDistance();
                                }

                                if (p_9874_.isOnGround()
                                    || this.player.hasLandedInLiquid()
                                    || this.player.onClimbable()
                                    || this.player.isSpectator()
                                    || flag
                                    || flag3) {
                                    this.player.tryResetCurrentImpulseContext();
                                }

                                this.player.checkMovementStatistics(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5);
                                this.lastGoodX = this.player.getX();
                                this.lastGoodY = this.player.getY();
                                this.lastGoodZ = this.player.getZ();
                            } else {
                                this.teleport(d3, d4, d5, f, f1);
                                this.player.doCheckFallDamage(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5, p_9874_.isOnGround());
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean updateAwaitingTeleport() {
        if (this.awaitingPositionFromClient != null) {
            if (this.tickCount - this.awaitingTeleportTime > 20) {
                this.awaitingTeleportTime = this.tickCount;
                this.teleport(
                    this.awaitingPositionFromClient.x,
                    this.awaitingPositionFromClient.y,
                    this.awaitingPositionFromClient.z,
                    this.player.getYRot(),
                    this.player.getXRot()
                );
            }
            this.allowedPlayerTicks = 20; // CraftBukkit

            return true;
        } else {
            this.awaitingTeleportTime = this.tickCount;
            return false;
        }
    }

    private boolean isPlayerCollidingWithAnythingNew(LevelReader p_289008_, AABB p_288986_, double p_288990_, double p_288991_, double p_288967_) {
        AABB aabb = this.player.getBoundingBox().move(p_288990_ - this.player.getX(), p_288991_ - this.player.getY(), p_288967_ - this.player.getZ());
        Iterable<VoxelShape> iterable = p_289008_.getCollisions(this.player, aabb.deflate(1.0E-5F));
        VoxelShape voxelshape = Shapes.create(p_288986_.deflate(1.0E-5F));

        for (VoxelShape voxelshape1 : iterable) {
            if (!Shapes.joinIsNotEmpty(voxelshape1, voxelshape, BooleanOp.AND)) {
                return true;
            }
        }

        return false;
    }

    // CraftBukkit start - Delegate to teleport(Location)
    public void teleport(double p_9775_, double p_9776_, double p_9777_, float p_9778_, float p_9779_) {
        this.teleport(p_9775_, p_9776_, p_9777_, p_9778_, p_9779_, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }
    public boolean teleport(double d0, double d1, double d2, float f, float f1, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        return this.teleport(d0, d1, d2, f, f1, Collections.emptySet(), cause);
    }

    private final AtomicReference<PlayerTeleportEvent.TeleportCause> teleport$cause = new AtomicReference<>(PlayerTeleportEvent.TeleportCause.UNKNOWN);
    private final AtomicBoolean teleport$boolean = new AtomicBoolean(true);
    public void teleport$cause(PlayerTeleportEvent.TeleportCause cause) {
        this.teleport$cause.set(cause);
    }

    public void teleport(double p_9781_, double p_9782_, double p_9783_, float p_9784_, float p_9785_, Set<RelativeMovement> p_9786_) {
        org.bukkit.entity.Player player = this.getCraftPlayer();
        org.bukkit.Location from = player.getLocation();
        double x = p_9781_;
        double y = p_9782_;
        double z = p_9783_;
        float yaw = p_9784_;
        float pitch = p_9785_;
        org.bukkit.Location to = new org.bukkit.Location(this.getCraftPlayer().getWorld(), x, y, z, yaw, pitch);
        // SPIGOT-5171: Triggered on join
        if (from.equals(to)) {
            this.internalTeleport(p_9781_, p_9782_, p_9783_, p_9784_, p_9785_, p_9786_);
            teleport$boolean.set(true);
            return; // CraftBukkit - Return event status
        }
        if (!AsyncCatcher.catchAsync()) {
            // Paper start - Teleport API
            Set<io.papermc.paper.entity.TeleportFlag.Relative> relativeFlags = java.util.EnumSet.noneOf(io.papermc.paper.entity.TeleportFlag.Relative.class);
            for (RelativeMovement relativeArgument : p_9786_) {
                relativeFlags.add(org.bukkit.craftbukkit.entity.CraftPlayer.toApiRelativeFlag(relativeArgument));
            }
            PlayerTeleportEvent event = new PlayerTeleportEvent(player, from.clone(), to.clone(), teleport$cause.getAndSet(PlayerTeleportEvent.TeleportCause.UNKNOWN), java.util.Set.copyOf(relativeFlags));
            // Paper end - Teleport API
            this.cserver.getPluginManager().callEvent(event);
            if (event.isCancelled() || !to.equals(event.getTo())) {
                // p_9786_ = Collections.emptySet(); // Can't relative teleport // Paper - Teleport API; Now you can!
                to = event.isCancelled() ? event.getFrom() : event.getTo();
                p_9781_ = to.getX();
                p_9782_ = to.getY();
                p_9783_ = to.getZ();
                p_9784_ = to.getYaw();
                p_9785_ = to.getPitch();
            }
            this.internalTeleport(p_9781_, p_9782_, p_9783_, p_9784_, p_9785_, p_9786_);
            teleport$boolean.set(!event.isCancelled());
        } else {
            this.internalTeleport(p_9781_, p_9782_, p_9783_, p_9784_, p_9785_, p_9786_);
            teleport$boolean.set(true);
        }
    }

    public boolean teleport(double d0, double d1, double d2, float f, float f1, Set<RelativeMovement> set, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) { // CraftBukkit - Return event status
        this.teleport$cause(cause);
        teleport(d0, d1, d2, f, f1, set);
        return teleport$boolean.getAndSet(true);
    }

    public void teleport(org.bukkit.Location dest) {
        internalTeleport(dest.getX(), dest.getY(), dest.getZ(), dest.getYaw(), dest.getPitch(), Collections.emptySet());
    }

    public void internalTeleport(double p_9781_, double p_9782_, double p_9783_, float p_9784_, float p_9785_, Set<RelativeMovement> p_9786_) {
        // Paper start - Prevent teleporting dead entities
        if (player.isRemoved()) {
            LOGGER.info("Attempt to teleport removed player {} restricted", player.getScoreboardName());
            if (server.isDebugging()) io.papermc.paper.util.TraceUtil.dumpTraceForThread("Attempt to teleport removed player");
            return;
        }
        // Paper end - Prevent teleporting dead entities
        // CraftBukkit start
        if (Float.isNaN(p_9784_)) {
            p_9784_ = 0;
        }
        if (Float.isNaN(p_9785_)) {
            p_9785_ = 0;
        }
        this.justTeleported = true;
        // CraftBukkit end
        double d0 = p_9786_.contains(RelativeMovement.X) ? this.player.getX() : 0.0;
        double d1 = p_9786_.contains(RelativeMovement.Y) ? this.player.getY() : 0.0;
        double d2 = p_9786_.contains(RelativeMovement.Z) ? this.player.getZ() : 0.0;
        float f = p_9786_.contains(RelativeMovement.Y_ROT) ? this.player.getYRot() : 0.0F;
        float f1 = p_9786_.contains(RelativeMovement.X_ROT) ? this.player.getXRot() : 0.0F;
        this.awaitingPositionFromClient = new Vec3(p_9781_, p_9782_, p_9783_);
        if (++this.awaitingTeleport == Integer.MAX_VALUE) {
            this.awaitingTeleport = 0;
        }

        // CraftBukkit start - update last location
        this.lastPosX = this.awaitingPositionFromClient.x;
        this.lastPosY = this.awaitingPositionFromClient.y;
        this.lastPosZ = this.awaitingPositionFromClient.z;
        this.lastYaw = f;
        this.lastPitch = f1;
        // CraftBukkit end

        this.awaitingTeleportTime = this.tickCount;
        this.player.moveTo(p_9781_, p_9782_, p_9783_, p_9784_, p_9785_); // Paper - Fix Entity Teleportation and cancel velocity if teleported
        this.player
            .connection
            .send(new ClientboundPlayerPositionPacket(p_9781_ - d0, p_9782_ - d1, p_9783_ - d2, p_9784_ - f, p_9785_ - f1, p_9786_, this.awaitingTeleport));
    }

    @Override
    public void handlePlayerAction(ServerboundPlayerActionPacket p_9889_) {
        PacketUtils.ensureRunningOnSameThread(p_9889_, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        BlockPos blockpos = p_9889_.getPos();
        this.player.resetLastActionTime();
        ServerboundPlayerActionPacket.Action serverboundplayeractionpacket$action = p_9889_.getAction();
        switch (serverboundplayeractionpacket$action) {
            case SWAP_ITEM_WITH_OFFHAND:
                if (!this.player.isSpectator()) {
                    ItemStack itemstack = this.player.getItemInHand(InteractionHand.OFF_HAND);
                    var event = net.neoforged.neoforge.common.CommonHooks.onLivingSwapHandItems(this.player);
                    if (event.isCanceled()) return;
                    // CraftBukkit start - inspiration taken from DispenserRegistry (See SpigotCraft#394)
                    CraftItemStack mainHand = CraftItemStack.asCraftMirror(itemstack);
                    CraftItemStack offHand = CraftItemStack.asCraftMirror(this.player.getItemInHand(InteractionHand.MAIN_HAND));
                    org.bukkit.event.player.PlayerSwapHandItemsEvent swapItemsEvent = new org.bukkit.event.player.PlayerSwapHandItemsEvent(getCraftPlayer(), mainHand.clone(), offHand.clone());
                    this.cserver.getPluginManager().callEvent(swapItemsEvent);
                    if (swapItemsEvent.isCancelled()) {
                        return;
                    }
                    if (swapItemsEvent.getOffHandItem().equals(offHand)) {
                        this.player.setItemInHand(InteractionHand.OFF_HAND, this.player.getItemInHand(InteractionHand.MAIN_HAND));
                    } else {
                        this.player.setItemInHand(InteractionHand.OFF_HAND, CraftItemStack.asNMSCopy(swapItemsEvent.getOffHandItem()));
                    }
                    if (swapItemsEvent.getMainHandItem().equals(mainHand)) {
                        this.player.setItemInHand(InteractionHand.MAIN_HAND, itemstack);
                    } else {
                        this.player.setItemInHand(InteractionHand.MAIN_HAND, CraftItemStack.asNMSCopy(swapItemsEvent.getMainHandItem()));
                    }
                    // CraftBukkit end
                    this.player.stopUsingItem();
                }

                return;
            case DROP_ITEM:
                if (!this.player.isSpectator()) {
                    // limit how quickly items can be dropped
                    // If the ticks aren't the same then the count starts from 0 and we update the lastDropTick.
                    if (this.lastDropTick != MinecraftServer.currentTick) {
                        this.dropCount = 0;
                        this.lastDropTick = MinecraftServer.currentTick;
                    } else {
                        // Else we increment the drop count and check the amount.
                        this.dropCount++;
                        if (this.dropCount >= 20) {
                            LOGGER.warn(this.player.getScoreboardName() + " dropped their items too quickly!");
                            this.disconnect(Component.literal("You dropped your items too quickly (Hacking?)"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // Paper - kick event cause
                            return;
                        }
                    }
                    // CraftBukkit end
                    this.player.drop(false);
                }

                return;
            case DROP_ALL_ITEMS:
                if (!this.player.isSpectator()) {
                    this.player.drop(true);
                }

                return;
            case RELEASE_USE_ITEM:
                this.player.releaseUsingItem();
                return;
            case START_DESTROY_BLOCK:
            case ABORT_DESTROY_BLOCK:
            case STOP_DESTROY_BLOCK:
                this.player
                    .gameMode
                    .handleBlockBreakAction(
                        blockpos, serverboundplayeractionpacket$action, p_9889_.getDirection(), this.player.level().getMaxBuildHeight(), p_9889_.getSequence()
                    );
                this.player.connection.ackBlockChangesUpTo =p_9889_.getSequence();
                return;
            default:
                throw new IllegalArgumentException("Invalid player action");
        }
    }

    private static boolean wasBlockPlacementAttempt(ServerPlayer p_9791_, ItemStack p_9792_) {
        if (p_9792_.isEmpty()) {
            return false;
        } else {
            Item item = p_9792_.getItem();
            return (item instanceof BlockItem || item instanceof BucketItem) && !p_9791_.getCooldowns().isOnCooldown(item);
        }
    }

    // Spigot start - limit place/interactions
    private int limitedPackets;
    private long lastLimitedPacket = -1;
    private static int getSpamThreshold() { return io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.incomingPacketThreshold; } // Paper - Configurable threshold
    private boolean checkLimit(long timestamp) {
        if (lastLimitedPacket != -1 && timestamp - lastLimitedPacket < getSpamThreshold() && this.limitedPackets++ >= 8) { // Paper - Configurable threshold; raise packet limit to 8
            return false;
        }
        if (lastLimitedPacket == -1 || timestamp - lastLimitedPacket >= getSpamThreshold()) { // Paper - Configurable threshold
            lastLimitedPacket = timestamp;
            limitedPackets = 0;
            return true;
        }
        return true;
    }
    // Spigot end

    @Override
    public void handleUseItemOn(ServerboundUseItemOnPacket p_9930_) {
        PacketUtils.ensureRunningOnSameThread(p_9930_, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (!checkLimit(p_9930_.timestamp)) return; // Spigot - check limit
        this.player.connection.ackBlockChangesUpTo = p_9930_.getSequence();
        ServerLevel serverlevel = this.player.serverLevel();
        InteractionHand interactionhand = p_9930_.getHand();
        ItemStack itemstack = this.player.getItemInHand(interactionhand);
        if (itemstack.isItemEnabled(serverlevel.enabledFeatures())) {
            BlockHitResult blockhitresult = p_9930_.getHitResult();
            Vec3 vec3 = blockhitresult.getLocation();
            // Paper start - improve distance check
            if (!Double.isFinite(vec3.x) || !Double.isFinite(vec3.y) || !Double.isFinite(vec3.z)) {
                return;
            }
            // Paper end - improve distance check
            BlockPos blockpos = blockhitresult.getBlockPos();
            if (this.player.canInteractWithBlock(blockpos, 1.0)) {
                Vec3 vec31 = vec3.subtract(Vec3.atCenterOf(blockpos));
                double d0 = 1.0000001;
                if (Math.abs(vec31.x()) < 1.0000001 && Math.abs(vec31.y()) < 1.0000001 && Math.abs(vec31.z()) < 1.0000001) {
                    Direction direction = blockhitresult.getDirection();
                    this.player.resetLastActionTime();
                    int i = this.player.level().getMaxBuildHeight();
                    if (blockpos.getY() < i) {
                        if (this.awaitingPositionFromClient == null && (serverlevel.mayInteract(this.player, blockpos) || (serverlevel.paperConfig().spawn.allowUsingSignsInsideSpawnProtection && serverlevel.getBlockState(blockpos).getBlock() instanceof net.minecraft.world.level.block.SignBlock))) { // Paper - Allow using signs inside spawn protection
                            InteractionResult interactionresult = this.player
                                .gameMode
                                .useItemOn(this.player, serverlevel, itemstack, interactionhand, blockhitresult);
                            if (interactionresult.consumesAction()) {
                                CriteriaTriggers.ANY_BLOCK_USE.trigger(this.player, blockhitresult.getBlockPos(), itemstack.copy());
                            }

                            if (direction == Direction.UP
                                && !interactionresult.consumesAction()
                                && blockpos.getY() >= i - 1
                                && wasBlockPlacementAttempt(this.player, itemstack)) {
                                Component component = Component.translatable("build.tooHigh", i - 1).withStyle(ChatFormatting.RED);
                                this.player.sendSystemMessage(component, true);
                            } else if (interactionresult.shouldSwing() ) {
                                this.player.swing(interactionhand, true);
                            }
                        } else { this.player.containerMenu.sendAllDataToRemote(); } // Paper - Fix inventory desync; MC-99075
                    } else {
                        Component component1 = Component.translatable("build.tooHigh", i - 1).withStyle(ChatFormatting.RED);
                        this.player.sendSystemMessage(component1, true);
                    }

                    this.player.connection.send(new ClientboundBlockUpdatePacket(serverlevel, blockpos));
                    this.player.connection.send(new ClientboundBlockUpdatePacket(serverlevel, blockpos.relative(direction)));
                } else {
                    LOGGER.warn(
                        "Rejecting UseItemOnPacket from {}: Location {} too far away from hit block {}.",
                        this.player.getGameProfile().getName(),
                        vec3,
                        blockpos
                    );
                }
            }
        }
    }

    @Override
    public void handleUseItem(ServerboundUseItemPacket p_9932_) {
        PacketUtils.ensureRunningOnSameThread(p_9932_, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (!checkLimit(p_9932_.timestamp)) return; // Spigot - check limit
        this.ackBlockChangesUpTo(p_9932_.getSequence());
        ServerLevel serverlevel = this.player.serverLevel();
        InteractionHand interactionhand = p_9932_.getHand();
        ItemStack itemstack = this.player.getItemInHand(interactionhand);
        this.player.resetLastActionTime();
        if (!itemstack.isEmpty() && itemstack.isItemEnabled(serverlevel.enabledFeatures())) {
            float f = Mth.wrapDegrees(p_9932_.getYRot());
            float f1 = Mth.wrapDegrees(p_9932_.getXRot());
            if (f1 != this.player.getXRot() || f != this.player.getYRot()) {
                this.player.absRotateTo(f, f1);
            }

            // CraftBukkit start
            // Raytrace to look for 'rogue armswings'
            double d0 = this.player.getX();
            double d1 = this.player.getY() + (double) this.player.getEyeHeight();
            double d2 = this.player.getZ();
            Vec3 vec3d = new Vec3(d0, d1, d2);

            float f3 = Mth.cos(-f * 0.017453292F - 3.1415927F);
            float f4 = Mth.sin(-f * 0.017453292F - 3.1415927F);
            float f5 = -Mth.cos(-f1 * 0.017453292F);
            float f6 = Mth.sin(-f1 * 0.017453292F);
            float f7 = f4 * f5;
            float f8 = f3 * f5;
            double d3 = this.player.blockInteractionRange();
            Vec3 vec3d1 = vec3d.add((double) f7 * d3, (double) f6 * d3, (double) f8 * d3);
            HitResult movingobjectposition = this.player.level().clip(new ClipContext(vec3d, vec3d1, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.player));

            boolean cancelled;
            if (movingobjectposition == null || movingobjectposition.getType() != HitResult.Type.BLOCK) {
                org.bukkit.event.player.PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(this.player, Action.RIGHT_CLICK_AIR, itemstack, interactionhand);
                cancelled = event.useItemInHand() == Event.Result.DENY;
            } else {
                BlockHitResult movingobjectpositionblock = (BlockHitResult) movingobjectposition;
                if (this.player.gameMode.firedInteract && this.player.gameMode.interactPosition.equals(movingobjectpositionblock.getBlockPos()) && this.player.gameMode.interactHand == interactionhand && ItemStack.isSameItemSameComponents(this.player.gameMode.interactItemStack, itemstack)) {
                    cancelled = this.player.gameMode.interactResult;
                } else {
                    org.bukkit.event.player.PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(this.player, Action.RIGHT_CLICK_BLOCK, movingobjectpositionblock.getBlockPos(), movingobjectpositionblock.getDirection(), itemstack, true, interactionhand, movingobjectpositionblock.getLocation());
                    cancelled = event.useItemInHand() == Event.Result.DENY;
                }
                this.player.gameMode.firedInteract = false;
            }

            if (cancelled) {
                this.player.resyncUsingItem(this.player); // Paper - Properly cancel usable items
                this.player.getBukkitEntity().updateInventory(); // SPIGOT-2524
                return;
            }
            itemstack = this.player.getItemInHand(interactionhand); // Update in case it was changed in the event
            if (itemstack.isEmpty()) {
                return;
            }
            // CraftBukkit end
            InteractionResult interactionresult = this.player.gameMode.useItem(this.player, serverlevel, itemstack, interactionhand);
            if (interactionresult.shouldSwing()) {
                this.player.swing(interactionhand, true);
            }
        }
    }

    @Override
    public void handleTeleportToEntityPacket(ServerboundTeleportToEntityPacket p_9928_) {
        PacketUtils.ensureRunningOnSameThread(p_9928_, this, this.player.serverLevel());
        if (this.player.isSpectator()) {
            for (ServerLevel serverlevel : this.server.getAllLevels()) {
                Entity entity = p_9928_.getEntity(serverlevel);
                if (entity != null) {
                    this.player.teleportTo(serverlevel, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                    return;
                }
            }
        }
    }

    @Override
    public void handlePaddleBoat(ServerboundPaddleBoatPacket p_9878_) {
        PacketUtils.ensureRunningOnSameThread(p_9878_, this, this.player.serverLevel());
        if (this.player.getControlledVehicle() instanceof Boat boat) {
            boat.setPaddleState(p_9878_.getLeft(), p_9878_.getRight());
        }
    }

    @Override
    public void onDisconnect(DisconnectionDetails p_350554_) {
        // Paper start - Fix kick event leave message not being sent
        this.onDisconnect(p_350554_, null);
    }

    @Override
    public void onDisconnect(DisconnectionDetails p_350554_, @Nullable net.kyori.adventure.text.Component quitMessage) {
        // Paper end - Fix kick event leave message not being sent
        // CraftBukkit start - Rarely it would send a disconnect line twice
        if (this.processedDisconnect) {
            return;
        } else {
            this.processedDisconnect = true;
        }
        // CraftBukkit end
        LOGGER.info("{} lost connection: {}", this.player.getName().getString(), p_350554_.reason().getString());
        removePlayerFromWorld$quitMessage = quitMessage;
        this.removePlayerFromWorld();
        super.onDisconnect(p_350554_, quitMessage);
    }

    private net.kyori.adventure.text.Component removePlayerFromWorld$quitMessage = null;

    private void removePlayerFromWorld() {
        this.chatMessageChain.close();
        // CraftBukkit start - Replace vanilla quit message handling with our own.
        /*
        this.server.invalidateStatus();
        this.server
            .getPlayerList()
            .broadcastSystemMessage(Component.translatable("multiplayer.player.left", this.player.getDisplayName()).withStyle(ChatFormatting.YELLOW), false);
        */
        this.player.disconnect();
        if  (removePlayerFromWorld$quitMessage != null) {
            this.server.getPlayerList().remove$leaveMessage = removePlayerFromWorld$quitMessage;
        }
        this.server.getPlayerList().remove(this.player);
        removePlayerFromWorld$quitMessage = this.server.getPlayerList().quitMessage; // Paper - pass in quitMessage to fix kick message not being used
        if (YouerConfig.quit_message && (removePlayerFromWorld$quitMessage != null) && !removePlayerFromWorld$quitMessage.equals(net.kyori.adventure.text.Component.empty())) {
            this.server.getPlayerList().broadcastSystemMessage(PaperAdventure.asVanilla(removePlayerFromWorld$quitMessage), false);
        }
        // CraftBukkit end
        this.player.getTextFilter().leave();
    }

    private void removePlayerFromWorld(@Nullable net.kyori.adventure.text.Component quitMessage) {
        removePlayerFromWorld$quitMessage = quitMessage;
        removePlayerFromWorld();
    }

    public void ackBlockChangesUpTo(int p_215202_) {
        if (p_215202_ < 0) {
            this.disconnect(Component.literal("Expected packet sequence nr >= 0"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // Paper - Treat sequence violations like they should be
            throw new IllegalArgumentException("Expected packet sequence nr >= 0");
        } else {
            this.ackBlockChangesUpTo = Math.max(p_215202_, this.ackBlockChangesUpTo);
        }
    }

    @Override
    public void handleSetCarriedItem(ServerboundSetCarriedItemPacket p_9909_) {
        PacketUtils.ensureRunningOnSameThread(p_9909_, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (p_9909_.getSlot() >= 0 && p_9909_.getSlot() < Inventory.getSelectionSize()) {
            if (p_9909_.getSlot() == this.player.getInventory().selected) { return; } // Paper - don't fire itemheldevent when there wasn't a slot change
            org.bukkit.event.player.PlayerItemHeldEvent event = new org.bukkit.event.player.PlayerItemHeldEvent(this.getCraftPlayer(), this.player.getInventory().selected, p_9909_.getSlot());
            this.cserver.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                this.send(new ClientboundSetCarriedItemPacket(this.player.getInventory().selected));
                this.player.resetLastActionTime();
                return;
            }
            // CraftBukkit end
            if (this.player.getInventory().selected != p_9909_.getSlot() && this.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                this.player.stopUsingItem();
            }

            this.player.getInventory().selected = p_9909_.getSlot();
            this.player.resetLastActionTime();
        } else {
            LOGGER.warn("{} tried to set an invalid carried item", this.player.getName().getString());
            this.disconnect(Component.literal("Invalid hotbar selection (Hacking?)"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // CraftBukkit // Paper - kick event cause
        }
    }

    @Override
    public void handleChat(ServerboundChatPacket p_9841_) {
        // CraftBukkit start - async chat
        // SPIGOT-3638
        if (this.server.isStopped()) {
            return;
        }
        // CraftBukkit end
        Optional<LastSeenMessages> optional = this.unpackAndApplyLastSeen(p_9841_.lastSeenMessages());
        if (!optional.isEmpty()) {
            if (Boolean.parseBoolean("true")) {
                LambdaFix.handleChat(this, p_9841_, optional);
                return;
            }
            this.tryHandleChat(p_9841_.message(), () -> {
                PlayerChatMessage playerchatmessage;
                try {
                    playerchatmessage = this.getSignedMessage(p_9841_, optional.get());
                } catch (SignedMessageChain.DecodeException signedmessagechain$decodeexception) {
                    this.handleMessageDecodeFailure(signedmessagechain$decodeexception);
                    return;
                }

                CompletableFuture<FilteredText> completablefuture = this.filterTextPacket(playerchatmessage.signedContent()).thenApplyAsync(Function.identity(), this.server.chatExecutor); // CraftBukkit - async chat
                Component component = net.neoforged.neoforge.common.CommonHooks.getServerChatSubmittedDecorator().decorate(this.player, playerchatmessage.decoratedContent()).join(); // Paper - Adventure
                this.chatMessageChain.append(completablefuture, p_300785_ -> {
                    if (component == null) return; // Forge: ServerChatEvent was canceled if this is null.
                    PlayerChatMessage playerchatmessage1 = playerchatmessage.withUnsignedContent(component).filter(p_300785_.mask());
                    this.broadcastChatMessage(playerchatmessage1);
                });
            }, false); // CraftBukkit - async chat
        }
    }

    @Override
    public void handleChatCommand(ServerboundChatCommandPacket p_215225_) {
        this.tryHandleChat(p_215225_.command(), () -> {
            // CraftBukkit start - SPIGOT-7346: Prevent disconnected players from executing commands
            if (this.player.hasDisconnected()) {
                return;
            }
            // CraftBukkit end
            this.performUnsignedChatCommand(p_215225_.command());
            this.detectRateSpam("/" + p_215225_.command()); // Spigot
        }, true); // CraftBukkit - sync commands
    }

    private void performUnsignedChatCommand(String p_338482_) {
        // CraftBukkit start
        String command1 = "/" + p_338482_;
        if (org.spigotmc.SpigotConfig.logCommands) { // Paper - Add missing SpigotConfig logCommands check
            ServerGamePacketListenerImpl.LOGGER.info(this.player.getScoreboardName() + " issued server command: " + command1);
        }
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(this.getCraftPlayer(), command1, new LazyPlayerSet(this.server));
        this.cserver.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }
        p_338482_ = event.getMessage().substring(1);
        // CraftBukkit end
        ParseResults<CommandSourceStack> parseresults = this.parseCommand(p_338482_);
        if (this.server.enforceSecureProfile() && SignableCommand.hasSignableArguments(parseresults)) {
            LOGGER.error(
                "Received unsigned command packet from {}, but the command requires signable arguments: {}", this.player.getGameProfile().getName(), p_338482_
            );
            this.player.sendSystemMessage(INVALID_COMMAND_SIGNATURE);
        } else {
            this.server.getCommands().performCommand(parseresults, p_338482_);
        }
    }

    @Override
    public void handleSignedChatCommand(ServerboundChatCommandSignedPacket p_338604_) {
        Optional<LastSeenMessages> optional = this.unpackAndApplyLastSeen(p_338604_.lastSeenMessages());
        if (!optional.isEmpty()) {
            this.tryHandleChat(p_338604_.command(), () -> {
                // CraftBukkit start - SPIGOT-7346: Prevent disconnected players from executing commands
                if (this.player.hasDisconnected()) {
                    return;
                }
                // CraftBukkit end
                this.performSignedChatCommand(p_338604_, optional.get());
                this.detectRateSpam("/" + p_338604_.command()); // Spigot
            }, true); // CraftBukkit - sync commands
        }
    }

    private void performSignedChatCommand(ServerboundChatCommandSignedPacket p_338593_, LastSeenMessages p_250484_) {
        // CraftBukkit start
        String command = "/" + p_338593_.command();
        if (org.spigotmc.SpigotConfig.logCommands) { // Paper - Add missing SpigotConfig logCommands check
            ServerGamePacketListenerImpl.LOGGER.info(this.player.getScoreboardName() + " issued server command: " + command);
        } // Paper - Add missing SpigotConfig logCommands check
        org.bukkit.event.player.PlayerCommandPreprocessEvent event = new org.bukkit.event.player.PlayerCommandPreprocessEvent(getCraftPlayer(), command, new LazyPlayerSet(server));
        command = event.getMessage().substring(1);

        // Paper start - Fix cancellation and message changing
        ParseResults<CommandSourceStack> parseresults = this.parseCommand(p_338593_.command());

        Map<String, PlayerChatMessage> map;
        try {
            // Always parse the original command to add to the chat chain
            map = this.collectSignedArguments(p_338593_, SignableCommand.of(parseresults), p_250484_);
        } catch (SignedMessageChain.DecodeException signedmessagechain$decodeexception) {
            this.handleMessageDecodeFailure(signedmessagechain$decodeexception);
            return;
        }

        if (event.isCancelled()) {
            // Only now are we actually good to return
            return;
        }

        // Remove signed parts if the command was changed
        if (!command.equals(p_338593_.command())) {
            parseresults = this.parseCommand(command);
            map = Collections.emptyMap();
        }
        // Paper end - Fix cancellation and message changing

        CommandSigningContext commandsigningcontext = new CommandSigningContext.SignedArguments(map);
        parseresults = Commands.mapSource(parseresults, p_301740_ -> p_301740_.withSigningContext(commandsigningcontext, this.chatMessageChain));
        this.server.getCommands().performCommand(parseresults, command); // CraftBukkit
    }

    public void handleMessageDecodeFailure(SignedMessageChain.DecodeException p_252068_) {
        LOGGER.warn("Failed to update secure chat state for {}: '{}'", this.player.getGameProfile().getName(), p_252068_.getComponent().getString());
        this.player.sendSystemMessage(p_252068_.getComponent().copy().withStyle(ChatFormatting.RED));
    }

    private <S> Map<String, PlayerChatMessage> collectSignedArguments(
        ServerboundChatCommandSignedPacket p_338222_, SignableCommand<S> p_250039_, LastSeenMessages p_249207_
    ) throws SignedMessageChain.DecodeException {
        List<ArgumentSignatures.Entry> list = p_338222_.argumentSignatures().entries();
        List<SignableCommand.Argument<S>> list1 = p_250039_.arguments();
        if (list.isEmpty()) {
            return this.collectUnsignedArguments(list1);
        } else {
            Map<String, PlayerChatMessage> map = new Object2ObjectOpenHashMap<>();

            for (ArgumentSignatures.Entry argumentsignatures$entry : list) {
                SignableCommand.Argument<S> argument = p_250039_.getArgument(argumentsignatures$entry.name());
                if (argument == null) {
                    this.signedMessageDecoder.setChainBroken();
                    throw createSignedArgumentMismatchException(p_338222_.command(), list, list1);
                }

                SignedMessageBody signedmessagebody = new SignedMessageBody(argument.value(), p_338222_.timeStamp(), p_338222_.salt(), p_249207_);
                map.put(argument.name(), this.signedMessageDecoder.unpack(argumentsignatures$entry.signature(), signedmessagebody));
            }

            for (SignableCommand.Argument<S> argument1 : list1) {
                if (!map.containsKey(argument1.name())) {
                    throw createSignedArgumentMismatchException(p_338222_.command(), list, list1);
                }
            }

            return map;
        }
    }

    private <S> Map<String, PlayerChatMessage> collectUnsignedArguments(List<SignableCommand.Argument<S>> p_338744_) throws SignedMessageChain.DecodeException {
        Map<String, PlayerChatMessage> map = new HashMap<>();

        for (SignableCommand.Argument<S> argument : p_338744_) {
            SignedMessageBody signedmessagebody = SignedMessageBody.unsigned(argument.value());
            map.put(argument.name(), this.signedMessageDecoder.unpack(null, signedmessagebody));
        }

        return map;
    }

    private static <S> SignedMessageChain.DecodeException createSignedArgumentMismatchException(
        String p_338499_, List<ArgumentSignatures.Entry> p_338388_, List<SignableCommand.Argument<S>> p_338708_
    ) {
        String s = p_338388_.stream().map(ArgumentSignatures.Entry::name).collect(Collectors.joining(", "));
        String s1 = p_338708_.stream().map(SignableCommand.Argument::name).collect(Collectors.joining(", "));
        LOGGER.error("Signed command mismatch between server and client ('{}'): got [{}] from client, but expected [{}]", p_338499_, s, s1);
        return new SignedMessageChain.DecodeException(INVALID_COMMAND_SIGNATURE);
    }

    private ParseResults<CommandSourceStack> parseCommand(String p_242938_) {
        CommandDispatcher<CommandSourceStack> commanddispatcher = this.server.getCommands().getDispatcher();
        return commanddispatcher.parse(p_242938_, this.player.createCommandSourceStack());
    }

    public void tryHandleChat(String p_338775_, Runnable p_338235_, boolean sync) { // CraftBukkit
        if (isChatMessageIllegal(p_338775_)) {
            this.disconnectAsync(Component.translatable("multiplayer.disconnect.illegal_characters"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_CHARACTERS); // Paper
        } else if (this.player.isRemoved() || this.player.getChatVisibility() == ChatVisiblity.HIDDEN) { // CraftBukkit - dead men tell no tales
            this.send(new ClientboundSystemChatPacket(Component.translatable("chat.disabled.options").withStyle(ChatFormatting.RED), false));
        } else {
            this.player.resetLastActionTime();
            // CraftBukkit start
            if (sync) {
                this.server.execute(p_338235_);
            } else {
                p_338235_.run();
            }
            // CraftBukkit end
        }
    }

    private Optional<LastSeenMessages> unpackAndApplyLastSeen(LastSeenMessages.Update p_249673_) {
        synchronized (this.lastSeenMessages) {
            Optional<LastSeenMessages> optional = this.lastSeenMessages.applyUpdate(p_249673_);
            if (optional.isEmpty()) {
                LOGGER.warn("Failed to validate message acknowledgements from {}", this.player.getName().getString());
                this.disconnectAsync(CHAT_VALIDATION_FAILED, org.bukkit.event.player.PlayerKickEvent.Cause.CHAT_VALIDATION_FAILED); // Paper - kick event causes
            }

            return optional;
        }
    }

    public static boolean isChatMessageIllegal(String p_215215_) {
        for (int i = 0; i < p_215215_.length(); i++) {
            if (!StringUtil.isAllowedChatCharacter(p_215215_.charAt(i))) {
                return true;
            }
        }

        return false;
    }

    // CraftBukkit start - add method
    public void chat(String s, PlayerChatMessage original, boolean async) {
        if (s.isEmpty() || this.player.getChatVisibility() == ChatVisiblity.HIDDEN) {
            return;
        }
        net.minecraft.network.chat.OutgoingChatMessage outgoing = net.minecraft.network.chat.OutgoingChatMessage.create(original);

        if (false && !async && s.startsWith("/")) { // Paper - Don't handle commands in chat logic
            this.handleCommand(s);
        } else if (this.player.getChatVisibility() == ChatVisiblity.SYSTEM) {
            // Do nothing, this is coming from a plugin
        } else if (true) {
            if (!async && !org.bukkit.Bukkit.isPrimaryThread()) {
                org.spigotmc.AsyncCatcher.catchOp("Asynchronous player chat is not allowed here");
            }
            final ChatProcessor cp = new ChatProcessor(this.server, this.player, original, async);
            cp.process();
            // Paper end
        } else if (false) { // Paper
            org.bukkit.entity.Player player = this.getCraftPlayer();
            org.bukkit.event.player.AsyncPlayerChatEvent event = new org.bukkit.event.player.AsyncPlayerChatEvent(async, player, s, new LazyPlayerSet(server));
            String originalFormat = event.getFormat(), originalMessage = event.getMessage();
            this.cserver.getPluginManager().callEvent(event);

            if (org.bukkit.event.player.PlayerChatEvent.getHandlerList().getRegisteredListeners().length != 0) {
                // Evil plugins still listening to deprecated event
                final org.bukkit.event.player.PlayerChatEvent queueEvent = new org.bukkit.event.player.PlayerChatEvent(player, event.getMessage(), event.getFormat(), event.getRecipients());
                queueEvent.setCancelled(event.isCancelled());
                Waitable waitable = new Waitable() {
                    @Override
                    protected Object evaluate() {
                        org.bukkit.Bukkit.getPluginManager().callEvent(queueEvent);

                        if (queueEvent.isCancelled()) {
                            return null;
                        }

                        String message = String.format(queueEvent.getFormat(), queueEvent.getPlayer().getDisplayName(), queueEvent.getMessage());
                        if (((LazyPlayerSet) queueEvent.getRecipients()).isLazy()) {
                            if (!org.spigotmc.SpigotConfig.bungee && originalFormat.equals(queueEvent.getFormat()) && originalMessage.equals(queueEvent.getMessage()) && queueEvent.getPlayer().getName().equalsIgnoreCase(queueEvent.getPlayer().getDisplayName())) { // Spigot
                                ServerGamePacketListenerImpl.this.server.getPlayerList().broadcastChatMessage(original, ServerGamePacketListenerImpl.this.player, ChatType.bind(ChatType.CHAT, (Entity) ServerGamePacketListenerImpl.this.player));
                                return null;
                            }

                            for (ServerPlayer recipient : server.getPlayerList().players) {
                                recipient.getBukkitEntity().sendMessage(ServerGamePacketListenerImpl.this.player.getUUID(), message);
                            }
                        } else {
                            for (org.bukkit.entity.Player player : queueEvent.getRecipients()) {
                                player.sendMessage(ServerGamePacketListenerImpl.this.player.getUUID(), message);
                            }
                        }
                        ServerGamePacketListenerImpl.this.server.console.sendMessage(message);

                        return null;
                    }};
                if (async) {
                    server.processQueue.add(waitable);
                } else {
                    waitable.run();
                }
                try {
                    waitable.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // This is proper habit for java. If we aren't handling it, pass it on!
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new RuntimeException("Exception processing chat event", e.getCause());
                }
            } else {
                if (event.isCancelled()) {
                    return;
                }

                s = String.format(event.getFormat(), event.getPlayer().getDisplayName(), event.getMessage());
                if (((LazyPlayerSet) event.getRecipients()).isLazy()) {
                    if (!org.spigotmc.SpigotConfig.bungee && originalFormat.equals(event.getFormat()) && originalMessage.equals(event.getMessage()) && event.getPlayer().getName().equalsIgnoreCase(event.getPlayer().getDisplayName())) { // Spigot
                        ServerGamePacketListenerImpl.this.server.getPlayerList().broadcastChatMessage(original, ServerGamePacketListenerImpl.this.player, ChatType.bind(ChatType.CHAT, (Entity) ServerGamePacketListenerImpl.this.player));
                        return;
                    }

                    for (ServerPlayer recipient : server.getPlayerList().players) {
                        recipient.getBukkitEntity().sendMessage(ServerGamePacketListenerImpl.this.player.getUUID(), s);
                    }
                } else {
                    for (org.bukkit.entity.Player recipient : event.getRecipients()) {
                        recipient.sendMessage(ServerGamePacketListenerImpl.this.player.getUUID(), s);
                    }
                }
                server.console.sendMessage(s);
            }
        }
    }

    @Deprecated // Paper
    public void handleCommand(String s) { // Paper - private -> public
        // Paper start - Remove all this old duplicated logic
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        /*
        It should be noted that this represents the "legacy" command execution path.
        Api can call commands even if there is no additional context provided.
        This method should ONLY be used if you need to execute a command WITHOUT
        an actual player's input.
        */
        this.performUnsignedChatCommand(s);
        // Paper end
    }
    // CraftBukkit end

    public PlayerChatMessage getSignedMessage(ServerboundChatPacket p_251061_, LastSeenMessages p_250566_) throws SignedMessageChain.DecodeException {
        SignedMessageBody signedmessagebody = new SignedMessageBody(p_251061_.message(), p_251061_.timeStamp(), p_251061_.salt(), p_250566_);
        return this.signedMessageDecoder.unpack(p_251061_.signature(), signedmessagebody);
    }

    public void broadcastChatMessage(PlayerChatMessage p_243277_) {
        // CraftBukkit start
        String s = p_243277_.signedContent();
        if (s.isEmpty()) {
            LOGGER.warn(this.player.getScoreboardName() + " tried to send an empty message");
        } else if (getCraftPlayer().isConversing()) {
            final String conversationInput = s;
            this.server.processQueue.add(new Runnable() {
                @Override
                public void run() {
                    getCraftPlayer().acceptConversationInput(conversationInput);
                }
            });
        } else if (this.player.getChatVisibility() == ChatVisiblity.SYSTEM) { // Re-add "Command Only" flag check
            this.send(new ClientboundSystemChatPacket(Component.translatable("chat.cannotSend").withStyle(ChatFormatting.RED), false));
        } else {
            this.chat(s, p_243277_, true);
        }
        // CraftBukkit end
        this.detectRateSpam(s);
    }

    // Spigot start - spam exclusions
    private void detectRateSpam(String s) {
        // CraftBukkit start - replaced with thread safe throttle
        boolean counted = true;
        for ( String exclude : org.spigotmc.SpigotConfig.spamExclusions )
        {
            if ( exclude != null && s.startsWith( exclude ) )
            {
                counted = false;
                break;
            }
        }
        // Spigot end
        if (counted && this.chatSpamTickCount.addAndGet(20) > 200 && !this.server.getPlayerList().isOp(this.player.getGameProfile()) && !this.server.isSingleplayerOwner(this.player.getGameProfile())) { // Paper - exclude from SpigotConfig.spamExclusions
            // CraftBukkit end
            this.disconnectAsync(Component.translatable("disconnect.spam"), org.bukkit.event.player.PlayerKickEvent.Cause.SPAM); // Paper - kick event cause
        }

    }

    @Override
    public void handleChatAck(ServerboundChatAckPacket p_242387_) {
        synchronized (this.lastSeenMessages) {
            if (!this.lastSeenMessages.applyOffset(p_242387_.offset())) {
                LOGGER.warn("Failed to validate message acknowledgements from {}", this.player.getName().getString());
                this.disconnectAsync(CHAT_VALIDATION_FAILED, org.bukkit.event.player.PlayerKickEvent.Cause.CHAT_VALIDATION_FAILED); // Paper - kick event causes
            }
        }
    }

    @Override
    public void handleAnimate(ServerboundSwingPacket p_9926_) {
        PacketUtils.ensureRunningOnSameThread(p_9926_, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        // CraftBukkit start - Raytrace to look for 'rogue armswings'
        float f1 = this.player.getXRot();
        float f2 = this.player.getYRot();
        double d0 = this.player.getX();
        double d1 = this.player.getY() + (double) this.player.getEyeHeight();
        double d2 = this.player.getZ();
        org.bukkit.Location origin = new org.bukkit.Location(this.player.level().getWorld(), d0, d1, d2, f2, f1);
        double d3 = Math.max(player.blockInteractionRange(), player.entityInteractionRange());
        // SPIGOT-5607: Only call interact event if no block or entity is being clicked. Use bukkit ray trace method, because it handles blocks and entities at the same time
        // SPIGOT-7429: Make sure to call PlayerInteractEvent for spectators and non-pickable entities
        org.bukkit.util.RayTraceResult result = this.player.level().getWorld().rayTrace(origin, origin.getDirection(), d3, org.bukkit.FluidCollisionMode.NEVER, false, 0.0, new Predicate<org.bukkit.entity.Entity>() {
            @Override
            public boolean test(org.bukkit.entity.Entity entity) { // Paper - Call interact event; change raySize from 0.1 to 0.0
                Entity handle = ((CraftEntity) entity).getHandle();
                return entity != ServerGamePacketListenerImpl.this.player.getBukkitEntity() && ServerGamePacketListenerImpl.this.player.getBukkitEntity().canSee(entity) && !handle.isSpectator() && handle.isPickable() && !handle.isPassengerOfSameVehicle(player);
            }
        });
        if (result == null) {
            CraftEventFactory.callPlayerInteractEvent(this.player, org.bukkit.event.block.Action.LEFT_CLICK_AIR, this.player.getInventory().getSelected(), InteractionHand.MAIN_HAND);
        } else { // Paper start - Call interact event
            GameType gameType = this.player.gameMode.getGameModeForPlayer();
            if (gameType == GameType.ADVENTURE && result.getHitBlock() != null) {
                CraftEventFactory.callPlayerInteractEvent(this.player, org.bukkit.event.block.Action.LEFT_CLICK_BLOCK, ((org.bukkit.craftbukkit.block.CraftBlock) result.getHitBlock()).getPosition(), org.bukkit.craftbukkit.block.CraftBlock.blockFaceToNotch(result.getHitBlockFace()), this.player.getInventory().getSelected(), InteractionHand.MAIN_HAND);
            } else if (gameType != GameType.CREATIVE && result.getHitEntity() != null && origin.toVector().distanceSquared(result.getHitPosition()) > this.player.entityInteractionRange() * this.player.entityInteractionRange()) {
                CraftEventFactory.callPlayerInteractEvent(this.player, org.bukkit.event.block.Action.LEFT_CLICK_AIR, this.player.getInventory().getSelected(), InteractionHand.MAIN_HAND);
            }
        } // Paper end - Call interact event
        // Arm swing animation
        io.papermc.paper.event.player.PlayerArmSwingEvent event = new io.papermc.paper.event.player.PlayerArmSwingEvent(this.getCraftPlayer(), org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(p_9926_.getHand())); // Paper - Add PlayerArmSwingEvent
        this.cserver.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        // CraftBukkit end
        this.player.swing(p_9926_.getHand());
    }

    @Override
    public void handlePlayerCommand(ServerboundPlayerCommandPacket p_9891_) {
        PacketUtils.ensureRunningOnSameThread(p_9891_, this, this.player.serverLevel());
        // CraftBukkit start
        if (this.player.isRemoved()) return;
        switch (p_9891_.getAction()) {
            case PRESS_SHIFT_KEY:
            case RELEASE_SHIFT_KEY:
                org.bukkit.event.player.PlayerToggleSneakEvent event = new org.bukkit.event.player.PlayerToggleSneakEvent(this.getCraftPlayer(), p_9891_.getAction() == ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY);
                this.cserver.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return;
                }
                break;
            case START_SPRINTING:
            case STOP_SPRINTING:
                org.bukkit.event.player.PlayerToggleSprintEvent e2 = new org.bukkit.event.player.PlayerToggleSprintEvent(this.getCraftPlayer(), p_9891_.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING);
                this.cserver.getPluginManager().callEvent(e2);
                if (e2.isCancelled()) {
                    return;
                }
                break;
        }
        // CraftBukkit end
        this.player.resetLastActionTime();
        switch (p_9891_.getAction()) {
            case PRESS_SHIFT_KEY:
                this.player.setShiftKeyDown(true);

                // Paper start - Add option to make parrots stay
                if (this.player.level().paperConfig().entities.behavior.parrotsAreUnaffectedByPlayerMovement) {
                    this.player.removeEntitiesOnShoulder();
                }
                // Paper end - Add option to make parrots stay

                break;
            case RELEASE_SHIFT_KEY:
                this.player.setShiftKeyDown(false);
                break;
            case START_SPRINTING:
                this.player.setSprinting(true);
                break;
            case STOP_SPRINTING:
                this.player.setSprinting(false);
                break;
            case STOP_SLEEPING:
                if (this.player.isSleeping()) {
                    this.player.stopSleepInBed(false, true);
                    this.awaitingPositionFromClient = this.player.position();
                }
                break;
            case START_RIDING_JUMP:
                if (this.player.getControlledVehicle() instanceof PlayerRideableJumping playerrideablejumping1) {
                    int i = p_9891_.getData();
                    if (playerrideablejumping1.canJump() && i > 0) {
                        playerrideablejumping1.handleStartJump(i);
                    }
                }
                break;
            case STOP_RIDING_JUMP:
                if (this.player.getControlledVehicle() instanceof PlayerRideableJumping playerrideablejumping) {
                    playerrideablejumping.handleStopJump();
                }
                break;
            case OPEN_INVENTORY:
                if (this.player.getVehicle() instanceof HasCustomInventoryScreen hascustominventoryscreen) {
                    hascustominventoryscreen.openCustomInventoryScreen(this.player);
                }
                break;
            case START_FALL_FLYING:
                if (!this.player.tryToStartFallFlying()) {
                    this.player.stopFallFlying();
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid client command!");
        }
    }

    public void addPendingMessage(PlayerChatMessage p_242439_) {
        MessageSignature messagesignature = p_242439_.signature();
        if (messagesignature != null) {
            this.messageSignatureCache.push(p_242439_.signedBody(), p_242439_.signature());
            int i;
            synchronized (this.lastSeenMessages) {
                this.lastSeenMessages.addPending(messagesignature);
                i = this.lastSeenMessages.trackedMessagesCount();
            }

            if (i > 4096) {
                this.disconnectAsync(Component.translatable("multiplayer.disconnect.too_many_pending_chats"), org.bukkit.event.player.PlayerKickEvent.Cause.TOO_MANY_PENDING_CHATS); // Paper - kick event cause
            }
        }
    }

    public void sendPlayerChatMessage(PlayerChatMessage p_250321_, ChatType.Bound p_250910_) {
        // CraftBukkit start - SPIGOT-7262: if hidden we have to send as disguised message. Query whether we should send at all (but changing this may not be expected).
        if (!getCraftPlayer().canSeePlayer(p_250321_.link().sender())) {
            sendDisguisedChatMessage(p_250321_.decoratedContent(), p_250910_);
            return;
        }
        // CraftBukkit end
        // Paper start - Ensure that client receives chat packets in the same order that we add into the message signature cache
        synchronized (this.messageSignatureCache) {
        this.send(
            new ClientboundPlayerChatPacket(
                p_250321_.link().sender(),
                p_250321_.link().index(),
                p_250321_.signature(),
                p_250321_.signedBody().pack(this.messageSignatureCache),
                p_250321_.unsignedContent(),
                p_250321_.filterMask(),
                p_250910_
            )
        );
        this.addPendingMessage(p_250321_);
        }
        // Paper end - Ensure that client receives chat packets in the same order that we add into the message signature cache
    }

    public void sendDisguisedChatMessage(Component p_251804_, ChatType.Bound p_250040_) {
        this.send(new ClientboundDisguisedChatPacket(p_251804_, p_250040_));
    }

    public SocketAddress getRemoteAddress() {
        return this.connection.getRemoteAddress();
    }

    // Spigot Start
    public SocketAddress getRawAddress()
    {
        // Paper start - Unix domain socket support; this can be nullable in the case of a Unix domain socket, so if it is, fake something
        if (connection.channel.remoteAddress() == null) {
            return new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0);
        }
        // Paper end - Unix domain socket support
        return this.connection.channel.remoteAddress();
    }
    // Spigot End

    public void switchToConfig() {
        this.waitingForSwitchToConfig = true;
        this.removePlayerFromWorld();
        this.send(ClientboundStartConfigurationPacket.INSTANCE);
        this.connection.setupOutboundProtocol(ConfigurationProtocols.CLIENTBOUND);
    }

    @Override
    public void handlePingRequest(ServerboundPingRequestPacket p_320356_) {
        this.connection.send(new ClientboundPongResponsePacket(p_320356_.getTime()));
    }

    @Override
    public void handleInteract(ServerboundInteractPacket p_9866_) {
        PacketUtils.ensureRunningOnSameThread(p_9866_, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (BanItem.check(player)) return; // Mohist
        final ServerLevel serverlevel = this.player.serverLevel();
        final Entity entity = p_9866_.getTarget(serverlevel);
        // Spigot Start
        if ( entity == player && !player.isSpectator() )
        {
            disconnect( Component.literal( "Cannot interact with self!" ), org.bukkit.event.player.PlayerKickEvent.Cause.SELF_INTERACTION ); // Paper - kick event cause
            return;
        }
        // Spigot End
        this.player.resetLastActionTime();
        this.player.setShiftKeyDown(p_9866_.isUsingSecondaryAction());
        if (entity != null) {
            if (!serverlevel.getWorldBorder().isWithinBounds(entity.blockPosition())) {
                return;
            }

            AABB aabb = entity.getBoundingBox();
            if (this.player.canInteractWithEntity(aabb, io.papermc.paper.configuration.GlobalConfiguration.get().misc.clientInteractionLeniencyDistance.or(1.0D))) { // Paper - configurable lenience value for interact range
                p_9866_.dispatch(
                    new ServerboundInteractPacket.Handler() {
                        private void performInteraction(InteractionHand p_143679_, ServerGamePacketListenerImpl.EntityInteraction p_143680_, org.bukkit.event.player.PlayerInteractEntityEvent event) { // CraftBukkit
                            ItemStack itemstack = ServerGamePacketListenerImpl.this.player.getItemInHand(p_143679_);
                            if (itemstack.isItemEnabled(serverlevel.enabledFeatures())) {
                                ItemStack itemstack1 = itemstack.copy();
                                // CraftBukkit start
                                ItemStack itemInHand = ServerGamePacketListenerImpl.this.player.getItemInHand(p_143679_);
                                boolean triggerLeashUpdate = itemInHand != null && itemInHand.getItem() == Items.LEAD && entity instanceof net.minecraft.world.entity.Mob;
                                Item origItem = player.getInventory().getSelected() == null ? null : player.getInventory().getSelected().getItem();
                                cserver.getPluginManager().callEvent(event);

                                player.processClick(p_143679_); // Purpur

                                // Entity in bucket - SPIGOT-4048 and SPIGOT-6859a
                                if ((entity instanceof net.minecraft.world.entity.animal.Bucketable && entity instanceof LivingEntity && origItem != null && origItem.asItem() == Items.WATER_BUCKET) && (event.isCancelled() || player.getInventory().getSelected() == null || player.getInventory().getSelected().getItem() != origItem)) {
                                    entity.resendPossiblyDesyncedEntityData(ServerGamePacketListenerImpl.this.player); // Paper - The entire mob gets deleted, so resend it
                                    player.containerMenu.sendAllDataToRemote();
                                }
                                if (triggerLeashUpdate && (event.isCancelled() || player.getInventory().getSelected() == null || player.getInventory().getSelected().getItem() != origItem)) {
                                    // Refresh the current leash state
                                    send(new net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket(entity, ((net.minecraft.world.entity.Mob) entity).getLeashHolder()));
                                }
                                if (event.isCancelled() || player.getInventory().getSelected() == null || player.getInventory().getSelected().getItem() != origItem) {
                                    // Refresh the current entity metadata
                                    entity.refreshEntityData(player);
                                    // SPIGOT-7136 - Allays
                                    if (entity instanceof net.minecraft.world.entity.animal.allay.Allay || entity instanceof net.minecraft.world.entity.animal.horse.AbstractHorse) { // Paper - Fix horse armor desync
                                        send(new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(entity.getId(), java.util.Arrays.stream(net.minecraft.world.entity.EquipmentSlot.values()).map((slot) -> com.mojang.datafixers.util.Pair.of(slot, ((net.minecraft.world.entity.LivingEntity) entity).getItemBySlot(slot).copy())).collect(Collectors.toList()), true)); // Paper - sanitize
                                    }

                                    ServerGamePacketListenerImpl.this.player.containerMenu.sendAllDataToRemote(); // Paper - fix slot desync - always refresh player inventory
                                }
                                if (event.isCancelled()) {
                                    return;
                                }
                                // CraftBukkit end
                                InteractionResult interactionresult = p_143680_.run(ServerGamePacketListenerImpl.this.player, entity, p_143679_);

                                // CraftBukkit start
                                if (!itemInHand.isEmpty() && itemInHand.getCount() <= -1) {
                                    player.containerMenu.sendAllDataToRemote();
                                }
                                // CraftBukkit end

                                if (interactionresult.consumesAction()) {
                                    CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY
                                        .trigger(
                                            ServerGamePacketListenerImpl.this.player,
                                            interactionresult.indicateItemUse() ? itemstack1 : ItemStack.EMPTY,
                                            entity
                                        );
                                    if (interactionresult.shouldSwing()) {
                                        ServerGamePacketListenerImpl.this.player.swing(p_143679_, true);
                                    }
                                }
                            }
                        }

                        @Override
                        public void onInteraction(InteractionHand p_143677_) {
                            this.performInteraction(p_143677_, Player::interactOn, new org.bukkit.event.player.PlayerInteractEntityEvent(getCraftPlayer(), entity.getBukkitEntity(), (p_143677_ == InteractionHand.OFF_HAND) ? org.bukkit.inventory.EquipmentSlot.OFF_HAND : org.bukkit.inventory.EquipmentSlot.HAND)); // CraftBukkit
                        }

                        @Override
                        public void onInteraction(InteractionHand p_143682_, Vec3 p_143683_) {
                            this.performInteraction(p_143682_, (p_143686_, p_143687_, p_143688_) -> {
                                InteractionResult onInteractEntityAtResult = net.neoforged.neoforge.common.CommonHooks.onInteractEntityAt(player, entity, p_143683_, p_143682_);
                                if (onInteractEntityAtResult != null) return onInteractEntityAtResult;
                                return p_143687_.interactAt(p_143686_, p_143683_, p_143688_);
                            }, new org.bukkit.event.player.PlayerInteractAtEntityEvent(getCraftPlayer(), entity.getBukkitEntity(), new org.bukkit.util.Vector(p_143683_.x, p_143683_.y, p_143683_.z), (p_143682_ == InteractionHand.OFF_HAND) ? org.bukkit.inventory.EquipmentSlot.OFF_HAND : org.bukkit.inventory.EquipmentSlot.HAND)); // CraftBukkit
                        }

                        @Override
                        public void onAttack() {
                            label23:
                            if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb) && (entity != ServerGamePacketListenerImpl.this.player || player.isSpectator())) {
                                if (entity instanceof AbstractArrow abstractarrow && !abstractarrow.isAttackable()) {
                                    break label23;
                                }

                                ItemStack itemstack = ServerGamePacketListenerImpl.this.player.getItemInHand(InteractionHand.MAIN_HAND);
                                if (!itemstack.isItemEnabled(serverlevel.enabledFeatures())) {
                                    return;
                                }

                                ServerGamePacketListenerImpl.this.player.attack(entity);
                                // CraftBukkit start
                                if (!itemstack.isEmpty() && itemstack.getCount() <= -1) {
                                    player.containerMenu.sendAllDataToRemote();
                                }
                                // CraftBukkit end
                                return;
                            }

                            ServerGamePacketListenerImpl.this.disconnect(Component.translatable("multiplayer.disconnect.invalid_entity_attacked"), org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_ENTITY_ATTACKED); // Paper - add cause
                            ServerGamePacketListenerImpl.LOGGER
                                .warn("Player {} tried to attack an invalid entity", ServerGamePacketListenerImpl.this.player.getName().getString());
                        }
                    }
                );
            }
        }
        // Paper start - PlayerUseUnknownEntityEvent
        else {
            p_9866_.dispatch(new net.minecraft.network.protocol.game.ServerboundInteractPacket.Handler() {
                @Override
                public void onInteraction(net.minecraft.world.InteractionHand hand) {
                    CraftEventFactory.callPlayerUseUnknownEntityEvent(ServerGamePacketListenerImpl.this.player, p_9866_, hand, null);
                }

                @Override
                public void onInteraction(net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 pos) {
                    CraftEventFactory.callPlayerUseUnknownEntityEvent(ServerGamePacketListenerImpl.this.player, p_9866_, hand, pos);
                }

                @Override
                public void onAttack() {
                    CraftEventFactory.callPlayerUseUnknownEntityEvent(ServerGamePacketListenerImpl.this.player, p_9866_, net.minecraft.world.InteractionHand.MAIN_HAND, null);
                }
            });
        }
        // Paper end - PlayerUseUnknownEntityEvent
    }

    @Override
    public void handleClientCommand(ServerboundClientCommandPacket p_9843_) {
        PacketUtils.ensureRunningOnSameThread(p_9843_, this, this.player.serverLevel());
        this.player.resetLastActionTime();
        ServerboundClientCommandPacket.Action serverboundclientcommandpacket$action = p_9843_.getAction();
        switch (serverboundclientcommandpacket$action) {
            case PERFORM_RESPAWN:
                if (this.player.wonGame) {
                    this.player.wonGame = false;
                    this.player = this.server.getPlayerList().respawn(this.player, true, Entity.RemovalReason.CHANGED_DIMENSION, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.END_PORTAL); // CraftBukkit
                    CriteriaTriggers.CHANGED_DIMENSION.trigger(this.player, Level.END, Level.OVERWORLD);
                } else {
                    if (this.player.getHealth() > 0.0F) {
                        return;
                    }
                    this.player = this.server.getPlayerList().respawn(this.player, false, Entity.RemovalReason.KILLED, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH); // CraftBukkit
                    if (this.server.isHardcore()) {
                        this.player.setGameMode(GameType.SPECTATOR, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.HARDCORE_DEATH, null); // Paper - Expand PlayerGameModeChangeEvent
                        this.player.level().getGameRules().getRule(GameRules.RULE_SPECTATORSGENERATECHUNKS).set(false, this.player.serverLevel()); // CraftBukkit - per-world
                    }
                }
                break;
            case REQUEST_STATS:
                this.player.getStats().sendStats(this.player);
        }
    }

    @Override
    public void handleContainerClose(ServerboundContainerClosePacket p_9858_) {
        PacketUtils.ensureRunningOnSameThread(p_9858_, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.doCloseContainer();
    }

    public void handleContainerClose(ServerboundContainerClosePacket packet, org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        InventoryOwner.setClose$Reason(reason);
        handleContainerClose(packet);
    }

    @Override
    public void handleContainerClick(ServerboundContainerClickPacket p_9856_) {
        PacketUtils.ensureRunningOnSameThread(p_9856_, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        if (this.player.containerMenu.containerId == p_9856_.getContainerId() && this.player.containerMenu.stillValid(this.player)) { // CraftBukkit
            boolean cancelled = this.player.isSpectator(); // CraftBukkit - see below if
            if (false/*this.player.isSpectator()*/) { // CraftBukkit
                this.player.containerMenu.sendAllDataToRemote();
            } else if (!this.player.containerMenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                int i = p_9856_.getSlotNum();
                if (!this.player.containerMenu.isValidSlotIndex(i)) {
                    LOGGER.debug(
                        "Player {} clicked invalid slot index: {}, available slots: {}", this.player.getName(), i, this.player.containerMenu.slots.size()
                    );
                } else {
                    boolean flag = p_9856_.getStateId() != this.player.containerMenu.getStateId();
                    this.player.containerMenu.suppressRemoteUpdates();

                    // CraftBukkit start - Call InventoryClickEvent
                    if (p_9856_.getSlotNum() < -1 && p_9856_.getSlotNum() != -999) {
                        return;
                    }

                    this.player.containerMenu.containerOwner = this.player;
                    InventoryView inventoryView = this.player.containerMenu.getBukkitView();
                    InventoryType.SlotType type = inventoryView.getSlotType(p_9856_.getSlotNum());
                    InventoryClickEvent event;
                    ClickType click = ClickType.UNKNOWN;
                    InventoryAction action = InventoryAction.UNKNOWN;

                    ItemStack itemstack = ItemStack.EMPTY;

                    switch (p_9856_.getClickType()) {
                        case PICKUP:
                            if (p_9856_.getButtonNum() == 0) {
                                click = org.bukkit.event.inventory.ClickType.LEFT;
                            } else if (p_9856_.getButtonNum() == 1) {
                                click = org.bukkit.event.inventory.ClickType.RIGHT;
                            }
                            if (p_9856_.getButtonNum() == 0 || p_9856_.getButtonNum() == 1) {
                                action = org.bukkit.event.inventory.InventoryAction.NOTHING; // Don't want to repeat ourselves
                                if (p_9856_.getSlotNum() == -999) {
                                    if (!player.containerMenu.getCarried().isEmpty()) {
                                        action = p_9856_.getButtonNum() == 0 ? org.bukkit.event.inventory.InventoryAction.DROP_ALL_CURSOR : org.bukkit.event.inventory.InventoryAction.DROP_ONE_CURSOR;
                                    }
                                } else if (p_9856_.getSlotNum() < 0)  {
                                    action = org.bukkit.event.inventory.InventoryAction.NOTHING;
                                } else {
                                    net.minecraft.world.inventory.Slot slot = this.player.containerMenu.getSlot(p_9856_.getSlotNum());
                                    if (slot != null) {
                                        ItemStack clickedItem = slot.getItem();
                                        ItemStack cursor = player.containerMenu.getCarried();
                                        if (clickedItem.isEmpty()) {
                                            if (!cursor.isEmpty()) {
                                                action = p_9856_.getButtonNum() == 0 ? org.bukkit.event.inventory.InventoryAction.PLACE_ALL : org.bukkit.event.inventory.InventoryAction.PLACE_ONE;
                                            }
                                        } else if (slot.mayPickup(player)) {
                                            if (cursor.isEmpty()) {
                                                action = p_9856_.getButtonNum() == 0 ? org.bukkit.event.inventory.InventoryAction.PICKUP_ALL : org.bukkit.event.inventory.InventoryAction.PICKUP_HALF;
                                            } else if (slot.mayPlace(cursor)) {
                                                if (ItemStack.isSameItemSameComponents(clickedItem, cursor)) {
                                                    int toPlace = p_9856_.getButtonNum() == 0 ? cursor.getCount() : 1;
                                                    toPlace = Math.min(toPlace, clickedItem.getMaxStackSize() - clickedItem.getCount());
                                                    toPlace = Math.min(toPlace, slot.container.getMaxStackSize() - clickedItem.getCount());
                                                    if (toPlace == 1) {
                                                        action = org.bukkit.event.inventory.InventoryAction.PLACE_ONE;
                                                    } else if (toPlace == cursor.getCount()) {
                                                        action = org.bukkit.event.inventory.InventoryAction.PLACE_ALL;
                                                    } else if (toPlace < 0) {
                                                        action = toPlace != -1 ? org.bukkit.event.inventory.InventoryAction.PICKUP_SOME : org.bukkit.event.inventory.InventoryAction.PICKUP_ONE; // this happens with oversized stacks
                                                    } else if (toPlace != 0) {
                                                        action = org.bukkit.event.inventory.InventoryAction.PLACE_SOME;
                                                    }
                                                } else if (cursor.getCount() <= slot.getMaxStackSize()) {
                                                    action = org.bukkit.event.inventory.InventoryAction.SWAP_WITH_CURSOR;
                                                }
                                            } else if (ItemStack.isSameItemSameComponents(cursor, clickedItem)) {
                                                if (clickedItem.getCount() >= 0) {
                                                    if (clickedItem.getCount() + cursor.getCount() <= cursor.getMaxStackSize()) {
                                                        // As of 1.5, this is result slots only
                                                        action = org.bukkit.event.inventory.InventoryAction.PICKUP_ALL;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            break;
                        // TODO check on updates
                        case QUICK_MOVE:
                            if (p_9856_.getButtonNum() == 0) {
                                click = org.bukkit.event.inventory.ClickType.SHIFT_LEFT;
                            } else if (p_9856_.getButtonNum() == 1) {
                                click = org.bukkit.event.inventory.ClickType.SHIFT_RIGHT;
                            }
                            if (p_9856_.getButtonNum() == 0 || p_9856_.getButtonNum() == 1) {
                                if (p_9856_.getSlotNum() < 0) {
                                    action = org.bukkit.event.inventory.InventoryAction.NOTHING;
                                } else {
                                    net.minecraft.world.inventory.Slot slot = this.player.containerMenu.getSlot(p_9856_.getSlotNum());
                                    if (slot != null && slot.mayPickup(this.player) && slot.hasItem()) {
                                        action = org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY;
                                    } else {
                                        action = org.bukkit.event.inventory.InventoryAction.NOTHING;
                                    }
                                }
                            }
                            break;
                        case SWAP:
                            if ((p_9856_.getButtonNum() >= 0 && p_9856_.getButtonNum() < 9) || p_9856_.getButtonNum() == 40) {
                                // Paper start - Add slot sanity checks to container clicks
                                if (p_9856_.getSlotNum() < 0) {
                                    action = InventoryAction.NOTHING;
                                    break;
                                }
                                // Paper end - Add slot sanity checks to container clicks
                                click = (p_9856_.getButtonNum() == 40) ? org.bukkit.event.inventory.ClickType.SWAP_OFFHAND : org.bukkit.event.inventory.ClickType.NUMBER_KEY;
                                net.minecraft.world.inventory.Slot clickedSlot = this.player.containerMenu.getSlot(p_9856_.getSlotNum());
                                if (clickedSlot.mayPickup(player)) {
                                    ItemStack hotbar = this.player.getInventory().getItem(p_9856_.getButtonNum());
                                    if ((!hotbar.isEmpty() && clickedSlot.mayPlace(hotbar)) || (hotbar.isEmpty() && clickedSlot.hasItem())) { // Paper - modernify this logic (no such thing as a "hotbar move and readd"
                                        action = org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP;
                                    } else {
                                        action = org.bukkit.event.inventory.InventoryAction.NOTHING;
                                    }
                                } else {
                                    action = org.bukkit.event.inventory.InventoryAction.NOTHING;
                                }
                            }
                            break;
                        case CLONE:
                            if (p_9856_.getButtonNum() == 2) {
                                click = org.bukkit.event.inventory.ClickType.MIDDLE;
                                if (p_9856_.getSlotNum() < 0) {
                                    action = org.bukkit.event.inventory.InventoryAction.NOTHING;
                                } else {
                                    net.minecraft.world.inventory.Slot slot = this.player.containerMenu.getSlot(p_9856_.getSlotNum());
                                    if (slot != null && slot.hasItem() && player.getAbilities().instabuild && player.containerMenu.getCarried().isEmpty()) {
                                        action = org.bukkit.event.inventory.InventoryAction.CLONE_STACK;
                                    } else {
                                        action = org.bukkit.event.inventory.InventoryAction.NOTHING;
                                    }
                                }
                            } else {
                                click = org.bukkit.event.inventory.ClickType.UNKNOWN;
                                action = org.bukkit.event.inventory.InventoryAction.UNKNOWN;
                            }
                            break;
                        case THROW:
                            if (p_9856_.getSlotNum() >= 0) {
                                if (p_9856_.getButtonNum() == 0) {
                                    click = org.bukkit.event.inventory.ClickType.DROP;
                                    net.minecraft.world.inventory.Slot slot = this.player.containerMenu.getSlot(p_9856_.getSlotNum());
                                    if (slot != null && slot.hasItem() && slot.mayPickup(player) && !slot.getItem().isEmpty() && slot.getItem().getItem() != Item.byBlock(Blocks.AIR)) {
                                        action = org.bukkit.event.inventory.InventoryAction.DROP_ONE_SLOT;
                                    } else {
                                        action = org.bukkit.event.inventory.InventoryAction.NOTHING;
                                    }
                                } else if (p_9856_.getButtonNum() == 1) {
                                    click = org.bukkit.event.inventory.ClickType.CONTROL_DROP;
                                    net.minecraft.world.inventory.Slot slot = this.player.containerMenu.getSlot(p_9856_.getSlotNum());
                                    if (slot != null && slot.hasItem() && slot.mayPickup(player) && !slot.getItem().isEmpty() && slot.getItem().getItem() != Item.byBlock(Blocks.AIR)) {
                                        action = org.bukkit.event.inventory.InventoryAction.DROP_ALL_SLOT;
                                    } else {
                                        action = org.bukkit.event.inventory.InventoryAction.NOTHING;
                                    }
                                }
                            } else {
                                // Sane default (because this happens when they are holding nothing. Don't ask why.)
                                click = org.bukkit.event.inventory.ClickType.LEFT;
                                if (p_9856_.getButtonNum() == 1) {
                                    click = org.bukkit.event.inventory.ClickType.RIGHT;
                                }
                                action = org.bukkit.event.inventory.InventoryAction.NOTHING;
                            }
                            break;
                        case QUICK_CRAFT:
                            // Paper start - Fix CraftBukkit drag system
                            AbstractContainerMenu containerMenu = this.player.containerMenu;
                            int currentStatus = this.player.containerMenu.quickcraftStatus;
                            int newStatus = AbstractContainerMenu.getQuickcraftHeader(p_9856_.getButtonNum());
                            if ((currentStatus != 1 || newStatus != 2 && currentStatus != newStatus)) {
                            } else if (containerMenu.getCarried().isEmpty()) {
                            } else if (newStatus == 0) {
                            } else if (newStatus == 1) {
                            } else if (newStatus == 2) {
                                if (!this.player.containerMenu.quickcraftSlots.isEmpty()) {
                                    if (this.player.containerMenu.quickcraftSlots.size() == 1) {
                                        int index = containerMenu.quickcraftSlots.iterator().next().index;
                                        containerMenu.resetQuickCraft();
                                        this.handleContainerClick(new ServerboundContainerClickPacket(p_9856_.getContainerId(), p_9856_.getStateId(), index, containerMenu.quickcraftType, net.minecraft.world.inventory.ClickType.PICKUP, p_9856_.getCarriedItem(), p_9856_.getChangedSlots()));
                                        return;
                                    }
                                }
                            }
                            // Paper end - Fix CraftBukkit drag system
                            this.player.containerMenu.clicked(p_9856_.getSlotNum(), p_9856_.getButtonNum(), p_9856_.getClickType(), this.player);
                            break;
                        case PICKUP_ALL:
                            click = org.bukkit.event.inventory.ClickType.DOUBLE_CLICK;
                            action = org.bukkit.event.inventory.InventoryAction.NOTHING;
                            if (p_9856_.getSlotNum() >= 0 && !this.player.containerMenu.getCarried().isEmpty()) {
                                ItemStack cursor = this.player.containerMenu.getCarried();
                                action = org.bukkit.event.inventory.InventoryAction.NOTHING;
                                // Quick check for if we have any of the item
                                if (inventoryView.getTopInventory().contains(CraftItemType.minecraftToBukkit(cursor.getItem())) || inventoryView.getBottomInventory().contains(CraftItemType.minecraftToBukkit(cursor.getItem()))) {
                                    action = org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR;
                                }
                            }
                            break;
                        default:
                            break;
                    }
                    if (p_9856_.getClickType() != net.minecraft.world.inventory.ClickType.QUICK_CRAFT) {
                        if (click == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
                            event = new InventoryClickEvent(inventoryView, type, p_9856_.getSlotNum(), click, action, p_9856_.getButtonNum());
                        } else {
                            event = new InventoryClickEvent(inventoryView, type, p_9856_.getSlotNum(), click, action);
                        }
                        org.bukkit.inventory.Inventory top = inventoryView.getTopInventory();
                        if (p_9856_.getSlotNum() == 0 && top instanceof org.bukkit.inventory.CraftingInventory) {
                            org.bukkit.inventory.Recipe recipe = ((org.bukkit.inventory.CraftingInventory) top).getRecipe();
                            if (recipe != null) {
                                if (click == ClickType.NUMBER_KEY) {
                                    event = new CraftItemEvent(recipe, inventoryView, type, p_9856_.getSlotNum(), click, action, p_9856_.getButtonNum());
                                } else {
                                    event = new CraftItemEvent(recipe, inventoryView, type, p_9856_.getSlotNum(), click, action);
                                }
                            }
                        }

                        if (p_9856_.getSlotNum() == 3 && top instanceof org.bukkit.inventory.SmithingInventory) {
                            org.bukkit.inventory.ItemStack result = ((org.bukkit.inventory.SmithingInventory) top).getResult();
                            if (result != null) {
                                if (click == ClickType.NUMBER_KEY) {
                                    event = new org.bukkit.event.inventory.SmithItemEvent(inventoryView, type, p_9856_.getSlotNum(), click, action, p_9856_.getButtonNum());
                                } else {
                                    event = new org.bukkit.event.inventory.SmithItemEvent(inventoryView, type, p_9856_.getSlotNum(), click, action);
                                }
                            }
                        }

                        // Paper start - cartography item event
                        if (p_9856_.getSlotNum() == net.minecraft.world.inventory.CartographyTableMenu.RESULT_SLOT && top instanceof org.bukkit.inventory.CartographyInventory cartographyInventory) {
                            org.bukkit.inventory.ItemStack result = cartographyInventory.getResult();
                            if (result != null && !result.isEmpty()) {
                                if (click == ClickType.NUMBER_KEY) {
                                    event = new io.papermc.paper.event.player.CartographyItemEvent(inventoryView, type, p_9856_.getSlotNum(), click, action, p_9856_.getButtonNum());
                                } else {
                                    event = new io.papermc.paper.event.player.CartographyItemEvent(inventoryView, type, p_9856_.getSlotNum(), click, action);
                                }
                            }
                        }
                        // Paper end - cartography item event

                        event.setCancelled(cancelled);
                        AbstractContainerMenu oldContainer = this.player.containerMenu; // SPIGOT-1224
                        cserver.getPluginManager().callEvent(event);
                        if (this.player.containerMenu != oldContainer) {
                            return;
                        }
                        switch (event.getResult()) {
                            case ALLOW:
                            case DEFAULT:
                                this.player.containerMenu.clicked(i, p_9856_.getButtonNum(), p_9856_.getClickType(), this.player);
                                break;
                            case DENY:
                                /* Needs enum constructor in InventoryAction
                                if (action.modifiesOtherSlots()) {
                                } else {
                                    if (action.modifiesCursor()) {
                                        this.player.playerConnection.sendPacket(new Packet103SetSlot(-1, -1, this.player.inventory.getCarried()));
                                    }
                                    if (action.modifiesClicked()) {
                                        this.player.playerConnection.sendPacket(new Packet103SetSlot(this.player.activeContainer.windowId, packet102windowclick.slot, this.player.activeContainer.getSlot(packet102windowclick.slot).getItem()));
                                    }
                                }*/
                                switch (action) {
                                    // Modified other slots
                                    case PICKUP_ALL:
                                    case MOVE_TO_OTHER_INVENTORY:
                                    case HOTBAR_MOVE_AND_READD:
                                    case HOTBAR_SWAP:
                                    case COLLECT_TO_CURSOR:
                                    case UNKNOWN:
                                        this.player.containerMenu.sendAllDataToRemote();
                                        break;
                                    // Modified cursor and clicked
                                    case PICKUP_SOME:
                                    case PICKUP_HALF:
                                    case PICKUP_ONE:
                                    case PLACE_ALL:
                                    case PLACE_SOME:
                                    case PLACE_ONE:
                                    case SWAP_WITH_CURSOR:
                                        this.player.connection.send(new ClientboundContainerSetSlotPacket(-1, -1, this.player.inventoryMenu.incrementStateId(), this.player.containerMenu.getCarried()));
                                        this.player.connection.send(new ClientboundContainerSetSlotPacket(this.player.containerMenu.containerId, this.player.inventoryMenu.incrementStateId(), p_9856_.getSlotNum(), this.player.containerMenu.getSlot(p_9856_.getSlotNum()).getItem()));
                                        break;
                                    // Modified clicked only
                                    case DROP_ALL_SLOT:
                                    case DROP_ONE_SLOT:
                                        this.player.connection.send(new ClientboundContainerSetSlotPacket(this.player.containerMenu.containerId, this.player.inventoryMenu.incrementStateId(), p_9856_.getSlotNum(), this.player.containerMenu.getSlot(p_9856_.getSlotNum()).getItem()));
                                        break;
                                    // Modified cursor only
                                    case DROP_ALL_CURSOR:
                                    case DROP_ONE_CURSOR:
                                    case CLONE_STACK:
                                        this.player.connection.send(new ClientboundContainerSetSlotPacket(-1, -1, this.player.inventoryMenu.incrementStateId(), this.player.containerMenu.getCarried()));
                                        break;
                                    // Nothing
                                    case NOTHING:
                                        break;
                                }
                        }
                        if (event instanceof org.bukkit.event.inventory.CraftItemEvent || event instanceof org.bukkit.event.inventory.SmithItemEvent) {
                            // Need to update the inventory on crafting to
                            // correctly support custom recipes
                            player.containerMenu.sendAllDataToRemote();
                        }
                    }
                    // CraftBukkit end

                    for (Entry<ItemStack> entry : Int2ObjectMaps.fastIterable(p_9856_.getChangedSlots())) {
                        this.player.containerMenu.setRemoteSlotNoCopy(entry.getIntKey(), entry.getValue());
                    }

                    this.player.containerMenu.setRemoteCarried(p_9856_.getCarriedItem());
                    this.player.containerMenu.resumeRemoteUpdates();
                    if (flag) {
                        this.player.containerMenu.broadcastFullState();
                    } else {
                        this.player.containerMenu.broadcastChanges();
                    }
                }
            }
        }
    }

    @Override
    public void handlePlaceRecipe(ServerboundPlaceRecipePacket p_9882_) {
        // Paper start - auto recipe limit
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            if (this.recipeSpamPackets.addAndGet(io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.recipeSpamIncrement) > io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.recipeSpamLimit) {
                this.disconnectAsync(net.minecraft.network.chat.Component.translatable("disconnect.spam"), org.bukkit.event.player.PlayerKickEvent.Cause.SPAM); // Paper - kick event cause // Paper - add proper async disconnect
                return;
            }
        }
        // Paper end - auto recipe limit
        PacketUtils.ensureRunningOnSameThread(p_9882_, this, this.player.serverLevel());
        this.player.resetLastActionTime();
        if (!this.player.isSpectator()
            && this.player.containerMenu.containerId == p_9882_.getContainerId()
            && this.player.containerMenu instanceof RecipeBookMenu) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                // Paper start - Add PlayerRecipeBookClickEvent
                ResourceLocation recipeName = p_9882_.getRecipe();
                boolean makeAll = p_9882_.isShiftDown();
                com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent paperEvent = new com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent(
                        this.player.getBukkitEntity(), org.bukkit.craftbukkit.util.CraftNamespacedKey.fromMinecraft(recipeName), makeAll
                );
                if (!paperEvent.callEvent()) {
                    return;
                }
                recipeName = CraftNamespacedKey.toMinecraft(paperEvent.getRecipe());
                makeAll = paperEvent.isMakeAll();
                if (org.bukkit.event.player.PlayerRecipeBookClickEvent.getHandlerList().getRegisteredListeners().length > 0) {
                    // Paper end - Add PlayerRecipeBookClickEvent
                    // CraftBukkit start - implement PlayerRecipeBookClickEvent
                    org.bukkit.inventory.Recipe recipe = this.cserver.getRecipe(CraftNamespacedKey.fromMinecraft(recipeName)); // Paper
                    if (recipe == null) {
                        return;
                    }
                    // Paper start - Add PlayerRecipeBookClickEvent
                    org.bukkit.event.player.PlayerRecipeBookClickEvent event = CraftEventFactory.callRecipeBookClickEvent(this.player, recipe, makeAll);
                    recipeName = CraftNamespacedKey.toMinecraft(((org.bukkit.Keyed) event.getRecipe()).getKey());
                    makeAll = event.isShiftClick();
                }
                if (!(this.player.containerMenu instanceof RecipeBookMenu<?, ?> recipeBookMenu)) {
                    return;
                }
                // Paper end - Add PlayerRecipeBookClickEvent

                // Cast to keyed should be safe as the recipe will never be a MerchantRecipe.
                // Paper start - Add PlayerRecipeBookClickEvent
                final boolean finalMakeAll = makeAll;
                this.server.getRecipeManager().byKey(recipeName).ifPresent((recipeholder) -> {
                    recipeBookMenu.handlePlacement(finalMakeAll, recipeholder, this.player);
                    // Paper end - Add PlayerRecipeBookClickEvent
                });
                // CraftBukkit end
            }
        }
    }

    @Override
    public void handleContainerButtonClick(ServerboundContainerButtonClickPacket p_9854_) {
        PacketUtils.ensureRunningOnSameThread(p_9854_, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        if (this.player.containerMenu.containerId == p_9854_.containerId() && !this.player.isSpectator()) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                boolean flag = this.player.containerMenu.clickMenuButton(this.player, p_9854_.buttonId());
                if (flag) {
                    this.player.containerMenu.broadcastChanges();
                }
            }
        }
    }

    @Override
    public void handleSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket p_9915_) {
        PacketUtils.ensureRunningOnSameThread(p_9915_, this, this.player.serverLevel());
        if (this.player.gameMode.isCreative()) {
            boolean flag = p_9915_.slotNum() < 0;
            ItemStack itemstack = p_9915_.itemStack();
            if (!itemstack.isItemEnabled(this.player.level().enabledFeatures())) {
                return;
            }

            CustomData customdata = itemstack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);
            if (customdata.contains("x") && customdata.contains("y") && customdata.contains("z") && this.player.getBukkitEntity().hasPermission("minecraft.nbt.copy")) { // Spigot
                BlockPos blockpos = BlockEntity.getPosFromTag(customdata.getUnsafe());
                if (this.player.level().isLoaded(blockpos)) {
                    BlockEntity blockentity = this.player.level().getBlockEntity(blockpos);
                    if (blockentity != null) {
                        blockentity.saveToItem(itemstack, this.player.level().registryAccess());
                    }
                }
            }

            boolean flag1 = p_9915_.slotNum() >= 1 && p_9915_.slotNum() <= 45;
            boolean flag2 = itemstack.isEmpty() || itemstack.getCount() <= itemstack.getMaxStackSize();

            if (flag || (flag1 && !ItemStack.matches(this.player.inventoryMenu.getSlot(p_9915_.slotNum()).getItem(), p_9915_.itemStack()))) { // Insist on valid slot
                // CraftBukkit start - Call click event
                org.bukkit.inventory.InventoryView inventory = this.player.inventoryMenu.getBukkitView();
                org.bukkit.inventory.ItemStack item = CraftItemStack.asBukkitCopy(p_9915_.itemStack());

                InventoryType.SlotType type = InventoryType.SlotType.QUICKBAR;
                if (flag) {
                    type = org.bukkit.event.inventory.InventoryType.SlotType.OUTSIDE;
                } else if (p_9915_.slotNum() < 36) {
                    if (p_9915_.slotNum() >= 5 && p_9915_.slotNum() < 9) {
                        type = InventoryType.SlotType.ARMOR;
                    } else {
                        type = InventoryType.SlotType.CONTAINER;
                    }
                }
                InventoryCreativeEvent event = new  InventoryCreativeEvent(inventory, type, flag ? -999 : p_9915_.slotNum(), item);
                cserver.getPluginManager().callEvent(event);
                itemstack = CraftItemStack.asNMSCopy(event.getCursor());
                switch (event.getResult()) {
                    case ALLOW:
                        // Plugin cleared the id / stacksize checks
                        flag2 = true;
                        break;
                    case DEFAULT:
                        break;
                    case DENY:
                        // Reset the slot
                        if (p_9915_.slotNum() >= 0) {
                            this.player.connection.send(new ClientboundContainerSetSlotPacket(this.player.inventoryMenu.containerId, this.player.inventoryMenu.incrementStateId(), p_9915_.slotNum(), this.player.inventoryMenu.getSlot(p_9915_.slotNum()).getItem()));
                            this.player.connection.send(new ClientboundContainerSetSlotPacket(-1, this.player.inventoryMenu.incrementStateId(), -1, ItemStack.EMPTY));
                        }
                        return;
                }
            }
            // CraftBukkit end

            if (flag1 && flag2) {
                this.player.inventoryMenu.getSlot(p_9915_.slotNum()).setByPlayer(itemstack);
                this.player.inventoryMenu.broadcastChanges();
            } else if (flag && flag2 && this.dropSpamTickCount < 200) {
                this.dropSpamTickCount += 20;
                this.player.drop(itemstack, true);
            }
        }
    }

    @Override
    public void handleSignUpdate(ServerboundSignUpdatePacket p_9921_) {
        List<String> list = Stream.of(p_9921_.getLines()).map(ChatFormatting::stripFormatting).collect(Collectors.toList());
        this.filterTextPacket(list).thenAcceptAsync(p_215245_ -> this.updateSignText(p_9921_, (List<FilteredText>)p_215245_), this.server);
    }

    private void updateSignText(ServerboundSignUpdatePacket p_9923_, List<FilteredText> p_9924_) {
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        ServerLevel serverlevel = this.player.serverLevel();
        BlockPos blockpos = p_9923_.getPos();
        if (serverlevel.hasChunkAt(blockpos)) {
            if (!(serverlevel.getBlockEntity(blockpos) instanceof SignBlockEntity signblockentity)) {
                return;
            }

            signblockentity.updateSignText(this.player, p_9923_.isFrontText(), p_9924_);
        }
    }

    @Override
    public void handlePlayerAbilities(ServerboundPlayerAbilitiesPacket p_9887_) {
        PacketUtils.ensureRunningOnSameThread(p_9887_, this, this.player.serverLevel());
        // CraftBukkit start
        if (this.player.mayFly() && this.player.getAbilities().flying != p_9887_.isFlying()) {
            org.bukkit.event.player.PlayerToggleFlightEvent event = new org.bukkit.event.player.PlayerToggleFlightEvent(this.player.getBukkitEntity(), p_9887_.isFlying());
            this.cserver.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                this.player.getAbilities().flying = p_9887_.isFlying(); // Actually set the player's flying status
            } else {
                this.player.onUpdateAbilities(); // Tell the player their ability was reverted
            }
        }
        // CraftBukkit end
    }

    @Override
    public void handleClientInformation(ServerboundClientInformationPacket p_301979_) {
        PacketUtils.ensureRunningOnSameThread(p_301979_, this, this.player.serverLevel());
        // Paper start - do not accept invalid information
        if (p_301979_.information().viewDistance() < 0) {
            LOGGER.warn("Disconnecting " + this.player.getScoreboardName() + " for invalid view distance: " + p_301979_.information().viewDistance());
            this.disconnect(Component.literal("Invalid client settings"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION);
            return;
        }
        // Paper end - do not accept invalid information
        net.minecraft.server.level.ClientInformation oldInfo = this.player.clientInformation();
        this.player.updateOptions(p_301979_.information());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.ClientInformationUpdatedEvent(this.player, oldInfo, p_301979_.information()));
        this.connection.channel.attr(io.papermc.paper.adventure.PaperAdventure.LOCALE_ATTRIBUTE).set(net.kyori.adventure.translation.Translator.parseLocale(p_301979_.information().language())); // Paper
    }

    @Override
    public void handleChangeDifficulty(ServerboundChangeDifficultyPacket p_9839_) {
        PacketUtils.ensureRunningOnSameThread(p_9839_, this, this.player.serverLevel());
        if (this.player.hasPermissions(2) || this.isSingleplayerOwner()) {
            // this.server.setDifficulty(p_9839_.getDifficulty(), false); // Paper - per level difficulty; don't allow clients to change this
        }
    }

    @Override
    public void handleLockDifficulty(ServerboundLockDifficultyPacket p_9872_) {
        PacketUtils.ensureRunningOnSameThread(p_9872_, this, this.player.serverLevel());
        if (this.player.hasPermissions(2) || this.isSingleplayerOwner()) {
            this.server.setDifficultyLocked(p_9872_.isLocked());
        }
    }

    @Override
    public void handleChatSessionUpdate(ServerboundChatSessionUpdatePacket p_253950_) {
        PacketUtils.ensureRunningOnSameThread(p_253950_, this, this.player.serverLevel());
        RemoteChatSession.Data remotechatsession$data = p_253950_.chatSession();
        ProfilePublicKey.Data profilepublickey$data = this.chatSession != null ? this.chatSession.profilePublicKey().data() : null;
        ProfilePublicKey.Data profilepublickey$data1 = remotechatsession$data.profilePublicKey();
        if (!Objects.equals(profilepublickey$data, profilepublickey$data1)) {
            if (profilepublickey$data != null && profilepublickey$data1.expiresAt().isBefore(profilepublickey$data.expiresAt())) {
                this.disconnect(ProfilePublicKey.EXPIRED_PROFILE_PUBLIC_KEY, org.bukkit.event.player.PlayerKickEvent.Cause.EXPIRED_PROFILE_PUBLIC_KEY); // Paper - kick event causes
            } else {
                try {
                    SignatureValidator signaturevalidator = this.server.getProfileKeySignatureValidator();
                    if (signaturevalidator == null) {
                        LOGGER.warn("Ignoring chat session from {} due to missing Services public key", this.player.getGameProfile().getName());
                        return;
                    }

                    this.resetPlayerChatState(remotechatsession$data.validate(this.player.getGameProfile(), signaturevalidator));
                } catch (ProfilePublicKey.ValidationException profilepublickey$validationexception) {
                    // LOGGER.error("Failed to validate profile key: {}", profilepublickey$validationexception.getMessage());
                    this.disconnect(profilepublickey$validationexception.getComponent(), profilepublickey$validationexception.kickCause); // Paper - kick event causes
                }
            }
        }
    }

    @Override
    public void handleConfigurationAcknowledged(ServerboundConfigurationAcknowledgedPacket p_294416_) {
        if (!this.waitingForSwitchToConfig) {
            throw new IllegalStateException("Client acknowledged config, but none was requested");
        } else {
            this.connection
                .setupInboundProtocol(
                    ConfigurationProtocols.SERVERBOUND,
                    new ServerConfigurationPacketListenerImpl(this.server, this.connection, this.createCookie(this.player.clientInformation(), this.connectionType))
                );
        }
    }

    @Override
    public void handleChunkBatchReceived(ServerboundChunkBatchReceivedPacket p_295247_) {
        PacketUtils.ensureRunningOnSameThread(p_295247_, this, this.player.serverLevel());
        this.chunkSender.onChunkBatchReceivedByClient(p_295247_.desiredChunksPerTick());
    }

    @Override
    public void handleDebugSampleSubscription(ServerboundDebugSampleSubscriptionPacket p_324293_) {
        PacketUtils.ensureRunningOnSameThread(p_324293_, this, this.player.serverLevel());
        this.server.subscribeToDebugSample(this.player, p_324293_.sampleType());
    }

    private void resetPlayerChatState(RemoteChatSession p_253823_) {
        this.chatSession = p_253823_;
        this.signedMessageDecoder = p_253823_.createMessageDecoder(this.player.getUUID());
        this.chatMessageChain
            .append(
                () -> {
                    this.player.setChatSession(p_253823_);
                    this.server
                        .getPlayerList()
                        .broadcastAll(
                            new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT), List.of(this.player))
                                , this.player); // Paper - Use single player info update packet on join
                }
            );
    }

    @Override
    public void handleCustomPayload(ServerboundCustomPayloadPacket p_333887_) {
        super.handleCustomPayload(p_333887_); // Neo: Call super to invoke modded payload handling.
    }

    @Override
    public ServerPlayer getPlayer() {
        return this.player;
    }

    @FunctionalInterface
    public interface EntityInteraction {
        InteractionResult run(ServerPlayer p_143695_, Entity p_143696_, InteractionHand p_143697_);
    }

    // Paper start - Add fail move event
    private io.papermc.paper.event.player.PlayerFailMoveEvent fireFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent.FailReason failReason,
                                                                           double toX, double toY, double toZ, float toYaw, float toPitch, boolean logWarning) {
        org.bukkit.entity.Player player = this.getCraftPlayer();
        Location from = new Location(player.getWorld(), this.lastPosX, this.lastPosY, this.lastPosZ, this.lastYaw, this.lastPitch);
        Location to = new Location(player.getWorld(), toX, toY, toZ, toYaw, toPitch);
        io.papermc.paper.event.player.PlayerFailMoveEvent event = new io.papermc.paper.event.player.PlayerFailMoveEvent(player, failReason,
                false, logWarning, from, to);
        event.callEvent();
        return event;
    }
    // Paper end - Add fail move event
}
