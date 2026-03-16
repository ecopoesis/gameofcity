package save

import kotlinx.serialization.Serializable

@Serializable
data class PeepPosition(
    val id: Int,
    val x: Int,
    val y: Int,
    val hunger: Float,
    val fatigue: Float,
    val topNeed: String? = null,
    val topNeedValue: Float = 0f,
    val homeless: Boolean = false
)

@Serializable
data class PeepUpdateMessage(
    val type: String = "peeps",
    val tick: Long,
    val hour: Int = 0,
    val minute: Int = 0,
    val day: Int = 1,
    val population: Int = 0,
    val births: Int = 0,
    val deaths: Int = 0,
    val immigrants: Int = 0,
    val emigrants: Int = 0,
    val peeps: List<PeepPosition>
)

@Serializable
data class SnapshotMessage(
    val type: String = "snapshot",
    val data: SaveData
)

@Serializable
data class CommandMessage(
    val type: String = "command",
    val action: String,
    val value: Int? = null,
    val stringValue: String? = null
)

@Serializable
data class EventData(
    val tick: Long,
    val day: Int,
    val type: String,
    val description: String,
    val peepIds: List<Int> = emptyList()
)

@Serializable
data class StatsData(
    val population: Int = 0,
    val births: Int = 0,
    val deaths: Int = 0,
    val immigrants: Int = 0,
    val emigrants: Int = 0,
    val employmentRate: Float = 0f,
    val unemployed: Int = 0,
    val avgWage: Float = 0f,
    val housingOccupancy: Float = 0f,
    val homeless: Int = 0,
    val avgRent: Float = 0f,
    val avgHappiness: Float = 0f,
    val medianMoney: Float = 0f,
    val gini: Float = 0f,
    val avgFriends: Float = 0f,
    val households: Int = 0,
    val singles: Int = 0
)

@Serializable
data class EventsMessage(
    val type: String = "events",
    val events: List<EventData>,
    val stats: StatsData
)
