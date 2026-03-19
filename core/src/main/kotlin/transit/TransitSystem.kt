package transit

import world.CellCoord
import world.PeepId

class TransitSystem {

    val stops: MutableMap<Int, BusStop> = mutableMapOf()
    val routes: MutableMap<Int, BusRoute> = mutableMapOf()
    val buses: MutableMap<Int, Bus> = mutableMapOf()

    val stations: MutableMap<Int, TrainStation> = mutableMapOf()
    val trainRoutes: MutableMap<Int, TrainRoute> = mutableMapOf()
    val trains: MutableMap<Int, Train> = mutableMapOf()

    private var nextBusId = 0
    private var nextTrainId = 0

    fun addStop(stop: BusStop) {
        stops[stop.id] = stop
    }

    fun addRoute(route: BusRoute) {
        routes[route.id] = route
        // Spawn initial buses along the route
        if (route.stops.size >= 2) {
            val bus = Bus(
                id = nextBusId++,
                routeId = route.id,
                position = route.stops[0].coord
            )
            buses[bus.id] = bus
        }
    }

    fun addStation(station: TrainStation) {
        stations[station.id] = station
    }

    fun addTrainRoute(route: TrainRoute) {
        trainRoutes[route.id] = route
        if (route.stations.size >= 2) {
            val train = Train(
                id = nextTrainId++,
                routeId = route.id,
                position = route.stations[0].coord
            )
            trains[train.id] = train
        }
    }

    /** Find nearest bus stop to a given coordinate. */
    fun nearestStop(coord: CellCoord): BusStop? {
        return stops.values.minByOrNull { it.coord.distanceTo(coord) }
    }

    /** Find nearest train station to a given coordinate. */
    fun nearestStation(coord: CellCoord): TrainStation? {
        return stations.values.minByOrNull { it.coord.distanceTo(coord) }
    }

    /** Find nearest stop to destination that is on a route serving the origin stop. */
    fun findBestRoute(originStop: BusStop, destCoord: CellCoord): Pair<BusRoute, BusStop>? {
        var bestRoute: BusRoute? = null
        var bestDestStop: BusStop? = null
        var bestDist = Int.MAX_VALUE

        for (route in routes.values) {
            val routeStopIds = route.stops.map { it.id }.toSet()
            if (originStop.id !in routeStopIds) continue

            for (stop in route.stops) {
                if (stop.id == originStop.id) continue
                val dist = stop.coord.distanceTo(destCoord)
                if (dist < bestDist) {
                    bestDist = dist
                    bestRoute = route
                    bestDestStop = stop
                }
            }
        }

        return if (bestRoute != null && bestDestStop != null) bestRoute to bestDestStop else null
    }

    /** Find best train route from origin station to destination coord. */
    fun findBestTrainRoute(originStation: TrainStation, destCoord: CellCoord): Pair<TrainRoute, TrainStation>? {
        var bestRoute: TrainRoute? = null
        var bestDestStation: TrainStation? = null
        var bestDist = Int.MAX_VALUE

        for (route in trainRoutes.values) {
            val routeStationIds = route.stations.map { it.id }.toSet()
            if (originStation.id !in routeStationIds) continue

            for (station in route.stations) {
                if (station.id == originStation.id) continue
                val dist = station.coord.distanceTo(destCoord)
                if (dist < bestDist) {
                    bestDist = dist
                    bestRoute = route
                    bestDestStation = station
                }
            }
        }

        return if (bestRoute != null && bestDestStation != null) bestRoute to bestDestStation else null
    }

