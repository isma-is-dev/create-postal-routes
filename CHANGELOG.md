# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.2] - 2026-11-20

### Changed

- Port icons shrank again to a 3x2 glyph (1 px translucent frame included), as requested:
  a dense network now reads as small markers instead of covering the base. The map button
  keeps drawing the same glyph at double scale so it stays legible in the UI.
## [1.0.1] - 2026-11-20

### Changed

- Routes are now a solid, fully opaque blue with a translucent dark outline, so they read
  clearly over any terrain. Default opacity is 100 and the default theme is `BLUE`
  (`AMBER` and `TEAL` remain available).
- Port icons shrank to a 5x4 glyph with a thin translucent frame; the previous 9x7 cards
  covered too much of the map.
- Station mail ports use a near-white icon so they cannot be confused with blue routes.
- Packages are interpolated over the measured time between updates instead of the
  configured interval, which removes the "smooth, freeze, jump" stutter caused by tick and
  network jitter. The server also streams positions every 5 ticks by default.

### Fixed

- The overlay could compute a NaN scale (and draw nothing) when the window reported a
  zero-sized framebuffer; the transform now derives from `Window#getGuiScale`.
## [1.0.0] - 2026-11-20

### Added

- Postal overlay for Xaero's World Map:
  - Chain conveyor routes rasterised into 128×128 block-aligned textures, with dashed
    style and elevation-aware opacity.
  - Frogport, postbox and station mail port icons at the exact chain position.
  - Live package dots with client-side interpolation between server updates.
  - Optional loose package entity markers.
  - Hover tooltips with address and accepting state.
  - On-map **Postal** toggle button with a live package counter.
- Server-side postal index driven by chunk load/unload plus block change rescans.
- Demand-driven sync: nothing is collected while no map is open.
- Chunked topology packets so very large networks stay under the payload limit.
- Client and server configuration, plus `/postalroutes stats`.
- English and Spanish translations.
