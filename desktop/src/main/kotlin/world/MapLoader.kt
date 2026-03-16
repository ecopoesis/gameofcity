package world

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.JsonReader

object MapLoader {

    fun loadFromJson(path: String): WorldMap {
        val text = Gdx.files.internal(path).readString()
        val root = JsonReader().parse(text)

        val width = root.getInt("width")
        val height = root.getInt("height")
        val map = WorldMap(width, height)

        // Set terrain
        val cells = root.get("cells")
        cells?.forEach { cell ->
            val x = cell.getInt("x")
            val y = cell.getInt("y")
            val terrainName = migrateTerrainName(cell.getString("terrain"))
            val terrain = Terrain.valueOf(terrainName)
            map.setCell(Cell(CellCoord(x, y), terrain))
        }

        // Load buildings
        val buildings = root.get("buildings")
        buildings?.forEach { b ->
            val id = b.getInt("id")
            val type = BuildingType.valueOf(b.getString("type"))
            val coords = b.get("cells").map { c ->
                CellCoord(c.getInt("x"), c.getInt("y"))
            }.toSet()
            map.addBuilding(Building(id = id, type = type, cells = coords))
        }

        return map
    }

    private fun migrateTerrainName(name: String): String = when (name) {
        "Road" -> "LocalRoad"
        else -> name
    }
}
