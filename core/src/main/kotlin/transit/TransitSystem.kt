package transit

import world.CellCoord
import world.PeepId

class TransitSystem {

    val stops: MutableMap<Int, BusStop> = mutableMapOf()
    val routes: MutableMap<Int, BusRoute> = mutableMapOf()
    val buses: MutableMap<Int, Bus> = mutableMapOf()

    private var nextBusId = 0

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

    /** Find nearest bus stop to a given coordinate. */
    fun nearestStop(coord: CellCoord): BusStop? {
        return stops.values.minByOrNull { it.coord.distanceTo(coord) }
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
}
