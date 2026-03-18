package peep

import kotlin.random.Random

class WaveBrain : Brain {

    private val nav = NavigationHelper()
    private val rng = Random

    override fun decide(peep: Peep, world: WorldView): Action {
        nav.pendingAction(peep, world)?.let { return it }
        return chooseGoal(peep, world)
    }

    private fun chooseGoal(peep: Peep, world: WorldView): Action {
        val pf = nav.pathfinder(world.map)
        val sched = ScheduleTemplate.forType(peep.schedule)
        val hour = world.clock.hour
        val candidates = mutableListOf<ScoredAction>()

        // Schedule-based bonus scores injected as candidates
        if (sched.isSleepTime(hour) && peep.homeId != null) {
            val home = world.map.buildings[peep.homeId!!]
            if (home != null) {
                val sleepScore = 6.0f + peep.needs.sleep * 4.0f  // strong pull at night
                candidates.add(ScoredAction(
                    ActionCandidate(home, Action.Sleep(home.id), listOf(NeedType.Sleep)),
                    sleepScore
                ))
            }
        }

        if (sched.isWorkTime(hour) && peep.jobId != null) {
            val job = world.map.buildings[peep.jobId!!]
            if (job != null) {
                val workScore = 3.0f + peep.needs.financial * 2.0f
                candidates.add(ScoredAction(
                    ActionCandidate(job, Action.Work(job.id), listOf(NeedType.Financial)),
                    workScore
                ))
            }
        }

        // Mealtime hunger boost
        if (sched.isMealTime(hour)) {
            val hungerBoost = 2.0f
            val candidate = NeedActionMapper.findAction(NeedType.Hunger, peep, world.map)
            if (candidate != null) {
                candidates.add(ScoredAction(candidate, hungerBoost + peep.needs.hunger * 8.0f))
            }
        }

        // Standard need-based scoring
        for (level in MaslowLevel.entries) {
            val weight = LEVEL_WEIGHTS[level.index - 1]
            for ((needType, value) in peep.needs.allAtLevel(level)) {
                if (value < 0.1f) continue
                val score = weight * value * value
                val candidate = NeedActionMapper.findAction(needType, peep, world.map)
                if (candidate != null) {
                    candidates.add(ScoredAction(candidate, score))
                }
            }
        }

        if (candidates.isEmpty()) {
            return defaultBehavior(peep, world, pf)
        }

        val top3 = candidates.sortedByDescending { it.score }.take(3)
        val chosen = weightedRandomSelect(top3)
        return nav.planTrip(
            peep.position, chosen.candidate.building.cells.first(),
            world.map, pf, peep, chosen.candidate.action
        )
    }

    private fun defaultBehavior(peep: Peep, world: WorldView, pf: pathfind.AStarPathfinder): Action {
        if (peep.homeId != null) {
            val home = world.map.buildings[peep.homeId!!]
            if (home != null) {
                return nav.planTrip(peep.position, home.cells.first(), world.map, pf, peep, Action.Sleep(home.id))
            }
        }
        return Action.Idle
    }

    private fun weightedRandomSelect(items: List<ScoredAction>): ScoredAction {
        val totalWeight = items.sumOf { it.score.toDouble() }
        if (totalWeight <= 0) return items.first()
        var roll = rng.nextDouble() * totalWeight
        for (item in items) {
            roll -= item.score
            if (roll <= 0) return item
        }
        return items.last()
    }

    private data class ScoredAction(val candidate: ActionCandidate, val score: Float)

    companion object {
        val LEVEL_WEIGHTS = floatArrayOf(8.0f, 4.0f, 2.0f, 1.0f, 0.5f)
    }
}
