package gen

import peep.Gender
import peep.Peep
import peep.UtilityBrain
import tick.TickEngine
import world.BuildingType
import world.CellCoord

object PeepSpawner {

    private val NAMES = listOf(
        "Alex", "Blake", "Casey", "Dana", "Ellis",
        "Finley", "Gray", "Harper", "Indigo", "Jules",
        "Kai", "Lane", "Morgan", "Nova", "Oakley",
        "Parker", "Quinn", "River", "Sage", "Taylor"
    )

    fun spawn(engine: TickEngine, count: Int) {
        val residentials = engine.map.buildingsOfType(BuildingType.Residential)
        val workplaces = engine.map.buildings.values
            .filter { it.type == BuildingType.Commercial || it.type == BuildingType.Industrial }
            .toList()

        repeat(count) { i ->
            val home = residentials.getOrNull(i % residentials.size.coerceAtLeast(1))
            val job = workplaces.getOrNull(i % workplaces.size.coerceAtLeast(1))
            val startPos = home?.cells?.first() ?: CellCoord(1, 1)

            val peep = Peep(
                id = i,
                name = "${NAMES[i % NAMES.size]} ${i / NAMES.size + 1}".trimEnd('1').trimEnd(),
                age = 18 + (i * 7 % 50),
                gender = Gender.entries[i % Gender.entries.size],
                position = startPos,
                homeId = home?.id,
                jobId = job?.id,
                brain = UtilityBrain()
            )
            engine.addPeep(peep)
        }
    }
}
