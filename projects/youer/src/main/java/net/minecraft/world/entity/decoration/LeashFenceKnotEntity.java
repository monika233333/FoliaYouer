package net.minecraft.world.entity.decoration;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.event.CraftEventFactory;

public class LeashFenceKnotEntity extends BlockAttachedEntity {
    public static final double OFFSET_Y = 0.375;

    public LeashFenceKnotEntity(EntityType<? extends LeashFenceKnotEntity> p_31828_, Level p_31829_) {
        super(p_31828_, p_31829_);
    }

    public LeashFenceKnotEntity(Level p_31831_, BlockPos p_31832_) {
        super(EntityType.LEASH_KNOT, p_31831_, p_31832_);
        this.setPos((double)p_31832_.getX(), (double)p_31832_.getY(), (double)p_31832_.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder p_344730_) {
    }

    @Override
    protected void recalculateBoundingBox() {
        this.setPosRaw((double)this.pos.getX() + 0.5, (double)this.pos.getY() + 0.375, (double)this.pos.getZ() + 0.5);
        double d0 = (double)this.getType().getWidth() / 2.0;
        double d1 = (double)this.getType().getHeight();
        this.setBoundingBox(new AABB(this.getX() - d0, this.getY(), this.getZ() - d0, this.getX() + d0, this.getY() + d1, this.getZ() + d0));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double p_31835_) {
        return p_31835_ < 1024.0;
    }

    @Override
    public void dropItem(@Nullable Entity p_31837_) {
        this.playSound(SoundEvents.LEASH_KNOT_BREAK, 1.0F, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag p_31852_) {
    }

    @Override
    public void readAdditionalSaveData(CompoundTag p_31850_) {
    }

    @Override
    public InteractionResult interact(Player p_31842_, InteractionHand p_31843_) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            boolean flag = false;
            List<Leashable> list = LeadItem.leashableInArea(this.level(), this.getPos(), p_353058_ -> {
                Entity entity = p_353058_.getLeashHolder();
                return entity == p_31842_ || entity == this;
            });

            for (Leashable leashable : list) {
                if (leashable.getLeashHolder() == p_31842_) {
                    // CraftBukkit start
                    if (leashable instanceof Entity leashed) {
                        if (CraftEventFactory.callPlayerLeashEntityEvent(leashed, this, p_31842_, p_31843_).isCancelled()) {
                            ((net.minecraft.server.level.ServerPlayer) p_31842_).connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket(leashed, leashable.getLeashHolder()));
                            flag = true; // Also set true when the event is cancelled otherwise it tries to unleash the entities
                            continue;
                        }
                    }
                    // CraftBukkit end
                    leashable.setLeashedTo(this, true);
                    flag = true;
                }
            }

            boolean flag1 = false;
            if (!flag) {
                // CraftBukkit start - Move below
                // this.discard();
                boolean die = true;
                // CraftBukkit end
                if (true || p_31842_.getAbilities().instabuild) { // CraftBukkit - Process for non-creative as well
                    for (Leashable leashable1 : list) {
                        if (leashable1.isLeashed() && leashable1.getLeashHolder() == this) {
                            // CraftBukkit start
                            boolean dropLeash = !p_31842_.hasInfiniteMaterials();
                            if (leashable1 instanceof Entity leashed) {
                                // Paper start - Expand EntityUnleashEvent
                                org.bukkit.event.player.PlayerUnleashEntityEvent event = CraftEventFactory.callPlayerUnleashEntityEvent(leashed, p_31842_, p_31843_, dropLeash);
                                dropLeash = event.isDropLeash();
                                if (event.isCancelled()) {
                                    // Paper end - Expand EntityUnleashEvent
                                    die = false;
                                    continue;
                                }
                            }
                            leashable1.dropLeash(true, dropLeash); // false -> survival mode boolean
                            // CraftBukkit end
                            flag1 = true;
                        }
                    }
                    // CraftBukkit start
                    if (die) {
                        this.removeReason(org.bukkit.event.entity.EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                        this.discard();
                    }
                    // CraftBukkit end
                }
            }

            if (flag || flag1) {
                this.gameEvent(GameEvent.BLOCK_ATTACH, p_31842_);
            }

            return InteractionResult.CONSUME;
        }
    }

    @Override
    public boolean survives() {
        return this.level().getBlockState(this.pos).is(BlockTags.FENCES);
    }

    public static LeashFenceKnotEntity getOrCreateKnot(Level p_31845_, BlockPos p_31846_) {
        int i = p_31846_.getX();
        int j = p_31846_.getY();
        int k = p_31846_.getZ();

        for (LeashFenceKnotEntity leashfenceknotentity : p_31845_.getEntitiesOfClass(
            LeashFenceKnotEntity.class, new AABB((double)i - 1.0, (double)j - 1.0, (double)k - 1.0, (double)i + 1.0, (double)j + 1.0, (double)k + 1.0)
        )) {
            if (leashfenceknotentity.getPos().equals(p_31846_)) {
                return leashfenceknotentity;
            }
        }

        LeashFenceKnotEntity leashfenceknotentity1 = new LeashFenceKnotEntity(p_31845_, p_31846_);
        p_31845_.addFreshEntity(leashfenceknotentity1);
        return leashfenceknotentity1;
    }

    public void playPlacementSound() {
        this.playSound(SoundEvents.LEASH_KNOT_PLACE, 1.0F, 1.0F);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity p_352402_) {
        return new ClientboundAddEntityPacket(this, 0, this.getPos());
    }

    @Override
    public Vec3 getRopeHoldPosition(float p_31863_) {
        return this.getPosition(p_31863_).add(0.0, 0.2, 0.0);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.LEAD);
    }
}
