package com.hereworldy.hereworldy.util;

import org.bukkit.Location;
import java.util.*;

public final class SelectionUtil {

    private static final Map<UUID, Location> pos1 = new HashMap<>();
    private static final Map<UUID, Location> pos2 = new HashMap<>();
    private static final Set<UUID> selectionMode = new HashSet<>();

    private SelectionUtil() {}

    public static boolean toggleSelectionMode(UUID uuid) {
        if (selectionMode.contains(uuid)) {
            selectionMode.remove(uuid);
            return false;
        } else {
            selectionMode.add(uuid);
            return true;
        }
    }

    public static boolean isSelectionMode(UUID uuid) {
        return selectionMode.contains(uuid);
    }

    public static void setPos1(UUID uuid, Location loc) {
        pos1.put(uuid, loc);
    }

    public static void setPos2(UUID uuid, Location loc) {
        pos2.put(uuid, loc);
    }

    public static Location getPos1(UUID uuid) {
        return pos1.get(uuid);
    }

    public static Location getPos2(UUID uuid) {
        return pos2.get(uuid);
    }

    public static void clearSelection(UUID uuid) {
        pos1.remove(uuid);
        pos2.remove(uuid);
    }
}
