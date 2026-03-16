package tick

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import peep.Action
import peep.Peep
import peep.WorldView
import world.CellCoord
import world.WorldMap
import kotlin.coroutines.CoroutineContext

class TickEngine(val map: WorldMap, private val parallelContext: CoroutineContext = Dispatchers.Default) {

    val peeps: MutableMap<Int, Peep> = mutableMapOf()
    var tick: Long = 0L
    val clock: SimClock = SimClock()

    private val worldView = object : WorldView {
        override val map: WorldMap get() = this@TickEngine.map
        override val peeps: Map<Int, Peep> get() = this@TickEngine.peeps
        override val tick: Long get() = this@TickEngine.tick
        override val clock: SimClock get() = this@TickEngine.clock
    }

    fun addPeep(peep: Peep) {
        peeps[peep.id] = peep
    }

    fun step() {
        // Phase 1: Perceive (worldView is always fresh)

        // Phase 2: Decide (parallel when population is large enough)
        val actions = if (peeps.size > PARALLEL_THRESHOLD) {
            runBlocking(parallelContext) {
                peeps.values.map { peep ->
                    async { peep to peep.brain.decide(peep, worldView) }
                }.map { it.await() }
            }
        } else {
            peeps.values.map { peep -> peep to peep.brain.decide(peep, worldView) }
        }

        // Phase 3: Validate — reject actions targeting full buildings
        val validated = validate(actions)

        // Phase 4: Execute
        validated.forEach { (peep, action) ->
            peep.lastAction = action
            execute(peep, action)
        }

        // Phase 5: Maintain
        maintain()

        clock.advance()
        tick++
    }

    private fun validate(actions: List<Pair<Peep, Action>>): List<Pair<Peep, Action>> {
        // Track how many peeps are trying to enter each building this tick
        val buildingDemand = mutableMapOf<Int, Int>()
        return actions.map { (peep, action) ->
            val buildingId = action.targetBuildingId()
            if (buildingId != null) {
                val building = map.buildings[buildingId]
                if (building != null && building.isFull) {
                    // Already at capacity — reject
                    peep to Action.Idle
                } else if (building != null) {
                    val demand = buildingDemand.getOrPut(buildingId) { building.currentOccupants.size }
                    if (demand >= building.capacity) {
                        peep to Action.Idle
                    } else {
                        buildingDemand[buildingId] = demand + 1
                        peep to action
                    }
                } else {
                    peep to action
                }
            } else {
                peep to action
            }
        }
    }

    private fun execute(peep: Peep, action: Action) {
        val n = peep.needs
        when (action) {
            is Action.MoveTo -> {
                if (map.isPassable(action.target)) {
                    map.peepsAt.getOrPut(peep.position) { mutableListOf() }.remove(peep.id)
                    peep.position = action.target
                    map.peepsAt.getOrPut(action.target) { mutableListOf() }.add(peep.id)
                }
            }
            is Action.Eat -> {
                n.hunger = (n.hunger - 0.5f).coerceAtLeast(0f)
                n.thirst = (n.thirst - 0.2f).coerceAtLeast(0f)
                peep.money -= 5f
            }
            is Action.Drink -> {
                n.thirst = (n.thirst - 0.5f).coerceAtLeast(0f)
                n.friendship = (n.friendship - 0.1f).coerceAtLeast(0f)
                peep.money -= 3f
            }
            is Action.Sleep -> {
                n.sleep = (n.sleep - 0.5f).coerceAtLeast(0f)
                n.warmth = (n.warmth - 0.3f).coerceAtLeast(0f)
                n.shelter = (n.shelter - 0.2f).coerceAtLeast(0f)
            }
            is Action.Work -> {
                n.recognition = (n.recognition - 0.1f).coerceAtLeast(0f)
                peep.money += 1f
            }
            is Action.Socialize -> {
                n.friendship = (n.friendship - 0.3f).coerceAtLeast(0f)
                n.community = (n.community - 0.1f).coerceAtLeast(0f)
                val other = peeps[action.targetPeepId]
                if (other != null) {
                    peep.friendships[other.id]  = (peep.friendships[other.id]  ?: 0f) + 0.05f
                    other.friendships[peep.id]  = (other.friendships[peep.id]  ?: 0f) + 0.05f
                }
            }
            is Action.Shop -> {
                n.accomplishment = (n.accomplishment - 0.2f).coerceAtLeast(0f)
                n.status = (n.status - 0.1f).coerceAtLeast(0f)
                peep.money -= 10f
            }
            is Action.Heal -> {
                n.health = (n.health - 0.5f).coerceAtLeast(0f)
                peep.money -= 20f
            }
            is Action.Learn -> {
                n.learning = (n.learning - 0.3f).coerceAtLeast(0f)
                n.creativity = (n.creativity - 0.1f).coerceAtLeast(0f)
            }
            is Action.Exercise -> {
                n.health = (n.health - 0.2f).coerceAtLeast(0f)
                n.accomplishment = (n.accomplishment - 0.15f).coerceAtLeast(0f)
            }
            is Action.Relax -> {
                n.creativity = (n.creativity - 0.2f).coerceAtLeast(0f)
                n.community = (n.community - 0.15f).coerceAtLeast(0f)
                n.friendship = (n.friendship - 0.1f).coerceAtLeast(0f)
            }
            is Action.Watch -> {
                n.creativity = (n.creativity - 0.15f).coerceAtLeast(0f)
                n.community = (n.community - 0.2f).coerceAtLeast(0f)
                peep.money -= 8f
            }
            is Action.Idle -> Unit
        }
    }

