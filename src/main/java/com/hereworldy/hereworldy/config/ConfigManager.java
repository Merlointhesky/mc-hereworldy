package com.hereworldy.hereworldy.config;

import com.hereworldy.hereworldy.HereWorldyPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {

    private final HereWorldyPlugin plugin;
    private File worldsFile;
    private FileConfiguration worldsConfig;
    private File portalsFile;
    private FileConfiguration portalsConfig;

    public ConfigManager(HereWorldyPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        loadConfigs();
    }

    public void loadConfigs() {
        // Worlds Config
        worldsFile = new File(plugin.getDataFolder(), "worlds.yml");
        if (!worldsFile.exists()) {
            try {
                worldsFile.getParentFile().mkdirs();
                worldsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create worlds.yml!");
            }
        }
        worldsConfig = YamlConfiguration.loadConfiguration(worldsFile);

        // Portals Config
        portalsFile = new File(plugin.getDataFolder(), "portals.yml");
        if (!portalsFile.exists()) {
            try {
                portalsFile.getParentFile().mkdirs();
                portalsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create portals.yml!");
            }
        }
        portalsConfig = YamlConfiguration.loadConfiguration(portalsFile);
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        loadConfigs();
    }

    public FileConfiguration getWorldsConfig() {
        return worldsConfig;
    }

    public void saveWorldsConfig() {
        try {
            worldsConfig.save(worldsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save worlds.yml!");
        }
    }

    public FileConfiguration getPortalsConfig() {
        return portalsConfig;
    }

    public void savePortalsConfig() {
        try {
            portalsConfig.save(portalsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save portals.yml!");
        }
    }
}
