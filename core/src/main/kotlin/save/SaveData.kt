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
    val version: Int = 7,
    val tick: Long,
    val clock: ClockData? = null,
    val map: MapData,
    val peeps: List<PeepData>,
    val transit: TransitData? = null
)

@Serializable
data class TransitData(
    val stops: List<BusStopData> = emptyList(),
    val routes: List<BusRouteData> = emptyList(),
    val buses: List<BusData> = emptyList(),
    val stations: List<TrainStationData> = emptyList(),
    val trainRoutes: List<TrainRouteData> = emptyList(),
    val trains: List<TrainData> = emptyList()
)

@Serializable
data class BusStopData(
    val id: Int,
    val x: Int,
    val y: Int,
    val name: String
)

@Serializable
data class BusRouteData(
    val id: Int,
    val name: String,
    val stopIds: List<Int>,
    val headwayTicks: Int = 300
)

@Serializable
data class BusData(
    val id: Int,
    val routeId: Int,
    val x: Int,
    val y: Int,
    val currentStopIndex: Int,
    val movingForward: Boolean = true,
    val passengers: List<Int> = emptyList()
)

@Serializable
data class TrainStationData(
    val id: Int,
    val x: Int,
    val y: Int,
    val name: String,
    val isSubway: Boolean = false
)

@Serializable
data class TrainRouteData(
    val id: Int,
    val name: String,
    val stationIds: List<Int>,
    val headwayTicks: Int = 200,
    val isSubway: Boolean = false
)

@Serializable
data class TrainData(
    val id: Int,
    val routeId: Int,
    val x: Int,
    val y: Int,
    val currentStationIndex: Int,
    val movingForward: Boolean = true,
    val passengers: List<Int> = emptyList()
)

@Serializable
data class MapData(
    val width: Int,
    val height: Int,
    val cells: List<CellData>,
    val buildings: List<BuildingData>,
    val parkedVehicles: List<CoordData> = emptyList()
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
    val relationships: Map<Int, String> = emptyMap(),
    val vehicle: String? = null,
    val travelMode: String = "Walk",
    val parkingSpotX: Int? = null,
    val parkingSpotY: Int? = null,
    val parkingSpotZ: Int? = null
)
