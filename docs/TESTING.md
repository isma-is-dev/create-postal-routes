# Testing the overlay

A quick checklist for verifying a build, in the order that has caught real issues.

## 1. It loads

Launch the client and look for these lines in `logs/latest.log`:

```
Create: Postal Routes 1.0.0 (postalroutes)
```

If the mod is missing from the mod list, the jar is in the wrong folder or Create is not
installed (Create is a hard dependency).

## 2. The map hook is wired

Open Xaero's World Map. On the first render you should see:

```
Postal Routes: Xaero world map hook is active
```

* No line at all? The mixin config is not loaded. Check that
  `META-INF/neoforge.mods.toml` contains a `[[mixins]]` block pointing at
  `postalroutes.mixins.json`. This is the most common packaging mistake.
* `Postal Routes could not render on Xaero's World Map` in the log? The stack trace says
  exactly what broke; the overlay disables itself for that session instead of crashing.

## 3. The widget appears

On the left of the map, below Create's train map toggle, there is a **Postal** button.
Clicking it toggles the layer. The number on the right is the amount of packages currently
moving in the dimension you are viewing.

If the widget is missing but the hook logged, the overlay returned early: either the
overlay is disabled in `config/postalroutes-client.toml`, or the map dimension has no data.

## 4. The data arrives

Build a minimal network in creative:

```
/give @p create:chain_conveyor 2
/give @p create:package_frogport 2
```

1. Place two chain conveyors 8-16 blocks apart and link them with the chain conveyor item
   (right-click the second one while holding it).
2. Place a frogport on each and give them addresses (`Storage` and `Home`).
3. Power the conveyors so packages can move.
4. Run `/postalroutes stats` (permission level 2). It prints tracked blocks, routes, ports
   and packages per dimension. If those numbers are zero, the server side did not index the
   network: run `/postalroutes rescan` and check again.

A quick way to see movement without a full logistics line: put a package into a frogport
that is linked to the other one. The dot count on the Postal button should go up.

## 5. Alignment

Routes are rasterised in world blocks (1 texture pixel = 1 block) and drawn with Xaero's own
camera transform, so a port icon must sit exactly on the block that holds the frogport.
If icons are offset by a constant factor (8x in the Nether, for example) the dimension
divisor is wrong — check `calculateDimDiv` usage in `PostalMapOverlay`.

## Multiplayer

* Client without the mod, server with it: nothing to see, no errors.
* Server without the mod: the client logs one informational line and stops asking.
* Both sides: only chunks loaded on the server are indexed; `/postalroutes rescan` is a
  manual re-index for testing.

## What cannot be tested headlessly

The overlay itself (mixins, textures, tooltips) needs a real client. The server side can be
exercised with RCON and `/postalroutes stats`, which is how the 1.0.0 release was verified
against Create 6.0.10.