    /** Advance all buses one tick. Returns map of peepId -> new position for riding peeps. */
    fun advance(): Map<PeepId, CellCoord> {
        val peepPositions = mutableMapOf<PeepId, CellCoord>()

        for (bus in buses.values) {
            val route = routes[bus.routeId] ?: continue
            if (route.stops.size < 2) continue

            if (bus.ticksAtStop > 0) {
                // Dwelling at stop
                bus.ticksAtStop--
                bus.passengers.forEach { peepPositions[it] = bus.position }
                continue
            }

            // Move to next stop
            if (bus.movingForward) {
                bus.currentStopIndex++
                if (bus.currentStopIndex >= route.stops.size) {
                    bus.currentStopIndex = route.stops.size - 2
                    bus.movingForward = false
                }
            } else {
                bus.currentStopIndex--
                if (bus.currentStopIndex < 0) {
                    bus.currentStopIndex = 1
                    bus.movingForward = true
                }
            }

            val nextStop = route.stops[bus.currentStopIndex]
            bus.position = nextStop.coord
            bus.ticksAtStop = Bus.DWELL_TICKS

            // Update passenger positions
            bus.passengers.forEach { peepPositions[it] = bus.position }
        }

        return peepPositions
    }

    /** Advance all trains one tick. Returns map of peepId -> new position for riding peeps. */
    fun advanceTrains(): Map<PeepId, CellCoord> {
        val peepPositions = mutableMapOf<PeepId, CellCoord>()

        for (train in trains.values) {
            val route = trainRoutes[train.routeId] ?: continue
            if (route.stations.size < 2) continue

            if (train.ticksAtStation > 0) {
                train.ticksAtStation--
                train.passengers.forEach { peepPositions[it] = train.position }
                continue
            }

            // Move to next station
            if (train.movingForward) {
                train.currentStationIndex++
                if (train.currentStationIndex >= route.stations.size) {
                    train.currentStationIndex = route.stations.size - 2
                    train.movingForward = false
                }
            } else {
                train.currentStationIndex--
                if (train.currentStationIndex < 0) {
                    train.currentStationIndex = 1
                    train.movingForward = true
                }
            }

            val nextStation = route.stations[train.currentStationIndex]
            train.position = nextStation.coord
            train.ticksAtStation = Train.DWELL_TICKS

            train.passengers.forEach { peepPositions[it] = train.position }
        }

        return peepPositions
    }

    /** Board a peep onto a bus at the given stop. Returns the bus if boarding succeeded. */
    fun boardBus(peepId: PeepId, stopId: Int): Bus? {
        val stop = stops[stopId] ?: return null
        // Find a bus currently at this stop
        val bus = buses.values.firstOrNull { bus ->
            val route = routes[bus.routeId] ?: return@firstOrNull false
            val currentStop = route.stops.getOrNull(bus.currentStopIndex)
            currentStop?.id == stopId && bus.ticksAtStop > 0
        }
        if (bus != null) {
            bus.passengers.add(peepId)
        }
        return bus
    }

    /** Remove a peep from their bus. */
    fun alightBus(peepId: PeepId) {
        for (bus in buses.values) {
            bus.passengers.remove(peepId)
        }
    }

    /** Check if a bus is currently at the given stop. */
    fun busAtStop(stopId: Int): Bus? {
        return buses.values.firstOrNull { bus ->
            val route = routes[bus.routeId] ?: return@firstOrNull false
            val currentStop = route.stops.getOrNull(bus.currentStopIndex)
            currentStop?.id == stopId && bus.ticksAtStop > 0
        }
    }

    /** Board a peep onto a train at the given station. Returns the train if boarding succeeded. */
    fun boardTrain(peepId: PeepId, stationId: Int): Train? {
        stations[stationId] ?: return null
        val train = trains.values.firstOrNull { train ->
            val route = trainRoutes[train.routeId] ?: return@firstOrNull false
            val currentStation = route.stations.getOrNull(train.currentStationIndex)
            currentStation?.id == stationId && train.ticksAtStation > 0
        }
        if (train != null) {
            train.passengers.add(peepId)
        }
        return train
    }

    /** Remove a peep from their train. */
    fun alightTrain(peepId: PeepId) {
        for (train in trains.values) {
            train.passengers.remove(peepId)
        }
    }

    /** Check if a train is currently at the given station. */
    fun trainAtStation(stationId: Int): Train? {
        return trains.values.firstOrNull { train ->
            val route = trainRoutes[train.routeId] ?: return@firstOrNull false
            val currentStation = route.stations.getOrNull(train.currentStationIndex)
            currentStation?.id == stationId && train.ticksAtStation > 0
        }
    }
}
