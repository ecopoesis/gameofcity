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
                return nav.planTrip(peep.position, shop.cells.first(), world.map, pf, peep, Action.Eat(shop.id), world.transit)
            }
        }

        // Mealtime hunger boost — eat if even moderately hungry at mealtimes
        if (sched.isMealTime(hour) && peep.needs.hunger > 0.3f) {
            val shop = world.map.buildingsOfType(BuildingType.Commercial).firstOrNull()
            if (shop != null) {
                return nav.planTrip(peep.position, shop.cells.first(), world.map, pf, peep, Action.Eat(shop.id), world.transit)
            }
        }

        // Sleep time — strongly prefer going home + sleeping
        if (sched.isSleepTime(hour)) {
            if (peep.homeId != null) {
                val home = world.map.buildings[peep.homeId!!]
                if (home != null) {
                    return nav.planTrip(peep.position, home.cells.first(), world.map, pf, peep, Action.Sleep(home.id), world.transit)
                }
            }
            // Homeless but tired: sleep at park
            if (peep.needs.sleep > 0.5f) {
                val candidate = NeedActionMapper.findAction(NeedType.Sleep, peep, world.map)
                if (candidate != null) {
                    return nav.planTrip(peep.position, candidate.building.cells.first(), world.map, pf, peep, candidate.action, world.transit)
                }
            }
        }

        // Very tired -> sleep regardless of schedule
        if (peep.needs.sleep > 0.8f && peep.homeId != null) {
            val home = world.map.buildings[peep.homeId!!]
            if (home != null) {
                return nav.planTrip(peep.position, home.cells.first(), world.map, pf, peep, Action.Sleep(home.id), world.transit)
            }
        }

        // Work hours -> go to job
        if (sched.isWorkTime(hour) && peep.jobId != null) {
            val job = world.map.buildings[peep.jobId!!]
            if (job != null) {
                return nav.planTrip(peep.position, job.cells.first(), world.map, pf, peep, Action.Work(job.id), world.transit)
            }
        }

        // Free time — use NeedActionMapper for most urgent need
        val topNeed = peep.needs.topNeed()
        if (topNeed != null && topNeed.second > 0.3f) {
            val candidate = NeedActionMapper.findAction(topNeed.first, peep, world.map)
            if (candidate != null) {
                return nav.planTrip(peep.position, candidate.building.cells.first(), world.map, pf, peep, candidate.action, world.transit)
            }
        }

        // Default: go home
        if (peep.homeId != null) {
            val home = world.map.buildings[peep.homeId!!]
            if (home != null) {
                return nav.planTrip(peep.position, home.cells.first(), world.map, pf, peep, Action.Sleep(home.id), world.transit)
            }
        }

        return Action.Idle
    }
}
