package pathfind

import world.CellCoord
import world.TravelMode
import world.WorldMap
import world.Terrain
import world.isRoad
import kotlin.math.abs

class AStarPathfinder(private val map: WorldMap) {

    private data class Node(val coord: CellCoord, val g: Float, val h: Float, val parent: Node?) {
        val f: Float get() = g + h
    }

    /** Find path using legacy passability (any non-empty terrain). */
    fun findPath(from: CellCoord, to: CellCoord): List<CellCoord> =
        findPath(from, to, null)

    /** Find path for a specific travel mode with variable movement costs. */
    fun findPath(from: CellCoord, to: CellCoord, mode: TravelMode?): List<CellCoord> {
        if (from == to) return emptyList()
        if (mode != null) {
            if (!map.isPassable(to, mode)) return emptyList()
        } else {
            if (!map.isPassable(to)) return emptyList()
        }

        val open = BinaryHeap<Node> { it.f }
        open.add(Node(from, 0f, heuristic(from, to), null))
        val closed = mutableSetOf<CellCoord>()
        val gScore = mutableMapOf(from to 0f)

        while (open.isNotEmpty()) {
            val current = open.poll()

            if (current.coord == to) return reconstructPath(current)

            if (current.coord in closed) continue
            closed.add(current.coord)

            for (neighbor in neighbors(current.coord, mode)) {
                if (neighbor in closed) continue
                val moveCost = movementCost(neighbor, mode)
                val tentativeG = current.g + moveCost
                if (tentativeG < (gScore[neighbor] ?: Float.MAX_VALUE)) {
                    gScore[neighbor] = tentativeG
                    open.add(Node(neighbor, tentativeG, heuristic(neighbor, to), current))
                }
            }
        }
        return emptyList()
    }

    private fun heuristic(a: CellCoord, b: CellCoord): Float =
        (abs(a.x - b.x) + abs(a.y - b.y)).toFloat()

    private fun neighbors(coord: CellCoord, mode: TravelMode?): List<CellCoord> {
        val deltas = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
        return deltas
            .map { (dx, dy) -> coord.copy(x = coord.x + dx, y = coord.y + dy) }
            .filter { if (mode != null) map.isPassable(it, mode) else map.isPassable(it) }
    }

    /** Variable movement cost based on terrain and travel mode. */
    private fun movementCost(coord: CellCoord, mode: TravelMode?): Float {
        if (mode == null) return 1f
        val cell = map.getCell(coord) ?: return 1f
        val terrain = cell.terrain
        return when (mode) {
            TravelMode.Walk -> when (terrain) {
                Terrain.Sidewalk -> 1.0f
                Terrain.Park -> 1.5f
                Terrain.Interior -> 1.0f
                Terrain.Platform -> 1.0f
                Terrain.Tunnel -> 1.2f
                else -> if (terrain.isRoad) 3.0f else 1.0f // walking on road is dangerous/slow
            }
            TravelMode.Drive -> when (terrain) {
                Terrain.Interstate -> 0.15f
                Terrain.ArterialRoad -> 0.25f
                Terrain.CollectorRoad -> 0.35f
                Terrain.LocalRoad -> 0.5f
                Terrain.RuralRoad -> 0.7f
                Terrain.Parking -> 1.0f
                else -> 1.0f
            }
            TravelMode.Bike -> when (terrain) {
                Terrain.BikePath -> 0.5f
                Terrain.LocalRoad -> 0.7f
                Terrain.CollectorRoad -> 0.7f
                Terrain.RuralRoad -> 0.8f
                else -> 1.0f
            }
            TravelMode.Bus -> when (terrain) {
                Terrain.BusLane -> 0.2f
                Terrain.ArterialRoad -> 0.3f
                Terrain.CollectorRoad -> 0.4f
                Terrain.LocalRoad -> 0.5f
                Terrain.Interstate -> 0.15f
                Terrain.RuralRoad -> 0.7f
                else -> 1.0f
            }
            TravelMode.Train -> when (terrain) {
                Terrain.RailTrack -> 0.1f
                else -> 1.0f
            }
        }
    }

    private fun reconstructPath(node: Node): List<CellCoord> {
        val path = mutableListOf<CellCoord>()
        var current: Node? = node
        while (current != null) {
            path.add(0, current.coord)
            current = current.parent
        }
        return path.drop(1) // exclude start
    }
}
