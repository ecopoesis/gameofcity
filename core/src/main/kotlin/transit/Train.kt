package transit

import world.CellCoord
import world.PeepId

data class Train(
    val id: Int,
    val routeId: Int,
    var position: CellCoord,
    var currentStationIndex: Int = 0,
    val passengers: MutableList<PeepId> = mutableListOf(),
    var ticksAtStation: Int = 0,
    var movingForward: Boolean = true
) {
    companion object {
        const val DWELL_TICKS = 8
    }
}
