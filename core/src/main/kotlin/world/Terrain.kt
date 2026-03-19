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

/** General road check (any road class). */
val Terrain.isRoad: Boolean get() = this in ROAD_TERRAINS

/** Walkable by pedestrians — delegates to TravelMode.Walk passable set. */
val Terrain.isWalkable: Boolean get() = this in TravelMode.passableTerrains(TravelMode.Walk)

/** Drivable by private vehicles — delegates to TravelMode.Drive passable set. */
val Terrain.isDrivable: Boolean get() = this in TravelMode.passableTerrains(TravelMode.Drive)

/** Bikeable — delegates to TravelMode.Bike passable set. */
val Terrain.isBikeable: Boolean get() = this in TravelMode.passableTerrains(TravelMode.Bike)

/** Speed limit in cells/tick for vehicles on this terrain. 0 = not drivable/bus-only. */
val Terrain.speedLimit: Int get() = when (this) {
    Terrain.Interstate -> 6
    Terrain.ArterialRoad -> 4
    Terrain.CollectorRoad -> 3
    Terrain.LocalRoad -> 2
    Terrain.RuralRoad -> 1
    Terrain.BusLane -> 3
    Terrain.Parking -> 1
    Terrain.RailTrack -> 4
    else -> 0
}

private val ROAD_TERRAINS = setOf(
    Terrain.Interstate, Terrain.ArterialRoad, Terrain.CollectorRoad,
    Terrain.LocalRoad, Terrain.RuralRoad
)
