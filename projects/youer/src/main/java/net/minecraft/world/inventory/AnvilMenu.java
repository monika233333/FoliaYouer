package net.minecraft.world.inventory;

import com.mohistmc.youer.YouerConfig;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftInventoryAnvil;
import org.bukkit.craftbukkit.inventory.view.CraftAnvilView;
import org.slf4j.Logger;

public class AnvilMenu extends ItemCombinerMenu {
    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_COST = false;
    public static final int MAX_NAME_LENGTH = 50;
    public int repairItemCountCost;
    @Nullable
    public String itemName;
    public final DataSlot cost = DataSlot.standalone();
    private static final int COST_FAIL = 0;
    private static final int COST_BASE = 1;
    private static final int COST_ADDED_BASE = 1;
    private static final int COST_REPAIR_MATERIAL = 1;
    private static final int COST_REPAIR_SACRIFICE = 2;
    private static final int COST_INCOMPATIBLE_PENALTY = 1;
    private static final int COST_RENAME = 1;
    private static final int INPUT_SLOT_X_PLACEMENT = 27;
    private static final int ADDITIONAL_SLOT_X_PLACEMENT = 76;
    private static final int RESULT_SLOT_X_PLACEMENT = 134;
    private static final int SLOT_Y_PLACEMENT = 47;
    // CraftBukkit start
    public static final int DEFAULT_DENIED_COST = -1;
    public int maximumRepairCost = Math.min(Short.MAX_VALUE, Math.max(41, YouerConfig.maximumRepairCost));
    private CraftAnvilView bukkitEntity;
    // CraftBukkit end
    public boolean bypassEnchantmentLevelRestriction = false; // Paper - bypass anvil level restrictions
    // Purpur start - Anvil API
    public boolean bypassCost = false;
    public boolean canDoUnsafeEnchants = false;
    // Purpur end - Anvil API

    private int getMaxMumRepairCost(int value) {
        if (value != 40) {
            return value;
        } else {
            return this.maximumRepairCost;
        }
    }

