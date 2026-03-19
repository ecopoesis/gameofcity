package peep

import pathfind.AStarPathfinder
import transit.TransitSystem
import world.BuildingSubtype
import world.CellCoord
import world.Terrain
import world.TravelMode
import world.VehicleType
import world.WorldMap
import world.speedLimit

class NavigationHelper {

    private val pathQueue = ArrayDeque<CellCoord>()
    private var terminalAction: Action = Action.Idle
    private var _pathfinder: AStarPathfinder? = null
    private var currentTravelMode: TravelMode? = null

    /** Upcoming trip legs after the current path completes. */
    private val upcomingLegs = ArrayDeque<TripLeg>()

    fun pathfinder(map: WorldMap): AStarPathfinder =
        _pathfinder ?: AStarPathfinder(map).also { _pathfinder = it }

    fun pendingAction(peep: Peep, world: WorldView): Action? {
        // Currently riding a train — check if we should alight
        if (peep.ridingTrainId != null) {
            val train = world.transit.trains[peep.ridingTrainId]
            if (train != null) {
                val route = world.transit.trainRoutes[train.routeId]
                val currentStation = route?.stations?.getOrNull(train.currentStationIndex)
                if (currentStation != null && currentStation.id == peep.alightAtStationId && train.ticksAtStation > 0) {
                    world.transit.alightTrain(peep.id)
                    peep.ridingTrainId = null
                    peep.travelMode = TravelMode.Walk
                    peep.alightAtStationId = null
                    if (upcomingLegs.isNotEmpty()) {
                        val leg = upcomingLegs.removeFirst()
                        val pf = _pathfinder ?: return Action.Idle
                        return navigateTo(peep.position, leg.destination, pf, leg.mode, leg.terminal)
                    }
                    return Action.Idle
                }
            } else {
                peep.ridingTrainId = null
                peep.travelMode = TravelMode.Walk
                peep.alightAtStationId = null
            }
            return Action.RideTrain(peep.ridingTrainId ?: return Action.Idle)
        }

        // Currently riding a bus — check if we should alight
        if (peep.ridingBusId != null) {
            val bus = world.transit.buses[peep.ridingBusId]
            if (bus != null) {
                val route = world.transit.routes[bus.routeId]
                val currentStop = route?.stops?.getOrNull(bus.currentStopIndex)
                if (currentStop != null && currentStop.id == peep.alightAtStopId && bus.ticksAtStop > 0) {
                    // Alight at this stop
                    world.transit.alightBus(peep.id)
                    peep.ridingBusId = null
                    peep.travelMode = TravelMode.Walk
                    peep.alightAtStopId = null
                    // Continue with remaining trip legs
                    if (upcomingLegs.isNotEmpty()) {
                        val leg = upcomingLegs.removeFirst()
                        val pf = _pathfinder ?: return Action.Idle
                        return navigateTo(peep.position, leg.destination, pf, leg.mode, leg.terminal)
                    }
                    return Action.Idle
                }
            } else {
                // Bus disappeared — reset
                peep.ridingBusId = null
                peep.travelMode = TravelMode.Walk
                peep.alightAtStopId = null
            }
            return Action.RideBus(peep.ridingBusId ?: return Action.Idle)
        }

        // Keep walking/driving current path
        if (pathQueue.isNotEmpty()) {
            val mode = currentTravelMode ?: peep.travelMode
            val steps = stepsPerTick(mode, world.map, pathQueue.first())
            var dest = pathQueue.removeFirst()
            repeat(steps - 1) {
                if (pathQueue.isNotEmpty()) dest = pathQueue.removeFirst()
            }
            return Action.MoveTo(dest)
        }
        // Execute terminal action on arrival
        val pending = terminalAction
        if (pending != Action.Idle) {
            terminalAction = Action.Idle
            currentTravelMode = null
            return pending
        }
        // Start next trip leg if available
        if (upcomingLegs.isNotEmpty()) {
            val leg = upcomingLegs.removeFirst()
            val pf = _pathfinder ?: return null
            return navigateTo(peep.position, leg.destination, pf, leg.mode, leg.terminal)
        }
        return null
    }

