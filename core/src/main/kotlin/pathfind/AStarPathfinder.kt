package pathfind

import world.CellCoord
import world.WorldMap
import kotlin.math.abs

class AStarPathfinder(private val map: WorldMap) {

    data class Node(val coord: CellCoord, val g: Float, val h: Float, val parent: Node?) {
        val f: Float get() = g + h
    }

    fun findPath(from: CellCoord, to: CellCoord): List<CellCoord> {
        if (from == to) return emptyList()
        if (!map.isPassable(to)) return emptyList()

        val open = mutableListOf(Node(from, 0f, heuristic(from, to), null))
        val closed = mutableSetOf<CellCoord>()
        val gScore = mutableMapOf(from to 0f)

        while (open.isNotEmpty()) {
            val current = open.minByOrNull { it.f }!!
            open.remove(current)

            if (current.coord == to) return reconstructPath(current)

            closed.add(current.coord)

            for (neighbor in neighbors(current.coord)) {
                if (neighbor in closed) continue
                val tentativeG = current.g + 1f
                if (tentativeG < (gScore[neighbor] ?: Float.MAX_VALUE)) {
                    gScore[neighbor] = tentativeG
                    val node = Node(neighbor, tentativeG, heuristic(neighbor, to), current)
                    open.removeAll { it.coord == neighbor }
                    open.add(node)
                }
            }
        }
        return emptyList()
    }

    private fun heuristic(a: CellCoord, b: CellCoord): Float =
        (abs(a.x - b.x) + abs(a.y - b.y)).toFloat()

    private fun neighbors(coord: CellCoord): List<CellCoord> {
        val deltas = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
        return deltas
            .map { (dx, dy) -> coord.copy(x = coord.x + dx, y = coord.y + dy) }
            .filter { map.isPassable(it) }
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
