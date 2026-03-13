package tick

import peep.Action
import peep.Peep
import peep.WorldView
import world.WorldMap

class TickEngine(val map: WorldMap) {

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
        // Phase 2: Decide
        val actions = peeps.values.map { peep -> peep to peep.brain.decide(peep, worldView) }

        // Phase 3: Validate (basic — skip conflicts for now)

        // Phase 4: Execute
        actions.forEach { (peep, action) -> execute(peep, action) }

        // Phase 5: Maintain
        peeps.values.forEach { peep ->
            peep.needs.hunger = (peep.needs.hunger + 0.001f).coerceIn(0f, 1f)
            peep.needs.fatigue = (peep.needs.fatigue + 0.0005f).coerceIn(0f, 1f)
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
            is Action.Eat -> peep.needs.hunger = (peep.needs.hunger - 0.5f).coerceAtLeast(0f)
            is Action.Sleep -> peep.needs.fatigue = (peep.needs.fatigue - 0.5f).coerceAtLeast(0f)
            is Action.Work -> peep.money += 10f
            is Action.Socialize -> peep.needs.social = (peep.needs.social - 0.3f).coerceAtLeast(0f)
            is Action.Idle -> Unit
        }
    }
}
