# Design notes

This document records the research behind the addon: what Create and Xaero actually
expose, how the overlay stays aligned with the terrain, and where the hard limits are.
It is written for future maintainers (including future me).

Everything below was verified against the exact jars used by the target modpack:

| Mod | File | Notes |
|---|---|---|
| Create | `create-1.21.1-6.0.10.jar` | `Implementation-Version: 6.0.10` |
| Xaero's World Map | `xaeroworldmap-neoforge-1.21.1-1.40.11.jar` | nests `xaerolib-neoforge-1.21.1-1.1.0.jar` |
| Xaero's Minimap | `xaerominimap-neoforge-1.21.1-25.3.10.jar` | not used yet |
| NeoForge | `21.1.228` | |
| Minecraft | `1.21.1` | |

## 1. What Create exposes

Create 6's package logistics are ordinary block entities with public state, no API layer
required:

```
com.simibubi.create.content.kinetics.chainConveyor
├── ChainConveyorBlockEntity
│     public Set<BlockPos> connections            // relative offsets to peer conveyors
│     public Map<BlockPos, ConnectionStats> connectionStats
│     public Map<BlockPos, ConnectedPort> loopPorts
│     public Map<BlockPos, ConnectedPort> travelPorts
│     List<ChainConveyorPackage> loopingPackages
│     Map<BlockPos, List<ChainConveyorPackage>> travellingPackages
│     public void prepareStats()
│     public Vec3 getPackagePosition(float chainPosition, @Nullable BlockPos travelTarget)
│     record ConnectionStats(float tangentAngle, float chainLength, Vec3 start, Vec3 end)
└── ChainConveyorPackage
      public int netId; public float chainPosition; public ItemStack item;
      public @Nullable Vec3 worldPosition;

com.simibubi.create.content.logistics.packagePort
├── PackagePortBlockEntity  (abstract)
│     public String addressFilter; public boolean acceptsPackages;
│     public PackagePortTarget target; public SmartInventory inventory;
├── PackagePortTarget
│     ChainConveyorFrogportTarget   // chainPos, connection, flipped
│     TrainStationFrogportTarget    // relative position of the station
└── frogport / postbox implementations

com.simibubi.create.content.logistics.box
├── PackageItem.getAddress(ItemStack)
└── PackageEntity (loose packages in flight)
```

Key findings that shaped the implementation:

1. **Chain geometry must come from Create.** A chain conveyor link is not a straight line
   between block centres: `calculateConnectionStats` offsets both ends by 1.25 blocks along
   the tangent angle (`direction ± 35°`, flipped by speed sign). Re-implementing that on the
   client would drift the moment Create tweaks it, so the server sends the resolved
   `start`/`end` of each strand.
2. **Both directions are drawn.** Each conveyor stores its own view of a shared link, so
   `A → B` and `B → A` produce the two parallel strands of the chain loop. Drawing both is
   both cheaper and more faithful than inventing a single centre line.
3. **Ports are not located at their block.** A chain frogport sits next to the conveyor and
   its real position is `chainPosition` along that chain. `PackagePortTarget#getExactTargetLocation`
   returns the correct world point, so icons sit on the route instead of one block off.
4. **Packages on conveyors are not entities.** They live in the block entity's lists and are
   synced through block entity update packets. Server-side extraction is the only way to see
   the whole network, because the client only knows loaded chunks.
5. **Station frogports** route through trains, not chains. Their target is the station block;
   the mod marks them with a distinct icon and does not draw a route for them, because the
   train itself is already on Create's train map.

## 2. How Create draws trains on Xaero's map (the reference)

Create ships its own integration in `com.simibubi.create.compat.trainmap`:

* `XaeroTrainMap` — an accessor mixin reads `GuiMap.cameraX/cameraZ/scale/mapProcessor`, and
  an injector runs before `GuiGraphics.blit(ResourceLocation,IIIIII)` inside `GuiMap.render`.
* `TrainMapRenderer` — rasterises tracks into 128×128 `DynamicTexture`s, one per 128-block
  section, culled against the visible rectangle.
* `TrainMapSync` / `TrainMapSyncPacket` — a demand-driven server → client feed, with a light
  packet every 5 ticks and a full packet every 10.

This addon deliberately reuses that architecture, because it is proven against the exact
Xaero version in the pack. Two details are worth copying verbatim:

**Camera transform** (`XaeroTrainMap.onRender`):

```java
double guiScale = window.getScreenWidth() / window.getGuiScaledWidth();
double interfaceScale = window.getWidth() / window.getScreenWidth();
double scale = mapScale / guiScale / interfaceScale;

pose.translate(screen.width / 2f, screen.height / 2f, 0);
pose.scale(scale, scale, 1);
pose.translate(-cameraX, -cameraZ, 0);
```

After that, **one unit is one block**, which is what makes a 128×128 texture line up with a
128×128 block area.

**Injection point**: the *first* `GuiGraphics.blit` call in `GuiMap.render` is the last
icon the map draws, so injecting before it places the overlay above map tiles and elements
but below the map's own UI. Injecting at `TAIL` would draw over the buttons; injecting at
`HEAD` would be hidden by the map.

## 3. Coordinate scaling (the part Create's version skips)

Xaero does not draw raw world coordinates. In `GuiMap.render`:

```java
playerDimDiv = mapDimension.calculateDimDiv(dimensionTypes, player.level().dimensionType());
scaledPlayerX = player.getX() / playerDimDiv;
```

