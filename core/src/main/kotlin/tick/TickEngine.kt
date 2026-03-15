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

    private val worldView = object : WorldView {
        override val map: WorldMap get() = this@TickEngine.map
        override val peeps: Map<Int, Peep> get() = this@TickEngine.peeps
        override val tick: Long get() = this@TickEngine.tick
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

        // Phase 3: Validate (basic — skip conflicts for now)

        // Phase 4: Execute
        actions.forEach { (peep, action) ->
            peep.lastAction = action
            execute(peep, action)
        }

        // Phase 5: Maintain
        val rentDay = tick > 0L && tick % 1440L == 0L
        peeps.values.forEach { peep ->
            peep.needs.hunger  = (peep.needs.hunger  + 0.001f).coerceIn(0f, 1f)
            peep.needs.fatigue = (peep.needs.fatigue + 0.0005f).coerceIn(0f, 1f)
            if (rentDay && peep.homeId != null) {
                peep.money -= 20f
                if (peep.money < 0f) peep.homeId = null  // evicted
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

        tick++
    }

    private fun execute(peep: Peep, action: Action) {
        when (action) {
            is Action.MoveTo -> {
                if (map.isPassable(action.target)) {
                    map.peepsAt.getOrPut(peep.position) { mutableListOf() }.remove(peep.id)
                    peep.position = action.target
                    map.peepsAt.getOrPut(action.target) { mutableListOf() }.add(peep.id)
                }
            }
            is Action.Eat -> {
                peep.needs.hunger = (peep.needs.hunger - 0.5f).coerceAtLeast(0f)
                peep.money -= 5f
            }
            is Action.Sleep -> peep.needs.fatigue = (peep.needs.fatigue - 0.5f).coerceAtLeast(0f)
            is Action.Work -> peep.money += 1f
            is Action.Socialize -> {
                peep.needs.social = (peep.needs.social - 0.3f).coerceAtLeast(0f)
                val other = peeps[action.targetPeepId]
                if (other != null) {
                    peep.friendships[other.id]  = (peep.friendships[other.id]  ?: 0f) + 0.05f
                    other.friendships[peep.id]  = (other.friendships[peep.id]  ?: 0f) + 0.05f
                }
            }
            is Action.Idle -> Unit
        }
    }

    companion object {
        private const val PARALLEL_THRESHOLD = 100
    }
}
