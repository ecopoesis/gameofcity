package save

import peep.*
import tick.TickEngine
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
                wage = b.wage
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
                relationships = p.relationships.toMap()
            )
        }

        val clockData = ClockData(
            day = engine.clock.day,
            hour = engine.clock.hour,
            minute = engine.clock.minute
        )

        return SaveData(tick = engine.tick, clock = clockData, map = MapData(map.width, map.height, cells, buildings), peeps = peeps)
    }

    fun fromSaveData(data: SaveData): TickEngine {
        val map = WorldMap(data.map.width, data.map.height)

        // Restore cells (before buildings, since addBuilding overwrites terrain)
        for (c in data.map.cells) {
            val terrain = Terrain.valueOf(c.terrain)
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
                schedule = schedule
            )
            engine.addPeep(peep)
        }

        return engine
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
