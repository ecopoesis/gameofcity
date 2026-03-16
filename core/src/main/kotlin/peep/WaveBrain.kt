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
        val candidates = mutableListOf<ScoredAction>()

        for (level in MaslowLevel.entries) {
            val weight = LEVEL_WEIGHTS[level.index - 1]
            for ((needType, value) in peep.needs.allAtLevel(level)) {
                if (value < 0.1f) continue
                val score = weight * value * value  // quadratic urgency
                val candidate = NeedActionMapper.findAction(needType, peep, world.map)
                if (candidate != null) {
                    candidates.add(ScoredAction(candidate, score))
                }
            }
        }

        if (candidates.isEmpty()) {
            return defaultBehavior(peep, world, pf)
        }

        // Weighted random from top-3 for behavioral variety
        val top3 = candidates.sortedByDescending { it.score }.take(3)
        val chosen = weightedRandomSelect(top3)
        return nav.navigateTo(
            peep.position, chosen.candidate.building.cells.first(),
            pf, chosen.candidate.action
        )
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
