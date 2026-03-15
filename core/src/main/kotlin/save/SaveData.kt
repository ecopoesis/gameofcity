package save

import kotlinx.serialization.Serializable

@Serializable
data class SaveData(
    val version: Int = 1,
    val tick: Long,
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
    val cells: List<CoordData>
)

@Serializable
data class CoordData(val x: Int, val y: Int, val z: Int = 0)

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
    val hunger: Float,
    val fatigue: Float,
    val shelter: Float,
    val social: Float,
    val entertainment: Float,
    val brainType: String,
    val friendships: Map<Int, Float> = emptyMap(),
    val relationships: Map<Int, String> = emptyMap()
)
