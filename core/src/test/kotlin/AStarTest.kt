import pathfind.AStarPathfinder
import world.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AStarTest {

    private fun makeMap(vararg roads: Pair<Int, Int>): WorldMap {
        val map = WorldMap(10, 10)
        roads.forEach { (x, y) -> map.setCell(Cell(CellCoord(x, y), Terrain.Road)) }
        return map
    }

    @Test
    fun `finds direct path on road`() {
        val map = makeMap(0 to 0, 1 to 0, 2 to 0, 3 to 0)
        val finder = AStarPathfinder(map)
        val path = finder.findPath(CellCoord(0, 0), CellCoord(3, 0))
        assertEquals(3, path.size)
        assertEquals(CellCoord(3, 0), path.last())
    }

    @Test
    fun `returns empty path when no route`() {
        val map = makeMap(0 to 0, 5 to 5)
        val finder = AStarPathfinder(map)
        val path = finder.findPath(CellCoord(0, 0), CellCoord(5, 5))
        assertTrue(path.isEmpty())
    }
}
