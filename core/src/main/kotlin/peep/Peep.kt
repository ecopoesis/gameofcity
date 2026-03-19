package peep

import world.BuildingId
import world.CellCoord
import world.PeepId
import world.TravelMode
import world.VehicleType

enum class Gender { Male, Female, NonBinary }

data class Peep(
    val id: PeepId,
    val name: String,
    var age: Int,
    val gender: Gender,
    var position: CellCoord,
    var homeId: BuildingId? = null,
    val relationships: MutableMap<PeepId, String> = mutableMapOf(),
    val needs: MaslowNeeds = MaslowNeeds(),
    var money: Float = 100f,
    var jobId: BuildingId? = null,
    var brain: Brain = IdleBrain(),
    var lastAction: Action = Action.Idle,
    val friendships: MutableMap<PeepId, Float> = mutableMapOf(),
    var schedule: ScheduleType = ScheduleType.Worker,
    var rentGraceDays: Int = 0,
    var partnerId: PeepId? = null,
    var householdId: Int? = null,
    var interactionCount: MutableMap<PeepId, Int> = mutableMapOf(),
    var vehicle: VehicleType? = null,
    var travelMode: TravelMode = TravelMode.Walk,
    var parkingSpot: CellCoord? = null,
    var ridingBusId: Int? = null,
    var alightAtStopId: Int? = null,
    var ridingTrainId: Int? = null,
    var alightAtStationId: Int? = null
) {
    val isHomeless: Boolean get() = homeId == null
    val isPartnered: Boolean get() = partnerId != null
    val isChild: Boolean get() = age < 18
    val isRetired: Boolean get() = age >= 65
    var criticalDays: Int = 0  // consecutive days with 3+ critical needs
}
