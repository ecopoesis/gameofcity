package gen

import peep.Gender
import peep.Peep
import peep.ScheduleType
import peep.UtilityBrain
import tick.TickEngine
import world.BuildingSubtype
import world.BuildingType
import world.CellCoord
import world.Terrain
import world.VehicleType

object PeepSpawner {

    private val NAMES = listOf(
        "Alex", "Blake", "Casey", "Dana", "Ellis",
        "Finley", "Gray", "Harper", "Indigo", "Jules",
        "Kai", "Lane", "Morgan", "Nova", "Oakley",
        "Parker", "Quinn", "River", "Sage", "Taylor"
    )

    fun spawn(engine: TickEngine, count: Int) {
        val residentials = engine.map.buildingsOfType(BuildingType.Residential)
        val workplaces = engine.map.buildings.values
            .filter { it.type == BuildingType.Commercial || it.type == BuildingType.Industrial }
            .toList()

        repeat(count) { i ->
            val home = residentials.getOrNull(i % residentials.size.coerceAtLeast(1))
            val job = workplaces.getOrNull(i % workplaces.size.coerceAtLeast(1))
            val startPos = home?.cells?.first() ?: CellCoord(1, 1)

            // Assign schedule based on job subtype
            val sched = when (job?.subtype) {
                BuildingSubtype.Factory, BuildingSubtype.Warehouse -> {
                    if (i % 4 == 0) ScheduleType.Nightshift else ScheduleType.Worker
                }
                null -> ScheduleType.Worker
                else -> ScheduleType.Worker
            }

            // Vary starting money: mix of wealthy, middle, and poor
            val money = when (i % 5) {
                0 -> 400f    // wealthy — can afford car
                1, 2 -> 200f // middle — can afford bike
                else -> 50f  // lower income — walks
            }

            // Vehicle ownership based on money
            val vehicle = assignVehicle(money)

            // Find initial parking spot for car owners
            val parkingSpot = if (vehicle == VehicleType.Car) {
                findInitialParking(startPos, engine)
            } else null

            val peep = Peep(
                id = i,
                name = "${NAMES[i % NAMES.size]} ${i / NAMES.size + 1}".trimEnd('1').trimEnd(),
                age = 18 + (i * 7 % 50),
                gender = Gender.entries[i % Gender.entries.size],
                position = startPos,
                homeId = home?.id,
                jobId = job?.id,
                brain = UtilityBrain(),
                schedule = sched,
                money = money,
                vehicle = vehicle,
                parkingSpot = parkingSpot
            )

            // Register parked vehicle
            if (parkingSpot != null) {
                engine.map.parkedVehicles[parkingSpot] = peep.id
            }

            engine.addPeep(peep)
        }
    }

    /** Assign vehicle type based on wealth. */
    fun assignVehicle(money: Float): VehicleType? = when {
        money > 300f -> VehicleType.Car
        money > 100f -> VehicleType.Bike
        else -> null
    }

    /** Find a nearby Parking terrain cell for initial car placement. */
    fun findInitialParking(near: CellCoord, engine: TickEngine): CellCoord? {
        val map = engine.map
        var best: CellCoord? = null
        var bestDist = Int.MAX_VALUE
        for (dx in -20..20) {
            for (dy in -20..20) {
                val c = CellCoord(near.x + dx, near.y + dy)
                val cell = map.getCell(c) ?: continue
                if (cell.terrain == Terrain.Parking && c !in map.parkedVehicles) {
                    val dist = c.distanceTo(near)
                    if (dist < bestDist) {
                        bestDist = dist
                        best = c
                    }
                }
            }
        }
        return best
    }
}