    /** Navigate using legacy passability (all terrains). */
    fun navigateTo(from: CellCoord, to: CellCoord, pf: AStarPathfinder, terminal: Action): Action =
        navigateTo(from, to, pf, null, terminal)

    /** Navigate using a specific travel mode for pathfinding. */
    fun navigateTo(from: CellCoord, to: CellCoord, pf: AStarPathfinder, mode: TravelMode?, terminal: Action): Action {
        if (from == to) return terminal
        val path = pf.findPath(from, to, mode)
        if (path.isEmpty()) return Action.Idle
        pathQueue.clear()
        pathQueue.addAll(path)
        upcomingLegs.clear()
        terminalAction = terminal
        currentTravelMode = mode
        return Action.MoveTo(pathQueue.removeFirst())
    }

    /**
     * Plan a multi-leg trip using the peep's vehicle if available.
     *
     * Car trip: walk to car -> drive to parking near dest -> park -> walk to dest.
     * Bike trip: bike on roads to near dest -> walk to dest.
     * No vehicle: walk directly (or use transit).
     */
    fun planTrip(
        from: CellCoord, to: CellCoord,
        map: WorldMap, pf: AStarPathfinder,
        peep: Peep, terminal: Action,
        transit: TransitSystem? = null
    ): Action {
        upcomingLegs.clear()

        // Car trip with parked car
        if (peep.vehicle == VehicleType.Car && peep.parkingSpot != null && from.distanceTo(to) > 30) {
            val carPos = peep.parkingSpot!!
            val parkingNearDest = findNearbyParking(to, map)

            if (parkingNearDest != null && parkingNearDest != carPos) {
                // Leg 2: drive from car to parking near destination
                upcomingLegs.addLast(TripLeg(parkingNearDest, TravelMode.Drive, Action.ParkCar(parkingNearDest)))
                // Leg 3: walk from parking to destination
                upcomingLegs.addLast(TripLeg(to, TravelMode.Walk, terminal))
                // Leg 1: walk to car
                return navigateWithoutClearingLegs(from, carPos, pf, TravelMode.Walk, Action.RetrieveCar(carPos))
            }
        }

        // Bike trip
        if (peep.vehicle == VehicleType.Bike && from.distanceTo(to) > 10) {
            return navigateTo(from, to, pf, TravelMode.Bike, terminal)
        }

        // Transit trip: no car, distance > 20, transit system available
        if (peep.vehicle != VehicleType.Car && transit != null && from.distanceTo(to) > 20) {
            val transitAction = planTransitTrip(from, to, pf, peep, terminal, transit)
            if (transitAction != null) return transitAction
        }

        // Default: walk directly
        return navigateTo(from, to, pf, TravelMode.Walk, terminal)
    }

    /**
     * Plan a transit trip (train preferred for long distance, bus as fallback).
     * Returns null if no suitable transit route found.
     */
    private fun planTransitTrip(
        from: CellCoord, to: CellCoord,
        pf: AStarPathfinder, peep: Peep,
        terminal: Action, transit: TransitSystem
    ): Action? {
        val distance = from.distanceTo(to)

        // Try train first for longer distances (> 40 cells)
        if (distance > 40 && transit.stations.isNotEmpty()) {
            val originStation = transit.nearestStation(from)
            if (originStation != null && originStation.coord.distanceTo(from) < 40) {
                val routeInfo = transit.findBestTrainRoute(originStation, to)
                if (routeInfo != null) {
                    val (_, destStation) = routeInfo
                    if (destStation.coord.distanceTo(to) < distance * 0.7) {
                        peep.alightAtStationId = destStation.id
                        // Leg 2: wait for train at origin station
                        upcomingLegs.addLast(TripLeg(originStation.coord, null, Action.WaitForTrain(originStation.id)))
                        // Leg 3: walk from dest station to destination
                        upcomingLegs.addLast(TripLeg(to, TravelMode.Walk, terminal))
                        // Leg 1: walk to nearest train station
                        return navigateWithoutClearingLegs(from, originStation.coord, pf, TravelMode.Walk, Action.WaitForTrain(originStation.id))
                    }
                }
            }
        }

        // Try bus
        val originStop = transit.nearestStop(from)
        if (originStop != null && originStop.coord.distanceTo(from) < 30) {
            val routeInfo = transit.findBestRoute(originStop, to)
            if (routeInfo != null) {
                val (_, destStop) = routeInfo
                if (destStop.coord.distanceTo(to) < distance * 0.7) {
                    peep.alightAtStopId = destStop.id
                    // Leg 2: wait for bus at origin stop
                    upcomingLegs.addLast(TripLeg(originStop.coord, null, Action.WaitForBus(originStop.id)))
                    // Leg 3: walk from dest stop to destination
                    upcomingLegs.addLast(TripLeg(to, TravelMode.Walk, terminal))
                    // Leg 1: walk to nearest bus stop
                    return navigateWithoutClearingLegs(from, originStop.coord, pf, TravelMode.Walk, Action.WaitForBus(originStop.id))
                }
            }
        }

        return null
    }

