# Web Target Investigation — Issue #6

## Context

The `core/` module is pure Kotlin stdlib (no `java.*` imports) with `kotlinx.serialization` and `kotlinx.coroutines`. This was designed to enable KMP extraction. This document evaluates options for running the simulation in a browser.

## KMP Readiness Assessment

**Overall: 93% ready.** One blocker exists.

| Criterion | Status | Notes |
|-----------|--------|-------|
| Dependencies | Ready | kotlinx-serialization + coroutines are multiplatform |
| java.* imports | Clean | Zero violations across 19 source files |
| JVM-specific APIs | **1 issue** | `runBlocking()` in TickEngine.kt is JVM-only |
| expect/actual needed | Minimal | Only for TickEngine parallel decide phase |

**Required fix:** Extract `runBlocking(parallelContext) { ... }` in `TickEngine.step()` into an `expect/actual` function. JVM uses `runBlocking` + `async`; JS/Wasm uses sequential fallback (single-threaded).

## Option Comparison

### 1. Kotlin/Wasm + Compose Multiplatform

**Maturity:** Beta (Sep 2025). JetBrains ships Kotlin Playground and KotlinConf on it.

**Rendering:** Compose renders via HTML Canvas (`CanvasBasedWindow`). No direct WebGL — would need raw interop or the third-party [Korender](https://github.com/zakgof/korender) engine (Beta, v0.6.1, supports WebGL 2 + PBR + model loading).

**Performance:** ~3x faster than Kotlin/JS. Near-JVM for compute.

**Browser support:** All modern browsers support WasmGC. Compose includes a compatibility mode with Kotlin/JS fallback.

| Pro | Con |
|-----|-----|
| Best compute performance | Requires rewriting entire rendering layer |
| core/ ports cleanly | Korender is Beta — not libGDX API-compatible |
| Future-proof KMP path | Multi-threaded Wasm not yet available |
| Static hosting (no server) | Higher implementation effort |

### 2. Kotlin/JS

**Maturity:** Stable. IR compiler is the only backend.

**Rendering:** Full access to Canvas 2D, WebGL, and JS libraries. Three.js wrappers exist (community-maintained, lag behind releases).

**Performance:** ~3x slower than Wasm for compute-heavy work. Meaningful for a tick engine with hundreds of Peeps.

| Pro | Con |
|-----|-----|
| Stable, well-documented | Slowest option for simulation compute |
| Rich JS ecosystem access | Three.js wrappers are community-maintained |
| core/ ports cleanly | Still requires full rendering rewrite |
| Static hosting | 500KB–2MB+ bundle size |

### 3. WebSocket Bridge (Ktor Server + Browser Client)

**Architecture:** JVM runs full simulation. Browser receives serialized world state over WebSocket and renders with Three.js/Babylon.js.

**Latency:** WebSocket round-trip: 1–5ms LAN, 20–100ms internet. For a simulation viewer (not twitch gameplay), this is well within tolerance.

| Pro | Con |
|-----|-----|
| **Zero porting of core/** | Requires running server |
| Keeps libGDX desktop intact | No offline/standalone web play |
| Any web rendering stack | Network complexity (reconnect, resync) |
| Multiple viewers can watch | Deployment is heavier than static |
| Fastest path to working web | |

### 4. libGDX Web Backend (GWT / TeaVM)

**GWT:** **Eliminated.** Transpiles Java source, not bytecode. Kotlin is incompatible — hard blocker.

**TeaVM ([gdx-teavm](https://github.com/xpenatan/gdx-teavm)):** Compiles JVM bytecode (including Kotlin) to JS/Wasm. Actively maintained (v1.5.3, March 2026). Supports libGDX API including WebGL. **However:** No published 3D demo confirming ModelBatch/PerspectiveCamera works. Sparse documentation.

| Pro | Con |
|-----|-----|
| Highest fidelity to existing code | GWT blocked for Kotlin |
| TeaVM handles Kotlin bytecode | Sparse docs, small community (161 stars) |
| Self-contained web build | 3D rendering unconfirmed |
| Static hosting | Setup complexity higher than other options |

## Summary Matrix

| Criterion | Kotlin/Wasm | Kotlin/JS | WebSocket | gdx-teavm |
|---|---|---|---|---|
| Maturity | Beta | Stable | Stable | Active, sparse docs |
| core/ portability | Excellent | Excellent | None needed | Good |
| 3D rendering | Korender (Beta) | Three.js (community) | Three.js/Babylon | libGDX WebGL (unconfirmed 3D) |
| Keeps libGDX code | No | No | Yes (server-side) | Partial |
| Browser support | Modern (WasmGC) | All | All | Modern |
| Performance | Near-JVM | ~3x slower | JVM + WS latency | Transpiled, good |
| Implementation effort | High | High | Medium | Medium-High |
| Offline capable | Yes | Yes | No | Yes |

## Recommendation

**Prototype: WebSocket Bridge with Ktor.**

Rationale:
1. **Lowest risk** — zero changes to `core/` or simulation logic
2. **Fastest to working prototype** — add Ktor WebSocket server, build a Three.js viewer
3. **Desktop and web share identical simulation** — no behavior divergence
4. **Decoupled rendering** — web client can evolve independently (2D canvas MVP → Three.js 3D later)

**Long-term path:** If standalone/offline web is needed, evaluate gdx-teavm (confirm 3D support first) or Kotlin/Wasm + Korender once both reach stable.

## Required core/ Changes

Regardless of approach chosen, one change is needed for KMP readiness:

**TickEngine.kt** — Replace `runBlocking` with expect/actual:

```kotlin
// commonMain
expect fun <T> runParallel(items: Collection<T>, transform: suspend (T) -> T): List<T>

// jvmMain
actual fun <T> runParallel(items: Collection<T>, transform: suspend (T) -> T): List<T> =
    runBlocking(Dispatchers.Default) {
        items.map { async { transform(it) } }.awaitAll()
    }

// jsMain / wasmMain
actual fun <T> runParallel(items: Collection<T>, transform: suspend (T) -> T): List<T> =
    runBlocking { items.map { transform(it) } }  // sequential, single-threaded
```

This change is backwards-compatible and can be done independently of the web target choice.
