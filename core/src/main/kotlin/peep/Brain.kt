package peep

import tick.SimClock
import world.WorldMap

interface WorldView {
    val map: WorldMap
    val peeps: Map<Int, Peep>
    val tick: Long
    val clock: SimClock
}

interface Brain {
    fun decide(peep: Peep, world: WorldView): Action
}

class RandomBrain : Brain {
    private val directions = listOf(
        0 to 1, 0 to -1, 1 to 0, -1 to 0
    )
    override fun decide(peep: Peep, world: WorldView): Action {
        val (dx, dy) = directions.random()
        val target = peep.position.copy(x = peep.position.x + dx, y = peep.position.y + dy)
        return if (world.map.isPassable(target)) Action.MoveTo(target) else Action.Idle
    }
}

class IdleBrain : Brain {
    override fun decide(peep: Peep, world: WorldView): Action = Action.Idle
}
