package com.hereworldy.hereworldy.manager;

import com.hereworldy.hereworldy.HereWorldyPlugin;
import com.hereworldy.hereworldy.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.*;

public class WorldManager {

    private final HereWorldyPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, WorldSettings> customWorlds = new HashMap<>();

    public static class WorldSettings {
        public String name;
        public World.Environment environment;
        public Difficulty difficulty;
        public GameMode gameMode;

        public WorldSettings(String name, World.Environment environment, Difficulty difficulty, GameMode gameMode) {
            this.name = name;
            this.environment = environment;
            this.difficulty = difficulty;
            this.gameMode = gameMode;
        }
    }

    public WorldManager(HereWorldyPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void loadAllWorlds() {
        FileConfiguration config = configManager.getWorldsConfig();
        ConfigurationSection section = config.getConfigurationSection("worlds");
        if (section == null) return;

        boolean anyChanged = false;
        for (String key : section.getKeys(false)) {
            String envStr = section.getString(key + ".environment", "NORMAL");
            String diffStr = section.getString(key + ".difficulty", "NORMAL");
            String gmStr = section.getString(key + ".gamemode", "SURVIVAL");

            World.Environment env = World.Environment.valueOf(envStr);
            Difficulty diff = Difficulty.valueOf(diffStr);
            GameMode mode = GameMode.valueOf(gmStr);

            plugin.getLogger().info("Loading dynamic world: " + key + " (" + envStr + ")...");
            
            WorldCreator creator = new WorldCreator(key);
            creator.environment(env);
            World world = creator.createWorld();
            if (world != null) {
                world.setDifficulty(diff);
                customWorlds.put(key.toLowerCase(), new WorldSettings(key, env, diff, mode));
                if (addWorldToBlueMap(key, false)) {
                    anyChanged = true;
                }
            }
        }
        if (anyChanged) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bluemap reload");
        }
    }

    public boolean createWorld(String name, World.Environment env, Difficulty diff, GameMode mode) {
        if (Bukkit.getWorld(name) != null) {
            return false;
        }

        WorldCreator creator = new WorldCreator(name);
        creator.environment(env);
        World world = creator.createWorld();
        if (world == null) return false;

        world.setDifficulty(diff);

        // Save settings locally
        customWorlds.put(name.toLowerCase(), new WorldSettings(name, env, diff, mode));

        // Save settings to worlds.yml
        FileConfiguration config = configManager.getWorldsConfig();
        config.set("worlds." + name + ".environment", env.name());
        config.set("worlds." + name + ".difficulty", diff.name());
        config.set("worlds." + name + ".gamemode", mode.name());
        configManager.saveWorldsConfig();
        
        addWorldToBlueMap(name, true);
        return true;
    }

    public boolean deleteWorld(String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) return false;

        // Get the world folder before unloading
        File worldFolder = world.getWorldFolder();

        // Teleport players out of the world to main world spawn
        World mainWorld = Bukkit.getWorlds().get(0);
        world.getPlayers().forEach(p -> p.teleport(mainWorld.getSpawnLocation()));

        // Unload the world
        boolean unloaded = Bukkit.unloadWorld(world, false);
        if (!unloaded) return false;

        // Remove from configurations
        customWorlds.remove(name.toLowerCase());
        FileConfiguration config = configManager.getWorldsConfig();
        config.set("worlds." + name, null);
        configManager.saveWorldsConfig();

        // Delete world directory recursively
        deleteDirectory(worldFolder);

        deleteWorldFromBlueMap(name);
        return true;
    }

    public Map<String, WorldSettings> getCustomWorlds() {
        return customWorlds;
    }

    public WorldSettings getWorldSettings(String worldName) {
        return customWorlds.get(worldName.toLowerCase());
    }

    private void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteDirectory(child);
                }
            }
        }
        file.delete();
    }

    private boolean addWorldToBlueMap(String worldName, boolean reload) {
        if (!plugin.getConfig().getBoolean("bluemap-integration.enabled", true)) return false;

        File pluginsFolder = plugin.getDataFolder().getParentFile();
        File bluemapMapsFolder = new File(pluginsFolder, "BlueMap/maps");
        if (bluemapMapsFolder.exists() && bluemapMapsFolder.isDirectory()) {
            File mapConfigFile = new File(bluemapMapsFolder, worldName + ".conf");
            
            World.Environment env = World.Environment.NORMAL;
            WorldSettings settings = customWorlds.get(worldName.toLowerCase());
            if (settings != null) {
                env = settings.environment;
            }
            
            String dim = "minecraft:overworld";
            if (env == World.Environment.NETHER) {
                dim = "minecraft:the_nether";
            } else if (env == World.Environment.THE_END) {
                dim = "minecraft:the_end";
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) return false;
            File worldFolder = world.getWorldFolder();
            String relativePath;
            try {
                relativePath = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize()
                    .relativize(worldFolder.toPath().toAbsolutePath().normalize())
                    .toString().replace('\\', '/');
            } catch (Exception e) {
                relativePath = worldName;
            }
            
            boolean needsUpdate = true;
            if (mapConfigFile.exists()) {
                try {
                    List<String> lines = java.nio.file.Files.readAllLines(mapConfigFile.toPath());
                    boolean hasCorrectWorld = false;
                    boolean hasCorrectDim = false;
                    for (String line : lines) {
                        if (line.trim().startsWith("world:") && line.contains("\"" + relativePath + "\"")) {
                            hasCorrectWorld = true;
                        }
                        if (line.trim().startsWith("dimension:") && line.contains("\"" + dim + "\"")) {
                            hasCorrectDim = true;
                        }
                    }
                    if (hasCorrectWorld && hasCorrectDim) {
                        needsUpdate = false;
                    }
                } catch (Exception e) {
                    // Ignore and overwrite
                }
            }
            
            if (needsUpdate) {
                try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(mapConfigFile))) {
                    writer.println("world: \"" + relativePath + "\"");
                    writer.println("dimension: \"" + dim + "\"");
                    writer.println("name: \"" + worldName + "\"");
                    writer.println("sorting: 200");
                    plugin.getLogger().info("Automatically created/updated BlueMap map configuration for " + worldName);
                    
                    if (reload) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bluemap reload");
                    }
                    return true;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to create BlueMap configuration for " + worldName + ": " + e.getMessage());
                }
            }
        }
        return false;
    }

    private void deleteWorldFromBlueMap(String worldName) {
        if (!plugin.getConfig().getBoolean("bluemap-integration.enabled", true)) return;

        File pluginsFolder = plugin.getDataFolder().getParentFile();
        File bluemapMapsFolder = new File(pluginsFolder, "BlueMap/maps");
        if (bluemapMapsFolder.exists() && bluemapMapsFolder.isDirectory()) {
            File mapConfigFile = new File(bluemapMapsFolder, worldName + ".conf");
            if (mapConfigFile.exists()) {
                if (mapConfigFile.delete()) {
                    plugin.getLogger().info("Automatically removed BlueMap map configuration for " + worldName);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bluemap reload");
                } else {
                    plugin.getLogger().warning("Failed to delete BlueMap configuration file for " + worldName);
                }
            }
        }
    }
}
