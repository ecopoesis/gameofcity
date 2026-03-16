package peep

import world.BuildingType

class UtilityBrain : Brain {

    private val nav = NavigationHelper()

    override fun decide(peep: Peep, world: WorldView): Action {
        nav.pendingAction(peep, world)?.let { return it }
        return chooseGoal(peep, world)
    }

    private fun chooseGoal(peep: Peep, world: WorldView): Action {
        val pf = nav.pathfinder(world.map)
        val sched = ScheduleTemplate.forType(peep.schedule)
        val hour = world.clock.hour

        // Critical hunger overrides everything
        if (peep.needs.hunger > 0.6f) {
            val shop = world.map.buildingsOfType(BuildingType.Commercial).firstOrNull()
            if (shop != null) {
                return nav.navigateTo(peep.position, shop.cells.first(), pf, Action.Eat(shop.id))
            }
        }

        // Mealtime hunger boost — eat if even moderately hungry at mealtimes
        if (sched.isMealTime(hour) && peep.needs.hunger > 0.3f) {
            val shop = world.map.buildingsOfType(BuildingType.Commercial).firstOrNull()
            if (shop != null) {
                return nav.navigateTo(peep.position, shop.cells.first(), pf, Action.Eat(shop.id))
            }
        }

        // Sleep time — strongly prefer going home + sleeping
        if (sched.isSleepTime(hour)) {
            if (peep.homeId != null) {
                val home = world.map.buildings[peep.homeId!!]
                if (home != null) {
                    return nav.navigateTo(peep.position, home.cells.first(), pf, Action.Sleep(home.id))
                }
            }
            // Homeless but tired: sleep at park
            if (peep.needs.sleep > 0.5f) {
                val candidate = NeedActionMapper.findAction(NeedType.Sleep, peep, world.map)
                if (candidate != null) {
                    return nav.navigateTo(peep.position, candidate.building.cells.first(), pf, candidate.action)
                }
            }
        }

        // Very tired -> sleep regardless of schedule
        if (peep.needs.sleep > 0.8f && peep.homeId != null) {
            val home = world.map.buildings[peep.homeId!!]
            if (home != null) {
                return nav.navigateTo(peep.position, home.cells.first(), pf, Action.Sleep(home.id))
            }
        }

        // Work hours -> go to job
        if (sched.isWorkTime(hour) && peep.jobId != null) {
            val job = world.map.buildings[peep.jobId!!]
            if (job != null) {
                return nav.navigateTo(peep.position, job.cells.first(), pf, Action.Work(job.id))
            }
        }

        // Free time — use NeedActionMapper for most urgent need
        val topNeed = peep.needs.topNeed()
        if (topNeed != null && topNeed.second > 0.3f) {
            val candidate = NeedActionMapper.findAction(topNeed.first, peep, world.map)
            if (candidate != null) {
                return nav.navigateTo(peep.position, candidate.building.cells.first(), pf, candidate.action)
            }
        }

        // Default: go home
        if (peep.homeId != null) {
            val home = world.map.buildings[peep.homeId!!]
            if (home != null) {
                return nav.navigateTo(peep.position, home.cells.first(), pf, Action.Sleep(home.id))
            }
        }

        return Action.Idle
    }
}
