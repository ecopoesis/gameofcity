package tick

import peep.Peep
import world.WorldMap

data class CityStats(
    val population: Int = 0,
    val birthsToday: Int = 0,
    val deathsToday: Int = 0,
    val immigrantsToday: Int = 0,
    val emigrantsToday: Int = 0,
    val employmentRate: Float = 0f,
    val unemployedCount: Int = 0,
    val avgWage: Float = 0f,
    val housingOccupancy: Float = 0f,
    val homelessCount: Int = 0,
    val avgRent: Float = 0f,
    val avgHappiness: Float = 0f,
    val medianMoney: Float = 0f,
    val giniCoefficient: Float = 0f,
    val avgFriends: Float = 0f,
    val householdCount: Int = 0,
    val singlesCount: Int = 0
) {
    companion object {
        fun compute(engine: TickEngine): CityStats {
            val peeps = engine.peeps.values.toList()
            val map = engine.map
            if (peeps.isEmpty()) return CityStats()

            val workers = peeps.filter { !it.isChild && !it.isRetired }
            val employed = workers.count { it.jobId != null }
            val employmentRate = if (workers.isNotEmpty()) employed.toFloat() / workers.size else 0f

            val workplaces = map.buildings.values.filter { it.isWorkplace }
            val avgWage = if (workplaces.isNotEmpty()) workplaces.map { it.wage }.average().toFloat() else 0f

            val residentials = map.buildings.values.filter { it.isResidential }
            val totalCapacity = residentials.sumOf { it.capacity }
            val housed = peeps.count { it.homeId != null }
            val housingOccupancy = if (totalCapacity > 0) housed.toFloat() / totalCapacity else 0f
            val avgRent = if (residentials.isNotEmpty()) residentials.map { it.rent }.average().toFloat() else 0f

            // Average happiness: mean of all need satisfactions (1 - needValue)
            val avgHappiness = peeps.map { p ->
                val n = p.needs
                1f - listOf(n.hunger, n.thirst, n.sleep, n.shelter, n.health,
                    n.friendship, n.community, n.creativity, n.learning).average().toFloat()
            }.average().toFloat()

            // Median money
            val sortedMoney = peeps.map { it.money }.sorted()
            val medianMoney = sortedMoney[sortedMoney.size / 2]

            // Gini coefficient
            val gini = computeGini(sortedMoney)

            val avgFriends = peeps.map { it.friendships.size }.average().toFloat()

            return CityStats(
                population = peeps.size,
                birthsToday = engine.birthsToday,
                deathsToday = engine.deathsToday,
                immigrantsToday = engine.immigrantsToday,
                emigrantsToday = engine.emigrantsToday,
                employmentRate = employmentRate,
                unemployedCount = workers.size - employed,
                avgWage = avgWage,
                housingOccupancy = housingOccupancy,
                homelessCount = peeps.count { it.isHomeless },
                avgRent = avgRent,
                avgHappiness = avgHappiness,
                medianMoney = medianMoney,
                giniCoefficient = gini,
                avgFriends = avgFriends,
                householdCount = engine.households.size,
                singlesCount = peeps.count { !it.isPartnered }
            )
        }

        private fun computeGini(sorted: List<Float>): Float {
            val n = sorted.size
            if (n <= 1) return 0f
            val total = sorted.sum()
            if (total <= 0f) return 0f
            var sumOfDiffs = 0f
            for (i in sorted.indices) {
                sumOfDiffs += (2 * (i + 1) - n - 1) * sorted[i]
            }
            return (sumOfDiffs / (n * total)).coerceIn(0f, 1f)
        }
    }
}
