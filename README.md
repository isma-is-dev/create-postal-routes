# Create: Postal Routes

**See your Create postal network on Xaero's World Map.** Chain conveyor routes, frogports,
postboxes and every package currently in transit, drawn live in postal amber — clearly
distinct from Create's blue-green train map.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-2f8d46)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-f16436)](https://neoforged.net)
[![Create](https://img.shields.io/badge/Create-6.0.10+-f0b23c)](https://createmod.com)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

---

## What it does

Create 6 turned logistics into a real postal service: packagers create packages, chain
conveyors and frogports move them, postboxes deliver them. It looks fantastic in world,
but the moment your base grows you lose track of it.

This addon reads that network on the server and mirrors it onto Xaero's World Map:

* **Postal routes** — every chain conveyor link, drawn as a solid orange line with a dark outline with an
  elevation-aware shadow.
* **Ports** — frogports, postboxes and station mail ports as envelope icons. Hover one to
  see its address and whether it accepts packages.
* **Live packages** — every package travelling on a chain conveyor (and, optionally, loose
  package entities) as an animated dot that moves smoothly between server updates.
* **Zero worldgen, zero blocks, zero items.** Nothing is registered into the world; the mod
  is a pure viewer. It is safe to add to an existing world and safe to remove.

Everything can be switched off per player, and the whole layer can be toggled from a
button on the map itself.

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Create | 6.0.10 or newer (6.0.x) |
| Xaero's World Map | 1.40.x (optional at runtime) |

Without Xaero's World Map the mod loads and does nothing. Create is required.

## Installation

1. Install NeoForge 21.1.x for Minecraft 1.21.1.
2. Drop [Create](https://www.curseforge.com/minecraft/mc-mods/create) and
   [Xaero's World Map](https://www.curseforge.com/minecraft/mc-mods/xaeros-world-map)
   into `mods/`.
3. Drop `postalroutes-1.21.1-1.0.0.jar` into `mods/` as well.
4. Join a world, open the map (`M` by default) and look for the **Postal** button on the
   left side, right below Create's train map toggle.

Multiplayer works out of the box: install it on the server and on clients. The server
decides what to send; the client decides what to draw.

## How to read the map

| Element | Meaning |
|---|---|
| Solid orange line, dark outline | A chain conveyor link between two chain conveyors |
| Stronger line | Higher elevation (same convention as Create's train map) |
| Amber envelope | Frogport attached to a chain conveyor |
| Teal envelope | Postbox |
| Blue envelope | Frogport attached to a train station |
| Moving amber dot | Package travelling on a chain conveyor |
| Moving white dot | Loose package entity (thrown, dropped or in flight) |

Hover a port or a dot for details. The number on the **Postal** button shows how many
packages are currently moving in the dimension you are viewing.

## Configuration

`config/postalroutes-client.toml`

| Option | Default | Description |
|---|---|---|
| `enabled` | `true` | Master switch (also toggled by the map button) |
| `showRoutes` | `true` | Draw chain conveyor routes |
| `showPorts` | `true` | Draw port icons |
| `showPackages` | `true` | Draw packages on conveyors |
| `showEntityPackages` | `true` | Draw loose package entities |
| `lineTheme` | `BLUE` | `BLUE`, `AMBER` or `TEAL` |
| `routeOpacity` | `100` | Route opacity, 0-100 |
| `dashedRoutes` | `true` | Dashed style (keeps routes distinct from train tracks) |
| `maxMapSections` | `256` | 128×128 block texture cache limit (64 KiB each) |

`config/postalroutes-server.toml`

| Option | Default | Description |
|---|---|---|
| `syncIntervalTicks` | `5` | Package update rate while a map is open |
| `maxPackages` | `1024` | Packages reported per update |
| `maxEntityPackages` | `128` | Loose package entities reported per update |
| `maxNodes` | `8192` | Tracked postal blocks per dimension |
| `maxDirtyChunksPerTick` | `4` | Chunk rescans per tick after a block change |

Server command: `/postalroutes stats` (permission level 2) prints tracked blocks, routes,
ports and packages per dimension.

## Performance notes

* Nothing is collected unless somebody has the world map open.
* Chunks are indexed when they load and dropped when they unload; there is no world scan.
* Routes are rasterised into 128×128 textures only when the topology actually changes, so
  a static network costs a handful of quads per frame.
* Package updates are one small packet per `syncIntervalTicks`, hard-capped by config.
* Only loaded chunks are visible: a postal network in an unloaded area cannot be observed
  by any mod, and it will appear as soon as its chunks load.

## Compatibility

Tested against the modpack this was built for (NeoForge 21.1.228, Create 6.0.10,
Xaero's Minimap 25.3.10, Xaero's World Map 1.40.11, ~190 Create addons). See
[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

The mod does not mix into Create at all: it reads public Create API (`ChainConveyorBlockEntity`,
`PackagePortBlockEntity`) and only mixins Xaero's map screen, with a config plugin that
skips the mixin entirely when Xaero is absent.

## Building

```powershell
powershell -ExecutionPolicy Bypass -File tools/fetch-libs.ps1   # Xaero jars -> libs/
powershell -ExecutionPolicy Bypass -File tools/build.ps1        # build/libs/*.jar
powershell -ExecutionPolicy Bypass -File tools/install-to-instance.ps1
```

Gradle needs JDK 21. Create and Ponder/Catnip are pulled from
`https://maven.createmod.net/`; Xaero's jars are compile-only and never bundled.

## Technical write-up

How the overlay is built, why it lines up with the terrain, and what the limitations are:
[docs/DESIGN.md](docs/DESIGN.md). Testing checklist: [docs/TESTING.md](docs/TESTING.md). Adding a language:
[docs/TRANSLATING.md](docs/TRANSLATING.md).

## License

MIT — see [LICENSE](LICENSE). Not affiliated with Mojang, Create or Xaero.
