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

enum class BuildingSubtype(val category: BuildingType) {
    // Residential
    House(BuildingType.Residential),
    Apartment(BuildingType.Residential),
    Luxury(BuildingType.Residential),
    // Commercial
    Restaurant(BuildingType.Commercial),
    GroceryStore(BuildingType.Commercial),
    Cafe(BuildingType.Commercial),
    Shop(BuildingType.Commercial),
    Office(BuildingType.Commercial),
    // Industrial
    Factory(BuildingType.Industrial),
    Warehouse(BuildingType.Industrial),
    Workshop(BuildingType.Industrial),
    // Civic
    Hospital(BuildingType.Civic),
    School(BuildingType.Civic),
    Library(BuildingType.Civic),
    CommunityCenter(BuildingType.Civic),
    // Recreation
    Park(BuildingType.Recreation),
    Gym(BuildingType.Recreation),
    Theater(BuildingType.Recreation),
    Stadium(BuildingType.Recreation),
    Museum(BuildingType.Recreation)
}

data class Building(
    val id: BuildingId,
    val type: BuildingType,
    val subtype: BuildingSubtype? = null,
    val cells: Set<CellCoord>,
    val ownerId: PeepId? = null,
    val tenantIds: Set<PeepId> = emptySet(),
    val workerIds: Set<PeepId> = emptySet()
)
