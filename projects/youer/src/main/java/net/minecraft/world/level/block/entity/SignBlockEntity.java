package net.minecraft.world.level.block.entity;

import com.mohistmc.youer.YouerConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.bukkit.block.sign.Side;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftSign;
import org.bukkit.craftbukkit.command.CraftBlockCommandSender;
import org.slf4j.Logger;

public class SignBlockEntity extends BlockEntity implements CommandSource { // CraftBukkit - implements
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TEXT_LINE_WIDTH = 90;
    private static final int TEXT_LINE_HEIGHT = 10;
    @Nullable
    public UUID playerWhoMayEdit;
    private SignText frontText = this.createDefaultSignText();
    private SignText backText = this.createDefaultSignText();
    private boolean isWaxed;

    public SignBlockEntity(BlockPos p_155700_, BlockState p_155701_) {
        this(BlockEntityType.SIGN, p_155700_, p_155701_);
    }

    public SignBlockEntity(BlockEntityType p_249609_, BlockPos p_248914_, BlockState p_249550_) {
        super(p_249609_, p_248914_, p_249550_);
    }

    protected SignText createDefaultSignText() {
        return new SignText();
    }

    public boolean isFacingFrontText(Player p_277382_) {
        // Paper start - More Sign Block API
        return this.isFacingFrontText(p_277382_.getX(), p_277382_.getZ());
    }
    public boolean isFacingFrontText(double x, double z) {
        // Paper end - More Sign Block API
        if (this.getBlockState().getBlock() instanceof SignBlock signblock) {
            Vec3 vec3 = signblock.getSignHitboxCenterPosition(this.getBlockState());
            double d0 = x - ((double) this.getBlockPos().getX() + vec3.x); // Paper - More Sign Block API
            double d1 = z - ((double) this.getBlockPos().getZ() + vec3.z); // Paper - More Sign Block API
            float f = signblock.getYRotationDegrees(this.getBlockState());
            float f1 = (float)(Mth.atan2(d1, d0) * 180.0F / (float)Math.PI) - 90.0F;
            return Mth.degreesDifferenceAbs(f, f1) <= 90.0F;
        } else {
            return false;
        }
    }

    public SignText getText(boolean p_277918_) {
        return p_277918_ ? this.frontText : this.backText;
    }

    public SignText getFrontText() {
        return this.frontText;
    }

    public SignText getBackText() {
        return this.backText;
    }

    public int getTextLineHeight() {
        return 10;
    }

    public int getMaxTextLineWidth() {
        return 90;
    }

    @Override
    protected void saveAdditional(CompoundTag p_187515_, HolderLookup.Provider p_324471_) {
        super.saveAdditional(p_187515_, p_324471_);
        DynamicOps<Tag> dynamicops = p_324471_.createSerializationContext(NbtOps.INSTANCE);
        SignText.DIRECT_CODEC
            .encodeStart(dynamicops, this.frontText)
            .resultOrPartial(LOGGER::error)
            .ifPresent(p_277417_ -> p_187515_.put("front_text", p_277417_));
        SignText.DIRECT_CODEC
            .encodeStart(dynamicops, this.backText)
            .resultOrPartial(LOGGER::error)
            .ifPresent(p_277389_ -> p_187515_.put("back_text", p_277389_));
        p_187515_.putBoolean("is_waxed", this.isWaxed);
    }

    @Override
    protected void loadAdditional(CompoundTag p_155716_, HolderLookup.Provider p_324351_) {
        super.loadAdditional(p_155716_, p_324351_);
        DynamicOps<Tag> dynamicops = p_324351_.createSerializationContext(NbtOps.INSTANCE);
        if (p_155716_.contains("front_text")) {
            SignText.DIRECT_CODEC
                .parse(dynamicops, p_155716_.getCompound("front_text"))
                .resultOrPartial(LOGGER::error)
                .ifPresent(p_278212_ -> this.frontText = this.loadLines(p_278212_));
        }

        if (p_155716_.contains("back_text")) {
            SignText.DIRECT_CODEC
                .parse(dynamicops, p_155716_.getCompound("back_text"))
                .resultOrPartial(LOGGER::error)
                .ifPresent(p_278213_ -> this.backText = this.loadLines(p_278213_));
        }

        this.isWaxed = p_155716_.getBoolean("is_waxed");
    }

    private SignText loadLines(SignText p_278305_) {
        for (int i = 0; i < 4; i++) {
            Component component = this.loadLine(p_278305_.getMessage(i, false));
            Component component1 = this.loadLine(p_278305_.getMessage(i, true));
            p_278305_ = p_278305_.setMessage(i, component, component1);
        }

        return p_278305_;
    }

