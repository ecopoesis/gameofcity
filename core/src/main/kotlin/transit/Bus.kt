package transit

import world.CellCoord
import world.PeepId

data class Bus(
    val id: Int,
    val routeId: Int,
    var position: CellCoord,
    var currentStopIndex: Int = 0,
    val passengers: MutableList<PeepId> = mutableListOf(),
    var ticksAtStop: Int = 0,
    var movingForward: Boolean = true
) {
    companion object {
        const val DWELL_TICKS = 5
    }
}
