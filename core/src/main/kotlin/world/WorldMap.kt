package world

enum class TravelMode {
    Walk, Drive, Bike, Bus, Train;

    companion object {
        private val WALK_TERRAINS = setOf(
            Terrain.Sidewalk, Terrain.Park, Terrain.Interior, Terrain.Platform, Terrain.Tunnel
        )
        private val DRIVE_TERRAINS = setOf(
            Terrain.Interstate, Terrain.ArterialRoad, Terrain.CollectorRoad,
            Terrain.LocalRoad, Terrain.RuralRoad, Terrain.Parking
        )
        private val BIKE_TERRAINS = setOf(
            Terrain.BikePath, Terrain.LocalRoad, Terrain.CollectorRoad, Terrain.RuralRoad
        )
        private val BUS_TERRAINS = setOf(
            Terrain.Interstate, Terrain.ArterialRoad, Terrain.CollectorRoad,
            Terrain.LocalRoad, Terrain.RuralRoad, Terrain.BusLane
        )
        private val TRAIN_TERRAINS = setOf(Terrain.RailTrack)

        fun passableTerrains(mode: TravelMode): Set<Terrain> = when (mode) {
            Walk -> WALK_TERRAINS
            Drive -> DRIVE_TERRAINS
            Bike -> BIKE_TERRAINS
            Bus -> BUS_TERRAINS
            Train -> TRAIN_TERRAINS
        }
    }
}

class WorldMap(val width: Int, val height: Int) {

    // Ground layer (z=0): dense array for fast access
    private val ground: Array<Array<Cell>> = Array(width) { x ->
        Array(height) { y ->
            Cell(CellCoord(x, y, 0), Terrain.Empty)
        }
    }

    // Upper/lower layers: sparse map
    private val layers: HashMap<CellCoord, Cell> = HashMap()

    // Spatial index: who is at which cell
    val peepsAt: HashMap<CellCoord, MutableList<PeepId>> = HashMap()

    // Building index
    val buildings: HashMap<BuildingId, Building> = HashMap()

    // Parked vehicle tracking: cell coord -> peep id of owner
    val parkedVehicles: HashMap<CellCoord, PeepId> = HashMap()

    fun getCell(coord: CellCoord): Cell? = when (coord.z) {
        0 -> if (coord.x in 0 until width && coord.y in 0 until height) ground[coord.x][coord.y] else null
        else -> layers[coord]
    }

    fun setCell(cell: Cell) {
        val coord = cell.coord
        when (coord.z) {
            0 -> if (coord.x in 0 until width && coord.y in 0 until height) ground[coord.x][coord.y] = cell
            else -> layers[coord] = cell
        }
    }

    fun addBuilding(building: Building) {
        buildings[building.id] = building
        building.cells.forEach { coord ->
            val existing = getCell(coord)
            if (existing != null) {
                setCell(existing.copy(buildingId = building.id, terrain = Terrain.Interior))
            }
        }
    }

    fun groundCells(): Sequence<Cell> = sequence {
        for (x in 0 until width) for (y in 0 until height) yield(ground[x][y])
    }

    fun buildingsOfType(type: BuildingType): List<Building> =
        buildings.values.filter { it.type == type }

    fun clearPeepsAt() {
        peepsAt.clear()
    }

    /** Check passability for a specific travel mode. */
    fun isPassable(coord: CellCoord, mode: TravelMode): Boolean {
        val cell = getCell(coord) ?: return false
        return cell.terrain in TravelMode.passableTerrains(mode)
    }

    /** Legacy passability: any non-empty terrain is passable. */
    fun isPassable(coord: CellCoord): Boolean {
        val cell = getCell(coord) ?: return false
        return cell.terrain != Terrain.Empty
    }
}
