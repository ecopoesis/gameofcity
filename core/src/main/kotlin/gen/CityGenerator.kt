package gen

import world.*
import kotlin.random.Random

object CityGenerator {

    fun generate(config: CityGenConfig): WorldMap {
        val map = WorldMap(config.width, config.height)
        val noise = SimplexNoise(config.seed)
        val rng = Random(config.seed)

        if (config.organicLevel > 0f) {
            generateOrganic(map, config, noise, rng)
        } else {
            generateGrid(map, config, noise, rng)
        }

        // Fill remaining Empty cells as sidewalk
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

    private fun generateGrid(map: WorldMap, config: CityGenConfig, noise: SimplexNoise, rng: Random) {
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

        // Step 3: Identify blocks
        val blocks = findBlocks(map, config)

        // Step 4: Zone blocks and place buildings
        placeBuildings(map, blocks, noise, rng, config)
    }

    private fun generateOrganic(map: WorldMap, config: CityGenConfig, noise: SimplexNoise, rng: Random) {
        val roadInterval = config.blockSize + 1

        // Start with grid layout
        for (x in 0 until config.width) {
            for (y in 0 until config.height) {
                val isPerimeter = x == 0 || y == 0 || x == config.width - 1 || y == config.height - 1
                val isRoad = isPerimeter || x % roadInterval == 0 || y % roadInterval == 0
                if (isRoad) {
                    map.setCell(Cell(CellCoord(x, y), Terrain.Road))
                }
            }
        }

        // Displace road midpoints based on organic level
        val displacement = (config.organicLevel * config.blockSize / 2f).toInt().coerceAtLeast(1)
        for (x in 0 until config.width) {
            for (y in 0 until config.height) {
                val cell = map.getCell(CellCoord(x, y)) ?: continue
                if (cell.terrain != Terrain.Road) continue
                val isPerimeter = x == 0 || y == 0 || x == config.width - 1 || y == config.height - 1
                if (isPerimeter) continue

                // Randomly displace some road cells
                if (rng.nextFloat() < config.organicLevel * 0.3f) {
                    val dx = rng.nextInt(-displacement, displacement + 1)
                    val dy = rng.nextInt(-displacement, displacement + 1)
                    val nx = (x + dx).coerceIn(1, config.width - 2)
                    val ny = (y + dy).coerceIn(1, config.height - 2)
                    // Add road at displaced position
                    map.setCell(Cell(CellCoord(nx, ny), Terrain.Road))
                }
            }
        }

        // Randomly remove some grid roads and add organic connectors
        if (config.organicLevel > 0.3f) {
            for (x in 1 until config.width - 1) {
                for (y in 1 until config.height - 1) {
                    val cell = map.getCell(CellCoord(x, y)) ?: continue
                    if (cell.terrain == Terrain.Road && rng.nextFloat() < config.organicLevel * 0.15f) {
                        map.setCell(Cell(CellCoord(x, y), Terrain.Empty))
                    }
                }
            }
        }

        // Add curved connector roads using noise-guided paths
        if (config.organicLevel > 0.5f) {
            val numConnectors = (config.organicLevel * 10).toInt()
            repeat(numConnectors) {
                val sx = rng.nextInt(2, config.width - 2)
                val sy = rng.nextInt(2, config.height - 2)
                var cx = sx
                var cy = sy
                repeat(config.blockSize * 3) {
                    val angle = noise.octaveNoise(cx * 0.1, cy * 0.1, 2) * Math.PI * 2
                    cx = (cx + kotlin.math.cos(angle).toInt()).coerceIn(1, config.width - 2)
                    cy = (cy + kotlin.math.sin(angle).toInt()).coerceIn(1, config.height - 2)
                    map.setCell(Cell(CellCoord(cx, cy), Terrain.Road))
                }
            }
        }

        // Sidewalks adjacent to roads
        for (x in 0 until config.width) {
            for (y in 0 until config.height) {
                val cell = map.getCell(CellCoord(x, y)) ?: continue
                if (cell.terrain != Terrain.Empty) continue
                if (hasAdjacentRoad(map, x, y)) {
                    map.setCell(Cell(CellCoord(x, y), Terrain.Sidewalk))
                }
            }
        }

        // Find blocks via flood-fill (works for arbitrary shapes)
        val blocks = floodFillBlocks(map, config)
        placeBuildings(map, blocks, noise, rng, config)
    }

    private fun placeBuildings(map: WorldMap, blocks: List<Block>, noise: SimplexNoise, rng: Random, config: CityGenConfig) {
        var nextBuildingId = 1
        for (block in blocks) {
            val cx = (block.x1 + block.x2) / 2.0
            val cy = (block.y1 + block.y2) / 2.0
            val noiseVal = noise.octaveNoise(cx * 0.08, cy * 0.08, 3)
            val blockArea = (block.x2 - block.x1 + 1) * (block.y2 - block.y1 + 1)

            // Park as building
            if (rng.nextFloat() < config.parkChance) {
                val cells = blockCells(block)
                if (cells.isNotEmpty()) {
                    val building = Building(
                        id = nextBuildingId++,
                        type = BuildingType.Recreation,
                        subtype = BuildingSubtype.Park,
                        cells = cells
                    )
                    map.addBuilding(building)
                }
                continue
            }

            val zoneType = zoneFromNoise(noiseVal, config.urbanizationLevel)
            if (zoneType == null) {
                // Null zone -> park
                val cells = blockCells(block)
                if (cells.isNotEmpty()) {
                    val building = Building(
                        id = nextBuildingId++,
                        type = BuildingType.Recreation,
                        subtype = BuildingSubtype.Park,
                        cells = cells
                    )
                    map.addBuilding(building)
                }
                continue
            }

            val cells = blockCells(block)
            if (cells.isNotEmpty()) {
                val subtype = assignSubtype(zoneType, blockArea, rng)
                val building = Building(
                    id = nextBuildingId++,
                    type = zoneType,
                    subtype = subtype,
                    cells = cells
                )
                map.addBuilding(building)
            }
        }
    }

    private fun assignSubtype(type: BuildingType, blockArea: Int, rng: Random): BuildingSubtype {
        val subtypes = BuildingSubtype.entries.filter { it.category == type }
        if (subtypes.isEmpty()) return BuildingSubtype.House // fallback

        // Size-based selection
        val sizeFiltered = when {
            blockArea > 12 -> subtypes.filter { it in LARGE_SUBTYPES } .ifEmpty { subtypes }
            blockArea in 5..12 -> subtypes.filter { it in MEDIUM_SUBTYPES }.ifEmpty { subtypes }
            else -> subtypes.filter { it in SMALL_SUBTYPES }.ifEmpty { subtypes }
        }

        return sizeFiltered[rng.nextInt(sizeFiltered.size)]
    }

    private fun blockCells(block: Block): Set<CellCoord> {
        val cells = mutableSetOf<CellCoord>()
        for (bx in block.x1..block.x2) {
            for (by in block.y1..block.y2) {
                cells.add(CellCoord(bx, by))
            }
        }
        return cells
    }

    private fun hasAdjacentRoad(map: WorldMap, x: Int, y: Int): Boolean {
        val neighbors = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)
        return neighbors.any { (nx, ny) ->
            val cell = map.getCell(CellCoord(nx, ny))
            cell != null && cell.terrain == Terrain.Road
        }
    }

