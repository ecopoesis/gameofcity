# Game of City

A Conway's-Game-of-Life-inspired city simulation where **Peeps** (citizens) are fully autonomous agents driven by needs, wants, relationships, and money. The design thesis: the simplest possible rule set that produces realistic emergent urban behavior.

## Concept

Each Peep has a set of needs (hunger, fatigue, shelter, social, entertainment) and a swappable AI brain that scores actions by urgency. Peeps walk to work, eat when hungry, sleep when tired, and form friendships through proximity. No scripted schedules — all behavior emerges from the rules.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.x (JVM) |
| Build | Gradle (Kotlin DSL) |
| Rendering | libGDX 1.12+ |
| 2D rendering | libGDX ShapeRenderer |
| 3D rendering (Phase 4) | libGDX ModelBatch + PerspectiveCamera |
| UI/HUD | libGDX Scene2D |
| Testing | JUnit 5 + Kotest |

## Architecture

```
gameofcity/
├── core/          # Pure Kotlin, no libGDX — KMP-extractable later
│   ├── world/     # City, Cell, Zone, Building
│   ├── peep/      # Peep, Brain (strategy), Needs, Action
│   ├── tick/      # TickEngine (5-phase pipeline)
│   └── pathfind/  # A* pathfinder on the grid
├── desktop/       # libGDX launcher + platform glue
│   ├── rendering/ # CityRenderer (2D → 3D swap in Phase 4)
│   └── world/     # MapLoader (JSON → WorldMap)
└── assets/
    └── maps/      # Hand-authored city maps (JSON)
```

The `core` module is pure Kotlin stdlib — no `java.*` imports — so it can be extracted to KMP `commonMain` for web deployment later.

## Tick Engine

Every simulation tick runs 5 phases in order:

1. **Perceive** — each Peep reads an immutable `WorldView` snapshot
2. **Decide** — each Peep's `Brain` produces an `Action`
3. **Validate** — detect conflicts (two Peeps same cell, etc.)
4. **Execute** — apply all mutations to world state
5. **Maintain** — decay needs, advance clock, trigger events

## Running

```bash
./gradlew :desktop:run
```

Arrow keys pan the camera.

## Testing

```bash
./gradlew :core:test
```

## Roadmap

| Phase | Status | Description |
|---|---|---|
| 1 | ✅ Done | Kotlin/libGDX foundation, grid, 2D renderer |
| 2 | [#1](https://github.com/ecopoesis/gameofcity/issues/1) | Peeps + Tick Engine — walking, needs, pathfinding |
| 3 | [#2](https://github.com/ecopoesis/gameofcity/issues/2) | Inspector UI + Economy |
| 4 | [#3](https://github.com/ecopoesis/gameofcity/issues/3) | 3D Camera + Relationships |
| 5 | [#4](https://github.com/ecopoesis/gameofcity/issues/4) | Procedural Generation + Polish |