    /** Navigate without clearing upcoming legs (for first leg of multi-leg trip). */
    private fun navigateWithoutClearingLegs(
        from: CellCoord, to: CellCoord,
        pf: AStarPathfinder, mode: TravelMode?, terminal: Action
    ): Action {
        if (from == to) return terminal
        val path = pf.findPath(from, to, mode)
        if (path.isEmpty()) {
            // Can't reach destination - fall back
            upcomingLegs.clear()
            return Action.Idle
        }
        pathQueue.clear()
        pathQueue.addAll(path)
        terminalAction = terminal
        currentTravelMode = mode
        return Action.MoveTo(pathQueue.removeFirst())
    }

    /**
     * Find the nearest available parking spot to the target.
     * Checks on-street Parking terrain and ParkingLot/ParkingGarage buildings.
     * Expands search radius if nothing found nearby.
     */
    private fun findNearbyParking(target: CellCoord, map: WorldMap): CellCoord? {
        var best: CellCoord? = null
        var bestDist = Int.MAX_VALUE

        // Search on-street parking cells
        for (radius in intArrayOf(15, 30)) {
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    val c = CellCoord(target.x + dx, target.y + dy)
                    val cell = map.getCell(c) ?: continue
                    if (cell.terrain == Terrain.Parking && c !in map.parkedVehicles) {
                        val dist = c.distanceTo(target)
                        if (dist < bestDist) {
                            bestDist = dist
                            best = c
                        }
                    }
                }
            }
            if (best != null) return best
        }

        // Check ParkingLot/ParkingGarage buildings near target
        val parkingBuildings = map.buildings.values.filter {
            it.subtype == BuildingSubtype.ParkingLot || it.subtype == BuildingSubtype.ParkingGarage
        }
        for (bld in parkingBuildings) {
            val entrance = bld.cells.firstOrNull() ?: continue
            val dist = entrance.distanceTo(target)
            if (dist < bestDist && !bld.isFull) {
                // Find an unoccupied cell in this parking building
                val freeCell = bld.cells.firstOrNull { it !in map.parkedVehicles }
                if (freeCell != null) {
                    bestDist = dist
                    best = freeCell
                }
            }
        }

        return best
    }

    private fun stepsPerTick(mode: TravelMode, map: WorldMap, nextCell: CellCoord): Int {
        return when (mode) {
            TravelMode.Walk -> 1
            TravelMode.Bike -> 2
            TravelMode.Drive -> {
                val terrain = map.getCell(nextCell)?.terrain
                terrain?.speedLimit?.coerceAtLeast(1) ?: 1
            }
            TravelMode.Bus -> {
                val terrain = map.getCell(nextCell)?.terrain
                terrain?.speedLimit?.coerceAtLeast(1) ?: 1
            }
            TravelMode.Train -> 4
        }
    }

    private data class TripLeg(val destination: CellCoord, val mode: TravelMode?, val terminal: Action)
}
