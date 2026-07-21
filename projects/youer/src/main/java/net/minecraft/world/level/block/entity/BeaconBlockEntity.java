package net.minecraft.world.level.block.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.LockCode;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.bukkit.craftbukkit.potion.CraftPotionUtil;
import org.bukkit.potion.PotionEffect;

public class BeaconBlockEntity extends BlockEntity implements MenuProvider, Nameable {
    private static final int MAX_LEVELS = 4;
    public static final List<List<Holder<MobEffect>>> BEACON_EFFECTS = List.of(
        List.of(MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED),
        List.of(MobEffects.DAMAGE_RESISTANCE, MobEffects.JUMP),
        List.of(MobEffects.DAMAGE_BOOST),
        List.of(MobEffects.REGENERATION)
    );
    private static final Set<Holder<MobEffect>> VALID_EFFECTS = BEACON_EFFECTS.stream().flatMap(Collection::stream).collect(Collectors.toSet());
    public static final int DATA_LEVELS = 0;
    public static final int DATA_PRIMARY = 1;
    public static final int DATA_SECONDARY = 2;
    public static final int NUM_DATA_VALUES = 3;
    private static final int BLOCKS_CHECK_PER_TICK = 10;
    private static final Component DEFAULT_NAME = Component.translatable("container.beacon");
    private static final String TAG_PRIMARY = "primary_effect";
    private static final String TAG_SECONDARY = "secondary_effect";
    List<BeaconBlockEntity.BeaconBeamSection> beamSections = Lists.newArrayList();
    private List<BeaconBlockEntity.BeaconBeamSection> checkingBeamSections = Lists.newArrayList();
    public int levels;
    private int lastCheckY;
    @Nullable
    public Holder<MobEffect> primaryPower;
    @Nullable
    public Holder<MobEffect> secondaryPower;
    @Nullable
    public Component name;
    public LockCode lockKey = LockCode.NO_LOCK;
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int p_58711_) {
            return switch (p_58711_) {
                case 0 -> BeaconBlockEntity.this.levels;
                case 1 -> BeaconMenu.encodeEffect(BeaconBlockEntity.this.primaryPower);
                case 2 -> BeaconMenu.encodeEffect(BeaconBlockEntity.this.secondaryPower);
                default -> 0;
            };
        }

        @Override
        public void set(int p_58713_, int p_58714_) {
            switch (p_58713_) {
                case 0:
                    BeaconBlockEntity.this.levels = p_58714_;
                    break;
                case 1:
                    if (!BeaconBlockEntity.this.level.isClientSide && !BeaconBlockEntity.this.beamSections.isEmpty()) {
                        BeaconBlockEntity.playSound(BeaconBlockEntity.this.level, BeaconBlockEntity.this.worldPosition, SoundEvents.BEACON_POWER_SELECT);
                    }

                    BeaconBlockEntity.this.primaryPower = BeaconBlockEntity.filterEffect(BeaconMenu.decodeEffect(p_58714_));
                    break;
                case 2:
                    BeaconBlockEntity.this.secondaryPower = BeaconBlockEntity.filterEffect(BeaconMenu.decodeEffect(p_58714_));
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    @Nullable
    static Holder<MobEffect> filterEffect(@Nullable Holder<MobEffect> p_316372_) {
        return VALID_EFFECTS.contains(p_316372_) ? p_316372_ : null;
    }

    public BeaconBlockEntity(BlockPos p_155088_, BlockState p_155089_) {
        super(BlockEntityType.BEACON, p_155088_, p_155089_);
    }

    public static void tick(Level p_155108_, BlockPos p_155109_, BlockState p_155110_, BeaconBlockEntity p_155111_) {
        int i = p_155109_.getX();
        int j = p_155109_.getY();
        int k = p_155109_.getZ();
        BlockPos blockpos;
        if (p_155111_.lastCheckY < j) {
            blockpos = p_155109_;
            p_155111_.checkingBeamSections = Lists.newArrayList();
            p_155111_.lastCheckY = p_155109_.getY() - 1;
        } else {
            blockpos = new BlockPos(i, p_155111_.lastCheckY + 1, k);
        }

        BeaconBlockEntity.BeaconBeamSection beaconblockentity$beaconbeamsection = p_155111_.checkingBeamSections.isEmpty()
            ? null
            : p_155111_.checkingBeamSections.get(p_155111_.checkingBeamSections.size() - 1);
        int l = p_155108_.getHeight(Heightmap.Types.WORLD_SURFACE, i, k);

        for (int i1 = 0; i1 < 10 && blockpos.getY() <= l; i1++) {
            BlockState blockstate = p_155108_.getBlockState(blockpos);
            Integer j1 = blockstate.getBeaconColorMultiplier(p_155108_, blockpos, p_155109_);
            if (j1 != null) {
                if (p_155111_.checkingBeamSections.size() <= 1) {
                    beaconblockentity$beaconbeamsection = new BeaconBlockEntity.BeaconBeamSection(j1);
                    p_155111_.checkingBeamSections.add(beaconblockentity$beaconbeamsection);
                } else if (beaconblockentity$beaconbeamsection != null) {
                    if (j1 == beaconblockentity$beaconbeamsection.color) {
                        beaconblockentity$beaconbeamsection.increaseHeight();
                    } else {
                        beaconblockentity$beaconbeamsection = new BeaconBlockEntity.BeaconBeamSection(
                            FastColor.ARGB32.average(beaconblockentity$beaconbeamsection.color, j1)
                        );
                        p_155111_.checkingBeamSections.add(beaconblockentity$beaconbeamsection);
                    }
                }
            } else {
                if (beaconblockentity$beaconbeamsection == null || blockstate.getLightBlock(p_155108_, blockpos) >= 15 && !blockstate.is(Blocks.BEDROCK)) {
                    p_155111_.checkingBeamSections.clear();
                    p_155111_.lastCheckY = l;
                    break;
                }

                beaconblockentity$beaconbeamsection.increaseHeight();
            }

            blockpos = blockpos.above();
            p_155111_.lastCheckY++;
        }

        int k1 = p_155111_.levels;
        if (p_155108_.getGameTime() % 80L == 0L) {
            if (!p_155111_.beamSections.isEmpty()) {
                p_155111_.levels = updateBase(p_155108_, i, j, k);
            }

            if (p_155111_.levels > 0 && !p_155111_.beamSections.isEmpty()) {
                applyEffects$blockEntity = p_155111_;
                applyEffects(p_155108_, p_155109_, p_155111_.levels, p_155111_.primaryPower, p_155111_.secondaryPower);
                playSound(p_155108_, p_155109_, SoundEvents.BEACON_AMBIENT);
            }
        }
        // Paper start - beacon activation/deactivation events
        if (k1 <= 0 && p_155111_.levels > 0) {
            org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(p_155108_, p_155109_);
            new io.papermc.paper.event.block.BeaconActivatedEvent(block).callEvent();
        } else if (k1 > 0 && p_155111_.levels <= 0) {
            org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(p_155108_, p_155109_);
            new io.papermc.paper.event.block.BeaconDeactivatedEvent(block).callEvent();
        }
        // Paper end - beacon activation/deactivation events
        if (p_155111_.lastCheckY >= l) {
            p_155111_.lastCheckY = p_155108_.getMinBuildHeight() - 1;
            boolean flag = k1 > 0;
            p_155111_.beamSections = p_155111_.checkingBeamSections;
            if (!p_155108_.isClientSide) {
                boolean flag1 = p_155111_.levels > 0;
                if (!flag && flag1) {
                    playSound(p_155108_, p_155109_, SoundEvents.BEACON_ACTIVATE);

                    for (ServerPlayer serverplayer : p_155108_.getEntitiesOfClass(
                        ServerPlayer.class, new AABB((double)i, (double)j, (double)k, (double)i, (double)(j - 4), (double)k).inflate(10.0, 5.0, 10.0)
                    )) {
                        CriteriaTriggers.CONSTRUCT_BEACON.trigger(serverplayer, p_155111_.levels);
                    }
                } else if (flag && !flag1) {
                    playSound(p_155108_, p_155109_, SoundEvents.BEACON_DEACTIVATE);
                }
            }
        }
    }

    private static int updateBase(Level p_155093_, int p_155094_, int p_155095_, int p_155096_) {
        int i = 0;

        for (int j = 1; j <= 4; i = j++) {
            int k = p_155095_ - j;
            if (k < p_155093_.getMinBuildHeight()) {
                break;
            }

            boolean flag = true;

            for (int l = p_155094_ - j; l <= p_155094_ + j && flag; l++) {
                for (int i1 = p_155096_ - j; i1 <= p_155096_ + j; i1++) {
                    if (!p_155093_.getBlockState(new BlockPos(l, k, i1)).is(BlockTags.BEACON_BASE_BLOCKS)) {
                        flag = false;
                        break;
                    }
                }
            }

            if (!flag) {
                break;
            }
        }

        return i;
    }

    @Override
    public void setRemoved() {
        // Paper start - beacon activation/deactivation events
        org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(level, worldPosition);
        new io.papermc.paper.event.block.BeaconDeactivatedEvent(block).callEvent();
        // Paper end - beacon activation/deactivation events
        // Paper start - fix MC-153086
        if (this.levels > 0 && !this.beamSections.isEmpty()) {
            playSound(this.level, this.worldPosition, SoundEvents.BEACON_DEACTIVATE);
        }
        // Paper end
        super.setRemoved();
    }

    private static @Nullable BeaconBlockEntity applyEffects$blockEntity = null;
    private static void applyEffects(
        Level p_155098_, BlockPos p_155099_, int p_155100_, @Nullable Holder<MobEffect> p_316599_, @Nullable Holder<MobEffect> p_316343_
    ) {
        if (!p_155098_.isClientSide && p_316599_ != null) {
            double d0 = (double)(p_155100_ * 10 + 10);
            int i = 0;
            if (p_155100_ >= 4 && Objects.equals(p_316599_, p_316343_)) {
                i = 1;
            }

            int j = (9 + p_155100_ * 2) * 20;
            AABB aabb = new AABB(p_155099_).inflate(d0).expandTowards(0.0, (double)p_155098_.getHeight(), 0.0);
            List<Player> list = getHumansInRange(p_155098_, p_155099_, p_155100_, applyEffects$blockEntity); // Paper - Custom beacon ranges
            applyEffects$blockEntity = null;
            for (Player player : list) {
                player.addEffect(new MobEffectInstance(p_316599_, j, i, true, true));
            }

            if (p_155100_ >= 4 && !Objects.equals(p_316599_, p_316343_) && p_316343_ != null) {
                for (Player player1 : list) {
                    player1.addEffect(new MobEffectInstance(p_316343_, j, 0, true, true));
                }
            }
        }
    }
    private static void applyEffectsPaper(Level p_155098_, BlockPos p_155099_, int p_155100_, @Nullable Holder<MobEffect> p_316599_, @Nullable Holder<MobEffect> p_316343_, @Nullable BeaconBlockEntity blockEntity) {
        applyEffects$blockEntity = blockEntity;
        applyEffects(p_155098_, p_155099_, p_155100_, p_316599_, p_316343_);
        // Paper end - Custom beacon ranges
    }

    public static void playSound(Level p_155104_, BlockPos p_155105_, SoundEvent p_155106_) {
        p_155104_.playSound(null, p_155105_, p_155106_, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    public List<BeaconBlockEntity.BeaconBeamSection> getBeamSections() {
        return (List<BeaconBlockEntity.BeaconBeamSection>)(this.levels == 0 ? ImmutableList.of() : this.beamSections);
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider p_324570_) {
        return this.saveCustomOnly(p_324570_);
    }

    private static void storeEffect(CompoundTag p_298214_, String p_298983_, @Nullable Holder<MobEffect> p_316689_) {
        if (p_316689_ != null) {
            p_316689_.unwrapKey().ifPresent(p_316401_ -> p_298214_.putString(p_298983_, p_316401_.location().toString()));
        }
    }

    @Nullable
    private static Holder<MobEffect> loadEffect(CompoundTag p_298570_, String p_299310_) {
        if (p_298570_.contains(p_299310_, 8)) {
            ResourceLocation resourcelocation = ResourceLocation.tryParse(p_298570_.getString(p_299310_));
            return resourcelocation == null ? null : BuiltInRegistries.MOB_EFFECT.getHolder(resourcelocation).orElse(null); // CraftBukkit - persist manually set non-default beacon effects (SPIGOT-3598)
        } else {
            return null;
        }
    }

    @Override
    protected void loadAdditional(CompoundTag p_338669_, HolderLookup.Provider p_338291_) {
        super.loadAdditional(p_338669_, p_338291_);
        this.primaryPower = loadEffect(p_338669_, "primary_effect");
        this.secondaryPower = loadEffect(p_338669_, "secondary_effect");
        this.levels = p_338669_.getInt("Levels"); // CraftBukkit - SPIGOT-5053, use where available
        if (p_338669_.contains("CustomName", 8)) {
            this.name = parseCustomNameSafe(p_338669_.getString("CustomName"), p_338291_);
        }

        this.lockKey = LockCode.fromTag(p_338669_);
        this.effectRange = p_338669_.contains(PAPER_RANGE_TAG, 6) ? p_338669_.getDouble(PAPER_RANGE_TAG) : -1; // Paper - Custom beacon ranges
    }

    @Override
    protected void saveAdditional(CompoundTag p_187463_, HolderLookup.Provider p_324268_) {
        super.saveAdditional(p_187463_, p_324268_);
        storeEffect(p_187463_, "primary_effect", this.primaryPower);
        storeEffect(p_187463_, "secondary_effect", this.secondaryPower);
        p_187463_.putInt("Levels", this.levels);
        if (this.name != null) {
            p_187463_.putString("CustomName", Component.Serializer.toJson(this.name, p_324268_));
        }

        this.lockKey.addToTag(p_187463_);
        p_187463_.putDouble(PAPER_RANGE_TAG, this.effectRange); // Paper - Custom beacon ranges
    }

    public void setCustomName(@Nullable Component p_58682_) {
        this.name = p_58682_;
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int p_58696_, Inventory p_58697_, Player p_58698_) {
        return BaseContainerBlockEntity.canUnlock(p_58698_, this.lockKey, this.getDisplayName(), this)
            ? new BeaconMenu(p_58696_, p_58697_, this.dataAccess, ContainerLevelAccess.create(this.level, this.getBlockPos()))
            : null;
    }

    @Override
    public Component getDisplayName() {
        return this.getName();
    }

    @Override
    public Component getName() {
        return this.name != null ? this.name : DEFAULT_NAME;
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput p_338364_) {
        super.applyImplicitComponents(p_338364_);
        this.name = p_338364_.get(DataComponents.CUSTOM_NAME);
        this.lockKey = p_338364_.getOrDefault(DataComponents.LOCK, LockCode.NO_LOCK);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder p_338239_) {
        super.collectImplicitComponents(p_338239_);
        p_338239_.set(DataComponents.CUSTOM_NAME, this.name);
        if (!this.lockKey.equals(LockCode.NO_LOCK)) {
            p_338239_.set(DataComponents.LOCK, this.lockKey);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag p_331401_) {
        p_331401_.remove("CustomName");
        p_331401_.remove("Lock");
    }

    @Override
    public void setLevel(Level p_155091_) {
        super.setLevel(p_155091_);
        this.lastCheckY = p_155091_.getMinBuildHeight() - 1;
    }

    public static class BeaconBeamSection {
        final int color;
        private int height;

        public BeaconBeamSection(int p_350966_) {
            this.color = p_350966_;
            this.height = 1;
        }

        protected void increaseHeight() {
            this.height++;
        }

        public int getColor() {
            return this.color;
        }

        public int getHeight() {
            return this.height;
        }
    }

    // CraftBukkit start - add fields and methods
    public PotionEffect getPrimaryEffect() {
        return (this.primaryPower != null) ? CraftPotionUtil.toBukkit(new MobEffectInstance(this.primaryPower, getLevel(this.levels), getAmplification(levels, primaryPower, secondaryPower), true, true)) : null;
    }

    public PotionEffect getSecondaryEffect() {
        return (hasSecondaryEffect(levels, primaryPower, secondaryPower)) ? CraftPotionUtil.toBukkit(new MobEffectInstance(this.secondaryPower, getLevel(this.levels), getAmplification(levels, primaryPower, secondaryPower), true, true)) : null;
    }
    // CraftBukkit end
    // Paper start - Custom beacon ranges
    private final String PAPER_RANGE_TAG = "Paper.Range";
    private double effectRange = -1;

    public double getEffectRange() {
        if (this.effectRange < 0) {
            return this.levels * 10 + 10;
        } else {
            return effectRange;
        }
    }

    public void setEffectRange(double range) {
        this.effectRange = range;
    }

    public void resetEffectRange() {
        this.effectRange = -1;
    }
    // Paper end - Custom beacon ranges

    public static List getHumansInRange(Level world, BlockPos blockposition, int i) {
        // Paper start - Custom beacon ranges
        return BeaconBlockEntity.getHumansInRange(world, blockposition, i, null);
    }
    public static List getHumansInRange(Level world, BlockPos blockposition, int i, @Nullable BeaconBlockEntity blockEntity) {
        // Paper end - Custom beacon ranges
        double d0 = blockEntity != null ? blockEntity.getEffectRange() : (i * 10 + 10); // Paper - Custom beacon ranges
        AABB axisalignedbb = (new AABB(blockposition)).inflate(d0).expandTowards(0.0D, (double) world.getHeight(), 0.0D);
        // Paper start - Perf: optimize player lookup for beacons
        List<Player> list;
        if (d0 <= 128.0) {
            list = world.getEntitiesOfClass(Player.class, axisalignedbb);
        } else {
            list = new java.util.ArrayList<>();
            for (Player player : world.players()) {
                if (player.isSpectator()) {
                    continue;
                }
                if (player.getBoundingBox().intersects(axisalignedbb)) {
                    list.add(player);
                }
            }
        }
        // Paper end - Perf: optimize player lookup for beacons
        return list;
    }

    private static byte getAmplification(int i, @Nullable Holder<MobEffect> holder, @Nullable Holder<MobEffect> holder1) {
        byte b0 = 0;
        if (i >= 4 && Objects.equals(holder, holder1)) {
            b0 = 1;
        }
        return b0;
    }

    private static boolean hasSecondaryEffect(int i, @Nullable Holder<MobEffect> holder, @Nullable Holder<MobEffect> holder1) {
        if (i >= 4 && !Objects.equals(holder, holder1) && holder1 != null) {
            return true;
        }
        return false;
    }

    private static int getLevel(int i) {
        return  (9 + i * 2) * 20;
    }
}