    private Component loadLine(Component p_278307_) {
        if (this.level instanceof ServerLevel serverlevel) {
            try {
                return ComponentUtils.updateForEntity(createCommandSourceStack(null, serverlevel, this.worldPosition), p_278307_, null, 0);
            } catch (CommandSyntaxException commandsyntaxexception) {
            }
        }

        return p_278307_;
    }

    public void updateSignText(Player p_278048_, boolean p_278103_, List<FilteredText> p_277990_) {
        if (!this.isWaxed() && p_278048_.getUUID().equals(this.getPlayerWhoMayEdit()) && this.level != null) {
            this.updateText(p_277776_ -> this.setMessages(p_278048_, p_277990_, p_277776_, p_278103_), p_278103_); // CraftBukkit
            this.setAllowedPlayerEditor(null);
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        } else {
            LOGGER.warn("Player {} just tried to change non-editable sign", p_278048_.getName().getString());
            if (p_278048_.distanceToSqr(this.getBlockPos().getX(), this.getBlockPos().getY(), this.getBlockPos().getZ()) < 32 * 32) // Paper - Dont send far away sign update
            ((ServerPlayer) p_278048_).connection.send(this.getUpdatePacket()); // CraftBukkit
        }
    }

    public boolean updateText(UnaryOperator<SignText> p_277877_, boolean p_277426_) {
        SignText signtext = this.getText(p_277426_);
        return this.setText(p_277877_.apply(signtext), p_277426_);
    }

    private SignText setMessages(Player p_277396_, List<FilteredText> p_277744_, SignText p_277359_, boolean front) {  // CraftBukkit
        SignText originalText = p_277359_; // CraftBukkit
        for (int i = 0; i < p_277744_.size(); i++) {
            FilteredText filteredtext = p_277744_.get(i);
            Style style = p_277359_.getMessage(i, p_277396_.isTextFilteringEnabled()).getStyle();
            if (p_277396_.isTextFilteringEnabled()) {
                p_277359_ = p_277359_.setMessage(i, Component.literal(net.minecraft.util.StringUtil.filterText(filteredtext.filteredOrEmpty())).setStyle(style)); // Paper - filter sign text to chat only
            } else {
                p_277359_ = p_277359_.setMessage(
                    i, Component.literal(net.minecraft.util.StringUtil.filterText(filteredtext.raw())).setStyle(style), Component.literal(net.minecraft.util.StringUtil.filterText(filteredtext.filteredOrEmpty())).setStyle(style)); // Paper - filter sign text to chat only
            }
        }

        // CraftBukkit start
        org.bukkit.entity.Player player = ((net.minecraft.server.level.ServerPlayer) p_277396_).getBukkitEntity();
        List<net.kyori.adventure.text.Component> lines = new java.util.ArrayList<>(); // Paper - adventure

        for (int i = 0; i < p_277744_.size(); ++i) {
            lines.add(io.papermc.paper.adventure.PaperAdventure.asAdventure(p_277359_.getMessage(i, p_277396_.isTextFilteringEnabled()))); // Paper - Adventure
        }

        org.bukkit.event.block.SignChangeEvent event = new org.bukkit.event.block.SignChangeEvent(CraftBlock.at(this.level, this.worldPosition), player, new java.util.ArrayList<>(lines), (front) ? org.bukkit.block.sign.Side.FRONT : org.bukkit.block.sign.Side.BACK);
        p_277396_.level().getCraftServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return originalText;
        }

        Component[] components = CraftSign.sanitizeLines(event.lines());  // Paper - Adventure
        for (int i = 0; i < components.length; i++) {
            if (!java.util.Objects.equals(lines.get(i), event.line(i))) {  // Paper - Adventure
                p_277359_ = p_277359_.setMessage(i, components[i]);
            }
        }
        // CraftBukkit end

