# HereWorldy

A premium [Paper](https://papermc.io) Minecraft plugin for **Dynamic World Management, Custom 2D Gradient Portals, Isolated Inventory Groups, and Inter-dimensional Storage Box Sync** — build custom dimensions and travel through beautiful text-display gradient portal fields client-mod-free!

---

## Features

- **Dynamic World Creator & Manager**:
  - Dynamically generate new custom worlds using Bukkit's WorldCreator API.
  - Custom settings per world: select Environment (Overworld, Nether, End), Game Mode (Survival, Creative, Hardcore), and Difficulty levels.
  - Automatically preserves and loads all custom worlds on server restart.

- **Custom 2D Gradient Portals (Client-Mod-Free)**:
  - **Custom Frame Material Options**: Build portal frames vertically with a minimum size of 2x3 and maximum size of 5x10. Prohibits Obsidian/Crying Obsidian (to avoid Nether portal confusion), but allows *any other* solid block material (e.g. Dirt, Stone, Cobblestone, Wood, Copper, Iron).
  - **Clock Selection Tool**: Operators can execute `/hw portal-select` to toggle selection mode. Once enabled, left-click a block with any **Clock** to set Point A, and right-click to set Point B.
  - **Visual 2D Gradient Energy Field**: Running `/hw portal-set` spawns a static `TextDisplay` entity aligned with the portal frame. The panel renders a beautiful vertical/horizontal color gradient using MiniMessage solid block characters (`\u2588`).
  - **Configurable Colors**: Choose from preset colors (e.g. `WHITE`, `RED`, `BLUE`, `YELLOW`, `GREEN`, `PURPLE`, `AQUA`, `MAGENTA`) or custom hex codes (like `#4f46e5`).
  - **Portal Interactivity**: Walking into the portal starts a countdown (with sound and particle effects) before teleporting the player to the linked target coordinate.

- **Isolated Inventory Groups**:
  - Automatically groups worlds to manage inventory segregation.
  - Survival worlds can be placed in shared "Inventory Groups" to carry over inventories, experience, equipment, and potion effects.
  - Creative and Hardcore worlds maintain separate isolated player inventories.

- **Inter-dimensional Storage Box (`[Here My Stuff!]`)**:
  - Place a sign on any chest or shulker container and write `[Here My Stuff!]` on any line.
  - Interacting with the box opens a custom personal virtual inventory (Ender-chest style) that syncs items across all worlds and dimensions seamlessly.

---

## Commands

All commands can be executed using `/hw` or `/hereworldy`.

| Command | Description | Permission |
|---------|-------------|------------|
| `/hw create <name> <environment> <difficulty> <gamemode>` | Creates a new dynamic world | `hereworldy.admin` |
| `/hw delete <name>` | Unloads and deletes a custom world | `hereworldy.admin` |
| `/hw list` | Lists all active custom worlds | `hereworldy.admin` |
| `/hw tp <world> [x y z]` | Teleport to a target world location | `hereworldy.admin` |
| `/hw portal-select` | Toggles portal coordinates clock selection mode | `hereworldy.admin` |
| `/hw portal-set <name> <targetWorld> <tx> <ty> <tz> [c1] [c2]` | Links selected frame area to destination with gradient colors | `hereworldy.admin` |

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `hereworldy.admin` | Allows full control over HereWorldy commands and settings | `op` |
| `hereworldy.use` | Allow players to use portals and inter-dimensional boxes | `true` |

---

## Building from Source

Package the plugin using Gradle:
```bash
./gradlew build
```
The compiled plugin JAR will be saved in `build/libs/`.

---

## Recent Changes (v1.0.1)

- **Stability & Performance Optimization**: Added chunk-loaded validations, empty-world checks, and proximity checks to the portal task loop to prevent loading or keeping chunks loaded unnecessarily.
- **Chest & Sign Lookup Optimization**: Implemented fast-path block type checks before retrieving sign block states, minimizing JVM garbage collection overhead.
- **Version Bump**: Incremented version to `1.0.1` following `v.r.b` versioning.

---

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

