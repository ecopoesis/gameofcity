package peep

import world.BuildingId
import world.CellCoord
import world.PeepId

sealed class Action {
    data class MoveTo(val target: CellCoord) : Action()
    data class Eat(val buildingId: BuildingId) : Action()
    data class Sleep(val buildingId: BuildingId) : Action()
    data class Work(val buildingId: BuildingId) : Action()
    data class Socialize(val targetPeepId: PeepId) : Action()
    object Idle : Action()
}
