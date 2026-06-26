package com.hereworldy.hereworldy.manager;

import com.hereworldy.hereworldy.HereWorldyPlugin;
import com.hereworldy.hereworldy.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class PortalManager {

    private final HereWorldyPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, PortalData> portals = new HashMap<>();
    private final Map<UUID, Long> warmupPlayers = new HashMap<>();
    private final Map<UUID, String> activeWarmups = new HashMap<>();
    private BukkitTask portalTask;

    // Pre-set colors mapped to modern hex codes
    private static final Map<String, String> COLOR_PRESETS = new HashMap<>();
    static {
        COLOR_PRESETS.put("WHITE", "#FFFFFF");
        COLOR_PRESETS.put("RED", "#EF4444");
        COLOR_PRESETS.put("BLUE", "#3B82F6");
        COLOR_PRESETS.put("YELLOW", "#F59E0B");
        COLOR_PRESETS.put("GREEN", "#10B981");
        COLOR_PRESETS.put("PURPLE", "#8B5CF6");
        COLOR_PRESETS.put("AQUA", "#06B6D4");
        COLOR_PRESETS.put("MAGENTA", "#D946EF");
        COLOR_PRESETS.put("ORANGE", "#F97316");
    }

    public static class PortalData {
        public String name;
        public String worldName;
        public int minX, maxX, minY, maxY, minZ, maxZ;
        public String targetWorldName;
        public double tx, ty, tz;
        public String color;
        public Material frameMaterial;

        public boolean contains(Location loc) {
            if (!loc.getWorld().getName().equals(worldName)) return false;
            double px = loc.getX();
            double py = loc.getY();
            double pz = loc.getZ();
            return px >= minX && px <= maxX + 1 &&
                   py >= minY && py <= maxY + 1 &&
                   pz >= minZ && pz <= maxZ + 1;
        }

        public Location getCenter() {
            World w = Bukkit.getWorld(worldName);
            return new Location(w, 
                (minX + maxX + 1) / 2.0, 
                (minY + maxY + 1) / 2.0, 
                (minZ + maxZ + 1) / 2.0
            );
        }

        public Location getSpawnLocation() {
            World w = Bukkit.getWorld(worldName);
            return new Location(w, 
                (minX + maxX + 1) / 2.0, 
                minY, 
                (minZ + maxZ + 1) / 2.0
            );
        }
    }

    public PortalManager(HereWorldyPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public static String resolveColor(String colorInput) {
        if (colorInput == null) return null;
        String upper = colorInput.toUpperCase();
        if (COLOR_PRESETS.containsKey(upper)) {
            return COLOR_PRESETS.get(upper);
        }
        if (colorInput.startsWith("#")) {
            return colorInput;
        }
        return null;
    }

    private org.bukkit.Color parseColor(String hex) {
        if (hex == null || !hex.startsWith("#") || hex.length() < 7) {
            return org.bukkit.Color.fromRGB(59, 130, 246); // Default Blue
        }
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            return org.bukkit.Color.fromRGB(r, g, b);
        } catch (Exception e) {
            return org.bukkit.Color.fromRGB(59, 130, 246);
        }
    }

    public void loadPortals() {
        portals.clear();
        FileConfiguration config = configManager.getPortalsConfig();
        ConfigurationSection section = config.getConfigurationSection("portals");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            PortalData data = new PortalData();
            data.name = key;
            data.worldName = section.getString(key + ".world");
            data.minX = section.getInt(key + ".min-x");
            data.maxX = section.getInt(key + ".max-x");
            data.minY = section.getInt(key + ".min-y");
            data.maxY = section.getInt(key + ".max-y");
            data.minZ = section.getInt(key + ".min-z");
            data.maxZ = section.getInt(key + ".max-z");
            data.targetWorldName = section.getString(key + ".target-world");
            data.tx = section.getDouble(key + ".tx");
            data.ty = section.getDouble(key + ".ty");
            data.tz = section.getDouble(key + ".tz");
            data.color = section.getString(key + ".color", section.getString(key + ".start-color", "#3B82F6"));
            
            String matStr = section.getString(key + ".frame-material", "STONE");
            try {
                data.frameMaterial = Material.valueOf(matStr);
            } catch (Exception e) {
                data.frameMaterial = Material.STONE;
            }

            portals.put(key.toLowerCase(), data);
        }
    }

    public void startTasks() {
        if (portalTask != null) {
            portalTask.cancel();
        }

        portalTask = new BukkitRunnable() {
            @Override
            public void run() {
                FileConfiguration mainConfig = configManager.getConfig();
                boolean particlesEnabled = mainConfig.getBoolean("portal-particles.enabled", true);
                
                double warmupTimeMs = mainConfig.getDouble("portal-warmup-seconds", 3) * 1000;

                for (PortalData portal : portals.values()) {
                    World world = Bukkit.getWorld(portal.worldName);
                    if (world == null) continue;

                    // Skip processing if there are no players in the world at all
                    if (world.getPlayers().isEmpty()) continue;

                    // Check if the chunks containing the portal coordinates are loaded
                    int minChunkX = portal.minX >> 4;
                    int maxChunkX = portal.maxX >> 4;
                    int minChunkZ = portal.minZ >> 4;
                    int maxChunkZ = portal.maxZ >> 4;
                    boolean chunksLoaded = true;
                    for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                            if (!world.isChunkLoaded(cx, cz)) {
                                chunksLoaded = false;
                                break;
                            }
                        }
                        if (!chunksLoaded) break;
                    }
                    if (!chunksLoaded) continue;

                    // Proximity check: only process portals where at least one player is nearby (within 64 blocks)
                    // or if the player is currently warming up for this portal.
                    double centerX = (portal.minX + portal.maxX + 1) / 2.0;
                    double centerY = (portal.minY + portal.maxY + 1) / 2.0;
                    double centerZ = (portal.minZ + portal.maxZ + 1) / 2.0;
                    boolean playerNearby = false;
                    for (Player player : world.getPlayers()) {
                        UUID uuid = player.getUniqueId();
                        if (activeWarmups.containsKey(uuid) && activeWarmups.get(uuid).equals(portal.name)) {
                            playerNearby = true;
                            break;
                        }
                        Location pl = player.getLocation();
                        double dx = pl.getX() - centerX;
                        double dy = pl.getY() - centerY;
                        double dz = pl.getZ() - centerZ;
                        if (dx * dx + dy * dy + dz * dz <= 64 * 64) {
                            playerNearby = true;
                            break;
                        }
                    }
                    if (!playerNearby) continue;

                    // 1. Spawn particles inside portal volume (only in AIR blocks)
                    if (particlesEnabled) {
                        org.bukkit.Color pColor = parseColor(portal.color);
                        Particle.DustOptions dustOptions = new Particle.DustOptions(pColor, 1.2f);
                        for (int x = portal.minX; x <= portal.maxX; x++) {
                            for (int y = portal.minY; y <= portal.maxY; y++) {
                                for (int z = portal.minZ; z <= portal.maxZ; z++) {
                                    if (world.getBlockAt(x, y, z).getType().isAir()) {
                                        for (int i = 0; i < 3; i++) {
                                            double rx = x + Math.random();
                                            double ry = y + Math.random();
                                            double rz = z + Math.random();
                                            world.spawnParticle(Particle.DUST, rx, ry, rz, 1, 0.0, 0.0, 0.0, 0.0, dustOptions);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Check players
                    for (Player player : world.getPlayers()) {
                        UUID uuid = player.getUniqueId();
                        Location loc = player.getLocation();

                        if (portal.contains(loc)) {
                            // Player is inside portal
                            if (!activeWarmups.containsKey(uuid)) {
                                activeWarmups.put(uuid, portal.name);
                                warmupPlayers.put(uuid, System.currentTimeMillis());
                                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6Entering portal to " + portal.targetWorldName + "... Stand still!"));
                            } else if (activeWarmups.get(uuid).equals(portal.name)) {
                                long start = warmupPlayers.get(uuid);
                                long elapsed = System.currentTimeMillis() - start;

                                if (elapsed >= warmupTimeMs) {
                                    // Teleport player!
                                    activeWarmups.remove(uuid);
                                    warmupPlayers.remove(uuid);
                                    teleportPlayer(player, portal);
                                } else {
                                    // Visual/sound tick
                                    player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 1.2f);
                                }
                            }
                        } else {
                            // Player is NOT inside this portal
                            if (activeWarmups.containsKey(uuid) && activeWarmups.get(uuid).equals(portal.name)) {
                                activeWarmups.remove(uuid);
                                warmupPlayers.remove(uuid);
                                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cPortal transition cancelled."));
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 4L, 4L); // Run every 4 ticks (0.2s)
    }

    public void stopTasks() {
        if (portalTask != null) {
            portalTask.cancel();
            portalTask = null;
        }
    }

    private void teleportPlayer(Player player, PortalData portal) {
        World targetWorld = Bukkit.getWorld(portal.targetWorldName);
        if (targetWorld == null) {
            player.sendMessage(ChatColor.RED + "Target world '" + portal.targetWorldName + "' is not loaded!");
            return;
        }

        Location dest = new Location(targetWorld, portal.tx, portal.ty, portal.tz, player.getLocation().getYaw(), player.getLocation().getPitch());
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        
        // Use Bukkit Scheduler to teleport safely
        new BukkitRunnable() {
            @Override
            public void run() {
                player.teleport(dest);
                player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                player.sendMessage(ChatColor.GREEN + "Switched dimensions to " + portal.targetWorldName + "!");
            }
        }.runTask(plugin);
    }

    public Material validateFrame(Location p1, Location p2) throws IllegalArgumentException {
        if (!p1.getWorld().equals(p2.getWorld())) {
            throw new IllegalArgumentException("Points must be in the same world.");
        }

        int x1 = p1.getBlockX();
        int y1 = p1.getBlockY();
        int z1 = p1.getBlockZ();

        int x2 = p2.getBlockX();
        int y2 = p2.getBlockY();
        int z2 = p2.getBlockZ();

        // 1. Must be vertically flat (aligned with either X or Z axis)
        boolean flatX = (x1 == x2);
        boolean flatZ = (z1 == z2);

        if (!flatX && !flatZ) {
            throw new IllegalArgumentException("Portal selection must be a flat vertical plane (constant X or constant Z).");
        }

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        // Bounding box dimensions (including frame)
        int totalWidth = flatX ? (maxZ - minZ + 1) : (maxX - minX + 1);
        int totalHeight = (maxY - minY + 1);

        // Inner dimensions (excluding frame boundary)
        int innerWidth = totalWidth - 2;
        int innerHeight = totalHeight - 2;

        if (innerWidth < 2 || innerWidth > 5 || innerHeight < 3 || innerHeight > 10) {
            throw new IllegalArgumentException("Portal inner dimensions must be between 2x3 and 5x10. Current inner size is " + innerWidth + "x" + innerHeight + ".");
        }

        World world = p1.getWorld();
        Material frameMat = null;

        // Loop over the entire bounding box
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    // Check if it's on the boundary (frame block)
                    boolean isBoundary;
                    if (flatX) {
                        isBoundary = (y == minY || y == maxY || z == minZ || z == maxZ);
                    } else {
                        isBoundary = (y == minY || y == maxY || x == minX || x == maxX);
                    }

                    Block block = world.getBlockAt(x, y, z);

                    if (isBoundary) {
                        // Validate frame block
                        Material mat = block.getType();
                        if (!mat.isSolid()) {
                            throw new IllegalArgumentException("Frame block at [" + x + ", " + y + ", " + z + "] is not solid (" + mat.name() + ").");
                        }
                        if (mat == Material.OBSIDIAN || mat == Material.CRYING_OBSIDIAN) {
                            throw new IllegalArgumentException("Obsidian and Crying Obsidian cannot be used for the portal frame to avoid Nether portal conflicts.");
                        }

                        if (frameMat == null) {
                            frameMat = mat;
                        } else if (frameMat != mat) {
                            throw new IllegalArgumentException("Frame contains mixed block types: " + frameMat.name() + " and " + mat.name() + ". Must be uniform.");
                        }
                    } else {
                        // Validate inner blocks (must be air)
                        if (!block.getType().isAir()) {
                            throw new IllegalArgumentException("Portal inner block at [" + x + ", " + y + ", " + z + "] is not air (" + block.getType().name() + ").");
                        }
                    }
                }
            }
        }

        return frameMat;
    }

    public boolean createPortal(String name, Location p1, Location p2, String targetWorld, double tx, double ty, double tz, String color) {
        Material frameMat;
        try {
            frameMat = validateFrame(p1, p2);
        } catch (IllegalArgumentException e) {
            Bukkit.getLogger().warning("Portal creation validation failed: " + e.getMessage());
            return false;
        }

        int x1 = p1.getBlockX();
        int y1 = p1.getBlockY();
        int z1 = p1.getBlockZ();

        int x2 = p2.getBlockX();
        int y2 = p2.getBlockY();
        int z2 = p2.getBlockZ();

        boolean flatX = (x1 == x2);
        boolean flatZ = (z1 == z2);

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        PortalData data = new PortalData();
        data.name = name;
        data.worldName = p1.getWorld().getName();
        
        // Save the portal inner dimensions (where players step and displays spawn)
        data.minX = minX + (flatZ ? 1 : 0);
        data.maxX = maxX - (flatZ ? 1 : 0);
        data.minY = minY + 1;
        data.maxY = maxY - 1;
        data.minZ = minZ + (flatX ? 1 : 0);
        data.maxZ = maxZ - (flatX ? 1 : 0);
        
        data.targetWorldName = targetWorld;
        data.tx = tx;
        data.ty = ty;
        data.tz = tz;
        data.color = color;
        data.frameMaterial = frameMat;

        // Save portal configurations
        FileConfiguration config = configManager.getPortalsConfig();
        String path = "portals." + name;
        config.set(path + ".world", data.worldName);
        config.set(path + ".min-x", data.minX);
        config.set(path + ".max-x", data.maxX);
        config.set(path + ".min-y", data.minY);
        config.set(path + ".max-y", data.maxY);
        config.set(path + ".min-z", data.minZ);
        config.set(path + ".max-z", data.maxZ);
        config.set(path + ".target-world", data.targetWorldName);
        config.set(path + ".tx", data.tx);
        config.set(path + ".ty", data.ty);
        config.set(path + ".tz", data.tz);
        config.set(path + ".color", data.color);
        config.set(path + ".frame-material", data.frameMaterial.name());

        portals.put(name.toLowerCase(), data);

        // Automatically create and link the target counter portal
        createCounterPortal(data);

        configManager.savePortalsConfig();
        return true;
    }

    public boolean editPortal(String name, String targetWorld, double tx, double ty, double tz, String color) {
        PortalData data = portals.get(name.toLowerCase());
        if (data == null) return false;

        // Delete existing counter portal if it exists
        String counterName = data.name + "_counter";
        deletePortal(counterName);

        data.targetWorldName = targetWorld;
        data.tx = tx;
        data.ty = ty;
        data.tz = tz;
        if (color != null) data.color = color;

        // Save portal configurations
        FileConfiguration config = configManager.getPortalsConfig();
        String path = "portals." + data.name;
        config.set(path + ".target-world", data.targetWorldName);
        config.set(path + ".tx", data.tx);
        config.set(path + ".ty", data.ty);
        config.set(path + ".tz", data.tz);
        config.set(path + ".color", data.color);

        // Re-create counter portal
        createCounterPortal(data);

        configManager.savePortalsConfig();
        return true;
    }

    public boolean deletePortal(String name) {
        String lowerName = name.toLowerCase();
        PortalData data = portals.remove(lowerName);
        if (data == null) return false;

        FileConfiguration config = configManager.getPortalsConfig();
        config.set("portals." + data.name, null);
        configManager.savePortalsConfig();

        // Also delete counter portal if it exists and this is not already a counter portal deletion
        if (!lowerName.endsWith("_counter")) {
            String counterName = data.name + "_counter";
            if (portals.containsKey(counterName.toLowerCase())) {
                deletePortal(counterName);
            }
        }

        return true;
    }

    private void createCounterPortal(PortalData original) {
        World tw = Bukkit.getWorld(original.targetWorldName);
        if (tw == null) {
            plugin.getLogger().warning("Could not create counter portal: Target world '" + original.targetWorldName + "' is not loaded.");
            return;
        }

        // Convert target destination to block coordinates
        int targetBlockX = (int) Math.floor(original.tx);
        int targetBlockY = (int) Math.floor(original.ty);
        int targetBlockZ = (int) Math.floor(original.tz);

        // Find width, height, and axis orientation of original portal
        boolean axisX = (original.minX == original.maxX); // constant X -> aligned with Z axis
        int width = axisX ? (original.maxZ - original.minZ + 1) : (original.maxX - original.minX + 1);
        int height = (original.maxY - original.minY + 1);

        // Calculate counter portal inner dimensions (where players step and displays spawn)
        int cMinX, cMaxX, cMinY, cMaxY, cMinZ, cMaxZ;

        cMinY = targetBlockY;
        cMaxY = targetBlockY + height - 1;

        if (axisX) { // constant X
            cMinX = targetBlockX;
            cMaxX = targetBlockX;
            cMinZ = targetBlockZ - (width - 1) / 2;
            cMaxZ = cMinZ + width - 1;
        } else { // constant Z
            cMinZ = targetBlockZ;
            cMaxZ = targetBlockZ;
            cMinX = targetBlockX - (width - 1) / 2;
            cMaxX = cMinX + width - 1;
        }

        // Calculate clearance room bounds (2 blocks each side/ceiling)
        int roomMinX, roomMaxX, roomMinY, roomMaxY, roomMinZ, roomMaxZ;
        roomMinY = cMinY;
        roomMaxY = cMaxY + 3; // 2 blocks above top frame (cMaxY + 1)

        if (axisX) {
            roomMinX = cMinX - 2;
            roomMaxX = cMinX + 2;
            roomMinZ = cMinZ - 3; // 2 blocks from left frame (cMinZ - 1)
            roomMaxZ = cMaxZ + 3; // 2 blocks from right frame (cMaxZ + 1)
        } else {
            roomMinX = cMinX - 3; // 2 blocks from left frame (cMinX - 1)
            roomMaxX = cMaxX + 3; // 2 blocks from right frame (cMaxX + 1)
            roomMinZ = cMinZ - 2;
            roomMaxZ = cMinZ + 2;
        }

        // Pre-load chunks in room bounds to ensure safe modifications
        int minChunkX = roomMinX >> 4;
        int maxChunkX = roomMaxX >> 4;
        int minChunkZ = roomMinZ >> 4;
        int maxChunkZ = roomMaxZ >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                tw.getChunkAt(cx, cz).load(true);
            }
        }

        // Dig the room first (set blocks to AIR)
        for (int x = roomMinX; x <= roomMaxX; x++) {
            for (int y = roomMinY; y <= roomMaxY; y++) {
                for (int z = roomMinZ; z <= roomMaxZ; z++) {
                    tw.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }

        // Place the frame blocks
        if (axisX) {
            // Bottom frame
            for (int z = cMinZ - 1; z <= cMaxZ + 1; z++) {
                tw.getBlockAt(cMinX, cMinY - 1, z).setType(original.frameMaterial);
            }
            // Top frame
            for (int z = cMinZ - 1; z <= cMaxZ + 1; z++) {
                tw.getBlockAt(cMinX, cMaxY + 1, z).setType(original.frameMaterial);
            }
            // Left frame
            for (int y = cMinY; y <= cMaxY; y++) {
                tw.getBlockAt(cMinX, y, cMinZ - 1).setType(original.frameMaterial);
            }
            // Right frame
            for (int y = cMinY; y <= cMaxY; y++) {
                tw.getBlockAt(cMinX, y, cMaxZ + 1).setType(original.frameMaterial);
            }
        } else {
            // Bottom frame
            for (int x = cMinX - 1; x <= cMaxX + 1; x++) {
                tw.getBlockAt(x, cMinY - 1, cMinZ).setType(original.frameMaterial);
            }
            // Top frame
            for (int x = cMinX - 1; x <= cMaxX + 1; x++) {
                tw.getBlockAt(x, cMaxY + 1, cMinZ).setType(original.frameMaterial);
            }
            // Left frame
            for (int y = cMinY; y <= cMaxY; y++) {
                tw.getBlockAt(cMinX - 1, y, cMinZ).setType(original.frameMaterial);
            }
            // Right frame
            for (int y = cMinY; y <= cMaxY; y++) {
                tw.getBlockAt(cMaxX + 1, y, cMinZ).setType(original.frameMaterial);
            }
        }

        // Create the counter portal data
        PortalData counter = new PortalData();
        counter.name = original.name + "_counter";
        counter.worldName = original.targetWorldName;
        counter.minX = cMinX;
        counter.maxX = cMaxX;
        counter.minY = cMinY;
        counter.maxY = cMaxY;
        counter.minZ = cMinZ;
        counter.maxZ = cMaxZ;
        counter.targetWorldName = original.worldName;
        
        // Link target coordinates back to the original portal spawn location
        counter.tx = (original.minX + original.maxX + 1) / 2.0;
        counter.ty = original.minY;
        counter.tz = (original.minZ + original.maxZ + 1) / 2.0;
        
        counter.color = original.color;
        counter.frameMaterial = original.frameMaterial;

        // Register and spawn display
        portals.put(counter.name.toLowerCase(), counter);

        // Save counter portal to config
        FileConfiguration config = configManager.getPortalsConfig();
        String path = "portals." + counter.name;
        config.set(path + ".world", counter.worldName);
        config.set(path + ".min-x", counter.minX);
        config.set(path + ".max-x", counter.maxX);
        config.set(path + ".min-y", counter.minY);
        config.set(path + ".max-y", counter.maxY);
        config.set(path + ".min-z", counter.minZ);
        config.set(path + ".max-z", counter.maxZ);
        config.set(path + ".target-world", counter.targetWorldName);
        config.set(path + ".tx", counter.tx);
        config.set(path + ".ty", counter.ty);
        config.set(path + ".tz", counter.tz);
        config.set(path + ".color", counter.color);
        config.set(path + ".frame-material", counter.frameMaterial.name());
    }

    public Map<String, PortalData> getPortals() {
        return portals;
    }
}
