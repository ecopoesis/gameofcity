package gen

import world.*
import kotlin.random.Random

object CityGenerator {

    fun generate(config: CityGenConfig): WorldMap {
        val map = WorldMap(config.width, config.height)
        val noise = SimplexNoise(config.seed)
        val rng = Random(config.seed)

        // Step 1: Lay road grid
        val roadInterval = config.blockSize + 1
        for (x in 0 until config.width) {
            for (y in 0 until config.height) {
                val isPerimeter = x == 0 || y == 0 || x == config.width - 1 || y == config.height - 1
                val isRoad = isPerimeter || x % roadInterval == 0 || y % roadInterval == 0
                if (isRoad) {
                    map.setCell(Cell(CellCoord(x, y), Terrain.Road))
                }
            }
        }

        // Step 2: Sidewalks adjacent to roads
        for (x in 0 until config.width) {
            for (y in 0 until config.height) {
                val cell = map.getCell(CellCoord(x, y)) ?: continue
                if (cell.terrain != Terrain.Empty) continue
                if (hasAdjacentRoad(map, x, y)) {
                    map.setCell(Cell(CellCoord(x, y), Terrain.Sidewalk))
                }
            }
        }

        // Step 3: Identify blocks (rectangular interiors between sidewalks)
        val blocks = findBlocks(map, config)

        // Step 4: Zone blocks using noise and place buildings/parks
        var nextBuildingId = 1
        for (block in blocks) {
            val cx = (block.x1 + block.x2) / 2.0
            val cy = (block.y1 + block.y2) / 2.0
            val noiseVal = noise.octaveNoise(cx * 0.08, cy * 0.08, 3)

            // Park override by random chance
            if (rng.nextFloat() < config.parkChance) {
                fillBlockTerrain(map, block, Terrain.Park)
                continue
            }

            val zoneType = zoneFromNoise(noiseVal, config.urbanizationLevel)
            if (zoneType == null) {
                fillBlockTerrain(map, block, Terrain.Park)
                continue
            }

            // Place building
            val cells = mutableSetOf<CellCoord>()
            for (bx in block.x1..block.x2) {
                for (by in block.y1..block.y2) {
                    cells.add(CellCoord(bx, by))
                }
            }
            if (cells.isNotEmpty()) {
                val building = Building(id = nextBuildingId++, type = zoneType, cells = cells)
                map.addBuilding(building)
            }
        }

        // Step 5: Fill remaining Empty cells as sidewalk
        for (x in 0 until config.width) {
            for (y in 0 until config.height) {
                val cell = map.getCell(CellCoord(x, y)) ?: continue
                if (cell.terrain == Terrain.Empty) {
                    map.setCell(Cell(CellCoord(x, y), Terrain.Sidewalk))
                }
            }
        }

        return map
    }

    private fun hasAdjacentRoad(map: WorldMap, x: Int, y: Int): Boolean {
        val neighbors = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)
        return neighbors.any { (nx, ny) ->
            val cell = map.getCell(CellCoord(nx, ny))
            cell != null && cell.terrain == Terrain.Road
        }
    }

    private data class Block(val x1: Int, val y1: Int, val x2: Int, val y2: Int)

    private fun findBlocks(map: WorldMap, config: CityGenConfig): List<Block> {
        val visited = mutableSetOf<Long>()
        val blocks = mutableListOf<Block>()

        fun key(x: Int, y: Int) = x.toLong() * config.height + y

        for (x in 0 until config.width) {
            for (y in 0 until config.height) {
                if (key(x, y) in visited) continue
                val cell = map.getCell(CellCoord(x, y)) ?: continue
                if (cell.terrain != Terrain.Empty) continue

                // Flood-fill to find rectangular extent
                var x2 = x
                while (x2 + 1 < config.width) {
                    val next = map.getCell(CellCoord(x2 + 1, y))
                    if (next == null || next.terrain != Terrain.Empty) break
                    x2++
                }

                var y2 = y
                outer@ while (y2 + 1 < config.height) {
                    for (bx in x..x2) {
                        val next = map.getCell(CellCoord(bx, y2 + 1))
                        if (next == null || next.terrain != Terrain.Empty) break@outer
                    }
                    y2++
                }

                if (x2 > x && y2 > y) {
                    blocks.add(Block(x, y, x2, y2))
                }
                for (bx in x..x2) for (by in y..y2) visited.add(key(bx, by))
            }
        }
        return blocks
    }

    private fun zoneFromNoise(noise: Double, urbanization: Float): BuildingType? {
        val shift = (urbanization - 0.5) * 0.3
        return when {
            noise < -0.3 + shift -> BuildingType.Industrial
            noise < 0.0 + shift  -> BuildingType.Commercial
            noise < 0.5 + shift  -> BuildingType.Residential
            noise < 0.7 + shift  -> BuildingType.Entertainment
            else                 -> null // park
        }
    }

    private fun fillBlockTerrain(map: WorldMap, block: Block, terrain: Terrain) {
        for (x in block.x1..block.x2) {
            for (y in block.y1..block.y2) {
                map.setCell(Cell(CellCoord(x, y), terrain))
            }
        }
    }
}
