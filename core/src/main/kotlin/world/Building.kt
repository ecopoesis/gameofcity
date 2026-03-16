package world

typealias PeepId = Int

enum class BuildingType {
    Residential,
    Commercial,
    Industrial,
    Civic,
    Recreation,
    Entertainment  // kept for v1 compat, maps to Recreation
}

enum class BuildingSubtype(val category: BuildingType, val capacityMultiplier: Int) {
    // Residential
    House(BuildingType.Residential, 2),
    Apartment(BuildingType.Residential, 4),
    Luxury(BuildingType.Residential, 2),
    // Commercial
    Restaurant(BuildingType.Commercial, 4),
    GroceryStore(BuildingType.Commercial, 3),
    Cafe(BuildingType.Commercial, 3),
    Shop(BuildingType.Commercial, 3),
    Office(BuildingType.Commercial, 6),
    // Industrial
    Factory(BuildingType.Industrial, 5),
    Warehouse(BuildingType.Industrial, 4),
    Workshop(BuildingType.Industrial, 3),
    // Civic
    Hospital(BuildingType.Civic, 3),
    School(BuildingType.Civic, 5),
    Library(BuildingType.Civic, 3),
    CommunityCenter(BuildingType.Civic, 4),
    // Recreation
    Park(BuildingType.Recreation, 10),
    Gym(BuildingType.Recreation, 3),
    Theater(BuildingType.Recreation, 6),
    Stadium(BuildingType.Recreation, 20),
    Museum(BuildingType.Recreation, 4)
}

data class Building(
    val id: BuildingId,
    val type: BuildingType,
    val subtype: BuildingSubtype? = null,
    val cells: Set<CellCoord>,
    val ownerId: PeepId? = null,
    val tenantIds: Set<PeepId> = emptySet(),
    val workerIds: Set<PeepId> = emptySet()
) {
    val capacity: Int get() = cells.size * (subtype?.capacityMultiplier ?: 3)

    val currentOccupants: MutableSet<PeepId> = mutableSetOf()

    val isFull: Boolean get() = currentOccupants.size >= capacity

    val vacancies: Int get() = (capacity - currentOccupants.size).coerceAtLeast(0)
}
