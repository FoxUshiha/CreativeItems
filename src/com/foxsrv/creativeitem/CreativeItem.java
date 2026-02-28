package com.foxsrv.creativeitem;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * CreativeItem - Main plugin class
 * Manages creative mode items with special handling
 */
public class CreativeItem extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    
    private static CreativeItem instance;
    private FileConfiguration config;
    
    // NBT-like tag using lore detection
    private static final String CREATIVE_TAG = "§c§lCreative Item";
    private static final String CREATIVE_TAG_RAW = ChatColor.RED + "" + ChatColor.BOLD + "Creative Item";
    
    // Store player inventories when switching to creative
    private final Map<UUID, ItemStack[]> savedInventories = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> savedOffHand = new ConcurrentHashMap<>();
    
    // Track players in creative mode
    private final Set<UUID> creativePlayers = ConcurrentHashMap.newKeySet();
    
    // Config values
    private String creativeLore;
    private boolean logRemovals;
    private boolean adminBypass;
    
    // List of inventory types that are considered "storage" (not player inventory)
    private final Set<InventoryType> storageInventories = EnumSet.of(
        InventoryType.CHEST,
        InventoryType.BARREL,
        InventoryType.ENDER_CHEST,
        InventoryType.SHULKER_BOX,
        InventoryType.HOPPER,
        InventoryType.DISPENSER,
        InventoryType.DROPPER,
        InventoryType.FURNACE,
        InventoryType.BLAST_FURNACE,
        InventoryType.SMOKER,
        InventoryType.BREWING,
        InventoryType.BEACON,
        InventoryType.ANVIL,
        InventoryType.ENCHANTING,
        InventoryType.GRINDSTONE,
        InventoryType.LECTERN,
        InventoryType.STONECUTTER,
        InventoryType.CARTOGRAPHY,
        InventoryType.LOOM,
        InventoryType.MERCHANT,
        InventoryType.CRAFTING,
        InventoryType.WORKBENCH
    );
    
    public static CreativeItem get() { return instance; }
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Save default config
        saveDefaultConfig();
        reloadPluginConfig();
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Register commands
        Objects.requireNonNull(getCommand("creativeitem")).setExecutor(this);
        Objects.requireNonNull(getCommand("creativeitem")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("ci")).setExecutor(this);
        Objects.requireNonNull(getCommand("ci")).setTabCompleter(this);
        
        // Load creative players on startup
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE) {
                creativePlayers.add(player.getUniqueId());
                saveInventory(player);
            }
        }
        
        // Start periodic cleanup task for any stray creative items
        startCleanupTask();
        
        getLogger().info("CreativeItem v" + getDescription().getVersion() + " enabled!");
    }
    
    @Override
    public void onDisable() {
        // Restore inventories for all creative players on disable
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE && !hasAdminBypass(player)) {
                restoreInventory(player);
            }
        }
        
        getLogger().info("CreativeItem disabled.");
    }
    
    @Override
    public void reloadConfig() {
        super.reloadConfig();
        reloadPluginConfig();
    }
    
    private void reloadPluginConfig() {
        this.config = getConfig();
        this.creativeLore = ChatColor.translateAlternateColorCodes('&', 
            config.getString("CreativeItemLore", "&c&lCreative Item"));
        this.logRemovals = config.getBoolean("LogRemovals", true);
        this.adminBypass = config.getBoolean("AdminBypass", true);
    }
    
    /**
     * Start periodic cleanup task for any stray creative items
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Check all online players for creative items in survival mode
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode() != GameMode.CREATIVE && !hasAdminBypass(player)) {
                        removeCreativeItems(player);
                    }
                }
                
                // Clean up any items on the ground
                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    for (Item item : world.getEntitiesByClass(Item.class)) {
                        ItemStack stack = item.getItemStack();
                        if (isCreativeItem(stack)) {
                            item.remove();
                            if (logRemovals) {
                                getLogger().info("Cleaned up stray creative item at " + 
                                    item.getLocation().getBlockX() + ", " + 
                                    item.getLocation().getBlockY() + ", " + 
                                    item.getLocation().getBlockZ());
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 200L, 200L); // Run every 10 seconds
    }
    
    /**
     * Check if player has admin bypass permission
     */
    private boolean hasAdminBypass(Player player) {
        return adminBypass && player.hasPermission("creativeitem.admin");
    }
    
    /**
     * Check if an inventory type is a storage container
     */
    private boolean isStorageInventory(InventoryType type) {
        return storageInventories.contains(type);
    }
    
    /**
     * Check if an item is a creative item (has the lore tag)
     */
    private boolean isCreativeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return false;
        
        List<String> lore = meta.getLore();
        return lore != null && lore.contains(creativeLore);
    }
    
    /**
     * Add creative tag to an item
     */
    private void addCreativeTag(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();
        
        // Add tag if not already present
        if (!lore.contains(creativeLore)) {
            lore.add(0, creativeLore); // Add at the beginning
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }
    
    /**
     * Remove creative tag from an item
     */
    private boolean removeCreativeTag(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return false;
        
        List<String> lore = meta.getLore();
        if (lore == null || !lore.contains(creativeLore)) return false;
        
        lore.remove(creativeLore);
        if (lore.isEmpty()) {
            meta.setLore(null);
        } else {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return true;
    }
    
    /**
     * Remove all creative items from player's inventory
     * @return true if any items were removed
     */
    private boolean removeCreativeItems(Player player) {
        boolean removed = false;
        
        // Check main inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isCreativeItem(item)) {
                player.getInventory().setItem(i, null);
                removed = true;
            }
        }
        
        // Check armor slots
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (isCreativeItem(armor)) {
                // Can't directly remove armor, will be handled by inventory clear
                removed = true;
            }
        }
        
        // Check off hand
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isCreativeItem(offHand)) {
            player.getInventory().setItemInOffHand(null);
            removed = true;
        }
        
        if (removed && logRemovals) {
            getLogger().info("Removed creative items from " + player.getName());
        }
        
        return removed;
    }
    
    /**
     * Save player's current inventory
     */
    private void saveInventory(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Save main inventory contents (excluding armor and offhand)
        ItemStack[] contents = player.getInventory().getContents();
        savedInventories.put(uuid, contents.clone());
        
        // Save armor contents
        savedArmor.put(uuid, player.getInventory().getArmorContents().clone());
        
        // Save off hand
        savedOffHand.put(uuid, player.getInventory().getItemInOffHand().clone());
        
        if (logRemovals) {
            getLogger().info("Saved inventory for " + player.getName());
        }
    }
    
    /**
     * Restore player's saved inventory
     */
    private void restoreInventory(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (!savedInventories.containsKey(uuid)) {
            // No saved inventory, just clear creative items
            removeCreativeItems(player);
            return;
        }
        
        // Clear current inventory first
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        
        // Restore saved contents
        player.getInventory().setContents(savedInventories.get(uuid));
        player.getInventory().setArmorContents(savedArmor.get(uuid));
        player.getInventory().setItemInOffHand(savedOffHand.get(uuid));
        
        // Clean up saved data
        savedInventories.remove(uuid);
        savedArmor.remove(uuid);
        savedOffHand.remove(uuid);
        
        if (logRemovals) {
            getLogger().info("Restored inventory for " + player.getName());
        }
    }
    
    /**
     * Handle creative item pickup from creative menu
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        
        // Check if player is getting a new item (cursor was air, now has item)
        if (cursor != null && !cursor.getType().isAir()) {
            // This is when they pick up from creative menu
            ItemStack newItem = cursor.clone();
            
            // Only tag if not admin bypass
            if (!hasAdminBypass(player)) {
                addCreativeTag(newItem);
                event.setCursor(newItem);
            }
        }
        
        // Allow moving items within creative inventory for non-admin players
        // This is now permitted
    }
    
    /**
     * Handle inventory clicks
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        
        // Skip if player has admin bypass
        if (hasAdminBypass(player)) return;
        
        // Allow all interactions within player's own inventory or creative inventory
        if (clickedInventory != null && 
            (clickedInventory.getType() == InventoryType.PLAYER || 
             clickedInventory.getType() == InventoryType.CREATIVE)) {
            // Allow moving items within player inventory
            return;
        }
        
        // Check if this is a storage container (chest, ender chest, etc.)
        if (isStorageInventory(inventory.getType())) {
            // Check if clicking on creative item in storage
            if (isCreativeItem(current)) {
                event.setCancelled(true);
                // Remove the item immediately as a security measure
                if (current != null) {
                    current.setAmount(0);
                }
                player.sendMessage(ChatColor.RED + "Creative item detected in storage! Item removed.");
                if (logRemovals) {
                    getLogger().info("Removed creative item from storage accessed by " + player.getName());
                }
                return;
            }
            
            // Check if moving creative item with cursor into storage
            if (isCreativeItem(cursor)) {
                event.setCancelled(true);
                // Remove the item from cursor
                event.setCursor(null);
                player.sendMessage(ChatColor.RED + "Cannot store creative items! Item removed.");
                if (logRemovals) {
                    getLogger().info("Removed creative item from cursor of " + player.getName());
                }
                return;
            }
        }
        
        // Check if trying to put creative item in armor slot
        if (event.getSlotType() == InventoryType.SlotType.ARMOR && isCreativeItem(current)) {
            event.setCancelled(true);
            if (current != null) {
                current.setAmount(0);
            }
            player.sendMessage(ChatColor.RED + "You cannot equip creative items!");
        }
    }
    
    /**
     * Handle inventory drag events
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        ItemStack oldCursor = event.getOldCursor();
        
        // Skip if admin bypass
        if (hasAdminBypass(player)) return;
        
        // Check if dragging creative item
        if (isCreativeItem(oldCursor)) {
            // Check if any of the slots are in storage containers
            for (int slot : event.getRawSlots()) {
                if (slot < event.getView().getTopInventory().getSize()) {
                    // This is top inventory - check if it's storage
                    if (isStorageInventory(inventory.getType())) {
                        event.setCancelled(true);
                        event.setCursor(null); // Remove the item
                        player.sendMessage(ChatColor.RED + "Cannot store creative items! Item removed.");
                        if (logRemovals) {
                            getLogger().info("Removed creative item from drag by " + player.getName());
                        }
                        return;
                    }
                }
            }
        }
    }
    
    /**
     * Handle inventory open events
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();
        
        // Skip if admin bypass
        if (hasAdminBypass(player)) return;
        
        // Check if opening a storage container
        if (isStorageInventory(inventory.getType())) {
            // Scan the inventory for creative items and remove them
            boolean removed = false;
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                if (isCreativeItem(item)) {
                    inventory.setItem(i, null);
                    removed = true;
                }
            }
            
            if (removed && logRemovals) {
                getLogger().info("Removed creative items from " + inventory.getType() + 
                    " opened by " + player.getName());
            }
        }
    }
    
    /**
     * Handle item drops
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItemDrop();
        ItemStack itemStack = item.getItemStack();
        
        // Skip if admin bypass
        if (hasAdminBypass(player)) return;
        
        // Check if dropped item is creative item
        if (isCreativeItem(itemStack)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot drop creative items!");
            
            // Remove the item from inventory
            player.getInventory().remove(itemStack);
        }
    }
    
    /**
     * Handle item spawn (from any source)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack itemStack = item.getItemStack();
        
        // Check if spawned item is creative item
        if (isCreativeItem(itemStack)) {
            event.setCancelled(true);
            
            if (logRemovals) {
                getLogger().info("Removed spawned creative item at " + 
                    item.getLocation().getBlockX() + ", " + 
                    item.getLocation().getBlockY() + ", " + 
                    item.getLocation().getBlockZ());
            }
        }
    }
    
    /**
     * Handle item pickup
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        Item item = event.getItem();
        ItemStack itemStack = item.getItemStack();
        
        // Skip if admin bypass
        if (hasAdminBypass(player)) return;
        
        // Prevent picking up creative items if not in creative mode
        if (isCreativeItem(itemStack)) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
                item.remove(); // Remove the item from ground
                player.sendMessage(ChatColor.RED + "You cannot pick up creative items in survival!");
            } else {
                // In creative mode, allow pickup but ensure it has tag
                addCreativeTag(itemStack);
            }
        }
    }
    
    /**
     * Handle player interaction (right-click on chests, etc.)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Skip if admin bypass
        if (hasAdminBypass(player)) return;
        
        // Check if interacting with a creative item
        if (isCreativeItem(item) && player.getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
            player.getInventory().remove(item);
            player.sendMessage(ChatColor.RED + "Creative item removed!");
        }
    }
    
    /**
     * Handle game mode changes
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        GameMode newGameMode = event.getNewGameMode();
        GameMode oldGameMode = player.getGameMode();
        
        // Switching TO creative
        if (newGameMode == GameMode.CREATIVE && oldGameMode != GameMode.CREATIVE) {
            // Save current inventory
            saveInventory(player);
            creativePlayers.add(player.getUniqueId());
            
            // Clear inventory (will be restored when leaving creative)
            if (!hasAdminBypass(player)) {
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                player.getInventory().setItemInOffHand(null);
            }
        }
        
        // Switching FROM creative
        if (oldGameMode == GameMode.CREATIVE && newGameMode != GameMode.CREATIVE) {
            creativePlayers.remove(player.getUniqueId());
            
            // Restore saved inventory
            if (!hasAdminBypass(player)) {
                restoreInventory(player);
            } else {
                // For admins, just remove creative tags but keep items
                for (ItemStack item : player.getInventory().getContents()) {
                    if (isCreativeItem(item)) {
                        removeCreativeTag(item);
                    }
                }
                for (ItemStack item : player.getInventory().getArmorContents()) {
                    if (isCreativeItem(item)) {
                        removeCreativeTag(item);
                    }
                }
                ItemStack offHand = player.getInventory().getItemInOffHand();
                if (isCreativeItem(offHand)) {
                    removeCreativeTag(offHand);
                }
            }
        }
    }
    
    /**
     * Handle world changes (in case of different game mode rules)
     */
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is in creative mode in new world
        if (player.getGameMode() == GameMode.CREATIVE && !creativePlayers.contains(player.getUniqueId())) {
            creativePlayers.add(player.getUniqueId());
            if (!hasAdminBypass(player)) {
                saveInventory(player);
            }
        }
    }
    
    /**
     * Handle player join
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check for creative items on join if not in creative mode
        if (player.getGameMode() != GameMode.CREATIVE && !hasAdminBypass(player)) {
            removeCreativeItems(player);
        }
    }
    
    /**
     * Handle player quit - restore inventory if needed
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Clean up saved data
        savedInventories.remove(uuid);
        savedArmor.remove(uuid);
        savedOffHand.remove(uuid);
        creativePlayers.remove(uuid);
    }
    
    /**
     * Command handling
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            player.sendMessage(ChatColor.GOLD + "=== CreativeItem Commands ===");
            player.sendMessage(ChatColor.YELLOW + "/ci remove " + ChatColor.GRAY + "- Remove creative tag from item in hand");
            return true;
        }
        
        if (args[0].equalsIgnoreCase("remove")) {
            ItemStack item = player.getInventory().getItemInMainHand();
            
            if (item == null || item.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "You must be holding an item!");
                return true;
            }
            
            if (removeCreativeTag(item)) {
                player.sendMessage(ChatColor.GREEN + "Creative tag removed from the item!");
                
                if (logRemovals) {
                    getLogger().info(player.getName() + " removed creative tag from " + 
                        item.getType().name());
                }
            } else {
                player.sendMessage(ChatColor.RED + "This item does not have a creative tag!");
            }
            return true;
        }
        
        player.sendMessage(ChatColor.RED + "Unknown subcommand. Use /ci remove");
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("remove");
            return filter(completions, args[0]);
        }
        
        return Collections.emptyList();
    }
    
    private List<String> filter(List<String> list, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return list;
        }
        
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return list.stream()
            .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
            .collect(Collectors.toList());
    }
}
