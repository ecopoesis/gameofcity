package save

import kotlinx.serialization.Serializable

@Serializable
data class ClockData(
    val day: Int = 1,
    val hour: Int = 6,
    val minute: Int = 0
)

@Serializable
data class SaveData(
    val version: Int = 3,
    val tick: Long,
    val clock: ClockData? = null,
    val map: MapData,
    val peeps: List<PeepData>
)

@Serializable
data class MapData(
    val width: Int,
    val height: Int,
    val cells: List<CellData>,
    val buildings: List<BuildingData>
)

@Serializable
data class CellData(
    val x: Int,
    val y: Int,
    val z: Int = 0,
    val terrain: String,
    val buildingId: Int? = null
)

@Serializable
data class BuildingData(
    val id: Int,
    val type: String,
    val subtype: String? = null,
    val cells: List<CoordData>,
    val capacity: Int = 0,
    val occupants: Int = 0,
    val isFull: Boolean = false,
    val wage: Int = 0,
    val rent: Int = 0
)

@Serializable
data class CoordData(val x: Int, val y: Int, val z: Int = 0)

@Serializable
data class MaslowNeedsData(
    val hunger: Float = 0f,
    val thirst: Float = 0f,
    val sleep: Float = 0f,
    val warmth: Float = 0f,
    val shelter: Float = 0f,
    val health: Float = 0f,
    val friendship: Float = 0f,
    val family: Float = 0f,
    val community: Float = 0f,
    val recognition: Float = 0f,
    val accomplishment: Float = 0f,
    val creativity: Float = 0f,
    val learning: Float = 0f,
    val purpose: Float = 0f
)

@Serializable
data class PeepData(
    val id: Int,
    val name: String,
    val age: Int,
    val gender: String,
    val posX: Int,
    val posY: Int,
    val posZ: Int = 0,
    val homeId: Int? = null,
    val jobId: Int? = null,
    val money: Float,
    // v1 flat fields (kept for backward compat)
    val hunger: Float = 0f,
    val fatigue: Float = 0f,
    val shelter: Float = 0f,
    val social: Float = 0f,
    val entertainment: Float = 0f,
    // v2 Maslow needs
    val maslowNeeds: MaslowNeedsData? = null,
    val brainType: String,
    val schedule: String = "Worker",
    val friendships: Map<Int, Float> = emptyMap(),
    val relationships: Map<Int, String> = emptyMap()
)
