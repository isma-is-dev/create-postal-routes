# CurseForge upload checklist

Everything needed to publish **Create: Postal Routes**. Field values are ready to copy.

## 1. Create the project (web)

1. Go to <https://authors.curseforge.com/#/projects> → **Create Project** → *Minecraft* →
   *Mods*.
2. Fill:

| Field | Value |
|---|---|
| Name | `Create: Postal Routes` |
| Summary | `See your Create package network on Xaero's World Map: chain conveyor routes, frogports, postboxes and live package dots. No blocks, no items.` |
| Category (primary) | `Map and Information` |
| Additional categories | `Technology`, `Storage` |
| License | `MIT License` |
| Game version | `1.21.1` |
| Mod loader | `NeoForge` |
| Environment | `Client` + `Server` (both) |
| Project avatar | `docs/curseforge/avatar-400.png` |
| Source | `https://github.com/isma-is-dev/create-postal-routes` |
| Issues | `https://github.com/isma-is-dev/create-postal-routes/issues` |
| Website | `https://ismadev.es` |

3. Description: paste all of `docs/CURSEFORGE_DESCRIPTION.md` (BBCode, ready as-is).
4. Screenshots: upload `docs/curseforge/screenshots/*.png` (replace them with nicer ones
   whenever you have a good base to show).

## 2. Dependencies (Relations)

In the project → **Relations**:

| Project | Relation |
|---|---|
| `Create` (328085) | Required dependency |
| `Xaero's World Map` (317780) | Optional dependency |

## 3. Upload a file

### Option A — web

Project → **Files** → *Upload File*:

| Field | Value |
|---|---|
| File | `build/libs/postalroutes-1.21.1-<version>.jar` |
| Display name | e.g. `1.0.3` |
| Release type | `Release` |
| Game versions | `1.21.1`, `NeoForge`, `Client`, `Server` |
| Changelog | copy the matching section of `CHANGELOG.md` (Markdown) |

### Option B — API script

1. Get a token at <https://legacy.curseforge.com/account/api-tokens>.
2. Find the numeric project ID in the project URL or dashboard.
3. Run:

```powershell
powershell -ExecutionPolicy Bypass -File tools/upload-curseforge.ps1 `
  -ProjectId 1234567 `
  -Token "<api-token>"
```

The script picks the newest jar in `build/libs`, reads the changelog from
`CHANGELOG.md`, and posts metadata + file to the CurseForge upload endpoint. Tokens are
never written to disk.

## 4. After publishing

- Check the file appears under *Files* with the right game versions.
- Verify the dependency list shows Create as required.
- Add the CurseForge project URL to `README.md` and to the GitHub release notes.
- Mirror the same file on Modrinth with `docs/MODRINTH_DESCRIPTION.md`.

## Notes

- The jar does **not** bundle Create, Ponder, Flywheel or Xaero's mods; CurseForge
  moderation rejects jars that repackage other authors' work.
- `libs/*.jar` and `libs/create-*.jar` are compile-time only and git-ignored.
- Rebuild before uploading: `powershell -ExecutionPolicy Bypass -File tools/build.ps1`.
