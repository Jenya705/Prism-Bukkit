package me.botsko.prism.actions;

import me.botsko.prism.Prism;
import me.botsko.prism.api.ChangeResult;
import me.botsko.prism.api.ChangeResultType;
import me.botsko.prism.api.PrismParameters;
import me.botsko.prism.api.actions.PrismProcessType;
import me.botsko.prism.api.objects.MaterialState;
import me.botsko.prism.appliers.ChangeResultImpl;
import me.botsko.prism.utils.EntityUtils;
import me.botsko.prism.utils.InventoryUtils;
import me.botsko.prism.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Builder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

public class ItemStackAction extends GenericAction {

    protected ItemStack item;
    private ItemStackActionData actionData;
    private Map<Enchantment, Integer> enchantments;
    /**
     * Holds durability if no actionData yet exists.
     */
    private short tempDurability = -1;

    @Override
    public boolean hasExtraData() {
        return actionData != null;
    }

    @Override
    public short getDurability() {
        if (actionData != null) {
            return actionData.durability;
        }
        return 0;
    }

    @Override
    public void setDurability(short durability) {
        if (actionData == null) {
            tempDurability = durability;
        } else {
            actionData.durability = durability;
        }
    }

    /**
     * Set the item.
     * @param item ItemStack
     * @param quantity int
     * @param enchantments Map of enchants.
     */
    public void setItem(ItemStack item, int quantity, Map<Enchantment, Integer> enchantments) {

        if (enchantments != null) {
            this.enchantments = enchantments;
        }

        if (item == null || item.getAmount() <= 0) {
            this.setCanceled(true);
            return;
        }

        this.item = item;
        if (enchantments == null) {
            this.enchantments = item.getEnchantments();
        }

        // Set basics
        setMaterial(item.getType());
        ItemStackActionData newActionData = serializeItem(item, quantity, enchantments);
        if (actionData != null && !actionData.slot.equals("-1")) {
            newActionData.slot = actionData.slot;
        }
        actionData = newActionData;
    }

    private static ItemStackActionData serializeItem(ItemStack item, int quantity, Map<Enchantment, Integer> enchantments) {
        ItemStackActionData actionData = new ItemStackActionData();
        actionData.durability = (short) ItemUtils.getItemDamage(item);

        actionData.amt = quantity;

        final ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null) {
            actionData.name = meta.getDisplayName();
        }
        if (meta instanceof LeatherArmorMeta) {
            final LeatherArmorMeta lam = (LeatherArmorMeta) meta;
            actionData.color = lam.getColor().asRGB();
        } else if (meta instanceof SkullMeta) {
            final SkullMeta skull = (SkullMeta) meta;
            if (skull.hasOwner()) {
                actionData.owner = Objects.requireNonNull(skull.getOwningPlayer()).getUniqueId().toString();
            }
        } else if (meta instanceof PotionMeta) {
            final PotionMeta potion = (PotionMeta) meta;
            actionData.potionType = potion.getBasePotionData().getType().toString().toLowerCase();
            actionData.potionExtended = potion.getBasePotionData().isExtended();
            actionData.potionUpgraded = potion.getBasePotionData().isUpgraded();
        }

        // Written books
        if (meta instanceof BookMeta) {
            final BookMeta bookMeta = (BookMeta) meta;
            actionData.by = bookMeta.getAuthor();
            actionData.title = bookMeta.getTitle();
            actionData.content = bookMeta.getPages().toArray(new String[0]);
        }

        // Lore
        if (meta != null && meta.hasLore()) {
            actionData.lore = Objects.requireNonNull(meta.getLore()).toArray(new String[0]);
        }