`calculateDimDiv` is `viewedDimension.coordinateScale() / playerDimension.coordinateScale()`.
In the Overworld the divisor is 1, so raw coordinates work — which is why a train overlay can
get away with ignoring it. In the Nether the divisor is 8, and any overlay using raw
coordinates would be eight times too far from the map origin.

This mod calls Xaero's own `calculateDimDiv` with the same arguments, so routes and dots are
correct in every dimension, including custom dimensions with a non-default coordinate scale.

## 4. Data flow

```
                 server thread                          client thread
┌─────────────────────────────────────┐      ┌──────────────────────────────┐
│ ChunkEvent.Load/Unload              │      │ PostalClientState            │
│ BlockEvent.Place/Break              │      │  ├─ topology (edges, ports)  │
│        │                            │      │  ├─ dots + interpolation     │
│        ▼                            │      │  └─ revision counter         │
│ PostalTracker (per dimension)       │      └──────────────┬───────────────┘
│  ├─ positions of loaded postal BEs  │                     │
│  ├─ snapshot(): edges/ports/packages│   S2CTopologyPart    │
│  └─ topology hash (order agnostic)  │ ───────────────────▶ │
│                                     │   S2CPackageDots     │
│ PostalServerEvents                  │ ───────────────────▶ ▼
│  ├─ clients with map open (5 s TTL) │      PostalTextureCache  (raster)
│  ├─ full topology on hash change    │      PostalMapOverlay    (draw+picking)
│  └─ dots every N ticks              │
└─────────────────────────────────────┘
```

### Why a topology hash instead of "send everything every tick"

The tracked set is a `HashSet`, so iteration order changes between ticks. The hash is built
with XOR over per-element hashes, which is order independent. The client only rebuilds its
textures when the hash, the dimension divisor or the theme changes, so an idle network costs
zero CPU per frame beyond drawing cached quads.

### Packet sizes

* Topology is split into parts of at most 192 entries. A typical base (a few hundred links)
  fits in one or two packets of a few KiB.
* Package positions are quantised to 1/8 block integers (12 bytes per package plus a short
  address string). 1024 packages stay well below the vanilla payload limit.

### Elevation

Like Create's train map, route opacity encodes height above the build limit. Two routes
crossing at different Y levels remain distinguishable without colour tricks, and the effect
survives the dashed style.

## 5. Optimisation decisions

| Concern | Decision |
|---|---|
| Not scanning the world | Positions are indexed from `ChunkEvent` and invalidated on block changes; `Level#getBlockEntity` is only called for loaded chunks (guarded by `isLoaded`, which avoids a synchronous chunk load). |
| Stale positions | A position whose block entity disappeared while its chunk is loaded is dropped during the next snapshot. |
| Chunk rescans | A placement/break marks one chunk dirty; at most `maxDirtyChunksPerTick` chunks are rescanned per tick. |
| Idle servers | Sync stops entirely after 5 seconds without a client request; `SENT` bookkeeping is cleared. |
| VRAM | 128×128 RGBA per section (64 KiB). Untouched sections are released on rebuild, and `maxMapSections` caps the cache. |
| Frame cost | Textures are rebuilt only on topology change; drawing is one quad per visible section plus a few dozen `fill` calls for icons and dots. |
| Bandwidth | Dots are only sent while a map is open, at `syncIntervalTicks`, capped by `maxPackages`. |

## 6. Failure modes

The mod is designed to degrade, never to crash:

* The mixin uses `require = 0`: if Xaero moves that `blit` call, the overlay simply does not
  appear.
* `PostalMixinPlugin` skips the mixin entirely when Xaero's World Map is not installed, so
  Mixin never has to resolve a missing target class.
* The overlay catches `Throwable` and disables itself for the session with a single log line.
* Server-side extraction wraps Create internals in `try/catch`: a single broken block entity
  cannot break the sync loop or the server tick.
* Config values are read defensively so a partially loaded config cannot throw during render.

## 7. Known limitations

1. **Loaded chunks only.** Block entity state exists only in loaded chunks. A postal network
   outside simulation distance is invisible until it loads. This is a Minecraft constraint,
   not a mod decision; a future version could persist a discovered topology to disk.
2. **Train-carried packages are not shown as dots.** They are inside a train; the train
   marker (Create's train map) already shows where they are. Station ports get their own icon.
3. **Map elements API not used.** Xaero exposes `MapElementRenderHandler` with
   `ElementRenderProvider`/`ElementRenderer`, which is designed for discrete, pickable
   elements (waypoints, players). A scanned route layer needs the camera transform anyway,
   so the mixin + raster approach is both simpler and cheaper.
4. **Xaero internals are private.** Field names `cameraX`, `cameraZ`, `scale`, `mapProcessor`
   are read through an accessor mixin. They are stable across Xaero 1.40.x but not a public
   API; the `require = 0` + try/catch design keeps a future break cosmetic.
5. **Minimap support** is not in 1.0.0. Xaero's minimap has a separate HUD element pipeline;
   it is on the roadmap.

## 8. Alternatives considered

| Option | Why not |
|---|---|
| Register custom blocks/items | Unnecessary; the mod is a viewer and should be trivially removable. |
| Render in world space with `RenderLevelStageEvent` | Would only show the local area, not a whole map. |
| Draw one quad per edge with `fill` | Thousands of quads per frame, no dashed pattern, no elevation alpha, and aliasing at low zoom. |
| Store raster in a `BufferedImage` and upload every frame | Wasted bandwidth; rebuilding only on topology change is strictly better. |
| Use `ScreenEvent.Render.Post` instead of a mixin | The overlay would draw over Xaero's buttons and side panels. |
