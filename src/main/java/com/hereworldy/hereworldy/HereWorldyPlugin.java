package com.hereworldy.hereworldy;

import com.hereworldy.hereworldy.command.HereWorldyCommand;
import com.hereworldy.hereworldy.config.ConfigManager;
import com.hereworldy.hereworldy.listener.PlayerListener;
import com.hereworldy.hereworldy.manager.BoxManager;
import com.hereworldy.hereworldy.manager.InventoryManager;
import com.hereworldy.hereworldy.manager.PortalManager;
import com.hereworldy.hereworldy.manager.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class HereWorldyPlugin extends JavaPlugin {

    private static HereWorldyPlugin instance;

    private ConfigManager configManager;
    private WorldManager worldManager;
    private PortalManager portalManager;
    private InventoryManager inventoryManager;
    private BoxManager boxManager;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize configuration files
        this.configManager = new ConfigManager(this);

        // Initialize Managers
        this.worldManager = new WorldManager(this, configManager);
        this.portalManager = new PortalManager(this, configManager);
        this.inventoryManager = new InventoryManager(this, configManager);
        this.boxManager = new BoxManager(this);

        // Load dynamic worlds
        this.worldManager.loadAllWorlds();

        // Load active portals
        this.portalManager.loadPortals();
        this.portalManager.startTasks();

        // Register Command
        HereWorldyCommand commandExecutor = new HereWorldyCommand(this);
        getCommand("hereworldy").setExecutor(commandExecutor);
        getCommand("hereworldy").setTabCompleter(commandExecutor);

        // Register Listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        getLogger().info("HereWorldy " + getDescription().getVersion() + " has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        // Save all online player inventories before shutdown to prevent loss
        if (inventoryManager != null) {
            Bukkit.getOnlinePlayers().forEach(p -> 
                inventoryManager.saveInventory(p, inventoryManager.getGroupForWorld(p.getWorld().getName()))
            );
        }

        // Stop active portal tasks and remove display entities to prevent ghosting
        if (portalManager != null) {
            portalManager.stopTasks();
        }

        getLogger().info("HereWorldy disabled!");
    }

    public static HereWorldyPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public PortalManager getPortalManager() {
        return portalManager;
    }

    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public BoxManager getBoxManager() {
        return boxManager;
    }
}
