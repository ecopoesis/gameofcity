package peep

import world.Building
import world.BuildingSubtype
import world.WorldMap

data class ActionCandidate(
    val building: Building,
    val action: Action,
    val needTypes: List<NeedType>
)

object NeedActionMapper {

    private data class SubtypeMapping(
        val subtype: BuildingSubtype,
        val needs: List<NeedType>,
        val actionFactory: (Int) -> Action
    )

    private val mappings = listOf(
        // Residential
        SubtypeMapping(BuildingSubtype.House, listOf(NeedType.Sleep, NeedType.Shelter, NeedType.Warmth)) { Action.Sleep(it) },
        SubtypeMapping(BuildingSubtype.Apartment, listOf(NeedType.Sleep, NeedType.Shelter, NeedType.Warmth)) { Action.Sleep(it) },
        SubtypeMapping(BuildingSubtype.Luxury, listOf(NeedType.Sleep, NeedType.Shelter, NeedType.Warmth, NeedType.Status)) { Action.Sleep(it) },
        // Commercial
        SubtypeMapping(BuildingSubtype.Restaurant, listOf(NeedType.Hunger, NeedType.Thirst)) { Action.Eat(it) },
        SubtypeMapping(BuildingSubtype.GroceryStore, listOf(NeedType.Hunger)) { Action.Shop(it) },
        SubtypeMapping(BuildingSubtype.Cafe, listOf(NeedType.Thirst, NeedType.Friendship)) { Action.Drink(it) },
        SubtypeMapping(BuildingSubtype.Shop, listOf(NeedType.Accomplishment, NeedType.Status)) { Action.Shop(it) },
        SubtypeMapping(BuildingSubtype.Office, listOf(NeedType.Financial, NeedType.Recognition)) { Action.Work(it) },
        // Industrial
        SubtypeMapping(BuildingSubtype.Factory, listOf(NeedType.Financial)) { Action.Work(it) },
        SubtypeMapping(BuildingSubtype.Warehouse, listOf(NeedType.Financial)) { Action.Work(it) },
        SubtypeMapping(BuildingSubtype.Workshop, listOf(NeedType.Financial, NeedType.Creativity)) { Action.Work(it) },
        // Civic
        SubtypeMapping(BuildingSubtype.Hospital, listOf(NeedType.Health)) { Action.Heal(it) },
        SubtypeMapping(BuildingSubtype.School, listOf(NeedType.Learning)) { Action.Learn(it) },
        SubtypeMapping(BuildingSubtype.Library, listOf(NeedType.Learning, NeedType.Creativity)) { Action.Learn(it) },
        SubtypeMapping(BuildingSubtype.CommunityCenter, listOf(NeedType.Community, NeedType.Friendship)) { Action.Relax(it) },
        // Recreation
        SubtypeMapping(BuildingSubtype.Park, listOf(NeedType.Friendship, NeedType.Community, NeedType.Creativity)) { Action.Relax(it) },
        SubtypeMapping(BuildingSubtype.Gym, listOf(NeedType.Health, NeedType.Accomplishment)) { Action.Exercise(it) },
        SubtypeMapping(BuildingSubtype.Theater, listOf(NeedType.Creativity, NeedType.Community)) { Action.Watch(it) },
        SubtypeMapping(BuildingSubtype.Stadium, listOf(NeedType.Community, NeedType.Friendship)) { Action.Watch(it) },
        SubtypeMapping(BuildingSubtype.Museum, listOf(NeedType.Learning, NeedType.Creativity)) { Action.Learn(it) }
    )

    private val needToMappings: Map<NeedType, List<SubtypeMapping>> = buildMap {
        for (m in mappings) {
            for (need in m.needs) {
                getOrPut(need) { mutableListOf() }.let { (it as MutableList).add(m) }
            }
        }
    }

    fun findAction(needType: NeedType, peep: Peep, map: WorldMap): ActionCandidate? {
        val subtypeMappings = needToMappings[needType] ?: return null

        for (m in subtypeMappings) {
            val building = map.buildings.values.firstOrNull { it.subtype == m.subtype }
            if (building != null) {
                return ActionCandidate(building, m.actionFactory(building.id), m.needs)
            }
        }

        // Fallback: try matching by category (for buildings without subtypes)
        for (m in subtypeMappings) {
            val building = map.buildings.values.firstOrNull { it.type == m.subtype.category }
            if (building != null) {
                return ActionCandidate(building, m.actionFactory(building.id), m.needs)
            }
        }

        return null
    }
}
