# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
