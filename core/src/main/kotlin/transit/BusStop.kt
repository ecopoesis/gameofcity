package transit

import world.CellCoord

data class BusStop(
    val id: Int,
    val coord: CellCoord,
    val name: String
)
