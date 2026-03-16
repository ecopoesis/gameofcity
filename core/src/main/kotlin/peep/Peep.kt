package peep

import world.BuildingId
import world.CellCoord
import world.PeepId

enum class Gender { Male, Female, NonBinary }

data class Peep(
    val id: PeepId,
    val name: String,
    val age: Int,
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
    var interactionCount: MutableMap<PeepId, Int> = mutableMapOf()
) {
    val isHomeless: Boolean get() = homeId == null
    val isPartnered: Boolean get() = partnerId != null
}
