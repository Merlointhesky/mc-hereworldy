package com.hereworldy.hereworldy.manager;

import com.hereworldy.hereworldy.HereWorldyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BoxManager {

    private final HereWorldyPlugin plugin;

    public static class HereWorldyChestHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public BoxManager(HereWorldyPlugin plugin) {
        this.plugin = plugin;
    }

    public void openBox(Player player) {
        UUID uuid = player.getUniqueId();
        HereWorldyChestHolder holder = new HereWorldyChestHolder();
        
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.translateAlternateColorCodes('&', "&d&lHere My Stuff!"));
        holder.setInventory(inv);

        // Load items from file
        File file = new File(plugin.getDataFolder(), "chests/" + uuid + ".yml");
        plugin.getLogger().info("[DEBUG] openBox called for " + player.getName() + " (" + uuid + "). File exists: " + file.exists());
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<?> itemsList = config.getList("items");
            plugin.getLogger().info("[DEBUG] itemsList loaded: " + (itemsList != null ? itemsList.size() : "null"));
            if (itemsList != null) {
                ItemStack[] items = new ItemStack[inv.getSize()];
                int loadCount = 0;
                for (int i = 0; i < Math.min(items.length, itemsList.size()); i++) {
                    Object item = itemsList.get(i);
                    if (item != null) {
                        plugin.getLogger().info("[DEBUG] Slot " + i + " type: " + item.getClass().getName());
                    }
                    if (item instanceof ItemStack) {
                        items[i] = (ItemStack) item;
                        loadCount++;
                    }
                }
                inv.setContents(items);
                plugin.getLogger().info("[DEBUG] Set " + loadCount + " non-null ItemStacks to virtual chest.");
            }
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    public void saveBox(Player player, Inventory inventory) {
        UUID uuid = player.getUniqueId();
        File file = new File(plugin.getDataFolder(), "chests/" + uuid + ".yml");
        plugin.getLogger().info("[DEBUG] saveBox called for " + player.getName() + " (" + uuid + ")");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create chest file for player " + player.getName());
                return;
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ItemStack[] contents = inventory.getContents();
        int saveCount = 0;
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() != org.bukkit.Material.AIR) {
                saveCount++;
            }
        }
        plugin.getLogger().info("[DEBUG] Saving " + saveCount + " non-air/non-null items to file.");
        config.set("items", contents);

        try {
            config.save(file);
            plugin.getLogger().info("[DEBUG] Successfully saved " + file.getName());
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save chest file for player " + player.getName());
        }

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);
    }

    public boolean isHereWorldySign(Block block) {
        plugin.getLogger().info("[DEBUG] isHereWorldySign checking block: " + block.getType() + " at " + block.getLocation().getBlockX() + "," + block.getLocation().getBlockY() + "," + block.getLocation().getBlockZ());
        if (block.getState() instanceof Sign) {
            Sign sign = (Sign) block.getState();
            plugin.getLogger().info("[DEBUG] Sign lines FRONT: " + Arrays.toString(sign.getSide(Side.FRONT).getLines()));
            plugin.getLogger().info("[DEBUG] Sign lines BACK: " + Arrays.toString(sign.getSide(Side.BACK).getLines()));
            for (Side side : Side.values()) {
                SignSide signSide = sign.getSide(side);
                for (int i = 0; i < 4; i++) {
                    String lineRaw = signSide.getLine(i);
                    String line = ChatColor.stripColor(lineRaw).trim();
                    if (line.equalsIgnoreCase("[Here My Stuff!]") || line.equalsIgnoreCase("Here My Stuff!")) {
                        plugin.getLogger().info("[DEBUG] Match found for HereWorldy Sign!");
                        return true;
                    }
                }
            }
        } else {
            plugin.getLogger().info("[DEBUG] Block is not a Sign instance.");
        }
        return false;
    }
}
