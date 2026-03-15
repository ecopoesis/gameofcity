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
                cells = b.cells.map { CoordData(it.x, it.y, it.z) }
            )
        }

        val peeps = engine.peeps.values.map { p ->
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
                hunger = p.needs.hunger,
                fatigue = p.needs.fatigue,
                shelter = p.needs.shelter,
                social = p.needs.social,
                entertainment = p.needs.entertainment,
                brainType = brainTypeName(p.brain),
                friendships = p.friendships.toMap(),
                relationships = p.relationships.toMap()
            )
        }

        return SaveData(tick = engine.tick, map = MapData(map.width, map.height, cells, buildings), peeps = peeps)
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
                cells = b.cells.map { CellCoord(it.x, it.y, it.z) }.toSet()
            )
            // Register building without overwriting cell terrain (already restored)
            map.buildings[building.id] = building
        }

        val engine = TickEngine(map)
        engine.tick = data.tick

        for (pd in data.peeps) {
            val peep = Peep(
                id = pd.id,
                name = pd.name,
                age = pd.age,
                gender = Gender.valueOf(pd.gender),
                position = CellCoord(pd.posX, pd.posY, pd.posZ),
                homeId = pd.homeId,
                jobId = pd.jobId,
                money = pd.money,
                needs = Needs(
                    hunger = pd.hunger,
                    fatigue = pd.fatigue,
                    shelter = pd.shelter,
                    social = pd.social,
                    entertainment = pd.entertainment
                ),
                brain = brainFromName(pd.brainType),
                friendships = pd.friendships.toMutableMap(),
                relationships = pd.relationships.toMutableMap()
            )
            engine.addPeep(peep)
        }

        return engine
    }

    private fun brainTypeName(brain: Brain): String = when (brain) {
        is UtilityBrain -> "Utility"
        is RandomBrain  -> "Random"
        is IdleBrain    -> "Idle"
        else            -> "Idle"
    }

    private fun brainFromName(name: String): Brain = when (name) {
        "Utility" -> UtilityBrain()
        "Random"  -> RandomBrain()
        else      -> IdleBrain()
    }
}
