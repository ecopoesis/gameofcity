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

    var wage: Int = subtype?.baseWage ?: 0

    var rent: Int = subtype?.baseRent ?: 0

    val isWorkplace: Boolean get() = subtype?.baseWage != null && subtype.baseWage > 0

    val isResidential: Boolean get() = type == BuildingType.Residential

    val workerCount: Int get() = currentOccupants.size  // during work hours approximates workers

    val isHiring: Boolean get() = isWorkplace && !isFull
}

val BuildingSubtype.baseRent: Int get() = when (this) {
    BuildingSubtype.House -> 10
    BuildingSubtype.Apartment -> 20
    BuildingSubtype.Luxury -> 50
    else -> 0
}

val BuildingSubtype.homeQuality: Int get() = when (this) {
    BuildingSubtype.House -> 1
    BuildingSubtype.Apartment -> 2
    BuildingSubtype.Luxury -> 3
    else -> 0
}

val BuildingSubtype.baseWage: Int get() = when (this) {
    BuildingSubtype.Office -> 15
    BuildingSubtype.Hospital -> 18
    BuildingSubtype.School -> 14
    BuildingSubtype.Factory -> 10
    BuildingSubtype.Workshop -> 12
    BuildingSubtype.Warehouse -> 8
    BuildingSubtype.Library -> 10
    BuildingSubtype.Restaurant -> 6
    BuildingSubtype.Cafe -> 5
    BuildingSubtype.Shop -> 7
    BuildingSubtype.GroceryStore -> 6
    else -> 0
}
