package world

enum class Terrain {
    Interstate,      // Divided highway / freeway
    ArterialRoad,    // Class 1: State highways, major arterials
    CollectorRoad,   // Class 2: Inter-town connectors
    LocalRoad,       // Class 3: Standard maintained town roads
    RuralRoad,       // Class 4: Unmaintained / gravel
    BusLane,         // Dedicated transit lane
    BikePath,        // Bike lane / path
    Sidewalk,        // Pedestrian
    Park,
    Interior,
    Tunnel,
    Parking,         // Parking space
    RailTrack,       // Train/subway rail
    Platform,        // Transit platform (bus stop, station platform)
    Empty
}

val Terrain.isRoad: Boolean get() = this in ROAD_TERRAINS
val Terrain.isWalkable: Boolean get() = this in WALKABLE_TERRAINS
val Terrain.isDrivable: Boolean get() = this in DRIVABLE_TERRAINS
val Terrain.isBikeable: Boolean get() = this in BIKEABLE_TERRAINS

/** Speed limit in cells/tick for vehicles on this terrain. 0 = not drivable. */
val Terrain.speedLimit: Int get() = when (this) {
    Terrain.Interstate -> 6
    Terrain.ArterialRoad -> 4
    Terrain.CollectorRoad -> 3
    Terrain.LocalRoad -> 2
    Terrain.RuralRoad -> 1
    Terrain.BusLane -> 3
    Terrain.Parking -> 1
    else -> 0
}

private val ROAD_TERRAINS = setOf(
    Terrain.Interstate, Terrain.ArterialRoad, Terrain.CollectorRoad,
    Terrain.LocalRoad, Terrain.RuralRoad
)

private val WALKABLE_TERRAINS = setOf(
    Terrain.Sidewalk, Terrain.Park, Terrain.Interior, Terrain.Platform, Terrain.Tunnel
)

private val DRIVABLE_TERRAINS = setOf(
    Terrain.Interstate, Terrain.ArterialRoad, Terrain.CollectorRoad,
    Terrain.LocalRoad, Terrain.RuralRoad, Terrain.Parking
)

private val BIKEABLE_TERRAINS = setOf(
    Terrain.BikePath, Terrain.LocalRoad, Terrain.CollectorRoad, Terrain.RuralRoad
)
