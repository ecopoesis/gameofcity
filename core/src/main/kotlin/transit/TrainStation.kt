package transit

import world.CellCoord

data class TrainStation(
    val id: Int,
    val coord: CellCoord,
    val name: String,
    val isSubway: Boolean = false
)
