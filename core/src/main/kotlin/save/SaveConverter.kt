package save

import peep.*
import tick.TickEngine
import transit.*
import world.*

object SaveConverter {

    fun toSaveData(engine: TickEngine): SaveData {
        val map = engine.map
        val cells = mutableListOf<CellData>()
        for (x in 0 until map.width) {
            for (y in 0 until map.height) {
                val cell = map.getCell(CellCoord(x, y)) ?: continue
                if (cell.terrain == Terrain.Empty && cell.buildingId == null) continue
                cells.add(CellData(x, y, 0, cell.terrain.name, cell.buildingId))
            }
        }

        val buildings = map.buildings.values.map { b ->
            BuildingData(
                id = b.id,
                type = b.type.name,
                subtype = b.subtype?.name,
                cells = b.cells.map { CoordData(it.x, it.y, it.z) },
                capacity = b.capacity,
                occupants = b.currentOccupants.size,
                isFull = b.isFull,
                wage = b.wage,
                rent = b.rent
            )
        }

        val peeps = engine.peeps.values.map { p ->
            val n = p.needs
            PeepData(
                id = p.id,
                name = p.name,
                age = p.age,
                gender = p.gender.name,
                posX = p.position.x,
                posY = p.position.y,
                posZ = p.position.z,
                homeId = p.homeId,
                jobId = p.jobId,
                money = p.money,
                // v1 compat fields
                hunger = n.hunger,
                fatigue = n.sleep,
                shelter = n.shelter,
                social = n.friendship,
                entertainment = n.creativity,
                // v2 Maslow
                maslowNeeds = MaslowNeedsData(
                    hunger = n.hunger,
                    thirst = n.thirst,
                    sleep = n.sleep,
                    warmth = n.warmth,
                    shelter = n.shelter,
                    health = n.health,
                    friendship = n.friendship,
                    family = n.family,
                    community = n.community,
                    recognition = n.recognition,
                    accomplishment = n.accomplishment,
                    creativity = n.creativity,
                    learning = n.learning,
                    purpose = n.purpose
                ),
                brainType = brainTypeName(p.brain),
                schedule = p.schedule.name,
                friendships = p.friendships.toMap(),
                relationships = p.relationships.toMap(),
                vehicle = p.vehicle?.name,
                travelMode = p.travelMode.name,
                parkingSpotX = p.parkingSpot?.x,
                parkingSpotY = p.parkingSpot?.y,
                parkingSpotZ = p.parkingSpot?.z
            )
        }

        val clockData = ClockData(
            day = engine.clock.day,
            hour = engine.clock.hour,
            minute = engine.clock.minute
        )

        val parkedVehicles = map.parkedVehicles.keys.map { CoordData(it.x, it.y, it.z) }

        // Transit data
        val transitData = TransitData(
            stops = engine.transit.stops.values.map { BusStopData(it.id, it.coord.x, it.coord.y, it.name) },
            routes = engine.transit.routes.values.map { BusRouteData(it.id, it.name, it.stops.map { s -> s.id }, it.headwayTicks) },
            buses = engine.transit.buses.values.map { BusData(it.id, it.routeId, it.position.x, it.position.y, it.currentStopIndex, it.movingForward, it.passengers.toList()) },
            stations = engine.transit.stations.values.map { TrainStationData(it.id, it.coord.x, it.coord.y, it.name, it.isSubway) },
            trainRoutes = engine.transit.trainRoutes.values.map { TrainRouteData(it.id, it.name, it.stations.map { s -> s.id }, it.headwayTicks, it.isSubway) },
            trains = engine.transit.trains.values.map { TrainData(it.id, it.routeId, it.position.x, it.position.y, it.currentStationIndex, it.movingForward, it.passengers.toList()) }
        )

        return SaveData(tick = engine.tick, clock = clockData, map = MapData(map.width, map.height, cells, buildings, parkedVehicles), peeps = peeps, transit = transitData)
    }

