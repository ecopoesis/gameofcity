package world

typealias BuildingId = Int

data class Cell(
    val coord: CellCoord,
    val terrain: Terrain,
    val buildingId: BuildingId? = null
)
