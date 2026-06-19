package com.hereworldy.hereworldy.listener;

import com.hereworldy.hereworldy.HereWorldyPlugin;
import com.hereworldy.hereworldy.manager.BoxManager;
import com.hereworldy.hereworldy.manager.InventoryManager;
import com.hereworldy.hereworldy.manager.WorldManager;
import com.hereworldy.hereworldy.util.SelectionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;

import org.bukkit.World;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final Set<UUID> hardcoreDeaths = new HashSet<>();

    private final HereWorldyPlugin plugin;
    private final InventoryManager inventoryManager;
    private final BoxManager boxManager;
    private final WorldManager worldManager;

    public PlayerListener(HereWorldyPlugin plugin) {
        this.plugin = plugin;
        this.inventoryManager = plugin.getInventoryManager();
        this.boxManager = plugin.getBoxManager();
        this.worldManager = plugin.getWorldManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Block block = event.getClickedBlock();

        // 1. Handle Portal Selection with Clock
        if (SelectionUtil.isSelectionMode(uuid) && player.getInventory().getItemInMainHand().getType() == Material.CLOCK) {
            if (event.getHand() == null || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (block == null) return;

            event.setCancelled(true);
            Location loc = block.getLocation();

            Location pos1 = SelectionUtil.getPos1(uuid);
            Location pos2 = SelectionUtil.getPos2(uuid);

            if (pos1 == null || (pos1 != null && pos2 != null)) {
                SelectionUtil.clearSelection(uuid);
                SelectionUtil.setPos1(uuid, loc);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&d[HereWorldy] Point A set to &e[" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "]. Now click another block to set Point B."));
            } else {
                SelectionUtil.setPos2(uuid, loc);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&d[HereWorldy] Point B set to &e[" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "]."));
                try {
                    Material mat = plugin.getPortalManager().validateFrame(pos1, loc);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                        "&d[HereWorldy] &aValid frame detected using " + mat.name() + "! You can now use /hw portal-set."));
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                        "&d[HereWorldy] &cSelection invalid: " + e.getMessage()));
                }
            }
            return;
        }

        // 2. Handle Inter-dimensional Chest interactions
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null) {
            boolean openBox = false;

            // Clicked a container with an attached sign
            if (block.getState() instanceof Container && isLinkedContainer(block)) {
                openBox = true;
            }
            // Clicked the sign directly attached to a container
            else if (isSignClicked(block)) {
                openBox = true;
            }

            if (openBox) {
                event.setCancelled(true);
                boxManager.openBox(player);
            }
        }
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        
        // Find if this is placed on a container
        boolean hasContainer = false;
        if (block.getBlockData() instanceof Directional) {
            Directional dir = (Directional) block.getBlockData();
            Block attached = block.getRelative(dir.getFacing().getOppositeFace());
            if (attached.getState() instanceof Container) {
                hasContainer = true;
            }
        } else {
            Block under = block.getRelative(org.bukkit.block.BlockFace.DOWN);
            if (under.getState() instanceof Container) {
                hasContainer = true;
            }
        }

        if (!hasContainer) return;

        for (int i = 0; i < 4; i++) {
            String line = ChatColor.stripColor(event.getLine(i)).trim();
            if (line.equalsIgnoreCase("[Here My Stuff!]") || line.equalsIgnoreCase("Here My Stuff!")) {
                // Style the sign text beautifully!
                event.line(i, Component.text("[Here My Stuff!]")
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decorate(TextDecoration.BOLD));
                
                event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&d[HereWorldy] &aLinked Inter-dimensional Storage Box successfully!"));
                break;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BoxManager.HereWorldyChestHolder) {
            boxManager.saveBox((Player) event.getPlayer(), event.getInventory());
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        inventoryManager.handleWorldChange(player, event.getFrom(), player.getWorld());

        // Update gamemode based on target world settings or saved group settings
        new BukkitRunnable() {
            @Override
            public void run() {
                WorldManager.WorldSettings settings = worldManager.getWorldSettings(player.getWorld().getName());
                if (settings != null) {
                    player.setGameMode(settings.gameMode);
                } else {
                    GameMode savedGm = inventoryManager.getSavedGameMode(player, inventoryManager.getGroupForWorld(player.getWorld().getName()));
                    player.setGameMode(savedGm != null ? savedGm : org.bukkit.Bukkit.getDefaultGameMode());
                }
            }
        }.runTaskLater(plugin, 5L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Load the inventory for current world group
        inventoryManager.loadInventory(player, inventoryManager.getGroupForWorld(player.getWorld().getName()));

        // Ensure gamemode is accurate on login
        WorldManager.WorldSettings settings = worldManager.getWorldSettings(player.getWorld().getName());
        if (settings != null) {
            player.setGameMode(settings.gameMode);
        } else {
            GameMode savedGm = inventoryManager.getSavedGameMode(player, inventoryManager.getGroupForWorld(player.getWorld().getName()));
            player.setGameMode(savedGm != null ? savedGm : org.bukkit.Bukkit.getDefaultGameMode());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String worldName = player.getWorld().getName();
        boolean isHardcore = plugin.getConfigManager().getWorldsConfig().getBoolean("worlds." + worldName + ".hardcore", false);

        if (isHardcore) {
            hardcoreDeaths.add(player.getUniqueId());

            // Clear drops and set keepInventory to true to bypass graves plugins (like AxGraves)
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(true);

            // Hard reset active player inventory and stats
            player.getInventory().clear();
            player.setLevel(0);
            player.setExp(0);
            for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }

            // Hard reset saved inventory file for the hardcore world's group
            String group = inventoryManager.getGroupForWorld(worldName);
            inventoryManager.resetInventory(player, group);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Save inventory for current world group
        inventoryManager.saveInventory(player, inventoryManager.getGroupForWorld(player.getWorld().getName()));
        
        // Clear selection mode
        SelectionUtil.clearSelection(player.getUniqueId());

        // Clean up hardcore death tracker
        hardcoreDeaths.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        boolean isHardcore = plugin.getConfigManager().getWorldsConfig().getBoolean("worlds." + worldName + ".hardcore", false);

        if (hardcoreDeaths.remove(player.getUniqueId()) || isHardcore) {
            World mainWorld = org.bukkit.Bukkit.getWorlds().get(0);
            String mainWorldName = mainWorld.getName();
            Location respawnLoc = null;

            // Find a portal in the hardcore world that targets the main world
            for (com.hereworldy.hereworldy.manager.PortalManager.PortalData portal : plugin.getPortalManager().getPortals().values()) {
                if (portal.worldName.equalsIgnoreCase(worldName) && portal.targetWorldName.equalsIgnoreCase(mainWorldName)) {
                    respawnLoc = new Location(mainWorld, portal.tx, portal.ty, portal.tz);
                    break;
                }
            }

            if (respawnLoc != null) {
                event.setRespawnLocation(respawnLoc);
                player.sendMessage(ChatColor.RED + "You died in a Hardcore dimension! Your inventory was reset, and you returned to the main world portal.");
            } else {
                event.setRespawnLocation(mainWorld.getSpawnLocation());
                player.sendMessage(ChatColor.RED + "You died in a Hardcore dimension! Your inventory was reset, and you returned to the main world spawn.");
            }
        }
    }

    private boolean isLinkedContainer(Block block) {
        if (block.getState() instanceof org.bukkit.block.Chest) {
            org.bukkit.block.Chest chest = (org.bukkit.block.Chest) block.getState();
            if (chest.getInventory() instanceof org.bukkit.inventory.DoubleChestInventory) {
                org.bukkit.inventory.DoubleChestInventory doubleInv = (org.bukkit.inventory.DoubleChestInventory) chest.getInventory();
                org.bukkit.block.DoubleChest doubleChest = doubleInv.getHolder();
                if (doubleChest != null) {
                    Block leftBlock = ((org.bukkit.block.Chest) doubleChest.getLeftSide()).getBlock();
                    Block rightBlock = ((org.bukkit.block.Chest) doubleChest.getRightSide()).getBlock();
                    return isLinkedSingleContainer(leftBlock) || isLinkedSingleContainer(rightBlock);
                }
            }
        }
        return isLinkedSingleContainer(block);
    }

    private boolean isLinkedSingleContainer(Block block) {
        Block[] checkBlocks = {
            block.getRelative(org.bukkit.block.BlockFace.NORTH),
            block.getRelative(org.bukkit.block.BlockFace.SOUTH),
            block.getRelative(org.bukkit.block.BlockFace.EAST),
            block.getRelative(org.bukkit.block.BlockFace.WEST),
            block.getRelative(org.bukkit.block.BlockFace.UP)
        };
        for (Block check : checkBlocks) {
            if (boxManager.isHereWorldySign(check)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSignClicked(Block block) {
        if (boxManager.isHereWorldySign(block)) {
            if (block.getState().getBlockData() instanceof Directional) {
                Directional dir = (Directional) block.getState().getBlockData();
                Block attachedBlock = block.getRelative(dir.getFacing().getOppositeFace());
                return attachedBlock.getState() instanceof Container;
            }
            Block under = block.getRelative(org.bukkit.block.BlockFace.DOWN);
            return under.getState() instanceof Container;
        }
        return false;
    }
}
