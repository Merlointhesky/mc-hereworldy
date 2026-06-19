package com.hereworldy.hereworldy.manager;

import com.hereworldy.hereworldy.HereWorldyPlugin;
import com.hereworldy.hereworldy.config.ConfigManager;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class InventoryManager {

    private final HereWorldyPlugin plugin;
    private final ConfigManager configManager;

    public InventoryManager(HereWorldyPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public String getGroupForWorld(String worldName) {
        FileConfiguration config = configManager.getConfig();
        org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("inventory-groups");
        if (section != null) {
            for (String group : section.getKeys(false)) {
                List<String> worlds = section.getStringList(group);
                for (String w : worlds) {
                    if (w.equalsIgnoreCase(worldName)) {
                        return group.toLowerCase();
                    }
                }
            }
        }
        // Default to the world's own name (isolated)
        return worldName.toLowerCase();
    }

    public void handleWorldChange(Player player, World from, World to) {
        String groupFrom = getGroupForWorld(from.getName());
        String groupTo = getGroupForWorld(to.getName());

        if (!groupFrom.equals(groupTo)) {
            // Save inventory for the old group
            saveInventory(player, groupFrom);

            // Clear player's inventory, status effects, levels
            clearPlayer(player);

            // Load inventory for the new group
            loadInventory(player, groupTo);
        }
    }

    public void saveInventory(Player player, String group) {
        File file = new File(plugin.getDataFolder(), "inventories/" + player.getUniqueId() + "/" + group + ".yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create inventory file for " + player.getName() + " in group " + group);
                return;
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        // Save contents
        config.set("inventory.contents", player.getInventory().getContents());
        config.set("stats.level", player.getLevel());
        config.set("stats.exp", player.getExp());
        config.set("stats.health", player.getHealth());
        config.set("stats.food", player.getFoodLevel());
        config.set("stats.gamemode", player.getGameMode().name());

        // Save potion effects
        List<PotionEffect> effects = new ArrayList<>(player.getActivePotionEffects());
        config.set("stats.potion-effects", effects);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save inventory for " + player.getName());
        }
    }

    public void loadInventory(Player player, String group) {
        File file = new File(plugin.getDataFolder(), "inventories/" + player.getUniqueId() + "/" + group + ".yml");
        if (!file.exists()) {
            // No saved inventory for this group, let them start fresh
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Load contents
        List<?> contentsList = config.getList("inventory.contents");
        if (contentsList != null) {
            ItemStack[] contents = new ItemStack[player.getInventory().getContents().length];
            for (int i = 0; i < Math.min(contents.length, contentsList.size()); i++) {
                Object item = contentsList.get(i);
                if (item instanceof ItemStack) {
                    contents[i] = (ItemStack) item;
                }
            }
            player.getInventory().setContents(contents);
        }

        player.setLevel(config.getInt("stats.level", 0));
        player.setExp((float) config.getDouble("stats.exp", 0.0));
        player.setHealth(config.getDouble("stats.health", player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getDefaultValue()));
        player.setFoodLevel(config.getInt("stats.food", 20));

        // Load potion effects
        List<?> effectsList = config.getList("stats.potion-effects");
        if (effectsList != null) {
            for (Object obj : effectsList) {
                if (obj instanceof PotionEffect) {
                    player.addPotionEffect((PotionEffect) obj);
                }
            }
        }
    }

    public GameMode getSavedGameMode(Player player, String group) {
        File file = new File(plugin.getDataFolder(), "inventories/" + player.getUniqueId() + "/" + group + ".yml");
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String gmStr = config.getString("stats.gamemode");
        if (gmStr != null) {
            try {
                return GameMode.valueOf(gmStr);
            } catch (Exception e) {
                // Ignore
            }
        }
        return null;
    }

    public void resetInventory(Player player, String group) {
        File file = new File(plugin.getDataFolder(), "inventories/" + player.getUniqueId() + "/" + group + ".yml");
        if (file.exists()) {
            file.delete();
        }
    }

    private void clearPlayer(Player player) {
        player.getInventory().clear();
        player.setLevel(0);
        player.setExp(0);
        player.setFoodLevel(20);
        player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }
}
