package com.hereworldy.hereworldy.command;

import com.hereworldy.hereworldy.HereWorldyPlugin;
import com.hereworldy.hereworldy.manager.WorldManager;
import com.hereworldy.hereworldy.manager.PortalManager;
import com.hereworldy.hereworldy.util.SelectionUtil;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;

public class HereWorldyCommand implements CommandExecutor, TabCompleter {

    private final HereWorldyPlugin plugin;
    private final WorldManager worldManager;
    private final PortalManager portalManager;

    public HereWorldyCommand(HereWorldyPlugin plugin) {
        this.plugin = plugin;
        this.worldManager = plugin.getWorldManager();
        this.portalManager = plugin.getPortalManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hereworldy.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to execute this command!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
                handleCreate(sender, args);
                break;
            case "delete":
                handleDelete(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "tp":
                handleTp(sender, args);
                break;
            case "portal-select":
                handlePortalSelect(sender);
                break;
            case "portal-set":
                handlePortalSet(sender, args);
                break;
            case "portal-edit":
                handlePortalEdit(sender, args);
                break;
            case "portal-delete":
                handlePortalDelete(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&d&lHereWorldy Administration Tools"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f/hw create <name> <environment> <difficulty> <gamemode>"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f/hw delete <name>"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f/hw list"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f/hw tp <world> [x y z]"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f/hw portal-select &7(Toggle Clock selection mode)"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f/hw portal-set <name> <targetWorld> <tx> <ty> <tz> [color]"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f/hw portal-edit <name> <targetWorld> <tx> <ty> <tz> [color]"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f/hw portal-delete <name>"));
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /hw create <name> <environment> <difficulty> <gamemode>");
            return;
        }

        String name = args[1];
        
        World.Environment env;
        try {
            env = World.Environment.valueOf(args[2].toUpperCase());
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Invalid environment! Choose NORMAL, NETHER, or THE_END.");
            return;
        }

        Difficulty diff;
        try {
            diff = Difficulty.valueOf(args[3].toUpperCase());
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Invalid difficulty! Choose PEACEFUL, EASY, NORMAL, or HARD.");
            return;
        }

        GameMode mode;
        String modeStr = args[4].toUpperCase();
        if (modeStr.equals("HARDCORE")) {
            mode = GameMode.SURVIVAL;
        } else {
            try {
                mode = GameMode.valueOf(modeStr);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Invalid gamemode! Choose SURVIVAL, CREATIVE, or HARDCORE.");
                return;
            }
        }

        sender.sendMessage(ChatColor.GREEN + "Generating world '" + name + "'... (This may take a moment)");
        boolean success = worldManager.createWorld(name, env, diff, mode);

        if (success) {
            // If hardcore, we save it as survival but note hardcore setting
            if (modeStr.equals("HARDCORE")) {
                plugin.getConfigManager().getWorldsConfig().set("worlds." + name + ".hardcore", true);
                plugin.getConfigManager().saveWorldsConfig();
            }
            sender.sendMessage(ChatColor.GREEN + "World '" + name + "' successfully created and loaded!");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to create world! Either a world with that name already exists, or generation failed.");
        }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /hw delete <name>");
            return;
        }

        String name = args[1];
        sender.sendMessage(ChatColor.YELLOW + "Unloading and deleting world '" + name + "'...");
        boolean success = worldManager.deleteWorld(name);

        if (success) {
            sender.sendMessage(ChatColor.GREEN + "World '" + name + "' permanently deleted.");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to delete world! Make sure it exists and can be unloaded.");
        }
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&d&lRegistered Dynamic Worlds:"));
        Map<String, WorldManager.WorldSettings> settingsMap = worldManager.getCustomWorlds();

        if (settingsMap.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No custom worlds loaded.");
            return;
        }

        for (WorldManager.WorldSettings s : settingsMap.values()) {
            boolean isHardcore = plugin.getConfigManager().getWorldsConfig().getBoolean("worlds." + s.name + ".hardcore", false);
            String modeStr = isHardcore ? "HARDCORE" : s.gameMode.name();
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&a- &f" + s.name + " &7[" + s.environment.name() + " | " + s.difficulty.name() + " | " + modeStr + "]"));
        }
    }

