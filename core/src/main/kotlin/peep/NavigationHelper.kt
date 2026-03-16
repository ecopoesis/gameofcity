package peep

import pathfind.AStarPathfinder
import world.CellCoord
import world.WorldMap

class NavigationHelper {

    private val pathQueue = ArrayDeque<CellCoord>()
    private var terminalAction: Action = Action.Idle
    private var _pathfinder: AStarPathfinder? = null

    fun pathfinder(map: WorldMap): AStarPathfinder =
        _pathfinder ?: AStarPathfinder(map).also { _pathfinder = it }

    fun pendingAction(peep: Peep, world: WorldView): Action? {
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
        return null
    }

    fun navigateTo(from: CellCoord, to: CellCoord, pf: AStarPathfinder, terminal: Action): Action {
        if (from == to) return terminal
        val path = pf.findPath(from, to)
        if (path.isEmpty()) return Action.Idle
        pathQueue.clear()
        pathQueue.addAll(path)
        terminalAction = terminal
        return Action.MoveTo(pathQueue.removeFirst())
    }
}