        return p_277359_;
    }

    public boolean setText(SignText p_277733_, boolean p_277720_) {
        return p_277720_ ? this.setFrontText(p_277733_) : this.setBackText(p_277733_);
    }

    private boolean setBackText(SignText p_277777_) {
        if (p_277777_ != this.backText) {
            this.backText = p_277777_;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    private boolean setFrontText(SignText p_278038_) {
        if (p_278038_ != this.frontText) {
            this.frontText = p_278038_;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    public boolean canExecuteClickCommands(boolean p_278276_, Player p_278240_) {
        return this.isWaxed() && this.getText(p_278276_).hasAnyClickCommands(p_278240_);
    }

    public boolean executeClickCommandsIfPresent(Player p_279304_, Level p_279201_, BlockPos p_278282_, boolean p_278254_) {
        boolean flag = false;

        if (YouerConfig.custom_disabled_sign_commands) {
            return false;
        }
        for (Component component : this.getText(p_278254_).getMessages(p_279304_.isTextFilteringEnabled())) {
            Style style = component.getStyle();
            ClickEvent clickevent = style.getClickEvent();
            if (clickevent != null && clickevent.getAction() == ClickEvent.Action.RUN_COMMAND) {
                // Paper start - Fix commands from signs not firing command events
                String command = clickevent.getValue().startsWith("/") ? clickevent.getValue() : "/" + clickevent.getValue();
                if (org.spigotmc.SpigotConfig.logCommands)  {
                    LOGGER.info("{} issued server command(Sign): {}", p_279304_.getScoreboardName(), command);
                }
                io.papermc.paper.event.player.PlayerSignCommandPreprocessEvent event = new io.papermc.paper.event.player.PlayerSignCommandPreprocessEvent((org.bukkit.entity.Player) p_279304_.getBukkitEntity(), command, new org.bukkit.craftbukkit.util.LazyPlayerSet(p_279304_.getServer()), (org.bukkit.block.Sign) CraftBlock.at(this.level, this.worldPosition).getState(), p_278254_ ? Side.FRONT : Side.BACK);
                if (!event.callEvent()) {
                    return false;
                }
                Player player = ((org.bukkit.craftbukkit.entity.CraftPlayer) event.getPlayer()).getHandle();
                if (event.getPlayer().isOp()) {
                    p_279304_.getServer().getCommands().performPrefixedCommand(createCommandSourceStack(player, p_279201_, p_278282_).withSource(commandSource(player, p_279201_)), event.getMessage());
                    // Paper end - Fix commands from signs not firing command events
                    flag = true;
                }
            }
        }

        return flag;
    }

    // CraftBukkit start
    @Override
    public void sendSystemMessage(Component ichatbasecomponent) {}

    @Override
    public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
        return wrapper.getEntity() != null ? wrapper.getEntity().getBukkitSender(wrapper) : new CraftBlockCommandSender(wrapper, this);
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

    public CommandSource commandSource(@Nullable Player p_279428_, Level p_279359_){
        // Paper start - Fix commands from signs not firing command events
        return p_279359_.paperConfig().misc.showSignClickCommandFailureMsgsToPlayer ? new io.papermc.paper.commands.DelegatingCommandSource(SignBlockEntity.this) {
            @Override
            public void sendSystemMessage(Component message) {
                if (p_279428_ != null) {
                    p_279428_.sendSystemMessage(message);
                }
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }
        } : this;
        // Paper end - Fix commands from signs not firing command events
    }

    private static CommandSourceStack createCommandSourceStack(@Nullable Player p_279428_, Level p_279359_, BlockPos p_279430_) {
        String s = p_279428_ == null ? "Sign" : p_279428_.getName().getString();
        Component component = (Component)(p_279428_ == null ? Component.literal("Sign") : p_279428_.getDisplayName());
        return new CommandSourceStack(
            CommandSource.NULL, Vec3.atCenterOf(p_279430_), Vec2.ZERO, (ServerLevel)p_279359_, 2, s, component, p_279359_.getServer(), p_279428_
        );
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider p_324439_) {
        return this.saveCustomOnly(p_324439_);
    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    public void setAllowedPlayerEditor(@Nullable UUID p_155714_) {
        this.playerWhoMayEdit = p_155714_;
    }

    @Nullable
    public UUID getPlayerWhoMayEdit() {
        // CraftBukkit start - unnecessary sign ticking removed, so do this lazily
        if (this.level != null && this.playerWhoMayEdit != null) {
            clearInvalidPlayerWhoMayEdit(this, this.level, this.playerWhoMayEdit);
        }
        // CraftBukkit end
        return this.playerWhoMayEdit;
    }

    private void markUpdated() {
        this.setChanged();
        if (this.level != null) this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3); // CraftBukkit - skip notify if world is null (SPIGOT-5122)
    }

    public boolean isWaxed() {
        return this.isWaxed;
    }

    public boolean setWaxed(boolean p_277344_) {
        if (this.isWaxed != p_277344_) {
            this.isWaxed = p_277344_;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    public boolean playerIsTooFarAwayToEdit(UUID p_277978_) {
        Player player = this.level.getPlayerByUUID(p_277978_);
        return player == null || !player.canInteractWithBlock(this.getBlockPos(), 4.0);
    }

    public static void tick(Level p_277662_, BlockPos p_278050_, BlockState p_277927_, SignBlockEntity p_277928_) {
        UUID uuid = p_277928_.getPlayerWhoMayEdit();
        if (uuid != null) {
            p_277928_.clearInvalidPlayerWhoMayEdit(p_277928_, p_277662_, uuid);
        }
    }

    private void clearInvalidPlayerWhoMayEdit(SignBlockEntity p_277656_, Level p_277853_, UUID p_277849_) {
        if (p_277656_.playerIsTooFarAwayToEdit(p_277849_)) {
            p_277656_.setAllowedPlayerEditor(null);
        }
    }

    public SoundEvent getSignInteractionFailedSoundEvent() {
        return SoundEvents.WAXED_SIGN_INTERACT_FAIL;
    }
}
