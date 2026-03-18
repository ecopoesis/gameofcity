import peep.Action
import tick.TickEngine
import world.BuildingId
import world.BuildingType

object SimLogger {

    private const val SAMPLE_PEEPS = 10

    fun log(engine: TickEngine) {
        val tick = engine.tick
        val day = engine.clock.day
        val h = engine.clock.hour
        val m = engine.clock.minute

        val peeps = engine.peeps.values.toList()
        val map = engine.map

        // Aggregate action counts
        var working = 0; var routing = 0; var home = 0; var eating = 0; var idle = 0
        var totalHunger = 0f; var maxHunger = 0f
        var totalSleep = 0f; var maxSleep = 0f

        peeps.forEach { p ->
            when (p.lastAction) {
                is Action.Work     -> working++
                is Action.MoveTo   -> routing++
                is Action.Sleep    -> home++
                is Action.Eat      -> eating++
                else               -> idle++
            }
            totalHunger += p.needs.hunger;  if (p.needs.hunger > maxHunger) maxHunger = p.needs.hunger
            totalSleep  += p.needs.sleep;   if (p.needs.sleep  > maxSleep)  maxSleep  = p.needs.sleep
        }

        val n = peeps.size.coerceAtLeast(1)
        val avgH = totalHunger / n
        val avgS = totalSleep / n

        // Building occupancy from peep positions
        val occupancy = mutableMapOf<BuildingId, Int>()
        peeps.forEach { p ->
            val bldgId = map.getCell(p.position)?.buildingId
            if (bldgId != null) occupancy[bldgId] = (occupancy[bldgId] ?: 0) + 1
        }

        println()
        println("=== TICK $tick | Day $day ${h.toString().padStart(2,'0')}:${m.toString().padStart(2,'0')} ===")
        println("Population: ${peeps.size} | Working: $working | Routing: $routing | Home: $home | Eating: $eating | Idle: $idle")
        println("Needs:  hunger avg=${fmtN(avgH)} max=${fmtN(maxHunger)}  sleep avg=${fmtN(avgS)} max=${fmtN(maxSleep)}")

        println()
        println("PEEPS (first $SAMPLE_PEEPS):")
        peeps.take(SAMPLE_PEEPS).forEach { p ->
            val pos    = "(${p.position.x},${p.position.y})"
            val hStr   = "H=${fmtN(p.needs.hunger)}${if (p.needs.hunger > 0.6f) "★" else " "}"
            val sStr   = "S=${fmtN(p.needs.sleep)}${if (p.needs.sleep > 0.8f) "★" else " "}"
            val action = fmtAction(p.lastAction)
            println(" P${p.id.toString().padStart(2,'0')} ${p.name.padEnd(12)} ${pos.padEnd(10)} $hStr $sStr  $action")
        }

        println()
        println("BUILDINGS:")
        map.buildings.values.sortedBy { it.id }.forEach { b ->
            val typeTag = (b.subtype?.name ?: b.type.name).take(15).padEnd(15)
            val occ     = occupancy[b.id] ?: 0
            val status  = if (b.type == BuildingType.Residential) "occupants=$occ" else "inside=$occ"
            println(" bldg${b.id} $typeTag  $status")
        }
    }

    private fun fmtN(f: Float) = "%.2f".format(f)

    private fun fmtAction(a: Action): String = when (a) {
        is Action.Work      -> "Work(bldg${a.buildingId})"
        is Action.Eat       -> "Eat(bldg${a.buildingId})"
        is Action.Drink     -> "Drink(bldg${a.buildingId})"
        is Action.Sleep     -> "Sleep(bldg${a.buildingId})"
        is Action.Shop      -> "Shop(bldg${a.buildingId})"
        is Action.Heal      -> "Heal(bldg${a.buildingId})"
        is Action.Learn     -> "Learn(bldg${a.buildingId})"
        is Action.Exercise  -> "Exercise(bldg${a.buildingId})"
        is Action.Relax     -> "Relax(bldg${a.buildingId})"
        is Action.Watch     -> "Watch(bldg${a.buildingId})"
        is Action.MoveTo    -> "MoveTo(${a.target.x},${a.target.y})"
        is Action.Socialize -> "Socialize(p${a.targetPeepId})"
        is Action.ParkCar   -> "ParkCar(${a.spot.x},${a.spot.y})"
        is Action.RetrieveCar -> "RetrieveCar(${a.spot.x},${a.spot.y})"
        is Action.WaitForBus -> "WaitForBus(stop${a.stopId})"
        is Action.RideBus   -> "RideBus(bus${a.busId})"
        Action.Idle         -> "Idle"
    }
}
