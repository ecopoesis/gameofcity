package peep

class PyramidBrain : Brain {

    private val nav = NavigationHelper()

    override fun decide(peep: Peep, world: WorldView): Action {
        nav.pendingAction(peep, world)?.let { return it }
        return chooseGoal(peep, world)
    }

    private fun chooseGoal(peep: Peep, world: WorldView): Action {
        val pf = nav.pathfinder(world.map)
        val sched = ScheduleTemplate.forType(peep.schedule)
        val hour = world.clock.hour

        // Schedule overrides (unless critical need)
        val maxNeed = peep.needs.topNeed()?.second ?: 0f

        // Sleep time with no critical needs -> go home
        if (sched.isSleepTime(hour) && maxNeed < 0.7f && peep.homeId != null) {
            val home = world.map.buildings[peep.homeId!!]
            if (home != null) {
                return nav.planTrip(peep.position, home.cells.first(), world.map, pf, peep, Action.Sleep(home.id), world.transit)
            }
        }

        // Work time with no critical needs -> go to job
        if (sched.isWorkTime(hour) && maxNeed < 0.5f && peep.jobId != null) {
            val job = world.map.buildings[peep.jobId!!]
            if (job != null) {
                return nav.planTrip(peep.position, job.cells.first(), world.map, pf, peep, Action.Work(job.id), world.transit)
            }
        }

        // Strict Maslow hierarchy
        for (level in MaslowLevel.entries) {
            val urgentNeeds = peep.needs.allAtLevel(level)
                .filter { it.second > THRESHOLD }

            if (urgentNeeds.isNotEmpty()) {
                val (needType, _) = urgentNeeds.maxBy { it.second }
                val candidate = NeedActionMapper.findAction(needType, peep, world.map)
                if (candidate != null) {
                    return nav.planTrip(
                        peep.position, candidate.building.cells.first(),
                        world.map, pf, peep, candidate.action, world.transit
                    )
                }
                return Action.Idle
            }
        }

        return defaultBehavior(peep, world, pf)
    }

    private fun defaultBehavior(peep: Peep, world: WorldView, pf: pathfind.AStarPathfinder): Action {
        if (peep.homeId != null) {
            val home = world.map.buildings[peep.homeId!!]
            if (home != null) {
                return nav.planTrip(peep.position, home.cells.first(), world.map, pf, peep, Action.Sleep(home.id), world.transit)
            }
        }
        return Action.Idle
    }

    companion object {
        const val THRESHOLD = 0.3f
    }
}
