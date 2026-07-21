package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.inventory.CraftInventoryLectern;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;

public class LecternMenu extends AbstractContainerMenu {
    private static final int DATA_COUNT = 1;
    private static final int SLOT_COUNT = 1;
    public static final int BUTTON_PREV_PAGE = 1;
    public static final int BUTTON_NEXT_PAGE = 2;
    public static final int BUTTON_TAKE_BOOK = 3;
    public static final int BUTTON_PAGE_JUMP_RANGE_START = 100;
    private final Container lectern;
    private final ContainerData lecternData;

    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private org.bukkit.entity.Player player;

    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventoryLectern inventory = new CraftInventoryLectern(this.lectern);
        bukkitEntity = new CraftInventoryView(this.player, inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end

    public LecternMenu(int p_39822_) {
        this(p_39822_, new SimpleContainer(1), new SimpleContainerData(1));
    }

    public LecternMenu(int p_39824_, Container p_39825_, ContainerData p_39826_) {
        super(MenuType.LECTERN, p_39824_);
        checkContainerSize(p_39825_, 1);
        checkContainerDataCount(p_39826_, 1);
        this.lectern = p_39825_;
        this.lecternData = p_39826_;
        this.addSlot(new Slot(p_39825_, 0, 0, 0) {
            @Override
            public void setChanged() {
                super.setChanged();
                LecternMenu.this.slotsChanged(this.container);
            }
        });
        this.addDataSlots(p_39826_);
    }

    // Youer start
    public LecternMenu player(Inventory playerinventory) {
        player = (org.bukkit.entity.Player) playerinventory.player.getBukkitEntity(); // CraftBukkit
        return this;
    }

    public LecternMenu(int pContainerId, Container pLectern, ContainerData pLecternData, Inventory playerinventory) {
        super(MenuType.LECTERN, pContainerId);
        checkContainerSize(pLectern, 1);
        checkContainerDataCount(pLecternData, 1);
        this.lectern = pLectern;
        this.lecternData = pLecternData;
        this.addSlot(new Slot(pLectern, 0, 0, 0) {
            public void setChanged() {
                super.setChanged();
                LecternMenu.this.slotsChanged(this.container);
            }
        });
        this.addDataSlots(pLecternData);
        player = (org.bukkit.entity.Player) playerinventory.player.getBukkitEntity(); // CraftBukkit
    }
    // Youer end

    @Override
    public boolean clickMenuButton(Player p_39833_, int p_39834_) {
        io.papermc.paper.event.player.PlayerLecternPageChangeEvent playerLecternPageChangeEvent; CraftInventoryLectern bukkitView; // Paper - Add PlayerLecternPageChangeEvent
        if (p_39834_ >= 100) {
            int k = p_39834_ - 100;
            this.setData(0, k);
            return true;
        } else {
            switch (p_39834_) {
                case 1:
                    int j = this.lecternData.get(0);
                    // Paper start - Add PlayerLecternPageChangeEvent
                    bukkitView = (CraftInventoryLectern) getBukkitView().getTopInventory();
                    playerLecternPageChangeEvent = new io.papermc.paper.event.player.PlayerLecternPageChangeEvent((org.bukkit.entity.Player) p_39833_.getBukkitEntity(), bukkitView.getHolder(), bukkitView.getBook(), io.papermc.paper.event.player.PlayerLecternPageChangeEvent.PageChangeDirection.LEFT, j, j - 1);
                    if (!playerLecternPageChangeEvent.callEvent()) {
                        return false;
                    }
                    this.setData(0, playerLecternPageChangeEvent.getNewPage());
                    // Paper end - Add PlayerLecternPageChangeEvent
                    return true;
                case 2:
                    int i = this.lecternData.get(0);
                    // Paper start - Add PlayerLecternPageChangeEvent
                    bukkitView = (CraftInventoryLectern) getBukkitView().getTopInventory();
                    playerLecternPageChangeEvent = new io.papermc.paper.event.player.PlayerLecternPageChangeEvent((org.bukkit.entity.Player) p_39833_.getBukkitEntity(), bukkitView.getHolder(), bukkitView.getBook(), io.papermc.paper.event.player.PlayerLecternPageChangeEvent.PageChangeDirection.RIGHT, i, i + 1);
                    if (!playerLecternPageChangeEvent.callEvent()) {
                        return false;
                    }
                    this.setData(0, playerLecternPageChangeEvent.getNewPage());
                    // Paper end - Add PlayerLecternPageChangeEvent
                    return true;
                case 3:
                    if (!p_39833_.mayBuild()) {
                        return false;
                    }
                    // CraftBukkit start - Event for taking the book
                    PlayerTakeLecternBookEvent event = new PlayerTakeLecternBookEvent((org.bukkit.entity.Player) p_39833_.getBukkitEntity(), ((CraftInventoryLectern) getBukkitView().getTopInventory()).getHolder());
                    Bukkit.getServer().getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        return false;
                    }
                    // CraftBukkit end
                    ItemStack itemstack = this.lectern.removeItemNoUpdate(0);
                    this.lectern.setChanged();
                    if (!p_39833_.getInventory().add(itemstack)) {
                        p_39833_.drop(itemstack, false);
                    }

                    return true;
                default:
                    return false;
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player p_219987_, int p_219988_) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setData(int p_39828_, int p_39829_) {
        super.setData(p_39828_, p_39829_);
        this.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player p_39831_) {
        if (lectern instanceof LecternBlockEntity.LecternInventory && !((LecternBlockEntity.LecternInventory) lectern).getLectern().hasBook()) return false; // CraftBukkit
        if (!this.checkReachable) return true; // CraftBukkit
        return this.lectern.stillValid(p_39831_);
    }

    public ItemStack getBook() {
        return this.lectern.getItem(0);
    }

    public int getPage() {
        return this.lecternData.get(0);
    }
}
