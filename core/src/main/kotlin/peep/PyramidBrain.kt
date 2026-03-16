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
                return nav.navigateTo(peep.position, home.cells.first(), pf, Action.Sleep(home.id))
            }
        }

        // Work time with no critical needs -> go to job
        if (sched.isWorkTime(hour) && maxNeed < 0.5f && peep.jobId != null) {
            val job = world.map.buildings[peep.jobId!!]
            if (job != null) {
                return nav.navigateTo(peep.position, job.cells.first(), pf, Action.Work(job.id))
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
                    return nav.navigateTo(
                        peep.position, candidate.building.cells.first(),
                        pf, candidate.action
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
                return nav.navigateTo(peep.position, home.cells.first(), pf, Action.Sleep(home.id))
            }
        }
        return Action.Idle
    }

    companion object {
        const val THRESHOLD = 0.3f
    }
}
