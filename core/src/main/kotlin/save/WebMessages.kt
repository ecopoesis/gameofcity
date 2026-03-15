package save

import kotlinx.serialization.Serializable

@Serializable
data class PeepPosition(
    val id: Int,
    val x: Int,
    val y: Int,
    val hunger: Float,
    val fatigue: Float
)

@Serializable
data class PeepUpdateMessage(
    val type: String = "peeps",
    val tick: Long,
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
    val value: Int? = null
)
