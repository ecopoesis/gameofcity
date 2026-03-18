package gen

import transit.BusRoute
import transit.BusStop
import transit.TransitSystem
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

    // --- Road corridor model ---

    private data class Corridor(
        val start: Int,     // first cell (sidewalk)
        val roadStart: Int, // first road cell
        val roadEnd: Int,   // last road cell
        val end: Int,       // last cell (sidewalk)
        val terrain: Terrain
    )

    private fun roadWidth(terrain: Terrain): Int = when (terrain) {
        Terrain.ArterialRoad -> 4
        Terrain.CollectorRoad -> 3
        Terrain.LocalRoad -> 2
        else -> 2
    }

    private fun roadTypeForIndex(index: Int, maxIndex: Int): Terrain = when {
        index == 0 || index == maxIndex -> Terrain.CollectorRoad
        index % 4 == 0 -> Terrain.ArterialRoad
        index % 2 == 0 -> Terrain.CollectorRoad
        else -> Terrain.LocalRoad
    }

    private fun roadPriority(terrain: Terrain): Int = when (terrain) {
        Terrain.ArterialRoad -> 3
        Terrain.CollectorRoad -> 2
        Terrain.LocalRoad -> 1
        else -> 0
    }

    private fun computeCorridors(mapSize: Int, blockSize: Int): List<Corridor> {
        val corridors = mutableListOf<Corridor>()
        var pos = 0
        var index = 0

        // First pass: compute corridor count to know the max index
        val tempPositions = mutableListOf<Int>()
        var tempPos = 0
        while (tempPos + 4 <= mapSize) {
            tempPositions.add(tempPos)
            val terrain = roadTypeForIndex(tempPositions.size - 1, Int.MAX_VALUE)
            val w = roadWidth(terrain)
            tempPos += w + 2 + blockSize // sidewalk + road + sidewalk + block
        }
        val maxIndex = tempPositions.size // closing corridor will be this index

        // Second pass: build corridors
        while (pos + 4 <= mapSize) {
            val terrain = roadTypeForIndex(index, maxIndex)
            val w = roadWidth(terrain)
            val corridorEnd = pos + w + 1

            if (corridorEnd >= mapSize) break

            corridors.add(Corridor(pos, pos + 1, pos + w, corridorEnd, terrain))
            pos = corridorEnd + 1 + blockSize
            index++
        }

        // Close with perimeter corridor if there's room
        if (corridors.isNotEmpty()) {
            val lastEnd = corridors.last().end
            val closingTerrain = Terrain.CollectorRoad
            val closingWidth = roadWidth(closingTerrain)
            val closingTotal = closingWidth + 2
            val closeStart = mapSize - closingTotal

            if (closeStart > lastEnd) {
                corridors.add(Corridor(closeStart, closeStart + 1, closeStart + closingWidth, mapSize - 1, closingTerrain))
            }
        }

        return corridors
    }

    // --- Grid generator ---

    private fun generateGrid(map: WorldMap, config: CityGenConfig, noise: SimplexNoise, rng: Random) {
        val corridorsX = computeCorridors(config.width, config.blockSize)
        val corridorsY = computeCorridors(config.height, config.blockSize)

        // Lay horizontal corridors (Y corridors spanning full width)
        for (corridor in corridorsY) {
            for (x in 0 until config.width) {
                map.setCell(Cell(CellCoord(x, corridor.start), Terrain.Sidewalk))
                map.setCell(Cell(CellCoord(x, corridor.end), Terrain.Sidewalk))
                for (ry in corridor.roadStart..corridor.roadEnd) {
                    map.setCell(Cell(CellCoord(x, ry), corridor.terrain))
                }
            }
        }

        // Lay vertical corridors (X corridors spanning full height)
        for (corridor in corridorsX) {
            for (y in 0 until config.height) {
                // Sidewalks: don't overwrite existing road cells
                val startCell = map.getCell(CellCoord(corridor.start, y))
                if (startCell == null || !startCell.terrain.isRoad) {
                    map.setCell(Cell(CellCoord(corridor.start, y), Terrain.Sidewalk))
                }
                val endCell = map.getCell(CellCoord(corridor.end, y))
                if (endCell == null || !endCell.terrain.isRoad) {
                    map.setCell(Cell(CellCoord(corridor.end, y), Terrain.Sidewalk))
                }
                // Road cells: at intersections, use higher-priority road type
                for (rx in corridor.roadStart..corridor.roadEnd) {
                    val existing = map.getCell(CellCoord(rx, y))
                    val newTerrain = if (existing != null && existing.terrain.isRoad) {
                        if (roadPriority(existing.terrain) >= roadPriority(corridor.terrain))
                            existing.terrain else corridor.terrain
                    } else {
                        corridor.terrain
                    }
                    map.setCell(Cell(CellCoord(rx, y), newTerrain))
                }
            }
        }

        // Place on-street parking alongside Collector/Local roads
        placeOnStreetParking(map, config, rng)

        // Find blocks (empty rectangles between corridors)
        val blocks = findBlocks(map, config)

        // Zone blocks and place buildings
        placeBuildings(map, blocks, noise, rng, config)
    }

    // --- Organic generator ---

    private fun generateOrganic(map: WorldMap, config: CityGenConfig, noise: SimplexNoise, rng: Random) {
        val roadInterval = config.blockSize + 3 // blockSize + local road corridor (2 road + 1 margin)

        // Start with grid of 2-cell-wide local roads
        for (x in 0 until config.width) {
            for (y in 0 until config.height) {
                val isPerimeter = x <= 1 || y <= 1 || x >= config.width - 2 || y >= config.height - 2
                val isRoadX = x % roadInterval == 0 || x % roadInterval == 1
                val isRoadY = y % roadInterval == 0 || y % roadInterval == 1
                if (isPerimeter || isRoadX || isRoadY) {
                    map.setCell(Cell(CellCoord(x, y), Terrain.LocalRoad))
                }
            }
        }

        // Displace road midpoints based on organic level
        val displacement = (config.organicLevel * config.blockSize / 2f).toInt().coerceAtLeast(1)
        for (x in 2 until config.width - 2) {
            for (y in 2 until config.height - 2) {
                val cell = map.getCell(CellCoord(x, y)) ?: continue
                if (!cell.terrain.isRoad) continue

                if (rng.nextFloat() < config.organicLevel * 0.3f) {
                    val dx = rng.nextInt(-displacement, displacement + 1)
                    val dy = rng.nextInt(-displacement, displacement + 1)
                    val nx = (x + dx).coerceIn(2, config.width - 3)
                    val ny = (y + dy).coerceIn(2, config.height - 3)
                    map.setCell(Cell(CellCoord(nx, ny), Terrain.LocalRoad))
                }
            }
        }

        // Randomly remove some grid roads
        if (config.organicLevel > 0.3f) {
            for (x in 2 until config.width - 2) {
                for (y in 2 until config.height - 2) {
                    val cell = map.getCell(CellCoord(x, y)) ?: continue
                    if (cell.terrain.isRoad && rng.nextFloat() < config.organicLevel * 0.15f) {
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
                    cx = (cx + kotlin.math.cos(angle).toInt()).coerceIn(2, config.width - 3)
                    cy = (cy + kotlin.math.sin(angle).toInt()).coerceIn(2, config.height - 3)
                    map.setCell(Cell(CellCoord(cx, cy), Terrain.LocalRoad))
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

        val blocks = floodFillBlocks(map, config)
        placeBuildings(map, blocks, noise, rng, config)
    }

    // --- Building placement ---

    private fun placeBuildings(map: WorldMap, blocks: List<Block>, noise: SimplexNoise, rng: Random, config: CityGenConfig) {
        var nextBuildingId = 1
        for (block in blocks) {
            val cx = (block.x1 + block.x2) / 2.0
            val cy = (block.y1 + block.y2) / 2.0
            val noiseVal = noise.octaveNoise(cx * 0.08, cy * 0.08, 3)
            val blockWidth = block.x2 - block.x1 + 1
            val blockHeight = block.y2 - block.y1 + 1
            val blockArea = blockWidth * blockHeight

            // Park chance
            if (rng.nextFloat() < config.parkChance) {
                val cells = blockCells(block)
                if (cells.isNotEmpty()) {
                    map.addBuilding(Building(
                        id = nextBuildingId++,
                        type = BuildingType.Recreation,
                        subtype = BuildingSubtype.Park,
                        cells = cells
                    ))
                }
                continue
            }

            val zoneType = zoneFromNoise(noiseVal, config.urbanizationLevel)
            if (zoneType == null) {
                // Null zone -> park
                val cells = blockCells(block)
                if (cells.isNotEmpty()) {
                    map.addBuilding(Building(
                        id = nextBuildingId++,
                        type = BuildingType.Recreation,
                        subtype = BuildingSubtype.Park,
                        cells = cells
                    ))
                }
                continue
            }

            // For large blocks, subdivide into individual buildings
            if (blockArea > 40) {
                nextBuildingId = subdivideBuildingsInBlock(map, block, zoneType, rng, nextBuildingId)
            } else {
                // Small block: single building fills it
                val cells = blockCells(block)
                if (cells.isNotEmpty()) {
                    val subtype = assignSubtype(zoneType, blockArea, rng)
                    map.addBuilding(Building(
                        id = nextBuildingId++,
                        type = zoneType,
                        subtype = subtype,
                        cells = cells
                    ))
                }
            }
        }
    }

    private fun subdivideBuildingsInBlock(
        map: WorldMap,
        block: Block,
        zoneType: BuildingType,
        rng: Random,
        startId: Int
    ): Int {
        var nextId = startId
        val (footW, footH) = buildingFootprint(zoneType, rng)
        val gap = 1 // 1-cell gap between buildings (becomes sidewalk)

        var bx = block.x1
        while (bx <= block.x2) {
            val w = footW.coerceAtMost(block.x2 - bx + 1)
            if (w < 3) { bx += w + gap; continue }

            var by = block.y1
            while (by <= block.y2) {
                val h = footH.coerceAtMost(block.y2 - by + 1)
                if (h < 3) { by += h + gap; continue }

                val cells = mutableSetOf<CellCoord>()
                for (x in bx until bx + w) {
                    for (y in by until by + h) {
                        cells.add(CellCoord(x, y))
                    }
                }

                val subtype = assignSubtype(zoneType, cells.size, rng)
                map.addBuilding(Building(
                    id = nextId++,
                    type = zoneType,
                    subtype = subtype,
                    cells = cells
                ))

                by += h + gap
            }
            bx += w + gap
        }

        return nextId
    }

    private fun buildingFootprint(type: BuildingType, rng: Random): Pair<Int, Int> = when (type) {
        BuildingType.Residential -> when (rng.nextInt(3)) {
            0 -> 5 to 5     // house
            1 -> 5 to 7     // townhouse
            else -> 8 to 10 // apartment
        }
        BuildingType.Commercial -> when (rng.nextInt(3)) {
            0 -> 4 to 4     // cafe/shop
            1 -> 6 to 6     // restaurant/grocery
            else -> 10 to 10 // office
        }
        BuildingType.Industrial -> when (rng.nextInt(2)) {
            0 -> 8 to 12    // factory
            else -> 10 to 8 // warehouse
        }
        BuildingType.Civic -> when (rng.nextInt(2)) {
            0 -> 6 to 8     // school/library
            else -> 8 to 10 // hospital
        }
        BuildingType.Recreation -> 0 to 0 // fills entire block
        BuildingType.Entertainment -> 6 to 6
    }

    private fun assignSubtype(type: BuildingType, blockArea: Int, rng: Random): BuildingSubtype {
        val subtypes = BuildingSubtype.entries.filter { it.category == type }
        if (subtypes.isEmpty()) return BuildingSubtype.House

        val sizeFiltered = when {
            blockArea > 60 -> subtypes.filter { it in LARGE_SUBTYPES }.ifEmpty { subtypes }
            blockArea in 20..60 -> subtypes.filter { it in MEDIUM_SUBTYPES }.ifEmpty { subtypes }
            else -> subtypes.filter { it in SMALL_SUBTYPES }.ifEmpty { subtypes }
        }

        return sizeFiltered[rng.nextInt(sizeFiltered.size)]
    }

    // --- Block finding ---

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
            cell != null && cell.terrain.isRoad
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

    // --- On-street parking ---

    private fun placeOnStreetParking(map: WorldMap, config: CityGenConfig, rng: Random) {
        // Place Parking terrain cells alongside Collector/Local roads, spaced periodically
        for (x in 0 until config.width) {
            for (y in 0 until config.height) {
                val cell = map.getCell(CellCoord(x, y)) ?: continue
                if (cell.terrain != Terrain.CollectorRoad && cell.terrain != Terrain.LocalRoad) continue

                // Every ~6 cells along a road, check for adjacent sidewalk to convert to parking
                if ((x + y) % 6 != 0) continue
                if (rng.nextFloat() > 0.4f) continue // 40% chance

                // Find an adjacent sidewalk cell to convert to parking
                val candidates = listOf(
                    CellCoord(x - 1, y), CellCoord(x + 1, y),
                    CellCoord(x, y - 1), CellCoord(x, y + 1)
                )
                val sidewalk = candidates.firstOrNull { c ->
                    val adj = map.getCell(c)
                    adj != null && adj.terrain == Terrain.Sidewalk && adj.buildingId == null
                }
                if (sidewalk != null) {
                    map.setCell(Cell(sidewalk, Terrain.Parking))
                }
            }
        }
    }

    // --- Zoning ---

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
        BuildingSubtype.Stadium, BuildingSubtype.Luxury, BuildingSubtype.Warehouse,
        BuildingSubtype.ParkingGarage
    )
    private val MEDIUM_SUBTYPES = setOf(
        BuildingSubtype.House, BuildingSubtype.Restaurant, BuildingSubtype.School,
        BuildingSubtype.Theater, BuildingSubtype.CommunityCenter, BuildingSubtype.Hospital,
        BuildingSubtype.Museum, BuildingSubtype.Gym, BuildingSubtype.PoliceStation,
        BuildingSubtype.FireStation, BuildingSubtype.ParkingLot
    )
    private val SMALL_SUBTYPES = setOf(
        BuildingSubtype.Cafe, BuildingSubtype.Shop, BuildingSubtype.Workshop,
        BuildingSubtype.Library, BuildingSubtype.GroceryStore
    )

    /** Generate bus stops and routes for an existing map. */
    fun generateTransit(map: WorldMap, transit: TransitSystem, rng: Random = Random) {
        val stopInterval = 50 // place stops every ~50 cells along arterial/collector roads
        val stops = mutableListOf<BusStop>()
        var nextStopId = 0

        // Find stop locations along major roads
        for (x in 0 until map.width step stopInterval) {
            for (y in 0 until map.height step stopInterval) {
                // Search a small area for an arterial/collector road cell with adjacent sidewalk
                val stopCoord = findBusStopLocation(map, x, y)
                if (stopCoord != null) {
                    val stop = BusStop(nextStopId++, stopCoord, "Stop ${stops.size + 1}")
                    stops.add(stop)
                    transit.addStop(stop)
                    // Place Platform terrain at the stop
                    map.setCell(Cell(stopCoord, Terrain.Platform))
                }
            }
        }

        if (stops.size < 2) return

        // Generate 2-4 bus routes connecting different zones
        val numRoutes = (stops.size / 4).coerceIn(2, 4)
        val usedStops = mutableSetOf<Int>()

        for (routeId in 0 until numRoutes) {
            val routeStops = pickRouteStops(stops, usedStops, rng)
            if (routeStops.size < 2) continue

            routeStops.forEach { usedStops.add(it.id) }
            val route = BusRoute(
                id = routeId,
                name = "Route ${routeId + 1}",
                stops = routeStops,
                headwayTicks = 300 // every 5 sim-minutes
            )
            transit.addRoute(route)
        }
    }

    private fun findBusStopLocation(map: WorldMap, centerX: Int, centerY: Int): CellCoord? {
        // Search a small radius for an arterial/collector road cell
        for (dx in -10..10) {
            for (dy in -10..10) {
                val x = centerX + dx
                val y = centerY + dy
                val cell = map.getCell(CellCoord(x, y)) ?: continue
                if (cell.terrain != Terrain.ArterialRoad && cell.terrain != Terrain.CollectorRoad) continue
                if (cell.buildingId != null) continue

                // Find an adjacent sidewalk cell for the platform
                val candidates = listOf(
                    CellCoord(x - 1, y), CellCoord(x + 1, y),
                    CellCoord(x, y - 1), CellCoord(x, y + 1)
                )
                val sidewalk = candidates.firstOrNull { c ->
                    val adj = map.getCell(c)
                    adj != null && adj.terrain == Terrain.Sidewalk && adj.buildingId == null
                }
                if (sidewalk != null) return sidewalk
            }
        }
        return null
    }

    private fun pickRouteStops(allStops: List<BusStop>, used: Set<Int>, rng: Random): List<BusStop> {
        // Pick a starting stop (prefer unused)
        val available = allStops.filter { it.id !in used }
        val start = if (available.isNotEmpty()) available[rng.nextInt(available.size)] else allStops[rng.nextInt(allStops.size)]

        // Build route by picking nearest unvisited stops
        val route = mutableListOf(start)
        val visited = mutableSetOf(start.id)
        val maxStops = (allStops.size / 2).coerceIn(3, 8)

        repeat(maxStops - 1) {
            val last = route.last()
            val next = allStops
                .filter { it.id !in visited }
                .minByOrNull { it.coord.distanceTo(last.coord) }
            if (next != null) {
                route.add(next)
                visited.add(next.id)
            }
        }

        return route
    }
}