    private fun maintain() {
        val isNewDay = clock.isNewDay()
        peeps.values.forEach { peep ->
            val n = peep.needs
            // Level 1 - Physiological (fast decay)
            n.hunger  = (n.hunger  + 0.001f).coerceIn(0f, 1f)
            n.thirst  = (n.thirst  + 0.0012f).coerceIn(0f, 1f)
            n.sleep   = (n.sleep   + 0.0005f).coerceIn(0f, 1f)
            n.warmth  = (n.warmth  + 0.0002f).coerceIn(0f, 1f)

            // Level 2 - Safety (moderate decay)
            if (peep.homeId == null) {
                n.shelter = (n.shelter + 0.0003f).coerceIn(0f, 1f)
            }
            n.health = (n.health + 0.0001f).coerceIn(0f, 1f)
            // financial: derived from money
            n.financial = when {
                peep.money >= 200f -> 0f
                peep.money <= 0f -> 1f
                else -> 1f - (peep.money / 200f)
            }

            // Level 3 - Love/Belonging (slow decay)
            n.friendship = (n.friendship + 0.0004f).coerceIn(0f, 1f)
            n.family     = (n.family     + 0.0002f).coerceIn(0f, 1f)
            n.community  = (n.community  + 0.0003f).coerceIn(0f, 1f)

            // Level 4 - Esteem (very slow decay)
            n.recognition    = (n.recognition    + 0.0002f).coerceIn(0f, 1f)
            n.accomplishment = (n.accomplishment + 0.0001f).coerceIn(0f, 1f)
            // status: derived from money + home + job
            val moneyScore = (peep.money / 500f).coerceIn(0f, 1f)
            val homeScore = if (peep.homeId != null) 0.3f else 0f
            val jobScore = if (peep.jobId != null) 0.3f else 0f
            n.status = (1f - (moneyScore + homeScore + jobScore).coerceIn(0f, 1f))

            // Level 5 - Self-Actualization (glacial decay)
            n.creativity = (n.creativity + 0.00005f).coerceIn(0f, 1f)
            n.learning   = (n.learning   + 0.00008f).coerceIn(0f, 1f)
            n.purpose    = (n.purpose    + 0.00005f).coerceIn(0f, 1f)

            // Rent (daily)
            if (isNewDay && peep.homeId != null) {
                peep.money -= 20f
                if (peep.money < 0f) peep.homeId = null  // evicted
            }
        }

        // Update building occupancy from peep positions
        map.buildings.values.forEach { it.currentOccupants.clear() }
        peeps.values.forEach { peep ->
            val cell = map.getCell(peep.position)
            val bldgId = cell?.buildingId
            if (bldgId != null) {
                map.buildings[bldgId]?.currentOccupants?.add(peep.id)
            }
        }

        // Proximity friendship: peeps sharing a cell grow closer slowly
        val occupancy = mutableMapOf<CellCoord, MutableList<Int>>()
        peeps.values.forEach { p -> occupancy.getOrPut(p.position) { mutableListOf() }.add(p.id) }
        occupancy.values.forEach { ids ->
            if (ids.size > 1) {
                for (i in ids.indices) for (j in i + 1 until ids.size) {
                    val a = peeps[ids[i]] ?: continue
                    val b = peeps[ids[j]] ?: continue
                    a.friendships[b.id] = (a.friendships[b.id] ?: 0f) + 0.0001f
                    b.friendships[a.id] = (b.friendships[a.id] ?: 0f) + 0.0001f
                }
            }
        }
    }

    companion object {
        private const val PARALLEL_THRESHOLD = 100
        const val TICKS_PER_DAY = 1440
    }
}
