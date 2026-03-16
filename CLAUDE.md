# Game of City — Claude Instructions

## Project Overview

Conway's-Game-of-Life-inspired city simulation. Citizens called **Peeps** are autonomous agents driven by needs, wants, relationships, and money. Goal: simplest rule set that produces realistic emergent urban behavior.

## Build & Run

```bash
./gradlew :desktop:run        # launch the game window
./gradlew :core:test          # run unit tests (headless)
./gradlew :core:compileKotlin # compile core only
./gradlew :desktop:compileKotlin # compile desktop only
```

## Module Rules

### core/
- **Pure Kotlin stdlib only** — no `java.*` imports, no libGDX imports
- This constraint exists so `core` can be extracted to KMP `commonMain` for web/mobile later
- Source root: `core/src/main/kotlin/` with packages `world`, `peep`, `tick`, `pathfind`

### desktop/
- libGDX lives here. All rendering, input, file I/O, and platform glue
- `MapLoader` uses libGDX `JsonReader().parse(text)` — NOT `Json().fromJson(JsonValue::class.java, ...)` (that fails: JsonValue has no no-arg constructor)
- Working directory for the run task is `assets/` (set in `desktop/build.gradle.kts`)

## macOS / JVM Gotchas

- libGDX on macOS requires `-XstartOnFirstThread` — already set in `desktop/build.gradle.kts` run task
- JDK 24 + LWJGL prints deprecation warnings about `sun.misc.Unsafe` — harmless, ignore them
- `[LWJGL] Unsupported JNI version` warning is also harmless

## Architecture

### Tick Engine (5 phases, in order)
1. Perceive — Peeps read immutable `WorldView`
2. Decide — `Brain.decide()` returns an `Action`
3. Validate — conflict detection
4. Execute — mutations applied to world state
5. Maintain — need decay, clock advance

### WorldMap storage
- Ground layer (z=0): dense `Array<Array<Cell>>` — always allocated
- Other z-layers: sparse `HashMap<CellCoord, Cell>`
- Spatial index: `peepsAt: HashMap<CellCoord, MutableList<PeepId>>`
- Building index: `buildings: HashMap<BuildingId, Building>`

### Brain interface
`Brain` is swappable per-Peep. Current implementations: `IdleBrain`, `RandomBrain`. `UtilityBrain` (Phase 2) scores actions by need urgency.

## GitHub Workflow

- All planned work is tracked as GitHub issues in `ecopoesis/gameofcity`
- When asked to work on an issue, assign it to `ecopoesis` before starting
- When work is done and pushed to `main`, close the issue
- **Do not open PRs** unless explicitly asked — commit directly to `main`
- New work identified during implementation should be filed as a new issue, not done silently
- When planning together, always create GitHub issues for each step of the plan before starting implementation

## Roadmap (GitHub Issues)

| Phase | Issue | Status |
|---|---|---|
| 1 — Foundation | — | ✅ Done |
| 2 — Peeps + Tick Engine | #1 | Open |
| 3 — Inspector UI + Economy | #2 | Open |
| 4 — 3D Camera + Relationships | #3 | Open |
| 5 — Procedural Generation + Polish | #4 | Open |

## Key Files

| File | Purpose |
|---|---|
| `core/src/main/kotlin/world/WorldMap.kt` | City grid + spatial index |
| `core/src/main/kotlin/world/Building.kt` | Building data model + `BuildingType` enum |
| `core/src/main/kotlin/peep/Peep.kt` | Citizen data model |
| `core/src/main/kotlin/peep/Brain.kt` | Strategy interface + `WorldView` + `RandomBrain`/`IdleBrain` |
| `core/src/main/kotlin/tick/TickEngine.kt` | 5-phase simulation pipeline |
| `core/src/main/kotlin/pathfind/AStarPathfinder.kt` | Grid pathfinding |
| `desktop/src/main/kotlin/GameOfCityApp.kt` | Main game loop, camera pan |
| `desktop/src/main/kotlin/rendering/CityRenderer.kt` | ShapeRenderer 2D city draw |
| `desktop/src/main/kotlin/world/MapLoader.kt` | JSON → WorldMap (libGDX JsonReader) |
| `assets/maps/starter.json` | 20×20 hand-authored starter city |