    @Override
    public CraftAnvilView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventoryAnvil inventory = new CraftInventoryAnvil(
                access.getLocation(), this.inputSlots, this.resultSlots);
        bukkitEntity = new CraftAnvilView(this.player.getBukkitEntity(), inventory, this);
        bukkitEntity.updateFromLegacy(inventory);
        return bukkitEntity;
    }
    // CraftBukkit end

    public AnvilMenu(int p_39005_, Inventory p_39006_) {
        this(p_39005_, p_39006_, ContainerLevelAccess.NULL);
    }

    public AnvilMenu(int p_39008_, Inventory p_39009_, ContainerLevelAccess p_39010_) {
        super(MenuType.ANVIL, p_39008_, p_39009_, p_39010_);
        this.addDataSlot(this.cost);
    }

    @Override
    protected ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create()
            .withSlot(0, 27, 47, p_266635_ -> true)
            .withSlot(1, 76, 47, p_266634_ -> true)
            .withResultSlot(2, 134, 47)
            .build();
    }

    @Override
    protected boolean isValidBlock(BlockState p_39019_) {
        return p_39019_.is(BlockTags.ANVIL);
    }

    @Override
    protected boolean mayPickup(Player p_39023_, boolean p_39024_) {
        return (p_39023_.hasInfiniteMaterials() || p_39023_.experienceLevel >= this.cost.get()) && (this.bypassCost || this.cost.get() > AnvilMenu.DEFAULT_DENIED_COST) && p_39024_; // CraftBukkit - allow cost 0 like a free item
    }

    @Override
    protected void onTake(Player p_150474_, ItemStack p_150475_) {
        // Purpur start - Anvil API
        ItemStack purpur$itemstack = this.activeQuickItem != null ? this.activeQuickItem : p_150475_;
        if (org.purpurmc.purpur.event.inventory.AnvilTakeResultEvent.getHandlerList().getRegisteredListeners().length > 0) new org.purpurmc.purpur.event.inventory.AnvilTakeResultEvent(
                p_150474_.getBukkitEntity(),
                getBukkitView(),
                org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(purpur$itemstack)
        ).callEvent();
        // Purpur end - Anvil API
        if (!p_150474_.getAbilities().instabuild) {
            if (this.bypassCost) ((ServerPlayer) p_150474_).lastSentExp = -1; else // Purpur - Anvil API
            p_150474_.giveExperienceLevels(-this.cost.get());
        }

        float breakChance = net.neoforged.neoforge.common.CommonHooks.onAnvilRepair(p_150474_, p_150475_, AnvilMenu.this.inputSlots.getItem(0), AnvilMenu.this.inputSlots.getItem(1));

        this.inputSlots.setItem(0, ItemStack.EMPTY);
        if (this.repairItemCountCost > 0) {
            ItemStack itemstack = this.inputSlots.getItem(1);
            if (!itemstack.isEmpty() && itemstack.getCount() > this.repairItemCountCost) {
                itemstack.shrink(this.repairItemCountCost);
                this.inputSlots.setItem(1, itemstack);
            } else {
                this.inputSlots.setItem(1, ItemStack.EMPTY);
            }
        } else {
            this.inputSlots.setItem(1, ItemStack.EMPTY);
        }

        this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
        this.access.execute((p_150479_, p_150480_) -> {
            BlockState blockstate = p_150479_.getBlockState(p_150480_);
            if (!p_150474_.getAbilities().instabuild && blockstate.is(BlockTags.ANVIL) && p_150474_.getRandom().nextFloat() < breakChance) {
                BlockState blockstate1 = AnvilBlock.damage(blockstate);
                // Paper start - AnvilDamageEvent
                com.destroystokyo.paper.event.block.AnvilDamagedEvent event = new com.destroystokyo.paper.event.block.AnvilDamagedEvent(getBukkitView(), blockstate1 != null ? org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(blockstate1) : null);
                if (!event.callEvent()) {
                    return;
                } else if (event.getDamageState() == com.destroystokyo.paper.event.block.AnvilDamagedEvent.DamageState.BROKEN) {
                    blockstate1 = null;
                } else {
                    blockstate1 = ((org.bukkit.craftbukkit.block.data.CraftBlockData) event.getDamageState().getMaterial().createBlockData()).getState().setValue(AnvilBlock.FACING, blockstate.getValue(AnvilBlock.FACING));
                }
                // Paper end - AnvilDamageEvent
                if (blockstate1 == null) {
                    p_150479_.removeBlock(p_150480_, false);
                    p_150479_.levelEvent(1029, p_150480_, 0);
                } else {
                    p_150479_.setBlock(p_150480_, blockstate1, 2);
                    p_150479_.levelEvent(1030, p_150480_, 0);
                }
            } else {
                p_150479_.levelEvent(1030, p_150480_, 0);
            }
        });
    }

    @Override
    public void createResult() {
        // Purpur start - Anvil API
        this.bypassCost = false;
        this.canDoUnsafeEnchants = false;
        if (org.purpurmc.purpur.event.inventory.AnvilUpdateResultEvent.getHandlerList().getRegisteredListeners().length > 0) new org.purpurmc.purpur.event.inventory.AnvilUpdateResultEvent(getBukkitView()).callEvent();
        // Purpur end - Anvil API
        ItemStack itemstack = this.inputSlots.getItem(0);
        this.cost.set(1);
        int i = 0;
        long j = 0L;
        int k = 0;
        if (!itemstack.isEmpty()) {  // don't fire the event if the left input slot is empty, because anvil shouldn't have recipes with an empty left slot
            if (!net.neoforged.neoforge.common.CommonHooks.onAnvilChange(this, itemstack, this.inputSlots.getItem(1), resultSlots, itemName, j, this.player)) {
                return;  // event is canceled or overrides the output item
            }
        }
        if (!itemstack.isEmpty() && this.canDoUnsafeEnchants || EnchantmentHelper.canStoreEnchantments(itemstack)) {
            ItemStack itemstack1 = itemstack.copy();
            ItemStack itemstack2 = this.inputSlots.getItem(1);
            ItemEnchantments.Mutable itemenchantments$mutable = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(itemstack1));
            j += (long)itemstack.getOrDefault(DataComponents.REPAIR_COST, Integer.valueOf(0)).intValue()
                + (long)itemstack2.getOrDefault(DataComponents.REPAIR_COST, Integer.valueOf(0)).intValue();
            this.repairItemCountCost = 0;
            boolean flag = false;
            if (!itemstack2.isEmpty()) {
                flag = itemstack2.has(DataComponents.STORED_ENCHANTMENTS);
                if (itemstack1.isDamageableItem() && itemstack1.getItem().isValidRepairItem(itemstack, itemstack2)) {
                    int l2 = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);
                    if (l2 <= 0) {
                        CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }

                    int j3;
                    for (j3 = 0; l2 > 0 && j3 < itemstack2.getCount(); j3++) {
                        int k3 = itemstack1.getDamageValue() - l2;
                        itemstack1.setDamageValue(k3);
                        i++;
                        l2 = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);
                    }

                    this.repairItemCountCost = j3;
                } else {
                    if (!flag && (!itemstack1.is(itemstack2.getItem()) || !itemstack1.isDamageableItem())) {
                        CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item

                        return;
                    }

                    if (itemstack1.isDamageableItem() && !flag) {
                        int l = itemstack.getMaxDamage() - itemstack.getDamageValue();
                        int i1 = itemstack2.getMaxDamage() - itemstack2.getDamageValue();
                        int j1 = i1 + itemstack1.getMaxDamage() * 12 / 100;
                        int k1 = l + j1;
                        int l1 = itemstack1.getMaxDamage() - k1;
                        if (l1 < 0) {
                            l1 = 0;
                        }

                        if (l1 < itemstack1.getDamageValue()) {
                            itemstack1.setDamageValue(l1);
                            i += 2;
                        }
                    }

                    ItemEnchantments itemenchantments = EnchantmentHelper.getEnchantmentsForCrafting(itemstack2);
                    boolean flag2 = false;
                    boolean flag3 = false;

                    for (Entry<Holder<Enchantment>> entry : itemenchantments.entrySet()) {
                        Holder<Enchantment> holder = entry.getKey();
                        int i2 = itemenchantments$mutable.getLevel(holder);
                        int j2 = entry.getIntValue();
                        j2 = i2 == j2 ? j2 + 1 : Math.max(j2, i2);
                        Enchantment enchantment = holder.value();
                        // Neo: Respect IItemExtension#supportsEnchantment - we also delegate the logic for Enchanted Books to this method.
                        // Though we still allow creative players to combine any item with any enchantment in the anvil here.
                        // Purpur start - Config to allow unsafe enchants
                        boolean flag1 = this.canDoUnsafeEnchants || org.purpurmc.purpur.PurpurConfig.allowInapplicableEnchants || itemstack.supportsEnchantment(holder);
                        boolean flag4 = true; // whether two incompatible enchantments can be applied on a single item
                        // Purpur end - Config to allow unsafe enchants
                        if (this.player.getAbilities().instabuild) {
                            flag1 = true;
                        }

                        Iterator iterator1 = itemenchantments$mutable.keySet().iterator();

                        while (iterator1.hasNext()) {
                            Holder<Enchantment> holder1 = (Holder) iterator1.next();

                            if (!holder1.equals(holder) && !Enchantment.areCompatible(holder, holder1)) {
                                flag4 = this.canDoUnsafeEnchants || org.purpurmc.purpur.PurpurConfig.allowIncompatibleEnchants; // Purpur - Anvil API // Purpur - flag3 -> flag4 - Config to allow unsafe enchants
                                // Purpur start - Config to allow unsafe enchants
                                if (!flag4 && org.purpurmc.purpur.PurpurConfig.replaceIncompatibleEnchants) {
                                    iterator1.remove(); // replace current enchant with the incompatible one trying to be applied
                                    flag4 = true;
                                }
                                // Purpur end - Config to allow unsafe enchants
                                i++;
                            }
                        }

                        if (!flag1 || !flag4) { // Purpur - Config to allow unsafe enchants
                            flag3 = true;
                        } else {
                            flag2 = true;
                            if (!org.purpurmc.purpur.PurpurConfig.allowHigherEnchantsLevels && j2 > enchantment.getMaxLevel() && !this.bypassEnchantmentLevelRestriction) { // Paper - bypass anvil level restrictions
                                j2 = enchantment.getMaxLevel();
                            }

                            itemenchantments$mutable.set(holder, j2);
                            int l3 = enchantment.getAnvilCost();
                            if (flag) {
                                l3 = Math.max(1, l3 / 2);
                            }

                            i += l3 * j2;
                            if (itemstack.getCount() > 1) {
                                i = 40;
                            }
                        }
                    }

                    if (flag3 && !flag2) {
                        CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }
                }
            }

            if (this.itemName != null && !StringUtil.isBlank(this.itemName)) {
                if (!this.itemName.equals(itemstack.getHoverName().getString())) {
                    k = 1;
                    i += k;
                    // Purpur start
                    if (this.player != null) {
                        org.bukkit.craftbukkit.entity.CraftHumanEntity player = this.player.getBukkitEntity();
                        String name = this.itemName;
                        boolean removeItalics = false;
                        if (player.hasPermission("purpur.anvil.remove_italics")) {
                            if (name.startsWith("&r")) {
                                name = name.substring(2);
                                removeItalics = true;
                            } else if (name.startsWith("<r>")) {
                                name = name.substring(3);
                                removeItalics = true;
                            } else if (name.startsWith("<reset>")) {
                                name = name.substring(7);
                                removeItalics = true;
                            }
                        }
                        if (this.player.level().purpurConfig.anvilAllowColors) {
                            if (player.hasPermission("purpur.anvil.color")) {
                                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)&([0-9a-fr])").matcher(name);
                                while (matcher.find()) {
                                    String match = matcher.group(1);
                                    name = name.replace("&" + match, "\u00a7" + match.toLowerCase(java.util.Locale.ROOT));
                                }
                                //name = name.replaceAll("(?i)&([0-9a-fr])", "\u00a7$1");
                            }
                            if (player.hasPermission("purpur.anvil.format")) {
                                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)&([k-or])").matcher(name);
                                while (matcher.find()) {
                                    String match = matcher.group(1);
                                    name = name.replace("&" + match, "\u00a7" + match.toLowerCase(java.util.Locale.ROOT));
                                }
                                //name = name.replaceAll("(?i)&([l-or])", "\u00a7$1");
                            }
                        }
                        net.kyori.adventure.text.Component component;
                        if (this.player.level().purpurConfig.anvilColorsUseMiniMessage && player.hasPermission("purpur.anvil.minimessage")) {
                            component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(org.bukkit.ChatColor.stripColor(name));
                        } else {
                            component = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(name);
                        }
                        if (removeItalics) {
                            component = component.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
                        }
                        itemstack1.set(DataComponents.CUSTOM_NAME, io.papermc.paper.adventure.PaperAdventure.asVanilla(component));
                    }
                    else
                    // Purpur end
                    itemstack1.set(DataComponents.CUSTOM_NAME, Component.literal(this.itemName));
                }
            } else if (itemstack.has(DataComponents.CUSTOM_NAME)) {
                k = 1;
                i += k;
                itemstack1.remove(DataComponents.CUSTOM_NAME);
            }
            if (flag && !itemstack1.isBookEnchantable(itemstack2)) itemstack1 = ItemStack.EMPTY;

            int k2 = (int)Mth.clamp(j + (long)i, 0L, 2147483647L);
            this.cost.set(k2);
            if (i <= 0) {
                itemstack1 = ItemStack.EMPTY;
            }

            if (k == i && k > 0 && this.cost.get() >= getMaxMumRepairCost(40)) {
                this.cost.set(getMaxMumRepairCost(40) -1);
            }

            // Purpur start - Anvil API
            if (this.bypassCost && this.cost.get() >= getMaxMumRepairCost(40)) {
                this.cost.set(getMaxMumRepairCost(40) -1);
            }
            // Purpur end - Anvil API

            if (this.cost.get() >= getMaxMumRepairCost(40) && !this.player.getAbilities().instabuild) {
                itemstack1 = ItemStack.EMPTY;
            }

            if (!itemstack1.isEmpty()) {
                int i3 = itemstack1.getOrDefault(DataComponents.REPAIR_COST, Integer.valueOf(0));
                if (i3 < itemstack2.getOrDefault(DataComponents.REPAIR_COST, Integer.valueOf(0))) {
                    i3 = itemstack2.getOrDefault(DataComponents.REPAIR_COST, Integer.valueOf(0));
                }

                if (k != i || k == 0) {
                    i3 = calculateIncreasedRepairCost(i3);
                }

                itemstack1.set(DataComponents.REPAIR_COST, i3);
                EnchantmentHelper.setEnchantments(itemstack1, itemenchantments$mutable.toImmutable());
            }

            CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), itemstack1); // CraftBukkit
            sendAllDataToRemote(); // CraftBukkit - SPIGOT-6686: Always send completed inventory to stay in sync with client
            this.broadcastChanges();

            // Purpur start - Anvil API
            if ((this.canDoUnsafeEnchants || org.purpurmc.purpur.PurpurConfig.allowInapplicableEnchants || org.purpurmc.purpur.PurpurConfig.allowIncompatibleEnchants) && itemstack1 != ItemStack.EMPTY) { // Purpur - Config to allow unsafe enchants
                ((ServerPlayer) this.player).connection.send(new ClientboundContainerSetSlotPacket(this.containerId, this.incrementStateId(), 2, itemstack1));
                ((ServerPlayer) this.player).connection.send(new ClientboundContainerSetDataPacket(this.containerId, 0, this.cost.get()));
            }
            // Purpur end - Anvil API
        } else {
            CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
            this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item

        }
    }

    public static int calculateIncreasedRepairCost(int p_39026_) {
        return (int)Math.min((long)p_39026_ * 2L + 1L, 2147483647L);
    }

    public boolean setItemName(String p_288970_) {
        String s = validateName(p_288970_);
        if (s != null && !s.equals(this.itemName)) {
            this.itemName = s;
            if (this.getSlot(2).hasItem()) {
                ItemStack itemstack = this.getSlot(2).getItem();
                if (StringUtil.isBlank(s)) {
                    itemstack.remove(DataComponents.CUSTOM_NAME);
                } else {
                    itemstack.set(DataComponents.CUSTOM_NAME, Component.literal(s));
                }
            }

            this.createResult();
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(this, RESULT_SLOT); // Paper - Add PrepareResultEvent
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    private static String validateName(String p_288995_) {
        String s = StringUtil.filterText(p_288995_);
        return s.length() <= 50 ? s : null;
    }

    public int getCost() {
        return this.cost.get();
    }

    /**
     * Neo: Sets the cost. Will be clamped to an integer.
     */
    public void setMaximumCost(long value) {
        this.cost.set((int)Mth.clamp(value, 0L, Integer.MAX_VALUE));
    }
}