    data class Block(val x1: Int, val y1: Int, val x2: Int, val y2: Int)

    private fun findBlocks(map: WorldMap, config: CityGenConfig): List<Block> {
        val visited = mutableSetOf<Long>()
        val blocks = mutableListOf<Block>()

        fun key(x: Int, y: Int) = x.toLong() * config.height + y

        for (x in 0 until config.width) {
            for (y in 0 until config.height) {
                if (key(x, y) in visited) continue
                val cell = map.getCell(CellCoord(x, y)) ?: continue
                if (cell.terrain != Terrain.Empty) continue

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

    private fun floodFillBlocks(map: WorldMap, config: CityGenConfig): List<Block> {
        val visited = mutableSetOf<Long>()
        val blocks = mutableListOf<Block>()

        fun key(x: Int, y: Int) = x.toLong() * config.height + y

        for (x in 0 until config.width) {
            for (y in 0 until config.height) {
                if (key(x, y) in visited) continue
                val cell = map.getCell(CellCoord(x, y)) ?: continue
                if (cell.terrain != Terrain.Empty) { visited.add(key(x, y)); continue }

                // Flood-fill to find contiguous empty region
                val region = mutableSetOf<Pair<Int, Int>>()
                val queue = ArrayDeque<Pair<Int, Int>>()
                queue.add(x to y)
                visited.add(key(x, y))
                while (queue.isNotEmpty()) {
                    val (cx, cy) = queue.removeFirst()
                    region.add(cx to cy)
                    for ((dx, dy) in listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)) {
                        val nx = cx + dx
                        val ny = cy + dy
                        if (nx < 0 || ny < 0 || nx >= config.width || ny >= config.height) continue
                        if (key(nx, ny) in visited) continue
                        val nc = map.getCell(CellCoord(nx, ny)) ?: continue
                        if (nc.terrain != Terrain.Empty) continue
                        visited.add(key(nx, ny))
                        queue.add(nx to ny)
                    }
                }

                if (region.size >= 2) {
                    // Compute bounding box
                    val minX = region.minOf { it.first }
                    val maxX = region.maxOf { it.first }
                    val minY = region.minOf { it.second }
                    val maxY = region.maxOf { it.second }
                    blocks.add(Block(minX, minY, maxX, maxY))
                }
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
            noise < 0.7 + shift  -> BuildingType.Civic
            else                 -> null // park/recreation
        }
    }

    private val LARGE_SUBTYPES = setOf(
        BuildingSubtype.Apartment, BuildingSubtype.Office, BuildingSubtype.Factory,
        BuildingSubtype.Stadium, BuildingSubtype.Luxury, BuildingSubtype.Warehouse
    )
    private val MEDIUM_SUBTYPES = setOf(
        BuildingSubtype.House, BuildingSubtype.Restaurant, BuildingSubtype.School,
        BuildingSubtype.Theater, BuildingSubtype.CommunityCenter, BuildingSubtype.Hospital,
        BuildingSubtype.Museum, BuildingSubtype.Gym, BuildingSubtype.PoliceStation,
        BuildingSubtype.FireStation
    )
    private val SMALL_SUBTYPES = setOf(
        BuildingSubtype.Cafe, BuildingSubtype.Shop, BuildingSubtype.Workshop,
        BuildingSubtype.Library, BuildingSubtype.GroceryStore
    )
}