        // Enchantments
        if (enchantments != null && !enchantments.isEmpty()) {
            final String[] enchs = new String[enchantments.size()];
            int i = 0;
            for (final Entry<Enchantment, Integer> ench : enchantments.entrySet()) {
                // This is silly
                enchs[i] = ench.getKey().getKey().getKey() + ":" + ench.getValue();
                i++;
            }
            actionData.enchs = enchs;
        } else if (meta instanceof EnchantmentStorageMeta) {
            final EnchantmentStorageMeta bookEnchantments = (EnchantmentStorageMeta) meta;
            if (bookEnchantments.hasStoredEnchants()) {
                if (bookEnchantments.getStoredEnchants().size() > 0) {
                    final String[] enchs = new String[bookEnchantments.getStoredEnchants().size()];
                    int i = 0;
                    for (final Entry<Enchantment, Integer> ench : bookEnchantments.getStoredEnchants().entrySet()) {
                        // This is absolutely silly
                        enchs[i] = ench.getKey().getKey().getKey() + ":" + ench.getValue();
                        i++;
                    }
                    actionData.enchs = enchs;
                }
            }
        }
        if (meta instanceof FireworkEffectMeta) {
            applyFireWorksMetaToActionData(actionData, meta);
        }
        if (meta instanceof BannerMeta) {
            List<Pattern> patterns = ((BannerMeta) meta).getPatterns();
            Map<String, String> stringyPatterns = new HashMap<>();
            patterns.forEach(
                    pattern -> stringyPatterns.put(pattern.getPattern().getIdentifier(), pattern.getColor().name()));
            actionData.bannerMeta = stringyPatterns;
        }
        if (meta instanceof BlockStateMeta) {
            BlockStateMeta blockStateMeta = (BlockStateMeta) meta;
            BlockState blockState = blockStateMeta.getBlockState();
            if (blockState instanceof InventoryHolder) {
                InventoryHolder inventoryHolder = (InventoryHolder) blockState;
                int slot = -1;
                List<ItemStackActionData> inventoryItemsData = new ArrayList<>();
                for (ItemStack inventoryItem : inventoryHolder.getInventory()) {
                    slot++;
                    if (inventoryItem == null || inventoryItem.getType().isAir()) continue;
                    ItemStackActionData inventoryActionData = serializeItem(inventoryItem, inventoryItem.getAmount(), inventoryItem.getEnchantments());
                    inventoryActionData.slot = Integer.toString(slot);
                    inventoryActionData.material = inventoryItem.getType().toString();
                    inventoryItemsData.add(inventoryActionData);
                }
                if (!inventoryItemsData.isEmpty()) {
                    actionData.inventory = inventoryItemsData.toArray(ItemStackActionData[]::new);
                }
            }
        }
        return actionData;
    }

    private static void applyFireWorksMetaToActionData(ItemStackActionData actionData, ItemMeta meta) {
        final FireworkEffectMeta fireworkMeta = (FireworkEffectMeta) meta;
        if (fireworkMeta.hasEffect()) {
            final FireworkEffect effect = fireworkMeta.getEffect();
            if (effect != null) {
                if (!effect.getColors().isEmpty()) {
                    final int[] effectColors = new int[effect.getColors().size()];
                    int i = 0;
                    for (final Color effectColor : effect.getColors()) {
                        effectColors[i] = effectColor.asRGB();
                        i++;
                    }
                    actionData.effectColors = effectColors;
                }

                if (!effect.getFadeColors().isEmpty()) {
                    final int[] fadeColors = new int[effect.getColors().size()];
                    final int i = 0;
                    for (final Color fadeColor : effect.getFadeColors()) {
                        fadeColors[i] = fadeColor.asRGB();
                    }
                    actionData.fadeColors = fadeColors;
                }
                if (effect.hasFlicker()) {
                    actionData.hasFlicker = true;
                }
                if (effect.hasTrail()) {
                    actionData.hasTrail = true;
                }
            }
        }
    }

    private static void deserializeFireWorksMeta(ItemStack item, ItemMeta meta, ItemStackActionData actionData) {

        final FireworkEffectMeta fireworkMeta = (FireworkEffectMeta) meta;
        final Builder effect = FireworkEffect.builder();

        for (int i = 0; i < actionData.effectColors.length; i++) {
            effect.withColor(Color.fromRGB(actionData.effectColors[i]));
        }
        fireworkMeta.setEffect(effect.build());

        if (actionData.fadeColors != null) {
            for (int i = 0; i < actionData.fadeColors.length; i++) {
                effect.withFade(Color.fromRGB(actionData.fadeColors[i]));
            }
            fireworkMeta.setEffect(effect.build());
        }
        if (actionData.hasFlicker) {
            effect.flicker(true);
        }
        if (actionData.hasTrail) {
            effect.trail(true);
        }
        fireworkMeta.setEffect(effect.build());
        item.setItemMeta(fireworkMeta);
    }

    public void setSlot(String slot) {
        if (actionData == null) {
            actionData = new ItemStackActionData();
        }
        actionData.slot = slot;
    }

    @Override
    public String serialize() {
        return gson().toJson(actionData);
    }

    @Override
    public void deserialize(String data) {
        if (data == null || !data.startsWith("{")) {
            return;
        }
        actionData = gson().fromJson(data, ItemStackActionData.class);

        item = deserializeItem(getMaterial(), actionData);
    }

    public static ItemStack deserializeItem(Material material, ItemStackActionData data) {
        ItemStack item = new ItemStack(material, data.amt);

        MaterialState.setItemDamage(item, data.durability);

        // Restore enchantment
        if (data.enchs != null && data.enchs.length > 0) {
            for (final String ench : data.enchs) {
                final String[] enchArgs = ench.split(":");
                Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchArgs[0]));

                // Restore book enchantment
                if (enchantment != null) {
                    if (item.getType() == Material.ENCHANTED_BOOK) {
                        final EnchantmentStorageMeta bookEnchantments = (EnchantmentStorageMeta) item.getItemMeta();
                        bookEnchantments.addStoredEnchant(enchantment, Integer.parseInt(enchArgs[1]), false);
                        item.setItemMeta(bookEnchantments);
                    } else {
                        item.addUnsafeEnchantment(enchantment, Integer.parseInt(enchArgs[1]));
                    }
                }
            }
        }

        ItemMeta meta = item.getItemMeta();

        // Leather color
        if (meta instanceof LeatherArmorMeta && data.color > 0) {
            final LeatherArmorMeta lam = (LeatherArmorMeta) meta;
            lam.setColor(Color.fromRGB(data.color));
            item.setItemMeta(lam);
        } else if (meta instanceof SkullMeta && data.owner != null) {
            final SkullMeta skull = (SkullMeta) meta;
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(EntityUtils.uuidOf(data.owner)));
            item.setItemMeta(skull);
        } else if (meta instanceof BookMeta) {
            final BookMeta bookMeta = (BookMeta) meta;
            bookMeta.setAuthor(data.by);
            bookMeta.setTitle(data.title);
            bookMeta.setPages(data.content);
            item.setItemMeta(bookMeta);
        } else if (meta instanceof PotionMeta) {
            final PotionType potionType = PotionType.valueOf(data.potionType.toUpperCase());
            final PotionMeta potionMeta = (PotionMeta) meta;
            potionMeta.setBasePotionData(new PotionData(potionType, data.potionExtended,
                    data.potionUpgraded));
        }
        if (meta instanceof FireworkEffectMeta && data.effectColors != null
                && data.effectColors.length > 0) {

            deserializeFireWorksMeta(item, meta, data);
        }
        if (meta instanceof BannerMeta && data.bannerMeta != null) {
            Map<String, String> stringStringMap = data.bannerMeta;
            List<Pattern> patterns = new ArrayList<>();
            stringStringMap.forEach((patternIdentifier, dyeName) -> {
                PatternType type = PatternType.getByIdentifier(patternIdentifier);
                DyeColor color = DyeColor.valueOf(dyeName);
                if (type != null) {
                    Pattern p = new Pattern(color, type);
                    patterns.add(p);
                }
            });
            ((BannerMeta) meta).setPatterns(patterns);
        }
        if (meta instanceof BlockStateMeta && data.inventory != null) {
            BlockState blockState = ((BlockStateMeta) meta).getBlockState();
            if (blockState instanceof Container) {
                Inventory inventory = ((Container) blockState).getInventory();
                for (ItemStackActionData inventoryActionData : data.inventory) {
                    int iSlot = -1;
                    try {
                        iSlot = Integer.parseInt(inventoryActionData.slot);
                    } catch (IllegalArgumentException e) {
                        // Ignored
                    }
                    try {
                        ItemStack itemStack = deserializeItem(Material.valueOf(inventoryActionData.material), inventoryActionData);
                        if (iSlot == -1) {
                            inventory.addItem(itemStack);
                        }
                        else {
                            inventory.setItem(iSlot, itemStack);
                        }
                    } catch (Exception e) {
                        // Ignored
                    }
                }
                blockState.update(true);
                ((BlockStateMeta) meta).setBlockState(blockState);
            }
        }

        if (data.name != null) {
            if (meta == null) {
                meta = item.getItemMeta();
            }

            if (meta != null) {
                meta.setDisplayName(data.name);
            }
        }

        if (data.lore != null) {
            if (meta == null) {
                meta = item.getItemMeta();
            }

            if (meta != null) {
                meta.setLore(Arrays.asList(data.lore));
            }
        }

        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStackActionData getActionData() {
        return this.actionData;
    }

    /**
     * ItemStack.
     * @return ItemStack
     */
    public ItemStack getItem() {
        return item;
    }

    /**
     * Nice name.
     * @return String
     */
    @Override
    public String getNiceName() {
        String name = "";
        if (item != null) {
            final String fullItemName = ItemUtils.getItemFullNiceName(item);
            name = actionData.amt + " " + fullItemName;
        }
        return name;
    }

    @Override
    public ChangeResult applyRollback(Player player, PrismParameters parameters, boolean isPreview) {
        return placeItems(player, parameters, isPreview);
    }

    @Override
    public ChangeResult applyRestore(Player player, PrismParameters parameters, boolean isPreview) {
        return placeItems(player, parameters, isPreview);
    }

    protected ChangeResult placeItems(Player player, PrismParameters parameters, boolean isPreview) {

        if (actionData == null) {
            return new ChangeResultImpl(ChangeResultType.SKIPPED, null);
        }

        ChangeResultType result = ChangeResultType.SKIPPED;

        if (isPreview) {
            return new ChangeResultImpl(ChangeResultType.PLANNED, null);
        }

        if (Prism.config.getBoolean("prism.appliers.allow-rollback-items-removed-from-container")) {

            final Block block = getWorld().getBlockAt(getLoc());
            Inventory inventory = null;

            // Item drop/pickup from player inventories
            if (getActionType().getName().equals("item-drop") || getActionType().getName().equals("item-pickup")) {

                // Is player online?
                final Player onlinePlayer = Bukkit.getServer().getPlayer(getUuid());
                if (onlinePlayer != null) {
                    inventory = onlinePlayer.getInventory();
                } else {
                    // Skip if the player isn't online
                    Prism.debug("Skipping inventory process because player is offline");
                    return new ChangeResultImpl(ChangeResultType.SKIPPED, null);
                }
            } else {
                if (block.getType().equals(Material.JUKEBOX)) {
                    final Jukebox jukebox = (Jukebox) block.getState();
                    jukebox.setPlaying(item.getType());
                    jukebox.update();
                } else if (block.getState() instanceof InventoryHolder) {
                    final InventoryHolder ih = (InventoryHolder) block.getState();
                    inventory = ih.getInventory();
                } else {
                    String slot = getActionData().slot.toUpperCase(Locale.ENGLISH);
                    EquipmentSlot eSlot = null;
                    // Prism.log("Slot found: " + slot);
                    try {
                        eSlot = EquipmentSlot.valueOf(slot);
                    } catch (IllegalArgumentException ignored) {
                        //ignored
                    }
                    // Prism.log("   eSlot: " + eSlot);

                    BlockFace fSlot = null;
                    try {
                        fSlot = BlockFace.valueOf(slot);
                    } catch (IllegalArgumentException ignored) {
                        //ignored
                    }
                    // Prism.log("   fSlot: " + fSlot);

                    Entity[] foundEntities = block.getChunk().getEntities();

                    for (Entity e : foundEntities) {
                        // Get the block location for better comparisons
                        Location loc = e.getLocation();
                        loc.setX(loc.getBlockX());
                        loc.setY(loc.getBlockY());
                        loc.setZ(loc.getBlockZ());

                        Prism.debug(block.getLocation());
                        Prism.debug(loc);

                        if (!block.getWorld().equals(e.getWorld())) {
                            continue;
                        }

                        if (block.getLocation().distanceSquared(loc) < 0.25) {
                            if (e instanceof ItemFrame) {
                                final ItemFrame frame = (ItemFrame) e;

                                // if we have a pseudo-slot try to use that
                                if (fSlot != null && fSlot != frame.getAttachedFace()) {
                                    // Prism.log("Skipping frame: " + frame.getFacing());
                                    continue;
                                }
                                // Prism.log("Using frame: " + frame.getFacing());

                                if ((getActionType().getName().equals("item-remove")
                                        && parameters.getProcessType().equals(PrismProcessType.ROLLBACK))
                                        || (getActionType().getName().equals("item-insert")
                                        && parameters.getProcessType().equals(PrismProcessType.RESTORE))) {
                                    if (frame.getItem().getType() == Material.AIR) {
                                        frame.setItem(item);
                                        result = ChangeResultType.APPLIED;
                                        break;
                                    }
                                } else if (frame.getItem().getType() != Material.AIR) {
                                    frame.setItem(null);
                                    result = ChangeResultType.APPLIED;
                                    break;
                                }
                            } else if (e instanceof ArmorStand) {
                                final LivingEntity stand = (ArmorStand) e;

                                EquipmentSlot actualSlot = eSlot;

                                if (actualSlot == null) {
                                    actualSlot = InventoryUtils.getTargetArmorSlot(item.getType());
                                }

                                ItemStack atPoint = InventoryUtils.getEquipment(stand.getEquipment(), eSlot);

                                if ((getActionType().getName().equals("item-remove")
                                        && parameters.getProcessType().equals(PrismProcessType.ROLLBACK))
                                        || (getActionType().getName().equals("item-insert")
                                        && parameters.getProcessType().equals(PrismProcessType.RESTORE))) {
                                    if (atPoint.getType() == Material.AIR) {
                                        InventoryUtils.setEquipment(stand.getEquipment(), actualSlot, item);
                                        result = ChangeResultType.APPLIED;
                                        break;
                                    }
                                } else if (atPoint.getType() != Material.AIR) {
                                    InventoryUtils.setEquipment(stand.getEquipment(), actualSlot, null);
                                    result = ChangeResultType.APPLIED;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (inventory != null) {

                final PrismProcessType pt = parameters.getProcessType();
                final String n = getActionType().getName();

                int iSlot = -1;

                try {
                    iSlot = Integer.parseInt(getActionData().slot);
                } catch (IllegalArgumentException ignored) {
                    //ignored
                }

                // Rolling back a:remove or a:drop should place the item into
                // the inventory
                // Restoring a:insert or a:pickup should place the item into the
                // inventory
                if ((pt.equals(PrismProcessType.ROLLBACK) && (n.equals("item-remove") || n.equals("item-drop")))
                        || (pt.equals(PrismProcessType.RESTORE)
                        && (n.equals("item-insert") || n.equals("item-pickup")))) {

                    boolean added = false;

                    // We'll attempt to put it back in the same slot
                    if (iSlot >= 0) {
                        // Ensure slot exists in this inventory
                        // I'm not sure why this happens but sometimes
                        // a slot larger than the contents size is recorded
                        // and triggers ArrayIndexOutOfBounds
                        // https://snowy-evening.com/botsko/prism/450/
                        if (iSlot < inventory.getSize()) {
                            final ItemStack currentSlotItem = inventory.getItem(iSlot);
                            int amount = 0;
                            ItemStack item = getItem().clone();
                            int max = item.getType().getMaxStackSize();

                            if (currentSlotItem == null || currentSlotItem.getType() == Material.AIR) {
                                amount = item.getAmount();
                            } else if (currentSlotItem.isSimilar(item)) {
                                amount = Math.min(currentSlotItem.getAmount() + item.getAmount(), max);
                            }

                            if (amount > 0) {
                                result = ChangeResultType.APPLIED;
                                item.setAmount(amount);
                                added = true;
                                inventory.setItem(iSlot, item);
                            }
                        }
                    }
                    // If that failed we'll attempt to put it anywhere
                    if (!added) {
                        // TODO: Skip is actually "partially applied"
                        final HashMap<Integer, ItemStack> leftovers = InventoryUtils.addItemToInventory(inventory,
                                getItem());
                        if (leftovers.size() > 0) {
                            Prism.debug("Skipping adding items because there are leftovers");
                            result = ChangeResultType.SKIPPED;
                        } else {
                            result = ChangeResultType.APPLIED;
                            added = true;
                        }
                    }

                    // Item was added to the inv, we need to remove the entity
                    if (added && (n.equals("item-drop") || n.equals("item-pickup"))) {
                        final Entity[] entities = getLoc().getChunk().getEntities();
                        for (final Entity entity : entities) {
                            if (entity instanceof Item) {
                                final ItemStack stack = ((Item) entity).getItemStack();
                                if (stack.isSimilar(getItem())) {
                                    // Remove the event's number of items from
                                    // the stack
                                    stack.setAmount(stack.getAmount() - getItem().getAmount());
                                    if (stack.getAmount() == 0) {
                                        entity.remove();
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }

                // Rolling back a:insert or a:pickup should remove the item from
                // the inventory
                // Restoring a:remove or a:drop should remove the item from the
                // inventory
                if ((pt.equals(PrismProcessType.ROLLBACK) && (n.equals("item-insert") || n.equals("item-pickup")))
                        || (pt.equals(PrismProcessType.RESTORE)
                        && (n.equals("item-remove") || n.equals("item-drop")))) {

                    // does inventory have item?
                    boolean removed = false;

                    // We'll attempt to take it from the same slot
                    if (iSlot >= 0) {

                        if (iSlot >= inventory.getContents().length) {
                            inventory.addItem(getItem());
                        } else {
                            final ItemStack currentSlotItem = inventory.getItem(iSlot);
                            ItemStack item = getItem().clone();

                            if (item.isSimilar(currentSlotItem)) {
                                int amount = 0;
                                if (currentSlotItem != null) {
                                    amount = Math.max(currentSlotItem.getAmount() - item.getAmount(), 0);
                                }
                                item.setAmount(amount);
                                result = ChangeResultType.APPLIED;
                                removed = true;
                                inventory.setItem(iSlot, amount > 0 ? item : null);
                            }
                        }
                    }
                    if (removed && (n.equals("item-drop") || n.equals("item-pickup"))) {
                        ItemUtils.dropItem(getLoc(), getItem());
                    }
                }
            }
        }
        return new ChangeResultImpl(result, null);
    }

    @SuppressWarnings("WeakerAccess")
    public static class ItemStackActionData {
        public int amt;
        public String material;
        public String name;
        public int color;
        public String owner;
        public String[] enchs;
        public String by;
        public String title;
        public String[] lore;
        public String[] content;
        public ItemStackActionData[] inventory;
        public String slot = "-1";
        public int[] effectColors;
        public int[] fadeColors;
        public boolean hasFlicker;
        public boolean hasTrail;
        public short durability = 0;
        public Map<String, String> bannerMeta;
        public String potionType;
        public boolean potionExtended;
        public boolean potionUpgraded;
    }

}
