package peep

import world.BuildingType
import world.WorldMap

object Demographics {

    /** Mortality probability per sim-day given age. */
    fun mortalityRate(age: Int, healthNeed: Float): Float {
        val base = when {
            age < 60 -> 0f
            age < 70 -> 0.01f
            age < 80 -> 0.03f
            age < 90 -> 0.08f
            else -> 0.15f
        }
        return if (healthNeed > 0.8f) base * 2f else base
    }

    /** Check if a household can have a child. */
    fun canHaveChild(peepA: Peep, peepB: Peep, map: WorldMap, childrenCount: Int): Boolean {
        if (peepA.age !in 20..45 || peepB.age !in 20..45) return false
        if (childrenCount >= 4) return false
        val homeId = peepA.homeId ?: return false
        val home = map.buildings[homeId] ?: return false
        return !home.isFull
    }

    /** Immigration conditions: more jobs than jobseekers and housing available. */
    fun shouldImmigrate(
        unemployedCount: Int,
        availableJobSlots: Int,
        availableHousingSlots: Int
    ): Boolean {
        return availableJobSlots > unemployedCount * 1.5 && availableHousingSlots > 0
    }

    /** Emigration conditions: 3+ critical needs for extended period. */
    fun shouldEmigrate(peep: Peep): Boolean {
        val n = peep.needs
        val criticalCount = listOf(
            n.hunger, n.thirst, n.sleep, n.warmth,
            n.shelter, n.health, n.friendship, n.community
        ).count { it > 0.8f }
        return criticalCount >= 3
    }
}
