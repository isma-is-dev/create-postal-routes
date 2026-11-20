# Compatibility

## Target environment

Verified against the "My Create" CurseForge instance this addon was written for:

| Component | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.228 |
| Create | 1.21.1-6.0.10 |
| Create More: Package Couriers | 2.2.3 / 2.3.1 |
| Create: Mobile Packages | 0.7.4 / 0.7.8 |
| Xaero's Minimap | 25.3.10 |
| Xaero's World Map | 1.40.11 |
| Mod count | ~190 (mostly Create addons) |

## Why it is safe with 190 Create addons

The mod never touches Create internals through mixins. It only reads state that Create
itself keeps public:

* `ChainConveyorBlockEntity` — connections, ports, package lists, connection stats.
* `PackagePortBlockEntity` — address filter, accept flag, target, inventory.
* `PackageItem.getAddress` and `PackageEntity` for loose parcels.

Create addons that add their own chain conveyor variants, packagers or "postal-like"
machines do not affect this: if the block is a `ChainConveyorBlockEntity` or a
`PackagePortBlockEntity` it shows up, otherwise it is ignored.

Notably safe with:

* **Create More: Package Couriers** and **Create: Mobile Packages** — they add courier
  entities and package flow on top of the same package items; loose packages appear as white
  dots, conveyor packages as amber dots.
* **Create: Central Kitchen**, **Create: Tradeworks**, **Create: Trading Floor** — they
  produce packages that flow through the network; they are reported like any other package.
* **Create Railways Navigator** (`createrailwaysnavigator`) — disabled in the target pack.
  It also draws traffic on Xaero's map, so if you enable it, expect two overlays; ours can be
  toggled off with the map button.
* **Sinytra Connector** (Fabric mods on NeoForge) — irrelevant to this mod; no Fabric code.
* **Sodium / Iris / ImmediatelyFast** — the overlay uses vanilla `GuiGraphics` calls only,
  no custom render types, so shader packs and GUI batching mods are unaffected.
* **Create Better FPS**, **ModernFix**, **FerriteCore**, **Lithium** — no shared state.

## Known interactions

| Situation | Behaviour |
|---|---|
| Xaero's World Map absent | Mod loads, does nothing. Mixin is skipped entirely. |
| Xaero's World Map version without `GuiMap#render(GuiGraphics,int,int,float)` | Overlay does not draw; no crash (`require = 0`). |
| Create older than 6.0 | Mod refuses to load (declared dependency). Create 5 has no package ports. |
| Server without the mod | Client shows nothing (no data source). No errors. |
| Very large network (> 8192 tracked blocks) | Tracked set is capped by `server.maxNodes`; raise it if needed. |
| Map zoomed far out | Port icons collapse into 2 px markers, routes stay visible. |

## Minimap

Xaero's Minimap includes and works normally; this version does not add postal markers to the
minimap HUD. That is tracked in [ROADMAP.md](ROADMAP.md).
