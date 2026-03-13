package peep

import pathfind.AStarPathfinder
import world.BuildingType
import world.CellCoord

class UtilityBrain : Brain {

    private val pathQueue = ArrayDeque<CellCoord>()
    private var terminalAction: Action = Action.Idle
    private var pathfinder: AStarPathfinder? = null

    override fun decide(peep: Peep, world: WorldView): Action {
        // Keep walking current path
        if (pathQueue.isNotEmpty()) {
            return Action.MoveTo(pathQueue.removeFirst())
        }
        // Execute terminal action on arrival
        val pending = terminalAction
        if (pending != Action.Idle) {
            terminalAction = Action.Idle
            return pending
        }
        return chooseGoal(peep, world)
    }

    private fun chooseGoal(peep: Peep, world: WorldView): Action {
        val pf = pathfinder ?: AStarPathfinder(world.map).also { pathfinder = it }
        val timeOfDay = (world.tick % TICKS_PER_DAY).toInt()

        // Hungry → eat at nearest commercial building
        if (peep.needs.hunger > 0.6f) {
            val shop = world.map.buildingsOfType(BuildingType.Commercial).firstOrNull()
            if (shop != null) {
                return navigateTo(peep.position, shop.cells.first(), pf, Action.Eat(shop.id))
            }
        }

        // Very tired → sleep at home
        if (peep.needs.fatigue > 0.8f && peep.homeId != null) {
            val home = world.map.buildings[peep.homeId!!]
            if (home != null) {
                return navigateTo(peep.position, home.cells.first(), pf, Action.Sleep(home.id))
            }
        }

        // Work hours → go to job
        if (timeOfDay in WORK_START until WORK_END && peep.jobId != null) {
            val job = world.map.buildings[peep.jobId!!]
            if (job != null) {
                return navigateTo(peep.position, job.cells.first(), pf, Action.Work(job.id))
            }
        }

        // Off hours → go home
        if (peep.homeId != null) {
            val home = world.map.buildings[peep.homeId!!]
            if (home != null) {
                return navigateTo(peep.position, home.cells.first(), pf, Action.Sleep(home.id))
            }
        }

        return Action.Idle
    }

    private fun navigateTo(from: CellCoord, to: CellCoord, pf: AStarPathfinder, terminal: Action): Action {
        if (from == to) return terminal
        val path = pf.findPath(from, to)
        if (path.isEmpty()) return Action.Idle
        pathQueue.clear()
        pathQueue.addAll(path)
        terminalAction = terminal
        return Action.MoveTo(pathQueue.removeFirst())
    }

    companion object {
        const val TICKS_PER_DAY = 1440
        const val WORK_START = 480  // 8 am
        const val WORK_END = 1080   // 6 pm
    }
}
