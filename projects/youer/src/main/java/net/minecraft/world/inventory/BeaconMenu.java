package net.minecraft.world.inventory;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.craftbukkit.inventory.CraftInventoryBeacon;
import org.bukkit.craftbukkit.inventory.view.CraftBeaconView;

public class BeaconMenu extends AbstractContainerMenu {
    private static final int PAYMENT_SLOT = 0;
    private static final int SLOT_COUNT = 1;
    private static final int DATA_COUNT = 3;
    private static final int INV_SLOT_START = 1;
    private static final int INV_SLOT_END = 28;
    private static final int USE_ROW_SLOT_START = 28;
    private static final int USE_ROW_SLOT_END = 37;
    private static final int NO_EFFECT = 0;
    private final Container beacon = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int p_39066_, ItemStack p_39067_) {
            return p_39067_.is(ItemTags.BEACON_PAYMENT_ITEMS);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
        // Paper start - Fix inventories returning null Locations
        @Override
        public org.bukkit.Location getLocation() {
            return access.getLocation();
        }
        // Paper end - Fix inventories returning null Locations

    };
    private final BeaconMenu.PaymentSlot paymentSlot;
    private final ContainerLevelAccess access;
    private final ContainerData beaconData;
    // CraftBukkit start
    private CraftBeaconView bukkitEntity = null;
    private Inventory player;
    @Override
    public CraftBeaconView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventoryBeacon inventory = new CraftInventoryBeacon(this.beacon);
        bukkitEntity = new CraftBeaconView(this.player.player.getBukkitEntity(), inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end

    public BeaconMenu(int p_39036_, Container p_39037_) {
        this(p_39036_, p_39037_, new SimpleContainerData(3), ContainerLevelAccess.NULL);
    }

    public BeaconMenu(int p_39039_, Container p_39040_, ContainerData p_39041_, ContainerLevelAccess p_39042_) {
        super(MenuType.BEACON, p_39039_);
        checkContainerDataCount(p_39041_, 3);
        this.player = (Inventory) p_39040_; // CraftBukkit - TODO: check this
        this.beaconData = p_39041_;
        this.access = p_39042_;
        this.paymentSlot = new BeaconMenu.PaymentSlot(this.beacon, 0, 136, 110);
        this.addSlot(this.paymentSlot);
        this.addDataSlots(p_39041_);
        int i = 36;
        int j = 137;

        for (int k = 0; k < 3; k++) {
            for (int l = 0; l < 9; l++) {
                this.addSlot(new Slot(p_39040_, l + k * 9 + 9, 36 + l * 18, 137 + k * 18));
            }
        }

        for (int i1 = 0; i1 < 9; i1++) {
            this.addSlot(new Slot(p_39040_, i1, 36 + i1 * 18, 195));
        }
    }

    @Override
    public void removed(Player p_39049_) {
        super.removed(p_39049_);
        if (!p_39049_.level().isClientSide) {
            ItemStack itemstack = this.paymentSlot.remove(this.paymentSlot.getMaxStackSize());
            if (!itemstack.isEmpty()) {
                p_39049_.drop(itemstack, false);
            }
        }
    }

    @Override
    public boolean stillValid(Player p_39047_) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, p_39047_, Blocks.BEACON);
    }

    @Override
    public void setData(int p_39044_, int p_39045_) {
        super.setData(p_39044_, p_39045_);
        this.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player p_39051_, int p_39052_) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(p_39052_);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (p_39052_ == 0) {
                if (!this.moveItemStackTo(itemstack1, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else if (this.moveItemStackTo(itemstack1, 0, 1, false)) { //Forge Fix Shift Clicking in beacons with stacks larger then 1.
                return ItemStack.EMPTY;
            } else if (p_39052_ >= 1 && p_39052_ < 28) {
                if (!this.moveItemStackTo(itemstack1, 28, 37, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (p_39052_ >= 28 && p_39052_ < 37) {
                if (!this.moveItemStackTo(itemstack1, 1, 28, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 1, 37, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(p_39051_, itemstack1);
        }

        return itemstack;
    }

    public int getLevels() {
        return this.beaconData.get(0);
    }

    public static int encodeEffect(@Nullable Holder<MobEffect> p_316580_) {
        return p_316580_ == null ? 0 : BuiltInRegistries.MOB_EFFECT.asHolderIdMap().getId(p_316580_) + 1;
    }

    @Nullable
    public static Holder<MobEffect> decodeEffect(int p_298318_) {
        return p_298318_ == 0 ? null : BuiltInRegistries.MOB_EFFECT.asHolderIdMap().byId(p_298318_ - 1);
    }

    @Nullable
    public Holder<MobEffect> getPrimaryEffect() {
        return decodeEffect(this.beaconData.get(1));
    }

    @Nullable
    public Holder<MobEffect> getSecondaryEffect() {
        return decodeEffect(this.beaconData.get(2));
    }

    // Paper start - Add PlayerChangeBeaconEffectEvent
    private static @Nullable org.bukkit.potion.PotionEffectType convert(Optional<Holder<MobEffect>> optionalEffect) {
        return optionalEffect.map(org.bukkit.craftbukkit.potion.CraftPotionEffectType::minecraftHolderToBukkit).orElse(null);
    }
    // Paper end - Add PlayerChangeBeaconEffectEvent

    public void updateEffects(Optional<Holder<MobEffect>> p_219973_, Optional<Holder<MobEffect>> p_219974_) {
        // Paper start - fix MC-174630 - validate secondary power
        if (p_219974_.isPresent() && p_219974_.get() != net.minecraft.world.effect.MobEffects.REGENERATION && (p_219973_.isPresent() && p_219974_.get() != p_219973_.get())) {
            p_219974_ = Optional.empty();
        }
        // Paper end
        if (this.paymentSlot.hasItem()) {
            // Paper start - Add PlayerChangeBeaconEffectEvent
            io.papermc.paper.event.player.PlayerChangeBeaconEffectEvent event = new io.papermc.paper.event.player.PlayerChangeBeaconEffectEvent((org.bukkit.entity.Player) this.player.player.getBukkitEntity(), convert(p_219973_), convert(p_219974_), this.access.getLocation().getBlock());
            if (event.callEvent()) {
                // Paper end - Add PlayerChangeBeaconEffectEvent
                this.beaconData.set(1, BeaconMenu.encodeEffect(event.getPrimary() == null ? null : org.bukkit.craftbukkit.potion.CraftPotionEffectType.bukkitToMinecraftHolder(event.getPrimary())));// CraftBukkit - decompile error
                this.beaconData.set(2, BeaconMenu.encodeEffect(event.getSecondary() == null ? null : org.bukkit.craftbukkit.potion.CraftPotionEffectType.bukkitToMinecraftHolder(event.getSecondary())));// CraftBukkit - decompile error
                if (event.willConsumeItem()) { // Paper
                    this.paymentSlot.remove(1);
                } // Paper
                this.access.execute(Level::blockEntityChanged);
            } // Paper end - Add PlayerChangeBeaconEffectEvent
        }
    }

    public boolean hasPayment() {
        return !this.beacon.getItem(0).isEmpty();
    }

    class PaymentSlot extends Slot {
        public PaymentSlot(Container p_39071_, int p_39072_, int p_39073_, int p_39074_) {
            super(p_39071_, p_39072_, p_39073_, p_39074_);
        }

        @Override
        public boolean mayPlace(ItemStack p_39077_) {
            return p_39077_.is(ItemTags.BEACON_PAYMENT_ITEMS);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
