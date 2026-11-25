# Modrinth description (copy/paste)

Markdown, ready for the Modrinth project body.

---

# Create: Postal Routes

**See your Create postal network on Xaero's World Map.**

Create 6 turned logistics into a real postal service: packagers make packages, chain
conveyors and frogports move them, postboxes deliver them. It looks amazing in world — and
becomes impossible to follow once your base grows.

This addon mirrors the network onto Xaero's World Map, live.

## Features

- **Postal routes** — every chain conveyor link as a solid orange line with a dark outline, with elevation
  encoded in opacity so overlapping routes stay readable.
- **Ports** — frogports, postboxes and station mail ports as envelope icons. Hover for the
  address and accept state.
- **Live packages** — every package travelling on a conveyor as a smoothly moving dot.
  Loose package entities optional.
- **On-map toggle** — a Postal button with a live package counter, right below Create's
  train map button.
- **Distinct palette** — amber/copper/teal instead of Create's blue-green, so trains and mail
  are readable at the same time.

## Nothing added to your world

No blocks, no items, no worldgen, no recipes. It reads Create's public block entity data and
streams it to players who have the map open. Add and remove it freely.

## Performance

- Nothing is collected while no map is open.
- Chunk load/unload indexing, no world scans.
- Texture rebuilds only when the network changes (same 128×128 raster technique Create uses
  for its train map).
- One small packet per 10 ticks, hard-capped by config.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- Create 6.0.10+ (6.0.x) — required
- Xaero's World Map 1.40.x — optional; the mod does nothing without it

## Multiplayer

Install on both sides. Only loaded chunks can be observed; networks in unloaded areas appear
when they load.

## Config

Client: toggles per layer, colour theme, opacity, dash style, texture cache size.
Server: sync interval and caps. Command: `/postalroutes stats`.

## License

MIT. Not affiliated with Mojang, Create or Xaero.
