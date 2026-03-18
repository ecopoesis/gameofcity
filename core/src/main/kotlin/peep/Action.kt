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
    data class ParkCar(val spot: CellCoord) : Action()
    data class RetrieveCar(val spot: CellCoord) : Action()
    data class WaitForBus(val stopId: Int) : Action()
    data class RideBus(val busId: Int) : Action()
    object Idle : Action()

    fun targetBuildingId(): Int? = when (this) {
        is Eat -> buildingId
        is Drink -> buildingId
        is Sleep -> buildingId
        is Work -> buildingId
        is Shop -> buildingId
        is Heal -> buildingId
        is Learn -> buildingId
        is Exercise -> buildingId
        is Relax -> buildingId
        is Watch -> buildingId
        is MoveTo, is Socialize, is ParkCar, is RetrieveCar,
        is WaitForBus, is RideBus, Idle -> null
    }
}
