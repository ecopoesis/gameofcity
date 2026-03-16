package peep

class PyramidBrain : Brain {

    private val nav = NavigationHelper()

    override fun decide(peep: Peep, world: WorldView): Action {
        nav.pendingAction(peep, world)?.let { return it }
        return chooseGoal(peep, world)
    }

    private fun chooseGoal(peep: Peep, world: WorldView): Action {
        val pf = nav.pathfinder(world.map)

        for (level in MaslowLevel.entries) {
            val urgentNeeds = peep.needs.allAtLevel(level)
                .filter { it.second > THRESHOLD }

            if (urgentNeeds.isNotEmpty()) {
                val (needType, _) = urgentNeeds.maxBy { it.second }
                val candidate = NeedActionMapper.findAction(needType, peep, world.map)
                if (candidate != null) {
                    return nav.navigateTo(
                        peep.position, candidate.building.cells.first(),
                        pf, candidate.action
                    )
                }
                // Can't satisfy most urgent need at this level — idle (don't skip up)
                return Action.Idle
            }
            // All needs at this level below threshold — proceed to next level
        }

        return defaultBehavior(peep, world, pf)
    }

    private fun defaultBehavior(peep: Peep, world: WorldView, pf: pathfind.AStarPathfinder): Action {
        // All satisfied -> go home or wander
        if (peep.homeId != null) {
            val home = world.map.buildings[peep.homeId!!]
            if (home != null) {
                return nav.navigateTo(peep.position, home.cells.first(), pf, Action.Sleep(home.id))
            }
        }
        return Action.Idle
    }

    companion object {
        const val THRESHOLD = 0.3f
    }
}
