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
`Brain` is swappable per-Peep. Implementations: `IdleBrain`, `RandomBrain`, `UtilityBrain` (priority-based), `PyramidBrain` (strict Maslow hierarchy), `WaveBrain` (weighted overlapping waves with top-3 random selection).

## GitHub Workflow

- All planned work is tracked as GitHub issues in `ecopoesis/gameofcity`
- When asked to work on an issue, assign it to `ecopoesis` before starting
- When work is done and pushed to `main`, close the issue
- **Do not open PRs** unless explicitly asked — commit directly to `main`
- New work identified during implementation should be filed as a new issue, not done silently
- When planning together, always create GitHub issues for each step of the plan before starting implementation
- Web UI and desktop UI must stay in parity — any control, inspector feature, or keyboard shortcut added to one must be added to the other

## Roadmap (GitHub Issues)

| Phase | Issue | Status |
|---|---|---|
| 1 — Foundation | — | ✅ Done |
| 2 — Peeps + Tick Engine | #1 | ✅ Done |
| 3 — Inspector UI + Economy | #2 | ✅ Done |
| 4 — 3D Camera + Relationships | #3 | ✅ Done |
| 5 — Procedural Generation + Polish | #4 | ✅ Done |
| Maslow Needs | #13 | ✅ Done |
| Building Subtypes + Actions | #14 | ✅ Done |
| PyramidBrain | #15 | ✅ Done |
| WaveBrain | #16 | ✅ Done |
| Brain Type + Renderers | #17 | ✅ Done |
| City Generation Improvements | #18 | ✅ Done |
| Day/Night Cycle + Schedules | #19 | ✅ Done |
| Building Capacity + Queuing | #20 | ✅ Done |
| Labor Market + Wages | #21 | ✅ Done |
| Housing Market + Homelessness | #22 | ✅ Done |
| Relationships + Households | #23 | ✅ Done |
| Life Events + Demographics | #24 | ✅ Done |
| Emergent Events + Stats Dashboard | #26 | ✅ Done |
| Environment + City Services | #27 | ✅ Done |
| Scale + Road Classification | #28 | ✅ Done |
| Multi-Lane Roads + Blocks | #29 | ✅ Done |
| Pathfinding Upgrade | #30 | ✅ Done |
| Vehicle Model | #31 | ✅ Done |
| Parking Infrastructure | #32 | |
| Brains Learn to Drive | #33 | |
| Vehicle + Road Rendering | #34 | |
| Bus Transit System | #35 | |
| Train + Subway System | #36 | |
| Transport Economics | #37 | |

## Key Files

| File | Purpose |
|---|---|
| `core/src/main/kotlin/world/WorldMap.kt` | City grid + spatial index |
| `core/src/main/kotlin/world/Building.kt` | Building data model + `BuildingType`/`BuildingSubtype` enums |
| `core/src/main/kotlin/peep/Peep.kt` | Citizen data model |
| `core/src/main/kotlin/peep/Needs.kt` | `MaslowNeeds` (5 Maslow levels) + `NeedType` enum |
| `core/src/main/kotlin/peep/Brain.kt` | Strategy interface + `WorldView` + `RandomBrain`/`IdleBrain` |
| `core/src/main/kotlin/peep/UtilityBrain.kt` | Original goal-driven brain |
| `core/src/main/kotlin/peep/PyramidBrain.kt` | Strict Maslow hierarchy brain |
| `core/src/main/kotlin/peep/WaveBrain.kt` | Kenrick's overlapping waves brain |
| `core/src/main/kotlin/peep/NeedActionMapper.kt` | Maps NeedType → BuildingSubtype → Action |
| `core/src/main/kotlin/peep/NavigationHelper.kt` | Shared pathfinding queue for brains |
| `core/src/main/kotlin/peep/Schedule.kt` | Schedule types + templates (Worker/Student/Retiree/Nightshift) |
| `core/src/main/kotlin/peep/Demographics.kt` | Mortality, birth, immigration/emigration logic |
| `core/src/main/kotlin/peep/Household.kt` | Household model + `RelationshipTier` enum |
| `core/src/main/kotlin/tick/TickEngine.kt` | 5-phase simulation pipeline |
| `core/src/main/kotlin/tick/SimClock.kt` | Centralized simulation clock (hour/minute/day) |
| `core/src/main/kotlin/tick/EventLog.kt` | Ring-buffered event log (evictions, births, deaths, etc.) |
| `core/src/main/kotlin/tick/CityStats.kt` | Aggregate city statistics (employment, happiness, Gini) |
| `core/src/main/kotlin/world/Weather.kt` | Weather state machine (Clear/Rain/Snow/Heatwave) |
| `core/src/main/kotlin/world/Vehicle.kt` | VehicleType enum (Car, Bike) |
| `core/src/main/kotlin/pathfind/BinaryHeap.kt` | Pure Kotlin min-heap priority queue |
| `core/src/main/kotlin/pathfind/AStarPathfinder.kt` | Multi-modal A* pathfinding with variable movement costs |
| `core/src/main/kotlin/gen/CityGenerator.kt` | Procedural city gen (grid + organic roads) |
| `desktop/src/main/kotlin/GameOfCityApp.kt` | Main game loop, camera pan |
| `desktop/src/main/kotlin/rendering/CityRenderer.kt` | 3D city render with subtype colors |
| `desktop/src/main/kotlin/world/MapLoader.kt` | JSON → WorldMap (libGDX JsonReader) |
| `assets/maps/starter.json` | 20×20 hand-authored starter city |
