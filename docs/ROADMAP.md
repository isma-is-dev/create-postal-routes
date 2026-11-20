# Roadmap

## 1.1 — Minimap

* Draw postal routes and package dots on Xaero's Minimap HUD.
  Xaero's minimap uses a separate `MinimapElementRenderer` pipeline
  (`xaero.hud.minimap.element.render`), registered against `MinimapProcessor`'s session.
  The data feed is already dimension-aware, so this is a rendering task only.
* Mirror the toggle state between world map and minimap.

## 1.2 — Network awareness

* Show the **destination** of a package when it can be resolved (address → port lookup),
  optionally as a faint line between the package and the destination port.
* Highlight a whole delivery chain when hovering a port: "3 packages heading here".
* Optional route colouring by address hash, so multi-network bases are readable at a glance.

## 1.3 — Persistence

* Cache discovered topology per dimension on the server so a network stays visible while
  its chunks are unloaded (with a "stale" style until re-verified).
* `/postalroutes debug` dump for bug reports.

## Ideas not scheduled

* A postal "lens" item / keybind to open a fullscreen network view without Xaero.
* Statistics graphs (packages per minute per port) in a side panel.
* Integrations with other map mods (JourneyMap/Xaero alternatives).
