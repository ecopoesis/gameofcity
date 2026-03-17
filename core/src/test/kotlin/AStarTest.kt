import pathfind.AStarPathfinder
import pathfind.BinaryHeap
import world.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AStarTest {

    private fun makeMap(vararg roads: Pair<Int, Int>): WorldMap {
        val map = WorldMap(10, 10)
        roads.forEach { (x, y) -> map.setCell(Cell(CellCoord(x, y), Terrain.LocalRoad)) }
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

    @Test
    fun `finds path with travel mode Walk on sidewalks`() {
        val map = WorldMap(10, 10)
        for (x in 0..5) map.setCell(Cell(CellCoord(x, 0), Terrain.Sidewalk))
        val finder = AStarPathfinder(map)
        val path = finder.findPath(CellCoord(0, 0), CellCoord(5, 0), TravelMode.Walk)
        assertEquals(5, path.size)
        assertEquals(CellCoord(5, 0), path.last())
    }

    @Test
    fun `walk mode cannot traverse roads-only map`() {
        val map = makeMap(0 to 0, 1 to 0, 2 to 0)
        val finder = AStarPathfinder(map)
        // Walk mode only allows sidewalks, parks, interior, platform, tunnel
        val path = finder.findPath(CellCoord(0, 0), CellCoord(2, 0), TravelMode.Walk)
        assertTrue(path.isEmpty())
    }

    @Test
    fun `drive mode finds path on roads`() {
        val map = makeMap(0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0)
        val finder = AStarPathfinder(map)
        val path = finder.findPath(CellCoord(0, 0), CellCoord(4, 0), TravelMode.Drive)
        assertEquals(4, path.size)
        assertEquals(CellCoord(4, 0), path.last())
    }

    @Test
    fun `drive mode prefers arterial over local (lower cost)`() {
        val map = WorldMap(10, 10)
        // Two parallel routes: arterial (top) and local (bottom)
        for (x in 0..8) {
            map.setCell(Cell(CellCoord(x, 0), Terrain.ArterialRoad))
            map.setCell(Cell(CellCoord(x, 2), Terrain.LocalRoad))
        }
        // Connect start and end vertically
        map.setCell(Cell(CellCoord(0, 1), Terrain.LocalRoad))
        map.setCell(Cell(CellCoord(8, 1), Terrain.LocalRoad))

        val finder = AStarPathfinder(map)
        val path = finder.findPath(CellCoord(0, 1), CellCoord(8, 1), TravelMode.Drive)
        assertTrue(path.isNotEmpty())
        // Path should prefer the arterial route (y=0) over local (y=2)
        val arterialSteps = path.count { it.y == 0 }
        val localSteps = path.count { it.y == 2 }
        assertTrue(arterialSteps > localSteps, "Should prefer arterial (got $arterialSteps arterial vs $localSteps local)")
    }

    @Test
    fun `binary heap maintains min order`() {
        val heap = BinaryHeap<Int> { it.toFloat() }
        heap.add(5)
        heap.add(1)
        heap.add(3)
        heap.add(2)
        heap.add(4)

        assertEquals(1, heap.poll())
        assertEquals(2, heap.poll())
        assertEquals(3, heap.poll())
        assertEquals(4, heap.poll())
        assertEquals(5, heap.poll())
        assertTrue(heap.isEmpty())
    }

    @Test
    fun `binary heap handles duplicates`() {
        val heap = BinaryHeap<Int> { it.toFloat() }
        heap.add(3)
        heap.add(1)
        heap.add(3)
        heap.add(1)

        assertEquals(1, heap.poll())
        assertEquals(1, heap.poll())
        assertEquals(3, heap.poll())
        assertEquals(3, heap.poll())
        assertTrue(heap.isEmpty())
    }
}
