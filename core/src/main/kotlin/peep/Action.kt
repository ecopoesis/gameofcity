package peep

import world.BuildingId
import world.CellCoord
import world.PeepId

sealed class Action {
    data class MoveTo(val target: CellCoord) : Action()
    data class Eat(val buildingId: BuildingId) : Action()
    data class Drink(val buildingId: BuildingId) : Action()
    data class Sleep(val buildingId: BuildingId) : Action()
    data class Work(val buildingId: BuildingId) : Action()
    data class Socialize(val targetPeepId: PeepId) : Action()
    data class Shop(val buildingId: BuildingId) : Action()
    data class Heal(val buildingId: BuildingId) : Action()
    data class Learn(val buildingId: BuildingId) : Action()
    data class Exercise(val buildingId: BuildingId) : Action()
    data class Relax(val buildingId: BuildingId) : Action()
    data class Watch(val buildingId: BuildingId) : Action()
    object Idle : Action()
}
