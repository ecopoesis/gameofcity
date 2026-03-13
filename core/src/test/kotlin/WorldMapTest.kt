import world.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WorldMapTest {

    @Test
    fun `cell can be set and retrieved at ground layer`() {
        val map = WorldMap(10, 10)
        val coord = CellCoord(3, 4)
        map.setCell(Cell(coord, Terrain.Road))
        val cell = map.getCell(coord)
        assertNotNull(cell)
        assertEquals(Terrain.Road, cell.terrain)
    }

    @Test
    fun `building is indexed and cells updated`() {
        val map = WorldMap(10, 10)
        val coords = setOf(CellCoord(1, 1), CellCoord(1, 2))
        coords.forEach { map.setCell(Cell(it, Terrain.Road)) }
        val building = Building(id = 1, type = BuildingType.Residential, cells = coords)
        map.addBuilding(building)
        assertEquals(building, map.buildings[1])
        assertEquals(Terrain.Interior, map.getCell(CellCoord(1, 1))?.terrain)
    }
}
