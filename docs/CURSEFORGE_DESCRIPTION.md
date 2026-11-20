# CurseForge description (copy/paste)

BBCode, ready for the CurseForge project description editor.

---

[b]See your Create postal network on Xaero's World Map.[/b]

Create 6 turned logistics into a real postal service: packagers make packages, chain conveyors and frogports move them, postboxes deliver them. It looks amazing in world — and becomes impossible to follow once your base grows.

[b]Create: Postal Routes[/b] mirrors that network onto Xaero's World Map, live:

[list]
[*][b]Postal routes[/b] — every chain conveyor link as a solid blue line with a dark outline, with elevation encoded in opacity so crossings stay readable.
[*][b]Ports[/b] — frogports, postboxes and station mail ports as envelope icons. Hover one to see its address and whether it accepts packages.
[*][b]Live packages[/b] — every package travelling on a conveyor as a smooth moving dot. Optionally loose package entities too.
[*][b]On-map toggle[/b] — a Postal button with a live package counter, right below Create's train map button.
[/list]

Colours are purposefully different from Create's train map (blue-green), so you can read trains and mail at the same time.

[h2]No blocks. No items. No worldgen.[/h2]

This is a pure viewer: it reads Create's public block entity data on the server and streams it to clients that have the map open. Add it to an existing world, remove it whenever — nothing is registered into your save.

[h2]Performance first[/h2]

[list]
[*]Nothing is collected while no one has the world map open.
[*]Chunks are indexed when they load and dropped when they unload — there is no world scan.
[*]Routes are rasterised only when the network actually changes (one 128x128 texture per 128x128 block area, exactly how Create draws its train map).
[*]Package updates are one small packet every 10 ticks, capped by config.
[/list]

[h2]Requirements[/h2]

[list]
[*]Minecraft 1.21.1
[*]NeoForge 21.1.x
[*]Create 6.0.10+ (6.0.x)
[*]Xaero's World Map 1.40.x (optional — without it the mod simply does nothing)
[/list]

[h2]Multiplayer[/h2]

Install on the server and on clients. The server decides what is sent, the client decides what is drawn. Only chunks that are loaded on the server can be observed — a postal network in an unloaded area appears as soon as it loads.

[h2]Configuration[/h2]

[list]
[*]Client: toggle routes / ports / packages, colour theme (amber, copper, teal), opacity, dashed style, texture cache size.
[*]Server: sync interval, caps for packages, tracked blocks and chunk rescans.
[*]Command: /postalroutes stats
[/list]

[h2]Credits[/h2]

Made by isma-is-dev. Not affiliated with Mojang, Create or Xaero. The overlay follows the same rendering approach Create uses for its own train map, which is the cleanest way to align custom data with Xaero's camera.