    fun fromSaveData(data: SaveData): TickEngine {
        val map = WorldMap(data.map.width, data.map.height)

        // Restore cells (before buildings, since addBuilding overwrites terrain)
        for (c in data.map.cells) {
            val terrainName = migrateTerrainName(c.terrain)
            val terrain = Terrain.valueOf(terrainName)
            map.setCell(Cell(CellCoord(c.x, c.y, c.z), terrain, c.buildingId))
        }

        // Restore buildings
        for (b in data.map.buildings) {
            val building = Building(
                id = b.id,
                type = BuildingType.valueOf(b.type),
                subtype = b.subtype?.let { name ->
                    try { BuildingSubtype.valueOf(name) } catch (_: Exception) { null }
                },
                cells = b.cells.map { CellCoord(it.x, it.y, it.z) }.toSet()
            )
            // Register building without overwriting cell terrain (already restored)
            map.buildings[building.id] = building
            if (b.wage > 0) building.wage = b.wage
            if (b.rent > 0) building.rent = b.rent
        }

        val engine = TickEngine(map)
        engine.tick = data.tick
        if (data.clock != null) {
            engine.clock.restore(data.tick, data.clock.day, data.clock.hour, data.clock.minute)
        }

        for (pd in data.peeps) {
            val needs = if (pd.maslowNeeds != null) {
                // v2 format
                val m = pd.maslowNeeds
                MaslowNeeds(
                    hunger = m.hunger,
                    thirst = m.thirst,
                    sleep = m.sleep,
                    warmth = m.warmth,
                    shelter = m.shelter,
                    health = m.health,
                    friendship = m.friendship,
                    family = m.family,
                    community = m.community,
                    recognition = m.recognition,
                    accomplishment = m.accomplishment,
                    creativity = m.creativity,
                    learning = m.learning,
                    purpose = m.purpose
                )
            } else {
                // v1 compat: map old flat fields
                MaslowNeeds(
                    hunger = pd.hunger,
                    sleep = pd.fatigue,
                    shelter = pd.shelter,
                    friendship = pd.social,
                    creativity = pd.entertainment
                )
            }

            val schedule = try { ScheduleType.valueOf(pd.schedule) } catch (_: Exception) { ScheduleType.Worker }
            val vehicle = pd.vehicle?.let {
                try { VehicleType.valueOf(it) } catch (_: Exception) { null }
            }
            val travelMode = try { TravelMode.valueOf(pd.travelMode) } catch (_: Exception) { TravelMode.Walk }
            val parkingSpot = if (pd.parkingSpotX != null && pd.parkingSpotY != null) {
                CellCoord(pd.parkingSpotX, pd.parkingSpotY, pd.parkingSpotZ ?: 0)
            } else null

            val peep = Peep(
                id = pd.id,
                name = pd.name,
                age = pd.age,
                gender = Gender.valueOf(pd.gender),
                position = CellCoord(pd.posX, pd.posY, pd.posZ),
                homeId = pd.homeId,
                jobId = pd.jobId,
                money = pd.money,
                needs = needs,
                brain = brainFromName(pd.brainType),
                friendships = pd.friendships.toMutableMap(),
                relationships = pd.relationships.toMutableMap(),
                schedule = schedule,
                vehicle = vehicle,
                travelMode = travelMode,
                parkingSpot = parkingSpot
            )
            engine.addPeep(peep)
            if (parkingSpot != null) {
                map.parkedVehicles[parkingSpot] = peep.id
            }
        }

        // Restore transit
        val td = data.transit
        if (td != null) {
            val stopsById = mutableMapOf<Int, BusStop>()
            for (sd in td.stops) {
                val stop = BusStop(sd.id, CellCoord(sd.x, sd.y), sd.name)
                stopsById[stop.id] = stop
                engine.transit.addStop(stop)
            }
            for (rd in td.routes) {
                val routeStops = rd.stopIds.mapNotNull { stopsById[it] }
                if (routeStops.size >= 2) {
                    engine.transit.addRoute(BusRoute(rd.id, rd.name, routeStops, rd.headwayTicks))
                }
            }
            for (bd in td.buses) {
                val bus = Bus(bd.id, bd.routeId, CellCoord(bd.x, bd.y), bd.currentStopIndex, bd.passengers.toMutableList(), 0, bd.movingForward)
                engine.transit.buses[bus.id] = bus
            }

            // Restore train data
            val stationsById = mutableMapOf<Int, TrainStation>()
            for (sd in td.stations) {
                val station = TrainStation(sd.id, CellCoord(sd.x, sd.y), sd.name, sd.isSubway)
                stationsById[station.id] = station
                engine.transit.addStation(station)
            }
            for (rd in td.trainRoutes) {
                val routeStations = rd.stationIds.mapNotNull { stationsById[it] }
                if (routeStations.size >= 2) {
                    engine.transit.addTrainRoute(TrainRoute(rd.id, rd.name, routeStations, rd.headwayTicks, rd.isSubway))
                }
            }
            for (td2 in td.trains) {
                val train = Train(td2.id, td2.routeId, CellCoord(td2.x, td2.y), td2.currentStationIndex, td2.passengers.toMutableList(), 0, td2.movingForward)
                engine.transit.trains[train.id] = train
            }
        }

        return engine
    }

    /** Migrate terrain names from older save versions. */
    private fun migrateTerrainName(name: String): String = when (name) {
        "Road" -> "LocalRoad"
        else -> name
    }

    fun brainTypeName(brain: Brain): String = when (brain) {
        is UtilityBrain  -> "Utility"
        is PyramidBrain  -> "Pyramid"
        is WaveBrain     -> "Wave"
        is RandomBrain   -> "Random"
        is IdleBrain     -> "Idle"
        else             -> "Idle"
    }

    fun brainFromName(name: String): Brain = when (name) {
        "Utility" -> UtilityBrain()
        "Pyramid" -> PyramidBrain()
        "Wave"    -> WaveBrain()
        "Random"  -> RandomBrain()
        else      -> IdleBrain()
    }
}