    private void handleTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can teleport!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /hw tp <world> [x y z]");
            return;
        }

        Player player = (Player) sender;
        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            sender.sendMessage(ChatColor.RED + "World '" + worldName + "' is not loaded!");
            return;
        }

        Location loc;
        if (args.length >= 5) {
            try {
                double x = Double.parseDouble(args[2]);
                double y = Double.parseDouble(args[3]);
                double z = Double.parseDouble(args[4]);
                loc = new Location(world, x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid coordinates format!");
                return;
            }
        } else {
            loc = world.getSpawnLocation();
        }

        player.teleport(loc);
        player.sendMessage(ChatColor.GREEN + "Teleported to " + world.getName());
    }

    private void handlePortalSelect(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can select portal zones!");
            return;
        }

        Player player = (Player) sender;
        boolean active = SelectionUtil.toggleSelectionMode(player.getUniqueId());

        if (active) {
            SelectionUtil.clearSelection(player.getUniqueId());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&dPortal selection mode &aENABLED&d. Click any block with a Clock to set Point A, and click another to set Point B."));
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&dPortal selection mode &cDISABLED&d."));
        }
    }

    private void handlePortalSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can set portals!");
            return;
        }

        if (args.length < 6) {
            sender.sendMessage(ChatColor.RED + "Usage: /hw portal-set <name> <targetWorld> <tx> <ty> <tz> [color]");
            return;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        Location pos1 = SelectionUtil.getPos1(uuid);
        Location pos2 = SelectionUtil.getPos2(uuid);

        if (pos1 == null || pos2 == null) {
            player.sendMessage(ChatColor.RED + "You must select Point A and Point B first! Use a Clock while in selection mode (/hw portal-select).");
            return;
        }

        String name = args[1];
        String targetWorld = args[2];

        if (org.bukkit.Bukkit.getWorld(targetWorld) == null) {
            player.sendMessage(ChatColor.RED + "Target world '" + targetWorld + "' is not loaded or does not exist!");
            return;
        }

        double tx, ty, tz;
        try {
            tx = Double.parseDouble(args[3]);
            ty = Double.parseDouble(args[4]);
            tz = Double.parseDouble(args[5]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid target coordinates!");
            return;
        }

        // Handle custom color
        String color = "#3B82F6"; // Default Blue

        if (args.length >= 7) {
            String c = PortalManager.resolveColor(args[6]);
            if (c != null) color = c;
            else {
                player.sendMessage(ChatColor.YELLOW + "Invalid color '" + args[6] + "', defaulting to Blue.");
            }
        }

        boolean success = portalManager.createPortal(name, pos1, pos2, targetWorld, tx, ty, tz, color);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "Portal '" + name + "' successfully created and active!");
            SelectionUtil.clearSelection(uuid);
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create portal. Ensure the frame built surrounding your selection is complete, contains no obsidian, and uses a single solid block material!");
        }
    }

    private void handlePortalEdit(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(ChatColor.RED + "Usage: /hw portal-edit <name> <targetWorld> <tx> <ty> <tz> [color]");
            return;
        }

        String name = args[1];
        String targetWorld = args[2];

        if (org.bukkit.Bukkit.getWorld(targetWorld) == null) {
            sender.sendMessage(ChatColor.RED + "Target world '" + targetWorld + "' is not loaded or does not exist!");
            return;
        }

        double tx, ty, tz;
        try {
            tx = Double.parseDouble(args[3]);
            ty = Double.parseDouble(args[4]);
            tz = Double.parseDouble(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid coordinates!");
            return;
        }

        String color = null;

        if (args.length >= 7) {
            color = PortalManager.resolveColor(args[6]);
            if (color == null) {
                sender.sendMessage(ChatColor.YELLOW + "Invalid color '" + args[6] + "'. Keeping existing color.");
            }
        }

        boolean success = portalManager.editPortal(name, targetWorld, tx, ty, tz, color);

        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Portal '" + name + "' successfully updated!");
        } else {
            sender.sendMessage(ChatColor.RED + "Portal '" + name + "' not found!");
        }
    }

    private void handlePortalDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /hw portal-delete <name>");
            return;
        }

        String name = args[1];
        boolean success = portalManager.deletePortal(name);

        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Portal '" + name + "' successfully deleted!");
        } else {
            sender.sendMessage(ChatColor.RED + "Portal '" + name + "' not found!");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("hereworldy.admin")) return Collections.emptyList();

        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = Arrays.asList("create", "delete", "list", "tp", "portal-select", "portal-set", "portal-edit", "portal-delete");
            StringUtil.copyPartialMatches(args[0], subs, list);
            Collections.sort(list);
            return list;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("create")) {
            if (args.length == 3) {
                List<String> envs = Arrays.asList("NORMAL", "NETHER", "THE_END");
                StringUtil.copyPartialMatches(args[2], envs, list);
                return list;
            }
            if (args.length == 4) {
                List<String> diffs = Arrays.asList("PEACEFUL", "EASY", "NORMAL", "HARD");
                StringUtil.copyPartialMatches(args[3], diffs, list);
                return list;
            }
            if (args.length == 5) {
                List<String> modes = Arrays.asList("SURVIVAL", "CREATIVE", "HARDCORE");
                StringUtil.copyPartialMatches(args[4], modes, list);
                return list;
            }
        }

        if (sub.equals("delete") || sub.equals("tp")) {
            if (args.length == 2) {
                List<String> worlds = new ArrayList<>();
                Bukkit.getWorlds().forEach(w -> worlds.add(w.getName()));
                StringUtil.copyPartialMatches(args[1], worlds, list);
                Collections.sort(list);
                return list;
            }
        }

        if (sub.equals("portal-delete")) {
            if (args.length == 2) {
                List<String> portalNames = new ArrayList<>(portalManager.getPortals().keySet());
                StringUtil.copyPartialMatches(args[1], portalNames, list);
                Collections.sort(list);
                return list;
            }
        }

        if (sub.equals("portal-set")) {
            if (args.length == 3) {
                List<String> worlds = new ArrayList<>();
                Bukkit.getWorlds().forEach(w -> worlds.add(w.getName()));
                StringUtil.copyPartialMatches(args[2], worlds, list);
                Collections.sort(list);
                return list;
            }
            if (args.length == 4 || args.length == 5 || args.length == 6) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    org.bukkit.block.Block target = p.getTargetBlockExact(5);
                    if (target != null) {
                        if (args.length == 4) list.add(String.valueOf(target.getX()));
                        if (args.length == 5) list.add(String.valueOf(target.getY()));
                        if (args.length == 6) list.add(String.valueOf(target.getZ()));
                    }
                }
                return list;
            }
            if (args.length == 7) {
                List<String> colors = Arrays.asList("WHITE", "RED", "BLUE", "YELLOW", "GREEN", "PURPLE", "AQUA", "MAGENTA", "ORANGE");
                StringUtil.copyPartialMatches(args[6], colors, list);
                Collections.sort(list);
                return list;
            }
        }

        if (sub.equals("portal-edit")) {
            if (args.length == 2) {
                List<String> portalNames = new ArrayList<>(portalManager.getPortals().keySet());
                StringUtil.copyPartialMatches(args[1], portalNames, list);
                Collections.sort(list);
                return list;
            }
            if (args.length == 3) {
                List<String> worlds = new ArrayList<>();
                Bukkit.getWorlds().forEach(w -> worlds.add(w.getName()));
                StringUtil.copyPartialMatches(args[2], worlds, list);
                Collections.sort(list);
                return list;
            }
            if (args.length == 4 || args.length == 5 || args.length == 6) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    org.bukkit.block.Block target = p.getTargetBlockExact(5);
                    if (target != null) {
                        if (args.length == 4) list.add(String.valueOf(target.getX()));
                        if (args.length == 5) list.add(String.valueOf(target.getY()));
                        if (args.length == 6) list.add(String.valueOf(target.getZ()));
                    }
                }
                return list;
            }
            if (args.length == 7) {
                List<String> colors = Arrays.asList("WHITE", "RED", "BLUE", "YELLOW", "GREEN", "PURPLE", "AQUA", "MAGENTA", "ORANGE");
                StringUtil.copyPartialMatches(args[6], colors, list);
                Collections.sort(list);
                return list;
            }
        }

        return Collections.emptyList();
    }
}
